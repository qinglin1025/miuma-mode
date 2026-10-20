package cn.niuma.app;
import android.app.AlarmManager;
import android.content.*;
public final class ScheduleRestoreReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
  String a=i.getAction();
  if(Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_TIME_CHANGED.equals(a)
    ||Intent.ACTION_TIMEZONE_CHANGED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
    ||AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(a))
   ScheduleManager.arm(c,true);
 }
}
