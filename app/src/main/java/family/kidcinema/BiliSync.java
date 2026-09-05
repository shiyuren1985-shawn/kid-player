package family.kidcinema;

/** Keeps the last successful metadata document; never saves media URLs or video files. */
final class BiliSync {
    private BiliSync() {}
    static void run(AppStore store, boolean manual) {run(store,manual,new BiliClient());}
    static synchronized void run(AppStore store, boolean manual, BiliClient client) {
        long now=System.currentTimeMillis(),elapsed=now-store.syncAttempt();
        if(elapsed>=0 && elapsed<(manual?60000:BiliPolicy.INTERVAL_MS))return;
        store.prefs.edit().putLong("bili.attempt",now).commit();
        try {store.feed(client.syncCollections());}
        catch(Exception e){store.prefs.edit().putString("bili.error",BiliClient.friendly(e)).commit();}
    }
}
