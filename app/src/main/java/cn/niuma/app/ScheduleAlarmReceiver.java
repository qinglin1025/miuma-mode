package cn.niuma.app;
import android.content.*;
public final class ScheduleAlarmReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
  if(ScheduleManager.FIRE.equals(i.getAction()))ScheduleManager.fire(c,i);
 }
}
