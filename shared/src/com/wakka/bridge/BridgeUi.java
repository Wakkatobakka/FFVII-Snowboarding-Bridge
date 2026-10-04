package com.wakka.bridge;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class BridgeUi {
    public static final String VERSION="0.1.2";
    public static final int BG=0xff080a10, PANEL=0xff111722, INK=0xfff0edf0,
            MUTED=0xffa9afbd, CYAN=0xff39e7e1;
    private BridgeUi() {}
    public static int dp(Context c,float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    public static TextView label(Context c,String value,float size,int color,boolean bold) {
        TextView v=new TextView(c); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        v.setLineSpacing(0,1.08f); return v;
    }
    public static GradientDrawable round(Context c,int fill,int stroke,int radius) {
        GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(c,radius));
        g.setStroke(dp(c,1),stroke); return g;
    }
    public static Button button(Context c,String text,boolean primary,int accent) {
        Button b=new Button(c); b.setText(text); b.setAllCaps(false); b.setTextSize(primary?17:12);
        b.setTextColor(INK); b.setTypeface(Typeface.DEFAULT_BOLD); b.setMinHeight(dp(c,48));
        b.setPadding(dp(c,8),0,dp(c,8),0);
        b.setBackground(round(c,primary?0xff123b42:0xff171c28,primary?accent:0xff3b4659,14));
        return b;
    }
    public static LinearLayout column(Context c) {
        LinearLayout v=new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    public static LinearLayout row(Context c) {
        LinearLayout v=new LinearLayout(c); v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    public static LinearLayout.LayoutParams lp(Context c,int h,int bottom) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,h<0?h:dp(c,h));
        p.bottomMargin=dp(c,bottom); return p;
    }
    public static FrameLayout safeRoot(Activity a) {
        a.getWindow().setStatusBarColor(BG); a.getWindow().setNavigationBarColor(BG);
        a.getWindow().getDecorView().setSystemUiVisibility(0);
        if(Build.VERSION.SDK_INT>=30) a.getWindow().setDecorFitsSystemWindows(false);
        FrameLayout root=new FrameLayout(a); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,in)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets i=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(i.left,i.top,i.right,i.bottom);
            } else {
                v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),
                        in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());
            }
            return in;
        });
        root.requestApplyInsets(); return root;
    }
    public static LinearLayout scrollColumn(Activity a,FrameLayout root) {
        ScrollView s=new ScrollView(a); s.setFillViewport(true);
        root.addView(s,new FrameLayout.LayoutParams(-1,-1)); LinearLayout c=column(a);
        c.setPadding(dp(a,20),dp(a,16),dp(a,20),dp(a,28)); s.addView(c); return c;
    }
    public static LinearLayout card(Context c) {
        LinearLayout v=column(c); v.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));
        v.setBackground(round(c,PANEL,0xff30394b,16)); return v;
    }
    public static TextView section(Context c,String text) {
        TextView t=label(c,text,11,CYAN,true); t.setLetterSpacing(.12f);
        t.setPadding(0,dp(c,16),0,dp(c,8)); return t;
    }
    public static View ornament(Context c,int accent) {
        return new View(c) {
            private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float y=getHeight()/2f,x=getWidth()/2f; p.setStrokeWidth(dp(c,1)); p.setColor(0xff30394b);
                canvas.drawLine(0,y,x-dp(c,14),y,p); canvas.drawLine(x+dp(c,14),y,getWidth(),y,p);
                p.setColor(accent); canvas.save(); canvas.rotate(45,x,y);
                canvas.drawRect(x-dp(c,3),y-dp(c,3),x+dp(c,3),y+dp(c,3),p); canvas.restore();
            }
        };
    }
}
