package br.visaobox.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Gerenciador de janelas, dock, apps e interação.
 * Espaço de visão: centro do olho em (0,0), altura do olho = 1, y para baixo.
 * Cada janela tem posição angular (guinada/inclinação), distância em metros e um tamanho de projeto.
 */
final class SpatialUi {

    /* ================= apps ================= */
    static final int NAV = 0, GALERIA = 1, CAMERA = 2, AJUSTES = 3, ARQUIVOS = 4, WEB = 5, MENU = 6,
            RELOGIO = 7, DESENHO = 8, BOLHAS = 9, MUSICA = 10, CINEMA = 11, NOTAS = 12, CONTROLE = 13, YOUTUBE = 14;
    static final String[] NAMES = {"Navegador", "Galeria", "Câmera", "Ajustes", "Arquivos", "Web", "Menu",
            "Relógio", "Desenho", "Bolhas", "Música", "Cinema", "Notas", "Controle", "YouTube"};
    static final int[][] COLS = {
            {0xFF5AC8FA, 0xFF1E6FD9}, {0xFFFFD36B, 0xFFF0932B}, {0xFFE6EBF2, 0xFF5B6876}, {0xFFB7BFCB, 0xFF5D6675},
            {0xFF7DBEFF, 0xFF3867D6}, {0xFF7BE0A8, 0xFF1E9E63}, {0xFF9AA3B6, 0xFF4B5468}, {0xFFFFB36B, 0xFFE8590C},
            {0xFFF88AE6, 0xFF8E44AD}, {0xFF7FE3FF, 0xFF0A8FC9}, {0xFFFF8A8A, 0xFFC0392B}, {0xFFFF7A9A, 0xFF9B1B50}, {0xFFFFE27A, 0xFFE0A800}, {0xFF9BE15D, 0xFF2E9E3E}, {0xFFFF5A52, 0xFFC4170C}};
    static final float[][] SIZES = {{.92f, .6f}, {.82f, .54f}, {.64f, .44f}, {.96f, .74f}, {.72f, .52f}, {.72f, .48f},
            {.8f, .48f}, {.48f, .29f}, {.66f, .44f}, {.62f, .44f}, {.58f, .38f}, {.6f, .4f}, {.66f, .46f}, {.86f, .5f}, {.96f, .62f}};
    static final int[] DOCK = {NAV, YOUTUBE, GALERIA, CAMERA, AJUSTES, ARQUIVOS, WEB, MENU};
    static final int[] MENU_APPS = {NAV, YOUTUBE, GALERIA, CAMERA, AJUSTES, ARQUIVOS, WEB, RELOGIO, DESENHO, BOLHAS, MUSICA, NOTAS, CONTROLE, CINEMA};
    static final String[][] SITES = {{"YouTube", "https://m.youtube.com"}, {"Google", "https://www.google.com"},
            {"Wikipédia", "https://pt.m.wikipedia.org"}, {"Mapas", "https://www.google.com/maps"},
            {"Notícias", "https://g1.globo.com"}, {"Clima", "https://www.climatempo.com.br"}};
    static final String[] NOTE_N = {"Dó", "Ré", "Mi", "Sol", "Lá", "Dó"};
    static final int[] PEN = {0xFFFFFFFF, 0xFFFF6B6B, 0xFFFECA57, 0xFF48DBFB, 0xFF1DD1A1};
    static final int[][] CON = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {0, 5}, {5, 6}, {6, 7}, {7, 8}, {5, 9}, {9, 10}, {10, 11},
            {11, 12}, {9, 13}, {13, 14}, {14, 15}, {15, 16}, {13, 17}, {17, 18}, {18, 19}, {19, 20}, {0, 17}};

    static final int H_DOCK = 1, H_CLOCK = 2, H_HOMEBAR = 3, H_CHIP = 4, H_TBTN = 5, H_GRAB = 6, H_RESIZE = 7,
            H_BTN = 8, H_AREA = 9, H_BODY = 10, H_EXITCINEMA = 11,
            H_DOCKBODY = 12, H_KEY = 13, H_KBGRAB = 14, H_KBBODY = 15, H_KBCLOSE = 16;
    static final String[] TBTN = {"close", "min", "far", "near", "layer"};
    static final String[] TBTN_TIP = {"Fechar", "Minimizar", "Afastar", "Aproximar", "Atrás / na frente da pessoa"};

    /* ================= tipos ================= */
    static final class Stroke { int c; final ArrayList<float[]> pts = new ArrayList<>(); }
    static final class Bubble { float x, y, r, vy, ph, dx; }
    static final class Part { float x, y, vx, vy, life; }
    static final class Win {
        int id, app; float yaw, pitch, dist, w, h; long born; boolean minimized, behind;
        float cx, cy, s; boolean visible;
        final ArrayList<Stroke> strokes = new ArrayList<>(); int pen = 0xFFFFFFFF;
        final ArrayList<Bubble> bubbles = new ArrayList<>(); final ArrayList<Part> parts = new ArrayList<>();
        int score; float t; final long[] flash = new long[6]; long shot;
        int galSel = -1, page = 0;
        final StringBuilder text = new StringBuilder();
    }
    static final class Btn {
        final String id; final boolean circle; float x, y, r; RectF rect;
        Btn(String id, float x, float y, float r) { this.id = id; circle = true; this.x = x; this.y = y; this.r = r; }
        Btn(String id, RectF rc) { this.id = id; circle = false; rect = rc; }
        boolean hit(float px, float py, float hs) {
            if (circle) return Math.hypot(px - x, py - y) < r * 1.5f * hs;
            float e = .004f * hs;
            return px > rect.left - e && px < rect.right + e && py > rect.top - e && py < rect.bottom + e;
        }
    }
    static final class Parts { RectF body, title, content, grab; float hw, hh; }
    static final class Hit {
        final int type; final String key; Win win; int index; String id;
        Hit(int t, String k) { type = t; key = k; }
    }
    static final class Hand { final float[] x = new float[21], y = new float[21]; boolean down, init; float cx, cy; long last; }
    static final class Ptr {
        boolean exists, down; float x, y; Hit hit; Win drag, resize; float dx, dy; float wy0, wp0;
        boolean dockDrag, dockMoved, dockHome, kbDrag; float sx, sy, ox, oy;
        int webMode; float wfx, wfy, wlx, wly;      // página: 0 = esperando, 1 = rolando
    }
    static final class Latch {
        boolean down, rel;
        void press() { down = true; rel = false; }
        void release() { if (down) rel = true; }
        boolean consume() { boolean d = down; if (rel) { down = false; rel = false; } return d; }
    }
    static final class Row {
        static final int CYCLE = 0, TOGGLE = 1, ACTION = 2, STEP = 3;
        final String label; final int kind; final Supplier<String> value; final Runnable minus, plus;
        Row(String l, int k, Supplier<String> v, Runnable m, Runnable p) { label = l; kind = k; value = v; minus = m; plus = p; }
    }

    /* ================= estado ================= */
    final Config cfg;
    final Host host;
    final Gfx g;
    final Synth synth = new Synth();
    final ArrayList<Win> wins = new ArrayList<>();
    private int wid = 0;

    float headYaw, headPitch, roll;
    boolean trackAvail;
    float f = .5f;                  // distância focal (unidades da cena)
    Bitmap frame;                   // imagem atual da câmera
    float eyeHalfW = .5f;

    final Hand[] hands = {new Hand(), new Hand()};
    private long lastStamp = -1;
    final Ptr[] ptrs = {new Ptr(), new Ptr(), new Ptr(), new Ptr()};
    final HashSet<String> hov = new HashSet<>();

    // toque / mouse
    private float tX, tY; private long tLast = -100000; private final Latch tLatch = new Latch();
    // controle (Xbox / Bluetooth)
    final HashSet<Integer> padDown = new HashSet<>();
    float padLX, padLY, padRX, padRY, padLT, padRT;
    long padSeen = -100000;
    private boolean ltWas, rtWas;
    private float scrollAccX, scrollAccY; private long lastScroll;
    private float hatX, hatY;

    boolean padActive(long now) { return now - padSeen < 120000; }

    // ponteiro central
    float gazeX = 0, gazeY = 0; private final Latch gLatch = new Latch(); private boolean gazeForced;
    private String dwellKey; private long dwellStart; private boolean dwellFired; private String lastGazeKey;

    // cinema
    private Win cinemaWin; private float[] cinemaBak; private float cinemaYaw;
    private boolean allHidden;
    private final float[] dockAnim = new float[DOCK.length + 1];
    private String toast; private long toastT;
    private final Random rnd = new Random();
    private final SimpleDateFormat fmtTime = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat fmtDate = new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR"));
    private final SimpleDateFormat fmtDay = new SimpleDateFormat("EEE d", new Locale("pt", "BR"));
    private final SimpleDateFormat fmtFile = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private List<Row> rows;
    String status = "";
    String camLabel = "";

    SpatialUi(Config cfg, Host host, Gfx g) {
        this.cfg = cfg; this.host = host; this.g = g;
    }

    /* ================= cabeça e projeção ================= */

    boolean spatial() { return cfg.headTracking && trackAvail && cfg.placement != Config.HUD; }

    void setHead(float yaw, float pitch, float roll, boolean avail) {
        this.headYaw = yaw; this.headPitch = pitch; this.roll = roll; this.trackAvail = avail;
    }

    float shift(int sign, float dist) {
        return sign * (cfg.ipdMm / 2000f) * f / Math.max(.3f, dist) * cfg.stereo3d;
    }

    float[] toSpatial(float x, float y) {
        if (!spatial() || roll == 0) return new float[]{x, y};
        double c = Math.cos(roll), s = Math.sin(roll);
        return new float[]{(float) (x * c - y * s), (float) (x * s + y * c)};
    }

    private void layout() {
        f = .5f / (float) Math.tan(Math.toRadians(Gfx.clamp(cfg.fovDeg, 40, 130) / 2));
        boolean sp = spatial();
        for (Win w : wins) {
            boolean cine = cinemaWin != null;
            if (w.minimized || (allHidden && !cine) || (cine && w != cinemaWin)) { w.visible = false; continue; }
            float yaw = w.yaw, pitch = w.pitch;
            if (w == cinemaWin) { yaw = cinemaYaw; pitch = 0; }
            float ry = sp ? HeadTracker.wrap(yaw - headYaw) : yaw;
            float rp = sp ? headPitch - pitch : -pitch;
            if (Math.abs(ry) > 1.25f || Math.abs(rp) > 1.25f) { w.visible = false; continue; }
            w.cx = f * (float) Math.tan(ry);
            w.cy = f * (float) Math.tan(rp);
            w.s = (f / .5f) * (1.5f / Math.max(.3f, w.dist)) * cfg.uiScaleEff();
            w.visible = true;
        }
        // dock: fixo no ambiente (modo espacial) ou preso à visão
        dockAnchored = sp && !cfg.dockFollow;
        if (dockAnchored) {
            float yaw = (float) Math.atan(cfg.dockX / f), pitch = (float) -Math.atan(cfg.dockY / f);
            float ry = HeadTracker.wrap(yaw - headYaw), rp = headPitch - pitch;
            dockRy = ry;
            dockVis = Math.abs(ry) < 1.25f && Math.abs(rp) < 1.25f;
            dockCX = dockVis ? f * (float) Math.tan(ry) : 0;
            dockCY = dockVis ? f * (float) Math.tan(rp) : 0;
            if (!dockVis) dockRy = ry;
        } else { dockCX = cfg.dockX; dockCY = cfg.dockY; dockVis = true; dockRy = 0; }
        // teclado
        float kry = sp ? HeadTracker.wrap(kbYaw - headYaw) : kbYaw, krp = sp ? headPitch - kbPitch : -kbPitch;
        kbVis = Math.abs(kry) < 1.25f && Math.abs(krp) < 1.25f;
        kbCX = kbVis ? f * (float) Math.tan(kry) : 0;
        kbCY = kbVis ? f * (float) Math.tan(krp) : 0;
    }

    /** Converte um ponto da visão (sem rolagem) para ângulos guardáveis (yaw, pitch). */
    private float[] viewToAngles(float x, float y) {
        float ry = (float) Math.atan(x / f), rp = (float) Math.atan(y / f);
        if (spatial()) return new float[]{HeadTracker.wrap(ry + headYaw), headPitch - rp};
        return new float[]{ry, -rp};
    }

    int visibleCount() { int k = 0; for (Win w : wins) if (w.visible) k++; return k; }
    static boolean isWeb(int app) { return app == NAV || app == YOUTUBE; }

    /** Quais navegadores estão à vista (bit 1 = Navegador, bit 2 = YouTube). */
    int webVisibleMask() {
        int m = 0;
        for (Win w : wins) if (w.visible && isWeb(w.app)) m |= w.app == NAV ? 1 : 2;
        return m;
    }
    boolean anyBehind() { for (Win w : wins) if (w.visible && w.behind) return true; return false; }

    private void convert(boolean from, boolean to) {
        if (from == to) return;
        for (Win w : wins) {
            if (to) { w.yaw = HeadTracker.wrap(w.yaw + headYaw); w.pitch += headPitch; }
            else { w.yaw = HeadTracker.wrap(w.yaw - headYaw); w.pitch -= headPitch; }
        }
        if (to) { kbYaw = HeadTracker.wrap(kbYaw + headYaw); kbPitch += headPitch; }
        else { kbYaw = HeadTracker.wrap(kbYaw - headYaw); kbPitch -= headPitch; }
    }

    void setPlacement(int p, long now) {
        if (p == Config.CINEMA) { enterCinema(now); return; }
        if (cinemaWin != null) exitCinema();
        boolean was = spatial();
        cfg.placement = p;
        convert(was, spatial());
        say(p == Config.HUD ? "Modo HUD: janelas presas à visão" : "Modo espacial: janelas fixas no ambiente", now);
    }

    void setTracking(boolean on, long now) {
        boolean was = spatial();
        cfg.headTracking = on;
        convert(was, spatial());
        say(on ? (trackAvail ? "Rastreamento da cabeça ligado" : "Este celular não tem sensor de rotação") : "Rastreamento da cabeça desligado", now);
    }

    void recentered(long now) {
        say("Centro recalibrado · dock na sua frente", now);
        if (kbOpen) { kbYaw = 0; kbPitch = -(float) Math.atan(.1f / f); }
    }

    private void enterCinema(long now) {
        Win t = target();
        Win w = t != null && isWeb(t.app) ? t : (find(YOUTUBE) != null ? find(YOUTUBE) : find(NAV));
        if (w == null) w = openApp(YOUTUBE, now);
        enterCinemaWith(w, now);
    }

    private void enterCinemaWith(Win w, long now) {
        if (cinemaWin != null || w == null) return;
        cinemaBak = new float[]{w.yaw, w.pitch, w.dist, w.w, w.h};
        cinemaWin = w;
        cinemaYaw = spatial() ? headYaw : 0;
        w.minimized = false;
        w.w = 1.8f; w.h = 1.06f; w.dist = 3f;
        cfg.placement = Config.CINEMA;
        host.webAspect(w.app, contentAspect(w));
        say("Modo cinema · B ou o botão da tela saem", now);
    }

    private void exitCinema() {
        if (cinemaWin == null) return;
        Win w = cinemaWin;
        w.yaw = cinemaBak[0]; w.pitch = cinemaBak[1]; w.dist = cinemaBak[2]; w.w = cinemaBak[3]; w.h = cinemaBak[4];
        cinemaWin = null;
        if (cfg.placement == Config.CINEMA) cfg.placement = Config.ESPACIAL;
        host.webAspect(w.app, contentAspect(w));
    }

    boolean cinema() { return cinemaWin != null; }

    private void say(String s, long now) { toast = s; toastT = now; }

    void sayNow(String s) { say(s, now()); }

    boolean closeFront() {
        Win w = front();
        if (w == null) return false;
        closeWin(w);
        return true;
    }

    /* ================= janelas ================= */

    private Win find(int app) { for (Win w : wins) if (w.app == app) return w; return null; }

    Win front() {
        for (int i = wins.size() - 1; i >= 0; i--) if (wins.get(i).visible) return wins.get(i);
        return null;
    }

    private void toFront(Win w) {
        int i = wins.indexOf(w);
        if (i >= 0 && i < wins.size() - 1) { wins.remove(i); wins.add(w); }
    }

    Win openApp(int app, long now) {
        if (app == CINEMA) { if (cinema()) exitCinema(); else enterCinema(now); return null; }
        allHidden = false;
        Win w = find(app);
        if (w != null) { w.minimized = false; toFront(w); if (isWeb(app)) host.webOpen(app); return w; }
        int k = 0;
        for (Win o : wins) if (!o.minimized) k++;
        w = new Win();
        w.id = ++wid; w.app = app;
        float off = ((k % 3) - 1) * .17f, pOff = .15f - ((k / 3) % 2) * .07f;
        if (spatial()) { w.yaw = HeadTracker.wrap(headYaw + off); w.pitch = Gfx.clamp(headPitch, -.4f, .4f) + pOff; }
        else { w.yaw = off; w.pitch = pOff; }
        w.dist = Math.max(cfg.minDist, 1.7f + (k % 2) * .12f);
        w.w = SIZES[app][0]; w.h = SIZES[app][1];
        w.born = now;
        wins.add(w);
        synth.open();
        if (isWeb(app)) { host.webOpen(app); host.webAspect(app, contentAspect(w)); }
        return w;
    }

    private void closeWin(Win w) {
        if (w == cinemaWin) exitCinema();
        wins.remove(w);
    }

    Parts parts(Win w) {
        Parts p = new Parts();
        float hw = w.w / 2, hh = w.h / 2;
        p.hw = hw; p.hh = hh;
        p.body = new RectF(-hw, -hh, hw, hh);
        p.title = new RectF(-hw, -hh, hw, -hh + .062f);
        p.content = new RectF(-hw + .022f, -hh + .074f, hw - .022f, hh - .022f);
        p.grab = new RectF(-.075f, hh + .03f, .075f, hh + .05f);
        return p;
    }

    private float contentAspect(Win w) {
        Parts P = parts(w);
        RectF a = navArea(P);
        return a.width() / Math.max(.01f, a.height());
    }

    private float tbtnX(Parts P, int i) { return P.hw - .032f - i * .044f; }
    private float tbtnY(Parts P) { return -P.hh + .032f; }

    /* ================= teclado virtual ================= */
    static final int K_CHAR = 0, K_SHIFT = 1, K_DEL = 2, K_ENTER = 3, K_SPACE = 4, K_LAYER = 5, K_ACCENT = 6;
    static final int T_WEB = 0, T_URL = 1, T_NOTES = 2;
    static final float KB_STEP = .062f, KB_GAP = .006f;
    static final RectF KB_PANEL = new RectF(-.34f, -.235f, .34f, .2f);

    static final class Key {
        final String label; final int kind; final RectF r;
        Key(String l, int k, RectF r) { label = l; kind = k; this.r = r; }
    }

    boolean kbOpen; int kbTarget; Win kbWin; int kbLayer; boolean kbShift;
    float kbYaw = 0f, kbPitch = -.12f;       // posição do teclado (ângulos; relativos à visão no modo HUD)
    float kbCX, kbCY; boolean kbVis = true;   // centro na visão neste quadro
    float dockCX, dockCY; boolean dockVis = true, dockAnchored;
    private float dockRy;                     // quanto o dock está fora do centro (para a seta)
    final StringBuilder urlBuf = new StringBuilder();
    private final long[] kbFlash = new long[64];
    private List<Key> kbKeys; private int kbKeysLayer = -1;
    private boolean lastWebFocus;

    private float kbScale() { return cfg.uiScaleEff() * (f / .5f) * .95f; }

    private static String[] chars(String s) {
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < s.length(); ) { int cp = s.codePointAt(i); out.add(new String(Character.toChars(cp))); i += Character.charCount(cp); }
        return out.toArray(new String[0]);
    }

    private static String[] row(String first, String mid, String last) {
        ArrayList<String> out = new ArrayList<>();
        out.add(first);
        for (String c : chars(mid)) out.add(c);
        out.add(last);
        return out.toArray(new String[0]);
    }

    private static int kindOf(String t) {
        switch (t) {
            case "#SHIFT": return K_SHIFT;
            case "#DEL": return K_DEL;
            case "#ENTER": return K_ENTER;
            case "#SPACE": return K_SPACE;
            case "#LAYER": return K_LAYER;
            case "#ACCENT": return K_ACCENT;
            default: return K_CHAR;
        }
    }

    private static float units(String t) {
        switch (t) {
            case "#SHIFT": case "#DEL": case "#ENTER": case "#LAYER": return 1.5f;
            case "#ACCENT": return 1.2f;
            case "#SPACE": return 3.8f;
            default: return 1f;
        }
    }

    private List<Key> kbKeys() {
        if (kbKeys != null && kbKeysLayer == kbLayer) return kbKeys;
        String[][] rows;
        String[] last = {"#LAYER", "#ACCENT", ",", "#SPACE", ".", "#ENTER"};
        if (kbLayer == 1) rows = new String[][]{chars("1234567890"), chars("@#$%&*-+()"), chars("!\"':;/?_=~"),
                row("#SHIFT", "<>[]{}\\", "#DEL"), last};
        else if (kbLayer == 2) rows = new String[][]{chars("áàâãéêíóôõ"), chars("úüçñÁÂÃÉÊÍ"), chars("ÓÔÕÚÜÇ¿¡«»"),
                row("#SHIFT", "ªº°§·…•", "#DEL"), last};
        else rows = new String[][]{chars("1234567890"), chars("qwertyuiop"), chars("asdfghjklç"),
                row("#SHIFT", "zxcvbnm", "#DEL"), last};
        ArrayList<Key> out = new ArrayList<>();
        for (int r = 0; r < rows.length; r++) {
            float total = 0;
            for (String t : rows[r]) total += units(t);
            float x = -(total * KB_STEP - KB_GAP) / 2, y = -.15f + r * KB_STEP;
            for (String t : rows[r]) {
                float w = units(t) * KB_STEP - KB_GAP;
                out.add(new Key(t, kindOf(t), new RectF(x, y, x + w, y + KB_STEP - KB_GAP)));
                x += units(t) * KB_STEP;
            }
        }
        kbKeys = out;
        kbKeysLayer = kbLayer;
        return out;
    }

    void openKeyboard(int target, Win w) {
        kbOpen = true; kbTarget = target; kbWin = w; kbLayer = 0; kbShift = false;
        float[] a = viewToAngles(0, .1f);
        kbYaw = a[0]; kbPitch = a[1];
        if (target == T_URL) urlBuf.setLength(0);
        synth.open();
    }

    private StringBuilder kbBuffer() {
        if (kbTarget == T_URL) return urlBuf;
        if (kbTarget == T_NOTES && kbWin != null && wins.contains(kbWin)) return kbWin.text;
        return null;
    }

    private int kbWeb() { return kbWin != null && isWeb(kbWin.app) ? kbWin.app : NAV; }

    private void kbType(String s) {
        if (kbTarget == T_WEB) { host.webInsert(kbWeb(), s); return; }
        StringBuilder b = kbBuffer();
        if (b != null && b.length() < 5000) b.append(s);
    }

    private void kbKey(Key k, long now) {
        switch (k.kind) {
            case K_CHAR: {
                String s = kbShift ? k.label.toUpperCase(new Locale("pt", "BR")) : k.label;
                kbType(s);
                kbShift = false;
                break;
            }
            case K_SHIFT: kbShift = !kbShift; break;
            case K_SPACE: kbType(" "); break;
            case K_LAYER: kbLayer = kbLayer == 1 ? 0 : 1; break;
            case K_ACCENT: kbLayer = kbLayer == 2 ? 0 : 2; break;
            case K_DEL: {
                if (kbTarget == T_WEB) { host.webKey(kbWeb(), Host.KEY_DEL); break; }
                StringBuilder b = kbBuffer();
                if (b != null && b.length() > 0) b.delete(b.offsetByCodePoints(b.length(), -1), b.length());
                break;
            }
            case K_ENTER: {
                if (kbTarget == T_WEB) host.webKey(kbWeb(), Host.KEY_ENTER);
                else if (kbTarget == T_URL) {
                    String u = urlBuf.toString().trim();
                    if (!u.isEmpty()) host.webLoad(kbWeb(), kbWeb() == YOUTUBE ? youtubeUrl(u) : normalizeUrl(u));
                    kbOpen = false;
                    say("Abrindo…", now);
                } else kbType("\n");
                break;
            }
            default: break;
        }
    }

    static String youtubeUrl(String u) {
        if (u.matches("(?i)^https?://.*") || (!u.contains(" ") && u.contains("youtu"))) return normalizeUrl(u);
        try { return "https://m.youtube.com/results?search_query=" + java.net.URLEncoder.encode(u, "UTF-8"); }
        catch (Exception e) { return "https://m.youtube.com/results?search_query=" + u.replace(' ', '+'); }
    }

    static String normalizeUrl(String u) {
        if (u.matches("(?i)^https?://.*")) return u;
        if (!u.contains(" ") && u.contains(".")) return "https://" + u;
        try { return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(u, "UTF-8"); }
        catch (Exception e) { return "https://www.google.com/search?q=" + u.replace(' ', '+'); }
    }

    private void drawKeyboard(int sign, long now) {
        Canvas c = g.c;
        float ks = kbScale();
        c.save();
        c.translate(kbCX + shift(sign, .9f), kbCY);
        c.scale(ks, ks);
        g.glass(KB_PANEL, .032f, false, true);
        // linha de prévia
        RectF pr = new RectF(KB_PANEL.left + .02f, KB_PANEL.top + .014f, KB_PANEL.right - .06f, KB_PANEL.top + .052f);
        g.fill.setColor(0x26FFFFFF);
        c.drawRoundRect(pr, .016f, .016f, g.fill);
        String pv; int pc = Color.WHITE;
        if (kbTarget == T_URL) { pv = urlBuf.length() == 0 ? "Pesquisar ou digitar endereço" : urlBuf + "|"; if (urlBuf.length() == 0) pc = 0x99FFFFFF; }
        else if (kbTarget == T_NOTES) {
            StringBuilder b = kbBuffer();
            String t = b == null ? "" : b.toString();
            int nl = t.lastIndexOf('\n');
            pv = "Notas: " + (nl >= 0 ? t.substring(nl + 1) : t) + "|";
        } else { pv = "Digitando na página"; pc = 0x99FFFFFF; }
        String shown = pv;
        float maxW = pr.width() - .03f;
        while (shown.length() > 1 && g.width(shown, .017f, g.regular) > maxW) shown = shown.substring(1);
        g.txt(shown, pr.left + .015f, pr.centerY(), .017f, pc, Paint.Align.LEFT, g.regular);
        // fechar
        boolean ch = hov.contains("kbclose");
        float cx = KB_PANEL.right - .03f, cy = KB_PANEL.top + .033f;
        g.circle(cx, cy, .016f * (ch ? 1.18f : 1f), ch ? 0xF2FFFFFF : 0x33FFFFFF);
        int ic = ch ? 0xFF15171E : 0xE6FFFFFF;
        g.line(cx - .0065f, cy - .0065f, cx + .0065f, cy + .0065f, .0024f, ic);
        g.line(cx + .0065f, cy - .0065f, cx - .0065f, cy + .0065f, .0024f, ic);
        // teclas
        List<Key> keys = kbKeys();
        Locale br = new Locale("pt", "BR");
        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            boolean h = hov.contains("key:" + i);
            float fl = Math.max(0, 1 - (now - kbFlash[i]) / 180f);
            boolean special = k.kind != K_CHAR && k.kind != K_SPACE;
            boolean active = (k.kind == K_SHIFT && kbShift) || (k.kind == K_ACCENT && kbLayer == 2) || (k.kind == K_LAYER && kbLayer == 1);
            int col = fl > 0 ? 0xF2FFFFFF : h ? 0x99FFFFFF : active ? 0xCC7CC4FF : special ? 0x1AFFFFFF : 0x38FFFFFF;
            c.drawRoundRect(k.r, .012f, .012f, paintOf(col));
            String label;
            switch (k.kind) {
                case K_SHIFT: label = "↑"; break;
                case K_DEL: label = "←"; break;
                case K_ENTER: label = kbTarget == T_URL ? "Ir" : "Enter"; break;
                case K_SPACE: label = "espaço"; break;
                case K_LAYER: label = kbLayer == 1 ? "abc" : "?123"; break;
                case K_ACCENT: label = kbLayer == 2 ? "abc" : "áé"; break;
                default: label = kbShift ? k.label.toUpperCase(br) : k.label; break;
            }
            int tc = (fl > 0 || h) ? 0xFF15171E : Color.WHITE;
            g.txt(label, k.r.centerX(), k.r.centerY(), special || k.kind == K_SPACE ? .018f : .024f, tc, Paint.Align.CENTER,
                    special ? g.medium : g.regular);
        }
        // alça para mover
        boolean gh = hov.contains("kbgrab");
        g.fill.setColor(gh ? 0xF2FFFFFF : 0x80FFFFFF);
        c.drawRoundRect(new RectF(-.075f, KB_PANEL.bottom + .028f, .075f, KB_PANEL.bottom + .046f), .009f, .009f, g.fill);
        c.restore();
    }

    private Paint paintOf(int color) { g.fill.setColor(color); return g.fill; }

    /* ================= toque na cena ================= */

    private float dockScale() {
        return cfg.dockScale * cfg.uiScaleEff() * (f / .5f) * (1.2f / Math.max(.4f, cfg.dockDist));
    }

    private float dockIconX(int i) {
        float sp = .09f * cfg.dockWidth;
        float total = .05f + .11f + .02f + DOCK.length * sp;
        return -total / 2 + .025f + .11f + .02f + sp / 2 + i * sp;
    }

    private RectF dockPill() {
        float sp = .09f * cfg.dockWidth;
        float total = .05f + .11f + .02f + DOCK.length * sp;
        return new RectF(-total / 2, -.06f, total / 2, .06f);
    }

    private RectF clockTile() {
        RectF p = dockPill();
        return new RectF(p.left + .018f, -.042f, p.left + .018f + .11f, .042f);
    }

    private List<Object[]> chips() {
        ArrayList<Object[]> out = new ArrayList<>();
        if (cinema()) out.add(new Object[]{"Sair do cinema", null});
        for (Win w : wins) if (w.minimized && w != cinemaWin) out.add(new Object[]{NAMES[w.app], w});
        return out;
    }

    private RectF chipRect(int i, int n) {
        float cw = .17f, gap = .015f, total = n * cw + (n - 1) * gap;
        float x = -total / 2 + i * (cw + gap);
        return new RectF(x, -.128f, x + cw, -.086f);
    }

    Hit hitTest(float vx, float vy) {
        float hs = cfg.hitScale();
        // ---- teclado ----
        if (kbOpen && kbVis) {
            float ks = kbScale();
            float[] kq = toSpatial(vx, vy);
            float kx = (kq[0] - kbCX) / ks, ky = (kq[1] - kbCY) / ks;
            if (Math.hypot(kx - (KB_PANEL.right - .03f), ky - (KB_PANEL.top + .033f)) < .018f * 1.5f * hs) return new Hit(H_KBCLOSE, "kbclose");
            if (kx > -.09f && kx < .09f && ky > KB_PANEL.bottom + .01f && ky < KB_PANEL.bottom + .06f) return new Hit(H_KBGRAB, "kbgrab");
            List<Key> keys = kbKeys();
            for (int i = 0; i < keys.size(); i++) {
                RectF r = keys.get(i).r;
                float e = .003f * hs;
                if (kx > r.left - e && kx < r.right + e && ky > r.top - e && ky < r.bottom + e) { Hit h = new Hit(H_KEY, "key:" + i); h.index = i; return h; }
            }
            if (KB_PANEL.contains(kx, ky)) return new Hit(H_KBBODY, "kbbody");
        }
        // ---- dock (preso à visão) ----
        float ds = dockScale();
        float[] dq = dockAnchored ? toSpatial(vx, vy) : new float[]{vx, vy};
        float lx = dockVis ? (dq[0] - dockCX) / ds : 99, ly = dockVis ? (dq[1] - dockCY) / ds : 99;
        for (int i = 0; i < DOCK.length; i++) {
            if (Math.hypot(lx - dockIconX(i), ly) < .036f * hs) { Hit h = new Hit(H_DOCK, "dock:" + i); h.index = i; return h; }
        }
        RectF ct = clockTile();
        if (ct.contains(lx, ly)) return new Hit(H_CLOCK, "clock");
        if (lx > -.17f && lx < .17f && ly > .07f && ly < .12f) return new Hit(H_HOMEBAR, "home");
        List<Object[]> ch = chips();
        for (int i = 0; i < ch.size(); i++) {
            RectF r = chipRect(i, ch.size());
            if (r.contains(lx, ly)) {
                Win w = (Win) ch.get(i)[1];
                if (w == null) return new Hit(H_EXITCINEMA, "exitcinema");
                Hit h = new Hit(H_CHIP, "chip:" + w.id); h.win = w; return h;
            }
        }
        if (dockPill().contains(lx, ly)) return new Hit(H_DOCKBODY, "dockbody");

        // ---- janelas (no espaço) ----
        float[] q = toSpatial(vx, vy);
        for (int i = wins.size() - 1; i >= 0; i--) {
            Win w = wins.get(i);
            if (!w.visible) continue;
            float wx = (q[0] - w.cx) / w.s, wy = (q[1] - w.cy) / w.s;
            Parts P = parts(w);
            for (int b = 0; b < TBTN.length; b++) {
                if (Math.hypot(wx - tbtnX(P, b), wy - tbtnY(P)) < .018f * 1.4f * hs) {
                    Hit h = new Hit(H_TBTN, "tbtn:" + w.id + ":" + TBTN[b]); h.win = w; h.id = TBTN[b]; return h;
                }
            }
            RectF gb = P.grab;
            if (wx > gb.left - .03f && wx < gb.right + .03f && wy > gb.top - .025f && wy < gb.bottom + .025f) {
                Hit h = new Hit(H_GRAB, "grab:" + w.id); h.win = w; return h;
            }
            if (Math.hypot(wx - P.hw, wy - P.hh) < .04f * hs) { Hit h = new Hit(H_RESIZE, "resize:" + w.id); h.win = w; return h; }
            if (P.body.contains(wx, wy)) {
                for (Btn bt : buttons(w, P)) if (bt.hit(wx, wy, hs)) {
                    Hit h = new Hit(H_BTN, "btn:" + w.id + ":" + bt.id); h.win = w; h.id = bt.id; return h;
                }
                RectF area = area(w, P);
                if (area != null && area.contains(wx, wy)) { Hit h = new Hit(H_AREA, "area:" + w.id); h.win = w; return h; }
                if (P.title.contains(wx, wy)) { Hit h = new Hit(H_GRAB, "grab:" + w.id); h.win = w; return h; }
                Hit h = new Hit(H_BODY, "body:" + w.id); h.win = w; return h;
            }
        }
        return null;
    }

    private static boolean clickable(Hit h) {
        return h != null && (h.type == H_DOCK || h.type == H_CLOCK || h.type == H_HOMEBAR || h.type == H_CHIP
                || h.type == H_TBTN || h.type == H_BTN || h.type == H_EXITCINEMA || h.type == H_KEY || h.type == H_KBCLOSE);
    }

    private float[] local(Win w, float vx, float vy) {
        float[] q = toSpatial(vx, vy);
        return new float[]{(q[0] - w.cx) / w.s, (q[1] - w.cy) / w.s};
    }

    private void press(Ptr p, Hit h, long now) {
        switch (h.type) {
            case H_DOCK: synth.click(); openApp(DOCK[h.index], now); break;
            case H_CLOCK: synth.click(); openApp(RELOGIO, now); break;
            case H_HOMEBAR: case H_DOCKBODY:
                p.dockDrag = true; p.dockHome = h.type == H_HOMEBAR; p.dockMoved = false;
                { float[] q0 = dockAnchored ? toSpatial(p.x, p.y) : new float[]{p.x, p.y}; p.sx = q0[0]; p.sy = q0[1]; }
                p.ox = dockCX; p.oy = dockCY;
                break;
            case H_KBGRAB: case H_KBBODY:
                { float[] q0 = toSpatial(p.x, p.y); p.kbDrag = true; p.sx = q0[0]; p.sy = q0[1]; p.ox = kbCX; p.oy = kbCY; }
                break;
            case H_KBCLOSE: synth.click(); kbOpen = false; break;
            case H_KEY: {
                List<Key> keys = kbKeys();
                if (h.index < keys.size()) { synth.click(); kbFlash[h.index] = now; kbKey(keys.get(h.index), now); }
                break;
            }
            case H_CHIP: synth.click(); h.win.minimized = false; allHidden = false; toFront(h.win); break;
            case H_EXITCINEMA: synth.click(); exitCinema(); break;
            case H_TBTN: pressTitle(h.win, h.id, now); break;
            case H_GRAB: {
                toFront(h.win);
                if (h.win == cinemaWin) break;
                float[] q = toSpatial(p.x, p.y);
                p.drag = h.win; p.dx = h.win.cx - q[0]; p.dy = h.win.cy - q[1];
                p.wy0 = h.win.yaw; p.wp0 = h.win.pitch;
                break;
            }
            case H_RESIZE: toFront(h.win); if (h.win != cinemaWin) p.resize = h.win; break;
            case H_BTN: toFront(h.win); pressBtn(h.win, h.id, now); break;
            case H_AREA: {
                toFront(h.win);
                Win w = h.win;
                float[] l = local(w, p.x, p.y);
                if (w.app == DESENHO) {
                    Stroke s = new Stroke(); s.c = w.pen; s.pts.add(l); w.strokes.add(s);
                } else if (isWeb(w.app)) {
                    // ainda não sabemos se é clique ou rolagem: decide ao mexer ou soltar
                    RectF a = navArea(parts(w));
                    p.webMode = 0;
                    p.wfx = p.wlx = (l[0] - a.left) / a.width();
                    p.wfy = p.wly = (l[1] - a.top) / a.height();
                }
                break;
            }
            default: if (h.win != null) toFront(h.win); break;
        }
    }

    private void toggleWindows(long now) {
        synth.click();
        boolean any = false;
        for (Win w : wins) if (w.visible) any = true;
        allHidden = any;
        if (!any) for (Win w : wins) w.minimized = false;
        say(any ? "Janelas ocultas · toque de novo para mostrar" : "Janelas de volta", now);
    }

    private void pressTitle(Win w, String id, long now) {
        synth.click();
        switch (id) {
            case "close": closeWin(w); break;
            case "min": if (w == cinemaWin) exitCinema(); w.minimized = true; break;
            case "far": w.dist = Math.min(4.5f, w.dist + .25f); say(String.format(Locale.US, "Distância: %.2f m", w.dist), now); break;
            case "near": w.dist = Math.max(cfg.minDist, w.dist - .25f); say(String.format(Locale.US, "Distância: %.2f m", w.dist), now); break;
            case "layer":
                w.behind = !w.behind;
                say(w.behind ? (cfg.segMode == Config.SEG_OFF ? "Atrás da pessoa (ligue o recorte de pessoas em Ajustes)" : "Janela atrás da pessoa")
                        : "Janela na frente", now);
                break;
            default: break;
        }
    }

    private void hold(Ptr p) {
        if (p.dockDrag) {
            float[] q = dockAnchored ? toSpatial(p.x, p.y) : new float[]{p.x, p.y};
            float dx = q[0] - p.sx, dy = q[1] - p.sy;
            if (!p.dockMoved && Math.hypot(dx, dy) > .012f) p.dockMoved = true;
            if (p.dockMoved) {
                float tx = p.ox + dx, ty = p.oy + dy;
                if (dockAnchored) {
                    float yaw = Gfx.clamp(HeadTracker.wrap((float) Math.atan(tx / f) + headYaw), -1.2f, 1.2f);
                    float pitch = Gfx.clamp(headPitch - (float) Math.atan(ty / f), -1.1f, 1.1f);
                    cfg.dockX = f * (float) Math.tan(yaw);
                    cfg.dockY = -f * (float) Math.tan(pitch);
                } else { cfg.dockX = Gfx.clamp(tx, -.45f, .45f); cfg.dockY = Gfx.clamp(ty, -.42f, .46f); }
            }
            return;
        }
        if (p.kbDrag) {
            float[] q = toSpatial(p.x, p.y);
            float[] a = viewToAngles(p.ox + q[0] - p.sx, p.oy + q[1] - p.sy);
            kbYaw = a[0]; kbPitch = a[1];
            return;
        }
        if (p.drag != null) {
            Win w = p.drag;
            float[] q = toSpatial(p.x, p.y);
            float x = q[0] + p.dx, y = q[1] + p.dy;
            float ry = (float) Math.atan(x / f), rp = (float) Math.atan(y / f);
            if (spatial()) { w.yaw = HeadTracker.wrap(ry + headYaw); w.pitch = headPitch - rp; }
            else { w.yaw = ry; w.pitch = -rp; }
            return;
        }
        if (p.resize != null) {
            Win w = p.resize;
            float[] l = local(w, p.x, p.y);
            w.w = Gfx.clamp(2 * Math.abs(l[0]), .4f, 1.7f);
            w.h = Gfx.clamp(2 * Math.abs(l[1]), .28f, 1.1f);
            return;
        }
        if (p.hit != null && p.hit.type == H_AREA && wins.contains(p.hit.win)) {
            Win w = p.hit.win;
            float[] l = local(w, p.x, p.y);
            if (w.app == DESENHO) {
                if (w.strokes.isEmpty()) return;
                Stroke s = w.strokes.get(w.strokes.size() - 1);
                float[] last = s.pts.get(s.pts.size() - 1);
                if (Math.hypot(l[0] - last[0], l[1] - last[1]) > .002f && s.pts.size() < 1500) s.pts.add(l);
            } else if (isWeb(w.app)) {
                RectF a = navArea(parts(w));
                float fx = (l[0] - a.left) / a.width(), fy = (l[1] - a.top) / a.height();
                if (p.webMode == 0 && Math.hypot((fx - p.wfx) * a.width(), (fy - p.wfy) * a.height()) > .018f) {
                    p.webMode = 1;                       // mexeu: vira rolagem
                    synth.hover();
                }
                if (p.webMode == 1) {
                    float pageW = WebPane.WIDTH, pageH = WebPane.WIDTH / Math.max(.3f, a.width() / a.height());
                    int dx = Math.round((p.wlx - fx) * pageW * 1.6f), dy = Math.round((p.wly - fy) * pageH * 1.6f);
                    if (dx != 0 || dy != 0) { host.webScroll(w.app, dx, dy); p.wlx = fx; p.wly = fy; }
                }
            }
        }
    }

    private void release(Ptr p) {
        if (p.hit != null && p.hit.type == H_AREA && p.hit.win != null && isWeb(p.hit.win.app) && p.webMode == 0) {
            // não mexeu: é um clique no ponto onde começou
            Win w = p.hit.win;
            host.webTouch(w.app, MotionEvent.ACTION_DOWN, p.wfx, p.wfy);
            host.webTouch(w.app, MotionEvent.ACTION_UP, p.wfx, p.wfy);
        }
        p.webMode = 0;
        if (p.resize != null && isWeb(p.resize.app)) host.webAspect(p.resize.app, contentAspect(p.resize));
        if (p.dockDrag) {
            if (!p.dockMoved && p.dockHome) toggleWindows(now());
            else if (p.dockMoved) { host.configChanged(); say("Dock movido", now()); }
        }
        p.dockDrag = false; p.dockMoved = false; p.dockHome = false; p.kbDrag = false;
        p.drag = null; p.resize = null; p.hit = null; p.down = false;
    }

    private void feed(Ptr p, float x, float y, boolean down, long now) {
        p.exists = true; p.x = x; p.y = y;
        if (down && !p.down) { p.down = true; p.hit = hitTest(x, y); if (p.hit != null) press(p, p.hit, now); }
        else if (down) hold(p);
        else if (p.down) release(p);
    }

    private void drop(Ptr p) { if (p.exists) { if (p.down) release(p); p.exists = false; } }

    /* ================= entradas ================= */

    void updateHands(float[][] raw, long stamp, float x0, float dw, float y0, float dh, long now) {
        if (stamp == lastStamp) return;
        lastStamp = stamp;
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
            if (h.down ? ratio > .45f : ratio < .28f) h.down = !h.down;
            h.cx = (h.x[4] + h.x[8]) / 2; h.cy = (h.y[4] + h.y[8]) / 2;
        }
    }

    boolean handActive(int i, long now) { return cfg.handInput && hands[i].init && now - hands[i].last < 300; }

    void resetHands() { for (Hand h : hands) h.init = false; }

    void touch(int action, float x, float y, long now) {
        tX = x; tY = y; tLast = now;
        if (action == MotionEvent.ACTION_DOWN) tLatch.press();
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) tLatch.release();
    }

    void hover(float x, float y, long now) { tX = x; tY = y; tLast = now; }

    static boolean handlesKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_R1: case KeyEvent.KEYCODE_BUTTON_R2: case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BUTTON_SELECT: case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_BUTTON_1: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_ESCAPE: case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_BUTTON_X: case KeyEvent.KEYCODE_BUTTON_Y: case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_L2: case KeyEvent.KEYCODE_BUTTON_START: case KeyEvent.KEYCODE_BUTTON_THUMBL:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                return true;
            default: return false;
        }
    }

    static boolean isPadKey(int code) { return KeyEvent.isGamepadButton(code); }

    /**
     * Controle:  A / RT = confirmar (segurar A arrasta pela barra)   B = voltar / cancelar
     *            X = menu de apps   Y = teclado   LB / RB = afastar / aproximar a janela
     *            LT (segurar) = arrastar qualquer janela com a mira   ≡ (Start) = Ajustes
     *            ⧉ (View) = recentralizar   analógico esq. = ajustar a mira (L3 centraliza)
     *            analógico dir. = rolar a página   direcional = mover a mira
     */
    boolean onKey(int code, boolean down, long now) {
        if (isPadKey(code)) {
            padSeen = now;
            gazeForced = true;
            if (down) padDown.add(code); else padDown.remove(code);
        }
        switch (code) {
            case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_R2: case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_BUTTON_1:
                gazeForced = true;
                if (down) gLatch.press(); else gLatch.release();
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_BUTTON_SELECT:
                if (down) host.recenter();
                return true;
            case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_ESCAPE:
                if (down) goBack(now);
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (down) { synth.click(); Win m = find(MENU); if (m != null && m.visible && front() == m) closeWin(m); else openApp(MENU, now); }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (down) toggleKeyboard(now);
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1: case KeyEvent.KEYCODE_BUTTON_R1:
                if (down) {
                    Win w = target();
                    if (w != null) pressTitle(w, code == KeyEvent.KEYCODE_BUTTON_L1 ? "far" : "near", now);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L2:
                setLt(down, now);
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
                if (down) { synth.click(); openApp(AJUSTES, now); }
                return true;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                if (down) { gazeX = 0; gazeY = 0; say("Mira no centro", now); }
                return true;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: if (down) { gazeX = Gfx.clamp(gazeX - .03f, -.4f, .4f); gazeForced = true; } return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (down) { gazeX = Gfx.clamp(gazeX + .03f, -.4f, .4f); gazeForced = true; } return true;
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: {
                if (!down) return true;
                int dir = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                Win w = target();
                if (w != null && isWeb(w.app)) host.webScroll(w.app, 0, dir * 260);       // rola a página
                else { gazeY = Gfx.clamp(gazeY + dir * .03f, -.4f, .4f); gazeForced = true; }
                return true;
            }
            default: return false;
        }
    }

    /** Analógicos e gatilhos do controle (chegam a cada movimento). */
    void setAxes(float lx, float ly, float rx, float ry, float lt, float rt, float hx, float hy, long now) {
        padSeen = now;
        gazeForced = true;
        padLX = lx; padLY = ly; padRX = rx; padRY = ry; padLT = lt; padRT = rt;
        // direcional que chega como eixo (comum no controle de Xbox)
        if (hx != hatX) { if (hx < -.5f) onKey(KeyEvent.KEYCODE_DPAD_LEFT, true, now); else if (hx > .5f) onKey(KeyEvent.KEYCODE_DPAD_RIGHT, true, now); hatX = hx; }
        if (hy != hatY) { if (hy < -.5f) onKey(KeyEvent.KEYCODE_DPAD_UP, true, now); else if (hy > .5f) onKey(KeyEvent.KEYCODE_DPAD_DOWN, true, now); hatY = hy; hatRepeat = now + 250; }
        boolean lt2 = lt > .5f, rt2 = rt > .5f;
        if (lt2 != ltWas) { setLt(lt2, now); ltWas = lt2; }
        if (rt2 != rtWas) { if (rt2) gLatch.press(); else gLatch.release(); rtWas = rt2; }
    }

    /** LT segurado: arrasta a janela que está na mira, segurando em qualquer ponto dela. */
    private void setLt(boolean down, long now) {
        Ptr p = ptrs[3];
        if (down) {
            if (p.down) return;
            Hit h = hitTest(gazeX, gazeY);
            if (h == null || h.win == null || h.win == cinemaWin) return;
            Hit g2 = new Hit(H_GRAB, "grab:" + h.win.id);
            g2.win = h.win;
            p.exists = true; p.x = gazeX; p.y = gazeY; p.down = true; p.hit = g2;
            press(p, g2, now);
            ltHolding = true;
        } else if (ltHolding) {
            ltHolding = false;
            release(p);
        }
    }
    private boolean ltHolding;

    /** Janela sob a mira (ou a da frente). */
    private Win target() {
        Hit h = hitTest(gazeX, gazeY);
        if (h != null && h.win != null) return h.win;
        return front();
    }

    /** B: cancela o arraste, fecha o teclado, sai da foto aberta ou fecha a janela. */
    private void goBack(long now) {
        synth.click();
        Ptr p = ptrs[3];
        if (p.down && p.drag != null) {
            p.drag.yaw = p.wy0; p.drag.pitch = p.wp0;
            p.drag = null; p.hit = null; ltHolding = false;
            say("Cancelado", now);
            return;
        }
        if (kbOpen) { kbOpen = false; return; }
        if (cinemaWin != null) { exitCinema(); return; }
        Win w = target();
        if (w == null) return;
        if (w.app == GALERIA && w.galSel >= 0) { w.galSel = -1; return; }
        closeWin(w);
    }

    private void toggleKeyboard(long now) {
        if (kbOpen) { kbOpen = false; return; }
        Win w = target();
        if (w != null && isWeb(w.app)) openKeyboard(T_URL, w);
        else if (w != null && w.app == NOTAS) openKeyboard(T_NOTES, w);
        else {
            Win n = openApp(NOTAS, now);
            if (n != null) openKeyboard(T_NOTES, n);
        }
    }

    private long hatRepeat;

    /** Aplica os analógicos a cada quadro. */
    private void updatePad(long now, float dt) {
        if (!padActive(now)) return;
        if (Math.abs(hatY) > .5f && now - hatRepeat > 140) {          // setinha segurada: continua rolando
            hatRepeat = now;
            Win w = target();
            if (w != null && isWeb(w.app)) host.webScroll(w.app, 0, (hatY > 0 ? 1 : -1) * 220);
        }
        final float dz = .18f;
        float lx = Math.abs(padLX) > dz ? padLX : 0, ly = Math.abs(padLY) > dz ? padLY : 0;
        if (lx != 0 || ly != 0) {
            gazeX = Gfx.clamp(gazeX + lx * Math.abs(lx) * .55f * dt, -.4f, .4f);
            gazeY = Gfx.clamp(gazeY + ly * Math.abs(ly) * .55f * dt, -.4f, .4f);
        }
        float rx = Math.abs(padRX) > dz ? padRX : 0, ry = Math.abs(padRY) > dz ? padRY : 0;
        if (rx != 0 || ry != 0) {
            Win w = target();
            if (w != null && isWeb(w.app)) {
                scrollAccX += rx * Math.abs(rx) * 1900 * dt;
                scrollAccY += ry * Math.abs(ry) * 1900 * dt;
                if (now - lastScroll > 33) {
                    host.webScroll(w.app, Math.round(scrollAccX), Math.round(scrollAccY));
                    scrollAccX = 0; scrollAccY = 0; lastScroll = now;
                }
            } else if (w != null && (w.app == GALERIA || w.app == ARQUIVOS) && now - lastScroll > 350 && Math.abs(rx) > .6f) {
                if (w.app == GALERIA && w.galSel >= 0) pressBtn(w, rx > 0 ? "gnext" : "gprev", now);
                else pressBtn(w, rx > 0 ? "pnext" : "pprev", now);
                lastScroll = now;
            }
        }
    }

    /** Navegador na frente (NAV ou YOUTUBE) ou -1. */
    int webFocusId() { Win w = front(); return w != null && isWeb(w.app) ? w.app : -1; }

    void update(long now, float dt) {
        layout();
        updatePad(now, dt);
        // mãos
        for (int i = 0; i < 2; i++) {
            if (handActive(i, now)) feed(ptrs[i], hands[i].cx, hands[i].cy, hands[i].down, now);
            else { hands[i].init = hands[i].init && now - hands[i].last < 300; drop(ptrs[i]); }
        }
        // toque / mouse
        if (tLatch.down || now - tLast < 1200) feed(ptrs[2], tX, tY, tLatch.consume(), now);
        else drop(ptrs[2]);
        // ponteiro central
        if (cfg.gazePointer || gazeForced) {
            if (cfg.dwell && !ptrs[3].down) {
                Hit h = hitTest(gazeX, gazeY);
                String k = clickable(h) ? h.key : null;
                if (k != null && k.equals(dwellKey)) {
                    if (!dwellFired && now - dwellStart > cfg.dwellMs()) { dwellFired = true; gLatch.press(); gLatch.release(); }
                } else { dwellKey = k; dwellStart = now; dwellFired = false; }
            } else if (!cfg.dwell) dwellKey = null;
            feed(ptrs[3], gazeX, gazeY, gLatch.consume(), now);
        } else drop(ptrs[3]);

        hov.clear();
        String gazeKey = null;
        for (int i = 0; i < ptrs.length; i++) {
            Ptr p = ptrs[i];
            if (!p.exists) continue;
            Hit h = (p.down && p.hit != null) ? p.hit : hitTest(p.x, p.y);
            if (h != null) { hov.add(h.key); if (i == 3 && clickable(h)) gazeKey = h.key; }
            if (p.drag != null) hov.add("grab:" + p.drag.id);
            if (p.resize != null) hov.add("resize:" + p.resize.id);
            if (p.dockDrag) hov.add("home");
            if (p.kbDrag) hov.add("kbgrab");
        }
        if (gazeKey != null && !gazeKey.equals(lastGazeKey)) synth.hover();
        lastGazeKey = gazeKey;

        int fid = webFocusId();
        boolean wf = fid >= 0 && host.webInputFocused(fid);
        if (wf && !lastWebFocus && !(kbOpen && kbTarget == T_WEB)) openKeyboard(T_WEB, front());
        if (!wf && lastWebFocus && kbOpen && kbTarget == T_WEB) kbOpen = false;
        lastWebFocus = wf;

        float rate = Math.min(1, dt * 12 * cfg.animSpeed);
        for (int i = 0; i < DOCK.length; i++) dockAnim[i] += ((hov.contains("dock:" + i) ? 1 : 0) - dockAnim[i]) * rate;
        dockAnim[DOCK.length] += ((hov.contains("clock") ? 1 : 0) - dockAnim[DOCK.length]) * rate;

        // bolhas
        ArrayList<float[]> tips = new ArrayList<>();
        for (int i = 0; i < 2; i++) if (handActive(i, now)) tips.add(toSpatial(hands[i].x[8], hands[i].y[8]));
        if (tLatch.down) tips.add(toSpatial(tX, tY));
        for (Win w : new ArrayList<>(wins)) if (w.app == BOLHAS && w.visible) updateBubbles(w, parts(w), dt, tips);
    }

    /* ================= botões dos apps ================= */

    private RectF navArea(Parts P) {
        RectF C = P.content;
        return new RectF(C.left, C.top + .056f, C.right, C.bottom);
    }

    private RectF area(Win w, Parts P) {
        if (w.app == DESENHO) return P.content;
        if (isWeb(w.app)) return navArea(P);
        return null;
    }

    private int galleryCols(Parts P) { return Math.max(3, Math.round(P.content.width() / .145f)); }

    private List<Btn> buttons(Win w, Parts P) {
        ArrayList<Btn> out = new ArrayList<>();
        RectF B = P.body, C = P.content;
        switch (w.app) {
            case NAV: case YOUTUBE: {
                float y = C.top + .024f;
                out.add(new Btn("back", C.left + .022f, y, .018f));
                out.add(new Btn("reload", C.left + .066f, y, .018f));
                out.add(new Btn("addr", new RectF(C.left + .1f, y - .019f, C.right - .104f, y + .019f)));
                out.add(new Btn("kb", C.right - .066f, y, .018f));
                out.add(new Btn("cine", C.right - .022f, y, .018f));
                break;
            }
            case NOTAS:
                out.add(new Btn("nkb", new RectF(C.right - .25f, C.bottom - .045f, C.right - .125f, C.bottom - .008f)));
                out.add(new Btn("nclr", new RectF(C.right - .115f, C.bottom - .045f, C.right, C.bottom - .008f)));
                break;
            case GALERIA: {
                List<Host.GalleryItem> items = host.galleryItems();
                if (w.galSel >= 0) {
                    out.add(new Btn("gback", new RectF(C.left, C.bottom - .045f, C.left + .13f, C.bottom - .005f)));
                    out.add(new Btn("gprev", C.right - .085f, C.bottom - .025f, .019f));
                    out.add(new Btn("gnext", C.right - .025f, C.bottom - .025f, .019f));
                } else {
                    int cols = galleryCols(P), rowsN = 3, per = cols * rowsN;
                    float gap = .012f, cw = (C.width() - gap * (cols - 1)) / cols, chh = (C.height() - .06f - gap * (rowsN - 1)) / rowsN;
                    int start = w.page * per;
                    for (int i = 0; i < per && start + i < items.size(); i++) {
                        float x = C.left + (i % cols) * (cw + gap), y = C.top + (i / cols) * (chh + gap);
                        out.add(new Btn("t" + (start + i), new RectF(x, y, x + cw, y + chh)));
                    }
                    out.add(new Btn("pprev", C.right - .085f, C.bottom - .025f, .019f));
                    out.add(new Btn("pnext", C.right - .025f, C.bottom - .025f, .019f));
                    if (!host.galleryAllowed()) out.add(new Btn("perm", new RectF(C.left, C.bottom - .045f, C.left + .3f, C.bottom - .005f)));
                }
                break;
            }
            case ARQUIVOS: {
                List<File> files = host.listFiles();
                float rh = .05f; int per = Math.max(1, (int) ((C.height() - .09f) / rh));
                int start = w.page * per;
                for (int i = 0; i < per && start + i < files.size(); i++) {
                    float y = C.top + .04f + i * rh;
                    out.add(new Btn("f" + (start + i), new RectF(C.left, y, C.right, y + rh - .006f)));
                }
                out.add(new Btn("pprev", C.right - .085f, C.bottom - .025f, .019f));
                out.add(new Btn("pnext", C.right - .025f, C.bottom - .025f, .019f));
                break;
            }
            case WEB: {
                float gap = .016f, cw = (C.width() - gap * 2) / 3, chh = (C.height() - gap) / 2;
                for (int i = 0; i < SITES.length; i++) {
                    float x = C.left + (i % 3) * (cw + gap), y = C.top + (i / 3) * (chh + gap);
                    out.add(new Btn("s" + i, new RectF(x, y, x + cw, y + chh)));
                }
                break;
            }
            case MENU: {
                int cols = 6;
                float cw = C.width() / cols, chh = C.height() / 2;
                for (int i = 0; i < MENU_APPS.length; i++) {
                    float x = C.left + (i % cols) * cw + cw / 2, y = C.top + (i / cols) * chh + chh * .42f;
                    out.add(new Btn("m" + i, x, y, Math.min(cw, chh) * .3f));
                }
                break;
            }
            case AJUSTES: {
                List<Row> rs = rows();
                int perCol = (rs.size() + 1) / 2;
                float gap = .012f, colW = (C.width() - gap) / 2, rh = (C.height() - gap * (perCol - 1)) / perCol;
                for (int i = 0; i < rs.size(); i++) {
                    Row r = rs.get(i);
                    float x = C.left + (i / perCol) * (colW + gap), y = C.top + (i % perCol) * (rh + gap);
                    float cy = y + rh / 2, right = x + colW - .012f;
                    if (r.kind == Row.CYCLE || r.kind == Row.STEP) {
                        out.add(new Btn("r" + i + "m", right - .068f, cy, .017f));
                        out.add(new Btn("r" + i + "p", right - .018f, cy, .017f));
                    } else {
                        out.add(new Btn("r" + i + "t", new RectF(right - .13f, cy - .018f, right, cy + .018f)));
                    }
                }
                break;
            }
            case CAMERA:
                out.add(new Btn("shot", C.right - .05f, C.centerY() - .05f, .032f));
                out.add(new Btn("rec", C.right - .05f, C.centerY() + .045f, .028f));
                out.add(new Btn("swap", new RectF(C.right - .1f, C.bottom - .045f, C.right, C.bottom - .008f)));
                break;
            case DESENHO:
                for (int i = 0; i < 5; i++) out.add(new Btn("c" + i, B.right - .44f + i * .03f, B.top + .032f, .0105f));
                out.add(new Btn("clear", new RectF(B.right - .28f, B.top + .018f, B.right - .22f, B.top + .046f)));
                break;
            case MUSICA: {
                float gap = .014f, pw = (C.width() - gap * 2) / 3, ph = (C.height() - gap) / 2;
                for (int i = 0; i < 6; i++) {
                    float x = C.left + (i % 3) * (pw + gap), y = C.top + (i / 3) * (ph + gap);
                    out.add(new Btn("p" + i, new RectF(x, y, x + pw, y + ph)));
                }
                break;
            }
            default: break;
        }
        return out;
    }

    private void pressBtn(Win w, String id, long now) {
        synth.click();
        switch (w.app) {
            case NAV: case YOUTUBE:
                if (id.equals("back")) host.webBack(w.app);
                else if (id.equals("reload")) host.webReload(w.app);
                else if (id.equals("cine")) { if (cinemaWin == w) exitCinema(); else { exitCinema(); enterCinemaWith(w, now); } }
                else if (id.equals("addr")) openKeyboard(T_URL, w);
                else if (id.equals("kb")) { if (kbOpen && kbTarget == T_WEB) kbOpen = false; else openKeyboard(T_WEB, w); }
                break;
            case NOTAS:
                if (id.equals("nkb")) { if (kbOpen && kbTarget == T_NOTES && kbWin == w) kbOpen = false; else openKeyboard(T_NOTES, w); }
                else if (id.equals("nclr")) w.text.setLength(0);
                break;
            case GALERIA: {
                int n = host.galleryItems().size();
                if (id.equals("gback")) w.galSel = -1;
                else if (id.equals("gprev")) w.galSel = n == 0 ? -1 : (w.galSel - 1 + n) % n;
                else if (id.equals("gnext")) w.galSel = n == 0 ? -1 : (w.galSel + 1) % n;
                else if (id.equals("pprev")) w.page = Math.max(0, w.page - 1);
                else if (id.equals("pnext")) { int per = galleryCols(parts(w)) * 3; if ((w.page + 1) * per < n) w.page++; }
                else if (id.equals("perm")) host.requestGallery();
                else if (id.startsWith("t")) w.galSel = Integer.parseInt(id.substring(1));
                break;
            }
            case ARQUIVOS: {
                List<File> files = host.listFiles();
                if (id.equals("pprev")) w.page = Math.max(0, w.page - 1);
                else if (id.equals("pnext")) w.page++;
                else if (id.startsWith("f")) {
                    int idx = Integer.parseInt(id.substring(1));
                    if (idx < files.size()) {
                        File fl = files.get(idx);
                        List<Host.GalleryItem> items = host.galleryItems();
                        Win gw = openApp(GALERIA, now);
                        if (gw != null) for (int i = 0; i < items.size(); i++)
                            if (items.get(i).file != null && items.get(i).file.equals(fl)) { gw.galSel = i; break; }
                    }
                }
                break;
            }
            case WEB:
                if (id.startsWith("s")) {
                    int site = id.charAt(1) - '0';
                    int app = site == 0 ? YOUTUBE : NAV;
                    openApp(app, now);
                    host.webLoad(app, SITES[site][1]);
                }
                break;
            case MENU:
                if (id.startsWith("m")) openApp(MENU_APPS[Integer.parseInt(id.substring(1))], now);
                break;
            case AJUSTES: {
                int i = Integer.parseInt(id.substring(1, id.length() - 1));
                char k = id.charAt(id.length() - 1);
                Row r = rows().get(i);
                if (k == 'm' && r.minus != null) r.minus.run();
                else if (r.plus != null) r.plus.run();
                host.configChanged();
                break;
            }
            case CAMERA:
                if (id.equals("shot")) {
                    Bitmap fr = frame;
                    if (fr != null) { host.savePhoto(fr); say("Foto salva na Galeria", now); }
                    else say("Sem imagem da câmera", now);
                    w.shot = now;
                    synth.shutter();
                } else if (id.equals("rec")) {
                    if (host.isRecording()) { host.stopRecording(); say("Gravação salva na galeria (Filmes › Visão Box)", now); }
                    else { host.startRecording(); say("Gravando a realidade mista…", now); }
                } else if (id.equals("swap")) host.switchCamera();
                break;
            case DESENHO:
                if (id.equals("clear")) w.strokes.clear();
                else w.pen = PEN[id.charAt(1) - '0'];
                break;
            case MUSICA: {
                int i = id.charAt(1) - '0';
                synth.note(i);
                w.flash[i] = now;
                break;
            }
            default: break;
        }
    }

    private int navSite(int chip) { return new int[]{0, 1, 2, 4}[chip]; }

    private List<Row> rows() {
        if (rows != null) return rows;
        final String[] place = {"HUD", "Espacial", "Cinema"};
        final String[] bg = {"Câmera", "Virtual 3D", "Escuro"};
        final String[] seg = {"Desligado", "Recorte", "Silhueta", "Contorno"};
        ArrayList<Row> r = new ArrayList<>();
        r.add(new Row("Posição das janelas", Row.CYCLE, () -> place[cfg.placement],
                () -> setPlacement((cfg.placement + 2) % 3, now()), () -> setPlacement((cfg.placement + 1) % 3, now())));
        r.add(new Row("Fundo", Row.CYCLE, () -> bg[cfg.background],
                () -> cfg.background = (cfg.background + 2) % 3, () -> cfg.background = (cfg.background + 1) % 3));
        r.add(new Row("Rastrear a cabeça", Row.TOGGLE, () -> cfg.headTracking ? "Ligado" : "Desligado",
                null, () -> setTracking(!cfg.headTracking, now())));
        r.add(new Row("Centro da visão", Row.ACTION, () -> "Recentralizar", null, () -> host.recenter()));
        r.add(new Row("Dock", Row.TOGGLE, () -> cfg.dockFollow ? "Segue a cabeça" : "Fixo no ambiente",
                null, () -> cfg.dockFollow = !cfg.dockFollow));
        r.add(new Row("Recorte de pessoas", Row.CYCLE, () -> seg[cfg.segMode],
                () -> cfg.segMode = (cfg.segMode + 3) % 4, () -> cfg.segMode = (cfg.segMode + 1) % 4));
        r.add(new Row("Ponteiro central", Row.TOGGLE, () -> cfg.gazePointer ? "Ligado" : "Desligado",
                null, () -> { cfg.gazePointer = !cfg.gazePointer; gazeForced = false; }));
        r.add(new Row("Tocar olhando parado", Row.TOGGLE, () -> cfg.dwell ? "Ligado" : "Desligado",
                null, () -> cfg.dwell = !cfg.dwell));
        r.add(new Row("Navegação simplificada", Row.TOGGLE, () -> cfg.simple ? "Ligada" : "Desligada",
                null, () -> cfg.simple = !cfg.simple));
        r.add(new Row("Vídeos no navegador", Row.TOGGLE, () -> cfg.webGpu ? "Ligado" : "Desligado",
                null, () -> cfg.webGpu = !cfg.webGpu));
        r.add(new Row("Rastrear as mãos", Row.TOGGLE, () -> cfg.handInput ? "Ligado" : "Desligado",
                null, () -> cfg.handInput = !cfg.handInput));
        r.add(new Row("Distância dos olhos (IPD)", Row.STEP, () -> Math.round(cfg.ipdMm) + " mm",
                () -> cfg.ipdMm = Gfx.clamp(cfg.ipdMm - 1, 50, 76), () -> cfg.ipdMm = Gfx.clamp(cfg.ipdMm + 1, 50, 76)));
        r.add(new Row("Distorção da lente", Row.STEP, () -> String.valueOf(Math.round(cfg.k1 * 100)),
                () -> cfg.k1 = Gfx.clamp(cfg.k1 - .02f, 0, .6f), () -> cfg.k1 = Gfx.clamp(cfg.k1 + .02f, 0, .6f)));
        r.add(new Row("Tamanho das janelas", Row.STEP, () -> Math.round(cfg.uiScale * 100) + "%",
                () -> cfg.uiScale = Gfx.clamp(cfg.uiScale - .05f, .8f, 1.8f), () -> cfg.uiScale = Gfx.clamp(cfg.uiScale + .05f, .8f, 1.8f)));
        r.add(new Row("Tamanho do texto", Row.STEP, () -> Math.round(cfg.textScale * 100) + "%",
                () -> cfg.textScale = Gfx.clamp(cfg.textScale - .05f, 1f, 1.8f), () -> cfg.textScale = Gfx.clamp(cfg.textScale + .05f, 1f, 1.8f)));
        r.add(new Row("Brilho da câmera", Row.STEP, () -> String.valueOf(Math.round(cfg.camBrightness * 100)),
                () -> cfg.camBrightness = Gfx.clamp(cfg.camBrightness - .05f, -.5f, .5f),
                () -> cfg.camBrightness = Gfx.clamp(cfg.camBrightness + .05f, -.5f, .5f)));
        r.add(new Row("VR box", Row.ACTION, () -> "Calibrar", null, () -> host.startCalibration()));
        rows = r;
        return r;
    }

    private static long now() { return android.os.SystemClock.uptimeMillis(); }

    /* ================= atualização dos apps ================= */

    private void updateBubbles(Win w, Parts P, float dt, List<float[]> tips) {
        RectF C = P.content;
        w.t += dt;
        if (w.t > .5f && w.bubbles.size() < 9) {
            w.t = 0;
            Bubble b = new Bubble();
            b.r = .022f + rnd.nextFloat() * .022f;
            b.x = C.left + b.r + rnd.nextFloat() * (C.width() - 2 * b.r);
            b.y = C.bottom - b.r;
            b.vy = -(.045f + rnd.nextFloat() * .06f);
            b.ph = rnd.nextFloat() * 6.28f;
            w.bubbles.add(b);
        }
        Iterator<Bubble> it = w.bubbles.iterator();
        while (it.hasNext()) {
            Bubble b = it.next();
            b.y += b.vy * dt; b.ph += dt * 2; b.dx = (float) Math.sin(b.ph) * .01f;
            if (b.y - b.r < C.top) { it.remove(); continue; }
            for (float[] tp : tips) {
                float tx = (tp[0] - w.cx) / w.s, ty = (tp[1] - w.cy) / w.s;
                if (!C.contains(tx, ty)) continue;
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

    /* ================= desenho: janelas ================= */

    /** layer: 0 = todas, 1 = só as que ficam atrás da pessoa, 2 = só as da frente. */
    void drawWindows(int sign, int layer, long now) {
        Canvas c = g.c;
        c.save();
        if (spatial() && roll != 0) c.rotate((float) -Math.toDegrees(roll));
        for (Win w : wins) {
            if (!w.visible) continue;
            if (layer == 1 && !w.behind) continue;
            if (layer == 2 && w.behind) continue;
            drawWindow(w, sign, now);
        }
        c.restore();
    }

    private void drawWindow(Win w, int sign, long now) {
        Canvas c = g.c;
        Parts P = parts(w);
        float a = Gfx.ease((now - w.born) * cfg.animSpeed / 280f);
        float sc = w.s * (.9f + .1f * a);
        c.save();
        c.translate(w.cx + shift(sign, w.dist), w.cy);
        c.scale(sc, sc);
        int alpha = Math.round(255 * a);
        if (alpha < 255) c.saveLayerAlpha(P.body.left - .08f, P.body.top - .08f, P.body.right + .08f, P.body.bottom + .1f, alpha);

        g.glass(P.body, .036f, false, true);
        g.line(P.body.left + .02f, P.title.bottom, P.body.right - .02f, P.title.bottom, .0016f, 0x22FFFFFF);
        // título
        String title = NAMES[w.app];
        if (isWeb(w.app)) { String t = host.webTitle(w.app); if (t != null && !t.isEmpty()) title = t; }
        float titleMax = P.body.width() - .27f;
        g.circle(P.body.left + .034f, tbtnY(P), .012f, COLS[w.app][1]);
        g.txt(g.fit(title, .022f, titleMax, g.medium), P.body.left + .056f, tbtnY(P), .022f, 0xE6FFFFFF, Paint.Align.LEFT, g.medium);
        // controles da barra
        for (int i = 0; i < TBTN.length; i++) {
            float bx = tbtnX(P, i), by = tbtnY(P);
            boolean h = hov.contains("tbtn:" + w.id + ":" + TBTN[i]);
            boolean on = TBTN[i].equals("layer") && w.behind;
            g.circle(bx, by, .016f * (h ? 1.18f : 1f), h ? 0xF2FFFFFF : (on ? 0xCC7CC4FF : 0x33FFFFFF));
            int ic = h ? 0xFF15171E : 0xE6FFFFFF;
            float q = .0065f;
            switch (TBTN[i]) {
                case "close": g.line(bx - q, by - q, bx + q, by + q, .0024f, ic); g.line(bx + q, by - q, bx - q, by + q, .0024f, ic); break;
                case "min": g.line(bx - q, by + .002f, bx + q, by + .002f, .0026f, ic); break;
                case "far": g.line(bx - q, by, bx + q, by, .0026f, ic); break;
                case "near": g.line(bx - q, by, bx + q, by, .0026f, ic); g.line(bx, by - q, bx, by + q, .0026f, ic); break;
                case "layer":
                    g.ring(bx, by, .007f, .0022f, ic);
                    g.fill.setColor(ic);
                    c.drawArc(new RectF(bx - .007f, by - .007f, bx + .007f, by + .007f), 90, 180, true, g.fill);
                    break;
                default: break;
            }
            if (h) g.txt(TBTN_TIP[i], bx, P.title.bottom + .02f, .015f, 0xFFFFFFFF, Paint.Align.CENTER, g.medium);
        }

        switch (w.app) {
            case NAV: case YOUTUBE: drawNav(w, P); break;
            case GALERIA: drawGallery(w, P); break;
            case CAMERA: drawCamera(w, P, now); break;
            case AJUSTES: drawSettings(w, P); break;
            case ARQUIVOS: drawFiles(w, P); break;
            case WEB: drawWeb(w, P); break;
            case MENU: drawMenu(w, P); break;
            case RELOGIO: drawClock(w, P); break;
            case DESENHO: drawPaint(w, P); break;
            case BOLHAS: drawBubbles(w, P); break;
            case MUSICA: drawMusic(w, P, now); break;
            case NOTAS: drawNotes(w, P); break;
            case CONTROLE: drawPad(w, P, now); break;
            default: break;
        }

        // barra para mover e canto para redimensionar
        boolean gh = hov.contains("grab:" + w.id);
        g.fill.setColor(gh ? 0xF2FFFFFF : 0x80FFFFFF);
        c.drawRoundRect(P.grab, .01f, .01f, g.fill);
        if (w != cinemaWin) {
            boolean rh = hov.contains("resize:" + w.id);
            g.stroke.setColor(rh ? 0xF2FFFFFF : 0x66FFFFFF);
            g.stroke.setStrokeWidth(rh ? .005f : .0035f);
            c.drawArc(new RectF(P.hw - .05f, P.hh - .05f, P.hw + .006f, P.hh + .006f), 10, 70, false, g.stroke);
        }
        if (w.behind && cfg.segMode != Config.SEG_OFF)
            g.txt("atrás da pessoa", P.body.right - .03f, P.body.bottom + .04f, .014f, 0xB37CC4FF, Paint.Align.RIGHT, g.medium);

        if (alpha < 255) c.restore();
        c.restore();
    }

    private void drawNav(Win w, Parts P) {
        RectF a = navArea(P);
        for (Btn b : buttons(w, P)) {
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            if (b.circle) {
                boolean on = b.id.equals("kb") && kbOpen && kbTarget == T_WEB;
                g.circle(b.x, b.y, b.r, h ? 0xF2FFFFFF : (on ? 0xCC7CC4FF : 0x2EFFFFFF));
                int ic = h ? 0xFF15171E : 0xFFFFFFFF;
                if (b.id.equals("back")) {
                    g.line(b.x + .004f, b.y - .008f, b.x - .004f, b.y, .0028f, ic);
                    g.line(b.x - .004f, b.y, b.x + .004f, b.y + .008f, .0028f, ic);
                } else if (b.id.equals("cine")) {
                    boolean cine = cinemaWin == w;
                    g.stroke.setColor(ic); g.stroke.setStrokeWidth(.002f);
                    float q = cine ? .0055f : .0085f;
                    g.c.drawRoundRect(new RectF(b.x - q * 1.3f, b.y - q, b.x + q * 1.3f, b.y + q), .002f, .002f, g.stroke);
                } else if (b.id.equals("kb")) {
                    g.stroke.setColor(ic); g.stroke.setStrokeWidth(.002f);
                    g.c.drawRoundRect(new RectF(b.x - .01f, b.y - .0065f, b.x + .01f, b.y + .0065f), .002f, .002f, g.stroke);
                    for (int i = 0; i < 4; i++) g.circle(b.x - .0055f + i * .0037f, b.y - .002f, .0009f, ic);
                    g.line(b.x - .004f, b.y + .003f, b.x + .004f, b.y + .003f, .0016f, ic);
                } else {
                    g.stroke.setColor(ic); g.stroke.setStrokeWidth(.0026f);
                    g.c.drawArc(new RectF(b.x - .008f, b.y - .008f, b.x + .008f, b.y + .008f), 40, 290, false, g.stroke);
                }
            } else {
                boolean editing = kbOpen && kbTarget == T_URL && kbWin == w;
                g.glass(b.rect, b.rect.height() / 2, h || editing, false);
                float ix = b.rect.left + .02f, iy = b.rect.centerY();
                g.ring(ix, iy - .002f, .006f, .002f, 0xCCFFFFFF);
                g.line(ix + .004f, iy + .002f, ix + .008f, iy + .006f, .002f, 0xCCFFFFFF);
                String txt;
                int col = Color.WHITE;
                String hint = w.app == YOUTUBE ? "Pesquisar no YouTube" : "Pesquisar ou digitar endereço";
                if (editing) { txt = urlBuf.length() == 0 ? hint : urlBuf + "|"; if (urlBuf.length() == 0) col = 0x99FFFFFF; }
                else {
                    String u = w.app == YOUTUBE ? "" : host.webUrl(w.app);
                    txt = (u == null || u.isEmpty()) ? hint : u.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
                    if (u == null || u.isEmpty()) col = 0x99FFFFFF;
                }
                g.txt(g.fit(txt, .016f, b.rect.width() - .05f, g.regular), ix + .016f, iy, .016f, col, Paint.Align.LEFT, g.regular);
            }
        }
        g.fill.setColor(0xFFFFFFFF);
        g.c.drawRoundRect(a, .014f, .014f, g.fill);
        Bitmap wb = host.webBitmap(w.app);
        if (wb != null) {
            g.c.save();
            g.clipRound(a, .014f);
            g.c.drawBitmap(wb, null, a, g.bmp);
            g.c.restore();
        } else g.txt(w.app == YOUTUBE ? "Abrindo o YouTube…" : "Carregando página…", a.centerX(), a.centerY(), .022f, 0xFF6B7280, Paint.Align.CENTER, g.medium);
    }

    private void drawGallery(Win w, Parts P) {
        RectF C = P.content;
        List<Host.GalleryItem> items = host.galleryItems();
        List<Btn> bs = buttons(w, P);
        if (w.galSel >= items.size()) w.galSel = -1;
        if (w.galSel >= 0 && w.galSel < items.size()) {
            Host.GalleryItem it = items.get(w.galSel);
            if (it.large == null) host.loadLarge(it);
            Bitmap b = it.large != null ? it.large : it.thumb;
            RectF r = new RectF(C.left, C.top, C.right, C.bottom - .055f);
            g.fill.setColor(0x66000000);
            g.c.drawRoundRect(r, .016f, .016f, g.fill);
            g.contain(b, r, g.bmp);
            g.txt(g.fit(it.name, .016f, C.width() * .5f, g.regular), C.centerX(), C.bottom - .025f, .016f, 0xB3FFFFFF, Paint.Align.CENTER, g.regular);
            g.txt((w.galSel + 1) + " de " + items.size(), C.right - .13f, C.bottom - .025f, .015f, 0x99FFFFFF, Paint.Align.RIGHT, g.regular);
        } else {
            if (items.isEmpty())
                g.txt("Nenhuma foto ainda. Tire fotos no app Câmera.", C.centerX(), C.centerY() - .03f, .02f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
            for (Btn b : bs) {
                if (!b.id.startsWith("t")) continue;
                int idx = Integer.parseInt(b.id.substring(1));
                if (idx >= items.size()) continue;
                Host.GalleryItem it = items.get(idx);
                boolean h = hov.contains("btn:" + w.id + ":" + b.id);
                g.c.save();
                g.clipRound(b.rect, .012f);
                g.fill.setColor(0x33FFFFFF);
                g.c.drawRect(b.rect, g.fill);
                if (it.thumb != null) g.cover(it.thumb, b.rect, g.bmp);
                g.c.restore();
                if (h) { g.stroke.setColor(0xF2FFFFFF); g.stroke.setStrokeWidth(.004f); g.c.drawRoundRect(b.rect, .012f, .012f, g.stroke); }
            }
            int per = galleryCols(P) * 3, pages = Math.max(1, (items.size() + per - 1) / per);
            g.txt("Página " + (w.page + 1) + " de " + pages, C.right - .13f, C.bottom - .025f, .015f, 0x99FFFFFF, Paint.Align.RIGHT, g.regular);
        }
        for (Btn b : bs) {
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            if (b.id.equals("gprev") || b.id.equals("pprev")) arrowBtn(b, h, true);
            else if (b.id.equals("gnext") || b.id.equals("pnext")) arrowBtn(b, h, false);
            else if (b.id.equals("gback")) { g.glass(b.rect, b.rect.height() / 2, h, false); g.txt("‹ Voltar", b.rect.centerX(), b.rect.centerY(), .016f, Color.WHITE, Paint.Align.CENTER, g.medium); }
            else if (b.id.equals("perm")) { g.glass(b.rect, b.rect.height() / 2, h, false); g.txt("Permitir acesso às fotos", b.rect.centerX(), b.rect.centerY(), .015f, Color.WHITE, Paint.Align.CENTER, g.medium); }
        }
    }

    private void arrowBtn(Btn b, boolean h, boolean left) {
        g.circle(b.x, b.y, b.r, h ? 0xF2FFFFFF : 0x2EFFFFFF);
        int ic = h ? 0xFF15171E : 0xFFFFFFFF;
        float d = left ? 1 : -1;
        g.line(b.x + .004f * d, b.y - .008f, b.x - .004f * d, b.y, .0028f, ic);
        g.line(b.x - .004f * d, b.y, b.x + .004f * d, b.y + .008f, .0028f, ic);
    }

    private void drawFiles(Win w, Parts P) {
        RectF C = P.content;
        List<File> files = host.listFiles();
        g.txt("Fotos do Visão Box · " + files.size() + (files.size() == 1 ? " arquivo" : " arquivos"),
                C.left, C.top + .016f, .017f, 0x99FFFFFF, Paint.Align.LEFT, g.medium);
        if (files.isEmpty())
            g.txt("Nenhum arquivo ainda. As fotos do app Câmera aparecem aqui.", C.centerX(), C.centerY(), .019f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
        for (Btn b : buttons(w, P)) {
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            if (b.id.startsWith("f")) {
                int fi = Integer.parseInt(b.id.substring(1));
                if (fi >= files.size()) continue;
                File fl = files.get(fi);
                g.fill.setColor(h ? 0x40FFFFFF : 0x14FFFFFF);
                g.c.drawRoundRect(b.rect, .01f, .01f, g.fill);
                float cy = b.rect.centerY();
                g.fill.setColor(0xFF7DBEFF);
                g.c.drawRoundRect(new RectF(b.rect.left + .012f, cy - .012f, b.rect.left + .036f, cy + .012f), .004f, .004f, g.fill);
                g.txt(g.fit(fl.getName(), .017f, b.rect.width() * .5f, g.medium), b.rect.left + .05f, cy, .017f, Color.WHITE, Paint.Align.LEFT, g.medium);
                String meta = (fl.length() / 1024) + " KB · " + fmtFile.format(new Date(fl.lastModified()));
                g.txt(meta, b.rect.right - .012f, cy, .015f, 0x99FFFFFF, Paint.Align.RIGHT, g.regular);
            } else arrowBtn(b, h, b.id.equals("pprev"));
        }
    }

    private void drawWeb(Win w, Parts P) {
        for (Btn b : buttons(w, P)) {
            int i = b.id.charAt(1) - '0';
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            g.glass(b.rect, .022f, h, false);
            float cx = b.rect.centerX(), cy = b.rect.centerY();
            g.circle(cx, cy - .025f, .028f * (h ? 1.1f : 1f), new int[]{0xFFFF3B30, 0xFF4285F4, 0xFF8E8E93, 0xFF34A853, 0xFFE53935, 0xFF0A84FF}[i]);
            g.txt(SITES[i][0].substring(0, 1), cx, cy - .025f, .026f, Color.WHITE, Paint.Align.CENTER, g.medium);
            g.txt(SITES[i][0], cx, cy + .03f, .019f, Color.WHITE, Paint.Align.CENTER, g.medium);
        }
    }

    private void drawMenu(Win w, Parts P) {
        for (Btn b : buttons(w, P)) {
            int app = MENU_APPS[Integer.parseInt(b.id.substring(1))];
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            appIcon(app, b.x, b.y, b.r * (h ? 1.12f : 1f), h);
            String name = app == CINEMA && cinema() ? "Sair do cinema" : NAMES[app];
            g.txt(name, b.x, b.y + b.r + .026f, .016f, 0xE6FFFFFF, Paint.Align.CENTER, g.regular);
        }
    }

    private void drawSettings(Win w, Parts P) {
        List<Row> rs = rows();
        RectF C = P.content;
        int perCol = (rs.size() + 1) / 2;
        float gap = .012f, colW = (C.width() - gap) / 2, rh = (C.height() - gap * (perCol - 1)) / perCol;
        for (int i = 0; i < rs.size(); i++) {
            Row r = rs.get(i);
            float x = C.left + (i / perCol) * (colW + gap), y = C.top + (i % perCol) * (rh + gap);
            RectF rr = new RectF(x, y, x + colW, y + rh);
            g.fill.setColor(0x14FFFFFF);
            g.c.drawRoundRect(rr, .012f, .012f, g.fill);
            float cy = rr.centerY(), right = rr.right - .012f;
            g.txt(g.fit(r.label, .016f, colW * .52f, g.medium), rr.left + .014f, cy, .016f, 0xE6FFFFFF, Paint.Align.LEFT, g.medium);
            if (r.kind == Row.CYCLE || r.kind == Row.STEP) {
                g.txt(r.value.get(), right - .095f, cy, .016f, 0xFF9FD3FF, Paint.Align.RIGHT, g.medium);
                for (int s = 0; s < 2; s++) {
                    String id = "r" + i + (s == 0 ? "m" : "p");
                    float bx = s == 0 ? right - .068f : right - .018f;
                    boolean h = hov.contains("btn:" + w.id + ":" + id);
                    g.circle(bx, cy, .017f, h ? 0xF2FFFFFF : 0x2EFFFFFF);
                    int ic = h ? 0xFF15171E : 0xFFFFFFFF;
                    if (r.kind == Row.STEP) {
                        g.line(bx - .006f, cy, bx + .006f, cy, .0026f, ic);
                        if (s == 1) g.line(bx, cy - .006f, bx, cy + .006f, .0026f, ic);
                    } else {
                        float d = s == 0 ? 1 : -1;
                        g.line(bx + .003f * d, cy - .006f, bx - .003f * d, cy, .0026f, ic);
                        g.line(bx - .003f * d, cy, bx + .003f * d, cy + .006f, .0026f, ic);
                    }
                }
            } else {
                String id = "r" + i + "t";
                boolean h = hov.contains("btn:" + w.id + ":" + id);
                RectF br = new RectF(right - .13f, cy - .018f, right, cy + .018f);
                boolean on = r.kind == Row.TOGGLE && (r.value.get().startsWith("Ligad"));
                g.fill.setColor(h ? 0xF2FFFFFF : (on ? 0xCC34C759 : 0x2EFFFFFF));
                g.c.drawRoundRect(br, .018f, .018f, g.fill);
                g.txt(r.value.get(), br.centerX(), cy, .015f, h ? 0xFF15171E : Color.WHITE, Paint.Align.CENTER, g.medium);
            }
        }
    }

    private void drawCamera(Win w, Parts P, long now) {
        RectF C = P.content;
        RectF pr = new RectF(C.left, C.top, C.right - .115f, C.bottom);
        g.c.save();
        g.clipRound(pr, .016f);
        g.fill.setColor(0xFF000000);
        g.c.drawRect(pr, g.fill);
        if (frame != null) g.cover(frame, pr, g.bmp);
        else g.txt("Sem imagem da câmera", pr.centerX(), pr.centerY(), .02f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
        float fl = Math.max(0, 1 - (now - w.shot) / 250f);
        if (fl > 0) { g.fill.setColor(Gfx.a(Color.WHITE, fl * .8f)); g.c.drawRect(pr, g.fill); }
        g.c.restore();
        for (Btn b : buttons(w, P)) {
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            if (b.id.equals("shot")) {
                g.ring(b.x, b.y, b.r, .004f, Color.WHITE);
                g.circle(b.x, b.y, b.r * (h ? .82f : .72f), Color.WHITE);
                g.txt("Foto", b.x, b.y + b.r + .016f, .013f, 0xB3FFFFFF, Paint.Align.CENTER, g.regular);
            } else if (b.id.equals("rec")) {
                boolean rec = host.isRecording();
                g.ring(b.x, b.y, b.r, .004f, Color.WHITE);
                if (rec) {
                    float q = b.r * (h ? .5f : .42f);
                    g.fill.setColor(0xFFFF3B30);
                    g.c.drawRoundRect(new RectF(b.x - q, b.y - q, b.x + q, b.y + q), q * .3f, q * .3f, g.fill);
                } else g.circle(b.x, b.y, b.r * (h ? .78f : .68f), 0xFFFF3B30);
                long sec = host.recordingSeconds();
                g.txt(rec ? String.format(Locale.US, "%d:%02d", sec / 60, sec % 60) : "Gravar", b.x, b.y + b.r + .016f, .013f,
                        rec ? 0xFFFF6B6B : 0xB3FFFFFF, Paint.Align.CENTER, g.medium);
            } else {
                g.glass(b.rect, b.rect.height() / 2, h, false);
                g.txt("Trocar", b.rect.centerX(), b.rect.centerY(), .015f, Color.WHITE, Paint.Align.CENTER, g.medium);
            }
        }
        g.txt(g.fit(host.cameraLabel(), .014f, .11f, g.regular), C.right - .05f, C.top + .02f, .014f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
    }

    private List<String> wrap(String text, float size, float maxW) {
        ArrayList<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ", -1)) {
                String cand = line.length() == 0 ? word : line + " " + word;
                if (line.length() == 0 || g.width(cand, size, g.regular) <= maxW) { line.setLength(0); line.append(cand); }
                else { out.add(line.toString()); line.setLength(0); line.append(word); }
            }
            out.add(line.toString());
        }
        return out;
    }

    private void drawNotes(Win w, Parts P) {
        RectF C = P.content;
        RectF ta = new RectF(C.left, C.top, C.right, C.bottom - .055f);
        g.fill.setColor(0x1FFFFFFF);
        g.c.drawRoundRect(ta, .016f, .016f, g.fill);
        boolean typing = kbOpen && kbTarget == T_NOTES && kbWin == w;
        String t = w.text.toString() + (typing ? "|" : "");
        float size = .021f, lh = .032f;
        if (w.text.length() == 0 && !typing)
            g.txt("Toque em Teclado e comece a escrever", ta.centerX(), ta.centerY(), .019f, 0x80FFFFFF, Paint.Align.CENTER, g.regular);
        else {
            List<String> lines = wrap(t, size, ta.width() - .04f);
            int max = Math.max(1, (int) ((ta.height() - .03f) / lh));
            int start = Math.max(0, lines.size() - max);
            g.c.save();
            g.clipRound(ta, .016f);
            for (int i = start; i < lines.size(); i++)
                g.txt(lines.get(i), ta.left + .02f, ta.top + .03f + (i - start) * lh, size, Color.WHITE, Paint.Align.LEFT, g.regular);
            g.c.restore();
        }
        g.txt(w.text.length() + " caracteres", C.left, C.bottom - .026f, .015f, 0x80FFFFFF, Paint.Align.LEFT, g.regular);
        for (Btn b : buttons(w, P)) {
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            boolean on = b.id.equals("nkb") && typing;
            g.fill.setColor(h ? 0xF2FFFFFF : (on ? 0xCC7CC4FF : 0x2EFFFFFF));
            g.c.drawRoundRect(b.rect, b.rect.height() / 2, b.rect.height() / 2, g.fill);
            g.txt(b.id.equals("nkb") ? "Teclado" : "Limpar", b.rect.centerX(), b.rect.centerY(), .015f,
                    h ? 0xFF15171E : Color.WHITE, Paint.Align.CENTER, g.medium);
        }
    }

    static final String[][] PAD_HELP = {
            {"A", "Confirmar (segurar na barra = arrastar)"}, {"B", "Voltar / cancelar / fechar"},
            {"X", "Menu de apps"}, {"Y", "Teclado"}, {"LB / RB", "Afastar / aproximar janela"},
            {"LT", "Segurar: arrastar janela na mira"}, {"RT", "Confirmar"}, {"≡ Start", "Ajustes"},
            {"⧉ View", "Recentralizar a visão"}, {"Analógico esq.", "Ajustar a mira (L3 centraliza)"},
            {"Analógico dir.", "Rolar a página / trocar foto"}, {"Direcional", "Mover a mira"}};

    private boolean pd(int code) { return padDown.contains(code); }

    /** Controle desenhado ao vivo: os botões acendem quando apertados. */
    private void drawPad(Win w, Parts P, long now) {
        RectF C = P.content;
        boolean on = padActive(now);
        float cx = C.left + C.width() * .27f, cy = C.top + C.height() * .46f;
        float bw = C.width() * .46f, bh = C.height() * .56f;
        // corpo
        Path body = new Path();
        body.addRoundRect(new RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh * .2f), bh * .3f, bh * .3f, Path.Direction.CW);
        body.addRoundRect(new RectF(cx - bw / 2, cy - bh * .2f, cx - bw * .12f, cy + bh / 2), bh * .26f, bh * .26f, Path.Direction.CW);
        body.addRoundRect(new RectF(cx + bw * .12f, cy - bh * .2f, cx + bw / 2, cy + bh / 2), bh * .26f, bh * .26f, Path.Direction.CW);
        g.fill.setColor(0x33FFFFFF);
        g.c.drawPath(body, g.fill);
        float u = bh * .09f;
        // gatilhos e bumpers
        int lit = 0xFF7CC4FF, dim = 0x40FFFFFF;
        float lt = Math.max(padLT, pd(KeyEvent.KEYCODE_BUTTON_L2) ? 1 : 0), rt = Math.max(padRT, pd(KeyEvent.KEYCODE_BUTTON_R2) ? 1 : 0);
        g.fill.setColor(lt > .5f ? lit : dim);
        g.c.drawRoundRect(new RectF(cx - bw * .44f, cy - bh * .74f, cx - bw * .22f, cy - bh * .6f), u * .5f, u * .5f, g.fill);
        g.fill.setColor(rt > .5f ? lit : dim);
        g.c.drawRoundRect(new RectF(cx + bw * .22f, cy - bh * .74f, cx + bw * .44f, cy - bh * .6f), u * .5f, u * .5f, g.fill);
        g.fill.setColor(pd(KeyEvent.KEYCODE_BUTTON_L1) ? lit : dim);
        g.c.drawRoundRect(new RectF(cx - bw * .46f, cy - bh * .56f, cx - bw * .2f, cy - bh * .5f), u * .3f, u * .3f, g.fill);
        g.fill.setColor(pd(KeyEvent.KEYCODE_BUTTON_R1) ? lit : dim);
        g.c.drawRoundRect(new RectF(cx + bw * .2f, cy - bh * .56f, cx + bw * .46f, cy - bh * .5f), u * .3f, u * .3f, g.fill);
        // analógicos (mexem de verdade)
        float lsx = cx - bw * .3f, lsy = cy - bh * .14f, rsx = cx + bw * .13f, rsy = cy + bh * .12f;
        g.ring(lsx, lsy, u * 1.25f, .002f, 0x66FFFFFF);
        g.circle(lsx + padLX * u * .7f, lsy + padLY * u * .7f, u * .8f, pd(KeyEvent.KEYCODE_BUTTON_THUMBL) ? lit : 0xCCFFFFFF);
        g.ring(rsx, rsy, u * 1.25f, .002f, 0x66FFFFFF);
        g.circle(rsx + padRX * u * .7f, rsy + padRY * u * .7f, u * .8f, pd(KeyEvent.KEYCODE_BUTTON_THUMBR) ? lit : 0xCCFFFFFF);
        // direcional
        float dx = cx - bw * .13f, dy = cy + bh * .12f;
        g.fill.setColor(dim);
        g.c.drawRect(dx - u * .35f, dy - u, dx + u * .35f, dy + u, g.fill);
        g.c.drawRect(dx - u, dy - u * .35f, dx + u, dy + u * .35f, g.fill);
        // ABXY
        float ax = cx + bw * .3f, ay = cy - bh * .14f, sp = u * 1.1f;
        Object[][] face = {{KeyEvent.KEYCODE_BUTTON_A, "A", 0xFF3CC85A, 0f, sp}, {KeyEvent.KEYCODE_BUTTON_B, "B", 0xFFE5484D, sp, 0f},
                {KeyEvent.KEYCODE_BUTTON_X, "X", 0xFF3E8BFF, -sp, 0f}, {KeyEvent.KEYCODE_BUTTON_Y, "Y", 0xFFF5C518, 0f, -sp}};
        for (Object[] f : face) {
            boolean d = pd((Integer) f[0]);
            float fx = ax + (Float) f[3], fy = ay + (Float) f[4];
            g.circle(fx, fy, u * (d ? .62f : .55f), d ? (Integer) f[2] : Gfx.a((Integer) f[2], .55f));
            g.txt((String) f[1], fx, fy, u * .7f, Color.WHITE, Paint.Align.CENTER, g.medium);
        }
        // View / Start
        g.circle(cx - u * .8f, cy - bh * .14f, u * .28f, pd(KeyEvent.KEYCODE_BUTTON_SELECT) ? lit : 0x99FFFFFF);
        g.circle(cx + u * .8f, cy - bh * .14f, u * .28f, pd(KeyEvent.KEYCODE_BUTTON_START) ? lit : 0x99FFFFFF);
        g.txt(on ? "Controle conectado" : "Conecte um controle Bluetooth e aperte um botão", cx, C.bottom - .02f, .016f,
                on ? 0xFF9FE8B0 : 0xB3FFFFFF, Paint.Align.CENTER, g.medium);
        // lista do que cada botão faz
        float lx = C.left + C.width() * .55f, ly = C.top + .02f, lh = (C.height() - .03f) / PAD_HELP.length;
        for (String[] h : PAD_HELP) {
            g.txt(h[0], lx, ly + lh / 2, .016f, 0xFF9FD3FF, Paint.Align.LEFT, g.medium);
            g.txt(g.fit(h[1], .016f, C.right - lx - .13f, g.regular), lx + .125f, ly + lh / 2, .016f, 0xE6FFFFFF, Paint.Align.LEFT, g.regular);
            ly += lh;
        }
    }

    private void drawClock(Win w, Parts P) {
        Date d = new Date();
        RectF B = P.body;
        g.txt(fmtTime.format(d), 0, B.top + .135f, .11f, Color.WHITE, Paint.Align.CENTER, g.light);
        g.txt(fmtDate.format(d), 0, B.top + .205f, .021f, 0xB8FFFFFF, Paint.Align.CENTER, g.regular);
        float fr = (System.currentTimeMillis() % 60000) / 60000f;
        float bw = B.width() - .12f, bx = -bw / 2, by = B.bottom - .034f;
        g.fill.setColor(0x26FFFFFF);
        g.c.drawRoundRect(new RectF(bx, by, bx + bw, by + .006f), .003f, .003f, g.fill);
        g.fill.setColor(0xFFFFB36B);
        g.c.drawRoundRect(new RectF(bx, by, bx + Math.max(.006f, bw * fr), by + .006f), .003f, .003f, g.fill);
    }

    private void drawPaint(Win w, Parts P) {
        RectF C = P.content;
        g.fill.setColor(0x3D000000);
        g.c.drawRoundRect(C, .018f, .018f, g.fill);
        g.c.save();
        g.clipRound(C, .018f);
        g.stroke.setStrokeWidth(.006f);
        for (Stroke s : w.strokes) {
            g.stroke.setColor(s.c);
            Path p = new Path();
            for (int i = 0; i < s.pts.size(); i++) {
                float[] q = s.pts.get(i);
                if (i == 0) p.moveTo(q[0], q[1]); else p.lineTo(q[0], q[1]);
            }
            if (s.pts.size() == 1) { float[] q = s.pts.get(0); p.lineTo(q[0] + .0001f, q[1]); }
            g.c.drawPath(p, g.stroke);
        }
        g.c.restore();
        if (w.strokes.isEmpty())
            g.txt("Faça a pinça e desenhe no ar", 0, C.centerY(), .022f, 0x80FFFFFF, Paint.Align.CENTER, g.regular);
        for (Btn b : buttons(w, P)) {
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            if (b.circle) {
                int col = PEN[b.id.charAt(1) - '0'];
                g.circle(b.x, b.y, b.r * (h ? 1.2f : 1f), col);
                if (w.pen == col) g.ring(b.x, b.y, b.r + .005f, .002f, 0xE6FFFFFF);
            } else {
                g.glass(b.rect, b.rect.height() / 2, h, false);
                g.txt("Limpar", b.rect.centerX(), b.rect.centerY(), .015f, Color.WHITE, Paint.Align.CENTER, g.medium);
            }
        }
    }

    private void drawBubbles(Win w, Parts P) {
        RectF C = P.content, B = P.body;
        g.txt("Pontos: " + w.score, B.right - .26f, B.top + .032f, .018f, 0xD9FFFFFF, Paint.Align.RIGHT, g.medium);
        g.fill.setColor(0x38001428);
        g.c.drawRoundRect(C, .018f, .018f, g.fill);
        g.c.save();
        g.clipRound(C, .018f);
        for (Bubble b : w.bubbles) {
            float x = b.x + b.dx;
            g.grad.setShader(new RadialGradient(x - b.r * .35f, b.y - b.r * .35f, b.r * 1.2f,
                    new int[]{0x8CFFFFFF, 0x1A48DBFB, 0xA6AAF0FF}, new float[]{0, .6f, 1}, Shader.TileMode.CLAMP));
            g.c.drawCircle(x, b.y, b.r, g.grad);
            g.grad.setShader(null);
            g.ring(x, b.y, b.r, .0015f, 0x73FFFFFF);
        }
        for (Part q : w.parts) g.circle(q.x, q.y, .004f, Gfx.a(0xFFBEF5FF, q.life / .4f));
        g.c.restore();
        if (w.score == 0)
            g.txt("Estoure as bolhas com a ponta do indicador", 0, C.top + .03f, .018f, 0x8CFFFFFF, Paint.Align.CENTER, g.regular);
    }

    private void drawMusic(Win w, Parts P, long now) {
        List<Btn> bs = buttons(w, P);
        for (int i = 0; i < bs.size(); i++) {
            Btn b = bs.get(i);
            float fl = Math.max(0, 1 - (now - w.flash[i]) / 450f);
            float[] hsv = {(i * 48 + 340) % 360, .72f, .62f + fl * .35f};
            g.fill.setColor(Gfx.a(Color.HSVToColor(hsv), .5f + fl * .45f));
            g.c.drawRoundRect(b.rect, .02f, .02f, g.fill);
            if (hov.contains("btn:" + w.id + ":" + b.id)) {
                g.stroke.setColor(0xD9FFFFFF); g.stroke.setStrokeWidth(.0025f);
                g.c.drawRoundRect(b.rect, .02f, .02f, g.stroke);
            }
            g.txt(NOTE_N[i], b.rect.centerX(), b.rect.centerY(), .032f, Color.WHITE, Paint.Align.CENTER, g.medium);
        }
    }

    /* ================= desenho: dock e HUD ================= */

    private final LinearGradient[] iconGrad = new LinearGradient[COLS.length];

    void appIcon(int app, float x, float y, float r, boolean hl) {
        Canvas c = g.c;
        if (hl) g.circle(x, y + r * .12f, r * 1.32f, 0x26FFFFFF);
        RectF rr = new RectF(x - r, y - r, x + r, y + r);
        g.fill.setColor(0x33000000);
        c.drawRoundRect(new RectF(x - r, y - r + r * .16f, x + r, y + r + r * .16f), r * .5f, r * .5f, g.fill);
        if (iconGrad[app] == null) iconGrad[app] = new LinearGradient(0, -1, 0, 1, COLS[app][0], COLS[app][1], Shader.TileMode.CLAMP);
        lm.setScale(1, r); lm.postTranslate(0, y);
        iconGrad[app].setLocalMatrix(lm);
        g.grad.setShader(iconGrad[app]);
        c.drawRoundRect(rr, r * .5f, r * .5f, g.grad);
        g.grad.setShader(null);
        g.fill.setColor(0x2EFFFFFF);
        c.drawRoundRect(new RectF(x - r * .82f, y - r * .9f, x + r * .82f, y - r * .25f), r * .4f, r * .4f, g.fill);
        glyph(app, x, y, r * .62f);
    }

    private void glyph(int id, float x, float y, float r) {
        Canvas c = g.c;
        c.save();
        c.translate(x, y);
        c.scale(r, r);
        Paint s = g.stroke, fl = g.fill;
        s.setColor(Color.WHITE); s.setStrokeWidth(.11f);
        fl.setColor(Color.WHITE);
        Path p = new Path();
        switch (id) {
            case NAV:
                c.drawCircle(0, 0, .62f, s);
                c.drawOval(new RectF(-.26f, -.62f, .26f, .62f), s);
                c.drawLine(-.62f, 0, .62f, 0, s);
                break;
            case GALERIA:
                c.drawRoundRect(new RectF(-.62f, -.48f, .62f, .48f), .12f, .12f, s);
                p.moveTo(-.5f, .36f); p.lineTo(-.12f, -.06f); p.lineTo(.12f, .18f); p.lineTo(.28f, .04f); p.lineTo(.5f, .36f);
                c.drawPath(p, s);
                c.drawCircle(.28f, -.2f, .1f, fl);
                break;
            case CAMERA:
                c.drawRoundRect(new RectF(-.62f, -.34f, .62f, .48f), .16f, .16f, s);
                c.drawCircle(0, .07f, .22f, s);
                p.moveTo(-.22f, -.34f); p.lineTo(-.12f, -.5f); p.lineTo(.12f, -.5f); p.lineTo(.22f, -.34f);
                c.drawPath(p, s);
                break;
            case AJUSTES:
                c.drawCircle(0, 0, .3f, s);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    c.drawLine((float) Math.cos(a) * .42f, (float) Math.sin(a) * .42f, (float) Math.cos(a) * .62f, (float) Math.sin(a) * .62f, s);
                }
                break;
            case ARQUIVOS:
                p.moveTo(-.62f, -.38f); p.lineTo(-.18f, -.38f); p.lineTo(-.06f, -.24f); p.lineTo(.62f, -.24f);
                p.lineTo(.62f, .46f); p.lineTo(-.62f, .46f); p.close();
                c.drawPath(p, s);
                break;
            case WEB:
                for (int i = 0; i < 4; i++) {
                    float ox = (i % 2) * .62f - .55f, oy = (i / 2) * .62f - .55f;
                    c.drawRoundRect(new RectF(ox, oy, ox + .48f, oy + .48f), .12f, .12f, s);
                }
                break;
            case MENU:
                for (int i = 0; i < 9; i++) c.drawCircle(((i % 3) - 1) * .42f, ((i / 3) - 1) * .42f, .1f, fl);
                break;
            case RELOGIO:
                c.drawCircle(0, 0, .6f, s);
                c.drawLine(0, 0, 0, -.38f, s);
                c.drawLine(0, 0, .28f, .12f, s);
                break;
            case DESENHO:
                p.moveTo(-.6f, .3f); p.cubicTo(-.3f, -.66f, 0, .66f, .24f, -.12f); p.quadTo(.38f, -.5f, .6f, -.36f);
                c.drawPath(p, s);
                break;
            case BOLHAS:
                c.drawCircle(-.19f, .14f, .36f, s);
                c.drawCircle(.31f, -.24f, .23f, s);
                c.drawCircle(.36f, .36f, .14f, s);
                break;
            case MUSICA:
                c.drawOval(new RectF(-.48f, .22f, -.1f, .5f), fl);
                c.drawOval(new RectF(.14f, .1f, .52f, .38f), fl);
                p.moveTo(-.12f, .32f); p.lineTo(-.12f, -.48f); p.lineTo(.5f, -.6f); p.lineTo(.5f, .2f);
                c.drawPath(p, s);
                break;
            case YOUTUBE:
                c.drawRoundRect(new RectF(-.62f, -.42f, .62f, .42f), .22f, .22f, fl);
                fl.setColor(0xFFE0261C);
                p.moveTo(-.16f, -.22f); p.lineTo(.26f, 0); p.lineTo(-.16f, .22f); p.close();
                c.drawPath(p, fl);
                fl.setColor(Color.WHITE);
                break;
            case CONTROLE:
                c.drawRoundRect(new RectF(-.66f, -.32f, .66f, .3f), .3f, .3f, s);
                c.drawLine(-.4f, -.06f, -.16f, -.06f, s);
                c.drawLine(-.28f, -.18f, -.28f, .06f, s);
                c.drawCircle(.3f, -.14f, .07f, fl);
                c.drawCircle(.3f, .04f, .07f, fl);
                break;
            case NOTAS:
                c.drawRoundRect(new RectF(-.5f, -.6f, .5f, .6f), .12f, .12f, s);
                for (int i = 0; i < 3; i++) c.drawLine(-.28f, -.28f + i * .26f, .28f, -.28f + i * .26f, s);
                break;
            case CINEMA:
                c.drawRoundRect(new RectF(-.64f, -.42f, .64f, .42f), .1f, .1f, s);
                p.moveTo(-.14f, -.2f); p.lineTo(.22f, 0); p.lineTo(-.14f, .2f); p.close();
                c.drawPath(p, fl);
                break;
            default: break;
        }
        c.restore();
    }

    /** Dock e teclado: presos ao ambiente no modo espacial (com a rolagem da cabeça) ou à visão. */
    void drawOverlay(int sign, long now) {
        Canvas c = g.c;
        boolean rollIt = spatial() && roll != 0;
        if (dockAnchored) {
            c.save();
            if (rollIt) c.rotate((float) -Math.toDegrees(roll));
            if (dockVis) drawDock(sign, now);
            c.restore();
            if (Math.abs(dockRy) > .55f) drawDockArrow(dockRy > 0);
        } else drawDock(sign, now);
        if (kbOpen && kbVis) {
            c.save();
            if (rollIt) c.rotate((float) -Math.toDegrees(roll));
            drawKeyboard(sign, now);
            c.restore();
        }
    }

    /** Seta na borda da visão apontando para o dock quando ele está longe. */
    private void drawDockArrow(boolean right) {
        float x = right ? eyeHalfW - .06f : -eyeHalfW + .06f, y = .34f, d = right ? 1 : -1;
        RectF r = new RectF(x - .05f, y - .028f, x + .05f, y + .028f);
        g.glass(r, .028f, false, false);
        g.line(x + .022f * d, y, x + .008f * d, y - .012f, .003f, Color.WHITE);
        g.line(x + .022f * d, y, x + .008f * d, y + .012f, .003f, Color.WHITE);
        g.txt("Dock", x - .012f * d, y, .016f, Color.WHITE, Paint.Align.CENTER, g.medium);
    }

    private final android.graphics.Matrix lm = new android.graphics.Matrix();
    private LinearGradient dockRim;

    private void drawDock(int sign, long now) {
        Canvas c = g.c;
        float ds = dockScale();
        c.save();
        c.translate(dockCX + shift(sign, cfg.dockDist), dockCY);
        c.scale(ds, ds);
        RectF pill = dockPill();
        g.softShadow(pill, .06f);
        g.fill.setColor(0xD9141620);
        c.drawRoundRect(pill, .06f, .06f, g.fill);
        if (dockRim == null) dockRim = new LinearGradient(0, 0, 0, 1, 0x55FFFFFF, 0x10FFFFFF, Shader.TileMode.CLAMP);
        lm.setScale(1, pill.height()); lm.postTranslate(0, pill.top);
        dockRim.setLocalMatrix(lm);
        g.stroke.setShader(dockRim);
        g.stroke.setStrokeWidth(.0025f);
        c.drawRoundRect(pill, .06f, .06f, g.stroke);
        g.stroke.setShader(null);
        // relógio
        RectF ct = clockTile();
        float ca = dockAnim[DOCK.length];
        g.fill.setColor(ca > .05f ? 0x55FFFFFF : 0x22FFFFFF);
        c.drawRoundRect(ct, .03f, .03f, g.fill);
        Date d = new Date();
        g.txt(fmtTime.format(d), ct.centerX(), ct.centerY() - .01f, .028f, Color.WHITE, Paint.Align.CENTER, g.medium);
        g.txt(fmtDay.format(d), ct.centerX(), ct.centerY() + .022f, .014f, 0xB3FFFFFF, Paint.Align.CENTER, g.regular);
        // ícones
        for (int i = 0; i < DOCK.length; i++) {
            float an = dockAnim[i];
            float x = dockIconX(i), y = -.012f * an;
            appIcon(DOCK[i], x, y, .033f * (1 + .18f * an), an > .5f);
            if (an > .05f) g.txt(NAMES[DOCK[i]], x, -.1f - .012f * an, .017f, Gfx.a(Color.WHITE, an), Paint.Align.CENTER, g.medium);
        }
        // alça: arrastar move o dock, toque rápido oculta/mostra as janelas
        boolean hh = hov.contains("home");
        g.fill.setColor(hh ? 0xF2FFFFFF : 0xB3FFFFFF);
        c.drawRoundRect(new RectF(-.15f, .085f, .15f, .097f), .006f, .006f, g.fill);
        // janelas minimizadas
        List<Object[]> ch = chips();
        for (int i = 0; i < ch.size(); i++) {
            RectF r = chipRect(i, ch.size());
            Win w = (Win) ch.get(i)[1];
            String key = w == null ? "exitcinema" : "chip:" + w.id;
            g.glass(r, r.height() / 2, hov.contains(key), false);
            g.txt((String) ch.get(i)[0], r.centerX(), r.centerY(), .016f, Color.WHITE, Paint.Align.CENTER, g.medium);
        }
        c.restore();
    }

    /** Avisos, mãos e ponteiros: sempre presos à visão. */
    void drawHud(int sign, long now, String error) {
        Canvas c = g.c;
        // ---- avisos ----
        float top = -.47f;
        g.txt(status, 0, top + .01f, .017f, 0x99FFFFFF, Paint.Align.CENTER, g.medium);
        if (error != null) g.txt(g.fit(error, .017f, eyeHalfW * 1.8f, g.medium), 0, top + .045f, .017f, 0xFFFF9A9A, Paint.Align.CENTER, g.medium);
        if (toast != null && now - toastT < 2200) {
            float ta = Math.min(1, (2200 - (now - toastT)) / 300f);
            float tw = g.width(toast, .019f, g.medium) + .06f;
            float ty = .2f;
            RectF tr = new RectF(-tw / 2, ty - .038f, tw / 2, ty + .016f);
            g.fill.setColor(Gfx.a(0xE6202230, ta));
            c.drawRoundRect(tr, .02f, .02f, g.fill);
            g.txt(toast, tr.centerX(), tr.centerY(), .019f, Gfx.a(Color.WHITE, ta), Paint.Align.CENTER, g.medium);
        }

        // ---- mãos e ponteiros (alinhados à câmera) ----
        for (int i = 0; i < 2; i++) {
            if (!handActive(i, now)) continue;
            Hand h = hands[i];
            if (cfg.skeleton) {
                Path p = new Path();
                for (int[] e : CON) { p.moveTo(h.x[e[0]], h.y[e[0]]); p.lineTo(h.x[e[1]], h.y[e[1]]); }
                g.stroke.setColor(0x527CC4FF); g.stroke.setStrokeWidth(.012f);
                c.drawPath(p, g.stroke);
                g.stroke.setColor(0xD9FFFFFF); g.stroke.setStrokeWidth(.0035f);
                c.drawPath(p, g.stroke);
                for (int j = 0; j < 21; j++) g.circle(h.x[j], h.y[j], .005f, Color.WHITE);
            } else {
                g.circle(h.x[4], h.y[4], .007f, 0xE6FFFFFF);
                g.circle(h.x[8], h.y[8], .007f, 0xE6FFFFFF);
            }
        }
        for (int i = 0; i < 3; i++) {
            Ptr p = ptrs[i];
            if (!p.exists) continue;
            if (p.down) g.circle(p.x, p.y, .012f, 0xF2FFFFFF);
            g.ring(p.x, p.y, p.down ? .02f : .017f, .003f, p.down ? 0xF27CC4FF : 0xE6FFFFFF);
        }
        Ptr gp = ptrs[3];
        if (gp.exists) {
            float gx = gp.x + shift(sign, 1.5f), gy = gp.y;
            boolean on = lastGazeKey != null;
            g.circle(gx, gy, .0045f, 0xF2FFFFFF);
            if (padActive(now) && !on) {
                g.line(gx - .022f, gy, gx - .013f, gy, .0022f, 0xB3FFFFFF);
                g.line(gx + .013f, gy, gx + .022f, gy, .0022f, 0xB3FFFFFF);
                g.line(gx, gy - .022f, gx, gy - .013f, .0022f, 0xB3FFFFFF);
                g.line(gx, gy + .013f, gx, gy + .022f, .0022f, 0xB3FFFFFF);
            }
            g.ring(gx, gy, on ? .016f : .011f, .0026f, gp.down ? 0xFF7CC4FF : (on ? 0xF2FFFFFF : 0x99FFFFFF));
            if (cfg.dwell && dwellKey != null && !dwellFired) {
                float pr = Math.min(1, (now - dwellStart) / (float) cfg.dwellMs());
                g.stroke.setColor(0xFF7CC4FF); g.stroke.setStrokeWidth(.004f);
                c.drawArc(new RectF(gx - .022f, gy - .022f, gx + .022f, gy + .022f), -90, 360 * pr, false, g.stroke);
            }
        }
    }
}
