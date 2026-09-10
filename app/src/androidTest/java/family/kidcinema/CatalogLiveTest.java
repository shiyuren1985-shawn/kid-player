package family.kidcinema;
import org.junit.*;
import org.json.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;
import static org.junit.Assert.*;
/** Opt-in public catalogue and real background continuation; never downloads videos. */
public class CatalogLiveTest {
 static void report(String text){android.util.Log.i("CatalogLive",text);android.os.Bundle b=new android.os.Bundle();b.putString("stream",text+"\n");InstrumentationRegistry.getInstrumentation().sendStatus(0,b);}
 @Test public void publicCataloguesAndAutomaticContinuation() throws Exception {
  Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("liveBili")));
  android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();BiliSession.configure(context);
  AppStore store=new AppStore(context);store.prefs.edit().clear().commit();store.remoteCreators(false);store.online(true);
  long[] uids={402576555L,84120411L,3546918961547843L};
  for(long uid:uids)WorkManager.getInstance(context).cancelUniqueWork("bili-catalog-resume-"+uid).getResult().get();
  WorkManager.getInstance(context).cancelUniqueWork("approved-bili-sync").getResult().get();
  BiliSyncWorker.testing=false;
  try {for(long uid:uids){
   if(uid!=BiliPolicy.UID)store.addCreator(new AppStore.Creator(uid,"待同步公开作者","",true));
   BiliSync.Outcome initial=BiliSync.run(store,uid,false,new BiliClient(uid));report("LIVE_START uid="+uid+" outcome="+initial+" loaded="+store.feed(uid).optInt("loadedCount")+" total="+store.feed(uid).optInt("total"));
   long deadline=android.os.SystemClock.elapsedRealtime()+240000;int prior=-1;
   while(!store.feed(uid).optBoolean("syncComplete")&&android.os.SystemClock.elapsedRealtime()<deadline){
    if(BiliSync.riskCooling(store))fail(BiliSync.riskMessage(store));
    if(!store.syncError(uid).isEmpty())fail(store.syncError(uid));
    int loaded=store.feed(uid).optInt("loadedCount");if(loaded!=prior){report("LIVE_PROGRESS uid="+uid+" loaded="+loaded+" waitMs="+BiliSync.waitMillis(store,uid,false));prior=loaded;}
    Thread.sleep(500);
   }
   JSONObject feed=store.feed(uid);assertTrue("Automatic continuation must finish "+uid,feed.optBoolean("syncComplete"));
   JSONArray rows=feed.getJSONArray("videos");assertEquals(feed.getInt("total"),rows.length());assertEquals(rows.length(),BiliClient.items(feed,uid).size());
   int union=0;String unionId="";for(int n=0;n<rows.length();n++){JSONObject row=rows.getJSONObject(n);if(row.optLong("publisherUid",uid)!=uid){union++;unionId=row.getString("bvid");}}
   String oldest=rows.getJSONObject(rows.length()-1).getString("title");CatalogSearch.Result search=CatalogSearch.search(store,oldest,false,uid);assertFalse(search.videos.isEmpty());assertEquals(0,search.incomplete);
   report("LIVE_COMPLETE uid="+uid+" loaded="+rows.length()+" total="+feed.getInt("total")+" jointUploads="+union+" oldestSearch=true");
   // Let optional collections/profile work finish before resolving and advancing to another creator.
   while(BiliSync.running(uid)&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(250);
   if(BiliSync.riskCooling(store))fail(BiliSync.riskMessage(store));
   if(!unionId.isEmpty()){new BiliClient(uid).resolve(unionId);report("LIVE_JOINT_RESOLVE uid="+uid+" bvid="+unionId+" success=true");}
  }} finally {
   BiliSyncWorker.testing=true;
   for(long uid:uids)WorkManager.getInstance(context).cancelUniqueWork("bili-catalog-resume-"+uid).getResult().get();
   store.prefs.edit().clear().commit();
  }
 }
}
