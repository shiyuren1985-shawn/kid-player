package family.kidcinema;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** URL-addressed cache: unchanged URLs survive restarts; least-recently-used files are evicted. */
final class ImageDiskCache {
    interface Fetch { byte[] get() throws Exception; }
    interface Validate { boolean valid(byte[] data); }
    private static final Object[] LOCKS=new Object[32];
    private static final Object WRITES=new Object();
    static {for(int i=0;i<LOCKS.length;i++)LOCKS[i]=new Object();}
    private final File directory;
    private final long capacity;
    private final int maxEntry;
    ImageDiskCache(File directory,long capacity,int maxEntry){this.directory=directory;this.capacity=capacity;this.maxEntry=maxEntry;}
    byte[] get(String url,Fetch fetch,Validate validate)throws Exception {
        StringBuilder key=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)))key.append(String.format(Locale.ROOT,"%02x",b&255));
        File file=new File(directory,key.toString());
        synchronized(LOCKS[(file.getAbsolutePath().hashCode()&0x7fffffff)%LOCKS.length]) {
            if(file.isFile()&&file.length()>0&&file.length()<=maxEntry){
                try{byte[] data=Files.readAllBytes(file.toPath());if(validate.valid(data)){file.setLastModified(System.currentTimeMillis());return data;}}catch(IOException ignored){}
            }
            byte[] data=fetch.get();
            if(data==null||data.length==0||data.length>maxEntry||!validate.valid(data))throw new IOException("Invalid image");
            // A full/unavailable disk must not prevent showing a downloaded image.
            try{synchronized(WRITES){
                if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Image cache unavailable");
                File temporary=File.createTempFile("image-",".tmp",directory);
                try{Files.write(temporary.toPath(),data);Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);trim(file);}finally{temporary.delete();}
            }}catch(IOException ignored){}
            return data;
        }
    }
    private void trim(File current){
        File[] files=directory.listFiles();if(files==null)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));long size=0;
        for(File file:files)size+=file.length();
        for(File file:files){if(size<=capacity)break;if(file.equals(current))continue;long length=file.length();if(file.delete())size-=length;}
    }
}
