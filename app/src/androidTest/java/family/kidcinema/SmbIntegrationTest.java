package family.kidcinema;

import android.content.Context;
import android.net.Uri;
import androidx.media3.datasource.DataSpec;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/** Requires tools/smb-fixture.py on the Mac; credentials are disposable test values. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class SmbIntegrationTest {
    private AppStore.Config fixture(){AppStore.Config c=new AppStore.Config();c.host="10.0.2.2";c.port=1445;c.share="KIDS";c.root="children";c.user="kidtest";c.password="fixture-only-123";return c;}
    @Test public void listReadSeekAndBoundary() throws Exception {
        AppStore.Config c=fixture();
        try(SmbLibrary library=new SmbLibrary(c)){
            List<LibraryItem> list=library.list("");assertEquals(1,list.size());assertEquals("space.mp4",list.get(0).path);
            assertThrows(IllegalArgumentException.class,()->library.list(".."));
            assertThrows(IllegalArgumentException.class,()->library.open("../space.mp4"));
        }
        SmbDataSource source=new SmbDataSource(c,"space.mp4");
        try {
            long size=source.open(new DataSpec(Uri.parse("smb://library/space.mp4")));assertTrue(size>100000);
            byte[] header=new byte[32];assertEquals(32,source.read(header,0,32));assertEquals("ftyp",new String(header,4,4,java.nio.charset.StandardCharsets.US_ASCII));source.close();
            source.open(new DataSpec.Builder().setUri("smb://library/space.mp4").setPosition(100000).setLength(100).build());
            byte[] chunk=new byte[100];assertEquals(100,source.read(chunk,0,100));assertEquals(-1,source.read(chunk,0,1));
        } finally {source.close();}
    }
    @Test public void wrongCredentialsFail() {
        AppStore.Config c=fixture();c.password="wrong";
        com.hierynomus.mssmb2.SMBApiException error=assertThrows(com.hierynomus.mssmb2.SMBApiException.class,
            ()->{try(SmbLibrary ignored=new SmbLibrary(c)) {ignored.list("");}});
        assertEquals("Authentication must fail, not just networking",0xc000006dL,error.getStatusCode());
    }
    @Test public void actualSmbVideoPlayback() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppStore store=new AppStore(context);store.prefs.edit().clear().commit();store.save(fixture());store.demo(false);
        androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        android.content.Intent launch=new android.content.Intent(context,PlayerActivity.class)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("demo",false).putExtra("path","space.mp4").putExtra("title","SMB 测试视频");
        try {
            context.startActivity(launch);
            long deadline=System.currentTimeMillis()+25000;
            while(store.progress("smb:space.mp4")<2000 && System.currentTimeMillis()<deadline)Thread.sleep(250);
            assertTrue("Media3 really plays from SMB, not only local assets",store.progress("smb:space.mp4")>=2000);
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE);Thread.sleep(2500);
            assertFalse(device.hasObject(androidx.test.uiautomator.By.text("这段视频暂时无法播放")));
            long paused=store.progress("smb:space.mp4");
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_MEDIA_PLAY);
            deadline=System.currentTimeMillis()+6000;
            while(store.progress("smb:space.mp4")<=paused+500 && System.currentTimeMillis()<deadline)Thread.sleep(250);
            assertTrue("SMB playback resumes",store.progress("smb:space.mp4")>paused+500);
        } finally {
            device.pressBack();device.waitForIdle();store.prefs.edit().clear().commit();
        }
    }
}
