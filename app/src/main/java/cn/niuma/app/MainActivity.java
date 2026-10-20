package cn.niuma.app;

import android.Manifest;
import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** All configuration lives here; the unlock overlay only starts a saved session. */
public final class MainActivity extends Activity {
    private Prefs prefs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status, toggle;
    private final Runnable refresh=new Runnable(){
        public void run(){updateStatus();handler.postDelayed(this,750);}
    };
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=new Prefs(this);}
    @Override public void onResume(){super.onResume();ScheduleManager.arm(this,false);render();handler.removeCallbacks(refresh);handler.post(refresh);
        if(getIntent().getBooleanExtra("lock_authorization",false)){
            getIntent().removeExtra("lock_authorization");
            if(!adminActive())explainAdmin();
        }}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private boolean adminActive(){
        return ((DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE)).isAdminActive(new ComponentName(this,LockAdminReceiver.class));
    }
    private void updateStatus(){
        if(status==null)return;
        status.setText(ModeService.running?"●  模式已开启":"○  模式未开启");
        toggle.setText(ModeService.running?"退出牛马模式":"开启牛马模式");
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);
        LinearLayout page=Ui.column(this);int pad=Ui.dp(this,24);page.setPadding(pad,pad,pad,pad);
        scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(i.left,i.top,i.right,i.bottom);
            }else{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());}
            return insets;
        });
        setContentView(scroll);
        page.addView(Ui.text(this,"NIUMA  /  PERSONAL",11,Ui.MUTED,true));
        Ui.space(page,14);
        page.addView(Ui.text(this,"牛马模式",34,Ui.INK,true));
        Ui.space(page,6);
        page.addView(Ui.text(this,"让每次拿起手机，都有分寸。",15,Ui.MUTED,false));
        Ui.space(page,24);
        LinearLayout state=Ui.card(this);state.setBackground(Ui.bg(this,Color.rgb(231,241,210),22));
        status=Ui.text(this,"",19,Ui.INK,true);state.addView(status);
        Ui.space(state,7);state.addView(Ui.text(this,"开启后，每次解锁选择本次计时方式",13,Ui.MUTED,false));
        Ui.space(state,18);
        toggle=Ui.button(this,"",true);state.addView(toggle);
        toggle.setOnClickListener(v->{
            if(ModeService.running){
                stopService(new Intent(this,ModeService.class));handler.postDelayed(this::updateStatus,150);
            }else startMode();
        });
        page.addView(state);Ui.space(page,22);

        LinearLayout countdown=Ui.card(this);
        LinearLayout duration=Ui.column(this);
        duration.addView(Ui.text(this,"倒计时时长",14,Ui.MUTED,false));Ui.space(duration,10);
        TextView minutes=Ui.text(this,prefs.minutes()+" 分钟    ›",28,Ui.INK,true);
        duration.addView(minutes);
        duration.setOnClickListener(v->editMinutes());duration.setContentDescription("修改倒计时时长，当前"+prefs.minutes()+"分钟");
        countdown.addView(duration);Ui.space(countdown,22);

        LinearLayout actions=Ui.column(this);actions.addView(Ui.text(this,"到时动作",15,Ui.INK,true));Ui.space(actions,12);
        LinearLayout choices=new LinearLayout(this);
        for(int a=0;a<3;a++){
            final int action=a;
            String label=a==0?"震动":a==1?"提醒":"自动锁屏";
            TextView option=Ui.button(this,label,prefs.action()==a);option.setTextSize(14);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,58),1);
            if(a<2)lp.setMargins(0,0,Ui.dp(this,6),0);
            choices.addView(option,lp);
            option.setOnClickListener(v->{
                prefs.action(action);render();
                if(action==Prefs.LOCK&&!adminActive())explainAdmin();
            });
        }
        actions.addView(choices);
        if(prefs.action()==Prefs.LOCK&&!adminActive()){
            Ui.space(actions,12);
            actions.addView(Ui.text(this,"自动锁屏需要单独授权；未授权时改为提醒",12,Ui.MUTED,false));
        }
        countdown.addView(actions);page.addView(countdown);Ui.space(page,22);

        LinearLayout popup=Ui.card(this);
        popup.addView(Ui.text(this,"解锁弹窗选项",17,Ui.INK,true));Ui.space(popup,8);
        popup.addView(Ui.text(this,"已显示 "+Integer.bitCount(prefs.popupMask())+" 个选项  ›",22,Ui.INK,true));
        popup.setOnClickListener(v->editPopupOptions());page.addView(popup);Ui.space(page,22);
        LinearLayout weekly=Ui.card(this);
        LinearLayout weeklyHeader=new LinearLayout(this);weeklyHeader.setGravity(Gravity.CENTER_VERTICAL);
        weeklyHeader.addView(Ui.text(this,"每周定时开启",18,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));
        Switch weeklyToggle=new Switch(this);
        weeklyToggle.setContentDescription("每周定时开启");
        weeklyToggle.setMinHeight(Ui.dp(this,48));weeklyToggle.setMinWidth(Ui.dp(this,48));
        weeklyToggle.setChecked(prefs.scheduleEnabled());
        weeklyHeader.addView(weeklyToggle);weekly.addView(weeklyHeader);Ui.space(weekly,8);
        weeklyToggle.setOnCheckedChangeListener((button,enabled)->{
            prefs.schedule(enabled,prefs.scheduleDays(),prefs.scheduleHour(),prefs.scheduleMinute());
            ScheduleManager.arm(this,true);render();
            if(enabled)requestSchedulePermissions();
        });
        weekly.addView(Ui.text(this,String.format(java.util.Locale.ROOT,"%02d:%02d",prefs.scheduleHour(),prefs.scheduleMinute()),28,Ui.INK,true));
        weekly.addView(Ui.text(this,ScheduleManager.days(prefs.scheduleDays()),13,Ui.MUTED,false));Ui.space(weekly,8);
        weekly.addView(Ui.text(this,ScheduleManager.summary(this),14,Ui.INK,false));
        if(!prefs.scheduleReport().isEmpty()){Ui.space(weekly,8);weekly.addView(Ui.text(this,"上次记录："+prefs.scheduleReport(),12,Ui.MUTED,false));}
        weekly.setOnClickListener(v->editWeeklySchedule());page.addView(weekly);Ui.space(page,22);
        LinearLayout permissions=Ui.card(this);
        permissions.addView(Ui.text(this,"权限与后台设置",18,Ui.INK,true));Ui.space(permissions,10);
        addSetting(permissions,"悬浮窗",Settings.canDrawOverlays(this)?"已允许":"去开启",()->{
            openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
        });
        boolean notifications=getSystemService(NotificationManager.class).areNotificationsEnabled();
        addSetting(permissions,"通知与退出入口",notifications?"已允许":"去开启",()->{
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
            }else openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        addSetting(permissions,"息屏 / 锁屏授权",adminActive()?"已授权 / 管理":"可选",()->{
            if(adminActive())openSettings(new Intent(Settings.ACTION_SECURITY_SETTINGS));else explainAdmin();
        });
        addSetting(permissions,"定时开启权限",ScheduleManager.exactAllowed(this)?"闹钟已允许":"去开启",this::requestSchedulePermissions);
        addSetting(permissions,"后台与弹窗权限","查看说明",this::showBackgroundHelp);
        addSetting(permissions,"系统电池优化",batteryExempt()?"已豁免":"查看",()->
            openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        addSetting(permissions,"设备与权限检查","查看",this::showDiagnostics);
        page.addView(permissions);
        Ui.space(page,14);
        page.addView(Ui.text(this,"个人版 0.1.7 · 设置仅保存在本机",12,Ui.MUTED,false));
        updateStatus();
    }
    private void addSetting(LinearLayout parent,String title,String detail,Runnable action){
        TextView row=Ui.text(this,title+"    "+detail+"  ›",14,Ui.INK,false);
        int p=Ui.dp(this,14);row.setPadding(p,p,p,p);row.setBackground(Ui.bg(this,Color.WHITE,12));
        row.setOnClickListener(v->action.run());parent.addView(row);Ui.space(parent,6);
    }
    private void editWeeklySchedule(){
        LinearLayout box=Ui.column(this);int pad=Ui.dp(this,20);box.setPadding(pad,0,pad,0);
        Switch enabled=new Switch(this);enabled.setText("启用每周定时");enabled.setChecked(prefs.scheduleEnabled());box.addView(enabled);
        Ui.space(box,12);box.addView(Ui.text(this,"选择星期（可多选）",16,Ui.INK,true));
        String[] names={"周一","周二","周三","周四","周五","周六","周日"};
        CheckBox[] days=new CheckBox[7];
        for(int row=0;row<2;row++){
            LinearLayout line=new LinearLayout(this);
            for(int col=0;col<4;col++){
                int day=row*4+col;if(day>=7)break;
                days[day]=new CheckBox(this);days[day].setText(names[day]);days[day].setTextSize(13);
                days[day].setChecked((prefs.scheduleDays()&(1<<day))!=0);
                line.addView(days[day],new LinearLayout.LayoutParams(0,-2,1));
            }
            box.addView(line);
        }
        TimePicker time=new TimePicker(this);time.setIs24HourView(true);
        time.setHour(prefs.scheduleHour());time.setMinute(prefs.scheduleMinute());box.addView(time);
        box.addView(Ui.text(this,"到点启用模式，不强行打开首页。锁屏时等解锁再弹窗。已在计时则保留原计时。\n退出模式不关闭规则；停用规则才取消下一次定时。",12,Ui.MUTED,false));
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("每周定时开启").setView(scroll)
          .setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            int mask=0;for(int i=0;i<7;i++)if(days[i].isChecked())mask|=1<<i;
            if(mask==0){Toast.makeText(this,"请至少选择一天",Toast.LENGTH_SHORT).show();return;}
            prefs.schedule(enabled.isChecked(),mask,time.getHour(),time.getMinute());
            ScheduleManager.arm(this,true);d.dismiss();render();
            if(enabled.isChecked())requestSchedulePermissions();
        }));d.show();
    }
    private void requestSchedulePermissions(){
        if(!ScheduleManager.exactAllowed(this)&&Build.VERSION.SDK_INT>=31){
            new AlertDialog.Builder(this).setTitle("允许定时开启")
              .setMessage("需要系统“闹钟和提醒”权限，才能按你设置的时间启用牛马模式。授权后返回软件。")
              .setNegativeButton("稍后",null).setPositiveButton("前往授权",(d,w)->
                 openSettings(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())))).show();
        }else if(!Settings.canDrawOverlays(this)){
            new AlertDialog.Builder(this).setTitle("还需要悬浮窗权限")
              .setMessage("规则已保存，授权后才能启用。请允许悬浮窗，然后返回软件。")
              .setNegativeButton("稍后",null).setPositiveButton("前往授权",(d,w)->
                 openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))).show();
        }else{
            ScheduleManager.arm(this,false);render();
            Toast.makeText(this,"请同时检查手机自启动和省电设置；定时状态可在首页查看",Toast.LENGTH_LONG).show();
        }
    }
    private void editPopupOptions(){
        String[] labels={"正计时","倒计时","直接息屏","退出牛马模式"};
        int[] bits={PopupOptions.UP,PopupOptions.DOWN,PopupOptions.SCREEN_OFF,PopupOptions.EXIT};
        int[] selected={prefs.popupMask()};
        boolean[] checked=new boolean[4];
        for(int i=0;i<4;i++)checked[i]=(selected[0]&bits[i])!=0;
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("显示哪些选项？（选择 2–4 个）")
            .setMultiChoiceItems(labels,checked,(d,which,on)->{
                if(on)selected[0]|=bits[which];else selected[0]&=~bits[which];
                ((AlertDialog)d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(PopupOptions.valid(selected[0]));
            }).setNegativeButton("取消",null)
            .setPositiveButton("保存",(d,w)->{
                if(!PopupOptions.valid(selected[0]))return;
                prefs.popupMask(selected[0]);render();
                Toast.makeText(this,"已保存，下次弹窗生效；仍可在软件或通知栏退出模式",Toast.LENGTH_LONG).show();
            }).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(PopupOptions.valid(selected[0])));
        dialog.show();
    }
    private void editMinutes(){
        EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(prefs.minutes()));input.selectAll();input.setHint("1–180 分钟");
        LinearLayout box=Ui.column(this);int p=Ui.dp(this,24);box.setPadding(p,0,p,0);box.addView(input);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("倒计时时长").setMessage("设置 1–180 分钟，下一次会话生效。")
            .setView(box).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            try{
                int n=Integer.parseInt(input.getText().toString().trim());
                if(n<1||n>180)throw new NumberFormatException();
                prefs.minutes(n);d.dismiss();render();
            }catch(NumberFormatException e){input.setError("请输入 1–180 的整数");}
        }));d.show();
    }
    private void startMode(){
        if(!Settings.canDrawOverlays(this)){
            new AlertDialog.Builder(this).setTitle("先允许悬浮窗")
                .setMessage("解锁选择框和悬浮时钟需要“显示在其他应用上层”权限。授权后返回这里，再点开启。")
                .setNegativeButton("稍后",null).setPositiveButton("前往设置",(d,w)->
                    openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))).show();
            return;
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        }
        try{startForegroundService(new Intent(this,ModeService.class).setAction(ModeService.START));}
        catch(RuntimeException e){new AlertDialog.Builder(this).setMessage("未能开启模式，请检查后台运行权限后重试。").setPositiveButton("知道了",null).show();}
    }
    private void explainAdmin(){
        new AlertDialog.Builder(this).setTitle("允许直接息屏与到时锁屏")
            .setMessage("此功能仅申请设备管理中的“锁定屏幕”能力，不申请清除数据。锁屏后可能需要密码解锁。你可在系统安全设置撤销授权；卸载前也可能需要先撤销。")
            .setNegativeButton("暂不授权",null).setPositiveButton("前往授权",(d,w)->{
                Intent i=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,new ComponentName(this,LockAdminReceiver.class));
                i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"仅用于你主动选择的直接息屏或倒计时到时锁屏。");
                openSettings(i);
            }).show();
    }
    private boolean batteryExempt(){
        return getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
    }
    private void showBackgroundHelp(){
        String device=(Build.BRAND+" "+Build.MANUFACTURER).toLowerCase(java.util.Locale.ROOT);
        String hint="请在系统设置中检查本应用的后台运行、电池优化及自启动选项（如有）。";
        if(device.contains("xiaomi")||device.contains("redmi")||device.contains("poco"))
            hint="小米 / Redmi / POCO：检查自启动、省电策略，以及系统提供的后台弹出界面权限。";
        else if(device.contains("vivo")||device.contains("iqoo"))
            hint="vivo / iQOO：检查后台耗电管理、自启动和悬浮窗选项（如有）。";
        else if(device.contains("oppo")||device.contains("realme")||device.contains("oneplus"))
            hint="OPPO / realme / 一加：检查应用电池管理中的后台活动、自启动及悬浮窗选项（如有）。";
        else if(device.contains("honor")||device.contains("huawei"))
            hint="华为 / 荣耀安卓兼容系统：检查应用启动管理中的自动启动、后台活动及悬浮窗选项（如有）。";
        else if(device.contains("samsung"))
            hint="三星：检查应用电池设置及休眠应用限制，避免运行中的计时被限制。";
        new AlertDialog.Builder(this).setTitle("后台运行设置")
            .setMessage(hint+"\n\n菜单名称随系统版本变化。系统电池优化显示已豁免，也不代表厂商的后台限制已解除。仅在计时停止或定时失败时按需调整。\n\n每周定时需要闹钟和悬浮窗权限。重启后恢复未来定时，错过的时间不补开。强行停止后需重新打开软件。\n\n退出模式仅结束本次使用；取消后续自动开启请关闭每周定时开关。")
            .setNegativeButton("知道了",null).setPositiveButton("打开应用信息",(d,w)->
                openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).show();
    }
    private void showDiagnostics(){
        String version="未知";
        try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException ignored){}
        String report="牛马模式 "+version+"\n设备："+Build.MANUFACTURER+" / "+Build.BRAND+" / "+Build.MODEL
            +"\nAndroid："+Build.VERSION.RELEASE+"（API "+Build.VERSION.SDK_INT+"）"
            +"\n悬浮窗："+(Settings.canDrawOverlays(this)?"已允许":"未允许")
            +"\n通知："+(getSystemService(NotificationManager.class).areNotificationsEnabled()?"已允许":"未允许")
            +"\n锁屏授权："+(adminActive()?"已授权":"未授权")
            +"\n精确闹钟："+(ScheduleManager.exactAllowed(this)?"可用":"未允许")
            +"\n系统电池优化："+(batteryExempt()?"已豁免":"未豁免")
            +"\n省电模式："+(getSystemService(PowerManager.class).isPowerSaveMode()?"已开启":"未开启")
            +"\n当前模式："+(ModeService.running?"运行中":"未开启")
            +"\n定时："+ScheduleManager.summary(this)
            +"\n最近定时记录："+(prefs.scheduleReport().isEmpty()?"无":prefs.scheduleReport())
            +"\n厂商自启动与后台限制：需手动检查";
        new AlertDialog.Builder(this).setTitle("设备与权限检查").setMessage(report)
            .setNegativeButton("关闭",null).setPositiveButton("复制信息",(d,w)->{
                getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("牛马模式检查",report));
                Toast.makeText(this,"已复制，可附上具体问题反馈",Toast.LENGTH_SHORT).show();
            }).show();
    }
    private boolean trySettings(Intent i){
        try{startActivity(i);return true;}
        catch(ActivityNotFoundException|SecurityException e){return false;}
    }
    private void openSettings(Intent i){
        if(trySettings(i))return;
        Toast.makeText(this,"此设置入口不可用，请在系统设置中手动查找",Toast.LENGTH_LONG).show();
        Intent details=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));
        if(!Settings.ACTION_APPLICATION_DETAILS_SETTINGS.equals(i.getAction())&&trySettings(details))return;
        trySettings(new Intent(Settings.ACTION_SETTINGS));
    }
}
