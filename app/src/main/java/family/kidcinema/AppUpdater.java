package family.kidcinema;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppUpdater {
    static final String DEFAULT_SOURCE = "https://kid-player.shiyu.ren/kid-player/update.json";
    static final long INTERVAL = 15 * 60 * 1000L;
    static volatile boolean testing;
    private static final AtomicBoolean homeCheck = new AtomicBoolean();
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("kid-player-updates", Context.MODE_PRIVATE); }
    static String source(Context c) { return prefs(c).getString("source", DEFAULT_SOURCE); }
    static long version(Context c) throws Exception { return code(c.getPackageManager().getPackageInfo(c.getPackageName(),0)); }
    static long code(PackageInfo p) { return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode; }
    static String versionName(Context c) { try { return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName; } catch(Exception e) {return "";} }
    static boolean automatic(Context c) { return prefs(c).getBoolean("automatic", true); }
    static void setSource(Context c, String value) throws Exception {
        String checked = UpdatePolicy.https(value.trim());
        if (!checked.equals(source(c))) prefs(c).edit().putString("source",checked).remove("manifest").remove("lastAttempt").remove("notified").remove("prompted").commit();
    }
    static synchronized UpdateManifest check(Context c, boolean force) throws Exception {
        long now = System.currentTimeMillis(), elapsed = now - prefs(c).getLong("lastAttempt",0);
        String origin = source(c);
        if (!force && elapsed >= 0 && elapsed < INTERVAL) return cached(c);
        prefs(c).edit().putLong("lastAttempt",now).apply();
        String text;
        HttpURLConnection connection = open(origin);
        try (InputStream in=connection.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[8192]; int n; long deadline=System.nanoTime()+30_000_000_000L;
            while ((n=in.read(bytes))!=-1) {
                if (Thread.currentThread().isInterrupted() || System.nanoTime()>deadline) throw new InterruptedIOException();
                if (out.size()+n>65536) throw new IOException("更新清单过大"); out.write(bytes,0,n);
            }
            text=out.toString("UTF-8");
        } finally { connection.disconnect(); }
        UpdateManifest m=new UpdateManifest(text);
        if (!origin.equals(source(c))) throw new IOException("更新源已改变，请重新检查");
        prefs(c).edit().putString("manifest",m.json).putLong("lastSuccess",now).apply();
        return eligible(c,m) ? m : null;
    }
    static UpdateManifest cached(Context c) {
        try { UpdateManifest m=new UpdateManifest(prefs(c).getString("manifest","")); return eligible(c,m)?m:null; } catch(Exception e) {return null;}
    }
    static boolean eligible(Context c, UpdateManifest m) throws Exception { return m.versionCode>version(c) && m.minSdk<=Build.VERSION.SDK_INT; }
    static HttpURLConnection open(String value) throws IOException {
        String url=UpdatePolicy.https(value);
        for(int i=0;i<5;i++) {
            HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
            conn.setConnectTimeout(10000);conn.setReadTimeout(15000);conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept-Encoding","identity");conn.setRequestProperty("Cache-Control","no-cache");
            try {
                int code=conn.getResponseCode();
                if(code>=300&&code<400) {String to=conn.getHeaderField("Location");if(to==null)throw new IOException("更新地址重定向无效");url=UpdatePolicy.https(new URL(new URL(url),to).toString());conn.disconnect();continue;}
                if(code!=200)throw new IOException("更新服务暂时不可用（HTTP "+code+"）");
                return conn;
            } catch(IOException e) {conn.disconnect();throw e;}
        }
        throw new IOException("更新地址重定向过多");
    }
    interface Progress { void update(long received, long total); }
    static File download(Context c, UpdateManifest m, AtomicBoolean cancel, Progress progress) throws Exception {
        File dir=new File(c.getFilesDir(),"updates");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建下载目录");
        if(dir.getUsableSpace()<m.size+20*1024*1024)throw new IOException("存储空间不足，请先释放空间");
        File part=new File(dir,"download.part"), target=new File(dir,"kid-player-"+m.versionCode+".apk");
        HttpURLConnection conn=open(m.apkUrl);
        try {
            long advertised=conn.getContentLengthLong();if(advertised>=0&&advertised!=m.size)throw new IOException("下载文件大小与清单不符");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0,deadline=System.nanoTime()+600_000_000_000L;
            try(InputStream in=conn.getInputStream(); FileOutputStream out=new FileOutputStream(part)) {
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1) {
                    if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("下载已取消");
                    if(System.nanoTime()>deadline)throw new IOException("下载超时，请重试");
                    total+=n;if(total>m.size)throw new IOException("下载文件超出清单大小");
                    out.write(buffer,0,n);digest.update(buffer,0,n);progress.update(total,m.size);
                }
                out.getFD().sync();
            }
            if(cancel.get())throw new InterruptedIOException("下载已取消");
            if(total!=m.size||!hex(digest.digest()).equals(m.sha256))throw new IOException("下载校验失败，请重新下载");
            verifyApk(c,part,m);
            if(target.exists()&&!target.delete())throw new IOException("无法替换旧下载");
            if(!part.renameTo(target))throw new IOException("无法保存更新文件");
            for(File old:Objects.requireNonNull(dir.listFiles()))if(!old.equals(target))old.delete();
            return target;
        } finally {conn.disconnect();part.delete();}
    }
    static void verifyApk(Context c, File file, UpdateManifest m) throws Exception {
        if(file.length()!=m.size)throw new IOException("安装包大小发生变化");
        MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
        if(!hex(d.digest()).equals(m.sha256))throw new IOException("安装包校验失败");
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageManager pm=c.getPackageManager();PackageInfo actual=pm.getPackageArchiveInfo(file.getPath(),flags), installed=pm.getPackageInfo(c.getPackageName(),flags);
        if(actual==null)throw new IOException("安装包无法读取");
        UpdatePolicy.identity(c.getPackageName(),code(installed),actual.packageName,m.versionCode,code(actual));
        if(actual.applicationInfo==null||actual.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT||actual.applicationInfo.minSdkVersion!=m.minSdk)throw new IOException("安装包不支持当前系统");
        if(!signatures(actual).equals(signatures(installed)))throw new IOException("更新签名与当前安装不一致；请联系开发者，勿卸载应用或清除数据");
    }
    static Set<String> signatures(PackageInfo p) throws Exception {
        Signature[] values=Build.VERSION.SDK_INT>=28?(p.signingInfo==null?null:p.signingInfo.getApkContentsSigners()):p.signatures;
        if(values==null||values.length==0)throw new IOException("无法核对应用签名");
        Set<String> result=new HashSet<>();for(Signature v:values)result.add(hex(MessageDigest.getInstance("SHA-256").digest(v.toByteArray())));return result;
    }
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    static void home(Activity a) {
        if(testing||!automatic(a)||!homeCheck.compareAndSet(false,true))return;
        UpdateWorker.schedule(a);
        new Thread(()->{
            try {UpdateManifest m=check(a,false);if(m!=null)a.runOnUiThread(()->{
                if(a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus()||prefs(a).getLong("prompted",0)==m.versionCode)return;
                prefs(a).edit().putLong("prompted",m.versionCode).apply();
                new AlertDialog.Builder(a).setTitle("kid player 有新版本").setMessage(m.versionName+"\n\n"+m.notes)
                    .setPositiveButton("查看更新",(d,w)->a.startActivity(new Intent(a,UpdateActivity.class))).setNegativeButton("稍后",null).show();
            });}catch(Exception ignored){}finally{homeCheck.set(false);}
        },"kid-player-update-check").start();
    }
}
