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
        store.feed(new JSONObject().put("schema",1).put("uid",BiliPolicy.UID).put("syncedAt",System.currentTimeMillis()).put("syncComplete",true).put("source","public_collections").put("collectionCount",1).put("videos",videos));
        store.prefs.edit().putLong("bili.attempt",System.currentTimeMillis()-120000).commit();
        c.startActivity(c.getPackageManager().getLaunchIntentForPackage(c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue(device.wait(Until.hasObject(By.text("kid player")),8000));
    }
    @After public void after(){device.pressHome();store.prefs.edit().clear().commit();}
    private void click(BySelector selector){UiObject2 v=device.wait(Until.findObject(selector),5000);assertNotNull(selector.toString(),v);v.click();}
    @Test public void historyShowsCompletedVideosAndClearRequiresConfirmation()throws Exception{
        store.progress("bili:BV0000000001",4000);store.progress("bili:BV0000000002",4000);store.progress("bili:BV0000000002",0);store.toggleFavorite("bili:BV0000000001");
        click(By.text("◷  观看历史"));
        assertTrue(device.wait(Until.hasObject(By.text("测试影片 2")),3000));assertTrue(device.hasObject(By.text("测试影片 1")));
        assertTrue(device.hasObject(By.textContains("最近看过的 30 个视频")));
        click(By.text("清空观看历史"));click(By.text("取消"));assertTrue(device.wait(Until.hasObject(By.text("测试影片 2")),3000));
        click(By.text("清空观看历史"));click(By.text("清空"));
        assertTrue(device.wait(Until.hasObject(By.text("还没有观看历史，播放视频后会记录在这里。")),3000));
        assertFalse(device.findObject(By.text("清空观看历史")).isEnabled());
        assertEquals(4000,store.progress("bili:BV0000000001"));assertTrue(store.favorite("bili:BV0000000001"));
        click(By.text("♡  我的收藏"));assertTrue(device.wait(Until.hasObject(By.text("测试影片 1")),3000));
    }
    @Test public void creatorManagementHasOneEntryInSettings()throws Exception{
        long selected=store.selectedCreator();
        int count=store.enabledCreators().size();
        assertFalse(device.hasObject(By.text("管理 UP 主")));
        click(By.desc("播放设置"));
        click(By.text("管理 UP 主名单"));
        assertTrue(device.wait(Until.hasObject(By.text("UP 主名单来源")),3000));
        assertEquals(selected,store.selectedCreator());assertEquals(count,store.enabledCreators().size());
        click(By.text("本机管理"));
        assertTrue(device.wait(Until.hasObject(By.text("管理 UP 主")),3000));
        assertTrue(device.hasObject(By.text("新增 UP 主 UID")));
        assertFalse(device.hasObject(By.text("编辑本机 UP 主")));
        click(By.text("完成"));
        click(By.text("管理 UP 主名单"));
        assertTrue(device.wait(Until.hasObject(By.text("管理 UP 主")),3000));
        assertTrue(device.hasObject(By.text("新增 UP 主 UID")));
        click(By.text("名单来源"));
        assertTrue(device.wait(Until.hasObject(By.text("UP 主名单来源")),3000));
        click(By.text("云端名单（推荐）"));click(By.text("完成"));
        assertTrue(store.remoteCreators());
        assertEquals(selected,store.selectedCreator());assertEquals(count,store.enabledCreators().size());
        click(By.text("取消"));
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
    @Test public void allUploadsAndCollectionFilterHaveSeparateLists()throws Exception{
        CatalogFixture fixture=new CatalogFixture();fixture.count=65;
        JSONObject old=store.feed();JSONObject full=new BiliClient(fixture).syncCatalog(old,true,f->{});store.feed(full);
        assertTrue(device.wait(Until.hasObject(By.textContains("全部投稿 65 条")),5000));
        assertTrue(device.hasObject(By.text("模拟投稿 65")));assertFalse(device.hasObject(By.text("管理 UP 主")));
        click(By.text("合集（1）"));click(By.text("模拟合集 1 · 35 条"));
        assertTrue(device.wait(Until.hasObject(By.text("模拟投稿 35")),3000));assertFalse(device.hasObject(By.text("模拟投稿 65")));
        click(By.text("全部投稿"));assertTrue(device.wait(Until.hasObject(By.text("模拟投稿 65")),3000));
        for(int attempt=0;attempt<25&&!device.hasObject(By.text("模拟投稿 1"));attempt++){
            android.graphics.Rect bounds=device.findObject(By.desc("影片列表")).getVisibleBounds();
            device.swipe(bounds.centerX(),bounds.bottom-100,bounds.centerX(),bounds.top+100,35);Thread.sleep(300);
        }
        assertTrue("All 65 posts must remain reachable at the bottom",device.hasObject(By.text("模拟投稿 1")));
    }
    @Test public void partialCountsAndCollectionFailureDoNotClaimAllUploadsLoaded()throws Exception{
        JSONObject feed=store.feed().put("source","public_uploads").put("syncComplete",false).put("loadedCount",30).put("total",95).put("collectionsError","模拟合集接口受限");store.feed(feed);
        assertTrue(device.wait(Until.hasObject(By.textContains("投稿已读取 30 / 共 95 条（尚未读完）")),3000));
        assertTrue(device.hasObject(By.textContains("模拟合集接口受限")));assertTrue(device.hasObject(By.text("测试影片 1")));
    }
    @Test public void clearingMediaCachesPreservesPersonalLibrary()throws Exception{
        String key="bili:BV0000000001";store.toggleFavorite(key);store.progress(key,12345);long uid=store.selectedCreator();int creators=store.enabledCreators().size();
        click(By.desc("播放设置"));UiScrollable settings=new UiScrollable(new UiSelector().scrollable(true));assertTrue(settings.scrollIntoView(new UiSelector().text("缓存管理")));click(By.text("缓存管理"));
        click(By.text("清理图片和视频缓存"));assertTrue(device.wait(Until.hasObject(By.text("缓存已清理；名单、收藏和观看记录已保留。")),5000));
        assertTrue(store.favorite(key));assertEquals(12345,store.progress(key));assertEquals(uid,store.selectedCreator());assertEquals(creators,store.enabledCreators().size());assertEquals(30,store.feed().getJSONArray("videos").length());
        click(By.text("关闭"));click(By.text("取消"));
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
