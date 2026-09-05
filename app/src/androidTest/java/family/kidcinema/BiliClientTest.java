package family.kidcinema;

import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** All API responses here are artificial fixtures, never evidence of live Bilibili success. */
public class BiliClientTest {
    private static final String ID="BV1xx411c7mD";
    @Test public void initialDocumentIsExplicitSnapshotNotSuccessfulSync() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();AppStore store=new AppStore(context);
        store.prefs.edit().remove("bili.feed").commit();JSONObject feed=store.feed();
        assertEquals(0,feed.getLong("syncedAt"));assertEquals("2026-09-04",feed.getString("seededAt"));
        assertEquals(3,BiliClient.items(feed).size());assertTrue(BiliClient.contains(feed,"BV1Yptj6zEEG"));
        assertFalse(BiliClient.contains(feed,ID));
    }
    private JSONObject row(long uid) throws Exception {
        return new JSONObject().put("mid",uid).put("bvid",ID).put("title","测试目录条目 · 非真实投稿")
            .put("author","测试作者").put("created",1700000000).put("length","01:00");
    }
    private JSONObject nav() throws Exception {
        return new JSONObject("{\"code\":-101,\"data\":{\"wbi_img\":{\"img_url\":\"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png\",\"sub_url\":\"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png\"}}}");
    }
    private BiliClient client(long owner) {
        return new BiliClient(path->{if(path.equals("/x/web-interface/nav"))return nav();
            assertTrue(path.startsWith("/x/space/wbi/arc/search?"));assertTrue(path.contains("mid=402576555"));assertTrue(path.contains("w_rid="));
            return new JSONObject().put("code",0).put("data",new JSONObject().put("list",new JSONObject().put("vlist",new JSONArray().put(row(owner)).put(row(owner)))));});
    }
    private JSONObject detail(long uid) throws Exception {
        return new JSONObject().put("code",0).put("data",new JSONObject().put("bvid",ID).put("state",0).put("rights",new JSONObject().put("pay",0))
            .put("owner",new JSONObject().put("mid",uid)).put("pages",new JSONArray().put(new JSONObject().put("cid",123))));
    }
    @Test public void syncNormalizesAndDeduplicatesOnlyApprovedCreator() throws Exception {
        JSONObject feed=client(BiliPolicy.UID).sync();assertEquals(1,BiliClient.items(feed).size());assertTrue(BiliClient.contains(feed,ID));
        assertTrue(BiliClient.items(feed).get(0).online);assertEquals("bili:"+ID,BiliClient.items(feed).get(0).key());
        assertFalse(BiliClient.contains(feed,"BV1yy411c7mD"));
        assertThrows(IllegalArgumentException.class,()->client(123).sync());
    }
    @Test public void platformDenialStopsWithoutFallback() throws Exception {
        int[] count={0};BiliClient client=new BiliClient(path->{count[0]++;return new JSONObject("{\"code\":-352,\"message\":\"fixture denial\"}");});
        Exception error=assertThrows(java.io.IOException.class,client::sync);assertTrue(error.getMessage().contains("-352"));assertEquals(1,count[0]);
    }
    @Test public void corruptedFeedCannotOverwriteLastGoodCache() throws Exception {
        AppStore store=new AppStore(InstrumentationRegistry.getInstrumentation().getTargetContext());
        JSONObject good=client(BiliPolicy.UID).sync();store.feed(good);
        JSONObject bad=new JSONObject(good.toString()).put("uid",123);
        assertThrows(IllegalArgumentException.class,()->store.feed(bad));assertEquals(good.toString(),store.feed().toString());
        JSONObject corrupt=new JSONObject(good.toString());corrupt.getJSONArray("videos").getJSONObject(0).put("uid",123);
        assertThrows(IllegalArgumentException.class,()->BiliClient.items(corrupt));
        store.prefs.edit().remove("bili.feed").commit();
    }
    @Test public void playbackRechecksOwnerBeforeRequestingStreams() throws Exception {
        int[] count={0};BiliClient client=new BiliClient(path->{count[0]++;assertTrue(path.startsWith("/x/web-interface/view?"));return detail(123);});
        assertThrows(IllegalArgumentException.class,()->client.resolve(ID));assertEquals(1,count[0]);
    }
    @Test public void playbackAcceptsAvcAndAacButRejectsExternalHosts() throws Exception {
        String dash="{\"code\":0,\"data\":{\"dash\":{\"video\":[{\"id\":32,\"codecs\":\"avc1.64001E\",\"baseUrl\":\"https://test.bilivideo.com/video.m4s\"}],\"audio\":[{\"codecs\":\"mp4a.40.2\",\"baseUrl\":\"https://test.bilivideo.com/audio.m4s\"}]}}}";
        BiliClient client=new BiliClient(path->path.startsWith("/x/web-interface/view?")?detail(BiliPolicy.UID):new JSONObject(dash));
        BiliClient.Playback playback=client.resolve(ID);assertEquals(123,playback.cid);assertTrue(playback.audio.endsWith("audio.m4s"));
        BiliClient backup=new BiliClient(path->{if(path.startsWith("/x/web-interface/view?"))return detail(BiliPolicy.UID);
            JSONObject response=new JSONObject(dash);JSONObject video=response.getJSONObject("data").getJSONObject("dash").getJSONArray("video").getJSONObject(0);
            video.put("baseUrl","https://test.bilivideo.cn:8082/video.m4s").put("backupUrl",new JSONArray().put("https://test.bilivideo.com/backup.m4s").put("https://backup.bilivideo.com/backup.m4s"));return response;});
        assertTrue(backup.resolve(ID).video.endsWith("backup.m4s"));
        assertEquals(2,backup.resolve(ID).videoUrls.size());
        BiliClient bad=new BiliClient(path->path.startsWith("/x/web-interface/view?")?detail(BiliPolicy.UID):new JSONObject(dash.replace("test.bilivideo.com","evil.test")));
        assertThrows(IllegalArgumentException.class,()->bad.resolve(ID));
        BiliClient paid=new BiliClient(path->{JSONObject view=detail(BiliPolicy.UID);view.getJSONObject("data").getJSONObject("rights").put("pay",1);return view;});
        assertThrows(java.io.IOException.class,()->paid.resolve(ID));
    }
    @Test public void onlineUiShowsStaleWarningWithoutSearchOrWebView() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();AppStore store=new AppStore(context);
        store.prefs.edit().clear().commit();store.feed(client(BiliPolicy.UID).sync());store.online(true);
        store.prefs.edit().putLong("bili.attempt",System.currentTimeMillis()).putString("bili.error","测试限制 -352（模拟响应）").commit();
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);context.startActivity(launch);
        assertTrue(device.wait(Until.hasObject(By.text("本次更新未完成，保留上次目录")),10000));
        assertTrue(device.hasObject(By.textContains("测试限制 -352")));
        assertFalse(device.hasObject(By.clazz("android.webkit.WebView")));assertFalse(device.hasObject(By.text("搜索")));
        assertFalse(device.hasObject(By.clazz("android.widget.EditText")));
        UiObject2 mode=device.findObject(By.text("体验电视布局"));assertNotNull(mode);mode.click();
        assertTrue(device.wait(Until.hasObject(By.text("切换平板布局")),5000));device.pressDPadDown();assertTrue(device.hasObject(By.focused(true)));
        device.pressHome();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{});
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("approved-bili-sync").getResult().get();
        store.prefs.edit().clear().commit();
    }
}
