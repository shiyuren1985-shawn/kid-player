package family.kidcinema;
import org.junit.*;
import org.json.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.net.*;
import java.io.*;
import java.util.*;

/** Bounded opt-in live comparison. No credentials, proxy rotation or automatic retries. */
public class RequestCompatibilityProbeTest {
    static final long UID=3546918961547843L;
    String ua="Mozilla/5.0", referer="https://www.bilibili.com/";
    boolean origin=false;
    final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    boolean session=false;
    long last;
    String get(String url)throws Exception {
        long wait=10000-(System.currentTimeMillis()-last);if(wait>0)Thread.sleep(wait);
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent",ua);c.setRequestProperty("Referer",referer);
        if(origin)c.setRequestProperty("Origin","https://space.bilibili.com");
        if(session)for(Map.Entry<String,List<String>> e:cookies.get(URI.create(url),Collections.emptyMap()).entrySet())c.setRequestProperty(e.getKey(),String.join("; ",e.getValue()));
        try {
            int status=c.getResponseCode();if(session)cookies.put(URI.create(url),c.getHeaderFields());
            System.out.println("PROBE path="+new URL(url).getPath()+" status="+status+" cookieCount="+cookies.getCookieStore().getCookies().size());
            if(status!=200)throw new IOException("HTTP "+status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()>2000000)throw new IOException("body limit");out.write(b,0,n);}return out.toString("UTF-8");
            }
        }finally{last=System.currentTimeMillis();c.disconnect();}
    }
    void page(String label,int page){
        try {JSONObject r=new BiliClient(UID,path->new JSONObject(get("https://api.bilibili.com"+path))).uploadPage(page);System.out.println("PROBE "+label+" SUCCESS page="+page+" count="+r.getJSONArray("videos").length()+" total="+r.getInt("total"));}
        catch(Exception e){System.out.println("PROBE "+label+" FAILED "+e.getMessage());}
    }
    @Test public void compare()throws Exception{
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("liveBili")));
        page("baseline-slow",1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ua=android.webkit.WebSettings.getDefaultUserAgent(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        page("actual-webview-UA",1);
        referer="https://space.bilibili.com/"+UID;origin=true;
        page("space-origin-referer",1);
        session=true;
        try{String html=get("https://www.bilibili.com/");System.out.println("PROBE official-home bytes="+html.length());}catch(Exception e){System.out.println("PROBE official-home FAILED "+e.getMessage());}
        page("server-issued-anonymous-session",1);
    }
}
