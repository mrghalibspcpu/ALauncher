package com.alauncher;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Two complete looks — Liquid Light and Liquid Dark — plus the glassmorphism
 * renderer every surface of A Launcher is painted with.
 *
 * A single blurred copy of the wallpaper is kept in memory and sampled by each
 * card in screen space, so cards really do refract what is behind them.
 */
public class Theme {

    public static final int AUTO = 0, LIGHT = 1, DARK = 2;

    private static Bitmap backdrop;       // blurred wallpaper, screen sized / 6
    private static Bitmap backdropLight;  // brightened variant
    private static int bw, bh;
    private static boolean darkCache;

    public static boolean dark(Context c) {
        int m = Prefs.get(c).i("theme", AUTO);
        if (m == LIGHT) return false;
        if (m == DARK) return true;
        int ui = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return ui != Configuration.UI_MODE_NIGHT_NO;
    }

    // ------------------------------------------------------------- palette
    public static int text(boolean dark)      { return dark ? 0xFFF2F5FA : 0xFF0E1218; }
    public static int textDim(boolean dark)   { return dark ? 0x99F2F5FA : 0x990E1218; }
    public static int textFaint(boolean dark) { return dark ? 0x5CF2F5FA : 0x5C0E1218; }
    public static int icon(boolean dark)      { return dark ? 0xFFE8ECF4 : 0xFF161B22; }
    public static int scrim(boolean dark)     { return dark ? 0xCC05070C : 0xB3F2F4F8; }
    public static int sheetBg(boolean dark)   { return dark ? 0xF21A1D26 : 0xF2FBFCFE; }
    public static int cardFill(boolean dark)  { return dark ? 0x26FFFFFF : 0x40FFFFFF; }
    public static int cardStroke(boolean dark){ return dark ? 0x33FFFFFF : 0x66FFFFFF; }
    public static int divider(boolean dark)   { return dark ? 0x1AFFFFFF : 0x1A000000; }
    public static int onAccent(int accent)    { return U.isLight(accent) ? 0xFF0B0E14 : 0xFFFFFFFF; }

    public static int shadow(boolean dark)    { return dark ? 0x66000000 : 0x33203040; }

    // ------------------------------------------------------------ backdrop
    public static void invalidateBackdrop() { backdrop = null; backdropLight = null; }

    /** Blurred wallpaper (or a generated aurora when the wallpaper is unreadable). */
    public static Bitmap backdrop(Context c, int w, int h, boolean dark) {
        if (backdrop == null || bw != w || bh != h || darkCache != dark) {
            bw = w; bh = h; darkCache = dark;
            int sw = Math.max(40, w / 6), sh = Math.max(60, h / 6);
            Bitmap base = null;
            try {
                WallpaperManager wm = WallpaperManager.getInstance(c);
                Drawable d = wm.getDrawable();
                if (d != null) {
                    base = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
                    Canvas cv = new Canvas(base);
                    d.setBounds(0, 0, sw, sh);
                    d.draw(cv);
                }
            } catch (Throwable ignored) { }
            if (base == null) base = aurora(sw, sh, Prefs.get(c).accent(), dark);
            Bitmap blurred = U.blur(base, 14);
            // tint pass so text always stays readable
            Canvas cv = new Canvas(blurred);
            Paint p = new Paint();
            p.setColor(dark ? 0x59000000 : 0x40FFFFFF);
            cv.drawRect(0, 0, blurred.getWidth(), blurred.getHeight(), p);
            backdrop = blurred;
        }
        return backdrop;
    }

    private static Bitmap aurora(int w, int h, int accent, boolean dark) {
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        c.drawColor(dark ? 0xFF0A0D14 : 0xFFEDF1F7);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] cols = {accent, U.boost(accent, 0.8f, dark ? 0.7f : 1.2f), 0xFFB47BFF, 0xFF5CE08A};
        for (int i = 0; i < 4; i++) {
            float cx = w * (0.2f + 0.22f * i), cy = h * (0.15f + 0.24f * ((i * 7) % 4));
            p.setShader(new RadialGradient(cx, cy, w * 0.6f,
                    U.alpha(cols[i], dark ? 0.35f : 0.45f), 0x00000000, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, w * 0.6f, p);
        }
        p.setShader(null);
        return bm;
    }

