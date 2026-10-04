package com.wakka.bridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.HashMap;
import java.util.Map;

/** Shared full keypad fallback. Masks are supplied by the title; no platform constants here. */
public final class KeypadView extends View {
    public interface Sink { void set(int contact,int mask); void remove(int contact); }
    private final Sink sink;
    private final String[] labels;
    private final int[] masks;
    private final RectF[] cells=new RectF[20];
    private final Map<Integer,Integer> contacts=new HashMap<>();
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public KeypadView(Context c,String[] labels,int[] masks,Sink sink) {
        super(c); if(labels.length!=20||masks.length!=20) throw new IllegalArgumentException("20 keypad slots required");
        this.labels=labels.clone(); this.masks=masks.clone(); this.sink=sink;
        for(int i=0;i<cells.length;i++) cells[i]=new RectF();
        setContentDescription("Original keypad controls");
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        float gap=BridgeUi.dp(getContext(),5),margin=BridgeUi.dp(getContext(),8);
        float cw=(w-margin*2-gap*3)/4f,ch=(h-margin*2-gap*4)/5f;
        for(int i=0;i<20;i++) {
            float x=margin+(i%4)*(cw+gap),y=margin+(i/4)*(ch+gap); cells[i].set(x,y,x+cw,y+ch);
        }
    }
    @Override protected void onDraw(Canvas c) {
        c.drawColor(BridgeUi.BG); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        for(int i=0;i<20;i++) {
            if(labels[i].isEmpty()) continue;
            boolean down=contacts.containsValue(i); p.setStyle(Paint.Style.FILL); p.setColor(down?0xff245c65:BridgeUi.PANEL);
            c.drawRoundRect(cells[i],BridgeUi.dp(getContext(),10),BridgeUi.dp(getContext(),10),p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(BridgeUi.dp(getContext(),1)); p.setColor(down?BridgeUi.CYAN:0xff3b4659);
            c.drawRoundRect(cells[i],BridgeUi.dp(getContext(),10),BridgeUi.dp(getContext(),10),p);
            p.setStyle(Paint.Style.FILL); p.setColor(BridgeUi.INK); p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);
            c.drawText(labels[i],cells[i].centerX(),cells[i].centerY()-(p.ascent()+p.descent())/2,p);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        int action=e.getActionMasked(),index=e.getActionIndex(),id=e.getPointerId(index);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
            for(int i=0;i<cells.length;i++) if(masks[i]!=0&&cells[i].contains(e.getX(index),e.getY(index))) {
                contacts.put(id,i); sink.set(id+1,masks[i]); break;
            }
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) {
            contacts.remove(id); sink.remove(id+1); if(action==MotionEvent.ACTION_UP) performClick();
        } else if(action==MotionEvent.ACTION_CANCEL) cancelAll();
        invalidate(); return true;
    }
    public void cancelAll() { for(int id:contacts.keySet()) sink.remove(id+1); contacts.clear(); invalidate(); }
    @Override public boolean performClick() { super.performClick(); return true; }
}
