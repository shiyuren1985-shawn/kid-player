package family.kidcinema;

import android.content.Context;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.*;
import java.io.File;
import java.util.ArrayList;

/** Read-through media cache. Only the bytes requested by playback are stored. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackCache {
    static final long MAX_BYTES=256L*1024*1024;
    private static volatile SimpleCache cache;
    private static StandaloneDatabaseProvider database;
    private static StandaloneDatabaseProvider database(Context context){if(database==null)database=new StandaloneDatabaseProvider(context.getApplicationContext());return database;}
    private static boolean attempted;
    private PlaybackCache(){}
    // Called on the resolver executor: SimpleCache initialization must not block the UI.
    static synchronized void initialize(Context context){
        if(attempted)return;attempted=true;
        if(context.getCacheDir().getUsableSpace()<512L*1024*1024)return;
        SimpleCache created=null;
        try{
            created=new SimpleCache(new File(context.getCacheDir(),"playback-media"),new LeastRecentlyUsedCacheEvictor(MAX_BYTES),database(context));
            created.checkInitialization();cache=created;
        }catch(Exception e){if(created!=null)created.release();}
    }
    static DataSource.Factory factory(DataSource.Factory upstream){
        SimpleCache current=cache;return current==null?upstream:factory(current,upstream);
    }
    static DataSource.Factory factory(Cache cache,DataSource.Factory upstream){
        // Keep the full verified URL as the key. Different signed URLs cannot mix bytes.
        return new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }
    static synchronized long bytes(Context context){return cache==null?diskBytes(new File(context.getCacheDir(),"playback-media")):cache.getCacheSpace();}
    private static long diskBytes(File file){if(file.isFile())return file.length();File[] children=file.listFiles();long n=0;if(children!=null)for(File child:children)n+=diskBytes(child);return n;}
    static synchronized void clear(Context context)throws Exception{
        if(cache!=null)for(String key:new ArrayList<>(cache.getKeys()))cache.removeResource(key);
        else {SimpleCache.delete(new File(context.getCacheDir(),"playback-media"),database(context));attempted=false;}
    }
}
