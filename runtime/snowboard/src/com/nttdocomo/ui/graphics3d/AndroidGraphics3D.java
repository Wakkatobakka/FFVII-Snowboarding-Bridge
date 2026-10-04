package com.nttdocomo.ui.graphics3d;

import android.graphics.*;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.util3d.Transform;
import com.wakka.snowboardbridge.*;
import java.util.*;

public final class AndroidGraphics3D extends Graphics implements Graphics3D {
    // Snowboarding/DoJa MBAC Figure model units are finer than the game's world-space units.
    // The game converts its own world translations with >> 6 before filling DoJa Transform.
    // Keep this conversion Figure-only: dynamic Primitive terrain already arrives in world units.
    private static final float FIGURE_MODEL_TO_WORLD = 1.0f / 64.0f;
    // DoJa screen Y increases downward. Perspective projection must therefore keep
    // +viewY as +screenY, matching the existing parallel projection path.
    private final float[] transform=new float[16];
    private int clipX=0,clipY=0,clipW=240,clipH=240;
    private boolean perspective=true;
    private float near=1f,far=10000f,fov=45f;
    private int orthoW=240,orthoH=240;
    private int renders=0,figureRenders=0,primitiveRenders=0,flushes=0;
    private int lastMatrixReportFlush=-1;
    private float lastNear=-1,lastFar=-1,lastFov=-1;
    private int lastClipX=-1,lastClipY=-1,lastClipW=-1,lastClipH=-1;
    private int frameFrontVertices=0, frameBackVertices=0, frameNearFarRejected=0;
    private final Paint facePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final ArrayList<DrawItem> pending=new ArrayList<DrawItem>(4096);

    private static final class DrawItem {
        static final int TRI=0, LINE=1, POINT=2;
        int kind, fillColor, edgeColor, alpha, blendMode;
        float ax,ay,az,bx,by,bz,cx,cy,cz,depth,radius;
        // Textured polygon state. DoJa Primitive UVs are per vertex; perspective
        // correction defaults OFF, so v0.0.20 interpolates U/V affinely in screen space for both Primitive and Figure geometry.
        Bitmap texture;
        boolean textured;
        float au,av,bu,bv,cu,cv;
        int colorKeyRgb;
        boolean hasColorKey;
    }

    public AndroidGraphics3D(){
        for(int i=0;i<16;i++)transform[i]=0;
        transform[0]=transform[5]=transform[10]=transform[15]=1;
        edgePaint.setStyle(Paint.Style.STROKE);edgePaint.setStrokeWidth(0.7f);
    }
    public void addLight(Light l,Transform t){if(renders<4)BridgeLog.i("3D","light added");}

    /**
     * DoJa accumulates 3D work until flushBuffer(). v0.0.8 approximated visibility
     * by painter-sorting whole triangles by average depth. That is not sufficient
     * for Snowboarding's connected terrain mesh, where large/intersecting faces
     * span a wide Z range. v0.0.9 resolves opaque triangle visibility per pixel
     * with a native-resolution software Z buffer, then draws special primitives.
     */
    public void flushBuffer(){
        flushes++;
        if(!pending.isEmpty()){
            final Bitmap fb=RuntimeHost.framebuffer;
            final int fw=fb.getWidth(), fh=fb.getHeight();
            final int[] pixels=new int[fw*fh];
            fb.getPixels(pixels,0,fw,0,0,fw,fh);
            final float[] zbuf=new float[fw*fh];
            java.util.Arrays.fill(zbuf,Float.NEGATIVE_INFINITY); // store 1/z; larger == nearer

            int tris=0, zPixels=0;
            for(int i=0;i<pending.size();i++){
                DrawItem d=pending.get(i);
                if(d.kind!=DrawItem.TRI)continue;
                zPixels += rasterTriangle(d,pixels,zbuf,fw,fh);
                tris++;
            }
            fb.setPixels(pixels,0,fw,0,0,fw,fh);

            // DoJa treats point/line/point-sprite special primitives after polygon groups.
            android.graphics.Canvas c=RuntimeHost.canvas;
            int save=c.save();
            c.clipRect(clipX,clipY,clipX+clipW,clipY+clipH);
            for(int i=0;i<pending.size();i++){
                DrawItem d=pending.get(i);
                if(d.kind==DrawItem.TRI)continue;
                facePaint.setStyle(Paint.Style.FILL);
                facePaint.setColor(d.fillColor);facePaint.setAlpha(d.alpha);
                edgePaint.setStyle(Paint.Style.STROKE);edgePaint.setStrokeWidth(0.8f);
                edgePaint.setColor(d.edgeColor);edgePaint.setAlpha(Math.min(d.alpha,150));
                if(d.kind==DrawItem.LINE)c.drawLine(d.ax,d.ay,d.bx,d.by,edgePaint);
                else c.drawCircle(d.ax,d.ay,d.radius,facePaint);
            }
            c.restoreToCount(save);

            RuntimeHost.threeDStatus="3D +Z flush="+flushes+" tri="+tris+" px="+zPixels+
                    " front/back="+frameFrontVertices+"/"+frameBackVertices+" clip="+frameNearFarRejected+
                    " n/f="+(int)near+"/"+(int)far+" a="+(int)fov;
            if(flushes<=12 || (flushes%30)==0)
                BridgeLog.i("3D FRAME","#"+flushes+" items="+pending.size()+" tri="+tris+" px="+zPixels+
                        " front/back="+frameFrontVertices+"/"+frameBackVertices+" clip="+frameNearFarRejected+
                        " near/far="+(int)near+"/"+(int)far+" fov="+(int)fov+" rect="+clipX+","+clipY+","+clipW+","+clipH);
            pending.clear();
        }
        frameFrontVertices=0; frameBackVertices=0; frameNearFarRejected=0;
        // Presentation is synchronized to Graphics.unlock(true); do not expose half-complete frames here.
    }

