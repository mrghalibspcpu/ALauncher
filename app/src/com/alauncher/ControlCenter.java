package com.alauncher;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A Launcher's own Control Center — glass panel with live tiles,
 * brightness / volume sliders, media card and device vitals.
 */
public class ControlCenter extends View {

    public interface Host {
        void closeControlCenter();
        void openSettings();
        int accent();
    }

    static class Tile {
        String id, label, sub;
        int glyph;
        boolean on;
        RectF r = new RectF();
        float press;
        Tile(String id, String label, int glyph) { this.id = id; this.label = label; this.glyph = glyph; }
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Tile> tiles = new ArrayList<>();
    private final RectF brightR = new RectF(), volR = new RectF(), mediaR = new RectF(), statsR = new RectF();
    private final RectF[] shortcutR = new RectF[4];
    private final String[] shortcutIds = {"calc", "camera", "clock", "settings"};
    private final int[] shortcutGlyphs = {Glyphs.CALC, Glyphs.CAMERA, Glyphs.TIMER, Glyphs.GEAR};
    private final String[] shortcutLabels = {"Calculator", "Camera", "Clock", "Settings"};

    private Host host;
    private Bitmap backdrop;
    private float brightness = 0.5f, volume = 0.5f;
    private int dragging = 0;        // 1 brightness, 2 volume
    private Tile pressedTile;
    private boolean torchOn;
    private String cameraId;
    private MediaController media;
    private String mediaTitle = "", mediaArtist = "";
    private Bitmap mediaArt;
    private boolean mediaPlaying;
    private String batteryText = "", ramText = "", storeText = "";
    private float batteryLevel = 0f;
    private boolean charging;

