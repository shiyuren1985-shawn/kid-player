package family.kidcinema;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.*;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class SmbLibrary implements AutoCloseable {
    private SMBClient client;
    private Connection connection;
    private Session session;
    private DiskShare share;
    private final AppStore.Config config;
    public SmbLibrary(AppStore.Config config) throws Exception {
        this.config = config; config.validate();
        String address = PathPolicy.privateAddress(config.host).getHostAddress();
        for (int attempt=0;attempt<2;attempt++) {
            try {
                client = new SMBClient(SmbConfig.builder().withTimeout(12, TimeUnit.SECONDS).withSoTimeout(12000)
                    .withDfsEnabled(false).withSocketFactory(new com.hierynomus.protocol.commons.socket.ProxySocketFactory(java.net.Proxy.NO_PROXY,6000)).build());
                connection = client.connect(address, config.port);
                session = connection.authenticate(new AuthenticationContext(config.user, config.password.toCharArray(), config.domain));
                share = (DiskShare) session.connectShare(config.share);
                return;
            } catch (java.net.SocketTimeoutException e) {
                close(); if(attempt==1)throw e; // One retry for transient LAN/virtual NAT connection loss only.
            } catch (Exception e) { close(); throw e; }
        }
    }
    /** Check ancestors without following a listed reparse point (symlink/junction). */
    private String checkedPath(String relative) throws IOException {
        String path = PathPolicy.join(config.root, relative);
        String parent = "";
        for (String component : path.isEmpty() ? new String[0] : path.split("/")) {
            FileIdBothDirectoryInformation found = null;
            for (FileIdBothDirectoryInformation entry : share.list(parent)) {
                if (entry.getFileName().equals(component)) { found = entry; break; }
            }
            if (found == null) throw new IOException("文件不存在或没有读取权限");
            if ((found.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.getValue()) != 0)
                throw new IOException("为保护目录边界，不访问链接或重解析点");
            parent = parent.isEmpty() ? component : parent + "/" + component;
        }
        return path;
    }
    public List<LibraryItem> list(String relative) throws IOException {
        String path = checkedPath(relative);
        List<LibraryItem> result = new ArrayList<>();
        for (FileIdBothDirectoryInformation e : share.list(path)) {
            String name = e.getFileName();
            if (name.equals(".") || name.equals("..") || name.startsWith(".")) continue;
            if ((e.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.getValue()) != 0) continue;
            boolean folder = (e.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
            if (!folder && !PathPolicy.video(name)) continue;
            String child;
            try { child = PathPolicy.join(relative, name); } catch (IllegalArgumentException ex) { continue; }
            String subtitle = folder ? "打开文件夹" : String.format(Locale.CHINA, "%.1f MB · 家庭视频", e.getEndOfFile() / 1048576.0);
            result.add(new LibraryItem(name, child, subtitle, folder, false, Math.floorMod(name.hashCode(), 4)));
        }
        result.sort(Comparator.comparing((LibraryItem i) -> !i.folder).thenComparing(i -> i.name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
    public File open(String relative) throws IOException {
        if (relative.isEmpty() || !PathPolicy.video(relative)) throw new IOException("不是允许播放的视频文件");
        return share.openFile(checkedPath(relative), EnumSet.of(AccessMask.FILE_READ_DATA, AccessMask.FILE_READ_ATTRIBUTES),
            EnumSet.noneOf(FileAttributes.class), EnumSet.allOf(SMB2ShareAccess.class), SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE, SMB2CreateOptions.FILE_OPEN_REPARSE_POINT));
    }
    public static String friendly(Throwable e) {
        String s = String.valueOf(e.getMessage());
        if (s.contains("LOGON_FAILURE") || s.contains("WRONG_PASSWORD")) return "账号或密码不正确，请家长检查共享凭据。";
        if (s.contains("ACCESS_DENIED")) return "没有读取这个文件夹的权限，请家长检查共享设置。";
        if (s.contains("BAD_NETWORK_NAME")) return "找不到共享名，请填写网络邻居里显示的共享名称。";
        if (e instanceof IllegalArgumentException) return s;
        return "暂时无法读取视频。请确认设备在同一局域网、家庭存储已开机，并检查连接与目录设置。";
    }
    @Override public void close() {
        if (share != null) try { share.close(); } catch (Exception ignored) {}
        if (session != null) try { session.close(); } catch (Exception ignored) {}
        if (connection != null) try { connection.close(); } catch (Exception ignored) {}
        if (client != null) try { client.close(); } catch (Exception ignored) {}
        share=null;session=null;connection=null;client=null;
    }
}
