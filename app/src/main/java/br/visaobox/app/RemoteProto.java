package br.visaobox.app;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Protocolo entre o celular-controle e o celular do óculos (UDP, porta 47811).
 *
 *  HELLO   controle → óculos (broadcast)   procura o óculos com o código de 4 dígitos
 *  STATE   controle → óculos (~90 por s)   rotação, botões, rolagem acumulada
 *  STATUS  óculos → controle (2 por s)     "estou aqui" + eco do relógio (para medir o atraso)
 *  HAPTIC  óculos → controle               vibrar por N ms
 *
 * Os botões vão como estado (segurado ou não) em todo pacote, e a rolagem vai como total
 * acumulado: se um pacote se perde, o próximo corrige tudo sozinho.
 */
final class RemoteProto {
    static final int PORT = 47811;
    static final byte HELLO = 1, STATE = 2, STATUS = 3, HAPTIC = 4;
    static final byte VERSION = 2;

    // botões
    static final int B_TRIGGER = 1, B_BACK = 2, B_MENU = 4, B_HOME = 8, B_GRIP = 16, B_KEYB = 32, B_VOICE = 64;
    static final int[] BITS = {B_TRIGGER, B_BACK, B_MENU, B_HOME, B_GRIP, B_KEYB, B_VOICE};
    // flags
    static final int F_LEFT = 1;
    // modo do controle (flags, bits 1-2)
    static final int M_MOUSE = 0, M_TOUCHPAD = 1, M_RAY = 2;
    static int mode(int flags) { return (flags >> 1) & 3; }
    static int withMode(int flags, int mode) { return (flags & ~6) | ((mode & 3) << 1); }
    static final int STATE_LEN = 56;

    static final class State {
        int code, seq, time, buttons, flags;
        float qx, qy, qz, qw = 1, scrollX, scrollY;
        float mouseX, mouseY;      // movimento total da mira (radianos), no mouse no ar e no touchpad
    }

    private static ByteBuffer head(int size, byte type) {
        ByteBuffer b = ByteBuffer.allocate(size);
        b.put((byte) 'V').put((byte) 'B').put(type).put(VERSION);
        return b;
    }

    /** Tipo do pacote (ou -1 se não for nosso). */
    static int type(byte[] d, int len) {
        if (len < 4 || d[0] != 'V' || d[1] != 'B' || d[3] != VERSION) return -1;
        return d[2];
    }

    static byte[] hello(int code, String name) {
        byte[] n = utf(name, 40);
        ByteBuffer b = head(4 + 4 + 1 + n.length, HELLO);
        b.putInt(code).put((byte) n.length).put(n);
        return b.array();
    }

    static int helloCode(byte[] d, int len) { return len >= 8 ? ByteBuffer.wrap(d, 4, 4).getInt() : -1; }

    static byte[] state(State s) {
        ByteBuffer b = head(STATE_LEN, STATE);
        b.putInt(s.code).putInt(s.seq).putInt(s.time)
                .putFloat(s.qx).putFloat(s.qy).putFloat(s.qz).putFloat(s.qw)
                .putInt(s.buttons).putFloat(s.scrollX).putFloat(s.scrollY).putInt(s.flags)
                .putFloat(s.mouseX).putFloat(s.mouseY);
        return b.array();
    }

    static State readState(byte[] d, int len) {
        if (len < STATE_LEN) return null;
        ByteBuffer b = ByteBuffer.wrap(d, 4, STATE_LEN - 4);
        State s = new State();
        s.code = b.getInt(); s.seq = b.getInt(); s.time = b.getInt();
        s.qx = b.getFloat(); s.qy = b.getFloat(); s.qz = b.getFloat(); s.qw = b.getFloat();
        s.buttons = b.getInt(); s.scrollX = b.getFloat(); s.scrollY = b.getFloat(); s.flags = b.getInt();
        s.mouseX = b.getFloat(); s.mouseY = b.getFloat();
        return s;
    }

    static byte[] status(int echoTime, String name) {
        byte[] n = utf(name, 40);
        ByteBuffer b = head(4 + 4 + 1 + n.length, STATUS);
        b.putInt(echoTime).put((byte) n.length).put(n);
        return b.array();
    }

    static int statusEcho(byte[] d, int len) { return len >= 8 ? ByteBuffer.wrap(d, 4, 4).getInt() : 0; }

    static String statusName(byte[] d, int len) {
        if (len < 9) return "";
        int n = Math.min(d[8] & 0xFF, len - 9);
        return new String(d, 9, n, StandardCharsets.UTF_8);
    }

    static byte[] haptic(int ms, int amp) {
        ByteBuffer b = head(8, HAPTIC);
        b.putShort((short) Math.max(1, Math.min(500, ms))).put((byte) Math.max(1, Math.min(255, amp))).put((byte) 0);
        return b.array();
    }

    static int hapticMs(byte[] d, int len) { return len >= 6 ? ByteBuffer.wrap(d, 4, 2).getShort() : 0; }
    static int hapticAmp(byte[] d, int len) { return len >= 7 ? d[6] & 0xFF : 128; }

    private static byte[] utf(String s, int max) {
        byte[] n = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        if (n.length <= max) return n;
        byte[] c = new byte[max];
        System.arraycopy(n, 0, c, 0, max);
        return c;
    }

