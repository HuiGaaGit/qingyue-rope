"""Fetch the explicitly authorized Temurin JDK17 into this project only."""
from pathlib import Path
import argparse,hashlib,json,time,urllib.parse,urllib.request,zipfile,sys

ROOT=Path(__file__).resolve().parents[1]
TOOLS=ROOT/'.tools'
API='https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
if hasattr(sys.stdout,'reconfigure'):sys.stdout.reconfigure(encoding='utf-8')

def request(url):
    return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Qingyue-build/0.1'}),timeout=60)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--authorized-temurin',action='store_true');args=parser.parse_args()
    if not args.authorized_temurin:raise SystemExit('Explicit Temurin download/run authorization is required.')
    TOOLS.mkdir(exist_ok=True);metadata_path=TOOLS/'temurin-source.json'
    if metadata_path.exists():metadata=json.loads(metadata_path.read_text('utf-8'))
    else:
        with request(API) as stream:releases=json.load(stream)
        release=releases[0];binary=release['binary'];package=binary['package']
        assert release['version']['major']==17 and release['vendor']=='eclipse'
        assert binary['os']=='windows' and binary['architecture']=='x64' and binary['image_type']=='jdk'
        parsed=urllib.parse.urlparse(package['link'])
        assert parsed.scheme=='https' and parsed.netloc=='github.com' and parsed.path.startswith('/adoptium/temurin17-binaries/')
        metadata={'api':API,'release':release['release_name'],'version':release['version'],'package':package,'authorization':'User explicitly approved official Eclipse Temurin JDK for task-local build on 2026-10-24.'}
        metadata_path.write_text(json.dumps(metadata,indent=2)+'\n',encoding='utf-8')
    package=metadata['package'];name=package['name'];assert Path(name).name==name and name.endswith('.zip')
    path=TOOLS/name
    if not path.exists():
        print('Downloading',metadata['release'],package['size'],'bytes from official Adoptium release',flush=True)
        partial=path.with_suffix('.zip.partial');last_report=0;start=time.monotonic()
        with request(package['link']) as stream,partial.open('wb') as output:
            total=0
            while True:
                chunk=stream.read(1024*1024)
                if not chunk:break
                output.write(chunk);total+=len(chunk)
                if total-last_report>=16*1024*1024:
                    print(f'Downloaded {total//(1024*1024)} MiB',flush=True);last_report=total
        assert total==package['size'],(total,package['size']);partial.replace(path)
    actual=hashlib.sha256(path.read_bytes()).hexdigest();assert actual==package['checksum'],'Official SHA-256 mismatch'
    print('PASS official metadata SHA-256',actual,flush=True)
    # Corroborate the release checksum file, then extract only within the task tree.
    with request(package['checksum_link']) as stream:checksum_file=stream.read().decode('ascii')
    assert actual in checksum_file
    (TOOLS/(name+'.sha256.txt')).write_text(checksum_file,encoding='ascii')
    target=(TOOLS/'temurin').resolve();target.mkdir(exist_ok=True)
    with zipfile.ZipFile(path) as z:
        for member in z.infolist():
            resolved=(target/member.filename).resolve()
            if not resolved.is_relative_to(target):raise RuntimeError('Unsafe archive member')
        z.extractall(target)
    choices=list(target.glob('*/bin/java.exe'));assert len(choices)==1
    metadata['verified_sha256']=actual;metadata['jdk_directory']=str(choices[0].parent.parent)
    metadata_path.write_text(json.dumps(metadata,indent=2)+'\n',encoding='utf-8')
    print('Approved task-local JDK ready:',metadata['jdk_directory'],flush=True)

if __name__=='__main__':main()
