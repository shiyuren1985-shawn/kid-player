package family.kidcinema;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.content.pm.*;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.json.*;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class SearchAndIconsTest {
    private AppStore store;private Context context;private UiDevice device;private int[] iconStates;
    @Before public void before()throws Exception{
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();store=new AppStore(context);store.prefs.edit().clear().commit();store.online(true);store.remoteCreators(false);store.mode("平板");
        iconStates=new int[LauncherIcons.ALL.length];for(int i=0;i<iconStates.length;i++)iconStates[i]=context.getPackageManager().getComponentEnabledSetting(LauncherIcons.ALL[i].component(context));
        store.addCreator(new AppStore.Creator(123,"作者乙","",true));store.addCreator(new AppStore.Creator(456,"停用作者","",false));
        feed(BiliPolicy.UID,true,"太空 ABC 探索","森林里的动物");feed(123,false,"太空飞船","海底故事");feed(456,true,"太空秘密");
        store.selectedCreator(BiliPolicy.UID);device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());Configurator.getInstance().setWaitForIdleTimeout(500);
    }
    @After public void after(){
        device.pressHome();for(int i=0;i<iconStates.length;i++)context.getPackageManager().setComponentEnabledSetting(LauncherIcons.ALL[i].component(context),iconStates[i],PackageManager.DONT_KILL_APP);
        store.prefs.edit().clear().commit();
    }
    private String id(long uid,int index){return String.format(Locale.ROOT,"BV%010d",uid+index);}
    private void feed(long uid,boolean complete,String... titles)throws Exception{
        JSONArray rows=new JSONArray();for(int i=0;i<titles.length;i++)rows.put(new JSONObject().put("uid",uid).put("bvid",id(uid,i)).put("title",titles[i]).put("author","作者"+uid).put("published",1700000000+i).put("duration","01:00"));
        store.feed(uid,new JSONObject().put("schema",1).put("uid",uid).put("videos",rows).put("syncComplete",complete).put("syncedAt",System.currentTimeMillis()));
        store.prefs.edit().putLong(store.biliKey(uid,"attempt"),System.currentTimeMillis()).commit();
    }
    private void launch(){context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));assertTrue(device.wait(Until.hasObject(By.desc("搜索视频关键词")),6000));}
    private void click(BySelector selector){UiObject2 view=device.wait(Until.findObject(selector),5000);assertNotNull(selector.toString(),view);view.click();}
    @Test public void cachedSearchMatchesWordsCaseAndWidthAndExcludesDisabled(){
        assertEquals(1,CatalogSearch.search(store,"  太空  ａｂｃ ",false,BiliPolicy.UID).videos.size());
        CatalogSearch.Result all=CatalogSearch.search(store,"太空",true,BiliPolicy.UID);assertEquals(2,all.videos.size());assertEquals(2,all.creators);assertEquals(1,all.incomplete);
        assertEquals(123,all.videos.get(1).creatorUid);assertTrue(CatalogSearch.search(store,"不存在",true,BiliPolicy.UID).videos.isEmpty());
        assertTrue(CatalogSearch.search(store,"  ",true,BiliPolicy.UID).videos.isEmpty());
    }
    @Test public void searchHasNoThirtyItemLimitAndDoesNotMutateNetworkState()throws Exception{
        String[] titles=new String[65];Arrays.fill(titles,"科学探索");feed(BiliPolicy.UID,true,titles);Map<String,?> before=new HashMap<>(store.prefs.getAll());
        assertEquals(65,CatalogSearch.search(store,"科学",false,BiliPolicy.UID).videos.size());assertEquals(before,store.prefs.getAll());
        store.creatorEnabled(123,false);assertEquals(1,CatalogSearch.search(store,"科学",true,BiliPolicy.UID).creators);
    }
    @Test public void searchUiSwitchesScopeClearsAndRoutesToResultOwner()throws Exception{
        store.toggleFavorite("bili:123","bili:"+id(123,0));store.progress("bili:123","bili:"+id(123,0),42000);
        launch();device.findObject(By.desc("搜索视频关键词")).setText("太空");assertTrue(device.wait(Until.hasObject(By.text("搜索结果 · 1 个视频")),6000));
        assertFalse(device.hasObject(By.text("太空飞船")));click(By.text("全部订阅"));assertTrue(device.wait(Until.hasObject(By.text("搜索结果 · 2 个视频")),6000));
        assertTrue(device.wait(Until.hasObject(By.text("太空飞船")),5000));assertTrue(device.hasObject(By.text("继续 00:42")));assertTrue(device.hasObject(By.text("♥  已收藏")));
        assertTrue(device.hasObject(By.textContains("投稿尚未加载完整")));
        final Intent[] launched={null};Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(intent.getComponent()!=null&&intent.getComponent().getClassName().equals(PlayerActivity.class.getName())){launched[0]=intent;return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}return null;}};
        instrumentation.addMonitor(monitor);try{click(By.text("太空飞船"));long until=SystemClock.elapsedRealtime()+3000;while(launched[0]==null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);assertNotNull(launched[0]);assertEquals(123,launched[0].getLongExtra("creatorUid",0));assertEquals(id(123,0),launched[0].getStringExtra("path"));}finally{instrumentation.removeMonitor(monitor);}
        assertEquals(BiliPolicy.UID,store.selectedCreator());assertTrue(store.prefs.getString("library:bili:123","{}").contains("太空飞船"));
        click(By.desc("清除搜索关键词"));assertTrue(device.findObject(By.desc("搜索视频关键词")).getText().matches("|搜索视频标题"));
        assertTrue(device.wait(Until.hasObject(By.text("全部投稿")),6000));
        for(int i=0;i<4&&!device.hasObject(By.text("森林里的动物"));i++)device.findObject(By.desc("影片列表")).scroll(Direction.DOWN,0.5f);
        assertTrue(device.wait(Until.hasObject(By.text("森林里的动物")),3000));assertFalse(device.hasObject(By.text("太空飞船")));
    }
    @Test public void searchUpdatesAfterFeedChangesAndSurvivesActivityRecreation()throws Exception{
        launch();device.findObject(By.desc("搜索视频关键词")).setText("海底");click(By.text("全部订阅"));assertTrue(device.wait(Until.hasObject(By.text("海底故事")),6000));
        feed(123,true,"海底故事","海底新片");assertTrue(device.wait(Until.hasObject(By.text("海底新片")),6000));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(Activity a:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))if(a instanceof MainActivity){a.recreate();break;}});
        assertTrue(device.wait(Until.hasObject(By.text("搜索结果 · 2 个视频")),6000));assertEquals("海底",device.findObject(By.desc("搜索视频关键词")).getText());
        store.creatorEnabled(123,false);assertTrue(device.wait(Until.hasObject(By.text("没有找到匹配的视频，试试更短的关键词。")),6000));
    }
    @Test public void everyLauncherChoiceHasExactlyOneLaunchableAliasAndMainStaysEnabled()throws Exception{
        PackageManager pm=context.getPackageManager();
        for(LauncherIcons.Choice choice:LauncherIcons.ALL){
            LauncherIcons.select(context,store,choice);int count=0;for(LauncherIcons.Choice candidate:LauncherIcons.ALL)if(LauncherIcons.enabled(context,candidate))count++;
            assertEquals(1,count);Intent launch=pm.getLaunchIntentForPackage(context.getPackageName());assertNotNull(launch);assertEquals(choice.component(context),launch.getComponent());
            assertEquals(choice.id,LauncherIcons.selected(new AppStore(context)).id);assertNotNull(pm.getActivityInfo(choice.component(context),0).loadIcon(pm));
            assertNotEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,pm.getComponentEnabledSetting(new ComponentName(context,MainActivity.class)));
        }
        context.startActivity(pm.getLaunchIntentForPackage(context.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));assertTrue(device.wait(Until.hasObject(By.desc("播放设置")),6000));
    }
    @Test public void iconPickerAppliesChoiceAndCanRestoreOriginal()throws Exception{
        launch();click(By.desc("播放设置"));new UiScrollable(new UiSelector().scrollable(true)).scrollTextIntoView("选择桌面图标");click(By.text("选择桌面图标"));
        assertTrue(device.wait(Until.hasObject(By.text("使用这个图标")),5000));click(By.desc("选择图标：Kid Player 原创"));click(By.text("使用这个图标"));
        long until=SystemClock.elapsedRealtime()+4000;while(!LauncherIcons.selected(store).id.equals("kid")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);assertEquals("kid",LauncherIcons.selected(store).id);
        assertTrue(LauncherIcons.enabled(context,LauncherIcons.find("kid")));LauncherIcons.select(context,store,LauncherIcons.find("heart"));assertTrue(LauncherIcons.enabled(context,LauncherIcons.find("heart")));
    }
}
