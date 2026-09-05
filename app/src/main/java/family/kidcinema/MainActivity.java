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
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int BG=0xfff7f5f0, INK=0xff283b31, MUTED=0xff69776e, GREEN=0xff526b56, LINE=0xffe5e7dd;
    private AppStore store;
    private LinearLayout shell, body, main;
    private TextView connectionLabel;
    private List<LibraryItem> items=new ArrayList<>();
    private String folder="", section="全部影片", error="";
    private boolean loading=false, tv=false;
    private int generation=0;
    private final Handler syncHandler=new Handler(Looper.getMainLooper());
    private final Runnable autoSync=new Runnable(){public void run(){if(store.online()&&!loading)refreshOnline(false);syncHandler.postDelayed(this,BiliPolicy.INTERVAL_MS);}};
    private final ExecutorService io=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new AppStore(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(state!=null){folder=state.getString("folder","");section=state.getString("section","全部影片");}
        refresh();
    }
    @Override protected void onResume(){super.onResume();if(main!=null){if(store.online()&&!loading)refreshOnline(false);else render();}syncHandler.postDelayed(autoSync,BiliPolicy.INTERVAL_MS);}
    @Override protected void onPause(){syncHandler.removeCallbacks(autoSync);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("folder",folder);b.putString("section",section);}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);render();}
    @Override protected void onDestroy(){generation++;io.shutdownNow();super.onDestroy();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setFontFeatureSettings("kern");return t;}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void space(LinearLayout l,int height){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private Button button(String label, boolean primary, Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextSize(tv?17:15);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:INK);b.setPadding(dp(18),dp(6),dp(18),dp(6));
        focusStyle(b,primary?GREEN:0xffeeefe7,14);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setOnClickListener(v->action.run());return b;
    }
    private void focusStyle(View view,int base,int radius){
        view.setBackground(bg(base,radius));view.setFocusable(true);view.setOnFocusChangeListener((v,focused)->{
            GradientDrawable d=bg(base,radius);if(focused)d.setStroke(dp(3),0xffd28b38);v.setBackground(d);
            v.setElevation(focused?dp(5):0);
        });
    }
    private void resolveMode(){
        boolean detected=(getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK)==Configuration.UI_MODE_TYPE_TELEVISION;
        tv=store.mode().equals("电视") || (store.mode().equals("自动") && detected);
    }
    private void refresh(){
        if(store.online()){refreshOnline(true);return;}
        int request=++generation;error="";
        if(store.demo()){
            loading=false;items=new ArrayList<>();
            String[] names={"小火箭去旅行","森林里的小秘密","出发，去看山","海洋奇遇记"};
            for(int i=0;i<4;i++)items.add(new LibraryItem(names[i],"sample-"+i,"封面演示 · 共用小火箭短片",false,true,i));
            render();return;
        }
        if(!store.hasConfig()){loading=false;items=new ArrayList<>();error="请家长先设置家庭存储连接。";render();return;}
        items=new ArrayList<>();loading=true;render();
        String relative=folder;
        io.execute(()->{
            List<LibraryItem> found=null;String failure="";
            try(SmbLibrary library=new SmbLibrary(store.config())){found=library.list(relative);}
            catch(Exception e){failure=SmbLibrary.friendly(e);}
            final List<LibraryItem> result=found;final String message=failure;
            runOnUiThread(()->{if(isDestroyed()||request!=generation)return;loading=false;error=message;items=result==null?new ArrayList<>():result;render();});
        });
    }
    private void refreshOnline(boolean manual){
        if(loading)return;
        int request=++generation;folder="";loading=true;error="";
        try{items=BiliClient.items(store.feed());}catch(Exception e){items=new ArrayList<>();}
        BiliSyncWorker.schedule(this);render();
        io.execute(()->{
            BiliSync.run(store,manual);List<LibraryItem> found=new ArrayList<>();String failure=store.syncError();
            try{found=BiliClient.items(store.feed());}catch(Exception e){failure=BiliClient.friendly(e);}
            final List<LibraryItem> result=found;final String message=failure;
            runOnUiThread(()->{if(isDestroyed()||request!=generation)return;loading=false;items=result;error=message;render();});
        });
    }
    private String onlineStatus(){
        try{org.json.JSONObject feed=store.feed();long time=feed.getLong("syncedAt");if(time==0)return "初始目录（"+feed.optString("seededAt","未标日期")+"） · 自动同步尚未成功";
            String scope=feed.optString("source").equals("public_collections")?"公开合集 "+feed.getInt("collectionCount")+" 个 · 最新 "+feed.getJSONArray("videos").length()+" 条":"最近 30 条投稿";
            return "上次成功同步："+new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(time))+" · "+scope;}
        catch(Exception e){return "目录暂不可用";}
    }
    private String syncFailureTitle(){
        try{if(store.feed().getLong("syncedAt")>0)return "本次更新未完成，保留上次目录";}catch(Exception ignored){}
        return "订阅已设置，等待同步成功";
    }
    private void render(){
        resolveMode();int width=getResources().getConfiguration().screenWidthDp;boolean wide=width>=780;
        shell=column();shell.setBackgroundColor(BG);shell.setPadding(dp(tv?28:22),dp(16),dp(tv?28:22),dp(12));
        shell.setOnApplyWindowInsetsListener((v,insets)->{shell.setPadding(dp(tv?28:22)+insets.getSystemWindowInsetLeft(),dp(16)+insets.getSystemWindowInsetTop(),dp(tv?28:22)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout header=row();
        TextView logo=text("▸",27,Color.WHITE,true);logo.setGravity(Gravity.CENTER);logo.setBackground(bg(GREEN,15));header.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout brand=column();brand.setPadding(dp(13),0,0,0);brand.addView(text("小小影院",25,INK,true));brand.addView(text("属于你的家庭电影时光",11,MUTED,false));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        if(width>600){connectionLabel=text(store.online()?"●  "+(loading?"正在同步":error.isEmpty()?"公开合集":"同步受限"):store.demo()?"●  本地演示":"●  "+(loading?"正在连接":error.isEmpty()?"家庭存储":"未连接"),13,GREEN,false);connectionLabel.setPadding(dp(14),dp(9),dp(14),dp(9));connectionLabel.setBackground(bg(0xffe7ede1,20));header.addView(connectionLabel);}
        Button mode=button(tv?"切换平板布局":"体验电视布局",false,()->{store.mode(tv?"平板":"电视");render();});
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-2,dp(46));hp.setMargins(dp(12),0,0,0);header.addView(mode,hp);
        Button parent=button("播放设置",false,this::settings);parent.setContentDescription("播放设置");LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,dp(46));pp.setMargins(dp(10),0,0,0);header.addView(parent,pp);
        shell.addView(header);space(shell,22);
        body=wide?row():column();body.setGravity(Gravity.TOP);shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=wide?column():row();
        if(wide){TextView tag=text("我的放映室",12,MUTED,true);tag.setPadding(dp(15),0,0,dp(12));nav.addView(tag);}
        String[] tabs={"全部影片","继续观看","我的喜欢"};String[] glyphs={"▦  ","▷  ","♡  "};
        for(int i=0;i<tabs.length;i++){
            String tab=tabs[i];Button b=button(glyphs[i]+tab,section.equals(tab),()->{section=tab;render();});b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            LinearLayout.LayoutParams np=wide?new LinearLayout.LayoutParams(-1,dp(54)):new LinearLayout.LayoutParams(0,dp(50),1);np.setMargins(0,0,wide?0:dp(8),dp(10));nav.addView(b,np);
        }
        nav.addView(button(store.online()?"✓ UP 主订阅":"UP 主订阅",false,()->{store.online(true);folder="";section="全部影片";loading=false;refresh();}),wide?new LinearLayout.LayoutParams(-1,dp(54)):new LinearLayout.LayoutParams(0,dp(50),1));
        if(wide){space(nav,30);TextView note=text(store.online()?"只看指定作者\n\n不提供搜索\n不显示推荐\n不加载弹幕和评论":store.demo()?"一个安静的小世界\n\n没有广告\n没有在线推荐\n只有家人选好的影片":"只看家长选好的影片\n\n不显示其他共享\n不提供删除操作",13,MUTED,false);note.setLineSpacing(dp(5),1);note.setPadding(dp(15),0,dp(15),0);nav.addView(note);space(nav,26);TextView ver=text("原型 0.3.1\n"+(tv?"遥控器布局":"触控布局"),11,MUTED,false);ver.setPadding(dp(15),0,0,0);nav.addView(ver);}
        body.addView(nav,wide?new LinearLayout.LayoutParams(dp(tv?165:170),-1):new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);if(wide)scroll.setPadding(dp(24),0,dp(3),dp(5));
        body.addView(scroll,wide?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));
        main=column();scroll.addView(main);
        if(section.equals("全部影片") && folder.isEmpty())hero(width);
        LinearLayout titleRow=row();String heading=folder.isEmpty()?section:folder.substring(folder.lastIndexOf('/')+1);
        titleRow.addView(text(heading,tv?25:23,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        if(!folder.isEmpty()){titleRow.addView(button("‹ 上一层",false,this::up));}
        titleRow.addView(button("刷新",false,this::refresh));main.addView(titleRow);space(main,6);
        main.addView(text(store.online()?onlineStatus():store.demo()?"4 种封面布局 · 共用 1 段 18 秒静音演示片":folder.isEmpty()?"仅显示家长设置的目录及其子文件夹":"当前目录内的影片",12,MUTED,false));space(main,16);
        if(store.online()){main.addView(text("同步范围：此作者公开合集中的视频；未加入合集的投稿可能不会出现。",13,GREEN,false));space(main,12);}
        if(loading){ProgressBar progress=new ProgressBar(this);main.addView(progress,new LinearLayout.LayoutParams(dp(40),dp(40)));space(main,10);main.addView(text(store.online()?"正在读取公开合集并逐条核验作者，请稍候…":"正在读取家庭存储…",17,MUTED,false));}
        if(!error.isEmpty()){empty(store.online()?syncFailureTitle():"暂时还没有连接上",error+(store.online()?"\n\n仍可尝试下方已核验的初始/缓存目录。不会加入其他作者；手动重试至少间隔一分钟。":""));main.addView(button(store.online()?"重试更新":"重新连接",true,this::refresh));}
        if(!loading && (error.isEmpty() || (store.online()&&!items.isEmpty()))){
            List<LibraryItem> visible=new ArrayList<>();
            for(LibraryItem item:items){if(section.equals("我的喜欢")&&!store.favorite(item.key()))continue;if(section.equals("继续观看")&&(item.folder||store.progress(item.key())<=0))continue;visible.add(item);}
            if(visible.isEmpty())empty(section.equals("我的喜欢")?"把喜欢的故事留在这里":section.equals("继续观看")?"新的故事，等你开始":store.online()?"暂时没有可显示的投稿":"这个文件夹还没有影片",section.equals("我的喜欢")?"在影片详情里点一下“喜欢”，下次就更容易找到。":section.equals("继续观看")?"播放后返回，观看进度会保存在这台设备上。":store.online()?"成功同步后，指定作者的公开投稿会显示在这里。":"请家长添加视频，或检查是否选对了共享文件夹。");
            else {
                int available=width-(wide?(tv?250:240):44);int cols=available>=1000?4:available>=680?3:available>=420?2:1;
                for(int index=0;index<visible.size();index+=cols){LinearLayout cards=row();cards.setGravity(Gravity.TOP);for(int c=0;c<cols;c++){LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(0,0,c<cols-1?dp(15):0,dp(18));if(index+c<visible.size())cards.addView(card(visible.get(index+c)),cp);else cards.addView(new View(this),cp);}main.addView(cards);}
            }
        }
        space(main,12);TextView footer=text(store.online()?"Wi-Fi / 移动网络在线播放 · 可能产生流量费用 · 封面为通用插画":store.demo()?"演示内容保存在本机 · 尚未连接你的华为家庭存储":"家庭局域网直连 · 播放进度仅保存在本机",11,MUTED,false);main.addView(footer);
        setContentView(shell);shell.requestApplyInsets();
    }
    private void hero(int width){
        LinearLayout hero=row();hero.setBackground(bg(0xffe9eddf,24));hero.setClipToOutline(true);
        LinearLayout words=column();words.setPadding(dp(25),dp(20),dp(18),dp(20));
        words.addView(text(store.online()?BiliPolicy.LABEL:store.demo()?"今天的小小放映场":"家人精选 · 放心观看",12,GREEN,true));space(words,10);
        words.addView(text("好故事，\n慢慢看。",width<600?28:36,INK,true));space(words,9);
        words.addView(text(store.online()?"自动同步公开合集；内容未经家长逐条审核。":store.demo()?"从一场小小的太空旅行开始。":"喜欢的影片，都在这里。",14,MUTED,false));space(words,16);
        LibraryItem first=null;for(LibraryItem item:items)if(!item.folder){first=item;break;}final LibraryItem pick=first;
        Button start=button(store.demo()?"▶  播放演示短片":store.online()&&pick==null?"等待订阅同步":"▶  开始观看",true,()->{if(pick!=null)play(pick);else toast("请先连接家庭存储并添加影片");});if(store.online()&&pick==null)start.setEnabled(false);words.addView(start,new LinearLayout.LayoutParams(-2,dp(48)));
        hero.addView(words,new LinearLayout.LayoutParams(0,-2,1.15f));
        if(width>=560)hero.addView(new ArtView(this,0),new LinearLayout.LayoutParams(0,dp(251),1));
        main.addView(hero,new LinearLayout.LayoutParams(-1,-2));space(main,25);
    }
    private View card(LibraryItem item){
        LinearLayout card=column();focusStyle(card,Color.WHITE,18);card.setClipToOutline(true);card.setPadding(dp(2),dp(2),dp(2),dp(8));
        ArtView art=new ArtView(this,item.art);card.addView(art,new LinearLayout.LayoutParams(-1,dp(tv?142:132)));
        LinearLayout caption=column();caption.setPadding(dp(13),dp(13),dp(13),dp(9));
        TextView title=text((item.folder?"▣  ":"")+item.name,tv?18:17,INK,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(title);space(caption,7);
        TextView sub=text(item.subtitle,11,MUTED,false);sub.setMaxLines(2);caption.addView(sub);
        long p=store.progress(item.key());if(p>0){space(caption,7);caption.addView(text("上次看到 "+(p/1000)+" 秒",11,GREEN,true));}
        if(store.favorite(item.key())){space(caption,5);caption.addView(text("♥  已喜欢",11,GREEN,true));}
        card.addView(caption);card.setContentDescription(item.name+(item.folder?"，文件夹":"，打开影片详情"));card.setOnClickListener(v->{if(item.folder){folder=item.path;refresh();}else detail(item);});return card;
    }
    private void empty(String title,String message){LinearLayout box=column();box.setPadding(dp(28),dp(32),dp(28),dp(32));box.setBackground(bg(0xffeeefe7,20));box.addView(text(title,23,INK,true));space(box,12);box.addView(text(message,15,MUTED,false));main.addView(box);space(main,16);}
    private void detail(LibraryItem item){
        LinearLayout panel=column();panel.setPadding(dp(24),dp(12),dp(24),dp(12));panel.addView(new ArtView(this,item.art),new LinearLayout.LayoutParams(-1,dp(190)));space(panel,16);
        panel.addView(text(item.demo?"这是封面与交互演示。四张卡片播放同一段 18 秒原创小火箭视频，无音轨。":item.subtitle,15,MUTED,false));
        if(!item.demo){space(panel,8);panel.addView(text((item.online?"视频编号：":"相对目录：")+item.path,12,MUTED,false));}
        if(item.online){space(panel,8);panel.addView(text("联网播放；播放前再次核对作者。多分 P 视频暂只播放第一 P，不加载弹幕、评论和推荐。",12,MUTED,false));}
        long p=store.progress(item.key());if(p>0){space(panel,10);panel.addView(text("将从 "+(p/1000)+" 秒处继续播放",14,GREEN,true));}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(item.name).setView(panel).setPositiveButton(p>0?"继续播放":"播放",(d,w)->play(item))
            .setNeutralButton(store.favorite(item.key())?"取消喜欢":"♡ 喜欢",(d,w)->{store.toggleFavorite(item.key());render();}).setNegativeButton("返回",null).create();dialog.show();
        if(tv)for(int which:new int[]{-1,-2,-3}){Button b=dialog.getButton(which);focusStyle(b,0xffeeefe7,10);if(b.hasFocus()){GradientDrawable highlight=bg(0xffeeefe7,10);highlight.setStroke(dp(3),0xffd28b38);b.setBackground(highlight);}}
    }
    private void play(LibraryItem item){if(item.folder)return;Intent intent=new Intent(this,PlayerActivity.class);intent.putExtra("online",item.online).putExtra("demo",item.demo).putExtra("path",item.path).putExtra("title",item.name);startActivity(intent);}
    private void up(){if(folder.isEmpty())return;int i=folder.lastIndexOf('/');folder=i<0?"":folder.substring(0,i);refresh();}
    @Override public void onBackPressed(){if(!folder.isEmpty()){up();return;}if(!section.equals("全部影片")){section="全部影片";render();return;}super.onBackPressed();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private EditText field(LinearLayout panel,String label,String value,String hint,boolean password){
        panel.addView(text(label,13,INK,true));EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(16);input.setText(value);input.setHint(hint);input.setContentDescription(label);
        input.setInputType(password?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        panel.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));space(panel,10);return input;
    }
    private void settings(){
        AppStore.Config previous;try{previous=store.config();}catch(Exception e){toast("原连接无法解密，请重新填写");previous=new AppStore.Config();}
        ScrollView scroll=new ScrollView(this);LinearLayout panel=column();panel.setPadding(dp(24),dp(12),dp(24),dp(16));scroll.addView(panel);
        Switch onlineSwitch=new Switch(this);onlineSwitch.setText("使用指定 UP 主订阅（402576555）");onlineSwitch.setChecked(store.online());onlineSwitch.setMinHeight(dp(48));panel.addView(onlineSwitch);
        panel.addView(text("在线来源固定，孩子不能添加作者或视频链接。自动检查约每 15 分钟一次，后台由系统安排；读取该作者公开合集，核验后保留最新 30 条。未加入合集的投稿可能遗漏，不代表全部投稿订阅。",12,MUTED,false));space(panel,12);
        panel.addView(text("连接只在家里使用，不需要云端账号。请勿使用能访问全部私人文件的管理员凭据。",14,MUTED,false));space(panel,15);
        Switch demoSwitch=new Switch(this);demoSwitch.setText("使用本地演示内容");demoSwitch.setChecked(store.demo());demoSwitch.setTextSize(16);demoSwitch.setMinHeight(dp(48));panel.addView(demoSwitch);space(panel,16);
        demoSwitch.setOnCheckedChangeListener((v,checked)->{if(checked)onlineSwitch.setChecked(false);});
        onlineSwitch.setOnCheckedChangeListener((v,checked)->{if(checked)demoSwitch.setChecked(false);});
        EditText host=field(panel,"家庭存储地址",previous.host,"例如 192.168.1.10",false);
        EditText port=field(panel,"SMB 端口",Integer.toString(previous.port),"445",false);port.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText share=field(panel,"共享名称",previous.share,"例如 家庭共享（以实际为准）",false);
        EditText root=field(panel,"允许访问的文件夹",previous.root,"例如 女儿视频；留空表示整个上述共享",false);
        EditText user=field(panel,"共享用户名",previous.user,"网络邻居里的用户名",false);
        EditText password=field(panel,"共享密码",previous.password,"密码保存在本机加密存储中",true);
        EditText domain=field(panel,"域（通常留空）",previous.domain,"",false);
        panel.addView(text("仅执行读取操作；是否真正限制了其他目录，要在家庭存储端配置并验证。本原型不等同于设备级儿童模式。",12,MUTED,false));space(panel,14);
        TextView status=text("尚未测试本次配置",13,MUTED,false);panel.addView(status);space(panel,10);
        java.util.function.Supplier<AppStore.Config> collect=()->{AppStore.Config c=new AppStore.Config();c.host=host.getText().toString().trim();c.port=Integer.parseInt(port.getText().toString());c.share=share.getText().toString();c.root=root.getText().toString();c.user=user.getText().toString();c.password=password.getText().toString();c.domain=domain.getText().toString();c.validate();return c;};
        Button test=button("测试此目录的读取",false,()->{});panel.addView(test);test.setOnClickListener(v->{
            AppStore.Config config;try{config=collect.get();}catch(Exception e){status.setText("请检查地址、端口、共享名与相对目录。");return;}
            test.setEnabled(false);status.setText("正在连接并读取目录…");
            io.execute(()->{String result;try(SmbLibrary library=new SmbLibrary(config)){List<LibraryItem> list=library.list("");result="读取成功：此目录有 "+list.size()+" 个视频或文件夹。尚未验证其他目录被拒绝访问。";}catch(Exception e){result=SmbLibrary.friendly(e);}String message=result;runOnUiThread(()->{if(isDestroyed())return;test.setEnabled(true);status.setText(message);});});
        });space(panel,16);panel.addView(text("界面模式",13,INK,true));Spinner modes=new Spinner(this);String[] options={"自动","平板","电视"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);modes.setAdapter(adapter);modes.setSelection(Arrays.asList(options).indexOf(store.mode()));panel.addView(modes,new LinearLayout.LayoutParams(-1,dp(52)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("播放设置").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->{dialog.getWindow().setLayout(Math.min(dp(600),getResources().getDisplayMetrics().widthPixels-dp(32)),(int)(getResources().getDisplayMetrics().heightPixels*.86));dialog.getButton(-1).setOnClickListener(v->{
            try{
                if(!onlineSwitch.isChecked() && (!demoSwitch.isChecked() || !host.getText().toString().trim().isEmpty()))store.save(collect.get());
                store.demo(demoSwitch.isChecked());store.online(onlineSwitch.isChecked());store.mode(options[modes.getSelectedItemPosition()]);folder="";section="全部影片";loading=false;dialog.dismiss();refresh();
            }catch(Exception e){status.setText(e instanceof IllegalArgumentException?"请检查端口、共享名与目录格式。":"设置保存失败，请检查输入后重试。");scroll.smoothScrollTo(0,status.getTop());}
        });});dialog.show();
    }
}
