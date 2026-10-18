package cn.niuma.app;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

final class Ui {
    static final int BG=Color.rgb(245,244,238), INK=Color.rgb(28,32,26), MUTED=Color.rgb(108,114,102),
        LIME=Color.rgb(194,244,92), LINE=Color.rgb(222,224,213);
    static int dp(Context c,float v){return (int)(v*c.getResources().getDisplayMetrics().density+0.5f);}
    static GradientDrawable bg(Context c,int color,float radius){
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    static TextView text(Context c,String value,int size,int color,boolean bold){
        TextView t=new TextView(c); t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setFontFeatureSettings("tnum"); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static void space(LinearLayout l,int height){Space s=new Space(l.getContext());l.addView(s,new LinearLayout.LayoutParams(1,dp(l.getContext(),height)));}
    static TextView button(Context c,String label,boolean primary){
        TextView t=text(c,label,16,INK,true); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(c,54));
        int p=dp(c,14);t.setPadding(p,p,p,p);
        GradientDrawable d=bg(c,primary?LIME:Color.WHITE,16);
        if(!primary)d.setStroke(dp(c,1),LINE);t.setBackground(d);
        t.setClickable(true);t.setFocusable(true);return t;
    }
    static LinearLayout card(Context c){
        LinearLayout l=column(c); int p=dp(c,20);l.setPadding(p,p,p,p);l.setBackground(bg(c,Color.WHITE,22));return l;
    }
}
