package family.kidcinema;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/** Shared validation, independent of Android UI and transport. */
final class UpdatePolicy {
    static final long MAX_APK = 256L * 1024 * 1024;
    static String https(String value) throws IOException {
        try {
            URI u = new URI(value);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null
                    || u.getFragment() != null || (u.getPort() != -1 && u.getPort() != 443)) throw new Exception();
            return u.toASCIIString();
        } catch (Exception e) { throw new IOException("请使用不含账号信息的 HTTPS 地址"); }
    }
    static String digest(String value) throws IOException {
        if (value == null || !value.matches("[a-fA-F0-9]{64}")) throw new IOException("更新校验值格式不正确");
        return value.toLowerCase(Locale.ROOT);
    }
    static void size(long size) throws IOException {
        if (size <= 0 || size > MAX_APK) throw new IOException("更新文件大小不符合要求");
    }
    static void identity(String expectedPackage, long current, String actualPackage, long declared, long actual) throws IOException {
        if (!expectedPackage.equals(actualPackage)) throw new IOException("更新包不属于 kid player");
        if (declared <= current || actual != declared) throw new IOException("更新包版本不匹配或不是新版本");
    }
}
