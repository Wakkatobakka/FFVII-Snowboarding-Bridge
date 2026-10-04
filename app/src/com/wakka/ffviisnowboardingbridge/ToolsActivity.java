package com.wakka.ffviisnowboardingbridge;
import com.wakka.bridge.BridgeBackend;
import com.wakka.bridge.BridgeToolsActivity;
public final class ToolsActivity extends BridgeToolsActivity {
    protected BridgeBackend backend() { return SnowboardingCatalog.game(this); }
}
