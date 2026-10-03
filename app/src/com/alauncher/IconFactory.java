package com.alauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * Normalises every app icon into one consistent A Launcher shape:
 * adaptive icons get their real layers, legacy icons get an auto generated
 * gradient plate derived from the icon's own dominant colour.
 */
public class IconFactory {

    public static final int SIZE = 192;

    public static Bitmap make(Context ctx, Drawable d, int shape) {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        RectF r = new RectF(0, 0, SIZE, SIZE);
        Path mask = U.shapePath(shape, r);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (Build.VERSION.SDK_INT >= 26 && d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
            // adaptive icons are designed with a 18% bleed on every side
            int over = (int) (SIZE * 0.25f);
            Bitmap layer = Bitmap.createBitmap(SIZE + over * 2, SIZE + over * 2, Bitmap.Config.ARGB_8888);
            Canvas lc = new Canvas(layer);
            if (ad.getBackground() != null) {
                ad.getBackground().setBounds(0, 0, layer.getWidth(), layer.getHeight());
                ad.getBackground().draw(lc);
            }
            if (ad.getForeground() != null) {
                ad.getForeground().setBounds(0, 0, layer.getWidth(), layer.getHeight());
                ad.getForeground().draw(lc);
            }
            Bitmap cropped = Bitmap.createBitmap(layer, over, over, SIZE, SIZE);
            c.save();
            c.clipPath(mask);
            c.drawBitmap(cropped, 0, 0, p);
            c.restore();
            layer.recycle();
        } else {
            Bitmap src = toBitmap(d);
            int dom = U.dominantColor(src);
            // plate
            int top = U.boost(dom, 0.85f, 1.25f);
            int bot = U.boost(dom, 1.05f, 0.62f);
            Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
            plate.setShader(new android.graphics.LinearGradient(0, 0, SIZE, SIZE, top, bot,
                    android.graphics.Shader.TileMode.CLAMP));
            c.drawPath(mask, plate);
            // subtle inner highlight
            Paint hi = new Paint(Paint.ANTI_ALIAS_FLAG);
            hi.setStyle(Paint.Style.STROKE);
            hi.setStrokeWidth(SIZE * 0.025f);
            hi.setColor(0x33FFFFFF);
            c.drawPath(mask, hi);
            // icon art, trimmed and centred
            float inset = SIZE * 0.19f;
            RectF dst = new RectF(inset, inset, SIZE - inset, SIZE - inset);
            if (src != null) c.drawBitmap(src, null, dst, p);
        }
        return out;
    }

    /** Monochrome "stencil" variant used by the themed-icon mode. */
    public static Bitmap mono(Bitmap icon, int tint, int shape) {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        RectF r = new RectF(0, 0, SIZE, SIZE);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0x33FFFFFF);
        c.drawPath(U.shapePath(shape, r), bg);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        float inset = SIZE * 0.12f;
        c.save();
        c.clipPath(U.shapePath(shape, r));
        c.drawBitmap(icon, null, new RectF(inset, inset, SIZE - inset, SIZE - inset), p);
        c.restore();

        Paint tintP = new Paint(Paint.ANTI_ALIAS_FLAG);
        tintP.setColorFilter(new android.graphics.PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_ATOP));
        tintP.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        c.drawColor(U.alpha(tint, 0.55f), PorterDuff.Mode.SRC_ATOP);
        return out;
    }

    /** Icon pack art is already styled — just normalise its size. */
    public static Bitmap fromPack(Drawable d) {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        d.setBounds(0, 0, SIZE, SIZE);
        d.draw(c);
        return out;
    }

    public static Bitmap toBitmap(Drawable d) {
        if (d == null) return null;
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            return ((BitmapDrawable) d).getBitmap();
        }
        int w = Math.max(1, d.getIntrinsicWidth());
        int h = Math.max(1, d.getIntrinsicHeight());
        Bitmap bm = Bitmap.createBitmap(Math.min(w, 256), Math.min(h, 256), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        d.setBounds(0, 0, bm.getWidth(), bm.getHeight());
        d.draw(c);
        return bm;
    }

    /** Soft drop shadow bitmap for icons, drawn once and reused. */
    public static Bitmap shadow(int shape, int size, int color, float radius) {
        Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setMaskFilter(new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL));
        float pad = radius * 1.6f;
        c.drawPath(U.shapePath(shape, new RectF(pad, pad, size - pad, size - pad)), p);
        return bm;
    }

    public static int badgeTextColor(int bg) {
        return U.isLight(bg) ? Color.BLACK : Color.WHITE;
    }
}
