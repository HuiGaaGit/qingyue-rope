"""Source manifest, model and native-ABI validation. Does not execute downloaded software."""
from pathlib import Path
import hashlib,json,struct,xml.etree.ElementTree as ET
from inspect_model import inspect

ROOT=Path(__file__).resolve().parents[1]
ANDROID='{http://schemas.android.com/apk/res/android}'

def elf_info(data):
    assert data[:4]==b'\x7fELF'
    assert data[5]==1,'Expected little-endian ELF'
    bits=data[4]
    machine=struct.unpack_from('<H',data,18)[0]
    if bits==2:offset=struct.unpack_from('<Q',data,32)[0];size,count=struct.unpack_from('<HH',data,54);fmt='<IIQQQQQQ'
    else:offset=struct.unpack_from('<I',data,28)[0];size,count=struct.unpack_from('<HH',data,42);fmt='<IIIIIIII'
    headers=[struct.unpack_from(fmt,data,offset+i*size) for i in range(count)]
    aligns=[h[-1] for h in headers if h[0]==1]
    segments=[(h[2],h[3],h[5]) if bits==2 else (h[1],h[2],h[4]) for h in headers if h[0]==1]
    dynamic=[h for h in headers if h[0]==2];needed=[];string_va=None
    if dynamic:
        h=dynamic[0];start,length=(h[2],h[5]) if bits==2 else (h[1],h[4]);step=16 if bits==2 else 8
        for cursor in range(start,start+length,step):
            tag,value=struct.unpack_from('<qQ' if bits==2 else '<iI',data,cursor)
            if tag==0:break
            if tag==1:needed.append(value)
            if tag==5:string_va=value
    dependencies=[]
    if needed:
        table=next(file_offset+string_va-virtual for file_offset,virtual,length in segments if virtual<=string_va<virtual+length)
        for index in needed:
            at=table+index;dependencies.append(data[at:data.index(0,at)].decode('ascii'))
    return {'bits':bits*32,'machine':machine,'load_alignments':aligns,'needed':dependencies}

def main():
    manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
    permissions=[p.attrib[ANDROID+'name'] for p in manifest.findall('uses-permission')]
    assert permissions==['android.permission.CAMERA']
    sdk=manifest.find('uses-sdk');assert sdk.attrib[ANDROID+'minSdkVersion']=='26';assert sdk.attrib[ANDROID+'targetSdkVersion']=='34'
    app=manifest.find('application');assert app.attrib[ANDROID+'allowBackup']=='false';assert app.attrib[ANDROID+'usesCleartextTraffic']=='false'
    for p in (ROOT/'app/src/main/res').rglob('*.xml'):ET.parse(p)
    result={'source_manifest':'PASS camera-only, min26 target34, backups disabled','model':inspect(ROOT/'app/src/main/assets/movenet.tflite'),'native':{}}
    expected={'armeabi-v7a':(32,40),'arm64-v8a':(64,183),'x86':(32,3),'x86_64':(64,62)}
    for abi,(bits,machine) in expected.items():
        p=ROOT/'.tools/litert/jni'/abi/'libtensorflowlite_jni.so';info=elf_info(p.read_bytes())
        assert (info['bits'],info['machine'])==(bits,machine)
        assert all(n>=16384 for n in info['load_alignments'])
        assert set(info['needed'])<={'libc.so','libm.so','libdl.so','liblog.so','libandroid.so','libz.so','libjnigraphics.so'},info['needed']
        result['native'][abi]=info
    print(json.dumps(result,indent=2))
    print('PASS source XML/manifest, real model signature, four native ABIs and 16KB ELF LOAD alignment.')
    print('Static checks only: no JVM test, Android installation, camera inference or human accuracy proven here.')
    (ROOT/'build').mkdir(exist_ok=True)
    (ROOT/'build/static-checks.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':main()
