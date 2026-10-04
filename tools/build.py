#!/usr/bin/env python3
"""Offline build with ABC API 35/platform build-tools. No Gradle/network dependency."""
from pathlib import Path
import hashlib, json, os, secrets, shutil, subprocess, sys, tempfile, xml.etree.ElementTree as ET, zipfile

ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'build'
OUT=ROOT/'dist'

def run(args):
    result=subprocess.run([str(x) for x in args],cwd=ROOT)
    if result.returncode: raise SystemExit(result.returncode)

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    project=json.loads((ROOT/'bridge-project.json').read_text())
    manifest=ET.parse(ROOT/'app/AndroidManifest.xml').getroot()
    assert manifest.attrib['package']==project['package'],'Manifest/package configuration mismatch'
    assert manifest.attrib['{http://schemas.android.com/apk/res/android}versionName']==project['version'],'Manifest/version configuration mismatch'
    jar=Path(os.environ.get('ANDROID_JAR',''))
    bt=Path(os.environ.get('ANDROID_BUILD_TOOLS',''))
    if not jar.is_file() or not (bt/'lib/d8.jar').is_file():
        raise SystemExit('Set ANDROID_JAR to API 35 android.jar and ANDROID_BUILD_TOOLS to the host build-tools/35.0.0 folder from ABC.')
    java=shutil.which('java')
    if not java: raise SystemExit('Java 17 or newer is required.')
    suffix='.exe' if os.name=='nt' else ''
    aapt,align=bt/('aapt'+suffix),bt/('zipalign'+suffix)
    for f in [aapt,align,bt/'lib/apksigner.jar',bt/'lib/d8.jar']:
        if not f.is_file(): raise SystemExit(f'Missing build tool: {f.name}')
    if os.name!='nt':
        # Stage native binaries outside virtual file mounts that discard executable bits.
        staging=tempfile.TemporaryDirectory(prefix='wakkan-bridge-native-')
        stage=Path(staging.name)
        for name in ['aapt','zipalign']:
            shutil.copy2(bt/name,stage/name); (stage/name).chmod(0o755)
        if (bt/'lib64').is_dir(): shutil.copytree(bt/'lib64',stage/'lib64')
        aapt,align=stage/'aapt',stage/'zipalign'
    if BUILD.exists(): shutil.rmtree(BUILD)
    for p in [BUILD/'classes',BUILD/'dex',OUT]: p.mkdir(parents=True,exist_ok=True)
    payloads=[ROOT/p for p in project.get('payload_jars',[])]
    if project.get('provenance_java'):
        provenance=ROOT/project['provenance_java'];cls=project['provenance_class']
        provenance.write_text('package '+project['provenance_package']+';\npublic final class '+cls+' {\n'
                              +'    public static final String GAME_JAR_SHA256="'+digest(payloads[project['provenance_payload_index']])+'";\n'
                              +'    private '+cls+'() {}\n}\n')
    source_roots=project['source_roots']
    sources=sorted(p.resolve() for d in source_roots for p in (ROOT/d).rglob('*.java'))
    source_hashes={p.relative_to(ROOT).as_posix():digest(p) for p in sources}
    inputs={'sources':source_hashes,'manifest_sha256':digest(ROOT/'app/AndroidManifest.xml'),
            'project_sha256':digest(ROOT/'bridge-project.json'),
            'payloads':{p.relative_to(ROOT).as_posix():digest(p) for p in payloads}}
    build_id=hashlib.sha256(json.dumps(inputs,sort_keys=True).encode()).hexdigest()
    (ROOT/'app/assets/bridge-build.json').write_text(json.dumps({'build_input_id':build_id,
                    'app_version':project['version'],'inputs':inputs},indent=2)+'\n')
    # javac @files require quoted paths, including on Windows.
    args=BUILD/'sources.txt'
    args.write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in sources)+'\n')
    run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-source','8','-target','8','-Xlint:-options',
         '-cp',jar.resolve(),'-d',BUILD/'classes','@'+str(args)])
    host=BUILD/'bridge-classes.jar'
    with zipfile.ZipFile(host,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted((BUILD/'classes').rglob('*.class')): z.write(p,p.relative_to(BUILD/'classes').as_posix())
    run([java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',jar.resolve(),
         '--output',BUILD/'dex',host,*payloads])
    unsigned=BUILD/'unsigned.apk'
    run([aapt.resolve(),'package','-f','-M',ROOT/'app/AndroidManifest.xml','-I',jar.resolve(),
         '-A',ROOT/'app/assets','-S',ROOT/'app/res','-F',unsigned])
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
        for p in sorted((BUILD/'dex').glob('classes*.dex')): z.write(p,p.name)
    aligned=BUILD/'aligned.apk'; run([align.resolve(),'-f','4',unsigned,aligned])
    signing=ROOT/'signing';signing.mkdir(mode=0o700,exist_ok=True)
    identity=signing/'release-identity.json';keystore=signing/'release.keystore'
    if identity.exists(): cfg=json.loads(identity.read_text())
    else:
        if keystore.exists(): raise SystemExit('Signing identity metadata missing; refusing to silently replace the key.')
        cfg={'alias':project['signing_alias'],'store_password':secrets.token_urlsafe(24),
             'package':project['package'],'note':'Private canonical release identity. Keep this out of public source and preserve it for future updates.'}
        identity.write_text(json.dumps(cfg,indent=2)+'\n');identity.chmod(0o600)
    signing_env=os.environ.copy();signing_env['WAKKAN_RELEASE_KS_PASSWORD']=cfg['store_password']
    if cfg['package']!=project['package']:
        raise SystemExit('Signing identity belongs to another package. Use a separate project/signing folder.')
    if not keystore.exists():
        command=[java,'-m','java.base/sun.security.tools.keytool.Main','-genkeypair','-keystore',str(keystore),
                 '-storepass:env','WAKKAN_RELEASE_KS_PASSWORD','-keypass:env','WAKKAN_RELEASE_KS_PASSWORD',
                 '-alias',cfg['alias'],'-keyalg','RSA','-keysize','2048','-validity','10000',
                 '-dname',project['signing_dn']]
        subprocess.run(command,cwd=ROOT,env=signing_env,check=True);keystore.chmod(0o600)
    apk=OUT/project['apk_name']
    sign=[java,'-jar',str(bt/'lib/apksigner.jar'),'sign','--ks',str(keystore),'--ks-key-alias',cfg['alias'],
          '--ks-pass','env:WAKKAN_RELEASE_KS_PASSWORD','--key-pass','env:WAKKAN_RELEASE_KS_PASSWORD',
          '--v4-signing-enabled','false','--out',str(apk),str(aligned)]
    subprocess.run(sign,cwd=ROOT,env=signing_env,check=True)
    run([java,'-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',apk])
    run([align.resolve(),'-c','4',apk])
    (OUT/'APK_SHA256.txt').write_text(digest(apk)+'  '+apk.name+'\n')
    print('Built',apk.name)

if __name__=='__main__':main()
