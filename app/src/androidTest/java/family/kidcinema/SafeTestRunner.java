package family.kidcinema;

import androidx.test.runner.AndroidJUnitRunner;
import java.io.*;

/** Refuse destructive fixture tests outside the dedicated AVD, even from raw am instrument. */
public final class SafeTestRunner extends AndroidJUnitRunner {
    @Override public void onStart(){
        try(android.os.ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand("getprop ro.boot.qemu.avd_name");InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[256];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            if(!"KidCinema_Tablet_12".equals(out.toString("UTF-8").trim()))throw new SecurityException("Tests are restricted to KidCinema_Tablet_12");
        }catch(IOException e){throw new SecurityException("Could not verify dedicated emulator",e);}
        super.onStart();
    }
}
