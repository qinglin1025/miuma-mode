package cn.niuma.app;
import java.time.*;
final class WeeklySchedule {
 static boolean valid(int days,int hour,int minute){return days>0&&(days&~127)==0&&hour>=0&&hour<24&&minute>=0&&minute<60;}
 static long next(long now,ZoneId zone,int days,int hour,int minute){
  if(!valid(days,hour,minute))throw new IllegalArgumentException("Invalid weekly rule");
  LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
  for(int offset=0;offset<=7;offset++){
   LocalDate day=today.plusDays(offset);
   if((days&(1<<(day.getDayOfWeek().getValue()-1)))==0)continue;
   long candidate=day.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli();
   if(candidate>now)return candidate;
  }
  throw new IllegalStateException("No future occurrence");
 }
 static boolean due(boolean enabled,long expected,long delivered,long now){
  return enabled&&expected>0&&expected==delivered&&now>=delivered&&now-delivered<=300000;
 }
}
