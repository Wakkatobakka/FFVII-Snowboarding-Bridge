package com.nttdocomo.ui.graphics3d;
public class Figure extends Object3D implements DrawableObject3D {
    public final String name;
    public Texture texture;
    public ActionTable action;
    public int actionIndex;
    public int time;
    /** MascotCapsule appearance mask. 0 means base geometry only; bits enable alternate pattern groups. */
    public int pattern;
    final MbacModel model;
    float[] poseVertices;
    public Figure(String n){this(n,null);} Figure(String n,MbacModel m){name=n;model=m;pattern=0;}
    public void setAction(ActionTable a,int i){
        if(a!=null && a.getNumActions()>0 && (i<0 || i>=a.getNumActions())) throw new IllegalArgumentException();
        action=a; actionIndex=i; setTime(0);
    }
    public ActionTable getActionTable(){return action;}
    public int getNumPatterns(){return model==null?1:model.numPatterns;}
    public void setPattern(int p){pattern=p;}
    public void setTexture(Texture t){texture=t;}
    public void setTime(int t){
        time=t;
        if(action!=null) pattern=action.patternFor(actionIndex,t,pattern);
    }
}
