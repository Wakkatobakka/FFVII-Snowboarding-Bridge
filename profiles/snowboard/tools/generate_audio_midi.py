#!/usr/bin/env python3
"""Generate the Android-playable MIDI mirrors used by the Snowboarding audio bridge.

This is intentionally a bounded MLD/MFi subset converter for the three preserved
FFVII Snowboarding tracks in this profile. It does not replace the runtime or
attempt to become a general MLD implementation.

The source MLDs use one native infinite DD loop. Native state after the first
pass is stable and there are no notes held across the loop boundary, so each
song is emitted as:
  * <name>.intro.mid : source start through the first DD loop end
  * <name>.loop.mid  : one stable subsequent DD pass, with its channel state
                       explicitly restored at MIDI tick 0
The Android presenter plays the intro once and loops the second file.
"""
from __future__ import annotations
from dataclasses import dataclass, field
from pathlib import Path
import copy
import struct
import sys

OCTAVE = (0, 12, -24, -12)
DEFAULT_TIMEBASE = 48
DEFAULT_TEMPO = 125

@dataclass
class Event:
    track: int
    raw: int
    kind: str
    command: int = -1
    value: int = -1
    part: int = -1
    timebase: int = -1
    voice: int = -1
    pitch: int = -1
    gate: int = 0
    velocity: int = 63
    octave: int = 0

@dataclass
class Channel:
    mode: int = 0
    program: int = 0
    bank: int = 0
    level: int = 63
    pan: int = 32
    pitch_coarse: int = 32
    pitch_fine: int = 32
    pitch_range: int = 2
    modulation: int = 0
    has_program: bool = False

@dataclass
class State:
    channels: list[Channel] = field(default_factory=lambda: [Channel() for _ in range(16)])
    master_volume: int = 100
    tempo: int = DEFAULT_TEMPO
    timebase: int = DEFAULT_TIMEBASE
    active: dict[tuple[int, int], tuple[int, int, int]] = field(default_factory=dict)
    # key -> (end_raw, midi_note, note_on_order)

@dataclass
class Cursor:
    track: int
    events: list[Event]
    index: int = 0
    due: int = 0
    done: bool = False
    def __post_init__(self):
        self.done = not self.events
        self.due = (1 << 62) if self.done else self.events[0].raw
    def current(self) -> Event:
        return self.events[self.index]
    def advance(self, current_tick: int) -> None:
        prev = self.events[self.index]
        self.index += 1
        if self.index >= len(self.events):
            self.done = True
            self.due = 1 << 62
            return
        nxt = self.events[self.index]
        self.due = current_tick + (nxt.raw - prev.raw)


def be16(b: bytes) -> int:
    return int.from_bytes(b, 'big')


def timebase_for(selector: int) -> int:
    return {0x0:6,0x1:12,0x2:24,0x3:48,0x4:96,0x5:192,0x6:384,
            0x8:15,0x9:30,0xA:60,0xB:120,0xC:240,0xD:480,0xE:960}.get(selector, -1)


def parse_mld(path: Path) -> list[list[Event]]:
    data = path.read_bytes()
    if data[:4] != b'melo':
        raise ValueError(f'{path.name}: not an MLD/melo file')
    pos = 13  # melo+size(8), header length(2), song/instruments/tracks(3)
    note_extra = 0
    exst = 0
    while pos + 6 <= len(data) and data[pos:pos+4] != b'trac':
        name = data[pos:pos+4]
        size = be16(data[pos+4:pos+6])
        body = data[pos+6:pos+6+size]
        if name == b'note' and len(body) >= 2:
            note_extra = be16(body[:2])
        elif name == b'exst' and len(body) >= 2:
            exst = be16(body[:2])
        pos += 6 + size
    tracks: list[list[Event]] = []
    track_index = 0
    while pos + 8 <= len(data):
        if data[pos:pos+4] != b'trac':
            raise ValueError(f'{path.name}: unexpected chunk at 0x{pos:x}')
        size = int.from_bytes(data[pos+4:pos+8], 'big')
        payload = data[pos+8:pos+8+size]
        pos += 8 + size
        tracks.append(parse_track(track_index, payload, note_extra, exst))
        track_index += 1
    return tracks


