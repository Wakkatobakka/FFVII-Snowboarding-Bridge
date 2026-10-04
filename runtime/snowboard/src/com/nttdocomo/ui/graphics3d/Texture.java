package com.nttdocomo.ui.graphics3d;
public class Texture extends Object3D {
    public final String name;
    public final android.graphics.Bitmap bitmap;
    public final boolean hasColorKey;
    public final int colorKeyRgb;
    public Texture(String n,android.graphics.Bitmap b){this(n,b,false,0);}
    public Texture(String n,android.graphics.Bitmap b,boolean key,int rgb){name=n;bitmap=b;hasColorKey=key;colorKeyRgb=rgb&0x00ffffff;}
}
