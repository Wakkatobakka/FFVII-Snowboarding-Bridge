package com.nttdocomo.ui.graphics3d;
import com.nttdocomo.ui.util3d.Transform;
public interface Graphics3D { void addLight(Light l,Transform t); void flushBuffer(); void renderObject3D(DrawableObject3D o,Transform t); void setClipRectFor3D(int x,int y,int w,int h); void setParallelView(int w,int h); void setPerspectiveView(float a,float b,float c); void setTransform(Transform t); }
