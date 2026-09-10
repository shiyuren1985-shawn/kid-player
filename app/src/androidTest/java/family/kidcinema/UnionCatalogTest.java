package family.kidcinema;
import org.junit.*;
import org.json.*;
import androidx.test.platform.app.InstrumentationRegistry;
import static org.junit.Assert.*;

public class UnionCatalogTest {
 AppStore store;
 @Before public void setup(){store=new AppStore(InstrumentationRegistry.getInstrumentation().getTargetContext());store.prefs.edit().clear().commit();store.remoteCreators(false);}
 @After public void cleanup(){store.prefs.edit().clear().commit();}
 static final long PUBLISHER=92601724L;
 static class UnionFixture implements BiliClient.Transport {
  final CatalogFixture base=new CatalogFixture(); boolean union=true,credited=true; int jointId,views,plays;
  JSONObject detail(String id)throws Exception{return new JSONObject().put("bvid",id).put("state",0).put("rights",new JSONObject()).put("owner",new JSONObject().put("mid",PUBLISHER))
    .put("staff",new JSONArray().put(new JSONObject().put("mid",PUBLISHER)).put(new JSONObject().put("mid",credited?base.uid:42)))
    .put("pages",new JSONArray().put(new JSONObject().put("cid",123)));}
  public JSONObject get(String path)throws Exception{
   if(path.contains("/view?")){views++;String id=android.net.Uri.parse("https://fixture.invalid"+path).getQueryParameter("bvid");return CatalogFixture.ok(detail(id));}
   if(path.contains("/playurl?")){plays++;return CatalogFixture.ok(new JSONObject().put("durl",new JSONArray().put(new JSONObject().put("url","https://test.bilivideo.com/test.mp4"))));}
   JSONObject response=base.get(path);
   if(path.contains("/arc/search")&&response.optInt("code")==0){JSONArray rows=response.getJSONObject("data").getJSONObject("list").getJSONArray("vlist");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.getString("bvid").equals(CatalogFixture.id(jointId)))row.put("mid",PUBLISHER).put("author","模拟联合发布者").put("is_union_video",union?1:0);}}
   return response;
  }
 }
 BiliSync.Outcome run(UnionFixture f){store.prefs.edit().putLong(store.biliKey(f.base.uid,"attempt"),0).commit();return BiliSync.run(store,f.base.uid,true,new BiliClient(f.base.uid,f));}
 void repair(int total,int prefix,long uid)throws Exception{
  if(uid!=BiliPolicy.UID)store.addCreator(new AppStore.Creator(uid,"模拟合作作者","",true));
  UnionFixture f=new UnionFixture();f.base.uid=uid;f.base.count=total;f.base.groups=0;f.jointId=total-prefix;f.union=false;
  assertEquals(BiliSync.Outcome.FAILED,run(f));assertEquals(prefix,store.feed(uid).getInt("loadedCount"));assertFalse(BiliSync.riskCooling(store));
  JSONObject partial=store.feed(uid);partial.getJSONObject("scan").put("startedAt",System.currentTimeMillis()-48*3600000L);store.feed(uid,partial);
  f.union=true;int reads=f.base.uploadCalls;BiliSync.Outcome outcome=run(f);
  // First page + prior boundary are checked; a two-day-old checkpoint must not restart at page one.
  int remainingPages=(total+29)/30-prefix/30;
  assertEquals(Math.min(5,remainingPages)+(prefix==30?1:2),f.base.uploadCalls-reads);
  int rounds=1;while(outcome==BiliSync.Outcome.PARTIAL){assertTrue(++rounds<5);outcome=run(f);}
  assertEquals(BiliSync.Outcome.UPDATED,outcome);assertEquals(total,BiliClient.items(store.feed(uid),uid).size());assertTrue(store.feed(uid).getBoolean("syncComplete"));
  assertTrue(BiliClient.contains(store.feed(uid),CatalogFixture.id(f.jointId),uid));assertTrue(BiliClient.contains(store.feed(uid),CatalogFixture.id(1),uid));
  CatalogSearch.Result result=CatalogSearch.search(store,"模拟投稿 1",false,uid);assertFalse(result.videos.isEmpty());assertEquals(0,result.incomplete);
  new BiliClient(uid,f).resolve(CatalogFixture.id(f.jointId));assertEquals(1,f.plays);
  f.credited=false;assertThrows(IllegalArgumentException.class,()->new BiliClient(uid,f).resolve(CatalogFixture.id(f.jointId)));assertEquals(1,f.plays);
 }
 @Test public void recoversNinetyOf276AndSearchesOldestUploads()throws Exception{repair(276,90,BiliPolicy.UID);}
 @Test public void recoversThirtyOf296ForSecondCreator()throws Exception{repair(296,30,84120411L);}
 @Test public void sameFixWorksForLargerOtherCreator()throws Exception{repair(601,90,1234567L);}
 @Test public void unionFlagAloneCannotAuthorizeUnrelatedAuthor()throws Exception{
  UnionFixture f=new UnionFixture();f.base.count=65;f.jointId=65;f.credited=false;
  assertEquals(BiliSync.Outcome.FAILED,run(f));assertFalse(BiliClient.contains(store.feed(),CatalogFixture.id(65)));assertEquals(0,f.plays);assertFalse(BiliSync.riskCooling(store));
  JSONObject detail=f.detail(CatalogFixture.id(65));assertThrows(java.io.IOException.class,()->BiliClient.verifyContributor(detail,CatalogFixture.id(64),BiliPolicy.UID));
 }
 @Test public void catalogueReusesRecentVerifiedCreditButPlaybackAlwaysRechecks()throws Exception{
  UnionFixture f=new UnionFixture();f.base.count=65;f.jointId=65;assertEquals(BiliSync.Outcome.UPDATED,run(f));assertEquals(1,f.views);
  assertEquals(BiliSync.Outcome.UPDATED,run(f));assertEquals(1,f.views);
  JSONObject feed=store.feed();feed.getJSONArray("videos").getJSONObject(0).put("contributorCheckedAt",System.currentTimeMillis()-BiliCatalog.FULL_INTERVAL-1000);store.feed(feed);
  assertEquals(BiliSync.Outcome.UPDATED,run(f));assertEquals(2,f.views);
  f.credited=false;assertThrows(IllegalArgumentException.class,()->new BiliClient(f).resolve(CatalogFixture.id(65)));assertEquals(3,f.views);assertEquals(0,f.plays);
 }
 @Test public void partialUiOffersContinuationAndSavedProgress()throws Exception{
  UnionFixture f=new UnionFixture();f.base.count=276;assertEquals(BiliSync.Outcome.PARTIAL,run(f));store.online(true);
  android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
  android.content.Intent launch=new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
  context.startActivity(launch);
  try {
   assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("继续读取")),10000));
   assertTrue(device.hasObject(androidx.test.uiautomator.By.textContains("150 / 共 276")));
   assertTrue(device.hasObject(androidx.test.uiautomator.By.textContains("秒后自动续读")));
  } finally {device.pressHome();}
 }
 @Test public void incompleteAutoCooldownIsShortButErrorsAndRiskStillBackOff()throws Exception{
  UnionFixture f=new UnionFixture();f.base.count=276;f.jointId=0;assertEquals(BiliSync.Outcome.PARTIAL,run(f));
  long uid=BiliPolicy.UID;assertTrue(BiliSync.waitMillis(store,uid,false)>59000);assertTrue(BiliSync.waitMillis(store,uid,false)<=60000);
  store.prefs.edit().putLong(store.biliKey(uid,"attempt"),System.currentTimeMillis()-61000).commit();assertTrue(BiliSync.due(store,uid,false));
  store.prefs.edit().putString(store.biliKey(uid,"error"),"模拟超时").commit();assertFalse(BiliSync.due(store,uid,false));assertTrue(BiliSync.due(store,uid,true));
  store.prefs.edit().putLong("bili.riskUntil",System.currentTimeMillis()+12*3600000L).commit();assertFalse(BiliSync.due(store,uid,true));assertFalse(BiliSync.due(store,uid,false));
 }
}
