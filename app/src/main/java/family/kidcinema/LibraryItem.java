package family.kidcinema;

import org.json.JSONObject;

public final class LibraryItem {
    public final String name,path,subtitle,image;
    public final boolean folder,demo,online;
    public final int art;
    public final long creatorUid;
    public LibraryItem(String name,String path,String subtitle,boolean folder,boolean demo,int art) {
        this(name,path,subtitle,folder,demo,art,false,0,"");
    }
    private LibraryItem(String name,String path,String subtitle,boolean folder,boolean demo,int art,boolean online,long creatorUid,String image) {
        this.name=name;this.path=path;this.subtitle=subtitle;this.folder=folder;this.demo=demo;this.art=art;this.online=online;this.creatorUid=creatorUid;this.image=image;
    }
    public static LibraryItem online(String name,String id,String subtitle,int art) {return online(name,id,subtitle,art,BiliPolicy.UID,"");}
    public static LibraryItem online(String name,String id,String subtitle,int art,long uid,String image) {
        return new LibraryItem(name,BiliPolicy.bvid(id),subtitle,false,false,art,true,BiliPolicy.creatorUid(uid),image);
    }
    public String key() {return (online?"bili:":demo?"demo:":"smb:")+path;}
    JSONObject json() throws Exception {return new JSONObject().put("name",name).put("path",path).put("subtitle",subtitle).put("art",art);}
    static LibraryItem fromJson(JSONObject row) throws Exception {return new LibraryItem(row.getString("name"),PathPolicy.clean(row.getString("path")),row.optString("subtitle"),false,false,row.optInt("art"));}
}
