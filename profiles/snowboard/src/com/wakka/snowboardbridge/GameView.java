package com.wakka.snowboardbridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import com.wakka.bridge.BridgeUi;
import java.util.HashMap;
import java.util.Map;

/** Snowboarding's keypad-derived two-thumb control deck only. */
public final class GameView extends View {
    private final SnowboardBackend backend;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Integer,Integer> contacts=new HashMap<>();
    private final RectF edgeL=new RectF(),kick=new RectF(),edgeR=new RectF();
    private final RectF turnL=new RectF(),jump=new RectF(),turnR=new RectF();
    private final RectF softL=new RectF(),brake=new RectF(),softR=new RectF();
    private boolean racing;

    public GameView(Context c,SnowboardBackend backend) {
        super(c); this.backend=backend; p.setTypeface(Typeface.DEFAULT_BOLD);
        setContentDescription("Snowboard two-hand keypad controls: edge, turn, kick, jump, brake and soft keys");
    }

    /** Called by the unchanged presentation path through RuntimeHost.view. */
    public void onFrameReady() { postInvalidate(); backend.invalidateDisplay(); }

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        float sx=w/360f,sy=h/244f;
        set(edgeL,8,8,110,92,sx,sy);       set(kick,116,8,244,65,sx,sy);       set(edgeR,250,8,352,92,sx,sy);
        set(turnL,8,98,110,165,sx,sy);     set(jump,116,71,244,165,sx,sy);     set(turnR,250,98,352,165,sx,sy);
        set(softL,8,171,110,236,sx,sy);    set(brake,116,171,244,236,sx,sy);   set(softR,250,171,352,236,sx,sy);
    }

    private static void set(RectF r,float l,float t,float rr,float b,float sx,float sy) {
        r.set(l*sx,t*sy,rr*sx,b*sy);
    }

    @Override protected void onDraw(Canvas c) {
        c.drawColor(BridgeUi.BG);
        boolean mode=SnowboardInput.racing(RuntimeHost.soft1Label,RuntimeHost.soft2Label);
        if(mode!=racing) { racing=mode; cancelAll(); }
        if(racing) drawRace(c); else drawMenu(c);
    }

    private void drawRace(Canvas c) {
        button(c,edgeL,"◀ EDGE",SnowboardInput.CUSTOM_EDGE_L,true,13);
        button(c,kick,"KICK",SnowboardInput.CUSTOM_KICK,false,13);
        button(c,edgeR,"EDGE ▶",SnowboardInput.CUSTOM_EDGE_R,true,13);
        button(c,turnL,"◀ TURN",SnowboardInput.CUSTOM_TURN_L,false,12);
        button(c,jump,"JUMP",SnowboardInput.CUSTOM_JUMP,true,17);
        button(c,turnR,"TURN ▶",SnowboardInput.CUSTOM_TURN_R,false,12);
        button(c,softL,RuntimeHost.soft1Label,SnowboardInput.CUSTOM_SOFT1,false,11);
        button(c,brake,"BRAKE",SnowboardInput.CUSTOM_BRAKE,false,13);
        button(c,softR,RuntimeHost.soft2Label,SnowboardInput.CUSTOM_SOFT2,false,11);
    }

    private void drawMenu(Canvas c) {
        button(c,edgeL,"←",SnowboardInput.CUSTOM_EDGE_L,true,24);
        button(c,kick,"↑",SnowboardInput.CUSTOM_KICK,false,22);
        button(c,edgeR,"→",SnowboardInput.CUSTOM_EDGE_R,true,24);
        // Numeric 4/6 are race-only; keep their physical areas visually quiet in menus.
        button(c,jump,"OK",SnowboardInput.CUSTOM_JUMP,true,17);
        button(c,softL,RuntimeHost.soft1Label,SnowboardInput.CUSTOM_SOFT1,false,11);
        button(c,brake,"↓",SnowboardInput.CUSTOM_BRAKE,false,22);
        button(c,softR,RuntimeHost.soft2Label,SnowboardInput.CUSTOM_SOFT2,false,11);
    }

    private void button(Canvas c,RectF rect,String label,int slot,boolean primary,float textSize) {
        int mask=SnowboardInput.customMask(slot,racing);
        if(mask==0) return;
        boolean down=(RuntimeHost.keypadState&mask)!=0;
        p.setStyle(Paint.Style.FILL); p.setColor(down?0xff245c65:BridgeUi.PANEL);
        c.drawRoundRect(rect,BridgeUi.dp(getContext(),13),BridgeUi.dp(getContext(),13),p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(BridgeUi.dp(getContext(),primary?1.4f:1f));
        p.setColor(down||primary?BridgeUi.CYAN:0xff536675);
        c.drawRoundRect(rect,BridgeUi.dp(getContext(),13),BridgeUi.dp(getContext(),13),p);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(textSize*getResources().getDisplayMetrics().scaledDensity); p.setColor(BridgeUi.INK);
        c.drawText(label==null?"":label,rect.centerX(),rect.centerY()-(p.ascent()+p.descent())/2,p);
    }

    private int slotAt(float x,float y) {
        if(edgeL.contains(x,y)) return SnowboardInput.CUSTOM_EDGE_L;
        if(kick.contains(x,y)) return SnowboardInput.CUSTOM_KICK;
        if(edgeR.contains(x,y)) return SnowboardInput.CUSTOM_EDGE_R;
        if(turnL.contains(x,y)) return SnowboardInput.CUSTOM_TURN_L;
        if(jump.contains(x,y)) return SnowboardInput.CUSTOM_JUMP;
        if(turnR.contains(x,y)) return SnowboardInput.CUSTOM_TURN_R;
        if(softL.contains(x,y)) return SnowboardInput.CUSTOM_SOFT1;
        if(brake.contains(x,y)) return SnowboardInput.CUSTOM_BRAKE;
        if(softR.contains(x,y)) return SnowboardInput.CUSTOM_SOFT2;
        return -1;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(backend.blocked()) { cancelAll(); return true; }
        int action=e.getActionMasked(),index=e.getActionIndex(),id=e.getPointerId(index);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
            int slot=slotAt(e.getX(index),e.getY(index));
            int mask=SnowboardInput.customMask(slot,racing);
            if(mask!=0) { contacts.put(id,slot); backend.setInput(id+1,mask); }
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) {
            if(contacts.remove(id)!=null) backend.removeInput(id+1);
            if(action==MotionEvent.ACTION_UP) performClick();
        } else if(action==MotionEvent.ACTION_CANCEL) cancelAll();
        invalidate(); return true;
    }

    public void cancelAll() {
        for(int id:contacts.keySet()) backend.removeInput(id+1);
        contacts.clear(); invalidate();
    }

    @Override public boolean performClick() { super.performClick(); return true; }
}
