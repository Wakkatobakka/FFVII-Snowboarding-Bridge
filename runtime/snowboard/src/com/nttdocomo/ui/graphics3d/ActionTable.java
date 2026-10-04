package com.nttdocomo.ui.graphics3d;

import java.io.*;

/**
 * MascotCapsule/DoJa MTRA action table reader.
 *
 * Snowboarding's .mtra files are version 5 and use the same fixed-point
 * transform/keyframe layout as MascotCapsule Micro3D. Frame values passed by
 * Figure.setTime are 16.16 fixed point; animation interpolation operates in
 * 12.12 after a four-bit precision reduction, matching the reference runtime.
 */
public class ActionTable extends Object3D {
    public final String name;
    final Action[] actions;
    final int boneCount;

    public ActionTable(String n){ name=n; actions=new Action[0]; boneCount=0; }

    ActionTable(String n, byte[] data) throws IOException {
        name=n;
        Cursor c=new Cursor(data);
        if(c.u8()!='M' || c.u8()!='T') throw new IOException("not MTRA");
        int version=c.u8();
        if(c.u8()!=0 || version<2 || version>5) throw new IOException("unsupported MTRA version "+version);
        int numActions=c.u16();
        boneCount=c.u16();
        for(int i=0;i<8;i++) c.u16(); // per-transform-type counts (allocation hints)
        c.i32(); // packed data size hint
        actions=new Action[numActions];
        for(int a=0;a<numActions;a++){
            Action act=new Action(c.u16(),boneCount);
            actions[a]=act;
            for(int b=0;b<boneCount;b++) act.bones[b]=readBone(c);
            if(version>=5){
                int count=c.u16();
                act.dynamic=new int[count*2];
                for(int i=0;i<count;i++){
                    act.dynamic[i*2]=c.u16();
                    act.dynamic[i*2+1]=c.i32();
                }
            }
        }
    }

    public int getNumActions(){ return actions.length; }
    public int getMaxFrame(int action){
        if(action<0 || action>=actions.length) throw new IllegalArgumentException();
        return actions[action].keyFrames << 16;
    }

    boolean compatible(MbacModel model){ return model!=null && model.bones!=null && boneCount==model.bones.length; }

    /** Deform raw MBAC vertices into out according to the selected action/time. */
    void deformInto(MbacModel model,int actionIndex,int frame,float[] out){
        if(!compatible(model) || actionIndex<0 || actionIndex>=actions.length || out==null || out.length<model.rawVertices.length){
            System.arraycopy(model.vertices,0,out,0,Math.min(out.length,model.vertices.length));
            return;
        }
        Action act=actions[actionIndex];
        int n=model.bones.length;
        int[][] worlds=new int[n][];
        for(int i=0;i<n;i++){
            MbacModel.Bone bone=model.bones[i];
            int[] local=act.bones[i].at(frame,bone.local);
            if(bone.parent>=0 && bone.parent<n) worlds[i]=mul(worlds[bone.parent],local);
            else worlds[i]=local;
        }
        System.arraycopy(model.rawVertices,0,out,0,model.rawVertices.length);
        for(int i=0;i<n;i++){
            MbacModel.Bone bone=model.bones[i]; int[] m=worlds[i];
            int end=Math.min(model.vertexCount,bone.start+bone.count);
            for(int v=bone.start;v<end;v++){
                int p=v*3;
                int x=Math.round(model.rawVertices[p]);
                int y=Math.round(model.rawVertices[p+1]);
                int z=Math.round(model.rawVertices[p+2]);
                out[p]  =((x*m[0]+y*m[1]+z*m[2]+2048)>>12)+m[3];
                out[p+1]=((x*m[4]+y*m[5]+z*m[6]+2048)>>12)+m[7];
                out[p+2]=((x*m[8]+y*m[9]+z*m[10]+2048)>>12)+m[11];
            }
        }
    }

    int patternFor(int actionIndex,int frame,int fallback){
        if(actionIndex<0||actionIndex>=actions.length)return fallback;
        int[] d=actions[actionIndex].dynamic;if(d==null)return fallback;
        int f=frame<0?0:(frame>>>16);
        for(int i=d.length-2;i>=0;i-=2) if(d[i]<=f)return d[i+1];
        return fallback;
    }