    public ControlCenter(Context c, Host h) {
        super(c);
        host = h;
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        setClickable(true);
        try {
            CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) { cameraId = id; break; }
            }
        } catch (Throwable ignored) { }
        buildTiles();
    }

    private void buildTiles() {
        tiles.clear();
        tiles.add(new Tile("wifi", "Wi-Fi", Glyphs.WIFI));
        tiles.add(new Tile("bt", "Bluetooth", Glyphs.BT));
        tiles.add(new Tile("data", "Mobile Data", Glyphs.DATA));
        tiles.add(new Tile("plane", "Airplane", Glyphs.PLANE));
        tiles.add(new Tile("torch", "Torch", Glyphs.TORCH));
        tiles.add(new Tile("rotate", "Auto-rotate", Glyphs.ROTATE));
        tiles.add(new Tile("dnd", "Do Not Disturb", Glyphs.MOON));
        tiles.add(new Tile("zen", "Focus Mode", Glyphs.SHIELD));
    }

    public void setBackdrop(Bitmap b) { backdrop = b; invalidate(); }

    /** Refresh every live value — called whenever the panel opens. */
    public void sync() {
        Context c = getContext();
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            tile("wifi").on = wm != null && wm.isWifiEnabled();
        } catch (Throwable ignored) { }
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            tile("bt").on = ba != null && ba.isEnabled();
        } catch (Throwable ignored) { }
        try {
            tile("plane").on = Settings.Global.getInt(c.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        } catch (Throwable ignored) { }
        try {
            tile("rotate").on = Settings.System.getInt(c.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
        } catch (Throwable ignored) { }
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            tile("dnd").on = nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
        } catch (Throwable ignored) { }
        tile("torch").on = torchOn;
        tile("zen").on = Focus.active(getContext());
        try {
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager)
                    c.getSystemService(Context.TELEPHONY_SERVICE);
            tile("data").on = tm != null && tm.getSimState() == android.telephony.TelephonyManager.SIM_STATE_READY;
        } catch (Throwable ignored) { }

        try {
            int b = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            brightness = U.clamp(b / 255f, 0.02f, 1f);
        } catch (Throwable ignored) { }
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            volume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    / (float) Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        } catch (Throwable ignored) { }

        syncMedia();
        syncVitals();
        invalidate();
    }

    private Tile tile(String id) {
        for (Tile t : tiles) if (t.id.equals(id)) return t;
        return tiles.get(0);
    }

    private void syncMedia() {
        media = null; mediaTitle = ""; mediaArtist = ""; mediaArt = null; mediaPlaying = false;
        List<MediaController> l = NotifService.sessions(getContext());
        for (MediaController mc : l) {
            PlaybackState st = mc.getPlaybackState();
            if (st == null) continue;
            if (media == null || st.getState() == PlaybackState.STATE_PLAYING) media = mc;
        }
        if (media != null) {
            MediaMetadata md = media.getMetadata();
            if (md != null) {
                mediaTitle = String.valueOf(md.getText(MediaMetadata.METADATA_KEY_TITLE));
                mediaArtist = String.valueOf(md.getText(MediaMetadata.METADATA_KEY_ARTIST));
                Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                mediaArt = art;
            }
            PlaybackState st = media.getPlaybackState();
            mediaPlaying = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            if ("null".equals(mediaTitle)) mediaTitle = media.getPackageName();
        }
    }

    private void syncVitals() {
        try {
            Intent i = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i != null) {
                int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int scale = Math.max(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                batteryLevel = lvl / (float) scale;
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                batteryText = Math.round(batteryLevel * 100) + "%  ·  " + (temp / 10f) + "°C";
            }
        } catch (Throwable ignored) { }
        try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            ramText = (mi.availMem >> 20) + " MB free of " + (mi.totalMem >> 20) + " MB";
        } catch (Throwable ignored) { }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long free = fs.getAvailableBytes() / (1024 * 1024 * 1024L);
            long total = fs.getTotalBytes() / (1024 * 1024 * 1024L);
            storeText = free + " GB free of " + total + " GB";
        } catch (Throwable ignored) { }
    }

    // --------------------------------------------------------------- layout
    private void layoutTiles() {
        float pad = U.dpf(18);
        float top = U.dpf(96);
        float w = getWidth() - pad * 2;
        float gap = U.dpf(12);

        // connectivity block: 2x2 on the left (half width), sliders on the right
        float blockW = w * 0.56f;
        float cellW = (blockW - gap) / 2f;
        float cellH = U.dpf(74);
        for (int i = 0; i < 4; i++) {
            float x = pad + (i % 2) * (cellW + gap);
            float y = top + (i / 2) * (cellH + gap);
            tiles.get(i).r.set(x, y, x + cellW, y + cellH);
        }
        float slidersX = pad + blockW + gap;
        float sliderW = (w - blockW - gap - gap) / 2f;
        float sliderH = cellH * 2 + gap;
        brightR.set(slidersX, top, slidersX + sliderW, top + sliderH);
        volR.set(slidersX + sliderW + gap, top, slidersX + sliderW * 2 + gap, top + sliderH);

        float y2 = top + sliderH + gap;
        float qw = (w - gap * 3) / 4f;
        for (int i = 4; i < 8; i++) {
            float x = pad + (i - 4) * (qw + gap);
            tiles.get(i).r.set(x, y2, x + qw, y2 + qw * 0.92f);
        }

        float y3 = y2 + qw * 0.92f + gap;
        mediaR.set(pad, y3, pad + w, y3 + U.dpf(96));

        float y4 = mediaR.bottom + gap;
        for (int i = 0; i < 4; i++) {
            float x = pad + i * (qw + gap);
            shortcutR[i] = new RectF(x, y4, x + qw, y4 + U.dpf(62));
        }
        statsR.set(pad, y4 + U.dpf(62) + gap, pad + w, y4 + U.dpf(62) + gap + U.dpf(84));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutTiles();
    }

    // ----------------------------------------------------------------- draw
    @Override protected void onDraw(Canvas c) {
        if (getWidth() == 0) return;
        if (tiles.get(0).r.isEmpty()) layoutTiles();
        int accent = host != null ? host.accent() : 0xFF6AE3FF;

        if (backdrop != null) {
            p.setAlpha(255);
            c.drawBitmap(backdrop, null, new RectF(0, 0, getWidth(), getHeight()), p);
        }
        p.setColor(0xD9080A10);
        c.drawRect(0, 0, getWidth(), getHeight(), p);

        // header
        tp.setColor(0xFFFFFFFF);
        tp.setTextSize(U.dpf(30));
        tp.setTypeface(Typeface.create("sans-serif-light", 0));
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        c.drawText(time, U.dpf(20), U.dpf(58), tp);
        tp.setTextSize(U.dpf(12.5f));
        tp.setTypeface(Typeface.create("sans-serif", 0));
        tp.setColor(0x99FFFFFF);
        c.drawText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()),
                U.dpf(20), U.dpf(76), tp);

        tp.setTextSize(U.dpf(11f));
        tp.setColor(U.alpha(accent, 0.9f));
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        String brand = "A CONTROL CENTER";
        c.drawText(brand, getWidth() - U.dpf(20) - tp.measureText(brand), U.dpf(40), tp);
        // battery pill in header
        drawBatteryPill(c, getWidth() - U.dpf(20) - U.dpf(62), U.dpf(54), accent);

        for (int i = 0; i < 4; i++) drawTile(c, tiles.get(i), accent, true);
        drawSlider(c, brightR, brightness, Glyphs.SUN, accent, true);
        drawSlider(c, volR, volume, Glyphs.VOLUME, accent, false);
        for (int i = 4; i < 8; i++) drawTile(c, tiles.get(i), accent, false);
        drawMedia(c, accent);
        for (int i = 0; i < 4; i++) drawShortcut(c, i, accent);
        drawStats(c, accent);

        // grabber
        p.setColor(0x44FFFFFF);
        c.drawRoundRect(new RectF(getWidth() / 2f - U.dpf(22), getHeight() - U.dpf(14),
                getWidth() / 2f + U.dpf(22), getHeight() - U.dpf(10)), U.dpf(2), U.dpf(2), p);
    }

    private void drawBatteryPill(Canvas c, float x, float y, int accent) {
        RectF r = new RectF(x, y - U.dpf(9), x + U.dpf(44), y + U.dpf(9));
        p.setColor(0x1AFFFFFF);
        c.drawRoundRect(r, U.dpf(9), U.dpf(9), p);
        RectF f = new RectF(r.left, r.top, r.left + r.width() * batteryLevel, r.bottom);
        p.setColor(U.alpha(charging ? 0xFF5CE08A : accent, 0.85f));
        c.drawRoundRect(f, U.dpf(9), U.dpf(9), p);
        tp.setTextSize(U.dpf(10));
        tp.setColor(0xFF0B0D12);
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        String s = Math.round(batteryLevel * 100) + "%";
        c.drawText(s, r.centerX() - tp.measureText(s) / 2f, r.centerY() + U.dpf(3.6f), tp);
    }

    private void drawTile(Canvas c, Tile t, int accent, boolean big) {
        float rad = U.dpf(22);
        float scale = 1f - 0.04f * t.press;
        c.save();
        c.scale(scale, scale, t.r.centerX(), t.r.centerY());
        p.setColor(t.on ? U.alpha(accent, 0.92f) : 0x1FFFFFFF);
        c.drawRoundRect(t.r, rad, rad, p);
        if (!t.on) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(U.dpf(1));
            p.setColor(0x14FFFFFF);
            c.drawRoundRect(t.r, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
        }
        int fg = t.on ? (U.isLight(accent) ? 0xFF07080C : 0xFFFFFFFF) : 0xFFE7EBF2;
        if (big) {
            Glyphs.draw(c, t.glyph, t.r.left + U.dpf(24), t.r.centerY() - U.dpf(2), U.dpf(11), fg);
            tp.setTextSize(U.dpf(12f));
            tp.setColor(fg);
            tp.setTypeface(Typeface.create("sans-serif-medium", 0));
            c.drawText(U.ellipsize(tp, t.label, t.r.width() - U.dpf(48)), t.r.left + U.dpf(42),
                    t.r.centerY() - U.dpf(1), tp);
            tp.setTextSize(U.dpf(10.5f));
            tp.setColor(U.alpha(fg, 0.65f));
            c.drawText(t.on ? "On" : "Off", t.r.left + U.dpf(42), t.r.centerY() + U.dpf(14), tp);
        } else {
            Glyphs.draw(c, t.glyph, t.r.centerX(), t.r.centerY() - U.dpf(6), U.dpf(12), fg);
            tp.setTextSize(U.dpf(10f));
            tp.setColor(U.alpha(fg, 0.9f));
            tp.setTypeface(Typeface.create("sans-serif", 0));
            String s = U.ellipsize(tp, t.label, t.r.width() - U.dpf(6));
            c.drawText(s, t.r.centerX() - tp.measureText(s) / 2f, t.r.bottom - U.dpf(10), tp);
        }
        c.restore();
    }

    private void drawSlider(Canvas c, RectF r, float value, int glyph, int accent, boolean warm) {
        float rad = U.dpf(22);
        p.setColor(0x1FFFFFFF);
        c.drawRoundRect(r, rad, rad, p);
        c.save();
        Path clip = new Path();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(clip);
        float fillTop = r.bottom - r.height() * U.clamp(value, 0.02f, 1f);
        p.setShader(new android.graphics.LinearGradient(r.left, fillTop, r.left, r.bottom,
                warm ? 0xFFFFE08A : U.boost(accent, 1f, 1.25f),
                warm ? 0xFFFFB347 : accent, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(r.left, fillTop, r.right, r.bottom, p);
        p.setShader(null);
        c.restore();
        int fg = value > 0.25f ? 0xFF14161C : 0xFFE7EBF2;
        Glyphs.draw(c, glyph, r.centerX(), r.bottom - U.dpf(22), U.dpf(11), fg);
        tp.setTextSize(U.dpf(11));
        tp.setColor(0x80FFFFFF);
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        String s = Math.round(value * 100) + "%";
        c.drawText(s, r.centerX() - tp.measureText(s) / 2f, r.top + U.dpf(20), tp);
    }

    private void drawMedia(Canvas c, int accent) {
        float rad = U.dpf(24);
        p.setColor(0x1FFFFFFF);
        c.drawRoundRect(mediaR, rad, rad, p);
        float as = mediaR.height() - U.dpf(24);
        RectF art = new RectF(mediaR.left + U.dpf(12), mediaR.top + U.dpf(12),
                mediaR.left + U.dpf(12) + as, mediaR.bottom - U.dpf(12));
        if (mediaArt != null) {
            c.save();
            Path pa = new Path();
            pa.addRoundRect(art, U.dpf(14), U.dpf(14), Path.Direction.CW);
            c.clipPath(pa);
            c.drawBitmap(mediaArt, null, art, p);
            c.restore();
        } else {
            p.setColor(U.alpha(accent, 0.25f));
            c.drawRoundRect(art, U.dpf(14), U.dpf(14), p);
            Glyphs.draw(c, Glyphs.PLAY, art.centerX(), art.centerY(), U.dpf(13), U.alpha(accent, 0.9f));
        }
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setTextSize(U.dpf(14));
        tp.setColor(0xFFFFFFFF);
        String title = media == null ? "Nothing playing" : mediaTitle;
        c.drawText(U.ellipsize(tp, title, mediaR.width() - as - U.dpf(150)),
                art.right + U.dpf(14), mediaR.centerY() - U.dpf(4), tp);
        tp.setTextSize(U.dpf(11.5f));
        tp.setColor(0x99FFFFFF);
        tp.setTypeface(Typeface.create("sans-serif", 0));
        String sub = media == null ? "Open a music app to control it here"
                : ("null".equals(mediaArtist) ? media.getPackageName() : mediaArtist);
        c.drawText(U.ellipsize(tp, sub, mediaR.width() - as - U.dpf(150)),
                art.right + U.dpf(14), mediaR.centerY() + U.dpf(14), tp);

        float cy = mediaR.centerY();
        float rx = mediaR.right - U.dpf(28);
        Glyphs.draw(c, Glyphs.NEXT, rx, cy, U.dpf(10), 0xFFFFFFFF);
        p.setColor(U.alpha(accent, 0.9f));
        c.drawCircle(rx - U.dpf(40), cy, U.dpf(18), p);
        Glyphs.draw(c, mediaPlaying ? Glyphs.PAUSE : Glyphs.PLAY, rx - U.dpf(40), cy, U.dpf(10),
                U.isLight(accent) ? 0xFF07080C : 0xFFFFFFFF);
        Glyphs.draw(c, Glyphs.PREV, rx - U.dpf(78), cy, U.dpf(10), 0xFFFFFFFF);
    }

    private void drawShortcut(Canvas c, int i, int accent) {
        RectF r = shortcutR[i];
        if (r == null) return;
        p.setColor(0x14FFFFFF);
        c.drawRoundRect(r, U.dpf(20), U.dpf(20), p);
        Glyphs.draw(c, shortcutGlyphs[i], r.centerX(), r.centerY() - U.dpf(6), U.dpf(11), accent);
        tp.setTextSize(U.dpf(9.5f));
        tp.setTypeface(Typeface.create("sans-serif", 0));
        tp.setColor(0xB3FFFFFF);
        String s = shortcutLabels[i];
        c.drawText(s, r.centerX() - tp.measureText(s) / 2f, r.bottom - U.dpf(9), tp);
    }

    private void drawStats(Canvas c, int accent) {
        p.setColor(0x14FFFFFF);
        c.drawRoundRect(statsR, U.dpf(22), U.dpf(22), p);
        float cx = statsR.left + U.dpf(44);
        float cy = statsR.centerY();
        float rad = U.dpf(26);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(U.dpf(6));
        p.setColor(0x22FFFFFF);
        c.drawCircle(cx, cy, rad, p);
        p.setColor(charging ? 0xFF5CE08A : accent);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(new RectF(cx - rad, cy - rad, cx + rad, cy + rad), -90, 360 * batteryLevel, false, p);
        p.setStyle(Paint.Style.FILL);
        tp.setTextSize(U.dpf(13));
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setColor(0xFFFFFFFF);
        String b = Math.round(batteryLevel * 100) + "";
        c.drawText(b, cx - tp.measureText(b) / 2f, cy + U.dpf(4.5f), tp);

        float tx = statsR.left + U.dpf(90);
        tp.setTextSize(U.dpf(11.5f));
        tp.setColor(0xFFE7EBF2);
        c.drawText("Battery", tx, cy - U.dpf(18), tp);
        tp.setTypeface(Typeface.create("sans-serif", 0));
        tp.setColor(0x99FFFFFF);
        c.drawText(batteryText + (charging ? "  ·  charging" : ""), tx, cy - U.dpf(3), tp);
        c.drawText("RAM  " + ramText, tx, cy + U.dpf(14), tp);
        c.drawText("Storage  " + storeText, tx, cy + U.dpf(30), tp);
    }

    // ---------------------------------------------------------------- input
    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (brightR.contains(x, y)) { dragging = 1; setBrightnessFromTouch(y); return true; }
                if (volR.contains(x, y)) { dragging = 2; setVolumeFromTouch(y); return true; }
                for (Tile t : tiles) {
                    if (t.r.contains(x, y)) { pressedTile = t; t.press = 1f; invalidate(); return true; }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging == 1) { setBrightnessFromTouch(y); return true; }
                if (dragging == 2) { setVolumeFromTouch(y); return true; }
                if (pressedTile != null && !pressedTile.r.contains(x, y)) {
                    pressedTile.press = 0f; pressedTile = null; invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging != 0) { dragging = 0; return true; }
                if (pressedTile != null && pressedTile.r.contains(x, y)) {
                    U.haptic(this, false);
                    onTile(pressedTile);
                    pressedTile.press = 0f; pressedTile = null;
                    invalidate();
                    return true;
                }
                if (mediaR.contains(x, y)) { onMediaTouch(x); return true; }
                for (int i = 0; i < 4; i++) {
                    if (shortcutR[i] != null && shortcutR[i].contains(x, y)) {
                        U.haptic(this, false);
                        onShortcut(shortcutIds[i]);
                        return true;
                    }
                }
                if (y > statsR.bottom + U.dpf(10) || y < U.dpf(20)) {
                    if (host != null) host.closeControlCenter();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = 0;
                if (pressedTile != null) { pressedTile.press = 0; pressedTile = null; }
                invalidate();
                return true;
        }
        return true;
    }

    private void setBrightnessFromTouch(float y) {
        brightness = U.clamp((brightR.bottom - y) / brightR.height(), 0.02f, 1f);
        applyBrightness();
        invalidate();
    }

    private void setVolumeFromTouch(float y) {
        volume = U.clamp((volR.bottom - y) / volR.height(), 0f, 1f);
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(volume * max), 0);
        } catch (Throwable ignored) { }
        invalidate();
    }

    private void applyBrightness() {
        Context c = getContext();
        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(c)) {
            requestWriteSettings();
            return;
        }
        try {
            Settings.System.putInt(c.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    Math.round(brightness * 255));
        } catch (Throwable ignored) { }
    }

    private void requestWriteSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Throwable ignored) { }
    }

    private void onTile(Tile t) {
        Context c = getContext();
        if (t.id.equals("torch")) {
            toggleTorch();
            t.on = torchOn;
            return;
        }
        if (t.id.equals("rotate")) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(c)) { requestWriteSettings(); return; }
            try {
                boolean on = Settings.System.getInt(c.getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
                Settings.System.putInt(c.getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION, on ? 0 : 1);
                t.on = !on;
            } catch (Throwable ignored) { }
            return;
        }
        if (t.id.equals("dnd")) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && !nm.isNotificationPolicyAccessGranted()) {
                open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                return;
            }
            try {
                boolean on = nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
                nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_ALL
                        : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                t.on = !on;
            } catch (Throwable ignored) { }
            return;
        }
        if (t.id.equals("zen")) {
            Focus.toggle(c, 30);
            t.on = Focus.active(c);
            return;
        }
        if (t.id.equals("wifi")) {
            if (Build.VERSION.SDK_INT >= 29) {
                open(new Intent(Settings.Panel.ACTION_WIFI));
            } else {
                try {
                    WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    wm.setWifiEnabled(!wm.isWifiEnabled());
                    t.on = wm.isWifiEnabled();
                } catch (Throwable ignored) { }
            }
            return;
        }
        if (t.id.equals("bt")) {
            try {
                BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
                if (Build.VERSION.SDK_INT < 31 && ba != null) {
                    if (ba.isEnabled()) ba.disable(); else ba.enable();
                    t.on = !t.on;
                } else {
                    open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                }
            } catch (Throwable ignored) {
                open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            }
            return;
        }
        if (t.id.equals("data")) {
            open(new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                    : Settings.ACTION_DATA_ROAMING_SETTINGS));
            return;
        }
        if (t.id.equals("plane")) {
            open(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
        }
    }

    private void toggleTorch() {
        try {
            CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            if (cameraId == null) return;
            torchOn = !torchOn;
            cm.setTorchMode(cameraId, torchOn);
        } catch (Throwable t) { torchOn = false; }
    }

    private void onMediaTouch(float x) {
        if (media == null) return;
        float rx = mediaR.right - U.dpf(28);
        MediaController.TransportControls tc = media.getTransportControls();
        if (Math.abs(x - rx) < U.dpf(22)) { tc.skipToNext(); }
        else if (Math.abs(x - (rx - U.dpf(40))) < U.dpf(22)) {
            if (mediaPlaying) tc.pause(); else tc.play();
            mediaPlaying = !mediaPlaying;
        } else if (Math.abs(x - (rx - U.dpf(78))) < U.dpf(22)) { tc.skipToPrevious(); }
        U.haptic(this, false);
        invalidate();
        postDelayed(new Runnable() { public void run() { syncMedia(); invalidate(); } }, 400);
    }

    private void onShortcut(String id) {
        if (id.equals("settings")) { if (host != null) host.openSettings(); return; }
        if (id.equals("camera")) { open(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); return; }
        if (id.equals("clock")) { open(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)); return; }
        if (id.equals("calc")) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_APP_CALCULATOR);
            open(i);
        }
    }

    private void open(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            if (host != null) host.closeControlCenter();
        } catch (Throwable ignored) { }
    }
}
