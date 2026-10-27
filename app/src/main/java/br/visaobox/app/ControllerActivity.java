package br.visaobox.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modo CONTROLE: este celular vira um controle apontador (como o do Meta Quest, sem a posição).
 * Envia a rotação do aparelho, os botões e a rolagem para o celular do óculos pela rede Wi-Fi.
 *
 * Segure o celular em pé, com a tela para cima e o topo apontando para a frente.
 */
public class ControllerActivity extends Activity implements SensorEventListener {

    private SharedPreferences prefs;
    private SensorManager sm;
    private Sensor rot, magRot;
    private final YawFusion fusion = new YawFusion();
    private boolean compass = true;
    private WifiManager.WifiLock wifiLock;
    private volatile long lastSend;
    private int ticks;
    private Vibrator vib;

    // rede
    private DatagramSocket sock;
    private HandlerThread netThread;
    private Handler net;
    private Thread rxThread;
    private volatile boolean running;
    private volatile InetAddress peer;
    private volatile int peerPort;
    private volatile long lastStatus = -100000;
    private volatile int rtt = -1;
    private volatile String peerName = "";
    private final long t0 = SystemClock.uptimeMillis();
    private long lastHello, lastTargets;
    private List<InetAddress> targets = new ArrayList<>();
    private int seq;

    // estado enviado
    private volatile float qx, qy, qz, qw = 1;
    private volatile int padButtons, keyButtons, pulseButtons;
    private volatile float scrollX, scrollY;
    private volatile boolean sensorOk;

    // ajustes do controle
    private int code;
    private String manualIp = "";
    private boolean left;