    private static BoneAnim readBone(Cursor c)throws IOException{
        BoneAnim b=new BoneAnim(c.u8());
        switch(b.type){
            case 0: b.constant=new int[12];for(int i=0;i<12;i++)b.constant[i]=c.s16();break;
            case 1: break;
            case 2: b.translate=frames3(c);b.scale=frames3(c);b.rotate=frames3(c);b.roll=frames1(c);break;
            case 3: b.translateConst=new int[]{c.s16(),c.s16(),c.s16()};b.rotate=frames3(c);b.rollConst=c.s16();break;
            case 4: b.rotate=frames3(c);b.roll=frames1(c);break;
            case 5: b.rotate=frames3(c);break;
            case 6: b.translate=frames3(c);b.rotate=frames3(c);b.roll=frames1(c);break;
            default: throw new IOException("invalid MTRA bone type "+b.type);
        }
        return b;
    }
    private static int[] frames3(Cursor c)throws IOException{
        int n=c.u16();int[] a=new int[n*4];
        for(int i=0;i<n;i++){int p=i*4;a[p]=c.u16();a[p+1]=c.s16();a[p+2]=c.s16();a[p+3]=c.s16();}
        return a;
    }
    private static int[] frames1(Cursor c)throws IOException{
        int n=c.u16();int[] a=new int[n*2];
        for(int i=0;i<n;i++){int p=i*2;a[p]=c.u16();a[p+1]=c.s16();}
        return a;
    }

    static final class Action{
        final int keyFrames;final BoneAnim[] bones;int[] dynamic;
        Action(int k,int n){keyFrames=k;bones=new BoneAnim[n];}
    }
    static final class BoneAnim{
        final int type;int[] constant,translate,scale,rotate,roll,translateConst;int rollConst;
        BoneAnim(int t){type=t;}
        int[] at(int frame16,int[] bind){
            int frame12=frame16>>4;
            if(type==0){return bind==null?constant.clone():mul(bind,constant);}
            if(type==1){return bind==null?identity():bind.clone();}
            int[] t=identity();int[] v;
            switch(type){
                case 2:
                    v=interp3(frame12,translate);t[3]=v[0];t[7]=v[1];t[11]=v[2];
                    v=interp3(frame12,rotate);int tx=t[3],ty=t[7],tz=t[11];t=rotation(v);t[3]=tx;t[7]=ty;t[11]=tz;
                    applyRoll(t,interp1(frame12,roll));applyScale(t,interp3(frame12,scale));break;
                case 3:
                    v=interp3(frame12,rotate);t=rotation(v);t[3]=translateConst[0];t[7]=translateConst[1];t[11]=translateConst[2];
                    applyRoll(t,rollConst);break;
                case 4:
                    t=rotation(interp3(frame12,rotate));applyRoll(t,interp1(frame12,roll));break;
                case 5:
                    t=rotation(interp3(frame12,rotate));break;
                case 6:
                    v=interp3(frame12,translate);t=rotation(interp3(frame12,rotate));t[3]=v[0];t[7]=v[1];t[11]=v[2];
                    applyRoll(t,interp1(frame12,roll));break;
            }
            return bind==null?t:mul(bind,t);
        }
    }

    private static int[] interp3(int frame,int[] a){
        if(a==null||a.length<4)return new int[]{0,0,0};
        int fi=frame>>12,n=a.length/4,last=n-1,lp=last*4;
        if(fi>=a[lp])return new int[]{a[lp+1],a[lp+2],a[lp+3]};
        for(int i=(last-1)*4;i>=0;i-=4){
            int prev=a[i];if(prev>fi)continue;
            if(prev==fi)return new int[]{a[i+1],a[i+2],a[i+3]};
            int next=a[i+4],den=next-prev;if(den==0)return new int[]{a[i+1],a[i+2],a[i+3]};
            int d=(frame-(prev<<12))/den;
            return new int[]{a[i+1]+(((a[i+5]-a[i+1])*d)>>12),a[i+2]+(((a[i+6]-a[i+2])*d)>>12),a[i+3]+(((a[i+7]-a[i+3])*d)>>12)};
        }
        return new int[]{a[1],a[2],a[3]};
    }
    private static int interp1(int frame,int[] a){
        if(a==null||a.length<2)return 0;
        int fi=frame>>12,n=a.length/2,last=n-1,lp=last*2;
        if(fi>=a[lp])return a[lp+1];
        for(int i=(last-1)*2;i>=0;i-=2){
            int prev=a[i];if(prev>fi)continue;if(prev==fi)return a[i+1];
            int next=a[i+2],den=next-prev;if(den==0)return a[i+1];
            int d=(frame-(prev<<12))/den;return a[i+1]+(((a[i+3]-a[i+1])*d)>>12);
        }
        return a[1];
    }

