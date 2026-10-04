package com.nttdocomo.ui.util3d;
public final class Transform {
    // DoJa exposes the 4x4 matrix by flat indices; Snowboarding fills rows and puts translation at 3/7/11.
    public final float[] m=new float[16];
    public Transform(){setIdentity();}
    public void setIdentity(){for(int i=0;i<16;i++)m[i]=0;m[0]=m[5]=m[10]=m[15]=1;}
    public void translate(float x,float y,float z){m[3]+=x;m[7]+=y;m[11]+=z;}
    public void set(int idx,float v){if(idx>=0&&idx<16)m[idx]=v;}
}
