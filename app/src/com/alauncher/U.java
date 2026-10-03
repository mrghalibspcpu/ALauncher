package com.alauncher;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.View;

/** Tiny utility belt: units, easing, blur, colour maths, haptics, shapes. */
public final class U {

    public static float density = 3f;

    public static void init(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        density = dm.density;
    }

    public static int dp(float v) { return Math.round(v * density); }
    public static float dpf(float v) { return v * density; }

    /** Material-ish emphasised easing. */
    public static final TimeInterpolator EASE = new TimeInterpolator() {
        public float getInterpolation(float t) {
            return (float) (1 - Math.pow(1 - t, 3));
        }
    };

    /** Overshoot spring used for panels and icon pops. */
    public static final TimeInterpolator SPRING = new TimeInterpolator() {
        public float getInterpolation(float t) {
            if (t >= 1f) return 1f;
            double p = 0.38;
            return (float) (1 - Math.pow(2, -11 * t) * Math.cos(2 * Math.PI * t / p));
        }
    };

    public static final TimeInterpolator DECEL = new TimeInterpolator() {
        public float getInterpolation(float t) { return (float) (1 - Math.pow(1 - t, 2.4)); }
    };

    public static ValueAnimator anim(float from, float to, long dur, TimeInterpolator i,
                                     final ValueAnimator.AnimatorUpdateListener l) {
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(dur);
        a.setInterpolator(i);
        a.addUpdateListener(l);
        a.start();
        return a;
    }

