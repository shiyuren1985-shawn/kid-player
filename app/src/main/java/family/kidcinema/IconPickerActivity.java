package family.kidcinema;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public final class IconPickerActivity extends Activity {
    private AppStore store;private LauncherIcons.Choice picked;private GridLayout grid;private TextView status;
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new AppStore(this);picked=state==null?LauncherIcons.selected(store):LauncherIcons.find(state.getString("picked"));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(20),dp(20),dp(16));root.setBackgroundColor(MainActivity.BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(20)+insets.getSystemWindowInsetLeft(),dp(20)+insets.getSystemWindowInsetTop(),dp(20)+insets.getSystemWindowInsetRight(),dp(16)+insets.getSystemWindowInsetBottom());return insets;});
        TextView title=new TextView(this);title.setText("选择桌面图标");title.setTextSize(24);title.setTextColor(MainActivity.INK);root.addView(title);
        TextView explanation=new TextView(this);explanation.setText("挑一个喜欢的图案，显示在手机或平板桌面上。部分桌面需要几秒钟刷新。");explanation.setTextSize(15);explanation.setTextColor(MainActivity.MUTED);explanation.setPadding(0,dp(8),0,dp(12));root.addView(explanation);
        ScrollView scroll=new ScrollView(this);grid=new GridLayout(this);grid.setColumnCount(getResources().getConfiguration().screenWidthDp>=700?4:2);scroll.addView(grid);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(this);status.setTextSize(16);status.setTextColor(MainActivity.INK);status.setPadding(0,dp(8),0,dp(8));root.addView(status);
        LinearLayout actions=new LinearLayout(this);Button back=new Button(this);back.setText("返回");back.setMinHeight(dp(52));back.setOnClickListener(v->finish());actions.addView(back,new LinearLayout.LayoutParams(0,-2,1));
        Button use=new Button(this);use.setText("使用这个图标");use.setMinHeight(dp(52));use.setOnClickListener(v->{try{LauncherIcons.select(this,store,picked);Toast.makeText(this,"已更换为“"+picked.label+"”",Toast.LENGTH_SHORT).show();finish();}catch(RuntimeException e){status.setText("暂时无法更换图标，请稍后重试。");}});actions.addView(use,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
        setContentView(root);root.requestApplyInsets();draw();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString("picked",picked.id);super.onSaveInstanceState(state);}
    private GradientDrawable background(boolean selected,boolean focused){GradientDrawable shape=new GradientDrawable();shape.setColor(selected?MainActivity.PANEL:Color.WHITE);shape.setCornerRadius(dp(18));if(selected||focused)shape.setStroke(dp(focused?4:2),MainActivity.FOCUS);return shape;}
    private void draw(){
        grid.removeAllViews();
        for(LauncherIcons.Choice choice:LauncherIcons.ALL){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(10),dp(12),dp(10),dp(12));card.setBackground(background(choice==picked,false));card.setFocusable(true);card.setContentDescription("选择图标："+choice.label);card.setTag("icon:"+choice.id);card.setSelected(choice==picked);
            ImageView preview=new ImageView(this);preview.setImageResource(choice.image);preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(preview,new LinearLayout.LayoutParams(dp(84),dp(84)));
            TextView name=new TextView(this);name.setText((choice==picked?"✓ ":"")+choice.label);name.setTextSize(16);name.setTextColor(MainActivity.INK);name.setGravity(Gravity.CENTER);name.setMinHeight(dp(38));card.addView(name,new LinearLayout.LayoutParams(-1,-2));
            card.setOnFocusChangeListener((v,focus)->v.setBackground(background(choice==picked,focus)));
            card.setOnClickListener(v->{picked=choice;draw();View replacement=grid.findViewWithTag("icon:"+choice.id);if(!v.isInTouchMode()&&replacement!=null)replacement.requestFocus();});
            GridLayout.LayoutParams params=new GridLayout.LayoutParams();params.width=0;params.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);params.setMargins(dp(5),dp(5),dp(5),dp(5));grid.addView(card,params);
        }
        status.setText("当前使用："+LauncherIcons.selected(store).label+"　｜　已选择："+picked.label);
    }
}
