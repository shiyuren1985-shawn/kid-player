package family.kidcinema;

import android.content.*;
import android.os.SystemClock;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import org.junit.*;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** Dedicated emulator only. Real Bilibili network coverage is explicitly opt-in. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PlaybackRegressionTest {
    @Rule public ActivityTestRule<PlayerActivity> activity=new ActivityTestRule<>(PlayerActivity.class,false,false);
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private ExoPlayer player(){
        try{Field field=PlayerActivity.class.getDeclaredField("player");field.setAccessible(true);return (ExoPlayer)field.get(activity.getActivity());}
        catch(Exception e){throw new AssertionError(e);}
    }
    private void await(java.util.function.BooleanSupplier condition,long timeout,String message){
        long deadline=SystemClock.elapsedRealtime()+timeout;boolean[] ok={false};
        do{main(()->ok[0]=condition.getAsBoolean());if(ok[0])return;SystemClock.sleep(150);}while(SystemClock.elapsedRealtime()<deadline);
        fail(message);
    }
    private Intent intent(boolean online,String id){return new Intent(context(),PlayerActivity.class).putExtra("online",online).putExtra("demo",!online).putExtra("path",id).putExtra("title","播放回归验证");}
    @Test public void pausedPlaybackStaysPausedAfterBackgroundAndReturnsAtPosition() throws Exception {
        AppStore store=new AppStore(context());store.prefs.edit().clear().commit();store.demo(true);
        activity.launchActivity(intent(false,"sample-0"));
        await(()->player()!=null&&player().getCurrentPosition()>1000,15000,"Demo did not start");
        long[] position={0};main(()->{player().pause();position[0]=player().getCurrentPosition();});
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome();
        await(()->player()==null,8000,"Background did not release player");
        context().startActivity(intent(false,"sample-0").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        await(()->player()!=null&&player().getPlaybackState()==Player.STATE_READY,15000,"Player did not return");
        await(()->player().getVideoDecoderCounters()!=null&&player().getVideoDecoderCounters().renderedOutputBufferCount>0,5000,"Paused return did not render a video frame");
        main(()->{assertFalse("Paused video must not autoplay after Home",player().getPlayWhenReady());assertEquals(position[0],player().getCurrentPosition(),200);});
        SystemClock.sleep(3000);
        main(()->{assertEquals(position[0],player().getCurrentPosition(),200);player().play();});
        await(()->player().getCurrentPosition()>position[0]+800,5000,"Resume did not advance");
    }
    @Test public void transientAudioFocusLossPausesAndGainResumes() throws Exception {
        AppStore store=new AppStore(context());store.prefs.edit().clear().commit();store.demo(true);
        activity.launchActivity(intent(false,"sample-0"));
        await(()->player()!=null&&player().getCurrentPosition()>500,15000,"Demo did not start");
        android.media.AudioManager audio=(android.media.AudioManager)context().getSystemService(Context.AUDIO_SERVICE);
        android.media.AudioFocusRequest focus=new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener(change->{},new android.os.Handler(android.os.Looper.getMainLooper())).build();
        try {
            assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED,audio.requestAudioFocus(focus));
            await(()->!player().isPlaying(),4000,"Transient focus loss did not pause playback");
            long[] position={0};main(()->position[0]=player().getCurrentPosition());SystemClock.sleep(1000);
            main(()->assertEquals(position[0],player().getCurrentPosition(),200));
            audio.abandonAudioFocusRequest(focus);
            await(()->player().isPlaying()&&player().getCurrentPosition()>position[0]+500,5000,"Focus gain did not resume");
        }finally{audio.abandonAudioFocusRequest(focus);}
    }
    @Test public void liveThreeApprovedVideosDecodeSeekPauseAndResume() throws Exception {
        Assume.assumeTrue("Run with -e liveBili true", "true".equals(InstrumentationRegistry.getArguments().getString("liveBili")));
        AppStore store=new AppStore(context());store.prefs.edit().clear().commit();store.online(true);
        for(LibraryItem item:BiliClient.items(store.feed())){
            activity.launchActivity(intent(true,item.path));
            await(()->player()!=null&&player().getPlayerError()==null&&player().getCurrentPosition()>1500,45000,"Live start failed: "+item.path);
            main(()->{
                assertTrue("Actual video frames: "+item.path,player().getVideoDecoderCounters().renderedOutputBufferCount>0);
                assertTrue("Decoded audio buffers: "+item.path,player().getAudioDecoderCounters().renderedOutputBufferCount>0);
                assertTrue("UI timeline must permit seeking: "+item.path,player().isCurrentMediaItemSeekable());
                player().pause();player().seekTo(60000);
            });
            await(()->player().getPlaybackState()==Player.STATE_READY&&Math.abs(player().getCurrentPosition()-60000)<300,20000,"Live seek failed: "+item.path);
            SystemClock.sleep(3000);
            main(()->{assertFalse(player().getPlayWhenReady());assertEquals(60000,player().getCurrentPosition(),300);assertNull(player().getPlayerError());player().play();});
            await(()->player().getCurrentPosition()>61500,8000,"Live resume failed: "+item.path);
            activity.finishActivity();
            await(()->store.progress(item.key())>60000,5000,"onStop did not save progress: "+item.path);
            android.util.Log.i("KidPlaybackTest","LIVE_PASS "+item.path+" video+audio decoded, pause, seek60s, resume, saved");
        }
    }
    @Test public void liveOfflineFirstOpenCanRetryAfterNetworkReturns() throws Exception {
        Assume.assumeTrue("Run with -e liveBili true", "true".equals(InstrumentationRegistry.getArguments().getString("liveBili")));
        Assume.assumeTrue(android.provider.Settings.Global.getInt(context().getContentResolver(),"wifi_on",0)==1);
        AppStore store=new AppStore(context());store.prefs.edit().clear().commit();store.online(true);
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        android.net.ConnectivityManager connectivity=(android.net.ConnectivityManager)context().getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean mobileWasEnabled=android.provider.Settings.Global.getInt(context().getContentResolver(),"mobile_data",0)==1;
        try{
            device.executeShellCommand("svc data disable");
            device.executeShellCommand("svc wifi disable");
            await(()->connectivity.getActiveNetwork()==null,12000,"Test device did not go offline");
            activity.launchActivity(intent(true,"BV1Yptj6zEEG"));
            assertTrue("Offline first-open must show retry",device.wait(Until.hasObject(By.text("暂时无法在线播放")),20000));
            assertNotNull(device.findObject(By.text("重试")));assertFalse(device.hasObject(By.clazz("android.webkit.WebView")));
            device.executeShellCommand("svc wifi enable");
            await(()->connectivity.getActiveNetwork()!=null,15000,"Test network did not return");
            device.findObject(By.text("重试")).click();
            await(()->player()!=null&&player().getCurrentPosition()>1500,45000,"Online retry did not play");
            android.util.Log.i("KidPlaybackTest","OFFLINE_RETRY_PASS first-open error then network restored and retry advances");
        }finally{device.executeShellCommand("svc wifi enable");if(mobileWasEnabled)device.executeShellCommand("svc data enable");}
    }
}
