package family.kidcinema;

import java.text.Normalizer;
import java.util.*;
import org.json.JSONObject;

/** Searches saved creator catalogs only. Never performs network requests. */
final class CatalogSearch {
    static final class Result {
        final List<LibraryItem> videos;
        final int creators, incomplete;
        Result(List<LibraryItem> videos,int creators,int incomplete){this.videos=videos;this.creators=creators;this.incomplete=incomplete;}
    }
    static String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);}
    static Result search(AppStore store,String query,boolean all,long current){
        String normalized=normalize(query);List<LibraryItem> found=new ArrayList<>();
        if(normalized.isEmpty())return new Result(found,0,0);
        String[] words=normalized.split("\\s+");Set<String> seen=new HashSet<>();int authors=0,incomplete=0;
        for(AppStore.Creator creator:store.enabledCreators()){
            if(Thread.currentThread().isInterrupted())break;
            if(!all&&creator.uid!=current)continue;
            authors++;boolean complete=false;
            try{
                JSONObject feed=store.feed(creator.uid);
                List<LibraryItem> catalog=BiliClient.items(feed,creator.uid);complete=feed.optBoolean("syncComplete");
                for(LibraryItem item:catalog){
                    String title=normalize(item.name);boolean match=true;
                    for(String word:words)if(!title.contains(word)){match=false;break;}
                    if(match&&seen.add(creator.uid+":"+item.key()))found.add(item);
                }
            }catch(Exception ignored){}
            if(!complete)incomplete++;
        }
        return new Result(found,authors,incomplete);
    }
}
