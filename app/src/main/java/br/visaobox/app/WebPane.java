package br.visaobox.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * Um navegador (WebView) cuja imagem vai para uma janela do VR.
 *  - Android 10+: a página é desenhada pela placa de vídeo direto num buffer (ImageReader) e vira um
 *    Bitmap de hardware, sem cópia. Pega vídeos (YouTube) e fica em resolução cheia.
 *  - Mais antigos ou modo compatível: cópia por software, como antes (vídeos podem ficar pretos).
 */
final class WebPane {

    static final int WIDTH = 960;     // menor largura = letras maiores dentro da janela

    final int id;
    private final Activity act;
    private final FrameLayout root;
    private final Handler main;
    private final String homeUrl;

    WebView web;
    private int h = 800;
    volatile Bitmap front;
    volatile String title = "", url = "";
    volatile boolean input;
    private String pending;
    private long downTime, lastFocus, lastSw;
    private boolean gpuMode;

    // captura pela placa de vídeo
    private ImageReader reader;
    private Image cur, prev;
    private int rw, rh;
    private int blankFrames;

    // captura por software
    private Bitmap swA, swB;

    WebPane(int id, Activity act, FrameLayout root, Handler main, String homeUrl) {
        this.id = id; this.act = act; this.root = root; this.main = main; this.homeUrl = homeUrl;
    }

    static boolean gpuSupported() { return Build.VERSION.SDK_INT >= 29; }

