package br.visaobox.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;
import java.util.Random;

/**
 * Renderização binocular: cada olho é desenhado numa imagem própria (com paralaxe),
 * depois a imagem é deformada (distorção de barril) para compensar a lente do VR box.
 * O centro de cada olho fica a IPD/2 do centro da tela, em milímetros reais.
 */
public class VrView extends View {

    static final int MESH = 24;

    final Config cfg;
    final HeadTracker head;
    final SpatialUi ui;
    private final Gfx g = new Gfx();

    // dados vindos da câmera e dos modelos (outras threads)
    public volatile Bitmap frame;
    public volatile float[][] rawHands = new float[0][];
    public volatile long rawStamp = 0;
    public volatile Bitmap segMask, segCut;
    public volatile boolean handReady, segReady;
    public volatile String error, camError;
    public volatile String camLabel = "";
    volatile boolean calibration = false;

    // geometria dos olhos (pixels da tela)
    private int n = 2;
    private final float[] eyeCX = new float[2], eyeCY = new float[2];
    private float eyeW, eyeH;
    private final Bitmap[] eyeBmp = new Bitmap[2];
    private final Canvas[] eyeCanvas = new Canvas[2];
    private final float[][] mesh = new float[2][];
    private String meshKey = "";

    private final Paint outPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint camPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint vigPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String outKey = "", camKey = "";

    // cabeça suavizada
    private float sYaw, sPitch, sRoll;
    private long lastT = 0;
    private int fpsFrames; private long fpsT; private int fps;

    // ambiente virtual
    private final float[][] stars = new float[160][3];

    // mapeamento da câmera na cena
    private float camX0, camY0, camDW, camDH;

    public VrView(Context ctx, Config cfg, HeadTracker head, Host host) {
        super(ctx);
        this.cfg = cfg;
        this.head = head;
        this.ui = new SpatialUi(cfg, host, g);
        Random r = new Random(7);
        for (float[] s : stars) {
            s[0] = (float) (r.nextFloat() * Math.PI * 2 - Math.PI);
            s[1] = (float) Math.asin(r.nextFloat() * .95f + .02f);
            s[2] = .4f + r.nextFloat() * .6f;
        }
        tintPaint.setColorFilter(new PorterDuffColorFilter(0xFF7CC4FF, PorterDuff.Mode.SRC_IN));
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
    }

    /* ================= geometria ================= */

    private void computeGeometry() {
        int W = getWidth(), H = getHeight();
        float pxPerMm = getResources().getDisplayMetrics().xdpi / 25.4f;
        if (cfg.stereo) {
            n = 2;
            float S = Math.min(W / 2f, H) * cfg.eyeScale;
            eyeW = eyeH = S;
            float half = cfg.ipdMm * pxPerMm / 2f;
            half = Gfx.clamp(half, S * .3f, W / 2f);
            float ax = cfg.alignXmm * pxPerMm, ay = cfg.alignYmm * pxPerMm;
            eyeCX[0] = W / 2f - half + ax;
            eyeCX[1] = W / 2f + half + ax;
            eyeCY[0] = eyeCY[1] = H / 2f + ay;
        } else {
            n = 1;
            eyeW = W; eyeH = H;
            eyeCX[0] = W / 2f; eyeCY[0] = H / 2f;
        }
        int bw = Math.max(64, Math.round(eyeW * cfg.resScale)), bh = Math.max(64, Math.round(eyeH * cfg.resScale));
        for (int e = 0; e < n; e++) {
            if (eyeBmp[e] == null || eyeBmp[e].getWidth() != bw || eyeBmp[e].getHeight() != bh) {
                eyeBmp[e] = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                eyeCanvas[e] = new Canvas(eyeBmp[e]);
            }
        }
        String key = n + ":" + eyeW + ":" + eyeCX[0] + ":" + eyeCX[1] + ":" + eyeCY[0] + ":" + cfg.k1 + ":" + cfg.k2 + ":" + cfg.distortion;
        if (!key.equals(meshKey)) { meshKey = key; buildMesh(); }
        ui.eyeHalfW = eyeW / eyeH / 2f;
    }

    private boolean distorted() { return cfg.stereo && cfg.distortion && (cfg.k1 > 0 || cfg.k2 > 0); }

