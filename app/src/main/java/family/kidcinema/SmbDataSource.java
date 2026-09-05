package family.kidcinema;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import com.hierynomus.smbj.share.File;
import java.io.IOException;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class SmbDataSource extends BaseDataSource {
    private final AppStore.Config config;
    private final String relative;
    private SmbLibrary library;
    private File file;
    private Uri uri;
    private long position, remaining;
    private boolean opened;
    public SmbDataSource(AppStore.Config config, String relative) { super(true); this.config = config; this.relative = PathPolicy.clean(relative); }
    @Override public long open(DataSpec spec) throws IOException {
        transferInitializing(spec);
        try {
            library = new SmbLibrary(config); file = library.open(relative);
            long size = file.getFileInformation().getStandardInformation().getEndOfFile();
            position = spec.position;
            if (position > size) throw new IOException("播放位置超过文件长度");
            remaining = spec.length == C.LENGTH_UNSET ? size - position : Math.min(size - position, spec.length);
            uri = spec.uri; opened = true; transferStarted(spec); return remaining;
        } catch (Exception e) { close(); throw new IOException(SmbLibrary.friendly(e), e); }
    }
    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (remaining == 0) return C.RESULT_END_OF_INPUT;
        try {
            int read = file.read(buffer, position, offset, (int) Math.min(length, remaining));
            if (read <= 0) return C.RESULT_END_OF_INPUT;
            position += read; remaining -= read; bytesTransferred(read); return read;
        } catch (Exception e) { throw new IOException("局域网读取中断，请返回后重试", e); }
    }
    @Override public Uri getUri() { return uri; }
    @Override public void close() {
        if (file != null) try { file.close(); } catch (Exception ignored) {}
        file = null;
        if (library != null) library.close(); library = null; uri = null;
        if (opened) { opened = false; transferEnded(); }
    }
}
