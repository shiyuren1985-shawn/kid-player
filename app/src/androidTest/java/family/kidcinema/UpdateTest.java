package family.kidcinema;

import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;

public class UpdateTest {
    private final Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private JSONObject base()throws Exception{return new JSONObject().put("schema",1).put("packageName","family.kidcinema").put("versionCode",999).put("versionName","test").put("minSdk",26).put("apkUrl","https://example.com/kid-player.apk").put("size",100).put("sha256",new String(new char[64]).replace('\0','a'));}
    @Test public void acceptsFutureVersionAndRejectsIncompatibleSdk()throws Exception{assertTrue(AppUpdater.eligible(c,new UpdateManifest(base().toString())));assertFalse(AppUpdater.eligible(c,new UpdateManifest(base().put("minSdk",999).toString())));}
    @Test public void rejectsMalformedAndWrongPackageManifests()throws Exception{for(String key:new String[]{"packageName","sha256","apkUrl","size"}){JSONObject j=base();j.put(key,key.equals("size")?0:"bad");try{new UpdateManifest(j.toString());fail(key);}catch(Exception expected){}}}
    @Test public void currentVersionIsNotAnUpdate()throws Exception{assertFalse(AppUpdater.eligible(c,new UpdateManifest(base().put("versionCode",AppUpdater.version(c)).toString())));}
    @Test public void invalidSourceCannotReplaceExistingConfiguration()throws Exception{String before=AppUpdater.source(c);try{AppUpdater.setSource(c,"http://example.com/update.json");fail();}catch(Exception expected){}assertEquals(before,AppUpdater.source(c));}
    @Test public void providerIsPrivateAndInstallPermissionDeclared()throws Exception{android.content.pm.ProviderInfo p=c.getPackageManager().resolveContentProvider(c.getPackageName()+".updates",0);assertNotNull(p);assertFalse(p.exported);assertTrue(p.grantUriPermissions);android.content.pm.PackageInfo pkg=c.getPackageManager().getPackageInfo(c.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS);assertTrue(java.util.Arrays.asList(pkg.requestedPermissions).contains("android.permission.REQUEST_INSTALL_PACKAGES"));}
    @Test public void rejectsBadHashBeforeInvokingInstaller()throws Exception{
        java.io.File file=new java.io.File(c.getApplicationInfo().sourceDir);
        UpdateManifest m=new UpdateManifest(base().put("size",file.length()).toString());
        try{AppUpdater.verifyApk(c,file,m);fail();}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("校验失败"));}
    }
    @Test public void rejectsManifestVersionThatDoesNotMatchApk()throws Exception{
        java.io.File file=new java.io.File(c.getApplicationInfo().sourceDir);java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
        try(java.io.InputStream in=new java.io.FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}
        UpdateManifest m=new UpdateManifest(base().put("size",file.length()).put("sha256",AppUpdater.hex(hash.digest())).toString());
        try{AppUpdater.verifyApk(c,file,m);fail();}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("版本"));}
    }
}
