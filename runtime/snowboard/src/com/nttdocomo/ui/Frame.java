package com.nttdocomo.ui;
import com.wakka.snowboardbridge.BridgeLog;
import com.wakka.snowboardbridge.RuntimeHost;
public class Frame {
    public int getWidth(){return 240;} public int getHeight(){return 240;}
    public void setSoftLabel(int which,String label){BridgeLog.i("SOFT LABEL",which+"="+label);RuntimeHost.setSoftLabel(which,label);}
}
