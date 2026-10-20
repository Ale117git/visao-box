package br.visaobox.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Desenha a interface estilo Vision Pro por cima da câmera, em 1 ou 2 olhos,
 * e cuida dos gestos (pinça para tocar, pinça na barrinha para arrastar).
 * Coordenadas da cena: a altura de cada olho vale 1, com o centro em (0,0).
 */
public class VrView extends View {

    /* ---------- ajustes (mudados pela tela de Ajustes) ---------- */
    public boolean stereo = true, skel = true;
    public float ipd = 0f, zoom = 1f, depth = 0.012f;

    /* ---------- dados vindos da câmera e do modelo ---------- */
    public volatile Bitmap frame;
    public volatile float[][] rawHands = new float[0][];
    public volatile long rawStamp = 0;
    public volatile boolean modelReady = false;
    public volatile String error = null;

    static final int RELOGIO = 0, DESENHO = 1, BOLHAS = 2, MUSICA = 3, CAMERA = 4;
    static final String[] NAMES = {"Relógio", "Desenho", "Bolhas", "Música", "Câmera"};
    static final int[][] COLS = {
            {0xFFFFB36B, 0xFFE8590C}, {0xFFF88AE6, 0xFF8E44AD}, {0xFF7FE3FF, 0xFF0A8FC9},
            {0xFFFF8A8A, 0xFFC0392B}, {0xFFDFE6EE, 0xFF5B6876}};
    static final float[][] SIZES = {{.48f, .27f}, {.64f, .42f}, {.6f, .42f}, {.56f, .36f}, {.62f, .4f}};
    static final String[] NOTE_N = {"Dó", "Ré", "Mi", "Sol", "Lá", "Dó"};
    static final double[] NOTE_F = {261.63, 293.66, 329.63, 392.0, 440.0, 523.25};
    static final int[] PEN = {0xFFFFFFFF, 0xFFFF6B6B, 0xFFFECA57, 0xFF48DBFB, 0xFF1DD1A1};
    static final int[][] CON = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {0, 5}, {5, 6}, {6, 7}, {7, 8}, {5, 9}, {9, 10}, {10, 11},
            {11, 12}, {9, 13}, {13, 14}, {14, 15}, {15, 16}, {13, 17}, {17, 18}, {18, 19}, {19, 20}, {0, 17}};
    static final int ICON = 0, DOCK = 1, CLOSE = 2, GRAB = 3, BTN = 4, AREA = 5, BODY = 6;

    static class Stroke { int c; final ArrayList<float[]> pts = new ArrayList<>(); }
    static class Bubble { float x, y, r, vy, ph, dx; }
    static class Part { float x, y, vx, vy, life; }
    static class Win {
        int id, app; float x, y, w, h; long born;
        final ArrayList<Stroke> strokes = new ArrayList<>(); int pen = 0xFFFFFFFF;
        final ArrayList<Bubble> bubbles = new ArrayList<>(); final ArrayList<Part> parts = new ArrayList<>();
        int score; float t; final long[] flash = new long[6];
        final ArrayList<Bitmap> photos = new ArrayList<>(); long shot;
    }
    static class Btn {
        final String id; final boolean circle; float x, y, r; RectF rect;
        Btn(String id, float x, float y, float r) { this.id = id; circle = true; this.x = x; this.y = y; this.r = r; }
        Btn(String id, RectF rc) { this.id = id; circle = false; rect = rc; }
        boolean hit(float px, float py) { return circle ? Math.hypot(px - x, py - y) < r * 1.6f : rect.contains(px, py); }
    }
    static class Parts { RectF body, grab, content; float cx, cy, cr; }
    static class Hit { final int type; final String key; Win win; int app; String id; Hit(int t, String k) { type = t; key = k; } }
    static class Hand { final float[] x = new float[21], y = new float[21]; boolean down, init; float cx, cy; long last; }
    static class Ptr { boolean exists, down; float x, y; Hit hit; Win drag; float dx, dy; }

    /* ---------- estado ---------- */
    private boolean home = true;
    private long homeT = 0, lastT = 0, lastStamp = -1;
    private final ArrayList<Win> wins = new ArrayList<>();
    private int wid = 0;
    private final Hand[] hands = {new Hand(), new Hand()};
    private final Ptr[] ptrs = {new Ptr(), new Ptr(), new Ptr()};
    private final HashSet<String> hov = new HashSet<>();
    private float tX, tY; private boolean tActive, tDown, tRelease, tEnd;
    private final Random rnd = new Random();

    /* ---------- geometria ---------- */
    private int n = 2; private float ew, eh, dw, dh, x0, y0, halfW;

    /* ---------- pintura ---------- */
    private Canvas c;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grad = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Typeface tfRegular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface tfMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface tfLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final SimpleDateFormat fmtTime = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat fmtDate = new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR"));
    private final Synth synth = new Synth();

    public VrView(Context ctx) {
        super(ctx);
        fill.setStyle(Paint.Style.FILL);
        grad.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setSubpixelText(true);
        setKeepScreenOn(true);
    }

    public void resetHands() {
        for (Hand h : hands) h.init = false;
        rawHands = new float[0][];
    }

    /* ================= geometria ================= */

    private void geom() {
        n = stereo ? 2 : 1;
        ew = getWidth() / (float) n;
        eh = getHeight();
        Bitmap f = frame;
        float vw = f != null ? f.getWidth() : 640, vh = f != null ? f.getHeight() : 480;
        float s = Math.max(ew / vw, eh / vh);
        dw = vw * s / eh; dh = vh * s / eh;
        x0 = -dw / 2; y0 = -dh / 2;
        halfW = ew / (2 * eh);
    }

    private Parts parts(Win w) {
        Parts p = new Parts();
        float x = w.x - w.w / 2, y = w.y - w.h / 2, b = y + w.h;
        p.body = new RectF(x, y, x + w.w, b);
        p.grab = new RectF(w.x - .07f, b + .028f, w.x + .07f, b + .048f);
        p.cx = w.x - .105f; p.cy = b + .038f; p.cr = .016f;
        p.content = new RectF(x + .022f, y + .065f, x + w.w - .022f, b - .022f);
        return p;
    }

    private List<Btn> buttons(Win w, Parts P) {
        ArrayList<Btn> out = new ArrayList<>();
        RectF B = P.body, C = P.content;
        if (w.app == DESENHO) {
            for (int i = 0; i < 5; i++) out.add(new Btn("c" + i, B.right - .215f + i * .03f, B.top + .033f, .0105f));
            out.add(new Btn("clear", new RectF(B.right - .08f, B.top + .019f, B.right - .022f, B.top + .047f)));
        } else if (w.app == MUSICA) {
            float gap = .014f, pw = (C.width() - gap * 2) / 3, ph = (C.height() - gap) / 2;
            for (int i = 0; i < 6; i++) {
                float x = C.left + (i % 3) * (pw + gap), y = C.top + (i / 3) * (ph + gap);
                out.add(new Btn("p" + i, new RectF(x, y, x + pw, y + ph)));
            }
        } else if (w.app == CAMERA) {
            out.add(new Btn("shot", C.right - .048f, C.centerY(), .034f));
        }
        return out;
    }

    private static float iconX(int i) { return (i - 2) * .165f; }
    private static final float ICON_Y = -.01f, ICON_R = .058f, DOCK_X = 0f, DOCK_Y = .4f, DOCK_R = .026f;

    /* ================= toque na cena ================= */

    private Hit hitTest(float x, float y) {
        if (home) for (int i = 0; i < 5; i++) {
            if (Math.hypot(x - iconX(i), y - ICON_Y) < ICON_R * 1.25f) { Hit h = new Hit(ICON, "icon:" + i); h.app = i; return h; }
        }
        if (Math.hypot(x - DOCK_X, y - DOCK_Y) < DOCK_R * 1.8f) return new Hit(DOCK, "dock");
        if (home) return null;
        for (int i = wins.size() - 1; i >= 0; i--) {
            Win w = wins.get(i);
            Parts P = parts(w);
            if (Math.hypot(x - P.cx, y - P.cy) < P.cr * 1.9f) { Hit h = new Hit(CLOSE, "close:" + w.id); h.win = w; return h; }
            RectF g = P.grab;
            if (x > g.left - .02f && x < g.right + .02f && y > g.top - .022f && y < g.bottom + .022f) {
                Hit h = new Hit(GRAB, "grab:" + w.id); h.win = w; return h;
            }
            if (P.body.contains(x, y)) {
                for (Btn b : buttons(w, P)) if (b.hit(x, y)) { Hit h = new Hit(BTN, "btn:" + w.id + ":" + b.id); h.win = w; h.id = b.id; return h; }
                if (w.app == DESENHO && P.content.contains(x, y)) { Hit h = new Hit(AREA, "area:" + w.id); h.win = w; return h; }
                Hit h = new Hit(BODY, "body:" + w.id); h.win = w; return h;
            }
        }
        return null;
    }

    private void front(Win w) {
        int i = wins.indexOf(w);
        if (i >= 0 && i < wins.size() - 1) { wins.remove(i); wins.add(w); }
    }

    private void openApp(int app, long now) {
        home = false;
        for (Win w : wins) if (w.app == app) { front(w); return; }
        int k = wins.size();
        Win w = new Win();
        w.id = ++wid; w.app = app;
        w.x = ((k % 3) - 1) * .05f; w.y = -.06f + (k % 3) * .03f;
        w.w = Math.min(SIZES[app][0], halfW * 2 / zoom - .08f); w.h = SIZES[app][1];
        w.born = now;
        wins.add(w);
    }

    private void closeWin(Win w, long now) {
        wins.remove(w);
        if (wins.isEmpty()) { home = true; homeT = now; }
    }

    private void press(Ptr p, Hit h, long now) {
        switch (h.type) {
            case ICON: synth.click(); openApp(h.app, now); break;
            case DOCK: synth.click(); home = !home; homeT = now; break;
            case CLOSE: synth.click(); closeWin(h.win, now); break;
            case GRAB: front(h.win); p.drag = h.win; p.dx = h.win.x - p.x; p.dy = h.win.y - p.y; break;
            case BTN: front(h.win); pressBtn(h.win, h.id, now); break;
            case AREA: {
                front(h.win);
                Stroke s = new Stroke(); s.c = h.win.pen;
                s.pts.add(new float[]{p.x - h.win.x, p.y - h.win.y});
                h.win.strokes.add(s);
                break;
            }
            default: if (h.win != null) front(h.win); break;
        }
    }

    private void pressBtn(Win w, String id, long now) {
        if (w.app == DESENHO) {
            synth.click();
            if (id.equals("clear")) w.strokes.clear();
            else w.pen = PEN[id.charAt(1) - '0'];
        } else if (w.app == MUSICA) {
            int i = id.charAt(1) - '0';
            synth.note(i);
            w.flash[i] = now;
        } else if (w.app == CAMERA) {
            Bitmap f = frame;
            if (f != null) {
                int th = Math.max(1, Math.round(320f * f.getHeight() / f.getWidth()));
                w.photos.add(Bitmap.createScaledBitmap(f, 320, th, true));
                if (w.photos.size() > 8) w.photos.remove(0);
            }
            w.shot = now;
            synth.shutter();
        }
    }

    private void hold(Ptr p) {
        if (p.drag != null) {
            p.drag.x = Math.max(-1.2f, Math.min(1.2f, p.x + p.dx));
            p.drag.y = Math.max(-.55f, Math.min(.5f, p.y + p.dy));
            return;
        }
        if (p.hit != null && p.hit.type == AREA && wins.contains(p.hit.win)) {
            Win w = p.hit.win;
            if (w.strokes.isEmpty()) return;
            Stroke s = w.strokes.get(w.strokes.size() - 1);
            float[] l = s.pts.get(s.pts.size() - 1);
            float x = p.x - w.x, y = p.y - w.y;
            if (Math.hypot(x - l[0], y - l[1]) > .002f && s.pts.size() < 1500) s.pts.add(new float[]{x, y});
        }
    }

    private void release(Ptr p) { p.drag = null; p.hit = null; p.down = false; }

    private void feed(Ptr p, float x, float y, boolean down, long now) {
        p.exists = true; p.x = x; p.y = y;
        if (down && !p.down) { p.down = true; p.hit = hitTest(x, y); if (p.hit != null) press(p, p.hit, now); }
        else if (down) hold(p);
        else if (p.down) release(p);
    }

    private boolean handActive(int i, long now) { return hands[i].init && now - hands[i].last < 300; }

    private void updateHands(long now) {
        if (rawStamp == lastStamp) return;
        lastStamp = rawStamp;
        float[][] raw = rawHands;
        for (int i = 0; i < raw.length && i < 2; i++) {
            Hand h = hands[i];
            float[] r = raw[i];
            boolean snap = !handActive(i, now);
            for (int j = 0; j < 21; j++) {
                float tx = x0 + r[2 * j] * dw, ty = y0 + r[2 * j + 1] * dh;
                if (snap) { h.x[j] = tx; h.y[j] = ty; }
                else { h.x[j] += (tx - h.x[j]) * .6f; h.y[j] += (ty - h.y[j]) * .6f; }
            }
            if (snap) h.down = false;
            h.init = true; h.last = now;
            float size = (float) Math.hypot(h.x[0] - h.x[9], h.y[0] - h.y[9]);
            if (size < 1e-4f) size = .1f;
            float ratio = (float) Math.hypot(h.x[4] - h.x[8], h.y[4] - h.y[8]) / size;
            if (h.down ? ratio > .45f : ratio < .28f) h.down = !h.down;   // pinça com folga
            h.cx = (h.x[4] + h.x[8]) / 2; h.cy = (h.y[4] + h.y[8]) / 2;
        }
    }

    private void updatePointers(long now) {
        for (int i = 0; i < 2; i++) {
            if (handActive(i, now)) feed(ptrs[i], hands[i].cx, hands[i].cy, hands[i].down, now);
            else { hands[i].init = false; if (ptrs[i].exists) { release(ptrs[i]); ptrs[i].exists = false; } }
        }
        Ptr t = ptrs[2];
        if (tActive) {
            feed(t, tX, tY, tDown, now);
            if (tRelease) { tDown = false; tRelease = false; tEnd = true; }
            else if (tEnd) { tActive = false; tEnd = false; }
        } else if (t.exists) { release(t); t.exists = false; }

        hov.clear();
        for (Ptr p : ptrs) {
            if (!p.exists) continue;
            Hit h = (p.down && p.hit != null) ? p.hit : hitTest(p.x, p.y);
            if (h != null) hov.add(h.key);
            if (p.drag != null) hov.add("grab:" + p.drag.id);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float px = e.getX(), py = e.getY();
        int eye = (stereo && px > getWidth() / 2f) ? 1 : 0;
        float off = stereo ? (eye == 1 ? ipd : -ipd) : 0;
        if (eh > 0) {
            tX = (px - eye * ew - ew / 2 - off) / (eh * zoom);
            tY = (py - eh / 2) / (eh * zoom);
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: tActive = true; tDown = true; tRelease = false; tEnd = false; break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: tRelease = true; break;
            default: break;
        }
        return true;
    }

    /* ================= apps ================= */

    private void updateBubbles(Win w, Parts P, float dt, List<float[]> tips) {
        RectF C = P.content;
        float L = C.left - w.x, T = C.top - w.y, R = C.right - w.x, B = C.bottom - w.y;
        w.t += dt;
        if (w.t > .5f && w.bubbles.size() < 9) {
            w.t = 0;
            Bubble b = new Bubble();
            b.r = .022f + rnd.nextFloat() * .022f;
            b.x = L + b.r + rnd.nextFloat() * (C.width() - 2 * b.r);
            b.y = B - b.r;
            b.vy = -(.045f + rnd.nextFloat() * .06f);
            b.ph = rnd.nextFloat() * 6.28f;
            w.bubbles.add(b);
        }
        Iterator<Bubble> it = w.bubbles.iterator();
        while (it.hasNext()) {
            Bubble b = it.next();
            b.y += b.vy * dt; b.ph += dt * 2; b.dx = (float) Math.sin(b.ph) * .01f;
            if (b.y - b.r < T) { it.remove(); continue; }
            for (float[] tp : tips) {
                float tx = tp[0] - w.x, ty = tp[1] - w.y;
                if (tx < L || tx > R || ty < T || ty > B) continue;
                if (Math.hypot(tx - (b.x + b.dx), ty - b.y) < b.r + .01f) {
                    it.remove();
                    w.score++;
                    synth.pop();
                    for (int k = 0; k < 9; k++) {
                        double a = k / 9.0 * Math.PI * 2;
                        Part q = new Part();
                        q.x = b.x + b.dx; q.y = b.y;
                        q.vx = (float) Math.cos(a) * .18f; q.vy = (float) Math.sin(a) * .18f; q.life = .4f;
                        w.parts.add(q);
                    }
                    break;
                }
            }
        }
        Iterator<Part> ip = w.parts.iterator();
        while (ip.hasNext()) {
            Part q = ip.next();
            q.x += q.vx * dt; q.y += q.vy * dt; q.life -= dt;
            if (q.life <= 0) ip.remove();
        }
    }

    /* ================= desenho ================= */

    private static int withAlpha(int color, float a) {
        int al = Math.max(0, Math.min(255, Math.round(a * 255)));
        return (color & 0x00FFFFFF) | (al << 24);
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private void txt(String s, float x, float y, float size, int color, Paint.Align align, Typeface tf) {
        c.save();
        c.translate(x, y);
        float k = size / 100f;
        c.scale(k, k);
        text.setTypeface(tf);
        text.setTextSize(100);
        text.setTextAlign(align);
        text.setColor(color);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(s, 0, -(fm.ascent + fm.descent) / 2, text);
        c.restore();
    }

    private void glass(RectF r, float rad, boolean hl) {
        fill.setColor(hl ? 0xAD3E4150 : 0x991C1E28);
        c.drawRoundRect(r, rad, rad, fill);
        stroke.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                new int[]{0x61FFFFFF, 0x0DFFFFFF, 0x29FFFFFF}, new float[]{0, .45f, 1}, Shader.TileMode.CLAMP));
        stroke.setStrokeWidth(.0022f);
        stroke.setColor(Color.WHITE);
        c.drawRoundRect(r, rad, rad, stroke);
        stroke.setShader(null);
    }

    private void circle(float x, float y, float r, int color) {
        fill.setColor(color);
        c.drawCircle(x, y, r, fill);
    }

    private void ring(float x, float y, float r, float w, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(w);
        c.drawCircle(x, y, r, stroke);
    }

    private void clipRound(RectF r, float rad) {
        Path p = new Path();
        p.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(p);
    }

    private void drawCover(Bitmap f, RectF dst) {
        if (f == null) return;
        float vw = f.getWidth(), vh = f.getHeight(), ta = dst.width() / dst.height(), va = vw / vh;
        Rect src;
        if (va > ta) { int sw = Math.round(vh * ta); int sx = Math.round((vw - sw) / 2); src = new Rect(sx, 0, sx + sw, (int) vh); }
        else { int sh = Math.round(vw / ta); int sy = Math.round((vh - sh) / 2); src = new Rect(0, sy, (int) vw, sy + sh); }
        c.drawBitmap(f, src, dst, bmp);
    }

    private void glyph(int id, float x, float y, float r) {
        c.save();
        c.translate(x, y);
        c.scale(r, r);
        stroke.setColor(Color.WHITE);
        stroke.setStrokeWidth(.09f);
        fill.setColor(Color.WHITE);
        Path p = new Path();
        switch (id) {
            case RELOGIO:
                c.drawCircle(0, 0, .5f, stroke);
                p.moveTo(0, 0); p.lineTo(0, -.32f); p.moveTo(0, 0); p.lineTo(.24f, .1f);
                c.drawPath(p, stroke);
                break;
            case DESENHO:
                p.moveTo(-.5f, .25f); p.cubicTo(-.25f, -.55f, 0, .55f, .2f, -.1f); p.quadTo(.32f, -.42f, .5f, -.3f);
                c.drawPath(p, stroke);
                break;
            case BOLHAS:
                c.drawCircle(-.16f, .12f, .3f, stroke);
                c.drawCircle(.26f, -.2f, .19f, stroke);
                c.drawCircle(.3f, .3f, .12f, stroke);
                break;
            case MUSICA:
                c.drawOval(new RectF(-.4f, .18f, -.08f, .42f), fill);
                c.drawOval(new RectF(.12f, .08f, .44f, .32f), fill);
                p.moveTo(-.1f, .27f); p.lineTo(-.1f, -.4f); p.lineTo(.42f, -.5f); p.lineTo(.42f, .17f);
                c.drawPath(p, stroke);
                break;
            case CAMERA:
                c.drawRoundRect(new RectF(-.5f, -.28f, .5f, .38f), .14f, .14f, stroke);
                c.drawCircle(0, .05f, .19f, stroke);
                p.moveTo(-.18f, -.28f); p.lineTo(-.1f, -.42f); p.lineTo(.1f, -.42f); p.lineTo(.18f, -.28f);
                c.drawPath(p, stroke);
                break;
            default: break;
        }
        c.restore();
    }

    private void drawHome(long now) {
        float a = ease((now - homeT) / 320f);
        int save = c.saveLayerAlpha(-2, -1, 2, 1, Math.round(255 * a));
        txt("Seus apps", 0, -.16f, .03f, withAlpha(Color.WHITE, .92f), Paint.Align.CENTER, tfMedium);
        for (int i = 0; i < 5; i++) {
            float ix = iconX(i);
            boolean h = hov.contains("icon:" + i);
            float r = ICON_R * (.85f + .15f * a) * (h ? 1.12f : 1f);
            if (h) circle(ix, ICON_Y, r * 1.28f, 0x24FFFFFF);
            grad.setShader(new LinearGradient(0, ICON_Y - r, 0, ICON_Y + r, COLS[i][0], COLS[i][1], Shader.TileMode.CLAMP));
            c.drawCircle(ix, ICON_Y, r, grad);
            grad.setShader(null);
            fill.setColor(0x33FFFFFF);
            c.drawOval(new RectF(ix - r * .62f, ICON_Y - r * .75f, ix + r * .62f, ICON_Y - r * .15f), fill);
            glyph(i, ix, ICON_Y, r);
            txt(NAMES[i], ix, ICON_Y + ICON_R + .032f, .02f, withAlpha(Color.WHITE, .9f), Paint.Align.CENTER, tfRegular);
        }
        c.restoreToCount(save);
    }

    private void drawDock() {
        boolean h = hov.contains("dock");
        float r = DOCK_R * (h ? 1.15f : 1f);
        glass(new RectF(DOCK_X - r, DOCK_Y - r, DOCK_X + r, DOCK_Y + r), r, h);
        int[][] d = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] q : d) circle(DOCK_X + q[0] * r * .28f, DOCK_Y + q[1] * r * .28f, r * .13f, Color.WHITE);
        if (h) txt(home ? "Fechar início" : "Início", DOCK_X, DOCK_Y - r - .022f, .016f, Color.WHITE, Paint.Align.CENTER, tfMedium);
    }

    private void drawWindow(Win w, long now) {
        Parts P = parts(w);
        float a = ease((now - w.born) / 260f), s = .92f + .08f * a;
        int alpha = Math.round(255 * (home ? .22f : 1f) * a);
        RectF B = P.body;
        int save = alpha < 255 ? c.saveLayerAlpha(B.left - .2f, B.top - .2f, B.right + .2f, B.bottom + .2f, alpha) : c.save();
        c.translate(w.x, w.y); c.scale(s, s); c.translate(-w.x, -w.y);

        glass(B, .032f, false);
        txt(NAMES[w.app], B.left + .025f, B.top + .036f, .022f, 0xE0FFFFFF, Paint.Align.LEFT, tfMedium);

        switch (w.app) {
            case RELOGIO: drawClock(w, P); break;
            case DESENHO: drawPaint(w, P); break;
            case BOLHAS: drawBubbles(w, P); break;
            case MUSICA: drawMusic(w, P, now); break;
            case CAMERA: drawCamera(w, P, now); break;
            default: break;
        }

        boolean gh = hov.contains("grab:" + w.id);
        fill.setColor(gh ? 0xF2FFFFFF : 0x80FFFFFF);
        c.drawRoundRect(P.grab, .01f, .01f, fill);

        boolean ch = hov.contains("close:" + w.id);
        float cr = P.cr * (ch ? 1.2f : 1f);
        circle(P.cx, P.cy, cr, ch ? 0xF2FFFFFF : 0xB8282A36);
        stroke.setColor(ch ? 0xFF111111 : 0xD9FFFFFF);
        stroke.setStrokeWidth(.0026f);
        float q = cr * .42f;
        c.drawLine(P.cx - q, P.cy - q, P.cx + q, P.cy + q, stroke);
        c.drawLine(P.cx + q, P.cy - q, P.cx - q, P.cy + q, stroke);

        c.restoreToCount(save);
    }

    private void drawClock(Win w, Parts P) {
        Date d = new Date();
        RectF B = P.body;
        txt(fmtTime.format(d), w.x, B.top + .128f, .11f, Color.WHITE, Paint.Align.CENTER, tfLight);
        txt(fmtDate.format(d), w.x, B.top + .198f, .021f, withAlpha(Color.WHITE, .72f), Paint.Align.CENTER, tfRegular);

        {
            long ms = System.currentTimeMillis() % 60000;
            float f = ms / 60000f;
            float bw = B.width() - .12f, bx = w.x - bw / 2, by = B.bottom - .034f;
            fill.setColor(0x26FFFFFF);
            c.drawRoundRect(new RectF(bx, by, bx + bw, by + .006f), .003f, .003f, fill);
            fill.setColor(0xFFFFB36B);
            c.drawRoundRect(new RectF(bx, by, bx + Math.max(.006f, bw * f), by + .006f), .003f, .003f, fill);
        }
    }

    private void drawPaint(Win w, Parts P) {
        RectF C = P.content, B = P.body;
        fill.setColor(0x3D000000);
        c.drawRoundRect(C, .018f, .018f, fill);
        c.save();
        clipRound(C, .018f);
        c.translate(w.x, w.y);
        stroke.setStrokeWidth(.006f);
        for (Stroke s : w.strokes) {
            stroke.setColor(s.c);
            Path p = new Path();
            for (int i = 0; i < s.pts.size(); i++) {
                float[] q = s.pts.get(i);
                if (i == 0) p.moveTo(q[0], q[1]); else p.lineTo(q[0], q[1]);
            }
            if (s.pts.size() == 1) { float[] q = s.pts.get(0); p.lineTo(q[0] + .0001f, q[1]); }
            c.drawPath(p, stroke);
        }
        c.restore();
        if (w.strokes.isEmpty())
            txt("Faça a pinça e desenhe no ar", w.x, C.centerY(), .022f, withAlpha(Color.WHITE, .5f), Paint.Align.CENTER, tfRegular);
        for (Btn b : buttons(w, P)) {
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            if (b.circle) {
                int col = PEN[b.id.charAt(1) - '0'];
                circle(b.x, b.y, b.r * (h ? 1.2f : 1f), col);
                if (w.pen == col) ring(b.x, b.y, b.r + .005f, .002f, 0xE6FFFFFF);
            } else {
                glass(b.rect, b.rect.height() / 2, h);
                txt("Limpar", b.rect.centerX(), b.rect.centerY(), .016f, Color.WHITE, Paint.Align.CENTER, tfMedium);
            }
        }
    }

    private void drawBubbles(Win w, Parts P) {
        RectF C = P.content, B = P.body;
        txt("Pontos: " + w.score, B.right - .025f, B.top + .036f, .02f, withAlpha(Color.WHITE, .85f), Paint.Align.RIGHT, tfMedium);
        fill.setColor(0x38001428);
        c.drawRoundRect(C, .018f, .018f, fill);
        c.save();
        clipRound(C, .018f);
        c.translate(w.x, w.y);
        for (Bubble b : w.bubbles) {
            float x = b.x + b.dx;
            grad.setShader(new RadialGradient(x - b.r * .35f, b.y - b.r * .35f, b.r * 1.2f,
                    new int[]{0x8CFFFFFF, 0x1A48DBFB, 0xA6AAF0FF}, new float[]{0, .6f, 1}, Shader.TileMode.CLAMP));
            c.drawCircle(x, b.y, b.r, grad);
            grad.setShader(null);
            ring(x, b.y, b.r, .0015f, 0x73FFFFFF);
        }
        for (Part q : w.parts) circle(q.x, q.y, .004f, withAlpha(0xFFBEF5FF, q.life / .4f));
        c.restore();
        if (w.score == 0)
            txt("Estoure as bolhas com a ponta do indicador", w.x, C.top + .03f, .018f, withAlpha(Color.WHITE, .55f), Paint.Align.CENTER, tfRegular);
    }

    private void drawMusic(Win w, Parts P, long now) {
        List<Btn> bs = buttons(w, P);
        for (int i = 0; i < bs.size(); i++) {
            Btn b = bs.get(i);
            float f = Math.max(0, 1 - (now - w.flash[i]) / 450f);
            float[] hsv = {(i * 48 + 340) % 360, .72f, .62f + f * .35f};
            fill.setColor(withAlpha(Color.HSVToColor(hsv), .5f + f * .45f));
            c.drawRoundRect(b.rect, .02f, .02f, fill);
            if (hov.contains("btn:" + w.id + ":" + b.id)) {
                stroke.setColor(0xD9FFFFFF); stroke.setStrokeWidth(.0025f);
                c.drawRoundRect(b.rect, .02f, .02f, stroke);
            }
            txt(NOTE_N[i], b.rect.centerX(), b.rect.centerY(), .032f, Color.WHITE, Paint.Align.CENTER, tfMedium);
        }
    }

    private void drawCamera(Win w, Parts P, long now) {
        RectF C = P.content;
        RectF pr = new RectF(C.left, C.top, C.right - .11f, C.bottom);
        c.save();
        clipRound(pr, .018f);
        fill.setColor(0xFF000000);
        c.drawRect(pr, fill);
        drawCover(frame, pr);
        float f = Math.max(0, 1 - (now - w.shot) / 250f);
        if (f > 0) { fill.setColor(withAlpha(Color.WHITE, f * .8f)); c.drawRect(pr, fill); }
        int start = Math.max(0, w.photos.size() - 4);
        for (int i = start; i < w.photos.size(); i++) {
            Bitmap ph = w.photos.get(i);
            float tw = .075f, th = tw * ph.getHeight() / ph.getWidth();
            float x = pr.left + .012f + (i - start) * (tw + .01f), y = pr.bottom - th - .012f;
            RectF r = new RectF(x, y, x + tw, y + th);
            c.drawBitmap(ph, null, r, bmp);
            stroke.setColor(0xE6FFFFFF); stroke.setStrokeWidth(.002f);
            c.drawRect(r, stroke);
        }
        c.restore();
        Btn b = buttons(w, P).get(0);
        boolean h = hov.contains("btn:" + w.id + ":shot");
        ring(b.x, b.y, b.r, .004f, Color.WHITE);
        circle(b.x, b.y, b.r * (h ? .82f : .74f), Color.WHITE);
        String label = w.photos.size() + (w.photos.size() == 1 ? " foto" : " fotos");
        txt(label, b.x, b.y + b.r + .025f, .015f, withAlpha(Color.WHITE, .7f), Paint.Align.CENTER, tfRegular);
    }

    private void drawHands(long now) {
        for (int i = 0; i < 2; i++) {
            if (!handActive(i, now)) continue;
            Hand h = hands[i];
            if (skel) {
                Path p = new Path();
                for (int[] e : CON) { p.moveTo(h.x[e[0]], h.y[e[0]]); p.lineTo(h.x[e[1]], h.y[e[1]]); }
                stroke.setColor(0x527CC4FF); stroke.setStrokeWidth(.012f);
                c.drawPath(p, stroke);
                stroke.setColor(0xD9FFFFFF); stroke.setStrokeWidth(.0035f);
                c.drawPath(p, stroke);
                for (int j = 0; j < 21; j++) circle(h.x[j], h.y[j], .005f, Color.WHITE);
            } else {
                circle(h.x[4], h.y[4], .007f, 0xE6FFFFFF);
                circle(h.x[8], h.y[8], .007f, 0xE6FFFFFF);
            }
        }
        for (Ptr p : ptrs) {
            if (!p.exists) continue;
            if (p.down) circle(p.x, p.y, .012f, 0xF2FFFFFF);
            ring(p.x, p.y, p.down ? .02f : .017f, .003f, p.down ? 0xF27CC4FF : 0xE6FFFFFF);
        }
    }

    private void drawHud(long now) {
        int count = 0;
        for (int i = 0; i < 2; i++) if (handActive(i, now)) count++;
        float top = -.5f / zoom + .035f;
        String label = !modelReady ? "Carregando reconhecimento de mãos…" : ("● " + count + (count == 1 ? " mão" : " mãos"));
        txt(label, 0, top, .016f, count > 0 ? 0xCC9FE8B0 : 0x99FFFFFF, Paint.Align.CENTER, tfMedium);
        String err = error;
        if (err != null) txt(err, 0, top + .032f, .015f, 0xFFFF9A9A, Paint.Align.CENTER, tfMedium);
        if (frame == null && err == null)
            txt("Abrindo a câmera…", 0, -.08f, .024f, 0xFFFFD27A, Paint.Align.CENTER, tfMedium);
        else if (modelReady && count == 0 && !tActive)
            txt("Mostre a mão para a câmera · junte polegar e indicador para tocar", 0, .3f, .018f, 0xBFFFFFFF, Paint.Align.CENTER, tfRegular);
    }

    private void drawWorld(long now, int shift) {
        Bitmap f = frame;
        if (f != null) c.drawBitmap(f, null, new RectF(x0, y0, x0 + dw, y0 + dh), bmp);
        fill.setColor(home ? 0x4D000000 : 0x1A000000);
        c.drawRect(-3, -2, 3, 2, fill);

        c.save();
        c.translate(shift * depth, 0);
        for (Win w : wins) drawWindow(w, now);
        drawDock();
        drawHud(now);
        if (home) { c.translate(shift * depth * .6f, 0); drawHome(now); }
        c.restore();

        drawHands(now);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        c = canvas;
        long now = SystemClock.uptimeMillis();
        if (lastT == 0) { lastT = now; homeT = now; }
        float dt = Math.min(.05f, (now - lastT) / 1000f);
        lastT = now;
        canvas.drawColor(Color.BLACK);
        if (getWidth() == 0 || getHeight() == 0) { postInvalidateOnAnimation(); return; }

        try {
            geom();
            updateHands(now);
            updatePointers(now);
            if (!home) {
                ArrayList<float[]> tips = new ArrayList<>();
                for (int i = 0; i < 2; i++) if (handActive(i, now)) tips.add(new float[]{hands[i].x[8], hands[i].y[8]});
                if (tActive && tDown) tips.add(new float[]{tX, tY});
                for (Win w : new ArrayList<>(wins)) if (w.app == BOLHAS) updateBubbles(w, parts(w), dt, tips);
            }

            for (int e = 0; e < n; e++) {
                float ex = e * ew;
                int shift = n == 2 ? (e == 0 ? 1 : -1) : 0;
                float off = n == 2 ? (e == 0 ? -ipd : ipd) : 0;
                c.save();
                c.clipRect(ex, 0, ex + ew, eh);
                c.translate(ex + ew / 2 + off, eh / 2);
                float sc = eh * zoom;
                c.scale(sc, sc);
                drawWorld(now, shift);
                c.restore();
            }
            if (n == 2) { fill.setColor(Color.BLACK); canvas.drawRect(ew - 1.5f, 0, ew + 1.5f, eh, fill); }
        } catch (Throwable t) {
            error = "Tela: " + t.getMessage();
        }
        postInvalidateOnAnimation();
    }

    /* ================= sons ================= */

    static class Synth {
        static final int SR = 22050;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final short[][] notes = new short[6][];
        private final short[] click, shutter;
        private final short[][] pops = new short[3][];
        private int popIdx = 0;

        Synth() {
            for (int i = 0; i < 6; i++) notes[i] = make(NOTE_F[i], .6, 1, .35, 0);
            click = make(1100, .05, 0, .15, 0);
            shutter = make(1500, .09, 2, .1, 300);
            for (int i = 0; i < 3; i++) pops[i] = make(500 + i * 150, .12, 1, .3, 1400);
        }

        private static short[] make(double f, double dur, int type, double vol, double f2) {
            int len = (int) (SR * dur);
            short[] b = new short[len];
            double ph = 0;
            for (int i = 0; i < len; i++) {
                double t = i / (double) SR;
                double fr = f2 > 0 ? f * Math.pow(f2 / f, t / dur) : f;
                ph += 2 * Math.PI * fr / SR;
                double v;
                if (type == 0) v = Math.sin(ph);
                else if (type == 1) v = 2 / Math.PI * Math.asin(Math.sin(ph));
                else v = Math.sin(ph) > 0 ? 1 : -1;
                double env = Math.min(1, t / .01) * Math.exp(-5 * t / dur);
                b[i] = (short) (v * env * vol * 32767);
            }
            return b;
        }

        @SuppressWarnings("deprecation")
        private void play(short[] b) {
            try {
                final AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, SR, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, b.length * 2, AudioTrack.MODE_STATIC);
                at.write(b, 0, b.length);
                at.play();
                handler.postDelayed(() -> { try { at.release(); } catch (Exception ignored) { } }, b.length * 1000L / SR + 300);
            } catch (Exception ignored) { }
        }

        void note(int i) { play(notes[i]); }
        void click() { play(click); }
        void shutter() { play(shutter); }
        void pop() { play(pops[popIdx++ % 3]); }
    }
}
