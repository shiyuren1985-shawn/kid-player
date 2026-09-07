package family.kidcinema;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PlaybackCacheTest {
    private SimpleCache cache;private File folder;
    @Before public void before()throws Exception{android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();folder=Files.createTempDirectory(context.getCacheDir().toPath(),"media-test-").toFile();cache=new SimpleCache(folder,new LeastRecentlyUsedCacheEvictor(8192),new StandaloneDatabaseProvider(context));cache.checkInitialization();}
    @After public void after(){cache.release();delete(folder);}
    private void delete(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)delete(c);f.delete();}
    private byte[] read(DataSource.Factory factory,String url,int position,int length)throws Exception{
        DataSource source=factory.createDataSource();try{source.open(new DataSpec.Builder().setUri(url).setPosition(position).setLength(length).build());ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=source.read(b,0,b.length))!=C.RESULT_END_OF_INPUT)out.write(b,0,n);return out.toByteArray();}finally{source.close();}
    }
    @Test public void replayAndSeekReadCachedBytesWithoutNetwork()throws Exception{
        AtomicInteger opens=new AtomicInteger();byte[] data=new byte[4096];for(int i=0;i<data.length;i++)data[i]=(byte)i;
        DataSource.Factory factory=PlaybackCache.factory(cache,()->{opens.incrementAndGet();return new ByteArrayDataSource(data);});
        assertArrayEquals(data,read(factory,"https://test.bilivideo.com/a",0,4096));
        DataSource.Factory offline=PlaybackCache.factory(cache,()->new BaseDataSource(true){public long open(DataSpec spec)throws IOException{throw new IOException("network unavailable");}public int read(byte[] b,int o,int n)throws IOException{throw new IOException();}public Uri getUri(){return null;}public void close(){}});
        assertArrayEquals(java.util.Arrays.copyOfRange(data,1024,2048),read(offline,"https://test.bilivideo.com/a",1024,1024));
        assertArrayEquals(data,read(offline,"https://test.bilivideo.com/a",0,4096));assertTrue(cache.getCacheSpace()>=4096);
    }
    @Test public void cachedMp4DecodesAndPlaysWithUpstreamUnavailable()throws Exception{
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        cache.release();cache=new SimpleCache(folder,new LeastRecentlyUsedCacheEvictor(8*1024*1024),new StandaloneDatabaseProvider(context));cache.checkInitialization();
        byte[] mp4;try(InputStream in=context.getResources().openRawResource(R.raw.demo_space);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);mp4=out.toByteArray();}
        String url="https://fixture.bilivideo.com/cache-demo.mp4";read(PlaybackCache.factory(cache,()->new ByteArrayDataSource(mp4)),url,0,mp4.length);
        AtomicInteger opens=new AtomicInteger();DataSource.Factory offline=PlaybackCache.factory(cache,()->new BaseDataSource(true){public long open(DataSpec spec)throws IOException{opens.incrementAndGet();throw new IOException("upstream disabled");}public int read(byte[] b,int o,int n)throws IOException{throw new IOException();}public Uri getUri(){return null;}public void close(){}});
        androidx.media3.exoplayer.ExoPlayer[] player={null};androidx.media3.exoplayer.video.PlaceholderSurface[] surface={null};boolean[] played={false};
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{surface[0]=androidx.media3.exoplayer.video.PlaceholderSurface.newInstanceV17(context,false);player[0]=new androidx.media3.exoplayer.ExoPlayer.Builder(context).build();player[0].setVideoSurface(surface[0]);player[0].setMediaSource(new androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(offline).createMediaSource(androidx.media3.common.MediaItem.fromUri(url)));player[0].prepare();player[0].play();});
            long deadline=android.os.SystemClock.elapsedRealtime()+15000;
            while(!played[0]&&android.os.SystemClock.elapsedRealtime()<deadline){Thread.sleep(100);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->played[0]=player[0].getPlayerError()==null&&player[0].getCurrentPosition()>1000&&player[0].getVideoDecoderCounters()!=null&&player[0].getVideoDecoderCounters().renderedOutputBufferCount>0);}
            assertTrue("Cached MP4 must actually decode video and advance",played[0]);assertEquals("No upstream open allowed",0,opens.get());
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{if(player[0]!=null)player[0].release();if(surface[0]!=null)surface[0].release();});}
    }
    @Test public void signedUrlsNeverShareDifferentRepresentationsAndLruStaysBounded()throws Exception{
        byte[] first=new byte[6000],second=new byte[6000];java.util.Arrays.fill(first,(byte)1);java.util.Arrays.fill(second,(byte)2);
        read(PlaybackCache.factory(cache,()->new ByteArrayDataSource(first)),"https://test.bilivideo.com/a?signature=old",0,6000);
        assertArrayEquals(second,read(PlaybackCache.factory(cache,()->new ByteArrayDataSource(second)),"https://test.bilivideo.com/a?signature=new",0,6000));
        assertTrue(cache.getCacheSpace()<=8192);assertFalse(cache.isCached("https://test.bilivideo.com/a?signature=old",0,6000));
    }
}
