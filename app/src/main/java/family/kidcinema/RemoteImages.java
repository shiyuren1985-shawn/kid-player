package family.kidcinema;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Bounded, optional image cache. Only platform image hosts, including redirects. */
final class RemoteImages {
    private static final ExecutorService IO=Executors.newFixedThreadPool(3);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(12*1024*1024) {
        protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    static void load(ImageView view,String url) {
        view.setTag(url);
        if(url==null || url.isEmpty())return;
        try{BiliPolicy.imageUrl(url);}catch(Exception ignored){return;}
        Bitmap ready=MEMORY.get(url);if(ready!=null){view.setImageBitmap(ready);return;}
        WeakReference<ImageView> target=new WeakReference<>(view);Context context=view.getContext().getApplicationContext();
        IO.execute(()->{
            try {
                byte[] data=bytes(context,url);
                BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,size);
                if(size.outWidth<=0 || size.outHeight<=0 || size.outWidth>16000 || size.outHeight>16000)return;
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                while(size.outWidth/options.inSampleSize>800 || size.outHeight/options.inSampleSize>800)options.inSampleSize*=2;
                Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);if(bitmap==null)return;MEMORY.put(url,bitmap);
                MAIN.post(()->{ImageView image=target.get();if(image!=null && url.equals(image.getTag()))image.setImageBitmap(bitmap);});
            }catch(Exception ignored){/* A missing thumbnail must never block browsing or playback. */}
        });
    }
    private static byte[] bytes(Context context,String source) throws Exception {
        File directory=new File(context.getCacheDir(),"creator-images");directory.mkdirs();
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();
        for(byte b:digest)key.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        File file=new File(directory,key.toString());
        if(file.isFile() && file.length()<=2_000_000 && System.currentTimeMillis()-file.lastModified()<7*86400000L)return java.nio.file.Files.readAllBytes(file.toPath());
        String url=BiliPolicy.imageUrl(source);
        for(int i=0;i<4;i++) {
            HttpURLConnection connection=(HttpURLConnection)new URL(BiliPolicy.imageUrl(url)).openConnection();
            connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);
            try {
                int code=connection.getResponseCode();
                if(code>=300 && code<400){String next=connection.getHeaderField("Location");if(next==null)throw new IOException();url=new URL(new URL(url),next).toString();continue;}
                if(code!=200)throw new IOException();
                String type=connection.getContentType();if(type==null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("image/"))throw new IOException();
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>2_000_000)throw new IOException();out.write(buffer,0,n);}
                    byte[] data=out.toByteArray();trim(directory);
                    File temporary=File.createTempFile("image-",".tmp",directory);
                    try{java.nio.file.Files.write(temporary.toPath(),data);java.nio.file.Files.move(temporary.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}finally{temporary.delete();}
                    return data;
                }
            }finally{connection.disconnect();}
        }
        throw new IOException();
    }
    private static synchronized void trim(File directory) {
        File[] files=directory.listFiles();if(files==null)return;
        java.util.Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));long size=0;
        for(File file:files)size+=file.length();
        for(File file:files){if(size<24_000_000)break;long length=file.length();if(file.delete())size-=length;}
    }
}
