package com.alauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The brain: knows every app, how often / when you use it, which are hidden,
 * and predicts what you want next (Flow Dock + smart suggestions).
 */
public class AppStore {

    public interface Listener { void onAppsChanged(); }

    private static AppStore I;
    private final Context ctx;
    private final Prefs prefs;

    public final List<AppItem> all = new ArrayList<>();
    public final Map<String, AppItem> byKey = new HashMap<>();
    public static final Map<String, Integer> badges = new HashMap<>();

    private JSONObject usage = new JSONObject();
    private Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    public boolean loaded;

    public static AppStore get(Context c) {
        if (I == null) I = new AppStore(c.getApplicationContext());
        return I;
    }

    private AppStore(Context c) {
        ctx = c;
        prefs = Prefs.get(c);
        try { usage = new JSONObject(prefs.s("usage", "{}")); } catch (Throwable t) { usage = new JSONObject(); }
    }

    public void setListener(Listener l) { listener = l; }

    public void reload() {
        new Thread(new Runnable() {
            public void run() {
                loadSync();
                ui.post(new Runnable() {
                    public void run() { if (listener != null) listener.onAppsChanged(); }
                });
            }
        }, "app-load").start();
    }

    private void loadSync() {
        PackageManager pm = ctx.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        List<AppItem> result = new ArrayList<>();
        Map<String, AppItem> map = new HashMap<>();
        int shape = prefs.iconShape();
        String pack = prefs.iconPack();
        if (!pack.isEmpty()) IconPacks.ensureLoaded(ctx, pack);
        for (ResolveInfo ri : ris) {
            try {
                AppItem a = new AppItem();
                a.pkg = ri.activityInfo.packageName;
                a.cls = ri.activityInfo.name;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.sortKey = a.label.toLowerCase(Locale.getDefault());
                android.graphics.drawable.Drawable raw = null;
                if (!pack.isEmpty()) raw = IconPacks.icon(ctx, pack, a.pkg, a.cls);
                boolean themed = raw != null;
                if (raw == null) raw = ri.loadIcon(pm);
                a.icon = themed ? IconFactory.fromPack(raw) : IconFactory.make(ctx, raw, shape);
                a.color = U.boost(U.dominantColor(a.icon), 1.15f, 1.1f);
                a.customLabel = prefs.s("name_" + a.key(), null);
                JSONObject u = usage.optJSONObject(a.key());
                if (u != null) {
                    a.launchCount = u.optInt("c");
                    a.lastUsed = u.optLong("l");
                }
                result.add(a);
                map.put(a.key(), a);
            } catch (Throwable ignored) { }
        }
        final Collator col = Collator.getInstance();
        Collections.sort(result, new Comparator<AppItem>() {
            public int compare(AppItem a, AppItem b) { return col.compare(a.title(), b.title()); }
        });
        synchronized (all) {
            all.clear(); all.addAll(result);
            byKey.clear(); byKey.putAll(map);
        }
        loaded = true;
    }

    public AppItem find(String key) { return byKey.get(key); }

    public List<AppItem> visible() {
        Set<String> hidden = prefs.set(Prefs.HIDDEN);
        List<AppItem> out = new ArrayList<>();
        for (AppItem a : all) if (!hidden.contains(a.key())) out.add(a);
        return out;
    }

    // ------------------------------------------------------------------ usage
    public void noteLaunch(AppItem a) {
        try {
            JSONObject u = usage.optJSONObject(a.key());
            if (u == null) u = new JSONObject();
            u.put("c", u.optInt("c") + 1);
            u.put("l", System.currentTimeMillis());
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            JSONArray h = u.optJSONArray("h");
            if (h == null || h.length() != 24) {
                h = new JSONArray();
                for (int i = 0; i < 24; i++) h.put(0);
            }
            h.put(hour, h.optInt(hour) + 1);
            u.put("h", h);
            usage.put(a.key(), u);
            prefs.setS("usage", usage.toString());
            a.launchCount++;
            a.lastUsed = System.currentTimeMillis();
        } catch (Throwable ignored) { }
    }

    public int hourAffinity(AppItem a, int hour) {
        JSONObject u = usage.optJSONObject(a.key());
        if (u == null) return 0;
        JSONArray h = u.optJSONArray("h");
        if (h == null || h.length() != 24) return 0;
        int s = h.optInt(hour) * 3;
        s += h.optInt((hour + 23) % 24);
        s += h.optInt((hour + 1) % 24);
        return s;
    }

    /** Flow score: frequency + recency + this-hour habit. */
    public float flowScore(AppItem a, int hour) {
        float recency = 0;
        if (a.lastUsed > 0) {
            float hours = (System.currentTimeMillis() - a.lastUsed) / 3600000f;
            recency = (float) (12.0 / (1.0 + hours));
        }
        return a.launchCount * 1.0f + recency * 2.2f + hourAffinity(a, hour) * 1.8f
                + (badges.containsKey(a.pkg) ? 4f : 0f);
    }

    public List<AppItem> predict(int count, List<String> excludeKeys) {
        final int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        List<AppItem> pool = visible();
        Collections.sort(pool, new Comparator<AppItem>() {
            public int compare(AppItem a, AppItem b) {
                return Float.compare(flowScore(b, hour), flowScore(a, hour));
            }
        });
        List<AppItem> out = new ArrayList<>();
        for (AppItem a : pool) {
            if (excludeKeys != null && excludeKeys.contains(a.key())) continue;
            out.add(a);
            if (out.size() >= count) break;
        }
        return out;
    }

