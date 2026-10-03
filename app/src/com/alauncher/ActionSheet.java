package com.alauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Floating glass menu used for long-press actions anywhere in the launcher. */
public class ActionSheet extends View {

    public interface OnAction { void run(String id); }

    public static class Row {
        public String id, label;
        public int glyph;          // index into Glyphs
        public boolean danger;
        public Row(String id, String label, int glyph, boolean danger) {
            this.id = id; this.label = label; this.glyph = glyph; this.danger = danger;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnAction cb;
    private float ax, ay;            // anchor
    private Bitmap header;           // optional app icon
    private String title;
    private float anim = 0f;
    private int pressed = -1;
    private float sheetX, sheetY, sheetW, sheetH;
    private int accent = 0xFF6AE3FF;

    public ActionSheet(Context c) {
        super(c);
        tp.setTextSize(U.dpf(14.5f));
        tp.setColor(0xFFFFFFFF);
        tp.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        setAlpha(0f);
    }

    public void show(float x, float y, String title, Bitmap icon, int accent, List<Row> list, OnAction cb) {
        this.ax = x; this.ay = y; this.title = title; this.header = icon; this.cb = cb;
        this.accent = accent;
        rows.clear(); rows.addAll(list);
        setVisibility(VISIBLE);
        animate().alpha(1f).setDuration(140).start();
        U.anim(0, 1, 320, U.SPRING, new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                anim = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        invalidate();
    }

    public boolean showing() { return getVisibility() == VISIBLE; }

    public void hide() {
        if (getVisibility() != VISIBLE) return;
        animate().alpha(0f).setDuration(130).withEndAction(new Runnable() {
            public void run() { setVisibility(GONE); }
        }).start();
    }

    @Override protected void onDraw(Canvas c) {
        if (rows.isEmpty()) return;
        float rowH = U.dpf(46);
        float padding = U.dpf(10);
        float headerH = title != null ? U.dpf(54) : 0;
        float w = U.dpf(226);
        float h = rows.size() * rowH + padding * 2 + headerH;

        float x = U.clamp(ax - w / 2f, U.dpf(12), getWidth() - w - U.dpf(12));
        boolean above = ay + h + U.dpf(16) > getHeight();
        float y = above ? ay - h - U.dpf(14) : ay + U.dpf(14);
        y = U.clamp(y, U.dpf(40), Math.max(U.dpf(40), getHeight() - h - U.dpf(24)));
        sheetX = x; sheetY = y; sheetW = w; sheetH = h;

        boolean dark = Theme.dark(getContext());
        // scrim
        p.setColor(U.alpha(Theme.scrim(dark), 0.75f * anim));
        c.drawRect(0, 0, getWidth(), getHeight(), p);

        c.save();
        float s = 0.86f + 0.14f * anim;
        c.scale(s, s, x + w / 2f, above ? y + h : y);

        RectF r = new RectF(x, y, x + w, y + h);
        U.softShadow(c, r, U.dpf(26), Theme.shadow(dark), U.dpf(10), U.dpf(5));

        p.setColor(Theme.sheetBg(dark));
        c.drawRoundRect(r, U.dpf(26), U.dpf(26), p);
        Theme.glass(c, getContext(), r, U.dpf(26), dark, accent, 0.9f);

        float cy = y + padding;
        if (title != null) {
            if (header != null) {
                RectF ir = new RectF(x + U.dpf(16), cy + U.dpf(6), x + U.dpf(16) + U.dpf(34), cy + U.dpf(40));
                c.save();
                c.clipPath(U.shapePath(Prefs.get(getContext()).iconShape(), ir));
                c.drawBitmap(header, null, ir, p);
                c.restore();
            }
            tp.setTextSize(U.dpf(15.5f));
            tp.setColor(Theme.text(dark));
            tp.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
            c.drawText(U.ellipsize(tp, title, w - U.dpf(70)), x + U.dpf(60), cy + U.dpf(29), tp);
            p.setColor(Theme.divider(dark));
            c.drawRect(x + U.dpf(14), cy + U.dpf(50), x + w - U.dpf(14), cy + U.dpf(50) + U.dpf(1), p);
            cy += headerH;
        }

        tp.setTextSize(U.dpf(14.5f));
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float top = cy + i * rowH;
            if (pressed == i) {
                p.setColor(dark ? 0x26FFFFFF : 0x1A000000);
                c.drawRoundRect(new RectF(x + U.dpf(8), top + U.dpf(2), x + w - U.dpf(8), top + rowH - U.dpf(2)),
                        U.dpf(16), U.dpf(16), p);
            }
            int col = row.danger ? 0xFFE0405A : Theme.text(dark);
            Glyphs.draw(c, row.glyph, x + U.dpf(30), top + rowH / 2f, U.dpf(9),
                    row.danger ? 0xFFE0405A : accent);
            tp.setColor(col);
            c.drawText(U.ellipsize(tp, row.label, w - U.dpf(70)), x + U.dpf(54),
                    top + rowH / 2f + U.dpf(5.2f), tp);
        }
        c.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float rowH = U.dpf(46);
        float headerH = title != null ? U.dpf(54) : 0;
        float listTop = sheetY + U.dpf(10) + headerH;
        int idx = -1;
        if (e.getX() > sheetX && e.getX() < sheetX + sheetW && e.getY() > listTop) {
            int i = (int) ((e.getY() - listTop) / rowH);
            if (i >= 0 && i < rows.size()) idx = i;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = idx; invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed != idx) { pressed = idx; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                if (idx >= 0 && cb != null) {
                    U.haptic(this, false);
                    cb.run(rows.get(idx).id);
                }
                pressed = -1;
                hide();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1; hide(); return true;
        }
        return true;
    }
}