    @SuppressLint("SetJavaScriptEnabled")
    void open() {
        if (web != null) return;
        web = new WebView(act);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(Color.WHITE);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView v, String t) { title = t == null ? "" : t; }
        });
        // fica atrás da tela VR; a imagem dele é levada para a janela
        root.addView(web, 0, new FrameLayout.LayoutParams(WIDTH, h));
        web.loadUrl(pending != null ? pending : homeUrl);
        pending = null;
    }

    void load(String u) {
        if (web == null) { pending = u; open(); }
        else web.loadUrl(u);
    }

    void setAspect(float aspect) {
        int nh = Math.round(WIDTH / Gfx.clamp(aspect, .5f, 3f));
        if (Math.abs(nh - h) < 16) return;
        h = nh;
        if (web != null) {
            ViewGroup.LayoutParams lp = web.getLayoutParams();
            lp.width = WIDTH; lp.height = h;
            web.setLayoutParams(lp);
        }
    }

    /** Chamado a cada quadro da tela (thread principal). */
    void tick(boolean visible, boolean wantGpu, long now) {
        if (web == null) return;
        String u = web.getUrl();
        url = u == null ? "" : u;
        if (now - lastFocus > 350) {
            lastFocus = now;
            web.evaluateJavascript("(function(){var e=document.activeElement;if(!e)return false;var t=(e.tagName||'').toUpperCase();"
                    + "if(t=='TEXTAREA'||e.isContentEditable)return true;if(t!='INPUT')return false;"
                    + "var y=(e.type||'text').toLowerCase();return ['text','search','email','url','tel','password','number',''].indexOf(y)>=0;})()",
                    v -> input = "true".equals(v));
        }
        boolean gpu = wantGpu && gpuSupported();
        if (gpu != gpuMode) {
            gpuMode = gpu;
            web.setLayerType(gpu ? View.LAYER_TYPE_NONE : View.LAYER_TYPE_SOFTWARE, null);
            if (!gpu) closeReader();
        }
        if (!visible || web.getWidth() == 0 || web.getHeight() == 0) return;
        if (gpuMode) captureGpu();
        else if (now - lastSw > 140) { lastSw = now; captureSoftware(); }
    }

    private void captureSoftware() {
        int w = web.getWidth(), hh = web.getHeight();
        if (swB == null || swB.getWidth() != w || swB.getHeight() != hh) swB = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(swB);
        c.drawColor(Color.WHITE);
        web.draw(c);
        Bitmap t = swA;
        swA = swB;
        front = swA;
        swB = (t != null && t.getWidth() == w && t.getHeight() == hh) ? t : null;
    }

    @SuppressLint("WrongConstant")
    private void captureGpu() {
        int w = web.getWidth(), hh = web.getHeight();
        try {
            if (reader == null || rw != w || rh != hh) {
                closeReader();
                reader = ImageReader.newInstance(w, hh, PixelFormat.RGBA_8888, 4,
                        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
                reader.setOnImageAvailableListener(this::onImage, main);
                rw = w; rh = hh;
            }
            Surface s = reader.getSurface();
            Canvas c = s.lockHardwareCanvas();
            try {
                c.drawColor(Color.WHITE);
                web.draw(c);
            } finally {
                s.unlockCanvasAndPost(c);
            }
        } catch (Throwable t) {
            // sem suporte neste aparelho: volta para a cópia por software
            closeReader();
            gpuMode = false;
            web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    private void onImage(ImageReader r) {
        Image img;
        try { img = r.acquireLatestImage(); } catch (Throwable t) { return; }
        if (img == null) return;
        Bitmap b = null;
        try {
            HardwareBuffer hb = img.getHardwareBuffer();
            if (hb != null) {
                b = Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB));
                hb.close();
            }
        } catch (Throwable ignored) { }
        if (b == null) { img.close(); return; }
        if (prev != null) prev.close();
        prev = cur;
        cur = img;
        front = b;
    }

    private void closeReader() {
        try { if (prev != null) prev.close(); } catch (Throwable ignored) { }
        try { if (cur != null) cur.close(); } catch (Throwable ignored) { }
        prev = null; cur = null;
        try { if (reader != null) reader.close(); } catch (Throwable ignored) { }
        reader = null;
    }

    /* ---------------- entrada ---------------- */

    void touch(int action, float fx, float fy) {
        if (web == null) return;
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = now;
        float x = Gfx.clamp(fx, 0, 1) * web.getWidth(), y = Gfx.clamp(fy, 0, 1) * web.getHeight();
        MotionEvent ev = MotionEvent.obtain(downTime, now, action, x, y, 0);
        web.dispatchTouchEvent(ev);
        ev.recycle();
    }

    // rolagem: um "dedo virtual" arrasta a página (funciona em qualquer site, inclusive no YouTube)
    private boolean dragging;
    private float dragX, dragY;
    private long dragDown, lastDragMove;
    private final Runnable endDrag = new Runnable() {
        @Override public void run() {
            if (!dragging) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastDragMove < 150) { main.postDelayed(this, 60); return; }
            finishDrag(now);
        }
    };

    private void send(int action, float x, float y, long down, long time) {
        MotionEvent ev = MotionEvent.obtain(down, time, action, x, y, 0);
        web.dispatchTouchEvent(ev);
        ev.recycle();
    }

    /** Solta o dedo parado (sem "arremesso"), para a página não sair rolando sozinha. */
    private void finishDrag(long now) {
        send(MotionEvent.ACTION_MOVE, dragX, dragY, dragDown, now + 100);
        send(MotionEvent.ACTION_UP, dragX, dragY, dragDown, now + 200);
        dragging = false;
    }

    void scroll(int dx, int dy) {
        if (web == null || web.getWidth() == 0) return;
        long now = SystemClock.uptimeMillis();
        float w = web.getWidth(), hh = web.getHeight();
        if (!dragging) {
            dragX = w / 2; dragY = hh / 2;
            dragDown = now;
            send(MotionEvent.ACTION_DOWN, dragX, dragY, dragDown, now);
            dragging = true;
        }
        // o dedo anda ao contrário da rolagem
        float nx = dragX - dx, ny = dragY - dy;
        if (nx < w * .08f || nx > w * .92f || ny < hh * .08f || ny > hh * .92f) {
            // chegou na borda: solta e começa de novo no meio
            finishDrag(now);
            dragX = w / 2; dragY = hh / 2;
            dragDown = now + 210;
            send(MotionEvent.ACTION_DOWN, dragX, dragY, dragDown, dragDown);
            dragging = true;
            nx = dragX - dx; ny = dragY - dy;
            dragX = nx; dragY = ny;
            send(MotionEvent.ACTION_MOVE, dragX, dragY, dragDown, dragDown + 16);
        } else {
            dragX = nx; dragY = ny;
            send(MotionEvent.ACTION_MOVE, dragX, dragY, dragDown, now);
        }
        lastDragMove = now;
        main.removeCallbacks(endDrag);
        main.postDelayed(endDrag, 160);
    }

    void back() { if (web != null && web.canGoBack()) web.goBack(); }

    void reload() { if (web != null) web.reload(); }

    void insert(String text) {
        if (web == null) return;
        String q = org.json.JSONObject.quote(text);
        web.evaluateJavascript("(function(){var e=document.activeElement;if(!e)return;"
                + "if(!document.execCommand('insertText',false," + q + ")){"
                + "if('value' in e){var s=e.selectionStart||e.value.length;e.value=e.value.slice(0,s)+" + q
                + "+e.value.slice(e.selectionEnd||s);e.selectionStart=e.selectionEnd=s+" + text.length() + ";"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));}}})()", null);
    }

    void key(int key) {
        if (web == null) return;
        if (key == Host.KEY_DEL) {
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

    void destroy() {
        closeReader();
        if (web != null) web.destroy();
    }
}