def parse_track(track: int, payload: bytes, note_extra: int, exst: int) -> list[Event]:
    out: list[Event] = []
    off = 0
    raw = 0
    pending = 0
    while off < len(payload):
        if off + 2 > len(payload):
            raise ValueError(f'track {track}: truncated event')
        delta = payload[off] + pending
        pending = 0
        status = payload[off+1]
        off += 2
        raw += delta
        if status in (0x3F, 0x7F, 0xBF):
            command = payload[off]; off += 1
            if command >= 0xF0:
                size = be16(payload[off:off+2]); off += 2 + size
            else:
                body = (1 + exst) if command < 0x80 else 1
                off += body
            continue  # no PCM/resource events exist in these three files
        if status == 0xFF:
            command = payload[off]; off += 1
            if command < 0x80:
                off += 1 + exst
                out.append(Event(track, raw, 'sys', command=command))
                continue
            if command >= 0xF0:
                size = be16(payload[off:off+2]); off += 2 + size
                out.append(Event(track, raw, 'sys', command=command))
                continue
            value = payload[off]; off += 1
            if command == 0xDC:
                pending = value << 8
            part = ((value >> 6) & 0x03) if 0xE0 <= command <= 0xEF else -1
            tb = timebase_for(command & 0x0F) if 0xC0 <= command <= 0xCF else -1
            out.append(Event(track, raw, 'sys', command=command, value=value, part=part, timebase=tb))
            if command == 0xDF:
                break
            continue
        if off >= len(payload):
            raise ValueError(f'track {track}: truncated note')
        gate = payload[off]; off += 1
        velocity = 63
        octave = 0
        if note_extra:
            attr = payload[off]; off += 1
            velocity = (attr >> 2) & 0x3F
            octave = attr & 0x03
            off += note_extra - 1
        out.append(Event(track, raw, 'note', voice=(status >> 6) & 3,
                         pitch=status & 0x3F, gate=gate,
                         velocity=velocity, octave=octave))
    return out


def copied(event: Event, raw: int) -> Event:
    e = copy.copy(event)
    e.raw = raw
    return e


def schedule_two_passes(tracks: list[list[Event]]) -> tuple[list[Event], list[int], list[int]]:
    """Execute the native DD rewind until two loop-end boundaries are reached."""
    cursors = [Cursor(i, t) for i, t in enumerate(tracks)]
    slot = None
    executed: list[Event] = []
    end_indices: list[int] = []
    end_ticks: list[int] = []
    current = 0
    while True:
        candidates = [i for i, c in enumerate(cursors) if not c.done]
        if not candidates:
            raise ValueError('MLD ended before two DD loop passes were observed')
        ci = min(candidates, key=lambda i: (cursors[i].due, cursors[i].track))
        cursor = cursors[ci]
        current = cursor.due
        source = cursor.current()
        event = copied(source, current)
        executed.append(event)
        if event.kind == 'sys' and event.track == 0 and event.command == 0xDD:
            slot_id = (event.value >> 6) & 0x03
            operation = event.value & 0x03
            if operation == 0:
                cursor.advance(current)  # capture after the START command
                slot = {
                    'id': slot_id,
                    'indices': [c.index for c in cursors],
                    'done': [c.done for c in cursors],
                    'remaining': [(None if c.done else c.due - current) for c in cursors],
                }
                continue
            if operation == 1 and slot is not None and slot['id'] == slot_id:
                repeat = (event.value >> 2) & 0x0F
                if repeat != 0:
                    raise ValueError('bounded converter expects the Snowboarding tracks\' infinite DD loop')
                end_indices.append(len(executed) - 1)
                end_ticks.append(current)
                if len(end_indices) == 2:
                    return executed, end_indices, end_ticks
                # Native DD rewinds every track parser context.
                for i, c in enumerate(cursors):
                    c.index = slot['indices'][i]
                    c.done = slot['done'][i]
                    c.due = (1 << 62) if c.done else current + slot['remaining'][i]
                continue
        cursor.advance(current)


def vlq(value: int) -> bytes:
    if value < 0:
        raise ValueError('negative MIDI delta')
    buf = [value & 0x7F]
    value >>= 7
    while value:
        buf.append(0x80 | (value & 0x7F))
        value >>= 7
    return bytes(reversed(buf))


def clamp(lo: int, hi: int, value: int) -> int:
    return max(lo, min(hi, value))


def program_for(c: Channel) -> int:
    p = c.program & 0x3F
    b = c.bank & 0x3F
    if (b & 0x3E) == 0:
        mapped = {0:0, 1:9, 2:16, 3:24, 4:13, 5:74}.get(p)
        if mapped is not None:
            return mapped
    return (p | (b << 6)) & 0x7F


def pitch_bend(c: Channel) -> int:
    return clamp(0, 16383, (8 * (c.pitch_fine + (32 * c.pitch_coarse))) - 256)


def midi_volume(state: State, c: Channel) -> int:
    return clamp(0, 127, round((c.level * 2) * state.master_volume / 127.0))


def midi_pan(c: Channel) -> int:
    return clamp(0, 127, c.pan * 2)


def add_cc(msgs, tick, order, ch, cc, value):
    msgs.append((tick, order, bytes((0xB0 | ch, cc & 0x7F, value & 0x7F))))


