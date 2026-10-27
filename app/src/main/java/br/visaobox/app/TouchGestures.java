package br.visaobox.app;

/**
 * Gestos do touchpad do celular-controle, como num notebook:
 *  - 1 dedo arrastando ........ move a mira (com aceleração: devagar = preciso, rápido = longe)
 *  - toque ..................... clicar (A)
 *  - 2 dedos arrastando ....... rolar
 *  - toque com 2 dedos ........ voltar (B)
 *  - toque duplo e arrastar ... segura o clique (arrasta janela pela barra, barras de rolagem)
 *  - segurar parado 0,5 s ..... pega a janela que está na mira e arrasta junto com o dedo
 * Sem nada de Android aqui (dá para testar no computador).
 */
final class TouchGestures {

    interface Out {
        void move(float dx, float dy, float dt);    // radianos de mira
        void scroll(float dx, float dy, float dt);  // pixels da página
        void hold(int bit, boolean down);           // botão segurado
        void tap(int bit);                          // botão tocado rápido
        void buzz(int ms);
    }

    static final long TAP_MS = 250, DOUBLE_MS = 300, HOLD_MS = 500;

    private final Out out;
    float density = 2.75f;      // px por dp
    float sens = 1f;
    boolean aim = true;         // 1 dedo move a mira (touchpad); senão 1 dedo rola

    private final int[] ids = new int[10];
    private final float[] lx = new float[10], ly = new float[10];
    private int n, maxN;
    private long startT, lastT, lastTapT = -100000;
    private float sx, sy, lastTapX, lastTapY;
    private boolean moved, dragging, grabbing;

    TouchGestures(Out out) { this.out = out; }

    private int idx(int id) { for (int i = 0; i < n; i++) if (ids[i] == id) return i; return -1; }

    int fingers() { return n; }
    boolean dragging() { return dragging; }
    boolean grabbing() { return grabbing; }

    void down(int id, float x, float y, long now) {
        if (n >= ids.length || idx(id) >= 0) return;
        if (n == 0) {
            startT = now; lastT = now; sx = x; sy = y; moved = false; maxN = 0;
            if (aim && now - lastTapT < DOUBLE_MS && dist(x, y, lastTapX, lastTapY) < 45) {   // toque duplo: segura o clique
                dragging = true; out.hold(RemoteProto.B_TRIGGER, true); out.buzz(15);
            }
        }
        ids[n] = id; lx[n] = x; ly[n] = y; n++;
        maxN = Math.max(maxN, n);
    }

    /** Novas posições dos dedos (os que não estão no touchpad são ignorados). */
    void move(int[] pid, float[] px, float[] py, int count, long now) {
        float dx = 0, dy = 0; int k = 0;
        for (int j = 0; j < count; j++) {
            int i = idx(pid[j]);
            if (i < 0) continue;
            dx += px[j] - lx[i]; dy += py[j] - ly[i]; k++;
            lx[i] = px[j]; ly[i] = py[j];
            if (dist(px[j], py[j], sx, sy) > 10) moved = true;
        }
        if (k == 0 || (dx == 0 && dy == 0)) return;
        dx /= k; dy /= k;
        float dt = Math.max(.004f, (now - lastT) / 1000f);
        lastT = now;
        float ddx = dx / density, ddy = dy / density;            // em dp
        if (aim && n == 1) {
            float v = (float) Math.hypot(ddx, ddy) / dt;          // dp por segundo
            float gain = .0024f * sens * (.45f + Math.min(2.2f, v / 650f));
            out.move(ddx * gain, ddy * gain, dt);
        } else {
            out.scroll(-ddx * 4.8f, -ddy * 4.8f, dt);
        }
    }

    void up(int id, long now, boolean cancel) {
        int i = idx(id);
        if (i < 0) return;
        for (int j = i; j < n - 1; j++) { ids[j] = ids[j + 1]; lx[j] = lx[j + 1]; ly[j] = ly[j + 1]; }
        n--;
        if (n > 0 && !cancel) return;
        n = 0;
        if (dragging) { dragging = false; out.hold(RemoteProto.B_TRIGGER, false); return; }
        if (grabbing) { grabbing = false; out.hold(RemoteProto.B_GRIP, false); return; }
        if (cancel || moved || now - startT > TAP_MS) return;
        if (maxN == 1) { out.tap(RemoteProto.B_TRIGGER); out.buzz(20); lastTapT = now; lastTapX = sx; lastTapY = sy; }
        else if (maxN == 2) { out.tap(RemoteProto.B_BACK); out.buzz(30); lastTapT = -100000; }
    }

    /** Chamado sempre (a cada ~50 ms) para perceber o "segurar parado". */
    void tick(long now) {
        if (aim && n == 1 && maxN == 1 && !moved && !dragging && !grabbing && now - startT > HOLD_MS) {
            grabbing = true; out.hold(RemoteProto.B_GRIP, true); out.buzz(40);
        }
    }

    private float dist(float x0, float y0, float x1, float y1) { return (float) Math.hypot(x0 - x1, y0 - y1) / density; }
}