    private int rasterTriangle(DrawItem d,int[] pixels,float[] zbuf,int fw,int fh){
        float minXf=Math.min(d.ax,Math.min(d.bx,d.cx)), maxXf=Math.max(d.ax,Math.max(d.bx,d.cx));
        float minYf=Math.min(d.ay,Math.min(d.by,d.cy)), maxYf=Math.max(d.ay,Math.max(d.by,d.cy));
        int minX=Math.max(Math.max(0,clipX),(int)Math.floor(minXf));
        int maxX=Math.min(Math.min(fw-1,clipX+clipW-1),(int)Math.ceil(maxXf));
        int minY=Math.max(Math.max(0,clipY),(int)Math.floor(minYf));
        int maxY=Math.min(Math.min(fh-1,clipY+clipH-1),(int)Math.ceil(maxYf));
        if(minX>maxX||minY>maxY)return 0;

        float area=edge(d.ax,d.ay,d.bx,d.by,d.cx,d.cy);
        if(Math.abs(area)<1.0e-6f)return 0;
        float invArea=1f/area;
        float iza=1f/Math.max(0.0001f,d.az), izb=1f/Math.max(0.0001f,d.bz), izc=1f/Math.max(0.0001f,d.cz);
        int written=0;
        for(int y=minY;y<=maxY;y++){
            float py=y+0.5f;
            int row=y*fw;
            for(int x=minX;x<=maxX;x++){
                float px=x+0.5f;
                float w0=edge(d.bx,d.by,d.cx,d.cy,px,py)*invArea;
                float w1=edge(d.cx,d.cy,d.ax,d.ay,px,py)*invArea;
                float w2=1f-w0-w1;
                if(w0<-0.0001f||w1<-0.0001f||w2<-0.0001f)continue;
                float invZ=w0*iza+w1*izb+w2*izc;
                int idx=row+x;
                if(invZ<=zbuf[idx])continue;
                int src=d.fillColor;
                if(d.textured && d.texture!=null){
                    // DoJa's default is no perspective correction, so use affine UVs.
                    float uf=w0*d.au+w1*d.bu+w2*d.cu;
                    float vf=w0*d.av+w1*d.bv+w2*d.cv;
                    int tw=d.texture.getWidth(), th=d.texture.getHeight();
                    if(tw>0 && th>0){
                        int tx=floorMod((int)Math.floor(uf),tw);
                        int ty=floorMod((int)Math.floor(vf),th);
                        src=d.texture.getPixel(tx,ty);
                        // DoJa treats palette index 0 as a color key. For 8-bit BMP
                        // textures we preserve palette[0]'s RGB while loading.
                        if(d.hasColorKey && ((src&0x00ffffff)==d.colorKeyRgb))continue;
                        if(Color.alpha(src)==0)continue;
                    }
                }
                zbuf[idx]=invZ;
                pixels[idx]=(d.blendMode==64)?blendAdd(pixels[idx],src,d.alpha):blend(pixels[idx],src,d.alpha);
                written++;
            }
        }
        return written;
    }

    private static int floorMod(int a,int b){ int r=a%b; return r<0?r+b:r; }

    private static float edge(float ax,float ay,float bx,float by,float px,float py){
        return (px-ax)*(by-ay)-(py-ay)*(bx-ax);
    }

