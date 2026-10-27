package br.visaobox.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Liga tudo: tela binocular, câmera, sensores, navegador, galeria, arquivos e painel de ajustes. */
public class MainActivity extends ComponentActivity implements Host, SettingsPanel.Actions {

    private static final int REQ_CAMERA = 7, REQ_GALLERY = 8, REQ_AUDIO = 9, REQ_MEDIA = 10, REQ_VOICE = 11, REQ_WAKE = 12;

    private SharedPreferences prefs;
    private final Config cfg = new Config();
    private HeadTracker head;
    private VrView vr;
    private CameraVision cam;
    private FrameLayout root;
    private SettingsPanel panel;
    private Assistant assistant;
    private RemoteLink remote;
    private SettingsPanel startScreen;
    private boolean started;
    private Button gear;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    // navegadores (um por app: Navegador e YouTube)
    private final android.util.SparseArray<WebPane> panes = new android.util.SparseArray<>();
    private int tickCount = 0;

    // galeria
    private final List<GalleryItem> gallery = new CopyOnWriteArrayList<>();
    private volatile List<File> files = new ArrayList<>();
    private boolean galleryLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("visaobox", MODE_PRIVATE);
        CrashLog.install(this);
        if (prefs.getBoolean("ctlStarting", false))            // o controle fechou ao abrir: volta para a tela normal
            prefs.edit().putBoolean("ctlMode", false).putBoolean("ctlStarting", false).commit();
        final String crash = CrashLog.take(prefs);
        if (crash != null) main.post(() -> showCrash(crash));
        // da última vez este celular era o controle: abre direto no modo controle
        if (crash == null && prefs.getBoolean("ctlMode", false)) {
            startActivity(new android.content.Intent(this, ControllerActivity.class));
            finish();
            return;
        }
        cfg.load(prefs, "cur_");
        remote = new RemoteLink(this, prefs);

        head = new HeadTracker(this);
        head.setCompass(cfg.compass);
        vr = new VrView(this, cfg, head, this);
        vr.ui.remote = remote;
        cam = new CameraVision(this, vr, cfg);
        assistant = new Assistant(this, cfg, new Assistant.Callback() {
            @Override public void onWake() {
                final long t = SystemClock.uptimeMillis();
                vr.onGl(() -> vr.ui.assistantWoke(t));
                main.postDelayed(() -> startVoice(), 350);   // depois do som de chamada
            }
            @Override public boolean micBusy() { return listening || recorder != null; }
            @Override public void info(String msg) { vrToast(msg); }
        });

