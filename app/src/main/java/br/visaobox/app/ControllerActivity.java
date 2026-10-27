package br.visaobox.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
 * Modo CONTROLE: este celular controla o Visão Box que está no óculos.
 *
 * Ligação:  Wi-Fi (código de 4 dígitos)  ou  Bluetooth (vira um controle de videogame de verdade).
 * Modos:    Mouse no ar (girar o celular move a mira)  ·  Touchpad (o dedo move a mira)  ·  Raio (só Wi-Fi).
 */
public class ControllerActivity extends Activity implements SensorEventListener {

    private static final int REQ_BT = 41;
    private static final int SENSOR_US = 5000;
    private static final String[] MODE_NAMES = {"Mouse no ar", "Touchpad", "Raio"};
    private static final String[] SENS_NAMES = {"Baixa", "Média", "Alta"};
    private static final float[] SENS = {.7f, 1f, 1.5f};

    private SharedPreferences prefs;
    private SensorManager sm;
    private Sensor rot, magRot, gyro;
    private final YawFusion fusion = new YawFusion();
    private Vibrator vib;
    private WifiManager.WifiLock wifiLock;
    private BtGamepad bt;
    private volatile String btStatus = "";

    // linha de envio
    private HandlerThread netThread;
    private Handler net;
    private Thread rxThread;
    private volatile boolean running;
    private DatagramSocket sock;
    private volatile InetAddress peer;
    private volatile int peerPort;
    private volatile long lastStatus = -100000;
    private volatile int rtt = -1;
    private volatile String peerName = "";
    private final long t0 = SystemClock.uptimeMillis();
    private long lastHello, lastTargets;
    private List<InetAddress> targets = new ArrayList<>();
    private int seq, ticks;
    private volatile long lastSend;

    // estado
    private volatile float qx, qy, qz, qw = 1;
    private volatile boolean sensorOk;
    private long lastGyroNs;
    private volatile float mouseX, mouseY;          // total (rad)
    private volatile float rateX, rateY;            // velocidade da mira pelo giro (rad/s), para o Bluetooth
    private volatile long rateAt;
    private volatile float tpVX, tpVY;              // velocidade do dedo no touchpad (rad/s)
    private volatile long tpAt;
    private volatile float scrollX, scrollY;        // total (px)
    private volatile float svX, svY;                // velocidade da rolagem (px/s)
    private volatile long svAt;
    private volatile int padButtons, keyButtons, pulseButtons;
    // ◎ no Bluetooth: toque = mira no centro (L3), segurar = centralizar tudo (Select)
    private long homeDownAt; private boolean homeFired; private int btPulse; private long btPulseUntil;

    // ajustes
    private boolean useBt, left, compass;
    private int mode, sens, code;
    private String manualIp = "";

