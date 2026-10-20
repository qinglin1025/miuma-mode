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
    boolean scheduleEnabled(){return p.getBoolean("weekly_enabled",false);}
    int scheduleDays(){int d=p.getInt("weekly_days",31);return d>0&&(d&~127)==0?d:31;}
    int scheduleHour(){return Math.max(0,Math.min(23,p.getInt("weekly_hour",22)));}
    int scheduleMinute(){return Math.max(0,Math.min(59,p.getInt("weekly_minute",30)));}
    long scheduleRevision(){return p.getLong("weekly_revision",0);}
    long scheduleNext(){return p.getLong("weekly_next",0);}
    void scheduleNext(long due){p.edit().putLong("weekly_next",due).commit();}
    long scheduleLastDue(){return p.getLong("weekly_last_due",0);}
    void consumeSchedule(long due){p.edit().putLong("weekly_last_due",due).putLong("weekly_next",0).commit();}
    String scheduleReport(){return p.getString("weekly_report","");}
    void scheduleReport(String report){
        p.edit().putString("weekly_report",android.text.format.DateFormat.format("MM-dd HH:mm",System.currentTimeMillis())+" "+report).apply();
    }
    void schedule(boolean enabled,int days,int hour,int minute){
        if(!WeeklySchedule.valid(days,hour,minute))throw new IllegalArgumentException("Invalid weekly rule");
        p.edit().putBoolean("weekly_enabled",enabled).putInt("weekly_days",days)
         .putInt("weekly_hour",hour).putInt("weekly_minute",minute)
         .putLong("weekly_revision",scheduleRevision()+1).putLong("weekly_next",0)
         .putLong("weekly_last_due",0).putString("weekly_report","").commit();
    }
    static String actionName(int v){return v==LOCK?"自动锁屏":v==REMIND?"弹出提醒":"震动";}
}
