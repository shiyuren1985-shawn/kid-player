package family.kidcinema;

import org.json.*;

/** Generated data only; no network or real creator videos. */
final class CatalogFixture implements BiliClient.Transport {
    long uid=BiliPolicy.UID;int count=65,groups=1,failPage=0,uploadCalls,memberCalls;boolean foreign,denyGroups,series;
    String prefix="模拟投稿 ";
    static String id(int n){return String.format(java.util.Locale.ROOT,"BV%010d",n);}
    static JSONObject ok(JSONObject value)throws Exception{return new JSONObject().put("code",0).put("data",value);}
    int param(String path,String name){return Integer.parseInt(android.net.Uri.parse("https://fixture.invalid"+path).getQueryParameter(name));}
    JSONObject meta(int n)throws Exception{return new JSONObject().put("mid",uid).put(series?"series_id":"season_id",n).put("name","模拟合集 "+n).put("total",Math.min(count,35));}
    @Override public JSONObject get(String path)throws Exception{
        if(path.contains("/card?"))return ok(new JSONObject().put("card",new JSONObject().put("mid",Long.toString(uid)).put("name","模拟作者").put("face","")));
        if(path.endsWith("/nav"))return ok(new JSONObject().put("wbi_img",new JSONObject().put("img_url","https://i0.hdslb.com/"+"a".repeat(32)+".png").put("sub_url","https://i0.hdslb.com/"+"b".repeat(32)+".png")));
        if(path.contains("/arc/search")){
            uploadCalls++;int page=param(path,"pn");if(page==failPage)return new JSONObject().put("code",-352);
            JSONArray rows=new JSONArray();for(int n=count-(page-1)*30;n>Math.max(0,count-page*30);n--)rows.put(new JSONObject().put("mid",foreign?1:uid).put("bvid",id(n)).put("title",prefix+n).put("author","模拟作者").put("created",1700000000+n).put("length","01:00"));
            return ok(new JSONObject().put("page",new JSONObject().put("pn",page).put("ps",30).put("count",count)).put("list",new JSONObject().put("vlist",rows)));
        }
        if(path.contains("seasons_series_list")){
            if(denyGroups)return new JSONObject().put("code",-352);
            int page=param(path,"page_num");JSONArray rows=new JSONArray();for(int n=(page-1)*20+1;n<=Math.min(groups,page*20);n++)rows.put(new JSONObject().put("meta",meta(n)));
            return ok(new JSONObject().put("items_lists",new JSONObject().put("page",new JSONObject().put("total",groups)).put("seasons_list",series?new JSONArray():rows).put("series_list",series?rows:new JSONArray())));
        }
        memberCalls++;int page=param(path,series?"pn":"page_num"),group=param(path,series?"series_id":"season_id"),total=Math.min(count,35);
        JSONArray rows=new JSONArray();for(int n=(page-1)*30+1;n<=Math.min(total,page*30);n++)rows.put(new JSONObject().put("bvid",id(n)));
        return ok(new JSONObject().put("meta",meta(group)).put("page",new JSONObject().put("total",total).put(series?"num":"page_num",page)).put("archives",rows));
    }
}
