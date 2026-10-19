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
    @Override public void onResume(){super.onResume();render();handler.removeCallbacks(refresh);handler.post(refresh);
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
        page.addView(state);Ui.space(page,16);

        LinearLayout duration=Ui.card(this);
        duration.addView(Ui.text(this,"倒计时时长",14,Ui.MUTED,false));Ui.space(duration,10);
        TextView minutes=Ui.text(this,prefs.minutes()+" 分钟    ›",28,Ui.INK,true);
        duration.addView(minutes);Ui.space(duration,8);
        duration.addView(Ui.text(this,"点击修改 · 下一次倒计时生效",12,Ui.MUTED,false));
        duration.setOnClickListener(v->editMinutes());duration.setContentDescription("修改倒计时时长，当前"+prefs.minutes()+"分钟");
        page.addView(duration);Ui.space(page,16);

        LinearLayout actions=Ui.card(this);actions.addView(Ui.text(this,"到时动作",15,Ui.INK,true));Ui.space(actions,12);
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
        actions.addView(choices);Ui.space(actions,12);
        actions.addView(Ui.text(this,prefs.action()==Prefs.LOCK?
            (adminActive()?"已授权锁屏 · 到时会锁定设备":"自动锁屏需要单独授权；未授权时改为提醒"):
            "修改仅影响下一次会话",12,Ui.MUTED,false));
        page.addView(actions);Ui.space(page,22);

        LinearLayout popup=Ui.card(this);
        popup.addView(Ui.text(this,"解锁弹窗选项",17,Ui.INK,true));Ui.space(popup,8);
        popup.addView(Ui.text(this,"已显示 "+Integer.bitCount(prefs.popupMask())+" 个选项  ›",22,Ui.INK,true));Ui.space(popup,8);
        popup.addView(Ui.text(this,"正计时 / 倒计时 / 直接息屏 / 退出\n点击选择任意 2–4 个，下次弹窗生效",12,Ui.MUTED,false));
        popup.setOnClickListener(v->editPopupOptions());page.addView(popup);Ui.space(page,22);
        toggle=Ui.button(this,"",true);page.addView(toggle);
        toggle.setOnClickListener(v->{
            if(ModeService.running){
                stopService(new Intent(this,ModeService.class));handler.postDelayed(this::updateStatus,150);
            }else startMode();
        });
        Ui.space(page,10);
        page.addView(Ui.text(this,"返回桌面后继续运行；退出模式后停止所有计时。",12,Ui.MUTED,false));
        Ui.space(page,24);
        page.addView(Ui.text(this,"权限与小米设置",18,Ui.INK,true));Ui.space(page,10);
        addSetting(page,"悬浮窗",Settings.canDrawOverlays(this)?"已允许":"去开启",()->{
            openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
        });
        boolean notifications=getSystemService(NotificationManager.class).areNotificationsEnabled();
        addSetting(page,"通知与退出入口",notifications?"已允许":"去开启",()->{
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
            }else openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        addSetting(page,"息屏 / 锁屏授权",adminActive()?"已授权 / 管理":"可选",()->{
            if(adminActive())openSettings(new Intent(Settings.ACTION_SECURITY_SETTINGS));else explainAdmin();
        });
        addSetting(page,"后台与弹窗权限","查看说明",this::showXiaomiHelp);
        Ui.space(page,14);
        page.addView(Ui.text(this,"正计时圆圈在 10 分钟达到最大，之后继续计时。\n锁屏结束本次计时，下次解锁重新选择。\n\n个人版 0.1.2 · 设置仅保存在本机",12,Ui.MUTED,false));
        updateStatus();
    }
    private void addSetting(LinearLayout parent,String title,String detail,Runnable action){
        TextView row=Ui.text(this,title+"    "+detail+"  ›",14,Ui.INK,false);
        int p=Ui.dp(this,14);row.setPadding(p,p,p,p);row.setBackground(Ui.bg(this,Color.WHITE,12));
        row.setOnClickListener(v->action.run());parent.addView(row);Ui.space(parent,6);
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
    private void showXiaomiHelp(){
        new AlertDialog.Builder(this).setTitle("HyperOS 使用提示")
            .setMessage("在应用信息中允许悬浮窗，以及系统提供的后台弹出界面权限。\n\n如模式经常停止，可将省电策略改为“不限制”，并在最近任务中锁定应用。不同系统版本的名称可能不同。\n\n本应用不随开机自动启动；进程被清理后，请重新打开应用开启模式。\n\n如果解锁未弹窗，先检查这些权限。")
            .setNegativeButton("知道了",null).setPositiveButton("打开应用信息",(d,w)->
                openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).show();
    }
    private void openSettings(Intent i){
        try{startActivity(i);}catch(ActivityNotFoundException e){Toast.makeText(this,"此页面不可用，请在系统设置中搜索对应权限",Toast.LENGTH_LONG).show();}
    }
}
