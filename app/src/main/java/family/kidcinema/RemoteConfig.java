package family.kidcinema;

import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** A parent-hosted HTTPS JSON document controls the allowlist, never media addresses. */
final class RemoteConfig {
    interface Fetcher {JSONObject get(String url) throws Exception;}
    static String checkedUrl(String value) {
        URI uri=URI.create(value.trim());
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)
            throw new IllegalArgumentException("请填写 HTTPS JSON 文件地址");
        return uri.toString();
    }
    static List<AppStore.Creator> parse(JSONObject document,List<AppStore.Creator> previous) throws Exception {
        if(!(document.get("schema") instanceof Number)||((Number)document.get("schema")).doubleValue()!=1)throw new IOException("不支持的名单格式，请使用 schema: 1");
        JSONArray rows=document.getJSONArray("creators");
        if(rows.length()>100)throw new IOException("当前最多支持 100 位 UP 主；未覆盖原名单");
        Map<Long,AppStore.Creator> existing=new HashMap<>();for(AppStore.Creator c:previous)existing.put(c.uid,c);
        List<AppStore.Creator> result=new ArrayList<>();Set<Long> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);String value=row.get("uid").toString();
            if(!value.matches("[1-9][0-9]*"))throw new IOException("UID 必须为正整数或数字字符串");
            long uid=BiliPolicy.creatorUid(Long.parseLong(value));
            if(!seen.add(uid))throw new IOException("名单包含重复 UID，未覆盖原名单");
            if(row.has("enabled") && !(row.get("enabled") instanceof Boolean))throw new IOException("enabled 必须为 true 或 false");
            AppStore.Creator old=existing.get(uid);String name=row.optString("name",old==null?"UP 主 "+uid:old.name);
            if(name.trim().isEmpty())name="UP 主 "+uid;
            result.add(new AppStore.Creator(uid,name,old==null?"":old.avatar,row.optBoolean("enabled",true)));
        }
        return result;
    }
    static boolean due(AppStore store){long elapsed=System.currentTimeMillis()-store.prefs.getLong("remote.attempt",0);return elapsed<0||elapsed>=BiliPolicy.INTERVAL_MS;}
    static synchronized boolean refresh(AppStore store,String url,boolean manual,Fetcher fetcher) {
        if(!store.remoteCreators())return false;
        long elapsed=System.currentTimeMillis()-store.prefs.getLong("remote.attempt",0);
        boolean same=url.equals(store.remoteUrl());
        if(same&&elapsed>=0&&elapsed<(manual?60000:BiliPolicy.INTERVAL_MS))return false;
        String sourceAtStart=store.remoteUrl();
        store.prefs.edit().putLong("remote.attempt",System.currentTimeMillis()).commit();
        try {
            String checked=checkedUrl(url);JSONObject document=fetcher.get(checked);
            List<AppStore.Creator> list=parse(document,store.creators());
            if(Thread.currentThread().isInterrupted()||!store.remoteCreators()||!sourceAtStart.equals(store.remoteUrl()))return false;
            store.applyRemoteCreators(checked,list);return true;
        }catch(Exception e) {
            if(!Thread.currentThread().isInterrupted())store.prefs.edit().putString("remote.error",e instanceof IllegalArgumentException?"请检查 HTTPS 地址或名单 UID 格式。":e instanceof IOException?"云端名单暂时无法读取，保留上次名单。":"云端名单格式不正确，保留上次名单。").commit();
            return false;
        }
    }
    static boolean refresh(AppStore store,boolean manual){return !store.remoteUrl().isEmpty() && refresh(store,store.remoteUrl(),manual,RemoteConfig::fetch);}
    static JSONObject fetch(String source) throws Exception {
        String target=checkedUrl(source);long deadline=System.nanoTime()+25_000_000_000L;
        for(int i=0;i<4;i++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            HttpURLConnection connection=(HttpURLConnection)new URL(checkedUrl(target)).openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept","application/json");
            try {
                int code=connection.getResponseCode();
                if(code>=300&&code<400){String location=connection.getHeaderField("Location");if(location==null)throw new IOException();target=new URL(new URL(target),location).toString();continue;}
                if(code!=200)throw new IOException("名单 HTTP "+code);
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new InterruptedIOException();if(out.size()+n>262144)throw new IOException("名单文件过大");out.write(buffer,0,n);}
                    return new JSONObject(out.toString("UTF-8"));
                }
            }finally{connection.disconnect();}
        }
        throw new IOException("名单重定向过多");
    }
}
