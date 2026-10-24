"""Compile using pinned AOSP Maven tools. Does not install an SDK or accept agreements."""
from pathlib import Path
import hashlib,json,os,shutil,struct,subprocess,sys,zipfile,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
TOOLS=ROOT/'.tools'; BUILD=ROOT/'build';DIST=ROOT/'dist'
if hasattr(sys.stdout,'reconfigure'):sys.stdout.reconfigure(encoding='utf-8')

def app_version():
    return ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot().attrib['{http://schemas.android.com/apk/res/android}versionName']

def runtime_inputs():
    return {p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted((ROOT/'app/src').rglob('*')) if p.is_file()}

def run(args):
    result=subprocess.run([str(x) for x in args],cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    try:output=result.stdout.decode('utf-8')
    except UnicodeDecodeError:output=result.stdout.decode('gb18030',errors='replace')
    output=output.replace('\r\n','\n')
    with (BUILD/'build-log.txt').open('a',encoding='utf-8') as log:log.write('> '+Path(args[0]).name+'\n'+output+'\n')
    print(output,end='',flush=True)
    if result.returncode:raise RuntimeError(f'Command failed ({result.returncode}): {args[0]}')
    return output

def jdk():
    configured=os.environ.get('QINGYUE_JDK')
    if configured:
        path=Path(configured)
        if not (path/'bin/java.exe').exists():raise RuntimeError('QINGYUE_JDK must point to the approved JDK directory')
        return path/'bin'
    choices=list(TOOLS.glob('temurin/jdk*/bin/java.exe'))
    if not choices:raise RuntimeError('No approved task-local JDK. Set QINGYUE_JDK only after explicit runtime approval.')
    return choices[0].parent

def clean_dir(path):
    path=path.resolve()
    if not path.is_relative_to(BUILD.resolve()) or path==BUILD.resolve():raise RuntimeError('Unsafe clean target')
    if path.exists():shutil.rmtree(path)
    path.mkdir(parents=True)

def compile_java(java,sources,out,classpath=None):
    out.mkdir(parents=True,exist_ok=True)
    args=[java/'javac.exe','--release','8','-encoding','UTF-8','-d',out]
    if classpath:args+=['-cp',os.pathsep.join(str(x) for x in classpath)]
    # Response file avoids Windows command-line length and shell interpolation.
    response=out.parent/(out.name+'-sources.txt')
    response.write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in sources),encoding='utf-8')
    return run(args+['@'+str(response)])

def tests(java):
    out=BUILD/'tests';clean_dir(out)
    sources=sorted((ROOT/'app/src/main/java/org/qingyue/rope/core').glob('*.java'))+[ROOT/'tests/CoreTests.java']
    compile_java(java,sources,out)
    result=run([java/'java.exe','-cp',out,'CoreTests'])
    (BUILD/'core-tests.txt').write_text(result,encoding='utf-8')

def add_aligned(archive,name,data,alignment=4):
    info=zipfile.ZipInfo(name,(2026,10,23,0,0,0));info.compress_type=zipfile.ZIP_STORED
    base=archive.fp.tell()+30+len(name.encode('utf-8'))
    padding=(-base-4)%alignment
    if base%alignment:info.extra=struct.pack('<HH',0xcafe,padding)+bytes(padding)
    archive.writestr(info,data)

def verify_package(apk):
    with zipfile.ZipFile(apk) as z:
        names=z.namelist()
        assert 'classes.dex' in names and 'assets/movenet.tflite' in names
        assert 'assets/LICENSE-2.0.txt' in names
        assert not any(x.startswith(('res/raw/video','assets/video')) for x in names)
        data=apk.read_bytes()
        for item in z.infolist():
            if item.filename=='resources.arsc' or item.filename.endswith(('.so','.tflite')):
                offset=item.header_offset
                name_len,extra_len=struct.unpack_from('<HH',data,offset+26)
                start=offset+30+name_len+extra_len
                alignment=16384 if item.filename.endswith('.so') else 4
                assert start%alignment==0,(item.filename,start,alignment)
        assert z.testzip() is None
    print('PASS APK CRC, model, licenses, Dex, resource/native alignment')

def validate_dependencies():
    lock=json.loads((ROOT/'dependencies.lock.json').read_text())
    for name,info in lock.items():
        assert (TOOLS/name).is_file(),f'Missing dependency: {name}; run bootstrap.py'
        assert hashlib.sha256((TOOLS/name).read_bytes()).hexdigest()==info['sha256'],name
    with zipfile.ZipFile(TOOLS/'aapt2.jar') as z:
        assert (TOOLS/'aapt2/aapt2.exe').read_bytes()==z.read('aapt2.exe')
    for name in ('litert','litert-api'):
        with zipfile.ZipFile(TOOLS/(name+'.aar')) as z:
            for entry in z.namelist():
                if entry=='classes.jar' or entry=='LICENSE' or entry.endswith('.so'):
                    assert (TOOLS/name/entry).read_bytes()==z.read(entry),(name,entry)