    private void buildMesh() {
        float aspect = eyeW / eyeH, scale = 1 + cfg.k1 + cfg.k2;
        for (int e = 0; e < n; e++) {
            float[] v = new float[(MESH + 1) * (MESH + 1) * 2];
            int k = 0;
            for (int j = 0; j <= MESH; j++) {
                for (int i = 0; i <= MESH; i++) {
                    float x = (i / (float) MESH * 2 - 1) * aspect, y = j / (float) MESH * 2 - 1;
                    float fct = 1;
                    if (distorted()) {
                        float r2 = x * x + y * y;
                        fct = scale / (1 + cfg.k1 * r2 + cfg.k2 * r2 * r2);
                    }
                    v[k++] = eyeCX[e] + x * fct * eyeH / 2;
                    v[k++] = eyeCY[e] + y * fct * eyeH / 2;
                }
            }
            mesh[e] = v;
        }
    }

    /** Tela (px) -> coordenadas da cena, desfazendo a distorção da lente. */
    private float[] screenToScene(float px, float py) {
        int e = (n == 2 && px > getWidth() / 2f) ? 1 : 0;
        float dx = (px - eyeCX[e]) / (eyeH / 2), dy = (py - eyeCY[e]) / (eyeH / 2);
        if (distorted()) {
            float rd = (float) Math.hypot(dx, dy);
            if (rd > 1e-5f) {
                float scale = 1 + cfg.k1 + cfg.k2, rs = rd;
                for (int it = 0; it < 10; it++) { float r2 = rs * rs; rs = rd * (1 + cfg.k1 * r2 + cfg.k2 * r2 * r2) / scale; }
                dx *= rs / rd; dy *= rs / rd;
            }
        }
        return new float[]{dx * .5f, dy * .5f};
    }

    /* ================= cores ================= */

    private static ColorMatrix adjust(float bri, float con, float sat) {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(sat);
        float t = (1 - con) * 128f + bri * 255f;
        cm.postConcat(new ColorMatrix(new float[]{con, 0, 0, 0, t, 0, con, 0, 0, t, 0, 0, con, 0, t, 0, 0, 0, 1, 0}));
        return cm;
    }

    private void updatePaints() {
        String k = cfg.brightness + ":" + cfg.contrast + ":" + cfg.saturation;
        if (!k.equals(outKey)) {
            outKey = k;
            boolean neutral = cfg.brightness == 0 && cfg.contrast == 1 && cfg.saturation == 1;
            outPaint.setColorFilter(neutral ? null : new ColorMatrixColorFilter(adjust(cfg.brightness, cfg.contrast, cfg.saturation)));
        }
        String c = cfg.camBrightness + ":" + cfg.camContrast;
        if (!c.equals(camKey)) {
            camKey = c;
            boolean neutral = cfg.camBrightness == 0 && cfg.camContrast == 1;
            camPaint.setColorFilter(neutral ? null : new ColorMatrixColorFilter(adjust(cfg.camBrightness, cfg.camContrast, 1)));
        }
        camPaint.setAlpha(Math.round(255 * Gfx.clamp(cfg.camOpacity, 0, 1)));
    }

    /* ================= quadro ================= */

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        if (lastT == 0) { lastT = now; fpsT = now; }
        float dt = Math.min(.05f, (now - lastT) / 1000f);
        lastT = now;
        canvas.drawColor(Color.BLACK);
        if (getWidth() == 0 || getHeight() == 0) { postInvalidateOnAnimation(); return; }

