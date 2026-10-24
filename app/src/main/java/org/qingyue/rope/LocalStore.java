package org.qingyue.rope;

import android.content.Context;
import android.util.AtomicFile;
import org.qingyue.rope.core.*;
import java.io.*;
import java.util.*;

/** All mutations run on the activity thread; publish in-memory state only after fsync+atomic replace. */
public final class LocalStore {
    private final AtomicFile file;
    private ArchiveCodec.Archive archive=new ArchiveCodec.Archive(Collections.emptyList(),null);
    private IOException loadError;
    public LocalStore(Context context) {
        file=new AtomicFile(new File(context.getFilesDir(),"workouts-v1.bin"));
        try { if(file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists())archive=ArchiveCodec.decode(file.readFully()); }
        catch(IOException error){loadError=error;}
    }
    public List<WorkoutRecord> history(){return archive.history;}
    public boolean blocked(){return loadError!=null;}
    public String error(){return loadError==null?"":loadError.getMessage();}
    public SessionEngine recover(long now)throws IOException {
        try{return archive.active==null?null:SessionEngine.restore(archive.active,now);}
        catch(IOException error){loadError=error;throw error;}
    }
    public void checkpoint(SessionEngine e)throws IOException{write(archive.checkpoint(e));}
    public void commit(SessionEngine e)throws IOException{write(archive.commit(new WorkoutRecord(e)));}
    public void discardActive()throws IOException{write(new ArchiveCodec.Archive(archive.history,null));}
    public void clearHistory()throws IOException{write(new ArchiveCodec.Archive(Collections.emptyList(),archive.active));}
    private void write(ArchiveCodec.Archive next)throws IOException {
        if(loadError!=null)throw new IOException("本地原文件读取异常，已保护，禁止覆盖："+loadError.getMessage());
        byte[] data=ArchiveCodec.encode(next);FileOutputStream stream=null;
        try {stream=file.startWrite();stream.write(data);stream.getFD().sync();file.finishWrite(stream);stream=null;
            // AtomicFile may log rather than throw on a failed final rename. Verify before
            // publishing success so the UI never reports a save solely from that return.
            if(!Arrays.equals(file.readFully(),data))throw new IOException("本地写入后校验失败");
            archive=next;}
        catch(IOException error){if(stream!=null)file.failWrite(stream);throw error;}
    }
}
