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
    static final byte VERSION = 1;

    // botões
    static final int B_TRIGGER = 1, B_BACK = 2, B_MENU = 4, B_HOME = 8, B_GRIP = 16, B_KEYB = 32, B_VOICE = 64;
    static final int[] BITS = {B_TRIGGER, B_BACK, B_MENU, B_HOME, B_GRIP, B_KEYB, B_VOICE};
    // flags
    static final int F_LEFT = 1;

    static final class State {
        int code, seq, time, buttons, flags;
        float qx, qy, qz, qw = 1, scrollX, scrollY;
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
        ByteBuffer b = head(48, STATE);
        b.putInt(s.code).putInt(s.seq).putInt(s.time)
                .putFloat(s.qx).putFloat(s.qy).putFloat(s.qz).putFloat(s.qw)
                .putInt(s.buttons).putFloat(s.scrollX).putFloat(s.scrollY).putInt(s.flags);
        return b.array();
    }

    static State readState(byte[] d, int len) {
        if (len < 48) return null;
        ByteBuffer b = ByteBuffer.wrap(d, 4, 44);
        State s = new State();
        s.code = b.getInt(); s.seq = b.getInt(); s.time = b.getInt();
        s.qx = b.getFloat(); s.qy = b.getFloat(); s.qz = b.getFloat(); s.qw = b.getFloat();
        s.buttons = b.getInt(); s.scrollX = b.getFloat(); s.scrollY = b.getFloat(); s.flags = b.getInt();
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
}
