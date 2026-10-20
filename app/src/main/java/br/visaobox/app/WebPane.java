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
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
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

    interface PageListener { void onPage(int id, String title, String url); }

    static final String LOCAL = "https://visaobox.local/";
    static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    final int id;
    private final PageListener listener;
    boolean desktop;
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

    /** homeUrl = null: abre na página inicial do Visão Box (favoritos e recentes). */
    WebPane(int id, Activity act, FrameLayout root, Handler main, String homeUrl, PageListener listener) {
        this.id = id; this.act = act; this.root = root; this.main = main; this.homeUrl = homeUrl; this.listener = listener;
    }

    boolean isHome() { return url.isEmpty() || url.startsWith("about:blank"); }

    void goHome() { if (web != null) web.loadUrl("about:blank"); url = ""; title = ""; }

    void setDesktop(boolean on) {
        desktop = on;
        if (web == null) return;
        WebSettings s = web.getSettings();
        s.setUserAgentString(on ? DESKTOP_UA : null);
        if (!isHome()) web.reload();
    }

    /** Toca uma página gerada aqui (player de vídeo e música). */
    void loadHtml(String html) {
        if (web == null) open();
        web.loadDataWithBaseURL(LOCAL + "player", html, "text/html", "utf-8", null);
    }

    /** Entrega arquivos do celular (content://) para o player, com suporte a avançar/voltar o vídeo. */
    private WebResourceResponse media(Uri uri, java.util.Map<String, String> headers) {
        try {
            AssetFileDescriptor afd = act.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (afd == null) return null;
            long len = afd.getLength();
            java.io.InputStream in = afd.createInputStream();
            String mime = act.getContentResolver().getType(uri);
            if (mime == null) mime = "video/mp4";
            java.util.Map<String, String> out = new java.util.HashMap<>();
            out.put("Accept-Ranges", "bytes");
            out.put("Access-Control-Allow-Origin", "*");
            String range = null;
            if (headers != null) for (java.util.Map.Entry<String, String> e : headers.entrySet())
                if ("range".equalsIgnoreCase(e.getKey())) range = e.getValue();
            if (range != null && len > 0 && range.startsWith("bytes=")) {
                String[] p = range.substring(6).split("-");
                long start = p.length > 0 && !p[0].isEmpty() ? Long.parseLong(p[0].trim()) : 0;
                long end = p.length > 1 && !p[1].isEmpty() ? Long.parseLong(p[1].trim()) : len - 1;
                end = Math.min(end, len - 1);
                long skipped = 0;
                while (skipped < start) { long k = in.skip(start - skipped); if (k <= 0) break; skipped += k; }
                out.put("Content-Range", "bytes " + start + "-" + end + "/" + len);
                out.put("Content-Length", String.valueOf(end - start + 1));
                return new WebResourceResponse(mime, null, 206, "Partial Content", out, in);
            }
            if (len > 0) out.put("Content-Length", String.valueOf(len));
            return new WebResourceResponse(mime, null, 200, "OK", out, in);
        } catch (Throwable t) {
            return null;
        }
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
        if (desktop) s.setUserAgentString(DESKTOP_UA);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String u) {
                if (listener != null && u != null && u.startsWith("http") && !u.startsWith(LOCAL)) listener.onPage(id, v.getTitle(), u);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (u != null && "visaobox.local".equals(u.getHost()) && "/media".equals(u.getPath())) {
                    String q = u.getQueryParameter("u");
                    if (q != null) return media(Uri.parse(q), r.getRequestHeaders());
                }
                return null;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView v, String t) { title = t == null ? "" : t; }
        });
        // fica atrás da tela VR; a imagem dele é levada para a janela
        root.addView(web, 0, new FrameLayout.LayoutParams(WIDTH, h));
        if (pending != null) web.loadUrl(pending);
        else if (homeUrl != null) web.loadUrl(homeUrl);
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
        pollMetrics(now);
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

    private long evClock;

    /** Horário do evento sempre crescente (eventos "voltando no tempo" eram descartados pela página). */
    private long clock(long now) { evClock = Math.max(now, evClock + 1); return evClock; }

    private void send(int action, float x, float y, long down, long time) {
        MotionEvent ev = MotionEvent.obtain(down, time, action, x, y, 0);
        web.dispatchTouchEvent(ev);
        ev.recycle();
    }

    /** Solta o dedo cancelando o gesto: a página para onde está, sem sair rolando sozinha. */
    private void finishDrag(long now) {
        send(MotionEvent.ACTION_CANCEL, dragX, dragY, dragDown, clock(now));
        dragging = false;
    }

    void scroll(int dx, int dy) {
        if (web == null || web.getWidth() == 0) return;
        long now = SystemClock.uptimeMillis();
        float w = web.getWidth(), hh = web.getHeight();
        float nx = dragX - dx, ny = dragY - dy;          // o dedo anda ao contrário da rolagem
        boolean out = nx < w * .06f || nx > w * .94f || ny < hh * .06f || ny > hh * .94f;
        if (dragging && out) finishDrag(now);
        if (!dragging) {
            // começa do lado oposto ao movimento, para ter bastante espaço antes de recomeçar
            dragX = dx > 0 ? w * .85f : dx < 0 ? w * .15f : w / 2;
            dragY = dy > 0 ? hh * .88f : dy < 0 ? hh * .12f : hh / 2;
            dragDown = clock(now);
            send(MotionEvent.ACTION_DOWN, dragX, dragY, dragDown, dragDown);
            dragging = true;
            nx = dragX - dx; ny = dragY - dy;
        }
        dragX = Gfx.clamp(nx, 1, w - 1);
        dragY = Gfx.clamp(ny, 1, hh - 1);
        send(MotionEvent.ACTION_MOVE, dragX, dragY, dragDown, clock(now));
        lastDragMove = now;
        main.removeCallbacks(endDrag);
        main.postDelayed(endDrag, 160);
    }

    /* ---------------- barra de rolagem ---------------- */

    /** [posição, altura total, altura visível] da parte da página que rola (em px da página), ou null. */
    volatile float[] metrics;
    private long lastMetrics;

    // acha o que realmente rola: a página inteira ou uma caixa interna (comum no YouTube)
    private static final String FIND_SCROLLER =
            "function vbSc(){var el=document.elementFromPoint(innerWidth/2,innerHeight/2);"
            + "while(el&&el!==document.body&&el!==document.documentElement){var s=getComputedStyle(el);"
            + "if((s.overflowY=='auto'||s.overflowY=='scroll')&&el.scrollHeight>el.clientHeight+20)return el;el=el.parentElement;}"
            + "return document.scrollingElement||document.documentElement;}"
            + "function vbIsDoc(e){return e===document.scrollingElement||e===document.documentElement||e===document.body;}";

    private void pollMetrics(long now) {
        if (now - lastMetrics < 250) return;
        lastMetrics = now;
        web.evaluateJavascript("(function(){" + FIND_SCROLLER
                + "var e=vbSc();var d=vbIsDoc(e);var t=d?(window.scrollY||e.scrollTop):e.scrollTop;"
                + "var h=d?innerHeight:e.clientHeight;return t+','+e.scrollHeight+','+h;})()", v -> {
            try {
                String[] p = v.replace("\"", "").split(",");
                float t = Float.parseFloat(p[0]), sh = Float.parseFloat(p[1]), vh = Float.parseFloat(p[2]);
                metrics = sh > vh + 4 ? new float[]{t, sh, vh} : null;
            } catch (Throwable e) { metrics = null; }
        });
    }

    /** Leva a página para uma posição (0 = topo, 1 = fim). */
    void scrollTo(float frac) {
        if (web == null) return;
        float f = Gfx.clamp(frac, 0, 1);
        web.evaluateJavascript("(function(){" + FIND_SCROLLER
                + "var e=vbSc();var d=vbIsDoc(e);var h=d?innerHeight:e.clientHeight;var t=" + f + "*(e.scrollHeight-h);"
                + "if(d)window.scrollTo(0,t);else e.scrollTop=t;})()", null);
        float[] m = metrics;
        if (m != null) metrics = new float[]{f * (m[1] - m[2]), m[1], m[2]};
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
