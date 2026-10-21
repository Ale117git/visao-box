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
            RELOGIO = 7, DESENHO = 8, BOLHAS = 9, MUSICA = 10, CINEMA = 11, NOTAS = 12, CONTROLE = 13, YOUTUBE = 14,
            PLAYER = 15, MIDIA = 16, NAVE = 17, VOZ = 18;
    static final String[] NAMES = {"Navegador", "Galeria", "Câmera", "Ajustes", "Arquivos", "Web", "Menu",
            "Relógio", "Desenho", "Bolhas", "Música", "Cinema", "Notas", "Controle", "YouTube", "Player", "Mídia", "Nave", "Visão"};
    static final int[][] COLS = {
            {0xFF5AC8FA, 0xFF1E6FD9}, {0xFFFFD36B, 0xFFF0932B}, {0xFFE6EBF2, 0xFF5B6876}, {0xFFB7BFCB, 0xFF5D6675},
            {0xFF7DBEFF, 0xFF3867D6}, {0xFF7BE0A8, 0xFF1E9E63}, {0xFF9AA3B6, 0xFF4B5468}, {0xFFFFB36B, 0xFFE8590C},
            {0xFFF88AE6, 0xFF8E44AD}, {0xFF7FE3FF, 0xFF0A8FC9}, {0xFFFF8A8A, 0xFFC0392B}, {0xFFFF7A9A, 0xFF9B1B50}, {0xFFFFE27A, 0xFFE0A800}, {0xFF9BE15D, 0xFF2E9E3E}, {0xFFFF5A52, 0xFFC4170C}, {0xFF8E7BFF, 0xFF4B2FD6}, {0xFFFF9F68, 0xFFE0457B}, {0xFF6EE7F9, 0xFF2649C9}, {0xFFB9F27C, 0xFF19A974}};
    static final float[][] SIZES = {{.92f, .6f}, {.82f, .54f}, {.64f, .44f}, {.96f, .74f}, {.72f, .52f}, {.72f, .48f},
            {.86f, .62f}, {.48f, .29f}, {.66f, .44f}, {.62f, .44f}, {.58f, .38f}, {.6f, .4f}, {.66f, .46f}, {.86f, .5f}, {.96f, .62f}, {.96f, .62f}, {.86f, .6f}, {.7f, .52f}, {.4f, .3f}};
    static final int[] DOCK = {NAV, YOUTUBE, MIDIA, GALERIA, CAMERA, VOZ, AJUSTES, MENU};
    static final int[] MENU_APPS = {NAV, YOUTUBE, MIDIA, GALERIA, CAMERA, AJUSTES, ARQUIVOS, WEB, RELOGIO, DESENHO,
            BOLHAS, MUSICA, NOTAS, CONTROLE, NAVE, CINEMA, VOZ};
    static final String[][] SITES = {{"YouTube", "https://m.youtube.com"}, {"Google", "https://www.google.com"},
            {"Wikipédia", "https://pt.m.wikipedia.org"}, {"Mapas", "https://www.google.com/maps"},
            {"Notícias", "https://g1.globo.com"}, {"Clima", "https://www.climatempo.com.br"},
            {"WhatsApp", "https://web.whatsapp.com"}, {"Instagram", "https://www.instagram.com"}, {"X", "https://x.com"}};
    static final int[] SITE_COL = {0xFFFF3B30, 0xFF4285F4, 0xFF8E8E93, 0xFF34A853, 0xFFE53935, 0xFF0A84FF, 0xFF25D366, 0xFFE1306C, 0xFF111111};
    /** Sites que só funcionam em "modo computador". */
    static boolean needsDesktop(String url) { return url != null && url.contains("web.whatsapp.com"); }
    static final String[][] DEFAULT_FAVS = {{"Google", "https://www.google.com"}, {"YouTube", "https://m.youtube.com"},
            {"Wikipédia", "https://pt.m.wikipedia.org"}, {"Notícias", "https://g1.globo.com"}, {"Mapas", "https://www.google.com/maps"},
            {"Clima", "https://www.climatempo.com.br"}, {"WhatsApp", "https://web.whatsapp.com"}, {"Instagram", "https://www.instagram.com"}};
    static final String[] NOTE_N = {"Dó", "Ré", "Mi", "Sol", "Lá", "Dó"};
    static final int[] PEN = {0xFFFFFFFF, 0xFFFF6B6B, 0xFFFECA57, 0xFF48DBFB, 0xFF1DD1A1};
    static final int[][] CON = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {0, 5}, {5, 6}, {6, 7}, {7, 8}, {5, 9}, {9, 10}, {10, 11},
            {11, 12}, {9, 13}, {13, 14}, {14, 15}, {15, 16}, {13, 17}, {17, 18}, {18, 19}, {19, 20}, {0, 17}};

    static final int H_DOCK = 1, H_CLOCK = 2, H_HOMEBAR = 3, H_CHIP = 4, H_TBTN = 5, H_GRAB = 6, H_RESIZE = 7,
            H_BTN = 8, H_AREA = 9, H_BODY = 10, H_EXITCINEMA = 11,
            H_DOCKBODY = 12, H_KEY = 13, H_KBGRAB = 14, H_KBBODY = 15, H_KBCLOSE = 16, H_SCROLL = 17;
    static final String[] TBTN = {"close", "min", "far", "near", "layer", "pin"};
    static final String[] TBTN_TIP = {"Fechar", "Minimizar", "Afastar", "Aproximar", "Atrás / na frente da pessoa", "Prender num QR code"};

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
        float sbFrac = -1; long sbHoldUntil;      // barra de rolagem: posição mostrada durante o arraste
        final StringBuilder text = new StringBuilder();
        final ArrayList<Integer> tabs = new ArrayList<>(); int tab;      // abas do Navegador (ids das páginas)
        int mediaKind;                                                   // Mídia: 0 = vídeos, 1 = músicas
        // jogo Nave
        float shipX, shipTarget = Float.NaN; int lives = 3, best; boolean over, firing; float spawn, fireT, hurt;
        final ArrayList<float[]> shots = new ArrayList<>(), rocks = new ArrayList<>();
        // presa a um QR code
        boolean pinned; float pinDX, pinDY, pinScale;
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
    static final class Parts { RectF body, title, content, grab, scroll; float hw, hh; }
    static final class Hit {
        final int type; final String key; Win win; int index; String id;
        Hit(int t, String k) { type = t; key = k; }
    }
    static final class Hand { final float[] x = new float[21], y = new float[21]; boolean down, init; float cx, cy; long last; }
    static final class Ptr {
        boolean exists, down; float x, y; Hit hit; Win drag, resize; float dx, dy; float wy0, wp0;
        boolean dockDrag, dockMoved, dockHome, kbDrag; float sx, sy, ox, oy;
        int webMode; float wfx, wfy, wlx, wly;      // página: 0 = esperando, 1 = rolando
        boolean sbDrag; float sbOff; long sbSent;   // barra de rolagem
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
    // QR code visto pela câmera (coordenadas da cena)
    float qrX, qrY, qrSize; long qrSeen = -100000; String qrText = "";
    long qrSearchUntil;
    private float qsx, qsy, qss;      // QR suavizado

    /** O app pede leitura de QR só quando precisa (janela presa ou procurando). */
    boolean qrWanted(long now) {
        if (now < qrSearchUntil) return true;
        for (Win w : wins) if (w.pinned) return true;
        return false;
    }

    boolean qrVisible(long now) { return now - qrSeen < 700; }

    void setQr(float x, float y, float size, String text, long stamp) {
        if (stamp - qrSeen > 700) { qsx = x; qsy = y; qss = size; }
        qrX = x; qrY = y; qrSize = size; qrText = text == null ? "" : text; qrSeen = stamp;
    }

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
        // janelas presas ao QR code acompanham o objeto real
        long nowL = now();
        if (qrVisible(nowL)) {
            qsx += (qrX - qsx) * .45f; qsy += (qrY - qsy) * .45f; qss += (qrSize - qss) * .35f;
        }
        for (Win w : wins) {
            if (!w.pinned || w.minimized || (allHidden && cinemaWin == null) || (cinemaWin != null && w != cinemaWin)) continue;
            float[] q = toSpatial(qsx, qsy);
            w.cx = q[0] + w.pinDX * qss;
            w.cy = q[1] + w.pinDY * qss;
            w.s = Math.max(.05f, w.pinScale * qss);
            w.visible = nowL - qrSeen < 4000;          // some se o QR ficar muito tempo fora da câmera
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
    static boolean isWeb(int app) { return app == NAV || app == YOUTUBE || app == PLAYER; }

    private int nextTab = 100;

    /** Página (WebView) que a janela mostra agora: a aba atual do Navegador, ou o próprio app. */
    int wid(Win w) {
        if (w.app == NAV) {
            if (w.tabs.isEmpty()) w.tabs.add(nextTab++);
            w.tab = Math.max(0, Math.min(w.tabs.size() - 1, w.tab));
            return w.tabs.get(w.tab);
        }
        return w.app;
    }

    /** Páginas que estão à vista agora (só essas são copiadas para as janelas). */
    int[] webVisible() {
        int n = 0;
        for (Win w : wins) if (w.visible && isWeb(w.app)) n++;
        int[] out = new int[n];
        int i = 0;
        for (Win w : wins) if (w.visible && isWeb(w.app)) out[i++] = wid(w);
        return out;
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
        host.webAspect(wid(w), contentAspect(w));
        say("Modo cinema · B ou o botão da tela saem", now);
    }

    private void exitCinema() {
        if (cinemaWin == null) return;
        Win w = cinemaWin;
        w.yaw = cinemaBak[0]; w.pitch = cinemaBak[1]; w.dist = cinemaBak[2]; w.w = cinemaBak[3]; w.h = cinemaBak[4];
        cinemaWin = null;
        if (cfg.placement == Config.CINEMA) cfg.placement = Config.ESPACIAL;
        host.webAspect(wid(w), contentAspect(w));
    }

    boolean cinema() { return cinemaWin != null; }

    private void say(String s, long now) { toast = s; toastT = now; }
    /** Resposta do Visão: aparece na tela e é falada. */
    private void reply(String s, long now) { host.speak(s); }

    private float widest(List<String> ls, float size) {
        float w = 0;
        for (String l : ls) w = Math.max(w, g.width(l, size, g.medium));
        return w;
    }

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
        if (app == VOZ) { host.startVoice(); return null; }
        if (app == MIDIA) host.loadMedia();
        if (w != null) { w.minimized = false; toFront(w); if (isWeb(app)) host.webOpen(wid(w)); return w; }
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
        if (isWeb(app)) { host.webOpen(wid(w)); host.webAspect(wid(w), contentAspect(w)); }
        return w;
    }

    private void closeWin(Win w) {
        if (w == cinemaWin) exitCinema();
        wins.remove(w);
        // fecha as páginas da janela (para o vídeo/música não continuar tocando)
        if (w.app == NAV) { for (int id : w.tabs) host.webClose(id); w.tabs.clear(); }
        else if (isWeb(w.app)) host.webClose(w.app);
    }

    Parts parts(Win w) {
        Parts p = new Parts();
        float hw = w.w / 2, hh = w.h / 2;
        p.hw = hw; p.hh = hh;
        p.body = new RectF(-hw, -hh, hw, hh);
        p.title = new RectF(-hw, -hh, hw, -hh + .062f);
        p.content = new RectF(-hw + .022f, -hh + .074f, hw - .022f, hh - .022f);
        p.grab = new RectF(-.075f, hh + .03f, .075f, hh + .05f);
        p.scroll = new RectF(hw + .016f, -hh + .074f, hw + .046f, hh - .06f);   // barra de rolagem (navegadores)
        return p;
    }

    private float contentAspect(Win w) {
        Parts P = parts(w);
        RectF a = navArea(w, P);
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

    private int kbWeb() { return kbWin != null && isWeb(kbWin.app) ? wid(kbWin) : NAV; }
    private boolean kbYoutube() { return kbWin != null && kbWin.app == YOUTUBE; }

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
                    if (!u.isEmpty()) openUrl(kbWin, kbYoutube() ? youtubeUrl(u) : normalizeUrl(u));
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
            if (isWeb(w.app)) {
                RectF sb = P.scroll;
                float e = .014f * hs;
                if (wx > sb.left - e && wx < sb.right + e && wy > sb.top - e && wy < sb.bottom + e) {
                    Hit h = new Hit(H_SCROLL, "scroll:" + w.id); h.win = w; return h;
                }
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
            case H_SCROLL: {
                toFront(h.win);
                Win w = h.win;
                float fy = sbFrac(w, p);
                float[] th = thumb(w);
                if (th == null) {
                    p.sbDrag = true; p.sbOff = fy;                     // sem medida: arrasta por diferença
                } else if (fy >= th[0] - .03f && fy <= th[0] + th[1] + .03f) {
                    p.sbDrag = true; p.sbOff = fy - th[0];             // pegou a barrinha: arrasta
                    synth.hover();
                } else {
                    // clicou no trilho: pula uma tela para cima ou para baixo
                    synth.click();
                    float[] m = host.webMetrics(wid(w));
                    if (m != null && th != null) {
                        float range = Math.max(1, m[1] - m[2]);
                        float cur = m[0] / range, step = m[2] * .9f / range;
                        float to = Gfx.clamp(cur + (fy < th[0] ? -step : step), 0, 1);
                        host.webScrollTo(wid(w), to);
                        w.sbFrac = to; w.sbHoldUntil = now + 500;
                    } else host.webScroll(wid(w), 0, fy < .5f ? -420 : 420);
                }
                break;
            }
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
                } else if (w.app == NAVE) {
                    RectF C = parts(w).content;
                    w.shipTarget = Gfx.clamp((l[0] - C.centerX()) / C.width(), -.45f, .45f);
                    w.firing = true;
                    gameFire(w);
                } else if (isWeb(w.app)) {
                    // ainda não sabemos se é clique ou rolagem: decide ao mexer ou soltar
                    RectF a = navArea(w, parts(w));
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
            case "pin":
                if (w.pinned) {
                    // solta: a janela fica no ambiente, onde estava
                    float ry = (float) Math.atan(w.cx / f), rp = (float) Math.atan(w.cy / f);
                    if (spatial()) { w.yaw = HeadTracker.wrap(ry + headYaw); w.pitch = headPitch - rp; } else { w.yaw = ry; w.pitch = -rp; }
                    w.dist = Gfx.clamp((f / .5f) * 1.5f * cfg.uiScaleEff() / Math.max(.05f, w.s), cfg.minDist, 4.5f);
                    w.pinned = false;
                    say("Janela solta", now);
                } else if (qrVisible(now) && cfg.background == Config.BG_CAMERA) {
                    float[] q = toSpatial(qsx, qsy);
                    w.pinDX = (w.cx - q[0]) / qss; w.pinDY = (w.cy - q[1]) / qss; w.pinScale = w.s / qss;
                    w.pinned = true;
                    say("Janela presa no QR code" + (qrText.isEmpty() ? "" : " (" + qrText + ")"), now);
                } else {
                    qrSearchUntil = now + 10000;
                    say(cfg.background == Config.BG_CAMERA ? "Aponte a câmera para um QR code e aperte de novo" : "Use o fundo Câmera para prender em QR codes", now);
                }
                break;
            case "layer":
                w.behind = !w.behind;
                say(w.behind ? (cfg.segMode == Config.SEG_OFF ? "Atrás da pessoa (ligue o recorte de pessoas em Ajustes)" : "Janela atrás da pessoa")
                        : "Janela na frente", now);
                break;
            default: break;
        }
    }

    /** Onde o ponteiro está no trilho da barra (0 = topo, 1 = fim). */
    private float sbFrac(Win w, Ptr p) {
        RectF sb = parts(w).scroll;
        float[] l = local(w, p.x, p.y);
        return Gfx.clamp((l[1] - sb.top) / sb.height(), 0, 1);
    }

    /** [início, tamanho] da barrinha no trilho (0..1), ou null se a página não rola / ainda não se sabe. */
    private float[] thumb(Win w) {
        float[] m = host.webMetrics(wid(w));
        if (m == null) return null;
        float len = Gfx.clamp(m[2] / m[1], .1f, 1f);
        float pos = w.sbFrac >= 0 && now() < w.sbHoldUntil ? w.sbFrac : Gfx.clamp(m[0] / Math.max(1, m[1] - m[2]), 0, 1);
        return new float[]{pos * (1 - len), len};
    }

    private void hold(Ptr p) {
        if (p.sbDrag && p.hit != null && p.hit.win != null) {
            Win w = p.hit.win;
            float[] th = thumb(w);
            if (th == null) {
                // ainda sem a medida da página: arrastar a barra rola pelo "dedo virtual"
                float fy = sbFrac(w, p);
                int dy = Math.round((fy - p.sbOff) * 3000);
                if (dy != 0) { host.webScroll(wid(w), 0, dy); p.sbOff = fy; }
                return;
            }
            float top = Gfx.clamp(sbFrac(w, p) - p.sbOff, 0, 1 - th[1]);
            float frac = th[1] >= .999f ? 0 : top / (1 - th[1]);
            w.sbFrac = frac; w.sbHoldUntil = now() + 600;
            long t = now();
            if (t - p.sbSent > 40) { p.sbSent = t; host.webScrollTo(wid(w), frac); }
            return;
        }
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
            if (w.app == NAVE) {
                RectF C = parts(w).content;
                w.shipTarget = Gfx.clamp((l[0] - C.centerX()) / C.width(), -.45f, .45f);
                return;
            }
            if (w.app == DESENHO) {
                if (w.strokes.isEmpty()) return;
                Stroke s = w.strokes.get(w.strokes.size() - 1);
                float[] last = s.pts.get(s.pts.size() - 1);
                if (Math.hypot(l[0] - last[0], l[1] - last[1]) > .002f && s.pts.size() < 1500) s.pts.add(l);
            } else if (isWeb(w.app)) {
                RectF a = navArea(w, parts(w));
                float fx = (l[0] - a.left) / a.width(), fy = (l[1] - a.top) / a.height();
                if (p.webMode == 0 && Math.hypot((fx - p.wfx) * a.width(), (fy - p.wfy) * a.height()) > .018f) {
                    p.webMode = 1;                       // mexeu: vira rolagem
                    synth.hover();
                }
                if (p.webMode == 1) {
                    float pageW = WebPane.WIDTH, pageH = WebPane.WIDTH / Math.max(.3f, a.width() / a.height());
                    int dx = Math.round((p.wlx - fx) * pageW * 1.6f), dy = Math.round((p.wly - fy) * pageH * 1.6f);
                    if (dx != 0 || dy != 0) { host.webScroll(wid(w), dx, dy); p.wlx = fx; p.wly = fy; }
                }
            }
        }
    }

    private void release(Ptr p) {
        if (p.hit != null && p.hit.type == H_AREA && p.hit.win != null && p.hit.win.app == NAVE) p.hit.win.firing = false;
        if (p.hit != null && p.hit.type == H_AREA && p.hit.win != null && isWeb(p.hit.win.app) && p.webMode == 0) {
            // não mexeu: é um clique no ponto onde começou
            Win w = p.hit.win;
            host.webTouch(wid(w), MotionEvent.ACTION_DOWN, p.wfx, p.wfy);
            host.webTouch(wid(w), MotionEvent.ACTION_UP, p.wfx, p.wfy);
        }
        p.webMode = 0;
        if (p.resize != null && isWeb(p.resize.app)) host.webAspect(wid(p.resize), contentAspect(p.resize));
        if (p.dockDrag) {
            if (!p.dockMoved && p.dockHome) toggleWindows(now());
            else if (p.dockMoved) { host.configChanged(); say("Dock movido", now()); }
        }
        if (p.sbDrag && p.hit != null && p.hit.win != null && p.hit.win.sbFrac >= 0) host.webScrollTo(wid(p.hit.win), p.hit.win.sbFrac);
        p.sbDrag = false;
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
                if (down) host.startVoice();                    // R3: comando de voz
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: if (down) { gazeX = Gfx.clamp(gazeX - .03f, -.4f, .4f); gazeForced = true; } return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (down) { gazeX = Gfx.clamp(gazeX + .03f, -.4f, .4f); gazeForced = true; } return true;
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: {
                if (!down) return true;
                int dir = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                Win w = target();
                if (w != null && isWeb(w.app)) host.webScroll(wid(w), 0, dir * 260);       // rola a página
                else { gazeY = Gfx.clamp(gazeY + dir * .03f, -.4f, .4f); gazeForced = true; }
                return true;
            }
            default: return false;
        }
    }

    /* ================= arrumação das janelas ================= */

    volatile String layoutCache = "";
    private long lastLayoutSave;

    /** Janelas abertas em texto (uma por linha): app;yaw;pitch;dist;w;h;minimizada;aba;endereços. */
    private String layoutString() {
        StringBuilder b = new StringBuilder();
        for (Win w : wins) {
            if (w.app == PLAYER || w == cinemaWin) continue;
            b.append(w.app).append(';').append(w.yaw).append(';').append(w.pitch).append(';').append(w.dist).append(';')
                    .append(w.w).append(';').append(w.h).append(';').append(w.minimized ? 1 : 0).append(';').append(w.tab).append(';');
            if (w.app == NAV) {
                for (int i = 0; i < w.tabs.size(); i++) {
                    if (i > 0) b.append('\u0001');
                    String u = host.webIsHome(w.tabs.get(i)) ? "" : host.webUrl(w.tabs.get(i));
                    b.append(u == null ? "" : u.replace(";", "%3B").replace("\n", ""));
                }
            } else if (w.app == YOUTUBE) {
                String u = host.webUrl(YOUTUBE);
                b.append(u == null ? "" : u.replace(";", "%3B").replace("\n", ""));
            }
            b.append('\n');
        }
        return b.toString();
    }

    /** Reabre as janelas como estavam (posições relativas à frente atual). */
    void restoreLayout(String text, long now) {
        if (text == null || text.trim().isEmpty() || !wins.isEmpty()) return;
        boolean wasSpatial = spatial();
        for (String line : text.split("\n")) {
            try {
                String[] f = line.split(";", -1);
                if (f.length < 8) continue;
                int app = Integer.parseInt(f[0]);
                if (app < 0 || app >= NAMES.length || app == VOZ || app == CINEMA) continue;
                Win w = new Win();
                w.id = ++wid; w.app = app;
                w.yaw = Float.parseFloat(f[1]); w.pitch = Float.parseFloat(f[2]); w.dist = Float.parseFloat(f[3]);
                w.w = Float.parseFloat(f[4]); w.h = Float.parseFloat(f[5]);
                w.minimized = "1".equals(f[6]);
                w.born = now;
                if (!wasSpatial) { w.yaw = Gfx.clamp(w.yaw, -.6f, .6f); w.pitch = Gfx.clamp(w.pitch, -.4f, .4f); }
                wins.add(w);
                String urls = f.length > 8 ? f[8] : "";
                if (app == NAV) {
                    String[] us = urls.split("\u0001", -1);
                    for (String u : us) {
                        w.tabs.add(nextTab++);
                        int id = w.tabs.get(w.tabs.size() - 1);
                        host.webOpen(id);
                        if (!u.isEmpty()) { String uu = u.replace("%3B", ";"); if (needsDesktop(uu)) host.webDesktop(id, true); host.webLoad(id, uu); }
                    }
                    w.tab = Math.max(0, Math.min(w.tabs.size() - 1, Integer.parseInt(f[7])));
                    host.webAspect(wid(w), contentAspect(w));
                } else if (app == YOUTUBE) {
                    host.webOpen(YOUTUBE);
                    if (!urls.isEmpty()) host.webLoad(YOUTUBE, urls.replace("%3B", ";"));
                    host.webAspect(YOUTUBE, contentAspect(w));
                } else if (app == MIDIA) host.loadMedia();
            } catch (Throwable ignored) { }
        }
        if (!wins.isEmpty()) say("Suas janelas voltaram onde estavam", now);
    }

    /* ================= jogo: Nave ================= */

    private void gameReset(Win w) {
        w.shots.clear(); w.rocks.clear();
        w.score = 0; w.lives = 3; w.over = false; w.shipX = 0; w.spawn = 0; w.hurt = 0;
    }

    private void gameFire(Win w) {
        if (w.over) { gameReset(w); return; }
        if (w.fireT > 0) return;
        w.fireT = .22f;
        w.shots.add(new float[]{w.shipX, 0});
        synth.click();
    }

    /** Atualiza o jogo (coordenadas: x de -0.5 a 0.5, y de 0 = nave a 1 = topo). */
    private void updateGame(Win w, float dt, long now) {
        if (w.over) return;
        // controle: analógico esquerdo move a nave quando o jogo está na mira
        boolean aimed = target() == w;
        if (aimed && padActive(now) && Math.abs(padLX) > .15f) w.shipX += padLX * 1.1f * dt;
        else if (!Float.isNaN(w.shipTarget)) w.shipX += (w.shipTarget - w.shipX) * Math.min(1, dt * 10);
        w.shipX = Gfx.clamp(w.shipX, -.45f, .45f);
        boolean padFire = aimed && (padDown.contains(KeyEvent.KEYCODE_BUTTON_A) || padRT > .5f);
        w.fireT -= dt;
        if (w.firing || padFire) gameFire(w);
        w.hurt = Math.max(0, w.hurt - dt);
        // meteoros caindo cada vez mais rápido
        float speed = .22f + Math.min(.5f, w.score * .006f);
        w.spawn -= dt;
        if (w.spawn <= 0) { w.spawn = Math.max(.35f, 1.1f - w.score * .01f); w.rocks.add(new float[]{rnd.nextFloat() * .9f - .45f, 1.05f, .025f + rnd.nextFloat() * .03f, rnd.nextFloat() * 6.28f}); }
        for (Iterator<float[]> it = w.shots.iterator(); it.hasNext(); ) { float[] b = it.next(); b[1] += 1.4f * dt; if (b[1] > 1.1f) it.remove(); }
        for (Iterator<float[]> it = w.rocks.iterator(); it.hasNext(); ) {
            float[] r = it.next();
            r[1] -= speed * dt; r[3] += dt;
            boolean hit = false;
            for (Iterator<float[]> is = w.shots.iterator(); is.hasNext(); ) {
                float[] b = is.next();
                if (Math.hypot(b[0] - r[0], (b[1] - r[1]) * .6f) < r[2] + .012f) { is.remove(); hit = true; break; }
            }
            if (hit) { it.remove(); w.score++; synth.pop(); continue; }
            if (r[1] < .06f && Math.abs(r[0] - w.shipX) < r[2] + .03f) {
                it.remove(); w.lives--; w.hurt = .5f; synth.note(0);
                if (w.lives <= 0) { w.over = true; w.best = Math.max(w.best, w.score); w.firing = false; }
                continue;
            }
            if (r[1] < -.05f) it.remove();
        }
    }

    private void drawGame(Win w, Parts P, long now) {
        RectF C = P.content;
        g.c.save();
        g.clipRound(C, .016f);
        g.fill.setColor(0xFF070A1A);
        g.c.drawRect(C, g.fill);
        float t = now / 1000f;
        for (int i = 0; i < 40; i++) {                                   // estrelas passando
            float sx = C.left + ((i * 73) % 100) / 100f * C.width();
            float sy = C.top + (((i * 37) % 100) / 100f + t * (.05f + (i % 3) * .03f)) % 1f * C.height();
            g.circle(sx, sy, .0015f + (i % 3) * .0008f, 0x99FFFFFF);
        }
        float gw = C.width(), gh = C.height() - .03f, ox = C.centerX(), oy = C.bottom - .03f;
        for (float[] b : w.shots) { g.fill.setColor(0xFF7CF3FF); g.c.drawRoundRect(new RectF(ox + b[0] * gw - .003f, oy - b[1] * gh - .016f, ox + b[0] * gw + .003f, oy - b[1] * gh), .003f, .003f, g.fill); }
        for (float[] r : w.rocks) {
            float rx = ox + r[0] * gw, ry = oy - r[1] * gh, rr = r[2] * gw;
            g.c.save(); g.c.rotate((float) Math.toDegrees(r[3]), rx, ry);
            g.fill.setColor(0xFF8A6A55);
            g.c.drawRoundRect(new RectF(rx - rr, ry - rr * .85f, rx + rr, ry + rr * .85f), rr * .6f, rr * .6f, g.fill);
            g.circle(rx - rr * .3f, ry - rr * .2f, rr * .22f, 0xFF6B5142);
            g.c.restore();
        }
        if (!w.over) {
            float sx = ox + w.shipX * gw, sy = oy;
            if (w.hurt <= 0 || (now / 80) % 2 == 0) {
                Path sh = new Path();
                sh.moveTo(sx, sy - .045f); sh.lineTo(sx + .028f, sy + .012f); sh.lineTo(sx, sy); sh.lineTo(sx - .028f, sy + .012f); sh.close();
                g.fill.setColor(0xFFE8F1FF);
                g.c.drawPath(sh, g.fill);
                g.circle(sx, sy - .018f, .006f, 0xFF6EE7F9);
                g.circle(sx, sy + .008f, .006f + .003f * (float) Math.sin(now / 40.0), 0xCCFF9F43);
            }
        }
        g.c.restore();
        g.txt("Pontos " + w.score, C.left + .02f, C.top + .022f, .018f, Color.WHITE, Paint.Align.LEFT, g.medium);
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < w.lives; i++) hearts.append("♥ ");
        g.txt(hearts.toString().trim(), C.right - .02f, C.top + .022f, .018f, 0xFFFF6B6B, Paint.Align.RIGHT, g.medium);
        if (w.over) {
            g.txt("Fim de jogo", C.centerX(), C.centerY() - .04f, .04f, Color.WHITE, Paint.Align.CENTER, g.medium);
            g.txt("Pontos: " + w.score + "   ·   Recorde: " + w.best, C.centerX(), C.centerY() + .01f, .018f, 0xCCFFFFFF, Paint.Align.CENTER, g.regular);
            g.txt("Aperte A (ou faça a pinça) para jogar de novo", C.centerX(), C.centerY() + .05f, .016f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
        } else if (w.score == 0 && w.rocks.size() < 2)
            g.txt("Analógico ou mira move · A ou pinça atira", C.centerX(), C.centerY(), .016f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
    }

    /* ================= comando de voz ================= */

    private static String plain(String t) {
        String n = java.text.Normalizer.normalize(t.toLowerCase(new Locale("pt", "BR")), java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").trim();
    }

    private static final Object[][] VOICE_APPS = {
            {NAV, "navegador", "internet", "chrome", "google"}, {YOUTUBE, "youtube"}, {MIDIA, "midia", "videos", "musicas", "player"},
            {GALERIA, "galeria", "fotos"}, {CAMERA, "camera"}, {AJUSTES, "ajustes", "configuracoes", "configuracao"},
            {ARQUIVOS, "arquivos"}, {MENU, "menu", "aplicativos", "apps"}, {RELOGIO, "relogio", "hora"}, {DESENHO, "desenho"},
            {BOLHAS, "bolhas"}, {MUSICA, "piano", "teclas"}, {NOTAS, "notas", "bloco"}, {CONTROLE, "controle"},
            {NAVE, "nave", "jogo"}, {WEB, "sites", "web"}};

    /** Executa o que a pessoa falou (ou digita, se o teclado estiver aberto). O Visão responde falando. */
    void voiceCommand(String text, long now) {
        if (text == null || text.trim().isEmpty()) return;
        if (kbOpen) { kbType(text); say("Ditado: " + text, now); return; }
        String t = plain(text).replaceAll("[,.!?]", "").replaceAll("\\s+", " ");
        t = t.replaceFirst("^(ei |oi |hey |e )?visao ", "").trim();
        String raw = text.trim().replaceFirst("(?i)^(ei |oi |hey |e )?vis[aã]o,? ", "").trim();
        if (t.isEmpty() || t.equals("visao")) { reply("Sim? Estou ouvindo.", now); host.startVoice(); return; }
        if (t.startsWith("pare de falar") || t.startsWith("silencio") || t.startsWith("cala") || t.equals("chega")) { host.speak(""); return; }
        String chat = host.assistantChat(t);
        if (chat != null) { reply(chat, now); return; }
        String[] go = {"abrir ", "abre ", "abra ", "mostrar ", "ir para ", "entrar no ", "entrar na "};
        for (String g0 : go) if (t.startsWith(g0)) {
            String what = t.substring(g0.length()).replaceFirst("^(o |a |os |as )", "");
            for (Object[] e : VOICE_APPS) for (int i = 1; i < e.length; i++)
                if (what.startsWith((String) e[i])) { openApp((Integer) e[0], now); reply("Abrindo " + NAMES[(Integer) e[0]] + ".", now); return; }
            openInNewTab(normalizeUrl(what.replace(" ponto ", ".").replace(" ", "")), now);
            reply("Abrindo " + what + ".", now);
            return;
        }
        String[] search = {"pesquisar ", "pesquise ", "pesquisa ", "procurar ", "procure ", "buscar ", "busque ", "tocar ", "toque ", "toca ", "assistir ", "coloca ", "coloque "};
        for (String s0 : search) if (t.startsWith(s0)) {
            String q = raw.substring(Math.min(raw.length(), s0.length())).trim();
            boolean yt = t.contains("youtube") || s0.startsWith("toc") || s0.startsWith("assist") || s0.startsWith("coloc");
            q = q.replaceAll("(?i)\\s*(no|na) youtube\\s*$", "").replaceAll("(?i)\\s*(no|na) (google|internet)\\s*$", "");
            if (yt) { Win y = openApp(YOUTUBE, now); if (y != null) openUrl(y, youtubeUrl(q)); reply("Procurando " + q + " no YouTube.", now); }
            else { openInNewTab(normalizeUrl(q), now); reply("Pesquisando " + q + ".", now); }
            return;
        }
        if (t.startsWith("fechar tudo") || t.startsWith("feche tudo") || t.startsWith("fecha tudo")) { for (Win w : new ArrayList<>(wins)) closeWin(w); reply("Fechei tudo.", now); return; }
        if (t.startsWith("fechar") || t.startsWith("feche") || t.startsWith("fecha")) { Win w = target(); if (w != null) { closeWin(w); reply("Fechado.", now); } return; }
        if (t.startsWith("minimizar")) { Win w = target(); if (w != null) w.minimized = true; return; }
        if (t.contains("centraliz") || t.equals("centro")) { host.recenter(); reply("Tudo na sua frente.", now); return; }
        if (t.startsWith("sair do cinema")) { exitCinema(); return; }
        if (t.contains("cinema")) { enterCinema(now); reply("Modo cinema. Bom filme.", now); return; }
        if (t.startsWith("parar") && host.isRecording()) { host.stopRecording(); reply("Gravação salva na galeria.", now); return; }
        if (t.contains("gravar") || t.contains("gravacao")) { if (!host.isRecording()) host.startRecording(); say("Gravando…", now); return; }
        if (t.contains("foto")) { Bitmap fr = frame; if (fr != null) { host.savePhoto(fr); synth.shutter(); reply("Foto salva.", now); } return; }
        if (t.contains("teclado")) { toggleKeyboard(now); return; }
        if (t.startsWith("voltar")) { Win w = target(); if (w != null && isWeb(w.app)) host.webBack(wid(w)); return; }
        if (t.contains("previsao do tempo") || t.contains("clima") || t.contains("vai chover")) {
            openInNewTab(normalizeUrl("previsão do tempo"), now); reply("Abri a previsão do tempo para você.", now); return;
        }
        // perguntas: o Visão pesquisa na internet
        String[] ask = {"o que ", "quem ", "qual ", "quais ", "quando ", "onde ", "como ", "por que ", "porque ", "quanto ", "quantos ", "quantas ", "me fala ", "me diga "};
        for (String a0 : ask) if (t.startsWith(a0)) {
            openInNewTab(normalizeUrl(raw), now);
            reply("Pesquisei isso para você.", now);
            return;
        }
        reply("Desculpe, não entendi: " + raw + ".", now);
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
            if (w != null && isWeb(w.app)) host.webScroll(wid(w), 0, (hatY > 0 ? 1 : -1) * 220);
        }
        final float dz = .18f;
        Win tw0 = target();
        boolean game = tw0 != null && tw0.app == NAVE && !tw0.over;
        float lx = !game && Math.abs(padLX) > dz ? padLX : 0, ly = !game && Math.abs(padLY) > dz ? padLY : 0;
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
                    host.webScroll(wid(w), Math.round(scrollAccX), Math.round(scrollAccY));
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
    int webFocusId() { Win w = front(); return w != null && isWeb(w.app) ? wid(w) : -1; }

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
            if (p.sbDrag && p.hit != null) hov.add(p.hit.key);
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
        for (Win w : wins) if (w.app == NAVE && w.visible) updateGame(w, dt, now);
        if (now - lastLayoutSave > 2000) { lastLayoutSave = now; layoutCache = layoutString(); }
    }

    /* ================= botões dos apps ================= */

    private RectF navArea(Win w, Parts P) {
        RectF C = P.content;
        return new RectF(C.left, C.top + (w.app == NAV ? .096f : .056f), C.right, C.bottom);
    }

    private RectF area(Win w, Parts P) {
        if (w.app == DESENHO || w.app == NAVE) return P.content;
        if (isWeb(w.app)) return navArea(w, P);
        return null;
    }

    private int galleryCols(Parts P) { return Math.max(3, Math.round(P.content.width() / .145f)); }

    private List<Btn> buttons(Win w, Parts P) {
        ArrayList<Btn> out = new ArrayList<>();
        RectF B = P.body, C = P.content;
        switch (w.app) {
            case NAV: case YOUTUBE: case PLAYER: {
                float y = C.top + .024f;
                boolean nav = w.app == NAV;
                out.add(new Btn("back", C.left + .022f, y, .018f));
                out.add(new Btn("reload", C.left + .066f, y, .018f));
                float left = C.left + .1f, right = C.right - .104f;
                if (nav) { out.add(new Btn("home", C.left + .11f, y, .018f)); left = C.left + .144f; right = C.right - .148f;
                           out.add(new Btn("star", C.right - .11f, y, .018f)); }
                out.add(new Btn("addr", new RectF(left, y - .019f, right, y + .019f)));
                out.add(new Btn("kb", C.right - .066f, y, .018f));
                out.add(new Btn("cine", C.right - .022f, y, .018f));
                if (nav) {
                    // abas
                    float ty = C.top + .07f, th = .034f;
                    int n = w.tabs.size();
                    float avail = C.width() - .1f, tw = Math.min(.2f, (avail - .04f) / Math.max(1, n));
                    for (int i = 0; i < n; i++) {
                        float x = C.left + i * tw;
                        out.add(new Btn("tb" + i, new RectF(x, ty - th / 2, x + tw - .006f, ty + th / 2)));
                    }
                    out.add(new Btn("tn", C.left + n * tw + .018f, ty, .015f));
                    out.add(new Btn("tx", C.right - .066f, ty, .015f));
                    out.add(new Btn("pc", C.right - .022f, ty, .015f));
                    // página inicial: favoritos e recentes
                    if (host.webIsHome(wid(w))) {
                        RectF a = navArea(w, P);
                        List<String[]> favs = favorites();
                        int cols = 4, nf = Math.min(8, favs.size());
                        float gx = .012f, cw = (a.width() - .04f - gx * (cols - 1)) / cols, chh = .075f;
                        for (int i = 0; i < nf; i++) {
                            float x = a.left + .02f + (i % cols) * (cw + gx), yy = a.top + .05f + (i / cols) * (chh + gx);
                            out.add(new Btn("fv" + i, new RectF(x, yy, x + cw, yy + chh)));
                        }
                        List<String[]> his = host.history();
                        float hy = a.top + .05f + 2 * (chh + gx) + .05f, rh = .042f;
                        int nh = Math.min(his.size(), Math.max(0, (int) ((a.bottom - hy - .01f) / rh)));
                        for (int i = 0; i < nh; i++) out.add(new Btn("hs" + i, new RectF(a.left + .02f, hy + i * rh, a.right - .02f, hy + i * rh + rh - .006f)));
                    }
                }
                break;
            }
            case MIDIA: {
                out.add(new Btn("mk0", new RectF(C.left, C.top, C.left + .14f, C.top + .04f)));
                out.add(new Btn("mk1", new RectF(C.left + .15f, C.top, C.left + .29f, C.top + .04f)));
                if (!host.mediaAllowed()) {
                    out.add(new Btn("perm", new RectF(C.centerX() - .16f, C.centerY() - .025f, C.centerX() + .16f, C.centerY() + .025f)));
                    break;
                }
                List<Host.MediaItem> items = host.mediaList(w.mediaKind);
                float rh = .05f, top = C.top + .055f;
                int per = Math.max(1, (int) ((C.bottom - .05f - top) / rh));
                int start = w.page * per;
                for (int i = 0; i < per && start + i < items.size(); i++) {
                    float y = top + i * rh;
                    out.add(new Btn("mi" + (start + i), new RectF(C.left, y, C.right, y + rh - .006f)));
                }
                out.add(new Btn("pprev", C.right - .085f, C.bottom - .025f, .019f));
                out.add(new Btn("pnext", C.right - .025f, C.bottom - .025f, .019f));
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
                float gap = .014f, cw = (C.width() - gap * 2) / 3, chh = (C.height() - gap * 2) / 3;
                for (int i = 0; i < SITES.length; i++) {
                    float x = C.left + (i % 3) * (cw + gap), y = C.top + (i / 3) * (chh + gap);
                    out.add(new Btn("s" + i, new RectF(x, y, x + cw, y + chh)));
                }
                break;
            }
            case MENU: {
                int cols = 6, rowsN = (MENU_APPS.length + cols - 1) / cols;
                float cw = C.width() / cols, chh = C.height() / rowsN;
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
            case NAV: case YOUTUBE: case PLAYER:
                if (id.equals("home")) host.webHome(wid(w));
                else if (id.equals("star")) {
                    String u = host.webUrl(wid(w));
                    if (u == null || u.isEmpty() || host.webIsHome(wid(w))) say("Abra uma página para favoritar", now);
                    else say(host.toggleFavorite(host.webTitle(wid(w)), u) ? "Adicionado aos favoritos ★" : "Removido dos favoritos", now);
                }
                else if (id.startsWith("tb")) { w.tab = Integer.parseInt(id.substring(2)); host.webOpen(wid(w)); host.webAspect(wid(w), contentAspect(w)); }
                else if (id.equals("tn")) newTab(w, null);
                else if (id.equals("tx")) closeTab(w, now);
                else if (id.equals("pc")) { boolean d = !host.webIsDesktop(wid(w)); host.webDesktop(wid(w), d); say(d ? "Modo computador" : "Modo celular", now); }
                else if (id.startsWith("fv")) { List<String[]> f = favorites(); int i = Integer.parseInt(id.substring(2)); if (i < f.size()) openUrl(w, f.get(i)[1]); }
                else if (id.startsWith("hs")) { List<String[]> hl = host.history(); int i = Integer.parseInt(id.substring(2)); if (i < hl.size()) openUrl(w, hl.get(i)[1]); }
                else if (id.equals("addr") && w.app == PLAYER) openApp(MIDIA, now);
                else if (id.equals("back")) host.webBack(wid(w));
                else if (id.equals("reload")) host.webReload(wid(w));
                else if (id.equals("cine")) { if (cinemaWin == w) exitCinema(); else { exitCinema(); enterCinemaWith(w, now); } }
                else if (id.equals("addr")) openKeyboard(T_URL, w);
                else if (id.equals("kb")) { if (kbOpen && kbTarget == T_WEB) kbOpen = false; else openKeyboard(T_WEB, w); }
                break;
            case MIDIA: {
                if (id.equals("mk0") || id.equals("mk1")) { w.mediaKind = id.charAt(2) - '0'; w.page = 0; host.loadMedia(); }
                else if (id.equals("perm")) host.requestMedia();
                else if (id.equals("pprev")) w.page = Math.max(0, w.page - 1);
                else if (id.equals("pnext")) { if (buttons(w, parts(w)).stream().anyMatch(b -> b.id.startsWith("mi"))) w.page++; }
                else if (id.startsWith("mi")) {
                    List<Host.MediaItem> items = host.mediaList(w.mediaKind);
                    int i = Integer.parseInt(id.substring(2));
                    if (i < items.size()) {
                        Win pw = openApp(PLAYER, now);
                        host.playMedia(items.get(i));
                        if (pw != null) say("Tocando: " + items.get(i).title, now);
                    }
                }
                break;
            }
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
                    int site = Integer.parseInt(id.substring(1));
                    if (site == 0) { Win y = openApp(YOUTUBE, now); if (y != null) openUrl(y, SITES[0][1]); }
                    else openInNewTab(SITES[site][1], now);
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

    private List<String[]> favorites() {
        List<String[]> f = host.favorites();
        if (!f.isEmpty()) return f;
        ArrayList<String[]> d = new ArrayList<>();
        for (String[] x : DEFAULT_FAVS) d.add(x);
        return d;
    }

    /** Abre um endereço na página atual da janela (ligando o modo computador quando o site pede). */
    void openUrl(Win w, String url) {
        if (w == null) return;
        int id = wid(w);
        host.webOpen(id);
        if (needsDesktop(url)) host.webDesktop(id, true);
        host.webLoad(id, url);
    }

    private void newTab(Win w, String url) {
        if (w.tabs.size() >= 8) { say("Máximo de 8 abas", now()); return; }
        w.tabs.add(nextTab++);
        w.tab = w.tabs.size() - 1;
        host.webOpen(wid(w));
        host.webAspect(wid(w), contentAspect(w));
        if (url != null) openUrl(w, url);
    }

    private void closeTab(Win w, long now) {
        if (w.tabs.size() <= 1) { host.webHome(wid(w)); return; }
        host.webClose(wid(w));
        w.tabs.remove(w.tab);
        w.tab = Math.max(0, w.tab - 1);
        host.webOpen(wid(w));
    }

    /** Abre um site numa aba nova do Navegador. */
    void openInNewTab(String url, long now) {
        Win w = find(NAV);
        if (w == null) { w = openApp(NAV, now); if (w != null) openUrl(w, url); return; }
        openApp(NAV, now);
        if (host.webIsHome(wid(w))) openUrl(w, url); else newTab(w, url);
    }

    private List<Row> rows() {
        if (rows != null) return rows;
        final String[] place = {"HUD", "Espacial", "Cinema"};
        final String[] bg = Config.BG_NAMES;
        final String[] seg = {"Desligado", "Recorte", "Silhueta", "Contorno"};
        ArrayList<Row> r = new ArrayList<>();
        r.add(new Row("Posição das janelas", Row.CYCLE, () -> place[cfg.placement],
                () -> setPlacement((cfg.placement + 2) % 3, now()), () -> setPlacement((cfg.placement + 1) % 3, now())));
        r.add(new Row("Fundo", Row.CYCLE, () -> bg[cfg.background],
                () -> cfg.background = (cfg.background + bg.length - 1) % bg.length, () -> cfg.background = (cfg.background + 1) % bg.length));
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
        if (isWeb(w.app)) { String t = host.webTitle(wid(w)); if (t != null && !t.isEmpty()) title = t; }
        float titleMax = P.body.width() - .32f;
        g.circle(P.body.left + .034f, tbtnY(P), .012f, COLS[w.app][1]);
        g.txt(g.fit(title, .022f, titleMax, g.medium), P.body.left + .056f, tbtnY(P), .022f, 0xE6FFFFFF, Paint.Align.LEFT, g.medium);
        // controles da barra
        for (int i = 0; i < TBTN.length; i++) {
            float bx = tbtnX(P, i), by = tbtnY(P);
            boolean h = hov.contains("tbtn:" + w.id + ":" + TBTN[i]);
            boolean on = (TBTN[i].equals("layer") && w.behind) || (TBTN[i].equals("pin") && w.pinned);
            g.circle(bx, by, .016f * (h ? 1.18f : 1f), h ? 0xF2FFFFFF : (on ? 0xCC7CC4FF : 0x33FFFFFF));
            int ic = h ? 0xFF15171E : 0xE6FFFFFF;
            float q = .0065f;
            switch (TBTN[i]) {
                case "close": g.line(bx - q, by - q, bx + q, by + q, .0024f, ic); g.line(bx + q, by - q, bx - q, by + q, .0024f, ic); break;
                case "min": g.line(bx - q, by + .002f, bx + q, by + .002f, .0026f, ic); break;
                case "far": g.line(bx - q, by, bx + q, by, .0026f, ic); break;
                case "near": g.line(bx - q, by, bx + q, by, .0026f, ic); g.line(bx, by - q, bx, by + q, .0026f, ic); break;
                case "pin":
                    g.ring(bx, by - .002f, .0045f, .0022f, ic);
                    g.line(bx, by + .0025f, bx, by + .008f, .0022f, ic);
                    break;
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
            case NAV: case YOUTUBE: case PLAYER: drawNav(w, P); break;
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
            case MIDIA: drawMedia(w, P); break;
            case NAVE: drawGame(w, P, now); break;
            case CONTROLE: drawPad(w, P, now); break;
            default: break;
        }

        // barra de rolagem (Navegador e YouTube)
        if (isWeb(w.app)) {
            RectF sb = P.scroll;
            boolean sh = hov.contains("scroll:" + w.id);
            float rr = sb.width() / 2;
            c.drawRoundRect(sb, rr, rr, paintOf(sh ? 0x40FFFFFF : 0x24FFFFFF));
            float[] th = thumb(w);
            float t0 = th == null ? 0 : th[0], tl = th == null ? .18f : th[1];
            RectF tr = new RectF(sb.left + .004f, sb.top + sb.height() * t0 + .004f, sb.right - .004f, sb.top + sb.height() * (t0 + tl) - .004f);
            c.drawRoundRect(tr, rr, rr, paintOf(sh ? 0xF2FFFFFF : (th == null ? 0x55FFFFFF : 0xB3FFFFFF)));
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
        RectF a = navArea(w, P);
        int pid = wid(w);
        boolean home = w.app == NAV && host.webIsHome(pid);
        String curUrl = host.webUrl(pid);
        for (Btn b : buttons(w, P)) {
            if (b.id.startsWith("fv") || b.id.startsWith("hs")) continue;      // desenhados na página inicial
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            if (b.id.startsWith("tb")) {
                int i = Integer.parseInt(b.id.substring(2));
                boolean cur = i == w.tab;
                g.fill.setColor(cur ? 0x4DFFFFFF : (h ? 0x33FFFFFF : 0x1AFFFFFF));
                g.c.drawRoundRect(b.rect, .01f, .01f, g.fill);
                int tid = w.tabs.get(i);
                String t = host.webIsHome(tid) ? "Nova aba" : host.webTitle(tid);
                if (t == null || t.isEmpty()) t = "Carregando…";
                g.txt(g.fit(t, .014f, b.rect.width() - .016f, g.regular), b.rect.left + .008f, b.rect.centerY(), .014f,
                        cur ? Color.WHITE : 0xB3FFFFFF, Paint.Align.LEFT, cur ? g.medium : g.regular);
                continue;
            }
            if (b.circle) {
                boolean on = (b.id.equals("kb") && kbOpen && kbTarget == T_WEB)
                        || (b.id.equals("pc") && host.webIsDesktop(pid))
                        || (b.id.equals("star") && curUrl != null && !home && host.isFavorite(curUrl));
                g.circle(b.x, b.y, b.r, h ? 0xF2FFFFFF : (on ? 0xCC7CC4FF : 0x2EFFFFFF));
                int ic = h ? 0xFF15171E : 0xFFFFFFFF;
                float q = b.r * .45f;
                switch (b.id) {
                    case "back":
                        g.line(b.x + q * .5f, b.y - q, b.x - q * .5f, b.y, .0026f, ic);
                        g.line(b.x - q * .5f, b.y, b.x + q * .5f, b.y + q, .0026f, ic);
                        break;
                    case "cine": {
                        boolean cine = cinemaWin == w;
                        g.stroke.setColor(ic); g.stroke.setStrokeWidth(.002f);
                        float k = cine ? q * .65f : q;
                        g.c.drawRoundRect(new RectF(b.x - k * 1.3f, b.y - k, b.x + k * 1.3f, b.y + k), .002f, .002f, g.stroke);
                        break;
                    }
                    case "kb":
                        g.stroke.setColor(ic); g.stroke.setStrokeWidth(.002f);
                        g.c.drawRoundRect(new RectF(b.x - .01f, b.y - .0065f, b.x + .01f, b.y + .0065f), .002f, .002f, g.stroke);
                        for (int i = 0; i < 4; i++) g.circle(b.x - .0055f + i * .0037f, b.y - .002f, .0009f, ic);
                        g.line(b.x - .004f, b.y + .003f, b.x + .004f, b.y + .003f, .0016f, ic);
                        break;
                    case "home": {
                        Path hp = new Path();
                        hp.moveTo(b.x - q, b.y); hp.lineTo(b.x, b.y - q); hp.lineTo(b.x + q, b.y);
                        hp.moveTo(b.x - q * .7f, b.y - q * .2f); hp.lineTo(b.x - q * .7f, b.y + q); hp.lineTo(b.x + q * .7f, b.y + q); hp.lineTo(b.x + q * .7f, b.y - q * .2f);
                        g.stroke.setColor(ic); g.stroke.setStrokeWidth(.0022f);
                        g.c.drawPath(hp, g.stroke);
                        break;
                    }
                    case "star": {
                        Path sp = new Path();
                        for (int i = 0; i < 10; i++) {
                            double ang = -Math.PI / 2 + i * Math.PI / 5;
                            float rr = i % 2 == 0 ? q * 1.15f : q * .5f;
                            float x = b.x + (float) Math.cos(ang) * rr, y = b.y + (float) Math.sin(ang) * rr;
                            if (i == 0) sp.moveTo(x, y); else sp.lineTo(x, y);
                        }
                        sp.close();
                        g.fill.setColor(ic);
                        g.c.drawPath(sp, g.fill);
                        break;
                    }
                    case "tn":
                        g.line(b.x - q, b.y, b.x + q, b.y, .0024f, ic);
                        g.line(b.x, b.y - q, b.x, b.y + q, .0024f, ic);
                        break;
                    case "tx":
                        g.line(b.x - q * .8f, b.y - q * .8f, b.x + q * .8f, b.y + q * .8f, .0022f, ic);
                        g.line(b.x + q * .8f, b.y - q * .8f, b.x - q * .8f, b.y + q * .8f, .0022f, ic);
                        break;
                    case "pc":
                        g.stroke.setColor(ic); g.stroke.setStrokeWidth(.0018f);
                        g.c.drawRect(b.x - q * 1.1f, b.y - q * .8f, b.x + q * 1.1f, b.y + q * .4f, g.stroke);
                        g.line(b.x - q * .5f, b.y + q, b.x + q * .5f, b.y + q, .0018f, ic);
                        break;
                    default:
                        g.stroke.setColor(ic); g.stroke.setStrokeWidth(.0024f);
                        g.c.drawArc(new RectF(b.x - q, b.y - q, b.x + q, b.y + q), 40, 290, false, g.stroke);
                        break;
                }
            } else {
                boolean editing = kbOpen && kbTarget == T_URL && kbWin == w;
                g.glass(b.rect, b.rect.height() / 2, h || editing, false);
                float ix = b.rect.left + .02f, iy = b.rect.centerY();
                g.ring(ix, iy - .002f, .006f, .002f, 0xCCFFFFFF);
                g.line(ix + .004f, iy + .002f, ix + .008f, iy + .006f, .002f, 0xCCFFFFFF);
                String txt;
                int col = Color.WHITE;
                String hint = w.app == YOUTUBE ? "Pesquisar no YouTube" : w.app == PLAYER ? "Escolher vídeo ou música" : "Pesquisar ou digitar endereço";
                if (editing) { txt = urlBuf.length() == 0 ? hint : urlBuf + "|"; if (urlBuf.length() == 0) col = 0x99FFFFFF; }
                else {
                    String u = w.app == YOUTUBE || home ? "" : w.app == PLAYER ? host.webTitle(pid) : curUrl;
                    txt = (u == null || u.isEmpty()) ? hint : u.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
                    if (u == null || u.isEmpty()) col = 0x99FFFFFF;
                }
                g.txt(g.fit(txt, .016f, b.rect.width() - .05f, g.regular), ix + .016f, iy, .016f, col, Paint.Align.LEFT, g.regular);
            }
        }
        if (home) { drawStartPage(w, P, a); return; }
        g.fill.setColor(w.app == PLAYER ? 0xFF000000 : 0xFFFFFFFF);
        g.c.drawRoundRect(a, .014f, .014f, g.fill);
        Bitmap wb = host.webBitmap(pid);
        if (wb != null) {
            g.c.save();
            g.clipRound(a, .014f);
            g.c.drawBitmap(wb, null, a, g.bmp);
            g.c.restore();
        } else g.txt(w.app == YOUTUBE ? "Abrindo o YouTube…" : "Carregando…", a.centerX(), a.centerY(), .022f, 0xFF6B7280, Paint.Align.CENTER, g.medium);
    }

    /** Página inicial do Navegador: favoritos e sites recentes. */
    private void drawStartPage(Win w, Parts P, RectF a) {
        g.fill.setColor(0x26000000);
        g.c.drawRoundRect(a, .014f, .014f, g.fill);
        g.txt("Favoritos", a.left + .02f, a.top + .026f, .018f, 0xE6FFFFFF, Paint.Align.LEFT, g.medium);
        List<String[]> favs = favorites();
        boolean hisTitle = false;
        for (Btn b : buttons(w, P)) {
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            if (b.id.startsWith("fv")) {
                int i = Integer.parseInt(b.id.substring(2));
                if (i >= favs.size()) continue;
                String[] f = favs.get(i);
                g.glass(b.rect, .016f, h, false);
                float r = b.rect.height() * .26f, cy = b.rect.top + b.rect.height() * .38f;
                g.circle(b.rect.centerX(), cy, r, siteColor(f[1]));
                String letter = f[0].isEmpty() ? "?" : f[0].substring(0, 1).toUpperCase(new Locale("pt", "BR"));
                g.txt(letter, b.rect.centerX(), cy, .02f, Color.WHITE, Paint.Align.CENTER, g.medium);
                g.txt(g.fit(f[0], .014f, b.rect.width() - .01f, g.regular), b.rect.centerX(), b.rect.bottom - .013f, .014f, Color.WHITE, Paint.Align.CENTER, g.regular);
            } else if (b.id.startsWith("hs")) {
                List<String[]> his = host.history();
                int i = Integer.parseInt(b.id.substring(2));
                if (i >= his.size()) continue;
                if (!hisTitle) { hisTitle = true; g.txt("Recentes", a.left + .02f, b.rect.top - .022f, .018f, 0xE6FFFFFF, Paint.Align.LEFT, g.medium); }
                g.fill.setColor(h ? 0x40FFFFFF : 0x14FFFFFF);
                g.c.drawRoundRect(b.rect, .01f, .01f, g.fill);
                String[] e = his.get(i);
                g.txt(g.fit(e[0].isEmpty() ? e[1] : e[0], .015f, b.rect.width() * .55f, g.medium), b.rect.left + .014f, b.rect.centerY(), .015f, Color.WHITE, Paint.Align.LEFT, g.medium);
                String host2 = e[1].replaceFirst("^https?://", "").replaceFirst("/.*$", "");
                g.txt(g.fit(host2, .013f, b.rect.width() * .35f, g.regular), b.rect.right - .014f, b.rect.centerY(), .013f, 0x99FFFFFF, Paint.Align.RIGHT, g.regular);
            }
        }
    }

    private static int siteColor(String url) {
        for (int i = 0; i < SITES.length; i++) {
            String hostPart = SITES[i][1].replaceFirst("^https?://", "").replaceFirst("^(www\\.|m\\.|pt\\.m\\.)", "");
            if (url != null && url.contains(hostPart.replaceFirst("/.*$", ""))) return SITE_COL[i];
        }
        int hsh = url == null ? 0 : url.hashCode();
        float[] hsv = {Math.abs(hsh % 360), .6f, .75f};
        return Color.HSVToColor(hsv);
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
            int i = Integer.parseInt(b.id.substring(1));
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            g.glass(b.rect, .022f, h, false);
            float cx = b.rect.centerX(), cy = b.rect.centerY();
            float r = Math.min(.024f, b.rect.height() * .22f);
            g.circle(cx, cy - r * .8f, r * (h ? 1.1f : 1f), SITE_COL[i]);
            g.txt(SITES[i][0].substring(0, 1), cx, cy - r * .8f, .022f, Color.WHITE, Paint.Align.CENTER, g.medium);
            g.txt(SITES[i][0], cx, cy + r * 1.1f, .016f, Color.WHITE, Paint.Align.CENTER, g.medium);
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
            {"Analógico dir.", "Rolar a página / trocar foto"}, {"R3", "Comando de voz"}, {"Direcional", "Mover a mira"}};

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

    private void drawMedia(Win w, Parts P) {
        RectF C = P.content;
        for (Btn b : buttons(w, P)) {
            boolean h = hov.contains("btn:" + w.id + ":" + b.id);
            if (b.id.startsWith("mk")) {
                boolean on = (b.id.charAt(2) - '0') == w.mediaKind;
                g.fill.setColor(on ? 0xF2FFFFFF : (h ? 0x40FFFFFF : 0x1FFFFFFF));
                g.c.drawRoundRect(b.rect, b.rect.height() / 2, b.rect.height() / 2, g.fill);
                g.txt(b.id.equals("mk0") ? "Vídeos" : "Músicas", b.rect.centerX(), b.rect.centerY(), .016f, on ? 0xFF15171E : Color.WHITE, Paint.Align.CENTER, g.medium);
            } else if (b.id.equals("perm")) {
                g.glass(b.rect, b.rect.height() / 2, h, false);
                g.txt("Permitir acesso a vídeos e músicas", b.rect.centerX(), b.rect.centerY(), .015f, Color.WHITE, Paint.Align.CENTER, g.medium);
            } else if (b.id.startsWith("mi")) {
                List<Host.MediaItem> items = host.mediaList(w.mediaKind);
                int i = Integer.parseInt(b.id.substring(2));
                if (i >= items.size()) continue;
                Host.MediaItem it = items.get(i);
                g.fill.setColor(h ? 0x40FFFFFF : 0x14FFFFFF);
                g.c.drawRoundRect(b.rect, .01f, .01f, g.fill);
                float th = b.rect.height() - .008f;
                RectF tr = new RectF(b.rect.left + .006f, b.rect.top + .004f, b.rect.left + .006f + th * (it.kind == 0 ? 1.6f : 1f), b.rect.bottom - .004f);
                if (it.thumb != null) {
                    g.c.save(); g.clipRound(tr, .006f); g.cover(it.thumb, tr, g.bmp); g.c.restore();
                } else {
                    g.fill.setColor(COLS[MIDIA][1]);
                    g.c.drawRoundRect(tr, .006f, .006f, g.fill);
                    glyph(it.kind == 0 ? CINEMA : MUSICA, tr.centerX(), tr.centerY(), th * .32f);
                }
                float tx = tr.right + .014f;
                g.txt(g.fit(it.title, .016f, b.rect.right - tx - .1f, g.medium), tx, b.rect.centerY() - (it.artist.isEmpty() ? 0 : .009f), .016f, Color.WHITE, Paint.Align.LEFT, g.medium);
                if (!it.artist.isEmpty())
                    g.txt(g.fit(it.artist, .013f, b.rect.right - tx - .1f, g.regular), tx, b.rect.centerY() + .011f, .013f, 0x99FFFFFF, Paint.Align.LEFT, g.regular);
                long sec = it.durationMs / 1000;
                g.txt(String.format(Locale.US, "%d:%02d", sec / 60, sec % 60), b.rect.right - .012f, b.rect.centerY(), .014f, 0x99FFFFFF, Paint.Align.RIGHT, g.regular);
            } else arrowBtn(b, h, b.id.equals("pprev"));
        }
        if (host.mediaAllowed() && host.mediaList(w.mediaKind).isEmpty())
            g.txt(w.mediaKind == 0 ? "Nenhum vídeo encontrado no celular" : "Nenhuma música encontrada no celular",
                    C.centerX(), C.centerY(), .018f, 0x99FFFFFF, Paint.Align.CENTER, g.regular);
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

    private Bitmap logo;
    private Bitmap logo() { if (logo == null) logo = host.logo(); return logo; }
    private long wokeUntil;

    /** Ouviu "ei visão": som de chamada e o rosto do Visão aparece. */
    void assistantWoke(long now) { synth.open(); wokeUntil = now + 6000; }

    void appIcon(int app, float x, float y, float r, boolean hl) {
        Canvas c = g.c;
        if (hl) g.circle(x, y + r * .12f, r * 1.32f, 0x26FFFFFF);
        RectF rr = new RectF(x - r, y - r, x + r, y + r);
        g.fill.setColor(0x33000000);
        c.drawRoundRect(new RectF(x - r, y - r + r * .16f, x + r, y + r + r * .16f), r * .5f, r * .5f, g.fill);
        if (app == VOZ && logo() != null) {          // o Visão usa o próprio rosto como ícone
            Path clip = new Path();
            clip.addRoundRect(rr, r * .5f, r * .5f, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.drawBitmap(logo, null, rr, g.bmp);
            c.restore();
            return;
        }
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
            case MIDIA:
                c.drawRoundRect(new RectF(-.62f, -.46f, .62f, .46f), .14f, .14f, s);
                p.moveTo(-.14f, -.22f); p.lineTo(.24f, 0); p.lineTo(-.14f, .22f); p.close();
                c.drawPath(p, fl);
                break;
            case PLAYER:
                c.drawCircle(0, 0, .6f, s);
                p.moveTo(-.16f, -.26f); p.lineTo(.3f, 0); p.lineTo(-.16f, .26f); p.close();
                c.drawPath(p, fl);
                break;
            case NAVE:
                p.moveTo(0, -.6f); p.lineTo(.36f, .42f); p.lineTo(0, .22f); p.lineTo(-.36f, .42f); p.close();
                c.drawPath(p, fl);
                break;
            case VOZ:
                c.drawRoundRect(new RectF(-.18f, -.6f, .18f, .14f), .18f, .18f, fl);
                c.drawArc(new RectF(-.38f, -.3f, .38f, .34f), 0, 180, false, s);
                c.drawLine(0, .34f, 0, .56f, s);
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

        long nowQ = now;
        if (qrVisible(nowQ) && qrWanted(nowQ)) {
            float qh = qss / 2, k = qh * .45f;
            g.stroke.setColor(0xE69FE8B0); g.stroke.setStrokeWidth(.004f);
            float[][] cs = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
            for (float[] cc : cs) {
                float x = qsx + cc[0] * qh, y = qsy + cc[1] * qh;
                c.drawLine(x, y, x - cc[0] * k, y, g.stroke);
                c.drawLine(x, y, x, y - cc[1] * k, g.stroke);
            }
        }

        // ---- Visão: ouvindo ou falando ----
        boolean lis = host.isListening();
        String cap = host.assistantCaption();
        if (lis || cap != null || now < wokeUntil) {
            if (lis || cap != null) wokeUntil = Math.min(wokeUntil, now + 600);
            String vt = host.voiceText();
            String label = lis ? (vt == null || vt.isEmpty() ? (now < wokeUntil + 4000 ? "Pode falar…" : "Ouvindo… fale um comando") : vt)
                    : cap != null ? cap : "Pode falar…";
            float ts0 = .019f, maxW = Math.min(eyeHalfW * 1.5f, .62f);
            List<String> ls = wrap(label, ts0, maxW - .12f);
            if (ls.size() > 3) { ls = new ArrayList<>(ls.subList(0, 3)); ls.set(2, g.fit(ls.get(2) + "…", ts0, maxW - .12f, g.medium)); }
            float lh = ts0 * 1.45f * cfg.textScale;
            float ph = Math.max(.075f, ls.size() * lh + .04f);
            float tw = maxW;
            tw = Math.min(maxW, Math.max(.3f, widest(ls, ts0) + .13f));
            RectF vb = new RectF(-tw / 2, -.38f, tw / 2, -.38f + ph);
            g.softShadow(vb, .035f);
            g.fill.setColor(0xEB1A1416);
            c.drawRoundRect(vb, .035f, .035f, g.fill);
            g.stroke.setStrokeWidth(.002f); g.stroke.setColor(lis ? 0x99FF5A4A : 0x66FFB547);
            c.drawRoundRect(vb, .035f, .035f, g.stroke);
            float cx = vb.left + .045f, cy = vb.top + Math.min(ph / 2, .0375f), rr0 = .026f;
            float pulse = (float) (.5 + .5 * Math.sin(now / (lis ? 140.0 : 220.0)));
            g.circle(cx, cy, rr0 + .006f + .006f * pulse, Gfx.a(lis ? 0xFFFF5A4A : 0xFFFFB547, .35f + .3f * pulse));
            if (logo() != null) {
                Path clip = new Path();
                clip.addCircle(cx, cy, rr0, Path.Direction.CW);
                c.save(); c.clipPath(clip);
                c.drawBitmap(logo, null, new RectF(cx - rr0, cy - rr0, cx + rr0, cy + rr0), g.bmp);
                c.restore();
            } else g.circle(cx, cy, rr0, 0xFFDD4F3F);
            float ty = vb.top + .02f + lh / 2;
            for (String l : ls) { g.txt(l, vb.left + .09f, ty, ts0, Color.WHITE, Paint.Align.LEFT, g.medium); ty += lh; }
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
