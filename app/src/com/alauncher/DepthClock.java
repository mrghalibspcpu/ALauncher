package com.alauncher;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The Depth Clock — the time is built from several parallaxed layers
 * (glow, extrusion, face, rim light, specular) that separate as you tilt the
 * phone, and home icons sit in front of it, so the clock lives *inside* the
 * wallpaper instead of on top of it.
 */
public class DepthClock {

    public static final int DEPTH = 0, STACKED = 1, MINIMAL = 2, ANALOG = 3;
    public static final String[] NAMES = {"Depth", "Stacked", "Minimal", "Analog glass"};

    private static final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** @return the height consumed, so the grid can lay out under it. */
    public static float draw(Canvas c, Context ctx, float left, float top, float width,
                             boolean dark, int accent, float tiltX, float tiltY,
                             int style, float sizeScale) {
        Date now = new Date();
        switch (style) {
            case STACKED: return stacked(c, now, left, top, width, dark, accent, tiltX, tiltY, sizeScale);
            case MINIMAL: return minimal(c, now, left, top, width, dark, accent, sizeScale);
            case ANALOG:  return analog(c, now, left, top, width, dark, accent, tiltX, tiltY, sizeScale);
            default:      return depth(c, now, left, top, width, dark, accent, tiltX, tiltY, sizeScale);
        }
    }

    private static Typeface face(boolean thin) {
        return Typeface.create(thin ? "sans-serif-thin" : "sans-serif-medium", Typeface.NORMAL);
    }

    // --------------------------------------------------------------- depth
    private static float depth(Canvas c, Date now, float left, float top, float width,
                               boolean dark, int accent, float tiltX, float tiltY, float scale) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
        float size = U.dpf(86) * scale;
        p.reset(); p.setAntiAlias(true);
        p.setTypeface(face(true));
        p.setTextSize(size);
        float tw = p.measureText(time);
        float x = left;
        float y = top + size * 0.82f;
        float px = tiltX * U.dpf(7), py = tiltY * U.dpf(5);

        // 1. far glow layer
        U.textGlow(c, time, x - px * 1.8f, y - py * 1.8f, p, accent, U.dpf(16));

        // 2. extrusion — stacked copies receding away from the light
        int steps = 9;
        for (int i = steps; i >= 1; i--) {
            float t = i / (float) steps;
            p.setColor(U.alpha(dark ? 0xFF000000 : 0xFF2A3545, 0.10f + 0.05f * (1 - t)));
            c.drawText(time, x - px * t * 1.1f + t * U.dpf(3), y - py * t * 1.1f + t * U.dpf(4), p);
        }

        // 3. face
        p.setShader(new LinearGradient(x, y - size * 0.8f, x + tw, y,
                dark ? 0xFFFFFFFF : 0xFF11161F,
                dark ? U.boost(accent, 0.75f, 1.1f) : 0xFF39465A, Shader.TileMode.CLAMP));
        c.drawText(time, x, y, p);
        p.setShader(null);

        // 4. rim light + specular sweep across the upper half
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(U.dpf(1.1f));
        p.setColor(dark ? 0x80FFFFFF : 0xB3FFFFFF);
        c.drawText(time, x + px * 0.25f, y + py * 0.25f, p);
        p.setStyle(Paint.Style.FILL);

        c.save();
        c.clipRect(x - U.dpf(10), y - size * 0.82f, x + tw + U.dpf(10), y - size * 0.32f);
        p.setShader(new LinearGradient(x, y - size * 0.8f, x + tw * 0.8f, y - size * 0.3f,
                new int[]{0x00FFFFFF, dark ? 0x59FFFFFF : 0x8CFFFFFF, 0x00FFFFFF},
                new float[]{0.2f, 0.5f, 0.85f}, Shader.TileMode.CLAMP));
        c.drawText(time, x + px * 0.4f, y, p);
        p.setShader(null);
        c.restore();

