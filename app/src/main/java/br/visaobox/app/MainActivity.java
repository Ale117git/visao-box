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
    private static final int WEB_W = 1280;

    private SharedPreferences prefs;
    private final Config cfg = new Config();
    private HeadTracker head;
    private VrView vr;
    private CameraVision cam;
    private FrameLayout root;
    private SettingsPanel panel;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    // navegador
    private WebView web;
    private int webH = 800;
    private volatile Bitmap webFront;
    private Bitmap webBack;
    private String webTitle = "";
    private long webDownTime = 0;
    private String pendingUrl;
    private volatile boolean webInput = false;
    private long lastFocusCheck = 0;

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
        main.postDelayed(this::captureWeb, 150);
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
        hideBars();
    }

    @Override
    protected void onPause() {
        head.stop();
        cfg.save(prefs, "cur_");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cam.stop();
        io.shutdown();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /* ================= teclado e controles ================= */

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (panel != null && panel.getParent() != null) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (e.getAction() == KeyEvent.ACTION_DOWN || e.getAction() == KeyEvent.ACTION_UP) {
            if (e.getRepeatCount() == 0 || !down) {
                if (vr.ui.onKey(code, down, SystemClock.uptimeMillis())) return true;
            } else if (isConfirmKey(code)) return true;
        }
        if (web != null && vr.ui.webFocused() && code != KeyEvent.KEYCODE_BACK) return web.dispatchKeyEvent(e);
        return super.dispatchKeyEvent(e);
    }

    private static boolean isConfirmKey(int code) {
        return code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_BUTTON_A
                || code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN;
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

    /* ================= navegador ================= */

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void webOpen() {
        if (web != null) return;
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(Color.WHITE);
        web.setLayerType(View.LAYER_TYPE_SOFTWARE, null); // permite copiar a página para a janela VR
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView v, String title) { webTitle = title == null ? "" : title; }
        });
        // fica atrás da tela VR; a imagem dele é copiada para a janela do navegador
        root.addView(web, 0, new FrameLayout.LayoutParams(WEB_W, webH));
        web.loadUrl(pendingUrl != null ? pendingUrl : "https://www.google.com");
        pendingUrl = null;
    }

    private void captureWeb() {
        try {
            if (web != null && web.getWidth() > 0 && web.getHeight() > 0) {
                int w = web.getWidth(), h = web.getHeight();
                if (webBack == null || webBack.getWidth() != w || webBack.getHeight() != h)
                    webBack = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(webBack);
                c.drawColor(Color.WHITE);
                web.draw(c);
                Bitmap t = webFront;
                webFront = webBack;
                webBack = (t != null && t.getWidth() == w && t.getHeight() == h) ? t : null;
                long now = SystemClock.uptimeMillis();
                if (now - lastFocusCheck > 350) {
                    lastFocusCheck = now;
                    web.evaluateJavascript("(function(){var e=document.activeElement;if(!e)return false;var t=(e.tagName||'').toUpperCase();"
                            + "if(t=='TEXTAREA'||e.isContentEditable)return true;if(t!='INPUT')return false;"
                            + "var y=(e.type||'text').toLowerCase();return ['text','search','email','url','tel','password','number',''].indexOf(y)>=0;})()",
                            v -> webInput = "true".equals(v));
                }
            }
        } catch (Throwable ignored) { }
        main.postDelayed(this::captureWeb, 110);
    }

    @Override public Bitmap webBitmap() { return webFront; }
    @Override public String webTitle() { return webTitle; }

    @Override
    public void webLoad(String url) {
        if (web == null) { pendingUrl = url; webOpen(); }
        else web.loadUrl(url);
    }

    @Override public String webUrl() { return web == null ? "" : (web.getUrl() == null ? "" : web.getUrl()); }

    @Override public boolean webInputFocused() { return web != null && webInput; }

    @Override
    public void webInsert(String text) {
        if (web == null) return;
        web.evaluateJavascript("(function(){var e=document.activeElement;if(!e)return;"
                + "if(!document.execCommand('insertText',false," + org.json.JSONObject.quote(text) + ")){"
                + "if('value' in e){var s=e.selectionStart||e.value.length;e.value=e.value.slice(0,s)+" + org.json.JSONObject.quote(text)
                + "+e.value.slice(e.selectionEnd||s);e.selectionStart=e.selectionEnd=s+" + text.length() + ";"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));}}})()", null);
    }

    @Override
    public void webKey(int key) {
        if (web == null) return;
        if (key == KEY_DEL) {
            web.evaluateJavascript("(function(){var e=document.activeElement;if(!e)return;"
                    + "if(!document.execCommand('delete',false)){if('value' in e){var s=e.selectionStart;var f=e.selectionEnd;"
                    + "if(s===f&&s>0)s--;e.value=e.value.slice(0,s)+e.value.slice(f);e.selectionStart=e.selectionEnd=s;"
                    + "e.dispatchEvent(new Event('input',{bubbles:true}));}}})()", null);
        } else {
            web.evaluateJavascript("(function(){var e=document.activeElement;if(!e)return;"
                    + "var o={key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true};"
                    + "var ok=e.dispatchEvent(new KeyboardEvent('keydown',o));e.dispatchEvent(new KeyboardEvent('keypress',o));"
                    + "e.dispatchEvent(new KeyboardEvent('keyup',o));"
                    + "if(ok&&(e.tagName||'').toUpperCase()=='TEXTAREA'){document.execCommand('insertText',false,'\\n');return;}"
                    + "if(ok&&e.form){if(e.form.requestSubmit)e.form.requestSubmit();else e.form.submit();}})()", null);
        }
    }

    @Override public void webBack() { if (web != null && web.canGoBack()) web.goBack(); }
    @Override public void webReload() { if (web != null) web.reload(); }

    @Override
    public void webTouch(int action, float fx, float fy) {
        if (web == null) return;
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) webDownTime = now;
        float x = Gfx.clamp(fx, 0, 1) * web.getWidth(), y = Gfx.clamp(fy, 0, 1) * web.getHeight();
        MotionEvent ev = MotionEvent.obtain(webDownTime, now, action, x, y, 0);
        web.dispatchTouchEvent(ev);
        ev.recycle();
    }

    @Override
    public void webAspect(float aspect) {
        int h = Math.round(WEB_W / Gfx.clamp(aspect, .5f, 3f));
        if (Math.abs(h - webH) < 16) return;
        webH = h;
        if (web != null) {
            ViewGroup.LayoutParams lp = web.getLayoutParams();
            lp.width = WEB_W; lp.height = webH;
            web.setLayoutParams(lp);
        }
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
    public void savePhoto(Bitmap b) {
        final Bitmap copy = b.copy(Bitmap.Config.ARGB_8888, false);
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
        vr.ui.recentered(SystemClock.uptimeMillis());
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

    @Override public void setPlacement(int p) { vr.ui.setPlacement(p, SystemClock.uptimeMillis()); }
    @Override public void setTracking(boolean on) { vr.ui.setTracking(on, SystemClock.uptimeMillis()); }
    @Override public void changed() { cfg.save(prefs, "cur_"); }

    private void vrToast(String s) { vr.ui.sayNow(s); android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show(); }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (panel != null && panel.getParent() != null) { closePanel(); return; }
        if (vr.ui.closeFront()) return;
        super.onBackPressed();
    }
}
