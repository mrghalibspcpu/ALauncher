package com.alauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** App Lock — a glass PIN pad shown before a protected app opens. */
public class LockView extends View {

    public interface OnUnlock { void run(boolean ok); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF[] keys = new RectF[12];
    private String entered = "";
    private String title = "Enter PIN";
    private OnUnlock cb;
    private boolean dark = true;
    private int accent = 0xFF6AE3FF;
    private boolean setMode;
    private String firstEntry;
    private float shake;

    public LockView(Context c) {
        super(c);
        setVisibility(GONE);
        setClickable(true);
    }

    public static boolean hasPin(Context c) {
        return Prefs.get(c).s("pin", "").length() >= 4;
    }

    public static boolean locked(Context c, AppItem a) {
        return hasPin(c) && Prefs.get(c).set(Prefs.LOCKED).contains(a.key());
    }

    private static String hash(String pin) {
        long h = 1125899906842597L;
        for (int i = 0; i < pin.length(); i++) h = 31 * h + pin.charAt(i);
        return Long.toHexString(h);
    }

    public void ask(String title, boolean setNew, int accent, OnUnlock cb) {
        this.title = title;
        this.setMode = setNew;
        this.accent = accent;
        this.cb = cb;
        this.entered = "";
        this.firstEntry = null;
        this.dark = Theme.dark(getContext());
        setVisibility(VISIBLE);
        setAlpha(0f);
        animate().alpha(1f).setDuration(160).start();
        invalidate();
    }

    public boolean showing() { return getVisibility() == VISIBLE; }

    public void dismiss() {
        animate().alpha(0f).setDuration(140).withEndAction(new Runnable() {
            public void run() { setVisibility(GONE); }
        }).start();
    }

    private void layoutKeys() {
        float w = Math.min(getWidth() * 0.72f, U.dpf(280));
        float kx = (getWidth() - w) / 2f;
        float ky = getHeight() * 0.42f;
        float cell = w / 3f;
        for (int i = 0; i < 12; i++) {
            float x = kx + (i % 3) * cell, y = ky + (i / 3) * cell;
            keys[i] = new RectF(x + U.dpf(7), y + U.dpf(7), x + cell - U.dpf(7), y + cell - U.dpf(7));
        }
    }

    @Override protected void onDraw(Canvas c) {
        if (keys[0] == null || getWidth() == 0) layoutKeys();
        Theme.sheet(c, getContext(), getWidth(), getHeight(), dark, accent);

        tp.reset(); tp.setAntiAlias(true);
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setTextSize(U.dpf(18));
        tp.setColor(Theme.text(dark));
        String t = setMode && firstEntry != null ? "Repeat the PIN" : title;
        c.drawText(t, getWidth() / 2f - tp.measureText(t) / 2f, getHeight() * 0.26f, tp);

        float dotY = getHeight() * 0.32f;
        for (int i = 0; i < 4; i++) {
            float x = getWidth() / 2f + (i - 1.5f) * U.dpf(26) + shake;
            p.reset(); p.setAntiAlias(true);
            if (i < entered.length()) {
                p.setColor(accent);
                c.drawCircle(x, dotY, U.dpf(7), p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(U.dpf(1.6f));
                p.setColor(Theme.textFaint(dark));
                c.drawCircle(x, dotY, U.dpf(7), p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"};
        for (int i = 0; i < 12; i++) {
            if (labels[i].isEmpty()) continue;
            Theme.glass(c, getContext(), keys[i], keys[i].width() / 2f, dark, accent, 0.7f);
            tp.setTextSize(U.dpf(i == 11 ? 18 : 22));
            tp.setColor(Theme.text(dark));
            c.drawText(labels[i], keys[i].centerX() - tp.measureText(labels[i]) / 2f,
                    keys[i].centerY() + U.dpf(8), tp);
        }
        tp.setTextSize(U.dpf(11.5f));
        tp.setColor(Theme.textDim(dark));
        String hint = "Tap outside to cancel";
        c.drawText(hint, getWidth() / 2f - tp.measureText(hint) / 2f, getHeight() - U.dpf(40), tp);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        if (keys[0] == null) layoutKeys();
        for (int i = 0; i < 12; i++) {
            if (keys[i] != null && keys[i].contains(e.getX(), e.getY())) {
                U.haptic(this, false);
                if (i == 9) return true;
                if (i == 11) {
                    if (entered.length() > 0) entered = entered.substring(0, entered.length() - 1);
                } else {
                    entered += (i == 10 ? "0" : String.valueOf(i + 1));
                    if (entered.length() == 4) check();
                }
                invalidate();
                return true;
            }
        }
        if (e.getY() < getHeight() * 0.22f || e.getY() > getHeight() - U.dpf(70)) {
            dismiss();
            if (cb != null) cb.run(false);
        }
        return true;
    }

    private void check() {
        Prefs prefs = Prefs.get(getContext());
        if (setMode) {
            if (firstEntry == null) {
                firstEntry = entered;
                entered = "";
                invalidate();
                return;
            }
            if (firstEntry.equals(entered)) {
                prefs.setS("pin", hash(entered));
                dismiss();
                if (cb != null) cb.run(true);
            } else {
                firstEntry = null;
                entered = "";
                fail();
            }
            return;
        }
        if (hash(entered).equals(prefs.s("pin", ""))) {
            dismiss();
            if (cb != null) cb.run(true);
        } else {
            entered = "";
            fail();
        }
    }

    private void fail() {
        U.haptic(this, true);
        U.anim(0, 1, 320, U.EASE, new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                shake = (float) (Math.sin(t * Math.PI * 6) * U.dpf(10) * (1 - t));
                invalidate();
            }
        });
    }
}
