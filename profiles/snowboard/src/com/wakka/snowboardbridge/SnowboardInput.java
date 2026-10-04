package com.wakka.snowboardbridge;

/** Snowboarding input semantics, independent of Android display density and shell design. */
public final class SnowboardInput {
    public static final int KEY_4=1<<4, KEY_6=1<<6,
            LEFT=1<<16, UP=1<<17, RIGHT=1<<18, DOWN=1<<19,
            SELECT=1<<20, SOFT1=1<<21, SOFT2=1<<22;

    public static final int CUSTOM_EDGE_L=0, CUSTOM_KICK=1, CUSTOM_EDGE_R=2,
            CUSTOM_TURN_L=3, CUSTOM_JUMP=4, CUSTOM_TURN_R=5,
            CUSTOM_BRAKE=6, CUSTOM_SOFT1=7, CUSTOM_SOFT2=8;

    private SnowboardInput() {}

    public static boolean racing(String soft1,String soft2) {
        return "Retry".equalsIgnoreCase(soft1)&&"Menu".equalsIgnoreCase(soft2);
    }

    /**
     * Keypad-derived custom deck. During a race every slot is one original digital input.
     * In menus, the five cardinal/confirm slots stay useful while numeric 4/6 are disabled.
     */
    public static int customMask(int slot,boolean racing) {
        switch(slot) {
            case CUSTOM_EDGE_L:return LEFT;
            case CUSTOM_KICK:return UP;
            case CUSTOM_EDGE_R:return RIGHT;
            case CUSTOM_TURN_L:return racing?KEY_4:0;
            case CUSTOM_JUMP:return SELECT;
            case CUSTOM_TURN_R:return racing?KEY_6:0;
            case CUSTOM_BRAKE:return DOWN;
            case CUSTOM_SOFT1:return SOFT1;
            case CUSTOM_SOFT2:return SOFT2;
            default:return 0;
        }
    }

    public static String names(int mask) {
        if(mask==0) return "NONE";
        StringBuilder s=new StringBuilder();
        for(int i=0;i<23;i++) if((mask&(1<<i))!=0) {
            String name=i<=9?"NUM"+i:i==10?"STAR":i==11?"HASH":i==16?"LEFT":i==17?"UP":i==18?"RIGHT":i==19?"DOWN":i==20?"CONFIRM":i==21?"SOFT1":i==22?"SOFT2":"BIT"+i;
            if(s.length()>0)s.append('+');s.append(name);
        }
        return s.toString();
    }
}
