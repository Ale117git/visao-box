package br.visaobox.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.hardware.HardwareBuffer;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.nio.ByteBuffer;

/**
 * Uma página do navegador (aba, YouTube ou Player).
 *
 * Como funciona (versão 6):
 * cada página roda numa TELA VIRTUAL de verdade do Android (VirtualDisplay + Presentation).
 * Para o navegador, é como se ele estivesse numa tela de verdade, do tamanho exato da janela:
 * ele desenha tudo normalmente, rola sem deixar partes brancas e toca vídeos.
 * Cada quadro pronto chega por um ImageReader:
 *  - Android 10+: vira um Bitmap de hardware, sem cópia nenhuma;
 *  - mais antigos: é copiado para um Bitmap comum (~15 por segundo).
 * Quando a página não muda, não chega quadro nenhum (não gasta nada).
 */
final class WebPane {

    /** Largura de referência para a rolagem (as contas da interface usam esta medida). */
    static final int WIDTH = 960;
    /** Largura das páginas em px de CSS (menor = letras maiores). Vem dos ajustes. */
    static volatile float cssWidth = 900f;

    interface PageListener { void onPage(int id, String title, String url); }

    static final String LOCAL = "https://visaobox.local/";
    static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static HandlerThread frameThread;
    private static Handler frames;

    private static synchronized Handler frameHandler() {
        if (frames == null) {
            frameThread = new HandlerThread("paginas");
            frameThread.start();
            frames = new Handler(frameThread.getLooper());
        }
        return frames;
    }

    final int id;
    private final PageListener listener;
    boolean desktop;
    private final Activity act;
    private final Handler main;
    private final String homeUrl;

    WebView web;
    volatile Bitmap front;
    volatile String title = "", url = "";
    volatile boolean input;
    /** Parte útil do quadro (a página ocupa o canto de cima à esquerda da tela virtual). */
    volatile float cropW = 1f, cropH = 1f;
    private String pending;
    private long lastFocus;

    // tela virtual (tamanho fixo; a página muda de tamanho dentro dela, sem refazer nada)
    static final int DISP = 1280;
    private VirtualDisplay vd;
    private Presentation pres;
    private FrameLayout holder;
    private volatile ImageReader reader;
    private int builtDpi;
    private float aspect = 16f / 10f;
    private boolean rebuildQueued, lost;

    // quadros (Android 10+: hardware; antes: cópia)
    private Image cur, prev;
    private Bitmap swA, swB, swRow;
    private long lastSw;

    /** homeUrl = null: abre na página inicial do Visão Box (favoritos e recentes). */
    WebPane(int id, Activity act, Handler main, String homeUrl, PageListener listener) {
        this.id = id; this.act = act; this.main = main; this.homeUrl = homeUrl; this.listener = listener;
    }

    boolean isHome() { return url.isEmpty() || url.startsWith("about:blank"); }

    void goHome() { if (web != null) web.loadUrl("about:blank"); url = ""; title = ""; }

    void setDesktop(boolean on) {
        desktop = on;
        if (web == null) return;
        web.getSettings().setUserAgentString(on ? DESKTOP_UA : null);
        if (!isHome()) web.reload();
    }

    /** Toca uma página gerada aqui (player de vídeo e música). */
    void loadHtml(String html) {
        if (web == null) open();
        if (web != null) web.loadDataWithBaseURL(LOCAL + "player", html, "text/html", "utf-8", null);
    }

    /* ================= página ================= */

    /**
     * Densidade da tela virtual. A página "acha" que a tela tem cssWidth pontos de largura:
     * 1280 px ÷ (dpi/160). Antes ela era montada como num celular pequeno (parecia com zoom);
     * agora tem largura de tablet, e dá para ver bem mais coisas.
     */
    private static int dpiFor(float css) { return Math.max(80, Math.round(160f * DISP / Gfx.clamp(css, 480, 1400))); }

