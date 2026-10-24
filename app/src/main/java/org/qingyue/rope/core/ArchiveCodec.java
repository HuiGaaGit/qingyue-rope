package org.qingyue.rope.core;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

/** Versioned length-checked archive with CRC. File-level atomicity is supplied by Android AtomicFile. */
public final class ArchiveCodec {
    public static final class Archive {
        public final List<WorkoutRecord> history;
        public final byte[] active;
        public Archive(List<WorkoutRecord> history,byte[] active){this.history=Collections.unmodifiableList(new ArrayList<>(history));this.active=active;}
        public Archive checkpoint(SessionEngine e) throws IOException{return new Archive(history,e.snapshot());}
        public Archive commit(WorkoutRecord result) {
            List<WorkoutRecord> next=new ArrayList<>();next.add(result);
            for(WorkoutRecord old:history)if(!old.id.equals(result.id))next.add(old);
            return new Archive(next,null);
        }
    }
    public static byte[] encode(Archive archive) throws IOException {
        if(archive.history.size()>100000)throw new IOException("本地历史已达容量上限，原记录已保留");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x51595250);out.writeInt(1);out.writeInt(archive.history.size());
        for(WorkoutRecord r:archive.history)r.write(out);
        byte[] active=archive.active;out.writeInt(active==null?0:active.length);if(active!=null)out.write(active);out.flush();
        if(bytes.size()>32*1024*1024-8)throw new IOException("本地档案容量已满，原记录已保留");
        CRC32 crc=new CRC32();crc.update(bytes.toByteArray());out.writeLong(crc.getValue());out.close();return bytes.toByteArray();
    }
    public static Archive decode(byte[] data) throws IOException {
        if(data.length<24||data.length>32*1024*1024)throw new IOException("记录文件大小异常");
        CRC32 crc=new CRC32();crc.update(data,0,data.length-8);
        DataInputStream tail=new DataInputStream(new ByteArrayInputStream(data,data.length-8,8));
        if(crc.getValue()!=tail.readLong())throw new IOException("记录完整性校验失败，原文件已保留");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(data,0,data.length-8));
        if(in.readInt()!=0x51595250||in.readInt()!=1)throw new IOException("记录版本不支持");
        int n=in.readInt();if(n<0||n>100000)throw new IOException("记录条数异常");
        List<WorkoutRecord> history=new ArrayList<>();for(int i=0;i<n;i++)history.add(WorkoutRecord.read(in));
        int length=in.readInt();if(length<0||length>8192||length>in.available())throw new IOException("恢复数据损坏");
        byte[] active=length==0?null:new byte[length];if(active!=null)in.readFully(active);
        if(in.available()!=0)throw new IOException("记录存在多余数据");return new Archive(history,active);
    }
}
