"""Static inspection of the shipped TFLite FlatBuffer, with Python standard library only."""
from pathlib import Path
import hashlib,json,struct

def inspect(path):
    data=Path(path).read_bytes()
    if data[4:8]!=b'TFL3':raise ValueError('Not TFLite')
    u32=lambda p:struct.unpack_from('<I',data,p)[0]
    i32=lambda p:struct.unpack_from('<i',data,p)[0]
    u16=lambda p:struct.unpack_from('<H',data,p)[0]
    def field(table,index):
        vt=table-i32(table);entry=vt+4+index*2
        offset=u16(entry) if entry<vt+u16(vt) else 0
        return table+offset if offset else 0
    def ref(position):return position+u32(position)
    def vector(table,index):
        pos=field(table,index)
        if not pos:return (0,0)
        pos=ref(pos);return pos+4,u32(pos)
    def shape(table):
        pos,n=vector(table,0);return [i32(pos+4*i) for i in range(n)]
    root=u32(0);graphs,n=vector(root,2);assert n==1
    graph=ref(graphs);tensors,nt=vector(graph,0)
    def tensor(index):
        assert 0<=index<nt
        t=ref(tensors+4*index);kind=field(t,1)
        return {'shape':shape(t),'type':data[kind] if kind else 0}
    inputs,ni=vector(graph,1);outputs,no=vector(graph,2)
    assert ni==1 and no==1
    result={'input':tensor(i32(inputs)),'output':tensor(i32(outputs)), 'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
    assert result['input']['shape']==[1,192,192,3]
    assert result['input']['type'] in (2,3), 'Expected INT32 or UINT8 RGB input'
    assert result['output']=={'shape':[1,1,17,3],'type':0}, 'Expected FLOAT32 MoveNet output'
    return result

if __name__=='__main__':
    path=Path(__file__).resolve().parents[1]/'app/src/main/assets/movenet.tflite'
    print(json.dumps(inspect(path),indent=2))
    print('PASS static model format/signature. No inference or human accuracy measured.')
