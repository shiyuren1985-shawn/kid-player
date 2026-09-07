package family.kidcinema;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class AppStore {
    final SharedPreferences prefs;
    private final Context context;
    private String cachedConnection, cachedScope;
    public static final String KEY = "kidcinema.connection.v1";
    public AppStore(Context c) { context=c.getApplicationContext();BiliSession.configure(context);prefs = c.getSharedPreferences("cinema", Context.MODE_PRIVATE); }
    public static final class Config {
        public String host = "", share = "", root = "", user = "", password = "", domain = "";
        public int port = 445;
        public void validate() {
            if (host.trim().isEmpty()) throw new IllegalArgumentException("请填写局域网地址");
            share = PathPolicy.share(share); root = PathPolicy.clean(root);
            if (port < 1 || port > 65535) throw new IllegalArgumentException("端口范围为 1–65535");
        }
    }
    private SecretKey secretKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(KEY)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY, null)).getSecretKey();
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    public void save(Config c) throws Exception {
        c.validate();
        JSONObject json = new JSONObject().put("host", c.host.trim()).put("port", c.port).put("share", c.share).put("root", c.root)
            .put("user", c.user).put("password", c.password).put("domain", c.domain);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secretKey());
        String stored = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().putString("connection", stored).commit()) throw new java.io.IOException("无法保存连接设置");
    }
    public Config config() throws Exception {
        Config c = new Config(); String stored = prefs.getString("connection", "");
        if (stored.isEmpty()) return c;
        String[] parts = stored.split(":");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        JSONObject j = new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), java.nio.charset.StandardCharsets.UTF_8));
        c.host = j.getString("host"); c.port = j.getInt("port"); c.share = j.getString("share"); c.root = j.getString("root");
        c.user = j.getString("user"); c.password = j.getString("password"); c.domain = j.optString("domain"); return c;
    }
    public boolean hasConfig() { return prefs.contains("connection"); }
    public boolean online() { return prefs.getBoolean("online", false); }
    public void online(boolean b) { prefs.edit().putBoolean("online",b).apply(); }
    public boolean demo() { return !online() && prefs.getBoolean("demo", true); }
    public void demo(boolean b) { prefs.edit().putBoolean("demo", b).putBoolean("online",false).apply(); }
    public static final class Creator {
        public final long uid;
        public final String name, avatar;
        public final boolean enabled;
        public Creator(long uid, String name, String avatar, boolean enabled) {
            BiliPolicy.creatorUid(uid);
            this.uid=uid; this.name=name; this.avatar=avatar; this.enabled=enabled;
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("uid",uid).put("name",name).put("avatar",avatar).put("enabled",enabled);
        }
    }
    public boolean remoteCreators(){return prefs.getBoolean("creators.remoteMode",true);}
    public void remoteCreators(boolean remote){prefs.edit().putBoolean("creators.remoteMode",remote).commit();}
    public String remoteUrl(){return prefs.getString("remote.url","");}
    private void requireLocalCreators(){if(remoteCreators())throw new IllegalStateException("云端模式请在网上的名单文件中修改 UID");}
    void applyRemoteCreators(String url,List<Creator> creators) throws Exception {
        synchronized(AppStore.class) {
            JSONArray rows=new JSONArray();for(Creator c:creators)rows.put(c.json());
            if(!prefs.edit().putString("creators.remote",rows.toString()).putString("remote.url",url)
                .putLong("remote.syncedAt",System.currentTimeMillis()).putString("remote.error","").commit())throw new java.io.IOException("无法保存云端名单");
        }
    }
    public List<Creator> creators() {
        List<Creator> result=new ArrayList<>();
        String listKey=remoteCreators()?"creators.remote":"creators";
        if (!prefs.contains(listKey)) {
            result.add(new Creator(BiliPolicy.UID,"画渣花小烙","",true));
            return result;
        }
        try {
            JSONArray rows=new JSONArray(prefs.getString(listKey,"[]"));
            Set<Long> seen=new HashSet<>();
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i); long uid=row.getLong("uid");
                if(seen.add(uid))result.add(new Creator(uid,row.getString("name"),row.optString("avatar"),row.optBoolean("enabled",true)));
            }
        } catch(Exception ignored) { result.clear(); /* Invalid allowlist never grants access. */ }
        return result;
    }
    public List<Creator> enabledCreators() {
        List<Creator> result=new ArrayList<>();
        for(Creator c:creators())if(c.enabled)result.add(c);
        return result;
    }
    public Creator creator(long uid) {
        for(Creator c:creators())if(c.uid==uid)return c;
        return null;
    }
    public boolean allowedCreator(long uid) { Creator c=creator(uid);return c!=null && c.enabled; }
    private void saveCreators(List<Creator> list) throws Exception {
        JSONArray rows=new JSONArray();for(Creator c:list)rows.put(c.json());
        if(!prefs.edit().putString(remoteCreators()?"creators.remote":"creators",rows.toString()).commit())throw new java.io.IOException("无法保存 UP 主列表");
    }
    public void addCreator(Creator creator) throws Exception {
        requireLocalCreators();
        synchronized(AppStore.class) {
            List<Creator> list=creators();
            for(Creator c:list)if(c.uid==creator.uid)throw new IllegalArgumentException("已经添加过这位 UP 主");
            list.add(creator);saveCreators(list);
        }
    }
    public void creatorEnabled(long uid, boolean enabled) throws Exception {
        requireLocalCreators();
        synchronized(AppStore.class) {
            List<Creator> list=creators();
            for(int i=0;i<list.size();i++){Creator c=list.get(i);if(c.uid==uid)list.set(i,new Creator(c.uid,c.name,c.avatar,enabled));}
            saveCreators(list);
        }
    }
    public void removeCreator(long uid) throws Exception {
        requireLocalCreators();
        synchronized(AppStore.class) {
            List<Creator> list=creators();list.removeIf(c->c.uid==uid);saveCreators(list);
        }
    }
    public void updateCreatorProfile(long uid,String name,String avatar) throws Exception {
        synchronized(AppStore.class) {
            List<Creator> list=creators();boolean changed=false;
            for(int i=0;i<list.size();i++) {Creator c=list.get(i);if(c.uid==uid && (!c.name.equals(name)||!c.avatar.equals(avatar))){list.set(i,new Creator(uid,name,avatar,c.enabled));changed=true;}}
            if(changed)saveCreators(list);
        }
    }
    public long selectedCreator() {
        long uid=prefs.getLong("creator.selected",BiliPolicy.UID);
        if(allowedCreator(uid))return uid;
        List<Creator> enabled=enabledCreators();return enabled.isEmpty()?0:enabled.get(0).uid;
    }
    public void selectedCreator(long uid) {
        if(!allowedCreator(uid))throw new IllegalArgumentException("这位 UP 主尚未启用");
        prefs.edit().putLong("creator.selected",uid).apply();
    }
    // Retain the v0.3 keys for the original creator; its catalog/history migrates without data loss.
    String biliKey(long uid,String field) { return uid==BiliPolicy.UID ? "bili."+field : "bili."+uid+"."+field; }
    public JSONObject feed() throws Exception {return feed(selectedCreator());}
    public JSONObject feed(long uid) throws Exception {
        BiliPolicy.creatorUid(uid);
        String key=biliKey(uid,"feed");
        if(prefs.contains(key))return new JSONObject(prefs.getString(key,""));
        if(uid!=BiliPolicy.UID)return new JSONObject().put("schema",1).put("uid",uid).put("syncedAt",0).put("videos",new JSONArray());
        try(java.io.InputStream in=context.getAssets().open("bilibili-initial.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1){if(out.size()+n>100000)throw new java.io.IOException("初始目录过大");out.write(buffer,0,n);}
            JSONObject initial=new JSONObject(out.toString("UTF-8"));BiliClient.items(initial,uid);return initial;
        }
    }
    public void feed(JSONObject feed) throws Exception {feed(selectedCreator(),feed);}
    public void feed(long uid,JSONObject feed) throws Exception {
        BiliClient.items(feed,uid);
        if(!prefs.edit().putString(biliKey(uid,"feed"),feed.toString()).putString(biliKey(uid,"error"),"").commit())throw new java.io.IOException("目录保存失败");
    }
    public String syncError() {return syncError(selectedCreator());}
    public String syncError(long uid) {return prefs.getString(biliKey(uid,"error"),"");}
    public long syncAttempt() {return syncAttempt(selectedCreator());}
    public long syncAttempt(long uid) {return prefs.getLong(biliKey(uid,"attempt"),0);}
    public String mode() {return prefs.getString("mode","自动");}
    public void mode(String v) {prefs.edit().putString("mode",v).apply();}
    public String scope() {
        if(online())return "bili:"+selectedCreator();
        if(demo())return "demo";
        String stored=prefs.getString("connection","");
        if(cachedScope!=null && stored.equals(cachedConnection))return cachedScope;
        try {Config c=config();cachedScope=c.host+":"+c.port+"/"+c.share+"/"+c.root;cachedConnection=stored;return cachedScope;}
        catch(Exception e){return "invalid";}
    }
    public long progress(String key) {return progress(scope(),key);}
    public long progress(String scope,String key) {return prefs.getLong("progress:"+scope+":"+key,0);}
    public void progress(String key,long ms) {progress(scope(),key,ms);}
    public void progress(String scope,String key,long ms) {
        prefs.edit().putLong("progress:"+scope+":"+key,Math.max(0,ms)).apply();
        // Starting over or failing before playback must not create a history entry.
        if(ms>0 || prefs.contains("watched:"+scope+":"+key))recordWatched(scope,key);
    }
    public void recordWatched(String scope,String key) {
        long newest=0;for(long stamp:watchedEntries(scope).values())newest=Math.max(newest,stamp);
        prefs.edit().putLong("watched:"+scope+":"+key,Math.max(System.currentTimeMillis(),newest+1)).apply();
        trimHistory(scope);
    }
    public boolean favorite(String key) {return favorite(scope(),key);}
    public boolean favorite(String scope,String key) {return prefs.getBoolean("favorite:"+scope+":"+key,false);}
    public void toggleFavorite(String key) {toggleFavorite(scope(),key);}
    public void toggleFavorite(String scope,String key) {prefs.edit().putBoolean("favorite:"+scope+":"+key,!favorite(scope,key)).apply();}
    public void remember(List<LibraryItem> items) {
        String key="library:"+scope();
        try {
            JSONObject index=new JSONObject(prefs.getString(key,"{}"));
            for(LibraryItem item:items)if(!item.folder)index.put(item.key(),item.json());
            prefs.edit().putString(key,index.toString()).apply();
        }catch(Exception ignored){}
    }
    private Map<String,Long> watchedEntries(String scope) {
        Map<String,Long> entries=new HashMap<>();String prefix="watched:"+scope+":";
        for(Map.Entry<String,?> entry:prefs.getAll().entrySet())
            if(entry.getKey().startsWith(prefix)&&entry.getValue() instanceof Long&&(Long)entry.getValue()>0)
                entries.put(entry.getKey(),(Long)entry.getValue());
        return entries;
    }
    private void trimHistory(String scope) {
        List<Map.Entry<String,Long>> entries=new ArrayList<>(watchedEntries(scope).entrySet());
        entries.sort((a,b)->Long.compare(b.getValue(),a.getValue()));
        SharedPreferences.Editor edit=prefs.edit();
        for(int i=30;i<entries.size();i++)edit.remove(entries.get(i).getKey());
        edit.apply();
    }
    public void clearWatchHistory() {
        SharedPreferences.Editor edit=prefs.edit();
        for(String key:watchedEntries(scope()).keySet())edit.remove(key);
        edit.apply();
    }
    public List<LibraryItem> watchHistory(List<LibraryItem> catalog) {
        trimHistory(scope());
        Map<String,LibraryItem> known=new LinkedHashMap<>();
        for(LibraryItem item:history(false))known.put(item.key(),item);
        for(LibraryItem item:catalog)if(!item.folder&&prefs.getLong("watched:"+scope()+":"+item.key(),0)>0)known.put(item.key(),item);
        List<LibraryItem> result=new ArrayList<>(known.values());
        result.sort((a,b)->Long.compare(prefs.getLong("watched:"+scope()+":"+b.key(),0),prefs.getLong("watched:"+scope()+":"+a.key(),0)));
        return result.size()>30?new ArrayList<>(result.subList(0,30)):result;
    }
    public List<LibraryItem> history(boolean favorites) {
        List<LibraryItem> result=new ArrayList<>();
        try {
            JSONObject index=new JSONObject(prefs.getString("library:"+scope(),"{}"));
            Iterator<String> keys=index.keys();
            Set<String> seen=new HashSet<>();
            while(keys.hasNext()) {
                String key=keys.next();LibraryItem item=LibraryItem.fromJson(index.getJSONObject(key));
                if((favorites?favorite(key):prefs.getLong("watched:"+scope()+":"+key,0)>0)&&seen.add(key))result.add(item);
            }
            // Older versions persisted keys only. Recover their relative paths without scanning outside the root.
            String prefix=(favorites?"favorite:":"watched:")+scope()+":smb:";
            for(Map.Entry<String,?> entry:prefs.getAll().entrySet()) {
                if(!entry.getKey().startsWith(prefix))continue;
                boolean include=favorites?Boolean.TRUE.equals(entry.getValue()):entry.getValue() instanceof Long && (Long)entry.getValue()>0;
                if(!include)continue;
                String path=PathPolicy.clean(entry.getKey().substring(prefix.length()));String key="smb:"+path;
                if(!PathPolicy.video(path)||!seen.add(key))continue;
                result.add(new LibraryItem(path.substring(path.lastIndexOf('/')+1),path,"家庭视频",false,false,0));
            }
            String scope=scope();
            result.sort((a,b)->Long.compare(prefs.getLong("watched:"+scope+":"+b.key(),0),prefs.getLong("watched:"+scope+":"+a.key(),0)));
        }catch(Exception ignored){}
        return result;
    }
}
