import com.wakka.bridge.InputLatch;
import com.wakka.bridge.SessionGate;
import com.wakka.snowboardbridge.SnowboardInput;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Invariants that guard actual regressions: overlapping contacts, keypad-derived custom mapping and pause reasons. */
public final class BehaviorChecks {
    private static int assertions;
    private static void check(boolean condition,String why) { assertions++;if(!condition)throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        InputLatch input=new InputLatch();
        check(input.set(1,SnowboardInput.LEFT)==SnowboardInput.LEFT,"edge is held");
        input.set(2,SnowboardInput.SELECT); input.set(3,SnowboardInput.SELECT);
        check(input.remove(2)==(SnowboardInput.LEFT|SnowboardInput.SELECT),"lifting one jump contact must not release another");
        check(input.remove(1)==SnowboardInput.SELECT,"lifting steering must not release jump");
        input.set(-101,SnowboardInput.SELECT);
        check(input.remove(3)==SnowboardInput.SELECT,"hardware and touch share ownership safely");
        check(input.clear()==0&&input.contacts()==0,"focus loss must release every owner");

        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_EDGE_L,true)==SnowboardInput.LEFT,"race left edge stays original LEFT");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_TURN_L,true)==SnowboardInput.KEY_4,"race left turn stays original 4");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_KICK,true)==SnowboardInput.UP,"race kick stays original UP");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_JUMP,true)==SnowboardInput.SELECT,"race jump stays original confirm");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_BRAKE,true)==SnowboardInput.DOWN,"race brake stays original DOWN");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_TURN_R,true)==SnowboardInput.KEY_6,"race right turn stays original 6");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_EDGE_R,true)==SnowboardInput.RIGHT,"race right edge stays original RIGHT");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_SOFT1,true)==SnowboardInput.SOFT1,"left soft key preserved");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_SOFT2,true)==SnowboardInput.SOFT2,"right soft key preserved");

        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_EDGE_L,false)==SnowboardInput.LEFT,"menu left uses cardinal LEFT");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_KICK,false)==SnowboardInput.UP,"menu up uses cardinal UP");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_JUMP,false)==SnowboardInput.SELECT,"menu OK uses confirm");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_BRAKE,false)==SnowboardInput.DOWN,"menu down uses cardinal DOWN");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_EDGE_R,false)==SnowboardInput.RIGHT,"menu right uses cardinal RIGHT");
        check(SnowboardInput.customMask(SnowboardInput.CUSTOM_TURN_L,false)==0&&SnowboardInput.customMask(SnowboardInput.CUSTOM_TURN_R,false)==0,
                "numeric turn keys stay inactive in menus");
        check(SnowboardInput.racing("Retry","Menu")&&!SnowboardInput.racing("Select","Back"),"soft labels determine race mode");

        SessionGate gate=new SessionGate();gate.foreground(true);gate.userPaused(true);
        gate.foreground(false);gate.foreground(true);
        check(gate.blocked(),"foreground changes must not erase the user's pause");
        CountDownLatch entered=new CountDownLatch(1),released=new CountDownLatch(1);
        Thread worker=new Thread(()->{entered.countDown();gate.awaitRunning();released.countDown();});worker.start();
        check(entered.await(1,TimeUnit.SECONDS),"worker entered");
        check(!released.await(40,TimeUnit.MILLISECONDS),"game remains blocked while paused");
        gate.userPaused(false);check(released.await(1,TimeUnit.SECONDS),"resume wakes game without replacing it");
        gate.foreground(false);check(gate.blocked(),"background independently pauses game");
        System.out.println("PASS: "+assertions+" behavioral assertions");
    }
}
