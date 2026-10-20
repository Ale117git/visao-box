package br.visaobox.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Ajudantes de desenho. As coordenadas são da cena: a altura do olho vale 1. */
final class Gfx {
    Canvas c;
    float textScale = 1f;

    /** Rótulos e textos pequenos crescem com o ajuste; títulos grandes (como o relógio) ficam iguais. */
    private float ts(float size) { return size < .04f ? Math.min(size * textScale, .05f) : size; }
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint grad = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private final Paint.FontMetrics fm = new Paint.FontMetrics();
    private final LinearGradient rim = new LinearGradient(0, 0, 0, 1,
            new int[]{0x66FFFFFF, 0x0DFFFFFF, 0x26FFFFFF}, new float[]{0, .45f, 1}, Shader.TileMode.CLAMP);
    private final android.graphics.Matrix lm = new android.graphics.Matrix();
    private final RectF tmp = new RectF();

    /** Sombra suave barata (3 camadas), sem desfoque — rápida na placa de vídeo. */
    void softShadow(RectF r, float rad) {
        for (int i = 0; i < 3; i++) {
            float e = .005f * (i + 1);
            tmp.set(r.left - e, r.top - e + .01f, r.right + e, r.bottom + e + .012f);
            fill.setColor(0x16000000);
            c.drawRoundRect(tmp, rad + e, rad + e, fill);
        }
    }

    Gfx() {
        fill.setStyle(Paint.Style.FILL);
        grad.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setSubpixelText(true);
    }

    static int a(int color, float alpha) {
        int al = Math.max(0, Math.min(255, Math.round(alpha * ((color >>> 24) & 0xFF))));
        return (color & 0x00FFFFFF) | (al << 24);
    }

    static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    void txt(String s, float x, float y, float size, int color, Paint.Align align, Typeface tf) {
        if (s == null) return;
        c.save();
        c.translate(x, y);
        float k = ts(size) / 100f;
        c.scale(k, k);
        text.setTypeface(tf);
        text.setTextSize(100);
        text.setTextAlign(align);
        text.setColor(color);
        text.getFontMetrics(fm);
        c.drawText(s, 0, -(fm.ascent + fm.descent) / 2, text);
        c.restore();
    }

    float width(String s, float size, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(100);
        return text.measureText(s) * ts(size) / 100f;
    }

    String fit(String s, float size, float maxW, Typeface tf) {
        if (s == null) return "";
        if (width(s, size, tf) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && width(t + "…", size, tf) > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    /** Painel de vidro: sombra suave, fundo translúcido e borda iluminada em cima. */
    void glass(RectF r, float rad, boolean hl, boolean withShadow) {
        if (withShadow) softShadow(r, rad);
        fill.setColor(hl ? 0xB83E4152 : 0xA01C1E28);
        c.drawRoundRect(r, rad, rad, fill);
        lm.setScale(1, r.height());
        lm.postTranslate(0, r.top);
        rim.setLocalMatrix(lm);
        stroke.setShader(rim);
        stroke.setStrokeWidth(.0024f);
        stroke.setColor(Color.WHITE);
        c.drawRoundRect(r, rad, rad, stroke);
        stroke.setShader(null);
    }

    void circle(float x, float y, float r, int color) {
        fill.setColor(color);
        c.drawCircle(x, y, r, fill);
    }

    void ring(float x, float y, float r, float w, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(w);
        c.drawCircle(x, y, r, stroke);
    }

    void line(float x1, float y1, float x2, float y2, float w, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(w);
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    void clipRound(RectF r, float rad) {
        Path p = new Path();
        p.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(p);
    }

    /** Desenha o bitmap cobrindo o retângulo (corta o excesso, sem esticar). */
    void cover(Bitmap f, RectF dst, Paint p) {
        if (f == null || f.isRecycled()) return;
        float vw = f.getWidth(), vh = f.getHeight(), ta = dst.width() / dst.height(), va = vw / vh;
        Rect src;
        if (va > ta) { int sw = Math.round(vh * ta); int sx = Math.round((vw - sw) / 2); src = new Rect(sx, 0, sx + sw, (int) vh); }
        else { int sh = Math.round(vw / ta); int sy = Math.round((vh - sh) / 2); src = new Rect(0, sy, (int) vw, sy + sh); }
        c.drawBitmap(f, src, dst, p);
    }

    /** Desenha o bitmap inteiro dentro do retângulo (sem cortar). */
    void contain(Bitmap f, RectF dst, Paint p) {
        if (f == null || f.isRecycled()) return;
        float s = Math.min(dst.width() / f.getWidth(), dst.height() / f.getHeight());
        float w = f.getWidth() * s, h = f.getHeight() * s;
        RectF r = new RectF(dst.centerX() - w / 2, dst.centerY() - h / 2, dst.centerX() + w / 2, dst.centerY() + h / 2);
        c.drawBitmap(f, null, r, p);
    }
}
