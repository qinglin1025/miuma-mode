package cn.niuma.app;
import java.time.*;
public final class WeeklyScheduleTest {
 static int checks;
 static void yes(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static long t(String v){return ZonedDateTime.parse(v).toInstant().toEpochMilli();}
 static void next(String now,String want,int mask,int h,int m){
  yes(WeeklySchedule.next(t(now),ZonedDateTime.parse(now).getZone(),mask,h,m)==t(want),"next after "+now);
 }
 public static void main(String[] args){
  next("2026-10-20T20:00:00+08:00[Asia/Shanghai]","2026-10-20T22:30:00+08:00[Asia/Shanghai]",2,22,30);
  next("2026-10-20T22:30:00+08:00[Asia/Shanghai]","2026-10-27T22:30:00+08:00[Asia/Shanghai]",2,22,30);
  next("2026-10-23T23:00:00+08:00[Asia/Shanghai]","2026-10-26T22:30:00+08:00[Asia/Shanghai]",31,22,30);
  next("2026-10-25T23:59:00+08:00[Asia/Shanghai]","2026-10-26T00:00:00+08:00[Asia/Shanghai]",1,0,0);
  next("2026-12-31T23:59:00+08:00[Asia/Shanghai]","2027-01-01T00:00:00+08:00[Asia/Shanghai]",127,0,0);
  next("2026-03-08T00:00:00-05:00[America/New_York]","2026-03-08T03:30:00-04:00[America/New_York]",64,2,30);
  yes(!WeeklySchedule.valid(0,22,30),"empty days rejected");
  yes(!WeeklySchedule.valid(128,22,30),"unknown weekday rejected");
  yes(!WeeklySchedule.valid(1,24,0),"invalid hour");
  yes(!WeeklySchedule.valid(1,22,60),"invalid minute");
  yes(WeeklySchedule.due(true,10000,10000,10000),"matching alarm runs");
  yes(!WeeklySchedule.due(false,10000,10000,10000),"disabled never runs");
  yes(!WeeklySchedule.due(true,20000,10000,10000),"old alarm ignored");
  yes(!WeeklySchedule.due(true,10000,10000,9999),"early alarm ignored");
  yes(!WeeklySchedule.due(true,10000,10000,310001),"very late alarm skipped");
  yes(!WeeklySchedule.due(true,0,0,0),"consumed alarm ignored");
  System.out.println("PASS: "+checks+" weekly schedule checks");
 }
}
