package com.wakka.snowboardbridge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import com.wakka.bridge.SessionGate;

public final class RuntimeHost {
    public static Context context;
    public static volatile GameView view;
    public static volatile Runnable presentationListener;
    public static final SessionGate sessionGate = new SessionGate();
    public static volatile long presentedFrames;

    /** Game-owned draw/back buffer. DoJa draws here while Graphics is locked. */
    public static Bitmap framebuffer;
    public static android.graphics.Canvas canvas;

    /** Two presentation buffers keep the UI from ever sampling the bitmap being copied. */
    private static Bitmap displayA, displayB;
    private static android.graphics.Canvas displayCanvasA, displayCanvasB;
    public static volatile Bitmap displayBuffer;
    private static final Paint presentPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    public static volatile int keypadState;
    public static volatile boolean terminated;
    public static volatile String soft1Label = "L";
    public static volatile String soft2Label = "R";
    public static volatile String threeDStatus = "3D waiting";

    public static synchronized void init(Context c, GameView v) {
        context = c.getApplicationContext(); view = v;
        framebuffer = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        canvas = new android.graphics.Canvas(framebuffer);
        canvas.drawColor(android.graphics.Color.BLACK);

        displayA = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        displayB = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        displayCanvasA = new android.graphics.Canvas(displayA);
        displayCanvasB = new android.graphics.Canvas(displayB);
        displayCanvasA.drawColor(android.graphics.Color.BLACK);
        displayCanvasB.drawColor(android.graphics.Color.BLACK);
        displayBuffer = displayA;

        keypadState = 0; terminated = false; presentedFrames = 0;
        soft1Label = "L"; soft2Label = "R"; threeDStatus = "3D waiting";
    }

    /**
     * Publish one completed DoJa frame. The game continues drawing into framebuffer;
     * GameView only sees an atomically swapped copy after Graphics.unlock(true).
     */
    public static synchronized void presentFrame() {
        if (framebuffer == null || displayA == null || displayB == null) return;
        Bitmap target = (displayBuffer == displayA) ? displayB : displayA;
        android.graphics.Canvas targetCanvas = (target == displayA) ? displayCanvasA : displayCanvasB;
        targetCanvas.drawBitmap(framebuffer, 0, 0, presentPaint);
        displayBuffer = target;
        presentedFrames++;
        invalidate();
    }

    public static void invalidate() {
        GameView v=view; if (v != null) v.postInvalidate();
        Runnable listener=presentationListener; if(listener!=null) listener.run();
    }
    /** Lifecycle seam only; the renderer and game JAR remain unchanged. */
    public static int readKeypadState() { sessionGate.awaitRunning(); return keypadState; }
    public static void setSoftLabel(int which, String label) {
        String clean = (label == null || label.length() == 0) ? (which == 0 ? "L" : "R") : label;
        if (which == 0) soft1Label = clean;
        else if (which == 1) soft2Label = clean;
        invalidate();
    }
    public static InputStream openGameAsset(String name) throws IOException {
        String clean = name;
        while (clean.startsWith("/")) clean = clean.substring(1);
        return SnowboardPayload.openAsset(context, clean);
    }
    public static InputStream openTextureAsset(String name) throws IOException {
        try { return openGameAsset(name + ".png"); }
        catch (IOException ignored) { return openGameAsset(name); }
    }
    private static File scratchFile(int index) { return new File(context.getFilesDir(), "scratch-"+index+".bin"); }
    private static synchronized void ensureScratch() throws IOException {
        if (scratchFile(0).exists()) return;
        byte[] all;
        try (InputStream in = openGameAsset("game.sp"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n; while ((n=in.read(buf))!=-1) out.write(buf,0,n); all=out.toByteArray();
        }
        if (all.length < 64) throw new IOException("game.sp too small");
        ByteBuffer bb = ByteBuffer.wrap(all,0,16).order(ByteOrder.LITTLE_ENDIAN);
        int[] sizes = {bb.getInt(),bb.getInt(),bb.getInt(),bb.getInt()};
        int pos=64;
        for(int i=0;i<4;i++) {
            if (sizes[i] < 0 || pos + sizes[i] > all.length) throw new IOException("bad SP region "+i);
            try(FileOutputStream fos=new FileOutputStream(scratchFile(i))) { fos.write(all,pos,sizes[i]); }
            BridgeLog.i("SCRATCH INIT", i+" bytes="+sizes[i]);
            pos += sizes[i];
        }
    }
    public static synchronized byte[] readScratch(int index) throws IOException {
        ensureScratch(); File f=scratchFile(index);
        byte[] b=new byte[(int)f.length()]; try(FileInputStream in=new FileInputStream(f)) { int off=0,n; while(off<b.length && (n=in.read(b,off,b.length-off))>0) off+=n; }
        return b;
    }
    public static synchronized void writeScratch(int index, byte[] data) throws IOException {
        ensureScratch(); try(FileOutputStream out=new FileOutputStream(scratchFile(index))) { out.write(data); }
        BridgeLog.i("SCRATCH WRITE", index+" bytes="+data.length);
    }
}
