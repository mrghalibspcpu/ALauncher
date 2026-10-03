package com.alauncher;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A Control Center — a full replacement for the system quick-settings panel,
 * drawn as liquid glass. Everything the platform lets a non-system app change
 * is changed in place; everything else deep-links to the exact system screen.
 */
public class ControlCenter extends View {

    public interface Host {
        void closeControlCenter();
        void openSettings();
        void lockScreen();
        void setKeepAwake(boolean on);
        boolean keepAwake();
        void onThemeChanged();
        int accent();
    }

    private static class Tile {
        final String id, label;
        final int glyph;
        String sub = "";
        boolean on;
        final RectF r = new RectF();
        float press;
        Tile(String id, String label, int glyph) { this.id = id; this.label = label; this.glyph = glyph; }
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Tile> big = new ArrayList<>();
    private final List<Tile> small = new ArrayList<>();
    private final RectF brightR = new RectF(), volR = new RectF(), ringR = new RectF();
    private final RectF mediaR = new RectF(), statsR = new RectF();
    private final RectF[] shortcutR = new RectF[5];
    private final String[] shortcutIds = {"calc", "camera", "clock", "sys", "alauncher"};
    private final int[] shortcutGlyphs = {Glyphs.CALC, Glyphs.CAMERA, Glyphs.TIMER, Glyphs.GEAR, Glyphs.STAR};
    private final String[] shortcutLabels = {"Calculator", "Camera", "Alarms", "System", "A Launcher"};

    private final Host host;
    private boolean dark = true;
    private float scrollY, contentH;
    private int dragging;              // 1 brightness 2 media vol 3 ring vol
    private Tile pressedTile;
    private boolean scrolling;
    private float downX, downY, lastY;
    private long downTime;

    private float brightness = 0.5f, volume = 0.5f, ring = 0.5f;
    private boolean torchOn;
    private String cameraId;
    private MediaController media;
    private String mediaTitle = "", mediaArtist = "";
    private Bitmap mediaArt;
    private boolean mediaPlaying;
    private String batteryText = "", ramText = "", storeText = "", netText = "", upText = "";
    private float batteryLevel = 0f;
    private boolean charging;

    public ControlCenter(Context c, Host h) {
        super(c);
        host = h;
        setClickable(true);
        try {
            CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) { cameraId = id; break; }
            }
        } catch (Throwable ignored) { }
        big.add(new Tile("wifi", "Wi-Fi", Glyphs.WIFI));
        big.add(new Tile("data", "Mobile data", Glyphs.DATA));
        big.add(new Tile("bt", "Bluetooth", Glyphs.BT));
        big.add(new Tile("plane", "Airplane", Glyphs.PLANE));

