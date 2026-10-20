package cn.niuma.app;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Owns every overlay and callback. Explicit exit tears all of them down. */
public final class ModeService extends Service {
    public static final String START="cn.niuma.app.START", STOP="cn.niuma.app.STOP";
    public static boolean running=false;
    private static final int NOTICE=41;
    private final Session session=new Session();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private PowerManager power;
    private KeyguardManager keyguard;
    private Prefs prefs;
    private FrameLayout cardRoot;
    private ClockView clock;
    private WindowManager.LayoutParams clockParams;
    private boolean registered;
    private int sessionAction;
    private int x=-1,y=-1;
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            android.util.Log.i("NiumaMode","screen event="+i.getAction()+" state="+session.state());
            if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))endSession();
            else if(Intent.ACTION_USER_PRESENT.equals(i.getAction())||Intent.ACTION_SCREEN_ON.equals(i.getAction()))syncScreen();
        }
    };
    // Kept alive after countdown completion and while waiting for the next unlock.
    // Handler callbacks do not hold a wake lock or wake a sleeping device.
    private final Runnable screenMonitor=new Runnable(){
        public void run(){
            if(!running)return;
            syncScreen();
            if(running)handler.postDelayed(this,1000);
        }
    };
    private void syncScreen(){
        if(!running)return;
        if(!Settings.canDrawOverlays(this)){exit();return;}
        boolean interactive=power.isInteractive(),locked=keyguard.isKeyguardLocked();
        int before=session.state();
        boolean prompt=session.reconcileScreen(interactive,locked);
        if(before!=Session.WAITING&&session.state()==Session.WAITING){
            endSession();
        }else if(prompt){
            handler.removeCallbacks(tick);
            removeClock();
            showChoice();
        }
        if(before!=session.state())android.util.Log.i("NiumaMode",
            "screen state "+before+" -> "+session.state()+" interactive="+interactive+" locked="+locked);
    }

    private final Runnable tick=new Runnable(){
        public void run(){
            if(!running)return;
            if(!Settings.canDrawOverlays(ModeService.this)){exit();return;}
            if(!power.isInteractive()||keyguard.isKeyguardLocked()){endSession();return;}
            long now=SystemClock.elapsedRealtime();
            if(session.expire(now)){finishCountdown();return;}
            updateClock(now);
            if(session.state()==Session.UP||session.state()==Session.DOWN)handler.postDelayed(this,250);
        }
    };
    @Override public void onCreate(){
        super.onCreate();windows=getSystemService(WindowManager.class);power=getSystemService(PowerManager.class);
        keyguard=getSystemService(KeyguardManager.class);prefs=new Prefs(this);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||STOP.equals(intent.getAction())){exit();return START_NOT_STICKY;}
        boolean scheduled=intent.hasExtra("scheduled_due");
        if(scheduled&&(!prefs.scheduleEnabled()
            ||intent.getLongExtra("scheduled_revision",-1)!=prefs.scheduleRevision()
            ||intent.getLongExtra("scheduled_due",0)!=prefs.scheduleLastDue())){
            if(!running)stopSelf();return START_NOT_STICKY;
        }
        if(!Settings.canDrawOverlays(this)){
            if(scheduled)ScheduleManager.failed(this,"定时未开启：缺少悬浮窗权限");
            exit();return START_NOT_STICKY;
        }
        NotificationManager nm=getSystemService(NotificationManager.class);
        NotificationChannel ch=new NotificationChannel("mode","牛马模式运行状态",NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("模式运行时显示，可随时退出");nm.createNotificationChannel(ch);
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,ModeService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"mode").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("牛马模式已开启").setContentText("解锁后选择计时 · 点此进入设置")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null,"退出模式",stop).build()).build();
        try{
            if(Build.VERSION.SDK_INT>=34)startForeground(NOTICE,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTICE,n);
        }catch(RuntimeException e){
            if(scheduled)ScheduleManager.failed(this,"系统阻止了定时后台运行，请检查自启动和后台运行权限");
            Toast.makeText(this,"无法启动后台计时，请检查系统权限",Toast.LENGTH_LONG).show();exit();return START_NOT_STICKY;
        }
        if(!registered){
            IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_USER_PRESENT);f.addAction(Intent.ACTION_SCREEN_ON);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screenReceiver,f);
            registered=true;
        }
        if(scheduled)prefs.scheduleReport(running?"到点时模式已开启，保留原计时":"定时开启成功");
        getSystemService(NotificationManager.class).cancel(71);
        running=true;
        if(session.state()==Session.OFF){
            session.enable();
            if(power.isInteractive()&&!keyguard.isKeyguardLocked())showChoice();else session.screenOff();
        }
        handler.removeCallbacks(screenMonitor);
        handler.postDelayed(screenMonitor,1000);
        return START_NOT_STICKY;
    }
    private void endSession(){
        handler.removeCallbacks(tick);session.screenOff();removeCard();removeClock();
        Vibrator v=getSystemService(Vibrator.class);if(v!=null)v.cancel();
    }
    private void showChoice(){
        if(!running||session.state()!=Session.CHOOSING)return;
        LinearLayout box=makeCard("这次，准备怎么用？","选择本次操作");
        for(int option:PopupOptions.ordered(prefs.popupMask())){
            if(option==PopupOptions.UP)addButton(box,"正计时\n看见时间流逝",true,()->startTimer(false));
            else if(option==PopupOptions.DOWN)addButton(box,"倒计时 · "+prefs.minutes()+" 分钟\n到时"+Prefs.actionName(prefs.action()),false,()->startTimer(true));
            else if(option==PopupOptions.SCREEN_OFF)addButton(box,"直接息屏\n保留牛马模式",false,this::directScreenOff);
            else if(option==PopupOptions.EXIT)addButton(box,"退出牛马模式",false,this::exit);
        }
        TextView hint=Ui.text(this,"显示选项、时长与提醒方式请在软件内修改",11,Ui.MUTED,false);
        hint.setGravity(Gravity.CENTER);Ui.space(box,10);box.addView(hint);
        attachCard(box);
    }
    private boolean lockScreen(){
        DevicePolicyManager dm=getSystemService(DevicePolicyManager.class);
        ComponentName admin=new ComponentName(this,LockAdminReceiver.class);
        try{
            if(dm!=null&&dm.isAdminActive(admin)){dm.lockNow();endSession();return true;}
        }catch(SecurityException e){android.util.Log.w("NiumaMode","Lock permission unavailable",e);}
        return false;
    }
    private void directScreenOff(){
        if(!running||session.state()!=Session.CHOOSING)return;
        if(lockScreen())return;
        LinearLayout box=makeCard("需要息屏授权","请在软件内开启息屏 / 锁屏授权，然后再次选择直接息屏。");
        addButton(box,"打开软件授权",true,()->{
            removeCard();
            try{
                startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("lock_authorization",true));
            }catch(RuntimeException e){
                Toast.makeText(this,"请手动打开牛马模式进行授权",Toast.LENGTH_LONG).show();showChoice();
            }
        });
        addButton(box,"返回选择",false,this::showChoice);attachCard(box);
    }
    private void startTimer(boolean down){
        if(session.state()!=Session.CHOOSING)return;
        removeCard();
        long now=SystemClock.elapsedRealtime();
        sessionAction=prefs.action();
        if(down)session.startDown(now,prefs.minutes()*60000L);else session.startUp(now);
        createClock();handler.removeCallbacks(tick);handler.post(tick);
    }
    private LinearLayout makeCard(String title,String subtitle){
        removeCard();
        LinearLayout b=Ui.card(this);
        TextView head=Ui.text(this,title,22,Ui.INK,true);head.setGravity(Gravity.CENTER);b.addView(head);
        Ui.space(b,8);
        TextView sub=Ui.text(this,subtitle,13,Ui.MUTED,false);sub.setGravity(Gravity.CENTER);b.addView(sub);
        Ui.space(b,22);return b;
    }
    private void addButton(LinearLayout b,String title,boolean primary,Runnable action){
        TextView button=Ui.button(this,title,primary);button.setOnClickListener(v->action.run());
        b.addView(button,new LinearLayout.LayoutParams(-1,-2));Ui.space(b,10);
    }
    private void attachCard(LinearLayout box){
        cardRoot=new FrameLayout(this);cardRoot.setBackgroundColor(0x55000000);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(box);
        int width=Math.min(screenWidth()-Ui.dp(this,40),Ui.dp(this,360));
        FrameLayout.LayoutParams inside=new FrameLayout.LayoutParams(width,-2,Gravity.CENTER);
        inside.setMargins(Ui.dp(this,20),Ui.dp(this,40),Ui.dp(this,20),Ui.dp(this,40));
        cardRoot.addView(scroll,inside);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.START;
        p.setTitle("牛马模式选择");
        try{windows.addView(cardRoot,p);}catch(RuntimeException e){cardRoot=null;overlayFailure();}
    }
    private void createClock(){
        removeClock();
        clock=new ClockView(this);
        int size=Ui.dp(this,session.state()==Session.DOWN?88:56);
        if(x<0)x=screenWidth()-size-Ui.dp(this,16);
        if(y<0)y=Ui.dp(this,70);
        clockParams=new WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        clockParams.gravity=Gravity.TOP|Gravity.START;clampPosition(size);clockParams.x=x;clockParams.y=y;clockParams.setTitle("牛马模式悬浮时钟");
        clock.setOnClickListener(v->showClockControls());
        clock.setOnTouchListener(new View.OnTouchListener(){
            float downX,downY;int originalX,originalY;boolean dragging;
            public boolean onTouch(View v,android.view.MotionEvent e){
                if(clockParams==null)return false;
                if(e.getAction()==MotionEvent.ACTION_DOWN){
                    downX=e.getRawX();downY=e.getRawY();originalX=x;originalY=y;dragging=false;return true;
                }
                if(e.getAction()==MotionEvent.ACTION_MOVE){
                    float dx=e.getRawX()-downX,dy=e.getRawY()-downY;
                    if(Math.abs(dx)+Math.abs(dy)>Ui.dp(ModeService.this,6))dragging=true;
                    if(dragging){
                        x=originalX+(int)dx;y=originalY+(int)dy;clampPosition(clockParams.width);
                        clockParams.x=x;clockParams.y=y;safeUpdateClock();
                    }return true;
                }
                if(e.getAction()==MotionEvent.ACTION_UP){if(!dragging)v.performClick();return true;}
                return true;
            }
        });
        updateClock(SystemClock.elapsedRealtime());
        if(clock==null)return;
        try{windows.addView(clock,clockParams);}catch(RuntimeException e){clock=null;overlayFailure();}
    }
    private void updateClock(long now){
        if(clock==null)return;
        boolean down=session.state()==Session.DOWN||session.state()==Session.DONE;
        float growth=session.growth(now);
        int min=Ui.dp(this,56),max=Math.min(Ui.dp(this,144),(int)(screenWidth()*0.40f));
        int size=down?Ui.dp(this,88):(int)(min+(max-min)*growth);
        clock.update(session.displayMillis(now),growth,down,session.remainingFraction(now));
        if(clockParams.width!=size){
            clockParams.width=clockParams.height=size;
            clampPosition(size);clockParams.x=x;clockParams.y=y;
            if(clock.isAttachedToWindow())safeUpdateClock();
        }
    }
    private void safeUpdateClock(){
        if(clock==null||!clock.isAttachedToWindow())return;
        try{windows.updateViewLayout(clock,clockParams);}catch(RuntimeException e){overlayFailure();}
    }
    private void showClockControls(){
        LinearLayout b=makeCard(session.state()==Session.DONE?"本次倒计时已结束":"计时进行中",
            session.state()==Session.DONE?"锁屏后，下次解锁重新选择":"时长与到时动作在软件内设置");
        addButton(b,"返回悬浮时钟",true,this::removeCard);
        addButton(b,"退出牛马模式",false,this::exit);
        attachCard(b);
    }
    private void finishCountdown(){
        updateClock(SystemClock.elapsedRealtime());
        removeCard();
        if(sessionAction==Prefs.LOCK){
            if(lockScreen())return;
            showReminder("自动锁屏未完成","请在软件内检查锁屏授权。");return;
        }
        if(sessionAction==Prefs.VIBRATE){
            Vibrator v=getSystemService(Vibrator.class);
            if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createWaveform(new long[]{0,350,200,350,200,550},-1));
            else showReminder("时间到了","该放下手机，休息一下了。");
        }else showReminder("时间到了","该放下手机，休息一下了。");
    }
    private void showReminder(String title,String message){
        LinearLayout b=makeCard(title,message);
        addButton(b,"知道了",true,this::removeCard);addButton(b,"退出牛马模式",false,this::exit);attachCard(b);
    }
    private int screenWidth(){
        if(Build.VERSION.SDK_INT>=30)return windows.getCurrentWindowMetrics().getBounds().width();
        return getResources().getDisplayMetrics().widthPixels;
    }
    private int screenHeight(){
        if(Build.VERSION.SDK_INT>=30)return windows.getCurrentWindowMetrics().getBounds().height();
        return getResources().getDisplayMetrics().heightPixels;
    }
    private void clampPosition(int size){
        x=Math.max(0,Math.min(x,screenWidth()-size));
        y=Math.max(Ui.dp(this,12),Math.min(y,screenHeight()-size-Ui.dp(this,70)));
    }
    @Override public void onConfigurationChanged(Configuration c){
        super.onConfigurationChanged(c);
        if(clock!=null){clampPosition(clockParams.width);clockParams.x=x;clockParams.y=y;safeUpdateClock();}
        if(cardRoot!=null){
            if(session.state()==Session.CHOOSING)showChoice();
            else if(session.state()==Session.DONE)showReminder("本次倒计时已结束","锁屏后，下次解锁重新选择。");
            else showClockControls();
        }
    }
    private void removeClock(){
        if(clock!=null){try{windows.removeView(clock);}catch(IllegalArgumentException ignored){}clock=null;}
    }
    private void removeCard(){
        if(cardRoot!=null){try{windows.removeView(cardRoot);}catch(IllegalArgumentException ignored){}cardRoot=null;}
    }
    private void overlayFailure(){
        if(prefs.scheduleEnabled())prefs.scheduleReport("模式已停止：悬浮窗未能显示");
        Toast.makeText(this,"悬浮窗未能显示，模式已停止。请检查系统权限。",Toast.LENGTH_LONG).show();exit();
    }
    private void exit(){
        running=false;session.disable();handler.removeCallbacksAndMessages(null);
        removeCard();removeClock();
        Vibrator v=getSystemService(Vibrator.class);if(v!=null)v.cancel();
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public void onDestroy(){
        android.util.Log.i("NiumaMode","service destroyed");
        running=false;session.disable();handler.removeCallbacksAndMessages(null);removeCard();removeClock();
        Vibrator v=getSystemService(Vibrator.class);if(v!=null)v.cancel();
        if(registered){unregisterReceiver(screenReceiver);registered=false;}
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
