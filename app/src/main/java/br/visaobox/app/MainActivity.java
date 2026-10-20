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

    private static final int REQ_CAMERA = 7, REQ_GALLERY = 8;

    private SharedPreferences prefs;
    private final Config cfg = new Config();
    private HeadTracker head;
    private VrView vr;
    private CameraVision cam;
    private FrameLayout root;
    private SettingsPanel panel;
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
        cfg.load(prefs, "cur_");

        head = new HeadTracker(this);
        vr = new VrView(this, cfg, head, this);
        cam = new CameraVision(this, vr, cfg);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(vr, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button gear = new Button(this);
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
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.END);
        glp.setMargins(dp(10), dp(10), dp(10), dp(10));
        root.addView(gear, glp);
        gear.setOnClickListener(v -> openSettings());

        setContentView(root);
        hideBars();

        cam.initModels();
        if (has(Manifest.permission.CAMERA)) cam.start();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);

        refreshFiles();
        loadGallery();
        android.view.Choreographer.getInstance().postFrameCallback(webTick);
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
        vr.onResume();
        hideBars();
    }

    @Override
    protected void onPause() {
        head.stop();
        vr.onPause();
        cfg.save(prefs, "cur_");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cam.stop();
        io.shutdown();
        for (int i = 0; i < panes.size(); i++) panes.valueAt(i).destroy();
        super.onDestroy();
    }

    /* ================= teclado e controles ================= */

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (panel != null && panel.getParent() != null) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
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
        if (joy && e.getActionMasked() == MotionEvent.ACTION_MOVE && (panel == null || panel.getParent() == null)) {
            final float lx = e.getAxisValue(MotionEvent.AXIS_X), ly = e.getAxisValue(MotionEvent.AXIS_Y);
            final float rx = e.getAxisValue(MotionEvent.AXIS_Z), ry = e.getAxisValue(MotionEvent.AXIS_RZ);
            final float lt = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
            final float rt = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
            final float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            final long t = SystemClock.uptimeMillis();
            vr.onGl(() -> vr.ui.setAxes(lx, ly, rx, ry, lt, rt, hx, hy, t));
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
            String home = id == SpatialUi.YOUTUBE ? "https://m.youtube.com" : "https://www.google.com";
            p = new WebPane(id, this, root, main, home);
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
                int mask = vr.webMask;
                for (int i = 0; i < panes.size(); i++) {
                    WebPane p = panes.valueAt(i);
                    boolean visible = (mask & (p.id == SpatialUi.NAV ? 1 : 2)) != 0;
                    try { p.tick(visible, cfg.webGpu, now); } catch (Throwable ignored) { }
                }
            }
            android.view.Choreographer.getInstance().postFrameCallback(this);
        }
    };

    @Override public void webOpen(int id) { main.post(() -> pane(id).open()); }
    @Override public Bitmap webBitmap(int id) { WebPane p = panes.get(id); return p == null ? null : p.front; }
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

    /* ================= sistema ================= */

    @Override
    public void openSettings() {
        main.post(() -> {
            if (panel == null) panel = new SettingsPanel(this, cfg, this);
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
    @Override public void configChanged() { cfg.save(prefs, "cur_"); }

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
        cfg.copyFrom(new Config());
        vrToast("Configurações padrão restauradas");
    }

    @Override public void setPlacement(int p) { final long t = SystemClock.uptimeMillis(); vr.onGl(() -> vr.ui.setPlacement(p, t)); }
    @Override public void setTracking(boolean on) { final long t = SystemClock.uptimeMillis(); vr.onGl(() -> vr.ui.setTracking(on, t)); }
    @Override public void changed() { cfg.save(prefs, "cur_"); }

    private void vrToast(String s) { vr.onGl(() -> vr.ui.sayNow(s)); android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show(); }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (panel != null && panel.getParent() != null) { closePanel(); return; }
        if (vr.openWindows > 0) { vr.onGl(vr.ui::closeFront); return; }
        super.onBackPressed();
    }
}
