package com.alauncher;

import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The home screen: paged cell grid, folders, widgets, Flow Dock,
 * ambient particle layer, drag &amp; drop and the glyph canvas.
 */
public class HomeView extends ViewGroup {

    public interface Host {
        void openDrawer(float velocity);
        void openControlCenter();
        void expandNotifications();
        void openApp(AppItem a, View from, float x, float y);
        void appMenu(AppItem a, float x, float y, Item homeItem);
        void homeMenu(float x, float y);
        void showOrbit(float x, float y);
        void lockScreen();
        int accent();
        void onAccentSuggestion(int color);
        AppWidgetHostView createWidgetView(int widgetId);
        void removeWidget(int widgetId);
        void toast(String msg);
    }

    // ------------------------------------------------------------- model
    public static class Item {
        public static final int APP = 0, FOLDER = 1, WIDGET = 2;
        public int type;
        public String key;
        public String name = "Folder";
        public List<String> keys = new ArrayList<>();
        public int page, cx, cy, sx = 1, sy = 1;
        public int widgetId = -1;
        public AppWidgetHostView widgetView;
        public float animScale = 1f;
        public transient AppItem cached;

        public AppItem app(Context c) {
            if (cached == null && key != null) cached = AppStore.get(c).find(key);
            return cached;
        }
    }

    private final Prefs prefs;
    private final AppStore store;
    private final Host host;

    public final List<Item> items = new ArrayList<>();
    public final List<Item> dock = new ArrayList<>();
    public int pageCount = 2;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OverScroller scroller;

    // layout metrics
    private float gridTop, gridBottom, cellW, cellH, sidePad;
    private float dockTop, dockH;

    // paging
    public int page = 0;
    private float scrollXf;

    // touch
    private float downX, downY, lastX, lastY;
    private long downTime;
    private boolean horizontal, vertical, glyphMode, movedOut;
    private Item touchItem;
    private Item dragItem;
    private float dragX, dragY, dragLift;
    private Item folderOpen;
    private float folderAnim;
    private Item dropTarget;
    private int dropAction = -1;     // 0 remove, 1 hide, 2 uninstall, 3 info
    private final List<PointF> glyph = new ArrayList<>();
    private long lastTapTime;
    private Runnable longPressRun;
    private boolean suppressTap;
    private long pageFlipTime;

    // ambient
    private final float[] px = new float[34], py = new float[34], pr = new float[34], pv = new float[34];
    private float tiltX, tiltY;
    private final Random rnd = new Random(7);

    public HomeView(Context c, Host h) {
        super(c);
        host = h;
        prefs = Prefs.get(c);
        store = AppStore.get(c);
        scroller = new OverScroller(c);
        setWillNotDraw(false);
        setClipChildren(false);
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        for (int i = 0; i < px.length; i++) {
            px[i] = rnd.nextFloat(); py[i] = rnd.nextFloat();
            pr[i] = 0.6f + rnd.nextFloat() * 2.2f;
            pv[i] = 0.00004f + rnd.nextFloat() * 0.00016f;
        }
    }

    // --------------------------------------------------------- persistence
    public void load() {
        removeAllViews();
        items.clear(); dock.clear();
        String s = prefs.s("layout", null);
        if (s == null) { buildDefault(); save(); return; }
        try {
            JSONObject o = new JSONObject(s);
            pageCount = Math.max(1, o.optInt("pages", 2));
            JSONArray arr = o.optJSONArray("items");
            if (arr != null) for (int i = 0; i < arr.length(); i++) items.add(fromJson(arr.getJSONObject(i)));
            JSONArray d = o.optJSONArray("dock");
            if (d != null) for (int i = 0; i < d.length(); i++) dock.add(fromJson(d.getJSONObject(i)));
        } catch (Throwable t) {
            items.clear(); dock.clear(); buildDefault();
        }
        pruneMissing();
        for (Item it : items) if (it.type == Item.WIDGET && it.widgetId >= 0) attachWidget(it);
        requestLayout();
        invalidate();
    }

    private Item fromJson(JSONObject o) {
        Item it = new Item();
        it.type = o.optInt("t");
        it.key = o.optString("k", null);
        it.name = o.optString("n", "Folder");
        it.page = o.optInt("p");
        it.cx = o.optInt("x");
        it.cy = o.optInt("y");
        it.sx = Math.max(1, o.optInt("sx", 1));
        it.sy = Math.max(1, o.optInt("sy", 1));
        it.widgetId = o.optInt("w", -1);
        JSONArray ks = o.optJSONArray("items");
        if (ks != null) for (int i = 0; i < ks.length(); i++) it.keys.add(ks.optString(i));
        return it;
    }

    private JSONObject toJson(Item it) throws Exception {
        JSONObject o = new JSONObject();
        o.put("t", it.type);
        if (it.key != null) o.put("k", it.key);
        o.put("n", it.name);
        o.put("p", it.page);
        o.put("x", it.cx);
        o.put("y", it.cy);
        o.put("sx", it.sx);
        o.put("sy", it.sy);
        o.put("w", it.widgetId);
        if (!it.keys.isEmpty()) o.put("items", new JSONArray(it.keys));
        return o;
    }