    /**
     * Direção para onde o topo do celular-controle aponta, a partir do vetor de rotação
     * (quaternion: aparelho → mundo, x leste, y norte, z para cima).
     * Devolve {guinada, inclinação} em radianos, no mesmo padrão do HeadTracker
     * (guinada positiva = para a direita, inclinação positiva = para cima).
     */
    static float[] yawPitch(float x, float y, float z, float w) {
        float dx = 2 * (x * y - z * w);
        float dy = 1 - 2 * (x * x + z * z);
        float dz = 2 * (y * z + x * w);
        float yaw = (float) Math.atan2(dx, dy);
        float pitch = (float) Math.asin(Math.max(-1f, Math.min(1f, dz)));
        return new float[]{yaw, pitch};
    }

    /* ================= quaternions {x, y, z, w} (aparelho → mundo) ================= */

    static float[] quat(float[] v) {
        float x = v[0], y = v[1], z = v[2];
        float w = v.length > 3 ? v[3] : (float) Math.sqrt(Math.max(0, 1 - x * x - y * y - z * z));
        return new float[]{x, y, z, w};
    }

    static float[] mul(float[] a, float[] b) {
        return new float[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    static float[] conj(float[] q) { return new float[]{-q[0], -q[1], -q[2], q[3]}; }

    static float[] norm(float[] q) {
        float n = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (n < 1e-9f) return new float[]{0, 0, 0, 1};
        return new float[]{q[0] / n, q[1] / n, q[2] / n, q[3] / n};
    }

    /** Gira o vetor v (do aparelho) para o mundo. */
    static float[] rotate(float[] q, float[] v) {
        float[] r = mul(mul(q, new float[]{v[0], v[1], v[2], 0}), conj(q));
        return new float[]{r[0], r[1], r[2]};
    }

    static float[] unrotate(float[] q, float[] v) { return rotate(conj(q), v); }

    /** Rotação em torno do eixo vertical (z) do mundo. */
    static float[] rotZ(float a) { return new float[]{0, 0, (float) Math.sin(a / 2), (float) Math.cos(a / 2)}; }

    /** Quanto "b" está girado em relação a "a" em torno do eixo vertical (b ≈ rotZ(δ)·a). */
    static float yawBetween(float[] b, float[] a) {
        float[] r = mul(b, conj(a));
        return wrap((float) (2 * Math.atan2(r[2], r[3])));
    }

    static float wrap(float a) {
        while (a > Math.PI) a -= (float) (2 * Math.PI);
        while (a < -Math.PI) a += (float) (2 * Math.PI);
        return a;
    }

    /**
     * Eixo de mira do aparelho, escolhido na hora de alinhar: o topo do celular (segurado deitado,
     * como um controle de TV) ou as costas dele (segurado em pé, como uma câmera) — o que estiver
     * mais na horizontal. O eixo é ajustado para apontar exatamente para a frente naquele momento,
     * assim a conta nunca fica instável (o problema de apontar o topo para cima).
     */
    static float[] aimAxis(float[] q) {
        float[] top = rotate(q, new float[]{0, 1, 0}), back = rotate(q, new float[]{0, 0, -1});
        float[] d = Math.abs(top[2]) <= Math.abs(back[2]) ? top : back;
        float h = (float) Math.hypot(d[0], d[1]);
        if (h < 1e-4f) return new float[]{0, 1, 0};
        return unrotate(q, new float[]{d[0] / h, d[1] / h, 0});
    }

    /** {guinada, inclinação} do eixo de mira (guinada + = direita, inclinação + = para cima). */
    static float[] aimYawPitch(float[] q, float[] axis) {
        float[] d = rotate(q, axis);
        return new float[]{(float) Math.atan2(d[0], d[1]), (float) Math.asin(Math.max(-1f, Math.min(1f, d[2])))};
    }

    /** Velocidade angular no mundo (rad/s) entre duas leituras. */
    static float[] angVel(float[] q0, float[] q1, float dt) {
        float[] r = mul(q1, conj(q0));
        if (r[3] < 0) r = new float[]{-r[0], -r[1], -r[2], -r[3]};
        float k = 2 / Math.max(1e-3f, dt);
        return new float[]{r[0] * k, r[1] * k, r[2] * k};
    }

    /** Prevê a rotação daqui a "tau" segundos girando com a velocidade "w". */
    static float[] predict(float[] q, float[] w, float tau) {
        float[] dq = norm(new float[]{w[0] * tau / 2, w[1] * tau / 2, w[2] * tau / 2, 1});
        return norm(mul(dq, q));
    }

    /* ================= mouse no ar ================= */

    /**
     * Giro da mão (rad/s) → velocidade da mira, com zona morta (tira o tremor) e aceleração
     * (devagar = preciso, rápido = atravessa a tela).
     */
    static float shape(float rate, float sens) {
        float a = Math.abs(rate) - .03f;
        if (a <= 0) return 0;
        float g = .65f + .55f * Math.min(1f, a / 2.5f);
        return Math.signum(rate) * a * g * sens;
    }

    /**
     * Giroscópio (no aparelho) → {direita, baixo} em rad/s, do mesmo jeito segurando deitado ou em pé:
     * virar para os lados = giro em torno da vertical do MUNDO; levantar = giro em torno do eixo x do aparelho.
     * rz = linha de baixo da matriz de rotação (para onde x, y, z do aparelho apontam na vertical).
     */
    static float[] mouseRates(float gx, float gy, float gz, float[] rz) {
        float yawRate = -(rz[0] * gx + rz[1] * gy + rz[2] * gz);
        return new float[]{yawRate, -gx};
    }

    /** Linha de baixo (eixo vertical) da matriz de rotação do quaternion. */
    static float[] upRow(float[] q) {
        float x = q[0], y = q[1], z = q[2], w = q[3];
        return new float[]{2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)};
    }
}
