package com.alauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** All tunables of A Launcher live here. */
public class Prefs {
    private static Prefs I;
    public final SharedPreferences sp;

    public static Prefs get(Context c) {
        if (I == null) I = new Prefs(c.getApplicationContext());
        return I;
    }

    private Prefs(Context c) {
        sp = c.getSharedPreferences("alauncher", Context.MODE_PRIVATE);
    }

    public int i(String k, int d) { return sp.getInt(k, d); }
    public void setI(String k, int v) { sp.edit().putInt(k, v).apply(); }
    public boolean b(String k, boolean d) { return sp.getBoolean(k, d); }
    public void setB(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    public String s(String k, String d) { return sp.getString(k, d); }
    public void setS(String k, String v) { sp.edit().putString(k, v).apply(); }

    public Set<String> set(String k) {
        return new HashSet<>(sp.getStringSet(k, new HashSet<String>()));
    }
    public void setSet(String k, Set<String> v) { sp.edit().putStringSet(k, v).apply(); }

    public void toggleIn(String k, String value) {
        Set<String> s = set(k);
        if (!s.remove(value)) s.add(value);
        setSet(k, s);
    }

    // ---- keys & defaults ----
    public int gridCols() { return i("grid_cols", 4); }
    public int gridRows() { return i("grid_rows", 5); }
    public int iconSize() { return i("icon_size", 100); }          // percent
    public int iconShape() { return i("icon_shape", 0); }          // 0 squircle..4 hex
    public boolean homeLabels() { return b("home_labels", true); }
    public boolean drawerLabels() { return b("drawer_labels", true); }
    public int drawerCols() { return i("drawer_cols", 4); }
    public int accent() { return i("accent", 0xFF6AE3FF); }
    public boolean dynamicAccent() { return b("dyn_accent", true); }
    public boolean blurPanels() { return b("blur", true); }
    public boolean parallax() { return b("parallax", true); }
    public boolean particles() { return b("particles", true); }
    public boolean smartDock() { return b("smart_dock", true); }
    public boolean badges() { return b("badges", true); }
    public boolean glyphs() { return b("glyphs", true); }
    public boolean monoIcons() { return b("mono_icons", false); }
    public boolean doubleTapLock() { return b("dt_lock", true); }
    public boolean statusClock() { return b("clock", true); }
    public boolean searchBar() { return b("search_bar", true); }
    public int dockCount() { return i("dock_count", 5); }

    public static final String HIDDEN = "hidden_apps";
    public static final String FAVS = "radial_favs";
    public static final String LOCKED = "locked_apps";
}
