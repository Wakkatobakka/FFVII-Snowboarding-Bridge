package com.nttdocomo.ui;
import com.wakka.snowboardbridge.BridgeLog;
public final class Display { public static void setCurrent(Frame f){ BridgeLog.i("DISPLAY",f==null?"null":f.getClass().getName()); } }
