package family.kidcinema;

import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

public class KidPlayerUiTest {
    private AppStore store;private UiDevice device;
    @Before public void before()throws Exception{
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();store=new AppStore(c);store.prefs.edit().clear().commit();store.online(true);store.mode("电视");device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());Configurator.getInstance().setWaitForIdleTimeout(500);
        JSONArray videos=new JSONArray();for(int i=1;i<=30;i++)videos.put(new JSONObject().put("bvid",String.format(java.util.Locale.ROOT,"BV%010d",i)).put("uid",BiliPolicy.UID).put("title","测试影片 "+i).put("author","测试作者").put("published",1700000000+i).put("duration","01:00"));
        store.feed(new JSONObject().put("schema",1).put("uid",BiliPolicy.UID).put("syncedAt",System.currentTimeMillis()).put("source","public_collections").put("collectionCount",1).put("videos",videos));
        store.prefs.edit().putLong("bili.attempt",System.currentTimeMillis()-120000).commit();
        c.startActivity(c.getPackageManager().getLaunchIntentForPackage(c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue(device.wait(Until.hasObject(By.text("kid player")),8000));
    }
    @After public void after(){device.pressHome();store.prefs.edit().clear().commit();}
    private void click(BySelector selector){UiObject2 v=device.wait(Until.findObject(selector),5000);assertNotNull(selector.toString(),v);v.click();}
    @Test public void manageCreatorsOpensListWithoutChangingSelection()throws Exception{
        long selected=store.selectedCreator();
        int count=store.enabledCreators().size();
        click(By.text("管理 UP 主"));
        assertTrue(device.wait(Until.hasObject(By.text("UP 主名单来源")),3000));
        assertEquals(selected,store.selectedCreator());assertEquals(count,store.enabledCreators().size());
        click(By.text("完成"));
        assertTrue(device.wait(Until.hasObject(By.text("测试影片 1")),3000));
    }
    @Test public void progressIndicatorTracksLoadingWhileCachedCardsRemain()throws Exception{
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(android.app.Activity activity:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))if(activity instanceof MainActivity){
                try{
                    java.lang.reflect.Field loading=MainActivity.class.getDeclaredField("loading");loading.setAccessible(true);
                    java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);
                    loading.setBoolean(activity,true);render.invoke(activity);
                }catch(Exception e){throw new AssertionError(e);}
            }
        });
        assertTrue(device.wait(Until.hasObject(By.desc("正在更新目录")),3000));
        assertTrue(device.hasObject(By.text("测试影片 1")));
        assertFalse(device.findObject(By.text("更新中")).isEnabled());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(android.app.Activity activity:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))if(activity instanceof MainActivity){
                try{java.lang.reflect.Method cancel=MainActivity.class.getDeclaredMethod("cancelLoad");cancel.setAccessible(true);cancel.invoke(activity);
                    java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
                }catch(Exception e){throw new AssertionError(e);}
            }
        });
        assertTrue(device.wait(Until.gone(By.desc("正在更新目录")),3000));
        assertTrue(device.findObject(By.text("刷新")).isEnabled());
    }
    @Test public void automaticLaunchDoesNotUseManualOneMinuteCooldown()throws Exception{
        long attempt=store.syncAttempt();Thread.sleep(500);assertEquals(attempt,store.syncAttempt());assertTrue(System.currentTimeMillis()-attempt>110000);
    }
    @Test public void lateCardDirectlyOpensPlayerAndReturnKeepsPosition()throws Exception{
        UiScrollable scroll=new UiScrollable(new UiSelector().description("影片列表"));assertTrue(scroll.scrollIntoView(new UiSelector().text("测试影片 18")));
        click(By.desc("测试影片 18，直接播放"));
        boolean[] playingActivity={false};long deadline=System.currentTimeMillis()+4000;
        while(!playingActivity[0]&&System.currentTimeMillis()<deadline){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(android.app.Activity a:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))if(a instanceof PlayerActivity)playingActivity[0]=true;
        });Thread.sleep(100);}
        assertTrue("One card click opens the player without a details confirmation",playingActivity[0]);device.pressBack();
        assertNotNull(device.wait(Until.findObject(By.pkg("family.kidcinema").focused(true).desc("测试影片 18，直接播放")),4000));
    }
    @Test public void tvDownFromSettingsEntersCatalogAndGridMoves()throws Exception{
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(android.app.Activity activity:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)){
                if(activity instanceof MainActivity){android.view.View settings=activity.getWindow().getDecorView().findViewWithTag("settings");assertNotNull(settings);assertTrue(settings.requestFocusFromTouch());}
            }
        });
        assertNotNull("Arrange physical remote focus on settings",device.wait(Until.findObject(By.pkg("family.kidcinema").focused(true).desc("播放设置")),3000));
        // Real remote cadence gives Android time to attach the next recycled row.
        for(int i=0;i<7;i++){device.pressDPadDown();Thread.sleep(150);}
        UiObject2 first=device.wait(Until.findObject(By.pkg("family.kidcinema").focused(true).descContains("直接播放")),3000);
        assertNotNull("DPAD down must transfer input from settings into video cards",first);String old=first.getContentDescription();
        device.pressDPadRight();Thread.sleep(150);device.pressDPadDown();
        UiObject2 next=device.wait(Until.findObject(By.pkg("family.kidcinema").focused(true).descContains("直接播放")),3000);
        assertNotNull(next);assertNotEquals(old,next.getContentDescription());
    }
    @Test public void demoPlaybackReturnsFocusToChosenCard()throws Exception{
        store.demo(true);Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        c.startActivity(c.getPackageManager().getLaunchIntentForPackage(c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        click(By.desc("森林里的小秘密，直接播放"));
        long deadline=System.currentTimeMillis()+15000;while(store.progress("demo:sample-1")<500&&System.currentTimeMillis()<deadline)Thread.sleep(200);
        assertTrue("Video must actually advance before returning",store.progress("demo:sample-1")>=500);
        device.pressBack();
        assertNotNull("Playback return must restore selected card, not settings",device.wait(Until.findObject(By.pkg("family.kidcinema").focused(true).desc("森林里的小秘密，直接播放")),4000));
    }
    @Test public void cloudRevocationUpdatesVisibleCatalogWithoutRestart()throws Exception{
        assertTrue(device.wait(Until.hasObject(By.text("测试影片 1")),3000));
        store.applyRemoteCreators("https://example.invalid/creators.json",java.util.Collections.emptyList());
        assertTrue(device.wait(Until.gone(By.text("测试影片 1")),3000));
        assertEquals(0,store.selectedCreator());
        assertTrue(device.wait(Until.hasObject(By.textContains("请在云端文件配置作者")),3000));
    }
    @Test public void creatorAvatarsSwitchIsolatedCatalogs()throws Exception{
        store.remoteCreators(false);store.addCreator(new AppStore.Creator(123,"测试作者乙","",true));
        JSONArray videos=new JSONArray().put(new JSONObject().put("bvid","BV0000000099").put("uid",123).put("title","乙的独立影片").put("author","测试作者乙").put("published",1700000000).put("duration","01:00"));
        store.feed(123,new JSONObject().put("schema",1).put("uid",123).put("syncedAt",System.currentTimeMillis()).put("videos",videos));store.prefs.edit().putLong(store.biliKey(123,"attempt"),System.currentTimeMillis()).commit();
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();device.pressHome();c.startActivity(c.getPackageManager().getLaunchIntentForPackage(c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        click(By.desc("测试作者乙，UP 主，查看视频"));assertTrue(device.wait(Until.hasObject(By.text("乙的独立影片")),5000));assertFalse(device.hasObject(By.text("测试影片 1")));assertEquals(123,store.selectedCreator());
        click(By.descContains("画渣花小烙，UP 主"));assertTrue(device.wait(Until.hasObject(By.text("测试影片 1")),5000));assertFalse(device.hasObject(By.text("乙的独立影片")));
    }
}
