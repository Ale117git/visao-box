package br.visaobox.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Locale;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Motor de renderização em OpenGL ES 2.0.
 *  - A câmera chega direto na placa de vídeo (textura externa, sem cópia).
 *  - A interface de cada olho é desenhada com Canvas acelerado por hardware numa textura.
 *  - Um shader GLSL junta tudo: câmera, janelas atrás da pessoa, recorte, interface, cores e vinheta.
 *  - A distorção da lente é feita por uma malha de vértices (barata).
 */
public class VrView extends GLSurfaceView implements GLSurfaceView.Renderer {

    static final int MESH = 32;

    final Config cfg;
    final HeadTracker head;
    final SpatialUi ui;
    private final Gfx g = new Gfx();
    final Backdrop backdrop;
    final Weather weather = new Weather();
    private long weatherT;

    /* ---------- dados vindos de outras threads ---------- */
    public volatile Bitmap frame;                 // imagem da análise (em pé), para a janela Câmera e reserva
    public volatile long frameStamp;
    public volatile float[][] rawHands = new float[0][];
    public volatile long rawStamp = 0;
    public volatile byte[] maskData;              // máscara de pessoas (0..255), em pé
    public volatile int maskW, maskH;
    public volatile long maskStamp;
    public volatile boolean handReady, segReady;
    public volatile String error, camError;
    public volatile String camLabel = "";
    public volatile boolean calibration = false;
    public volatile boolean mirror = false;
    public volatile int[] webVisible = new int[0];

    // QR code (normalizado na imagem em pé: centro u, v e tamanho em altura)
    public volatile float qrU, qrV, qrS;
    public volatile String qrText;
    public volatile long qrStamp;
    private long qrStampSeen;
    public volatile boolean qrWanted;

    public volatile String pendingLayout;

    // gravação da realidade mista (uma imagem só, sem dividir os olhos)
    public volatile Surface recSurface;
    public volatile int recW, recH;
    public volatile long recStart;
    private long lastRecFrame;
    private boolean recSoftware;
    public volatile int openWindows;
    public volatile int webFocusId = -1;


    /* ---------- geometria (pixels da tela) ---------- */
    private volatile int W, H, n = 2;
    private volatile float eyeW, eyeH;
    private final float[] eyeCX = new float[2], eyeCY = new float[2];
    private volatile boolean distNow;
    private volatile float k1Now, k2Now;

    /* ---------- GL ---------- */
    private int prog, aPos, aScene;
    private int uUiS, uUiT, uUiMat, uBackMat, uUpS, uUpT;
    private int uCam2D, uUi, uBack, uMask, uBg, uHasBack, uSeg, uMaskTexel, uCamAdj, uAdj, uVigR;
    private int cam2DTex, maskTex;
    private final float[] ident = new float[16];
    private long cam2DStamp = -1, maskStampGl = -1;
    private int cam2DW, cam2DH, maskTexW, maskTexH;
    private ByteBuffer maskBuf;
    private Layer front, back;          // a interface é desenhada UMA vez por quadro; o 3D sai do shader
    private final FloatBuffer[] meshPos = new FloatBuffer[2], meshScene = new FloatBuffer[2];
    private ShortBuffer meshIdx;
    private int meshCount;
    private String meshKey = "";

    // câmera na cena
    private float camX0, camY0, camDW, camDH, upW = 4, upH = 3;
    private long lastFrameSeen, frameSeenAt;

    // cabeça suavizada
    private float sYaw, sPitch, sRoll;
    private long lastT = 0;
    private int fpsFrames; private long fpsT; private int fps;
    private final float[][] stars = new float[160][3];

    /** Uma camada de interface: Canvas acelerado desenhando numa textura do OpenGL. */
    static final class Layer {
        final int tex, w, h;
        final SurfaceTexture st;
        final Surface surface;
        final float[] mat = new float[16];

        Layer(int w, int h) {
            this.w = w; this.h = h;
            tex = genOes();
            st = new SurfaceTexture(tex);
            st.setDefaultBufferSize(w, h);
            surface = new Surface(st);
            Matrix.setIdentityM(mat, 0);
        }

        void latch() {
            try { st.updateTexImage(); st.getTransformMatrix(mat); } catch (Throwable ignored) { }
        }

        void release() {
            try { surface.release(); } catch (Throwable ignored) { }
            try { st.release(); } catch (Throwable ignored) { }
            GLES20.glDeleteTextures(1, new int[]{tex}, 0);
        }
    }

    public VrView(Context ctx, Config cfg, HeadTracker head, Host host) {
        super(ctx);
        this.cfg = cfg;
        this.head = head;
        this.ui = new SpatialUi(cfg, host, g);
        this.backdrop = new Backdrop(ctx);
        ui.backdrop = backdrop;
        Random r = new Random(7);
        for (float[] s : stars) {
            s[0] = (float) (r.nextFloat() * Math.PI * 2 - Math.PI);
            s[1] = (float) Math.asin(r.nextFloat() * .95f + .02f);
            s[2] = .4f + r.nextFloat() * .6f;
        }
        Matrix.setIdentityM(ident, 0);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
    }

    /** Roda uma ação na thread da tela (onde vive a interface). */
    void onGl(Runnable r) { queueEvent(r); }

    /* ================= shaders ================= */

    private static final String VS =
            "attribute vec2 aPos;\n" +
            "attribute vec2 aScene;\n" +
            "uniform vec3 uUiS; uniform vec3 uUiT; uniform mat4 uUiMat; uniform mat4 uBackMat;\n" +

