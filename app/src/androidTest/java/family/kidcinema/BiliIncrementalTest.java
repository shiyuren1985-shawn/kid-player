package family.kidcinema;
import org.junit.*;
import org.json.*;
import static org.junit.Assert.*;
import androidx.test.platform.app.InstrumentationRegistry;

public class BiliIncrementalTest {
    AppStore store;
    @Before public void setup(){store=new AppStore(InstrumentationRegistry.getInstrumentation().getTargetContext());store.prefs.edit().clear().commit();}
    @After public void cleanup(){store.prefs.edit().clear().commit();}
    BiliSync.Outcome run(CatalogFixture f)throws Exception{store.prefs.edit().putLong("bili.attempt",0).commit();return BiliSync.run(store,BiliPolicy.UID,true,new BiliClient(f));}
    @Test public void unchangedAndNewUploadsOnlyReadPrefixButPeriodicScanReconciles()throws Exception{
        CatalogFixture f=new CatalogFixture();assertEquals(BiliSync.Outcome.UPDATED,run(f));long fullAt=store.feed().getLong("fullScanAt");int initial=f.uploadCalls;
        assertEquals(BiliSync.Outcome.UPDATED,run(f));assertEquals(initial+1,f.uploadCalls);assertEquals("incremental",store.feed().getString("syncMode"));
        f.count=70;initial=f.uploadCalls;assertEquals(BiliSync.Outcome.UPDATED,run(f));assertEquals(initial+1,f.uploadCalls);assertEquals(70,store.feed().getJSONArray("videos").length());assertEquals(fullAt,store.feed().getLong("fullScanAt"));
        store.feed(new JSONObject(store.feed().toString()).put("fullScanAt",System.currentTimeMillis()-BiliCatalog.FULL_INTERVAL-1000));initial=f.uploadCalls;run(f);assertEquals(initial+3,f.uploadCalls);assertEquals("full",store.feed().getString("syncMode"));
    }
    @Test public void historyResumesAfterRestartAndChangedHeadRestartsSafely()throws Exception{
        CatalogFixture f=new CatalogFixture();f.count=200;assertEquals(BiliSync.Outcome.PARTIAL,run(f));assertEquals(150,store.feed().getInt("loadedCount"));assertEquals("paused",store.feed().getString("phase"));
        store=new AppStore(InstrumentationRegistry.getInstrumentation().getTargetContext());int reads=f.uploadCalls;assertEquals(BiliSync.Outcome.UPDATED,run(f));assertEquals(reads+4,f.uploadCalls);assertEquals(200,store.feed().getJSONArray("videos").length());assertFalse(store.feed().has("scan"));
        store.prefs.edit().remove("bili.feed").commit();f.count=200;run(f);f.count=201;reads=f.uploadCalls;assertEquals(BiliSync.Outcome.PARTIAL,run(f));assertEquals(reads+5,f.uploadCalls);assertEquals(150,store.feed().getInt("loadedCount"));assertTrue(BiliClient.contains(store.feed(),CatalogFixture.id(201)));
    }
    @Test public void riskCooldownPersistsAcrossCreatorsAndManualRefreshCannotBypass()throws Exception{
        CatalogFixture f=new CatalogFixture();run(f);String good=store.feed().toString();f.failPage=1;
        assertEquals(BiliSync.Outcome.FAILED,run(f));long until=BiliSync.riskUntil(store);assertTrue(until-System.currentTimeMillis()>29*60000);assertEquals(good,store.feed().toString());int reads=f.uploadCalls;
        assertEquals(BiliSync.Outcome.COOLDOWN,run(f));assertEquals(reads,f.uploadCalls);
        AppStore restarted=new AppStore(InstrumentationRegistry.getInstrumentation().getTargetContext());assertFalse(BiliSync.due(restarted,3546918961547843L,true));assertTrue(BiliSync.cooldownMessage(restarted,BiliPolicy.UID).contains("下次可尝试"));
        store.prefs.edit().putLong("bili.riskUntil",System.currentTimeMillis()-1).commit();assertEquals(BiliSync.Outcome.FAILED,run(f));assertTrue(BiliSync.riskUntil(store)-System.currentTimeMillis()>119*60000);
        store.prefs.edit().putLong("bili.riskUntil",System.currentTimeMillis()-1).commit();f.failPage=0;assertEquals(BiliSync.Outcome.UPDATED,run(f));assertFalse(BiliSync.riskCooling(store));assertEquals(0,store.prefs.getInt("bili.riskLevel",0));
    }
    @Test public void ordinaryTimeoutDoesNotPretendToBeRiskControl()throws Exception{
        store.prefs.edit().putLong("bili.attempt",0).commit();BiliClient client=new BiliClient(path->{throw new java.net.SocketTimeoutException();});
        assertEquals(BiliSync.Outcome.FAILED,BiliSync.run(store,BiliPolicy.UID,true,client));assertFalse(BiliSync.riskCooling(store));assertTrue(store.syncError().contains("超时"));
        for(int code:new int[]{403,412,429,-352,-401,-403,-412,-509})assertTrue(BiliAccessException.riskCode(code));assertFalse(BiliAccessException.riskCode(-101));
    }
}
