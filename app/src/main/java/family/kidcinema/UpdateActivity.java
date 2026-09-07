package family.kidcinema;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private TextView status, details;
    private ProgressBar progress;
    private Button check, download, install, cancel, source;
    private UpdateManifest manifest;
    private File apk;
    private boolean busy;
    private int shownPercent=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.rgb(74,44,58));t.setPadding(0,dp(10),0,dp(10));return t;}
    private Button button(LinearLayout box,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(17);b.setMinHeight(dp(56));b.setAllCaps(false);box.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->action.run());return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(18),dp(24),dp(24));box.setBackgroundColor(Color.rgb(255,248,245));scroll.addView(box);setContentView(scroll);
        box.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(24)+insets.getSystemWindowInsetLeft(),dp(18)+insets.getSystemWindowInsetTop(),dp(24)+insets.getSystemWindowInsetRight(),dp(24)+insets.getSystemWindowInsetBottom());return insets;});
        box.addView(label("kid player 更新",25));box.addView(label("当前版本 "+AppUpdater.versionName(this),16));
        status=label("正在检查更新…",17);box.addView(status);details=label("",16);box.addView(details);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(View.GONE);box.addView(progress,new LinearLayout.LayoutParams(-1,dp(24)));
        check=button(box,"检查更新",()->checkNow());download=button(box,"下载更新",()->download());install=button(box,"安装更新",()->install());cancel=button(box,"取消下载",()->{cancelled.set(true);status.setText("正在取消下载…");});
        Switch automatic=new Switch(this);automatic.setText("自动检查新版本");automatic.setTextSize(17);automatic.setMinHeight(dp(56));automatic.setChecked(AppUpdater.automatic(this));box.addView(automatic);
        automatic.setOnCheckedChangeListener((v,on)->{AppUpdater.prefs(this).edit().putBoolean("automatic",on).apply();if(on)UpdateWorker.schedule(this);else UpdateWorker.cancel(this);});
        box.addView(label("自动检查不会打断播放。下载后由系统确认安装，收藏和观看记录会保留。",15));
        if(Build.VERSION.SDK_INT>=33)button(box,"允许更新通知",()->requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},71));
        source=button(box,"更新源设置",()->sourceDialog());button(box,"返回",()->finish());
        render();checkNow();
    }
    private void render(){check.setEnabled(!busy);source.setEnabled(!busy);download.setVisibility(manifest!=null&&apk==null?View.VISIBLE:View.GONE);download.setEnabled(!busy);install.setVisibility(apk!=null?View.VISIBLE:View.GONE);install.setEnabled(!busy);cancel.setVisibility(busy&&progress.getVisibility()==View.VISIBLE?View.VISIBLE:View.GONE);}
    private void checkNow(){if(busy)return;busy=true;apk=null;manifest=null;details.setText("");status.setText("正在检查更新…");render();executor.execute(()->{
        try{UpdateManifest m=AppUpdater.check(this,true);ui(()->{manifest=m;busy=false;status.setText(m==null?"当前已是可用的最新版本":"发现新版本 "+m.versionName);if(m!=null){details.setText(m.notes+"\n\n下载大小："+String.format(java.util.Locale.ROOT,"%.1f MB",m.size/1048576.0));File existing=new File(getFilesDir(),"updates/kid-player-"+m.versionCode+".apk");if(existing.isFile())apk=existing;}render();});}
        catch(Exception e){ui(()->{busy=false;status.setText("暂时无法检查更新，请稍后重试。\n"+message(e));render();});}
    });}
    private void download(){if(busy||manifest==null)return;busy=true;cancelled.set(false);progress.setVisibility(View.VISIBLE);progress.setProgress(0);shownPercent=-1;status.setText("正在下载更新…");render();UpdateManifest selected=manifest;
        executor.execute(()->{try{File result=AppUpdater.download(this,selected,cancelled,(done,total)->{int percent=(int)(done*100/total);if(percent!=shownPercent){shownPercent=percent;ui(()->{progress.setProgress(percent);status.setText("正在下载更新 "+percent+"%");});}});ui(()->{apk=result;busy=false;progress.setVisibility(View.GONE);status.setText("下载与签名校验完成，可以安装更新");render();});}
        catch(Exception e){ui(()->{busy=false;progress.setVisibility(View.GONE);status.setText(cancelled.get()?"下载已取消，可重新下载":"下载未完成："+message(e));render();});}});
    }
    private void install(){if(busy||apk==null||manifest==null)return;busy=true;status.setText("正在核对安装包…");render();executor.execute(()->{
        try{AppUpdater.verifyApk(this,apk,manifest);ui(()->{busy=false;render();requestInstall();});}
        catch(Exception e){ui(()->{busy=false;apk=null;status.setText(message(e));render();});}
    });}
    private void requestInstall(){
        try{
            if(!getPackageManager().canRequestPackageInstalls()){
                new AlertDialog.Builder(this).setTitle("允许 kid player 安装更新").setMessage("请在接下来的系统页面允许此来源，返回后继续安装。")
                    .setPositiveButton("前往设置",(d,w)->{try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),70);}catch(Exception e){status.setText("此设备无法打开安装授权设置，请在系统设置中允许 kid player 安装应用。");}}).setNegativeButton("稍后",null).show();return;
            }
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".updates",apk);
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);
            startActivityForResult(intent,72);status.setText("请在系统页面确认安装；取消后仍可继续安装。");
        }catch(Exception e){status.setText("此设备无法启动安装器，请检查系统安装限制。");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==70){if(getPackageManager().canRequestPackageInstalls())install();else status.setText("尚未允许安装更新，可稍后重试。");}else if(request==72){status.setText(result==RESULT_OK?"安装完成":"安装未完成或已取消，可以重试。");}}
    private void sourceDialog(){EditText entry=new EditText(this);entry.setSingleLine();entry.setText(AppUpdater.source(this));entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);entry.setContentDescription("更新清单 HTTPS 地址");
        AlertDialog d=new AlertDialog.Builder(this).setTitle("更新源设置").setMessage("使用可信的 HTTPS 更新清单。更换服务器不改变应用签名要求。").setView(entry).setPositiveButton("保存",null).setNegativeButton("取消",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{AppUpdater.setSource(this,entry.getText().toString());d.dismiss();checkNow();}catch(Exception e){entry.setError(message(e));}}));d.show();}
    private String message(Exception e){return e.getMessage()==null?"网络连接失败，请重试":e.getMessage();}
    private void ui(Runnable action){handler.post(()->{if(!isFinishing()&&!isDestroyed())action.run();});}
    @Override public void onBackPressed(){if(busy){cancelled.set(true);}super.onBackPressed();}
    @Override protected void onDestroy(){cancelled.set(true);executor.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