        try {
            computeGeometry();
            updatePaints();
            updateHead(dt);
            mapCamera();
            ui.frame = frame;
            ui.camLabel = camLabel;
            ui.updateHands(rawHands, rawStamp, camX0, camDW, camY0, camDH, now);
            ui.update(now, dt);
            ui.status = statusLine();

            for (int e = 0; e < n; e++) renderEye(e, now);

            for (int e = 0; e < n; e++) {
                canvas.save();
                if (n == 2) canvas.clipRect(e == 0 ? 0 : getWidth() / 2f, 0, e == 0 ? getWidth() / 2f : getWidth(), getHeight());
                if (distorted()) canvas.drawBitmapMesh(eyeBmp[e], MESH, MESH, mesh[e], 0, null, 0, outPaint);
                else canvas.drawBitmap(eyeBmp[e], null, new RectF(eyeCX[e] - eyeW / 2, eyeCY[e] - eyeH / 2,
                        eyeCX[e] + eyeW / 2, eyeCY[e] + eyeH / 2), outPaint);
                if (n == 2 && cfg.vignette > 0) {
                    vigPaint.setShader(new RadialGradient(eyeCX[e], eyeCY[e], eyeH * .62f,
                            new int[]{0x00000000, 0x00000000, Gfx.a(0xFF000000, cfg.vignette)},
                            new float[]{0, .72f, 1}, Shader.TileMode.CLAMP));
                    canvas.drawRect(eyeCX[e] - eyeW, 0, eyeCX[e] + eyeW, getHeight(), vigPaint);
                }
                canvas.restore();
            }
            if (n == 2) {
                g.fill.setColor(0xFF2A2C33);
                canvas.drawRect(getWidth() / 2f - 1, getHeight() * .25f, getWidth() / 2f + 1, getHeight(), g.fill);
            }
        } catch (Throwable t) {
            error = "Tela: " + t.getClass().getSimpleName() + " " + t.getMessage();
        }
        fpsFrames++;
        if (now - fpsT > 1000) { fps = fpsFrames; fpsFrames = 0; fpsT = now; }
        postInvalidateOnAnimation();
    }

    private String statusLine() {
        String place = ui.cinema() ? "Cinema" : (cfg.placement == Config.HUD ? "HUD" : "Espacial");
        String track = !head.available ? "sem sensor de rotação" : (cfg.headTracking ? "cabeça ✓" : "cabeça desligada");
        int hc = 0;
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < 2; i++) if (ui.handActive(i, now)) hc++;
        String hands = !cfg.handInput ? "mãos desligadas" : (!handReady ? "carregando mãos…" : hc + (hc == 1 ? " mão" : " mãos"));
        return place + " · " + track + " · " + hands + " · " + fps + " fps";
    }

    private void updateHead(float dt) {
        boolean avail = head.available && head.hasData;
        boolean on = avail && cfg.headTracking;
        float ty = on ? head.relYaw() * cfg.headSens : 0;
        float tp = on ? head.pitch() * cfg.headSens : 0;
        float tr = on ? head.roll() : 0;
        float a = Gfx.clamp(1 - cfg.headSmooth * .9f, .08f, 1f);
        sYaw = HeadTracker.wrap(sYaw + HeadTracker.wrap(ty - sYaw) * a);
        sPitch += (tp - sPitch) * a;
        sRoll += (tr - sRoll) * a;
        ui.setHead(sYaw, sPitch, sRoll, avail);
    }

    private void mapCamera() {
        Bitmap f = frame;
        float vw = f != null ? f.getWidth() : 640, vh = f != null ? f.getHeight() : 480;
        float aspect = eyeW / eyeH;
        float s = Math.max(aspect / vw, 1f / vh) * cfg.camZoom;
        camDW = vw * s; camDH = vh * s;
        camX0 = -camDW / 2; camY0 = -camDH / 2;
    }

    /* ================= cada olho ================= */

    private void renderEye(int e, long now) {
        Canvas c = eyeCanvas[e];
        Bitmap b = eyeBmp[e];
        g.c = c;
        c.drawColor(Color.BLACK);
        c.save();
        c.translate(b.getWidth() / 2f, b.getHeight() / 2f);
        float sc = b.getHeight();
        c.scale(sc, sc);
        int sign = n == 2 ? (e == 0 ? 1 : -1) : 0;

        if (calibration) { drawCalibration(sign, e); c.restore(); return; }

        drawBackground(sign);
        Bitmap cut = segCut, mask = segMask;
        boolean seg = cfg.segMode != Config.SEG_OFF && cfg.background == Config.BG_CAMERA && cut != null && frame != null;
        if (seg) {
            ui.drawWindows(sign, 1, now);
            drawPerson(cut, mask);
            ui.drawWindows(sign, 2, now);
        } else ui.drawWindows(sign, 0, now);
        ui.drawHud(sign, now, error != null ? error : camError);
        c.restore();
    }

    private void drawBackground(int sign) {
        Canvas c = g.c;
        float hw = eyeW / eyeH / 2f + .05f;
        if (cfg.background == Config.BG_CAMERA) {
            Bitmap f = frame;
            g.fill.setColor(0xFF07080C);
            c.drawRect(-hw, -.55f, hw, .55f, g.fill);
            if (f != null) c.drawBitmap(f, null, new RectF(camX0, camY0, camX0 + camDW, camY0 + camDH), camPaint);
            else {
                // não simula o ambiente: sem imagem, mostra só o aviso
                g.txt(camError != null ? "Câmera indisponível" : "Abrindo a câmera…", 0, -.06f, .026f, 0xFFFFD27A, Paint.Align.CENTER, g.medium);
                g.txt("Use o fundo Virtual 3D ou Escuro em Ajustes", 0, -.02f, .018f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
            }
            if (ui.cinema()) { g.fill.setColor(0xB3000000); c.drawRect(-hw, -.55f, hw, .55f, g.fill); }
        } else if (cfg.background == Config.BG_VIRTUAL) {
            drawVirtual(sign, hw);
        } else {
            g.grad.setShader(new RadialGradient(0, .1f, .9f, 0xFF161A26, 0xFF030407, Shader.TileMode.CLAMP));
            c.drawRect(-hw, -.55f, hw, .55f, g.grad);
            g.grad.setShader(null);
        }
    }

    /** Ambiente virtual simples: céu estrelado e piso quadriculado em perspectiva (com paralaxe por olho). */
    private void drawVirtual(int sign, float hw) {
        Canvas c = g.c;
        boolean on = ui.spatial() || (cfg.headTracking && head.available && head.hasData);
        float yaw = on ? sYaw : 0, pitch = on ? sPitch : 0, roll = on ? sRoll : 0;
        float f = ui.f;
        c.save();
        c.rotate((float) -Math.toDegrees(roll));
        float horizon = f * (float) Math.tan(Gfx.clamp(pitch, -1.2f, 1.2f));
        float big = 2f;
        g.grad.setShader(new LinearGradient(0, horizon - .9f, 0, horizon, 0xFF050816, 0xFF1B2550, Shader.TileMode.CLAMP));
        c.drawRect(-big, -big, big, horizon, g.grad);
        g.grad.setShader(new LinearGradient(0, horizon, 0, horizon + .6f, 0xFF0E1324, 0xFF04050A, Shader.TileMode.CLAMP));
        c.drawRect(-big, horizon, big, big, g.grad);
        g.grad.setShader(null);
        for (float[] s : stars) {
            float ry = HeadTracker.wrap(s[0] - yaw);
            if (Math.abs(ry) > 1.2f) continue;
            float rp = pitch - s[1];
            if (Math.abs(rp) > 1.2f) continue;
            g.circle(f * (float) Math.tan(ry), f * (float) Math.tan(rp), .0022f * s[2], Gfx.a(0xFFFFFFFF, .5f + .5f * s[2]));
        }
        // piso
        float sx = (float) Math.sin(yaw), cx = (float) Math.cos(yaw);
        float sp = (float) Math.sin(pitch), cp = (float) Math.cos(pitch);
        float eo = -sign * cfg.ipdMm / 2000f * cfg.stereo3d;
        float ex = eo * cx, ey = eo * -sx;
        Path near = new Path(), far = new Path();
        for (int k = -18; k <= 18; k++) {
            for (int dir = 0; dir < 2; dir++) {
                boolean started = false;
                Path p = Math.abs(k) <= 5 ? near : far;
                for (int t = -18; t <= 18; t++) {
                    float wx = dir == 0 ? k : t, wy = dir == 0 ? t : k;
                    float rx = wx - ex, ryy = wy - ey;
                    float xc = rx * cx - ryy * sx, zc = rx * sx + ryy * cx, yc = -1.6f;
                    float y2 = yc * cp - zc * sp, z2 = yc * sp + zc * cp;
                    if (z2 < .2f) { started = false; continue; }
                    float px = f * xc / z2, py = -f * y2 / z2;
                    if (Math.abs(px) > 3 || Math.abs(py) > 3) { started = false; continue; }
                    if (!started) { p.moveTo(px, py); started = true; } else p.lineTo(px, py);
                }
            }
        }
        g.stroke.setStrokeWidth(.0016f);
        g.stroke.setColor(0x2648DBFB);
        c.drawPath(far, g.stroke);
        g.stroke.setColor(0x6648DBFB);
        c.drawPath(near, g.stroke);
        g.grad.setShader(new LinearGradient(0, horizon - .04f, 0, horizon + .04f, new int[]{0x0048DBFB, 0x3348DBFB, 0x0048DBFB}, null, Shader.TileMode.CLAMP));
        c.drawRect(-big, horizon - .04f, big, horizon + .04f, g.grad);
        g.grad.setShader(null);
        c.restore();
    }

    private void drawPerson(Bitmap cut, Bitmap mask) {
        Canvas c = g.c;
        RectF r = new RectF(camX0, camY0, camX0 + camDW, camY0 + camDH);
        if (cfg.segMode == Config.SEG_CONTORNO && mask != null) {
            float gx = r.width() * .012f, gy = r.height() * .012f;
            tintPaint.setAlpha(230);
            c.drawBitmap(mask, null, new RectF(r.left - gx, r.top - gy, r.right + gx, r.bottom + gy), tintPaint);
        }
        c.drawBitmap(cut, null, r, camPaint);
        if (cfg.segMode == Config.SEG_SILHUETA && mask != null) {
            tintPaint.setAlpha(120);
            c.drawBitmap(mask, null, r, tintPaint);
        }
    }

    /** Tela de calibração: grade, círculos, cruz central e alvo de fusão com a profundidade das janelas. */
    private void drawCalibration(int sign, int eye) {
        Canvas c = g.c;
        float hw = eyeW / eyeH / 2f + .05f;
        g.fill.setColor(0xFF0A0C12);
        c.drawRect(-hw, -.55f, hw, .55f, g.fill);
        for (int i = -10; i <= 10; i++) {
            float v = i * .05f;
            int col = i % 2 == 0 ? 0x40FFFFFF : 0x1AFFFFFF;
            g.line(v, -.5f, v, .5f, .0015f, col);
            g.line(-hw, v, hw, v, .0015f, col);
        }
        for (int i = 1; i <= 5; i++) g.ring(0, 0, i * .1f, .002f, 0x667CC4FF);
        g.line(-.04f, 0, .04f, 0, .003f, Color.WHITE);
        g.line(0, -.04f, 0, .04f, .003f, Color.WHITE);
        g.txt(n == 2 ? (eye == 0 ? "OLHO ESQUERDO" : "OLHO DIREITO") : "TELA ÚNICA", 0, -.42f, .022f, 0xFF9FD3FF, Paint.Align.CENTER, g.medium);
        // marcas nas bordas: todas devem estar visíveis pela lente
        float[][] marks = {{0, -.48f}, {0, .48f}, {-.46f, 0}, {.46f, 0}};
        for (float[] m : marks) g.circle(m[0], m[1], .012f, 0xFFFFD27A);
        // alvo de fusão a 1,5 m
        float s = ui.shift(sign, 1.5f);
        RectF t = new RectF(-.09f + s, .14f, .09f + s, .3f);
        g.glass(t, .02f, false, false);
        g.txt("1,5 m", s, .22f, .024f, Color.WHITE, Paint.Align.CENTER, g.medium);
        g.txt(String.format(Locale.US, "IPD %.0f mm · distorção %.2f / %.2f", cfg.ipdMm, cfg.k1, cfg.k2), 0, .36f, .017f, 0xCCFFFFFF, Paint.Align.CENTER, g.regular);
        g.txt("Ajuste até ver uma imagem só, reta e sem cortes", 0, .4f, .017f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
    }

    /* ================= entradas ================= */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (calibration) return true;
        float[] p = screenToScene(e.getX(), e.getY());
        ui.touch(e.getActionMasked(), p[0], p[1], SystemClock.uptimeMillis());
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE || e.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
            float[] p = screenToScene(e.getX(), e.getY());
            ui.hover(p[0], p[1], SystemClock.uptimeMillis());
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    void resetHands() {
        rawHands = new float[0][];
        ui.resetHands();
    }
}
