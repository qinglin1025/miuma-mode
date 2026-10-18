package cn.niuma.app;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;

final class ClockView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private long millis;
    private float growth, fraction;
    private boolean countdown;
    ClockView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription("悬浮计时，可拖动；点按查看操作");}
    void update(long ms,float g,boolean down,float remaining){
        millis=ms;growth=g;countdown=down;fraction=remaining;invalidate();
    }
    static String format(long ms,boolean roundUp){
        long seconds=roundUp?(ms+999)/1000:ms/1000;
        return String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float size=Math.min(getWidth(),getHeight()),r=size/2f-Ui.dp(getContext(),3),cx=size/2f,cy=size/2f;
        int color=countdown?Ui.LIME:growth<0.5f?Ui.LIME:growth<1f?Color.rgb(255,182,70):Color.rgb(247,104,87);
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setShadowLayer(3,0,2,0x22000000);
        c.drawCircle(cx,cy,r,p);p.clearShadowLayer();
        if(countdown){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),2.5f));p.setColor(0x33709B23);
            float inset=Ui.dp(getContext(),8);RectF arc=new RectF(inset,inset,size-inset,size-inset);
            c.drawOval(arc,p);p.setColor(0xFF658E20);p.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(arc,-90,360*fraction,false,p);p.setStyle(Paint.Style.FILL);
        }
        p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(size*0.22f);
        p.setColor(!countdown&&growth>=1?Color.WHITE:Ui.INK);
        c.drawText(format(millis,countdown),cx,cy+size*0.025f,p);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(size*0.11f);
        c.drawText(countdown?"剩余时间":"已使用",cx,cy+size*0.20f,p);
    }
    @Override public boolean performClick(){return super.performClick();}
}