        // date capsule, nearer to the viewer
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now);
        p.setTypeface(face(false));
        p.setTextSize(U.dpf(12.5f) * Math.min(1.3f, scale));
        float dw = p.measureText(date);
        RectF cap = new RectF(x - px * 0.6f, y + U.dpf(6) - py * 0.6f,
                x + dw + U.dpf(26) - px * 0.6f, y + U.dpf(34) - py * 0.6f);
        p.setColor(U.alpha(dark ? 0xFF000000 : 0xFFFFFFFF, 0.3f));
        c.drawRoundRect(cap, cap.height() / 2f, cap.height() / 2f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(U.dpf(1));
        p.setColor(dark ? 0x33FFFFFF : 0x66FFFFFF);
        c.drawRoundRect(cap, cap.height() / 2f, cap.height() / 2f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.text(dark));
        c.drawText(date, cap.left + U.dpf(13), cap.centerY() + U.dpf(4.4f), p);

        return (y + U.dpf(40)) - top;
    }

    // ------------------------------------------------------------- stacked
    private static float stacked(Canvas c, Date now, float left, float top, float width,
                                 boolean dark, int accent, float tiltX, float tiltY, float scale) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        String hh = String.format(Locale.US, "%02d", cal.get(Calendar.HOUR_OF_DAY));
        String mm = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE));
        float size = U.dpf(92) * scale;
        p.reset(); p.setAntiAlias(true);
        p.setTypeface(face(true));
        p.setTextSize(size);
        float y1 = top + size * 0.78f;
        float y2 = y1 + size * 0.78f;
        float px = tiltX * U.dpf(8);

        U.textGlow(c, hh, left - px, y1, p, accent, U.dpf(14));
        U.textGlow(c, mm, left + U.dpf(22) + px, y2, p, accent, U.dpf(14));
        p.setStyle(Paint.Style.FILL);

        p.setShader(new LinearGradient(left, y1 - size, left + width, y2,
                dark ? 0xFFFFFFFF : 0xFF11161F, dark ? U.boost(accent, 0.8f, 1.1f) : 0xFF49566C,
                Shader.TileMode.CLAMP));
        c.drawText(hh, left - px * 0.4f, y1, p);
        c.drawText(mm, left + U.dpf(22) + px * 0.4f, y2, p);
        p.setShader(null);

        p.setTypeface(face(false));
        p.setTextSize(U.dpf(12.5f));
        p.setColor(Theme.textDim(dark));
        c.drawText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
                left + U.dpf(4), y2 + U.dpf(24), p);
        return (y2 + U.dpf(32)) - top;
    }

    // ------------------------------------------------------------- minimal
    private static float minimal(Canvas c, Date now, float left, float top, float width,
                                 boolean dark, int accent, float scale) {
        p.reset(); p.setAntiAlias(true);
        p.setTypeface(face(true));
        p.setTextSize(U.dpf(48) * scale);
        p.setColor(Theme.text(dark));
        Theme.labelShadow(p, dark);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
        c.drawText(time, left, top + U.dpf(48) * scale, p);
        p.clearShadowLayer();
        p.setTypeface(face(false));
        p.setTextSize(U.dpf(12.5f));
        p.setColor(Theme.textDim(dark));
        c.drawText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
                left + U.dpf(2), top + U.dpf(48) * scale + U.dpf(20), p);
        return U.dpf(48) * scale + U.dpf(30);
    }

    // -------------------------------------------------------------- analog
    private static float analog(Canvas c, Date now, float left, float top, float width,
                                boolean dark, int accent, float tiltX, float tiltY, float scale) {
        float r = U.dpf(62) * scale;
        float cx = left + r + U.dpf(6), cy = top + r + U.dpf(6);
        RectF box = new RectF(cx - r, cy - r, cx + r, cy + r);
        p.reset(); p.setAntiAlias(true);

        for (int i = 4; i >= 1; i--) {
            p.setColor(U.alpha(accent, 0.07f * (5 - i)));
            c.drawCircle(cx - tiltX * U.dpf(6), cy - tiltY * U.dpf(6), r + U.dpf(3) * i, p);
        }

        p.setColor(dark ? 0x33FFFFFF : 0x4DFFFFFF);
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(U.dpf(1.2f));
        p.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom,
                dark ? 0x80FFFFFF : 0xCCFFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);

        for (int i = 0; i < 12; i++) {
            double a = Math.PI / 6 * i;
            float r1 = r * (i % 3 == 0 ? 0.78f : 0.86f);
            p.setStrokeWidth(i % 3 == 0 ? U.dpf(2.4f) : U.dpf(1.2f));
            p.setColor(i % 3 == 0 ? Theme.text(dark) : Theme.textFaint(dark));
            c.drawLine((float) (cx + Math.sin(a) * r1), (float) (cy - Math.cos(a) * r1),
                    (float) (cx + Math.sin(a) * r * 0.93f), (float) (cy - Math.cos(a) * r * 0.93f), p);
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        float hA = (float) ((cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f) * Math.PI / 6);
        float mA = (float) (cal.get(Calendar.MINUTE) * Math.PI / 30);
        float sA = (float) (cal.get(Calendar.SECOND) * Math.PI / 30);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(U.dpf(4.5f));
        p.setColor(Theme.text(dark));
        c.drawLine(cx, cy, (float) (cx + Math.sin(hA) * r * 0.5f), (float) (cy - Math.cos(hA) * r * 0.5f), p);
        p.setStrokeWidth(U.dpf(3f));
        c.drawLine(cx, cy, (float) (cx + Math.sin(mA) * r * 0.72f), (float) (cy - Math.cos(mA) * r * 0.72f), p);
        p.setStrokeWidth(U.dpf(1.6f));
        p.setColor(accent);
        c.drawLine(cx, cy, (float) (cx + Math.sin(sA) * r * 0.8f), (float) (cy - Math.cos(sA) * r * 0.8f), p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, U.dpf(3.5f), p);

        p.setTypeface(face(false));
        p.setTextSize(U.dpf(12.5f));
        p.setColor(Theme.textDim(dark));
        c.drawText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
                cx + r + U.dpf(16), cy + U.dpf(4), p);
        return r * 2 + U.dpf(20);
    }
}
