package com.wakka.bridge;

import android.app.Activity;
import android.view.View;
import java.io.InputStream;

/** One live title session. Implementations must never boot from snapshot(). */
public interface BridgeBackend {
    GameSpec spec();
    boolean ready();
    String readiness();
    /** Public releases may accept a locally built, title-specific payload ZIP. */
    default boolean supportsPayloadImport() { return false; }
    default String payloadImportLabel() { return "IMPORT GAME DATA"; }
    default String importPayload(InputStream in) throws Exception { throw new UnsupportedOperationException("Payload import unavailable"); }
    /** Optional import/checkpoint/native tools appear within the same Tools screen. */
    default void addToolSections(Activity activity, android.widget.LinearLayout parent) {}
    View attachDisplay(Activity activity);
    View createControls(Activity activity, boolean keypad);
    void detachViews();
    void start();
    boolean started();
    boolean running();
    void setForeground(boolean foreground);
    void setUserPaused(boolean paused);
    boolean userPaused();
    void releaseInput();
    boolean hardwareKey(int androidKeyCode, boolean down);
    String status();
    String snapshot();
}
