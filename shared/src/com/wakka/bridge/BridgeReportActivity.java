package com.wakka.bridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import java.io.File;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BridgeReportActivity extends Activity {
    private static final int SAVE_AS=701;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->new Thread(r,"BridgeReportIO"));
    protected abstract BridgeBackend backend();
    private String pendingName;
    private boolean reportBusy;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); if(state!=null) pendingName=state.getString("pending_report");
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("pending_report",pendingName);
    }
    protected void reportSaved(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    /** mode 0=quick Downloads, 1=Save as, 2=Share. Capture always precedes external UI. */
    protected final void exportReport(int mode) {
        if(reportBusy) return; reportBusy=true;
        String snapshot=backend().started()||!new File(new File(getFilesDir(),"reports"),"latest-report.txt").isFile()
                ?ReportStore.compose(this,backend()):ReportStore.latest(this);
        final android.content.Context context=getApplicationContext();
        IO.execute(()->{
            try {
                File file=ReportStore.capture(context,snapshot);
                if(mode==0&&Build.VERSION.SDK_INT>=29) {
                    try { ReportStore.downloads(context,file); runOnUiThread(()->{reportBusy=false;reportSaved("Report saved to Downloads/FFVII_Snowboarding_Bridge");}); }
                    catch(Exception e) { runOnUiThread(()->{reportBusy=false;new AlertDialog.Builder(this).setTitle("Report captured")
                            .setMessage("The report is safe in the app. Downloads failed; choose a destination instead.")
                            .setPositiveButton("Save as",(d,w)->saveAs(file)).setNegativeButton("Close",null).show();}); }
                } else runOnUiThread(()->{
                    reportBusy=false;
                    if(mode==2) share(file); else saveAs(file);
                });
            } catch(Exception e) { runOnUiThread(()->{reportBusy=false;reportSaved("Report capture failed: "+e.getMessage());}); }
        });
    }
    private void saveAs(File file) {
        pendingName=file.getName();
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE,file.getName()),SAVE_AS);
    }
    private void share(File file) {
        Uri uri=ReportStore.shareUri(this,file);
        Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri("Runtime report",uri));
        startActivity(Intent.createChooser(send,"Share runtime report"));
    }
    protected final void preserveSession() {
        if(!backend().started()) return;
        String report=ReportStore.compose(this,backend()); android.content.Context context=getApplicationContext();
        IO.execute(()->{try { ReportStore.preserveLatest(context,report); } catch(Exception e) { android.util.Log.e("WakkanBridge","Cannot preserve runtime report",e); }});
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=SAVE_AS||result!=RESULT_OK||data==null||data.getData()==null||pendingName==null) return;
        final String name=pendingName; final Uri uri=data.getData(); final android.content.Context context=getApplicationContext();
        IO.execute(()->{try {
            try(OutputStream out=context.getContentResolver().openOutputStream(uri,"w")) {
                if(out==null) throw new java.io.IOException("Cannot write selected destination");
                ReportStore.copy(ReportStore.resolve(context,name),out);
            }
            runOnUiThread(()->reportSaved("Report saved"));
        } catch(Exception e) { runOnUiThread(()->reportSaved("Report save failed: "+e.getMessage())); }});
    }
}
