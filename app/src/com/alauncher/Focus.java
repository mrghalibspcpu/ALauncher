package com.alauncher;

import android.content.Context;

import java.util.Set;

/** Focus Mode: temporarily greys out and blocks the apps you marked distracting. */
public class Focus {

    public static final String KEY_UNTIL = "focus_until";
    public static final String KEY_APPS = "focus_apps";

    public static boolean active(Context c) {
        return Prefs.get(c).sp.getLong(KEY_UNTIL, 0) > System.currentTimeMillis();
    }

    public static long remainingMin(Context c) {
        long d = Prefs.get(c).sp.getLong(KEY_UNTIL, 0) - System.currentTimeMillis();
        return d <= 0 ? 0 : (d / 60000 + 1);
    }

    public static void toggle(Context c, int minutes) {
        if (active(c)) stop(c); else start(c, minutes);
    }

    public static void start(Context c, int minutes) {
        Prefs.get(c).sp.edit()
                .putLong(KEY_UNTIL, System.currentTimeMillis() + minutes * 60000L).apply();
    }

    public static void stop(Context c) {
        Prefs.get(c).sp.edit().putLong(KEY_UNTIL, 0).apply();
    }

    public static boolean blocked(Context c, AppItem a) {
        if (!active(c)) return false;
        Set<String> s = Prefs.get(c).set(KEY_APPS);
        return s.contains(a.key());
    }

    public static void toggleApp(Context c, AppItem a) {
        Prefs.get(c).toggleIn(KEY_APPS, a.key());
    }
}
