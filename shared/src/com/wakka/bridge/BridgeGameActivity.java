package com.wakka.bridge;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Dirge's toolbar / separate stage / status / controller structure, shared by all profiles. */
public abstract class BridgeGameActivity extends BridgeReportActivity {
    protected abstract Intent toolsIntent();
    private LinearLayout controls;
    private Button pause,keys;
    private TextView status;
    private boolean keypad;
    private boolean resumed;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable() { public void run() { refresh(); handler.postDelayed(this,500); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); if(state!=null) keypad=state.getBoolean("keypad");
        else backend().setUserPaused(false);
        GameSpec g=backend().spec(); FrameLayout safe=BridgeUi.safeRoot(this); LinearLayout root=BridgeUi.column(this);
        safe.addView(root,new FrameLayout.LayoutParams(-1,-1)); LinearLayout toolbar=BridgeUi.row(this);
        toolbar.setPadding(BridgeUi.dp(this,6),BridgeUi.dp(this,4),BridgeUi.dp(this,6),BridgeUi.dp(this,4));
        addToolbar(toolbar,"Menu",()->{backend().releaseInput();finish();});
        pause=addToolbar(toolbar,"Pause",()->{backend().releaseInput();backend().setUserPaused(!backend().userPaused());refresh();});
        keys=addToolbar(toolbar,"Keypad",()->{backend().releaseInput();keypad=!keypad;showControls();refresh();});
        addToolbar(toolbar,"Report",()->{backend().releaseInput();exportReport(0);});
        root.addView(toolbar,BridgeUi.lp(this,56,0));
        View stage=backend().attachDisplay(this); root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        status=BridgeUi.label(this,"Starting…",11,BridgeUi.MUTED,false);
        status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(BridgeUi.dp(this,12),0,BridgeUi.dp(this,12),0);
        status.setBackgroundColor(BridgeUi.PANEL); status.setMinHeight(BridgeUi.dp(this,44));
        status.setOnClickListener(v->{backend().releaseInput();startActivity(toolsIntent());});
        status.setContentDescription("Runtime status. Tap to open diagnostics.");
        root.addView(status,BridgeUi.lp(this,44,0)); controls=BridgeUi.column(this);
        root.addView(controls,BridgeUi.lp(this,-2,0)); showControls(); setContentView(safe); backend().start(); refresh();
    }
    private Button addToolbar(LinearLayout toolbar,String text,Runnable click) {
        Button b=BridgeUi.button(this,text,false,BridgeUi.CYAN); b.setTextSize(12); b.setOnClickListener(v->click.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1); p.setMargins(BridgeUi.dp(this,2),0,BridgeUi.dp(this,2),0);
        toolbar.addView(b,p); return b;
    }
    private void showControls() {
        controls.removeAllViews(); controls.addView(backend().createControls(this,keypad),new LinearLayout.LayoutParams(-1,BridgeUi.dp(this,244)));
    }
    private void refresh() {
        if(status==null) return;
        pause.setText(backend().userPaused()?"Resume":"Pause"); keys.setText(keypad?"Controls":"Keypad");
        status.setText(backend().status()+"  ·  tap for diagnostics");
    }
    @Override protected void onResume() {
        super.onResume(); resumed=true;backend().setForeground(true); handler.removeCallbacks(ticker); handler.post(ticker);
    }
    @Override protected void onPause() {
        resumed=false;backend().releaseInput(); backend().setForeground(false); handler.removeCallbacks(ticker); preserveSession(); super.onPause();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(!hasFocus) backend().releaseInput();
        backend().setForeground(resumed&&hasFocus);
    }
    @Override protected void onDestroy() { handler.removeCallbacks(ticker); backend().detachViews(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putBoolean("keypad",keypad); }
    @Override public void onBackPressed() { backend().releaseInput(); finish(); }
    @Override public boolean onKeyDown(int code,KeyEvent event) {
        if(code!=KeyEvent.KEYCODE_BACK&&backend().hardwareKey(code,true)) return true;
        return super.onKeyDown(code,event);
    }
    @Override public boolean onKeyUp(int code,KeyEvent event) {
        if(code!=KeyEvent.KEYCODE_BACK&&backend().hardwareKey(code,false)) return true;
        return super.onKeyUp(code,event);
    }
}
