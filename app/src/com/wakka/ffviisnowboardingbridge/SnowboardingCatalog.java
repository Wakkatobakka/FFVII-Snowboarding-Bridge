package com.wakka.ffviisnowboardingbridge;
import android.content.Context;
import com.wakka.bridge.BridgeBackend;
import com.wakka.snowboardbridge.SnowboardBackend;
/** Single-title public release catalog. */
public final class SnowboardingCatalog {
    private static BridgeBackend game;
    private SnowboardingCatalog() {}
    public static synchronized BridgeBackend game(Context c) {
        if(game==null) game=new SnowboardBackend(c);
        return game;
    }
}
