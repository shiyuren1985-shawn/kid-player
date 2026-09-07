package family.kidcinema;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;

public class UpdatePolicyTest {
    private void rejectsUrl(String s){try{UpdatePolicy.https(s);fail(s);}catch(IOException expected){}}
    @Test public void acceptsLocalTunnelAndFutureHttpsSources()throws Exception{assertEquals("https://kid-player.shiyu.ren/kid-player/update.json",UpdatePolicy.https("https://kid-player.shiyu.ren/kid-player/update.json"));assertEquals("https://github.com/owner/repo/releases/download/v8/app.apk",UpdatePolicy.https("https://github.com/owner/repo/releases/download/v8/app.apk"));}
    @Test public void rejectsCleartextCredentialsAndFragments(){for(String s:new String[]{"http://localhost/app.apk","file:///sdcard/app.apk","https://user:secret@example.com/a","https://example.com:8443/a","https://example.com/a#b","javascript:alert(1)","https:///foo"})rejectsUrl(s);}
    @Test public void requiresValidSha256()throws Exception{assertEquals("a".repeat(64),UpdatePolicy.digest("A".repeat(64)));for(String s:new String[]{"","z".repeat(64),"a".repeat(63)})try{UpdatePolicy.digest(s);fail();}catch(IOException expected){}}
    @Test public void boundsApkSize()throws Exception{UpdatePolicy.size(19729336);for(long n:new long[]{-1,0,UpdatePolicy.MAX_APK+1})try{UpdatePolicy.size(n);fail();}catch(IOException expected){}}
    @Test public void permitsOnlyExactIncreasingPackageVersion()throws Exception{UpdatePolicy.identity("family.kidcinema",7,"family.kidcinema",8,8);for(long[] n:new long[][]{{7,7},{6,6},{8,9}})try{UpdatePolicy.identity("family.kidcinema",7,"family.kidcinema",n[0],n[1]);fail();}catch(IOException expected){}}
    @Test public void rejectsAnotherPackage()throws Exception{try{UpdatePolicy.identity("family.kidcinema",7,"other.app",8,8);fail();}catch(IOException expected){}}
}
