package br.visaobox.app;

import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.os.SystemClock;
import android.view.Surface;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * App "Computador": recebe a tela do Mac/PC (vídeo H.264) e decodifica no chip de vídeo do celular,
 * direto numa textura do OpenGL. O celular continua montando o 3D (janelas, cabeça, câmera);
 * o vídeo só preenche a janela "Computador".
 *
 * Protocolo (veja PcProto/visaobox_pc.py):
 *   descoberta UDP 47820:  celular → "VBPC?"   PC → "VBPC!" + nome
 *   vídeo TCP 47821:       celular → "VBPC1" + largura máx. (int)
 *                          PC → [tipo 1 byte][tamanho 4 bytes][dados]   1 = info "largura altura nome", 2 = quadro H.264
 */
final class PcLink {
    static final int DISCO_PORT = 47820, TCP_PORT = 47821;
    static final int T_INFO = 1, T_FRAME = 2;

    private final SharedPreferences prefs;
    volatile String status = "Procurando o computador…";
    volatile String pcName = "";
    volatile boolean streaming;
    volatile int vw = 16, vh = 9;
    volatile int fps;

    // GL (só na thread da tela)
    int tex;
    private SurfaceTexture st;
    private volatile Surface surface;
    final float[] mat = new float[16];
    private volatile boolean frameNew;
    volatile boolean hasFrame;

    private volatile boolean wanted;
    private Thread thread;
    private volatile Socket sock;
    private volatile MediaCodec codec;
    private int frames; private long fpsT;

    PcLink(SharedPreferences prefs) { this.prefs = prefs; Matrix.setIdentityM(mat, 0); }

    /* ================= GL ================= */

    /** Cria a textura de vídeo (chamar na thread do GL, depois de criar o contexto). */
    void glInit() {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        tex = t[0];
        SurfaceTexture old = st;
        st = new SurfaceTexture(tex);
        st.setOnFrameAvailableListener(s -> frameNew = true);
        surface = new Surface(st);
        hasFrame = false;
        if (old != null) { try { old.release(); } catch (Throwable ignored) { } }
        Socket s = sock;                                   // contexto novo: reconecta para o decodificador usar a superfície nova
        if (s != null) try { s.close(); } catch (Throwable ignored) { }
    }

    /** Pega o quadro novo, se houver (thread do GL). */
    void glUpdate() {
        if (!frameNew || st == null) return;
        frameNew = false;
        try { st.updateTexImage(); st.getTransformMatrix(mat); hasFrame = true; } catch (Throwable ignored) { }
    }

    /* ================= ligar / desligar ================= */

    synchronized void open() {
        if (wanted) return;
        wanted = true;
        thread = new Thread(this::loop, "pc-video");
        thread.setDaemon(true);
        thread.start();
    }

    synchronized void close() {
        wanted = false;
        Socket s = sock;
        if (s != null) try { s.close(); } catch (Throwable ignored) { }
        streaming = false;
        status = "Desligado";
    }

    boolean isOpen() { return wanted; }

    /* ================= rede ================= */

    private void loop() {
        while (wanted) {
            try {
                if (surface == null) { status = "Preparando a tela…"; Thread.sleep(200); continue; }
                status = "Procurando o computador… abra o Visão Box PC no Mac";
                InetAddress pc = discover();
                if (pc == null) {
                    String ip = prefs.getString("pcIp", "");
                    if (!ip.isEmpty()) pc = InetAddress.getByName(ip);
                }
                if (pc == null) { Thread.sleep(1200); continue; }
                stream(pc);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                streaming = false;
                if (wanted) status = "Conexão caiu (" + t.getClass().getSimpleName() + "). Tentando de novo…";
                try { Thread.sleep(1500); } catch (InterruptedException e) { break; }
            } finally {
                releaseCodec();
            }
        }
        streaming = false;
    }

    /** Pergunta na rede "tem um Visão Box PC aí?" (broadcast) e devolve quem responder. */
    private InetAddress discover() {
        try (DatagramSocket ds = new DatagramSocket()) {
            ds.setBroadcast(true);
            ds.setSoTimeout(400);
            byte[] q = "VBPC?".getBytes(StandardCharsets.US_ASCII);
            for (InetAddress a : broadcasts()) {
                try { ds.send(new DatagramPacket(q, q.length, a, DISCO_PORT)); } catch (Throwable ignored) { }
            }
            byte[] buf = new byte[256];
            long end = SystemClock.uptimeMillis() + 900;
            while (SystemClock.uptimeMillis() < end) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try { ds.receive(p); } catch (SocketTimeoutException e) { continue; }
                String r = new String(buf, 0, p.getLength(), StandardCharsets.UTF_8);
                if (r.startsWith("VBPC!")) {
                    pcName = r.substring(5).trim();
                    prefs.edit().putString("pcIp", p.getAddress().getHostAddress()).apply();
                    return p.getAddress();
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static List<InetAddress> broadcasts() {
        ArrayList<InetAddress> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses())
                    if (ia.getAddress() instanceof Inet4Address && ia.getBroadcast() != null) out.add(ia.getBroadcast());
            }
        } catch (Throwable ignored) { }
        try { out.add(InetAddress.getByName("255.255.255.255")); } catch (Throwable ignored) { }
        return out;
    }

