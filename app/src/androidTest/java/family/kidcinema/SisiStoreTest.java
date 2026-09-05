package family.kidcinema;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

/** Only synthetic documents and injected HTTP responses; no VPS or new real creators. */
public class SisiStoreTest {
    private AppStore store;
    @Before public void before(){store=new AppStore(InstrumentationRegistry.getInstrumentation().getTargetContext());store.prefs.edit().clear().commit();}
    @After public void after(){store.prefs.edit().clear().commit();}
    private JSONObject document(String rows)throws Exception{return new JSONObject("{\"schema\":1,\"creators\":"+rows+"}");}
    private boolean sync(JSONObject doc)throws Exception{store.prefs.edit().putLong("remote.attempt",0).commit();return RemoteConfig.refresh(store,"https://example.com/sisi.json",true,url->doc);}
    @Test public void cloudIsDefaultAndLocalUidEditsCannotChangeIt()throws Exception{
        assertTrue(store.remoteCreators());assertEquals(402576555L,store.selectedCreator());
        assertThrows(IllegalStateException.class,()->store.addCreator(new AppStore.Creator(123,"测试作者","",true)));
        assertTrue(sync(document("[{\"uid\":\"123\",\"name\":\"测试作者甲\"},{\"uid\":456,\"enabled\":false}]")));
        assertEquals(2,store.creators().size());assertEquals(1,store.enabledCreators().size());assertEquals(123,store.selectedCreator());
        assertFalse(store.allowedCreator(BiliPolicy.UID));assertFalse(store.allowedCreator(456));
        assertThrows(IllegalStateException.class,()->store.creatorEnabled(456,true));
    }
    @Test public void invalidDocumentAndNetworkFailureKeepLastGoodList()throws Exception{
        assertTrue(sync(document("[{\"uid\":123}]")));String good=store.prefs.getString("creators.remote","");
        assertFalse(sync(document("[{\"uid\":456},{\"uid\":456}]")));assertEquals(good,store.prefs.getString("creators.remote",""));
        store.prefs.edit().putLong("remote.attempt",0).commit();assertFalse(RemoteConfig.refresh(store,store.remoteUrl(),true,url->{throw new java.io.IOException();}));
        assertEquals(good,store.prefs.getString("creators.remote",""));assertFalse(store.prefs.getString("remote.error","").isEmpty());
        assertFalse(sync(document("[{\"uid\":123,\"enabled\":\"false\"}]")));
        assertFalse(sync(document("[{\"uid\":-1}]")));
    }
    @Test public void explicitEmptyCloudListRevokesAllInsteadOfRestoringDefault()throws Exception{
        assertTrue(sync(document("[]")));assertTrue(store.creators().isEmpty());assertEquals(0,store.selectedCreator());assertFalse(store.allowedCreator(BiliPolicy.UID));
        assertEquals(BiliSync.Outcome.DISABLED,BiliSync.run(store,BiliPolicy.UID,true,new BiliClient(path->{fail("Disabled author must not request");return null;})));
    }
    @Test public void localAndCloudModesKeepSeparateLists()throws Exception{
        store.remoteCreators(false);store.addCreator(new AppStore.Creator(111,"测试本机作者","",true));
        store.remoteCreators(true);assertTrue(sync(document("[{\"uid\":222}]")));assertNull(store.creator(111));
        store.remoteCreators(false);assertNotNull(store.creator(111));assertNull(store.creator(222));
        store.remoteCreators(true);assertTrue(store.allowedCreator(222));
    }
    @Test public void feedsAndHistoryAreIsolatedAndOriginalKeysRemainReadable()throws Exception{
        store.online(true);store.prefs.edit().putLong("progress:bili:402576555:bili:BV1Yptj6zEEG",27000).commit();assertEquals(27000,store.progress("bili:BV1Yptj6zEEG"));
        store.remoteCreators(false);store.addCreator(new AppStore.Creator(123,"测试作者","",true));store.selectedCreator(123);
        assertEquals(0,store.progress("bili:BV1Yptj6zEEG"));assertEquals(0,store.feed().getJSONArray("videos").length());
        assertThrows(IllegalArgumentException.class,()->store.feed(123,store.feed(BiliPolicy.UID)));
        store.selectedCreator(BiliPolicy.UID);assertEquals(27000,store.progress("bili:BV1Yptj6zEEG"));
    }
    @Test public void automaticAndManualCooldownsAreIndependentAcrossAuthors()throws Exception{
        store.remoteCreators(false);store.addCreator(new AppStore.Creator(123,"测试作者","",true));
        store.prefs.edit().putLong("bili.attempt",System.currentTimeMillis()-120000).commit();
        assertFalse(BiliSync.due(store,BiliPolicy.UID,false));assertTrue(BiliSync.due(store,BiliPolicy.UID,true));assertTrue(BiliSync.due(store,123,false));
    }
    @Test public void smbHistoryIncludesNestedFilesAndLegacyKeysWithinSameScope()throws Exception{
        AppStore.Config c=new AppStore.Config();c.host="10.0.2.2";c.port=1445;c.share="KIDS";store.save(c);store.demo(false);
        LibraryItem item=new LibraryItem("太空","children/space.mp4","测试",false,false,0);store.remember(Collections.singletonList(item));store.toggleFavorite(item.key());store.progress(item.key(),4000);
        assertEquals("children/space.mp4",store.history(true).get(0).path);assertEquals("children/space.mp4",store.history(false).get(0).path);
        store.prefs.edit().putBoolean("favorite:"+store.scope()+":smb:older/old.mp4",true).commit();assertEquals(2,store.history(true).size());
        c.root="another";store.save(c);assertTrue(store.history(true).isEmpty());
    }
    @Test public void secondCreatorCollectionScanAndPlaybackVerifyThatCreator()throws Exception{
        BiliCollectionsTest.Fixture base=new BiliCollectionsTest.Fixture();base.collectionOwner=123;
        BiliClient client=new BiliClient(123,path->{JSONObject reply=base.get(path);if(path.contains("web-interface/view"))reply.getJSONObject("data").getJSONObject("owner").put("mid",123);return reply;});
        JSONObject feed=client.syncCollections();assertEquals(123,feed.getLong("uid"));assertEquals(2,BiliClient.items(feed,123).size());
        assertThrows(IllegalArgumentException.class,()->BiliClient.items(feed,BiliPolicy.UID));
        BiliClient wrong=new BiliClient(123,path->new JSONObject().put("code",0).put("data",new JSONObject().put("bvid","BV1Yptj6zEEG").put("owner",new JSONObject().put("mid",456))));
        assertThrows(IllegalArgumentException.class,()->wrong.resolve("BV1Yptj6zEEG"));
    }
    @Test public void sourceAndImageUrlsRejectNonHttpsOrUnrelatedImages(){
        assertThrows(IllegalArgumentException.class,()->RemoteConfig.checkedUrl("http://example.com/config.json"));
        assertThrows(IllegalArgumentException.class,()->RemoteConfig.checkedUrl("https://user:secret@example.com/config.json"));
        assertThrows(IllegalArgumentException.class,()->BiliPolicy.imageUrl("https://example.com/photo.jpg"));
        assertEquals("https://i0.hdslb.com/bfs/face/test.jpg",BiliPolicy.imageUrl("http://i0.hdslb.com/bfs/face/test.jpg"));
    }
}
