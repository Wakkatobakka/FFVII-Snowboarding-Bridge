package com.nttdocomo.ui;
import android.graphics.*;
import com.wakka.snowboardbridge.*;
public class Graphics {
    protected final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG); protected Font font=Font.getDefaultFont();
    public Graphics(){ setFont(font); }
    public void lock(){}
    public void unlock(boolean update){if(update)RuntimeHost.presentFrame();}
    public void setColor(int c){paint.setColor(0xff000000 | (c & 0xffffff));}
    public static int getColorOfRGB(int r,int g,int b){return ((r&255)<<16)|((g&255)<<8)|(b&255);}
    public void setFont(Font f){if(f!=null){font=f;paint.setTypeface(f.p.getTypeface());paint.setTextSize(f.p.getTextSize());}}
    public void fillRect(int x,int y,int w,int h){RuntimeHost.canvas.drawRect(x,y,x+w,y+h,paint);}
    // DoJa drawString() uses y as the text baseline. Snowboarding already adds
    // Font.getAscent() when it wants a top-aligned label, so adding ascent here again
    // pushed every HUD/menu string down by one full ascent.
    public void drawString(String s,int x,int y){RuntimeHost.canvas.drawText(s==null?"":s,x,y,paint);}
    // DoJa 5.1 order is destination first, then source rectangle:
    // drawImage(image, dx, dy, sx, sy, width, height).
    public void drawImage(Image img,int dx,int dy,int sx,int sy,int width,int height){
        if(img==null||img.bitmap==null||width<=0||height<=0)return;
        int left=Math.max(0,sx), top=Math.max(0,sy);
        int right=Math.min(img.bitmap.getWidth(),sx+width);
        int bottom=Math.min(img.bitmap.getHeight(),sy+height);
        if(right<=left||bottom<=top)return;
        // If a source rectangle is clipped at the bitmap edge, preserve 1:1 placement.
        int adjDx=dx+(left-sx), adjDy=dy+(top-sy);
        Rect src=new Rect(left,top,right,bottom);
        Rect dst=new Rect(adjDx,adjDy,adjDx+(right-left),adjDy+(bottom-top));
        RuntimeHost.canvas.drawBitmap(img.bitmap,src,dst,paint);
    }
}
