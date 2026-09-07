package family.kidcinema;

import org.junit.*;
import org.json.*;
import static org.junit.Assert.*;
import androidx.test.platform.app.InstrumentationRegistry;

public class BiliCatalogTest {
    private AppStore store;
    @Before public void before(){store=new AppStore(InstrumentationRegistry.getInstrumentation().getTargetContext());store.prefs.edit().clear().commit();}
    @After public void after(){store.prefs.edit().clear().commit();}
    private BiliSync.Outcome run(CatalogFixture f)throws Exception{store.prefs.edit().putLong("bili.attempt",0).remove("bili.riskUntil").commit();return BiliSync.run(store,BiliPolicy.UID,true,new BiliClient(f));}
    @Test public void all601UploadsSurviveDiskReloadAndCollectionsDoNotLimitThem()throws Exception{
        CatalogFixture f=new CatalogFixture();f.count=601;
        BiliSync.Outcome outcome;int rounds=0;do{outcome=run(f);rounds++;assertTrue(rounds<=5);}while(outcome==BiliSync.Outcome.PARTIAL);assertEquals(BiliSync.Outcome.UPDATED,outcome);assertEquals(5,rounds);assertEquals(29,f.uploadCalls);
        JSONObject feed=new AppStore(InstrumentationRegistry.getInstrumentation().getTargetContext()).feed();
        assertEquals(601,BiliClient.items(feed).size());assertEquals(601,feed.getInt("total"));assertTrue(feed.getBoolean("syncComplete"));assertTrue(BiliClient.contains(feed,CatalogFixture.id(1)));
        assertEquals(35,feed.getJSONArray("collections").getJSONObject(0).getJSONArray("bvids").length());
    }
    @Test public void zeroCollectionsStillLoadEveryPage()throws Exception{
        CatalogFixture f=new CatalogFixture();f.groups=0;assertEquals(BiliSync.Outcome.UPDATED,run(f));assertEquals(65,store.feed().getJSONArray("videos").length());assertEquals(0,store.feed().getJSONArray("collections").length());
    }
    @Test public void progressiveSaveRetainsOldCacheAndNeverClaimsPartialSuccess()throws Exception{
        CatalogFixture f=new CatalogFixture();run(f);long prior=store.feed().getLong("syncedAt");store.feed(new JSONObject(store.feed().toString()).put("fullScanAt",0));f.count=70;f.failPage=2;
        assertEquals(BiliSync.Outcome.FAILED,run(f));JSONObject feed=store.feed();assertFalse(feed.getBoolean("syncComplete"));assertEquals(30,feed.getInt("loadedCount"));assertEquals(70,feed.getInt("total"));assertEquals(prior,feed.getLong("syncedAt"));
        assertTrue(BiliClient.contains(feed,CatalogFixture.id(70)));assertTrue(BiliClient.contains(feed,CatalogFixture.id(1)));assertTrue(store.syncError().contains("-352"));assertTrue(BiliSync.cooldownMessage(store,BiliPolicy.UID).contains("下次可尝试"));
        f.failPage=0;assertEquals(BiliSync.Outcome.UPDATED,run(f));assertEquals(70,store.feed().getJSONArray("videos").length());assertTrue(store.feed().getBoolean("syncComplete"));
    }
    @Test public void completedSnapshotRemovesDeletedEntriesButFailureDoesNot()throws Exception{
        CatalogFixture f=new CatalogFixture();run(f);f.count=64;f.failPage=1;assertEquals(BiliSync.Outcome.FAILED,run(f));assertTrue(BiliClient.contains(store.feed(),CatalogFixture.id(65)));
        f.failPage=0;assertEquals(BiliSync.Outcome.UPDATED,run(f));assertFalse(BiliClient.contains(store.feed(),CatalogFixture.id(65)));assertEquals(64,BiliClient.items(store.feed()).size());
    }
    @Test public void untrustedOwnerCannotReplaceCache()throws Exception{
        CatalogFixture f=new CatalogFixture();run(f);String old=store.feed().toString();f.foreign=true;assertEquals(BiliSync.Outcome.FAILED,run(f));assertEquals(old,store.feed().toString());
    }
    @Test public void optionalCollectionFailureCannotHideUploads()throws Exception{
        CatalogFixture f=new CatalogFixture();f.denyGroups=true;assertEquals(BiliSync.Outcome.FAILED,run(f));assertEquals(65,BiliClient.items(store.feed()).size());assertTrue(store.feed().getBoolean("syncComplete"));assertTrue(store.feed().getString("collectionsError").contains("-352"));
    }
    @Test public void collectionIndexAndSeriesArePaginatedBeyond20()throws Exception{
        CatalogFixture f=new CatalogFixture();f.groups=21;f.count=2;f.series=true;run(f);assertEquals(21,store.feed().getJSONArray("collections").length());assertEquals(21,f.memberCalls);assertEquals("series",store.feed().getJSONArray("collections").getJSONObject(20).getString("kind"));
    }
    @Test public void automaticRefreshReusesUnchangedCollectionMembership()throws Exception{
        CatalogFixture f=new CatalogFixture();run(f);int reads=f.memberCalls;store.prefs.edit().putLong("bili.attempt",0).commit();
        assertEquals(BiliSync.Outcome.UPDATED,BiliSync.run(store,BiliPolicy.UID,false,new BiliClient(f)));assertEquals(reads,f.memberCalls);
    }
}
