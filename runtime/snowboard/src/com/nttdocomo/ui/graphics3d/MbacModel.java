package com.nttdocomo.ui.graphics3d;

import java.io.*;
import java.util.*;

/**
 * MBAC v5 geometry reader used by Snowboard Bridge.
 *
 * v0.0.16 keeps the established MascotCapsule bind-pose transform behavior,
 * but now preserves per-triangle UVs instead of discarding them. UV values are
 * stored in the original 0..255-ish texture coordinate domain expected by the
 * DoJa/MascotCapsule texture atlas.
 */
final class MbacModel {
    final float[] vertices;       // xyz triples in bind pose (after bone hierarchy)
    final float[] rawVertices;    // original MBAC vertex coordinates before bone transforms
    final Bone[] bones;           // original local bind transforms for MTRA deformation
    final int[] triangles;        // vertex indices, three per triangle
    final float[] triangleUVs;    // six floats per triangle; NaN means flat/untextured triangle
    final int[] trianglePatterns; // per-triangle MBAC appearance mask; 0 means always visible
    final int numPatterns;
    final int vertexCount;
    final int triangleCount;
    final int texturedTriangleCount;

    final float rawMinX,rawMaxX,rawMinY,rawMaxY,rawMinZ,rawMaxZ;
    final float bindMinX,bindMaxX,bindMinY,bindMaxY,bindMinZ,bindMaxZ;

    private MbacModel(float[] v,float[] raw,Bone[] boneData,int[] t,float[] uv,int[] patterns,int patternCount,int textured,
                      float rminx,float rmaxx,float rminy,float rmaxy,float rminz,float rmaxz,
                      float bminx,float bmaxx,float bminy,float bmaxy,float bminz,float bmaxz){
        vertices=v;rawVertices=raw;bones=boneData;triangles=t;triangleUVs=uv;trianglePatterns=patterns;numPatterns=patternCount;texturedTriangleCount=textured;
        vertexCount=v.length/3;triangleCount=t.length/3;
        rawMinX=rminx;rawMaxX=rmaxx;rawMinY=rminy;rawMaxY=rmaxy;rawMinZ=rminz;rawMaxZ=rmaxz;
        bindMinX=bminx;bindMaxX=bmaxx;bindMinY=bminy;bindMaxY=bmaxy;bindMinZ=bminz;bindMaxZ=bmaxz;
    }

