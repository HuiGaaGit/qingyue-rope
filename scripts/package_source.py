"""Versioned source delivery from a positive allowlist; no tools, keys or private logs."""
from pathlib import Path
import hashlib,json,shutil,zipfile
from build import app_version
ROOT=Path(__file__).resolve().parents[1]
DIST=ROOT/'dist'

def main():
    version=app_version();DIST.mkdir(exist_ok=True)
    required=['README.md','LICENSE','NOTICE','THIRD_PARTY_NOTICES.md','dependencies.lock.json','settings.gradle','build.gradle','.gitignore','TEST_REPORT.md','app/build.gradle']
    scripts=['bootstrap.py','build.py','fetch_temurin.py','inspect_model.py','verify_apk.py','verify_static.py','test_image_pipeline.py','SignAndVerify.java','package_source.py']
    required+=['scripts/'+name for name in scripts]
    required+=['docs/'+name for name in ['DESIGN.md','DEVICE_ACCEPTANCE.md','RECOGNITION_FIX.md']]
    files=[ROOT/name for name in required]
    for directory in ('app/src','tests','licenses'):
        files.extend(p for p in (ROOT/directory).rglob('*') if p.is_file())
    assert all(p.is_file() for p in files)
    for p in files:
        assert p.suffix.lower() not in ('.pk8','.pem','.apk','.exe','.dll','.jar','.zip','.jpg','.png','.jpeg','.mp4','.so','.mnn','.onnx','.pb','.pt','.param','.weights'),p.name
        if p.suffix.lower()=='.tflite':assert p.relative_to(ROOT).as_posix()=='app/src/main/assets/movenet.tflite',p.name
        # This packager's policy literals are expected; scan the actual deliverable content.
        if p.name!='package_source.py' and p.suffix.lower() in ('.md','.txt','.json','.java','.py','.gradle','.xml'):
            data=p.read_bytes()
            for private in (b'xwechat_files',b'pan.quark.cn/s/',b'd.apkpure.com/b/',b'local-apk-provenance',b'STATIC_RESEARCH_SUMMARY'):
                assert private not in data,'Private research reference in source delivery: '+p.name
    evidence_names=['static-checks.json','core-tests.txt','apk-signature.txt','apk-badging.txt','apk-permissions.txt','apk-audit.json','image-pipeline-tests.json','gap-regressions-before.txt','build-inputs.json']
    evidence={name:(ROOT/'build'/name).read_bytes() for name in evidence_names}
    built=json.loads(evidence['build-inputs.json'])
    assert built['version']==version
    assert all(hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==h for name,h in built['source_files'].items()),'Source differs from compiled inputs'
    assert hashlib.sha256((DIST/('qingyue-rope-'+version+'-test.apk')).read_bytes()).hexdigest()==built['apk_sha256'],'APK differs from build record'
    for name,data in evidence.items():
        assert b'C:\\Users\\' not in data and b'C:\\\\Users\\\\' not in data,name
    archive=DIST/('qingyue-rope-source-'+version+'.zip')
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in sorted(set(files)):z.write(p,'qingyue-rope/'+p.relative_to(ROOT).as_posix())
        for name,data in evidence.items():z.writestr('qingyue-rope/docs/test-evidence/'+name,data)
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        assert not any(part in n for n in z.namelist() for part in ('/.tools/','/.publication/','/.git/','CHECKPOINT','ENVIRONMENT','RECOGNITION_PROGRESS','build-log'))
    report=DIST/('TEST_REPORT-'+version+'.md');shutil.copyfile(ROOT/'TEST_REPORT.md',report)
    acceptance=DIST/('DEVICE_ACCEPTANCE-'+version+'.md');shutil.copyfile(ROOT/'docs/DEVICE_ACCEPTANCE.md',acceptance)
    repair=DIST/('RECOGNITION_FIX-'+version+'.md');shutil.copyfile(ROOT/'docs/RECOGNITION_FIX.md',repair)
    outputs=[DIST/('qingyue-rope-'+version+'-test.apk'),archive,report,acceptance,repair]
    lines=[]
    for p in outputs:
        sha=hashlib.sha256(p.read_bytes()).hexdigest();lines.append(sha+'  '+p.name);print(p.name,p.stat().st_size,sha)
    (DIST/('SHA256SUMS-'+version+'.txt')).write_text('\n'.join(lines)+'\n',encoding='utf-8')
    manifest={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    (ROOT/'build/source-package-audit.json').write_text(json.dumps({'version':version,'source_files':manifest,'evidence':evidence_names,'zip_entries':len(files)+len(evidence),'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()},indent=2)+'\n',encoding='utf-8')
    print('PASS allowlist source ZIP; JDK/tools/keys/photos/screenshots/other projects excluded.')

if __name__=='__main__':main()
