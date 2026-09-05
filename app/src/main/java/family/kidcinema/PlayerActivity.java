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
    private boolean resumePlaying=true;
    private AlertDialog errorDialog;
    private int resolveGeneration;
    private BiliClient.Playback onlinePlayback;
    private TextView onlineStatus;
    private final java.util.concurrent.ExecutorService network=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener approvalChanged=(prefs,changed)->{
        if(online && active && (changed==null || changed.startsWith("creators")) && !store.allowedCreator(creatorUid)) {
            runOnUiThread(()->{if(!isFinishing()){Toast.makeText(this,"这位 UP 主已从观看名单中停用",Toast.LENGTH_LONG).show();finish();}});
        }
    };
    private final Runnable waiting = new Runnable() { public void run() {
        if(!active || onlineStatus==null || onlineStatus.getVisibility()!=View.VISIBLE)return;
        long seconds=(android.os.SystemClock.elapsedRealtime()-stageStarted)/1000;
        onlineStatus.setText((resolving?"正在核对作者与播放资源":"正在加载视频画面")+"…\n已等待 "+seconds+" 秒，可以随时返回");
        if(seconds>=30){release();onlineStatus.setVisibility(View.GONE);errorDialog=new AlertDialog.Builder(PlayerActivity.this).setTitle("连接等待时间较长")
            .setMessage("请确认网络已恢复后重试。观看进度已保留。")
            .setPositiveButton("重试",(d,w)->prepare()).setNegativeButton("返回",(d,w)->finish()).setOnCancelListener(d->finish()).show();return;}
        handler.postDelayed(this,1000);
    }};
    private void showLoading(String message){stageStarted=android.os.SystemClock.elapsedRealtime();onlineStatus.setText(message);onlineStatus.setVisibility(View.VISIBLE);handler.removeCallbacks(waiting);handler.postDelayed(waiting,1000);}
    private void screenAwake(){if(player!=null && player.getPlayWhenReady() && player.getPlaybackState()!=Player.STATE_ENDED)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private final Runnable saver = new Runnable() { public void run() { save(); handler.postDelayed(this,2000); } };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); store = new AppStore(this);
        if(b!=null)resumePlaying=b.getBoolean("resumePlaying",true);

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
        FrameLayout root = new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        playerView = (PlayerView)getLayoutInflater().inflate(R.layout.video_player,root,false); playerView.setShowNextButton(false); playerView.setShowPreviousButton(false);
        playerView.setControllerShowTimeoutMs(4000);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        onlineStatus=new TextView(this);onlineStatus.setText("正在核验作者并获取在线播放资源…");onlineStatus.setTextColor(Color.WHITE);onlineStatus.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);onlineStatus.setPadding(0,Math.round(100*getResources().getDisplayMetrics().density),0,0);onlineStatus.setTextSize(18);onlineStatus.setVisibility(View.VISIBLE);root.addView(onlineStatus,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top = new LinearLayout(this); top.setPadding(20,12,20,12);top.setGravity(Gravity.CENTER_VERTICAL);top.setBackgroundColor(0xcc4A2C3A);
        Button back = new Button(this);back.setText("‹ 返回");back.setContentDescription("返回视频列表");back.setOnClickListener(v -> finish());top.addView(back);
        TextView label = new TextView(this);label.setText(title + (demo ? "   ·   本地演示 / 无音轨" : ""));label.setTextColor(Color.WHITE);label.setTextSize(18);label.setPadding(20,0,0,0);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> top.setVisibility(onlineStatus.getVisibility()==View.VISIBLE?View.VISIBLE:visibility));
        setContentView(root);
    }
    @Override protected void onStart() { super.onStart();active=true;if(!isFinishing()&&playerView!=null)prepare(); }
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
                public void onRenderedFirstFrame(){if(player!=current)return;firstFrame=true;onlineStatus.setVisibility(View.GONE);handler.removeCallbacks(waiting);android.util.Log.d("KidPlayback","first video frame; paused="+!current.getPlayWhenReady());}
                public void onIsPlayingChanged(boolean playing){screenAwake();}
                public void onPlayWhenReadyChanged(boolean ready,int reason){screenAwake();}
                public void onPlayerError(PlaybackException e) {
                    if(!active||player!=current||isFinishing())return;
                    onlineStatus.setVisibility(View.GONE);handler.removeCallbacks(waiting);
                    // Log only stable error codes/classes: never signed URLs or NAS credentials.
                    android.util.Log.e("KidPlayback","playback error="+e.getErrorCodeName()+" cause="+(e.getCause()==null?"none":e.getCause().getClass().getSimpleName()));
                    if(errorDialog!=null)errorDialog.dismiss();
                    errorDialog=new AlertDialog.Builder(PlayerActivity.this).setTitle("这段视频暂时无法播放")
                        .setMessage((online?"在线播放遇到连接或解码问题。请检查网络后重试；会重新获取地址，不会跳转 B 站。":demo ? "演示视频加载失败，请返回后重试。" : "请检查家庭网络与共享权限；如果只有这一部无法播放，也可能是片源编码不受设备支持。")+"\n错误代码："+e.getErrorCodeName())
                        .setPositiveButton("重试",(d,w) -> { release(); prepare(); }).setNegativeButton("返回",(d,w) -> finish()).setOnCancelListener(d->finish()).show();
                }
                public void onPlaybackStateChanged(int state) {
                    if(player!=current)return;
                    if (state == Player.STATE_READY && !restored) {
                        restored = true;
                    }
                    if(state==Player.STATE_BUFFERING && firstFrame)showLoading("正在缓冲视频…");
                    if(state==Player.STATE_READY && firstFrame){onlineStatus.setVisibility(View.GONE);handler.removeCallbacks(waiting);}
                    if (state == Player.STATE_ENDED){store.progress(playbackScope,key,0);onlineStatus.setVisibility(View.GONE);handler.removeCallbacks(waiting);}
                    screenAwake();
                }
            });
            long startPosition=Math.max(0,store.progress(playbackScope,key));
            if(online){
                BiliClient.Playback streams=onlinePlayback;
                ProgressiveMediaSource.Factory factory=new ProgressiveMediaSource.Factory(()->new BiliDataSource(streams.videoUrls));
                MediaSource video=factory.createMediaSource(MediaItem.fromUri(uri));
                player.setMediaSource(streams.audio==null?video:new MergingMediaSource(video,new ProgressiveMediaSource.Factory(()->new BiliDataSource(streams.audioUrls)).createMediaSource(MediaItem.fromUri(streams.audio))),startPosition);
            }else player.setMediaItem(MediaItem.fromUri(uri),startPosition);
            player.prepare();player.setPlayWhenReady(resumePlaying);playerView.requestFocus();handler.post(saver);
        } catch (Exception e) {
            release();onlineStatus.setVisibility(View.GONE);
            errorDialog=new AlertDialog.Builder(this).setTitle("暂时无法开始播放").setMessage(online?"视频已不在允许目录中，或播放资源不可用。请返回列表后重试。":"无法读取有效的播放配置，请返回播放设置重新连接。")
                .setPositiveButton("返回",(d,w)->finish()).setOnCancelListener(d->finish()).show();
        }
    }
    private void resolveOnline(){
        if(resolving)return;resolving=true;int request=++resolveGeneration;showLoading("正在核对作者与播放资源…");
        final BiliClient client=new BiliClient(creatorUid);resolvingClient=client;
        resolveTask=network.submit(()->{
            BiliClient.Playback result=null;String failure="";
            try{if(!store.online()||!store.allowedCreator(creatorUid)||!BiliClient.contains(store.feed(creatorUid),path,creatorUid))throw new IllegalArgumentException("视频不在当前允许目录中");result=client.resolve(path);}
            catch(Exception e){failure=BiliClient.friendly(e);}
            final BiliClient.Playback playback=result;final String message=failure;
            runOnUiThread(()->{
                if(!active||isDestroyed()||request!=resolveGeneration)return;resolving=false;
                if(playback==null){onlineStatus.setVisibility(View.GONE);handler.removeCallbacks(waiting);errorDialog=new AlertDialog.Builder(this).setTitle("暂时无法在线播放").setMessage(message)
                    .setPositiveButton("重试",(d,w)->prepare()).setNegativeButton("返回",(d,w)->finish()).setOnCancelListener(d->finish()).show();return;}
                onlinePlayback=playback;prepare();
            });
        });
    }
    private void save() {
        if (player != null && restored) store.progress(playbackScope,key, player.getPlaybackState() == Player.STATE_ENDED ? 0 : player.getCurrentPosition());
    }
    private void release() { if(resolvingClient!=null){resolvingClient.cancel();resolvingClient=null;}if(resolveTask!=null){resolveTask.cancel(true);resolveTask=null;}handler.removeCallbacks(waiting);resolveGeneration++;resolving=false;onlinePlayback=null;handler.removeCallbacks(saver);save();if(player != null){resumePlaying=player.getPlayWhenReady();playerView.setPlayer(null);player.release();player=null;}restored=false;screenAwake();if(errorDialog!=null){errorDialog.dismiss();errorDialog=null;} }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("resumePlaying",player==null?resumePlaying:player.getPlayWhenReady());super.onSaveInstanceState(state);}
    @Override protected void onStop() { active=false;release();super.onStop(); }
    @Override protected void onDestroy(){store.prefs.unregisterOnSharedPreferenceChangeListener(approvalChanged);network.shutdownNow();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (player != null && event.getAction()==KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible()) {
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT){player.seekBack();playerView.showController();return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT){player.seekForward();playerView.showController();return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER){if(player.isPlaying())player.pause();else player.play();playerView.showController();return true;}
        }
        return (playerView!=null && playerView.dispatchKeyEvent(event)) || super.dispatchKeyEvent(event);
    }
}
