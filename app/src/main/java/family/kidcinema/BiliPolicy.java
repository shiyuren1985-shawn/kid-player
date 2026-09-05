package family.kidcinema;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Parent-owned allowlist. No child-facing editor, search, or arbitrary URL input. */
public final class BiliPolicy {
    public static final long UID = 402576555L;
    public static final String LABEL = "画渣花小烙 · 402576555";
    public static final long INTERVAL_MS = 15 * 60 * 1000L;
    private BiliPolicy() {}
    public static String bvid(String value) {
        if (value == null || !value.matches("BV[0-9A-Za-z]{10}")) throw new IllegalArgumentException("视频编号不正确");
        return value;
    }
    public static void owner(long uid) {
        if (uid != UID) throw new IllegalArgumentException("不在家长指定的作者范围内");
    }
    public static String mediaUrl(String value) {
        URI uri = URI.create(value); String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null ||
            (uri.getPort() != -1 && uri.getPort() != 443) || uri.getFragment() != null ||
            !(host.endsWith(".bilivideo.com") || host.endsWith(".bilivideo.cn")))
            throw new IllegalArgumentException("播放地址不在允许的视频域名内");
        return value;
    }
    // Public WBI parameter signing; no login tokens, device simulation, or challenge handling.
    public static String signedQuery(Map<String,String> values, String imageKey, String subKey, long seconds) throws Exception {
        int[] permutation={46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,36,20,34,44,52};
        String seed=imageKey+subKey;
        if (!seed.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("签名信息无效");
        StringBuilder key=new StringBuilder();for(int i=0;i<32;i++)key.append(seed.charAt(permutation[i]));
        TreeMap<String,String> sorted=new TreeMap<>(values);sorted.put("wts",Long.toString(seconds));
        StringJoiner query=new StringJoiner("&");
        for(Map.Entry<String,String> entry:sorted.entrySet())query.add(encode(entry.getKey())+"="+encode(entry.getValue().replaceAll("[!'()*]","")));
        byte[] digest=MessageDigest.getInstance("MD5").digest((query.toString()+key).getBytes(StandardCharsets.UTF_8));
        StringBuilder hash=new StringBuilder();for(byte b:digest)hash.append(String.format(Locale.ROOT,"%02x",b & 255));
        return query+"&w_rid="+hash;
    }
    private static String encode(String s) throws java.io.UnsupportedEncodingException { return URLEncoder.encode(s,"UTF-8").replace("+","%20"); }
}