def resources():
    asset=ROOT/'app/src/main/assets';asset.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(TOOLS/'Apache-2.0.txt',asset/'LICENSE-2.0.txt')
    shutil.copyfile(ROOT/'THIRD_PARTY_NOTICES.md',asset/'THIRD_PARTY_NOTICES.md')
    shutil.copyfile(TOOLS/'litert/LICENSE',asset/'LiteRT-LICENSE.txt')
    aapt=TOOLS/'aapt2/aapt2.exe';api=TOOLS/'android-all.jar';gen=BUILD/'generated';clean_dir(gen)
    run([aapt,'compile','--dir',ROOT/'app/src/main/res','-o',BUILD/'compiled-res.zip'])
    run([aapt,'link','-I',api,'--manifest',ROOT/'app/src/main/AndroidManifest.xml','--java',gen,'-A',asset,
         '--min-sdk-version','26','--target-sdk-version','34','-0','tflite','-o',BUILD/'resources.apk',BUILD/'compiled-res.zip'])
    report=run([aapt,'dump','badging',BUILD/'resources.apk'])+run([aapt,'dump','permissions',BUILD/'resources.apk'])
    (BUILD/'resources-check.txt').write_text(report,encoding='utf-8')
    return gen

def main():
    BUILD.mkdir(exist_ok=True);DIST.mkdir(exist_ok=True)
    (BUILD/'build-log.txt').write_text('Qingyue task-local build log\n',encoding='utf-8')
    if '--resources-only' in sys.argv:
        validate_dependencies();resources();print('PASS resource compilation; not a complete installable APK.');return
    java=jdk()
    version=run([java/'java.exe','-version']);(BUILD/'jdk-version.txt').write_text(version,encoding='utf-8')
    if '--skip-tests' in sys.argv:
        log=BUILD/'core-tests.txt'
        inputs=list((ROOT/'app/src/main/java/org/qingyue/rope/core').glob('*.java'))+[ROOT/'tests/CoreTests.java']
        assert log.is_file() and 'assertions passed.' in log.read_text('utf-8'),'No passed core-test result to reuse'
        assert all(p.stat().st_mtime_ns<=log.stat().st_mtime_ns for p in inputs),'Core sources changed; rerun tests'
        print('Reusing just-passed core tests; core/test source files are unchanged.')
    else:tests(java)
    if '--test-only' in sys.argv:return
    validate_dependencies();gen=resources();input_hashes=runtime_inputs()
    aapt=TOOLS/'aapt2/aapt2.exe';api=TOOLS/'android-all.jar'
    classes=BUILD/'classes';dex=BUILD/'dex'
    for directory in (classes,dex):clean_dir(directory)
    cp=[api,TOOLS/'litert/classes.jar',TOOLS/'litert-api/classes.jar']
    sources=sorted((ROOT/'app/src/main/java').rglob('*.java'))+sorted(gen.rglob('*.java'))
    compile_java(java,sources,classes,cp)
    # A JAR is a ZIP of class files. Write it directly inside the workspace:
    # the JDK jar tool otherwise moves its temporary file across Windows sandbox roots.
    with zipfile.ZipFile(BUILD/'app-classes.jar','w',zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(classes.rglob('*.class')):archive.write(file,file.relative_to(classes).as_posix())
    run([java/'java.exe','-cp',TOOLS/'r8.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',api,'--output',dex,
         BUILD/'app-classes.jar',TOOLS/'litert/classes.jar',TOOLS/'litert-api/classes.jar'])
    unsigned=BUILD/'qingyue-unsigned.apk'
    with zipfile.ZipFile(unsigned,'w') as z:
        with zipfile.ZipFile(BUILD/'resources.apk') as resource_archive:
            for name in resource_archive.namelist():add_aligned(z,name,resource_archive.read(name))
        for file in sorted(dex.glob('*.dex')):add_aligned(z,file.name,file.read_bytes())
        for file in sorted((TOOLS/'litert/jni').glob('*/*.so')):
            add_aligned(z,'lib/'+file.parent.name+'/'+file.name,file.read_bytes(),16384)
    sign=BUILD/'sign';clean_dir(sign);compile_java(java,[ROOT/'scripts/SignAndVerify.java'],sign,[TOOLS/'apksig.jar'])
    apk=DIST/('qingyue-rope-'+app_version()+'-test.apk')
    verify=run([java/'java.exe','-cp',str(sign)+os.pathsep+str(TOOLS/'apksig.jar'),'SignAndVerify','sign',unsigned,apk,TOOLS/'testkey.pk8',TOOLS/'testkey.x509.pem'])
    (BUILD/'apk-signature.txt').write_text(verify,encoding='utf-8');verify_package(apk)
    badging=run([aapt,'dump','badging',apk]);(BUILD/'apk-badging.txt').write_text(badging,encoding='utf-8')
    permissions=run([aapt,'dump','permissions',apk]);(BUILD/'apk-permissions.txt').write_text(permissions,encoding='utf-8')
    assert 'android.permission.INTERNET' not in permissions and 'android.permission.CAMERA' in permissions
    checksum=hashlib.sha256(apk.read_bytes()).hexdigest();(DIST/('SHA256SUMS-'+app_version()+'.txt')).write_text(checksum+'  '+apk.name+'\n',encoding='utf-8')
    assert runtime_inputs()==input_hashes,'Runtime source changed during compilation'
    (BUILD/'build-inputs.json').write_text(json.dumps({'version':app_version(),'source_files':input_hashes,'apk_sha256':checksum,
        'dex_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(dex.glob('*.dex'))},
        'app_classes_jar_sha256':hashlib.sha256((BUILD/'app-classes.jar').read_bytes()).hexdigest()},indent=2)+'\n',encoding='utf-8')
    print(f'BUILT {apk.name}: {apk.stat().st_size} bytes SHA256 {checksum}')

if __name__=='__main__':main()
