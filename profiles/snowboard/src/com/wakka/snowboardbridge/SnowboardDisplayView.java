package com.wakka.snowboardbridge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import com.wakka.bridge.BridgeUi;

/** Fits the complete baseline 240x240 display; no cropping or renderer changes. */
public final class SnowboardDisplayView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF rect=new RectF();
    public SnowboardDisplayView(Context c) { super(c); setContentDescription("Snowboarding game screen"); }
    @Override protected void onDraw(Canvas c) {
        c.drawColor(BridgeUi.BG); float side=Math.min(getWidth(),getHeight());
        float x=(getWidth()-side)/2f,y=(getHeight()-side)/2f; rect.set(x,y,x+side,y+side);
        Bitmap b=RuntimeHost.displayBuffer; if(b!=null) c.drawBitmap(b,null,rect,paint);
    }
}
