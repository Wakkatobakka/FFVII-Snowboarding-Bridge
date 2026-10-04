package com.wakka.bridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import android.net.Uri;
import android.widget.Toast;

/** Shared Dirge-derived player-facing launcher. Title art is profile data. */
public abstract class BridgeLauncherActivity extends Activity {
    private static final int REQUEST_IMPORT_PAYLOAD=0x5342;
    protected abstract BridgeBackend backend();
    protected abstract Intent gameIntent();
    protected abstract Intent toolsIntent();
    private Button play;
    @Override public void onCreate(Bundle b) { super.onCreate(b); showHome(); }
    @Override protected void onResume() {
        super.onResume(); if(play!=null) play.setText(backend().running()?"RESUME GAME":backend().spec().playLabel);
        if(play!=null) play.setEnabled(backend().ready());
    }
    private void showHome() {
        GameSpec spec=backend().spec(); FrameLayout frame=BridgeUi.safeRoot(this);
        LinearLayout root=BridgeUi.scrollColumn(this,frame),header=BridgeUi.row(this);
        TextView brand=BridgeUi.label(this,"◆  WAKKAN // BRIDGEKEEPER",11,BridgeUi.CYAN,true);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(BridgeUi.label(this,BridgeUi.VERSION,11,BridgeUi.MUTED,true));
        root.addView(header,BridgeUi.lp(this,-2,14));
        FrameLayout hero=new FrameLayout(this); hero.setBackground(BridgeUi.round(this,BridgeUi.PANEL,spec.accent,22));
        hero.setClipToOutline(true);
        addArt(hero,spec.backdropAsset,true); View shade=new View(this); shade.setBackgroundColor(0x60080a10);
        hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        if(spec.logoAsset!=null) addArt(hero,spec.logoAsset,false);
        else { TextView title=BridgeUi.label(this,spec.title,32,BridgeUi.INK,true);
            title.setTypeface(Typeface.create("serif",Typeface.BOLD)); title.setGravity(Gravity.CENTER);
            hero.addView(title,new FrameLayout.LayoutParams(-1,-1)); }
        TextView caption=BridgeUi.label(this,"ANDROID BRIDGE // WAKKAN",10,BridgeUi.CYAN,true);
        caption.setGravity(Gravity.CENTER); FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,BridgeUi.dp(this,32),Gravity.BOTTOM);
        hero.addView(caption,cp); root.addView(hero,BridgeUi.lp(this,190,12));
        TextView sub=BridgeUi.label(this,spec.subtitle,13,BridgeUi.MUTED,false); sub.setGravity(Gravity.CENTER);
        root.addView(sub,BridgeUi.lp(this,-2,12)); root.addView(BridgeUi.ornament(this,spec.accent),BridgeUi.lp(this,18,12));
        LinearLayout card=BridgeUi.card(this);
        card.addView(BridgeUi.label(this,"RECOVERED SIGNAL",10,BridgeUi.CYAN,true));
        TextView ready=BridgeUi.label(this,backend().readiness(),16,BridgeUi.INK,true);
        ready.setPadding(0,BridgeUi.dp(this,6),0,BridgeUi.dp(this,4)); card.addView(ready);
        card.addView(BridgeUi.label(this,"Offline play · modern touch controls",12,BridgeUi.MUTED,false));
        root.addView(card,BridgeUi.lp(this,-2,12));
        play=BridgeUi.button(this,spec.playLabel,true,spec.accent);
        play.setEnabled(backend().ready()); play.setOnClickListener(v->{if(backend().ready())startActivity(gameIntent());}); root.addView(play,BridgeUi.lp(this,68,12));
        if(backend().supportsPayloadImport()) {
            Button importData=BridgeUi.button(this,backend().payloadImportLabel(),false,spec.accent);
            importData.setOnClickListener(v->choosePayload());
            root.addView(importData,BridgeUi.lp(this,56,10));
        }
        LinearLayout actions=BridgeUi.row(this);
        Button controls=BridgeUi.button(this,"CONTROLS",false,spec.accent);
        controls.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(spec.title+" controls")
                .setMessage(spec.controlsHelp).setPositiveButton("Close",null).show());
        actions.addView(controls,new LinearLayout.LayoutParams(0,BridgeUi.dp(this,56),1));
        View gap=new View(this); actions.addView(gap,new LinearLayout.LayoutParams(BridgeUi.dp(this,8),1));
        Button tools=BridgeUi.button(this,"TOOLS / DIAGNOSTICS",false,spec.accent);
        tools.setTextColor(0xffbff9f5); tools.setOnClickListener(v->startActivity(toolsIntent()));
        actions.addView(tools,new LinearLayout.LayoutParams(0,BridgeUi.dp(this,56),1));
        root.addView(actions,BridgeUi.lp(this,-2,18));
        TextView signal=BridgeUi.label(this,"◈  CYAN WAKKAN LINK STABLE  ◈",10,BridgeUi.CYAN,true);
        signal.setGravity(Gravity.CENTER); root.addView(signal,BridgeUi.lp(this,-2,20));
        TextView footer=BridgeUi.label(this,spec.title+" Bridge · WAKKA",11,BridgeUi.MUTED,false);
        footer.setGravity(Gravity.CENTER); root.addView(footer); setContentView(frame);
    }

    private void choosePayload() {
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("application/zip");
        startActivityForResult(pick,REQUEST_IMPORT_PAYLOAD);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_IMPORT_PAYLOAD||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new java.io.IOException("Selected file could not be opened");
                final String result=backend().importPayload(in);
                runOnUiThread(()->{new AlertDialog.Builder(this).setTitle("Import complete").setMessage(result).setPositiveButton("OK",null).show();showHome();});
            } catch(final Exception e) {
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Import failed").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("OK",null).show());
            }
        },"SnowboardPayloadImport").start();
    }

    private void addArt(FrameLayout hero,String path,boolean backdrop) {
        if(path==null) return;
        try(InputStream in=getAssets().open(path)) {
            ImageView image=new ImageView(this); image.setImageBitmap(BitmapFactory.decodeStream(in));
            image.setScaleType(backdrop?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);
            if(!backdrop) image.setPadding(BridgeUi.dp(this,16),BridgeUi.dp(this,20),BridgeUi.dp(this,16),BridgeUi.dp(this,44));
            hero.addView(image,new FrameLayout.LayoutParams(-1,-1));
        } catch(Exception ignored) { }
    }
}
