package io.projectir.mobile;
import android.app.Application;
public final class ProjectIrApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        ShellBridge.initialize(this);
    }
}
