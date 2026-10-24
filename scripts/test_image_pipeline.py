"""Real still-image graph tests on the desktop, not Android or human jumping accuracy."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.tools/inference-libs'))
import numpy as np
from PIL import Image,ImageDraw
from ai_edge_litert.interpreter import Interpreter
import build

def main():
    build.BUILD.mkdir(exist_ok=True)
    folder=ROOT/'.tools/image-fixtures';source=folder/'tf-pose.jpg'
    expected='c8a830ed683c0276d713dd5aeda28f415f10cd6291972084a40d0d8b934ed62b'
    assert hashlib.sha256(source.read_bytes()).hexdigest()==expected,'Use the documented official TensorFlow fixture'
    original=Image.open(source).convert('RGB');fixtures={'original':original}
    for name,ratio,offset in [('portrait',.94,.18),('far',.65,.30),('near_feet',.94,.47)]:
        canvas=Image.new('RGB',(480,640),(125,125,125));w=round(480*ratio);h=round(original.height*w/original.width)
        canvas.paste(original.resize((w,h)),((480-w)//2,round((640-h)*offset)));fixtures[name]=canvas
    fixtures['blank']=Image.new('RGB',(480,640),(125,125,125))
    for name,image in fixtures.items():image.save(folder/(name+'.png'))
    java=build.jdk();out=build.BUILD/'image-tests';build.clean_dir(out)
    build.compile_java(java,sorted((ROOT/'app/src/main/java/org/qingyue/rope/core').glob('*.java'))+[ROOT/'tests/ImagePipelineTool.java'],out)
    process=subprocess.Popen([str(java/'java.exe'),'-cp',str(out),'ImagePipelineTool'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,encoding='utf-8')
    def command(*args):
        process.stdin.write('\t'.join(str(x) for x in args)+'\n');process.stdin.flush();line=process.stdout.readline()
        if not line:raise RuntimeError('Java image bridge stopped')
        return json.loads(line)
    interpreter=Interpreter(model_path=str(ROOT/'app/src/main/assets/movenet.tflite'),num_threads=2);interpreter.allocate_tensors()
    inp=interpreter.get_input_details()[0];output=interpreter.get_output_details()[0]
    assert inp['dtype']==np.uint8 and inp['shape'].tolist()==[1,192,192,3]
    assert output['dtype']==np.float32 and output['shape'].tolist()==[1,1,17,3]
    rgb=out/'input.rgb';prediction=out/'prediction.f32'
    def inference(crop,t):
        command('prepare',*crop,rgb)
        tensor=np.frombuffer(rgb.read_bytes(),dtype=np.uint8).reshape(1,192,192,3)
        start=time.perf_counter();interpreter.set_tensor(inp['index'],tensor);interpreter.invoke();ms=(time.perf_counter()-start)*1000
        points=interpreter.get_tensor(output['index']).reshape(17,3);prediction.write_bytes(points.astype('>f4').tobytes())
        result=command('step',*crop,t,prediction);result['desktop_inference_ms']=round(ms,3);return result
    results={}
    try:
        for name in fixtures:
            dim=command('image',folder/(name+'.png'));command('reset');size=max(dim.values());crop=[(dim['width']-size)/2,(dim['height']-size)/2,size]
            result=inference(crop,0);results[name]=result
            if name=='blank':assert not result['person'],'Blank must not acquire a torso'
            else:assert result['person'] and result['countable'],f'{name}: actual image should acquire person and feet'
        assert not results['portrait']['old_gate'] and not results['far']['old_gate'],'Reproduce old minimum-height rejection'
        dim=command('image',folder/'portrait.png');command('reset');crop=[-80,0,640];count=0;states=[]
        for i in range(75):
            result=inference(crop,i*67);count+=int(result['jump']);crop=result['crop'];states.append({k:result[k] for k in ('countable','ready','height','crop','status')})
        assert count==0,'Changing crop on a still real photo must not create a jump'
        assert any(x['ready'] for x in states),'Still real photo must reach calibrated state'
        report={'scope':'Desktop native LiteRT 2.3.0, real still photograph and synthetic canvas variants; NOT phone, NOT real jumping accuracy',
                'fixture_source':'https://raw.githubusercontent.com/tensorflow/tfjs-models/master/pose-detection/test_data/pose.jpg','fixture_sha256':expected,
                'model_sha256':hashlib.sha256((ROOT/'app/src/main/assets/movenet.tflite').read_bytes()).hexdigest(),
                'input':{'dtype':'UINT8','shape':inp['shape'].tolist()},'output':{'dtype':'FLOAT32','shape':output['shape'].tolist()},
                'fixtures':results,'dynamic_crop_still':{'frames':75,'jumps':count,'ready_frames':sum(x['ready'] for x in states),'states':states}}
        (build.BUILD/'image-pipeline-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print('PASS real photo 4 placements + blank, actual native graph with shared Java preprocessing; 75 still-image dynamic crop frames, 0 jumps.')
    finally:
        process.stdin.close();process.wait(timeout=10)
if __name__=='__main__':main()
