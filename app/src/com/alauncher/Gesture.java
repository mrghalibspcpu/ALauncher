package com.alauncher;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Glyph Launch — draw a letter anywhere on the home screen and A Launcher
 * opens the app you mean. Lightweight $1-style unistroke recogniser.
 */
public class Gesture {

    private static final int N = 32;
    private static final Map<String, List<PointF>> TEMPLATES = new HashMap<>();

    static {
        add("C", poly(arc(0.5f, 0.5f, 0.42f, 50, 310)));
        add("O", poly(arc(0.5f, 0.5f, 0.42f, 0, 360)));
        add("L", poly(pts(0.22f, 0.08f, 0.22f, 0.9f, 0.86f, 0.9f)));
        add("V", poly(pts(0.14f, 0.08f, 0.5f, 0.92f, 0.86f, 0.08f)));
        add("M", poly(pts(0.1f, 0.92f, 0.26f, 0.08f, 0.5f, 0.62f, 0.74f, 0.08f, 0.9f, 0.92f)));
        add("N", poly(pts(0.14f, 0.92f, 0.14f, 0.08f, 0.86f, 0.92f, 0.86f, 0.08f)));
        add("W", poly(pts(0.1f, 0.08f, 0.3f, 0.92f, 0.5f, 0.3f, 0.7f, 0.92f, 0.9f, 0.08f)));
        add("Z", poly(pts(0.14f, 0.12f, 0.86f, 0.12f, 0.14f, 0.88f, 0.86f, 0.88f)));
        add("U", poly(pts(0.15f, 0.08f, 0.15f, 0.62f, 0.3f, 0.9f, 0.7f, 0.9f, 0.85f, 0.62f, 0.85f, 0.08f)));
        add("S", poly(pts(0.82f, 0.18f, 0.4f, 0.1f, 0.2f, 0.3f, 0.5f, 0.5f, 0.8f, 0.68f, 0.6f, 0.9f, 0.18f, 0.84f)));
        add("J", poly(pts(0.75f, 0.08f, 0.75f, 0.7f, 0.6f, 0.9f, 0.35f, 0.88f, 0.25f, 0.72f)));
        add("G", poly(pts(0.85f, 0.22f, 0.5f, 0.08f, 0.18f, 0.35f, 0.3f, 0.78f, 0.7f, 0.86f, 0.85f, 0.6f, 0.55f, 0.58f)));
        add("T", poly(pts(0.12f, 0.12f, 0.88f, 0.12f, 0.5f, 0.12f, 0.5f, 0.92f)));
        add("P", poly(pts(0.22f, 0.92f, 0.22f, 0.08f, 0.72f, 0.16f, 0.72f, 0.42f, 0.24f, 0.5f)));
    }

    private static float[] pts(float... v) { return v; }

    private static float[] arc(float cx, float cy, float r, float from, float to) {
        int steps = 24;
        float[] out = new float[steps * 2];
        for (int i = 0; i < steps; i++) {
            double a = Math.toRadians(from + (to - from) * i / (float) (steps - 1));
            out[i * 2] = (float) (cx + r * Math.cos(a));
            out[i * 2 + 1] = (float) (cy + r * Math.sin(a));
        }
        return out;
    }

    private static List<PointF> poly(float[] v) {
        List<PointF> l = new ArrayList<>();
        for (int i = 0; i < v.length; i += 2) l.add(new PointF(v[i], v[i + 1]));
        return normalize(resample(l));
    }

    private static void add(String k, List<PointF> v) { TEMPLATES.put(k, v); }

    public static String recognize(List<PointF> raw) {
        if (raw == null || raw.size() < 8) return null;
        float len = pathLength(raw);
        if (len < 60) return null;
        List<PointF> c = normalize(resample(raw));
        String best = null;
        float bestD = Float.MAX_VALUE;
        for (Map.Entry<String, List<PointF>> e : TEMPLATES.entrySet()) {
            float d = distance(c, e.getValue());
            if (d < bestD) { bestD = d; best = e.getKey(); }
        }
        return bestD < 0.33f ? best : null;
    }

    private static float distance(List<PointF> a, List<PointF> b) {
        float s = 0;
        for (int i = 0; i < N; i++) s += Math.hypot(a.get(i).x - b.get(i).x, a.get(i).y - b.get(i).y);
        return s / N;
    }

    private static float pathLength(List<PointF> p) {
        float s = 0;
        for (int i = 1; i < p.size(); i++) s += Math.hypot(p.get(i).x - p.get(i - 1).x, p.get(i).y - p.get(i - 1).y);
        return s;
    }

    private static List<PointF> resample(List<PointF> points) {
        List<PointF> src = new ArrayList<>();
        for (PointF p : points) src.add(new PointF(p.x, p.y));
        float I = pathLength(src) / (N - 1);
        if (I <= 0) I = 0.0001f;
        float D = 0;
        List<PointF> out = new ArrayList<>();
        out.add(new PointF(src.get(0).x, src.get(0).y));
        for (int i = 1; i < src.size(); i++) {
            PointF p0 = src.get(i - 1), p1 = src.get(i);
            float d = (float) Math.hypot(p1.x - p0.x, p1.y - p0.y);
            if (D + d >= I) {
                float t = (I - D) / d;
                PointF q = new PointF(p0.x + t * (p1.x - p0.x), p0.y + t * (p1.y - p0.y));
                out.add(q);
                src.add(i, q);
                D = 0;
            } else D += d;
        }
        while (out.size() < N) out.add(new PointF(src.get(src.size() - 1).x, src.get(src.size() - 1).y));
        while (out.size() > N) out.remove(out.size() - 1);
        return out;
    }

    private static List<PointF> normalize(List<PointF> p) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (PointF q : p) {
            minX = Math.min(minX, q.x); maxX = Math.max(maxX, q.x);
            minY = Math.min(minY, q.y); maxY = Math.max(maxY, q.y);
        }
        float w = Math.max(0.0001f, maxX - minX), h = Math.max(0.0001f, maxY - minY);
        float s = Math.max(w, h);
        List<PointF> out = new ArrayList<>();
        for (PointF q : p) out.add(new PointF((q.x - minX) / s, (q.y - minY) / s));
        // centre
        float cx = 0, cy = 0;
        for (PointF q : out) { cx += q.x; cy += q.y; }
        cx /= out.size(); cy /= out.size();
        for (PointF q : out) { q.x += 0.5f - cx; q.y += 0.5f - cy; }
        return out;
    }
}
