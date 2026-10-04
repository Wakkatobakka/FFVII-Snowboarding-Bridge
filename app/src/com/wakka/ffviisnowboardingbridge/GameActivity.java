package com.wakka.ffviisnowboardingbridge;
import android.content.Intent;
import com.wakka.bridge.BridgeBackend;
import com.wakka.bridge.BridgeGameActivity;
public final class GameActivity extends BridgeGameActivity {
    protected BridgeBackend backend() { return SnowboardingCatalog.game(this); }
    protected Intent toolsIntent() { return new Intent(this,ToolsActivity.class); }
}