    private final Handler main = new Handler(Looper.getMainLooper());
    private PadView pad;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("visaobox", MODE_PRIVATE);
        prefs.edit().putBoolean("ctlMode", true).apply();
        code = prefs.getInt("ctlCode", 0);
        manualIp = prefs.getString("ctlIp", "");
        left = prefs.getBoolean("ctlLeft", false);
        compass = prefs.getBoolean("ctlCompass", true);
        fusion.enabled = compass;
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            rot = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (rot == null) rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            else magRot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);     // bússola contra o desvio
        }
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (code >= 1000) showPad(); else showSetup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startNet();
    }

    @Override
    protected void onPause() {
        if (sm != null) sm.unregisterListener(this);
        stopNet();
        super.onPause();
    }

    /* ================= telas ================= */

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private Button button(String s, boolean primary, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(s);
        bt.setAllCaps(false);
        bt.setTextColor(Color.WHITE);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(primary ? 0xFF2F8CFF : 0x26FFFFFF);
        bt.setBackground(g);
        bt.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        lp.setMargins(0, dp(8), 0, 0);
        bt.setLayoutParams(lp);
        return bt;
    }

    private EditText field(String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(type);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0x66FFFFFF);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        e.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(0x1AFFFFFF);
        g.setStroke(dp(1), 0x40FFFFFF);
        e.setBackground(g);
        e.setPadding(dp(12), dp(12), dp(12), dp(12));
        return e;
    }

    private void showSetup() {
        pad = null;
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0B0D14);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(28), dp(22), dp(28));
        sv.addView(box);
        box.addView(text("📱  Celular-controle", 26, Color.WHITE, true));
        box.addView(text("Este celular vai virar o controle do Visão Box: aponte para mirar, toque para clicar e arraste para rolar.\n\n"
                + "1. Ligue os dois celulares no mesmo Wi-Fi (ou ligue o roteador/hotspot no celular do óculos e conecte este nele).\n"
                + "2. No celular do óculos, a tela de início mostra o código de 4 dígitos.\n"
                + "3. Digite o código aqui e toque em Conectar.", 15, 0xCCFFFFFF, false));

        box.addView(text("Código do óculos", 14, 0xFF9FD3FF, true));
        final EditText codeF = field("0000", code >= 1000 ? String.valueOf(code) : "", InputType.TYPE_CLASS_NUMBER);
        box.addView(codeF);
        box.addView(text("IP do óculos (opcional — só se ele não for achado sozinho)", 14, 0xFF9FD3FF, true));
        final EditText ipF = field("ex.: 192.168.0.12", manualIp, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ipF.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        box.addView(ipF);

        box.addView(text("Em qual mão você vai segurar?", 14, 0xFF9FD3FF, true));
        LinearLayout hands = new LinearLayout(this);
        hands.setOrientation(LinearLayout.HORIZONTAL);
        final Button bl = button("✋ Esquerda", left, null), br = button("Direita 🤚", !left, null);
        View.OnClickListener pick = v -> {
            left = v == bl;
            ((GradientDrawable) bl.getBackground()).setColor(left ? 0xFF2F8CFF : 0x26FFFFFF);
            ((GradientDrawable) br.getBackground()).setColor(!left ? 0xFF2F8CFF : 0x26FFFFFF);
        };
        bl.setOnClickListener(pick); br.setOnClickListener(pick);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, dp(54), 1);
        hl.setMargins(0, dp(8), dp(6), 0);
        LinearLayout.LayoutParams hr = new LinearLayout.LayoutParams(0, dp(54), 1);
        hr.setMargins(dp(6), dp(8), 0, 0);
        hands.addView(bl, hl); hands.addView(br, hr);
        box.addView(hands);

        final Button cb = button(compass ? "🧭 Bússola contra o desvio: ligada" : "🧭 Bússola contra o desvio: desligada", false, null);
        cb.setOnClickListener(v -> {
            compass = !compass; fusion.enabled = compass;
            prefs.edit().putBoolean("ctlCompass", compass).apply();
            cb.setText(compass ? "🧭 Bússola contra o desvio: ligada" : "🧭 Bússola contra o desvio: desligada");
        });
        box.addView(cb);
        box.addView(text("A bússola impede que o raio escorregue com o tempo. Se o raio ficar puxando para um lado perto de metais, desligue.", 13, 0x99FFFFFF, false));

        final TextView err = text("", 14, 0xFFFF8A80, false);
        box.addView(button("Conectar ▶", true, v -> {
            String c = codeF.getText().toString().trim();
            if (!c.matches("\\d{4}")) { err.setText("O código tem 4 números. Veja na tela de início do celular do óculos."); return; }
            code = Integer.parseInt(c);
            manualIp = ipF.getText().toString().trim();
            prefs.edit().putInt("ctlCode", code).putString("ctlIp", manualIp).putBoolean("ctlLeft", left).apply();
            lastTargets = 0; peer = null; lastStatus = -100000;
            showPad();
        }));
        box.addView(err);
        box.addView(button("Usar este celular como óculos", false, v -> backToHeadset()));
        box.addView(text("Segure como preferir: deitado (tela para cima, como controle de TV) ou em pé (como uma câmera). Aponte para a frente e toque ◎: o app aprende o jeito que você segura.\n\n"
                + "Para menos atraso, ligue o roteador (hotspot) no celular do óculos e conecte este nele, em vez de usar o Wi-Fi de casa.", 13, 0x99FFFFFF, false));
        setContentView(sv);
    }

    private void showPad() {
        pad = new PadView(this);
        setContentView(pad);
        pad.setKeepScreenOn(true);
    }

    private void backToHeadset() {
        prefs.edit().putBoolean("ctlMode", false).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (pad != null) { showSetup(); return; }
        super.onBackPressed();
    }

    /* ================= botões físicos: volume ================= */

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int c = e.getKeyCode();
        if (pad != null && (c == KeyEvent.KEYCODE_VOLUME_UP || c == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            int bit = c == KeyEvent.KEYCODE_VOLUME_UP ? RemoteProto.B_TRIGGER : RemoteProto.B_GRIP;
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) { keyButtons |= bit; kick(); buzz(bit == RemoteProto.B_TRIGGER ? 22 : 14); pad.invalidate(); }
            } else if (e.getAction() == KeyEvent.ACTION_UP) { keyButtons &= ~bit; kick(); pad.invalidate(); }
            return true;     // não muda o volume
        }
        return super.dispatchKeyEvent(e);
    }

    /* ================= sensores ================= */

    @Override
    public void onSensorChanged(SensorEvent e) {         // chega na linha de envio, não na tela
        if (e.sensor == magRot && magRot != null) { fusion.compass(RemoteProto.quat(e.values), e.timestamp); return; }
        float[] q = RemoteProto.quat(e.values);
        if (magRot != null) { fusion.game(q); q = fusion.corrected(q); }
        qx = q[0]; qy = q[1]; qz = q[2]; qw = q[3];
        sensorOk = true;
        if (SystemClock.uptimeMillis() - lastSend >= 8) {    // manda na hora (até ~120 por segundo)
            try { send(); } catch (Throwable ignored) { }
        }
    }

    /** Botão apertado/solto: manda já, sem esperar a próxima leitura do sensor. */
    private void kick() {
        Handler h = net;
        if (h != null && running) h.post(() -> { try { send(); } catch (Throwable ignored) { } });
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    /* ================= rede ================= */

    private boolean connected() { return peer != null && SystemClock.uptimeMillis() - lastStatus < 2500; }

    private void startNet() {
        if (running) return;
        running = true;
        netThread = new HandlerThread("controle-envio");
        netThread.start();
        net = new Handler(netThread.getLooper());
        net.post(() -> {
            try {
                sock = new DatagramSocket();
                sock.setBroadcast(true);
                sock.setSoTimeout(500);
            } catch (Throwable t) { sock = null; }
            if (rot != null) sm.registerListener(this, rot, SensorManager.SENSOR_DELAY_FASTEST, net);
            if (magRot != null && compass) sm.registerListener(this, magRot, SensorManager.SENSOR_DELAY_UI, net);
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    wifiLock = wm.createWifiLock(Build.VERSION.SDK_INT >= 29 ? RemoteLink.WIFI_MODE_FULL_LOW_LATENCY : RemoteLink.WIFI_MODE_FULL_HIGH_PERF, "visaobox-controle");
                    wifiLock.setReferenceCounted(false);
                    wifiLock.acquire();       // sem economia de energia: os pacotes não ficam presos
                }
            } catch (Throwable ignored) { }
            rxThread = new Thread(this::receive, "controle-recebe");
            rxThread.setDaemon(true);
            rxThread.start();
            net.post(tick);
        });
    }

    private void stopNet() {
        running = false;
        if (net != null) net.removeCallbacksAndMessages(null);
        if (netThread != null) netThread.quitSafely();
        DatagramSocket s = sock;
        if (s != null) s.close();
        sock = null;
        peer = null;
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Throwable ignored) { }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (SystemClock.uptimeMillis() - lastSend >= 18) {   // celular parado: o sensor manda menos, então garante o envio
                try { send(); } catch (Throwable ignored) { }
            }
            net.postDelayed(this, 20);
            if (pad != null && (++ticks % 6) == 0) main.post(() -> { if (pad != null) pad.invalidate(); });
        }
    };

    private void send() throws Exception {
        DatagramSocket s = sock;
        if (s == null || code < 1000) return;
        long now = SystemClock.uptimeMillis();
        if (!connected()) {
            if (now - lastHello < 400) return;
            lastHello = now;
            if (now - lastTargets > 3000) { targets = findTargets(); lastTargets = now; }
            byte[] d = RemoteProto.hello(code, RemoteLink.deviceName());
            for (InetAddress a : targets) {
                try { s.send(new DatagramPacket(d, d.length, a, RemoteProto.PORT)); } catch (Throwable ignored) { }
            }
            return;
        }
        RemoteProto.State st = new RemoteProto.State();
        st.code = code; st.seq = ++seq; st.time = (int) (now - t0);
        st.qx = qx; st.qy = qy; st.qz = qz; st.qw = qw;
        st.buttons = padButtons | keyButtons | pulseButtons;
        st.scrollX = scrollX; st.scrollY = scrollY;
        st.flags = left ? RemoteProto.F_LEFT : 0;
        byte[] d = RemoteProto.state(st);
        s.send(new DatagramPacket(d, d.length, peer, peerPort));
        lastSend = now;
    }

    /** Onde procurar o óculos: IP digitado, o roteador/hotspot e o broadcast de cada rede. */
    @SuppressWarnings("deprecation")
    private List<InetAddress> findTargets() {
        ArrayList<InetAddress> out = new ArrayList<>();
        if (manualIp != null && !manualIp.isEmpty()) {
            try { out.add(InetAddress.getByName(manualIp)); } catch (Throwable ignored) { }
        }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            DhcpInfo di = wm != null ? wm.getDhcpInfo() : null;
            if (di != null && di.gateway != 0) {
                int g = di.gateway;   // o celular do óculos pode ser o hotspot (roteador) desta rede
                byte[] ip = {(byte) g, (byte) (g >> 8), (byte) (g >> 16), (byte) (g >> 24)};
                out.add(InetAddress.getByAddress(ip));
            }
        } catch (Throwable ignored) { }
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getAddress() instanceof Inet4Address && ia.getBroadcast() != null) out.add(ia.getBroadcast());
                }
            }
        } catch (Throwable ignored) { }
        try { out.add(InetAddress.getByName("255.255.255.255")); } catch (Throwable ignored) { }
        return out;
    }

    private void receive() {
        byte[] buf = new byte[256];
        while (running) {
            DatagramSocket s = sock;
            if (s == null) break;
            try {
                DatagramPacket pk = new DatagramPacket(buf, buf.length);
                s.receive(pk);
                int t = RemoteProto.type(buf, pk.getLength());
                if (t == RemoteProto.STATUS) {
                    boolean was = connected();
                    peer = pk.getAddress(); peerPort = pk.getPort();
                    lastStatus = SystemClock.uptimeMillis();
                    peerName = RemoteProto.statusName(buf, pk.getLength());
                    int echo = RemoteProto.statusEcho(buf, pk.getLength());
                    if (echo > 0) {
                        int r = (int) (lastStatus - t0) - echo;
                        if (r >= 0 && r < 2000) rtt = rtt < 0 ? r : (rtt * 3 + r) / 4;
                    }
                    if (!was) buzz(60);
                } else if (t == RemoteProto.HAPTIC) {
                    buzzAmp(RemoteProto.hapticMs(buf, pk.getLength()), RemoteProto.hapticAmp(buf, pk.getLength()));
                }
            } catch (SocketTimeoutException ignored) {
            } catch (Throwable e) {
                if (!running) break;
            }
        }
    }

    /* ================= vibração ================= */

    private void buzz(int ms) { buzzAmp(ms, 200); }

    @SuppressWarnings("deprecation")
    private void buzzAmp(int ms, int amp) {
        if (vib == null || ms <= 0) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(ms, Math.max(1, Math.min(255, amp))));
            else vib.vibrate(ms);
        } catch (Throwable ignored) { }
    }

    /* ================= a tela do controle ================= */

    private static final int R_PAD = 100, R_SETUP = 101;

    private final class PadView extends View {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        final ArrayList<RectF> rects = new ArrayList<>();
        final ArrayList<Integer> ids = new ArrayList<>();
        final ArrayList<String[]> labels = new ArrayList<>();
        final SparseIntArray ptrRegion = new SparseIntArray();
        float padSX, padSY, padLX, padLY; long padT; int padPtr = -1; boolean padMoved;
        final long[] downAt = new long[128];

        PadView(Context c) {
            super(c);
            setBackgroundColor(0xFF07080D);
            stroke.setStyle(Paint.Style.STROKE);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setColor(Color.WHITE);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            rects.clear(); ids.clear(); labels.clear();
            float m = dp(10), top = dp(64);
            float padB = top + (h - top) * .44f;
            add(new RectF(m, top, w - m, padB), R_PAD, "Toque = clicar", "Arraste = rolar a página");
            float r2b = padB + (h - top) * .25f;
            float c1 = w * .30f, c2 = w * .70f;
            int leftId = left ? RemoteProto.B_TRIGGER : RemoteProto.B_BACK, rightId = left ? RemoteProto.B_BACK : RemoteProto.B_TRIGGER;
            add(new RectF(m, padB + m, c1 - m / 2, r2b), leftId, leftId == RemoteProto.B_TRIGGER ? "A" : "B", leftId == RemoteProto.B_TRIGGER ? "clicar" : "voltar");
            add(new RectF(c1 + m / 2, padB + m, c2 - m / 2, r2b), RemoteProto.B_GRIP, "✊", "segurar janela");
            add(new RectF(c2 + m / 2, padB + m, w - m, r2b), rightId, rightId == RemoteProto.B_TRIGGER ? "A" : "B", rightId == RemoteProto.B_TRIGGER ? "clicar" : "voltar");
            float r3b = h - dp(40);
            int[] row = {RemoteProto.B_MENU, RemoteProto.B_KEYB, RemoteProto.B_VOICE, RemoteProto.B_HOME};
            String[][] lb = {{"▦", "menu"}, {"⌨", "teclado"}, {"🎤", "voz"}, {"◎", "alinhar"}};
            float cw = (w - m) / 4f;
            for (int i = 0; i < 4; i++) add(new RectF(m + i * cw, r2b + m, m + (i + 1) * cw - m, r3b), row[i], lb[i][0], lb[i][1]);
            add(new RectF(w - dp(58), dp(10), w - m, dp(52)), R_SETUP, "⚙", "");
        }

        private void add(RectF r, int id, String a, String b) { rects.add(r); ids.add(id); labels.add(new String[]{a, b}); }

        private int region(float x, float y) {
            for (int i = rects.size() - 1; i >= 0; i--) if (rects.get(i).contains(x, y)) return ids.get(i);
            return 0;
        }

        private void recompute() {
            int b = 0;
            for (int i = 0; i < ptrRegion.size(); i++) {
                int r = ptrRegion.valueAt(i);
                if (r > 0 && r < R_PAD) b |= r;
            }
            if (b != padButtons) { padButtons = b; kick(); }
        }

        /** Garante que um toque rápido dure pelo menos ~70 ms (para nenhum pacote perdido apagar o clique). */
        private void pulse(int bit, long ms) {
            pulseButtons |= bit;
            kick();
            main.postDelayed(() -> { pulseButtons &= ~bit; kick(); invalidate(); }, ms);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            long now = SystemClock.uptimeMillis();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                int i = e.getActionIndex(), id = e.getPointerId(i);
                float x = e.getX(i), y = e.getY(i);
                int r = region(x, y);
                if (r == R_SETUP) { showSetup(); return true; }
                ptrRegion.put(id, r);
                if (id < downAt.length) downAt[id] = now;
                if (r == R_PAD && padPtr < 0) { padPtr = id; padSX = padLX = x; padSY = padLY = y; padT = now; padMoved = false; }
                else if (r > 0 && r < R_PAD) buzz(r == RemoteProto.B_TRIGGER ? 22 : 14);
                recompute();
            } else if (a == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    if (e.getPointerId(i) != padPtr) continue;
                    float x = e.getX(i), y = e.getY(i);
                    if (Math.hypot(x - padSX, y - padSY) > dp(10)) padMoved = true;
                    float k = 2.4f / getResources().getDisplayMetrics().density;
                    scrollX -= (x - padLX) * k * 2;
                    scrollY -= (y - padLY) * k * 2;
                    padLX = x; padLY = y;
                }
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_CANCEL) {
                int i = e.getActionIndex(), id = e.getPointerId(i);
                int r = ptrRegion.get(id, 0);
                ptrRegion.delete(id);
                if (a == MotionEvent.ACTION_CANCEL) ptrRegion.clear();
                if (id == padPtr) {
                    padPtr = -1;
                    if (!padMoved && now - padT < 260 && a != MotionEvent.ACTION_CANCEL) { buzz(22); pulse(RemoteProto.B_TRIGGER, 90); }
                } else if (r > 0 && r < R_PAD && id < downAt.length && now - downAt[id] < 70) pulse(r, 70 - (now - downAt[id]) + 10);
                recompute();
            }
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            // linha de status
            boolean ok = connected();
            txt.setTextAlign(Paint.Align.LEFT);
            txt.setTextSize(dp(15));
            txt.setTypeface(Typeface.DEFAULT_BOLD);
            fill.setColor(ok ? 0xFF4CD964 : 0xFFFFC94D);
            c.drawCircle(dp(22), dp(31), dp(6), fill);
            txt.setColor(Color.WHITE);
            String st = ok ? "Conectado" + (peerName.isEmpty() ? "" : " ao " + peerName) + (rtt >= 0 ? " · " + rtt + " ms" : "")
                    : "Procurando o óculos… código " + code;
            c.drawText(st, dp(36), dp(29), txt);
            txt.setTypeface(Typeface.DEFAULT);
            txt.setTextSize(dp(12));
            txt.setColor(0x99FFFFFF);
            c.drawText(!sensorOk ? "Sem sensor de rotação neste celular" : ok ? "Aponte para a frente e toque ◎ para alinhar" : "Os dois celulares precisam estar no mesmo Wi-Fi", dp(36), dp(48), txt);
            txt.setTextAlign(Paint.Align.CENTER);

            int held = padButtons | keyButtons | pulseButtons;
            for (int i = 0; i < rects.size(); i++) {
                RectF r = rects.get(i);
                int id = ids.get(i);
                boolean on = id == R_PAD ? padPtr >= 0 : id < R_PAD && (held & id) != 0;
                boolean main1 = id == RemoteProto.B_TRIGGER;
                fill.setColor(on ? 0xFF2F8CFF : main1 ? 0x332F8CFF : id == R_PAD ? 0x14FFFFFF : 0x1FFFFFFF);
                c.drawRoundRect(r, dp(18), dp(18), fill);
                stroke.setColor(on ? 0xFF9FD3FF : 0x33FFFFFF);
                stroke.setStrokeWidth(dp(on ? 2 : 1));
                c.drawRoundRect(r, dp(18), dp(18), stroke);
                String[] lb = labels.get(i);
                boolean small = r.height() < dp(90) || id == R_SETUP;
                txt.setColor(Color.WHITE);
                txt.setTypeface(Typeface.DEFAULT_BOLD);
                txt.setTextSize(id == R_PAD ? dp(18) : small ? dp(22) : dp(34));
                float cy = r.centerY() + (lb[1].isEmpty() ? txt.getTextSize() * .35f : -dp(2));
                c.drawText(lb[0], r.centerX(), cy, txt);
                if (!lb[1].isEmpty()) {
                    txt.setTypeface(Typeface.DEFAULT);
                    txt.setTextSize(dp(13));
                    txt.setColor(0xB3FFFFFF);
                    c.drawText(lb[1], r.centerX(), cy + dp(22), txt);
                }
            }
            if (padPtr >= 0) {
                fill.setColor(0x669FD3FF);
                c.drawCircle(padLX, padLY, dp(26), fill);
            }
            txt.setTextSize(dp(12));
            txt.setTypeface(Typeface.DEFAULT);
            txt.setColor(0x80FFFFFF);
            c.drawText("Vol + = clicar  ·  Vol − = segurar  ·  segure ◎ = centralizar", w / 2f, getHeight() - dp(16), txt);
        }
    }
}
