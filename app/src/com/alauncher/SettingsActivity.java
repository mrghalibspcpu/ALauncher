package com.alauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A Launcher settings — everything tweakable, in one scroll. */
public class SettingsActivity extends Activity {

    private Prefs prefs;
    private LinearLayout col;

    private static final int[] ACCENTS = {
            0xFF6AE3FF, 0xFFB47BFF, 0xFF5CE08A, 0xFFFFB347, 0xFFFF6B9D,
            0xFFFF5A5A, 0xFF7C9BFF, 0xFFFFE066, 0xFF4ECDC4, 0xFFFFFFFF};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this);
        prefs = Prefs.get(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF07080C);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(U.dp(20), U.dp(44), U.dp(20), U.dp(40));
        sv.addView(col);
        setContentView(sv);

        title("A Launcher");
        sub("Version 1.0  ·  built for people who want everything");

        header("Appearance");
        accentPicker();
        shapePicker();
        slider("Grid columns", 3, 7, prefs.gridCols(), v -> prefs.setI("grid_cols", v));
        slider("Grid rows", 4, 8, prefs.gridRows(), v -> prefs.setI("grid_rows", v));
        slider("Drawer columns", 3, 6, prefs.drawerCols(), v -> prefs.setI("drawer_cols", v));
        slider("Icon size %", 70, 140, prefs.iconSize(), v -> prefs.setI("icon_size", v));
        slider("Dock slots", 3, 6, prefs.dockCount(), v -> prefs.setI("dock_count", v));
        toggle("Home screen labels", "home_labels", true);
        toggle("Drawer labels", "drawer_labels", true);
        toggle("Dynamic accent from the last app", "dyn_accent", true);
        toggle("Big clock on home", "clock", true);
        toggle("Ambient particles", "particles", true);
        toggle("Gyroscope parallax", "parallax", true);
        toggle("Blur behind panels", "blur", true);

        header("Behaviour");
        toggle("Flow Dock (predicted apps)", "smart_dock", true);
        toggle("Notification badges", "badges", true);
        toggle("Glyph Launch (hold, then draw)", "glyphs", true);
        toggle("Double tap to lock", "dt_lock", true);

        header("Lists");
        button("Hidden apps", "Show or restore apps you hid", () -> appPicker("Hidden apps", Prefs.HIDDEN));
        button("Orbit favourites", "Apps in the radial launcher", () -> appPicker("Orbit favourites", Prefs.FAVS));
        button("Distracting apps", "Blocked while Focus Mode is on", () -> appPicker("Distracting apps", Focus.KEY_APPS));
        button("Reset home layout", "Rebuild the default pages and dock", () -> {
            prefs.sp.edit().remove("layout").apply();
            toast("Layout reset — go back home");
        });

        header("Permissions & system");
        button("Set A Launcher as default", "Open the home app chooser", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Throwable t) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        button("Notification access", "Needed for badges and the media card", () -> {
            try {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (Throwable ignored) { }
        });
        button("Modify system settings", "Needed for brightness and auto-rotate tiles", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable ignored) { }
        });
        button("Do Not Disturb access", "Needed for the DND tile", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            } catch (Throwable ignored) { }
        });
        button("Screen lock permission", "Enables double tap to lock", () -> AdminReceiver.request(this));

        header("Gestures cheat sheet");
        info("Swipe up — app drawer, search, maths, commands\n"
                + "Swipe down right — A Control Center\n"
                + "Swipe down left — system notifications\n"
                + "Two-finger tap — Orbit radial launcher\n"
                + "Hold then draw a letter — Glyph Launch\n"
                + "Double tap — lock screen\n"
                + "Long press icon — full app menu\n"
                + "Drag icon onto icon — create a folder\n"
                + "Drag to top bar — remove, hide, uninstall");
    }

    // ---------------------------------------------------------- widgets
    private void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(30);
        t.setTypeface(Typeface.create("sans-serif-light", 0));
        col.addView(t);
    }

    private void sub(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0x99FFFFFF);
        t.setTextSize(12);
        t.setPadding(0, U.dp(2), 0, U.dp(10));
        col.addView(t);
    }

    private void header(String s) {
        TextView t = new TextView(this);
        t.setText(s.toUpperCase());
        t.setTextColor(prefs.accent());
        t.setTextSize(11);
        t.setLetterSpacing(0.14f);
        t.setTypeface(Typeface.create("sans-serif-medium", 0));
        t.setPadding(U.dp(4), U.dp(26), 0, U.dp(10));
        col.addView(t);
    }

    private void info(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xCCFFFFFF);
        t.setTextSize(12.5f);
        t.setLineSpacing(U.dpf(4), 1f);
        t.setBackground(card());
        t.setPadding(U.dp(16), U.dp(16), U.dp(16), U.dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = U.dp(10);
        col.addView(t, lp);
    }

    private GradientDrawable card() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x14FFFFFF);
        g.setCornerRadius(U.dpf(20));
        g.setStroke(U.dp(1), 0x14FFFFFF);
        return g;
    }

    private void toggle(String label, final String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(0xFFE7EBF2);
        sw.setTextSize(14);
        sw.setChecked(prefs.b(key, def));
        sw.setBackground(card());
        sw.setPadding(U.dp(16), U.dp(14), U.dp(16), U.dp(14));
        sw.setOnCheckedChangeListener((v, c) -> prefs.setB(key, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = U.dp(8);
        col.addView(sw, lp);
    }

    private interface IntSink { void set(int v); }

    private void slider(String label, final int min, int max, int value, final IntSink sink) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(card());
        box.setPadding(U.dp(16), U.dp(12), U.dp(16), U.dp(8));
        final TextView t = new TextView(this);
        t.setText(label + ":  " + value);
        t.setTextColor(0xFFE7EBF2);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = U.dp(8);
        col.addView(box, lp);
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
        t.setTextColor(0xFFE7EBF2);
        t.setTextSize(14);
        box.addView(t);
        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(0x80FFFFFF);
        s.setTextSize(11.5f);
        box.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = U.dp(8);
        col.addView(box, lp);
    }

    private void accentPicker() {
        LinearLayout box = new LinearLayout(this);
        box.setBackground(card());
        box.setPadding(U.dp(14), U.dp(14), U.dp(14), U.dp(14));
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
                        p.setColor(0xFFFFFFFF);
                        cv.drawCircle(getWidth() / 2f, getHeight() / 2f, r + U.dpf(3), p);
                    }
                }
            };
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, U.dp(38), 1f);
            v.setOnClickListener(x -> {
                prefs.setI("accent", c);
                toast("Accent updated");
                recreate();
            });
            box.addView(v, lp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = U.dp(8);
        col.addView(box, lp);
    }

    private void shapePicker() {
        final String[] names = {"Squircle", "Circle", "Rounded", "Teardrop", "Hexagon"};
        button("Icon shape", names[U.clampI(prefs.iconShape(), 0, 4)], () ->
                new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Icon shape")
                        .setItems(names, (d, w) -> {
                            prefs.setI("icon_shape", w);
                            AppStore.get(this).reload();
                            toast("Shape updated");
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
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
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

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