    public void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("pages", pageCount);
            JSONArray a = new JSONArray();
            for (Item it : items) a.put(toJson(it));
            o.put("items", a);
            JSONArray d = new JSONArray();
            for (Item it : dock) d.put(toJson(it));
            o.put("dock", d);
            prefs.setS("layout", o.toString());
        } catch (Throwable ignored) { }
    }

    private void pruneMissing() {
        List<Item> dead = new ArrayList<>();
        for (Item it : items) {
            if (it.type == Item.APP && store.loaded && store.find(it.key) == null) dead.add(it);
            if (it.type == Item.FOLDER) {
                List<String> gone = new ArrayList<>();
                for (String k : it.keys) if (store.loaded && store.find(k) == null) gone.add(k);
                it.keys.removeAll(gone);
                if (it.keys.isEmpty()) dead.add(it);
            }
        }
        items.removeAll(dead);
        List<Item> deadDock = new ArrayList<>();
        for (Item it : dock) if (it.type == Item.APP && store.loaded && store.find(it.key) == null) deadDock.add(it);
        dock.removeAll(deadDock);
    }

    private void buildDefault() {
        pageCount = 2;
        List<AppItem> apps = store.visible();
        String[] wanted = {"dialer", "phone", "contacts", "message", "sms", "chrome", "browser", "camera", "gallery", "photos"};
        List<AppItem> dockApps = new ArrayList<>();
        for (String w : wanted) {
            for (AppItem a : apps) {
                if (dockApps.size() >= prefs.dockCount()) break;
                if (dockApps.contains(a)) continue;
                if (a.pkg.toLowerCase(Locale.US).contains(w) || a.title().toLowerCase(Locale.US).contains(w)) {
                    dockApps.add(a);
                    break;
                }
            }
        }
        for (AppItem a : apps) {
            if (dockApps.size() >= Math.min(4, prefs.dockCount())) break;
            if (!dockApps.contains(a)) dockApps.add(a);
        }
        for (AppItem a : dockApps) {
            Item it = new Item();
            it.type = Item.APP; it.key = a.key();
            dock.add(it);
        }
        int cols = prefs.gridCols(), rows = prefs.gridRows();
        int i = 0;
        for (AppItem a : apps) {
            if (i >= cols * (rows - 1)) break;
            boolean inDock = false;
            for (Item d : dock) if (a.key().equals(d.key)) inDock = true;
            if (inDock) continue;
            Item it = new Item();
            it.type = Item.APP;
            it.key = a.key();
            it.page = 0;
            it.cx = i % cols;
            it.cy = i / cols;
            items.add(it);
            i++;
        }
    }

    public void onAppsChanged() {
        for (Item it : items) it.cached = null;
        for (Item it : dock) it.cached = null;
        if (items.isEmpty() && dock.isEmpty()) { buildDefault(); save(); }
        pruneMissing();
        invalidate();
    }

    // ------------------------------------------------------------- layout
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        computeMetrics();
    }

    private void computeMetrics() {
        int w = getWidth(), h = getHeight();
        sidePad = U.dpf(10);
        dockH = U.dpf(96);
        dockTop = h - dockH - U.dpf(12);
        gridTop = U.dpf(128);
        gridBottom = dockTop - U.dpf(26);
        cellW = (w - sidePad * 2) / (float) prefs.gridCols();
        cellH = (gridBottom - gridTop) / (float) prefs.gridRows();
    }

    public float iconSize() {
        return Math.min(cellW, cellH) * 0.56f * (prefs.iconSize() / 100f);
    }

    private RectF cellRect(int page, int cx, int cy, int sx, int sy) {
        float x0 = page * getWidth() + sidePad + cx * cellW;
        float y0 = gridTop + cy * cellH;
        return new RectF(x0, y0, x0 + cellW * sx, y0 + cellH * sy);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        computeMetrics();
        for (Item it : items) {
            if (it.type == Item.WIDGET && it.widgetView != null) {
                RectF rc = cellRect(it.page, it.cx, it.cy, it.sx, it.sy);
                int pad = U.dp(6);
                int left = (int) rc.left + pad;
                int top = (int) rc.top + pad;
                it.widgetView.layout(0, 0, (int) rc.width() - pad * 2, (int) rc.height() - pad * 2);
                it.widgetView.setX(rc.left - scrollXf + pad);
                it.widgetView.setY(rc.top + pad);
            }
        }
    }

    /** Keeps widget host views glued to their cells while pages scroll. */
    private void positionWidgets() {
        for (Item it : items) {
            if (it.type == Item.WIDGET && it.widgetView != null) {
                RectF rc = cellRect(it.page, it.cx, it.cy, it.sx, it.sy);
                it.widgetView.setX(rc.left - scrollXf + U.dpf(6));
                it.widgetView.setY(rc.top + U.dpf(6));
            }
        }
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        super.onMeasure(wSpec, hSpec);
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        computeMetrics();
        for (Item it : items) {
            if (it.type == Item.WIDGET && it.widgetView != null) {
                RectF rc = cellRect(it.page, it.cx, it.cy, it.sx, it.sy);
                it.widgetView.measure(
                        MeasureSpec.makeMeasureSpec((int) rc.width() - U.dp(12), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec((int) rc.height() - U.dp(12), MeasureSpec.EXACTLY));
            }
        }
    }

    public void attachWidget(Item it) {
        AppWidgetHostView v = host.createWidgetView(it.widgetId);
        if (v == null) return;
        it.widgetView = v;
        addView(v);
        requestLayout();
    }

    public void addWidget(int widgetId, int spanX, int spanY) {
        Item it = new Item();
        it.type = Item.WIDGET;
        it.widgetId = widgetId;
        it.sx = U.clampI(spanX, 1, prefs.gridCols());
        it.sy = U.clampI(spanY, 1, prefs.gridRows());
        int[] free = findFree(page, it.sx, it.sy);
        if (free == null) { host.toast("No room on this page"); return; }
        it.page = page; it.cx = free[0]; it.cy = free[1];
        items.add(it);
        attachWidget(it);
        save();
        invalidate();
    }

    private boolean occupied(int pg, int cx, int cy) {
        for (Item it : items) {
            if (it.page != pg) continue;
            if (cx >= it.cx && cx < it.cx + it.sx && cy >= it.cy && cy < it.cy + it.sy) return true;
        }
        return false;
    }

    private int[] findFree(int pg, int sx, int sy) {
        for (int y = 0; y + sy <= prefs.gridRows(); y++) {
            for (int x = 0; x + sx <= prefs.gridCols(); x++) {
                boolean ok = true;
                for (int i = 0; i < sx && ok; i++)
                    for (int j = 0; j < sy && ok; j++)
                        if (occupied(pg, x + i, y + j)) ok = false;
                if (ok) return new int[]{x, y};
            }
        }
        return null;
    }

    public void addAppToHome(AppItem a) {
        int[] free = findFree(page, 1, 1);
        int pg = page;
        if (free == null) {
            for (int q = 0; q < pageCount && free == null; q++) { free = findFree(q, 1, 1); pg = q; }
        }
        if (free == null) { pageCount++; pg = pageCount - 1; free = new int[]{0, 0}; }
        Item it = new Item();
        it.type = Item.APP; it.key = a.key(); it.page = pg; it.cx = free[0]; it.cy = free[1];
        items.add(it);
        save();
        invalidate();
        host.toast(a.title() + " added to home");
    }

    public void setTilt(float x, float y) {
        tiltX = U.lerp(tiltX, x, 0.12f);
        tiltY = U.lerp(tiltY, y, 0.12f);
        if (prefs.parallax() || prefs.particles()) invalidate();
    }

    // --------------------------------------------------------------- draw
    @Override protected void onDraw(Canvas c) {
        if (getWidth() == 0) return;
        if (!scroller.isFinished()) {
            scroller.computeScrollOffset();
            scrollXf = scroller.getCurrX();
            postInvalidateOnAnimation();
            positionWidgets();
        }
        int accent = host.accent();

        drawAmbient(c, accent);
        drawClock(c, accent);

        // pages
        c.save();
        c.translate(-scrollXf, 0);
        for (Item it : items) {
            if (it.type == Item.WIDGET) continue;
            if (it == dragItem) continue;
            drawItem(c, it, accent);
        }
        c.restore();

        drawDock(c, accent);
        drawPageDots(c, accent);

        if (dragItem != null) drawDragLayer(c, accent);
        if (!glyph.isEmpty()) drawGlyph(c, accent);
        if (folderOpen != null || folderAnim > 0.001f) drawFolder(c, accent);
    }

    private void drawAmbient(Canvas c, int accent) {
        float w = getWidth(), h = getHeight();
        // vignette + accent glow so icons stay readable on any wallpaper
        p.setShader(new android.graphics.RadialGradient(w * 0.5f + tiltX * U.dpf(18),
                h * 0.22f + tiltY * U.dpf(18), h * 0.9f,
                new int[]{U.alpha(accent, 0.16f), 0x00000000, 0x80000000},
                new float[]{0f, 0.55f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        if (!prefs.particles()) return;
        for (int i = 0; i < px.length; i++) {
            px[i] += pv[i] * (i % 2 == 0 ? 1 : -1) * 6f;
            py[i] -= pv[i] * 4f;
            if (py[i] < -0.05f) py[i] = 1.05f;
            if (px[i] > 1.05f) px[i] = -0.05f;
            if (px[i] < -0.05f) px[i] = 1.05f;
            float x = px[i] * w + tiltX * U.dpf(10) * pr[i];
            float y = py[i] * h + tiltY * U.dpf(10) * pr[i];
            p.setColor(U.alpha(i % 3 == 0 ? accent : 0xFFFFFFFF, 0.05f + 0.05f * pr[i] / 2.8f));
            c.drawCircle(x, y, U.dpf(pr[i]), p);
        }
        postInvalidateOnAnimation();
    }

    private void drawClock(Canvas c, int accent) {
        if (!prefs.statusClock()) return;
        Date now = new Date();
        tp.setTypeface(Typeface.create("sans-serif-thin", 0));
        tp.setTextSize(U.dpf(52));
        tp.setColor(0xFFFFFFFF);
        tp.setShadowLayer(U.dpf(10), 0, U.dpf(2), 0x66000000);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
        c.drawText(time, U.dpf(24), gridTop - U.dpf(44), tp);
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setTextSize(U.dpf(13));
        tp.setColor(0xCCFFFFFF);
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now);
        c.drawText(date, U.dpf(26), gridTop - U.dpf(22), tp);
        tp.clearShadowLayer();

        if (Focus.active(getContext())) {
            String s = "FOCUS " + Focus.remainingMin(getContext()) + "m";
            tp.setTextSize(U.dpf(10.5f));
            float w = tp.measureText(s) + U.dpf(24);
            RectF r = new RectF(getWidth() - U.dpf(24) - w, gridTop - U.dpf(72),
                    getWidth() - U.dpf(24), gridTop - U.dpf(48));
            p.setColor(U.alpha(accent, 0.22f));
            c.drawRoundRect(r, U.dpf(12), U.dpf(12), p);
            tp.setColor(accent);
            c.drawText(s, r.left + U.dpf(12), r.centerY() + U.dpf(3.6f), tp);
        }
    }

    private void drawItem(Canvas c, Item it, int accent) {
        RectF r = cellRect(it.page, it.cx, it.cy, it.sx, it.sy);
        float sz = iconSize();
        float cx = r.centerX(), cy = r.top + cellH * 0.44f;
        if (it.type == Item.APP) {
            AppItem a = it.app(getContext());
            if (a == null) return;
            drawIcon(c, a, cx, cy, sz, prefs.homeLabels(), accent, dropTarget == it);
        } else if (it.type == Item.FOLDER) {
            drawFolderIcon(c, it, cx, cy, sz, accent, dropTarget == it);
        }
    }

    private void drawIcon(Canvas c, AppItem a, float cx, float cy, float sz, boolean label,
                          int accent, boolean highlight) {
        boolean blocked = Focus.blocked(getContext(), a);
        RectF ir = new RectF(cx - sz / 2, cy - sz / 2, cx + sz / 2, cy + sz / 2);
        if (highlight) {
            p.setColor(U.alpha(accent, 0.3f));
            c.drawCircle(cx, cy, sz * 0.78f, p);
        }
        // soft shadow
        p.setColor(0x40000000);
        p.setMaskFilter(new android.graphics.BlurMaskFilter(U.dpf(7), android.graphics.BlurMaskFilter.Blur.NORMAL));
        c.drawPath(U.shapePath(prefs.iconShape(), new RectF(ir.left + U.dpf(1), ir.top + U.dpf(4),
                ir.right - U.dpf(1), ir.bottom + U.dpf(2))), p);
        p.setMaskFilter(null);

        p.setAlpha(blocked ? 80 : 255);
        if (a.icon != null) c.drawBitmap(a.icon, null, ir, p);
        p.setAlpha(255);

        if (prefs.badges()) {
            Integer b = AppStore.badges.get(a.pkg);
            if (b != null && b > 0) {
                p.setColor(0xFFFF4D6D);
                c.drawCircle(ir.right - U.dpf(3), ir.top + U.dpf(3), U.dpf(8.5f), p);
                tp.setTextSize(U.dpf(9.5f));
                tp.setColor(0xFFFFFFFF);
                tp.setTypeface(Typeface.create("sans-serif-medium", 0));
                String s = b > 9 ? "9+" : String.valueOf(b);
                c.drawText(s, ir.right - U.dpf(3) - tp.measureText(s) / 2f, ir.top + U.dpf(6.8f), tp);
            }
        }
        if (label) {
            tp.setTypeface(Typeface.create("sans-serif", 0));
            tp.setTextSize(U.dpf(11.5f));
            tp.setColor(blocked ? 0x80FFFFFF : 0xFFFFFFFF);
            tp.setShadowLayer(U.dpf(4), 0, U.dpf(1), 0xAA000000);
            String s = U.ellipsize(tp, a.title(), cellW - U.dpf(6));
            c.drawText(s, cx - tp.measureText(s) / 2f, cy + sz / 2 + U.dpf(16), tp);
            tp.clearShadowLayer();
        }
    }

    private void drawFolderIcon(Canvas c, Item it, float cx, float cy, float sz, int accent, boolean highlight) {
        RectF ir = new RectF(cx - sz / 2, cy - sz / 2, cx + sz / 2, cy + sz / 2);
        if (highlight) {
            p.setColor(U.alpha(accent, 0.3f));
            c.drawCircle(cx, cy, sz * 0.8f, p);
        }
        p.setColor(0x4DFFFFFF);
        Path shape = U.shapePath(prefs.iconShape(), ir);
        p.setColor(0x33000000);
        c.drawPath(shape, p);
        p.setColor(0x33FFFFFF);
        c.drawPath(shape, p);
        c.save();
        c.clipPath(shape);
        float pad = sz * 0.1f;
        float mini = (sz - pad * 3) / 2f;
        for (int i = 0; i < Math.min(4, it.keys.size()); i++) {
            AppItem a = store.find(it.keys.get(i));
            if (a == null || a.icon == null) continue;
            float x = ir.left + pad + (i % 2) * (mini + pad);
            float y = ir.top + pad + (i / 2) * (mini + pad);
            c.drawBitmap(a.icon, null, new RectF(x, y, x + mini, y + mini), p);
        }
        c.restore();
        if (prefs.homeLabels()) {
            tp.setTypeface(Typeface.create("sans-serif", 0));
            tp.setTextSize(U.dpf(11.5f));
            tp.setColor(0xFFFFFFFF);
            tp.setShadowLayer(U.dpf(4), 0, U.dpf(1), 0xAA000000);
            String s = U.ellipsize(tp, it.name, cellW - U.dpf(6));
            c.drawText(s, cx - tp.measureText(s) / 2f, cy + sz / 2 + U.dpf(16), tp);
            tp.clearShadowLayer();
        }
    }

    private List<Item> dockSlots() {
        List<Item> slots = new ArrayList<>(dock);
        if (prefs.smartDock()) {
            int free = prefs.dockCount() - slots.size();
            if (free > 0) {
                List<String> ex = new ArrayList<>();
                for (Item it : dock) if (it.key != null) ex.add(it.key);
                for (Item it : items) if (it.key != null) ex.add(it.key);
                for (AppItem a : store.predict(free, ex)) {
                    Item it = new Item();
                    it.type = Item.APP;
                    it.key = a.key();
                    slots.add(it);
                }
            }
        }
        return slots;
    }

    private void drawDock(Canvas c, int accent) {
        List<Item> slots = dockSlots();
        RectF pill = new RectF(U.dpf(12), dockTop, getWidth() - U.dpf(12), dockTop + dockH);
        p.setColor(0x33101218);
        c.drawRoundRect(pill, U.dpf(30), U.dpf(30), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(U.dpf(1));
        p.setColor(0x1AFFFFFF);
        c.drawRoundRect(pill, U.dpf(30), U.dpf(30), p);
        p.setStyle(Paint.Style.FILL);

        tp.setTextSize(U.dpf(9));
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setColor(U.alpha(accent, 0.75f));
        if (prefs.smartDock()) c.drawText("FLOW DOCK", U.dpf(26), dockTop + U.dpf(15), tp);

        if (slots.isEmpty()) return;
        float sz = iconSize() * 0.95f;
        float slotW = (pill.width() - U.dpf(20)) / slots.size();
        for (int i = 0; i < slots.size(); i++) {
            Item it = slots.get(i);
            float cx = pill.left + U.dpf(10) + slotW * (i + 0.5f);
            float cy = dockTop + dockH * 0.58f;
            if (it == dragItem) continue;
            if (it.type == Item.FOLDER) drawFolderIcon(c, it, cx, cy, sz, accent, dropTarget == it);
            else {
                AppItem a = it.app(getContext());
                if (a != null) drawIcon(c, a, cx, cy, sz, false, accent, dropTarget == it);
            }
        }
    }

    private void drawPageDots(Canvas c, int accent) {
        float y = dockTop - U.dpf(14);
        float gap = U.dpf(14);
        float total = (pageCount - 1) * gap;
        float x0 = getWidth() / 2f - total / 2f;
        float cur = scrollXf / Math.max(1, getWidth());
        for (int i = 0; i < pageCount; i++) {
            float d = Math.abs(cur - i);
            float rr = U.lerp(U.dpf(2.4f), U.dpf(4.2f), U.clamp(1 - d, 0, 1));
            p.setColor(U.alpha(d < 0.5f ? accent : 0xFFFFFFFF, U.lerp(0.3f, 1f, U.clamp(1 - d, 0, 1))));
            c.drawCircle(x0 + i * gap, y, rr, p);
        }
    }

    private void drawDragLayer(Canvas c, int accent) {
        // drop targets
        String[] labels = {"Remove", "Hide", "Uninstall", "Info"};
        int[] glyphs = {Glyphs.CLOSE, Glyphs.EYE_OFF, Glyphs.TRASH, Glyphs.INFO};
        float w = (getWidth() - U.dpf(48)) / 4f;
        for (int i = 0; i < 4; i++) {
            RectF r = new RectF(U.dpf(18) + i * w, U.dpf(44), U.dpf(18) + (i + 1) * w - U.dpf(8), U.dpf(44) + U.dpf(52));
            boolean on = dropAction == i;
            p.setColor(on ? U.alpha(i == 2 ? 0xFFFF5A5A : accent, 0.9f) : 0x33151821);
            c.drawRoundRect(r, U.dpf(18), U.dpf(18), p);
            int fg = on ? 0xFF0A0C12 : 0xFFE7EBF2;
            Glyphs.draw(c, glyphs[i], r.centerX(), r.centerY() - U.dpf(5), U.dpf(8), fg);
            tp.setTextSize(U.dpf(9.5f));
            tp.setColor(fg);
            String s = labels[i];
            c.drawText(s, r.centerX() - tp.measureText(s) / 2f, r.bottom - U.dpf(8), tp);
        }
        float sz = iconSize() * (1.18f + 0.06f * dragLift);
        if (dragItem.type == Item.FOLDER) drawFolderIcon(c, dragItem, dragX, dragY, sz, accent, false);
        else {
            AppItem a = dragItem.app(getContext());
            if (a != null) drawIcon(c, a, dragX, dragY, sz, false, accent, false);
        }
    }

    private void drawGlyph(Canvas c, int accent) {
        Path path = new Path();
        for (int i = 0; i < glyph.size(); i++) {
            PointF q = glyph.get(i);
            if (i == 0) path.moveTo(q.x, q.y); else path.lineTo(q.x, q.y);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(U.dpf(7));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(U.alpha(accent, 0.35f));
        p.setMaskFilter(new android.graphics.BlurMaskFilter(U.dpf(10), android.graphics.BlurMaskFilter.Blur.NORMAL));
        c.drawPath(path, p);
        p.setMaskFilter(null);
        p.setStrokeWidth(U.dpf(3.4f));
        p.setColor(U.alpha(0xFFFFFFFF, 0.92f));
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawFolder(Canvas c, int accent) {
        float t = folderAnim;
        p.setColor(U.alpha(0xFF05070C, 0.82f * t));
        c.drawRect(0, 0, getWidth(), getHeight(), p);
        if (folderOpen == null) return;
        float w = getWidth() - U.dpf(56);
        int cols = 4;
        int rows = (int) Math.ceil(folderOpen.keys.size() / (float) cols);
        float cell = w / cols;
        float h = rows * cell + U.dpf(76);
        RectF r = new RectF(U.dpf(28), getHeight() / 2f - h / 2f, getWidth() - U.dpf(28), getHeight() / 2f + h / 2f);
        c.save();
        c.scale(U.lerp(0.85f, 1f, t), U.lerp(0.85f, 1f, t), r.centerX(), r.centerY());
        p.setColor(U.alpha(0xFF141821, 0.96f * t));
        c.drawRoundRect(r, U.dpf(32), U.dpf(32), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(U.dpf(1));
        p.setColor(U.alpha(accent, 0.3f * t));
        c.drawRoundRect(r, U.dpf(32), U.dpf(32), p);
        p.setStyle(Paint.Style.FILL);
        tp.setTextSize(U.dpf(16));
        tp.setTypeface(Typeface.create("sans-serif-medium", 0));
        tp.setColor(U.alpha(0xFFFFFFFF, t));
        c.drawText(folderOpen.name, r.left + U.dpf(24), r.top + U.dpf(36), tp);
        tp.setTextSize(U.dpf(10.5f));
        tp.setColor(U.alpha(accent, 0.8f * t));
        c.drawText(folderOpen.keys.size() + " APPS  ·  TAP NAME TO RENAME",
                r.left + U.dpf(24), r.top + U.dpf(54), tp);

        float sz = cell * 0.56f;
        for (int i = 0; i < folderOpen.keys.size(); i++) {
            AppItem a = store.find(folderOpen.keys.get(i));
            if (a == null) continue;
            float cx = r.left + U.dpf(12) + cell * (i % cols + 0.5f);
            float cy = r.top + U.dpf(76) + cell * (i / cols + 0.42f);
            drawIcon(c, a, cx, cy, sz, true, accent, false);
        }
        c.restore();
    }

    // -------------------------------------------------------------- hit test
    public Item itemAt(float x, float y) {
        if (y >= dockTop) {
            List<Item> slots = dockSlots();
            if (slots.isEmpty()) return null;
            float slotW = (getWidth() - U.dpf(44)) / slots.size();
            int idx = (int) ((x - U.dpf(22)) / slotW);
            if (idx >= 0 && idx < slots.size()) return slots.get(idx);
            return null;
        }
        if (y < gridTop || y > gridBottom) return null;
        float wx = x + scrollXf;
        int pg = (int) (wx / getWidth());
        float lx = wx - pg * getWidth();
        int cx = (int) ((lx - sidePad) / cellW);
        int cy = (int) ((y - gridTop) / cellH);
        for (Item it : items) {
            if (it.page == pg && cx >= it.cx && cx < it.cx + it.sx && cy >= it.cy && cy < it.cy + it.sy) {
                return it.type == Item.WIDGET ? null : it;
            }
        }
        return null;
    }

    private int[] cellAt(float x, float y) {
        float wx = x + scrollXf;
        int pg = U.clampI((int) (wx / getWidth()), 0, pageCount - 1);
        float lx = wx - pg * getWidth();
        int cx = U.clampI((int) ((lx - sidePad) / cellW), 0, prefs.gridCols() - 1);
        int cy = U.clampI((int) ((y - gridTop) / cellH), 0, prefs.gridRows() - 1);
        return new int[]{pg, cx, cy};
    }

    // ---------------------------------------------------------------- touch
    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

        if (folderOpen != null) return folderTouch(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                downX = lastX = x; downY = lastY = y;
                downTime = System.currentTimeMillis();
                horizontal = vertical = glyphMode = movedOut = suppressTap = false;
                dropAction = -1; dropTarget = null;
                touchItem = itemAt(x, y);
                final float fx = x, fy = y;
                longPressRun = new Runnable() {
                    public void run() {
                        if (movedOut) return;
                        if (touchItem != null) startDrag(touchItem, fx, fy);
                        else { U.haptic(HomeView.this, true); host.homeMenu(fx, fy); suppressTap = true; }
                    }
                };
                postDelayed(longPressRun, 420);
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2 && dragItem == null) {
                    removeCallbacks(longPressRun);
                    suppressTap = true;
                    float mx = (e.getX(0) + e.getX(1)) / 2f, my = (e.getY(0) + e.getY(1)) / 2f;
                    host.showOrbit(mx, my);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragItem != null) {
                    dragX = x; dragY = y;
                    updateDragTarget(x, y);
                    invalidate();
                    return true;
                }
                float dx = x - downX, dy = y - downY;
                if (!horizontal && !vertical && !glyphMode) {
                    if (Math.hypot(dx, dy) > slop) {
                        movedOut = true;
                        removeCallbacks(longPressRun);
                        long held = System.currentTimeMillis() - downTime;
                        if (prefs.glyphs() && touchItem == null && held > 200) {
                            glyphMode = true;
                            glyph.clear();
                            glyph.add(new PointF(downX, downY));
                        } else if (Math.abs(dx) > Math.abs(dy)) horizontal = true;
                        else vertical = true;
                    }
                }
                if (glyphMode) {
                    glyph.add(new PointF(x, y));
                    invalidate();
                } else if (horizontal) {
                    scrollXf = U.clamp(scrollXf - (x - lastX), -U.dpf(60),
                            (pageCount - 1) * getWidth() + U.dpf(60));
                    positionWidgets();
                    invalidate();
                }
                lastX = x; lastY = y;
                return true;

            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressRun);
                if (dragItem != null) { finishDrag(x, y); return true; }
                if (glyphMode) { finishGlyph(); return true; }
                if (horizontal) { snapPage(x); return true; }
                if (vertical) {
                    float d = y - downY;
                    long dt = Math.max(1, System.currentTimeMillis() - downTime);
                    float vel = d / dt * 1000f;
                    if (d < -U.dpf(50) || vel < -800) host.openDrawer(-vel);
                    else if (d > U.dpf(50) || vel > 800) {
                        if (downX > getWidth() * 0.5f) host.openControlCenter();
                        else host.expandNotifications();
                    }
                    return true;
                }
                if (!suppressTap) onTap(x, y);
                return true;

            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRun);
                if (dragItem != null) finishDrag(x, y);
                glyph.clear();
                invalidate();
                return true;
        }
        return true;
    }

    private void onTap(float x, float y) {
        long now = System.currentTimeMillis();
        Item it = itemAt(x, y);
        if (it != null) {
            if (it.type == Item.FOLDER) { openFolder(it); return; }
            AppItem a = it.app(getContext());
            if (a == null) return;
            if (Focus.blocked(getContext(), a)) {
                host.toast(a.title() + " is paused by Focus Mode");
                U.haptic(this, true);
                return;
            }
            if (prefs.dynamicAccent()) host.onAccentSuggestion(a.color);
            host.openApp(a, this, x, y);
            return;
        }
        if (y < gridTop && y > U.dpf(30)) {       // clock tap
            try {
                android.content.Intent i = new android.content.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS);
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            } catch (Throwable ignored) { }
            return;
        }
        if (now - lastTapTime < 280 && prefs.doubleTapLock()) {
            host.lockScreen();
            lastTapTime = 0;
        } else lastTapTime = now;
    }

    // ---------------------------------------------------------------- pages
    private void snapPage(float x) {
        long dt = Math.max(1, System.currentTimeMillis() - downTime);
        float vel = (x - downX) / dt * 1000f;
        int target = Math.round(scrollXf / getWidth());
        if (vel < -700) target = (int) Math.ceil(scrollXf / getWidth());
        else if (vel > 700) target = (int) Math.floor(scrollXf / getWidth());
        target = U.clampI(target, 0, pageCount - 1);
        page = target;
        scroller.startScroll((int) scrollXf, 0, (int) (target * getWidth() - scrollXf), 0, 420);
        postInvalidateOnAnimation();
    }

    public void goToPage(int pg) {
        pg = U.clampI(pg, 0, pageCount - 1);
        page = pg;
        scroller.startScroll((int) scrollXf, 0, (int) (pg * getWidth() - scrollXf), 0, 380);
        postInvalidateOnAnimation();
    }

    public boolean atFirstPage() { return page == 0 && Math.abs(scrollXf) < 2; }

    public void resetToHome() {
        closeFolder();
        goToPage(0);
    }

    // ----------------------------------------------------------------- drag
    private void startDrag(Item it, float x, float y) {
        dragItem = it;
        dragX = x; dragY = y;
        U.haptic(this, true);
        U.anim(0, 1, 220, U.SPRING, new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { dragLift = (Float) a.getAnimatedValue(); invalidate(); }
        });
    }

    private void updateDragTarget(float x, float y) {
        dropAction = -1;
        dropTarget = null;
        if (y < U.dpf(110)) {
            float w = (getWidth() - U.dpf(48)) / 4f;
            int idx = (int) ((x - U.dpf(18)) / w);
            if (idx >= 0 && idx < 4 && y > U.dpf(38)) dropAction = idx;
            return;
        }
        Item over = itemAt(x, y);
        if (over != null && over != dragItem) dropTarget = over;

        // edge page flip
        long now = System.currentTimeMillis();
        if (now - pageFlipTime > 650) {
            if (x < U.dpf(28) && page > 0) { pageFlipTime = now; goToPage(page - 1); }
            else if (x > getWidth() - U.dpf(28)) {
                pageFlipTime = now;
                if (page == pageCount - 1 && pageCount < 6) pageCount++;
                goToPage(page + 1);
            }
        }
    }

    private void finishDrag(float x, float y) {
        Item it = dragItem;
        dragItem = null;
        if (it == null) return;
        AppItem app = it.app(getContext());
        if (dropAction >= 0) {
            switch (dropAction) {
                case 0: removeItem(it); break;
                case 1:
                    if (app != null) { prefs.toggleIn(Prefs.HIDDEN, app.key()); removeItem(it); host.toast(app.title() + " hidden"); }
                    break;
                case 2:
                    if (app != null) store.uninstall((android.app.Activity) getContext(), app);
                    break;
                case 3:
                    if (app != null) store.appInfo((android.app.Activity) getContext(), app);
                    break;
            }
            dropAction = -1; dropTarget = null;
            save(); invalidate();
            return;
        }
        if (dropTarget != null && dropTarget != it) {
            // make or extend folder
            Item t = dropTarget;
            if (t.type == Item.FOLDER) {
                if (it.type == Item.APP) { t.keys.add(it.key); removeItem(it); }
                else { t.keys.addAll(it.keys); removeItem(it); }
            } else if (t.type == Item.APP && it.type == Item.APP) {
                t.type = Item.FOLDER;
                t.keys.clear();
                t.keys.add(t.key);
                t.keys.add(it.key);
                AppItem a1 = store.find(t.key);
                t.name = a1 != null ? smartFolderName(a1) : "Folder";
                t.key = null;
                removeItem(it);
            }
            dropTarget = null;
            save(); invalidate();
            U.haptic(this, true);
            return;
        }
        if (y >= dockTop) {
            // move into dock
            items.remove(it);
            if (!dock.contains(it)) {
                if (dock.size() >= prefs.dockCount()) dock.remove(dock.size() - 1);
                List<Item> slots = dockSlots();
                float slotW = (getWidth() - U.dpf(44)) / Math.max(1, slots.size());
                int idx = U.clampI((int) ((x - U.dpf(22)) / slotW), 0, dock.size());
                dock.add(Math.min(idx, dock.size()), it);
            }
            save(); invalidate();
            return;
        }
        int[] cell = cellAt(x, y);
        dock.remove(it);
        if (!items.contains(it)) items.add(it);
        Item occupant = null;
        for (Item o : items) {
            if (o != it && o.page == cell[0] && cell[1] >= o.cx && cell[1] < o.cx + o.sx
                    && cell[2] >= o.cy && cell[2] < o.cy + o.sy) occupant = o;
        }
        if (occupant != null) {
            // swap
            int px2 = occupant.cx, py2 = occupant.cy, pg2 = occupant.page;
            occupant.cx = it.cx; occupant.cy = it.cy; occupant.page = it.page;
            it.cx = px2; it.cy = py2; it.page = pg2;
        } else {
            it.page = cell[0]; it.cx = cell[1]; it.cy = cell[2];
        }
        dropTarget = null;
        save();
        invalidate();
    }

    private String smartFolderName(AppItem a) {
        String t = a.title();
        return t.length() > 0 ? (t.substring(0, 1).toUpperCase(Locale.getDefault()) + " apps") : "Folder";
    }

    public void removeItem(Item it) {
        items.remove(it);
        dock.remove(it);
        if (it.type == Item.WIDGET) {
            if (it.widgetView != null) removeView(it.widgetView);
            host.removeWidget(it.widgetId);
        }
        save();
        invalidate();
    }

    // -------------------------------------------------------------- folders
    public void openFolder(Item it) {
        folderOpen = it;
        U.anim(0, 1, 300, U.SPRING, new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { folderAnim = (Float) a.getAnimatedValue(); invalidate(); }
        });
    }

    public boolean folderShowing() { return folderOpen != null; }

    public void closeFolder() {
        if (folderOpen == null) return;
        folderOpen = null;
        U.anim(1, 0, 220, U.EASE, new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { folderAnim = (Float) a.getAnimatedValue(); invalidate(); }
        });
    }

    private boolean folderTouch(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        float x = e.getX(), y = e.getY();
        Item f = folderOpen;
        if (f == null) return true;
        int cols = 4;
        float w = getWidth() - U.dpf(56);
        float cell = w / cols;
        int rows = (int) Math.ceil(f.keys.size() / (float) cols);
        float h = rows * cell + U.dpf(76);
        RectF r = new RectF(U.dpf(28), getHeight() / 2f - h / 2f, getWidth() - U.dpf(28), getHeight() / 2f + h / 2f);
        if (!r.contains(x, y)) { closeFolder(); return true; }
        if (y < r.top + U.dpf(60)) { renameFolder(f); return true; }
        int col = (int) ((x - r.left - U.dpf(12)) / cell);
        int row = (int) ((y - r.top - U.dpf(76)) / cell);
        int idx = row * cols + col;
        if (idx >= 0 && idx < f.keys.size()) {
            AppItem a = store.find(f.keys.get(idx));
            if (a != null) {
                closeFolder();
                host.openApp(a, this, x, y);
            }
        }
        return true;
    }

    private void renameFolder(final Item f) {
        final android.widget.EditText et = new android.widget.EditText(getContext());
        et.setText(f.name);
        et.setTextColor(0xFFFFFFFF);
        new android.app.AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Folder name")
                .setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    f.name = et.getText().toString();
                    save();
                    invalidate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------- glyph
    private void finishGlyph() {
        List<PointF> pts = new ArrayList<>(glyph);
        glyph.clear();
        invalidate();
        String letter = Gesture.recognize(pts);
        if (letter == null) { host.toast("Glyph not recognised"); return; }
        if (letter.equals("O")) { host.showOrbit(getWidth() / 2f, getHeight() / 2f); return; }
        if (letter.equals("S")) { host.openDrawer(0); return; }
        AppItem best = null;
        float bestScore = -1;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        for (AppItem a : store.visible()) {
            if (!a.title().toUpperCase(Locale.getDefault()).startsWith(letter)) continue;
            float s = store.flowScore(a, hour) + 1;
            if (s > bestScore) { bestScore = s; best = a; }
        }
        if (best == null) { host.toast("No app starts with " + letter); return; }
        U.haptic(this, true);
        host.toast(letter + " → " + best.title());
        host.openApp(best, this, getWidth() / 2f, getHeight() / 2f);
    }
}
