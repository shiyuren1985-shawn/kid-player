package family.kidcinema;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public final class BiliSyncWorker extends Worker {
    public BiliSyncWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);}
    @NonNull @Override public Result doWork(){AppStore store=new AppStore(getApplicationContext());if(store.online())BiliSync.run(store,false);return Result.success();}
    static void schedule(Context context){
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(BiliSyncWorker.class,15,TimeUnit.MINUTES)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("approved-bili-sync",ExistingPeriodicWorkPolicy.KEEP,request);
    }
}