    void open() {
        if (web != null) return;
        buildDisplay();
        if (pres == null) return;
        createWeb(pres.getContext());
        String u = pending != null ? pending : homeUrl;
        pending = null;
        if (u != null) web.loadUrl(u);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createWeb(Context ctx) {
        // criada com o "contexto" da tela virtual: assim usa a densidade dela
        web = new WebView(ctx);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        web.setBackgroundColor(Color.WHITE);
        web.setVerticalScrollBarEnabled(false);       // a janela já tem a barra dela
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
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
        holder.addView(web, pageParams());
    }

    void load(String u) {
        if (web == null) { pending = u; open(); }
        else web.loadUrl(u);
    }

    /* ================= tela virtual ================= */

    /** Tamanho da página dentro da tela virtual, pela proporção da janela. */
    private FrameLayout.LayoutParams pageParams() {
        int w = DISP, h = Math.round(DISP / aspect);
        if (h > DISP) { h = DISP; w = Math.round(DISP * aspect); }
        w = Math.max(320, w) & ~1; h = Math.max(240, h) & ~1;
        cropW = w / (float) DISP; cropH = h / (float) DISP;
        return new FrameLayout.LayoutParams(w, h, android.view.Gravity.TOP | android.view.Gravity.START);
    }

    private void buildDisplay() {
        teardownDisplay();
        builtDpi = dpiFor(cssWidth);
        try {
            reader = newReader(DISP, DISP);
            DisplayManager dm = (DisplayManager) act.getSystemService(Context.DISPLAY_SERVICE);
            vd = dm.createVirtualDisplay("visaobox-pagina-" + id, DISP, DISP, builtDpi, reader.getSurface(), 0);
            pres = new Presentation(act, vd.getDisplay());
            Window win = pres.getWindow();
            if (win != null) {
                // não rouba o foco do app (o controle continua falando com o VR)
                win.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
                win.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            }
            holder = new FrameLayout(pres.getContext());
            pres.setContentView(holder);
            lost = false;
            pres.setOnDismissListener(d -> { if (pres == d && web != null) { lost = true; queueRebuild(); } });
            pres.show();
        } catch (Throwable t) {
            teardownDisplay();
            title = "Erro na página: " + t.getMessage();
        }
    }

    private void teardownDisplay() {
        Presentation p = pres;
        pres = null;
        holder = null;
        if (p != null) { try { p.dismiss(); } catch (Throwable ignored) { } }
        if (vd != null) { try { vd.release(); } catch (Throwable ignored) { } vd = null; }
        final ImageReader r = reader;
        reader = null;
        if (r != null) frameHandler().post(() -> {          // fecha na thread dos quadros (sem corrida)
            try { if (prev != null) prev.close(); } catch (Throwable ignored) { }
            try { if (cur != null) cur.close(); } catch (Throwable ignored) { }
            prev = null; cur = null;
            try { r.close(); } catch (Throwable ignored) { }
        });
    }

    /** Refaz tudo (página nova na mesma tela de endereço): usado ao mudar o tamanho das páginas. */
    private void queueRebuild() {
        if (rebuildQueued) return;
        rebuildQueued = true;
        main.postDelayed(() -> {
            rebuildQueued = false;
            if (web == null) return;
            if (pres != null && !lost && builtDpi == dpiFor(cssWidth)) return;
            String u = web.getUrl();
            boolean wasHome = isHome();
            WebView old = web;
            web = null;
            try { old.stopLoading(); old.destroy(); } catch (Throwable ignored) { }
            buildDisplay();
            if (pres == null) return;
            createWeb(pres.getContext());
            if (!wasHome && u != null && !u.startsWith("data:")) web.loadUrl(u);
            else if (homeUrl != null && !wasHome) web.loadUrl(homeUrl);
        }, 300);
    }

    void setAspect(float a) {
        float na = Gfx.clamp(a, .5f, 3f);
        if (Math.abs(na - aspect) / aspect < .02f) return;
        aspect = na;
        if (web != null) web.setLayoutParams(pageParams());     // só muda o tamanho da página (rápido)
        else cropW = cropH = 1;
    }

    /** Ajustes mudaram o tamanho das páginas: refaz a tela com a nova densidade. */
    void refreshSize() { if (web != null) queueRebuild(); }

    @SuppressLint("WrongConstant")
    private ImageReader newReader(int w, int h) {
        ImageReader r;
        if (Build.VERSION.SDK_INT >= 29)
            r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 4,
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
        else r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        r.setOnImageAvailableListener(this::onImage, frameHandler());
        return r;
    }

    /** Chega um quadro novo da página (thread dos quadros). */
    private void onImage(ImageReader r) {
        if (r != reader) {                                   // leitor antigo: só descarta
            try { Image i = r.acquireLatestImage(); if (i != null) i.close(); } catch (Throwable ignored) { }
            return;
        }
        Image img;
        try { img = r.acquireLatestImage(); } catch (Throwable t) { return; }
        if (img == null) return;
        if (Build.VERSION.SDK_INT >= 29) {
            Bitmap b = null;
            try {
                HardwareBuffer hb = img.getHardwareBuffer();
                if (hb != null) {
                    b = Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB));
                    hb.close();
                }
            } catch (Throwable ignored) { }
            if (b == null) { copyFrame(img); img.close(); return; }
            if (prev != null) prev.close();                  // guarda 2 quadros: a tela pode estar desenhando o anterior
            prev = cur;
            cur = img;
            front = b;
        } else {
            long now = SystemClock.uptimeMillis();
            if (now - lastSw >= 66) { lastSw = now; copyFrame(img); }
            img.close();
        }
    }

    /** Cópia por software (aparelhos antigos). */
    private void copyFrame(Image img) {
        try {
            int w = img.getWidth(), h = img.getHeight();
            Image.Plane pl = img.getPlanes()[0];
            ByteBuffer buf = pl.getBuffer();
            int rowPx = pl.getRowStride() / Math.max(1, pl.getPixelStride());
            if (swB == null || swB.getWidth() != w || swB.getHeight() != h) swB = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            buf.rewind();
            if (rowPx == w) swB.copyPixelsFromBuffer(buf);
            else {
                if (swRow == null || swRow.getWidth() != rowPx || swRow.getHeight() != h) swRow = Bitmap.createBitmap(rowPx, h, Bitmap.Config.ARGB_8888);
                swRow.copyPixelsFromBuffer(buf);
                new Canvas(swB).drawBitmap(swRow, new Rect(0, 0, w, h), new Rect(0, 0, w, h), null);
            }
            Bitmap t = swA;
            swA = swB;
            front = swA;
            swB = t;
        } catch (Throwable ignored) { }
    }

    /** Chamado ~30 vezes por segundo (thread principal): endereço, campo de texto e barra de rolagem. */
    void tick(boolean visible, long now) {
        if (web == null) return;
        String u = web.getUrl();
        url = u == null ? "" : u;
        if (pres == null && !rebuildQueued) queueRebuild();
        if (!visible) return;
        if (now - lastFocus > 350) {
            lastFocus = now;
            web.evaluateJavascript("(function(){var e=document.activeElement;if(!e)return false;var t=(e.tagName||'').toUpperCase();"
                    + "if(t=='TEXTAREA'||e.isContentEditable)return true;if(t!='INPUT')return false;"
                    + "var y=(e.type||'text').toLowerCase();return ['text','search','email','url','tel','password','number',''].indexOf(y)>=0;})()",
                    v -> input = "true".equals(v));
        }
        pollMetrics(now);
    }

    /* ================= arquivos do celular (player) ================= */

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

    /* ================= toque e rolagem ================= */

    private long evClock;
    private long touchDown;

    /** Horário do evento sempre crescente (eventos "voltando no tempo" são descartados pela página). */
    private long clock(long now) { evClock = Math.max(now, evClock + 1); return evClock; }

    private void send(int action, float x, float y, long down, long time) {
        MotionEvent ev = MotionEvent.obtain(down, time, action, x, y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(ev);
        ev.recycle();
    }

    /** A página acha que a janela dela está em foco (assim os campos de texto aceitam o teclado do VR). */
    private void focusPage() {
        try { web.requestFocus(); web.onWindowFocusChanged(true); } catch (Throwable ignored) { }
    }

    void touch(int action, float fx, float fy) {
        if (web == null || web.getWidth() == 0) return;
        if (dragging) finishDrag(SystemClock.uptimeMillis());
        long now = clock(SystemClock.uptimeMillis());
        if (action == MotionEvent.ACTION_DOWN) { touchDown = now; focusPage(); }
        float x = Gfx.clamp(fx, 0, 1) * web.getWidth(), y = Gfx.clamp(fy, 0, 1) * web.getHeight();
        send(action, x, y, touchDown, now);
    }

    // rolagem pelo controle/mãos: um "dedo virtual" arrasta a página (funciona em qualquer site)
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

    /** Solta o dedo cancelando o gesto: a página para onde está, sem sair rolando sozinha. */
    private void finishDrag(long now) {
        send(MotionEvent.ACTION_CANCEL, dragX, dragY, dragDown, clock(now));
        dragging = false;
    }

    /** dx, dy na medida de referência (WIDTH px de largura). */
    void scroll(int dx0, int dy0) {
        if (web == null || web.getWidth() == 0) return;
        float k = web.getWidth() / (float) WIDTH;
        float dx = dx0 * k, dy = dy0 * k;
        long now = SystemClock.uptimeMillis();
        float w = web.getWidth(), hh = web.getHeight();
        float nx = dragX - dx, ny = dragY - dy;              // o dedo anda ao contrário da rolagem
        boolean out = nx < w * .06f || nx > w * .94f || ny < hh * .06f || ny > hh * .94f;
        if (dragging && out) finishDrag(now);
        if (!dragging) {
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

    /* ================= barra de rolagem ================= */

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

    /* ================= teclado ================= */

    void insert(String text) {
        if (web == null) return;
        focusPage();
        String q = org.json.JSONObject.quote(text);
        web.evaluateJavascript("(function(){var e=document.activeElement;if(!e)return;"
                + "if(!document.execCommand('insertText',false," + q + ")){"
                + "if('value' in e){var s=e.selectionStart||e.value.length;e.value=e.value.slice(0,s)+" + q
                + "+e.value.slice(e.selectionEnd||s);e.selectionStart=e.selectionEnd=s+" + text.length() + ";"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));}}})()", null);
    }

    void key(int key) {
        if (web == null) return;
        focusPage();
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
        WebView w = web;
        web = null;
        teardownDisplay();
        if (w != null) { try { w.stopLoading(); w.destroy(); } catch (Throwable ignored) { } }
        front = null;
    }
}
