package br.visaobox.app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Modo BLUETOOTH: este celular se apresenta como um controle de videogame de verdade (HID).
 * O celular do óculos enxerga um gamepad comum — o mesmo caminho do controle de Xbox, sem Wi-Fi.
 *
 * Precisa de Android 9 ou mais novo, e o fabricante precisa ter deixado o recurso ligado.
 */
@SuppressLint("MissingPermission")
final class BtGamepad {

    interface Listener { void onStatus(String text, boolean connected); }

    // botões HID (Button n → bit n−1); o Linux/Android os lê como A, B, C, X, Y, Z, L1, R1, L2, R2, Select, Start, Mode, L3, R3
    static final int A = 1, B = 1 << 1, X = 1 << 3, Y = 1 << 4, L2 = 1 << 8, SELECT = 1 << 10, L3 = 1 << 13, R3 = 1 << 14;
    /** Valores fixos nos eixos Rx / Ry: o óculos reconhece que é o celular-controle e usa a mira direta. */
    static final int MARK_RX = 100, MARK_RY = -50;

    /** Gamepad: 16 botões + X, Y, Z, Rz (analógicos) + Rx, Ry (marca). Report ID 1. */
    static final byte[] DESCRIPTOR = {
            0x05, 0x01,                    // Usage Page (Generic Desktop)
            0x09, 0x05,                    // Usage (Game Pad)
            (byte) 0xA1, 0x01,             // Collection (Application)
            (byte) 0x85, 0x01,             //   Report ID (1)
            0x05, 0x09,                    //   Usage Page (Button)
            0x19, 0x01,                    //   Usage Minimum (1)
            0x29, 0x10,                    //   Usage Maximum (16)
            0x15, 0x00,                    //   Logical Minimum (0)
            0x25, 0x01,                    //   Logical Maximum (1)
            0x75, 0x01,                    //   Report Size (1)
            (byte) 0x95, 0x10,             //   Report Count (16)
            (byte) 0x81, 0x02,             //   Input (Data, Variable, Absolute)
            0x05, 0x01,                    //   Usage Page (Generic Desktop)
            0x15, (byte) 0x81,             //   Logical Minimum (−127)
            0x25, 0x7F,                    //   Logical Maximum (127)
            0x75, 0x08,                    //   Report Size (8)
            (byte) 0x95, 0x06,             //   Report Count (6)
            0x09, 0x30,                    //   Usage (X)
            0x09, 0x31,                    //   Usage (Y)
            0x09, 0x32,                    //   Usage (Z)
            0x09, 0x35,                    //   Usage (Rz)
            0x09, 0x33,                    //   Usage (Rx)
            0x09, 0x34,                    //   Usage (Ry)
            (byte) 0x81, 0x02,             //   Input (Data, Variable, Absolute)
            (byte) 0xC0                    // End Collection
    };

    /** Monta o pacote: 2 bytes de botões + 6 eixos (−127..127). */
    static byte[] report(int buttons, float lx, float ly, float rx, float ry) {
        return new byte[]{
                (byte) (buttons & 0xFF), (byte) ((buttons >> 8) & 0xFF),
                axis(lx), axis(ly), axis(rx), axis(ry), (byte) MARK_RX, (byte) MARK_RY};
    }

    static byte axis(float v) { return (byte) Math.round(Math.max(-1f, Math.min(1f, v)) * 127); }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final Executor exec = Executors.newSingleThreadExecutor();
    private BluetoothAdapter adapter;
    private volatile BluetoothHidDevice hid;
    private volatile BluetoothDevice host;
    volatile boolean registered, connected;
    private byte[] lastSent;
    private long lastSentAt;

    BtGamepad(Context ctx, SharedPreferences prefs, Listener l) {
        this.ctx = ctx.getApplicationContext(); this.prefs = prefs; listener = l;
    }

    static boolean supported() { return android.os.Build.VERSION.SDK_INT >= 28; }

    BluetoothAdapter adapter() {
        if (adapter == null) {
            BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
            adapter = bm != null ? bm.getAdapter() : null;
        }
        return adapter;
    }

    String name() {
        try { BluetoothAdapter a = adapter(); return a != null && a.getName() != null ? a.getName() : "este celular"; }
        catch (Throwable t) { return "este celular"; }
    }

