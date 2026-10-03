package com.alauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The app drawer: instant fuzzy search, A–Z sections, alphabet rail,
 * inline calculator and the ":" command palette.
 */
public class DrawerView extends FrameLayout {

    public interface Host {
        void openApp(AppItem a, View from, float x, float y);
        void appMenu(AppItem a, float x, float y);
        void closeDrawer();
        void runCommand(String cmd);
        int accent();
    }

    private final Host host;
    private final Grid grid;
    private final EditText search;
    private final AppStore store;
    private String query = "";

    public DrawerView(Context c, Host h) {
        super(c);
        host = h;
        store = AppStore.get(c);
        grid = new Grid(c);
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        search = new EditText(c);
        search.setHint("Search apps, maths, or type :");
        search.setHintTextColor(0x66FFFFFF);
        search.setTextColor(0xFFFFFFFF);
        search.setTextSize(15f);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(U.dp(46), U.dp(10), U.dp(16), U.dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1FFFFFFF);
        bg.setCornerRadius(U.dpf(24));
        bg.setStroke(U.dp(1), 0x1AFFFFFF);
        search.setBackground(bg);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, U.dp(48));
        lp.setMargins(U.dp(18), U.dp(46), U.dp(18), 0);
        lp.gravity = Gravity.TOP;
        addView(search, lp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString();
                grid.rebuild();
            }
            public void afterTextChanged(Editable s) { }
        });
        search.setOnEditorActionListener((v, actionId, ev) -> {
            if (query.startsWith(":")) { host.runCommand(query.substring(1).trim()); reset(); return true; }
            List<AppItem> r = store.search(query);
            if (!r.isEmpty()) { host.openApp(r.get(0), DrawerView.this, getWidth() / 2f, getHeight() / 2f); reset(); }
            return true;
        });
        setClickable(true);
    }

    public void reset() {
        search.setText("");
        query = "";
        grid.scrollYv = 0;
        grid.rebuild();
        hideKeyboard();
    }

    public void focusSearch() {
        search.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
        search.clearFocus();
    }

    public void refresh() { grid.rebuild(); }

    public void setSearchVisible(boolean v) { search.setVisibility(v ? VISIBLE : GONE); }

    // ------------------------------------------------------------------ grid
    class Grid extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final List<Object> rows = new ArrayList<>();     // String header | AppItem[] row
        final OverScroller scroller;
        float scrollYv;
        float downX, downY, lastY;
        boolean dragging;
        long downTime;
        AppItem pressed;
        float pressAmt;
        Runnable longPress;
        String mathResult;
        float railTouchY = -1;

        Grid(Context c) {
            super(c);
            scroller = new OverScroller(c);
            tp.setTypeface(Typeface.create("sans-serif-medium", 0));
            rebuild();
        }

        int cols() { return Prefs.get(getContext()).drawerCols(); }

        void rebuild() {
            rows.clear();
            mathResult = null;
            List<AppItem> list;
            if (query.length() > 0) {
                if (query.startsWith("=")) mathResult = Calc.eval(query.substring(1));
                else {
                    String r = Calc.eval(query);
                    if (r != null) mathResult = r;
                }
                if (query.startsWith(":")) {
                    list = new ArrayList<>();
                    rows.add("COMMANDS");
                    for (String cmd : Commands.suggest(query.substring(1))) rows.add("\u0001" + cmd);
                } else {
                    list = store.search(query);
                }
            } else {
                list = store.visible();
            }
            if (mathResult != null) rows.add(0, "\u0002" + mathResult);
            String section = null;
            List<AppItem> cur = new ArrayList<>();
            boolean sections = query.isEmpty();
            for (AppItem a : list) {
                if (sections) {
                    String s = sectionOf(a);
                    if (!s.equals(section)) {
                        if (!cur.isEmpty()) { rows.add(cur.toArray(new AppItem[0])); cur = new ArrayList<>(); }
                        section = s;
                        rows.add(s);
                    }
                }
                cur.add(a);
                if (cur.size() == cols()) { rows.add(cur.toArray(new AppItem[0])); cur = new ArrayList<>(); }
            }
            if (!cur.isEmpty()) rows.add(cur.toArray(new AppItem[0]));
            if (list.isEmpty() && query.length() > 0 && !query.startsWith(":")) rows.add("\u0003" + query);
            invalidate();
        }

        String sectionOf(AppItem a) {
            String t = a.title().trim();
            if (t.isEmpty()) return "#";
            char ch = Character.toUpperCase(t.charAt(0));
            return Character.isLetter(ch) ? String.valueOf(ch) : "#";
        }

        float rowH() {
            float iconSz = getWidth() / (float) cols() * 0.52f * (Prefs.get(getContext()).iconSize() / 100f);
            return iconSz + U.dpf(Prefs.get(getContext()).drawerLabels() ? 40 : 22);
        }

        float headerH() { return U.dpf(34); }
        float top() { return U.dpf(108); }

        float contentHeight() {
            float h = 0;
            for (Object o : rows) h += (o instanceof AppItem[]) ? rowH() : headerH();
            return h + U.dpf(40);
        }

        float maxScroll() {
            return Math.max(0, contentHeight() - (getHeight() - top()));
        }

        @Override protected void onDraw(Canvas c) {
            if (!scroller.isFinished()) {
                scroller.computeScrollOffset();
                scrollYv = scroller.getCurrY();
                postInvalidateOnAnimation();
            }
            scrollYv = U.clamp(scrollYv, 0, maxScroll());
            int accent = host.accent();

            // glass background
            p.setColor(0xF00A0C12);
            c.drawRect(0, 0, getWidth(), getHeight(), p);
            p.setShader(new android.graphics.LinearGradient(0, 0, getWidth(), getHeight(),
                    U.alpha(accent, 0.10f), 0x00000000, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, getWidth(), getHeight(), p);
            p.setShader(null);

            Glyphs.draw(c, Glyphs.SEARCH, U.dpf(42), U.dpf(70), U.dpf(9), U.alpha(accent, 0.8f));

            c.save();
            c.clipRect(0, top() - U.dpf(6), getWidth(), getHeight());
            c.translate(0, top() - scrollYv);
            float y = 0;
            float iconSz = getWidth() / (float) cols() * 0.52f * (Prefs.get(getContext()).iconSize() / 100f);
            for (Object o : rows) {
                if (o instanceof String) {
                    String s = (String) o;
                    if (s.startsWith("\u0002")) {
                        drawMath(c, y, s.substring(1), accent);
                    } else if (s.startsWith("\u0001")) {
                        drawCommand(c, y, s.substring(1), accent);
                    } else if (s.startsWith("\u0003")) {
                        drawWebRow(c, y, s.substring(1), accent);
                    } else {
                        tp.setTextSize(U.dpf(12.5f));
                        tp.setColor(U.alpha(accent, 0.85f));
                        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
                        c.drawText(s, U.dpf(22), y + U.dpf(24), tp);
                        p.setColor(0x14FFFFFF);
                        c.drawRect(U.dpf(22) + tp.measureText(s) + U.dpf(10), y + U.dpf(19),
                                getWidth() - U.dpf(44), y + U.dpf(20), p);
                    }
                    y += headerH();
                } else {
                    AppItem[] r = (AppItem[]) o;
                    float cw = (getWidth() - U.dpf(34)) / (float) cols();
                    for (int i = 0; i < r.length; i++) {
                        float cx = U.dpf(17) + cw * (i + 0.5f);
                        drawApp(c, r[i], cx, y, iconSz, accent);
                    }
                    y += rowH();
                }
            }
            c.restore();

            if (query.isEmpty()) drawRail(c, accent);
        }

        void drawApp(Canvas c, AppItem a, float cx, float y, float sz, int accent) {
            float scale = (pressed == a) ? 0.9f : 1f;
            RectF r = new RectF(cx - sz / 2 * scale, y + U.dpf(6), cx + sz / 2 * scale, y + U.dpf(6) + sz * scale);
            boolean blocked = Focus.blocked(getContext(), a);
            p.setAlpha(blocked ? 90 : 255);
            if (a.icon != null) c.drawBitmap(a.icon, null, r, p);
            p.setAlpha(255);
            if (Prefs.get(getContext()).badges()) {
                Integer b = AppStore.badges.get(a.pkg);
                if (b != null && b > 0) drawBadge(c, r.right - U.dpf(4), r.top + U.dpf(4), b, accent);
            }
            if (Prefs.get(getContext()).drawerLabels()) {
                tp.setTextSize(U.dpf(11.5f));
                tp.setTypeface(Typeface.create("sans-serif", 0));
                tp.setColor(blocked ? 0x66FFFFFF : 0xE6FFFFFF);
                String s = U.ellipsize(tp, a.title(), getWidth() / (float) cols() - U.dpf(8));
                c.drawText(s, cx - tp.measureText(s) / 2f, r.bottom + U.dpf(17), tp);
            }
        }

        void drawBadge(Canvas c, float x, float y, int n, int accent) {
            float r = U.dpf(9);
            p.setColor(0xFFFF4D6D);
            c.drawCircle(x, y, r, p);
            tp.setTextSize(U.dpf(10));
            tp.setColor(0xFFFFFFFF);
            tp.setTypeface(Typeface.create("sans-serif-medium", 0));
            String s = n > 9 ? "9+" : String.valueOf(n);
            c.drawText(s, x - tp.measureText(s) / 2f, y + U.dpf(3.5f), tp);
        }

        void drawMath(Canvas c, float y, String res, int accent) {
            RectF r = new RectF(U.dpf(18), y, getWidth() - U.dpf(18), y + headerH() - U.dpf(6));
            p.setColor(U.alpha(accent, 0.18f));
            c.drawRoundRect(r, U.dpf(14), U.dpf(14), p);
            Glyphs.draw(c, Glyphs.CALC, r.left + U.dpf(22), r.centerY(), U.dpf(8), accent);
            tp.setTextSize(U.dpf(14));
            tp.setColor(0xFFFFFFFF);
            c.drawText("= " + res, r.left + U.dpf(42), r.centerY() + U.dpf(5), tp);
        }

        void drawCommand(Canvas c, float y, String cmd, int accent) {
            RectF r = new RectF(U.dpf(18), y, getWidth() - U.dpf(18), y + headerH() - U.dpf(6));
            p.setColor(0x14FFFFFF);
            c.drawRoundRect(r, U.dpf(14), U.dpf(14), p);
            Glyphs.draw(c, Glyphs.BOLT, r.left + U.dpf(22), r.centerY(), U.dpf(8), accent);
            tp.setTextSize(U.dpf(13.5f));
            tp.setColor(0xFFE7EBF2);
            c.drawText(cmd, r.left + U.dpf(42), r.centerY() + U.dpf(5), tp);
        }

        void drawWebRow(Canvas c, float y, String q, int accent) {
            RectF r = new RectF(U.dpf(18), y, getWidth() - U.dpf(18), y + headerH() - U.dpf(6));
            p.setColor(0x14FFFFFF);
            c.drawRoundRect(r, U.dpf(14), U.dpf(14), p);
            Glyphs.draw(c, Glyphs.SEARCH, r.left + U.dpf(22), r.centerY(), U.dpf(8), accent);
            tp.setTextSize(U.dpf(13.5f));
            tp.setColor(0xFFE7EBF2);
            c.drawText("Search the web for \"" + q + "\"", r.left + U.dpf(42), r.centerY() + U.dpf(5), tp);
        }

        void drawRail(Canvas c, int accent) {
            List<String> letters = new ArrayList<>();
            for (Object o : rows) if (o instanceof String && ((String) o).length() == 1) letters.add((String) o);
            if (letters.isEmpty()) return;
            float h = getHeight() - top() - U.dpf(50);
            float step = Math.min(U.dpf(18), h / letters.size());
            float y0 = top() + U.dpf(10);
            tp.setTextSize(U.dpf(10.5f));
            tp.setTypeface(Typeface.create("sans-serif-medium", 0));
            for (int i = 0; i < letters.size(); i++) {
                float ly = y0 + step * (i + 0.5f);
                boolean near = railTouchY >= 0 && Math.abs(railTouchY - ly) < U.dpf(26);
                tp.setColor(near ? accent : 0x80FFFFFF);
                tp.setTextSize(near ? U.dpf(14) : U.dpf(10.5f));
                c.drawText(letters.get(i), getWidth() - U.dpf(18) - tp.measureText(letters.get(i)) / 2f,
                        ly + U.dpf(4), tp);
            }
        }

        // --------------------------------------------------------- touch
        AppItem hit(float x, float y) {
            float yy = y - top() + scrollYv;
            float cy = 0;
            float iconSz = getWidth() / (float) cols() * 0.52f;
            for (Object o : rows) {
                float h = (o instanceof AppItem[]) ? rowH() : headerH();
                if (yy >= cy && yy < cy + h && o instanceof AppItem[]) {
                    AppItem[] r = (AppItem[]) o;
                    float cw = (getWidth() - U.dpf(34)) / (float) cols();
                    int idx = (int) ((x - U.dpf(17)) / cw);
                    if (idx >= 0 && idx < r.length) return r[idx];
                }
                cy += h;
            }
            return null;
        }

        String hitRow(float x, float y) {
            float yy = y - top() + scrollYv;
            float cy = 0;
            for (Object o : rows) {
                float h = (o instanceof AppItem[]) ? rowH() : headerH();
                if (yy >= cy && yy < cy + h && o instanceof String) {
                    String s = (String) o;
                    if (s.startsWith("\u0001") || s.startsWith("\u0003")) return s;
                }
                cy += h;
            }
            return null;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            boolean onRail = query.isEmpty() && x > getWidth() - U.dpf(34);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scroller.forceFinished(true);
                    downX = x; downY = y; lastY = y; dragging = false;
                    downTime = System.currentTimeMillis();
                    if (onRail) { railTouchY = y; jumpToRail(y); invalidate(); return true; }
                    pressed = hit(x, y);
                    if (pressed != null) {
                        final AppItem a = pressed;
                        longPress = new Runnable() {
                            public void run() {
                                if (pressed == a && !dragging) {
                                    U.haptic(Grid.this, true);
                                    hideKeyboard();
                                    host.appMenu(a, downX, downY);
                                    pressed = null;
                                    invalidate();
                                }
                            }
                        };
                        postDelayed(longPress, 380);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (railTouchY >= 0) { railTouchY = y; jumpToRail(y); invalidate(); return true; }
                    if (!dragging && Math.abs(y - downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                        dragging = true;
                        pressed = null;
                        removeCallbacks(longPress);
                        hideKeyboard();
                    }
                    if (dragging) {
                        scrollYv = U.clamp(scrollYv - (y - lastY), 0, maxScroll());
                        lastY = y;
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    removeCallbacks(longPress);
                    if (railTouchY >= 0) { railTouchY = -1; invalidate(); return true; }
                    if (!dragging) {
                        AppItem a = hit(x, y);
                        if (a != null) {
                            if (Focus.blocked(getContext(), a)) {
                                U.haptic(this, true);
                            } else {
                                hideKeyboard();
                                host.openApp(a, DrawerView.this, x, y);
                            }
                        } else {
                            String row = hitRow(x, y);
                            if (row != null) {
                                if (row.startsWith("\u0001")) host.runCommand(row.substring(1).split(" ")[0]);
                                else host.runCommand("web " + row.substring(1));
                            } else if (y < top() - U.dpf(60)) {
                                host.closeDrawer();
                            }
                        }
                    } else {
                        // fling
                        android.view.VelocityTracker vt = null;
                        scroller.fling(0, (int) scrollYv, 0, (int) -velocity(e), 0, 0, 0, (int) maxScroll());
                        postInvalidateOnAnimation();
                    }
                    pressed = null;
                    dragging = false;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(longPress);
                    pressed = null; dragging = false; railTouchY = -1;
                    invalidate();
                    return true;
            }
            return true;
        }

        private float lastVelocity;
        private float velocity(MotionEvent e) {
            long dt = Math.max(1, System.currentTimeMillis() - downTime);
            return (e.getY() - downY) / dt * 1000f;
        }

        void jumpToRail(float y) {
            List<Integer> idx = new ArrayList<>();
            List<Float> offsets = new ArrayList<>();
            float cy = 0;
            for (Object o : rows) {
                if (o instanceof String && ((String) o).length() == 1) { idx.add(1); offsets.add(cy); }
                cy += (o instanceof AppItem[]) ? rowH() : headerH();
            }
            if (offsets.isEmpty()) return;
            float h = getHeight() - top() - U.dpf(50);
            float step = Math.min(U.dpf(18), h / offsets.size());
            int i = U.clampI((int) ((y - top() - U.dpf(10)) / step), 0, offsets.size() - 1);
            scrollYv = U.clamp(offsets.get(i), 0, maxScroll());
        }
    }
}
