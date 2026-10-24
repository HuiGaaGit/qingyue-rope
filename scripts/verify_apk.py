"""Independent APK container/Dex/native audit. Python standard library only."""
from pathlib import Path
import hashlib,json,struct,sys,zipfile,zlib
from verify_static import elf_info
from build import app_version

ROOT=Path(__file__).resolve().parents[1]

def dex_classes(data):
    assert data.startswith(b'dex\n') and data[7]==0
    assert struct.unpack_from('<I',data,8)[0]==zlib.adler32(data[12:])&0xffffffff
    assert data[12:32]==hashlib.sha1(data[32:]).digest()
    string_count,string_offset=struct.unpack_from('<II',data,56)
    type_count,type_offset=struct.unpack_from('<II',data,64)
    class_count,class_offset=struct.unpack_from('<II',data,96)
    def string(index):
        assert index<string_count
        offset=struct.unpack_from('<I',data,string_offset+index*4)[0]
        while data[offset]&128:offset+=1
        offset+=1;end=data.index(0,offset)
        return data[offset:end].decode('utf-8',errors='replace')
    descriptors=[]
    for i in range(class_count):
        type_index=struct.unpack_from('<I',data,class_offset+i*32)[0];assert type_index<type_count
        descriptors.append(string(struct.unpack_from('<I',data,type_offset+type_index*4)[0]))
    return descriptors

def main():
    apk=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'dist'/('qingyue-rope-'+app_version()+'-test.apk')
    data=apk.read_bytes();report={'apk':apk.name,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'classes':0,'native':{},'permissions':'See aapt2 dump log, not inferred from source'}
    expected={'armeabi-v7a':(32,40),'arm64-v8a':(64,183),'x86':(32,3),'x86_64':(64,62)}
    with zipfile.ZipFile(apk) as z:
        assert z.testzip() is None
        names=z.namelist();assert len(set(names))==len(names)
        assert 'AndroidManifest.xml' in names and 'resources.arsc' in names
        model=z.read('assets/movenet.tflite');assert hashlib.sha256(model).hexdigest()=='0fac2226112d0371903ca86e3853cec24ef603a0b2f96f589b180f0ebdd135ab'
        assert z.read('assets/THIRD_PARTY_NOTICES.md')==(ROOT/'THIRD_PARTY_NOTICES.md').read_bytes()
        assert z.read('assets/LiteRT-LICENSE.txt')==(ROOT/'licenses/LiteRT-LICENSE.txt').read_bytes()
        for item in z.infolist():
            name=item.filename
            assert not name.endswith(('.class','.java','.jar','.pk8','.pem')),'Unwanted build input in APK: '+name
            if name.endswith('.dex'):
                classes=dex_classes(z.read(name));report['classes']+=len(classes)
                assert any(c.startswith('Lorg/qingyue/rope/') for c in classes)
                assert not any(c.startswith(('Landroid/','Ljava/','Ljavax/','Ldalvik/','Lsun/')) for c in classes),'Android/JDK framework class packaged'
            if name.endswith('.so'):
                _,abi,_=name.split('/');assert abi in expected
                info=elf_info(z.read(name));assert (info['bits'],info['machine'])==expected[abi]
                assert all(a>=16384 for a in info['load_alignments']);report['native'][abi]=info
                assert set(info['needed'])<={'libc.so','libm.so','libdl.so','liblog.so','libandroid.so','libz.so','libjnigraphics.so'},info['needed']
            if name=='resources.arsc' or name.endswith(('.tflite','.so')):
                base=item.header_offset;n,e=struct.unpack_from('<HH',data,base+26);offset=base+30+n+e
                assert offset%(16384 if name.endswith('.so') else 4)==0
                assert item.compress_type==zipfile.ZIP_STORED
        assert set(report['native'])==set(expected)
    print(json.dumps(report,indent=2));print('PASS APK CRC/Dex checksums, no Android/JDK classes, model hash, licenses, ABI, ELF and ZIP alignment.')
    (ROOT/'build/apk-audit.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':main()
