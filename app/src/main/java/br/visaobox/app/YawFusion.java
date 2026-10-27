package br.visaobox.app;

/**
 * Bússola contra o desvio: o giroscópio (vetor de rotação "de jogo") é suave, mas a direção
 * vai escorregando com o tempo. A bússola não escorrega, mas treme e sofre com metais.
 * Aqui a direção do giroscópio é puxada BEM devagar (~6 s) para a da bússola, e saltos grandes
 * da bússola (um metal perto) são ignorados por alguns segundos.
 */
final class YawFusion {
    volatile boolean enabled = true;
    private float off; private boolean init; private long lastNs, badSinceNs;
    private float[] game;
    /** Fica true uma vez quando a bússola entra (para recentralizar sem pulo). */
    volatile boolean justInit;

    void game(float[] q) { game = q; }

    void compass(float[] qMag, long tNs) {
        float[] g = game;
        if (g == null) return;
        float d = RemoteProto.yawBetween(qMag, g);
        if (!init) { off = d; init = true; lastNs = tNs; justInit = true; return; }
        float dt = Math.max(0, Math.min(.2f, (tNs - lastNs) / 1e9f));
        lastNs = tNs;
        float e = RemoteProto.wrap(d - off);
        if (Math.abs(e) > .35f) {                          // salto: provavelmente um metal perto
            if (badSinceNs == 0) badSinceNs = tNs;
            if (tNs - badSinceNs < 3_000_000_000L) return;
        } else badSinceNs = 0;
        off = RemoteProto.wrap(off + e * Math.min(1f, dt / 6f));
    }

    /** Rotação do giroscópio já corrigida pela bússola (ou a original, se desligada). */
    float[] corrected(float[] q) {
        if (!enabled || !init) return q;
        return RemoteProto.mul(RemoteProto.rotZ(off), q);
    }

    boolean active() { return enabled && init; }
}
