package family.kidcinema;

import android.content.*;
import android.os.SystemClock;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.*;
import org.junit.*;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class EasyPlayerTest {
    @Rule public ActivityTestRule<PlayerActivity> activity=new ActivityTestRule<>(PlayerActivity.class,false,false);
    private AppStore store;private UiDevice device;
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private ExoPlayer player(){try{java.lang.reflect.Field f=PlayerActivity.class.getDeclaredField("player");f.setAccessible(true);return (ExoPlayer)f.get(activity.getActivity());}catch(Exception e){throw new AssertionError(e);}}
    private void await(java.util.function.BooleanSupplier condition,long timeout,String message){long until=SystemClock.elapsedRealtime()+timeout;boolean[] ok={false};do{main(()->ok[0]=condition.getAsBoolean());if(ok[0])return;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<until);StringBuilder visible=new StringBuilder();for(UiObject2 label:device.findObjects(By.clazz("android.widget.TextView")))visible.append(label.getText()).append(" | ");fail(message+"; visible player status: "+visible);}
    private Intent intent(boolean online,String id){return new Intent(context(),PlayerActivity.class).putExtra("online",online).putExtra("demo",!online).putExtra("path",id).putExtra("title","小火箭测试影片").putExtra("creatorUid",BiliPolicy.UID);}
    private void click(String description){UiObject2 b=device.wait(Until.findObject(By.desc(description)),6000);assertNotNull(description,b);b.click();}
    @Before public void before(){store=new AppStore(context());store.prefs.edit().clear().commit();device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());Configurator.getInstance().setWaitForIdleTimeout(500);}
    private void startDemo(){store.demo(true);activity.launchActivity(intent(false,"sample-0"));await(()->player()!=null&&player().getCurrentPosition()>500,15000,"Demo must play");}
    private void finishVideo(){main(()->player().seekTo(Math.max(0,player().getDuration()-350)));await(()->player().getPlaybackState()==Player.STATE_ENDED,15000,"Video must reach actual end");assertTrue(device.wait(Until.hasObject(By.desc("播放结束页")),4000));}
    @Test public void endScreenSurvivesBackgroundAndReplayStartsAtZero(){
        startDemo();finishVideo();assertEquals(0,store.progress("demo:sample-0"));SystemClock.sleep(4500);
        assertNotNull(device.findObject(By.desc("返回视频列表")));assertNotNull(device.findObject(By.desc("收藏视频")));assertNotNull(device.findObject(By.desc("关闭影院")));
        device.pressHome();await(()->player()==null,5000,"Background releases completed player");
        context().startActivity(intent(false,"sample-0").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        assertTrue(device.wait(Until.hasObject(By.desc("播放结束页")),5000));main(()->assertNull("Returning to end screen must not autoplay",player()));
        click("重放这部视频");await(()->player()!=null&&player().getCurrentPosition()>300&&player().getCurrentPosition()<5000,15000,"Replay starts from beginning");assertFalse(device.hasObject(By.desc("播放结束页")));
    }
    @Test public void persistentButtonsFavoritePauseMediaKeysAndClose(){
        startDemo();SystemClock.sleep(4500);click("收藏视频");await(()->store.favorite("demo:sample-0"),3000,"Favorite tap must persist");click("暂停视频");
        await(()->!player().getPlayWhenReady(),3000,"Large pause action works");
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_MEDIA_PLAY);await(()->player().isPlaying(),4000,"Media key works while toolbar focused");
        click("关闭影院");await(()->player()==null,5000,"Closing cinema releases playback");assertTrue(device.wait(Until.gone(By.desc("常驻播放按钮区")),5000));
    }
    @Test public void remotePauseKeepsFocusInPersistentButtons(){
        startDemo();SystemClock.sleep(4500);device.pressDPadDown();
        await(()->activity.getActivity().getCurrentFocus()!=null&&"暂停视频".contentEquals(activity.getActivity().getCurrentFocus().getContentDescription()),3000,"Down enters large pause button");
        device.pressDPadCenter();await(()->!player().getPlayWhenReady(),3000,"Remote pauses video");SystemClock.sleep(500);
        main(()->assertEquals("播放视频",String.valueOf(activity.getActivity().getCurrentFocus().getContentDescription())));
        device.pressDPadRight();device.pressDPadCenter();await(()->store.favorite("demo:sample-0"),3000,"Right then confirm favorites without central controls stealing focus");
    }
    @Test public void downEntersPersistentButtonsWhileCentralControlsAreVisible()throws Exception{
        startDemo();java.lang.reflect.Field f=PlayerActivity.class.getDeclaredField("playerView");f.setAccessible(true);androidx.media3.ui.PlayerView view=(androidx.media3.ui.PlayerView)f.get(activity.getActivity());
        main(()->view.showController());device.pressDPadDown();
        await(()->activity.getActivity().getCurrentFocus()!=null&&"暂停视频".contentEquals(activity.getActivity().getCurrentFocus().getContentDescription()),3000,"Down enters pause even with central controls visible");
        main(()->assertFalse(view.isControllerFullyVisible()));device.pressDPadCenter();await(()->!player().getPlayWhenReady(),3000,"Remote pauses from persistent buttons");
        device.pressDPadRight();device.pressDPadCenter();await(()->store.favorite("demo:sample-0"),3000,"Favorite remains accessible immediately after pause");
    }
    @Test public void shellRemoteAfterTouchKeepsPersistentFocus()throws Exception{
        startDemo();device.click(500,350);SystemClock.sleep(1500);
        device.executeShellCommand("input keyevent 20");SystemClock.sleep(300);
        main(()->assertEquals("After shell DOWN","暂停视频",String.valueOf(activity.getActivity().getCurrentFocus().getContentDescription())));
        device.executeShellCommand("input keyevent 23");SystemClock.sleep(600);
        main(()->assertEquals("After shell CENTER","播放视频",String.valueOf(activity.getActivity().getCurrentFocus().getContentDescription())));
        device.executeShellCommand("input keyevent 22");SystemClock.sleep(300);
        main(()->assertEquals("After shell RIGHT","收藏视频",String.valueOf(activity.getActivity().getCurrentFocus().getContentDescription())));
    }
    @Test public void liveEndCardPlaysAnotherApprovedVideoWithoutDialog()throws Exception{
        Assume.assumeTrue("Explicit live opt-in","true".equals(InstrumentationRegistry.getArguments().getString("liveBili")));store.online(true);
        java.util.List<LibraryItem> feed=BiliClient.items(store.feed());LibraryItem current=feed.get(0),next=feed.get(1);
        activity.launchActivity(intent(true,current.path));await(()->player()!=null&&player().getCurrentPosition()>500,45000,"Live video starts");finishVideo();
        assertEquals(2,device.findObjects(By.descStartsWith("接着看：")).size());assertFalse(device.hasObject(By.desc("接着看："+current.name)));
        click("接着看："+next.name);await(()->next.path.equals(activity.getActivity().getIntent().getStringExtra("path"))&&player()!=null&&player().getCurrentPosition()>500,45000,"End card directly plays next approved video");
        main(()->assertTrue(player().getVideoDecoderCounters().renderedOutputBufferCount>0));assertEquals(BiliPolicy.UID,activity.getActivity().getIntent().getLongExtra("creatorUid",0));assertFalse(device.hasObject(By.desc("播放结束页")));
    }
}