def add_program(msgs, tick, order, ch, program):
    msgs.append((tick, order, bytes((0xC0 | ch, program & 0x7F))))


def add_bend(msgs, tick, order, ch, bend14):
    v = clamp(0, 16383, bend14)
    msgs.append((tick, order, bytes((0xE0 | ch, v & 0x7F, (v >> 7) & 0x7F))))


def add_tempo(msgs, tick, order, bpm):
    mpqn = 60_000_000 // max(1, bpm)
    msgs.append((tick, order, b'\xff\x51\x03' + mpqn.to_bytes(3, 'big')))


def emit_channel_snapshot(msgs, state: State, tick: int, order_seed: int = -10000) -> int:
    order = order_seed
    add_tempo(msgs, tick, order, state.tempo); order += 1
    for ch, c in enumerate(state.channels):
        add_cc(msgs, tick, order, ch, 7, midi_volume(state, c)); order += 1
        add_cc(msgs, tick, order, ch, 10, midi_pan(c)); order += 1
        # Explicitly establish the native default/current bend range.
        for cc, value in ((101,0),(100,0),(6,c.pitch_range),(38,0),(101,127),(100,127)):
            add_cc(msgs, tick, order, ch, cc, value); order += 1
        add_bend(msgs, tick, order, ch, pitch_bend(c)); order += 1
        add_cc(msgs, tick, order, ch, 1, clamp(0, 127, c.modulation * 2)); order += 1
        if c.has_program and c.mode in (0,1):
            add_program(msgs, tick, order, ch, program_for(c)); order += 1
    return order


def render(events: list[Event], start_raw: int, end_raw: int, initial: State | None = None) -> tuple[list[tuple[int,int,bytes]], State]:
    state = copy.deepcopy(initial) if initial is not None else State()
    if state.timebase != DEFAULT_TIMEBASE:
        raise ValueError('Snowboarding audio bridge expects 48-tick timebase at segment start')
    msgs: list[tuple[int,int,bytes]] = []
    order = emit_channel_snapshot(msgs, state, 0)

    def rel(raw: int) -> int:
        return raw - start_raw

    def flush(until_raw: int):
        nonlocal order
        expired = sorted((k,v) for k,v in state.active.items() if v[0] <= until_raw)
        for key, (end_raw_note, midi_note, _note_order) in expired:
            ch = key[0]
            msgs.append((rel(end_raw_note), order, bytes((0x80 | ch, midi_note & 0x7F, 0))))
            order += 1
            del state.active[key]

    for event in events:
        if event.raw < start_raw or event.raw > end_raw:
            continue
        flush(event.raw)
        tick = rel(event.raw)
        if event.kind == 'note':
            ch = event.track * 4 + event.voice
            if ch >= 16:
                continue
            c = state.channels[ch]
            if c.mode not in (0,1):
                continue
            pitch_offset = event.pitch + OCTAVE[event.octave]
            native_note = (35 if c.mode == 1 else 45) + pitch_offset
            midi_note = clamp(0, 127, (35 if (ch == 9 and c.mode in (0,1)) else (35 if c.mode == 1 else 45)) + pitch_offset)
            velocity = clamp(1, 127, event.velocity * 2)
            key = (ch, native_note & 0xFF)
            end = event.raw + event.gate
            if key in state.active:
                old = state.active[key]
                state.active[key] = (end, old[1], old[2])
            else:
                if not c.has_program:
                    # Native channel defaults are valid; no Program Change is required.
                    pass
                msgs.append((tick, order, bytes((0x90 | ch, midi_note & 0x7F, velocity & 0x7F))))
                state.active[key] = (end, midi_note, order)
                order += 1
            continue
        if event.kind != 'sys':
            continue
        cmd = event.command
        val = event.value
        if 0xC0 <= cmd <= 0xCF and event.timebase > 0:
            if event.timebase != DEFAULT_TIMEBASE:
                raise ValueError('these Snowboarding tracks unexpectedly change away from 48-tick timebase')
            state.timebase = event.timebase
            state.tempo = clamp(20, 255, val)
            add_tempo(msgs, tick, order, state.tempo); order += 1
            continue
        if cmd == 0xB0 and event.track == 0 and 0 <= val < 128:
            state.master_volume = val
            for ch, c in enumerate(state.channels):
                add_cc(msgs, tick, order, ch, 7, midi_volume(state, c)); order += 1
            continue
        if cmd == 0xBA and event.track == 0 and 0 <= val < 128:
            ch = (val >> 3) & 0x0F
            state.channels[ch].mode = val & 0x07
            c = state.channels[ch]
            if c.mode == 1 and c.has_program:
                add_program(msgs, tick, order, ch, program_for(c)); order += 1
            continue
        if not (0xE0 <= cmd <= 0xEF) or event.part < 0:
            continue
        ch = event.track * 4 + event.part
        if ch >= 16:
            continue
        c = state.channels[ch]
        packed = val & 0x3F
        if cmd == 0xE0:
            c.program = packed; c.has_program = True
            if c.mode in (0,1):
                add_program(msgs, tick, order, ch, program_for(c)); order += 1
        elif cmd == 0xE1:
            c.bank = packed
            if c.mode == 1 and c.has_program:
                add_program(msgs, tick, order, ch, program_for(c)); order += 1
        elif cmd == 0xE2:
            c.level = packed
            add_cc(msgs, tick, order, ch, 7, midi_volume(state, c)); order += 1
        elif cmd == 0xE3:
            c.pan = packed
            add_cc(msgs, tick, order, ch, 10, midi_pan(c)); order += 1
        elif cmd == 0xE4:
            c.pitch_coarse = packed
            add_bend(msgs, tick, order, ch, pitch_bend(c)); order += 1
        elif cmd == 0xE6:
            c.level = clamp(0, 63, c.level + packed - 32)
            add_cc(msgs, tick, order, ch, 7, midi_volume(state, c)); order += 1
        elif cmd == 0xE7:
            if packed <= 24:
                c.pitch_range = packed
                for cc, value in ((101,0),(100,0),(6,c.pitch_range),(38,0),(101,127),(100,127)):
                    add_cc(msgs, tick, order, ch, cc, value); order += 1
        elif cmd == 0xE8:
            c.pitch_fine = packed
            add_bend(msgs, tick, order, ch, pitch_bend(c)); order += 1
        elif cmd == 0xE9:
            c.pitch_fine = packed  # semantic state only; E8/E4 performs the audible write
        elif cmd == 0xEA:
            c.modulation = packed
            add_cc(msgs, tick, order, ch, 1, clamp(0,127,c.modulation*2)); order += 1
    flush(end_raw)
    if state.active:
        # The three preserved Snowboarding loops are intentionally clean at the boundary.
        held = ', '.join(str(k) for k in state.active)
        raise ValueError(f'notes cross segment boundary: {held}')
    return msgs, state


