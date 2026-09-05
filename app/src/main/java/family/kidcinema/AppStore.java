package family.kidcinema;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class AppStore {
    final SharedPreferences prefs;
    private final Context context;
    public static final String KEY = "kidcinema.connection.v1";
    public AppStore(Context c) { context=c.getApplicationContext();prefs = c.getSharedPreferences("cinema", Context.MODE_PRIVATE); }
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
    public JSONObject feed() throws Exception {
        if(prefs.contains("bili.feed"))return new JSONObject(prefs.getString("bili.feed",""));
        try(java.io.InputStream in=context.getAssets().open("bilibili-initial.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>100000)throw new java.io.IOException("初始目录过大");out.write(buffer,0,n);}
            JSONObject initial=new JSONObject(out.toString("UTF-8"));BiliClient.items(initial);return initial;
        }
    }
    public void feed(JSONObject feed) throws Exception {
        BiliClient.items(feed);
        if(!prefs.edit().putString("bili.feed",feed.toString()).putString("bili.error","").commit())throw new java.io.IOException("目录保存失败");
    }
    public String syncError() { return prefs.getString("bili.error",""); }
    public long syncAttempt() { return prefs.getLong("bili.attempt",0); }
    public String mode() { return prefs.getString("mode", "自动"); }
    public void mode(String v) { prefs.edit().putString("mode", v).apply(); }
    public String scope() {
        if (online()) return "bili:"+BiliPolicy.UID;
        if (demo()) return "demo";
        try { Config c = config(); return c.host + ":" + c.port + "/" + c.share + "/" + c.root; }
        catch (Exception e) { return "invalid"; }
    }
    public long progress(String key) { return prefs.getLong("progress:" + scope() + ":" + key, 0); }
    public void progress(String key, long ms) { prefs.edit().putLong("progress:" + scope() + ":" + key, ms).apply(); }
    public boolean favorite(String key) { return prefs.getBoolean("favorite:" + scope() + ":" + key, false); }
    public void toggleFavorite(String key) { prefs.edit().putBoolean("favorite:" + scope() + ":" + key, !favorite(key)).apply(); }
}
