package family.kidcinema;

import android.app.*;
import android.os.*;
import android.net.Uri;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.ui.PlayerView;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlayerActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base) {
        android.content.res.Configuration config=new android.content.res.Configuration(base.getResources().getConfiguration());
        config.setLocale(java.util.Locale.SIMPLIFIED_CHINESE);super.attachBaseContext(base.createConfigurationContext(config));
    }
    private ExoPlayer player;
    private PlayerView playerView;
    private AppStore store;
    private String key, path, title, playbackScope;
    private long creatorUid;
    private boolean firstFrame;
    private long stageStarted;
    private BiliClient resolvingClient;
    private java.util.concurrent.Future<?> resolveTask;
    private boolean demo, restored, online, resolving, active;
    private boolean resumePlaying=true,ended;
    private FrameLayout videoArea;
    private ScrollView endScreen;
    private LinearLayout actionArea;
    private TextView titleLabel;
    private Button pauseButton,favoriteButton;
    private ScrollView failureScreen;
    private int resolveGeneration;
    private BiliClient.Playback onlinePlayback;
    private TextView onlineStatus;
    private final java.util.concurrent.ExecutorService network=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener approvalChanged=(prefs,changed)->{
        if(online && active && (changed==null || changed.startsWith("creators") || changed.equals(store.biliKey(creatorUid,"feed")))) {
            runOnUiThread(()->{if(!isFinishing()){if(!store.allowedCreator(creatorUid)){Toast.makeText(this,"这位 UP 主已从观看名单中停用",Toast.LENGTH_LONG).show();finish();}else if(ended)showEndScreen();}});
        }
    };
    private final Runnable waiting = new Runnable() { public void run() {
        if(!active || onlineStatus==null || onlineStatus.getVisibility()!=View.VISIBLE)return;
        long seconds=(android.os.SystemClock.elapsedRealtime()-stageStarted)/1000;
        onlineStatus.setText((resolving?"正在核对作者与播放资源":"正在加载视频画面")+"…\n已等待 "+seconds+" 秒，可以随时返回");
        if(seconds>=30){showFailure("连接等待时间较长","请确认网络已恢复后重试。观看进度已保留。",true);return;}
        handler.postDelayed(this,1000);
    }};
    private void showLoading(String message){stageStarted=android.os.SystemClock.elapsedRealtime();onlineStatus.setText(message);onlineStatus.setVisibility(View.VISIBLE);handler.removeCallbacks(waiting);handler.postDelayed(waiting,1000);}
    private void screenAwake(){if(player!=null && player.getPlayWhenReady() && player.getPlaybackState()!=Player.STATE_ENDED)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private final Runnable saver = new Runnable() { public void run() { save(); handler.postDelayed(this,2000); } };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); store = new AppStore(this);
        if(b!=null){resumePlaying=b.getBoolean("resumePlaying",true);ended=b.getBoolean("ended",false);}

        getWindow().getDecorView().setSystemUiVisibility(5894);
        demo = getIntent().getBooleanExtra("demo",true);
        online = getIntent().getBooleanExtra("online",false);
        path = getIntent().getStringExtra("path"); title = getIntent().getStringExtra("title");
        creatorUid=getIntent().getLongExtra("creatorUid",store.selectedCreator());
        playbackScope=online?"bili:"+creatorUid:store.scope();
        key = (online ? "bili:" : demo ? "demo:" : "smb:") + path;
        if (online != store.online() || (!online && demo != store.demo())) { finish(); return; }
        if(online){try{if(!store.allowedCreator(creatorUid)||!BiliClient.contains(store.feed(creatorUid),path,creatorUid))throw new IllegalArgumentException();}catch(Exception e){finish();return;}}
        if(b==null && getIntent().getBooleanExtra("fromStart",false))store.progress(playbackScope,key,0);
        store.prefs.registerOnSharedPreferenceChangeListener(approvalChanged);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.BLACK);
        videoArea=new FrameLayout(this);root.addView(videoArea,new LinearLayout.LayoutParams(-1,0,1));
        playerView=(PlayerView)getLayoutInflater().inflate(R.layout.video_player,videoArea,false);playerView.setShowNextButton(false);playerView.setShowPreviousButton(false);
        playerView.setControllerShowTimeoutMs(4000);playerView.setControllerAutoShow(false);playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        videoArea.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        onlineStatus=new TextView(this);onlineStatus.setTextColor(Color.WHITE);onlineStatus.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);onlineStatus.setPadding(dp(20),dp(38),dp(20),0);onlineStatus.setTextSize(18);onlineStatus.setVisibility(View.GONE);videoArea.addView(onlineStatus,new FrameLayout.LayoutParams(-1,-1));
        actionArea=new LinearLayout(this);actionArea.setOrientation(LinearLayout.VERTICAL);actionArea.setPadding(dp(16),dp(8),dp(16),dp(12));actionArea.setBackgroundColor(MainActivity.BG);actionArea.setContentDescription("常驻播放按钮区");
        titleLabel=new TextView(this);titleLabel.setTextColor(MainActivity.INK);titleLabel.setTextSize(17);titleLabel.setMaxLines(1);titleLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);titleLabel.setPadding(dp(6),0,dp(6),dp(8));actionArea.addView(titleLabel);
        GridLayout actions=new GridLayout(this);int columns=getResources().getConfiguration().screenWidthDp>=600?4:2;actions.setColumnCount(columns);
        Button back=iconAction("返回列表","返回视频列表",R.drawable.player_back,0xFF245C99,0xFFE2EDFF,()->finish());
        pauseButton=iconAction("暂停","暂停视频",R.drawable.player_pause,0xFF176747,0xFFDFF3E6,()->{if(ended){replay();return;}if(player!=null){if(player.getPlayWhenReady())player.pause();else player.play();}});
        favoriteButton=iconAction("收藏","收藏视频",R.drawable.player_heart,0xFFAD2859,0xFFFFE2EB,()->{if(online&&!store.allowedCreator(creatorUid))return;store.toggleFavorite(playbackScope,key);updateActions();favoriteButton.announceForAccessibility(store.favorite(playbackScope,key)?"已收藏":"已取消收藏");});
        Button close=iconAction("关闭影院","关闭影院",R.drawable.player_exit,0xFF994715,0xFFFFEBD5,()->{release();finishAffinity();});
        for(Button button:new Button[]{back,pauseButton,favoriteButton,close}){GridLayout.LayoutParams params=new GridLayout.LayoutParams();params.width=0;params.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);params.setMargins(dp(4),dp(4),dp(4),dp(4));actions.addView(button,params);}
        actionArea.addView(actions);root.addView(actionArea,new LinearLayout.LayoutParams(-1,-2));updateActions();
        setContentView(root);
        // Android may consume the first direction key when leaving touch mode.
        // Route that transition to the same child-friendly area as normal DPAD input.
        root.getViewTreeObserver().addOnTouchModeChangeListener(touch->{if(!touch)handler.post(()->{
            if(!active||isFinishing()||isDestroyed()||ended||failureScreen!=null||player==null)return;
            View focus=getCurrentFocus();if(focus==null||!isInside(focus,actionArea)){playerView.hideController();pauseButton.requestFocus();}
        });});
    }
    @Override protected void onStart() { super.onStart();active=true;if(!isFinishing()&&playerView!=null){if(online&&!store.allowedCreator(creatorUid)){finish();return;}if(ended)showEndScreen();else prepare();} }
    private void prepare() {
        if (player != null || !active) return;
        if(online && onlinePlayback==null){resolveOnline();return;}
        firstFrame=false;showLoading("正在加载视频画面…");
        try {
            androidx.media3.exoplayer.DefaultRenderersFactory renderers=new androidx.media3.exoplayer.DefaultRenderersFactory(this).setEnableDecoderFallback(true);
            ExoPlayer.Builder builder = new ExoPlayer.Builder(this,renderers).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000)
                .setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
                .setHandleAudioBecomingNoisy(true);
            Uri uri;
            if (online) {
                if(!store.online()||!store.allowedCreator(creatorUid)||!BiliClient.contains(store.feed(creatorUid),path,creatorUid))throw new IllegalArgumentException("已不在当前目录中");
                uri=Uri.parse(onlinePlayback.video);
            }
            else if (demo) uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.demo_space);
            else {
                if(!playbackScope.equals(store.scope()))throw new IllegalArgumentException("家庭存储设置已经改变，请返回列表重新选择");
                String checked = PathPolicy.clean(path); AppStore.Config config = store.config(); config.validate();
                builder.setMediaSourceFactory(new DefaultMediaSourceFactory(() -> new SmbDataSource(config,checked)));
                // Credentials never appear in a URI or log message.
                uri = new Uri.Builder().scheme("smb").authority("library").path(checked).build();
            }
            player = builder.build();playerView.setPlayer(player);
            final ExoPlayer current=player;
            player.addListener(new Player.Listener() {
                public void onRenderedFirstFrame(){if(player!=current)return;firstFrame=true;store.recordWatched(playbackScope,key);onlineStatus.setVisibility(View.GONE);handler.removeCallbacks(waiting);android.util.Log.d("KidPlayback","first video frame; paused="+!current.getPlayWhenReady());}
                public void onIsPlayingChanged(boolean playing){screenAwake();}
                public void onPlayWhenReadyChanged(boolean ready,int reason){screenAwake();updateActions();}
                public void onPlayerError(PlaybackException e) {
                    if(!active||player!=current||isFinishing())return;
                    onlineStatus.setVisibility(View.GONE);handler.removeCallbacks(waiting);
                    // Log only stable error codes/classes: never signed URLs or NAS credentials.
                    android.util.Log.e("KidPlayback","playback error="+e.getErrorCodeName()+" cause="+(e.getCause()==null?"none":e.getCause().getClass().getSimpleName()));
                    showFailure("这段视频暂时无法播放",(online?"在线播放遇到连接或解码问题，请检查网络后重试。":demo?"演示视频加载失败，请重试。":"请检查家庭网络与共享权限；这部影片的编码也可能不受设备支持。")+"\n错误代码："+e.getErrorCodeName(),true);
                }
                public void onPlaybackStateChanged(int state) {
                    if(player!=current)return;
                    if (state == Player.STATE_READY && !restored) {
                        restored = true;
                    }
                    if(state==Player.STATE_BUFFERING && firstFrame)showLoading("正在缓冲视频…");
                    if(state==Player.STATE_READY && firstFrame){onlineStatus.setVisibility(View.GONE);handler.removeCallbacks(waiting);}
                    if (state == Player.STATE_ENDED){store.progress(playbackScope,key,0);showEndScreen();}
                    screenAwake();
                }
            });
            long startPosition=Math.max(0,store.progress(playbackScope,key));
            if(online){
                BiliClient.Playback streams=onlinePlayback;
                ProgressiveMediaSource.Factory factory=new ProgressiveMediaSource.Factory(PlaybackCache.factory(()->new BiliDataSource(streams.videoUrls)));
                MediaSource video=factory.createMediaSource(MediaItem.fromUri(uri));
                player.setMediaSource(streams.audio==null?video:new MergingMediaSource(video,new ProgressiveMediaSource.Factory(PlaybackCache.factory(()->new BiliDataSource(streams.audioUrls))).createMediaSource(MediaItem.fromUri(streams.audio))),startPosition);
            }else player.setMediaItem(MediaItem.fromUri(uri),startPosition);
            player.prepare();player.setPlayWhenReady(resumePlaying);updateActions();playerView.requestFocus();handler.post(saver);
        } catch (Exception e) {
            showFailure("暂时无法开始播放",online?"视频已不在允许目录中，或播放资源不可用，请返回列表重新选择。":"无法读取有效的播放配置，请返回播放设置重新连接。",false);
        }
    }
    private void resolveOnline(){
        if(resolving)return;resolving=true;int request=++resolveGeneration;showLoading("正在核对作者与播放资源…");
        final BiliClient client=new BiliClient(creatorUid);resolvingClient=client;
        resolveTask=network.submit(()->{
            BiliClient.Playback result=null;String failure="";
            try{if(!store.online()||!store.allowedCreator(creatorUid)||!BiliClient.contains(store.feed(creatorUid),path,creatorUid))throw new IllegalArgumentException("视频不在当前允许目录中");result=client.resolve(path);PlaybackCache.initialize(getApplicationContext());}
            catch(Exception e){failure=BiliClient.friendly(e);}
            final BiliClient.Playback playback=result;final String message=failure;
            runOnUiThread(()->{
                if(!active||isDestroyed()||request!=resolveGeneration)return;resolving=false;
                if(playback==null){showFailure("暂时无法在线播放",message,true);return;}
                onlinePlayback=playback;prepare();
            });
        });
    }
    private void showFailure(String heading,String message,boolean retry){
        release();onlineStatus.setVisibility(View.GONE);playerView.hideController();updateActions();
        failureScreen=new ScrollView(this);failureScreen.setFillViewport(true);failureScreen.setBackgroundColor(MainActivity.BG);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(28),dp(24),dp(28),dp(24));failureScreen.addView(content);
        TextView title=new TextView(this);title.setText(heading);title.setTextSize(25);title.setTextColor(MainActivity.INK);content.addView(title);
        TextView explanation=new TextView(this);explanation.setText(message);explanation.setTextSize(18);explanation.setTextColor(MainActivity.MUTED);explanation.setPadding(0,dp(16),0,dp(18));content.addView(explanation);
        if(retry){Button again=action("重试","重试播放",()->{release();prepare();});content.addView(again,new LinearLayout.LayoutParams(dp(220),-2));again.requestFocus();}
        videoArea.addView(failureScreen,new FrameLayout.LayoutParams(-1,-1));
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static boolean isInside(View view,View parent){for(android.view.ViewParent p=view.getParent();p!=null;p=p.getParent())if(p==parent)return true;return view==parent;}
    private android.graphics.drawable.GradientDrawable buttonBackground(boolean focused){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(MainActivity.PANEL);d.setCornerRadius(dp(16));if(focused)d.setStroke(dp(4),MainActivity.FOCUS);return d;}
    private Button action(String label,String description,Runnable action){Button b=new Button(this);b.setText(label);b.setContentDescription(description);b.setTextSize(22);b.setTextColor(MainActivity.INK);b.setAllCaps(false);b.setMinHeight(dp(72));b.setMinimumHeight(dp(72));b.setMaxLines(2);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setBackground(buttonBackground(false));b.setOnFocusChangeListener((v,focus)->v.setBackground(buttonBackground(focus)));b.setOnClickListener(v->action.run());return b;}
    private void icon(Button button,int resource,int color){
        android.graphics.drawable.Drawable drawable=getDrawable(resource).mutate();drawable.setTint(color);drawable.setBounds(0,0,dp(40),dp(40));
        button.setCompoundDrawablesRelative(null,drawable,null,null);button.setCompoundDrawablePadding(dp(6));
    }
    private Button iconAction(String label,String description,int resource,int tint,int fill,Runnable command){
        Button button=action(label,description,command);button.setTextSize(20);button.setGravity(Gravity.CENTER);button.setMinHeight(dp(112));button.setMinimumHeight(dp(112));
        button.setTypeface(null,android.graphics.Typeface.BOLD);icon(button,resource,tint);
        java.util.function.Consumer<Boolean> background=focused->{android.graphics.drawable.GradientDrawable shape=buttonBackground(focused);shape.setColor(fill);button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),shape,null));};
        background.accept(false);button.setOnFocusChangeListener((v,focused)->background.accept(focused));return button;
    }
    private void updateActions(){
        if(pauseButton==null)return;
        titleLabel.setText(title+(demo?" · 本地演示 / 无音轨":""));boolean playing=player==null?resumePlaying:player.getPlayWhenReady();
        pauseButton.setText(ended?"重放":playing?"暂停":"播放");icon(pauseButton,ended?R.drawable.player_replay:playing?R.drawable.player_pause:R.drawable.player_play,0xFF176747);pauseButton.setContentDescription(ended?"重放视频":playing?"暂停视频":"播放视频");pauseButton.setEnabled(ended||player!=null);pauseButton.setAlpha(pauseButton.isEnabled()?1f:.45f);
        boolean saved=store.favorite(playbackScope,key);favoriteButton.setText(saved?"已收藏":"收藏");icon(favoriteButton,saved?R.drawable.player_heart_filled:R.drawable.player_heart,0xFFAD2859);favoriteButton.setContentDescription(saved?"取消收藏视频":"收藏视频");favoriteButton.setSelected(saved);
    }
    private void showEndScreen(){
        ended=true;onlineStatus.setVisibility(View.GONE);handler.removeCallbacks(waiting);playerView.hideController();playerView.setUseController(false);updateActions();
        if(endScreen!=null)videoArea.removeView(endScreen);
        endScreen=new ScrollView(this);endScreen.setFillViewport(true);endScreen.setBackgroundColor(MainActivity.BG);endScreen.setContentDescription("播放结束页");
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(20),dp(24),dp(20));endScreen.addView(content);
        TextView heading=new TextView(this);heading.setText("这一集看完啦");heading.setTextSize(26);heading.setTextColor(MainActivity.INK);content.addView(heading);
        Button replay=action("↻ 再看一遍","重放这部视频",this::replay);LinearLayout.LayoutParams replayParams=new LinearLayout.LayoutParams(dp(220),-2);replayParams.setMargins(0,dp(12),0,dp(18));content.addView(replay,replayParams);
        java.util.List<LibraryItem> next=online?EndSuggestions.forCreator(store,creatorUid,path):java.util.Collections.emptyList();
        AppStore.Creator owner=online?store.creator(creatorUid):null;
        TextView caption=new TextView(this);caption.setText(online?(next.isEmpty()?"这位 UP 主暂时没有其他可看的影片":"再看一部 · "+(owner==null?"同一位 UP 主":owner.name)):"也可以返回列表，挑选另一个故事");caption.setTextSize(18);caption.setTextColor(MainActivity.MUTED);content.addView(caption);
        GridLayout grid=new GridLayout(this);grid.setColumnCount(getResources().getConfiguration().screenWidthDp>=1000?4:2);content.addView(grid);
        for(LibraryItem item:next){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(8),dp(8),dp(12));card.setBackground(buttonBackground(false));card.setFocusable(true);card.setContentDescription("接着看："+item.name);card.setOnFocusChangeListener((v,f)->v.setBackground(buttonBackground(f)));card.setOnClickListener(v->playSuggestion(item));
            ImageView image=new ImageView(this);image.setImageResource(R.drawable.heartsping_foreground);image.setScaleType(item.image.isEmpty()?ImageView.ScaleType.CENTER_INSIDE:ImageView.ScaleType.CENTER_CROP);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);RemoteImages.load(image,item.image);card.addView(image,new LinearLayout.LayoutParams(-1,dp(110)));
            TextView name=new TextView(this);name.setText(item.name);name.setTextColor(MainActivity.INK);name.setTextSize(18);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name);
            GridLayout.LayoutParams params=new GridLayout.LayoutParams();params.width=0;params.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);params.setMargins(dp(5),dp(12),dp(5),dp(5));grid.addView(card,params);
        }
        videoArea.addView(endScreen,new FrameLayout.LayoutParams(-1,-1));replay.requestFocus();screenAwake();
    }
    private void hideEndScreen(){if(endScreen!=null){videoArea.removeView(endScreen);endScreen=null;}ended=false;playerView.setUseController(true);}
    private void replay(){
        if(online){try{if(!store.allowedCreator(creatorUid)||!BiliClient.contains(store.feed(creatorUid),path,creatorUid)){finish();return;}}catch(Exception e){finish();return;}}
        release();hideEndScreen();store.progress(playbackScope,key,0);resumePlaying=true;updateActions();prepare();
    }
    private void playSuggestion(LibraryItem item){
        try{if(!online||item.creatorUid!=creatorUid||!store.allowedCreator(creatorUid)||!BiliClient.contains(store.feed(creatorUid),item.path,creatorUid))throw new IllegalArgumentException();}
        catch(Exception e){Toast.makeText(this,"这部影片已不在当前目录中",Toast.LENGTH_SHORT).show();showEndScreen();return;}
        release();hideEndScreen();path=item.path;title=item.name;key=item.key();resumePlaying=true;
        setIntent(new android.content.Intent(getIntent()).putExtra("path",path).putExtra("title",title).putExtra("creatorUid",creatorUid).putExtra("fromStart",false));updateActions();prepare();
    }
    private void save() {
        if (player != null && restored) store.progress(playbackScope,key, player.getPlaybackState() == Player.STATE_ENDED ? 0 : player.getCurrentPosition());
    }
    private void release() { if(resolvingClient!=null){resolvingClient.cancel();resolvingClient=null;}if(resolveTask!=null){resolveTask.cancel(true);resolveTask=null;}handler.removeCallbacks(waiting);resolveGeneration++;resolving=false;onlinePlayback=null;handler.removeCallbacks(saver);save();if(player != null){resumePlaying=player.getPlayWhenReady();playerView.setPlayer(null);player.release();player=null;}restored=false;screenAwake();if(failureScreen!=null){videoArea.removeView(failureScreen);failureScreen=null;} }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("ended",ended);state.putBoolean("resumePlaying",player==null?resumePlaying:player.getPlayWhenReady());super.onSaveInstanceState(state);}
    @Override protected void onStop() { active=false;release();super.onStop(); }
    @Override protected void onDestroy(){store.prefs.unregisterOnSharedPreferenceChangeListener(approvalChanged);network.shutdownNow();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN&&player!=null&&!ended){
            int keyCode=event.getKeyCode();
            if(keyCode==KeyEvent.KEYCODE_MEDIA_PLAY){player.play();return true;}
            if(keyCode==KeyEvent.KEYCODE_MEDIA_PAUSE){player.pause();return true;}
            if(keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(player.getPlayWhenReady())player.pause();else player.play();return true;}
        }
        View focus=getCurrentFocus();boolean onActions=focus!=null&&isInside(focus,actionArea);
        if(ended||failureScreen!=null||onActions)return super.dispatchKeyEvent(event);
        if(player!=null&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN){playerView.hideController();pauseButton.requestFocusFromTouch();return true;}
        if (player != null && event.getAction()==KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible()) {
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT){player.seekBack();playerView.showController();return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT){player.seekForward();playerView.showController();return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER){if(player.isPlaying())player.pause();else player.play();playerView.showController();return true;}
        }
        return (playerView!=null && playerView.dispatchKeyEvent(event)) || super.dispatchKeyEvent(event);
    }
}
