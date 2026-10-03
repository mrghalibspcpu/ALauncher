package com.alauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A Launcher settings — everything tweakable, in one themed scroll. */
public class SettingsActivity extends Activity {

    private Prefs prefs;
    private LinearLayout col;
    private boolean dark;
    private LockView lock;

    private static final int[] ACCENTS = {
            0xFF6AE3FF, 0xFFB47BFF, 0xFF5CE08A, 0xFFFFB347, 0xFFFF6B9D,
            0xFFFF5A5A, 0xFF7C9BFF, 0xFFFFE066, 0xFF4ECDC4, 0xFF9AA6B2};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this);
        prefs = Prefs.get(this);
        dark = Theme.dark(this);

        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(dark ? 0xFF07080C : 0xFFEFF2F7);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(U.dp(18), U.dp(44), U.dp(18), U.dp(48));
        sv.addView(col);
        wrap.addView(sv);
        lock = new LockView(this);
        wrap.addView(lock);
        setContentView(wrap);
        getWindow().setStatusBarColor(dark ? 0xFF07080C : 0xFFEFF2F7);
        getWindow().setNavigationBarColor(dark ? 0xFF07080C : 0xFFEFF2F7);
        if (!dark) {
            int f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(f);
        }

        title("A Launcher");
        sub("Version 2.0  ·  Liquid Glass");

        header("Theme");
        themePicker();
        accentPicker();
        toggle("Dynamic accent from the last app", "dyn_accent", true);
        shapePicker();

        header("Depth Clock");
        clockPicker();
        slider("Clock size %", 70, 150, prefs.i("clock_size", 100), v -> prefs.setI("clock_size", v));
        toggle("Show the clock", "clock", true);

        header("Layout");
        slider("Grid columns", 3, 7, prefs.gridCols(), v -> prefs.setI("grid_cols", v));
        slider("Grid rows", 4, 8, prefs.gridRows(), v -> prefs.setI("grid_rows", v));
        slider("Drawer columns", 3, 6, prefs.drawerCols(), v -> prefs.setI("drawer_cols", v));
        slider("Icon size %", 70, 140, prefs.iconSize(), v -> prefs.setI("icon_size", v));
        slider("Dock slots", 3, 6, prefs.dockCount(), v -> prefs.setI("dock_count", v));
        toggle("Home screen labels", "home_labels", true);
        toggle("Drawer labels", "drawer_labels", true);
        toggle("Search pill on home", "search_pill", true);
        toggle("Suggestions row in the drawer", "suggestions", true);

        header("Icons");
        iconPackPicker();
        toggle("Notification badges", "badges", true);

        header("Effects");
        toggle("Ambient particles", "particles", true);
        toggle("Gyroscope parallax & depth", "parallax", true);
        toggle("Glass blur behind panels", "blur", true);
        toggle("Blurred wallpaper background", "blur_home", true);

        header("Behaviour");
        toggle("Flow Dock (predicted apps)", "smart_dock", true);
        toggle("Glyph Launch (hold, then draw)", "glyphs", true);
        toggle("Double tap to lock", "dt_lock", true);

        header("Privacy");
        button(LockView.hasPin(this) ? "Change app-lock PIN" : "Set app-lock PIN",
                "A 4-digit PIN guards the apps you choose", () ->
                        lock.ask("Set a 4-digit PIN", true, prefs.accent(), ok -> {
                            if (ok) toast("PIN saved");
                        }));
        button("Locked apps", "Ask for the PIN before these open",
                () -> appPicker("Locked apps", Prefs.LOCKED));
        button("Hidden apps", "Hide apps from the drawer", () -> appPicker("Hidden apps", Prefs.HIDDEN));
        button("Orbit favourites", "Apps in the radial launcher", () -> appPicker("Orbit favourites", Prefs.FAVS));
        button("Distracting apps", "Paused while Focus Mode runs", () -> appPicker("Distracting apps", Focus.KEY_APPS));

        header("Backup");
        button("Export settings", "Writes alauncher-backup.json to app storage", this::exportPrefs);
        button("Import settings", "Restores the last exported backup", this::importPrefs);
        button("Reset home layout", "Rebuild the default pages and dock", () -> {
            prefs.sp.edit().remove("layout").apply();
            toast("Layout reset — press home");
        });

        header("Permissions & system");
        button("Set A Launcher as default", "Open the home app chooser", () -> {
            try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
            catch (Throwable t) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        button("Notification access", "Badges and the Control Center media card", () -> {
            try { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); }
            catch (Throwable ignored) { }
        });
        button("Modify system settings", "Brightness, auto-rotate and screen timeout tiles", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable ignored) { }
        });
        button("Do Not Disturb access", "DND and silent ringer tiles", () -> {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); }
            catch (Throwable ignored) { }
        });
        button("Screen lock permission", "Enables double tap to lock", () -> AdminReceiver.request(this));

        header("Gestures cheat sheet");
        info("Swipe up — drawer, search, maths, commands\n"
                + "Swipe down right — A Control Center\n"
                + "Swipe down left — system notifications\n"
                + "Two-finger tap — Orbit radial launcher\n"
                + "Hold then draw a letter — Glyph Launch\n"
                + "Double tap — lock screen\n"
                + "Long press icon — full app menu & app shortcuts\n"
                + "Drag icon onto icon — create a folder\n"
                + "Drag to the top bar — remove, hide, uninstall");
    }

    // ------------------------------------------------------------- helpers
    private int textCol() { return Theme.text(dark); }
    private int dimCol() { return Theme.textDim(dark); }

    private void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(textCol());
        t.setTextSize(30);
        t.setTypeface(Typeface.create("sans-serif-light", 0));
        col.addView(t);
    }

    private void sub(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(dimCol());
        t.setTextSize(12);
        t.setPadding(0, U.dp(2), 0, U.dp(8));
        col.addView(t);
    }

    private void header(String s) {
        TextView t = new TextView(this);
        t.setText(s.toUpperCase());
        t.setTextColor(prefs.accent());
        t.setTextSize(11);
        t.setLetterSpacing(0.14f);
        t.setTypeface(Typeface.create("sans-serif-medium", 0));
        t.setPadding(U.dp(4), U.dp(24), 0, U.dp(10));
        col.addView(t);
    }

    private void info(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(dimCol());
        t.setTextSize(12.5f);
        t.setLineSpacing(U.dpf(4), 1f);
        t.setBackground(card());
        t.setPadding(U.dp(16), U.dp(16), U.dp(16), U.dp(16));
        add(t);
    }

    private void add(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = U.dp(8);
        col.addView(v, lp);
    }

    private GradientDrawable card() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark ? 0x14FFFFFF : 0xFFFFFFFF);
        g.setCornerRadius(U.dpf(20));
        g.setStroke(U.dp(1), dark ? 0x14FFFFFF : 0x14000000);
        return g;
    }

    private void toggle(String label, final String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(textCol());
        sw.setTextSize(14);
        sw.setChecked(prefs.b(key, def));
        sw.setBackground(card());
        sw.setPadding(U.dp(16), U.dp(14), U.dp(16), U.dp(14));
        sw.setOnCheckedChangeListener((v, c) -> prefs.setB(key, c));
        add(sw);
    }

    private interface IntSink { void set(int v); }

    private void slider(String label, final int min, int max, int value, final IntSink sink) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(card());
        box.setPadding(U.dp(16), U.dp(12), U.dp(16), U.dp(8));
        final TextView t = new TextView(this);
        t.setText(label + ":  " + value);
        t.setTextColor(textCol());
        t.setTextSize(14);
        box.addView(t);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.getProgressDrawable().setColorFilter(prefs.accent(), android.graphics.PorterDuff.Mode.SRC_IN);
        sb.getThumb().setColorFilter(prefs.accent(), android.graphics.PorterDuff.Mode.SRC_IN);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                t.setText(label + ":  " + (p + min));
                sink.set(p + min);
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        box.addView(sb);
        add(box);
    }

    private void button(String label, String subtitle, final Runnable r) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(card());
        box.setPadding(U.dp(16), U.dp(14), U.dp(16), U.dp(14));
        box.setClickable(true);
        box.setOnClickListener(v -> r.run());
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(textCol());
        t.setTextSize(14);
        box.addView(t);
        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(dimCol());
        s.setTextSize(11.5f);
        box.addView(s);
        add(box);
    }

    private void themePicker() {
        final String[] names = {"Auto (follow system)", "Liquid Light", "Liquid Dark"};
        button("Theme", names[U.clampI(prefs.i("theme", Theme.AUTO), 0, 2)], () ->
                new AlertDialog.Builder(this, dialogTheme())
                        .setTitle("Theme")
                        .setItems(names, (d, w) -> {
                            prefs.setI("theme", w);
                            Theme.invalidateBackdrop();
                            recreate();
                        }).show());
    }

    private void clockPicker() {
        button("Clock style", DepthClock.NAMES[U.clampI(prefs.clockStyle(), 0, 3)], () ->
                new AlertDialog.Builder(this, dialogTheme())
                        .setTitle("Clock style")
                        .setItems(DepthClock.NAMES, (d, w) -> {
                            prefs.setI("clock_style", w);
                            recreate();
                        }).show());
    }

    private int dialogTheme() {
        return dark ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert;
    }

    private void accentPicker() {
        LinearLayout box = new LinearLayout(this);
        box.setBackground(card());
        box.setPadding(U.dp(12), U.dp(12), U.dp(12), U.dp(12));
        for (final int c : ACCENTS) {
            View v = new View(this) {
                @Override protected void onDraw(Canvas cv) {
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setColor(c);
                    float r = Math.min(getWidth(), getHeight()) / 2f - U.dpf(4);
                    cv.drawCircle(getWidth() / 2f, getHeight() / 2f, r, p);
                    if (prefs.accent() == c) {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(U.dpf(2));
                        p.setColor(textCol());
                        cv.drawCircle(getWidth() / 2f, getHeight() / 2f, r + U.dpf(3), p);
                    }
                }
            };
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, U.dp(36), 1f);
            v.setOnClickListener(x -> { prefs.setI("accent", c); recreate(); });
            box.addView(v, lp);
        }
        add(box);
    }

    private void shapePicker() {
        final String[] names = {"Squircle", "Circle", "Rounded", "Teardrop", "Hexagon"};
        button("Icon shape", names[U.clampI(prefs.iconShape(), 0, 4)], () ->
                new AlertDialog.Builder(this, dialogTheme())
                        .setTitle("Icon shape")
                        .setItems(names, (d, w) -> {
                            prefs.setI("icon_shape", w);
                            AppStore.get(this).reload();
                            recreate();
                        }).show());
    }

    private void iconPackPicker() {
        final List<IconPacks.Pack> packs = IconPacks.list(this);
        String cur = prefs.iconPack();
        String curLabel = "System icons";
        final String[] names = new String[packs.size() + 1];
        names[0] = "System icons";
        for (int i = 0; i < packs.size(); i++) {
            names[i + 1] = packs.get(i).label;
            if (packs.get(i).pkg.equals(cur)) curLabel = packs.get(i).label;
        }
        button("Icon pack", curLabel + (packs.isEmpty() ? "  ·  none installed" : ""), () ->
                new AlertDialog.Builder(this, dialogTheme())
                        .setTitle("Icon pack")
                        .setItems(names, (d, w) -> {
                            prefs.setS("icon_pack", w == 0 ? "" : packs.get(w - 1).pkg);
                            AppStore.get(this).reload();
                            toast("Applying icon pack…");
                            recreate();
                        }).show());
    }

    private void appPicker(String title, final String key) {
        final List<AppItem> apps = AppStore.get(this).all;
        if (apps.isEmpty()) { toast("Apps still loading…"); return; }
        final String[] names = new String[apps.size()];
        final boolean[] checked = new boolean[apps.size()];
        Set<String> cur = prefs.set(key);
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i).title();
            checked[i] = cur.contains(apps.get(i).key());
        }
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(title)
                .setMultiChoiceItems(names, checked, (d, w, isChecked) -> checked[w] = isChecked)
                .setPositiveButton("Save", (d, w) -> {
                    Set<String> out = new java.util.HashSet<>();
                    for (int i = 0; i < apps.size(); i++) if (checked[i]) out.add(apps.get(i).key());
                    prefs.setSet(key, out);
                    toast("Saved");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private File backupFile() {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        return new File(dir, "alauncher-backup.json");
    }

    private void exportPrefs() {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> e : prefs.sp.getAll().entrySet()) {
                Object v = e.getValue();
                if (v instanceof Set) o.put(e.getKey(), new org.json.JSONArray((Set<?>) v));
                else o.put(e.getKey(), v);
            }
            FileOutputStream fo = new FileOutputStream(backupFile());
            fo.write(o.toString(2).getBytes("UTF-8"));
            fo.close();
            toast("Saved to " + backupFile().getAbsolutePath());
        } catch (Throwable t) {
            toast("Export failed");
        }
    }

    private void importPrefs() {
        try {
            File f = backupFile();
            if (!f.exists()) { toast("No backup found"); return; }
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream fi = new java.io.FileInputStream(f);
            int read = fi.read(data);
            fi.close();
            JSONObject o = new JSONObject(new String(data, 0, Math.max(0, read), "UTF-8"));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object v = o.get(k);
                if (v instanceof Integer) prefs.setI(k, (Integer) v);
                else if (v instanceof Boolean) prefs.setB(k, (Boolean) v);
                else if (v instanceof String) prefs.setS(k, (String) v);
                else if (v instanceof org.json.JSONArray) {
                    org.json.JSONArray a = (org.json.JSONArray) v;
                    Set<String> set = new java.util.HashSet<>();
                    for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                    prefs.setSet(k, set);
                }
            }
            toast("Restored — press home");
            recreate();
        } catch (Throwable t) {
            toast("Import failed");
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
