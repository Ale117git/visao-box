package br.visaobox.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lado do ÓCULOS: recebe o celular-controle pela rede Wi-Fi (UDP).
 * Uma linha de execução escuta a porta; a interface lê o estado a cada quadro.
 */
final class RemoteLink {

    final int code;                                   // código de 4 dígitos para parear
    private final Context ctx;
    private volatile boolean run;
    private Thread thread;
    private DatagramSocket sock;
    private WifiManager.MulticastLock lock;
    private WifiManager.WifiLock wifiLock;              // tira a economia de energia do Wi-Fi (ela atrasa os pacotes)
    private final ExecutorService out = Executors.newSingleThreadExecutor();

    private volatile InetAddress peer;
    private volatile int peerPort;
    private volatile long lastRx = -100000;
    private int lastSeq;
    private int buttons;
    private int lastTime;

    // estado lido pela interface
    private float[] qNow = {0, 0, 0, 1}, lastQ;
    private final float[] omega = new float[3];           // velocidade de giro (rad/s), para prever
    private long rxAt;
    volatile boolean leftHand;
    volatile float scrollX, scrollY;
    volatile int mode;                                   // RemoteProto.M_MOUSE / M_TOUCHPAD / M_RAY
    volatile float mouseX, mouseY;                       // movimento total da mira (rad)
    /** Eventos de botão: {bit, 1 = apertou / 0 = soltou}. Um {0, 1} avisa "conectou". */
    final ConcurrentLinkedQueue<int[]> events = new ConcurrentLinkedQueue<>();

    RemoteLink(Context ctx, SharedPreferences prefs) {
        this.ctx = ctx.getApplicationContext();
        int c = prefs.getInt("remoteCode", 0);
        if (c < 1000 || c > 9999) {
            c = 1000 + new Random().nextInt(9000);
            prefs.edit().putInt("remoteCode", c).apply();
        }
        code = c;
    }

    boolean active(long now) { return now - lastRx < 1500; }

