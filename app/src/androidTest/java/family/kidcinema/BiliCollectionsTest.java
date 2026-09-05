package family.kidcinema;

import org.json.*;
import org.junit.Test;
import androidx.test.platform.app.InstrumentationRegistry;
import static org.junit.Assert.*;
import java.io.IOException;

/** Generated response fixtures, not real creator uploads. */
public class BiliCollectionsTest {
    static String id(int n){return String.format(java.util.Locale.ROOT,"BV%010d",n);}
    static JSONObject ok(JSONObject data)throws Exception{return new JSONObject().put("code",0).put("data",data);}
    static final class Fixture implements BiliClient.Transport {
        int count=2,pages,details;long collectionOwner=BiliPolicy.UID;int foreign=-1;boolean denied,incomplete;
        JSONObject meta()throws Exception{return new JSONObject().put("mid",collectionOwner).put("season_id",123).put("total",count);}
        public JSONObject get(String path)throws Exception {
            if(denied)return new JSONObject().put("code",-352);
            if(path.contains("seasons_series_list"))return ok(new JSONObject().put("items_lists",new JSONObject()
                .put("page",new JSONObject().put("total",1)).put("series_list",new JSONArray())
                .put("seasons_list",new JSONArray().put(new JSONObject().put("meta",meta())))));
            if(path.contains("seasons_archives_list")){
                pages++;int page=path.contains("page_num=2")?2:1;JSONArray rows=new JSONArray();
                // Deliberately oldest first: the collector must scan all pages before selecting.
                for(int n=(page-1)*30+1;n<=Math.min(page*30,count);n++)rows.put(new JSONObject().put("bvid",id(n)).put("pubdate",1700000000+n));
                if(incomplete)rows=new JSONArray();
                return ok(new JSONObject().put("meta",meta()).put("page",new JSONObject().put("page_num",page).put("total",count)).put("archives",rows));
            }
            assertTrue(path.startsWith("/x/web-interface/view?bvid=BV"));details++;int n=Integer.parseInt(path.substring(path.indexOf("BV")+2));
            return ok(new JSONObject().put("bvid",id(n)).put("state",0).put("owner",new JSONObject().put("mid",n==foreign?1:BiliPolicy.UID).put("name","测试作者"))
                .put("rights",new JSONObject().put("pay",0)).put("title","已核验测试标题 "+n).put("duration",65).put("pubdate",1700000000+n));
        }
    }
    @Test public void completeScanThenSortAndCap()throws Exception {
        Fixture f=new Fixture();f.count=31;JSONObject feed=new BiliClient(f).syncCollections();
        assertEquals(2,f.pages);assertEquals(30,f.details);assertEquals(30,feed.getJSONArray("videos").length());
        assertEquals(id(31),feed.getJSONArray("videos").getJSONObject(0).getString("bvid"));
        assertFalse(BiliClient.contains(feed,id(1)));assertEquals("public_collections",feed.getString("source"));assertEquals(31,feed.getInt("scannedCount"));
    }
    @Test public void videoOwnerIsCheckedSeparatelyFromCollectionOwner()throws Exception {
        Fixture f=new Fixture();f.foreign=2;JSONObject feed=new BiliClient(f).syncCollections();
        assertEquals(1,feed.getJSONArray("videos").length());assertFalse(BiliClient.contains(feed,id(2)));assertEquals(1,feed.getInt("excludedCount"));
        assertEquals("已核验测试标题 1",feed.getJSONArray("videos").getJSONObject(0).getString("title"));
    }
    @Test public void wrongCollectionOwnerStopsBeforeListing()throws Exception {
        Fixture f=new Fixture();f.collectionOwner=1;assertThrows(IllegalArgumentException.class,()->new BiliClient(f).syncCollections());assertEquals(0,f.pages);
    }
    @Test public void incompletePagesAreNotSuccessfulFeeds()throws Exception {
        Fixture f=new Fixture();f.incomplete=true;assertThrows(IOException.class,()->new BiliClient(f).syncCollections());assertEquals(0,f.details);
    }
    @Test public void refreshImportsNewVideosAndFailurePreservesLastGoodDocument()throws Exception {
        AppStore store=new AppStore(InstrumentationRegistry.getInstrumentation().getTargetContext());store.prefs.edit().clear().commit();
        Fixture fixture=new Fixture();BiliClient client=new BiliClient(fixture);
        BiliSync.run(store,true,client);assertEquals(2,BiliClient.items(store.feed()).size());assertEquals("",store.syncError());
        fixture.count=3;store.prefs.edit().putLong("bili.attempt",0).commit();BiliSync.run(store,false,client);
        assertEquals(id(3),BiliClient.items(store.feed()).get(0).path);assertEquals(3,BiliClient.items(store.feed()).size());
        String good=store.feed().toString();fixture.denied=true;store.prefs.edit().putLong("bili.attempt",0).commit();BiliSync.run(store,false,client);
        assertEquals(good,store.feed().toString());assertTrue(store.syncError().contains("-352"));store.prefs.edit().clear().commit();
    }
    @Test public void refreshCooldownDoesNotMakeNetworkRequests()throws Exception {
        AppStore store=new AppStore(InstrumentationRegistry.getInstrumentation().getTargetContext());store.prefs.edit().clear().putLong("bili.attempt",System.currentTimeMillis()).commit();
        BiliSync.run(store,true,new BiliClient(path->{fail("Must not request during cooldown");return null;}));store.prefs.edit().clear().commit();
    }
}
