package family.kidcinema;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.*;

/** Stable aliases are retained across upgrades; MainActivity is never disabled. */
final class LauncherIcons {
    static final class Choice {
        final String id,label,alias;final int image;
        Choice(String id,String label,String alias,int image){this.id=id;this.label=label;this.alias=alias;this.image=image;}
        ComponentName component(Context context){return new ComponentName(context.getPackageName(),context.getPackageName()+"."+alias);}
    }
    static final Choice[] ALL={
        new Choice("heart","萌可","LauncherHeart",R.mipmap.ic_launcher),
        new Choice("kid","Kid Player 原创","LauncherKid",R.mipmap.launcher_kid),
        new Choice("ultra","奥特曼","LauncherUltra",R.mipmap.launcher_ultra),
        new Choice("robot","擎天柱","LauncherRobot",R.mipmap.launcher_robot),
        new Choice("moon","美少女战士","LauncherMoon",R.mipmap.launcher_moon),
        new Choice("bear","太空小熊","LauncherBear",R.mipmap.launcher_bear),
        new Choice("dora","哆啦 A 梦","LauncherDora",R.mipmap.launcher_dora),
        new Choice("dino","小恐龙","LauncherDino",R.mipmap.launcher_dino)
    };
    static Choice find(String id){for(Choice choice:ALL)if(choice.id.equals(id))return choice;return ALL[0];}
    static Choice selected(AppStore store){return find(store.prefs.getString("launcher.icon","heart"));}
    static boolean enabled(Context context,Choice choice){
        int state=context.getPackageManager().getComponentEnabledSetting(choice.component(context));
        return state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED||(state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT&&choice.id.equals("heart"));
    }
    static void reconcile(Context context,AppStore store){
        Choice selected=selected(store);boolean repair=!enabled(context,selected);
        for(Choice choice:ALL)if(choice!=selected&&enabled(context,choice))repair=true;
        if(repair)select(context,store,selected);
    }
    static void select(Context context,AppStore store,Choice selected){
        if(!Arrays.asList(ALL).contains(selected))throw new IllegalArgumentException("Unknown icon");
        PackageManager pm=context.getPackageManager();int[] previous=new int[ALL.length];
        for(int i=0;i<ALL.length;i++)previous[i]=pm.getComponentEnabledSetting(ALL[i].component(context));
        try{
            if(Build.VERSION.SDK_INT>=33){
                List<PackageManager.ComponentEnabledSetting> changes=new ArrayList<>();
                for(Choice choice:ALL)changes.add(new PackageManager.ComponentEnabledSetting(choice.component(context),choice==selected?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP));
                pm.setComponentEnabledSettings(changes);
            }else{
                // Enable the replacement first so the app never loses all launcher entries.
                pm.setComponentEnabledSetting(selected.component(context),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
                for(Choice choice:ALL)if(choice!=selected)pm.setComponentEnabledSetting(choice.component(context),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
            }
            store.prefs.edit().putString("launcher.icon",selected.id).apply();
        }catch(RuntimeException failure){
            for(int i=0;i<ALL.length;i++)try{pm.setComponentEnabledSetting(ALL[i].component(context),previous[i],PackageManager.DONT_KILL_APP);}catch(RuntimeException ignored){}
            throw failure;
        }
    }
}
