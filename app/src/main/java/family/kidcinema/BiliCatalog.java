package family.kidcinema;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** All upload pages are canonical. Collections are optional filters over that catalogue. */
final class BiliCatalog {
    interface Save { void accept(JSONObject feed)throws Exception; }
    private final long uid;
    private final BiliClient client;
    BiliCatalog(long uid,BiliClient client){this.uid=uid;this.client=client;}
    private JSONObject read(String path)throws Exception{
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        return client.api(path);
    }
    static final int PAGE_BUDGET=5;
    static final long FULL_INTERVAL=86400000L;
    private static JSONArray array(JSONObject value,String key){JSONArray a=value.optJSONArray(key);return a==null?new JSONArray():a;}
    private static boolean samePage(JSONArray a,JSONArray b)throws Exception{
        if(a.length()!=b.length())return false;
        for(int i=0;i<a.length();i++)if(!a.getJSONObject(i).getString("bvid").equals(b.getJSONObject(i).getString("bvid"))||a.getJSONObject(i).getLong("published")!=b.getJSONObject(i).getLong("published"))return false;
        return true;
    }
    // Only join a new prefix to an unchanged, consecutive prefix of a recent complete catalogue.
    private static boolean joins(JSONObject old,LinkedHashMap<String,JSONObject> fresh,int total)throws Exception{
        JSONArray prior=old.getJSONArray("videos");int added=total-prior.length();
        if(added<0||fresh.size()<added+Math.min(10,prior.length()))return false;
        Set<String> known=new HashSet<>();for(int i=0;i<prior.length();i++)known.add(prior.getJSONObject(i).getString("bvid"));
        int index=0;for(JSONObject row:fresh.values()){
            if(index<added){if(known.contains(row.getString("bvid")))return false;}
            else {int j=index-added;if(j>=prior.length()||!samePage(new JSONArray().put(row),new JSONArray().put(prior.getJSONObject(j))))return false;}
            index++;
        }
        return true;
    }
    JSONObject sync(JSONObject old,boolean manual,Save save)throws Exception{
        long now=System.currentTimeMillis(),fullAt=old.optLong("fullScanAt");
        boolean incremental=old.optBoolean("syncComplete")&&fullAt>0&&now-fullAt>=0&&now-fullAt<FULL_INTERVAL;
        LinkedHashMap<String,JSONObject> fresh=new LinkedHashMap<>();
        BiliClient pages=new BiliClient(uid,this::read);pages.cachedContributors(old);
        JSONObject first=pages.uploadPage(1),result=null;
        int total=first.getInt("total"),page=1,newPages=0;long started=now;
        // Durable progress survives long cooldowns; head, total and boundary are revalidated below.
        JSONObject checkpoint=old.optJSONObject("scan");
        if(!incremental&&checkpoint!=null&&checkpoint.optInt("total",-1)==total&&now-checkpoint.optLong("startedAt")>=0){
            int next=checkpoint.optInt("nextPage",1);JSONArray saved=array(checkpoint,"videos");
            if(next>1&&saved.length()==(next-1)*30&&samePage(array(checkpoint,"firstPage"),first.getJSONArray("videos"))){
                JSONObject boundary=next==2?first:pages.uploadPage(next-1);
                if(boundary.getInt("total")==total&&samePage(array(checkpoint,"lastPage"),boundary.getJSONArray("videos"))){
                    for(int i=0;i<saved.length();i++){JSONObject row=saved.getJSONObject(i);BiliPolicy.owner(row.getLong("uid"),uid);if(fresh.put(row.getString("bvid"),row)!=null)throw new IOException("历史读取进度重复，请重新核对。");}
                    page=next;started=checkpoint.getLong("startedAt");
                }
            }
        }
        for(;;page++){
            JSONObject batch=page==1?first:pages.uploadPage(page);
            if(total!=batch.getInt("total"))throw new IOException("读取期间投稿数量发生变化，已保留缓存，请稍后重新更新。");
            JSONArray rows=batch.getJSONArray("videos");
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(fresh.put(row.getString("bvid"),row)!=null)throw new IOException("投稿分页重复或顺序发生变化，未把部分目录当作完整目录。");}
            newPages++;
            boolean full=(long)page*30>=total;
            if(full&&fresh.size()!=total)throw new IOException("投稿分页数量不一致，未完成全量读取。");
            boolean joined=!full&&incremental&&joins(old,fresh,total),complete=full||joined;
            LinkedHashMap<String,JSONObject> visible=new LinkedHashMap<>(fresh);
            if(!full){JSONArray cached=old.getJSONArray("videos");for(int i=0;i<cached.length();i++){JSONObject row=cached.getJSONObject(i);visible.putIfAbsent(row.getString("bvid"),row);}}
            result=new JSONObject().put("schema",1).put("uid",uid).put("source","public_uploads")
                .put("syncedAt",complete?now:old.optLong("syncedAt"))
                .put("syncComplete",complete).put("loadedCount",complete?visible.size():fresh.size()).put("total",total)
                .put("fullScanAt",full?now:fullAt).put("syncMode",joined?"incremental":"full")
                .put("phase",complete?"collections":"uploads").put("videos",new JSONArray(visible.values()))
                .put("collections",array(old,"collections")).put("collectionsComplete",false);
            if(!complete)result.put("scan",new JSONObject().put("total",total).put("nextPage",page+1).put("startedAt",started)
                .put("firstPage",first.getJSONArray("videos")).put("lastPage",rows).put("videos",new JSONArray(fresh.values())));
            save.accept(result);
            if(complete)break;
            if(newPages>=PAGE_BUDGET){result.put("phase","paused");save.accept(result);return result;}
        }
        try{
            JSONArray collections=collections(old,manual);
            result.put("collections",collections).put("collectionCount",collections.length()).put("collectionsComplete",true).put("collectionsError","");
            try{AppStore.Creator profile=new BiliClient(uid,this::read).profile();result.put("creatorName",profile.name).put("avatar",profile.avatar);}
            catch(Exception e){if(Thread.currentThread().isInterrupted()||e instanceof InterruptedException||e instanceof BiliAccessException)throw e;}
        }catch(Exception e){
            if(Thread.currentThread().isInterrupted()||e instanceof InterruptedException)throw e;
            result.put("collectionsError","合集或作者资料读取未完成："+BiliClient.friendly(e));
            if(e instanceof BiliAccessException){result.put("phase","");save.accept(result);throw e;}
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
            if(prior!=null&&collection.getString("revision").equals(prior.optString("revision"))&&prior.has("bvids")&&now-prior.optLong("checkedAt")>=0&&now-prior.optLong("checkedAt")<86400000L){
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
