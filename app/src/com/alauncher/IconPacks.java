package com.alauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Third-party icon pack support (ADW / Nova / GO appfilter format). */
public class IconPacks {

    private static final String[] ACTIONS = {
            "org.adw.launcher.THEMES", "com.gau.go.launcherex.theme",
            "com.novalauncher.THEME", "com.anddoes.launcher.THEME"};

    private static String loadedPack;
    private static final Map<String, String> MAP = new HashMap<>();
    private static String iconBack, iconMask, iconUpon;
    private static float scale = 1f;

    public static class Pack {
        public final String pkg, label;
        Pack(String p, String l) { pkg = p; label = l; }
    }

    public static List<Pack> list(Context c) {
        PackageManager pm = c.getPackageManager();
        List<Pack> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String a : ACTIONS) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(a), PackageManager.GET_META_DATA)) {
                String pkg = ri.activityInfo.packageName;
                if (seen.contains(pkg)) continue;
                seen.add(pkg);
                out.add(new Pack(pkg, String.valueOf(ri.loadLabel(pm))));
            }
        }
        return out;
    }

    public static synchronized void ensureLoaded(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) { loadedPack = null; MAP.clear(); return; }
        if (pkg.equals(loadedPack)) return;
        MAP.clear();
        iconBack = iconMask = iconUpon = null;
        scale = 1f;
        loadedPack = pkg;
        try {
            Resources res = c.getPackageManager().getResourcesForApplication(pkg);
            XmlPullParser xpp = null;
            int id = res.getIdentifier("appfilter", "xml", pkg);
            if (id != 0) {
                xpp = res.getXml(id);
            } else {
                InputStream is = c.createPackageContext(pkg, 0).getAssets().open("appfilter.xml");
                XmlPullParserFactory f = XmlPullParserFactory.newInstance();
                xpp = f.newPullParser();
                xpp.setInput(is, "UTF-8");
            }
            int ev = xpp.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String name = xpp.getName();
                    if ("item".equals(name)) {
                        String comp = xpp.getAttributeValue(null, "component");
                        String drw = xpp.getAttributeValue(null, "drawable");
                        if (comp != null && drw != null) {
                            String key = comp.replace("ComponentInfo{", "").replace("}", "");
                            MAP.put(key, drw);
                        }
                    } else if ("iconback".equals(name)) {
                        iconBack = xpp.getAttributeValue(null, "img1");
                    } else if ("iconmask".equals(name)) {
                        iconMask = xpp.getAttributeValue(null, "img1");
                    } else if ("iconupon".equals(name)) {
                        iconUpon = xpp.getAttributeValue(null, "img1");
                    } else if ("scale".equals(name)) {
                        try { scale = Float.parseFloat(xpp.getAttributeValue(null, "factor")); }
                        catch (Throwable ignored) { }
                    }
                }
                ev = xpp.next();
            }
        } catch (Throwable t) {
            MAP.clear();
        }
    }

    /** @return the themed icon for this app, or null when the pack has none. */
    public static Drawable icon(Context c, String packPkg, String appPkg, String appCls) {
        if (packPkg == null || packPkg.isEmpty()) return null;
        ensureLoaded(c, packPkg);
        if (MAP.isEmpty()) return null;
        String name = MAP.get(appPkg + "/" + appCls);
        if (name == null) name = MAP.get(appPkg + "/" + appPkg + ".MainActivity");
        if (name == null) {
            for (Map.Entry<String, String> e : MAP.entrySet()) {
                if (e.getKey().startsWith(appPkg + "/")) { name = e.getValue(); break; }
            }
        }
        if (name == null) return null;
        try {
            Resources res = c.getPackageManager().getResourcesForApplication(packPkg);
            int id = res.getIdentifier(name, "drawable", packPkg);
            if (id == 0) return null;
            return res.getDrawable(id, null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Drawable back(Context c, String packPkg) {
        if (iconBack == null || packPkg == null) return null;
        try {
            Resources res = c.getPackageManager().getResourcesForApplication(packPkg);
            int id = res.getIdentifier(iconBack, "drawable", packPkg);
            return id == 0 ? null : res.getDrawable(id, null);
        } catch (Throwable t) { return null; }
    }
}