    void start() {
        try { startInner(); } catch (Throwable t) { listener.onStatus("Não deu para ligar o modo Bluetooth neste celular. Use o Wi-Fi.", false); }
    }

    private void startInner() {
        if (!supported()) { listener.onStatus("O modo Bluetooth precisa de Android 9 ou mais novo. Use o Wi-Fi.", false); return; }
        BluetoothAdapter a = adapter();
        if (a == null) { listener.onStatus("Este celular não tem Bluetooth.", false); return; }
        if (!a.isEnabled()) { listener.onStatus("Ligue o Bluetooth.", false); return; }
        if (hid != null) { register(); return; }
        listener.onStatus("Preparando o Bluetooth…", false);
        boolean ok = a.getProfileProxy(ctx, new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile != BluetoothProfile.HID_DEVICE) return;
                hid = (BluetoothHidDevice) proxy;
                register();
            }
            @Override public void onServiceDisconnected(int profile) {
                if (profile != BluetoothProfile.HID_DEVICE) return;
                hid = null; registered = false; connected = false;
                listener.onStatus("Bluetooth desligado.", false);
            }
        }, BluetoothProfile.HID_DEVICE);
        if (!ok) listener.onStatus("Este celular não deixa virar controle Bluetooth. Use o Wi-Fi.", false);
    }

    private void register() {
        BluetoothHidDevice h = hid;
        if (h == null || registered) return;
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "Visão Controle", "Controle do Visão Box", "Visão Box",
                (byte) (BluetoothHidDevice.SUBCLASS1_NONE | BluetoothHidDevice.SUBCLASS2_GAMEPAD), DESCRIPTOR);
        boolean ok;
        try {
            ok = h.registerApp(sdp, null, null, exec, new BluetoothHidDevice.Callback() {
                @Override public void onAppStatusChanged(BluetoothDevice plugged, boolean reg) {
                    registered = reg;
                    if (!reg) { connected = false; listener.onStatus("Controle Bluetooth desligado.", false); return; }
                    listener.onStatus("Pronto. No celular do óculos: Configurações → Bluetooth → parear \"" + name() + "\".", false);
                    BluetoothDevice d = plugged != null ? plugged : saved();
                    if (d != null) try { h.connect(d); } catch (Throwable ignored) { }
                }
                @Override public void onConnectionStateChanged(BluetoothDevice d, int state) {
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        host = d; connected = true;
                        prefs.edit().putString("btHost", d.getAddress()).apply();
                        listener.onStatus("Conectado por Bluetooth" + (d.getName() != null ? " a " + d.getName() : ""), true);
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED && (host == null || host.equals(d))) {
                        connected = false; host = null;
                        listener.onStatus("Desconectado. Aguardando o celular do óculos…", false);
                    }
                }
            });
        } catch (Throwable t) { ok = false; }
        if (!ok) listener.onStatus("Este celular não deixa virar controle Bluetooth (o fabricante bloqueou). Use o Wi-Fi.", false);
    }

    private BluetoothDevice saved() {
        String addr = prefs.getString("btHost", null);
        BluetoothAdapter a = adapter();
        if (addr == null || a == null) return null;
        try {
            List<BluetoothDevice> c = hid != null ? hid.getConnectedDevices() : null;
            if (c != null && !c.isEmpty()) return c.get(0);
            return a.getRemoteDevice(addr);
        } catch (Throwable t) { return null; }
    }

    /** Manda o estado (só quando muda, e a cada 100 ms para manter vivo). */
    void send(int buttons, float lx, float ly, float rx, float ry, long now) {
        BluetoothHidDevice h = hid; BluetoothDevice d = host;
        if (h == null || d == null || !connected) return;
        byte[] r = report(buttons, lx, ly, rx, ry);
        if (lastSent != null && java.util.Arrays.equals(r, lastSent) && now - lastSentAt < 100) return;
        try { h.sendReport(d, 1, r); lastSent = r; lastSentAt = now; } catch (Throwable ignored) { }
    }

    void stop() {
        BluetoothHidDevice h = hid;
        try { if (h != null && registered) h.unregisterApp(); } catch (Throwable ignored) { }
        try { if (h != null && adapter != null) adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, h); } catch (Throwable ignored) { }
        hid = null; registered = false; connected = false; host = null;
    }
}