    private static int blendAdd(int dst,int src,int alpha){
        if(alpha<=0)return dst;
        int r=Math.min(255,Color.red(dst)+(Color.red(src)*alpha+127)/255);
        int g=Math.min(255,Color.green(dst)+(Color.green(src)*alpha+127)/255);
        int b=Math.min(255,Color.blue(dst)+(Color.blue(src)*alpha+127)/255);
        return Color.argb(255,r,g,b);
    }

    private static int blend(int dst,int src,int alpha){
        if(alpha>=255)return 0xff000000|(src&0x00ffffff);
        if(alpha<=0)return dst;
        int inv=255-alpha;
        int r=(Color.red(src)*alpha+Color.red(dst)*inv+127)/255;
        int g=(Color.green(src)*alpha+Color.green(dst)*inv+127)/255;
        int b=(Color.blue(src)*alpha+Color.blue(dst)*inv+127)/255;
        return Color.argb(255,r,g,b);
    }

    public void setClipRectFor3D(int x,int y,int w,int h){
        clipX=x;clipY=y;clipW=w;clipH=h;
        if(x!=lastClipX||y!=lastClipY||w!=lastClipW||h!=lastClipH){
            lastClipX=x;lastClipY=y;lastClipW=w;lastClipH=h;
            BridgeLog.i("3D CLIP",x+","+y+" "+w+"x"+h);
        }
    }
    public void setParallelView(int w,int h){perspective=false;orthoW=Math.max(1,w);orthoH=Math.max(1,h);BridgeLog.i("3D VIEW","parallel "+orthoW+"x"+orthoH);}
    public void setPerspectiveView(float a,float b,float c){
        perspective=true;near=Math.max(0.01f,a);far=Math.max(near+1f,b);fov=Math.max(10f,Math.min(150f,c));
        if(near!=lastNear||far!=lastFar||fov!=lastFov){lastNear=near;lastFar=far;lastFov=fov;BridgeLog.i("3D VIEW","perspective near="+near+" far="+far+" fov="+fov);}
    }
    public void setTransform(Transform t){
        if(t==null){for(int i=0;i<16;i++)transform[i]=0;transform[0]=transform[5]=transform[10]=transform[15]=1;return;}
        System.arraycopy(t.m,0,transform,0,16);
        RuntimeHost.threeDStatus=String.format(java.util.Locale.US,"3D M %.2f %.2f %.2f T %.0f %.0f %.0f",transform[0],transform[5],transform[10],transform[3],transform[7],transform[11]);
        if(flushes!=lastMatrixReportFlush && (flushes<12 || (flushes%30)==0)){
            lastMatrixReportFlush=flushes;
            BridgeLog.i("3D MATRIX",String.format(java.util.Locale.US,
                    "#%d [%.3f %.3f %.3f %.3f | %.3f %.3f %.3f %.3f | %.3f %.3f %.3f %.3f]",
                    flushes,transform[0],transform[1],transform[2],transform[3],transform[4],transform[5],transform[6],transform[7],transform[8],transform[9],transform[10],transform[11]));
        }
    }
    public void renderObject3D(DrawableObject3D o,Transform local){
        renders++;
        float[] m=effectiveTransform(local);
        if(o instanceof Primitive){renderPrimitive((Primitive)o,m);return;}
        if(o instanceof Figure){renderFigure((Figure)o,m);}
    }

    private float[] effectiveTransform(Transform local){
        if(local==null)return transform;
        float[] out=new float[16],b=local.m;
        for(int r=0;r<4;r++) for(int c=0;c<4;c++){
            float s=0f; for(int k=0;k<4;k++)s+=transform[r*4+k]*b[k*4+c]; out[r*4+c]=s;
        }
        return out;
    }

