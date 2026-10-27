package br.visaobox.app;

/**
 * Filtro "One Euro": com a mão parada filtra forte (tira o tremor); com a mão rápida filtra
 * quase nada (sem atraso). Usado na mira do celular-controle.
 */
final class OneEuro {
    private final float minCut, beta, dCut;
    private float x, dx; private boolean init;

    OneEuro(float minCutoffHz, float beta, float dCutoffHz) { minCut = minCutoffHz; this.beta = beta; dCut = dCutoffHz; }

    private static float alpha(float cut, float dt) {
        float tau = 1f / (float) (2 * Math.PI * cut);
        return 1f / (1f + tau / dt);
    }

    float filter(float v, float dt) {
        if (!init || dt <= 0) { x = v; dx = 0; init = true; return v; }
        float d = (v - x) / dt;
        dx += alpha(dCut, dt) * (d - dx);
        float cut = minCut + beta * Math.abs(dx);
        x += alpha(cut, dt) * (v - x);
        return x;
    }

    void reset() { init = false; }
}
