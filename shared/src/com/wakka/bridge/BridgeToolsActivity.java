package com.wakka.bridge;

import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Diagnostics survive UI polish and remain reachable without booting a game. */
public abstract class BridgeToolsActivity extends BridgeReportActivity {
    private TextView report;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); GameSpec g=backend().spec(); FrameLayout safe=BridgeUi.safeRoot(this);
        LinearLayout root=BridgeUi.scrollColumn(this,safe);
        Button back=BridgeUi.button(this,"‹ RETURN TO BRIDGE",false,g.accent); back.setOnClickListener(v->finish());
        root.addView(back,BridgeUi.lp(this,48,14));
        root.addView(BridgeUi.label(this,"BRIDGEKEEPER TOOLS",26,BridgeUi.INK,true),BridgeUi.lp(this,-2,8));
        root.addView(BridgeUi.label(this,g.title,15,BridgeUi.MUTED,false),BridgeUi.lp(this,-2,12));
        root.addView(BridgeUi.section(this,"SESSION REPORT"));
        root.addView(BridgeUi.label(this,"Capture the current session or export the last saved report. Reports include runtime details, cumulative counters, input context, graphics status, errors and the retained log.",13,BridgeUi.MUTED,false),BridgeUi.lp(this,-2,12));
        addButton(root,"Save report to Downloads",()->exportReport(0));
        addButton(root,"Save report as…",()->exportReport(1));
        addButton(root,"Share report…",()->exportReport(2));
        addButton(root,"Refresh diagnostics",()->refreshReport());
        backend().addToolSections(this,root);
        root.addView(BridgeUi.section(this,"CONTROLS"));
        root.addView(BridgeUi.label(this,g.controlsHelp,13,BridgeUi.INK,false));
        root.addView(BridgeUi.section(this,"CURRENT BASELINE / KNOWN ISSUES"));
        root.addView(BridgeUi.label(this,g.baseline+"\n"+g.knownIssues,13,BridgeUi.MUTED,false));
        root.addView(BridgeUi.section(this,"DIAGNOSTICS"));
        report=BridgeUi.label(this,"",11,0xffbfe5e4,false); report.setTypeface(android.graphics.Typeface.MONOSPACE);
        report.setTextIsSelectable(true); root.addView(report); setContentView(safe); refreshReport();
    }
    private void addButton(LinearLayout root,String label,Runnable action) {
        Button b=BridgeUi.button(this,label,false,BridgeUi.CYAN); b.setOnClickListener(v->action.run());
        root.addView(b,BridgeUi.lp(this,52,8));
    }
    private void refreshReport() {
        String text=backend().started()||!new java.io.File(new java.io.File(getFilesDir(),"reports"),"latest-report.txt").isFile()
                ?ReportStore.compose(this,backend()):ReportStore.latest(this);
        if(text.length()>100000) text=text.substring(0,5000)+"\n[Preview shortened. Saved/shared report retains the full captured log.]\n"+text.substring(text.length()-95000);
        report.setText(text);
    }
    @Override protected void reportSaved(String text) { super.reportSaved(text); refreshReport(); }
}
