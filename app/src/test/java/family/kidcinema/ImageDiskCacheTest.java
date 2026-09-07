package family.kidcinema;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageDiskCacheTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private ImageDiskCache cache(){return new ImageDiskCache(folder.getRoot(),12,8);}
    @Test public void unchangedUrlSurvivesRestartAndOldTimestamp()throws Exception{
        cache().get("avatar",()->new byte[]{1,2},d->true);
        folder.getRoot().listFiles()[0].setLastModified(1);
        assertArrayEquals(new byte[]{1,2},cache().get("avatar",()->{throw new IOException("offline");},d->true));
    }
    @Test public void changedUrlFetchesOnlyNewImage()throws Exception{
        AtomicInteger calls=new AtomicInteger();ImageDiskCache.Fetch fetch=()->new byte[]{(byte)calls.incrementAndGet()};
        cache().get("a",fetch,d->true);cache().get("a",fetch,d->true);cache().get("b",fetch,d->true);
        assertEquals(2,calls.get());
    }
    @Test public void concurrentViewsShareOneDownload()throws Exception{
        AtomicInteger calls=new AtomicInteger();ExecutorService pool=Executors.newFixedThreadPool(4);
        try{java.util.List<Future<byte[]>> results=new java.util.ArrayList<>();
            for(int i=0;i<8;i++)results.add(pool.submit(()->cache().get("same",()->{calls.incrementAndGet();Thread.sleep(50);return new byte[]{1};},d->true)));
            for(Future<byte[]> result:results)assertArrayEquals(new byte[]{1},result.get(3,TimeUnit.SECONDS));
            assertEquals(1,calls.get());
        }finally{pool.shutdownNow();}
    }
    @Test public void corruptEntryIsRepairedAndInvalidResponseIsNotSaved()throws Exception{
        cache().get("a",()->new byte[]{1},d->d[0]==1);
        Files.write(folder.getRoot().listFiles()[0].toPath(),new byte[]{0});
        assertArrayEquals(new byte[]{1},cache().get("a",()->new byte[]{1},d->d[0]==1));
        try{cache().get("b",()->new byte[]{0},d->d[0]==1);fail();}catch(IOException expected){}
        assertEquals(1,folder.getRoot().listFiles().length);
    }
    @Test public void trimsAfterWriteAndKeepsRecentlyReadEntry()throws Exception{
        cache().get("a",()->new byte[6],d->true);cache().get("b",()->new byte[6],d->true);
        for(File f:folder.getRoot().listFiles())f.setLastModified(1);
        cache().get("a",()->{throw new IOException();},d->true);
        cache().get("c",()->new byte[6],d->true);
        assertEquals(12,java.util.Arrays.stream(folder.getRoot().listFiles()).mapToLong(File::length).sum());
        cache().get("a",()->{throw new IOException("recent entry evicted");},d->true);
        AtomicInteger calls=new AtomicInteger();cache().get("b",()->{calls.incrementAndGet();return new byte[]{1};},d->true);assertEquals(1,calls.get());
    }
    @Test public void failedOrOversizedDownloadLeavesExistingCacheUsable()throws Exception{
        cache().get("a",()->new byte[]{1},d->true);
        try{cache().get("b",()->{throw new IOException();},d->true);fail();}catch(IOException expected){}
        try{cache().get("c",()->new byte[9],d->true);fail();}catch(IOException expected){}
        assertArrayEquals(new byte[]{1},cache().get("a",()->{throw new IOException();},d->true));
    }
}
