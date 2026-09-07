package family.kidcinema;

import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** One real public video in the actual player; opt-in only. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class DirectBiliPlaybackLiveTest {
    @Rule public ActivityTestRule<PlayerActivity> activity=new ActivityTestRule<>(PlayerActivity.class,false,false);
    @Test public void actualPlayerDecodesApprovedCreatorVideo()throws Exception{
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("liveBili")));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();AppStore store=new AppStore(context);
        store.prefs.edit().clear().commit();store.remoteCreators(false);
        long uid=3546918961547843L;String id="BV1D3th6VEnR";
        store.addCreator(new AppStore.Creator(uid,"混子哥边画边讲","",true));store.selectedCreator(uid);store.online(true);
        // This BVID was independently returned by the real creator catalogue. Playback rechecks its owner/rights.
        store.feed(uid,new JSONObject().put("schema",1).put("uid",uid).put("syncedAt",0).put("videos",new JSONArray().put(new JSONObject().put("uid",uid).put("bvid",id).put("title","混子哥边画边讲:西藏吉隆泥石流").put("author","混子哥边画边讲").put("published",1700000000).put("duration",""))));
        store.prefs.edit().putLong(store.biliKey(uid,"attempt"),System.currentTimeMillis()).commit();
        PlayerActivity screen=activity.launchActivity(new Intent(context,PlayerActivity.class).putExtra("online",true).putExtra("demo",false).putExtra("creatorUid",uid).putExtra("path",id).putExtra("title","混子哥边画边讲:西藏吉隆泥石流").putExtra("fromStart",true));
        java.lang.reflect.Field field=PlayerActivity.class.getDeclaredField("player");field.setAccessible(true);
        long deadline=android.os.SystemClock.elapsedRealtime()+55000;long[] position={0};int[] frames={0},audio={0};String[] error={""};
        try{
            while(android.os.SystemClock.elapsedRealtime()<deadline){
                Thread.sleep(250);
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{ExoPlayer p=(ExoPlayer)field.get(screen);if(p!=null){position[0]=p.getCurrentPosition();frames[0]=p.getVideoDecoderCounters()==null?0:p.getVideoDecoderCounters().renderedOutputBufferCount;audio[0]=p.getAudioDecoderCounters()==null?0:p.getAudioDecoderCounters().renderedOutputBufferCount;if(p.getPlayerError()!=null)error[0]=p.getPlayerError().getErrorCodeName();}}catch(Exception e){error[0]=e.getClass().getSimpleName();}});
                if(!error[0].isEmpty()||(position[0]>=5000&&frames[0]>0&&audio[0]>0))break;
            }
            System.out.println("DIRECT_PLAY positionMs="+position[0]+" videoFrames="+frames[0]+" audioBuffers="+audio[0]+" error="+error[0]);
            assertEquals("",error[0]);assertTrue("Actual video must advance at least 5 seconds",position[0]>=5000);assertTrue("Video decoded",frames[0]>0);assertTrue("Audio decoded",audio[0]>0);
        }finally{activity.finishActivity();}
    }
}
