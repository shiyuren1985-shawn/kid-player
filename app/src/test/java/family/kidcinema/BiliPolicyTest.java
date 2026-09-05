package family.kidcinema;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class BiliPolicyTest {
    @Test public void onlyFixedCreator(){BiliPolicy.owner(402576555);assertThrows(IllegalArgumentException.class,()->BiliPolicy.owner(2));}
    @Test public void strictVideoIds(){
        assertEquals("BV1xx411c7mD",BiliPolicy.bvid("BV1xx411c7mD"));
        for(String id:new String[]{null,"","https://www.bilibili.com/video/BV1xx411c7mD","BV1xx411c7mD&mid=1","../x","av123"})assertThrows(IllegalArgumentException.class,()->BiliPolicy.bvid(id));
    }
    @Test public void onlyHttpsMediaHosts(){
        assertEquals("https://a.bilivideo.com/video.m4s?token=sample",BiliPolicy.mediaUrl("https://a.bilivideo.com/video.m4s?token=sample"));
        BiliPolicy.mediaUrl("https://a.bilivideo.cn/video.mp4");
        for(String url:new String[]{"http://a.bilivideo.com/v","https://a.bilivideo.com.evil.test/v","https://evil.test/v","https://a.bilivideo.com@evil.test/v","https://127.0.0.1/v","file:///tmp/a","https://a.bilivideo.com:444/v","https://a.bilivideo.com/v#x"})
            assertThrows(IllegalArgumentException.class,()->BiliPolicy.mediaUrl(url));
    }
    @Test public void signingIsDeterministicAndEscapesValues() throws Exception {
        Map<String,String> one=new LinkedHashMap<>();one.put("z","中 !'()*");one.put("a","1");
        Map<String,String> two=new LinkedHashMap<>();two.put("a","1");two.put("z","中 !'()*");
        String key="7cd084941338484aae1ad9425b84077c",sub="4932caff0ff746eab6f01bf08b70ac45";
        String query=BiliPolicy.signedQuery(one,key,sub,1000);
        assertEquals(query,BiliPolicy.signedQuery(two,key,sub,1000));
        assertTrue(query.startsWith("a=1&wts=1000&z=%E4%B8%AD%20&w_rid="));
        assertTrue(query.matches(".*&w_rid=[0-9a-f]{32}"));
        assertNotEquals(query,BiliPolicy.signedQuery(one,key,sub,1001));
        assertThrows(IllegalArgumentException.class,()->BiliPolicy.signedQuery(one,"invalid",sub,1000));
    }
}
