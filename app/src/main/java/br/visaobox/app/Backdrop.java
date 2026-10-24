package br.visaobox.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fundo em imagem, ao redor da pessoa (360°).
 *  - "preset:N": paisagens prontas (assets/pano_N.jpg), já em 360°.
 *  - foto da galeria: vira um panorama juntando a foto com o espelho dela (sem emenda).
 * A imagem é carregada fora da tela, sem travar.
 */
final class Backdrop {

    static final String[] PRESETS = {"Aurora", "Rio na floresta", "Cidade à noite", "Montanhas", "Fundo do mar"};
    static final int W = 2048;                     // largura do panorama = 360°

    private final Context ctx;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile String loadedKey = "", loadingKey = "";
    private volatile Bitmap pano;
    private volatile int topColor, bottomColor;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Bitmap[] thumbs = new Bitmap[PRESETS.length];

    Backdrop(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    /** Miniatura de uma paisagem pronta (para o app Fundos). */
    Bitmap thumb(int i) {
        if (i < 0 || i >= thumbs.length) return null;
        if (thumbs[i] == null) {
            try (InputStream in = ctx.getAssets().open("pano_" + i + ".jpg")) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 8;
                thumbs[i] = BitmapFactory.decodeStream(in, null, o);
            } catch (Exception ignored) { }
        }
        return thumbs[i];
    }

    /** Desenha o fundo: guinada/elevação da cabeça em radianos, f = distância focal da cena. */
    boolean draw(Canvas c, String key, float yaw, float pitch, float f, float halfW) {
        if (key == null) key = "";
        if (!key.equals(loadedKey) && !key.equals(loadingKey)) load(key);
        Bitmap b = pano;
        if (b == null) return false;
        float ppr = b.getWidth() / (float) (Math.PI * 2);        // pixels por radiano
        float k = f / ppr;                                        // pixels -> unidades da cena
        float top = f * (pitch - (b.getHeight() / 2f) / ppr);    // horizonte no meio da imagem
        float bot = top + b.getHeight() * k;
        paint.setColor(topColor);
        c.drawRect(-3, -3, 3, top + .002f, paint);
        paint.setColor(bottomColor);
        c.drawRect(-3, bot - .002f, 3, 3, paint);
        float wScene = b.getWidth() * k;
        float yawN = (float) (((yaw % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2));
        float x0 = -yawN * f;                                     // posição do começo da imagem
        while (x0 > -halfW) x0 -= wScene;
        for (float x = x0; x < halfW; x += wScene)
            c.drawBitmap(b, null, new RectF(x, top, x + wScene + .001f, bot), paint);
        return true;
    }

    private void load(String key) {
        loadingKey = key;
        io.execute(() -> {
            Bitmap out = null;
            try {
                if (key.startsWith("preset:")) {
                    int i = Integer.parseInt(key.substring(7));
                    try (InputStream in = ctx.getAssets().open("pano_" + Math.max(0, Math.min(PRESETS.length - 1, i)) + ".jpg")) {
                        out = BitmapFactory.decodeStream(in);
                    }
                } else if (!key.isEmpty()) {
                    out = photoPano(key);
                }
            } catch (Throwable ignored) { }
            if (out != null) {
                topColor = out.getPixel(out.getWidth() / 3, 0) | 0xFF000000;
                bottomColor = out.getPixel(out.getWidth() / 3, out.getHeight() - 1) | 0xFF000000;
            }
            Bitmap old = pano;
            pano = out;
            loadedKey = key;
            loadingKey = "";
            if (old != null && old != out) old.recycle();
        });
    }

    /** Foto comum -> panorama: [foto | foto espelhada], cada uma cobrindo 180°. */
    private Bitmap photoPano(String key) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        decode(key, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= W / 2) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        Bitmap src = decode(key, o);
        if (src == null) return null;
        int half = W / 2;
        int h = Math.max(2, Math.round(half * src.getHeight() / (float) src.getWidth()));
        Bitmap out = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(src, null, new RectF(0, 0, half, h), p);
        Matrix m = new Matrix();
        m.setScale(-half / (float) src.getWidth(), h / (float) src.getHeight());
        m.postTranslate(W, 0);
        c.drawBitmap(src, m, p);
        src.recycle();
        return out;
    }

    private Bitmap decode(String key, BitmapFactory.Options o) throws Exception {
        if (key.startsWith("content:")) {
            try (InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(key))) { return BitmapFactory.decodeStream(in, null, o); }
        }
        return BitmapFactory.decodeFile(key, o);
    }
}
