package com.wakka.ffviisnowboardingbridge;
import android.content.Intent;
import com.wakka.bridge.BridgeBackend;
import com.wakka.bridge.BridgeLauncherActivity;
public final class LauncherActivity extends BridgeLauncherActivity {
    protected BridgeBackend backend() { return SnowboardingCatalog.game(this); }
    protected Intent gameIntent() { return new Intent(this,GameActivity.class); }
    protected Intent toolsIntent() { return new Intent(this,ToolsActivity.class); }
}
