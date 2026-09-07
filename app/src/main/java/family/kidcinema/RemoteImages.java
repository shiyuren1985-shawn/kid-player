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
import java.util.concurrent.*;

/** Bounded, optional image cache. Only platform image hosts, including redirects. */
final class RemoteImages {
    private static final ExecutorService IO=Executors.newFixedThreadPool(3);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(12*1024*1024) {
        protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    private static final java.util.concurrent.atomic.AtomicBoolean REGISTERED=new java.util.concurrent.atomic.AtomicBoolean();
    static void trimMemory(){MEMORY.evictAll();}
    static long diskBytes(Context context){File[] files=new File(context.getCacheDir(),"creator-images").listFiles();long size=0;if(files!=null)for(File f:files)size+=f.length();return size;}
    static void clear(Context context)throws IOException{trimMemory();File[] files=new File(context.getCacheDir(),"creator-images").listFiles();if(files!=null)for(File f:files)if(!f.delete()&&f.exists())throw new IOException("Image cache could not be removed");}
    static void load(ImageView view,String url) {
        if(REGISTERED.compareAndSet(false,true))view.getContext().getApplicationContext().registerComponentCallbacks(new android.content.ComponentCallbacks2(){
            public void onConfigurationChanged(android.content.res.Configuration config){}
            public void onLowMemory(){trimMemory();}
            public void onTrimMemory(int level){if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)trimMemory();}
        });
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
        return new ImageDiskCache(new File(context.getCacheDir(),"creator-images"),24_000_000,2_000_000)
            .get(source,()->download(source),RemoteImages::validImage);
    }
    private static boolean validImage(byte[] data){
        BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,size);
        if(size.outWidth<=0||size.outHeight<=0||size.outWidth>16000||size.outHeight>16000)return false;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
        while(size.outWidth/options.inSampleSize>800||size.outHeight/options.inSampleSize>800)options.inSampleSize*=2;
        Bitmap decoded=BitmapFactory.decodeByteArray(data,0,data.length,options);
        if(decoded==null)return false;decoded.recycle();return true;
    }
    private static byte[] download(String source)throws Exception {
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
                    return out.toByteArray();
                }
            }finally{connection.disconnect();}
        }
        throw new IOException();
    }
}
