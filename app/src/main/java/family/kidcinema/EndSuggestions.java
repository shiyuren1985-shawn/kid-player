package family.kidcinema;

import java.util.*;

/** A local choice from the current approved catalog, never a platform recommendation feed. */
final class EndSuggestions {
    static List<LibraryItem> forCreator(AppStore store,long uid,String current) {
        List<LibraryItem> result=new ArrayList<>();
        if(!store.online()||!store.allowedCreator(uid))return result;
        try {
            for(LibraryItem item:BiliClient.items(store.feed(uid),uid)) {
                if(item.creatorUid==uid&&!item.path.equals(current))result.add(item);
            }
            // Unfinished stories first; catalog order remains stable within each group.
            result.sort(Comparator.comparingInt(item->store.progress("bili:"+uid,item.key())>0?0:1));
            return new ArrayList<>(result.subList(0,Math.min(4,result.size())));
        }catch(Exception ignored){return Collections.emptyList();}
    }
}
