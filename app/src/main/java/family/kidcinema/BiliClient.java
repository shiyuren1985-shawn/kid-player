package family.kidcinema;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Experimental, anonymous adapter. A platform denial is terminal for this attempt. */
public final class BiliClient {
    interface Transport { JSONObject get(String path) throws Exception; }
    private final Transport transport;
    public BiliClient() { this(BiliClient::request); }
    BiliClient(Transport transport) { this.transport=transport; }
    static final String REFERER="https://www.bilibili.com/";
    static final String UA="Mozilla/5.0";
    private static JSONObject request(String path) throws Exception {
        if(!path.startsWith("/x/"))throw new IOException("接口地址无效");
        HttpURLConnection connection=(HttpURLConnection)new URL("https://api.bilibili.com"+path).openConnection();
        connection.setConnectTimeout(12000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent",UA);connection.setRequestProperty("Referer",REFERER);
        try {
            int status=connection.getResponseCode();
            if(status!=200)throw new IOException("B 站暂未允许访问（HTTP "+status+"）。未跳转或尝试绕过限制。");
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){if(out.size()+n>2_000_000)throw new IOException("接口响应过大");out.write(buffer,0,n);}
                try{return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));}
                catch(JSONException e){throw new IOException("B 站返回了无法识别的响应。");}
            }
        } finally {connection.disconnect();}
    }
    static JSONObject data(JSONObject response) throws Exception {
        int code=response.getInt("code");
        if(code!=0)throw new IOException("B 站接口未允许本次请求（"+code+"）"+
            ((code==-352||code==-403)?"：访问或风控限制。":"：可能需要登录、视频已下架或接口已变化。")+"不会打开 B 站网页。");
        return response.getJSONObject("data");
    }
    private static String imageKey(String url) {
        String file=url.substring(url.lastIndexOf('/')+1);return file.substring(0,file.indexOf('.'));
    }
    public JSONObject sync() throws Exception {
        JSONObject nav=transport.get("/x/web-interface/nav");
        if(nav.getInt("code")!=0 && nav.getInt("code")!=-101)data(nav);
        JSONObject keys=nav.getJSONObject("data").getJSONObject("wbi_img");
        Map<String,String> params=new HashMap<>();params.put("mid",Long.toString(BiliPolicy.UID));params.put("pn","1");params.put("ps","30");params.put("order","pubdate");
        String query=BiliPolicy.signedQuery(params,imageKey(keys.getString("img_url")),imageKey(keys.getString("sub_url")),System.currentTimeMillis()/1000);
        JSONObject response=data(transport.get("/x/space/wbi/arc/search?"+query));
        JSONArray rows=response.getJSONObject("list").getJSONArray("vlist"), videos=new JSONArray();
        if(rows.length()>30)throw new IOException("投稿列表超过本次同步范围");
        Set<String> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);BiliPolicy.owner(row.getLong("mid"));
            String id=BiliPolicy.bvid(row.getString("bvid"));if(!seen.add(id))continue;
            videos.put(new JSONObject().put("bvid",id).put("uid",BiliPolicy.UID).put("title",row.getString("title"))
                .put("author",row.getString("author")).put("published",row.getLong("created")).put("duration",row.optString("length")));
        }
        return new JSONObject().put("schema",1).put("uid",BiliPolicy.UID).put("syncedAt",System.currentTimeMillis()).put("videos",videos);
    }
    public JSONObject syncCollections() throws Exception {return new BiliCollections(transport).sync();}
    public static List<LibraryItem> items(JSONObject feed) throws Exception {
        if(feed.getInt("schema")!=1)throw new IOException("目录格式不支持");BiliPolicy.owner(feed.getLong("uid"));
        JSONArray rows=feed.getJSONArray("videos");if(rows.length()>30)throw new IOException("目录过大");
        List<LibraryItem> items=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);BiliPolicy.owner(row.getLong("uid"));String id=BiliPolicy.bvid(row.getString("bvid"));
            if(!seen.add(id))continue;
            String date=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date(row.getLong("published")*1000));
            items.add(LibraryItem.online(row.getString("title"),id,row.getString("author")+" · "+date+" · "+row.optString("duration"),i%4));
        }
        return items;
    }
    public static boolean contains(JSONObject feed,String id) throws Exception {
        BiliPolicy.bvid(id);for(LibraryItem item:items(feed))if(item.path.equals(id))return true;return false;
    }
    public static final class Playback {
        final String video,audio;final long cid;
        final List<String> videoUrls,audioUrls;
        Playback(List<String> videoUrls,List<String> audioUrls,long cid){
            this.videoUrls=Collections.unmodifiableList(new ArrayList<>(videoUrls));
            this.audioUrls=Collections.unmodifiableList(new ArrayList<>(audioUrls));
            this.video=videoUrls.get(0);this.audio=audioUrls.isEmpty()?null:audioUrls.get(0);this.cid=cid;
        }
    }
    public Playback resolve(String id) throws Exception {
        BiliPolicy.bvid(id);
        JSONObject view=data(transport.get("/x/web-interface/view?bvid="+id));
        if(!id.equals(view.getString("bvid")))throw new IOException("视频身份不匹配");
        BiliPolicy.owner(view.getJSONObject("owner").getLong("mid"));
        if(view.getInt("state")!=0 || view.optBoolean("is_upower_exclusive") || view.optInt("is_upower_exclusive")!=0 || view.getJSONObject("rights").optInt("pay")!=0 || view.getJSONObject("rights").optInt("ugc_pay")!=0)
            throw new IOException("本原型只尝试可公开播放的视频，不处理付费或受限内容。");
        JSONArray pages=view.getJSONArray("pages");
        if(pages.length()==0)throw new IOException("视频没有可播放的分段");
        long cid=pages.getJSONObject(0).getLong("cid");if(cid<=0)throw new IOException("视频分段无效");
        JSONObject play=data(transport.get("/x/player/playurl?bvid="+id+"&cid="+cid+"&qn=32&fnval=16&fourk=0"));
        if(play.optInt("is_preview")!=0 || play.optBoolean("is_preview"))throw new IOException("不播放受限视频的预览片段。");
        if(play.has("dash")){
            JSONObject dash=play.getJSONObject("dash");JSONArray video=dash.getJSONArray("video"),audio=dash.getJSONArray("audio");
            JSONObject choice=null;int quality=-1;
            for(int i=0;i<video.length();i++){JSONObject stream=video.getJSONObject(i);int q=stream.getInt("id");
                if(stream.optString("codecs").startsWith("avc") && q<=32 && q>quality){choice=stream;quality=q;}}
            if(choice==null)throw new IOException("没有适合此原型的公开 H.264 清晰度。");
            JSONObject sound=null;for(int i=0;i<audio.length();i++)if(audio.getJSONObject(i).optString("codecs").startsWith("mp4a")){sound=audio.getJSONObject(i);break;}
            if(sound==null)throw new IOException("没有兼容的公开音轨。");
            return new Playback(streamUrls(choice),streamUrls(sound),cid);
        }
        JSONArray urls=play.getJSONArray("durl");if(urls.length()!=1)throw new IOException("暂不支持此视频的多段流格式。");
        return new Playback(streamUrls(urls.getJSONObject(0)),Collections.emptyList(),cid);
    }
    private static List<String> streamUrls(JSONObject stream) throws Exception {
        List<String> candidates=new ArrayList<>();
        candidates.add(stream.has("baseUrl")?stream.getString("baseUrl"):stream.has("base_url")?stream.getString("base_url"):stream.optString("url"));
        JSONArray backups=stream.optJSONArray("backupUrl");if(backups==null)backups=stream.optJSONArray("backup_url");
        if(backups!=null)for(int i=0;i<backups.length();i++)candidates.add(backups.getString(i));
        List<String> allowed=new ArrayList<>();
        for(String candidate:candidates)try{String url=BiliPolicy.mediaUrl(candidate);if(!allowed.contains(url))allowed.add(url);}catch(IllegalArgumentException ignored){}
        if(allowed.isEmpty())throw new IllegalArgumentException("本次返回的播放地址均不在允许的视频域名和端口内。");
        return allowed;
    }
    static String friendly(Exception e) {
        if(e instanceof java.net.SocketTimeoutException)return "连接超时，请检查网络后重试。";
        if(e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException)return "暂时无法连接视频服务，请检查 Wi-Fi 或移动网络后重试。";
        if(e instanceof IOException || e instanceof IllegalArgumentException)return e.getMessage()==null?"连接失败，请稍后重试。":e.getMessage();
        return "在线目录或播放接口格式发生变化，已停止本次请求。";
    }
}
