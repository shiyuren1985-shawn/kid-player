package family.kidcinema;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** All upload pages are canonical. Collections are optional filters over that catalogue. */
final class BiliCatalog {
    interface Save { void accept(JSONObject feed)throws Exception; }
    private final long uid;
    private final BiliClient client;
    private int requests;
    BiliCatalog(long uid,BiliClient client){this.uid=uid;this.client=client;}
    private JSONObject read(String path)throws Exception{
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        if(requests++>0)Thread.sleep(200);
        return client.api(path);
    }
    JSONObject sync(JSONObject old,boolean manual,Save save)throws Exception{
        LinkedHashMap<String,JSONObject> fresh=new LinkedHashMap<>();
        BiliClient pages=new BiliClient(uid,this::read);int total=-1;
        JSONObject result=null;
        for(int page=1;;page++){
            JSONObject batch=pages.uploadPage(page);
            int count=batch.getInt("total");
            if(total<0)total=count;
            if(total!=count)throw new IOException("读取期间投稿数量发生变化，已保留缓存，请稍后重新更新。");
            JSONArray rows=batch.getJSONArray("videos");
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(fresh.put(row.getString("bvid"),row)!=null)throw new IOException("投稿分页重复或顺序发生变化，未把部分目录当作完整目录。");}
            boolean complete=(long)page*30>=total;
            if(complete&&fresh.size()!=total)throw new IOException("投稿分页数量不一致，未完成全量读取。");
            LinkedHashMap<String,JSONObject> visible=new LinkedHashMap<>(fresh);
            // During a refresh, keep older cached cards until the complete snapshot is confirmed.
            if(!complete){JSONArray cached=old.getJSONArray("videos");for(int i=0;i<cached.length();i++){JSONObject row=cached.getJSONObject(i);visible.putIfAbsent(row.getString("bvid"),row);}}
            result=new JSONObject().put("schema",1).put("uid",uid).put("source","public_uploads")
                .put("syncedAt",complete?System.currentTimeMillis():(old.optBoolean("syncComplete")?old.optLong("syncedAt"):0))
                .put("syncComplete",complete).put("loadedCount",fresh.size()).put("total",total)
                .put("phase",complete?"collections":"uploads").put("videos",new JSONArray(visible.values()))
                .put("collections",old.optJSONArray("collections")==null?new JSONArray():old.getJSONArray("collections"))
                .put("collectionsComplete",false);
            save.accept(result);
            if(complete)break;
        }
        try{
            JSONArray collections=collections(old,manual);
            result.put("collections",collections).put("collectionCount",collections.length()).put("collectionsComplete",true).put("collectionsError","");
            try{AppStore.Creator profile=new BiliClient(uid,this::read).profile();result.put("creatorName",profile.name).put("avatar",profile.avatar);}
            catch(Exception e){if(Thread.currentThread().isInterrupted()||e instanceof InterruptedException)throw e;/* Keep cached profile when this optional request fails. */}
        }catch(Exception e){
            if(Thread.currentThread().isInterrupted()||e instanceof InterruptedException)throw e;
            result.put("collectionsError","合集分类读取未完成："+BiliClient.friendly(e));
        }
        result.put("phase","");save.accept(result);return result;
    }
    private JSONArray collections(JSONObject old,boolean manual)throws Exception{
        JSONArray index=new JSONArray();Set<String> keys=new HashSet<>();int total=-1;
        for(int page=1;;page++){
            JSONObject lists=BiliClient.data(read("/x/polymer/web-space/seasons_series_list?mid="+uid+"&page_num="+page+"&page_size=20")).getJSONObject("items_lists");
            int count=lists.getJSONObject("page").getInt("total");if(count<0||(total>=0&&total!=count))throw new IOException("合集数量发生变化，请稍后重试。");total=count;
            JSONArray seasons=lists.getJSONArray("seasons_list"),series=lists.getJSONArray("series_list");
            if(seasons.length()+series.length()!=Math.min(20,Math.max(0,total-(page-1)*20)))throw new IOException("合集列表分页不完整。");
            for(String kind:new String[]{"season","series"}){
                JSONArray rows=kind.equals("season")?seasons:series;
                for(int i=0;i<rows.length();i++){
                    JSONObject meta=rows.getJSONObject(i).getJSONObject("meta");BiliPolicy.owner(meta.getLong("mid"),uid);
                    long id=meta.getLong(kind+"_id");int size=meta.getInt("total");String key=kind+":"+id;
                    if(id<=0||size<0||!keys.add(key))throw new IOException("合集身份或条目数量无效。");
                    index.put(new JSONObject().put("key",key).put("kind",kind).put("id",id).put("name",meta.getString("name"))
                        .put("total",size).put("revision",meta.toString()));
                }
            }
            if((long)page*20>=total)break;
        }
        JSONArray previous=old.optJSONArray("collections");Map<String,JSONObject> cached=new HashMap<>();
        if(previous!=null)for(int i=0;i<previous.length();i++){JSONObject c=previous.getJSONObject(i);cached.put(c.optString("key"),c);}
        for(int i=0;i<index.length();i++){
            JSONObject collection=index.getJSONObject(i),prior=cached.get(collection.getString("key"));long now=System.currentTimeMillis();
            if(!manual&&prior!=null&&collection.getString("revision").equals(prior.optString("revision"))&&prior.has("bvids")&&now-prior.optLong("checkedAt")>=0&&now-prior.optLong("checkedAt")<86400000L){
                collection.put("bvids",prior.getJSONArray("bvids")).put("checkedAt",prior.getLong("checkedAt"));
            }else collection.put("bvids",members(collection)).put("checkedAt",now);
        }
        return index;
    }
    private JSONArray members(JSONObject collection)throws Exception{
        String kind=collection.getString("kind");long id=collection.getLong("id");int total=collection.getInt("total");
        JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();
        for(int page=1;(long)(page-1)*30<total;page++){
            String path=kind.equals("season")?"/x/polymer/web-space/seasons_archives_list?mid="+uid+"&season_id="+id+"&sort_reverse=true&page_num="+page+"&page_size=30"
                :"/x/series/archives?mid="+uid+"&series_id="+id+"&only_normal=true&sort=desc&pn="+page+"&ps=30";
            JSONObject data=BiliClient.data(read(path)),paging=data.getJSONObject("page");
            if(paging.getInt("total")!=total||paging.getInt(kind.equals("season")?"page_num":"num")!=page)throw new IOException("合集内容分页发生变化。");
            if(kind.equals("season")){JSONObject meta=data.getJSONObject("meta");BiliPolicy.owner(meta.getLong("mid"),uid);if(meta.getLong("season_id")!=id)throw new IOException("合集身份不一致。");}
            JSONArray rows=data.getJSONArray("archives");if(rows.length()!=Math.min(30,total-(page-1)*30))throw new IOException("合集内容分页不完整。");
            for(int i=0;i<rows.length();i++){String bvid=BiliPolicy.bvid(rows.getJSONObject(i).getString("bvid"));if(!seen.add(bvid))throw new IOException("合集内容重复。");result.put(bvid);}
        }
        return result;
    }
}