    private static int[] rotation(int[] vec){
        int[] v=unit(vec);int x=v[0],y=v[1],z=v[2];int xx=(x*x+2048)>>12,yy=(y*y+2048)>>12;int[] m=identity();
        if(xx>0||yy>0){
            int a=((4096-z)<<12)/(yy+xx);int b=(a * -((x*y+2048)>>12))>>12;
            m[0]=z+((yy*a+2048)>>12);m[1]=b;m[2]=x;
            m[4]=b;m[5]=z+((xx*a+2048)>>12);m[6]=y;
            m[8]=-x;m[9]=-y;m[10]=z;
        }else{m[0]=4096;m[1]=m[2]=m[4]=m[6]=m[8]=m[9]=0;m[5]=z;m[10]=z;}
        return m;
    }
    private static int[] unit(int[] v){
        double x=v[0],y=v[1],z=v[2],len=Math.sqrt(x*x+y*y+z*z);if(len==0)return new int[]{0,0,4096};
        return new int[]{(int)Math.round(x*4096.0/len),(int)Math.round(y*4096.0/len),(int)Math.round(z*4096.0/len)};
    }
    private static int sin(int p){return (int)Math.floor(Math.sin((p&4095)*Math.PI/2048.0)*4096.0+0.5);}
    private static int cos(int p){return sin(p+1024);}
    private static void applyRoll(int[] m,int angle){
        int s=sin(angle),c=cos(angle),m00=m[0],m01=m[1],m10=m[4],m11=m[5],m20=m[8],m21=m[9];
        m[0]=(m00*c+m01*s+2048)>>12;m[1]=(m01*c-m00*s+2048)>>12;
        m[4]=(m10*c+m11*s+2048)>>12;m[5]=(m11*c-m10*s+2048)>>12;
        m[8]=(m20*c+m21*s+2048)>>12;m[9]=(m21*c-m20*s+2048)>>12;
    }
    private static void applyScale(int[] m,int[] s){
        for(int r=0;r<3;r++){m[r*4]=(m[r*4]*s[0]+2048)>>12;m[r*4+1]=(m[r*4+1]*s[1]+2048)>>12;m[r*4+2]=(m[r*4+2]*s[2]+2048)>>12;}
    }
    static int[] mul(int[] a,int[] b){
        int[] o=new int[12];
        for(int r=0;r<3;r++){
            int p=r*4,l0=a[p],l1=a[p+1],l2=a[p+2];
            o[p]=(l0*b[0]+l1*b[4]+l2*b[8]+2048)>>12;
            o[p+1]=(l0*b[1]+l1*b[5]+l2*b[9]+2048)>>12;
            o[p+2]=(l0*b[2]+l1*b[6]+l2*b[10]+2048)>>12;
            o[p+3]=((l0*b[3]+l1*b[7]+l2*b[11]+2048)>>12)+a[p+3];
        }
        return o;
    }
    private static int[] identity(){return new int[]{4096,0,0,0,0,4096,0,0,0,0,4096,0};}

    private static final class Cursor{
        final byte[] d;int p;Cursor(byte[] b){d=b;}
        int u8()throws IOException{if(p>=d.length)throw new EOFException();return d[p++]&255;}
        int u16()throws IOException{return u8()|(u8()<<8);}int s16()throws IOException{int v=u16();return v>=32768?v-65536:v;}
        int i32()throws IOException{return u8()|(u8()<<8)|(u8()<<16)|(u8()<<24);}
    }
}
