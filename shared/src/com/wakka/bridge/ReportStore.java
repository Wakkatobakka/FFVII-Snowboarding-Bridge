package com.wakka.bridge;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Durable snapshots, including after process death. Never owns or resets a runtime. */
public final class ReportStore {
    private ReportStore() {}
    public static String compose(Context c,BridgeBackend backend) {
        GameSpec g=backend.spec();
        return "FFVII SNOWBOARDING BRIDGE v"+BridgeUi.VERSION+"\nDEVICE RUN REPORT\n"
                +"Captured: "+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(new Date())+"\n"
                +"Package: "+c.getPackageName()+"\n"
                +"Title: "+g.title+"\nProfile: "+g.id+"\n"
                +"Backend: "+g.family+" / "+g.baseline+"\n"
                +"Build input ID: "+buildInputId(c)+"\n"
                +"Device: "+Build.MANUFACTURER+" "+Build.MODEL+"\n"
                +"Android: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\n"
                +"ABIs: "+Arrays.toString(Build.SUPPORTED_ABIS)+"\n"
                +"Known issues: "+g.knownIssues+"\n\n"+backend.snapshot();
    }
    private static String buildInputId(Context c) {
        try(java.io.InputStream in=c.getAssets().open("bridge-build.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
            return new org.json.JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).getString("build_input_id");
        } catch(Exception e) { return "unavailable"; }
    }
    public static synchronized File capture(Context c,String report) throws IOException {
        File dir=new File(c.getFilesDir(),"reports");
        if(!dir.isDirectory()&&!dir.mkdirs()) throw new IOException("Cannot create report folder");
        String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date());
        File out=new File(dir,"FFVII-Snowboarding-Bridge-"+BridgeUi.VERSION+"-report-"+stamp+"-"+UUID.randomUUID().toString().substring(0,6)+".txt");
        byte[] data=report.getBytes(StandardCharsets.UTF_8); write(out,data);
        preserveLatest(c,report);
        return out;
    }
    public static synchronized void preserveLatest(Context c,String report) throws IOException {
        File dir=new File(c.getFilesDir(),"reports");
        if(!dir.isDirectory()&&!dir.mkdirs()) throw new IOException("Cannot create report folder");
        File temp=new File(dir,"latest-report.tmp"),latest=new File(dir,"latest-report.txt");
        write(temp,report.getBytes(StandardCharsets.UTF_8));
        if(!temp.renameTo(latest)) throw new IOException("Cannot replace latest report");
    }
    private static void write(File f,byte[] bytes) throws IOException {
        try(FileOutputStream out=new FileOutputStream(f)) { out.write(bytes); out.getFD().sync(); }
    }
    public static File resolve(Context c,String name) throws IOException {
        if(name==null||!name.matches("[A-Za-z0-9._-]+\\.txt")) throw new IOException("Invalid report name");
        File f=new File(new File(c.getFilesDir(),"reports"),name);
        if(!f.isFile()) throw new IOException("Report no longer available"); return f;
    }
    public static String latest(Context c) {
        try { return read(resolve(c,"latest-report.txt")); }
        catch(IOException e) { return "No saved runtime report yet. Play the game or capture a report.\n"; }
    }
    public static String read(File f) throws IOException {
        try(FileInputStream in=new FileInputStream(f);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n);
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    public static void copy(File source,OutputStream out) throws IOException {
        try(FileInputStream in=new FileInputStream(source)) { byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n); }
    }
    public static Uri downloads(Context c,File report) throws IOException {
        if(Build.VERSION.SDK_INT<29) throw new IOException("Use Save as on this Android version");
        ContentValues v=new ContentValues(); v.put(MediaStore.Downloads.DISPLAY_NAME,report.getName());
        v.put(MediaStore.Downloads.MIME_TYPE,"text/plain");
        v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/FFVII_Snowboarding_Bridge");
        v.put(MediaStore.Downloads.IS_PENDING,1);
        Uri uri=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
        if(uri==null) throw new IOException("Downloads is unavailable");
        try {
            try(OutputStream out=c.getContentResolver().openOutputStream(uri,"w")) {
                if(out==null) throw new IOException("Cannot write Downloads report"); copy(report,out);
            }
            ContentValues done=new ContentValues(); done.put(MediaStore.Downloads.IS_PENDING,0);
            c.getContentResolver().update(uri,done,null,null); return uri;
        } catch(Exception e) { c.getContentResolver().delete(uri,null,null); throw new IOException(e); }
    }
    public static Uri shareUri(Context c,File report) {
        return new Uri.Builder().scheme("content").authority(c.getPackageName()+".reports")
                .appendPath(report.getName()).build();
    }
}