    static MbacModel parse(byte[] data) throws IOException {
        Cursor c=new Cursor(data);
        if(c.u8()!='M' || c.u8()!='B') throw new IOException("not MBAC");
        int version=c.u16();
        int vertexFormat=1,normalFormat=0,polygonFormat=1,boneFormat=1;
        if(version>3){vertexFormat=c.u8();normalFormat=c.u8();polygonFormat=c.u8();boneFormat=c.u8();}
        int numVertices=c.u16(), numT3=c.u16(), numT4=c.u16(), numBones=c.u16();
        int numF3=0,numF4=0,matCount=0,maxMeta=0,numColor=0;
        int[] patternF3=null,patternF4=null,patternT3=null,patternT4=null;
        if(polygonFormat>=3){
            // MBAC v5 calls these colored-tri, colored-quad, texture-count, pattern-count, color-count.
            // v0.0.15 consumed the pattern table but discarded it, causing every alternate Cloud
            // appearance group to render simultaneously. Keep the table so Figure.setPattern()/setTime()
            // can select the intended polygon groups.
            numF3=c.u16();numF4=c.u16();matCount=c.u16();maxMeta=c.u16();numColor=c.u16();
            patternF3=new int[maxMeta];patternF4=new int[maxMeta];patternT3=new int[maxMeta];patternT4=new int[maxMeta];
            for(int i=0;i<maxMeta;i++){
                patternF3[i]=c.u16();patternF4[i]=c.u16();
                for(int j=0;j<matCount;j++){patternT3[i]+=c.u16();patternT4[i]+=c.u16();}
            }
        }
        if(vertexFormat!=2) throw new IOException("MBAC vertex format "+vertexFormat+" not supported yet");

        BitReader br=new BitReader(data,c.pos);
        float[] verts=new float[numVertices*3]; int vi=0;
        final int[] magnitude={8,10,13,16};
        float rawMinX=Float.POSITIVE_INFINITY,rawMaxX=Float.NEGATIVE_INFINITY;
        float rawMinY=Float.POSITIVE_INFINITY,rawMaxY=Float.NEGATIVE_INFINITY;
        float rawMinZ=Float.POSITIVE_INFINITY,rawMaxZ=Float.NEGATIVE_INFINITY;
        while(vi<numVertices){
            int header=br.u(8); int bits=magnitude[(header>>>6)&3]; int count=(header&63)+1;
            for(int k=0;k<count && vi<numVertices;k++,vi++){
                float x=br.s(bits),y=br.s(bits),z=br.s(bits);
                verts[vi*3]=x;verts[vi*3+1]=y;verts[vi*3+2]=z;
                rawMinX=Math.min(rawMinX,x);rawMaxX=Math.max(rawMaxX,x);
                rawMinY=Math.min(rawMinY,y);rawMaxY=Math.max(rawMaxY,y);
                rawMinZ=Math.min(rawMinZ,z);rawMaxZ=Math.max(rawMaxZ,z);
            }
        }
        c.pos=br.bytePos();

        // Normals are not required for this software rasterizer yet, but must be consumed exactly.
        if(normalFormat!=0){
            br=new BitReader(data,c.pos);
            for(int i=0;i<numVertices;i++){
                int x=br.s(7);
                if(x==-64) br.u(3);
                else {br.s(7);br.u(1);}
            }
            c.pos=br.bytePos();
        }

        ArrayList<Integer> out=new ArrayList<Integer>((numT3+numT4*2+numF3+numF4*2)*3);
        ArrayList<Float> outUv=new ArrayList<Float>((numT3+numT4*2+numF3+numF4*2)*6);
        int texturedTriangles=0;
        br=new BitReader(data,c.pos);
        if(numF3+numF4>0){
            int unkBits=br.u(8), indexBits=br.u(8), colorBits=br.u(8), colorIdBits=br.u(8);br.u(8);
            for(int i=0;i<numColor;i++){br.u(colorBits);br.u(colorBits);br.u(colorBits);}
            for(int i=0;i<numF3;i++){
                br.u(unkBits);int a=br.u(indexBits),b=br.u(indexBits),d=br.u(indexBits);br.u(colorIdBits);
                triFlat(out,outUv,a,b,d);
            }
            for(int i=0;i<numF4;i++){
                br.u(unkBits);int a=br.u(indexBits),b=br.u(indexBits),d=br.u(indexBits),e=br.u(indexBits);br.u(colorIdBits);
                triFlat(out,outUv,a,b,d);triFlat(out,outUv,d,b,e);
            }
        }
        if(numT3+numT4>0){
            int unkBits=br.u(8), indexBits=br.u(8), uvBits=br.u(8);br.u(8);
            for(int i=0;i<numT3;i++){
                br.u(unkBits);int a=br.u(indexBits),b=br.u(indexBits),d=br.u(indexBits);
                float au=br.u(uvBits),av=br.u(uvBits), bu=br.u(uvBits),bv=br.u(uvBits), du=br.u(uvBits),dv=br.u(uvBits);
                triUv(out,outUv,a,b,d,au,av,bu,bv,du,dv);texturedTriangles++;
            }
            for(int i=0;i<numT4;i++){
                br.u(unkBits);int a=br.u(indexBits),b=br.u(indexBits),d=br.u(indexBits),e=br.u(indexBits);
                float au=br.u(uvBits),av=br.u(uvBits), bu=br.u(uvBits),bv=br.u(uvBits);
                float du=br.u(uvBits),dv=br.u(uvBits), eu=br.u(uvBits),ev=br.u(uvBits);
                triUv(out,outUv,a,b,d,au,av,bu,bv,du,dv);
                triUv(out,outUv,d,b,e,du,dv,bu,bv,eu,ev);
                texturedTriangles+=2;
            }
        }
        c.pos=br.bytePos();

        // Expand MBAC appearance-pattern membership to the same triangle order used above.
        // Pattern table entry 0 is the always-visible/base geometry (mask 0). Later entries
        // use a 32-bit bit mask (1<<patternIndex); MTRA v5 supplies those masks dynamically.
        ArrayList<Integer> triPatternList=new ArrayList<Integer>(out.size()/3);
        for(int i=0;i<numF3;i++) triPatternList.add(patternMaskForFace(i,patternF3));
        for(int i=0;i<numF4;i++){int pm=patternMaskForFace(i,patternF4);triPatternList.add(pm);triPatternList.add(pm);}
        for(int i=0;i<numT3;i++) triPatternList.add(patternMaskForFace(i,patternT3));
        for(int i=0;i<numT4;i++){int pm=patternMaskForFace(i,patternT4);triPatternList.add(pm);triPatternList.add(pm);}

        float[] rawVerts=verts.clone();
        Bone[] bones=new Bone[numBones]; int start=0;
        for(int i=0;i<numBones;i++){
            int count=c.u16();int parent=c.s16();int[] local=new int[12];
            for(int q=0;q<12;q++) local[q]=c.s16();
            bones[i]=new Bone(start,count,parent,local); start+=count;
        }

        // MascotCapsule transforms each bone's vertex run with parent * local bind matrix.
        // This matches the established J2ME-Loader/native implementation and reference parsers.
        float[][] worlds=new float[numBones][];
        for(int i=0;i<numBones;i++) boneWorld(i,bones,worlds);
        for(int i=0;i<numBones;i++){
            Bone b=bones[i]; float[] m=worlds[i]; int end=Math.min(numVertices,b.start+b.count);
            for(int v=b.start;v<end;v++){
                int p=v*3;float x=verts[p],y=verts[p+1],z=verts[p+2];
                verts[p]=m[0]*x+m[1]*y+m[2]*z+m[3];
                verts[p+1]=m[4]*x+m[5]*y+m[6]*z+m[7];
                verts[p+2]=m[8]*x+m[9]*y+m[10]*z+m[11];
            }
        }

        float bindMinX=Float.POSITIVE_INFINITY,bindMaxX=Float.NEGATIVE_INFINITY;
        float bindMinY=Float.POSITIVE_INFINITY,bindMaxY=Float.NEGATIVE_INFINITY;
        float bindMinZ=Float.POSITIVE_INFINITY,bindMaxZ=Float.NEGATIVE_INFINITY;
        for(int i=0;i<numVertices;i++){
            float x=verts[i*3],y=verts[i*3+1],z=verts[i*3+2];
            bindMinX=Math.min(bindMinX,x);bindMaxX=Math.max(bindMaxX,x);
            bindMinY=Math.min(bindMinY,y);bindMaxY=Math.max(bindMaxY,y);
            bindMinZ=Math.min(bindMinZ,z);bindMaxZ=Math.max(bindMaxZ,z);
        }

        int[] tris=new int[out.size()];for(int i=0;i<tris.length;i++)tris[i]=out.get(i);
        float[] triUvs=new float[outUv.size()];for(int i=0;i<triUvs.length;i++)triUvs[i]=outUv.get(i);
        int[] triPatterns=new int[tris.length/3];
        for(int i=0;i<triPatterns.length;i++)triPatterns[i]=(i<triPatternList.size()?triPatternList.get(i):0);
        int patternCount=Math.max(1,maxMeta);
        return new MbacModel(verts,rawVerts,bones,tris,triUvs,triPatterns,patternCount,texturedTriangles,
                rawMinX,rawMaxX,rawMinY,rawMaxY,rawMinZ,rawMaxZ,
                bindMinX,bindMaxX,bindMinY,bindMaxY,bindMinZ,bindMaxZ);
    }