    private void stream(InetAddress pc) throws Exception {
        status = "Conectando a " + (pcName.isEmpty() ? pc.getHostAddress() : pcName) + "…";
        Socket s = new Socket();
        sock = s;
        s.setTcpNoDelay(true);
        s.setReceiveBufferSize(1 << 20);
        s.connect(new InetSocketAddress(pc, TCP_PORT), 3000);
        s.setSoTimeout(5000);
        OutputStream os = s.getOutputStream();
        ByteBuffer hello = ByteBuffer.allocate(9);
        hello.put("VBPC1".getBytes(StandardCharsets.US_ASCII)).putInt(1920);
        os.write(hello.array());
        os.flush();
        DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(s.getInputStream(), 1 << 16));
        byte[] buf = new byte[1 << 20];
        long pts = 0;
        fpsT = SystemClock.uptimeMillis(); frames = 0;
        while (wanted) {
            int type = in.readUnsignedByte();
            int len = in.readInt();
            if (len < 0 || len > 16 << 20) throw new java.io.IOException("pacote inválido");
            if (len > buf.length) buf = new byte[len];
            in.readFully(buf, 0, len);
            if (type == T_INFO) {
                String[] p = new String(buf, 0, len, StandardCharsets.UTF_8).split(" ", 3);
                vw = Math.max(1, Integer.parseInt(p[0])); vh = Math.max(1, Integer.parseInt(p[1]));
                if (p.length > 2) pcName = p[2];
                status = "Recebendo a tela de " + pcName + "…";
            } else if (type == T_FRAME) {
                if (codec == null) {
                    byte[][] ps = H264.paramSets(buf, len);
                    if (ps == null) continue;                   // espera um quadro-chave com SPS/PPS
                    startCodec(ps[0], ps[1]);
                }
                feed(buf, len, pts);
                pts += 16_666;
                frames++;
                long now = SystemClock.uptimeMillis();
                if (now - fpsT >= 1000) { fps = frames; frames = 0; fpsT = now; }
                streaming = true;
                status = "Conectado a " + pcName;
            }
        }
    }

    /* ================= decodificador ================= */

    private void startCodec(byte[] sps, byte[] pps) throws Exception {
        MediaFormat f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, vw, vh);
        f.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
        f.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
        if (Build.VERSION.SDK_INT >= 30) f.setInteger("low-latency", 1);
        f.setInteger("priority", 0);                          // tempo real
        MediaCodec c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        c.configure(f, surface, null, 0);
        c.start();
        codec = c;
        Thread out = new Thread(() -> drain(c), "pc-saida");
        out.setDaemon(true);
        out.start();
    }

    private void feed(byte[] data, int len, long pts) throws Exception {
        MediaCodec c = codec;
        if (c == null) return;
        int i = c.dequeueInputBuffer(100_000);
        if (i < 0) return;                                    // decodificador cheio: pula (o próximo quadro-chave arruma)
        ByteBuffer b = c.getInputBuffer(i);
        if (b == null) return;
        b.clear();
        b.put(data, 0, len);
        c.queueInputBuffer(i, 0, len, pts, 0);
    }

    /** Mostra sempre o quadro mais novo (sem fila: menos atraso). */
    private void drain(MediaCodec c) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (codec == c) {
            try {
                int o = c.dequeueOutputBuffer(info, 20_000);
                if (o >= 0) c.releaseOutputBuffer(o, true);
                else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat f = c.getOutputFormat();
                    if (f.containsKey("crop-right") && f.containsKey("crop-left")) {
                        vw = f.getInteger("crop-right") - f.getInteger("crop-left") + 1;
                        vh = f.getInteger("crop-bottom") - f.getInteger("crop-top") + 1;
                    }
                }
            } catch (Throwable t) { break; }
        }
    }

    private void releaseCodec() {
        MediaCodec c = codec;
        codec = null;
        if (c != null) {
            try { c.stop(); } catch (Throwable ignored) { }
            try { c.release(); } catch (Throwable ignored) { }
        }
        Socket s = sock;
        sock = null;
        if (s != null) try { s.close(); } catch (Throwable ignored) { }
        streaming = false;
        hasFrame = false;
    }

    /** Partes do H.264 (sem Android: dá para testar no computador). */
    static final class H264 {
        /** Procura SPS (7) e PPS (8) no quadro e devolve {sps, pps} com o código de início, ou null. */
        static byte[][] paramSets(byte[] d, int len) {
            byte[] sps = null, pps = null;
            int i = 0;
            while (true) {
                int s = next(d, i, len);
                if (s < 0) break;
                int start = s + (s + 2 < len && d[s + 2] == 1 ? 3 : 4);
                int e = next(d, start, len);
                int end = e < 0 ? len : e;
                int type = d[start] & 0x1F;
                if (type == 7 || type == 8) {
                    byte[] nal = new byte[4 + end - start];
                    nal[3] = 1;
                    System.arraycopy(d, start, nal, 4, end - start);
                    if (type == 7) sps = nal; else pps = nal;
                }
                if (e < 0) break;
                i = e;
            }
            return sps != null && pps != null ? new byte[][]{sps, pps} : null;
        }

        /** Posição do próximo código de início (00 00 01 ou 00 00 00 01) a partir de "from". */
        static int next(byte[] d, int from, int len) {
            for (int i = from; i + 2 < len; i++) {
                if (d[i] == 0 && d[i + 1] == 0) {
                    if (d[i + 2] == 1) return i > 0 && d[i - 1] == 0 && i - 1 >= from ? i - 1 : i;
                    if (i + 3 < len && d[i + 2] == 0 && d[i + 3] == 1) return i;
                }
            }
            return -1;
        }
    }
}
