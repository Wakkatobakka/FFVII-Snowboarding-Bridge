package com.nttdocomo.ui;
import java.io.*;
import android.graphics.BitmapFactory;
import com.wakka.snowboardbridge.*;
import javax.microedition.io.Connector;

public final class MediaManager {
    public static MediaImage getImage(InputStream in){
        try {
            android.graphics.Bitmap b=BitmapFactory.decodeStream(in);
            // Android's decoder support for old BMP variants differs by device.  The
            // preservation package includes lossless PNG mirrors for the game's BMPs.
            if(b==null && in instanceof Connector.NamedInputStream){
                String n=((Connector.NamedInputStream)in).name;
                if(n.toLowerCase().endsWith(".bmp")){
                    try(InputStream alt=RuntimeHost.openGameAsset(n+".png")){ b=BitmapFactory.decodeStream(alt); }
                }
            }
            if(b==null) throw new IOException("BitmapFactory returned null");
            if(in instanceof Connector.NamedInputStream) BridgeLog.i("IMAGE",((Connector.NamedInputStream)in).name+" "+b.getWidth()+"x"+b.getHeight());
            return new AndroidMediaImage(new Image(b));
        } catch(Throwable e){
            BridgeLog.e("IMAGE",e);
            return new AndroidMediaImage(new Image(android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888)));
        }
    }
    public static MediaSound getSound(InputStream in){
        try{
            String name=(in instanceof Connector.NamedInputStream)?((Connector.NamedInputStream)in).name:null;
            ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1)o.write(b,0,n);
            return new MediaSound(name,o.toByteArray());
        }
        catch(Exception e){BridgeLog.e("SOUND LOAD",e);return new MediaSound(null,new byte[0]);}
    }
}
