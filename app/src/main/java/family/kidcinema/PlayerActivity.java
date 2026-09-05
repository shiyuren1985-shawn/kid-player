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
    private String key, path, title;
    private boolean demo, restored, online, resolving, active;
    private boolean resumePlaying=true;
    private AlertDialog errorDialog;
    private int resolveGeneration;
    private BiliClient.Playback onlinePlayback;
    private TextView onlineStatus;
    private final java.util.concurrent.ExecutorService network=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saver = new Runnable() { public void run() { save(); handler.postDelayed(this,2000); } };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); store = new AppStore(this);
        if(b!=null)resumePlaying=b.getBoolean("resumePlaying",true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        demo = getIntent().getBooleanExtra("demo",true);
        online = getIntent().getBooleanExtra("online",false);
        path = getIntent().getStringExtra("path"); title = getIntent().getStringExtra("title");
        key = (online ? "bili:" : demo ? "demo:" : "smb:") + path;
        if (online != store.online() || (!online && demo != store.demo())) { finish(); return; }
        if(online){try{if(!BiliClient.contains(store.feed(),path))throw new IllegalArgumentException();}catch(Exception e){finish();return;}}
        FrameLayout root = new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        playerView = (PlayerView)getLayoutInflater().inflate(R.layout.video_player,root,false); playerView.setShowNextButton(false); playerView.setShowPreviousButton(false);
        playerView.setControllerShowTimeoutMs(4000);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        onlineStatus=new TextView(this);onlineStatus.setText("正在核验作者并获取在线播放资源…");onlineStatus.setTextColor(Color.WHITE);onlineStatus.setGravity(Gravity.CENTER);onlineStatus.setTextSize(18);onlineStatus.setVisibility(online?View.VISIBLE:View.GONE);root.addView(onlineStatus,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top = new LinearLayout(this); top.setPadding(20,12,20,12);top.setGravity(Gravity.CENTER_VERTICAL);top.setBackgroundColor(0x99203036);
        Button back = new Button(this);back.setText("‹ 返回");back.setContentDescription("返回视频列表");back.setOnClickListener(v -> finish());top.addView(back);
        TextView label = new TextView(this);label.setText(title + (demo ? "   ·   本地演示 / 无音轨" : ""));label.setTextColor(Color.WHITE);label.setTextSize(18);label.setPadding(20,0,0,0);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> top.setVisibility(visibility));
        setContentView(root);
    }
    @Override protected void onStart() { super.onStart();active=true;if(!isFinishing()&&playerView!=null)prepare(); }
    private void prepare() {
        if (player != null || !active) return;
        if(online && onlinePlayback==null){resolveOnline();return;}
        try {
            androidx.media3.exoplayer.DefaultRenderersFactory renderers=new androidx.media3.exoplayer.DefaultRenderersFactory(this).setEnableDecoderFallback(true);
            ExoPlayer.Builder builder = new ExoPlayer.Builder(this,renderers).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000);
            Uri uri;
            if (online) {
                if(!store.online()||!BiliClient.contains(store.feed(),path))throw new IllegalArgumentException("已不在当前目录中");
                uri=Uri.parse(onlinePlayback.video);
            }
            else if (demo) uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.demo_space);
            else {
                String checked = PathPolicy.clean(path); AppStore.Config config = store.config(); config.validate();
                builder.setMediaSourceFactory(new DefaultMediaSourceFactory(() -> new SmbDataSource(config,checked)));
                // Credentials never appear in a URI or log message.
                uri = new Uri.Builder().scheme("smb").authority("library").path(checked).build();
            }
            player = builder.build();playerView.setPlayer(player);
            final ExoPlayer current=player;
            player.addListener(new Player.Listener() {
                public void onRenderedFirstFrame(){android.util.Log.d("KidPlayback","first video frame; paused="+!current.getPlayWhenReady());}
                public void onPlayerError(PlaybackException e) {
                    if(!active||player!=current||isFinishing())return;
                    onlineStatus.setVisibility(View.GONE);
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
                        onlineStatus.setVisibility(View.GONE);
                        restored = true;
                    }
                    if (state == Player.STATE_ENDED) store.progress(key,0);
                }
            });
            long startPosition=Math.max(0,store.progress(key));
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
        if(resolving)return;resolving=true;int request=++resolveGeneration;onlineStatus.setVisibility(View.VISIBLE);
        network.execute(()->{
            BiliClient.Playback result=null;String failure="";
            try{if(!store.online()||!BiliClient.contains(store.feed(),path))throw new IllegalArgumentException("视频不在当前允许目录中");result=new BiliClient().resolve(path);}
            catch(Exception e){failure=BiliClient.friendly(e);}
            final BiliClient.Playback playback=result;final String message=failure;
            runOnUiThread(()->{
                if(!active||isDestroyed()||request!=resolveGeneration)return;resolving=false;
                if(playback==null){onlineStatus.setVisibility(View.GONE);errorDialog=new AlertDialog.Builder(this).setTitle("暂时无法在线播放").setMessage(message)
                    .setPositiveButton("重试",(d,w)->prepare()).setNegativeButton("返回",(d,w)->finish()).setOnCancelListener(d->finish()).show();return;}
                onlinePlayback=playback;prepare();
            });
        });
    }
    private void save() {
        if (player != null && restored) store.progress(key, player.getPlaybackState() == Player.STATE_ENDED ? 0 : player.getCurrentPosition());
    }
    private void release() { resolveGeneration++;resolving=false;onlinePlayback=null;handler.removeCallbacks(saver);save();if(player != null){resumePlaying=player.getPlayWhenReady();playerView.setPlayer(null);player.release();player=null;}restored=false;if(errorDialog!=null){errorDialog.dismiss();errorDialog=null;} }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("resumePlaying",player==null?resumePlaying:player.getPlayWhenReady());super.onSaveInstanceState(state);}
    @Override protected void onStop() { active=false;release();super.onStop(); }
    @Override protected void onDestroy(){network.shutdownNow();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (player != null && event.getAction()==KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible()) {
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT){player.seekBack();playerView.showController();return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT){player.seekForward();playerView.showController();return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER){if(player.isPlaying())player.pause();else player.play();playerView.showController();return true;}
        }
        return (playerView!=null && playerView.dispatchKeyEvent(event)) || super.dispatchKeyEvent(event);
    }
}
