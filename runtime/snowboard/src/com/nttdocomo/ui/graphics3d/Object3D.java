package com.nttdocomo.ui.graphics3d;
import java.io.*; import javax.microedition.io.Connector; import com.wakka.snowboardbridge.*; import android.graphics.BitmapFactory;
public class Object3D {
    private static byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toByteArray();}
    private static int le16(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8);}
    private static int le32(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8)|((b[o+2]&255)<<16)|((b[o+3]&255)<<24);}
    public static Object3D createInstance(InputStream in) throws IOException {
        String n=(in instanceof Connector.NamedInputStream)?((Connector.NamedInputStream)in).name:"";String lower=n.toLowerCase();
        if(lower.endsWith(".mtra")){
            byte[] bytes=readAll(in);
            try{ActionTable a=new ActionTable(n,bytes);BridgeLog.i("3D OBJECT","ActionTable "+n+" actions="+a.getNumActions()+" bones="+a.boneCount);return a;}
            catch(Throwable e){BridgeLog.e("3D MTRA "+n,e);return new ActionTable(n);}
        }
        if(lower.endsWith(".mbac")){
            byte[] bytes=readAll(in);
            try{MbacModel m=MbacModel.parse(bytes);BridgeLog.i("3D MBAC",n+" v="+m.vertexCount+" tri="+m.triangleCount);return new Figure(n,m);}
            catch(Throwable e){BridgeLog.e("3D MBAC "+n,e);return new Figure(n);}
        }
        if(lower.endsWith(".bmp")||lower.endsWith(".gif")||lower.endsWith(".png")||lower.endsWith(".jpg")){
            try{
                byte[] bytes=readAll(in);
                android.graphics.Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                boolean key=false;int keyRgb=0;
                if(lower.endsWith(".bmp") && bytes.length>=58 && bytes[0]=='B' && bytes[1]=='M'){
                    int dib=le32(bytes,14);
                    int bpp=le16(bytes,28);
                    int pal=14+dib;
                    if(bpp==8 && pal>=0 && pal+3<bytes.length){
                        int bb=bytes[pal]&255, gg=bytes[pal+1]&255, rr=bytes[pal+2]&255;
                        keyRgb=(rr<<16)|(gg<<8)|bb;key=true;
                    }
                }
                BridgeLog.i("3D OBJECT","Texture "+n+" "+(b==null?"decode-null":b.getWidth()+"x"+b.getHeight())+
                        (key?String.format(java.util.Locale.US," key=#%06X",keyRgb):""));
                return new Texture(n,b,key,keyRgb);
            }catch(Exception e){BridgeLog.e("3D TEXTURE",e);return new Texture(n,null);}
        }
        BridgeLog.i("3D OBJECT","Unknown object "+n);return new Object3D();
    }
}
