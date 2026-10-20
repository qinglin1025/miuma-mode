package cn.niuma.app;
import android.app.*;
import android.content.*;
import android.os.Build;
import android.provider.Settings;
import java.time.ZoneId;

final class ScheduleManager {
 static final String FIRE="cn.niuma.app.WEEKLY_START";
 static boolean exactAllowed(Context c){
  return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
 }
 private static PendingIntent alarm(Context c,long due,long revision){
  return PendingIntent.getBroadcast(c,70,new Intent(c,ScheduleAlarmReceiver.class).setAction(FIRE)
   .putExtra("due",due).putExtra("revision",revision),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 static void arm(Context c,boolean reset){
  Prefs p=new Prefs(c);
  AlarmManager am=c.getSystemService(AlarmManager.class);
  am.cancel(alarm(c,0,0));
  if(!p.scheduleEnabled()||!exactAllowed(c)||!Settings.canDrawOverlays(c)){
   p.scheduleNext(0);return;
  }
  long now=System.currentTimeMillis(),next=p.scheduleNext();
  if(reset||next<=0||next<now-300000)
   next=WeeklySchedule.next(now,ZoneId.systemDefault(),p.scheduleDays(),p.scheduleHour(),p.scheduleMinute());
  try{
   p.scheduleNext(next);
   am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarm(c,next,p.scheduleRevision()));
  }catch(RuntimeException e){
   p.scheduleNext(0);p.scheduleReport("定时安排失败，请检查闹钟权限");
   android.util.Log.w("NiumaMode","Cannot schedule weekly start",e);
  }
 }
 static void fire(Context c,Intent intent){
  Prefs p=new Prefs(c);
  long now=System.currentTimeMillis(),due=intent.getLongExtra("due",0),revision=intent.getLongExtra("revision",-1);
  if(!p.scheduleEnabled()||revision!=p.scheduleRevision()||due!=p.scheduleNext())return;
  boolean run=WeeklySchedule.due(true,p.scheduleNext(),due,now);
  if(now<due){arm(c,false);return;}
  p.consumeSchedule(due);
  arm(c,true); // Always re-arm the next occurrence, including when this one cannot start.
  if(!run){p.scheduleReport("错过本次时间，已安排下一次");return;}
  if(!exactAllowed(c)||!Settings.canDrawOverlays(c)){
   failed(c,"定时未开启：请检查闹钟和悬浮窗权限");return;
  }
  if(ModeService.running){p.scheduleReport("到点时模式已开启，保留原计时");return;}
  try{
   p.scheduleReport("定时已触发，正在开启");
   c.startForegroundService(new Intent(c,ModeService.class).setAction(ModeService.START)
    .putExtra("scheduled_due",due).putExtra("scheduled_revision",revision));
  }catch(RuntimeException e){
   failed(c,"系统阻止了后台开启，请检查小米自启动和省电设置");
   android.util.Log.w("NiumaMode","Scheduled start blocked",e);
  }
 }
 static void failed(Context c,String message){
  new Prefs(c).scheduleReport(message);
  NotificationManager nm=c.getSystemService(NotificationManager.class);
  if(!nm.areNotificationsEnabled())return;
  nm.createNotificationChannel(new NotificationChannel("schedule","定时开启提醒",NotificationManager.IMPORTANCE_DEFAULT));
  PendingIntent open=PendingIntent.getActivity(c,71,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  try{nm.notify(71,new Notification.Builder(c,"schedule").setSmallIcon(R.drawable.ic_launcher)
   .setContentTitle("牛马模式未能定时开启").setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
   .setContentIntent(open).setAutoCancel(true).build());}catch(SecurityException ignored){}
 }
 static String summary(Context c){
  Prefs p=new Prefs(c);
  if(!p.scheduleEnabled())return "未启用";
  if(!exactAllowed(c))return "等待“闹钟和提醒”授权";
  if(!Settings.canDrawOverlays(c))return "等待悬浮窗授权";
  if(p.scheduleNext()==0)return "尚未安排成功，请检查权限";
  return "下次："+android.text.format.DateFormat.format("MM月dd日 E HH:mm",p.scheduleNext());
 }
 static String days(int mask){
  String[] names={"周一","周二","周三","周四","周五","周六","周日"};
  StringBuilder s=new StringBuilder();
  for(int i=0;i<7;i++)if((mask&(1<<i))!=0){if(s.length()>0)s.append(" ");s.append(names[i]);}
  return s.toString();
 }
}
