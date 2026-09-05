package family.kidcinema;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Audio/video HTTP only. Validates every redirect and never handles HTML or external intents. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class BiliDataSource extends BaseDataSource {
    interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }
    private final ConnectionFactory connections;
    private final List<String> candidates;
    private int preferred;
    private static final class AccessDenied extends IOException { AccessDenied(int status){super("视频服务器限制访问（HTTP "+status+"），已停止播放请求");} }
    private HttpURLConnection connection;
    private InputStream input;
    private Uri uri;
    private long remaining;
    private boolean opened;
    public BiliDataSource(){this(url->(HttpURLConnection)url.openConnection());}
    BiliDataSource(ConnectionFactory connections){this(Collections.emptyList(),connections);}
    public BiliDataSource(List<String> candidates){this(candidates,url->(HttpURLConnection)url.openConnection());}
    BiliDataSource(List<String> candidates,ConnectionFactory connections){super(true);this.connections=connections;this.candidates=new ArrayList<>(candidates);}
    @Override public long open(DataSpec spec) throws IOException {
        // Only alternate URLs supplied for this exact track by the verified playback response.
        List<String> urls=candidates.isEmpty()?Collections.singletonList(spec.uri.toString()):candidates;
        if(!urls.contains(spec.uri.toString()))throw new IOException("播放地址不属于当前音视频轨道");
        try{for(String url:urls)BiliPolicy.mediaUrl(url);}catch(IllegalArgumentException e){throw new IOException("播放地址未通过安全检查",e);}
        IOException last=null;int start=preferred%urls.size();
        for(int attempt=0;attempt<urls.size();attempt++){
            preferred=(start+attempt)%urls.size();
            try{return openCandidate(spec,urls.get(preferred));}
            catch(AccessDenied e){throw e;}
            catch(IOException e){if(e.getCause() instanceof IllegalArgumentException)throw e;last=e;
                android.util.Log.w("KidPlayback","CDN open failed: "+e.getClass().getSimpleName()+"; candidate="+(attempt+1)+"/"+urls.size());}
        }
        throw last;
    }
    private long openCandidate(DataSpec spec,String target) throws IOException {
        transferInitializing(spec);
        try {
            for(int redirects=0;redirects<=4;redirects++){
                BiliPolicy.mediaUrl(target);uri=Uri.parse(target);
                connection=connections.open(new URL(target));connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(12000);connection.setReadTimeout(15000);
                connection.setRequestProperty("Referer",BiliClient.REFERER);connection.setRequestProperty("User-Agent",BiliClient.UA);
                connection.setRequestProperty("Accept-Encoding","identity");
                if(spec.position!=0 || spec.length!=C.LENGTH_UNSET)connection.setRequestProperty("Range","bytes="+spec.position+"-"+(spec.length==C.LENGTH_UNSET?"":Long.toString(spec.position+spec.length-1)));
                int status=connection.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){
                    String location=connection.getHeaderField("Location");
                    if(location==null || redirects==4)throw new IOException("播放地址重定向异常");
                    target=new URL(new URL(target),location).toString();connection.disconnect();connection=null;continue;
                }
                if(status==401 || status==403)throw new AccessDenied(status);
                if(status!=200 && status!=206)throw new IOException("视频服务器暂不可用（HTTP "+status+"）");
                String type=connection.getContentType();if(type!=null && (type.contains("text/")||type.contains("json")))throw new IOException("视频服务器未返回音视频内容");
                if(status==206){String range=connection.getHeaderField("Content-Range");if(range==null||!range.startsWith("bytes "+spec.position+"-"))throw new IOException("视频跳转范围不匹配");}
                input=connection.getInputStream();
                long skip=status==200?spec.position:0;
                if(skip>0){byte[] buffer=new byte[8192];while(skip>0){int count=input.read(buffer,0,(int)Math.min(buffer.length,skip));if(count<0)throw new EOFException();skip-=count;}}
                long length=connection.getContentLengthLong();
                if(status==200 && length>=0 && spec.position>length)throw new EOFException("跳转位置超过视频长度");
                remaining=spec.length!=C.LENGTH_UNSET?spec.length:length<0?C.LENGTH_UNSET:length-(status==200?spec.position:0);
                if(remaining<0 && remaining!=C.LENGTH_UNSET)throw new IOException("视频长度无效");
                opened=true;transferStarted(spec);return remaining;
            }
            throw new IOException("播放地址无法解析");
        }catch(Exception e){close();if(e instanceof IOException)throw (IOException)e;throw new IOException("播放地址未通过安全检查",e);}
    }
    @Override public int read(byte[] buffer,int offset,int length) throws IOException {
        try{return readCurrent(buffer,offset,length);}catch(IOException e){if(!candidates.isEmpty())preferred=(preferred+1)%candidates.size();throw e;}
    }
    private int readCurrent(byte[] buffer,int offset,int length) throws IOException {
        if(length==0)return 0;if(remaining==0)return C.RESULT_END_OF_INPUT;
        int read=input.read(buffer,offset,remaining==C.LENGTH_UNSET?length:(int)Math.min(length,remaining));
        if(read==-1){if(remaining>0)throw new EOFException("视频连接提前结束");return C.RESULT_END_OF_INPUT;}
        if(remaining!=C.LENGTH_UNSET)remaining-=read;bytesTransferred(read);return read;
    }
    @Override public Uri getUri(){return uri;}
    @Override public Map<String,List<String>> getResponseHeaders(){return connection==null?Collections.emptyMap():connection.getHeaderFields();}
    @Override public void close(){try{if(input!=null)input.close();}catch(IOException ignored){}input=null;if(connection!=null)connection.disconnect();connection=null;uri=null;if(opened){opened=false;transferEnded();}}
}
