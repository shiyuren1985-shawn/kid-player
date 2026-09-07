package family.kidcinema;

import org.json.JSONObject;
import java.io.IOException;

final class UpdateManifest {
    final long versionCode, size;
    final int minSdk;
    final String versionName, apkUrl, sha256, notes, json;
    UpdateManifest(String text) throws Exception {
        JSONObject j = new JSONObject(text);
        if (j.getInt("schema") != 1 || !"family.kidcinema".equals(j.getString("packageName"))) throw new IOException("更新清单不适用于此应用");
        versionCode = j.getLong("versionCode"); size = j.getLong("size"); minSdk = j.getInt("minSdk");
        if (versionCode <= 0 || minSdk < 26) throw new IOException("更新版本格式不正确");
        UpdatePolicy.size(size);
        versionName = j.getString("versionName");
        if (versionName.isEmpty() || versionName.length() > 80) throw new IOException("更新版本名称不正确");
        apkUrl = UpdatePolicy.https(j.getString("apkUrl")); sha256 = UpdatePolicy.digest(j.getString("sha256"));
        notes = j.optString("notes", "改进使用体验");
        if (notes.length() > 4000) throw new IOException("更新说明过长");
        json = j.toString();
    }
}
