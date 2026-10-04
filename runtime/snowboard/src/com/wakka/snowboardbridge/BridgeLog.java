package com.wakka.snowboardbridge;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public final class BridgeLog {
    private static final List<String> lines = new ArrayList<>();
    private static final List<String> crash = new ArrayList<>();
    private static long start = System.currentTimeMillis();
    private static long events, errors, trimmed;
    private static final int MAX_LINES=6000;
    public static synchronized void reset() { lines.clear(); crash.clear(); events=errors=trimmed=0; start = System.currentTimeMillis(); }
    private static void bound() { while(lines.size()>MAX_LINES) { lines.remove(0); trimmed++; } }
    public static synchronized long eventCount() { return events; }
    public static synchronized long errorCount() { return errors; }
    public static synchronized int retainedCount() { return lines.size(); }
    public static synchronized void i(String tag, String msg) {
        String s = String.format("%7.3f  %-14s %s", (System.currentTimeMillis()-start)/1000.0, tag, msg);
        events++; lines.add(s); bound(); Log.i("SnowboardBridge", s);
        RuntimeHost.invalidate();
    }
    public static synchronized void e(String tag, Throwable t) {
        events++; errors++;
        crash.clear();
        Throwable x=t;
        // Reflection can wrap the actual game failure more than once. Always keep
        // walking to the deepest cause so the device overlay reports game code.
        while(x.getCause()!=null && x.getCause()!=x) x=x.getCause();
        String head=tag+"  "+x.getClass().getName()+": "+String.valueOf(x.getMessage());
        lines.add(head); crash.add(head); Log.e("SnowboardBridge",head,x);
        StackTraceElement[] st=x.getStackTrace();
        for(int i=0;i<st.length;i++){
            String row="at "+st[i].toString(); lines.add("    "+row);
            if(i<6) crash.add(row);
        }
        bound();
        RuntimeHost.invalidate();
    }
    public static synchronized String tail(int n) {
        StringBuilder sb = new StringBuilder(); int from=Math.max(0,lines.size()-n);
        for(int i=from;i<lines.size();i++) sb.append(lines.get(i)).append('\n'); return sb.toString();
    }
    public static synchronized String screenText(int n){
        StringBuilder sb=new StringBuilder();
        if(!crash.isEmpty()){
            for(int i=0;i<Math.min(n,crash.size());i++) sb.append(crash.get(i)).append('\n');
            return sb.toString();
        }
        return tail(n);
    }
    public static synchronized boolean hasCrash(){ return !crash.isEmpty(); }
    public static synchronized String all() {
        return (trimmed>0?"["+trimmed+" older log lines trimmed; cumulative counters preserved]\n":"")+tail(lines.size());
    }
}