        WebPane.cssWidth = cfg.webCss;
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        gear = new Button(this);
        gear.setText("⚙");
        gear.setTextColor(Color.WHITE);
        gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        gear.setPadding(0, 0, 0, 0);
        gear.setAlpha(.55f);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x99202230);
        bg.setStroke(dp(1), 0x40FFFFFF);
        gear.setBackground(bg);
        gear.setOnClickListener(v -> openSettings());

        setContentView(root);
        hideBars();

        // tela de início: escolher Controle ou Mãos e ajustar tudo ANTES de ligar câmera e VR (assim não trava)
        if (cfg.showStart) {
            startScreen = new SettingsPanel(this, cfg, this, true);
            root.addView(startScreen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else begin();
    }

    /** "Começar": liga o VR, a câmera, os modelos e o assistente. */
    @Override
    public void begin() {
        if (started) return;
        started = true;
        cfg.save(prefs, "cur_");
        if (startScreen != null) { root.removeView(startScreen); startScreen = null; }
        vr.calibration = false;
        root.addView(vr, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.END);
        glp.setMargins(dp(10), dp(10), dp(10), dp(10));
        root.addView(gear, glp);
        hideBars();
        vr.onResume();

        cam.initModels();
        if (has(Manifest.permission.CAMERA)) { cam.start(); askWake(); }
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);

        refreshFiles();
        loadGallery();
        loadLists();
        if (cfg.rememberLayout) vr.pendingLayout = prefs.getString("layout", "");
        android.view.Choreographer.getInstance().postFrameCallback(webTick);
        if (cfg.remoteCtl) remote.start();
    }

    /** O "ei visão" precisa do microfone: pede uma vez ao abrir o app. */
    private void askWake() {
        if (!cfg.assistant) return;
        if (has(Manifest.permission.RECORD_AUDIO)) assistant.startWake();
        else if (!prefs.getBoolean("askedWake", false)) {
            prefs.edit().putBoolean("askedWake", true).apply();
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_WAKE);
        }
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private boolean has(String perm) { return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED; }

    private String galleryPerm() {
        return Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_IMAGES" : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_CAMERA) {
            if (ok) cam.start();
            else vr.camError = "Permissão da câmera negada. Libere em Configurações > Apps > Visão Box.";
            askWake();
        } else if (code == REQ_WAKE) {
            if (ok) assistant.startWake();
        } else if (code == REQ_MEDIA) {
            mediaLoaded = false;
            loadMedia();
        } else if (code == REQ_VOICE) {
            if (ok) startVoice();
        } else if (code == REQ_AUDIO) {
            beginRecording(ok);
        } else if (code == REQ_GALLERY && ok) {
            galleryLoaded = false;
            loadGallery();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        head.displayRotation = getWindowManager().getDefaultDisplay().getRotation();
        head.start();
        hideBars();
        if (!started) return;
        vr.onResume();
        if (cfg.assistant && has(Manifest.permission.RECORD_AUDIO)) assistant.startWake();
        if (cfg.remoteCtl) remote.start();
    }

    @Override
    protected void onPause() {
        if (remote == null) { super.onPause(); return; }     // virou controle no onCreate
        remote.stop();
        if (recorder != null) stopRecordingMain();          // salva o vídeo se o app for fechado gravando
        head.stop();
        assistant.stopWake();
        vr.weather.stop();
        if (started && cfg.rememberLayout) prefs.edit().putString("layout", vr.ui.layoutCache).apply();
        if (started) vr.onPause();
        cfg.save(prefs, "cur_");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (remote == null) { super.onDestroy(); return; }
        cam.stop();
        io.shutdown();
        for (int i = 0; i < panes.size(); i++) panes.valueAt(i).destroy();
        if (speech != null) speech.destroy();
        assistant.destroy();
        super.onDestroy();
    }

    /* ================= teclado e controles ================= */

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (startScreen != null) {
            // tela de início pelo controle: direcional anda, A escolhe, Start começa
            if (down && code == KeyEvent.KEYCODE_BUTTON_START) { begin(); return true; }
            if (code == KeyEvent.KEYCODE_BUTTON_A) {
                View f = getCurrentFocus();
                if (down && e.getRepeatCount() == 0 && f != null) f.performClick();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (panel != null && panel.getParent() != null) return super.dispatchKeyEvent(e);
        if (vr.calibration) {
            // calibração dentro do óculos: ← → distância dos olhos, ↑ ↓ distorção, A ou B sai
            if (down) switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: cfg.ipdMm = Math.max(50, cfg.ipdMm - .5f); break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: cfg.ipdMm = Math.min(76, cfg.ipdMm + .5f); break;
                case KeyEvent.KEYCODE_DPAD_UP: cfg.k1 = Math.min(.6f, cfg.k1 + .01f); break;
                case KeyEvent.KEYCODE_DPAD_DOWN: cfg.k1 = Math.max(0, cfg.k1 - .01f); break;
                case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
                    vr.calibration = false; cfg.save(prefs, "cur_"); break;
                default: return super.dispatchKeyEvent(e);
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN || e.getAction() == KeyEvent.ACTION_UP) {
            if (SpatialUi.handlesKey(code)) {
                boolean dpadV = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
                if (e.getRepeatCount() == 0 || !down || dpadV) {
                    final long t = SystemClock.uptimeMillis();
                    vr.onGl(() -> vr.ui.onKey(code, down, t));
                }
                return true;
            }
        }
        WebPane fp = vr.webFocusId >= 0 ? panes.get(vr.webFocusId) : null;
        if (fp != null && fp.web != null && code != KeyEvent.KEYCODE_BACK) return fp.web.dispatchKeyEvent(e);
        return super.dispatchKeyEvent(e);
    }

    /** Analógicos, gatilhos e direcional do controle (Xbox e outros controles Bluetooth). */
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        boolean joy = (e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (e.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        if (joy && e.getActionMasked() == MotionEvent.ACTION_MOVE && started && (panel == null || panel.getParent() == null)) {
            final float lx = e.getAxisValue(MotionEvent.AXIS_X), ly = e.getAxisValue(MotionEvent.AXIS_Y);
            final float rx = e.getAxisValue(MotionEvent.AXIS_Z), ry = e.getAxisValue(MotionEvent.AXIS_RZ);
            final float lt = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
            final float rt = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
            final float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            final long t = SystemClock.uptimeMillis();
            // o celular-controle por Bluetooth marca Rx = 100/127 e Ry = −50/127 (assim sabemos que é ele)
            final boolean ours = Math.abs(e.getAxisValue(MotionEvent.AXIS_RX) - BtGamepad.MARK_RX / 127f) < .02f
                    && Math.abs(e.getAxisValue(MotionEvent.AXIS_RY) - BtGamepad.MARK_RY / 127f) < .02f;
            vr.onGl(() -> { vr.ui.padLinear = ours; vr.ui.setAxes(lx, ly, rx, ry, lt, rt, hx, hy, t); });
            return true;
        }
        return super.dispatchGenericMotionEvent(e);
    }

    /* ================= tela cheia ================= */

    @SuppressWarnings("deprecation")
    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    /* ================= navegadores ================= */

    private WebPane pane(int id) {
        WebPane p = panes.get(id);
        if (p == null) {
            String home = id == SpatialUi.YOUTUBE ? "https://m.youtube.com" : null;   // abas novas abrem na página inicial
            p = new WebPane(id, this, main, home, id >= 100 ? this::addHistory : null);
            panes.put(id, p);
        }
        return p;
    }

    /** A cada quadro: copia as páginas visíveis para as janelas (~30 por segundo). */
    private final android.view.Choreographer.FrameCallback webTick = new android.view.Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if ((++tickCount & 1) == 0) {
                long now = SystemClock.uptimeMillis();
                int[] vis = vr.webVisible;
                for (int i = 0; i < panes.size(); i++) {
                    WebPane p = panes.valueAt(i);
                    boolean visible = false;
                    for (int v : vis) if (v == p.id) { visible = true; break; }
                    try { p.tick(visible, now); } catch (Throwable ignored) { }
                }
            }
            android.view.Choreographer.getInstance().postFrameCallback(this);
        }
    };

    @Override public void webOpen(int id) { main.post(() -> pane(id).open()); }
    @Override public Bitmap webBitmap(int id) { WebPane p = panes.get(id); return p == null ? null : p.front; }
    @Override public float[] webCrop(int id) { WebPane p = panes.get(id); return p == null ? new float[]{1, 1} : new float[]{p.cropW, p.cropH}; }
    @Override public String webTitle(int id) { WebPane p = panes.get(id); return p == null ? "" : p.title; }
    @Override public String webUrl(int id) { WebPane p = panes.get(id); return p == null ? "" : p.url; }
    @Override public boolean webInputFocused(int id) { WebPane p = panes.get(id); return p != null && p.input; }
    @Override public void webLoad(int id, String url) { main.post(() -> pane(id).load(url)); }
    @Override public void webBack(int id) { main.post(() -> pane(id).back()); }
    @Override public void webReload(int id) { main.post(() -> pane(id).reload()); }
    @Override public void webTouch(int id, int action, float fx, float fy) { main.post(() -> pane(id).touch(action, fx, fy)); }
    @Override public void webAspect(int id, float aspect) { main.post(() -> pane(id).setAspect(aspect)); }
    @Override public void webInsert(int id, String text) { main.post(() -> pane(id).insert(text)); }
    @Override public void webKey(int id, int key) { main.post(() -> pane(id).key(key)); }

    @Override
    public void webClose(int id) {
        main.post(() -> {
            WebPane p = panes.get(id);
            if (p == null) return;
            panes.remove(id);
            p.destroy();
        });
    }

    @Override public void webHome(int id) { main.post(() -> pane(id).goHome()); }
    @Override public boolean webIsHome(int id) { WebPane p = panes.get(id); return p == null || p.isHome(); }
    @Override public void webDesktop(int id, boolean on) { main.post(() -> pane(id).setDesktop(on)); }
    @Override public boolean webIsDesktop(int id) { WebPane p = panes.get(id); return p != null && p.desktop; }

    /* ---------- favoritos e histórico (guardados no celular) ---------- */

    private final List<String[]> favs = new CopyOnWriteArrayList<>();
    private final List<String[]> hist = new CopyOnWriteArrayList<>();

    private void loadLists() {
        readList("favs", favs);
        readList("hist", hist);
    }

    private void readList(String key, List<String[]> out) {
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONArray e = a.getJSONArray(i);
                out.add(new String[]{e.getString(0), e.getString(1)});
            }
        } catch (Throwable ignored) { }
    }

    private void writeList(String key, List<String[]> list) {
        org.json.JSONArray a = new org.json.JSONArray();
        for (String[] e : list) { org.json.JSONArray x = new org.json.JSONArray(); x.put(e[0]); x.put(e[1]); a.put(x); }
        prefs.edit().putString(key, a.toString()).apply();
    }

    private static String cleanTitle(String t, String url) {
        if (t == null || t.trim().isEmpty() || t.startsWith("http")) return url.replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceFirst("/.*$", "");
        return t.trim();
    }

    private void addHistory(int id, String title, String url) {
        if (url == null || url.startsWith("about:")) return;
        for (String[] e : hist) if (e[1].equals(url)) hist.remove(e);
        hist.add(0, new String[]{cleanTitle(title, url), url});
        while (hist.size() > 30) hist.remove(hist.size() - 1);
        writeList("hist", hist);
    }

    @Override public List<String[]> favorites() { return favs; }
    @Override public List<String[]> history() { return hist; }

    @Override
    public boolean isFavorite(String url) {
        if (url == null) return false;
        for (String[] e : favs) if (e[1].equals(url)) return true;
        return false;
    }

    @Override
    public boolean toggleFavorite(String title, String url) {
        for (String[] e : favs) if (e[1].equals(url)) { favs.remove(e); writeList("favs", favs); return false; }
        favs.add(new String[]{cleanTitle(title, url), url});
        writeList("favs", favs);
        return true;
    }

    @Override public float[] webMetrics(int id) { WebPane p = panes.get(id); return p == null ? null : p.metrics; }
    @Override public void webScrollTo(int id, float frac) { main.post(() -> pane(id).scrollTo(frac)); }

    @Override
    public void webScroll(int id, int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        main.post(() -> pane(id).scroll(dx, dy));
    }

    /* ================= galeria e arquivos ================= */

    private File photoDir() {
        File d = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (d == null) d = new File(getFilesDir(), "Pictures");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private void refreshFiles() {
        File[] fs = photoDir().listFiles();
        List<File> list = fs == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(fs));
        Collections.sort(list, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        files = list;
    }

    private void loadGallery() {
        if (galleryLoaded) return;
        galleryLoaded = true;
        io.execute(() -> {
            List<GalleryItem> out = new ArrayList<>();
            for (File f : files) {
                if (f.getName().endsWith(".mp4")) continue;       // vídeos não entram na grade de fotos
                GalleryItem it = new GalleryItem();
                it.file = f; it.name = f.getName();
                it.thumb = decode(f, null, 256);
                out.add(it);
            }
            if (galleryAllowed()) {
                String[] proj = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME};
                try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                        MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                    int count = 0;
                    while (c != null && c.moveToNext() && count < 60) {
                        long id = c.getLong(0);
                        GalleryItem it = new GalleryItem();
                        it.uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                        it.name = c.getString(1);
                        try {
                            if (Build.VERSION.SDK_INT >= 29) it.thumb = getContentResolver().loadThumbnail(it.uri, new Size(256, 256), null);
                            else it.thumb = decode(null, it.uri, 256);
                        } catch (Throwable ignored) { }
                        if (it.thumb != null) { out.add(it); count++; }
                    }
                } catch (Throwable ignored) { }
            }
            gallery.clear();
            gallery.addAll(out);
        });
    }

    private Bitmap decode(File f, Uri uri, int target) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            if (f != null) BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            else try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= target) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            if (f != null) return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
            try (InputStream in = getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(in, null, o2); }
        } catch (Throwable t) {
            return null;
        }
    }

    @Override public List<GalleryItem> galleryItems() { return gallery; }
    @Override public boolean galleryAllowed() { return has(galleryPerm()); }

    @Override
    public void requestGallery() {
        main.post(() -> requestPermissions(new String[]{galleryPerm()}, REQ_GALLERY));
    }

    @Override
    public void loadLarge(GalleryItem item) {
        if (item.large != null || item.loadingLarge) return;
        item.loadingLarge = true;
        io.execute(() -> item.large = decode(item.file, item.uri, 1400));
    }

    @Override public List<File> listFiles() { return files; }

    @Override
    public void savePhoto(Bitmap ignored) { main.post(() -> cam.takePhoto(this::saveBitmap)); }

    private void saveBitmap(Bitmap copy) {
        io.execute(() -> {
            String name = "VisaoBox_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
            File f = new File(photoDir(), name);
            try (FileOutputStream out = new FileOutputStream(f)) {
                copy.compress(Bitmap.CompressFormat.JPEG, 92, out);
            } catch (Throwable t) {
                vr.error = "Não consegui salvar a foto: " + t.getMessage();
                return;
            }
            GalleryItem it = new GalleryItem();
            it.file = f; it.name = name;
            it.thumb = Bitmap.createScaledBitmap(copy, 256, Math.max(1, 256 * copy.getHeight() / copy.getWidth()), true);
            gallery.add(0, it);
            main.post(this::refreshFiles);
        });
    }

    /* ================= voz ================= */

    private android.speech.SpeechRecognizer speech;
    private volatile boolean listening;
    private volatile String heard = "";

    @Override public boolean isListening() { return listening; }

    /* ================= assistente Visão ================= */

    private Bitmap logoBmp;

    @Override public void speak(String text) {
        if (text == null || text.isEmpty()) { assistant.shutUp(); return; }
        assistant.speak(text);
    }
    @Override public String assistantCaption() { return assistant.caption(); }
    @Override public String assistantChat(String t) {
        String before = cfg.userName;
        String r = Assistant.chat(t, cfg, battery());
        if (!String.valueOf(before).equals(cfg.userName)) cfg.save(prefs, "cur_");
        return r;
    }
    private int logoName = -1;
    @Override public Bitmap logo() {
        if (logoBmp == null || logoName != cfg.assistantName) {
            logoName = cfg.assistantName;
            try (InputStream in = getAssets().open(cfg.assistantName == 1 ? "jarvis_logo.png" : "visao_logo.png")) { logoBmp = BitmapFactory.decodeStream(in); }
            catch (Exception ignored) { }
        }
        return logoBmp;
    }
    @Override public SettingsModel.Env settingsEnv() { return this; }

    /* ================= apps do celular ================= */

    private final List<PhoneApp> phoneApps = new CopyOnWriteArrayList<>();
    private volatile boolean appsLoading, appsLoaded;

    @Override
    public List<PhoneApp> phoneApps() {
        if (!appsLoaded && !appsLoading) {
            appsLoading = true;
            io.execute(() -> {
                try {
                    android.content.pm.PackageManager pm = getPackageManager();
                    android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_MAIN);
                    i.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
                    List<android.content.pm.ResolveInfo> list = pm.queryIntentActivities(i, 0);
                    List<PhoneApp> out = new ArrayList<>();
                    for (android.content.pm.ResolveInfo ri : list) {
                        if (ri.activityInfo == null || getPackageName().equals(ri.activityInfo.packageName)) continue;
                        PhoneApp a = new PhoneApp();
                        a.pkg = ri.activityInfo.packageName;
                        CharSequence l = ri.loadLabel(pm);
                        a.label = l == null ? a.pkg : l.toString();
                        out.add(a);
                    }
                    Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
                    phoneApps.clear();
                    phoneApps.addAll(out);
                    appsLoaded = true;
                    for (PhoneApp a : out) {                        // ícones depois (a lista aparece logo)
                        try { a.icon = drawableToBitmap(pm.getApplicationIcon(a.pkg), 96); } catch (Throwable ignored) { }
                    }
                } catch (Throwable t) {
                    vrToast("Não consegui listar os apps: " + t.getMessage());
                } finally { appsLoading = false; }
            });
        }
        return phoneApps;
    }

    private static Bitmap drawableToBitmap(android.graphics.drawable.Drawable d, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        return b;
    }

    @Override
    public void launchApp(String pkg) {
        main.post(() -> {
            android.content.Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) { vrToast("Esse app não pode ser aberto"); return; }
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Throwable t) { vrToast("Não consegui abrir: " + t.getMessage()); }
        });
    }

    private int battery() {
        try {
            android.os.BatteryManager bm = (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
            return bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Throwable t) { return -1; }
    }

    // ---- ajustes do assistente ----
    @Override public void assistantToggled() {
        main.post(() -> {
            assistant.stopWake();                      // reinicia: o nome (Visão / Jarvis) pode ter mudado
            if (!started || !cfg.assistant) return;
            if (has(Manifest.permission.RECORD_AUDIO)) assistant.startWake();
            else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_WAKE);
        });
    }
    @Override public void calibrate() { vr.calibration = !vr.calibration; }
    @Override public void webSizeChanged() {
        WebPane.cssWidth = cfg.webCss;
        main.post(() -> { for (int i = 0; i < panes.size(); i++) panes.valueAt(i).refreshSize(); });
    }
    @Override public String nextVoice() { String n = assistant.nextVoice(); testVoice(); return n; }
    @Override public String voiceLabel() { return assistant.voiceLabel(); }
    @Override public void testVoice() {
        String nome = cfg.userName == null || cfg.userName.isEmpty() ? "" : ", " + cfg.userName;
        assistant.speak("Olá" + nome + ". Eu sou o " + cfg.assistantLabel() + ". É assim que a minha voz soa.");
    }
    @Override public String voiceText() { return heard; }

    @Override
    public void startVoice() {
        main.post(() -> {
            if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) { vrToast("Reconhecimento de voz indisponível neste celular"); return; }
            if (!has(Manifest.permission.RECORD_AUDIO)) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_VOICE); return; }
            if (listening && speech != null) { speech.stopListening(); return; }
            assistant.pauseNow();                     // o "ei visão" solta o microfone
            assistant.shutUp();
            if (speech == null) {
                speech = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
                speech.setRecognitionListener(new android.speech.RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle b) { listening = true; heard = ""; }
                    @Override public void onBeginningOfSpeech() { }
                    @Override public void onRmsChanged(float v) { }
                    @Override public void onBufferReceived(byte[] b) { }
                    @Override public void onEndOfSpeech() { }
                    @Override public void onEvent(int t, Bundle b) { }
                    @Override public void onError(int e) {
                        listening = false;
                        assistant.resumeSoon();
                        if (e == android.speech.SpeechRecognizer.ERROR_NO_MATCH || e == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                            vrToast("Não ouvi nada — tente de novo");
                    }
                    @Override public void onPartialResults(Bundle b) {
                        java.util.ArrayList<String> r = b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                        if (r != null && !r.isEmpty()) heard = r.get(0);
                    }
                    @Override public void onResults(Bundle b) {
                        listening = false;
                        assistant.resumeSoon();
                        java.util.ArrayList<String> r = b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                        if (r == null || r.isEmpty()) return;
                        final String text = r.get(0);
                        heard = text;
                        vr.onGl(() -> vr.ui.voiceCommand(text, SystemClock.uptimeMillis()));
                    }
                });
            }
            android.content.Intent i = new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
            i.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            listening = true;
            heard = "";
            speech.startListening(i);
        });
    }

    /* ================= mídia do celular ================= */

    private final List<MediaItem> videos = new CopyOnWriteArrayList<>(), songs = new CopyOnWriteArrayList<>();
    private volatile boolean mediaLoaded;

    private String[] mediaPerms() {
        return Build.VERSION.SDK_INT >= 33
                ? new String[]{"android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO"}
                : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    @Override
    public boolean mediaAllowed() {
        for (String p : mediaPerms()) if (!has(p)) return false;
        return true;
    }

    @Override public void requestMedia() { main.post(() -> requestPermissions(mediaPerms(), REQ_MEDIA)); }

    @Override public List<MediaItem> mediaList(int kind) { return kind == 0 ? videos : songs; }

    @Override
    public void loadMedia() {
        if (mediaLoaded || !mediaAllowed()) return;
        mediaLoaded = true;
        io.execute(() -> {
            List<MediaItem> v = new ArrayList<>(), a = new ArrayList<>();
            String[] vp = {MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION};
            try (Cursor c = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vp, null, null,
                    MediaStore.Video.Media.DATE_ADDED + " DESC")) {
                while (c != null && c.moveToNext() && v.size() < 200) {
                    MediaItem it = new MediaItem();
                    it.kind = 0;
                    it.uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0));
                    it.title = c.getString(1) == null ? "Vídeo" : c.getString(1);
                    it.durationMs = c.getLong(2);
                    v.add(it);
                }
            } catch (Throwable ignored) { }
            String[] ap = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION};
            try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ap,
                    MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE + " ASC")) {
                while (c != null && c.moveToNext() && a.size() < 500) {
                    MediaItem it = new MediaItem();
                    it.kind = 1;
                    it.uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0));
                    it.title = c.getString(1) == null ? "Música" : c.getString(1);
                    String ar = c.getString(2);
                    it.artist = ar == null || ar.contains("<unknown>") ? "" : ar;
                    it.durationMs = c.getLong(3);
                    a.add(it);
                }
            } catch (Throwable ignored) { }
            videos.clear(); videos.addAll(v);
            songs.clear(); songs.addAll(a);
            // miniaturas dos vídeos (Android 10+)
            if (Build.VERSION.SDK_INT >= 29) {
                for (MediaItem it : v) {
                    try { it.thumb = getContentResolver().loadThumbnail(it.uri, new Size(256, 144), null); } catch (Throwable ignored) { }
                }
            }
        });
    }

    private static String esc(String t) {
        return t == null ? "" : t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Monta a página do player e toca na janela Player. */
    @Override
    public void playMedia(MediaItem it) {
        String src = WebPane.LOCAL + "media?u=" + Uri.encode(it.uri.toString());
        String css = "html,body{margin:0;height:100%;background:#000;color:#fff;font-family:sans-serif;overflow:hidden}"
                + "video{width:100%;height:100%;object-fit:contain;background:#000}"
                + ".m{display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;gap:18px;"
                + "background:linear-gradient(160deg,#3b1d6e,#0d0f1a)}"
                + ".art{width:38vh;height:38vh;border-radius:24px;background:linear-gradient(135deg,#ff9f68,#e0457b);"
                + "display:flex;align-items:center;justify-content:center;font-size:18vh;box-shadow:0 20px 60px #0008}"
                + "h1{font-size:6vh;margin:0 24px;text-align:center}p{opacity:.7;margin:0;font-size:4vh}audio{width:86%}";
        String body = it.kind == 0
                ? "<video src=\"" + src + "\" controls autoplay playsinline></video>"
                : "<div class=m><div class=art>♪</div><h1>" + esc(it.title) + "</h1><p>" + esc(it.artist)
                  + "</p><audio src=\"" + src + "\" controls autoplay></audio></div>";
        String html = "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<title>" + esc(it.title) + "</title><style>" + css + "</style></head><body>" + body + "</body></html>";
        main.post(() -> pane(SpatialUi.PLAYER).loadHtml(html));
    }

    /* ================= gravação da realidade mista ================= */

    private android.media.MediaRecorder recorder;
    private File recFile;
    private boolean askedAudio;
    private volatile boolean recording;
    private volatile long recStart;

    @Override public boolean isRecording() { return recording; }
    @Override public long recordingSeconds() { return recording ? (SystemClock.uptimeMillis() - recStart) / 1000 : 0; }

    @Override
    public void startRecording() {
        main.post(() -> {
            if (recorder != null) return;
            boolean mic = has(Manifest.permission.RECORD_AUDIO);
            if (!mic && !askedAudio) {
                askedAudio = true;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);  // continua no retorno
                return;
            }
            beginRecording(mic);
        });
    }

    private void beginRecording(boolean mic) {
        if (recorder != null) return;
        if (mic) assistant.pauseNow();
        String name = "VisaoBox_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
        File f = new File(photoDir(), name);
        int[][] sizes = {{1280, 960}, {1280, 720}};           // 4:3 casa com a câmera; 16:9 de reserva
        for (int[] sz : sizes) {
            android.media.MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new android.media.MediaRecorder(this) : newRecorderOld();
            try {
                if (mic) r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
                r.setVideoSource(android.media.MediaRecorder.VideoSource.SURFACE);
                r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
                r.setOutputFile(f.getAbsolutePath());
                r.setVideoEncoder(android.media.MediaRecorder.VideoEncoder.H264);
                r.setVideoSize(sz[0], sz[1]);
                r.setVideoFrameRate(30);
                r.setVideoEncodingBitRate(8_000_000);
                if (mic) {
                    r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
                    r.setAudioSamplingRate(44100);
                    r.setAudioEncodingBitRate(128_000);
                }
                r.prepare();
                android.view.Surface surface = r.getSurface();
                r.start();
                recorder = r;
                recFile = f;
                recStart = SystemClock.uptimeMillis();
                recording = true;
                final int w = sz[0], h = sz[1];
                final long t0 = recStart;
                vr.onGl(() -> { vr.recW = w; vr.recH = h; vr.recStart = t0; vr.recSurface = surface; });
                if (!mic) vrToast("Gravando sem som (microfone não permitido)");
                return;
            } catch (Throwable t) {
                try { r.release(); } catch (Throwable ignored) { }
                f.delete();
            }
        }
        vrToast("Não consegui iniciar a gravação neste aparelho");
    }

    @SuppressWarnings("deprecation")
    private static android.media.MediaRecorder newRecorderOld() { return new android.media.MediaRecorder(); }

    @Override
    public void stopRecording() {
        // primeiro a tela para de desenhar no vídeo, depois o gravador fecha
        vr.onGl(() -> { vr.recSurface = null; main.post(this::stopRecordingMain); });
    }

    private void stopRecordingMain() {
        vr.recSurface = null;
        android.media.MediaRecorder r = recorder;
        recorder = null;
        recording = false;
        if (r == null) return;
        boolean ok = true;
        try { r.stop(); } catch (Throwable t) { ok = false; }
        try { r.release(); } catch (Throwable ignored) { }
        final File f = recFile;
        if (!ok || f == null) { if (f != null) f.delete(); vrToast("A gravação foi curta demais"); return; }
        refreshFiles();
        assistant.resumeSoon();
        io.execute(() -> publishVideo(f));
    }

    /** Copia o vídeo para a galeria do celular (Filmes › Visão Box). */
    private void publishVideo(File f) {
        if (Build.VERSION.SDK_INT < 29) return;              // nos antigos ele fica em Arquivos
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
            v.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Visão Box");
            Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return;
            try (InputStream in = new java.io.FileInputStream(f);
                 java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) return;
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        } catch (Throwable ignored) { }
    }

    /* ================= sistema ================= */

    @Override
    public void openSettings() {
        main.post(() -> {
            if (panel == null) panel = new SettingsPanel(this, cfg, this, false);
            else panel.build();
            if (panel.getParent() == null) {
                int w = Math.round(getResources().getDisplayMetrics().widthPixels * .48f);
                root.addView(panel, new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
            }
        });
    }

    @Override public void startCalibration() { setCalibration(true); openSettings(); }

    @Override
    public void recenter() {
        head.recenter();
        final long t = SystemClock.uptimeMillis();
        vr.onGl(() -> vr.ui.recentered(t));
    }

    @Override public void switchCamera() { main.post(() -> cam.next()); }
    @Override public String cameraLabel() { return cam.label(); }
    @Override public void configChanged() { cfg.save(prefs, "cur_"); head.setCompass(cfg.compass); }

    // ---- painel de ajustes ----
    @Override
    public void closePanel() {
        if (panel != null && panel.getParent() != null) root.removeView(panel);
        vr.calibration = false;
        cfg.save(prefs, "cur_");
        hideBars();
    }

    @Override public void setCalibration(boolean on) { vr.calibration = on; }
    @Override public boolean calibrating() { return vr.calibration; }

    @Override public void saveProfile() { cfg.save(prefs, "prof_"); vrToast("Perfil salvo"); }

    @Override
    public void loadProfile() {
        if (!prefs.getBoolean("prof_exists", false)) { vrToast("Nenhum perfil salvo ainda"); return; }
        cfg.load(prefs, "prof_");
        vrToast("Perfil carregado");
    }

    @Override
    public void resetDefaults() {
        String nome = cfg.userName;
        cfg.copyFrom(new Config());
        cfg.userName = nome;
        vrToast("Configurações padrão restauradas");
    }

    @Override public void setPlacement(int p) { final long t = SystemClock.uptimeMillis(); vr.onGl(() -> vr.ui.setPlacement(p, t)); }
    @Override public void setTracking(boolean on) { final long t = SystemClock.uptimeMillis(); vr.onGl(() -> vr.ui.setTracking(on, t)); }
    @Override public void changed() {
        cfg.save(prefs, "cur_");
        head.setCompass(cfg.compass);
        if (remote != null && started) { if (cfg.remoteCtl) remote.start(); else remote.stop(); }
    }

    @Override public String remoteInfo() { return remote.info(); }

    @Override
    public void becomeController() {
        main.post(() -> {
            cfg.save(prefs, "cur_");
            prefs.edit().putBoolean("ctlMode", true).apply();
            startActivity(new android.content.Intent(this, ControllerActivity.class));
            finish();
        });
    }

    /** Mostra o erro da última vez, com botão para copiar e mandar. */
    private void showCrash(String text) {
        try {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("O app fechou com um erro da última vez")
                    .setMessage("Copie e mande este texto para corrigir:\n\n" + text)
                    .setPositiveButton("Copiar", (d, w) -> {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("erro", text));
                        android.widget.Toast.makeText(this, "Copiado", android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Fechar", null)
                    .show();
        } catch (Throwable ignored) { }
    }

    private void vrToast(String s) { vr.onGl(() -> vr.ui.sayNow(s)); android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show(); }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (panel != null && panel.getParent() != null) { closePanel(); return; }
        if (vr.openWindows > 0) { vr.onGl(vr.ui::closeFront); return; }
        super.onBackPressed();
    }
}
