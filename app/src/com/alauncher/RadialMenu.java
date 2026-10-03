package com.alauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Orbit — a thumb-sized radial launcher that opens wherever you hold. */
public class RadialMenu extends View {

    public interface Host {
        void openApp(AppItem a, View from, float x, float y);
        int accent();
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<AppItem> items = new ArrayList<>();
    private Host host;
    private float cx, cy;
    private int selected = -1;
    private float anim;

    public RadialMenu(Context c, Host h) {
        super(c);
        host = h;
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        setVisibility(GONE);
    }

    public void show(float x, float y, List<AppItem> apps) {
        items.clear();
        items.addAll(apps);
        if (items.isEmpty()) return;
        cx = U.clamp(x, U.dpf(120), getWidth() - U.dpf(120));
        cy = U.clamp(y, U.dpf(150), getHeight() - U.dpf(120));
        selected = -1;
        setVisibility(VISIBLE);
        U.anim(0, 1, 260, U.SPRING, new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { anim = (Float) a.getAnimatedValue(); invalidate(); }
        });
        U.haptic(this, true);
    }

    public boolean showing() { return getVisibility() == VISIBLE; }

    public void hide() {
        setVisibility(GONE);
        selected = -1;
        invalidate();
    }

    private float radius() { return U.dpf(108) * U.lerp(0.75f, 1f, anim); }

    @Override protected void onDraw(Canvas c) {
        if (items.isEmpty()) return;
        int accent = host.accent();
        p.setColor(U.alpha(0xFF000000, 0.5f * anim));
        c.drawRect(0, 0, getWidth(), getHeight(), p);

        float r = radius();
        p.setColor(U.alpha(0xFF11141C, 0.92f * anim));
        c.drawCircle(cx, cy, r + U.dpf(34), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(U.dpf(1.2f));
        p.setColor(U.alpha(accent, 0.35f));
        c.drawCircle(cx, cy, r + U.dpf(34), p);
        p.setStyle(Paint.Style.FILL);

        int n = items.size();
        float iconSz = U.dpf(46);
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / n;
            float x = (float) (cx + Math.cos(a) * r);
            float y = (float) (cy + Math.sin(a) * r);
            float s = (selected == i ? 1.22f : 1f) * anim;
            RectF rect = new RectF(x - iconSz / 2 * s, y - iconSz / 2 * s, x + iconSz / 2 * s, y + iconSz / 2 * s);
            if (selected == i) {
                p.setColor(U.alpha(accent, 0.3f));
                c.drawCircle(x, y, iconSz * 0.78f, p);
            }
            AppItem it = items.get(i);
            if (it.icon != null) c.drawBitmap(it.icon, null, rect, p);
        }
        // centre label
        tp.setTextSize(U.dpf(12.5f));
        tp.setColor(0xFFFFFFFF);
        String label = selected >= 0 ? items.get(selected).title() : "Orbit";
        label = U.ellipsize(tp, label, r * 1.5f);
        c.drawText(label, cx - tp.measureText(label) / 2f, cy + U.dpf(4), tp);
        tp.setTextSize(U.dpf(9.5f));
        tp.setColor(U.alpha(accent, 0.85f));
        String hint = "tap or slide";
        if (selected < 0) c.drawText(hint, cx - tp.measureText(hint) / 2f, cy + U.dpf(20), tp);
    }

    public void onFingerMove(float x, float y) {
        if (items.isEmpty()) return;
        float dx = x - cx, dy = y - cy;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < U.dpf(34)) { if (selected != -1) { selected = -1; invalidate(); } return; }
        double ang = Math.atan2(dy, dx) + Math.PI / 2;
        if (ang < 0) ang += 2 * Math.PI;
        int idx = (int) Math.round(ang / (2 * Math.PI / items.size())) % items.size();
        if (idx != selected) { selected = idx; U.haptic(this, false); invalidate(); }
    }

    public void onFingerUp() {
        if (selected >= 0 && selected < items.size()) {
            AppItem a = items.get(selected);
            hide();
            host.openApp(a, this, cx, cy);
        } else hide();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_DOWN:
                onFingerMove(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_UP:
                onFingerUp();
                return true;
            case MotionEvent.ACTION_CANCEL:
                hide();
                return true;
        }
        return true;
    }
}
