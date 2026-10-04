package com.wakka.snowboardbridge;

import android.app.Activity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import com.wakka.bridge.BridgeBackend;
import com.wakka.bridge.GameSpec;
import com.wakka.bridge.InputLatch;
import com.wakka.bridge.KeypadView;
import java.lang.reflect.Method;
import java.util.UUID;

/** Snowboarding adapter; the shared shell never imports DoJa or this class. */
public final class SnowboardBackend implements BridgeBackend {
    private static final GameSpec SPEC=new GameSpec("ffvii-snowboard-doja",
            "FFVII Snowboarding","Wakkan restoration host · English game data",
            "Snowboard Bridge 0.0.23","DoJa / graphics3d","HIT THE SLOPES",
            null,null,
            "RACE\nCustom controls use the original digital inputs in a two-thumb layout.\nLeft column: EDGE LEFT / TURN LEFT.\nRight column: EDGE RIGHT / TURN RIGHT.\nCenter: KICK / JUMP / BRAKE.\nRetry/Menu: original soft keys.\nEach touch stays with the button it started on until release.\n\nMENUS\nEDGE/KICK/JUMP/BRAKE/EDGE become LEFT/UP/OK/DOWN/RIGHT. The race-only TURN buttons are inactive.\n\nBRIDGE\nMenu returns to the bridge and keeps the session. Pause freezes at the next input poll. Keypad switches to the full original keypad. Report captures the existing session.\n\nAndroid Back returns to the bridge; the game's own Menu soft key remains available.",
            "Audio playback is phone-verified. Full lifecycle/report-export stress remains a separate validation item.",0xff52e0e8);
    private final Context app;
    private final InputLatch input=new InputLatch();
    private volatile SnowboardDisplayView display;
    private volatile GameView controls;
    private volatile KeypadView keypad;
    private volatile boolean started;
    private volatile String sessionId="not-started";
    private long transitions,presses,releases;
    private int lastMask,lastPressMask,lastReleaseMask;
    public SnowboardBackend(Context c) { app=c.getApplicationContext(); }
    @Override public GameSpec spec() { return SPEC; }
    @Override public boolean ready() { return SnowboardPayload.ready(app); }
    @Override public String readiness() { return SnowboardPayload.readiness(app); }
    @Override public boolean supportsPayloadImport() { return true; }
    @Override public String payloadImportLabel() { return ready()?"REIMPORT GAME DATA":"IMPORT GAME DATA"; }
    @Override public String importPayload(java.io.InputStream in) throws Exception { return SnowboardPayload.importZip(app,in); }
    @Override public View attachDisplay(Activity activity) {
        display=new SnowboardDisplayView(activity);
        RuntimeHost.presentationListener=this::invalidateDisplay;
        return display;
    }
    @Override public View createControls(Activity activity,boolean raw) {
        releaseInput(); controls=null; keypad=null;
        if(raw) {
            String[] labels={"Soft 1","↑","OK","Soft 2","←","↓","→","0","1","2","3","*","4","5","6","#","7","8","9",""};
            int[] masks={SnowboardInput.SOFT1,SnowboardInput.UP,SnowboardInput.SELECT,SnowboardInput.SOFT2,
                    SnowboardInput.LEFT,SnowboardInput.DOWN,SnowboardInput.RIGHT,1,
                    1<<1,1<<2,1<<3,1<<10,1<<4,1<<5,1<<6,1<<11,1<<7,1<<8,1<<9,0};
            keypad=new KeypadView(activity,labels,masks,new KeypadView.Sink() {
                public void set(int contact,int mask) { setInput(contact,mask); }
                public void remove(int contact) { removeInput(contact); }
            });
            RuntimeHost.view=null; return keypad;
        }
        controls=new GameView(activity,this); RuntimeHost.view=controls; return controls;
    }
    @Override public void detachViews() { releaseInput(); display=null;controls=null;keypad=null;RuntimeHost.view=null;RuntimeHost.presentationListener=null; }
    @Override public synchronized void start() {
        if(started&&!RuntimeHost.terminated) return;
        if(started) try { com.wakka.bridge.ReportStore.capture(app,com.wakka.bridge.ReportStore.compose(app,this)); } catch(Exception ignored) { }
        BridgeLog.reset(); RuntimeHost.init(app,controls); input.clear();
        transitions=presses=releases=0;lastMask=lastPressMask=lastReleaseMask=0;sessionId=UUID.randomUUID().toString();started=true;
        BridgeLog.i("BOOT","FFVII Snowboarding Bridge 0.1.2; baseline Snowboard 0.0.23; full 240x240 completed-frame presentation");
        Thread thread=new Thread(()->{
            try {
                BridgeLog.i("DEX","Loading locally imported / verified SnowBoard game.dex");
                Class<?> c=SnowboardPayload.gameClass(app);Object game=c.getDeclaredConstructor().newInstance();
                Method m=c.getMethod("start");BridgeLog.i("GAME CODE","enter SnowBoard.start");
                m.invoke(game);BridgeLog.i("GAME CODE","SnowBoard.start returned");
            } catch(Throwable t) { BridgeLog.e("GAME CRASH",t); }
            finally { RuntimeHost.terminated=true;invalidateDisplay(); }
        },"FF7Snowboard"); thread.start();
    }
    @Override public boolean started() { return started; }
    @Override public boolean running() { return started&&!RuntimeHost.terminated; }
    public boolean blocked() { return !running()||RuntimeHost.sessionGate.blocked(); }
    @Override public void setForeground(boolean value) {
        if(!value) releaseInput(); RuntimeHost.sessionGate.foreground(value);
        if(started) BridgeLog.i("LIFECYCLE",value?"foreground":"background / paused at input boundary");
    }
    @Override public void setUserPaused(boolean value) {
        releaseInput();RuntimeHost.sessionGate.userPaused(value);
        if(started) BridgeLog.i("PAUSE",value?"user pause":"user resume");
    }
    @Override public boolean userPaused() { return RuntimeHost.sessionGate.isUserPaused(); }
    public synchronized void setInput(int contact,int mask) {
        if(blocked()) return;publish(input.set(contact,mask));
    }
    public synchronized void removeInput(int contact) { publish(input.remove(contact)); }
    private void publish(int mask) {
        int previous=RuntimeHost.keypadState;if(mask==previous) return;
        transitions++;presses+=Integer.bitCount(mask&~previous);releases+=Integer.bitCount(previous&~mask);
        if((mask&~previous)!=0) lastPressMask=mask&~previous;
        if((previous&~mask)!=0) lastReleaseMask=previous&~mask;
        lastMask=mask;RuntimeHost.keypadState=mask;
        BridgeLog.i("KEYPAD",SnowboardInput.names(mask)+" mask=0x"+Integer.toHexString(mask)+" previous=0x"+Integer.toHexString(previous));
        if(controls!=null) controls.postInvalidate();
    }
    @Override public synchronized void releaseInput() {
        if(controls!=null) controls.cancelAll(); if(keypad!=null) keypad.cancelAll();publish(input.clear());
    }
    public void invalidateDisplay() { SnowboardDisplayView d=display;if(d!=null)d.postInvalidate(); }
    @Override public boolean hardwareKey(int code,boolean down) {
        int mask=0; boolean race=SnowboardInput.racing(RuntimeHost.soft1Label,RuntimeHost.soft2Label);
        switch(code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: mask=race?SnowboardInput.KEY_4:SnowboardInput.LEFT;break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: mask=race?SnowboardInput.KEY_6:SnowboardInput.RIGHT;break;
            case KeyEvent.KEYCODE_DPAD_UP:mask=SnowboardInput.UP;break;
            case KeyEvent.KEYCODE_DPAD_DOWN:mask=SnowboardInput.DOWN;break;
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_BUTTON_A:mask=SnowboardInput.SELECT;break;
            case KeyEvent.KEYCODE_BUTTON_L1:mask=SnowboardInput.LEFT;break;
            case KeyEvent.KEYCODE_BUTTON_R1:mask=SnowboardInput.RIGHT;break;
            case KeyEvent.KEYCODE_BUTTON_X:mask=SnowboardInput.SOFT1;break;
            case KeyEvent.KEYCODE_BUTTON_Y:mask=SnowboardInput.SOFT2;break;
            case KeyEvent.KEYCODE_STAR:mask=1<<10;break;
            case KeyEvent.KEYCODE_POUND:mask=1<<11;break;
            default:if(code>=KeyEvent.KEYCODE_0&&code<=KeyEvent.KEYCODE_9) mask=1<<(code-KeyEvent.KEYCODE_0);
        }
        if(mask==0) return false;int contact=-1000-code;
        if(down) setInput(contact,mask);else removeInput(contact);return true;
    }
    @Override public String status() {
        if(BridgeLog.hasCrash()) return "Runtime error · diagnostics retained";
        if(!running()) return started?"Session ended":"Starting";
        if(userPaused()) return "Paused";
        return (SnowboardInput.racing(RuntimeHost.soft1Label,RuntimeHost.soft2Label)?"Race":"Menu")
                +" · "+RuntimeHost.soft1Label+" / "+RuntimeHost.soft2Label;
    }
    @Override public synchronized String snapshot() {
        String summary="Session: "+(!started?"NOT STARTED":RuntimeHost.terminated?"ENDED":RuntimeHost.sessionGate.blocked()?"LIVE / PAUSED":"LIVE / RUNNING")
                +"\nSession ID: "+sessionId+"\nForeground: "+RuntimeHost.sessionGate.isForeground()
                +"\nUser pause: "+userPaused()+"\nFrames presented: "+RuntimeHost.presentedFrames
                +"\nInput transitions: "+transitions+" (press "+presses+" / release "+releases+")"
                +"\nHeld mask: 0x"+Integer.toHexString(RuntimeHost.keypadState)
                +"\nLast mask: 0x"+Integer.toHexString(lastMask)
                +"\nLast key press: "+SnowboardInput.names(lastPressMask)+" / 0x"+Integer.toHexString(lastPressMask)
                +"\nLast key release: "+SnowboardInput.names(lastReleaseMask)+" / 0x"+Integer.toHexString(lastReleaseMask)
                +"\nTouch/hardware contacts: "+input.contacts()+"\nAudio: MLD -> MIDI PLAYBACK ACTIVE"
                +"\nLog events: "+BridgeLog.eventCount()+" / retained "+BridgeLog.retainedCount()
                +" / errors "+BridgeLog.errorCount()+"\nGame JAR SHA256: "+SnowboardProvenance.GAME_JAR_SHA256+"\n\n";
        return summary+(started?ReportWriter.buildReport():"No game has been launched in this process. Reporting did not start it.\n");
    }
}
