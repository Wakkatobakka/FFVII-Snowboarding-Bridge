package com.nttdocomo.ui.util3d;

/**
 * DoJa 4/5 util3d numeric helper.
 *
 * The 3D engine's "inner int" is a signed 20.12 fixed-point value: 4096 == 1.0.
 * Snowboarding depends on this directly in its own integer matrix code (matrix
 * products are rounded then shifted right by 12).  Treating these ints as IEEE
 * float bit patterns collapses every view matrix toward zero.
 *
 * DoJa trigonometric methods take degrees, not radians.
 */
public final class FastMath {
    private static final float SCALE = 4096.0f;

    private FastMath() {}

    public static float sin(float degrees){
        return (float)Math.sin(Math.toRadians(degrees));
    }
    public static float cos(float degrees){
        return (float)Math.cos(Math.toRadians(degrees));
    }
    public static float tan(float degrees){
        return (float)Math.tan(Math.toRadians(degrees));
    }
    public static float abs(float x){ return Math.abs(x); }
    public static float sqrt(float x){ return (float)Math.sqrt(x); }
    public static float add(float x,float y){ return x+y; }
    public static float sub(float x,float y){ return x-y; }
    public static float mul(float x,float y){ return x*y; }
    public static float div(float x,float y){ return x/y; }
    public static float asin(float x){ return (float)Math.toDegrees(Math.asin(x)); }
    public static float acos(float x){ return (float)Math.toDegrees(Math.acos(x)); }
    public static float atan(float x){ return (float)Math.toDegrees(Math.atan(x)); }
    public static float atan2(float a,float b){ return (float)Math.toDegrees(Math.atan2(b,a)); }

    public static int floatToInnerInt(float v){
        if(Float.isNaN(v) || Float.isInfinite(v)) throw new IllegalArgumentException("non-finite");
        double scaled=(double)v*SCALE;
        if(scaled>=Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if(scaled<=Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int)Math.round(scaled);
    }

    public static float innerIntToFloat(int v){
        return ((float)v)/SCALE;
    }
}