def write_midi(path: Path, messages: list[tuple[int,int,bytes]], end_tick: int) -> None:
    messages = sorted(messages, key=lambda x: (x[0], x[1]))
    track = bytearray()
    last = 0
    for tick, _order, payload in messages:
        if tick < 0 or tick > end_tick:
            continue
        track += vlq(tick - last)
        track += payload
        last = tick
    track += vlq(max(0, end_tick - last)) + b'\xff\x2f\x00'
    header = b'MThd' + struct.pack('>IHHH', 6, 0, 1, DEFAULT_TIMEBASE)
    path.write_bytes(header + b'MTrk' + struct.pack('>I', len(track)) + track)


def build_one(mld: Path, out_dir: Path) -> None:
    tracks = parse_mld(mld)
    executed, ends, ticks = schedule_two_passes(tracks)
    first_i, second_i = ends
    first_tick, second_tick = ticks
    if second_tick <= first_tick:
        raise ValueError('invalid loop boundaries')
    intro_events = executed[:first_i+1]
    loop_events = executed[first_i+1:second_i+1]
    intro_msgs, boundary_state = render(intro_events, 0, first_tick, None)
    loop_msgs, final_state = render(loop_events, first_tick, second_tick, boundary_state)
    # A stable loop must return to exactly the same semantic channel/tempo state.
    a = copy.deepcopy(boundary_state); b = copy.deepcopy(final_state)
    a.active.clear(); b.active.clear()
    if a != b:
        raise ValueError(f'{mld.name}: loop state did not stabilize after first native pass')
    stem = mld.stem
    write_midi(out_dir / f'{stem}.intro.mid', intro_msgs, first_tick)
    write_midi(out_dir / f'{stem}.loop.mid', loop_msgs, second_tick-first_tick)
    seconds_intro = first_tick * 60.0 / (boundary_state.tempo * DEFAULT_TIMEBASE)
    seconds_loop = (second_tick-first_tick) * 60.0 / (boundary_state.tempo * DEFAULT_TIMEBASE)
    print(f'{mld.name}: intro={first_tick} ticks ({seconds_intro:.3f}s), loop={second_tick-first_tick} ticks ({seconds_loop:.3f}s)')


def main() -> int:
    here = Path(__file__).resolve()
    root = here.parents[3]
    game = root / 'app' / 'assets' / 'game'
    out = game / 'audio'
    out.mkdir(parents=True, exist_ok=True)
    names = ('elec_de_chocobo.mld', 'fanfare.mld', 'yuki_ni_tozasarete.mld')
    for name in names:
        build_one(game / name, out)
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
