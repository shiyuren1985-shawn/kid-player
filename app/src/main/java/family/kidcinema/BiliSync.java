package family.kidcinema;

/** Independent creator cooldowns/caches, with a single sync operation per process. */
final class BiliSync {
    private BiliSync() {}
    enum Outcome { UPDATED, COOLDOWN, DISABLED, FAILED, CANCELLED }
    static boolean due(AppStore store,long uid,boolean manual) {
        long elapsed=System.currentTimeMillis()-store.syncAttempt(uid);
        return elapsed<0 || elapsed>=(manual?60000:BiliPolicy.INTERVAL_MS);
    }
    static void run(AppStore store,boolean manual) {long uid=store.selectedCreator();if(uid>0)run(store,uid,manual,new BiliClient(uid));}
    static void run(AppStore store,boolean manual,BiliClient client) {run(store,store.selectedCreator(),manual,client);}
    static synchronized Outcome run(AppStore store,long uid,boolean manual,BiliClient client) {
        if(Thread.currentThread().isInterrupted())return Outcome.CANCELLED;
        if(!store.allowedCreator(uid))return Outcome.DISABLED;
        if(!due(store,uid,manual))return Outcome.COOLDOWN;
        store.prefs.edit().putLong(store.biliKey(uid,"attempt"),System.currentTimeMillis()).commit();
        try {
            org.json.JSONObject feed=client.syncCollections();
            if(Thread.currentThread().isInterrupted())return Outcome.CANCELLED;
            synchronized(AppStore.class) {
                if(!store.allowedCreator(uid))return Outcome.DISABLED;
                store.feed(uid,feed);
                String name=feed.optString("creatorName"),avatar=feed.optString("avatar");
                if(!name.isEmpty())store.updateCreatorProfile(uid,name,avatar);
            }
            return Outcome.UPDATED;
        }catch(Exception e) {
            if(Thread.currentThread().isInterrupted()||e instanceof InterruptedException)return Outcome.CANCELLED;
            store.prefs.edit().putString(store.biliKey(uid,"error"),BiliClient.friendly(e)).commit();
            return Outcome.FAILED;
        }
    }
}
