package com.alauncher;

import android.graphics.Bitmap;

/** One launchable activity. */
public class AppItem {
    public String pkg;
    public String cls;
    public String label;
    public String sortKey;
    public Bitmap icon;
    public Bitmap mono;
    public int color = 0xFF6AE3FF;
    public long lastUsed;
    public int launchCount;
    public int badge;
    public String customLabel;

    public String key() { return pkg + "/" + cls; }

    public String title() {
        return customLabel != null && customLabel.length() > 0 ? customLabel : label;
    }
}
