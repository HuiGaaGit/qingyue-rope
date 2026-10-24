"""Task-local open-source build dependencies. No SDK manager/EULA or system install."""
from pathlib import Path
import concurrent.futures, hashlib, json, urllib.request, zipfile, tarfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
ARTIFACTS = {
    'android-all.jar': 'https://repo.maven.apache.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar',
    'aapt2.jar': 'https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/8.6.1-11315950/aapt2-8.6.1-11315950-windows.jar',
    'r8.jar': 'https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.6.27/r8-8.6.27.jar',
    'apksig.jar': 'https://dl.google.com/dl/android/maven2/com/android/tools/build/apksig/8.6.1/apksig-8.6.1.jar',
    'litert.aar': 'https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/1.4.0/litert-1.4.0.aar',
    'litert-api.aar': 'https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert-api/1.4.0/litert-api-1.4.0.aar',
    'litert.pom': 'https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/1.4.0/litert-1.4.0.pom',
    'litert-api.pom': 'https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert-api/1.4.0/litert-api-1.4.0.pom',
    'aapt2.pom': 'https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/8.6.1-11315950/aapt2-8.6.1-11315950.pom',
    'r8.pom': 'https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.6.27/r8-8.6.27.pom',
    'apksig.pom': 'https://dl.google.com/dl/android/maven2/com/android/tools/build/apksig/8.6.1/apksig-8.6.1.pom',
    'android-all.pom': 'https://repo.maven.apache.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.pom',
    'kaggle-model-f16.tar.gz': 'https://www.kaggle.com/api/v1/models/google/movenet/tflite/singlepose-lightning-tflite-float16/1/download',
    'testkey.pk8': 'https://raw.githubusercontent.com/aosp-mirror/platform_build/android-14.0.0_r1/target/product/security/testkey.pk8',
    'testkey.x509.pem': 'https://raw.githubusercontent.com/aosp-mirror/platform_build/android-14.0.0_r1/target/product/security/testkey.x509.pem',
    'Apache-2.0.txt': 'https://www.apache.org/licenses/LICENSE-2.0.txt',
    'movenet-model-card.pdf': 'https://storage.googleapis.com/movenet/MoveNet.SinglePose%20Model%20Card.pdf',
}

def fetch(item):
    name, url = item
    path = TOOLS / name
    if not path.exists():
        req = urllib.request.Request(url, headers={'User-Agent':'kagglehub/0.3.10' if 'kaggle.com' in url else 'Qingyue-build/0.1'})
        with urllib.request.urlopen(req, timeout=120) as stream:
            data = stream.read()
        path.write_bytes(data)
    sha = hashlib.sha256(path.read_bytes()).hexdigest()
    print(f'{name}: {path.stat().st_size} bytes SHA256 {sha}', flush=True)
    return name, {'url':url, 'sha256':sha, 'bytes':path.stat().st_size}

def main():
    TOOLS.mkdir(exist_ok=True)
    lock_path = ROOT / 'dependencies.lock.json'
    previous = json.loads(lock_path.read_text()) if lock_path.exists() else {}
    results, errors = {}, []
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures = {pool.submit(fetch,item): item[0] for item in ARTIFACTS.items()}
        for future in concurrent.futures.as_completed(futures):
            try:
                name, info = future.result()
                if name in previous and info['sha256'] != previous[name]['sha256']:
                    raise RuntimeError(f'Checksum mismatch for {name}')
                results[name] = info
            except Exception as error:
                errors.append((futures[future],str(error)))
    if errors:
        print(json.dumps(errors,ensure_ascii=False),flush=True)
        raise SystemExit(1)
    lock_path.write_text(json.dumps(results,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    with zipfile.ZipFile(TOOLS/'aapt2.jar') as z: z.extractall(TOOLS/'aapt2')
    for name in ('litert','litert-api'):
        with zipfile.ZipFile(TOOLS/f'{name}.aar') as z: z.extractall(TOOLS/name)
    with tarfile.open(TOOLS/'kaggle-model-f16.tar.gz','r:gz') as tar:
        models=[member for member in tar.getmembers() if member.isfile() and member.name.endswith('.tflite')]
        if len(models)!=1:raise RuntimeError('Expected exactly one official TFLite model')
        data=tar.extractfile(models[0]).read()
        if data[4:8]!=b'TFL3':raise RuntimeError('Not a TFLite model')
        (TOOLS/'movenet.tflite').write_bytes(data)
    asset = ROOT/'app/src/main/assets'
    asset.mkdir(parents=True,exist_ok=True)
    (asset/'movenet.tflite').write_bytes((TOOLS/'movenet.tflite').read_bytes())
    legal = ROOT/'licenses';legal.mkdir(exist_ok=True)
    (legal/'Apache-2.0.txt').write_bytes((TOOLS/'Apache-2.0.txt').read_bytes())
    (legal/'MoveNet.SinglePose-Model-Card.pdf').write_bytes((TOOLS/'movenet-model-card.pdf').read_bytes())
    (legal/'LiteRT-LICENSE.txt').write_bytes((TOOLS/'litert/LICENSE').read_bytes())
    for name in ARTIFACTS:
        if name.endswith('.pom'):(legal/name).write_bytes((TOOLS/name).read_bytes())
    print('Task-local dependencies ready.',flush=True)

if __name__=='__main__': main()
