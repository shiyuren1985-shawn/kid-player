package family.kidcinema;

import android.content.Context;
import android.os.*;
import android.webkit.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** App-private anonymous browser session. No injected fingerprint, account or JS interface. */
final class BiliSession {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile Context context;
    private static long sequence;
    private static WebView web;
    private static CompletableFuture<Void> ready;
    private static long lastRequest;
    static void configure(Context value){context=value.getApplicationContext();}
    private static boolean allowed(String url){
        android.net.Uri uri=android.net.Uri.parse(url);String host=uri.getHost();
        return "https".equals(uri.getScheme())&&("m.bilibili.com".equals(host)||"www.bilibili.com".equals(host)||"space.bilibili.com".equals(host));
    }
    private static <T>T await(CompletableFuture<T> future,BooleanSupplier cancelled)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+25000;
        while(SystemClock.elapsedRealtime()<deadline){
            if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedException();
            try{return future.get(200,TimeUnit.MILLISECONDS);}catch(TimeoutException ignored){}
            catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new IOException("浏览器会话不可用",cause);}
        }
        throw new java.net.SocketTimeoutException("浏览器会话请求超时");
    }
    private static void initialize()throws IOException{
        if(context==null)throw new IOException("浏览器会话尚未初始化");
        if(ready!=null)return;
        ready=new CompletableFuture<>();final CompletableFuture<Void> start=ready;
        MAIN.post(()->{
            try{
                web=new WebView(context);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
                web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
                web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                CookieManager.getInstance().setAcceptCookie(true);
                web.setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !allowed(request.getUrl().toString());}
                    @Override public void onPageFinished(WebView view,String url){if(allowed(url))start.complete(null);}
                    @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())start.completeExceptionally(new IOException("B 站匿名会话建立失败，请稍后重试。"));}
                    @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame()&&response.getStatusCode()>=400)start.completeExceptionally(BiliAccessException.riskCode(response.getStatusCode())?new BiliAccessException(response.getStatusCode()):new IOException("B 站会话页面 HTTP "+response.getStatusCode()));}
                });
                web.loadUrl("https://m.bilibili.com/");
            }catch(Exception e){start.completeExceptionally(new IOException("此设备的 Android WebView 不可用",e));}
        });
    }
    static synchronized JSONObject get(String path,BooleanSupplier cancelled)throws Exception{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IOException("网络请求不能在界面线程执行");
        if(!path.startsWith("/x/")||path.contains("#"))throw new IOException("接口地址无效");
        initialize();
        try{await(ready,cancelled);}catch(Exception e){if(!(e instanceof InterruptedException)){MAIN.post(()->{if(web!=null){web.stopLoading();web.destroy();web=null;}});ready=null;}throw e;}
        long wait=1200-(SystemClock.elapsedRealtime()-lastRequest);if(wait>0)Thread.sleep(wait);
        if(cancelled.getAsBoolean())throw new InterruptedException();
        CompletableFuture<String> result=new CompletableFuture<>();
        String slot="window.__kidResult"+(++sequence);
        String url=JSONObject.quote("https://api.bilibili.com"+path);
        // Returning the Promise itself would not await it in evaluateJavascript. Poll one bounded result slot.
        String script="(()=>{const state={result:null,controller:new AbortController()};"+slot+"=state;(async()=>{const t=setTimeout(()=>state.controller.abort(),15000);try{const r=await fetch("+url+",{credentials:'include',signal:state.controller.signal});if(r.status!==200){state.result=JSON.stringify({status:r.status,body:''});return;}const reader=r.body.getReader(),decoder=new TextDecoder();let body='',size=0;for(;;){const part=await reader.read();if(part.done)break;size+=part.value.length;if(size>2000000){await reader.cancel();state.result=JSON.stringify({status:r.status,body:null});return;}body+=decoder.decode(part.value,{stream:true});}body+=decoder.decode();state.result=JSON.stringify({status:r.status,body});}catch(e){state.result=JSON.stringify({error:e.name||'Error'});}finally{clearTimeout(t);}})();})();";
        MAIN.post(()->{
            web.evaluateJavascript(script,null);
            Runnable poll=new Runnable(){public void run(){
                if(result.isDone())return;
                web.evaluateJavascript(slot+".result",value->{
                    if(value!=null&&!value.equals("null"))result.complete(value);
                    else MAIN.postDelayed(this,100);
                });
            }};MAIN.postDelayed(poll,100);
        });
        try{
            String encoded=await(result,cancelled);Object decoded=new JSONTokener(encoded).nextValue();
            JSONObject response=new JSONObject((String)decoded);
            if("AbortError".equals(response.optString("error")))throw new java.net.SocketTimeoutException();
            if(response.has("error"))throw new IOException("B 站浏览器会话请求失败，请稍后重试。");
            int status=response.getInt("status");if(BiliAccessException.riskCode(status))throw new BiliAccessException(status);
            if(status!=200)throw new IOException("B 站接口 HTTP "+status);
            if(response.isNull("body"))throw new IOException("接口响应过大");
            return new JSONObject(response.getString("body"));
        }finally{result.cancel(false);MAIN.post(()->{if(web!=null)web.evaluateJavascript("if("+slot+"){"+slot+".controller.abort();delete "+slot+";}",null);});lastRequest=SystemClock.elapsedRealtime();}
    }
}
