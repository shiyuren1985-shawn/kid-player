package family.kidcinema;

/** Independent creator cooldowns/caches, with a single sync operation per process. */
final class BiliSync {
    private BiliSync() {}
    private static volatile long activeUid;
    static boolean running(long uid){return uid>0&&activeUid==uid;}
    enum Outcome { UPDATED, PARTIAL, COOLDOWN, DISABLED, FAILED, CANCELLED }
    static long riskUntil(AppStore store){return store.prefs.getLong("bili.riskUntil",0);}
    static boolean riskCooling(AppStore store){return riskUntil(store)>System.currentTimeMillis();}
    private static void recordRisk(AppStore store){
        int level=Math.min(4,store.prefs.getInt("bili.riskLevel",0)+1);
        long[] delays={0,30*60000L,2*3600000L,6*3600000L,12*3600000L};
        store.prefs.edit().putInt("bili.riskLevel",level).putLong("bili.riskUntil",System.currentTimeMillis()+delays[level]).commit();
    }
    static String riskMessage(AppStore store){
        return "B 站暂时限制访问，已保留缓存。下次可尝试："+new java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(riskUntil(store)))+"（应用冷却时间）。";
    }
    static boolean incomplete(AppStore store,long uid){
        try{return !store.feed(uid).optBoolean("syncComplete");}catch(Exception e){return false;}
    }
    static long waitMillis(AppStore store,long uid,boolean manual){
        long now=System.currentTimeMillis(),attempt=store.syncAttempt(uid);
        long interval=manual||(incomplete(store,uid)&&store.syncError(uid).isEmpty())?60000:BiliPolicy.INTERVAL_MS;
        long remaining=now<attempt?0:Math.max(0,interval-(now-attempt));
        return Math.max(remaining,Math.max(0,riskUntil(store)-now));
    }
    static boolean due(AppStore store,long uid,boolean manual) {
        return waitMillis(store,uid,manual)==0;
    }

    static String cooldownMessage(AppStore store,long uid){
        if(riskCooling(store))return riskMessage(store);
        long seconds=Math.max(1,(60000-(System.currentTimeMillis()-store.syncAttempt(uid))+999)/1000);
        return (store.syncError(uid).isEmpty()?"刚刚请求过，":"上次更新未成功，请查看页面上的原因；")+seconds+" 秒后可重试。";
    }
    static void run(AppStore store,boolean manual) {long uid=store.selectedCreator();if(uid>0)run(store,uid,manual,new BiliClient(uid));}
    static void run(AppStore store,boolean manual,BiliClient client) {run(store,store.selectedCreator(),manual,client);}
    static synchronized Outcome run(AppStore store,long uid,boolean manual,BiliClient client) {
        if(Thread.currentThread().isInterrupted())return Outcome.CANCELLED;
        if(!store.allowedCreator(uid))return Outcome.DISABLED;
        if(!due(store,uid,manual))return Outcome.COOLDOWN;
        activeUid=uid;
        store.prefs.edit().putLong(store.biliKey(uid,"attempt"),System.currentTimeMillis()).commit();
        try {
            org.json.JSONObject old=store.feed(uid);
            org.json.JSONObject result=client.syncCatalog(old,manual,feed->{
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                synchronized(AppStore.class){
                    if(!store.allowedCreator(uid))throw new InterruptedException();
                    store.feed(uid,feed);
                    if(feed.has("creatorName"))store.updateCreatorProfile(uid,feed.getString("creatorName"),feed.optString("avatar"));
                }
            });
            if(!result.optBoolean("syncComplete"))return Outcome.PARTIAL;
            store.prefs.edit().remove("bili.riskUntil").remove("bili.riskLevel").commit();
            return Outcome.UPDATED;
        }catch(Exception e) {
            if(Thread.currentThread().isInterrupted()||e instanceof InterruptedException)return Outcome.CANCELLED;
            if(e instanceof BiliAccessException)recordRisk(store);
            store.prefs.edit().putString(store.biliKey(uid,"error"),BiliClient.friendly(e)).commit();
            return Outcome.FAILED;
        }finally{
            // Rest between completed batches, including slow requests and errors.
            store.prefs.edit().putLong(store.biliKey(uid,"attempt"),System.currentTimeMillis()).commit();
            activeUid=0;
            if(!Thread.currentThread().isInterrupted()&&store.allowedCreator(uid)&&incomplete(store,uid))
                BiliSyncWorker.continueLater(store.appContext(),store,uid);
        }
    }
}