    public static float clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }
    public static int clampI(int v, int a, int b) { return v < a ? a : (v > b ? b : v); }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | (((int) (U.clamp(a, 0, 1) * 255)) << 24);
    }

    public static int mix(int c1, int c2, float t) {
        return Color.argb(
                (int) lerp(Color.alpha(c1), Color.alpha(c2), t),
                (int) lerp(Color.red(c1), Color.red(c2), t),
                (int) lerp(Color.green(c1), Color.green(c2), t),
                (int) lerp(Color.blue(c1), Color.blue(c2), t));
    }

    public static int boost(int color, float sat, float val) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = clamp(hsv[1] * sat, 0f, 1f);
        hsv[2] = clamp(hsv[2] * val, 0.08f, 1f);
        return Color.HSVToColor(hsv);
    }

    public static boolean isLight(int color) {
        return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) > 165;
    }

    public static void haptic(View v, boolean heavy) {
        try {
            Vibrator vb = (Vibrator) v.getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vb == null || !vb.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                vb.vibrate(VibrationEffect.createOneShot(heavy ? 18 : 9, heavy ? 120 : 60));
            } else {
                vb.vibrate(heavy ? 18 : 9);
            }
        } catch (Throwable ignored) { }
    }

    /** Super-ellipse (squircle) path — the signature A Launcher icon shape. */
    public static Path squircle(float l, float t, float r, float b, float n) {
        Path p = new Path();
        float cx = (l + r) / 2f, cy = (t + b) / 2f;
        float a = (r - l) / 2f, c = (b - t) / 2f;
        int steps = 72;
        for (int i = 0; i <= steps; i++) {
            double th = 2 * Math.PI * i / steps;
            double ct = Math.cos(th), st = Math.sin(th);
            float x = (float) (cx + a * Math.signum(ct) * Math.pow(Math.abs(ct), 2.0 / n));
            float y = (float) (cy + c * Math.signum(st) * Math.pow(Math.abs(st), 2.0 / n));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    public static Path shapePath(int shape, RectF r) {
        Path p = new Path();
        switch (shape) {
            case 0: // squircle
                return squircle(r.left, r.top, r.right, r.bottom, 4.2f);
            case 1: // circle
                p.addOval(r, Path.Direction.CW);
                return p;
            case 2: // rounded square
                p.addRoundRect(r, r.width() * 0.26f, r.height() * 0.26f, Path.Direction.CW);
                return p;
            case 3: // teardrop
                float rad = r.width() / 2f;
                p.addRoundRect(r, new float[]{rad, rad, rad, rad, rad, rad, rad * 0.25f, rad * 0.25f},
                        Path.Direction.CW);
                return p;
            case 4: // hexagon
                for (int i = 0; i < 6; i++) {
                    double th = Math.PI / 3 * i - Math.PI / 2;
                    float x = (float) (r.centerX() + r.width() / 2 * Math.cos(th));
                    float y = (float) (r.centerY() + r.height() / 2 * Math.sin(th));
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.close();
                return p;
            default:
                p.addRoundRect(r, r.width() * 0.32f, r.height() * 0.32f, Path.Direction.CW);
                return p;
        }
    }

    public static Bitmap drawableToBitmap(Drawable d, int size) {
        Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        return bm;
    }

    public static Bitmap scaled(Bitmap src, int w, int h) {
        if (src == null) return null;
        return Bitmap.createScaledBitmap(src, Math.max(1, w), Math.max(1, h), true);
    }

    public static Bitmap crop(Bitmap src, int x, int y, int w, int h) {
        x = clampI(x, 0, Math.max(0, src.getWidth() - 1));
        y = clampI(y, 0, Math.max(0, src.getHeight() - 1));
        w = clampI(w, 1, src.getWidth() - x);
        h = clampI(h, 1, src.getHeight() - y);
        Matrix m = new Matrix();
        return Bitmap.createBitmap(src, x, y, w, h, m, true);
    }

    /** Fast stack blur (pure Java, works on every API level). */
    public static Bitmap blur(Bitmap src, int radius) {
        if (src == null) return null;
        Bitmap bm = src.copy(Bitmap.Config.ARGB_8888, true);
        if (radius < 1) return bm;
        int w = bm.getWidth(), h = bm.getHeight();
        int[] pix = new int[w * h];
        bm.getPixels(pix, 0, w, 0, 0, w, h);
        int wm = w - 1, hm = h - 1, wh = w * h, div = radius + radius + 1;
        int[] r = new int[wh], g = new int[wh], b = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int[] vmin = new int[Math.max(w, h)];
        int divsum = (div + 1) >> 1; divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) dv[i] = i / divsum;
        yw = yi = 0;
        int[][] stack = new int[div][3];
        int stackpointer, stackstart, rbs, r1 = radius + 1, routsum, goutsum, boutsum, rinsum, ginsum, binsum;
        int[] sir;
        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16; sir[1] = (p & 0x00ff00) >> 8; sir[2] = p & 0x0000ff;
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs;
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; }
            }
            stackpointer = radius;
            for (x = 0; x < w; x++) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
                if (y == 0) vmin[x] = Math.min(x + radius + 1, wm);
                p = pix[yw + vmin[x]];
                sir[0] = (p & 0xff0000) >> 16; sir[1] = (p & 0x00ff00) >> 8; sir[2] = p & 0x0000ff;
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer % div];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;
                sir = stack[i + radius];
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi];
                rbs = r1 - Math.abs(i);
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs;
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; }
                if (i < hm) yp += w;
            }
            yi = x; stackpointer = radius;
            for (y = 0; y < h; y++) {
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
                if (x == 0) vmin[y] = Math.min(y + r1, hm) * w;
                p = x + vmin[y];
                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p];
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
                yi += w;
            }
        }
        bm.setPixels(pix, 0, w, 0, 0, w, h);
        return bm;
    }

    /** Dominant vivid colour of a bitmap — our own mini "palette". */
    public static int dominantColor(Bitmap bm) {
        if (bm == null) return 0xFF6AE3FF;
        Bitmap s = scaled(bm, 24, 24);
        int bestColor = 0xFF6AE3FF;
        float bestScore = -1;
        float[] hsv = new float[3];
        for (int y = 0; y < s.getHeight(); y++) {
            for (int x = 0; x < s.getWidth(); x++) {
                int p = s.getPixel(x, y);
                if (Color.alpha(p) < 160) continue;
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * 1.6f + hsv[2] * 0.6f;
                if (hsv[2] < 0.18f || hsv[1] < 0.12f) score *= 0.25f;
                if (score > bestScore) { bestScore = score; bestColor = p; }
            }
        }
        return bestColor;
    }

    public static String ellipsize(android.graphics.Paint p, String text, float maxWidth) {
        if (text == null) return "";
        if (p.measureText(text) <= maxWidth) return text;
        String e = "…";
        int n = text.length();
        while (n > 1 && p.measureText(text.substring(0, n) + e) > maxWidth) n--;
        return text.substring(0, Math.max(1, n)).trim() + e;
    }
}
