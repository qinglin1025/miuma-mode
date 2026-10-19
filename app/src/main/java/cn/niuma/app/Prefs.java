package cn.niuma.app;
import android.content.Context;
import android.content.SharedPreferences;
final class Prefs {
    static final int VIBRATE=0, REMIND=1, LOCK=2;
    private final SharedPreferences p;
    Prefs(Context c){p=c.getSharedPreferences("settings",Context.MODE_PRIVATE);}
    int minutes(){return Math.max(1,Math.min(180,p.getInt("minutes",15)));}
    void minutes(int v){p.edit().putInt("minutes",Math.max(1,Math.min(180,v))).apply();}
    int action(){int v=p.getInt("action",VIBRATE); return v>=0&&v<=2?v:VIBRATE;}
    void action(int v){p.edit().putInt("action",v).apply();}
    int popupMask(){return PopupOptions.normalize(p.getInt("popup_options",PopupOptions.ALL));}
    void popupMask(int mask){
        if(!PopupOptions.valid(mask))throw new IllegalArgumentException("Choose 2 to 4 options");
        p.edit().putInt("popup_options",mask).apply();
    }
    static String actionName(int v){return v==LOCK?"自动锁屏":v==REMIND?"弹出提醒":"震动";}
}