            "uniform vec3 uUpS; uniform vec3 uUpT;\n" +
            "varying vec2 vUi; varying vec2 vBack; varying vec2 vUp; varying vec2 vScene;\n" +
            "void main() {\n" +
            "  vec3 p = vec3(aScene, 1.0);\n" +
            "  vec4 u = vec4(dot(p, uUiS), dot(p, uUiT), 0.0, 1.0);\n" +
            "  vUi = (uUiMat * u).xy;\n" +
            "  vBack = (uBackMat * u).xy;\n" +

            "  vUp = vec2(dot(p, uUpS), dot(p, uUpT));\n" +
            "  vScene = aScene;\n" +
            "  gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "}\n";

    private static final String FS =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform sampler2D uCam2D;\n" +
            "uniform samplerExternalOES uUi; uniform samplerExternalOES uBack; uniform sampler2D uMask;\n" +
            "uniform int uBg; uniform int uHasBack; uniform int uSeg;\n" +
            "uniform vec2 uMaskTexel; uniform vec4 uCamAdj; uniform vec4 uAdj; uniform float uVigR;\n" +
            "varying vec2 vUi; varying vec2 vBack; varying vec2 vUp; varying vec2 vScene;\n" +
            "void main() {\n" +
            "  vec4 ui = texture2D(uUi, vUi);\n" +
            "  vec3 col;\n" +
            "  if (uBg == 2) {\n" +
            "    col = ui.rgb;\n" +
            "  } else {\n" +
            "    bool inside = vUp.x >= 0.0 && vUp.x <= 1.0 && vUp.y >= 0.0 && vUp.y <= 1.0;\n" +
            "    vec3 cam = texture2D(uCam2D, vUp).rgb;\n" +
            "    if (!inside) cam = vec3(0.03);\n" +
            "    cam = clamp((cam - 0.5) * uCamAdj.y + 0.5 + uCamAdj.x, 0.0, 1.0) * uCamAdj.z * uCamAdj.w;\n" +
            "    vec3 base = cam;\n" +
            "    if (uHasBack == 1) { vec4 b = texture2D(uBack, vBack); base = b.rgb + base * (1.0 - b.a); }\n" +
            "    if (uSeg > 0 && inside) {\n" +
            "      float m = texture2D(uMask, vUp).r;\n" +
            "      vec3 person = cam;\n" +
            "      if (uSeg == 2) person = mix(cam, vec3(0.49, 0.77, 1.0), 0.45);\n" +
            "      base = mix(base, person, m);\n" +
            "      if (uSeg == 3) {\n" +
            "        vec2 dx = vec2(uMaskTexel.x * 2.0, 0.0); vec2 dy = vec2(0.0, uMaskTexel.y * 2.0);\n" +
            "        float nb = texture2D(uMask, vUp + dx).r + texture2D(uMask, vUp - dx).r\n" +
            "                 + texture2D(uMask, vUp + dy).r + texture2D(uMask, vUp - dy).r;\n" +
            "        float e = clamp(abs(m - nb * 0.25) * 6.0, 0.0, 1.0);\n" +
            "        base = mix(base, vec3(0.49, 0.77, 1.0), e);\n" +
            "      }\n" +
            "    }\n" +
            "    col = ui.rgb + base * (1.0 - ui.a);\n" +
            "  }\n" +
            "  float l = dot(col, vec3(0.299, 0.587, 0.114));\n" +
            "  col = mix(vec3(l), col, uAdj.z);\n" +
            "  col = (col - 0.5) * uAdj.y + 0.5 + uAdj.x;\n" +
            "  float r = length(vScene) / uVigR;\n" +
            "  col *= 1.0 - uAdj.w * smoothstep(0.72, 1.0, r);\n" +
            "  gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);\n" +
            "}\n";

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(s);
            GLES20.glDeleteShader(s);
            throw new RuntimeException("shader: " + log);
        }
        return s;
    }

    static int genOes() {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return t[0];
    }

    private static int gen2D() {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        ByteBuffer one = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        one.put(new byte[]{0, 0, 0, (byte) 255}).position(0);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, one);
        return t[0];
    }

    /* ================= ciclo do GL ================= */

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, VS));
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, FS));
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) error = "GL: " + GLES20.glGetProgramInfoLog(p);
        prog = p;
        aPos = GLES20.glGetAttribLocation(p, "aPos");
        aScene = GLES20.glGetAttribLocation(p, "aScene");
        uUiS = GLES20.glGetUniformLocation(p, "uUiS");
        uUiT = GLES20.glGetUniformLocation(p, "uUiT");
        uUiMat = GLES20.glGetUniformLocation(p, "uUiMat");
        uBackMat = GLES20.glGetUniformLocation(p, "uBackMat");
        uUpS = GLES20.glGetUniformLocation(p, "uUpS");
        uUpT = GLES20.glGetUniformLocation(p, "uUpT");
        uCam2D = GLES20.glGetUniformLocation(p, "uCam2D");
        uUi = GLES20.glGetUniformLocation(p, "uUi");
        uBack = GLES20.glGetUniformLocation(p, "uBack");
        uMask = GLES20.glGetUniformLocation(p, "uMask");
        uBg = GLES20.glGetUniformLocation(p, "uBg");
        uHasBack = GLES20.glGetUniformLocation(p, "uHasBack");
        uSeg = GLES20.glGetUniformLocation(p, "uSeg");
        uMaskTexel = GLES20.glGetUniformLocation(p, "uMaskTexel");
        uCamAdj = GLES20.glGetUniformLocation(p, "uCamAdj");
        uAdj = GLES20.glGetUniformLocation(p, "uAdj");
        uVigR = GLES20.glGetUniformLocation(p, "uVigR");

        cam2DTex = gen2D();
        maskTex = gen2D();
        cam2DStamp = -1; maskStampGl = -1; cam2DW = cam2DH = maskTexW = maskTexH = 0;
        front = null; back = null;
        meshKey = "";
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        W = width; H = height;
        meshKey = "";
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = SystemClock.uptimeMillis();
        if (lastT == 0) { lastT = now; fpsT = now; }
        float dt = Math.min(.05f, (now - lastT) / 1000f);
        lastT = now;
        GLES20.glViewport(0, 0, W, H);
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (W == 0 || H == 0) return;

        try {
            computeGeometry();
            updateHead(dt);
            mapCamera(now);
            g.textScale = cfg.textScale;
            ui.frame = frame;
            ui.camLabel = camLabel;
            ui.updateHands(rawHands, rawStamp, camX0, camDW, camY0, camDH, now);
            long qs = qrStamp;
            if (qs != qrStampSeen) {
                qrStampSeen = qs;
                float u = mirror ? 1 - qrU : qrU;
                ui.setQr(camX0 + u * camDW, camY0 + qrV * camDH, qrS * camDH, qrText, now);
            }
            qrWanted = ui.qrWanted(now);
            String pl = pendingLayout;
            if (pl != null) { pendingLayout = null; ui.restoreLayout(pl, now); }
            ui.update(now, dt);
            ui.status = statusLine(now);
            webVisible = ui.webVisible();
            openWindows = ui.visibleCount();
            webFocusId = ui.webFocusId();

            boolean camBg = cfg.background == Config.BG_CAMERA && !calibration;
            ui.setCam(camX0, camY0, camDW, camDH, mirror, frame);
            boolean segOn = camBg && cfg.segMode != Config.SEG_OFF && segReady && maskData != null;
            boolean useBack = segOn && ui.anyBehind();

            // 1) interface: desenhada uma vez só (Canvas acelerado); o 3D de cada olho sai do shader
            drawLayer(front, useBack ? 2 : 0, camBg, now);
            if (useBack) drawLayer(back, 1, camBg, now);
            // 1b) gravação: a mesma cena, em tela cheia
            if (recSurface != null) drawRecording(camBg, now);
            // 2) pega as imagens novas
            front.latch();
            if (useBack) back.latch();
            int bgMode = camBg ? 0 : 2;
            if (camBg) upload2D();
            if (segOn) uploadMask();

            // 3) junta tudo no shader
            composite(bgMode, useBack, segOn);
        } catch (Throwable t) {
            error = "Tela: " + t.getClass().getSimpleName() + " " + t.getMessage();
        }
        fpsFrames++;
        if (now - fpsT > 1000) { fps = fpsFrames; fpsFrames = 0; fpsT = now; }
    }

    /* ================= geometria e malha ================= */

    private boolean distorted() { return cfg.stereo && cfg.distortion && (cfg.k1 > 0 || cfg.k2 > 0); }

    private void computeGeometry() {
        float pxPerMm = getResources().getDisplayMetrics().xdpi / 25.4f;
        float[] e = eyeSize(cfg.stereo ? W / 2f : W, H);
        if (cfg.stereo) {
            n = 2;
            float S = Math.min(e[0], e[1]);
            eyeW = e[0]; eyeH = e[1];
            float half = Gfx.clamp(cfg.ipdMm * pxPerMm / 2f, S * .3f, W / 2f);
            float ax = cfg.alignXmm * pxPerMm, ay = cfg.alignYmm * pxPerMm;
            eyeCX[0] = W / 2f - half + ax;
            eyeCX[1] = W / 2f + half + ax;
            eyeCY[0] = eyeCY[1] = H / 2f + ay;
        } else {
            n = 1;
            eyeW = e[0]; eyeH = e[1];
            eyeCX[0] = W / 2f; eyeCY[0] = H / 2f;
        }
        distNow = distorted(); k1Now = cfg.k1; k2Now = cfg.k2;
        // a lente amplia o centro: desenha com sobra de resolução para compensar o zoom (fica nítido)
        float mag = distNow ? 1 + cfg.k1 + cfg.k2 : 1;
        int resPx = Config.RES_PX[Math.max(0, Math.min(Config.RES_PX.length - 1, cfg.resolution))];
        float rs = resPx > 0 ? resPx / Math.max(1f, eyeH)                         // resolução escolhida (altura em pixels)
                : Math.min(cfg.resScale * mag, 1800f / Math.max(1, eyeH));   // automática: tela × compensação da lente
        if (eyeW * rs > 3072) rs = 3072 / eyeW;                     // limite de memória (formatos bem largos)
        int bw = Math.max(64, Math.round(eyeW * rs)), bh = Math.max(64, Math.round(eyeH * rs));
        if (front == null || front.w != bw || front.h != bh) {
            if (front != null) front.release();
            if (back != null) back.release();
            front = new Layer(bw, bh);
            back = new Layer(bw, bh);
        }
        String key = W + ":" + H + ":" + n + ":" + eyeW + ":" + eyeH + ":" + eyeCX[0] + ":" + eyeCX[1] + ":" + eyeCY[0] + ":" + cfg.k1 + ":" + cfg.k2 + ":" + distNow;
        if (!key.equals(meshKey)) {
            meshKey = key;
            buildMesh();
        }
        ui.eyeHalfW = eyeW / eyeH / 2f;
    }

    /** Tamanho da imagem de cada olho (px) dentro da área disponível, no formato escolhido. */
    private float[] eyeSize(float availW, float availH) {
        float r = Config.FORMAT_RATIO[Math.max(0, Math.min(Config.FORMAT_RATIO.length - 1, cfg.format))];
        float w, h;
        if (r <= 0) { w = availW; h = availH; }              // tela cheia
        else { h = Math.min(availH, availW / r); w = h * r; }
        return new float[]{w * cfg.eyeScale, h * cfg.eyeScale};
    }

    private void buildMesh() {
        int vn = (MESH + 1) * (MESH + 1);
        float aspect = eyeW / eyeH, scale = 1 + cfg.k1 + cfg.k2;
        for (int e = 0; e < n; e++) {
            FloatBuffer pos = ByteBuffer.allocateDirect(vn * 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
            FloatBuffer sc = ByteBuffer.allocateDirect(vn * 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
            for (int j = 0; j <= MESH; j++) {
                for (int i = 0; i <= MESH; i++) {
                    float x = (i / (float) MESH * 2 - 1) * aspect, y = j / (float) MESH * 2 - 1;
                    float fct = 1;
                    if (distNow) {
                        float r2 = x * x + y * y;
                        fct = scale / (1 + cfg.k1 * r2 + cfg.k2 * r2 * r2);
                    }
                    float px = eyeCX[e] + x * fct * eyeH / 2, py = eyeCY[e] + y * fct * eyeH / 2;
                    pos.put(px / W * 2 - 1).put(1 - py / H * 2);
                    sc.put(x / 2).put(y / 2);
                }
            }
            pos.position(0); sc.position(0);
            meshPos[e] = pos; meshScene[e] = sc;
        }
        ShortBuffer idx = ByteBuffer.allocateDirect(MESH * MESH * 6 * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        for (int j = 0; j < MESH; j++) {
            for (int i = 0; i < MESH; i++) {
                short a = (short) (j * (MESH + 1) + i), b = (short) (a + 1), c = (short) (a + MESH + 1), d = (short) (c + 1);
                idx.put(a).put(c).put(b).put(b).put(c).put(d);
            }
        }
        idx.position(0);
        meshIdx = idx;
        meshCount = MESH * MESH * 6;
    }

    /** Tela (px) -> coordenadas da cena, desfazendo a distorção da lente. */
    private float[] screenToScene(float px, float py) {
        int e = (n == 2 && px > W / 2f) ? 1 : 0;
        float h2 = Math.max(1, eyeH / 2);
        float dx = (px - eyeCX[e]) / h2, dy = (py - eyeCY[e]) / h2;
        if (distNow) {
            float rd = (float) Math.hypot(dx, dy);
            if (rd > 1e-5f) {
                float k1 = k1Now, k2 = k2Now, scale = 1 + k1 + k2, rs = rd;
                for (int it = 0; it < 10; it++) { float r2 = rs * rs; rs = rd * (1 + k1 * r2 + k2 * r2 * r2) / scale; }
                dx *= rs / rd; dy *= rs / rd;
            }
        }
        return new float[]{dx * .5f, dy * .5f};
    }

    /* ================= câmera ================= */

    private void mapCamera(long now) {
        Bitmap f = frame;
        if (f != null) { upW = f.getWidth(); upH = f.getHeight(); }
        long st = frameStamp;
        if (st != lastFrameSeen) { lastFrameSeen = st; frameSeenAt = now; }
        float aspect = eyeW / eyeH;
        float s = Math.max(aspect / upW, 1f / upH) * cfg.camZoom;
        camDW = upW * s; camDH = upH * s;
        camX0 = -camDW / 2; camY0 = -camDH / 2;
    }

    private void upload2D() {
        Bitmap f = frame;
        long st = frameStamp;
        if (f == null || st == cam2DStamp || f.isRecycled()) return;
        cam2DStamp = st;
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cam2DTex);
        try {
            if (f.getWidth() == cam2DW && f.getHeight() == cam2DH) GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, f);
            else { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, f, 0); cam2DW = f.getWidth(); cam2DH = f.getHeight(); }
        } catch (Throwable ignored) { }
    }

    private void uploadMask() {
        byte[] d = maskData;
        long st = maskStamp;
        int w = maskW, h = maskH;
        if (d == null || st == maskStampGl || d.length < w * h || w == 0) return;
        maskStampGl = st;
        if (maskBuf == null || maskBuf.capacity() < w * h) maskBuf = ByteBuffer.allocateDirect(w * h).order(ByteOrder.nativeOrder());
        maskBuf.clear();
        maskBuf.put(d, 0, w * h).position(0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        if (w == maskTexW && h == maskTexH)
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, w, h, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, maskBuf);
        else {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, w, h, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, maskBuf);
            maskTexW = w; maskTexH = h;
        }
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4);
    }

    /* ================= composição final ================= */

    private void composite(int bgMode, boolean useBack, boolean segOn) {
        GLES20.glUseProgram(prog);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        // da cena para a câmera em pé (u, v), com espelho na câmera frontal
        float a = 1f / camDW, c0 = -camX0 / camDW, b = 1f / camDH, d0 = -camY0 / camDH;
        float[] uS = mirror ? new float[]{-a, 0, 1 - c0} : new float[]{a, 0, c0};
        float[] uT = {0, b, d0};

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cam2DTex);
        GLES20.glUniform1i(uCam2D, 1);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex);
        GLES20.glUniform1i(uMask, 4);

        GLES20.glUniform3f(uUiT, 0, -1, .5f);
        GLES20.glUniform3f(uUpS, uS[0], uS[1], uS[2]);
        GLES20.glUniform3f(uUpT, uT[0], uT[1], uT[2]);
        GLES20.glUniform1i(uBg, bgMode);
        GLES20.glUniform1i(uHasBack, useBack ? 1 : 0);
        GLES20.glUniform1i(uSeg, segOn ? cfg.segMode : 0);
        GLES20.glUniform2f(uMaskTexel, 1f / Math.max(1, maskTexW), 1f / Math.max(1, maskTexH));
        GLES20.glUniform4f(uCamAdj, cfg.camBrightness, cfg.camContrast, Gfx.clamp(cfg.camOpacity, 0, 1), ui.cinema() ? .3f : 1f);
        GLES20.glUniform4f(uAdj, cfg.brightness, cfg.contrast, cfg.saturation, n == 2 ? cfg.vignette : 0);
        GLES20.glUniform1f(uVigR, .62f);

        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aScene);
        if (n == 2) GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        for (int e = 0; e < n; e++) {
            if (n == 2) GLES20.glScissor(e == 0 ? 0 : W / 2, 0, W / 2, H);
            Layer fl = front, bl = useBack ? back : front;
            // paralaxe: cada olho vê a interface deslocada como se estivesse a ~1,5 m
            int sign = n == 2 ? (e == 0 ? 1 : -1) : 0;
            float aspect = eyeW / eyeH;
            GLES20.glUniform3f(uUiS, 1f / aspect, 0, .5f - ui.shift(sign, 1.5f) / aspect);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, fl.tex);
            GLES20.glUniform1i(uUi, 2);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, bl.tex);
            GLES20.glUniform1i(uBack, 3);
            GLES20.glUniformMatrix4fv(uUiMat, 1, false, fl.mat, 0);
            GLES20.glUniformMatrix4fv(uBackMat, 1, false, bl.mat, 0);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, meshPos[e]);
            GLES20.glVertexAttribPointer(aScene, 2, GLES20.GL_FLOAT, false, 0, meshScene[e]);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, meshCount, GLES20.GL_UNSIGNED_SHORT, meshIdx);
        }
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aScene);
        if (n == 2) {
            GLES20.glScissor(W / 2 - 1, 0, 2, Math.round(H * .75f));
            GLES20.glClearColor(.165f, .173f, .2f, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    /* ================= interface (Canvas acelerado) ================= */

    private void drawLayer(Layer layer, int mode, boolean camBg, long now) {
        final int sign = 0;
        Canvas c = layer.surface.lockHardwareCanvas();
        try {
            c.drawColor(0, PorterDuff.Mode.CLEAR);
            c.save();
            c.translate(layer.w / 2f, layer.h / 2f);
            c.scale(layer.h, layer.h);
            g.c = c;
            if (calibration) {
                drawCalibration();
            } else {
                if (mode != 1) {
                    if (!camBg) drawBackground(sign);
                    else if (frame == null || now - frameSeenAt > 2500) drawNoCamera();
                    drawWeather(now, true);
                }
                if (mode == 1) ui.drawWindows(sign, 1, now);
                else {
                    ui.drawWindows(sign, mode == 2 ? 2 : 0, now);
                    ui.drawOverlay(sign, now);
                    ui.drawHud(sign, now, error != null ? error : camError);
                    drawRecIndicator(now);
                }
            }
            c.restore();
        } finally {
            layer.surface.unlockCanvasAndPost(c);
        }
    }

    /** Desenha a cena para o vídeo: câmera de fundo + janelas + dock, como você vê (sem os dois olhos). */
    private void drawRecording(boolean camBg, long now) {
        if (now - lastRecFrame < 30) return;            // ~30 quadros por segundo
        Surface s = recSurface;
        if (s == null) return;
        lastRecFrame = now;
        Canvas c;
        try { c = recSoftware ? s.lockCanvas(null) : s.lockHardwareCanvas(); }
        catch (Throwable t) {
            if (recSoftware) return;
            recSoftware = true;                          // aparelho sem desenho acelerado no gravador: usa o processador
            return;
        }
        String status = ui.status;
        try {
            int w = recW, h = recH;
            c.drawColor(Color.BLACK);
            c.save();
            c.translate(w / 2f, h / 2f);
            c.scale(h, h);
            g.c = c;
            if (camBg) {
                Bitmap f = frame;
                if (f != null && !f.isRecycled()) {
                    RectF r = new RectF(camX0, camY0, camX0 + camDW, camY0 + camDH);
                    c.save();
                    if (mirror) c.scale(-1, 1);
                    c.drawBitmap(f, null, r, g.bmp);
                    c.restore();
                    if (ui.cinema()) { g.fill.setColor(0xB3000000); c.drawRect(-2, -1, 2, 1, g.fill); }
                }
            } else drawBackground(0);
            drawWeather(now, false);
            ui.status = "";                              // sem a barra de status no vídeo
            ui.drawWindows(0, 0, now);
            ui.drawOverlay(0, now);
            ui.drawHud(0, now, null);
            c.restore();
        } catch (Throwable ignored) {
        } finally {
            ui.status = status;
            try { s.unlockCanvasAndPost(c); } catch (Throwable ignored) { }
        }
    }

    /** "REC" piscando no canto, só no óculos (não aparece no vídeo). */
    private void drawRecIndicator(long now) {
        if (recSurface == null) return;
        long sec = Math.max(0, (now - recStart) / 1000);
        float x = -.3f, y = -.4f;
        if ((now / 500) % 2 == 0) g.circle(x, y, .011f, 0xFFFF3B30);
        g.txt(String.format(Locale.US, "REC %d:%02d", sec / 60, sec % 60), x + .022f, y, .018f, 0xFFFF6B6B, Paint.Align.LEFT, g.medium);
    }

    private void drawNoCamera() {
        // não simula o ambiente: sem imagem, mostra só o aviso
        g.txt(camError != null ? "Câmera indisponível" : "Abrindo a câmera…", 0, -.06f, .026f, 0xFFFFD27A, Paint.Align.CENTER, g.medium);
        g.txt("Use o fundo Virtual 3D ou Escuro em Ajustes", 0, -.02f, .018f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
    }

    /** Chuva, neve etc. (por cima do fundo, atrás das janelas). */
    private void drawWeather(long now, boolean advance) {
        if (cfg.weather == Weather.NONE && !advance) return;
        float dt = advance ? Math.min(.05f, (now - weatherT) / 1000f) : 0;
        if (advance) weatherT = now;
        boolean on = cfg.headTracking && head.available && head.hasData;
        weather.draw(g.c, calibration ? 0 : cfg.weather, cfg.weatherAmt, cfg.weatherSound, on ? sYaw : 0, ui.f, eyeW / eyeH / 2f + .05f, dt, now);
    }

    private void drawBackground(int sign) {
        Canvas c = g.c;
        float hw = eyeW / eyeH / 2f + .05f;
        if (cfg.background == Config.BG_IMAGE) {
            boolean on = cfg.headTracking && head.available && head.hasData;
            c.save();
            if (on) c.rotate((float) -Math.toDegrees(sRoll));
            boolean ok = backdrop.draw(c, cfg.bgImage, on ? sYaw : 0, on ? sPitch : 0, ui.f, hw + .3f);
            c.restore();
            if (ok) return;
            g.txt("Carregando a imagem de fundo…", 0, 0, .02f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
            return;
        }
        if (cfg.background == Config.BG_VIRTUAL) drawVirtual(sign);
        else if (cfg.background >= Config.BG_ESPACO) drawScene(cfg.background);
        else {
            g.grad.setShader(new RadialGradient(0, .1f, .9f, 0xFF161A26, 0xFF030407, Shader.TileMode.CLAMP));
            c.drawRect(-hw, -.55f, hw, .55f, g.grad);
            g.grad.setShader(null);
        }
    }

    /* ================= ambientes 360° ================= */

    private float envYaw, envPitch;

    /** Direção no mundo (guinada, elevação) -> ponto na visão. Devolve null se estiver atrás. */
    private float[] dir(float yaw, float elev) {
        float ry = HeadTracker.wrap(yaw - envYaw), rp = envPitch - elev;
        if (Math.abs(ry) > 1.3f || Math.abs(rp) > 1.3f) return null;
        return new float[]{ui.f * (float) Math.tan(ry), ui.f * (float) Math.tan(rp)};
    }

    private float elevY(float elev) { return ui.f * (float) Math.tan(Gfx.clamp(envPitch - elev, -1.3f, 1.3f)); }

    private void vgrad(float y0, float y1, int c0, int c1) {
        g.grad.setShader(new LinearGradient(0, y0, 0, y1, c0, c1, Shader.TileMode.CLAMP));
        g.c.drawRect(-3, Math.min(y0, y1), 3, Math.max(y0, y1), g.grad);
        g.grad.setShader(null);
    }

    private void glow(float x, float y, float r, int color) {
        g.grad.setShader(new RadialGradient(x, y, r, new int[]{color, Gfx.a(color, .35f), color & 0x00FFFFFF},
                new float[]{0, .35f, 1}, Shader.TileMode.CLAMP));
        g.c.drawCircle(x, y, r, g.grad);
        g.grad.setShader(null);
    }

    /** Cenários que acompanham a cabeça: espaço, praia, pôr do sol e sala de cinema. */
    private void drawScene(int kind) {
        Canvas c = g.c;
        boolean on = cfg.headTracking && head.available && head.hasData;
        envYaw = on ? sYaw : 0; envPitch = on ? sPitch : 0;
        float roll = on ? sRoll : 0;
        float t = SystemClock.uptimeMillis() / 1000f;
        c.save();
        c.rotate((float) -Math.toDegrees(roll));
        float hz = elevY(0);
        switch (kind) {
            case Config.BG_ESPACO: {
                vgrad(-2, 2, 0xFF05020F, 0xFF0B0A22);
                for (float[] st : stars) { float[] q = dir(st[0], st[1] * 1.6f - .7f); if (q != null) g.circle(q[0], q[1], .0022f * st[2], Gfx.a(0xFFFFFFFF, .4f + .6f * st[2])); }
                float[] n1 = dir(-.9f, .3f); if (n1 != null) glow(n1[0], n1[1], .45f, 0x553A6BFF);
                float[] n2 = dir(1.9f, -.1f); if (n2 != null) glow(n2[0], n2[1], .5f, 0x55E0457B);
                float[] pl = dir(.55f, .22f);
                if (pl != null) {
                    float r = .16f;
                    glow(pl[0], pl[1], r * 1.6f, 0x332FA4FF);
                    g.grad.setShader(new RadialGradient(pl[0] - r * .35f, pl[1] - r * .35f, r * 1.3f, 0xFF7FD3FF, 0xFF143A7A, Shader.TileMode.CLAMP));
                    c.drawCircle(pl[0], pl[1], r, g.grad);
                    g.grad.setShader(null);
                    g.stroke.setColor(0x99C9E7FF); g.stroke.setStrokeWidth(.006f);
                    c.save(); c.rotate(-18, pl[0], pl[1]);
                    c.drawOval(new RectF(pl[0] - r * 1.9f, pl[1] - r * .42f, pl[0] + r * 1.9f, pl[1] + r * .42f), g.stroke);
                    c.restore();
                }
                float[] moon = dir(-.35f, .5f);
                if (moon != null) { g.circle(moon[0], moon[1], .035f, 0xFFD9D4CC); g.circle(moon[0] - .01f, moon[1] - .008f, .008f, 0xFFB9B3A8); }
                break;
            }
            case Config.BG_PRAIA: {
                vgrad(hz - 1.2f, hz, 0xFF3D8BE0, 0xFFCDE9FF);
                float sea = elevY(-.2f);
                vgrad(hz, sea, 0xFF2E86C1, 0xFF1A5E8F);
                vgrad(sea, sea + 1.5f, 0xFFF1DDB0, 0xFFD9BC85);
                float[] sun = dir(-.6f, .55f); if (sun != null) { glow(sun[0], sun[1], .2f, 0xAAFFF6C8); g.circle(sun[0], sun[1], .045f, 0xFFFFFBEA); }
                g.stroke.setStrokeWidth(.003f);
                for (int i = 0; i < 6; i++) {
                    float e = -.03f - i * .03f;
                    float y = elevY(e) + (float) Math.sin(t * 1.3f + i) * .004f;
                    g.stroke.setColor(Gfx.a(0xFFFFFFFF, .25f + i * .06f));
                    c.drawLine(-3, y, 3, y, g.stroke);
                }
                float foam = sea + (float) Math.sin(t * .8f) * .006f;
                g.stroke.setColor(0xCCFFFFFF); g.stroke.setStrokeWidth(.006f);
                c.drawLine(-3, foam, 3, foam, g.stroke);
                for (int i = 0; i < 5; i++) {
                    float[] cl = dir(-2.5f + i * 1.25f + t * .01f, .32f + (i % 2) * .08f);
                    if (cl != null) { g.fill.setColor(0xCCFFFFFF); c.drawOval(new RectF(cl[0] - .09f, cl[1] - .025f, cl[0] + .09f, cl[1] + .025f), g.fill);
                        c.drawOval(new RectF(cl[0] - .05f, cl[1] - .045f, cl[0] + .04f, cl[1] + .01f), g.fill); }
                }
                break;
            }
            case Config.BG_POR_DO_SOL: {
                vgrad(hz - 1.2f, hz, 0xFF2B1B4D, 0xFFFF8A4C);
                vgrad(hz, hz + 1.2f, 0xFF3A2350, 0xFF120B1E);
                float[] sun = dir(0, .04f);
                if (sun != null) {
                    glow(sun[0], sun[1], .45f, 0x99FFB15C);
                    g.c.save(); g.c.clipRect(-3, -3, 3, hz);
                    g.circle(sun[0], sun[1], .07f, 0xFFFFD08A);
                    g.c.restore();
                    g.stroke.setStrokeWidth(.004f);
                    for (int i = 0; i < 9; i++) {
                        float y = hz + .012f + i * .016f, wdt = .08f - i * .007f + (float) Math.sin(t * 2 + i) * .006f;
                        g.stroke.setColor(Gfx.a(0xFFFFB15C, .7f - i * .07f));
                        c.drawLine(sun[0] - wdt, y, sun[0] + wdt, y, g.stroke);
                    }
                }
                Path mt = new Path();
                boolean st = false;
                for (int i = -40; i <= 40; i++) {
                    float yw = i * .08f;
                    float h = .05f + .035f * (float) Math.sin(yw * 3.1f) + .02f * (float) Math.sin(yw * 7.3f);
                    float[] q = dir(yw, h);
                    if (q == null) { st = false; continue; }
                    if (!st) { mt.moveTo(q[0], hz); mt.lineTo(q[0], q[1]); st = true; } else mt.lineTo(q[0], q[1]);
                }
                mt.lineTo(3, hz); mt.lineTo(-3, hz); mt.close();
                g.fill.setColor(0xFF1B1030);
                c.drawPath(mt, g.fill);
                break;
            }
            case Config.BG_CINEMA: {
                vgrad(-2, 2, 0xFF120709, 0xFF070304);
                float[] scr = dir(0, .08f);
                if (scr != null) {
                    glow(scr[0], scr[1], .7f, 0x44506AA0);
                    g.fill.setColor(0xFF1A1E2A);
                    c.drawRoundRect(new RectF(scr[0] - .62f, scr[1] - .3f, scr[0] + .62f, scr[1] + .3f), .01f, .01f, g.fill);
                }
                for (int side = -1; side <= 1; side += 2) {
                    float[] cur = dir(side * .75f, .1f);
                    if (cur != null) { vgradAt(cur[0], .16f, 0xFF5A0F18, 0xFF2A0509); }
                }
                g.fill.setColor(0xFF0D0507);
                for (int row = 0; row < 4; row++) {
                    float y = elevY(-.2f - row * .09f);
                    for (int k = -14; k <= 14; k++) {
                        float[] q = dir(k * .09f, -.2f - row * .09f);
                        if (q == null) continue;
                        float s = .028f + row * .006f;
                        c.drawRoundRect(new RectF(q[0] - s, y - s * 1.1f, q[0] + s, y + s), s * .5f, s * .5f, g.fill);
                    }
                }
                break;
            }
            default: break;
        }
        c.restore();
    }

    private void vgradAt(float x, float half, int c0, int c1) {
        g.grad.setShader(new LinearGradient(x - half, 0, x + half, 0, new int[]{c1, c0, c1}, null, Shader.TileMode.CLAMP));
        g.c.drawRect(x - half, -3, x + half, 3, g.grad);
        g.grad.setShader(null);
    }

    /** Ambiente virtual: céu estrelado e piso quadriculado em perspectiva, com paralaxe por olho. */
    private void drawVirtual(int sign) {
        Canvas c = g.c;
        boolean on = cfg.headTracking && head.available && head.hasData;
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
        float sx = (float) Math.sin(yaw), cx = (float) Math.cos(yaw);
        float sp = (float) Math.sin(pitch), cp = (float) Math.cos(pitch);
        float eo = -sign * cfg.ipdMm / 2000f * cfg.stereo3d;
        float ex = eo * cx, ey = eo * -sx;
        Path near = new Path(), far = new Path();
        for (int k = -14; k <= 14; k++) {
            for (int dir = 0; dir < 2; dir++) {
                boolean started = false;
                Path p = Math.abs(k) <= 5 ? near : far;
                for (int t = -14; t <= 14; t++) {
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
        c.restore();
    }

    /** Tela de calibração: grade, círculos, cruz central e alvo de fusão. */
    private void drawCalibration() {
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
        g.txt("CALIBRAÇÃO DO VR BOX", 0, -.42f, .022f, 0xFF9FD3FF, Paint.Align.CENTER, g.medium);
        float[][] marks = {{0, -.48f}, {0, .48f}, {-.46f, 0}, {.46f, 0}};
        for (float[] m : marks) g.circle(m[0], m[1], .012f, 0xFFFFD27A);
        float s = 0;
        RectF t = new RectF(-.09f + s, .14f, .09f + s, .3f);
        g.glass(t, .02f, false, false);
        g.txt("1,5 m", s, .22f, .024f, Color.WHITE, Paint.Align.CENTER, g.medium);
        g.txt(String.format(Locale.US, "IPD %.0f mm · distorção %.2f / %.2f", cfg.ipdMm, cfg.k1, cfg.k2), 0, .36f, .017f, 0xCCFFFFFF, Paint.Align.CENTER, g.regular);
        g.txt("Ajuste até ver uma imagem só, reta e sem cortes · controle: ← → olhos, ↑ ↓ distorção, A ou B sai", 0, .4f, .017f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
    }

    /* ================= cabeça e status ================= */

    private void updateHead(float dt) {
        boolean avail = head.available && head.hasData;
        boolean on = avail && cfg.headTracking;
        float ty = on ? head.relYaw() * cfg.headSens : 0;
        float tp = on ? head.pitch() * cfg.headSens : 0;
        float tr = on ? head.roll() : 0;
        // suavização independente da taxa de quadros
        float k = Gfx.clamp(1 - cfg.headSmooth * .9f, .08f, 1f);
        float a = 1 - (float) Math.pow(1 - k, Math.max(.25f, dt * 60f));
        sYaw = HeadTracker.wrap(sYaw + HeadTracker.wrap(ty - sYaw) * a);
        sPitch += (tp - sPitch) * a;
        sRoll += (tr - sRoll) * a;
        ui.setHead(sYaw, sPitch, sRoll, avail);
    }

    private String statusLine(long now) {
        String place = ui.cinema() ? "Cinema" : (cfg.placement == Config.HUD ? "HUD" : "Espacial");
        String track = !head.available ? "sem sensor de rotação" : (cfg.headTracking ? "cabeça ✓" : "cabeça desligada");
        int hc = 0;
        for (int i = 0; i < 2; i++) if (ui.handActive(i, now)) hc++;
        String hands = ui.padActive(now) ? "controle ✓" : !cfg.handInput ? "mira (mãos desligadas)" : (!handReady ? "carregando mãos…" : hc + (hc == 1 ? " mão" : " mãos"));
        return place + " · " + track + " · " + hands + " · " + fps + " fps";
    }

    /* ================= entradas (thread principal -> thread da tela) ================= */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (calibration) return true;
        final float[] p = screenToScene(e.getX(), e.getY());
        final int action = e.getActionMasked();
        final long t = SystemClock.uptimeMillis();
        onGl(() -> ui.touch(action, p[0], p[1], t));
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_HOVER_MOVE || a == MotionEvent.ACTION_HOVER_ENTER) {
            final float[] p = screenToScene(e.getX(), e.getY());
            final long t = SystemClock.uptimeMillis();
            onGl(() -> ui.hover(p[0], p[1], t));
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    void resetHands() {
        rawHands = new float[0][];
        onGl(ui::resetHands);
    }
}
