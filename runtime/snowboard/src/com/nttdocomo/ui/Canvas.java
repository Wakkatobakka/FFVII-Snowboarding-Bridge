package com.nttdocomo.ui;
import com.wakka.snowboardbridge.*;
public class Canvas extends Frame {
    private final Graphics graphics = new com.nttdocomo.ui.graphics3d.AndroidGraphics3D();
    public Canvas(){}
    public Graphics getGraphics(){return graphics;}
    public int getKeypadState(){return RuntimeHost.readKeypadState();}
    public void paint(Graphics g){}
    public void processIMEEvent(int type,String text){}
    public void imeOn(String text,int mode,int max){ BridgeLog.i("IME","requested; returning current text"); processIMEEvent(0,text==null?"":text); }
}