    synchronized void start() {
        if (run) return;
        run = true;
        try {
            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                lock = wm.createMulticastLock("visaobox-controle"); lock.setReferenceCounted(false); lock.acquire();
                wifiLock = wm.createWifiLock(Build.VERSION.SDK_INT >= 29 ? WIFI_MODE_FULL_LOW_LATENCY : WIFI_MODE_FULL_HIGH_PERF, "visaobox-controle");
                wifiLock.setReferenceCounted(false); wifiLock.acquire();
            }
        } catch (Throwable ignored) { }
        thread = new Thread(this::loop, "controle-rede");
        thread.setDaemon(true);
        thread.start();
    }

    synchronized void stop() {
        run = false;
        DatagramSocket s = sock;
        if (s != null) s.close();
        try { if (lock != null && lock.isHeld()) lock.release(); } catch (Throwable ignored) { }
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Throwable ignored) { }
        if (buttons != 0) releaseAll();
    }

    static final int WIFI_MODE_FULL_HIGH_PERF = 3, WIFI_MODE_FULL_LOW_LATENCY = 4;

    /**
     * Rotação do controle prevista para AGORA: a última recebida, girada um pouquinho para a frente
     * no tempo com a velocidade da mão (compensa o atraso da rede e os pacotes que chegam aos montes).
     */
    synchronized float[] predicted(long now) {
        float tau = Math.min(.045f, Math.max(0, now - rxAt + 10) / 1000f);
        return RemoteProto.predict(qNow, omega, tau);
    }

    private void releaseAll() {
        for (int bit : RemoteProto.BITS) if ((buttons & bit) != 0) events.add(new int[]{bit, 0});
        buttons = 0;
    }

    /** Pede ao controle para vibrar (por exemplo, ao passar o raio por um botão). */
    void haptic(int ms, int amp) {
        final InetAddress a = peer; final int p = peerPort; final DatagramSocket s = sock;
        if (a == null || s == null || !active(SystemClock.uptimeMillis())) return;
        final byte[] d = RemoteProto.haptic(ms, amp);
        out.execute(() -> { try { s.send(new DatagramPacket(d, d.length, a, p)); } catch (Throwable ignored) { } });
    }

    private void loop() {
        byte[] buf = new byte[256];
        long lastStatus = 0;
        while (run) {
            try {
                if (sock == null || sock.isClosed()) {
                    DatagramSocket s = new DatagramSocket(null);
                    s.setReuseAddress(true);
                    s.setBroadcast(true);
                    s.bind(new InetSocketAddress(RemoteProto.PORT));
                    s.setSoTimeout(250);
                    sock = s;
                }
                DatagramPacket pk = new DatagramPacket(buf, buf.length);
                try {
                    sock.receive(pk);
                    handle(pk);
                } catch (SocketTimeoutException ignored) { }
                long now = SystemClock.uptimeMillis();
                if (!active(now) && buttons != 0) releaseAll();     // controle sumiu: solta tudo
                if (peer != null && active(now) && now - lastStatus > 450) {
                    lastStatus = now;
                    byte[] d = RemoteProto.status(lastTime, "Visão Box");
                    sock.send(new DatagramPacket(d, d.length, peer, peerPort));
                }
            } catch (Throwable t) {
                if (!run) break;
                if (sock != null) sock.close();
                sock = null;
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            }
        }
        if (sock != null) sock.close();
        sock = null;
    }

    private void handle(DatagramPacket pk) throws Exception {
        byte[] d = pk.getData();
        int len = pk.getLength();
        int t = RemoteProto.type(d, len);
        long now = SystemClock.uptimeMillis();
        if (t == RemoteProto.HELLO) {
            if (RemoteProto.helloCode(d, len) != code) return;
            byte[] r = RemoteProto.status(0, "Visão Box");
            sock.send(new DatagramPacket(r, r.length, pk.getAddress(), pk.getPort()));
        } else if (t == RemoteProto.STATE) {
            RemoteProto.State s = RemoteProto.readState(d, len);
            if (s == null || s.code != code) return;
            boolean fresh = !active(now);
            boolean newPeer = !pk.getAddress().equals(peer) || pk.getPort() != peerPort;
            if (!fresh && !newPeer && s.seq - lastSeq <= 0 && lastSeq - s.seq < 100000) return;   // pacote atrasado
            if (fresh || newPeer) {
                peer = pk.getAddress(); peerPort = pk.getPort();
                if (fresh) { scrollX = s.scrollX; scrollY = s.scrollY; events.add(new int[]{0, 1}); }
            }
            lastSeq = s.seq;
            float[] q = RemoteProto.norm(new float[]{s.qx, s.qy, s.qz, s.qw});
            synchronized (this) {
                float dt = (s.time - lastTime) / 1000f;
                if (lastQ != null && !fresh && dt > .002f && dt < .2f) {
                    float[] w = RemoteProto.angVel(lastQ, q, dt);
                    for (int i = 0; i < 3; i++) omega[i] += (w[i] - omega[i]) * .5f;
                } else if (fresh || dt >= .2f) { omega[0] = omega[1] = omega[2] = 0; }
                lastQ = q; qNow = q; rxAt = now;
            }
            lastTime = s.time;
            leftHand = (s.flags & RemoteProto.F_LEFT) != 0;
            scrollX = s.scrollX; scrollY = s.scrollY;
            mode = RemoteProto.mode(s.flags);
            mouseX = s.mouseX; mouseY = s.mouseY;
            int ch = s.buttons ^ buttons;
            if (ch != 0) for (int bit : RemoteProto.BITS) if ((ch & bit) != 0) events.add(new int[]{bit, (s.buttons & bit) != 0 ? 1 : 0});
            buttons = s.buttons;
            lastRx = now;
        }
    }

    /** IP deste celular na rede (para digitar no controle, se a busca automática falhar). */
    static String localIp() {
        try {
            String best = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (!(a instanceof Inet4Address) || !a.isSiteLocalAddress()) continue;
                    String n = ni.getName();
                    if (n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("swlan")) return a.getHostAddress();
                    if (best == null) best = a.getHostAddress();
                }
            }
            return best;
        } catch (Throwable t) { return null; }
    }

    String info() {
        String ip = localIp();
        return "Código " + code + (ip != null ? " · IP " + ip : " · sem Wi-Fi");
    }

    static String deviceName() { return Build.MODEL == null ? "Celular" : Build.MODEL; }
}
