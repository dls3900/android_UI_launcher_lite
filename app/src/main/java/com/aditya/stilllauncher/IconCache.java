package com.aditya.stilllauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class IconCache {
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    private final Map<String, List<Runnable>> loading = new HashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    Bitmap get(AppEntry entry, int density) {
        String key = entry.key + ":" + density;
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        Bitmap bitmap = load(entry, density);
        cache.put(key, bitmap);
        return bitmap;
    }

    Bitmap getCached(AppEntry entry, int density) {
        return cache.get(entry.key + ":" + density);
    }

    void request(AppEntry entry, int density, Runnable onReady) {
        String key = entry.key + ":" + density;
        synchronized (loading) {
            if (cache.get(key) != null) { main.post(onReady); return; }
            List<Runnable> callbacks = loading.get(key);
            if (callbacks != null) { callbacks.add(onReady); return; }
            callbacks = new ArrayList<>();
            callbacks.add(onReady);
            loading.put(key, callbacks);
        }
        worker.execute(() -> {
            try {
                cache.put(key, load(entry, density));
            } catch (RuntimeException ignored) {
                // An app can be uninstalled before its icon finishes loading.
            } finally {
                List<Runnable> callbacks;
                synchronized (loading) { callbacks = loading.remove(key); }
                if (callbacks != null) main.post(() -> {
                    for (Runnable callback : callbacks) callback.run();
                });
            }
        });
    }

    private Bitmap load(AppEntry entry, int density) {
        int size = Math.max(48, Math.round(36f * density / 160f));
        Drawable icon = entry.info.getBadgedIcon(density).mutate();
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        icon.setBounds(0, 0, size, size);
        icon.draw(canvas);
        return bitmap;
    }

    void clear() { cache.evictAll(); }
    void close() { worker.shutdownNow(); synchronized (loading) { loading.clear(); } clear(); }
}