    private final Handler main = new Handler(Looper.getMainLooper());
    private PadView pad;
    private TextView btText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("visaobox", MODE_PRIVATE);
        // "ctlStarting" fica ligado até o controle abrir direito: se fechar com erro, o app não reabre preso nele
        prefs.edit().putBoolean("ctlMode", true).putBoolean("ctlStarting", true).commit();
        main.postDelayed(() -> prefs.edit().putBoolean("ctlStarting", false).apply(), 4000);
        code = prefs.getInt("ctlCode", 0);
        manualIp = prefs.getString("ctlIp", "");
        left = prefs.getBoolean("ctlLeft", false);
        compass = prefs.getBoolean("ctlCompass", true);
        useBt = prefs.getBoolean("ctlBt", false);
        mode = prefs.getInt("ctlMode2", RemoteProto.M_MOUSE);
        sens = prefs.getInt("ctlSens", 1);
        if (sens < 0 || sens > 2) sens = 1;
        if (mode < 0 || mode > 2) mode = RemoteProto.M_MOUSE;
        fusion.enabled = compass;
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            rot = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (rot == null) rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            else magRot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        bt = new BtGamepad(this, prefs, (txt, ok) -> {
            btStatus = txt;
            if (ok) buzz(60);
            main.post(() -> { if (btText != null) btText.setText(txt); if (pad != null) pad.invalidate(); });
        });
        boolean ready = useBt ? btAllowed() : code >= 1000;
        if (ready) showPad(); else showSetup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLoop();
        if (useBt && btAllowed()) bt.start();
    }

    @Override
    protected void onPause() {
        if (sm != null) sm.unregisterListener(this);
        stopLoop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        bt.stop();
        super.onDestroy();
    }

    /* ================= permissões do Bluetooth ================= */

    private String[] btPerms() {
        return Build.VERSION.SDK_INT >= 31
                ? new String[]{"android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_ADVERTISE"}
                : new String[0];
    }

    private boolean btAllowed() {
        for (String p : btPerms()) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int c, String[] perms, int[] res) {
        super.onRequestPermissionsResult(c, perms, res);
        if (c != REQ_BT) return;
        if (btAllowed()) { ensureBtOn(); bt.start(); showPad(); }
        else if (btText != null) btText.setText("Sem a permissão de Bluetooth não dá para virar controle. Use o Wi-Fi.");
    }

    private void ensureBtOn() {
        try {
            BluetoothAdapter a = bt.adapter();
            if (a != null && !a.isEnabled()) startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } catch (Throwable ignored) { }
    }

    private void makeVisible() {
        try {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300));
        } catch (Throwable ignored) { }
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
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(primary ? 0xFF2F8CFF : 0x26FFFFFF);
        b.setBackground(g);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        lp.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void mark(Button b, boolean on) { ((GradientDrawable) b.getBackground()).setColor(on ? 0xFF2F8CFF : 0x26FFFFFF); }

    /** Linha de botões em que só um fica marcado. */
    private LinearLayout choice(String[] opts, int sel, java.util.function.IntConsumer pick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final Button[] bs = new Button[opts.length];
        for (int i = 0; i < opts.length; i++) {
            final int k = i;
            bs[i] = button(opts[i], i == sel, v -> { for (int j = 0; j < bs.length; j++) mark(bs[j], j == k); pick.accept(k); });
            bs[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1);
            lp.setMargins(i == 0 ? 0 : dp(4), dp(6), i == opts.length - 1 ? 0 : dp(4), 0);
            row.addView(bs[i], lp);
        }
        return row;
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

        box.addView(text("Como ligar no celular do óculos?", 14, 0xFF9FD3FF, true));
        final LinearLayout wifiBox = new LinearLayout(this), btBox = new LinearLayout(this);
        wifiBox.setOrientation(LinearLayout.VERTICAL);
        btBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(choice(new String[]{"🔵 Bluetooth", "📶 Wi-Fi"}, useBt ? 0 : 1, k -> {
            useBt = k == 0;
            wifiBox.setVisibility(useBt ? View.GONE : View.VISIBLE);
            btBox.setVisibility(useBt ? View.VISIBLE : View.GONE);
        }));

        // ---- Bluetooth ----
        btBox.addView(text("Este celular vira um controle de videogame de verdade. Não precisa de Wi-Fi nem de código.\n\n"
                + "1. Toque em Conectar (o Android pede a permissão de Bluetooth).\n"
                + "2. Toque em \"Ficar visível\".\n"
                + "3. No celular do óculos: Configurações → Bluetooth → parear \"" + bt.name() + "\".\n"
                + "Depois disso ele conecta sozinho.", 15, 0xCCFFFFFF, false));
        btBox.addView(button("👁  Ficar visível para parear", false, v -> makeVisible()));
        btText = text(btStatus.isEmpty() ? (BtGamepad.supported() ? "" : "Precisa de Android 9 ou mais novo.") : btStatus, 14, 0xFFFFC94D, false);
        btBox.addView(btText);
        btBox.addView(text("Se aparecer \"o fabricante bloqueou\", este celular não aceita o modo Bluetooth: use o Wi-Fi.", 13, 0x99FFFFFF, false));
        box.addView(btBox);

        // ---- Wi-Fi ----
        wifiBox.addView(text("Os dois no mesmo Wi-Fi (ou o hotspot do celular do óculos ligado e este conectado nele). "
                + "A tela de início do óculos mostra o código.", 15, 0xCCFFFFFF, false));
        wifiBox.addView(text("Código do óculos", 14, 0xFF9FD3FF, true));
        final EditText codeF = field("0000", code >= 1000 ? String.valueOf(code) : "", InputType.TYPE_CLASS_NUMBER);
        wifiBox.addView(codeF);
        wifiBox.addView(text("IP do óculos (opcional)", 14, 0xFF9FD3FF, true));
        final EditText ipF = field("ex.: 192.168.0.12", manualIp, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ipF.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        wifiBox.addView(ipF);
        box.addView(wifiBox);
        wifiBox.setVisibility(useBt ? View.GONE : View.VISIBLE);
        btBox.setVisibility(useBt ? View.VISIBLE : View.GONE);

        box.addView(text("Sensibilidade do mouse no ar", 14, 0xFF9FD3FF, true));
        box.addView(choice(SENS_NAMES, sens, k -> sens = k));
        box.addView(text("Mão (para o modo Raio)", 14, 0xFF9FD3FF, true));
        box.addView(choice(new String[]{"✋ Esquerda", "Direita 🤚"}, left ? 0 : 1, k -> left = k == 0));
        final Button cb = button("", false, null);
        Runnable cbText = () -> cb.setText(compass ? "🧭 Bússola no modo Raio: ligada" : "🧭 Bússola no modo Raio: desligada");
        cbText.run();
        cb.setOnClickListener(v -> { compass = !compass; fusion.enabled = compass; cbText.run(); });
        box.addView(cb);

        final TextView err = text("", 14, 0xFFFF8A80, false);
        box.addView(button("Conectar ▶", true, v -> {
            if (!useBt) {
                String c = codeF.getText().toString().trim();
                if (!c.matches("\\d{4}")) { err.setText("O código tem 4 números. Veja na tela de início do celular do óculos."); return; }
                code = Integer.parseInt(c);
                manualIp = ipF.getText().toString().trim();
                lastTargets = 0; peer = null; lastStatus = -100000;
            } else if (mode == RemoteProto.M_RAY) mode = RemoteProto.M_MOUSE;
            save();
            stopLoop(); startLoop();                     // troca Wi-Fi ⇄ Bluetooth
            if (useBt) {
                if (!BtGamepad.supported()) { err.setText("O modo Bluetooth precisa de Android 9 ou mais novo. Use o Wi-Fi."); return; }
                if (!btAllowed()) { requestPermissions(btPerms(), REQ_BT); return; }
                ensureBtOn();
                bt.start();
            } else bt.stop();
            showPad();
        }));
        box.addView(err);
        box.addView(button("Usar este celular como óculos", false, v -> backToHeadset()));
        setContentView(sv);
    }

    private void save() {
        prefs.edit().putInt("ctlCode", code).putString("ctlIp", manualIp).putBoolean("ctlLeft", left)
                .putBoolean("ctlCompass", compass).putBoolean("ctlBt", useBt).putInt("ctlMode2", mode).putInt("ctlSens", sens).apply();
    }

    private void showPad() {
        btText = null;
        pad = new PadView(this);
        setContentView(pad);
        pad.setKeepScreenOn(true);
    }

    private void backToHeadset() {
        bt.stop();
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

    private void nextMode() {
        int n = useBt ? 2 : 3;                                  // o raio precisa do Wi-Fi
        mode = (mode + 1) % n;
        save();
        buzz(25);
    }

    /* ================= volume = gatilho ================= */

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int c = e.getKeyCode();
        if (pad != null && (c == KeyEvent.KEYCODE_VOLUME_UP || c == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            int bit = c == KeyEvent.KEYCODE_VOLUME_UP ? RemoteProto.B_TRIGGER : RemoteProto.B_GRIP;
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) { keyButtons |= bit; kick(); buzz(bit == RemoteProto.B_TRIGGER ? 22 : 14); pad.invalidate(); }
            } else if (e.getAction() == KeyEvent.ACTION_UP) { keyButtons &= ~bit; kick(); pad.invalidate(); }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /* ================= sensores (chegam na linha de envio) ================= */

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor == magRot && magRot != null) { fusion.compass(RemoteProto.quat(e.values), e.timestamp); return; }
        if (e.sensor == gyro && gyro != null) {
            float dt = lastGyroNs == 0 ? 0 : Math.min(.05f, (e.timestamp - lastGyroNs) / 1e9f);
            lastGyroNs = e.timestamp;
            float[] r = RemoteProto.mouseRates(e.values[0], e.values[1], e.values[2], RemoteProto.upRow(new float[]{qx, qy, qz, qw}));
            float sx = RemoteProto.shape(r[0], SENS[sens]), sy = RemoteProto.shape(r[1], SENS[sens]);
            rateX = sx; rateY = sy; rateAt = SystemClock.uptimeMillis();
            if (mode == RemoteProto.M_MOUSE && dt > 0) { mouseX += sx * dt; mouseY += sy * dt; }
            if (!useBt && mode == RemoteProto.M_MOUSE) maybeSend();
            return;
        }
        float[] q = RemoteProto.quat(e.values);
        if (magRot != null) { fusion.game(q); q = fusion.corrected(q); }
        qx = q[0]; qy = q[1]; qz = q[2]; qw = q[3];
        sensorOk = true;
        if (!useBt && mode == RemoteProto.M_RAY) maybeSend();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void maybeSend() {
        if (SystemClock.uptimeMillis() - lastSend >= 8) { try { sendWifi(); } catch (Throwable ignored) { } }
    }

    /** Botão mudou: manda já. */
    private void kick() {
        Handler h = net;
        if (h != null && running) h.post(() -> { try { if (useBt) sendBt(SystemClock.uptimeMillis()); else sendWifi(); } catch (Throwable ignored) { } });
    }

    /* ================= laço de envio ================= */

    private void startLoop() {
        if (running) return;
        running = true;
        netThread = new HandlerThread("controle-envio");
        netThread.start();
        net = new Handler(netThread.getLooper());
        net.post(() -> {
            // 5 ms = 200 por segundo: o máximo permitido sem permissão especial (Android 12+ fecha o app acima disso)
            try { if (rot != null) sm.registerListener(this, rot, SENSOR_US, net); } catch (Throwable t) { rot = null; }
            try { if (gyro != null) sm.registerListener(this, gyro, SENSOR_US, net); } catch (Throwable t) { gyro = null; }
            try { if (magRot != null && compass) sm.registerListener(this, magRot, SensorManager.SENSOR_DELAY_UI, net); } catch (Throwable t) { magRot = null; }
            if (!useBt) {
                try { sock = new DatagramSocket(); sock.setBroadcast(true); sock.setSoTimeout(500); } catch (Throwable t) { sock = null; }
                try {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wm != null) {
                        wifiLock = wm.createWifiLock(Build.VERSION.SDK_INT >= 29 ? RemoteLink.WIFI_MODE_FULL_LOW_LATENCY : RemoteLink.WIFI_MODE_FULL_HIGH_PERF, "visaobox-controle");
                        wifiLock.setReferenceCounted(false);
                        wifiLock.acquire();
                    }
                } catch (Throwable ignored) { }
                rxThread = new Thread(this::receive, "controle-recebe");
                rxThread.setDaemon(true);
                rxThread.start();
            }
            net.post(tick);
        });
    }

    private void stopLoop() {
        running = false;
        if (sm != null) sm.unregisterListener(this);
        lastGyroNs = 0;
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
            long now = SystemClock.uptimeMillis();
            try {
                if (useBt) sendBt(now);
                else if (now - lastSend >= 18) sendWifi();
            } catch (Throwable ignored) { }
            net.postDelayed(this, useBt ? 10 : 20);
            if (pad != null && (++ticks % (useBt ? 12 : 6)) == 0) main.post(() -> { if (pad != null) pad.invalidate(); });
        }
    };

    private int ourButtons() { return padButtons | keyButtons | pulseButtons; }

    /* ---------- Wi-Fi ---------- */

    private boolean wifiConnected() { return peer != null && SystemClock.uptimeMillis() - lastStatus < 2500; }

    private void sendWifi() throws Exception {
        DatagramSocket s = sock;
        if (s == null || code < 1000) return;
        long now = SystemClock.uptimeMillis();
        if (!wifiConnected()) {
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
        st.buttons = ourButtons();
        st.scrollX = scrollX; st.scrollY = scrollY;
        st.mouseX = mouseX; st.mouseY = mouseY;
        st.flags = RemoteProto.withMode(left ? RemoteProto.F_LEFT : 0, mode);
        byte[] d = RemoteProto.state(st);
        s.send(new DatagramPacket(d, d.length, peer, peerPort));
        lastSend = now;
    }

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
                int g = di.gateway;
                out.add(InetAddress.getByAddress(new byte[]{(byte) g, (byte) (g >> 8), (byte) (g >> 16), (byte) (g >> 24)}));
            }
        } catch (Throwable ignored) { }
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
                    boolean was = wifiConnected();
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

    /* ---------- Bluetooth ---------- */

    /** Nossos botões → botões do gamepad que o Visão Box já entende (A, B, X, Y, LT, R3, L3, Select). */
    static int btButtons(int ours) {
        int b = 0;
        if ((ours & RemoteProto.B_TRIGGER) != 0) b |= BtGamepad.A;
        if ((ours & RemoteProto.B_BACK) != 0) b |= BtGamepad.B;
        if ((ours & RemoteProto.B_MENU) != 0) b |= BtGamepad.X;
        if ((ours & RemoteProto.B_KEYB) != 0) b |= BtGamepad.Y;
        if ((ours & RemoteProto.B_GRIP) != 0) b |= BtGamepad.L2;
        if ((ours & RemoteProto.B_VOICE) != 0) b |= BtGamepad.R3;
        return b;
    }

    /** Velocidade de rolagem (px/s) → analógico direito (o óculos rola v = a·|a|·1900 px/s). */
    static float scrollAxis(float v) { return Math.signum(v) * Math.min(1f, (float) Math.sqrt(Math.abs(v) / 1900f)); }

    private void sendBt(long now) {
        int ours = ourButtons();
        // ◎: toque = mira no centro, segurar = centralizar tudo
        boolean home = (ours & RemoteProto.B_HOME) != 0;
        if (home && homeDownAt == 0) { homeDownAt = now; homeFired = false; }
        if (home && !homeFired && now - homeDownAt > 800) { homeFired = true; btPulse = BtGamepad.SELECT; btPulseUntil = now + 150; }
        if (!home && homeDownAt != 0) { if (!homeFired) { btPulse = BtGamepad.L3; btPulseUntil = now + 150; } homeDownAt = 0; }
        int b = btButtons(ours) | (now < btPulseUntil ? btPulse : 0);

        float vx = 0, vy = 0;
        if (mode == RemoteProto.M_TOUCHPAD) {
            if (now - tpAt > 40) { tpVX *= .5f; tpVY *= .5f; }
            vx = tpVX; vy = tpVY;
        } else if (now - rateAt < 60) { vx = rateX; vy = rateY; }
        if (now - svAt > 40) { svX *= .5f; svY *= .5f; }
        bt.send(b, vx / SpatialUi.BT_RATE, vy / SpatialUi.BT_RATE, scrollAxis(svX), scrollAxis(svY), now);
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

    private static final int R_PAD = 100, R_SETUP = 101, R_MODE = 102;

    private final class PadView extends View {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        final ArrayList<RectF> rects = new ArrayList<>();
        final ArrayList<Integer> ids = new ArrayList<>();
        final ArrayList<String[]> labels = new ArrayList<>();
        final SparseIntArray ptrRegion = new SparseIntArray();
        final SparseIntArray padIds = new SparseIntArray();         // dedos no touchpad
        final float[] lastX = new float[16], lastY = new float[16];
        float tapX, tapY, dotX, dotY; long padT, lastMoveT; boolean padMoved; int maxFingers;
        final long[] downAt = new long[128];

        PadView(Context c) {
            super(c);
            setBackgroundColor(0xFF07080D);
            stroke.setStyle(Paint.Style.STROKE);
            txt.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            rects.clear(); ids.clear(); labels.clear();
            float m = dp(10), top = dp(110);
            float padB = top + (h - top) * .44f;
            add(new RectF(m, top, w - m, padB), R_PAD, "", "");
            float r2b = padB + (h - top) * .25f;
            float c1 = w * .30f, c2 = w * .70f;
            int leftId = left ? RemoteProto.B_TRIGGER : RemoteProto.B_BACK, rightId = left ? RemoteProto.B_BACK : RemoteProto.B_TRIGGER;
            add(new RectF(m, padB + m, c1 - m / 2, r2b), leftId, leftId == RemoteProto.B_TRIGGER ? "A" : "B", leftId == RemoteProto.B_TRIGGER ? "clicar" : "voltar");
            add(new RectF(c1 + m / 2, padB + m, c2 - m / 2, r2b), RemoteProto.B_GRIP, "✊", "segurar janela");
            add(new RectF(c2 + m / 2, padB + m, w - m, r2b), rightId, rightId == RemoteProto.B_TRIGGER ? "A" : "B", rightId == RemoteProto.B_TRIGGER ? "clicar" : "voltar");
            float r3b = h - dp(40);
            int[] row = {RemoteProto.B_MENU, RemoteProto.B_KEYB, RemoteProto.B_VOICE, RemoteProto.B_HOME};
            String[][] lb = {{"▦", "menu"}, {"⌨", "teclado"}, {"🎤", "voz"}, {"◎", "centro"}};
            float cw = (w - m) / 4f;
            for (int i = 0; i < 4; i++) add(new RectF(m + i * cw, r2b + m, m + (i + 1) * cw - m, r3b), row[i], lb[i][0], lb[i][1]);
            add(new RectF(m, dp(58), w - dp(68), dp(100)), R_MODE, "", "");
            add(new RectF(w - dp(58), dp(58), w - m, dp(100)), R_SETUP, "⚙", "");
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

        private void pulse(int bit, long ms) {
            pulseButtons |= bit;
            kick();
            main.postDelayed(() -> { pulseButtons &= ~bit; kick(); invalidate(); }, ms);
        }

        /** Dedo(s) no touchpad: 1 dedo = mira (touchpad) ou rolagem (mouse no ar/raio); 2 dedos = rolagem. */
        private void padMove(float dx, float dy, int fingers, long now) {
            float dt = Math.max(.004f, (now - lastMoveT) / 1000f);
            lastMoveT = now;
            boolean aim = mode == RemoteProto.M_TOUCHPAD && fingers == 1;
            if (aim) {
                float k = .0024f / getResources().getDisplayMetrics().density * SENS[sens];
                mouseX += dx * k; mouseY += dy * k;
                tpVX += (dx * k / dt - tpVX) * .6f; tpVY += (dy * k / dt - tpVY) * .6f; tpAt = now;
                Handler h = net;
                if (!useBt && h != null && running) h.post(ControllerActivity.this::maybeSend);
            } else {
                float k = 4.8f / getResources().getDisplayMetrics().density;
                float sx = -dx * k, sy = -dy * k;
                scrollX += sx; scrollY += sy;
                svX += (sx / dt - svX) * .6f; svY += (sy / dt - svY) * .6f; svAt = now;
            }
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
                if (r == R_MODE) { nextMode(); invalidate(); return true; }
                ptrRegion.put(id, r);
                if (id < downAt.length) downAt[id] = now;
                if (r == R_PAD) {
                    if (padIds.size() == 0) { tapX = x; tapY = y; padT = now; padMoved = false; maxFingers = 0; lastMoveT = now; }
                    padIds.put(id, 1);
                    maxFingers = Math.max(maxFingers, padIds.size());
                    if (id < lastX.length) { lastX[id] = x; lastY[id] = y; }
                    dotX = x; dotY = y;
                } else if (r > 0 && r < R_PAD) buzz(r == RemoteProto.B_TRIGGER ? 22 : 14);
                recompute();
            } else if (a == MotionEvent.ACTION_MOVE) {
                float sdx = 0, sdy = 0; int n = 0;
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    if (padIds.get(id, 0) == 0 || id >= lastX.length) continue;
                    float x = e.getX(i), y = e.getY(i);
                    sdx += x - lastX[id]; sdy += y - lastY[id]; n++;
                    lastX[id] = x; lastY[id] = y;
                    dotX = x; dotY = y;
                    if (Math.hypot(x - tapX, y - tapY) > dp(10)) padMoved = true;
                }
                if (n > 0 && (sdx != 0 || sdy != 0)) padMove(sdx / n, sdy / n, n, now);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_CANCEL) {
                int i = e.getActionIndex(), id = e.getPointerId(i);
                int r = ptrRegion.get(id, 0);
                ptrRegion.delete(id);
                if (a == MotionEvent.ACTION_CANCEL) { ptrRegion.clear(); padIds.clear(); }
                if (padIds.get(id, 0) != 0) {
                    padIds.delete(id);
                    if (padIds.size() == 0 && !padMoved && maxFingers == 1 && now - padT < 260 && a != MotionEvent.ACTION_CANCEL) {
                        buzz(22); pulse(RemoteProto.B_TRIGGER, 90);           // toque = clicar
                    }
                } else if (r > 0 && r < R_PAD && id < downAt.length && now - downAt[id] < 70) pulse(r, 80 - (now - downAt[id]));
                recompute();
            }
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            boolean ok = useBt ? bt.connected : wifiConnected();
            txt.setTextAlign(Paint.Align.LEFT);
            txt.setTextSize(dp(15));
            txt.setTypeface(Typeface.DEFAULT_BOLD);
            fill.setColor(ok ? 0xFF4CD964 : 0xFFFFC94D);
            c.drawCircle(dp(22), dp(27), dp(6), fill);
            txt.setColor(Color.WHITE);
            String st = useBt
                    ? (ok ? "Bluetooth: conectado" : "Bluetooth: aguardando o óculos")
                    : (ok ? "Wi-Fi: conectado" + (rtt >= 0 ? " · " + rtt + " ms" : "") : "Wi-Fi: procurando… código " + code);
            c.drawText(st, dp(36), dp(32), txt);
            txt.setTypeface(Typeface.DEFAULT);
            txt.setTextSize(dp(12));
            txt.setColor(0x99FFFFFF);
            String sub = useBt && !ok ? btStatus : gyro == null && mode == RemoteProto.M_MOUSE ? "Sem giroscópio neste celular: use o Touchpad" : "";
            if (sub.length() > 60) sub = sub.substring(0, 58) + "…";
            c.drawText(sub, dp(36), dp(50), txt);
            txt.setTextAlign(Paint.Align.CENTER);

            int held = ourButtons();
            for (int i = 0; i < rects.size(); i++) {
                RectF r = rects.get(i);
                int id = ids.get(i);
                String[] lb = labels.get(i);
                if (id == R_PAD) {
                    lb = mode == RemoteProto.M_TOUCHPAD ? new String[]{"Arraste = mover a mira", "Toque = clicar · 2 dedos = rolar"}
                            : mode == RemoteProto.M_MOUSE ? new String[]{"Gire o celular = mover a mira", "Toque = clicar · Arraste = rolar"}
                            : new String[]{"Aponte = mirar", "Toque = clicar · Arraste = rolar"};
                } else if (id == R_MODE) {
                    lb = new String[]{"Modo: " + MODE_NAMES[mode] + "   ⇄", ""};
                }
                boolean on = id == R_PAD ? padIds.size() > 0 : id < R_PAD && (held & id) != 0;
                fill.setColor(on ? 0xFF2F8CFF : id == RemoteProto.B_TRIGGER ? 0x332F8CFF : id == R_PAD ? 0x14FFFFFF : id == R_MODE ? 0x262F8CFF : 0x1FFFFFFF);
                c.drawRoundRect(r, dp(18), dp(18), fill);
                stroke.setColor(on ? 0xFF9FD3FF : 0x33FFFFFF);
                stroke.setStrokeWidth(dp(on ? 2 : 1));
                c.drawRoundRect(r, dp(18), dp(18), stroke);
                boolean small = r.height() < dp(90) || id == R_SETUP || id == R_MODE;
                txt.setColor(Color.WHITE);
                txt.setTypeface(Typeface.DEFAULT_BOLD);
                txt.setTextSize(id == R_PAD ? dp(17) : id == R_MODE ? dp(15) : small ? dp(22) : dp(34));
                float cy = r.centerY() + (lb[1].isEmpty() ? txt.getTextSize() * .35f : -dp(2));
                c.drawText(lb[0], r.centerX(), cy, txt);
                if (!lb[1].isEmpty()) {
                    txt.setTypeface(Typeface.DEFAULT);
                    txt.setTextSize(dp(13));
                    txt.setColor(0xB3FFFFFF);
                    c.drawText(lb[1], r.centerX(), cy + dp(22), txt);
                }
            }
            if (padIds.size() > 0) {
                fill.setColor(0x669FD3FF);
                c.drawCircle(dotX, dotY, dp(26), fill);
            }
            txt.setTextSize(dp(12));
            txt.setTypeface(Typeface.DEFAULT);
            txt.setColor(0x80FFFFFF);
            c.drawText("Vol + = clicar  ·  Vol − = segurar  ·  segure ◎ = centralizar", w / 2f, getHeight() - dp(16), txt);
        }
    }
}