    private static int patternMaskForFace(int ordinal,int[] counts){
        if(counts==null || counts.length==0)return 0;
        int base=0;
        for(int p=0;p<counts.length;p++){
            int end=base+Math.max(0,counts[p]);
            if(ordinal>=base && ordinal<end)return p==0?0:(1<<p);
            base=end;
        }
        return 0;
    }

    String rawBounds(){return range(rawMinX,rawMaxX)+"/"+range(rawMinY,rawMaxY)+"/"+range(rawMinZ,rawMaxZ);}
    String bindBounds(){return range(bindMinX,bindMaxX)+"/"+range(bindMinY,bindMaxY)+"/"+range(bindMinZ,bindMaxZ);}
    private static String range(float a,float b){return ((int)a)+".."+((int)b);}

    private static void triFlat(ArrayList<Integer> out,ArrayList<Float> uv,int a,int b,int c){
        out.add(a);out.add(b);out.add(c);
        for(int i=0;i<6;i++)uv.add(Float.NaN);
    }
    private static void triUv(ArrayList<Integer> out,ArrayList<Float> uv,int a,int b,int c,
                              float au,float av,float bu,float bv,float cu,float cv){
        out.add(a);out.add(b);out.add(c);
        uv.add(au);uv.add(av);uv.add(bu);uv.add(bv);uv.add(cu);uv.add(cv);
    }
    private static float[] boneWorld(int i,Bone[] bones,float[][] worlds){
        if(worlds[i]!=null)return worlds[i]; Bone b=bones[i];float[] local=toFloat(b.local);
        if(b.parent<0 || b.parent>=bones.length)return worlds[i]=local;
        return worlds[i]=mul34(boneWorld(b.parent,bones,worlds),local);
    }
    private static float[] toFloat(int[] m){
        float[] f=new float[12];for(int i=0;i<12;i++)f[i]=(i%4==3)?m[i]:(m[i]/4096.0f);return f;
    }
    private static float[] mul34(float[] a,float[] b){
        float[] o=new float[12];
        for(int r=0;r<3;r++){
            for(int col=0;col<3;col++)o[r*4+col]=a[r*4]*b[col]+a[r*4+1]*b[4+col]+a[r*4+2]*b[8+col];
            o[r*4+3]=a[r*4]*b[3]+a[r*4+1]*b[7]+a[r*4+2]*b[11]+a[r*4+3];
        }
        return o;
    }
    static final class Bone{final int start,count,parent;final int[] local;Bone(int s,int c,int p,int[] m){start=s;count=c;parent=p;local=m;}}
    private static final class Cursor{
        final byte[] d;int pos;Cursor(byte[] x){d=x;}
        int u8()throws IOException{if(pos>=d.length)throw new EOFException();return d[pos++]&255;}
        int u16()throws IOException{int a=u8(),b=u8();return a|(b<<8);} int s16()throws IOException{int v=u16();return v>=32768?v-65536:v;}
    }
    private static final class BitReader{
        final byte[] d;int pos,have;long bits;BitReader(byte[] dd,int p){d=dd;pos=p;}
        int u(int n)throws IOException{while(have<n){if(pos>=d.length)throw new EOFException();bits|=((long)(d[pos++]&255))<<have;have+=8;}int v=(int)(bits&((1L<<n)-1));bits>>>=n;have-=n;return v;}
        int s(int n)throws IOException{int v=u(n);int sign=1<<(n-1);return (v&sign)!=0?v-(1<<n):v;}
        int bytePos(){return pos;}
    }
}
