package family.kidcinema;

import android.content.Context;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Run only against the dedicated prototype emulator, not a user's configured tablet. */
public class PrototypeTest {
    private void click(UiDevice device,BySelector selector){UiObject2 item=device.wait(Until.findObject(selector),5000);assertNotNull("Expected control: "+selector,item);item.click();}
    @Test public void localPlaybackUiAndOpenSettings() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppStore store=new AppStore(context);store.prefs.edit().clear().commit();
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Configurator.getInstance().setWaitForIdleTimeout(500);
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(launch);
        assertNotNull(device.wait(Until.findObject(By.text("思思影院")),10000));
        assertNotNull(device.findObject(By.textContains("本地演示")));
        click(device,By.text("▶  播放演示短片"));
        UiObject2 fullscreenTip=device.wait(Until.findObject(By.text("Got it")),1500);
        if(fullscreenTip!=null)fullscreenTip.click();
        assertTrue(device.wait(Until.hasObject(By.desc("返回视频列表")),10000));
        long deadline=System.currentTimeMillis()+12000;
        while(store.progress("demo:sample-0")<1000 && System.currentTimeMillis()<deadline)Thread.sleep(250);
        assertTrue("Local MP4 advances before leaving",store.progress("demo:sample-0")>1000);
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE);
        Thread.sleep(3000);
        long held=store.progress("demo:sample-0");Thread.sleep(9000);
        assertEquals("Position must stay still throughout pause",held,store.progress("demo:sample-0"));
        assertFalse("Pause must not trigger a decoder error",device.hasObject(By.text("这段视频暂时无法播放")));
        long paused=store.progress("demo:sample-0");
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_MEDIA_PLAY);
        deadline=System.currentTimeMillis()+6000;
        while(store.progress("demo:sample-0")<=paused+500 && System.currentTimeMillis()<deadline)Thread.sleep(250);
        assertTrue("Playback resumes after pause",store.progress("demo:sample-0")>paused+500);
        deadline=System.currentTimeMillis()+10000;
        while(store.progress("demo:sample-0")<8500 && System.currentTimeMillis()<deadline)Thread.sleep(250);
        assertTrue("Sample reaches later playback position",store.progress("demo:sample-0")>=8500);
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE);Thread.sleep(3000);
        long heldLater=store.progress("demo:sample-0");Thread.sleep(9000);
        assertTrue("Later pause must not let the clip finish",heldLater>=8500);
        assertEquals("Later pause holds position",heldLater,store.progress("demo:sample-0"));
        assertFalse("Later pause must not trigger decoder error",device.hasObject(By.text("这段视频暂时无法播放")));
        // Player must actually move through the local MP4, not merely show a mock screen.
        device.pressBack();device.wait(Until.hasObject(By.text("思思影院")),5000);
        assertTrue("Real playback progress saved",store.progress("demo:sample-0")>1000);
        click(device,By.text("▷  继续观看"));
        assertNotNull(device.wait(Until.findObject(By.text("小火箭去旅行")),5000));
        click(device,By.text("小火箭去旅行"));
        click(device,By.text("♡ 喜欢"));
        click(device,By.text("返回"));
        assertTrue(device.wait(Until.hasObject(By.text("♥  已喜欢")),5000));
        assertTrue(store.favorite("demo:sample-0"));
        click(device,By.text("♡  我的喜欢"));
        assertNotNull(device.wait(Until.findObject(By.text("小火箭去旅行")),3000));
        click(device,By.desc("播放设置"));
        assertFalse(device.hasObject(By.text("家长验证")));
        click(device,By.text("家庭存储"));
        assertNotNull(device.wait(Until.findObject(By.desc("家庭存储地址")),6000));
        click(device,By.text("取消"));
        assertNotNull(device.wait(Until.findObject(By.desc("播放设置")),5000));
    }
    @Test public void encryptedSettingsRoundTrip() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();AppStore store=new AppStore(context);
        AppStore.Config config=new AppStore.Config();config.host="192.168.1.10";config.share="children";config.root="kids";config.user="readonly";config.password="test-only-secret";
        store.save(config);assertEquals(config.password,store.config().password);
        assertFalse(store.prefs.getString("connection","").contains(config.password));
        assertFalse(store.prefs.getString("connection","").contains(config.host));
        store.prefs.edit().remove("connection").commit();
    }
}