    private void renderFigure(Figure f,float[] m){
        figureRenders++; MbacModel model=f.model;
        if(model==null){if(figureRenders<10)BridgeLog.i("3D RENDER","Figure "+f.name+" has no parsed geometry");return;}
        final int n=model.vertexCount;
        float[] sourceVertices=model.vertices;
        boolean animated=false;
        if(f.action!=null && f.action.getNumActions()>0 && f.action.compatible(model) &&
                f.actionIndex>=0 && f.actionIndex<f.action.getNumActions()){
            try{
                if(f.poseVertices==null || f.poseVertices.length!=model.rawVertices.length)
                    f.poseVertices=new float[model.rawVertices.length];
                f.action.deformInto(model,f.actionIndex,f.time,f.poseVertices);
                sourceVertices=f.poseVertices; animated=true;
            }catch(Throwable animError){
                if(figureRenders<=12 || (figureRenders%180)==0) BridgeLog.e("3D MTRA APPLY "+f.name,animError);
                sourceVertices=model.vertices; animated=false;
            }
        }
        float poseMinX=Float.POSITIVE_INFINITY,poseMaxX=Float.NEGATIVE_INFINITY;
        float poseMinY=Float.POSITIVE_INFINITY,poseMaxY=Float.NEGATIVE_INFINITY;
        float poseMinZ=Float.POSITIVE_INFINITY,poseMaxZ=Float.NEGATIVE_INFINITY;
        for(int i=0;i<n;i++){
            int p=i*3; float x=sourceVertices[p],y=sourceVertices[p+1],z=sourceVertices[p+2];
            poseMinX=Math.min(poseMinX,x);poseMaxX=Math.max(poseMaxX,x);
            poseMinY=Math.min(poseMinY,y);poseMaxY=Math.max(poseMaxY,y);
            poseMinZ=Math.min(poseMinZ,z);poseMaxZ=Math.max(poseMaxZ,z);
        }
        float[] tx=new float[n],ty=new float[n],tz=new float[n];
        int plus=0,minus=0;
        for(int i=0;i<n;i++){
            int p=i*3;
            // MBAC Figure coordinates use a finer model-space unit than Snowboarding's world-space
            // Primitive/camera coordinates.  The game itself converts its internal translations with
            // >> 6 before filling a DoJa Transform; apply that same 1/64 unit conversion to Figure
            // geometry only, after skeletal deformation, before the game's world/view matrix.
            float x=sourceVertices[p]*FIGURE_MODEL_TO_WORLD;
            float y=sourceVertices[p+1]*FIGURE_MODEL_TO_WORLD;
            float z=sourceVertices[p+2]*FIGURE_MODEL_TO_WORLD;
            float xx=m[0]*x+m[1]*y+m[2]*z+m[3];
            float yy=m[4]*x+m[5]*y+m[6]*z+m[7];
            float zz=m[8]*x+m[9]*y+m[10]*z+m[11];
            tx[i]=xx;ty[i]=yy;tz[i]=zz;
            if(zz>=near && zz<=far)plus++; if(-zz>=near && -zz<=far)minus++;
        }
        final float zSign=1f;
        frameFrontVertices += plus; frameBackVertices += minus;
        float[] sx=new float[n],sy=new float[n],sz=new float[n];boolean[] ok=new boolean[n];
        float focal=(clipH*0.5f)/(float)Math.tan(Math.toRadians(fov*0.5f));
        float minSX=Float.POSITIVE_INFINITY,maxSX=Float.NEGATIVE_INFINITY,minSY=Float.POSITIVE_INFINITY,maxSY=Float.NEGATIVE_INFINITY;
        float minVZ=Float.POSITIVE_INFINITY,maxVZ=Float.NEGATIVE_INFINITY;
        int projected=0;
        for(int i=0;i<n;i++){
            float z=tz[i]*zSign;sz[i]=z;
            if(perspective){
                if(z<near || z>far){ frameNearFarRejected++; continue; }
                sx[i]=clipX+clipW*0.5f+(tx[i]*focal/z);
                sy[i]=clipY+clipH*0.5f+(ty[i]*focal/z);
            }else{
                sx[i]=clipX+clipW*0.5f+(tx[i]/(orthoW*0.5f))*(clipW*0.5f);
                sy[i]=clipY+clipH*0.5f+(ty[i]/(orthoH*0.5f))*(clipH*0.5f);
            }
            ok[i]=Float.isFinite(sx[i])&&Float.isFinite(sy[i]);
            if(ok[i]){
                projected++;
                minSX=Math.min(minSX,sx[i]);maxSX=Math.max(maxSX,sx[i]);
                minSY=Math.min(minSY,sy[i]);maxSY=Math.max(maxSY,sy[i]);
                minVZ=Math.min(minVZ,z);maxVZ=Math.max(maxVZ,z);
            }
        }
        int base=colorFor(f.name);int alpha=f.name.toLowerCase().contains("shadow")?70:255;
        int[] tri=model.triangles;float[] tuv=model.triangleUVs;int[] tpat=model.trianglePatterns;int drawn=0,textured=0,patternHidden=0;
        final Texture tex=f.texture;
        int uvMinU=Integer.MAX_VALUE,uvMaxU=Integer.MIN_VALUE,uvMinV=Integer.MAX_VALUE,uvMaxV=Integer.MIN_VALUE;
        for(int q=0;q+2<tri.length;q+=3){
            int triIndex=q/3;
            int patternMask=(tpat!=null && triIndex<tpat.length)?tpat[triIndex]:0;
            if(patternMask!=0 && (f.pattern & patternMask)==0){patternHidden++;continue;}
            int ia=tri[q],ib=tri[q+1],ic=tri[q+2]; if(ia<0||ib<0||ic<0||ia>=n||ib>=n||ic>=n)continue;
            if(!ok[ia]||!ok[ib]||!ok[ic])continue;
            float ax=sx[ia],ay=sy[ia],bx=sx[ib],by=sy[ib],cx=sx[ic],cy=sy[ic];
            if(offscreen(ax,ay,bx,by,cx,cy))continue;
            float area=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax);if(Math.abs(area)<0.02f)continue;
            float dz=(sz[ia]+sz[ib]+sz[ic])/3f;
            float shade=perspective?Math.max(0.55f,Math.min(1.15f,1.1f-dz/Math.max(far,1f)*0.45f)):0.9f;
            int uvBase=(q/3)*6;
            boolean hasUv=tex!=null && tex.bitmap!=null && tuv!=null && uvBase+5<tuv.length && Float.isFinite(tuv[uvBase]);
            if(hasUv){
                float au=tuv[uvBase],av=tuv[uvBase+1],bu=tuv[uvBase+2],bv=tuv[uvBase+3],cu=tuv[uvBase+4],cv=tuv[uvBase+5];
                if(queueTexturedTri(ax,ay,sz[ia],au,av,bx,by,sz[ib],bu,bv,cx,cy,sz[ic],cu,cv,
                        adjust(base,shade),adjust(base,0.55f),alpha,tex,0)){
                    drawn++;textured++;
                    uvMinU=Math.min(uvMinU,(int)Math.min(au,Math.min(bu,cu)));uvMaxU=Math.max(uvMaxU,(int)Math.max(au,Math.max(bu,cu)));
                    uvMinV=Math.min(uvMinV,(int)Math.min(av,Math.min(bv,cv)));uvMaxV=Math.max(uvMaxV,(int)Math.max(av,Math.max(bv,cv)));
                }
            }else{
                enqueueTri(ax,ay,sz[ia],bx,by,sz[ib],cx,cy,sz[ic],adjust(base,shade),adjust(base,0.55f),alpha,0);drawn++;
            }
        }
        String texName=(tex==null?"-":tex.name);
        String uvRange=(uvMinU==Integer.MAX_VALUE?"-":uvMinU+".."+uvMaxU+","+uvMinV+".."+uvMaxV);
        String bbox=projected==0?"-":((int)minSX)+".."+((int)maxSX)+","+((int)minSY)+".."+((int)maxSY);
        String zrange=projected==0?"-":((int)minVZ)+".."+((int)maxVZ);
        String poseBounds=((int)poseMinX)+".."+((int)poseMaxX)+"/"+((int)poseMinY)+".."+((int)poseMaxY)+"/"+((int)poseMinZ)+".."+((int)poseMaxZ);
        String worldBounds=String.format(java.util.Locale.US,"%.2f..%.2f/%.2f..%.2f/%.2f..%.2f",
                poseMinX*FIGURE_MODEL_TO_WORLD,poseMaxX*FIGURE_MODEL_TO_WORLD,
                poseMinY*FIGURE_MODEL_TO_WORLD,poseMaxY*FIGURE_MODEL_TO_WORLD,
                poseMinZ*FIGURE_MODEL_TO_WORLD,poseMaxZ*FIGURE_MODEL_TO_WORLD);
        int maxFrame=-1;
        if(f.action!=null && f.action.getNumActions()>0 && f.actionIndex>=0 && f.actionIndex<f.action.getNumActions())
            try{maxFrame=f.action.getMaxFrame(f.actionIndex);}catch(Throwable ignored){}
        if(f.name!=null && f.name.toLowerCase().contains("cloud")){
            RuntimeHost.threeDStatus="FIG Cloud "+(animated?"ANIM":"BIND")+" scale=1/64 pat=0x"+Integer.toHexString(f.pattern)+" hide="+patternHidden+" bbox="+bbox+" z="+zrange+" a="+f.actionIndex+" t="+f.time;
        }
        if(figureRenders<=12 || (figureRenders%180)==0){
            BridgeLog.i("3D FIGURE",f.name+" tri="+model.triangleCount+" queued="+drawn+" textured="+textured+
                    "/"+model.texturedTriangleCount+" tex="+texName+" uv="+uvRange+" projected="+projected+" bbox="+bbox+
                    " viewZ="+zrange+" raw="+model.rawBounds()+" bind="+model.bindBounds()+" pose="+(animated?"anim":"bind")+
                    " poseBounds="+poseBounds+" worldBounds="+worldBounds+" unitScale=1/64 patterns="+model.numPatterns+" pat=0x"+Integer.toHexString(f.pattern)+" patHide="+patternHidden+
                    " action="+(f.action==null?"-":f.action.name)+"#"+f.actionIndex+
                    " time="+f.time+" maxFrame="+maxFrame+
                    " front/back="+plus+"/"+minus+" near="+(int)near+" far="+(int)far+" fov="+(int)fov);
        }
    }

    private static int colorFor(String name){String n=name==null?"":name.toLowerCase();if(n.contains("cloud"))return Color.rgb(65,85,120);if(n.contains("shadow"))return Color.rgb(25,30,40);if(n.contains("sabotender"))return Color.rgb(70,135,75);if(n.contains("moguri"))return Color.rgb(185,155,105);if(n.contains("snow"))return Color.rgb(235,245,250);return Color.rgb(205,220,230);}
    private static int adjust(int color,float f){int r=Math.min(255,Math.max(0,(int)(Color.red(color)*f))),g=Math.min(255,Math.max(0,(int)(Color.green(color)*f))),b=Math.min(255,Math.max(0,(int)(Color.blue(color)*f)));return Color.rgb(r,g,b);}

    private void renderPrimitive(Primitive p,float[] m){
        primitiveRenders++;
        final int vpp=p.verticesPerPrimitive;
        final int count=p.primitiveCount;
        final int[] v=p.getVertexArray();
        if(v==null || vpp<=0 || count<=0)return;

        int liveFaces=0;
        float rawMinX=Float.POSITIVE_INFINITY,rawMaxX=Float.NEGATIVE_INFINITY;
        float rawMinY=Float.POSITIVE_INFINITY,rawMaxY=Float.NEGATIVE_INFINITY;
        float rawMinZ=Float.POSITIVE_INFINITY,rawMaxZ=Float.NEGATIVE_INFINITY;
        int plus=0,minus=0;
        for(int face=0;face<count;face++){
            int base=face*vpp*3;
            if(base+vpp*3>v.length)break;
            if(!faceHasVertexData(v,base,vpp))continue;
            liveFaces++;
            for(int j=0;j<vpp;j++){
                int q=base+j*3; float x=v[q],y=v[q+1],z=v[q+2];
                rawMinX=Math.min(rawMinX,x);rawMaxX=Math.max(rawMaxX,x);
                rawMinY=Math.min(rawMinY,y);rawMaxY=Math.max(rawMaxY,y);
                rawMinZ=Math.min(rawMinZ,z);rawMaxZ=Math.max(rawMaxZ,z);
                float zz=m[8]*x+m[9]*y+m[10]*z+m[11];
                if(zz>=near && zz<=far)plus++;
                if(-zz>=near && -zz<=far)minus++;
            }
        }
        if(liveFaces==0)return;
        final float zSign=1f;
        frameFrontVertices += plus; frameBackVertices += minus;

        int baseColor=primitiveColor(p);
        final int blendMode=p.blend;
        final float transPct=Math.max(0f,Math.min(100f,p.transparency));
        // DoJa calls this value "transparency", but 100% means fully opaque/source-visible.
        // It only affects BLEND_ALPHA (32) and BLEND_ADD (64); BLEND_NORMAL ignores it.
        int alpha=(blendMode==32 || blendMode==64)
                ? Math.max(0,Math.min(255,Math.round(255f*transPct/100f)))
                : 255;

        float[] sx=new float[4],sy=new float[4],sz=new float[4];
        float[] uu=new float[4],vv=new float[4];
        final int[] uv=p.getTextureCoordArray();
        final Texture texObj=p.texture;
        final boolean useTexture=(texObj!=null && texObj.bitmap!=null && uv!=null && (p.attributes&0x3000)!=0);
        int drawnFaces=0, skippedDepth=0, skippedDegenerate=0;
        float seenMinZ=Float.POSITIVE_INFINITY,seenMaxZ=Float.NEGATIVE_INFINITY;
        int uvMinU=Integer.MAX_VALUE,uvMaxU=Integer.MIN_VALUE,uvMinV=Integer.MAX_VALUE,uvMaxV=Integer.MIN_VALUE;
        for(int face=0;face<count;face++){
            int base=face*vpp*3;
            if(base+vpp*3>v.length)break;
            if(!faceHasVertexData(v,base,vpp))continue;
            boolean good=true;
            for(int j=0;j<vpp;j++){
                int q=base+j*3;
                if(!project(v[q],v[q+1],v[q+2],zSign,m,sx,sy,sz,j)){good=false;break;}
                seenMinZ=Math.min(seenMinZ,sz[j]);seenMaxZ=Math.max(seenMaxZ,sz[j]);
            }
            if(!good){skippedDepth++;continue;}

            if(useTexture){
                int uvBase=face*vpp*2;
                if(uvBase+vpp*2<=uv.length){
                    for(int j=0;j<vpp;j++){
                        int tq=uvBase+j*2;
                        uu[j]=uv[tq]; vv[j]=uv[tq+1];
                        uvMinU=Math.min(uvMinU,uv[tq]);uvMaxU=Math.max(uvMaxU,uv[tq]);
                        uvMinV=Math.min(uvMinV,uv[tq+1]);uvMaxV=Math.max(uvMaxV,uv[tq+1]);
                    }
                }
            }

            int color=colorForPrimitiveFace(p,face,baseColor);
            float stripe=(face&1)==0?1.00f:0.92f;
            int fill=adjust(color,stripe), edge=adjust(color,0.58f);

            if(vpp==4){
                boolean a=useTexture
                        ? queueTexturedTri(sx[0],sy[0],sz[0],uu[0],vv[0],sx[1],sy[1],sz[1],uu[1],vv[1],sx[2],sy[2],sz[2],uu[2],vv[2],fill,edge,alpha,texObj,blendMode)
                        : queueTri(sx[0],sy[0],sz[0],sx[1],sy[1],sz[1],sx[2],sy[2],sz[2],fill,edge,alpha,blendMode);
                boolean b=useTexture
                        ? queueTexturedTri(sx[0],sy[0],sz[0],uu[0],vv[0],sx[2],sy[2],sz[2],uu[2],vv[2],sx[3],sy[3],sz[3],uu[3],vv[3],fill,edge,alpha,texObj,blendMode)
                        : queueTri(sx[0],sy[0],sz[0],sx[2],sy[2],sz[2],sx[3],sy[3],sz[3],fill,edge,alpha,blendMode);
                if(a||b)drawnFaces++; else skippedDegenerate++;
            }else if(vpp==3){
                boolean a=useTexture
                        ? queueTexturedTri(sx[0],sy[0],sz[0],uu[0],vv[0],sx[1],sy[1],sz[1],uu[1],vv[1],sx[2],sy[2],sz[2],uu[2],vv[2],fill,edge,alpha,texObj,blendMode)
                        : queueTri(sx[0],sy[0],sz[0],sx[1],sy[1],sz[1],sx[2],sy[2],sz[2],fill,edge,alpha,blendMode);
                if(a)drawnFaces++; else skippedDegenerate++;
            }else if(vpp==2){
                DrawItem d=new DrawItem();d.kind=DrawItem.LINE;d.ax=sx[0];d.ay=sy[0];d.bx=sx[1];d.by=sy[1];d.depth=(sz[0]+sz[1])*0.5f;d.edgeColor=edge;d.alpha=alpha;d.blendMode=blendMode;pending.add(d);drawnFaces++;
            }else{
                DrawItem d=new DrawItem();d.kind=DrawItem.POINT;d.ax=sx[0];d.ay=sy[0];d.depth=sz[0];d.fillColor=fill;d.alpha=alpha;d.blendMode=blendMode;d.radius=Math.max(1f,p.type==5?3f:1.5f);pending.add(d);drawnFaces++;
            }
        }

        String tex=(p.texture==null?"-":p.texture.name);
        String uvRange=(uvMinU==Integer.MAX_VALUE?"-":uvMinU+".."+uvMaxU+","+uvMinV+".."+uvMaxV);
        RuntimeHost.threeDStatus="3D P"+primitiveRenders+" live="+liveFaces+" queue="+drawnFaces+" skip="+skippedDepth+
                " z="+finiteRange(seenMinZ,seenMaxZ)+" tex="+(useTexture?"Y":"N")+" uv="+uvRange;
        if(primitiveRenders<=24 || (primitiveRenders%180)==0){
            BridgeLog.i("3D PRIM","#"+primitiveRenders+" type="+p.type+" attr=0x"+Integer.toHexString(p.attributes)+
                    " max="+count+" live="+liveFaces+" queued="+drawnFaces+" depthSkip="+skippedDepth+
                    " rawX="+(int)rawMinX+".."+(int)rawMaxX+" rawY="+(int)rawMinY+".."+(int)rawMaxY+
                    " rawZ="+(int)rawMinZ+".."+(int)rawMaxZ+" viewZ="+finiteRange(seenMinZ,seenMaxZ)+
                    " cam=+Z front/back="+plus+"/"+minus+" near="+(int)near+" far="+(int)far+" fov="+(int)fov+" tex="+tex+
                    " textured="+useTexture+" uv="+uvRange+" blend="+blendMode+" trans="+transPct+" alpha="+alpha);
        }
    }

    private boolean project(float x,float y,float z,float zSign,float[] m,float[] sx,float[] sy,float[] sz,int i){
        float xx=m[0]*x+m[1]*y+m[2]*z+m[3];
        float yy=m[4]*x+m[5]*y+m[6]*z+m[7];
        float zz=(m[8]*x+m[9]*y+m[10]*z+m[11])*zSign;
        sz[i]=zz;
        if(perspective){
            if(zz<near || zz>far){ frameNearFarRejected++; return false; }
            float focal=(clipH*0.5f)/(float)Math.tan(Math.toRadians(fov*0.5f));
            sx[i]=clipX+clipW*0.5f+(xx*focal/zz);
            sy[i]=clipY+clipH*0.5f+(yy*focal/zz);
        }else{
            sx[i]=clipX+clipW*0.5f+(xx/(orthoW*0.5f))*(clipW*0.5f);
            sy[i]=clipY+clipH*0.5f+(yy/(orthoH*0.5f))*(clipH*0.5f);
        }
        return Float.isFinite(sx[i])&&Float.isFinite(sy[i])&&Math.abs(sx[i])<20000&&Math.abs(sy[i])<20000;
    }

    private boolean queueTexturedTri(float ax,float ay,float az,float au,float av,
            float bx,float by,float bz,float bu,float bv,
            float cx,float cy,float cz,float cu,float cv,
            int fill,int edge,int alpha,Texture tex,int blendMode){
        if(offscreen(ax,ay,bx,by,cx,cy))return false;
        float area=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
        if(Math.abs(area)<0.015f)return false;
        DrawItem d=new DrawItem();d.kind=DrawItem.TRI;
        d.ax=ax;d.ay=ay;d.az=az;d.bx=bx;d.by=by;d.bz=bz;d.cx=cx;d.cy=cy;d.cz=cz;
        d.au=au;d.av=av;d.bu=bu;d.bv=bv;d.cu=cu;d.cv=cv;
        d.depth=(az+bz+cz)/3f;d.fillColor=fill;d.edgeColor=edge;d.alpha=alpha;d.blendMode=blendMode;
        d.texture=tex==null?null:tex.bitmap;d.textured=d.texture!=null;
        d.hasColorKey=tex!=null&&tex.hasColorKey;d.colorKeyRgb=tex==null?0:tex.colorKeyRgb;
        pending.add(d);return true;
    }

    private boolean queueTri(float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,int fill,int edge,int alpha,int blendMode){
        if(offscreen(ax,ay,bx,by,cx,cy))return false;
        float area=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
        if(Math.abs(area)<0.015f)return false;
        enqueueTri(ax,ay,az,bx,by,bz,cx,cy,cz,fill,edge,alpha,blendMode);return true;
    }

    private void enqueueTri(float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,int fill,int edge,int alpha,int blendMode){
        DrawItem d=new DrawItem();d.kind=DrawItem.TRI;
        d.ax=ax;d.ay=ay;d.az=az;d.bx=bx;d.by=by;d.bz=bz;d.cx=cx;d.cy=cy;d.cz=cz;
        d.depth=(az+bz+cz)/3f;d.fillColor=fill;d.edgeColor=edge;d.alpha=alpha;d.blendMode=blendMode;pending.add(d);
    }

    private static boolean offscreen(float ax,float ay,float bx,float by,float cx,float cy){
        return (ax<-720&&bx<-720&&cx<-720)||(ax>960&&bx>960&&cx>960)||
               (ay<-720&&by<-720&&cy<-720)||(ay>960&&by>960&&cy>960);
    }

    private static boolean faceHasVertexData(int[] v,int base,int vpp){
        for(int j=0;j<vpp;j++){
            int q=base+j*3;
            if(v[q]!=0||v[q+1]!=0||v[q+2]!=0)return true;
        }
        return false;
    }

    private static int primitiveColor(Primitive p){
        int[] colors=p.getColorArray();
        if(colors!=null && colors.length>0 && colors[0]!=0)return 0xff000000|(colors[0]&0x00ffffff);
        if(p.texture!=null){
            String n=p.texture.name==null?"":p.texture.name.toLowerCase();
            if(n.contains("snow")||n.contains("course")||n.contains("road"))return Color.rgb(225,238,245);
            if(n.contains("tree")||n.contains("wood"))return Color.rgb(130,165,145);
            return Color.rgb(205,225,238);
        }
        return Color.rgb(220,230,240);
    }

    private static int colorForPrimitiveFace(Primitive p,int face,int fallback){
        int[] colors=p.getColorArray();
        if(colors==null||colors.length==0)return fallback;
        int idx=Math.min(face,colors.length-1);int c=colors[idx];
        if(c==0)return fallback;
        return 0xff000000|(c&0x00ffffff);
    }

    private static String finiteRange(float a,float b){
        if(!Float.isFinite(a)||!Float.isFinite(b))return "-";
        return ((int)a)+".."+((int)b);
    }
}
