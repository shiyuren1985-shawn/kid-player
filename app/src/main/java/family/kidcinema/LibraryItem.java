package family.kidcinema;

public final class LibraryItem {
    public final String name, path, subtitle;
    public final boolean folder, demo, online;
    public final int art;
    public LibraryItem(String name, String path, String subtitle, boolean folder, boolean demo, int art) {
        this(name,path,subtitle,folder,demo,art,false);
    }
    private LibraryItem(String name, String path, String subtitle, boolean folder, boolean demo, int art, boolean online) {
        this.name = name; this.path = path; this.subtitle = subtitle;
        this.folder = folder; this.demo = demo; this.art = art; this.online=online;
    }
    public static LibraryItem online(String name,String id,String subtitle,int art){return new LibraryItem(name,BiliPolicy.bvid(id),subtitle,false,false,art,true);}
    public String key() { return (online ? "bili:" : demo ? "demo:" : "smb:") + path; }
}
