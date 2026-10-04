package com.nttdocomo.ui;

/** Minimal DoJa 5.1 font/metrics bridge. */
public final class Font {
    final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

    private static final int DOT_TINY=10, DOT_SMALL=12, DOT_MEDIUM=14, DOT_LARGE=16;

    private Font(int size, int style, int face){
        p.setTextSize(size);
        int tfStyle=android.graphics.Typeface.NORMAL;
        if(style==0x11) tfStyle=android.graphics.Typeface.BOLD;
        else if(style==0x12) tfStyle=android.graphics.Typeface.ITALIC;
        else if(style==0x13) tfStyle=android.graphics.Typeface.BOLD_ITALIC;
        String family=(face==0x72)?"monospace":"sans-serif";
        p.setTypeface(android.graphics.Typeface.create(family,tfStyle));
    }

    private static Font make(int flags){
        // DoJa encodes SIZE_* in bits 8..15: small=1, medium=2, large=3, tiny=4.
        int sizeCode=(flags>>>8)&0xff;
        int dots;
        switch(sizeCode){
            case 1: dots=DOT_SMALL; break;
            case 2: dots=DOT_MEDIUM; break;
            case 3: dots=DOT_LARGE; break;
            case 4: dots=DOT_TINY; break;
            default: dots=DOT_TINY; break; // DoJa 5.1 initial/default font is SIZE_TINY.
        }
        int style=(flags>>>16)&0xff;
        int face=(flags>>>24)&0xff;
        return new Font(dots,style,face);
    }

    public static Font getDefaultFont(){ return make(0x70000400); }
    public static Font getFont(int flags){ return make(flags); }
    public static Font getFont(int flags,int fontSize){
        Font f=make(flags);
        if(fontSize>0) f.p.setTextSize(fontSize);
        return f;
    }

    /** Positive distance from baseline to top, matching DoJa Font.getAscent(). */
    public int getAscent(){ return (int)Math.ceil(-p.ascent()); }
    public int getDescent(){ return (int)Math.ceil(p.descent()); }
    public int getHeight(){ return (int)Math.ceil(p.descent()-p.ascent()); }
    public int stringWidth(String s){ return (int)Math.ceil(p.measureText(s==null?"":s)); }
}
