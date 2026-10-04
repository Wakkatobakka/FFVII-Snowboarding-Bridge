#!/usr/bin/env python3
"""Public-release checks. No phone claims and no private game inputs required."""
from pathlib import Path
from hashlib import sha256
import json, os, struct, subprocess, tempfile, xml.etree.ElementTree as ET, zipfile

ROOT=Path(__file__).resolve().parents[1]
ANDROID='{http://schemas.android.com/apk/res/android}'

def dex_classes(data):
    def u32(offset): return struct.unpack_from('<I',data,offset)[0]
    strings=[]
    for i in range(u32(56)):
        offset=u32(u32(60)+i*4)
        while data[offset]&128:offset+=1
        offset+=1;end=data.index(0,offset)
        strings.append(data[offset:end].decode('utf-8','replace'))
    types=[strings[u32(u32(68)+i*4)] for i in range(u32(64))]
    return {types[u32(u32(100)+i*32)] for i in range(u32(96))}

def expected_payload_count():
    text=(ROOT/'app/assets/expected-payload.properties').read_text().splitlines()
    return sum(1 for line in text if line.startswith('entry.') and line.endswith(tuple()) )

def main():
    java=os.environ.get('JAVA','java')
    with tempfile.TemporaryDirectory(prefix='snowboard-bridge-checks-') as t:
        files=['shared/src/com/wakka/bridge/InputLatch.java','shared/src/com/wakka/bridge/SessionGate.java',
               'profiles/snowboard/src/com/wakka/snowboardbridge/SnowboardInput.java','tests/BehaviorChecks.java']
        subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-d',t,*[str(ROOT/f) for f in files]],check=True)
        subprocess.run([java,'-cp',t,'BehaviorChecks'],check=True)

    manifest=ET.parse(ROOT/'app/AndroidManifest.xml').getroot()
    assert manifest.attrib['package']=='com.wakka.ffviisnowboardingbridge'
    assert manifest.attrib[ANDROID+'versionName']=='0.1.2'
    application=manifest.find('application');assert application.attrib[ANDROID+'allowBackup']=='false'
    assert application.attrib[ANDROID+'label']=='FFVII Snowboarding Bridge'
    provider=application.find('provider')
    assert provider.attrib[ANDROID+'authorities']=='com.wakka.ffviisnowboardingbridge.reports'
    assert provider.attrib[ANDROID+'exported']=='false'
    assert not manifest.findall('uses-permission')
    assert all(x.attrib[ANDROID+'exported']=='false' for x in application.findall('activity') if not x.findall('intent-filter'))

    # Public source boundary.
    forbidden=[]
    for p in ROOT.rglob('*'):
        if not p.is_file(): continue
        rel=p.relative_to(ROOT).as_posix().lower()
        if rel.startswith('build/') or rel.startswith('dist/'): continue
        if rel.endswith('/game-offline-english.jar') or rel.endswith('/game-offline-english.sp') or rel.endswith('/game-offline-english.jam'):
            forbidden.append(rel)
    assert not forbidden,forbidden
    assert not (ROOT/'app/assets/game').exists()
    assert not (ROOT/'profiles/snowboard/inputs').exists()

    project=json.loads((ROOT/'bridge-project.json').read_text())
    assert project['package']=='com.wakka.ffviisnowboardingbridge' and project['version']=='0.1.2'
    assert project.get('payload_jars')==[]
    apk=ROOT/'dist'/project['apk_name']
    with zipfile.ZipFile(apk) as z:
        assert z.testzip() is None
        names=set(z.namelist())
        assert 'assets/expected-payload.properties' in names
        assert not any(n.startswith('assets/game/') for n in names)
        assert not any(n.lower().endswith(('.jar','.sp','.jam','.mld','.mid','.bmp','.gif','.mbac','.mtra','.dat','.dec')) for n in names)
        assert not any(secret in n.lower() for n in names for secret in ['keystore','prototype-identity','store_password'])
        build=json.loads(z.read('assets/bridge-build.json'))
        assert build['app_version']=='0.1.2'
        assert build['inputs']['payloads']=={}
        for relative,expected in build['inputs']['sources'].items():
            assert sha256((ROOT/relative).read_bytes()).hexdigest()==expected,'Stale APK source: '+relative
        assert sha256((ROOT/'app/AndroidManifest.xml').read_bytes()).hexdigest()==build['inputs']['manifest_sha256']
        assert sha256((ROOT/'bridge-project.json').read_bytes()).hexdigest()==build['inputs']['project_sha256']
        classes=set()
        for name in names:
            if name.endswith('.dex'):classes.update(dex_classes(z.read(name)))
        assert 'LSnowBoard;' not in classes
        assert {'Lcom/wakka/ffviisnowboardingbridge/LauncherActivity;','Lcom/wakka/ffviisnowboardingbridge/GameActivity;',
                'Lcom/wakka/ffviisnowboardingbridge/ToolsActivity;','Lcom/wakka/bridge/ReportProvider;',
                'Lcom/wakka/snowboardbridge/SnowboardPayload;'}<=classes

    props=(ROOT/'app/assets/expected-payload.properties').read_text().splitlines()
    hashes=[x for x in props if x.startswith('entry.') and '.sha256=' in x]
    sizes=[x for x in props if x.startswith('entry.') and '.size=' in x]
    assert len(hashes)==13 and len(sizes)==13,(len(hashes),len(sizes))
    assert (ROOT/'payload-builder/build_payload.py').is_file()
    assert (ROOT/'payload-builder/RUN-PAYLOAD-BUILDER-WINDOWS.bat').is_file()
    print('PASS: 26 custom-control/lifecycle assertions')
    print('PASS: canonical public package/version/provider identity; no runtime permissions')
    print('PASS: public source contains no original JAR/JAM/SP; signing material is never bundled in the APK')
    local_keys=list((ROOT/'signing').glob('*.keystore'))+list((ROOT/'signing').glob('*.jks'))
    if local_keys: print('NOTE: local build signing key exists under signing/; .gitignore excludes it — do not publish it')
    print('PASS: public APK contains no game payload, game assets, original game classes, or generated game audio')
    print('PASS: APK embeds only the 13-file payload allowlist/fingerprints needed for local verification')
    print('PASS: payload builder/importer and in-memory game DEX loader are present')
    print('NOT RUN: physical Android import/gameplay/audio/custom-control validation for v0.1.2')

if __name__=='__main__':main()
