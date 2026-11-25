package io.projectir.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // MY_PACKAGE_REPLACED: an APK update stops the service; bring it back.
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        try {
            ConfigStore.Config config = new ConfigStore(context).load();
            if (config.autostart && config.ready()) {
                context.startForegroundService(new Intent(context, AgentService.class));
            }
        } catch (Throwable t) {
            Log.w("ProjectIRBoot", "boot start deferred", t);
        }
    }
}