    // --------------------------------------------------------------- glass
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Full-screen frosted sheet (used by the drawer and Control Center). */
    public static void sheet(Canvas c, Context ctx, int w, int h, boolean dark, int accent) {
        Bitmap bd = backdrop(ctx, w, h, dark);
        P.reset();
        P.setAntiAlias(true);
        P.setFilterBitmap(true);
        c.drawBitmap(bd, null, new RectF(0, 0, w, h), P);
        P.setShader(new LinearGradient(0, 0, w * 0.4f, h,
                U.alpha(accent, dark ? 0.12f : 0.16f),
                dark ? 0xB3070A10 : 0xA6FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, P);
        P.setShader(null);
        P.setColor(dark ? 0xA6080A10 : 0x99FFFFFF);
        c.drawRect(0, 0, w, h, P);
    }

    /**
     * Liquid glass card: refracted backdrop, soft tint, specular sheen,
     * hairline rim light and a lifted shadow.
     */
    public static void glass(Canvas c, Context ctx, RectF r, float rad,
                             boolean dark, int accent, float strength) {
        if (r.width() <= 0 || r.height() <= 0) return;
        P.reset();
        P.setAntiAlias(true);

        // lifted shadow
        U.softShadow(c, r, rad, shadow(dark), U.dpf(7) * strength, U.dpf(3));

        c.save();
        Path clip = new Path();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(clip);

        // refracted backdrop (sampled in screen space, pushed slightly for a lens feel)
        Bitmap bd = backdrop;
        if (bd != null) {
            P.setFilterBitmap(true);
            float ox = (r.centerX() - bw / 2f) * 0.012f;
            float oy = (r.centerY() - bh / 2f) * 0.012f;
            c.drawBitmap(bd, null, new RectF(-ox, -oy, bw - ox, bh - oy), P);
        }
        // glass tint
        P.setColor(dark ? 0x3DFFFFFF : 0x59FFFFFF);
        c.drawRect(r, P);
        P.setShader(new LinearGradient(r.left, r.top, r.left + r.width() * 0.6f, r.bottom,
                dark ? 0x26FFFFFF : 0x80FFFFFF, dark ? 0x0DFFFFFF : 0x1AFFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(r, P);
        P.setShader(null);
        // accent bloom in the corner
        P.setShader(new RadialGradient(r.left + r.width() * 0.15f, r.top + r.height() * 0.1f,
                Math.max(r.width(), r.height()) * 0.8f,
                U.alpha(accent, dark ? 0.16f : 0.14f), 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(r, P);
        P.setShader(null);
        // specular sweep along the top edge
        P.setShader(new LinearGradient(r.left, r.top, r.right, r.top + r.height() * 0.5f,
                new int[]{0x00FFFFFF, dark ? 0x40FFFFFF : 0x99FFFFFF, 0x00FFFFFF},
                new float[]{0.15f, 0.42f, 0.8f}, Shader.TileMode.CLAMP));
        c.drawRect(r.left, r.top, r.right, r.top + r.height() * 0.42f, P);
        P.setShader(null);
        c.restore();

        // rim light
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(U.dpf(1.1f));
        P.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                dark ? 0x59FFFFFF : 0xB3FFFFFF, dark ? 0x0DFFFFFF : 0x40FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, P);
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
    }

    /** Filled "active" version of a glass card, tinted with the accent. */
    public static void glassActive(Canvas c, Context ctx, RectF r, float rad,
                                   boolean dark, int accent) {
        P.reset();
        P.setAntiAlias(true);
        U.softShadow(c, r, rad, shadow(dark), U.dpf(6), U.dpf(3));
        P.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                U.boost(accent, 1f, 1.18f), U.boost(accent, 1.05f, 0.82f), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, P);
        P.setShader(null);
        P.setShader(new LinearGradient(r.left, r.top, r.right, r.top + r.height() * 0.6f,
                0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, P);
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(U.dpf(1.1f));
        P.setColor(0x73FFFFFF);
        c.drawRoundRect(r, rad, rad, P);
        P.setStyle(Paint.Style.FILL);
    }

    /** Pill used for labels on the home screen so text stays legible on light walls. */
    public static void labelShadow(Paint tp, boolean dark) {
        if (dark) tp.setShadowLayer(U.dpf(5), 0, U.dpf(1), 0xB3000000);
        else tp.setShadowLayer(U.dpf(5), 0, U.dpf(1), 0x66FFFFFF);
    }
}
