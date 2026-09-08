package family.kidcinema;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Base64;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Wrapper-only cleanup under the app UID; shell cannot reset component defaults on Android 12. */
public class LauncherStateRestoreTest {
    @Test public void restoreSavedStates()throws Exception{
        String encoded=InstrumentationRegistry.getArguments().getString("launcherStates");
        org.junit.Assume.assumeNotNull(encoded);
        JSONObject saved=new JSONObject(new String(Base64.decode(encoded,Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();PackageManager pm=context.getPackageManager();
        for(LauncherIcons.Choice choice:LauncherIcons.ALL){
            String value=saved.getString(choice.component(context).getClassName());
            int state=value.equals("enable")?1:value.equals("disable")?2:0;
            pm.setComponentEnabledSetting(choice.component(context),state,PackageManager.DONT_KILL_APP);
            assertEquals(state,pm.getComponentEnabledSetting(choice.component(context)));
        }
    }
}