        small.add(new Tile("torch", "Torch", Glyphs.TORCH));
        small.add(new Tile("rotate", "Rotate", Glyphs.ROTATE));
        small.add(new Tile("dnd", "DND", Glyphs.MOON));
        small.add(new Tile("ringer", "Ringer", Glyphs.VOLUME));
        small.add(new Tile("sync", "Sync", Glyphs.ROTATE));
        small.add(new Tile("location", "Location", Glyphs.PIN));
        small.add(new Tile("saver", "Saver", Glyphs.BOLT));
        small.add(new Tile("hotspot", "Hotspot", Glyphs.CAST));
        small.add(new Tile("nfc", "NFC", Glyphs.SHORTCUT));
        small.add(new Tile("timeout", "Timeout", Glyphs.TIMER));
        small.add(new Tile("caffeine", "Caffeine", Glyphs.BULB));
        small.add(new Tile("theme", "Theme", Glyphs.SUN));
        small.add(new Tile("zen", "Focus", Glyphs.SHIELD));
        small.add(new Tile("night", "Night light", Glyphs.MOON));
        small.add(new Tile("cast", "Cast", Glyphs.CAST));
        small.add(new Tile("lock", "Lock", Glyphs.LOCK));
    }

    private Tile tile(String id) {
        for (Tile t : big) if (t.id.equals(id)) return t;
        for (Tile t : small) if (t.id.equals(id)) return t;
        return big.get(0);
    }

    // ----------------------------------------------------------- live sync
    public void sync() {
        dark = Theme.dark(getContext());
        Context c = getContext();
        ContentResolver cr = c.getContentResolver();

        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            Tile t = tile("wifi");
            t.on = wm != null && wm.isWifiEnabled();
            t.sub = t.on ? ssid(wm) : "Off";
        } catch (Throwable ignored) { }
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            Tile t = tile("bt");
            t.on = ba != null && ba.isEnabled();
            t.sub = ba == null ? "Unavailable" : (t.on ? "On" : "Off");
        } catch (Throwable ignored) { }
        try {
            Tile t = tile("plane");
            t.on = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
            t.sub = t.on ? "On" : "Off";
        } catch (Throwable ignored) { }
        try {
            Tile t = tile("data");
            t.sub = networkType();
            t.on = !"No data".equals(t.sub) && !"Off".equals(t.sub);
        } catch (Throwable ignored) { }
        try {
            Tile t = tile("rotate");
            t.on = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
        } catch (Throwable ignored) { }
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            tile("dnd").on = nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
        } catch (Throwable ignored) { }
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            int m = am.getRingerMode();
            Tile t = tile("ringer");
            t.on = m == AudioManager.RINGER_MODE_NORMAL;
            t.sub = m == AudioManager.RINGER_MODE_NORMAL ? "Ring"
                    : (m == AudioManager.RINGER_MODE_VIBRATE ? "Vibrate" : "Silent");
        } catch (Throwable ignored) { }
        try { tile("sync").on = ContentResolver.getMasterSyncAutomatically(); } catch (Throwable ignored) { }
        try {
            android.location.LocationManager lm =
                    (android.location.LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            tile("location").on = lm != null
                    && (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER));
        } catch (Throwable ignored) { }
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            tile("saver").on = pm != null && pm.isPowerSaveMode();
        } catch (Throwable ignored) { }
        try {
            NfcAdapter na = NfcAdapter.getDefaultAdapter(c);
            Tile t = tile("nfc");
            t.on = na != null && na.isEnabled();
            t.sub = na == null ? "n/a" : (t.on ? "On" : "Off");
        } catch (Throwable ignored) { }
        try {
            int ms = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000);
            Tile t = tile("timeout");
            t.sub = ms >= 60000 ? (ms / 60000) + "m" : (ms / 1000) + "s";
            t.on = ms >= 120000;
        } catch (Throwable ignored) { }
        tile("caffeine").on = host.keepAwake();
        tile("theme").on = !dark;
        tile("theme").sub = Prefs.get(c).i("theme", Theme.AUTO) == Theme.AUTO
                ? "Auto" : (dark ? "Dark" : "Light");
        tile("zen").on = Focus.active(c);
        tile("torch").on = torchOn;

        try {
            int b = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128);
            brightness = U.clamp(b / 255f, 0.02f, 1f);
        } catch (Throwable ignored) { }
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            volume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    / (float) Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            ring = am.getStreamVolume(AudioManager.STREAM_RING)
                    / (float) Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_RING));
        } catch (Throwable ignored) { }

        syncMedia();
        syncVitals();
        layoutAll();
        invalidate();
    }

    private String ssid(WifiManager wm) {
        try {
            String s = wm.getConnectionInfo().getSSID();
            if (s == null || s.contains("unknown") || s.length() < 3) return "On";
            return s.replace("\"", "");
        } catch (Throwable t) { return "On"; }
    }

    private String networkType() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) return "No data";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "On Wi-Fi";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager)
                        getContext().getSystemService(Context.TELEPHONY_SERVICE);
                return tm != null ? "Cellular" : "On";
            }
            return "Connected";
        } catch (Throwable t) { return "On"; }
    }

    private void syncMedia() {
        media = null; mediaTitle = ""; mediaArtist = ""; mediaArt = null; mediaPlaying = false;
        for (MediaController mc : NotifService.sessions(getContext())) {
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
            if (mediaTitle == null || "null".equals(mediaTitle)) mediaTitle = media.getPackageName();
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
                int volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                batteryText = (temp / 10f) + "°C  ·  " + (volt / 1000f) + "V";
            }
        } catch (Throwable ignored) { }
        try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            ramText = (mi.availMem >> 20) + " MB free / " + (mi.totalMem >> 20) + " MB";
        } catch (Throwable ignored) { }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            storeText = (fs.getAvailableBytes() / (1024 * 1024 * 1024L)) + " GB free / "
                    + (fs.getTotalBytes() / (1024 * 1024 * 1024L)) + " GB";
        } catch (Throwable ignored) { }
        netText = networkType();
        long up = SystemClock.elapsedRealtime() / 1000;
        upText = (up / 3600) + "h " + ((up % 3600) / 60) + "m";
    }

    // -------------------------------------------------------------- layout
    private void layoutAll() {
        float pad = U.dpf(16);
        float w = getWidth() - pad * 2;
        if (w <= 0) return;
        float gap = U.dpf(10);
        float y = U.dpf(104);

        float blockW = w * 0.58f;
        float cellW = (blockW - gap) / 2f;
        float cellH = U.dpf(70);
        for (int i = 0; i < 4; i++) {
            float x = pad + (i % 2) * (cellW + gap);
            big.get(i).r.set(x, y + (i / 2) * (cellH + gap), x + cellW, y + (i / 2) * (cellH + gap) + cellH);
        }
        float sx = pad + blockW + gap;
        float sw = (w - blockW - gap - gap * 2) / 3f;
        float sh = cellH * 2 + gap;
        brightR.set(sx, y, sx + sw, y + sh);
        volR.set(sx + sw + gap, y, sx + sw * 2 + gap, y + sh);
        ringR.set(sx + sw * 2 + gap * 2, y, sx + sw * 3 + gap * 2, y + sh);

        y += sh + gap;
        float qw = (w - gap * 3) / 4f;
        float qh = qw * 0.9f;
        for (int i = 0; i < small.size(); i++) {
            float x = pad + (i % 4) * (qw + gap);
            float ty = y + (i / 4) * (qh + gap);
            small.get(i).r.set(x, ty, x + qw, ty + qh);
        }
        y += Math.ceil(small.size() / 4f) * (qh + gap);

        mediaR.set(pad, y, pad + w, y + U.dpf(94));
        y += U.dpf(94) + gap;

        float shw = (w - gap * 4) / 5f;
        for (int i = 0; i < 5; i++) {
            shortcutR[i] = new RectF(pad + i * (shw + gap), y, pad + i * (shw + gap) + shw, y + U.dpf(64));
        }
        y += U.dpf(64) + gap;

        statsR.set(pad, y, pad + w, y + U.dpf(118));
        contentH = y + U.dpf(118) + U.dpf(40);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutAll();
    }

    private float maxScroll() { return Math.max(0, contentH - getHeight()); }

    // ---------------------------------------------------------------- draw
    @Override protected void onDraw(Canvas c) {
        if (getWidth() == 0) return;
        if (contentH == 0) layoutAll();
        int accent = host.accent();
        Theme.sheet(c, getContext(), getWidth(), getHeight(), dark, accent);

        c.save();
        c.translate(0, -scrollY);
        drawHeader(c, accent);
        for (Tile t : big) drawBigTile(c, t, accent);
        drawSlider(c, brightR, brightness, Glyphs.SUN, accent, 0);
        drawSlider(c, volR, volume, Glyphs.VOLUME, accent, 1);
        drawSlider(c, ringR, ring, Glyphs.BT, accent, 2);
        for (Tile t : small) drawSmallTile(c, t, accent);
        drawMedia(c, accent);
        for (int i = 0; i < 5; i++) drawShortcut(c, i, accent);
        drawStats(c, accent);
        c.restore();

        // grabber
        p.reset();
        p.setAntiAlias(true);
        p.setColor(dark ? 0x59FFFFFF : 0x4D000000);
        c.drawRoundRect(new RectF(getWidth() / 2f - U.dpf(24), getHeight() - U.dpf(16),
                getWidth() / 2f + U.dpf(24), getHeight() - U.dpf(12)), U.dpf(2), U.dpf(2), p);
    }

    private void drawHeader(Canvas c, int accent) {
        tp.reset();
        tp.setAntiAlias(true);
        tp.setColor(Theme.text(dark));
        tp.setTextSize(U.dpf(32));
        tp.setTypeface(Typeface.create("sans-serif-thin", 0));
        c.drawText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), U.dpf(20), U.dpf(62), tp);
        tp.setTextSize(U.dpf(12));
        tp.setTypeface(Typeface.create("sans-serif", 0));
        tp.setColor(Theme.textDim(dark));
        c.drawText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()),
                U.dpf(21), U.dpf(80), tp);

        tp.setTextSize(U.dpf(10.5f));
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setColor(U.alpha(accent, 0.95f));
        String brand = "A CONTROL CENTER";
        c.drawText(brand, getWidth() - U.dpf(20) - tp.measureText(brand), U.dpf(36), tp);

        RectF r = new RectF(getWidth() - U.dpf(86), U.dpf(50), getWidth() - U.dpf(20), U.dpf(74));
        Theme.glass(c, getContext(), r, r.height() / 2f, dark, accent, 0.5f);
        RectF f = new RectF(r.left + U.dpf(3), r.top + U.dpf(3),
                r.left + U.dpf(3) + (r.width() - U.dpf(6)) * batteryLevel, r.bottom - U.dpf(3));
        p.reset(); p.setAntiAlias(true);
        p.setColor(U.alpha(charging ? 0xFF4ADE80 : accent, 0.9f));
        c.drawRoundRect(f, f.height() / 2f, f.height() / 2f, p);
        tp.setTextSize(U.dpf(11));
        tp.setColor(Theme.text(dark));
        String s = Math.round(batteryLevel * 100) + "%" + (charging ? " ⚡" : "");
        c.drawText(s, r.centerX() - tp.measureText(s) / 2f, r.centerY() + U.dpf(4), tp);
    }

    private void drawBigTile(Canvas c, Tile t, int accent) {
        float rad = U.dpf(22);
        c.save();
        float sc = 1f - 0.04f * t.press;
        c.scale(sc, sc, t.r.centerX(), t.r.centerY());
        if (t.on) Theme.glassActive(c, getContext(), t.r, rad, dark, accent);
        else Theme.glass(c, getContext(), t.r, rad, dark, accent, 1f);
        int fg = t.on ? Theme.onAccent(accent) : Theme.icon(dark);
        Glyphs.draw(c, t.glyph, t.r.left + U.dpf(24), t.r.centerY(), U.dpf(11), fg);
        tp.reset(); tp.setAntiAlias(true);
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setTextSize(U.dpf(12.5f));
        tp.setColor(fg);
        c.drawText(U.ellipsize(tp, t.label, t.r.width() - U.dpf(48)), t.r.left + U.dpf(42),
                t.r.centerY() - U.dpf(2), tp);
        tp.setTextSize(U.dpf(10.5f));
        tp.setTypeface(Typeface.create("sans-serif", 0));
        tp.setColor(U.alpha(fg, 0.7f));
        c.drawText(U.ellipsize(tp, t.sub.isEmpty() ? (t.on ? "On" : "Off") : t.sub, t.r.width() - U.dpf(48)),
                t.r.left + U.dpf(42), t.r.centerY() + U.dpf(14), tp);
        c.restore();
    }

    private void drawSmallTile(Canvas c, Tile t, int accent) {
        float rad = U.dpf(20);
        c.save();
        float sc = 1f - 0.05f * t.press;
        c.scale(sc, sc, t.r.centerX(), t.r.centerY());
        if (t.on) Theme.glassActive(c, getContext(), t.r, rad, dark, accent);
        else Theme.glass(c, getContext(), t.r, rad, dark, accent, 0.85f);
        int fg = t.on ? Theme.onAccent(accent) : Theme.icon(dark);
        Glyphs.draw(c, t.glyph, t.r.centerX(), t.r.centerY() - U.dpf(8), U.dpf(11), fg);
        tp.reset(); tp.setAntiAlias(true);
        tp.setTextSize(U.dpf(9.5f));
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setColor(fg);
        String s = U.ellipsize(tp, t.label, t.r.width() - U.dpf(8));
        c.drawText(s, t.r.centerX() - tp.measureText(s) / 2f, t.r.bottom - U.dpf(16), tp);
        if (!t.sub.isEmpty()) {
            tp.setTextSize(U.dpf(8.5f));
            tp.setColor(U.alpha(fg, 0.72f));
            String s2 = U.ellipsize(tp, t.sub, t.r.width() - U.dpf(8));
            c.drawText(s2, t.r.centerX() - tp.measureText(s2) / 2f, t.r.bottom - U.dpf(5), tp);
        }
        c.restore();
    }

    private void drawSlider(Canvas c, RectF r, float value, int glyph, int accent, int kind) {
        float rad = U.dpf(22);
        Theme.glass(c, getContext(), r, rad, dark, accent, 1f);
        c.save();
        Path clip = new Path();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(clip);
        float fillTop = r.bottom - r.height() * U.clamp(value, 0.015f, 1f);
        int c1 = kind == 0 ? 0xFFFFE8A3 : U.boost(accent, 1f, 1.25f);
        int c2 = kind == 0 ? 0xFFFFB347 : accent;
        p.reset(); p.setAntiAlias(true);
        p.setShader(new LinearGradient(r.left, fillTop, r.left, r.bottom, c1, c2, Shader.TileMode.CLAMP));
        c.drawRect(r.left, fillTop, r.right, r.bottom, p);
        p.setShader(null);
        p.setColor(0x33FFFFFF);
        c.drawRect(r.left, fillTop, r.right, fillTop + U.dpf(1.4f), p);
        c.restore();
        int fg = value > 0.22f ? 0xFF14161C : Theme.icon(dark);
        Glyphs.draw(c, glyph, r.centerX(), r.bottom - U.dpf(22), U.dpf(10), fg);
        tp.reset(); tp.setAntiAlias(true);
        tp.setTextSize(U.dpf(10.5f));
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setColor(Theme.textDim(dark));
        String s = Math.round(value * 100) + "%";
        c.drawText(s, r.centerX() - tp.measureText(s) / 2f, r.top + U.dpf(20), tp);
    }

    private void drawMedia(Canvas c, int accent) {
        Theme.glass(c, getContext(), mediaR, U.dpf(24), dark, accent, 1f);
        float as = mediaR.height() - U.dpf(24);
        RectF art = new RectF(mediaR.left + U.dpf(12), mediaR.top + U.dpf(12),
                mediaR.left + U.dpf(12) + as, mediaR.bottom - U.dpf(12));
        p.reset(); p.setAntiAlias(true);
        if (mediaArt != null) {
            c.save();
            Path pa = new Path();
            pa.addRoundRect(art, U.dpf(14), U.dpf(14), Path.Direction.CW);
            c.clipPath(pa);
            c.drawBitmap(mediaArt, null, art, p);
            c.restore();
        } else {
            p.setColor(U.alpha(accent, 0.22f));
            c.drawRoundRect(art, U.dpf(14), U.dpf(14), p);
            Glyphs.draw(c, Glyphs.PLAY, art.centerX(), art.centerY(), U.dpf(12), U.alpha(accent, 0.95f));
        }
        tp.reset(); tp.setAntiAlias(true);
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setTextSize(U.dpf(14));
        tp.setColor(Theme.text(dark));
        float textW = mediaR.width() - as - U.dpf(150);
        c.drawText(U.ellipsize(tp, media == null ? "Nothing playing" : mediaTitle, textW),
                art.right + U.dpf(14), mediaR.centerY() - U.dpf(4), tp);
        tp.setTextSize(U.dpf(11.5f));
        tp.setTypeface(Typeface.create("sans-serif", 0));
        tp.setColor(Theme.textDim(dark));
        String sub = media == null ? "Start a music app to control it here"
                : (mediaArtist == null || "null".equals(mediaArtist) ? media.getPackageName() : mediaArtist);
        c.drawText(U.ellipsize(tp, sub, textW), art.right + U.dpf(14), mediaR.centerY() + U.dpf(14), tp);

        float cy = mediaR.centerY();
        float rx = mediaR.right - U.dpf(28);
        Glyphs.draw(c, Glyphs.NEXT, rx, cy, U.dpf(10), Theme.icon(dark));
        RectF pb = new RectF(rx - U.dpf(58), cy - U.dpf(18), rx - U.dpf(22), cy + U.dpf(18));
        Theme.glassActive(c, getContext(), pb, pb.height() / 2f, dark, accent);
        Glyphs.draw(c, mediaPlaying ? Glyphs.PAUSE : Glyphs.PLAY, pb.centerX(), cy, U.dpf(10), Theme.onAccent(accent));
        Glyphs.draw(c, Glyphs.PREV, rx - U.dpf(78), cy, U.dpf(10), Theme.icon(dark));
    }

    private void drawShortcut(Canvas c, int i, int accent) {
        RectF r = shortcutR[i];
        if (r == null) return;
        Theme.glass(c, getContext(), r, U.dpf(20), dark, accent, 0.8f);
        Glyphs.draw(c, shortcutGlyphs[i], r.centerX(), r.centerY() - U.dpf(7), U.dpf(10), accent);
        tp.reset(); tp.setAntiAlias(true);
        tp.setTextSize(U.dpf(8.8f));
        tp.setTypeface(Typeface.create("sans-serif", 0));
        tp.setColor(Theme.textDim(dark));
        String s = U.ellipsize(tp, shortcutLabels[i], r.width() - U.dpf(4));
        c.drawText(s, r.centerX() - tp.measureText(s) / 2f, r.bottom - U.dpf(10), tp);
    }

    private void drawStats(Canvas c, int accent) {
        Theme.glass(c, getContext(), statsR, U.dpf(24), dark, accent, 1f);
        float cx = statsR.left + U.dpf(52), cy = statsR.centerY(), rad = U.dpf(30);
        p.reset(); p.setAntiAlias(true);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(U.dpf(7));
        p.setColor(dark ? 0x26FFFFFF : 0x1A000000);
        c.drawCircle(cx, cy, rad, p);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setShader(new LinearGradient(cx - rad, cy - rad, cx + rad, cy + rad,
                charging ? 0xFF4ADE80 : U.boost(accent, 1f, 1.2f),
                charging ? 0xFF22C55E : accent, Shader.TileMode.CLAMP));
        c.drawArc(new RectF(cx - rad, cy - rad, cx + rad, cy + rad), -90, 360 * batteryLevel, false, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        tp.reset(); tp.setAntiAlias(true);
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setTextSize(U.dpf(14));
        tp.setColor(Theme.text(dark));
        String b = Math.round(batteryLevel * 100) + "";
        c.drawText(b, cx - tp.measureText(b) / 2f, cy + U.dpf(5), tp);

        float tx = statsR.left + U.dpf(100);
        tp.setTextSize(U.dpf(11.5f));
        c.drawText(charging ? "Charging" : "Battery", tx, cy - U.dpf(28), tp);
        tp.setTypeface(Typeface.create("sans-serif", 0));
        tp.setColor(Theme.textDim(dark));
        c.drawText(batteryText, tx, cy - U.dpf(12), tp);
        c.drawText("RAM  " + ramText, tx, cy + U.dpf(6), tp);
        c.drawText("Storage  " + storeText, tx, cy + U.dpf(24), tp);
        c.drawText("Network  " + netText + "   ·   Uptime  " + upText, tx, cy + U.dpf(42), tp);
    }

    // --------------------------------------------------------------- input
    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY() + scrollY;
        int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x; downY = e.getY(); lastY = e.getY();
                downTime = System.currentTimeMillis();
                scrolling = false; dragging = 0; pressedTile = null;
                if (brightR.contains(x, y)) { dragging = 1; setBrightness(y); return true; }
                if (volR.contains(x, y)) { dragging = 2; setVolume(y); return true; }
                if (ringR.contains(x, y)) { dragging = 3; setRing(y); return true; }
                for (Tile t : big) if (t.r.contains(x, y)) { pressedTile = t; t.press = 1f; invalidate(); return true; }
                for (Tile t : small) if (t.r.contains(x, y)) { pressedTile = t; t.press = 1f; invalidate(); return true; }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragging == 1) { setBrightness(y); return true; }
                if (dragging == 2) { setVolume(y); return true; }
                if (dragging == 3) { setRing(y); return true; }
                if (!scrolling && Math.abs(e.getY() - downY) > slop) {
                    scrolling = true;
                    if (pressedTile != null) { pressedTile.press = 0; pressedTile = null; }
                }
                if (scrolling) {
                    scrollY = U.clamp(scrollY - (e.getY() - lastY), 0, maxScroll());
                    lastY = e.getY();
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (dragging != 0) { dragging = 0; return true; }
                if (scrolling) {
                    if (scrollY <= 0 && e.getY() - downY > U.dpf(90)) host.closeControlCenter();
                    scrolling = false;
                    return true;
                }
                if (pressedTile != null && pressedTile.r.contains(x, y)) {
                    U.haptic(this, false);
                    onTile(pressedTile);
                    pressedTile.press = 0; pressedTile = null;
                    sync();
                    return true;
                }
                if (mediaR.contains(x, y)) { onMediaTouch(x); return true; }
                for (int i = 0; i < 5; i++) {
                    if (shortcutR[i] != null && shortcutR[i].contains(x, y)) {
                        U.haptic(this, false);
                        onShortcut(shortcutIds[i]);
                        return true;
                    }
                }
                if (y > statsR.bottom + U.dpf(8) || y < U.dpf(24)) host.closeControlCenter();
                return true;

            case MotionEvent.ACTION_CANCEL:
                dragging = 0; scrolling = false;
                if (pressedTile != null) { pressedTile.press = 0; pressedTile = null; }
                invalidate();
                return true;
        }
        return true;
    }

    private void setBrightness(float y) {
        brightness = U.clamp((brightR.bottom - y) / brightR.height(), 0.02f, 1f);
        Context c = getContext();
        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(c)) { requestWriteSettings(); return; }
        try {
            Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    Math.round(brightness * 255));
        } catch (Throwable ignored) { }
        invalidate();
    }

    private void setVolume(float y) {
        volume = U.clamp((volR.bottom - y) / volR.height(), 0f, 1f);
        setStream(AudioManager.STREAM_MUSIC, volume);
        invalidate();
    }

    private void setRing(float y) {
        ring = U.clamp((ringR.bottom - y) / ringR.height(), 0f, 1f);
        setStream(AudioManager.STREAM_RING, ring);
        invalidate();
    }

    private void setStream(int stream, float v) {
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            am.setStreamVolume(stream, Math.round(v * am.getStreamMaxVolume(stream)), 0);
        } catch (Throwable ignored) { }
    }

    private void requestWriteSettings() {
        open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getContext().getPackageName())), false);
    }

    private void onTile(Tile t) {
        Context c = getContext();
        ContentResolver cr = c.getContentResolver();
        switch (t.id) {
            case "torch":
                try {
                    CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
                    if (cameraId != null) { torchOn = !torchOn; cm.setTorchMode(cameraId, torchOn); }
                } catch (Throwable x) { torchOn = false; }
                break;
            case "rotate":
                if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(c)) { requestWriteSettings(); break; }
                try {
                    boolean on = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
                    Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, on ? 0 : 1);
                } catch (Throwable ignored) { }
                break;
            case "dnd": {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= 23 && !nm.isNotificationPolicyAccessGranted()) {
                    open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), false);
                    break;
                }
                try {
                    boolean on = nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
                    nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_ALL
                            : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                } catch (Throwable ignored) { }
                break;
            }
            case "ringer":
                try {
                    AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                    int m = am.getRingerMode();
                    int next = m == AudioManager.RINGER_MODE_NORMAL ? AudioManager.RINGER_MODE_VIBRATE
                            : (m == AudioManager.RINGER_MODE_VIBRATE ? AudioManager.RINGER_MODE_SILENT
                            : AudioManager.RINGER_MODE_NORMAL);
                    am.setRingerMode(next);
                } catch (Throwable x) {
                    open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), false);
                }
                break;
            case "sync":
                try { ContentResolver.setMasterSyncAutomatically(!ContentResolver.getMasterSyncAutomatically()); }
                catch (Throwable ignored) { }
                break;
            case "timeout":
                if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(c)) { requestWriteSettings(); break; }
                try {
                    int[] steps = {15000, 30000, 60000, 120000, 300000, 600000, 1800000};
                    int cur = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000);
                    int next = steps[0];
                    for (int i = 0; i < steps.length; i++) {
                        if (cur < steps[i]) { next = steps[i]; break; }
                        if (i == steps.length - 1) next = steps[0];
                    }
                    Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, next);
                } catch (Throwable ignored) { }
                break;
            case "caffeine":
                host.setKeepAwake(!host.keepAwake());
                break;
            case "theme": {
                Prefs pr = Prefs.get(c);
                int m = pr.i("theme", Theme.AUTO);
                pr.setI("theme", m == Theme.AUTO ? Theme.LIGHT : (m == Theme.LIGHT ? Theme.DARK : Theme.AUTO));
                Theme.invalidateBackdrop();
                dark = Theme.dark(c);
                host.onThemeChanged();
                break;
            }
            case "zen":
                Focus.toggle(c, 30);
                break;
            case "lock":
                host.lockScreen();
                break;
            case "wifi":
                if (Build.VERSION.SDK_INT >= 29) open(new Intent(Settings.Panel.ACTION_WIFI), false);
                else {
                    try {
                        WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        wm.setWifiEnabled(!wm.isWifiEnabled());
                    } catch (Throwable ignored) { }
                }
                break;
            case "bt":
                try {
                    BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
                    if (Build.VERSION.SDK_INT < 31 && ba != null) {
                        if (ba.isEnabled()) ba.disable(); else ba.enable();
                    } else open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), false);
                } catch (Throwable x) { open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), false); }
                break;
            case "data":
                open(new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                        : Settings.ACTION_DATA_ROAMING_SETTINGS), false);
                break;
            case "plane": open(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), false); break;
            case "location": open(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), false); break;
            case "saver": open(new Intent("android.settings.BATTERY_SAVER_SETTINGS"), false); break;
            case "hotspot": open(new Intent("android.settings.TETHER_SETTINGS"), false); break;
            case "nfc": open(new Intent("android.settings.NFC_SETTINGS"), false); break;
            case "night": open(new Intent("android.settings.NIGHT_DISPLAY_SETTINGS"), false); break;
            case "cast": open(new Intent("android.settings.CAST_SETTINGS"), false); break;
        }
    }

    private void onMediaTouch(float x) {
        if (media == null) return;
        float rx = mediaR.right - U.dpf(28);
        MediaController.TransportControls tc = media.getTransportControls();
        if (Math.abs(x - rx) < U.dpf(20)) tc.skipToNext();
        else if (Math.abs(x - (rx - U.dpf(40))) < U.dpf(24)) {
            if (mediaPlaying) tc.pause(); else tc.play();
            mediaPlaying = !mediaPlaying;
        } else if (Math.abs(x - (rx - U.dpf(78))) < U.dpf(20)) tc.skipToPrevious();
        U.haptic(this, false);
        invalidate();
        postDelayed(new Runnable() { public void run() { syncMedia(); invalidate(); } }, 400);
    }

    private void onShortcut(String id) {
        switch (id) {
            case "alauncher": host.openSettings(); break;
            case "sys": open(new Intent(Settings.ACTION_SETTINGS), true); break;
            case "camera": open(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), true); break;
            case "clock": open(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), true); break;
            case "calc": {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_APP_CALCULATOR);
                open(i, true);
                break;
            }
        }
    }

    private void open(Intent i, boolean close) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            if (close) host.closeControlCenter();
        } catch (Throwable ignored) { }
    }
}
