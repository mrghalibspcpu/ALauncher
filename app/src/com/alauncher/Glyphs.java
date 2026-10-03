package com.alauncher;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Hand-drawn vector icon set — no resources, scales to any size. */
public class Glyphs {

    public static final int INFO = 0, TRASH = 1, EYE_OFF = 2, PENCIL = 3, STAR = 4, SPLIT = 5,
            PIN = 6, WIFI = 7, BT = 8, TORCH = 9, ROTATE = 10, PLANE = 11, MOON = 12, GEAR = 13,
            SUN = 14, VOLUME = 15, PLAY = 16, PAUSE = 17, NEXT = 18, PREV = 19, SEARCH = 20,
            GRID = 21, WIDGET = 22, WALL = 23, CLOCK = 24, FOLDER = 25, LOCK = 26, SHORTCUT = 27,
            CALC = 28, CAMERA = 29, BULB = 30, CAST = 31, DATA = 32, SHIELD = 33, BOLT = 34,
            TIMER = 35, HEART = 36, PLUS = 37, CHECK = 38, CLOSE = 39;

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static void draw(Canvas c, int id, float cx, float cy, float r, int color) {
        P.reset();
        P.setAntiAlias(true);
        P.setColor(color);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1.6f, r * 0.19f));
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeJoin(Paint.Join.ROUND);
        Path p = new Path();
        switch (id) {
            case INFO:
                c.drawCircle(cx, cy, r, P);
                c.drawLine(cx, cy - r * 0.45f, cx, cy - r * 0.42f, P);
                c.drawLine(cx, cy - r * 0.1f, cx, cy + r * 0.5f, P);
                break;
            case TRASH:
                c.drawLine(cx - r, cy - r * 0.6f, cx + r, cy - r * 0.6f, P);
                p.moveTo(cx - r * 0.7f, cy - r * 0.6f);
                p.lineTo(cx - r * 0.55f, cy + r);
                p.lineTo(cx + r * 0.55f, cy + r);
                p.lineTo(cx + r * 0.7f, cy - r * 0.6f);
                c.drawPath(p, P);
                c.drawLine(cx - r * 0.35f, cy - r * 0.6f, cx - r * 0.3f, cy - r, P);
                c.drawLine(cx + r * 0.35f, cy - r * 0.6f, cx + r * 0.3f, cy - r, P);
                break;
            case EYE_OFF:
                p.moveTo(cx - r, cy);
                p.quadTo(cx, cy - r, cx + r, cy);
                p.quadTo(cx, cy + r, cx - r, cy);
                c.drawPath(p, P);
                c.drawLine(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f, P);
                break;
            case PENCIL:
                c.drawLine(cx - r * 0.8f, cy + r * 0.8f, cx + r * 0.7f, cy - r * 0.7f, P);
                c.drawLine(cx - r * 0.8f, cy + r * 0.8f, cx - r * 0.45f, cy + r * 0.9f, P);
                c.drawLine(cx + r * 0.4f, cy - r, cx + r, cy - r * 0.4f, P);
                break;
            case STAR: {
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI / 5 * i - Math.PI / 2;
                    float rr = (i % 2 == 0) ? r : r * 0.45f;
                    float x = (float) (cx + rr * Math.cos(a)), y = (float) (cy + rr * Math.sin(a));
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.close();
                c.drawPath(p, P);
                break;
            }
            case SPLIT:
                c.drawRoundRect(new RectF(cx - r, cy - r, cx - r * 0.12f, cy + r), r * 0.2f, r * 0.2f, P);
                c.drawRoundRect(new RectF(cx + r * 0.12f, cy - r, cx + r, cy + r), r * 0.2f, r * 0.2f, P);
                break;
            case PIN:
                c.drawCircle(cx, cy - r * 0.25f, r * 0.55f, P);
                c.drawLine(cx, cy + r * 0.3f, cx, cy + r, P);
                break;
            case WIFI:
                for (int i = 0; i < 3; i++) {
                    float rr = r * (0.35f + i * 0.33f);
                    RectF rect = new RectF(cx - rr, cy - rr + r * 0.45f, cx + rr, cy + rr + r * 0.45f);
                    c.drawArc(rect, 215, 110, false, P);
                }
                P.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy + r * 0.55f, r * 0.14f, P);
                break;
            case BT:
                p.moveTo(cx - r * 0.55f, cy - r * 0.45f);
                p.lineTo(cx + r * 0.5f, cy + r * 0.5f);
                p.lineTo(cx, cy + r);
                p.lineTo(cx, cy - r);
                p.lineTo(cx + r * 0.5f, cy - r * 0.5f);
                p.lineTo(cx - r * 0.55f, cy + r * 0.45f);
                c.drawPath(p, P);
                break;
            case TORCH:
                p.moveTo(cx - r * 0.5f, cy - r);
                p.lineTo(cx + r * 0.5f, cy - r);
                p.lineTo(cx + r * 0.3f, cy - r * 0.4f);
                p.lineTo(cx + r * 0.3f, cy + r);
                p.lineTo(cx - r * 0.3f, cy + r);
                p.lineTo(cx - r * 0.3f, cy - r * 0.4f);
                p.close();
                c.drawPath(p, P);
                break;
            case ROTATE:
                c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 40, 280, false, P);
                p.moveTo(cx + r * 0.45f, cy - r * 0.95f);
                p.lineTo(cx + r * 0.95f, cy - r * 0.55f);
                p.lineTo(cx + r * 0.35f, cy - r * 0.3f);
                c.drawPath(p, P);
                break;
            case PLANE:
                p.moveTo(cx - r, cy + r * 0.1f);
                p.lineTo(cx + r, cy - r * 0.35f);
                p.lineTo(cx + r * 0.2f, cy + r * 0.55f);
                p.lineTo(cx - r * 0.1f, cy + r * 0.1f);
                p.close();
                c.drawPath(p, P);
                break;
            case MOON:
                p.addArc(new RectF(cx - r, cy - r, cx + r, cy + r), 70, 230);
                p.quadTo(cx + r * 0.35f, cy, cx + r * 0.33f, cy + r * 0.92f);
                c.drawPath(p, P);
                break;
            case GEAR:
                c.drawCircle(cx, cy, r * 0.42f, P);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine((float) (cx + Math.cos(a) * r * 0.65f), (float) (cy + Math.sin(a) * r * 0.65f),
                            (float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r), P);
                }
                break;
            case SUN:
                c.drawCircle(cx, cy, r * 0.45f, P);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine((float) (cx + Math.cos(a) * r * 0.72f), (float) (cy + Math.sin(a) * r * 0.72f),
                            (float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r), P);
                }
                break;
            case VOLUME:
                p.moveTo(cx - r * 0.9f, cy - r * 0.3f);
                p.lineTo(cx - r * 0.45f, cy - r * 0.3f);
                p.lineTo(cx, cy - r * 0.85f);
                p.lineTo(cx, cy + r * 0.85f);
                p.lineTo(cx - r * 0.45f, cy + r * 0.3f);
                p.lineTo(cx - r * 0.9f, cy + r * 0.3f);
                p.close();
                c.drawPath(p, P);
                c.drawArc(new RectF(cx - r * 0.1f, cy - r * 0.6f, cx + r * 0.75f, cy + r * 0.6f), -60, 120, false, P);
                break;
            case PLAY:
                P.setStyle(Paint.Style.FILL);
                p.moveTo(cx - r * 0.55f, cy - r * 0.85f);
                p.lineTo(cx + r * 0.85f, cy);
                p.lineTo(cx - r * 0.55f, cy + r * 0.85f);
                p.close();
                c.drawPath(p, P);
                break;
            case PAUSE:
                P.setStyle(Paint.Style.FILL);
                c.drawRoundRect(new RectF(cx - r * 0.65f, cy - r * 0.8f, cx - r * 0.18f, cy + r * 0.8f), r * .12f, r * .12f, P);
                c.drawRoundRect(new RectF(cx + r * 0.18f, cy - r * 0.8f, cx + r * 0.65f, cy + r * 0.8f), r * .12f, r * .12f, P);
                break;
            case NEXT:
                P.setStyle(Paint.Style.FILL);
                p.moveTo(cx - r * 0.8f, cy - r * 0.75f);
                p.lineTo(cx + r * 0.25f, cy);
                p.lineTo(cx - r * 0.8f, cy + r * 0.75f);
                p.close();
                c.drawPath(p, P);
                c.drawRect(cx + r * 0.42f, cy - r * 0.75f, cx + r * 0.72f, cy + r * 0.75f, P);
                break;
            case PREV:
                P.setStyle(Paint.Style.FILL);
                p.moveTo(cx + r * 0.8f, cy - r * 0.75f);
                p.lineTo(cx - r * 0.25f, cy);
                p.lineTo(cx + r * 0.8f, cy + r * 0.75f);
                p.close();
                c.drawPath(p, P);
                c.drawRect(cx - r * 0.72f, cy - r * 0.75f, cx - r * 0.42f, cy + r * 0.75f, P);
                break;
            case SEARCH:
                c.drawCircle(cx - r * 0.18f, cy - r * 0.18f, r * 0.62f, P);
                c.drawLine(cx + r * 0.3f, cy + r * 0.3f, cx + r * 0.9f, cy + r * 0.9f, P);
                break;
            case GRID:
                for (int i = 0; i < 2; i++)
                    for (int j = 0; j < 2; j++) {
                        float x = cx + (i == 0 ? -r * 0.95f : r * 0.1f);
                        float y = cy + (j == 0 ? -r * 0.95f : r * 0.1f);
                        c.drawRoundRect(new RectF(x, y, x + r * 0.85f, y + r * 0.85f), r * 0.2f, r * 0.2f, P);
                    }
                break;
            case WIDGET:
                c.drawRoundRect(new RectF(cx - r, cy - r * 0.6f, cx + r, cy + r * 0.9f), r * 0.25f, r * 0.25f, P);
                c.drawLine(cx - r * 0.5f, cy + r * 0.15f, cx + r * 0.5f, cy + r * 0.15f, P);
                break;
            case WALL:
                c.drawRoundRect(new RectF(cx - r, cy - r * 0.8f, cx + r, cy + r * 0.8f), r * 0.2f, r * 0.2f, P);
                p.moveTo(cx - r * 0.8f, cy + r * 0.5f);
                p.lineTo(cx - r * 0.2f, cy - r * 0.2f);
                p.lineTo(cx + r * 0.3f, cy + r * 0.5f);
                c.drawPath(p, P);
                c.drawCircle(cx + r * 0.45f, cy - r * 0.35f, r * 0.17f, P);
                break;
            case CLOCK:
                c.drawCircle(cx, cy, r, P);
                c.drawLine(cx, cy, cx, cy - r * 0.55f, P);
                c.drawLine(cx, cy, cx + r * 0.4f, cy + r * 0.2f, P);
                break;
            case FOLDER:
                p.moveTo(cx - r, cy + r * 0.7f);
                p.lineTo(cx - r, cy - r * 0.6f);
                p.lineTo(cx - r * 0.2f, cy - r * 0.6f);
                p.lineTo(cx + r * 0.02f, cy - r * 0.3f);
                p.lineTo(cx + r, cy - r * 0.3f);
                p.lineTo(cx + r, cy + r * 0.7f);
                p.close();
                c.drawPath(p, P);
                break;
            case LOCK:
                c.drawRoundRect(new RectF(cx - r * 0.75f, cy - r * 0.15f, cx + r * 0.75f, cy + r * 0.85f), r * 0.2f, r * 0.2f, P);
                c.drawArc(new RectF(cx - r * 0.45f, cy - r * 0.95f, cx + r * 0.45f, cy + r * 0.1f), 180, 180, false, P);
                break;
            case SHORTCUT:
                c.drawLine(cx - r * 0.6f, cy + r * 0.6f, cx + r * 0.7f, cy - r * 0.7f, P);
                c.drawLine(cx + r * 0.1f, cy - r * 0.7f, cx + r * 0.7f, cy - r * 0.7f, P);
                c.drawLine(cx + r * 0.7f, cy - r * 0.1f, cx + r * 0.7f, cy - r * 0.7f, P);
                break;
            case CALC:
                c.drawRoundRect(new RectF(cx - r * 0.8f, cy - r, cx + r * 0.8f, cy + r), r * 0.22f, r * 0.22f, P);
                c.drawLine(cx - r * 0.45f, cy - r * 0.55f, cx + r * 0.45f, cy - r * 0.55f, P);
                c.drawLine(cx - r * 0.4f, cy + r * 0.15f, cx - r * 0.1f, cy + r * 0.15f, P);
                c.drawLine(cx + r * 0.1f, cy + r * 0.15f, cx + r * 0.4f, cy + r * 0.15f, P);
                c.drawLine(cx - r * 0.4f, cy + r * 0.6f, cx - r * 0.1f, cy + r * 0.6f, P);
                c.drawLine(cx + r * 0.1f, cy + r * 0.6f, cx + r * 0.4f, cy + r * 0.6f, P);
                break;
            case CAMERA:
                c.drawRoundRect(new RectF(cx - r, cy - r * 0.6f, cx + r, cy + r * 0.8f), r * 0.25f, r * 0.25f, P);
                c.drawCircle(cx, cy + r * 0.1f, r * 0.42f, P);
                c.drawLine(cx - r * 0.35f, cy - r * 0.6f, cx - r * 0.15f, cy - r * 0.9f, P);
                break;
            case BULB:
                c.drawCircle(cx, cy - r * 0.2f, r * 0.62f, P);
                c.drawLine(cx - r * 0.3f, cy + r * 0.55f, cx + r * 0.3f, cy + r * 0.55f, P);
                c.drawLine(cx - r * 0.2f, cy + r * 0.85f, cx + r * 0.2f, cy + r * 0.85f, P);
                break;
            case CAST:
                c.drawRoundRect(new RectF(cx - r, cy - r * 0.8f, cx + r, cy + r * 0.55f), r * 0.2f, r * 0.2f, P);
                c.drawArc(new RectF(cx - r * 1.1f, cy, cx + r * 0.3f, cy + r * 1.4f), 270, 90, false, P);
                break;
            case DATA:
                c.drawLine(cx - r * 0.8f, cy + r * 0.8f, cx - r * 0.8f, cy + r * 0.1f, P);
                c.drawLine(cx - r * 0.2f, cy + r * 0.8f, cx - r * 0.2f, cy - r * 0.3f, P);
                c.drawLine(cx + r * 0.4f, cy + r * 0.8f, cx + r * 0.4f, cy - r * 0.8f, P);
                break;
            case SHIELD:
                p.moveTo(cx, cy - r);
                p.lineTo(cx + r * 0.85f, cy - r * 0.55f);
                p.quadTo(cx + r * 0.85f, cy + r * 0.6f, cx, cy + r);
                p.quadTo(cx - r * 0.85f, cy + r * 0.6f, cx - r * 0.85f, cy - r * 0.55f);
                p.close();
                c.drawPath(p, P);
                break;
            case BOLT:
                P.setStyle(Paint.Style.FILL);
                p.moveTo(cx + r * 0.3f, cy - r);
                p.lineTo(cx - r * 0.6f, cy + r * 0.15f);
                p.lineTo(cx - r * 0.05f, cy + r * 0.15f);
                p.lineTo(cx - r * 0.3f, cy + r);
                p.lineTo(cx + r * 0.6f, cy - r * 0.15f);
                p.lineTo(cx + r * 0.05f, cy - r * 0.15f);
                p.close();
                c.drawPath(p, P);
                break;
            case TIMER:
                c.drawCircle(cx, cy + r * 0.15f, r * 0.8f, P);
                c.drawLine(cx - r * 0.35f, cy - r * 0.95f, cx + r * 0.35f, cy - r * 0.95f, P);
                c.drawLine(cx, cy + r * 0.15f, cx, cy - r * 0.35f, P);
                break;
            case HEART:
                p.moveTo(cx, cy + r * 0.85f);
                p.cubicTo(cx - r * 1.6f, cy - r * 0.2f, cx - r * 0.45f, cy - r * 1.2f, cx, cy - r * 0.25f);
                p.cubicTo(cx + r * 0.45f, cy - r * 1.2f, cx + r * 1.6f, cy - r * 0.2f, cx, cy + r * 0.85f);
                c.drawPath(p, P);
                break;
            case PLUS:
                c.drawLine(cx - r * 0.8f, cy, cx + r * 0.8f, cy, P);
                c.drawLine(cx, cy - r * 0.8f, cx, cy + r * 0.8f, P);
                break;
            case CHECK:
                p.moveTo(cx - r * 0.75f, cy);
                p.lineTo(cx - r * 0.15f, cy + r * 0.6f);
                p.lineTo(cx + r * 0.8f, cy - r * 0.65f);
                c.drawPath(p, P);
                break;
            case CLOSE:
                c.drawLine(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, P);
                c.drawLine(cx + r * 0.7f, cy - r * 0.7f, cx - r * 0.7f, cy + r * 0.7f, P);
                break;
            default:
                c.drawCircle(cx, cy, r * 0.8f, P);
        }
    }
}
