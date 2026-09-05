package family.kidcinema;

import java.net.InetAddress;
import java.util.Locale;

/** One policy for browsing AND playback. Never accept parent traversal or UNC paths. */
public final class PathPolicy {
    private PathPolicy() {}
    public static String clean(String input) {
        if (input == null) return "";
        String path = input.trim().replace('\\', '/');
        if (path.startsWith("/") || path.contains(":") || path.contains("\u0000"))
            throw new IllegalArgumentException("请填写共享内的相对文件夹，不要填写完整 SMB 地址");
        if (path.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ") || part.chars().anyMatch(c -> c < 32) || part.matches(".*[<>\"|?*].*"))
                throw new IllegalArgumentException("文件路径包含不允许的片段");
            if (result.length() > 0) result.append('/');
            result.append(part);
        }
        return result.toString();
    }
    public static String join(String root, String relative) {
        String a = clean(root), b = clean(relative);
        return a.isEmpty() ? b : b.isEmpty() ? a : a + "/" + b;
    }
    public static String share(String name) {
        String s = clean(name);
        if (s.isEmpty() || s.contains("/")) throw new IllegalArgumentException("共享名只能填写一级名称");
        return s;
    }
    public static boolean video(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        return s.endsWith(".mp4") || s.endsWith(".mkv") || s.endsWith(".mov") || s.endsWith(".m4v") || s.endsWith(".webm") || s.endsWith(".avi") || s.endsWith(".ts");
    }
    public static InetAddress privateAddress(String host) throws Exception {
        if (host == null || host.trim().isEmpty() || host.contains("/") || host.contains("@"))
            throw new IllegalArgumentException("请填写家庭存储的局域网 IP 或主机名");
        InetAddress[] addresses = InetAddress.getAllByName(host.trim());
        for (InetAddress address : addresses) {
            byte[] bytes = address.getAddress();
            boolean ula = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
            if (!address.isSiteLocalAddress() && !address.isLoopbackAddress() && !ula)
                throw new IllegalArgumentException("原型仅允许连接局域网地址，不连接公网服务器");
        }
        return addresses[0];
    }
}
