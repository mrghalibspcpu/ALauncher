package com.alauncher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** A Launcher — the home activity that wires every surface together. */
public class Launcher extends Activity implements HomeView.Host, DrawerView.Host,
        ControlCenter.Host, RadialMenu.Host, AppStore.Listener, SensorEventListener {

    private static final int REQ_PICK_WIDGET = 101, REQ_BIND_WIDGET = 102, REQ_CONFIG_WIDGET = 103;

    private FrameLayout root;
    private HomeView home;
    private DrawerView drawer;
    private ControlCenter cc;
    private RadialMenu orbit;
    private ActionSheet sheet;

    private AppStore store;
    private Prefs prefs;
    private AppWidgetManager widgetManager;
    private AppWidgetHost widgetHost;
    private SensorManager sensors;

    private int accent;
    private boolean drawerOpen, ccOpen;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int pendingWidgetId = -1;

    private final BroadcastReceiver appReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) { store.reload(); }
    };
    private final BroadcastReceiver badgeReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) { if (home != null) home.invalidate(); if (drawer != null) drawer.refresh(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this);
        prefs = Prefs.get(this);
        accent = prefs.accent();
        store = AppStore.get(this);
        store.setListener(this);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        root = new FrameLayout(this);
        setContentView(root);

        home = new HomeView(this, this);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));

        drawer = new DrawerView(this, this);
        drawer.setVisibility(View.GONE);
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));

        cc = new ControlCenter(this, this);
        cc.setVisibility(View.GONE);
        root.addView(cc, new FrameLayout.LayoutParams(-1, -1));

        orbit = new RadialMenu(this, this);
        root.addView(orbit, new FrameLayout.LayoutParams(-1, -1));

        sheet = new ActionSheet(this);
        sheet.setVisibility(View.GONE);
        root.addView(sheet, new FrameLayout.LayoutParams(-1, -1));

        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(this, 0x4C41);  // "LA"
        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        home.load();
        store.reload();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(appReceiver, f);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(badgeReceiver, new IntentFilter(NotifService.ACTION_BADGES), RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(badgeReceiver, new IntentFilter(NotifService.ACTION_BADGES));
        }

        if (prefs.b("first_run", true)) {
            prefs.setB("first_run", false);
            handler.postDelayed(new Runnable() { public void run() { welcome(); } }, 900);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        try { widgetHost.startListening(); } catch (Throwable ignored) { }
    }

    @Override protected void onStop() {
        super.onStop();
        try { widgetHost.stopListening(); } catch (Throwable ignored) { }
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs.parallax()) {
            Sensor s = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (s != null) sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
        }
        home.load();
        home.invalidate();
        if (drawer != null) drawer.refresh();
    }

    @Override protected void onPause() {
        super.onPause();
        sensors.unregisterListener(this);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(appReceiver); } catch (Throwable ignored) { }
        try { unregisterReceiver(badgeReceiver); } catch (Throwable ignored) { }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (sheet.showing()) { sheet.hide(); return; }
        if (orbit.showing()) { orbit.hide(); return; }
        if (ccOpen) { closeControlCenter(); return; }
        if (drawerOpen) { closeDrawer(); return; }
        if (home.folderShowing()) { home.closeFolder(); return; }
        home.resetToHome();
    }

    @Override public void onBackPressed() {
        if (sheet.showing()) { sheet.hide(); return; }
        if (orbit.showing()) { orbit.hide(); return; }
        if (ccOpen) { closeControlCenter(); return; }
        if (drawerOpen) { closeDrawer(); return; }
        if (home.folderShowing()) { home.closeFolder(); return; }
        home.resetToHome();
    }

    // ------------------------------------------------------------ AppStore
    @Override public void onAppsChanged() {
        home.onAppsChanged();
        drawer.refresh();
    }

    // -------------------------------------------------------------- sensors
    @Override public void onSensorChanged(SensorEvent e) {
        if (home == null) return;
        float x = U.clamp(-e.values[0] / 6f, -1, 1);
        float y = U.clamp((e.values[1] - 5f) / 6f, -1, 1);
        home.setTilt(x, y);
    }

    @Override public void onAccuracyChanged(Sensor s, int a) { }

    // ------------------------------------------------------------ surfaces
    @Override public void openDrawer(float velocity) {
        if (drawerOpen) return;
        drawerOpen = true;
        drawer.setVisibility(View.VISIBLE);
        drawer.reset();
        drawer.setTranslationY(root.getHeight() * 0.35f);
        drawer.setAlpha(0f);
        drawer.animate().translationY(0).alpha(1f).setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2.2f)).start();
        home.animate().alpha(0.2f).scaleX(0.94f).scaleY(0.94f).setDuration(300).start();
    }

    @Override public void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        drawer.hideKeyboard();
        drawer.animate().translationY(root.getHeight() * 0.3f).alpha(0f).setDuration(240)
                .withEndAction(new Runnable() {
                    public void run() { drawer.setVisibility(View.GONE); drawer.reset(); }
                }).start();
        home.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).start();
    }

    @Override public void openControlCenter() {
        if (ccOpen) return;
        ccOpen = true;
        if (prefs.blurPanels()) cc.setBackdrop(snapshotBlur());
        cc.sync();
        cc.setVisibility(View.VISIBLE);
        cc.setTranslationY(-root.getHeight() * 0.25f);
        cc.setAlpha(0f);
        cc.animate().translationY(0).alpha(1f).setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2.4f)).start();
    }

    @Override public void closeControlCenter() {
        if (!ccOpen) return;
        ccOpen = false;
        cc.animate().translationY(-root.getHeight() * 0.22f).alpha(0f).setDuration(220)
                .withEndAction(new Runnable() { public void run() { cc.setVisibility(View.GONE); } }).start();
    }

    private Bitmap snapshotBlur() {
        try {
            int w = Math.max(1, root.getWidth() / 8), h = Math.max(1, root.getHeight() / 8);
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            c.scale(1f / 8f, 1f / 8f);
            // wallpaper first
            try {
                WallpaperManager wm = WallpaperManager.getInstance(this);
                android.graphics.drawable.Drawable d = wm.getDrawable();
                if (d != null) {
                    d.setBounds(0, 0, root.getWidth(), root.getHeight());
                    d.draw(c);
                }
            } catch (Throwable ignored) { }
            home.draw(c);
            return U.blur(bm, 10);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override public void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class<?> cls = Class.forName("android.app.StatusBarManager");
            cls.getMethod(Build.VERSION.SDK_INT >= 17 ? "expandNotificationsPanel" : "expand").invoke(sb);
        } catch (Throwable t) {
            openControlCenter();
        }
    }

    @Override public void lockScreen() {
        if (!AdminReceiver.lock(this)) {
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Double tap to lock")
                    .setMessage("A Launcher needs the screen-lock permission to do this. Enable it now?")
                    .setPositiveButton("Enable", (d, w) -> AdminReceiver.request(Launcher.this))
                    .setNegativeButton("Later", null)
                    .show();
        }
    }

    @Override public void showOrbit(float x, float y) {
        List<AppItem> favs = new ArrayList<>();
        for (String k : prefs.set(Prefs.FAVS)) {
            AppItem a = store.find(k);
            if (a != null) favs.add(a);
        }
        if (favs.size() < 3) {
            for (AppItem a : store.predict(8 - favs.size(), null)) if (!favs.contains(a)) favs.add(a);
        }
        if (favs.size() > 8) favs = favs.subList(0, 8);
        orbit.show(x, y, favs);
    }

    @Override public int accent() { return accent; }

    @Override public void onAccentSuggestion(int color) {
        if (!prefs.dynamicAccent()) return;
        final int from = accent, to = U.boost(color, 1.1f, 1.15f);
        if (from == to) return;
        U.anim(0, 1, 500, U.EASE, new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                accent = U.mix(from, to, (Float) a.getAnimatedValue());
                home.invalidate();
            }
        });
    }

    @Override public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override public void openApp(AppItem a, View from, float x, float y) {
        store.launch(this, a, from);
        if (drawerOpen) handler.postDelayed(new Runnable() { public void run() { closeDrawer(); } }, 220);
    }

    @Override public void openSettings() {
        closeControlCenter();
        startActivity(new Intent(this, SettingsActivity.class));
    }

    // ------------------------------------------------------------ widgets
    @Override public AppWidgetHostView createWidgetView(int widgetId) {
        try {
            AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(widgetId);
            if (info == null) return null;
            AppWidgetHostView v = widgetHost.createView(this, widgetId, info);
            v.setAppWidget(widgetId, info);
            return v;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override public void removeWidget(int widgetId) {
        try { widgetHost.deleteAppWidgetId(widgetId); } catch (Throwable ignored) { }
    }

    private void pickWidget() {
        final List<AppWidgetProviderInfo> providers = widgetManager.getInstalledProviders();
        if (providers.isEmpty()) { toast("No widgets installed"); return; }
        final String[] names = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) {
            AppWidgetProviderInfo p = providers.get(i);
            CharSequence l = Build.VERSION.SDK_INT >= 21 ? p.loadLabel(getPackageManager()) : p.label;
            names[i] = String.valueOf(l);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add a widget")
                .setItems(names, (d, which) -> bindWidget(providers.get(which)))
                .show();
    }

    private void bindWidget(AppWidgetProviderInfo info) {
        int id = widgetHost.allocateAppWidgetId();
        pendingWidgetId = id;
        boolean ok = false;
        try { ok = widgetManager.bindAppWidgetIdIfAllowed(id, info.provider); } catch (Throwable ignored) { }
        if (!ok) {
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            startActivityForResult(i, REQ_BIND_WIDGET);
            return;
        }
        finishWidget(id);
    }

    private void finishWidget(int id) {
        AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
        if (info == null) return;
        if (info.configure != null) {
            try {
                Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                i.setComponent(info.configure);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                startActivityForResult(i, REQ_CONFIG_WIDGET);
                return;
            } catch (Throwable ignored) { }
        }
        placeWidget(id, info);
    }

    private void placeWidget(int id, AppWidgetProviderInfo info) {
        int cellW = Math.max(1, (int) Math.ceil(info.minWidth / (160f)));
        int cellH = Math.max(1, (int) Math.ceil(info.minHeight / (160f)));
        home.addWidget(id, cellW, cellH);
        toast("Widget added");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        int id = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) : pendingWidgetId;
        if (res != RESULT_OK) {
            if (id > 0) removeWidget(id);
            return;
        }
        if (req == REQ_BIND_WIDGET) finishWidget(id);
        else if (req == REQ_CONFIG_WIDGET) {
            AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
            if (info != null) placeWidget(id, info);
        }
    }

    // --------------------------------------------------------------- menus
    @Override public void appMenu(AppItem a, float x, float y) { appMenu(a, x, y, null); }

    @Override public void appMenu(final AppItem a, float x, float y, final HomeView.Item homeItem) {
        if (a == null) return;
        List<ActionSheet.Row> rows = new ArrayList<>();
        rows.add(new ActionSheet.Row("open", "Open", Glyphs.SHORTCUT, false));
        rows.add(new ActionSheet.Row("split", "Open in split screen", Glyphs.SPLIT, false));
        if (homeItem == null) rows.add(new ActionSheet.Row("home", "Add to home", Glyphs.PLUS, false));
        else rows.add(new ActionSheet.Row("remove", "Remove from home", Glyphs.CLOSE, false));
        rows.add(new ActionSheet.Row("orbit", prefs.set(Prefs.FAVS).contains(a.key())
                ? "Remove from Orbit" : "Add to Orbit", Glyphs.STAR, false));
        rows.add(new ActionSheet.Row("rename", "Rename", Glyphs.PENCIL, false));
        rows.add(new ActionSheet.Row("focus", Focus.blocked(this, a) || prefs.set(Focus.KEY_APPS).contains(a.key())
                ? "Unmark as distracting" : "Mark as distracting", Glyphs.SHIELD, false));
        rows.add(new ActionSheet.Row("hide", "Hide app", Glyphs.EYE_OFF, false));
        rows.add(new ActionSheet.Row("info", "App info", Glyphs.INFO, false));
        rows.add(new ActionSheet.Row("uninstall", "Uninstall", Glyphs.TRASH, true));

        sheet.show(x, y, a.title(), a.icon, accent, rows, new ActionSheet.OnAction() {
            public void run(String id) {
                switch (id) {
                    case "open": openApp(a, null, 0, 0); break;
                    case "split": store.launchAdjacent(Launcher.this, a); break;
                    case "home": home.addAppToHome(a); break;
                    case "remove": if (homeItem != null) home.removeItem(homeItem); break;
                    case "orbit":
                        prefs.toggleIn(Prefs.FAVS, a.key());
                        toast("Orbit updated");
                        break;
                    case "rename": renameApp(a); break;
                    case "focus":
                        Focus.toggleApp(Launcher.this, a);
                        toast("Focus list updated");
                        home.invalidate();
                        break;
                    case "hide":
                        prefs.toggleIn(Prefs.HIDDEN, a.key());
                        if (homeItem != null) home.removeItem(homeItem);
                        drawer.refresh();
                        home.invalidate();
                        toast(a.title() + " hidden — restore in Settings");
                        break;
                    case "info": store.appInfo(Launcher.this, a); break;
                    case "uninstall": store.uninstall(Launcher.this, a); break;
                }
            }
        });
    }

    private void renameApp(final AppItem a) {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setText(a.title());
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Rename " + a.label)
                .setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    a.customLabel = et.getText().toString();
                    prefs.setS("name_" + a.key(), a.customLabel);
                    home.invalidate();
                    drawer.refresh();
                })
                .setNeutralButton("Reset", (d, w) -> {
                    a.customLabel = null;
                    prefs.sp.edit().remove("name_" + a.key()).apply();
                    home.invalidate();
                    drawer.refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override public void homeMenu(float x, float y) {
        List<ActionSheet.Row> rows = new ArrayList<>();
        rows.add(new ActionSheet.Row("wallpaper", "Wallpaper", Glyphs.WALL, false));
        rows.add(new ActionSheet.Row("widget", "Add widget", Glyphs.WIDGET, false));
        rows.add(new ActionSheet.Row("orbit", "Open Orbit", Glyphs.STAR, false));
        rows.add(new ActionSheet.Row("timeline", "Time Machine", Glyphs.CLOCK, false));
        rows.add(new ActionSheet.Row("focus", Focus.active(this) ? "Stop Focus Mode" : "Start Focus (30m)", Glyphs.SHIELD, false));
        rows.add(new ActionSheet.Row("page", "Add page", Glyphs.PLUS, false));
        rows.add(new ActionSheet.Row("settings", "Launcher settings", Glyphs.GEAR, false));
        sheet.show(x, y, "Home screen", null, accent, rows, new ActionSheet.OnAction() {
            public void run(String id) {
                switch (id) {
                    case "wallpaper":
                        try {
                            startActivity(Intent.createChooser(
                                    new Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper"));
                        } catch (Throwable ignored) { }
                        break;
                    case "widget": pickWidget(); break;
                    case "orbit": showOrbit(root.getWidth() / 2f, root.getHeight() * 0.6f); break;
                    case "timeline": showTimeline(); break;
                    case "focus":
                        Focus.toggle(Launcher.this, 30);
                        toast(Focus.active(Launcher.this) ? "Focus Mode on for 30 minutes" : "Focus Mode off");
                        home.invalidate();
                        break;
                    case "page":
                        if (home.pageCount < 6) { home.pageCount++; home.save(); home.goToPage(home.pageCount - 1); }
                        break;
                    case "settings": openSettings(); break;
                }
            }
        });
    }

    private void showTimeline() {
        final int[] data = store.timeline();
        View v = new View(this) {
            @Override protected void onDraw(Canvas c) {
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                int max = 1;
                for (int d : data) max = Math.max(max, d);
                float bw = getWidth() / 24f;
                int nowH = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
                for (int i = 0; i < 24; i++) {
                    float h = (getHeight() - U.dpf(30)) * (data[i] / (float) max);
                    p.setColor(i == nowH ? accent : U.alpha(accent, 0.35f));
                    c.drawRoundRect(new android.graphics.RectF(i * bw + U.dpf(2), getHeight() - U.dpf(22) - h,
                            (i + 1) * bw - U.dpf(2), getHeight() - U.dpf(22)), U.dpf(4), U.dpf(4), p);
                    if (i % 6 == 0) {
                        p.setColor(0x99FFFFFF);
                        p.setTextSize(U.dpf(9));
                        c.drawText(i + "h", i * bw + U.dpf(2), getHeight() - U.dpf(6), p);
                    }
                }
            }
        };
        v.setMinimumHeight(U.dp(180));
        StringBuilder sb = new StringBuilder();
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        List<AppItem> top = store.topForHour(hour, 3);
        for (AppItem a : top) sb.append("• ").append(a.title()).append('\n');
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Time Machine")
                .setMessage("What you usually open around " + hour + ":00\n"
                        + (sb.length() == 0 ? "Not enough data yet — keep using A Launcher." : sb))
                .setView(v)
                .setPositiveButton("Close", null)
                .show();
    }

    // ------------------------------------------------------------ commands
    @Override public void runCommand(String cmd) {
        if (cmd == null) return;
        String c = cmd.trim().toLowerCase();
        String arg = "";
        int sp = c.indexOf(' ');
        if (sp > 0) { arg = c.substring(sp + 1); c = c.substring(0, sp); }
        switch (c) {
            case "torch": cc.sync(); toast("Use the Control Center torch tile"); openControlCenter(); break;
            case "focus":
                Focus.toggle(this, 30);
                toast(Focus.active(this) ? "Focus Mode on" : "Focus Mode off");
                break;
            case "wifi": startSafe(new Intent(Build.VERSION.SDK_INT >= 29
                    ? android.provider.Settings.Panel.ACTION_WIFI
                    : android.provider.Settings.ACTION_WIFI_SETTINGS)); break;
            case "bluetooth": startSafe(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); break;
            case "settings": openSettings(); break;
            case "wallpaper": startSafe(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")); break;
            case "widgets": closeDrawer(); pickWidget(); break;
            case "hidden": openSettings(); break;
            case "timeline": closeDrawer(); showTimeline(); break;
            case "orbit": closeDrawer(); showOrbit(root.getWidth() / 2f, root.getHeight() * 0.6f); break;
            case "lock": lockScreen(); break;
            case "restart": recreate(); break;
            case "sysinfo": openControlCenter(); break;
            case "web":
                Intent i = new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(arg)));
                startSafe(i);
                break;
            default: toast("Unknown command: " + c);
        }
        closeDrawer();
    }

    private void startSafe(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } catch (Throwable ignored) { }
    }

    private void welcome() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Welcome to A Launcher")
                .setMessage("Gestures:\n"
                        + "• Swipe up — app drawer & search\n"
                        + "• Swipe down (right) — A Control Center\n"
                        + "• Swipe down (left) — notifications\n"
                        + "• Two-finger tap — Orbit radial launcher\n"
                        + "• Hold, then draw a letter — Glyph Launch\n"
                        + "• Double tap — lock screen\n"
                        + "• Long press — menus, widgets, wallpaper\n\n"
                        + "Type \":\" in search for the command palette, or \"2+2\" for maths.")
                .setPositiveButton("Let's go", null)
                .setNeutralButton("Settings", (d, w) -> openSettings())
                .show();
    }
}
