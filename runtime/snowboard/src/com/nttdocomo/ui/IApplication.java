package com.nttdocomo.ui;
import com.wakka.snowboardbridge.*;
public class IApplication {
    private static IApplication current;
    public IApplication(){ current=this; }
    public static IApplication getCurrentApp(){ return current; }
    public String getSourceURL(){ return "resource:///"; }
    public String[] getArgs(){ return new String[]{"100","1.0.0","http://ffi.sqexm.net/mobile/ff7sb.cgi","http://ffi.sqexm.net/snowboard/","1","http://ffi.sqexm.net/mobile/index.cgi?uid=NULLGWDOCOMO","NULLGWDOCOMO","100","10000108"}; }
    public void launch(int type,String[] args){ BridgeLog.i("LAUNCH","ignored offline launch type="+type); }
    public void terminate(){ RuntimeHost.terminated=true; BridgeLog.i("TERMINATE","IApplication.terminate"); }
    public void start(){} public void resume(){}
}
