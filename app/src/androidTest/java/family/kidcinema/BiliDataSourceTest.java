package family.kidcinema;

import androidx.media3.datasource.DataSpec;
import org.junit.Test;
import java.io.*;
import java.net.*;
import static org.junit.Assert.*;

/** In-memory HTTP fixtures; no network, cookies or real video downloads. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class BiliDataSourceTest {
    static final String URL="https://fixture.bilivideo.com/video.mp4";
    static final class Response extends HttpURLConnection {
        int status;String range,location,type="video/mp4";byte[] bytes;boolean closed;
        Response(int status,byte[] bytes)throws Exception{super(new URL(URL));this.status=status;this.bytes=bytes;}
        public void connect(){}public void disconnect(){closed=true;}public boolean usingProxy(){return false;}
        public int getResponseCode(){return status;}public String getContentType(){return type;}public long getContentLengthLong(){return bytes.length;}
        public String getHeaderField(String name){return name.equals("Location")?location:name.equals("Content-Range")?range:null;}
        public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
    }
    @Test public void fullResponseSupportsBoundedSeek() throws Exception {
        Response reply=new Response(200,new byte[]{0,1,2,3,4,5});BiliDataSource source=new BiliDataSource(url->reply);
        try{assertEquals(3,source.open(new DataSpec.Builder().setUri(URL).setPosition(2).setLength(3).build()));
            byte[] bytes=new byte[8];assertEquals(3,source.read(bytes,0,8));assertEquals(2,bytes[0]);assertEquals(4,bytes[2]);assertEquals(-1,source.read(bytes,0,8));
            assertEquals("bytes=2-4",reply.getRequestProperty("Range"));assertEquals(BiliClient.REFERER,reply.getRequestProperty("Referer"));
        }finally{source.close();}assertTrue(reply.closed);
    }
    @Test public void partialResponseMustMatchRequestedPosition() throws Exception {
        Response reply=new Response(206,new byte[]{2,3});reply.range="bytes 2-3/6";BiliDataSource source=new BiliDataSource(url->reply);
        assertEquals(2,source.open(new DataSpec.Builder().setUri(URL).setPosition(2).build()));source.close();
        reply.range="bytes 0-1/6";assertThrows(IOException.class,()->source.open(new DataSpec.Builder().setUri(URL).setPosition(2).build()));assertTrue(reply.closed);
    }
    @Test public void rejectsRedirectBeforeContactingOtherDomain() throws Exception {
        Response reply=new Response(302,new byte[0]);reply.location="https://evil.test/video.mp4";int[] count={0};
        BiliDataSource source=new BiliDataSource(url->{count[0]++;return reply;});assertThrows(IOException.class,()->source.open(new DataSpec.Builder().setUri(URL).build()));assertEquals(1,count[0]);assertTrue(reply.closed);
    }
    @Test public void rejectsHtmlAndTruncatedStreams() throws Exception {
        Response reply=new Response(200,new byte[]{1});reply.type="text/html";BiliDataSource source=new BiliDataSource(url->reply);
        assertThrows(IOException.class,()->source.open(new DataSpec.Builder().setUri(URL).build()));assertTrue(reply.closed);
        reply.type="video/mp4";source.open(new DataSpec.Builder().setUri(URL).setLength(3).build());assertEquals(1,source.read(new byte[3],0,3));
        assertThrows(EOFException.class,()->source.read(new byte[3],0,3));source.close();
    }
    @Test public void transientFailureUsesOnlySameTrackBackup() throws Exception {
        String backup="https://backup.bilivideo.com/video.mp4";
        Response failed=new Response(503,new byte[0]),ok=new Response(206,new byte[]{2,3});ok.range="bytes 2-3/4";
        java.util.List<String> contacted=new java.util.ArrayList<>();
        BiliDataSource source=new BiliDataSource(java.util.Arrays.asList(URL,backup),url->{contacted.add(url.toString());return url.toString().equals(URL)?failed:ok;});
        assertEquals(2,source.open(new DataSpec.Builder().setUri(URL).setPosition(2).build()));
        assertEquals(java.util.Arrays.asList(URL,backup),contacted);assertTrue(failed.closed);
        assertEquals("bytes=2-",ok.getRequestProperty("Range"));assertEquals(2,source.read(new byte[8],0,8));source.close();
    }
    @Test public void accessDenialAndUnsafeBackupsStayClosed() throws Exception {
        int[] calls={0};Response denied=new Response(403,new byte[0]);
        BiliDataSource source=new BiliDataSource(java.util.Arrays.asList(URL,"https://backup.bilivideo.com/video.mp4"),url->{calls[0]++;return denied;});
        assertThrows(IOException.class,()->source.open(new DataSpec.Builder().setUri(URL).build()));assertEquals(1,calls[0]);
        BiliDataSource unsafe=new BiliDataSource(java.util.Arrays.asList(URL,"https://evil.test/video.mp4"),url->{calls[0]++;return denied;});
        assertThrows(IOException.class,()->unsafe.open(new DataSpec.Builder().setUri(URL).build()));assertEquals(1,calls[0]);
    }
    @Test public void readFailureReopensBackupAtExactBytePosition() throws Exception {
        String backup="https://backup.bilivideo.com/video.mp4";
        Response truncated=new Response(200,new byte[]{0,1}),ok=new Response(206,new byte[]{2,3});ok.range="bytes 2-3/4";
        BiliDataSource source=new BiliDataSource(java.util.Arrays.asList(URL,backup),url->url.toString().equals(URL)?truncated:ok);
        source.open(new DataSpec.Builder().setUri(URL).setLength(4).build());assertEquals(2,source.read(new byte[8],0,8));
        assertThrows(EOFException.class,()->source.read(new byte[8],0,8));source.close();
        source.open(new DataSpec.Builder().setUri(URL).setPosition(2).setLength(2).build());
        byte[] bytes=new byte[8];assertEquals(2,source.read(bytes,0,8));assertEquals(2,bytes[0]);assertEquals("bytes=2-3",ok.getRequestProperty("Range"));source.close();
    }
}
