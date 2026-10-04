package javax.microedition.io;
import java.io.*; import com.wakka.snowboardbridge.*;
public final class Connector {
    public static final class NamedInputStream extends FilterInputStream { public final String name; public NamedInputStream(String n,InputStream in){super(in);name=n;} }
    public static Connection open(String url,int mode,boolean timeouts) throws IOException {
        if(url.startsWith("resource:///") || url.startsWith("scratchpad:///")) throw new IOException("Stream URL must use openInputStream/openOutputStream");
        BridgeLog.i("NETWORK", "offline build blocked: "+url);
        return new OfflineHttpConnection(url);
    }
    private static final class OfflineHttpConnection implements com.nttdocomo.io.HttpConnection {
        final String url; ByteArrayOutputStream out=new ByteArrayOutputStream();
        OfflineHttpConnection(String u){url=u;}
        public void connect(){} public int getResponseCode(){return 200;}
        public InputStream openInputStream(){return new ByteArrayInputStream(new byte[0]);}
        public OutputStream openOutputStream(){out.reset();return out;}
        public void setRequestMethod(String m){} public void setRequestProperty(String k,String v){}
        public long getLength(){return 0;} public void close(){}
    }
    public static InputStream openInputStream(String url) throws IOException {
        if(url.startsWith("resource:///")){String name=url.substring("resource:///".length());BridgeLog.i("RESOURCE",name);return new NamedInputStream(name,RuntimeHost.openGameAsset(name));}
        if(url.startsWith("scratchpad:///")){ScratchRef r=parse(url);byte[] all=RuntimeHost.readScratch(r.index);int pos=Math.min(r.pos,all.length),len=Math.min(r.length<0?all.length-pos:r.length,all.length-pos);BridgeLog.i("SCRATCH READ",r.index+" pos="+pos+" len="+len);return new ByteArrayInputStream(all,pos,len);}
        throw new IOException("Unsupported URL: "+url);
    }
    public static OutputStream openOutputStream(String url) throws IOException {
        if(url.startsWith("scratchpad:///")){final ScratchRef r=parse(url);final byte[] base=RuntimeHost.readScratch(r.index);return new ByteArrayOutputStream(){@Override public void close() throws IOException{super.close();byte[] d=toByteArray();if(r.pos==0 && r.length<0){RuntimeHost.writeScratch(r.index,d);}else{int need=Math.max(base.length,r.pos+d.length);byte[] out=new byte[need];System.arraycopy(base,0,out,0,base.length);System.arraycopy(d,0,out,r.pos,d.length);RuntimeHost.writeScratch(r.index,out);}}};}
        throw new IOException("Unsupported URL: "+url);
    }
    private static ScratchRef parse(String u){String s=u.substring("scratchpad:///".length());int semi=s.indexOf(';');String id=semi<0?s:s.substring(0,semi);int pos=0,len=-1;if(semi>=0){String[] ps=s.substring(semi+1).split(",");for(String p:ps){String[] kv=p.split("=");if(kv.length==2){if(kv[0].equals("pos"))pos=Integer.parseInt(kv[1]);if(kv[0].equals("length"))len=Integer.parseInt(kv[1]);}}}return new ScratchRef(Integer.parseInt(id),pos,len);}
    private static final class ScratchRef{final int index,pos,length;ScratchRef(int i,int p,int l){index=i;pos=p;length=l;}}
}
