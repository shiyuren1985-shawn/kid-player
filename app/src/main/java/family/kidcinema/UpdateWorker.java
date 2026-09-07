package family.kidcinema;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public final class UpdateWorker extends Worker {
    static final String WORK="kid-player-update-check", CHANNEL="kid-player-updates";
    public UpdateWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();if(AppUpdater.testing||!AppUpdater.automatic(c))return Result.success();
        try{
            UpdateManifest m=AppUpdater.check(c,false);
            if(m!=null&&AppUpdater.prefs(c).getLong("notified",0)!=m.versionCode){
                NotificationManager nm=c.getSystemService(NotificationManager.class);
                nm.createNotificationChannel(new NotificationChannel(CHANNEL,"应用更新",NotificationManager.IMPORTANCE_DEFAULT));
                if(nm.areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)){
                    PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,UpdateActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                    nm.notify(7401,new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("kid player 有新版本").setContentText(m.versionName+" · 点此查看并安装").setContentIntent(open).setAutoCancel(true).build());
                    AppUpdater.prefs(c).edit().putLong("notified",m.versionCode).apply();
                }
            }
        }catch(Exception ignored){}return Result.success();
    }
    static void schedule(Context c){if(AppUpdater.testing)return;WorkManager.getInstance(c).enqueueUniquePeriodicWork(WORK,ExistingPeriodicWorkPolicy.KEEP,new PeriodicWorkRequest.Builder(UpdateWorker.class,15,TimeUnit.MINUTES).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());}
    static void cancel(Context c){WorkManager.getInstance(c).cancelUniqueWork(WORK);c.getSystemService(NotificationManager.class).cancel(7401);}
}
