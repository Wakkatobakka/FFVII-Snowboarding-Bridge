#!/usr/bin/env python3
"""Build the private FFVII Snowboarding Bridge payload from user-supplied game files.

No game data is downloaded or bundled with this tool. The accepted JAR/SP hashes
match the phone-verified private master used to validate Bridge v0.1.1.
"""
from pathlib import Path
import hashlib, os, shutil, struct, subprocess, sys, tempfile, zipfile, zlib
from generate_audio_midi import build_one

JAR_SHA='ef4c7527d55e0ece097ca1d4ea83e24b415064d7a8deba7a7310fbf0706cac01'
SP_SHA='2183917f4631250e710ab1c3aa0d68c69f0179129f7d191760ca281c12426bbb'
DEX_SHA='d8984af13ae2be8d73204680b548dc27164fc55a8a15cdf165900d9524b6c49e'
OUT_NAME='FFVII_Snowboarding_Data_for_Bridge_v0.1.2.zip'
MLD_NAMES=('elec_de_chocobo.mld','fanfare.mld','yuki_ni_tozasarete.mld')
BMP_NAMES=('SB_advanced.bmp','SB_basic.bmp','SB_beginners.bmp','SB_middle.bmp')

def sha(path: Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''): h.update(block)
    return h.hexdigest()

def sdk_paths():
    bt=os.environ.get('ANDROID_BUILD_TOOLS')
    aj=os.environ.get('ANDROID_JAR')
    if bt and aj and (Path(bt)/'lib/d8.jar').is_file() and Path(aj).is_file():
        return Path(bt),Path(aj)
    roots=[]
    for k in ('ANDROID_SDK_ROOT','ANDROID_HOME'):
        if os.environ.get(k): roots.append(Path(os.environ[k]))
    if os.name=='nt' and os.environ.get('LOCALAPPDATA'):
        roots.append(Path(os.environ['LOCALAPPDATA'])/'Android'/'Sdk')
    here=Path(__file__).resolve().parent
    roots += [here/'android-sdk',here.parent/'android-sdk',here.parent/'android_sdk_35']
    for root in roots:
        if not root.is_dir(): continue
        android_jar=root/'platforms'/'android-35'/'android.jar'
        preferred=root/'build-tools'/'35.0.0'
        if (preferred/'lib/d8.jar').is_file() and android_jar.is_file(): return preferred,android_jar
        candidates=sorted((root/'build-tools').glob('*'),reverse=True) if (root/'build-tools').is_dir() else []
        for cand in candidates:
            if (cand/'lib/d8.jar').is_file() and android_jar.is_file(): return cand,android_jar
    raise SystemExit('Android SDK not found. Install API 35 + Build-Tools 35.0.0, or set ANDROID_BUILD_TOOLS and ANDROID_JAR.')

def png_chunk(kind: bytes,data: bytes) -> bytes:
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)

def bmp8_to_png(data: bytes) -> bytes:
    if data[:2]!=b'BM': raise ValueError('not BMP')
    off=struct.unpack_from('<I',data,10)[0]
    dib=struct.unpack_from('<I',data,14)[0]
    if dib<40: raise ValueError('unsupported BMP DIB')
    width,height,planes,bpp,compression=struct.unpack_from('<iiHHI',data,18)
    if planes!=1 or bpp!=8 or compression!=0 or width<=0 or height==0: raise ValueError('expected uncompressed 8-bit BMP')
    colors=struct.unpack_from('<I',data,46)[0] or 256
    palette=data[14+dib:14+dib+colors*4]
    if len(palette)<colors*4: raise ValueError('truncated palette')
    plte=bytearray()
    for i in range(colors):
        b,g,r,_=palette[i*4:i*4+4]; plte += bytes((r,g,b))
    row_bytes=((width+3)//4)*4
    h=abs(height); raw=bytearray()
    for y in range(h):
        src_y=(h-1-y) if height>0 else y
        start=off+src_y*row_bytes
        row=data[start:start+width]
        if len(row)!=width: raise ValueError('truncated pixels')
        raw.append(0); raw.extend(row)
    sig=b'\x89PNG\r\n\x1a\n'
    ihdr=struct.pack('>IIBBBBB',width,h,8,3,0,0,0)
    return sig+png_chunk(b'IHDR',ihdr)+png_chunk(b'PLTE',bytes(plte))+png_chunk(b'IDAT',zlib.compress(bytes(raw),9))+png_chunk(b'IEND',b'')

def main(argv):
    if len(argv)!=3:
        print('Usage: python build_payload.py <game-offline-english.jar> <game-offline-english.sp>')
        return 2
    jar=Path(argv[1]).expanduser().resolve(); sp=Path(argv[2]).expanduser().resolve()
    if not jar.is_file() or not sp.is_file(): raise SystemExit('JAR or SP file not found.')
    if sha(jar)!=JAR_SHA: raise SystemExit('JAR does not match the supported FFVII Snowboarding English/offline build.\nSHA-256: '+sha(jar))
    if sha(sp)!=SP_SHA: raise SystemExit('SP does not match the supported FFVII Snowboarding data.\nSHA-256: '+sha(sp))
    bt,android_jar=sdk_paths(); java=shutil.which('java')
    if not java: raise SystemExit('Java is required (Java 17 recommended).')
    with tempfile.TemporaryDirectory(prefix='ffvii-snow-payload-') as td:
        work=Path(td); payload=work/'payload'; payload.mkdir(); (payload/'audio').mkdir()
        shutil.copy2(jar,payload/'game.jar'); shutil.copy2(sp,payload/'game.sp')
        dexout=work/'dex'; dexout.mkdir()
        subprocess.run([java,'-cp',str(bt/'lib/d8.jar'),'com.android.tools.r8.D8','--min-api','26','--lib',str(android_jar),'--output',str(dexout),str(jar)],check=True)
        dex=dexout/'classes.dex'
        if sha(dex)!=DEX_SHA: raise SystemExit('Generated game.dex does not match the validated D8/API-35 output. Use Android Build-Tools 35.0.0 + API 35.')
        shutil.copy2(dex,payload/'game.dex')
        with zipfile.ZipFile(jar) as z:
            for name in MLD_NAMES:
                target=work/name; target.write_bytes(z.read(name)); build_one(target,payload/'audio')
            for name in BMP_NAMES:
                (payload/(name+'.png')).write_bytes(bmp8_to_png(z.read(name)))
        out=Path.cwd()/OUT_NAME
        with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
            for p in sorted(payload.rglob('*')):
                if not p.is_file(): continue
                rel=p.relative_to(payload).as_posix()
                info=zipfile.ZipInfo(rel,date_time=(1980,1,1,0,0,0))
                info.compress_type=zipfile.ZIP_DEFLATED
                info.external_attr=0o100644 << 16
                z.writestr(info,p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
        print('\nBuilt:',out)
        print('SHA-256:',sha(out))
        print('Entries:')
        for p in sorted(payload.rglob('*')):
            if p.is_file(): print(' ',p.relative_to(payload).as_posix(),p.stat().st_size,sha(p))
    return 0
if __name__=='__main__': raise SystemExit(main(sys.argv))
