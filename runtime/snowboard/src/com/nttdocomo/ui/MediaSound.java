package com.nttdocomo.ui;
public final class MediaSound implements MediaResource {
    public final String name;
    public final byte[] bytes;
    public MediaSound(byte[] b){this(null,b);}
    public MediaSound(String n,byte[] b){name=n;bytes=b;}
    public void use(){}
}
