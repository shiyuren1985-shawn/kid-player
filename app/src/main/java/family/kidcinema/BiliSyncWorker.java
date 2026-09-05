package family.kidcinema;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public final class BiliSyncWorker extends Worker {
    public BiliSyncWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);}
    @NonNull @Override public Result doWork(){
        AppStore store=new AppStore(getApplicationContext());if(!store.online())return Result.success();
        if(store.remoteCreators())RemoteConfig.refresh(store,false);
        java.util.List<AppStore.Creator> creators=store.enabledCreators();if(creators.isEmpty())return Result.success();
        int start=Math.floorMod(store.prefs.getInt("bili.workerNext",0),creators.size());long deadline=android.os.SystemClock.elapsedRealtime()+240000;
        for(int n=0;n<creators.size() && !isStopped() && android.os.SystemClock.elapsedRealtime()<deadline;n++){
            int index=(start+n)%creators.size();AppStore.Creator c=creators.get(index);BiliSync.run(store,c.uid,false,new BiliClient(c.uid));store.prefs.edit().putInt("bili.workerNext",(index+1)%creators.size()).apply();
        }
        return Result.success();
    }
    static void schedule(Context context){
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(BiliSyncWorker.class,15,TimeUnit.MINUTES)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("approved-bili-sync",ExistingPeriodicWorkPolicy.KEEP,request);
    }
}
