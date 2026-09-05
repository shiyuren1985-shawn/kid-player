package family.kidcinema;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Public collections are a distinct, explicitly labelled subset, not the full upload feed. */
final class BiliCollections {
    private final BiliClient.Transport transport;
    private final long uid;
    private final long deadline=System.nanoTime()+90_000_000_000L;
    private int requests;
    BiliCollections(BiliClient.Transport transport){this(BiliPolicy.UID,transport);}
    BiliCollections(long uid,BiliClient.Transport transport){this.uid=BiliPolicy.creatorUid(uid);this.transport=transport;}
    private JSONObject read(String path) throws Exception {
        if(Thread.currentThread().isInterrupted() || System.nanoTime()>deadline)throw new IOException("本次目录同步超时，保留原目录。");
        if(requests++>0)Thread.sleep(150);
        return transport.get(path);
    }
    JSONObject sync() throws Exception {
        String base="/x/polymer/web-space/";
        JSONObject lists=BiliClient.data(read(base+"seasons_series_list?mid="+uid+"&page_num=1&page_size=20")).getJSONObject("items_lists");
        JSONArray seasons=lists.getJSONArray("seasons_list"),series=lists.getJSONArray("series_list");
        int totalLists=lists.getJSONObject("page").getInt("total");
        if(series.length()!=0 || totalLists!=seasons.length() || totalLists>20)
            throw new IOException("该作者的合集结构或数量超出当前支持范围，未用部分目录覆盖原目录。");
        Map<String,Long> candidates=new HashMap<>();Set<Long> collectionIds=new HashSet<>();int scanned=0;
        for(int i=0;i<seasons.length();i++){
            JSONObject meta=seasons.getJSONObject(i).getJSONObject("meta");BiliPolicy.owner(meta.getLong("mid"),uid);
            long season=meta.getLong("season_id");int total=meta.getInt("total");
            if(season<=0 || !collectionIds.add(season) || total<0 || (scanned+=total)>500)
                throw new IOException("合集目录无效或超过 500 条扫描上限。");
            Set<String> seen=new HashSet<>();
            for(int page=1;page<=(total+29)/30;page++){
                JSONObject data=BiliClient.data(read(base+"seasons_archives_list?mid="+uid+"&season_id="+season+"&sort_reverse=true&page_num="+page+"&page_size=30"));
                JSONObject identity=data.getJSONObject("meta"),paging=data.getJSONObject("page");BiliPolicy.owner(identity.getLong("mid"),uid);
                if(identity.getLong("season_id")!=season || paging.getInt("page_num")!=page || paging.getInt("total")!=total)
                    throw new IOException("合集在读取中发生变化或分页不一致，请稍后重试。");
                JSONArray rows=data.getJSONArray("archives");
                if(rows.length()!=Math.min(30,total-(page-1)*30))throw new IOException("合集分页不完整，未覆盖原目录。");
                for(int j=0;j<rows.length();j++){
                    JSONObject row=rows.getJSONObject(j);String id=BiliPolicy.bvid(row.getString("bvid"));long published=row.getLong("pubdate");
                    if(published<=0 || !seen.add(id))throw new IOException("合集包含重复或无效记录。");
                    candidates.merge(id,published,Math::max);
                }
            }
        }
        List<String> sorted=new ArrayList<>(candidates.keySet());
        sorted.sort((a,b)->{int date=Long.compare(candidates.get(b),candidates.get(a));return date!=0?date:a.compareTo(b);});
        JSONArray videos=new JSONArray();int excluded=0;String creatorName="",avatar="";
        for(String id:sorted){
            if(videos.length()>=30)break;
            JSONObject envelope=read("/x/web-interface/view?bvid="+id);
            if(envelope.optInt("code")==-404){excluded++;continue;}
            JSONObject video=BiliClient.data(envelope);
            if(!id.equals(video.getString("bvid")))throw new IOException("视频身份不一致，已停止同步。");
            // A creator's collection may contain collaborations or someone else's upload.
            if(video.getJSONObject("owner").getLong("mid")!=uid || video.getInt("state")!=0 ||
                video.optBoolean("is_upower_exclusive") || video.optInt("is_upower_exclusive")!=0 ||
                video.getJSONObject("rights").optInt("pay")!=0 || video.getJSONObject("rights").optInt("ugc_pay")!=0){excluded++;continue;}
            creatorName=video.getJSONObject("owner").getString("name");
            try{avatar=BiliPolicy.imageUrl(video.getJSONObject("owner").optString("face"));}catch(Exception ignored){}
            int duration=video.getInt("duration");long published=video.getLong("pubdate");
            if(duration<0 || published<=0)throw new IOException("视频元数据无效。");
            videos.put(new JSONObject().put("bvid",id).put("uid",uid).put("title",video.getString("title"))
                .put("author",video.getJSONObject("owner").getString("name")).put("published",published)
                .put("pic",video.optString("pic")).put("duration",String.format(Locale.ROOT,"%02d:%02d",duration/60,duration%60)));
        }
        return new JSONObject().put("schema",1).put("uid",uid).put("syncedAt",System.currentTimeMillis())
            .put("source","public_collections").put("collectionCount",seasons.length()).put("scannedCount",candidates.size())
            .put("excludedCount",excluded).put("creatorName",creatorName).put("avatar",avatar).put("videos",videos);
    }
}
