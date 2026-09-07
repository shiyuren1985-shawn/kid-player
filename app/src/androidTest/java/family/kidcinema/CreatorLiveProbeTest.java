package family.kidcinema;

import org.junit.*;
import org.json.*;
import androidx.test.platform.app.InstrumentationRegistry;
import static org.junit.Assert.*;

/** Explicit opt-in live diagnostic; never changes a family device. */
public class CreatorLiveProbeTest {
    @Test public void secondCreatorProfileCatalogAndRetry()throws Exception{
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("liveBili")));
        long uid=3546918961547843L;AppStore store=new AppStore(InstrumentationRegistry.getInstrumentation().getTargetContext());
        store.prefs.edit().clear().commit();store.remoteCreators(false);
        AppStore.Creator profile=new BiliClient(uid).profile();assertEquals(uid,profile.uid);
        System.out.println("LIVE profile="+profile.name);store.addCreator(profile);store.selectedCreator(uid);
        BiliSync.Outcome result=BiliSync.run(store,uid,true,new BiliClient(uid));
        System.out.println("LIVE catalog="+result+" count="+store.feed(uid).getJSONArray("videos").length()+" error="+store.syncError(uid));
        System.out.println("LIVE immediateRetry="+BiliSync.run(store,uid,true,new BiliClient(uid)));
        assertEquals(BiliSync.Outcome.UPDATED,result);
        JSONObject feed=store.feed(uid);assertEquals("public_uploads",feed.getString("source"));assertTrue(feed.getBoolean("syncComplete"));assertEquals(feed.getInt("total"),feed.getJSONArray("videos").length());
        for(int i=0;i<feed.getJSONArray("videos").length();i++)assertEquals(uid,feed.getJSONArray("videos").getJSONObject(i).getLong("uid"));
        System.out.println("LIVE firstTitle="+feed.getJSONArray("videos").getJSONObject(0).getString("title"));
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();store.online(true);
        context.startActivity(context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
        androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text(feed.getJSONArray("videos").getJSONObject(0).getString("title"))),8000));
        assertTrue(device.hasObject(androidx.test.uiautomator.By.textContains("全部投稿")));
        device.findObject(androidx.test.uiautomator.By.text("刷新")).click();
        assertTrue(device.hasObject(androidx.test.uiautomator.By.text(feed.getJSONArray("videos").getJSONObject(0).getString("title"))));
    }
}