    /** Hour-by-hour "Time Machine" histogram of total usage. */
    public int[] timeline() {
        int[] out = new int[24];
        java.util.Iterator<String> it = usage.keys();
        while (it.hasNext()) {
            JSONObject u = usage.optJSONObject(it.next());
            if (u == null) continue;
            JSONArray h = u.optJSONArray("h");
            if (h == null || h.length() != 24) continue;
            for (int i = 0; i < 24; i++) out[i] += h.optInt(i);
        }
        return out;
    }

    public List<AppItem> topForHour(final int hour, int n) {
        List<AppItem> pool = visible();
        Collections.sort(pool, new Comparator<AppItem>() {
            public int compare(AppItem a, AppItem b) {
                return Integer.compare(hourAffinity(b, hour), hourAffinity(a, hour));
            }
        });
        List<AppItem> out = new ArrayList<>();
        for (AppItem a : pool) {
            if (hourAffinity(a, hour) <= 0) break;
            out.add(a);
            if (out.size() >= n) break;
        }
        return out;
    }

    // ----------------------------------------------------------------- search
    public List<AppItem> search(String q) {
        List<AppItem> out = new ArrayList<>();
        if (q == null) return out;
        final String s = q.trim().toLowerCase(Locale.getDefault());
        if (s.isEmpty()) return out;
        final Map<AppItem, Integer> score = new HashMap<>();
        for (AppItem a : visible()) {
            String t = a.title().toLowerCase(Locale.getDefault());
            int sc = -1;
            if (t.equals(s)) sc = 1000;
            else if (t.startsWith(s)) sc = 800 - t.length();
            else if (initials(t).startsWith(s)) sc = 700;
            else if (t.contains(" " + s)) sc = 600;
            else if (t.contains(s)) sc = 400;
            else if (fuzzy(t, s)) sc = 150;
            if (sc > 0) { sc += Math.min(80, a.launchCount * 4); score.put(a, sc); out.add(a); }
        }
        Collections.sort(out, new Comparator<AppItem>() {
            public int compare(AppItem a, AppItem b) {
                return Integer.compare(score.get(b), score.get(a));
            }
        });
        return out;
    }

    private static String initials(String t) {
        StringBuilder sb = new StringBuilder();
        boolean b = true;
        for (char c : t.toCharArray()) {
            if (c == ' ') { b = true; continue; }
            if (b) { sb.append(c); b = false; }
        }
        return sb.toString();
    }

    private static boolean fuzzy(String text, String q) {
        int i = 0;
        for (int j = 0; j < text.length() && i < q.length(); j++) {
            if (text.charAt(j) == q.charAt(i)) i++;
        }
        return i == q.length();
    }

    // ----------------------------------------------------------------- launch
    public void launch(Activity act, AppItem a, View from) {
        if (a == null) return;
        noteLaunch(a);
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(a.pkg, a.cls));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            android.os.Bundle opts = null;
            if (from != null) {
                int[] loc = new int[2];
                from.getLocationOnScreen(loc);
                opts = android.app.ActivityOptions.makeClipRevealAnimation(from, 0, 0,
                        from.getWidth(), from.getHeight()).toBundle();
            }
            act.startActivity(i, opts);
        } catch (Throwable t) {
            try {
                Intent i = ctx.getPackageManager().getLaunchIntentForPackage(a.pkg);
                if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); act.startActivity(i); }
            } catch (Throwable ignored) { }
        }
    }

    /** Dual launch: opens an app side-by-side with the current one. */
    public void launchAdjacent(Activity act, AppItem a) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(a.pkg, a.cls));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                    | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            act.startActivity(i);
            noteLaunch(a);
        } catch (Throwable ignored) { }
    }

    public void appInfo(Activity act, AppItem a) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (Throwable ignored) { }
    }

    public void uninstall(Activity act, AppItem a) {
        try {
            Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (Throwable ignored) { }
    }

    /** App shortcuts (long-press shortcuts) exposed by the app, API 25+. */
    public List<String> shortcutLabels(AppItem a) {
        List<String> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT < 25) return out;
        try {
            android.content.pm.LauncherApps la = (android.content.pm.LauncherApps)
                    ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            android.content.pm.LauncherApps.ShortcutQuery q =
                    new android.content.pm.LauncherApps.ShortcutQuery();
            q.setPackage(a.pkg);
            q.setQueryFlags(android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    | android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    | android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<android.content.pm.ShortcutInfo> list =
                    la.getShortcuts(q, android.os.Process.myUserHandle());
            if (list != null) {
                for (android.content.pm.ShortcutInfo si : list) {
                    CharSequence c = si.getShortLabel() != null ? si.getShortLabel() : si.getLongLabel();
                    out.add(si.getId() + "\u0000" + (c == null ? si.getId() : c));
                }
            }
        } catch (Throwable ignored) { }
        return out;
    }

    public void startShortcut(AppItem a, String id, Rect src) {
        if (Build.VERSION.SDK_INT < 25) return;
        try {
            android.content.pm.LauncherApps la = (android.content.pm.LauncherApps)
                    ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            la.startShortcut(a.pkg, id, src, null, android.os.Process.myUserHandle());
        } catch (Throwable ignored) { }
    }
}
