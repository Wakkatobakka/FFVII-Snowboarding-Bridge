package com.wakka.snowboardbridge;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ReportWriter {
    private ReportWriter() {}

    public static String buildReport() {
        StringBuilder s = new StringBuilder(65536);
        s.append("SNOWBOARD BRIDGE v"+BridgeVersion.NAME+" — DEVICE REPORT\n");
        s.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append('\n');
        s.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        s.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        s.append("ABI: ");
        if (Build.SUPPORTED_ABIS != null) {
            for (int i=0;i<Build.SUPPORTED_ABIS.length;i++) { if(i>0)s.append(", "); s.append(Build.SUPPORTED_ABIS[i]); }
        }
        s.append("\nFramebuffer: 240x240 ARGB_8888\n");
        s.append("Keypad state: 0x").append(Integer.toHexString(RuntimeHost.keypadState)).append('\n');
        s.append("Soft labels: ").append(RuntimeHost.soft1Label).append(" / ").append(RuntimeHost.soft2Label).append('\n');
        s.append("Current 3D: ").append(RuntimeHost.threeDStatus).append("\n\n");
        s.append("=== SESSION LOG ===\n");
        s.append(BridgeLog.all());
        return s.toString();
    }

    public static Uri saveToDownloads(Context context) throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String name = "Snowboard-Bridge-v" + BridgeVersion.NAME + "-report-" + stamp + ".txt";
        ContentResolver cr = context.getContentResolver();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SnowboardBridge");
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new java.io.IOException("MediaStore insert returned null");
            try (OutputStream out = cr.openOutputStream(uri, "w")) {
                if (out == null) throw new java.io.IOException("MediaStore output stream was null");
                out.write(buildReport().getBytes(StandardCharsets.UTF_8));
            }
            ContentValues done = new ContentValues(); done.put(MediaStore.Downloads.IS_PENDING, 0); cr.update(uri, done, null, null);
            return uri;
        }
        throw new java.io.IOException("Direct Downloads report requires Android 10+");
    }
}
