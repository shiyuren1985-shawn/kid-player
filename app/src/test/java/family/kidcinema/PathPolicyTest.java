package family.kidcinema;
import org.junit.Test;
import static org.junit.Assert.*;

public class PathPolicyTest {
    @Test public void joinsInsideRoot(){assertEquals("儿童视频/动画/第一集.mp4",PathPolicy.join("儿童视频","动画/第一集.mp4"));}
    @Test public void windowsSeparators(){assertEquals("儿童/动画/1.mp4",PathPolicy.join("儿童","动画\\1.mp4"));}
    @Test public void emptyRootIsSelectedShare(){assertEquals("1.mp4",PathPolicy.join("","1.mp4"));}
    @Test public void emptyRelativeIsRoot(){assertEquals("儿童",PathPolicy.join("儿童",""));}
    @Test public void rejectsTraversal(){for(String s:new String[]{"..","a/../b","/absolute","\\\\nas\\private","a//b","a/./b","a/","C:/private","foo\u0000.mp4","foo:stream","a/.. /b","a/../b.mp4","a?/b","a/..\\b"}){assertThrows(s,IllegalArgumentException.class,()->PathPolicy.clean(s));}}
    @Test public void rejectsNestedShare(){assertThrows(IllegalArgumentException.class,()->PathPolicy.share("public/secret"));assertThrows(IllegalArgumentException.class,()->PathPolicy.share(""));}
    @Test public void allowedVideoExtensions(){assertTrue(PathPolicy.video("MOVIE.MP4"));assertTrue(PathPolicy.video("片源.mkv"));assertFalse(PathPolicy.video("x.mp4.exe"));assertFalse(PathPolicy.video("readme.txt"));}
    @Test public void blocksPublicIp(){assertThrows(IllegalArgumentException.class,()->PathPolicy.privateAddress("8.8.8.8"));}
    @Test public void allowsLan() throws Exception {assertEquals("192.168.1.10",PathPolicy.privateAddress("192.168.1.10").getHostAddress());}
}
