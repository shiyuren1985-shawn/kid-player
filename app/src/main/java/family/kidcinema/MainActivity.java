package family.kidcinema;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int BG=0xffFFF8F5, INK=0xff4A2C3A, MUTED=0xff765A67, GREEN=0xffA83D68;
    static final int PANEL=0xffFBE8EF, LINE=0xffE7CBD7, FOCUS=0xff6E3A89, ERROR=0xffA52F49;
    private AppStore store;
    private LinearLayout shell,nav;
    private RecyclerView list;
    private GridLayoutManager layout;
    private Cards adapter;
    private List<LibraryItem> items=new ArrayList<>(),visible=new ArrayList<>();
    private String folder="",section="全部影片",error="",shownPage="",layoutSignature="";
    private boolean loading=false,tv=false,foreground=false;
    private String knownCreators="";
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener creatorChanges=(prefs,key)->onCreatorsChanged(key);
    private int generation=0,renderGeneration=0;
    private Future<?> loadTask;
    private BiliClient syncClient;
    private AlertDialog videoDetail;
    private long detailCreator;
    private String playbackReturnFocus="";
    private boolean leftForPlayback;
    private String pendingNavigationFocus="";
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final ExecutorService settingsIo=Executors.newFixedThreadPool(2);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<String,Position> positions=new HashMap<>();
    private final Runnable autoSync=new Runnable(){public void run(){if(store.online()&&!loading)refreshOnline(false);handler.postDelayed(this,BiliPolicy.INTERVAL_MS);}};
    private static final class Position {
        String anchor="header",focus="";int index,offset;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new AppStore(this);knownCreators=creatorSignature();store.prefs.registerOnSharedPreferenceChangeListener(creatorChanges);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(state!=null){folder=state.getString("folder","");section=state.getString("section","全部影片");
            Position p=new Position();p.anchor=state.getString("anchor","header");p.focus=state.getString("focus","");p.index=state.getInt("index");p.offset=state.getInt("offset");positions.put(page(),p);}
        refresh(false);
    }
    @Override protected void onResume(){super.onResume();foreground=true;if(list!=null){render();if(store.online()&&!loading)refreshOnline(false);}handler.removeCallbacks(autoSync);handler.postDelayed(autoSync,BiliPolicy.INTERVAL_MS);}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus&&list!=null&&tv){Position p=positions.get(shownPage);if(p!=null&&!p.focus.isEmpty())restoreFocus(renderGeneration,p.focus,0);}}
    @Override protected void onPause(){foreground=false;if(!playbackReturnFocus.isEmpty())leftForPlayback=true;capture();handler.removeCallbacks(autoSync);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){capture();super.onSaveInstanceState(state);state.putString("folder",folder);state.putString("section",section);Position p=positions.get(page());if(p!=null){state.putString("anchor",p.anchor);state.putString("focus",p.focus);state.putInt("index",p.index);state.putInt("offset",p.offset);}}
    @Override public void onConfigurationChanged(Configuration config){capture();super.onConfigurationChanged(config);layoutSignature="";render();}
    @Override protected void onDestroy(){store.prefs.unregisterOnSharedPreferenceChangeListener(creatorChanges);cancelLoad();io.shutdownNow();settingsIo.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private String page(){return store.scope()+"|"+section+"|"+(section.equals("全部影片")?folder:"");}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String label,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(label);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private GradientDrawable bg(int color,int radius){GradientDrawable v=new GradientDrawable();v.setColor(color);v.setCornerRadius(dp(radius));return v;}
    private void space(LinearLayout box,int height){box.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private void focusStyle(View view,int base,int radius){
        view.setFocusable(true);view.setBackground(bg(base,radius));
        view.setOnFocusChangeListener((v,focused)->{GradientDrawable d=bg(base,radius);if(focused)d.setStroke(dp(4),base==GREEN?Color.WHITE:FOCUS);v.setBackground(d);});
    }
    private Button button(String label,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextSize(tv?18:16);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:INK);
        b.setPadding(dp(14),dp(10),dp(14),dp(10));b.setMinHeight(dp(52));b.setMinimumHeight(dp(52));b.setMaxLines(2);
        focusStyle(b,primary?GREEN:PANEL,14);b.setOnClickListener(v->action.run());return b;
    }
    private void capture(){
        if(list==null || shownPage.isEmpty() || layout==null || adapter==null)return;
        Position p=new Position();int index=layout.findFirstVisibleItemPosition();if(index<0)return;
        p.index=index;p.anchor=index==0?"header":index<=adapter.data.size()?adapter.data.get(index-1).key():"header";
        View first=layout.findViewByPosition(index);p.offset=first==null?0:first.getTop()-list.getPaddingTop();
        View focused=getCurrentFocus();if(focused!=null && focused.getTag() instanceof String)p.focus=(String)focused.getTag();
        Position old=positions.get(shownPage);if(p.focus.isEmpty() && old!=null)p.focus=old.focus;
        if(!pendingNavigationFocus.isEmpty())p.focus=pendingNavigationFocus;
        if(!playbackReturnFocus.isEmpty())p.focus=playbackReturnFocus;
        positions.put(shownPage,p);
    }
    private void restore(){
        Position p=positions.get(shownPage);int request=++renderGeneration;pendingNavigationFocus="";if(p==null)return;
        int index=0;if(!p.anchor.equals("header")){index=Math.min(p.index,visible.size());for(int i=0;i<visible.size();i++)if(visible.get(i).key().equals(p.anchor)){index=i+1;break;}}
        layout.scrollToPositionWithOffset(index,p.offset);
        if(tv && !p.focus.isEmpty())restoreFocus(request,p.focus,0);
    }
    private void restoreFocus(int request,String tag,int attempt){
        list.postDelayed(()->{if(request!=renderGeneration||isDestroyed())return;View target=shell.findViewWithTag(tag);
            if(!hasWindowFocus())return; // The dialog may still own input during its dismissal callback.
            if(target!=null && target.requestFocusFromTouch()){if(tag.equals(pendingNavigationFocus)){pendingNavigationFocus="";capture();}if(leftForPlayback&&tag.equals(playbackReturnFocus)){playbackReturnFocus="";leftForPlayback=false;}return;}
            if(attempt<5)restoreFocus(request,tag,attempt+1);else {if(tag.equals(pendingNavigationFocus))pendingNavigationFocus="";if(tag.equals(playbackReturnFocus)){playbackReturnFocus="";leftForPlayback=false;}}
        },attempt==0?0:40);
    }
    private void cancelLoad(){generation++;if(loadTask!=null)loadTask.cancel(true);if(syncClient!=null)syncClient.cancel();syncClient=null;loadTask=null;loading=false;}
    private void refresh(){refresh(true);}
    private void refresh(boolean manual){
        if(store.online()){refreshOnline(manual);return;}
        cancelLoad();int request=generation;error="";
        if(store.demo()){
            items=new ArrayList<>();String[] names={"小火箭去旅行","森林里的小秘密","出发，去看山","海洋奇遇记"};
            for(int i=0;i<4;i++)items.add(new LibraryItem(names[i],"sample-"+i,"封面演示 · 共用小火箭短片",false,true,i));render();return;
        }
        if(!section.equals("全部影片")){render();return;}
        if(!store.hasConfig()){items=new ArrayList<>();error="请先在播放设置连接家庭存储。";render();return;}
        loading=true;render();String relative=folder;
        loadTask=io.submit(()->{
            List<LibraryItem> found=new ArrayList<>();String failure="";
            try(SmbLibrary library=new SmbLibrary(store.config())){found=library.list(relative);}catch(Exception e){failure=SmbLibrary.friendly(e);}
            final List<LibraryItem> result=found;final String message=failure;
            runOnUiThread(()->{if(isDestroyed()||request!=generation)return;loading=false;error=message;items=result;store.remember(result);render();});
        });
    }
    private void refreshOnline(boolean manual){
        if(loading)return;
        if(store.remoteCreators() && !store.remoteUrl().isEmpty() && (manual||RemoteConfig.due(store))) {
            refreshManifest(manual);return;
        }
        refreshCreator(manual);
    }
    private void refreshManifest(boolean manual){
        loading=true;int request=++generation;render();
        loadTask=io.submit(()->{RemoteConfig.refresh(store,manual);runOnUiThread(()->{if(isDestroyed()||request!=generation)return;loading=false;items=new ArrayList<>();refreshCreator(manual);});});
    }
    private String creatorSignature(){StringBuilder s=new StringBuilder(Boolean.toString(store.remoteCreators()));for(AppStore.Creator c:store.enabledCreators())s.append(":").append(c.uid);return s.toString();}
    private void onCreatorsChanged(String key){if(key!=null&&key.startsWith("creators"))handler.post(()->{if(foreground&&store.online()&&!knownCreators.equals(creatorSignature())){capture();cancelLoad();if(videoDetail!=null&&detailCreator>0&&!store.allowedCreator(detailCreator))videoDetail.dismiss();items=new ArrayList<>();refreshCreator(false);}});}
    private void refreshCreator(boolean manual){
        knownCreators=creatorSignature();long uid=store.selectedCreator();
        if(uid==0){items=new ArrayList<>();error="";render();return;}
        try{items=BiliClient.items(store.feed(uid),uid);error=store.syncError(uid);}catch(Exception e){items=new ArrayList<>();error=BiliClient.friendly(e);}
        BiliSyncWorker.schedule(this);
        if(!BiliSync.due(store,uid,manual)){render();if(manual)toast("刚刚检查过，稍后再更新；已有影片仍可观看。");return;}
        loading=true;int request=++generation;render();
        BiliClient client=new BiliClient(uid);syncClient=client;
        loadTask=io.submit(()->{
            BiliSync.run(store,uid,manual,client);List<LibraryItem> found=new ArrayList<>();String failure=store.syncError(uid);
            try{found=BiliClient.items(store.feed(uid),uid);}catch(Exception e){failure=BiliClient.friendly(e);}
            final List<LibraryItem> result=found;final String message=failure;
            runOnUiThread(()->{if(isDestroyed()||request!=generation||uid!=store.selectedCreator()||!store.online())return;loading=false;items=result;error=message;render();});
        });
    }
    private void section(String value){capture();section=value;error="";if(!store.online()&&!store.demo()&&value.equals("全部影片"))refresh(false);else render();}
    private void selectCreator(long uid){capture();cancelLoad();store.selectedCreator(uid);folder="";if(tv&&!positions.containsKey(page())){Position p=new Position();p.focus="creator:"+uid;positions.put(page(),p);}items=new ArrayList<>();error="";refreshOnline(false);}
    private void online(){capture();cancelLoad();store.online(true);folder="";section="全部影片";items=new ArrayList<>();error="";refreshOnline(false);}
    private String onlineStatus(){
        if(store.selectedCreator()==0)return (store.remoteCreators()?"请在云端文件配置作者后读取名单":"在播放设置中添加并启用 UP 主");
        try{org.json.JSONObject feed=store.feed();long time=feed.getLong("syncedAt");if(time==0)return items.isEmpty()?"等待首次同步":"初始目录 · 自动同步尚未成功";
            return "上次更新 "+new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(time))+" · 公开合集 "+feed.optInt("collectionCount")+" 个 · 最新 "+feed.getJSONArray("videos").length()+" 条";
        }catch(Exception e){return "目录暂不可用";}
    }
    private List<LibraryItem> filtered(){
        if(!store.online()&&!store.demo()&&!section.equals("全部影片"))return store.history(section.equals("我的喜欢"));
        List<LibraryItem> result=new ArrayList<>();for(LibraryItem item:items){
            if(section.equals("我的喜欢")&&(item.folder||!store.favorite(item.key())))continue;
            if(section.equals("继续观看")&&(item.folder||store.progress(item.key())<=0))continue;result.add(item);
        }return result;
    }
    private void render(){
        capture();Configuration config=getResources().getConfiguration();
        tv=store.mode().equals("电视") || (store.mode().equals("自动") && (config.uiMode&Configuration.UI_MODE_TYPE_MASK)==Configuration.UI_MODE_TYPE_TELEVISION);
        String signature=config.screenWidthDp+":"+tv+":"+config.fontScale;
        visible=filtered();
        if(list==null || !signature.equals(layoutSignature)){layoutSignature=signature;buildShell();}
        shownPage=page();buildNav();adapter.data=new ArrayList<>(visible);adapter.notifyDataSetChanged();restore();
    }
    private void buildShell(){
        int width=getResources().getConfiguration().screenWidthDp;boolean wide=width>=780;float scale=getResources().getConfiguration().fontScale;
        shell=column();shell.setBackgroundColor(BG);shell.setPadding(dp(20),dp(12),dp(20),dp(12));
        shell.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(20)+insets.getSystemWindowInsetLeft(),dp(12)+insets.getSystemWindowInsetTop(),dp(20)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout header=row();ImageView icon=new ImageView(this);icon.setImageResource(R.mipmap.ic_launcher);icon.setContentDescription("思思影院");header.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout brand=column();brand.setPadding(dp(12),0,dp(8),0);brand.addView(text("思思影院",25,INK,true));if(width>600)brand.addView(text("把喜欢的故事，留给思思",13,MUTED,false));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=button("播放设置",false,this::settings);settings.setTag("settings");settings.setContentDescription("播放设置");header.addView(settings);shell.addView(header);space(shell,16);
        LinearLayout body=wide?row():column();body.setGravity(Gravity.TOP);shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        nav=wide?column():row();int navWidth=Math.round(190+Math.max(0,scale-1)*80);
        if(wide){ScrollView navScroll=new ScrollView(this);navScroll.addView(nav);body.addView(navScroll,new LinearLayout.LayoutParams(dp(navWidth),-1));}
        else {HorizontalScrollView navScroll=new HorizontalScrollView(this);navScroll.setHorizontalScrollBarEnabled(false);navScroll.addView(nav);body.addView(navScroll,new LinearLayout.LayoutParams(-1,-2));}
        list=new RecyclerView(this);list.setId(View.generateViewId());list.setContentDescription("影片列表");list.setClipToPadding(false);list.setPadding(dp(wide?18:0),0,dp(2),dp(8));list.setItemAnimator(null);
        int available=width-40-(wide?navWidth+18:0);int minCard=scale>=1.3f?300:240;int columns=Math.max(1,Math.min(4,available/minCard));
        layout=new GridLayoutManager(this,columns);layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup(){public int getSpanSize(int position){return position==0?columns:1;}});list.setLayoutManager(layout);
        adapter=new Cards();list.setAdapter(adapter);body.addView(list,wide?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));
        setContentView(shell);shell.requestApplyInsets();
    }
    private void buildNav(){
        nav.removeAllViews();boolean wide=getResources().getConfiguration().screenWidthDp>=780;
        String[] names={"全部影片","继续观看","我的喜欢"};String[] glyphs={"▦  ","▷  ","♡  "};
        for(int i=0;i<names.length;i++){String name=names[i];Button button=button(glyphs[i]+name,section.equals(name),()->section(name));button.setTag("nav:"+name);button.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(wide?-1:dp(150),-2);p.setMargins(0,0,wide?0:dp(8),dp(10));nav.addView(button,p);}
        Button up=button(store.online()?"✓ UP 主订阅":"UP 主订阅",false,this::online);up.setTag("nav:online");nav.addView(up,new LinearLayout.LayoutParams(wide?-1:dp(150),-2));
        if(wide){space(nav,22);nav.addView(text(store.online()?"只看已选择的作者\n没有推荐与搜索":store.demo()?"●  本地演示\n18 秒静音短片":"家庭存储\n只读指定目录",13,MUTED,false));}
    }
    private View listHeader(){
        LinearLayout box=column();box.setPadding(dp(2),0,dp(10),dp(12));
        if(store.online()){
            HorizontalScrollView authors=new HorizontalScrollView(this);authors.setHorizontalScrollBarEnabled(false);LinearLayout row=row();
            for(AppStore.Creator creator:store.enabledCreators()){
                boolean selected=creator.uid==store.selectedCreator();LinearLayout author=column();author.setGravity(Gravity.CENTER);author.setPadding(dp(10),dp(10),dp(10),dp(10));focusStyle(author,selected?PANEL:BG,18);author.setSelected(selected);author.setTag("creator:"+creator.uid);author.setContentDescription(creator.name+"，UP 主，"+(selected?"已选择":"查看视频"));
                ImageView avatar=new ImageView(this);avatar.setImageResource(R.mipmap.ic_launcher);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setBackground(bg(PANEL,100));avatar.setClipToOutline(true);avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);RemoteImages.load(avatar,creator.avatar);author.addView(avatar,new LinearLayout.LayoutParams(dp(tv?64:56),dp(tv?64:56)));
                TextView name=text((selected?"✓ ":"")+creator.name,tv?16:14,selected?GREEN:INK,true);name.setMaxLines(2);name.setGravity(Gravity.CENTER);name.setEllipsize(android.text.TextUtils.TruncateAt.END);author.addView(name);author.setOnClickListener(v->selectCreator(creator.uid));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(tv?140:124),-2);p.setMargins(0,0,dp(8),0);row.addView(author,p);
            }
            authors.addView(row);box.addView(authors);space(box,12);
        }
        LinearLayout titleRow=row();String title=section;
        if(section.equals("全部影片")){if(store.online()){AppStore.Creator c=store.creator(store.selectedCreator());title=c==null?"我的 UP 主":c.name+"的视频";}else if(!folder.isEmpty())title=folder.substring(folder.lastIndexOf('/')+1);}
        titleRow.addView(text(title,tv?24:22,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        if(!folder.isEmpty() && section.equals("全部影片"))titleRow.addView(button("‹ 上一层",false,this::up));
        Button refresh=button(loading?"更新中":"刷新",false,this::refresh);refresh.setTag("refresh");refresh.setEnabled(!loading);titleRow.addView(refresh);box.addView(titleRow);space(box,6);
        box.addView(text(store.online()?onlineStatus():store.demo()?"4 种封面 · 共用 1 段 18 秒静音演示片":section.equals("全部影片")?"当前文件夹 · 可进入子文件夹":"允许目录内的全部"+section,13,MUTED,false));
        if(store.online()){space(box,6);box.addView(text("公开合集最新 30 条；未加入合集的投稿可能遗漏。",13,MUTED,false));if(store.remoteCreators()&&!store.prefs.getString("remote.error","").isEmpty())box.addView(text("云端名单更新未完成，保留上次名单。",13,ERROR,false));}
        if(loading){space(box,8);box.addView(text(store.online()?(items.isEmpty()?"正在读取作者目录…":"正在更新，已有影片可以继续观看…"):"正在读取家庭存储…",14,GREEN,false));}
        if(!error.isEmpty()){space(box,8);TextView failure=text((store.online()?"本次更新未完成，保留上次目录\n":"")+error,14,ERROR,false);box.addView(failure);}
        if(section.equals("全部影片")&&!items.isEmpty()){
            LibraryItem pick=null;for(LibraryItem i:items)if(!i.folder&&store.progress(i.key())>0){pick=i;break;}if(pick==null)for(LibraryItem i:items)if(!i.folder){pick=i;break;}
            if(pick!=null){final LibraryItem item=pick;space(box,12);LinearLayout banner=row();banner.setPadding(dp(16),dp(12),dp(16),dp(12));banner.setBackground(bg(PANEL,18));
                LinearLayout words=column();words.addView(text(store.progress(item.key())>0?"继续这个故事":"今天想看这一部吗？",14,GREEN,true));TextView name=text(item.name,16,INK,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(name);banner.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                Button play=button(store.demo()?"▶  播放演示短片":store.progress(item.key())>0?"继续播放":"播放",true,()->play(item,false,"hero"));play.setTag("hero");banner.addView(play);box.addView(banner);
            }
        }
        if(visible.isEmpty()&&!loading){space(box,14);String message=section.equals("我的喜欢")?"还没有喜欢的影片。打开影片详情，点一下喜欢。":section.equals("继续观看")?"看过的故事，会在这里等你继续。":store.online()?(store.remoteCreators()?"请在云端文件配置作者，再读取名单并更新目录。":"在播放设置添加并启用 UP 主，然后更新目录。"):"这个文件夹还没有影片。";TextView empty=text(message,17,MUTED,false);empty.setPadding(dp(20),dp(24),dp(20),dp(24));empty.setBackground(bg(PANEL,18));box.addView(empty);}
        return box;
    }
    private final class Cards extends RecyclerView.Adapter<Cards.Holder>{
        List<LibraryItem> data=new ArrayList<>();
        final class Holder extends RecyclerView.ViewHolder{Holder(LinearLayout v){super(v);}}
        public int getItemCount(){return data.size()+1;}
        public int getItemViewType(int position){return position==0?0:1;}
        public Holder onCreateViewHolder(android.view.ViewGroup parent,int type){LinearLayout box=column();box.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(box);}
        public void onBindViewHolder(Holder holder,int position){LinearLayout box=(LinearLayout)holder.itemView;box.removeAllViews();box.setPadding(0,0,position==0?0:dp(12),dp(14));box.addView(position==0?listHeader():card(data.get(position-1)));}
    }
    private ImageView picture(String url,int height){ImageView image=new ImageView(this);image.setImageResource(R.drawable.heartsping_foreground);image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);image.setBackground(bg(PANEL,14));image.setClipToOutline(true);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);image.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(height)));if(!url.isEmpty()){image.setScaleType(ImageView.ScaleType.CENTER_CROP);RemoteImages.load(image,url);}return image;}
    private View card(LibraryItem item){
        LinearLayout card=column();focusStyle(card,Color.WHITE,18);card.setPadding(dp(5),dp(5),dp(5),dp(12));card.setTag("card:"+item.key());card.setContentDescription(item.name+(item.folder?"，文件夹":"，打开影片详情"));
        if(item.demo)card.addView(new ArtView(this,item.art),new LinearLayout.LayoutParams(-1,dp(132)));else card.addView(picture(item.image,tv?145:135));
        LinearLayout caption=column();caption.setPadding(dp(10),dp(12),dp(10),0);TextView name=text((item.folder?"文件夹 · ":"")+item.name,tv?20:18,INK,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(name);space(caption,6);
        TextView subtitle=text(item.subtitle,tv?14:13,MUTED,false);subtitle.setMaxLines(2);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(subtitle);
        long progress=store.progress(item.key());if(progress>0){space(caption,5);caption.addView(text("继续 "+time(progress),14,GREEN,true));}if(store.favorite(item.key())){space(caption,5);caption.addView(text("♥  已喜欢",14,GREEN,true));}card.addView(caption);
        card.setOnClickListener(v->{capture();Position p=positions.get(page());if(p!=null)p.focus="card:"+item.key();if(item.folder){cancelLoad();folder=item.path;items=new ArrayList<>();refresh(false);}else detail(item);});return card;
    }
    static String time(long ms){long seconds=ms/1000;return String.format(Locale.CHINA,"%02d:%02d",seconds/60,seconds%60);}
    private void detail(LibraryItem item){
        ScrollView scroll=new ScrollView(this);LinearLayout panel=column();panel.setPadding(dp(22),dp(10),dp(22),dp(16));scroll.addView(panel);panel.addView(picture(item.image,150));space(panel,12);
        panel.addView(text(item.demo?"四张卡片共用 18 秒静音演示短片。":item.subtitle,15,MUTED,false));
        if(item.online){space(panel,8);panel.addView(text("只播放此作者公开内容。多分 P 视频暂只播放第一 P。",13,MUTED,false));}
        long progress=store.progress(item.key());if(progress>0){space(panel,10);panel.addView(text("上次看到 "+time(progress),15,GREEN,true));}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(item.name).setView(scroll).setPositiveButton(progress>0?"继续播放":"播放",(d,w)->play(item,false)).setNegativeButton("返回",null).create();
        space(panel,12);Button favorite=button(store.favorite(item.key())?"取消喜欢":"♡ 喜欢",false,()->{});favorite.setOnClickListener(v->{store.remember(Collections.singletonList(item));store.toggleFavorite(item.key());favorite.setText(store.favorite(item.key())?"取消喜欢":"♡ 喜欢");favorite.announceForAccessibility(store.favorite(item.key())?"已喜欢":"已取消喜欢");});panel.addView(favorite);
        if(progress>0){space(panel,8);panel.addView(button("从头播放",false,()->{dialog.dismiss();play(item,true);}));}
        videoDetail=dialog;detailCreator=item.online?item.creatorUid:0;
        dialog.setOnDismissListener(d->{if(videoDetail==dialog){videoDetail=null;detailCreator=0;}if(!isDestroyed())render();});dialog.show();if(tv){focusStyle(dialog.getButton(-1),PANEL,12);focusStyle(dialog.getButton(-2),PANEL,12);dialog.getButton(-1).requestFocus();}
    }
    private void play(LibraryItem item,boolean fromStart){play(item,fromStart,"card:"+item.key());}
    private void play(LibraryItem item,boolean fromStart,String returnFocus){
        if(item.folder)return;playbackReturnFocus=tv?returnFocus:"";leftForPlayback=false;capture();store.remember(Collections.singletonList(item));
        Intent intent=new Intent(this,PlayerActivity.class).putExtra("online",item.online).putExtra("demo",item.demo).putExtra("path",item.path).putExtra("title",item.name).putExtra("creatorUid",item.creatorUid).putExtra("fromStart",fromStart);startActivity(intent);
    }
    private void up(){capture();cancelLoad();if(folder.isEmpty())return;int i=folder.lastIndexOf('/');folder=i<0?"":folder.substring(0,i);items=new ArrayList<>();refresh(false);}
    private boolean focusCard(int index){
        if(index<0||index>=visible.size())return true;
        String tag="card:"+visible.get(index).key();View target=shell.findViewWithTag(tag);
        if(target!=null&&target.requestFocusFromTouch()){capture();return true;}
        pendingNavigationFocus=tag;layout.scrollToPosition(index+1);restoreFocus(renderGeneration,tag,0);return true;
    }
    private boolean focusHeader(){
        String tag=store.online()&&!store.enabledCreators().isEmpty()?"creator:"+store.selectedCreator():"hero";
        View target=shell.findViewWithTag(tag);if(target!=null&&target.requestFocusFromTouch())return true;
        pendingNavigationFocus=tag;layout.scrollToPosition(0);restoreFocus(renderGeneration,tag,0);return true;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();boolean arrow=key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;
        View focused=getCurrentFocus();
        if(tv&&hasWindowFocus()&&event.getAction()==KeyEvent.ACTION_DOWN&&arrow){
            if(!pendingNavigationFocus.isEmpty())return true;
            String tag=focused!=null&&focused.getTag() instanceof String?(String)focused.getTag():"";
            if(key==KeyEvent.KEYCODE_DPAD_DOWN&&tag.equals("settings")){
                View author=shell.findViewWithTag("creator:"+store.selectedCreator());if(author!=null&&author.requestFocusFromTouch())return true;
                if(!visible.isEmpty())return focusCard(Math.max(0,layout.findFirstVisibleItemPosition()-1));
            }
            if(key==KeyEvent.KEYCODE_DPAD_DOWN&&(tag.startsWith("creator:")||tag.equals("hero")||tag.equals("refresh"))&&!visible.isEmpty())return focusCard(0);
            if(tag.startsWith("card:")){
                int index=-1;for(int i=0;i<visible.size();i++)if(tag.equals("card:"+visible.get(i).key())){index=i;break;}
                if(index>=0){int columns=layout.getSpanCount();
                    if(key==KeyEvent.KEYCODE_DPAD_DOWN)return focusCard(index+columns);
                    if(key==KeyEvent.KEYCODE_DPAD_UP)return index<columns?focusHeader():focusCard(index-columns);
                    if(key==KeyEvent.KEYCODE_DPAD_RIGHT)return index%columns==columns-1?true:focusCard(index+1);
                    if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(index%columns!=0)return focusCard(index-1);View navItem=shell.findViewWithTag("nav:"+section);if(navItem!=null)navItem.requestFocusFromTouch();return true;}
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){if(!section.equals("全部影片")){section("全部影片");return;}if(!folder.isEmpty()){up();return;}super.onBackPressed();}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private EditText field(LinearLayout panel,String label,String value,String hint,boolean password){panel.addView(text(label,14,INK,true));EditText input=new EditText(this);input.setText(value);input.setHint(hint);input.setTextSize(16);input.setSingleLine(true);input.setContentDescription(label);input.setMinHeight(dp(56));input.setInputType(password?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);panel.addView(input,new LinearLayout.LayoutParams(-1,-2));space(panel,10);return input;}
    private void settings(){
        AppStore.Config previous;try{previous=store.config();}catch(Exception e){previous=new AppStore.Config();}
        ScrollView scroll=new ScrollView(this);LinearLayout panel=column();panel.setPadding(dp(22),dp(12),dp(22),dp(18));scroll.addView(panel);
        panel.addView(text("内容来源",17,INK,true));RadioGroup sources=new RadioGroup(this);String[] sourceNames={"UP 主订阅","家庭存储","本地演示"};
        int[] sourceIds={R.id.source_online,R.id.source_smb,R.id.source_demo};
        for(int i=0;i<sourceNames.length;i++){RadioButton radio=new RadioButton(this);radio.setId(sourceIds[i]);radio.setText(sourceNames[i]);radio.setTextSize(16);radio.setMinHeight(dp(52));sources.addView(radio);}sources.check(store.online()?R.id.source_online:store.demo()?R.id.source_demo:R.id.source_smb);panel.addView(sources);
        LinearLayout onlinePanel=column();onlinePanel.addView(text("点击头像分类观看。每位作者独立更新公开合集最新 30 条，未加入合集的投稿可能遗漏。",14,MUTED,false));space(onlinePanel,10);onlinePanel.addView(button("管理 UP 主名单",false,this::manageCreators));panel.addView(onlinePanel);
        LinearLayout smbPanel=column();panel.addView(smbPanel);smbPanel.addView(text("请使用家庭存储的只读账号，并选择允许观看的目录。",14,MUTED,false));space(smbPanel,10);
        EditText host=field(smbPanel,"家庭存储地址",previous.host,"例如 192.168.1.10",false);
        EditText port=field(smbPanel,"SMB 端口",Integer.toString(previous.port),"445",false);port.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText share=field(smbPanel,"共享名称",previous.share,"例如 家庭共享",false);
        EditText root=field(smbPanel,"允许访问的文件夹",previous.root,"留空表示整个上述共享",false);
        EditText user=field(smbPanel,"共享用户名",previous.user,"只读账号",false);
        EditText password=field(smbPanel,"共享密码",previous.password,"仅保存在本机加密存储中",true);
        EditText domain=field(smbPanel,"域（通常留空）",previous.domain,"",false);
        TextView status=text("尚未测试本次配置",14,MUTED,false);smbPanel.addView(status);
        java.util.function.Supplier<AppStore.Config> collect=()->{
            for(EditText field:new EditText[]{host,port,share,root})field.setError(null);
            if(host.getText().toString().trim().isEmpty()){invalid(host,"请填写家庭存储地址");return null;}
            AppStore.Config c=new AppStore.Config();c.host=host.getText().toString().trim();
            try{c.port=Integer.parseInt(port.getText().toString());if(c.port<1||c.port>65535)throw new NumberFormatException();}catch(Exception e){invalid(port,"请输入 1–65535 之间的端口");return null;}
            try{c.share=PathPolicy.share(share.getText().toString());}catch(Exception e){invalid(share,"请填写一级共享名称");return null;}
            try{c.root=PathPolicy.clean(root.getText().toString());}catch(Exception e){invalid(root,"请填写共享内相对文件夹，不能包含 .. 或完整地址");return null;}
            c.user=user.getText().toString();c.password=password.getText().toString();c.domain=domain.getText().toString();return c;
        };
        Button test=button("测试此目录的读取",false,()->{});smbPanel.addView(test);final Future<?>[] testing={null};
        test.setOnClickListener(v->{AppStore.Config c=collect.get();if(c==null)return;if(testing[0]!=null)testing[0].cancel(true);test.setEnabled(false);status.setText("正在连接并读取目录…");testing[0]=settingsIo.submit(()->{String result;try(SmbLibrary library=new SmbLibrary(c)){result="读取成功："+library.list("").size()+" 个视频或文件夹";}catch(Exception e){result=SmbLibrary.friendly(e);}String message=result;runOnUiThread(()->{if(!isDestroyed()){test.setEnabled(true);status.setText(message);}});});});
        TextView demo=text("本地演示为同一段 18 秒静音短片。",14,MUTED,false);panel.addView(demo);
        Runnable display=()->{int value=sources.getCheckedRadioButtonId();onlinePanel.setVisibility(value==R.id.source_online?View.VISIBLE:View.GONE);smbPanel.setVisibility(value==R.id.source_smb?View.VISIBLE:View.GONE);demo.setVisibility(value==R.id.source_demo?View.VISIBLE:View.GONE);};sources.setOnCheckedChangeListener((g,id)->display.run());display.run();
        space(panel,18);panel.addView(text("界面模式",16,INK,true));Spinner modes=new Spinner(this);String[] names={"自动","平板","电视"};modes.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));modes.setSelection(Math.max(0,Arrays.asList(names).indexOf(store.mode())));modes.setContentDescription("界面模式");panel.addView(modes,new LinearLayout.LayoutParams(-1,dp(56)));
        space(panel,10);panel.addView(text("思思影院 0.4.0 · 家庭自用原型",13,MUTED,false));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("播放设置").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnDismissListener(d->{if(testing[0]!=null)testing[0].cancel(true);if(!isDestroyed())render();});dialog.show();dialog.getWindow().setLayout(Math.min(dp(650),getResources().getDisplayMetrics().widthPixels-dp(32)),(int)(getResources().getDisplayMetrics().heightPixels*.88));
        dialog.getButton(-1).setOnClickListener(v->{int source=sources.getCheckedRadioButtonId();try{
            if(source==R.id.source_smb){AppStore.Config c=collect.get();if(c==null)return;store.save(c);}
            capture();cancelLoad();store.demo(source==R.id.source_demo);store.online(source==R.id.source_online);store.mode(names[modes.getSelectedItemPosition()]);folder="";section="全部影片";items=new ArrayList<>();error="";dialog.dismiss();refresh(false);
        }catch(Exception e){toast("设置保存失败，请稍后重试。");}});
    }
    private void invalid(EditText field,String message){field.setError(message);field.requestFocus();field.requestRectangleOnScreen(new android.graphics.Rect(0,0,field.getWidth(),field.getHeight()),false);}
    private void manageCreators(){
        ScrollView scroll=new ScrollView(this);LinearLayout panel=column();panel.setPadding(dp(22),dp(14),dp(22),dp(18));scroll.addView(panel);
        RadioGroup source=new RadioGroup(this);RadioButton remote=new RadioButton(this);remote.setId(R.id.creators_cloud);remote.setText("云端名单（推荐）");remote.setMinHeight(dp(52));source.addView(remote);RadioButton local=new RadioButton(this);local.setId(R.id.creators_local);local.setText("本机管理");local.setMinHeight(dp(52));source.addView(local);source.check(store.remoteCreators()?R.id.creators_cloud:R.id.creators_local);panel.addView(source);panel.addView(text("名单改动立即生效，独立于外层播放设置的保存。",13,MUTED,false));
        LinearLayout cloud=column();panel.addView(cloud);cloud.addView(text("在你自己的 HTTPS JSON 文件中设置 UID，App 自动读取。此模式下不能在 App 内增删作者。",15,MUTED,false));space(cloud,12);
        EditText url=field(cloud,"云端名单地址",store.remoteUrl(),"HTTPS JSON 文件地址",false);url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        TextView status=text(remoteSummary(),14,MUTED,false);cloud.addView(status);space(cloud,10);Button fetch=button("读取并使用云端名单",true,()->{});cloud.addView(fetch);
        LinearLayout localPanel=column();localPanel.addView(text("本机名单与云端名单分别保存。切回云端时，以文件中的名单为准。",14,MUTED,false));space(localPanel,10);Button editLocal=button("编辑本机 UP 主",false,()->{});localPanel.addView(editLocal);panel.addView(localPanel);
        TextView active=text("",14,INK,true);space(panel,12);panel.addView(active);Runnable update=()->{cloud.setVisibility(store.remoteCreators()?View.VISIBLE:View.GONE);localPanel.setVisibility(store.remoteCreators()?View.GONE:View.VISIBLE);active.setText("当前启用 "+store.enabledCreators().size()+" / "+store.creators().size()+" 位 UP 主");};update.run();editLocal.setOnClickListener(v->localCreators(update));
        source.setOnCheckedChangeListener((g,id)->{capture();cancelLoad();store.remoteCreators(id==R.id.creators_cloud);items=new ArrayList<>();error="";update.run();refresh(false);});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("UP 主名单来源").setView(scroll).setPositiveButton("完成",null).create();final Future<?>[] request={null};
        fetch.setOnClickListener(v->{String value;try{value=RemoteConfig.checkedUrl(url.getText().toString());}catch(Exception e){invalid(url,"请填写有效的 HTTPS 文件地址");return;}fetch.setEnabled(false);status.setText("正在读取云端名单…");request[0]=settingsIo.submit(()->{boolean applied=RemoteConfig.refresh(store,value,true,RemoteConfig::fetch);runOnUiThread(()->{if(isDestroyed()||!dialog.isShowing())return;fetch.setEnabled(true);String failure=store.prefs.getString("remote.error","");status.setText(applied?remoteSummary():failure.isEmpty()?"刚刚检查过，请稍后再试。":failure);update.run();if(applied){cancelLoad();items=new ArrayList<>();refreshCreator(false);}});});});
        dialog.setOnDismissListener(d->{if(request[0]!=null)request[0].cancel(true);if(!isDestroyed())render();});dialog.show();dialog.getWindow().setLayout(Math.min(dp(680),getResources().getDisplayMetrics().widthPixels-dp(32)),(int)(getResources().getDisplayMetrics().heightPixels*.85));
    }
    private String remoteSummary(){
        if(store.remoteUrl().isEmpty())return "尚未连接云端文件，当前使用初始名单。";
        long time=store.prefs.getLong("remote.syncedAt",0);String error=store.prefs.getString("remote.error","");
        return (time==0?"尚未成功读取":"上次读取 "+new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(time)))+(error.isEmpty()?"":"\n"+error);
    }
    private void localCreators(Runnable onChanged){
        if(store.remoteCreators()){toast("云端名单请在网上的文件中修改");return;}
        ScrollView scroll=new ScrollView(this);LinearLayout panel=column();panel.setPadding(dp(22),dp(12),dp(22),dp(16));scroll.addView(panel);
        panel.addView(text("选择思思可以观看的 UP 主。停用或移除后，其视频不再显示；观看记录保留在本机。",14,MUTED,false));
        LinearLayout creators=column();panel.addView(creators);
        Runnable[] draw={null};draw[0]=()->{creators.removeAllViews();for(AppStore.Creator creator:store.creators()){
            space(creators,12);LinearLayout card=row();ImageView avatar=picture(creator.avatar,50);card.addView(avatar,new LinearLayout.LayoutParams(dp(50),dp(50)));
            Switch enabled=new Switch(this);enabled.setText(creator.name+"\nUID "+creator.uid);enabled.setTextColor(INK);enabled.setTextSize(15);enabled.setChecked(creator.enabled);enabled.setPadding(dp(10),0,dp(8),0);enabled.setMinHeight(dp(56));enabled.setContentDescription("启用 "+creator.name);card.addView(enabled,new LinearLayout.LayoutParams(0,-2,1));
            enabled.setOnCheckedChangeListener((v,checked)->{try{store.creatorEnabled(creator.uid,checked);cancelLoad();items=new ArrayList<>();error="";refresh(false);}catch(Exception e){toast("未能保存，请重试");}});
            card.addView(button("移除",false,()->{try{store.removeCreator(creator.uid);cancelLoad();items=new ArrayList<>();error="";draw[0].run();refresh(false);}catch(Exception e){toast("移除失败，请重试");}}));creators.addView(card);
        }};draw[0].run();space(panel,18);EditText uid=field(panel,"新增 UP 主 UID","","例如 402576555",false);uid.setInputType(InputType.TYPE_CLASS_NUMBER);
        TextView status=text("填写主页上的 UID，核对昵称和头像后添加。",14,MUTED,false);panel.addView(status);Button lookup=button("查找 UP 主",true,()->{});panel.addView(lookup);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("管理 UP 主").setView(scroll).setPositiveButton("完成",null).create();final Future<?>[] task={null};
        lookup.setOnClickListener(v->{long id;try{id=BiliPolicy.creatorUid(Long.parseLong(uid.getText().toString().trim()));}catch(Exception e){invalid(uid,"请输入有效的数字 UID");return;}if(store.creator(id)!=null){invalid(uid,"已经添加过这位 UP 主");return;}
            lookup.setEnabled(false);status.setText("正在核对 UP 主资料…");task[0]=settingsIo.submit(()->{AppStore.Creator found=null;String failure="";try{found=new BiliClient(id).profile();}catch(Exception e){failure=BiliClient.friendly(e);}AppStore.Creator creator=found;String message=failure;
                runOnUiThread(()->{if(isDestroyed()||!dialog.isShowing())return;lookup.setEnabled(true);if(creator==null){status.setText(message);return;}
                    LinearLayout preview=column();preview.setGravity(Gravity.CENTER_HORIZONTAL);preview.setPadding(dp(24),dp(12),dp(24),dp(12));preview.addView(picture(creator.avatar,90),new LinearLayout.LayoutParams(dp(90),dp(90)));space(preview,12);TextView identity=text(creator.name+"\nUID "+creator.uid,18,INK,true);identity.setGravity(Gravity.CENTER);preview.addView(identity);
                    new AlertDialog.Builder(this).setTitle("添加这位 UP 主？").setView(preview).setPositiveButton("添加",(d,w)->{try{store.addCreator(creator);uid.setText("");status.setText("已添加 "+creator.name);draw[0].run();render();}catch(Exception e){status.setText(e.getMessage());}}).setNegativeButton("取消",null).show();
                });});});
        dialog.setOnDismissListener(d->{if(task[0]!=null)task[0].cancel(true);if(!isDestroyed()){onChanged.run();render();}});dialog.show();dialog.getWindow().setLayout(Math.min(dp(680),getResources().getDisplayMetrics().widthPixels-dp(32)),(int)(getResources().getDisplayMetrics().heightPixels*.85));
    }
}
