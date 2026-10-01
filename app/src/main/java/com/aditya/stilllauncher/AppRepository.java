package com.aditya.stilllauncher;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class AppRepository {
    private final LauncherApps launcherApps;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LauncherApps.Callback callback;
    private Consumer<List<AppEntry>> listener;
    private volatile boolean closed;

    AppRepository(Context context, Consumer<List<AppEntry>> listener) {
        launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        this.listener = listener;
        callback = new LauncherApps.Callback() {
            @Override public void onPackageRemoved(String packageName, UserHandle user) { refresh(); }
            @Override public void onPackageAdded(String packageName, UserHandle user) { refresh(); }
            @Override public void onPackageChanged(String packageName, UserHandle user) { refresh(); }
            @Override public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) { refresh(); }
            @Override public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) { refresh(); }
        };
        launcherApps.registerCallback(callback);
    }

    void refresh() {
        if (closed) return;
        worker.execute(() -> {
            List<AppEntry> result = new ArrayList<>();
            try {
                for (UserHandle profile : launcherApps.getProfiles()) {
                    for (LauncherActivityInfo info : launcherApps.getActivityList(null, profile)) {
                        result.add(new AppEntry(info));
                    }
                }
                // Section first, so every "#" app sits in one group the rail can jump to.
                Collections.sort(result, Comparator.comparing((AppEntry a) -> a.section)
                        .thenComparing(a -> a.label, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(a -> a.key));
            } catch (RuntimeException ignored) {
                // Keep the last good list if a profile disappears during a scan.
                return;
            }
            main.post(() -> { if (!closed && listener != null) listener.accept(result); });
        });
    }

    void launch(AppEntry entry) {
        launcherApps.startMainActivity(entry.component, entry.user, null, null);
    }

    void close() {
        closed = true;
        listener = null;
        launcherApps.unregisterCallback(callback);
        worker.shutdownNow();
    }
}
