package br.visaobox.app;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Efeitos de clima por cima do fundo (e por trás das janelas):
 * chuva, tempestade (com raios e trovão), neve, neblina, vaga-lumes, estrelas cadentes e folhas.
 * As partículas ficam presas ao mundo na horizontal: viram junto quando você vira a cabeça.
 */
final class Weather {

    static final int NONE = 0, RAIN = 1, STORM = 2, SNOW = 3, FOG = 4, FIREFLY = 5, METEOR = 6, LEAVES = 7;
    private static final int MAX = 420;

    private final Random rnd = new Random(11);
    private final float[] px = new float[MAX], py = new float[MAX], pz = new float[MAX], pv = new float[MAX], pa = new float[MAX];
    private int kind = -1;
    private float lastYaw;
    private boolean yawInit;
    private float flash;           // brilho do relâmpago (0..1)
    private long nextBolt;
    private Path bolt;
    private float boltX;
    private float meteorT = -1, meteorX, meteorY;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Sound sound = new Sound();

    void stop() { sound.set(0, 0); }

    /**
     * @param halfW meia largura visível (unidades da cena), yaw = guinada da cabeça (rad), f = distância focal.
     */
    void draw(Canvas c, int k, float amt, boolean withSound, float yaw, float f, float halfW, float dt, long now) {
        if (k != kind) { reset(k, halfW); kind = k; }
        sound.set(withSound && (k == RAIN || k == STORM) ? (k == STORM ? .9f : .55f) * amt : 0, k == STORM ? 1 : 0);
        if (k == NONE) return;
        float span = halfW + .15f;
        // vira junto com a cabeça (horizontal), para parecer que está no mundo
        float dyaw = yawInit ? HeadTracker.wrap(yaw - lastYaw) : 0;
        lastYaw = yaw; yawInit = true;
        float shift = -dyaw * f;
        int n = Math.max(8, Math.round(count(k) * amt));
        dt = Math.min(dt, .05f);

        switch (k) {
            case RAIN: case STORM: {
                if (k == STORM) {
                    // céu escuro + relâmpagos
                    p.setShader(null);
                    p.setColor(0x55000814);
                    c.drawRect(-3, -3, 3, 3, p);
                    if (now > nextBolt) {
                        nextBolt = now + 2500 + rnd.nextInt(6000);
                        flash = 1;
                        boltX = (rnd.nextFloat() * 2 - 1) * halfW;
                        bolt = new Path();
                        float x = boltX, y = -.55f;
                        bolt.moveTo(x, y);
                        while (y < .05f) { x += (rnd.nextFloat() - .5f) * .07f; y += .03f + rnd.nextFloat() * .05f; bolt.lineTo(x, y); }
                        sound.thunder(.6f + .4f * amt);
                    }
                    if (flash > 0) {
                        p.setColor(Gfx.a(0xFFDDE8FF, flash * .45f));
                        c.drawRect(-3, -3, 3, 3, p);
                        if (bolt != null && flash > .5f) {
                            p.setStyle(Paint.Style.STROKE);
                            p.setStrokeWidth(.004f);
                            p.setColor(Gfx.a(0xFFFFFFFF, flash));
                            c.drawPath(bolt, p);
                            p.setStyle(Paint.Style.FILL);
                        }
                        flash = Math.max(0, flash - dt * 2.2f);
                    }
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                float wind = k == STORM ? .35f : .12f;
                for (int i = 0; i < n; i++) {
                    float z = pz[i];
                    float sp = (1.3f + 1.4f * z) * (k == STORM ? 1.35f : 1f);
                    py[i] += sp * dt;
                    px[i] += wind * sp * dt * .3f + shift * (.4f + .6f * z);
                    if (py[i] > .6f) { py[i] = -.6f - rnd.nextFloat() * .1f; px[i] = (rnd.nextFloat() * 2 - 1) * span; }
                    wrapX(i, span);
                    float len = .02f + .035f * z;
                    p.setStrokeWidth(.0012f + .0016f * z);
                    p.setColor(Gfx.a(0xFFB8CCE6, .25f + .45f * z));
                    c.drawLine(px[i], py[i], px[i] - wind * len * .3f, py[i] - len, p);
                }
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case SNOW: {
                for (int i = 0; i < n; i++) {
                    float z = pz[i];
                    py[i] += (.06f + .1f * z) * dt;
                    pa[i] += dt * (1 + pv[i]);
                    px[i] += (float) Math.sin(pa[i]) * .02f * dt + shift * (.4f + .6f * z);
                    if (py[i] > .6f) { py[i] = -.6f; px[i] = (rnd.nextFloat() * 2 - 1) * span; }
                    wrapX(i, span);
                    p.setColor(Gfx.a(0xFFFFFFFF, .45f + .5f * z));
                    c.drawCircle(px[i], py[i], .0018f + .0042f * z, p);
                }
                break;
            }
            case FOG: {
                p.setColor(Gfx.a(0xFFB9C2CC, .18f + .3f * amt));
                c.drawRect(-3, -3, 3, 3, p);
                for (int i = 0; i < 9; i++) {
                    px[i] += (.012f + .01f * pz[i]) * dt + shift;
                    wrapX(i, span + .4f);
                    float r = .25f + .25f * pz[i];
                    p.setShader(new RadialGradient(px[i], py[i], r, Gfx.a(0xFFE2E8EE, .35f * amt), 0x00E2E8EE, Shader.TileMode.CLAMP));
                    c.drawCircle(px[i], py[i], r, p);
                }
                p.setShader(null);
                break;
            }
            case FIREFLY: {
                p.setColor(0x40000008);
                c.drawRect(-3, -3, 3, 3, p);
                for (int i = 0; i < n; i++) {
                    pa[i] += dt * (.6f + pv[i]);
                    px[i] += (float) Math.cos(pa[i] * 1.3f) * .03f * dt + shift * (.5f + .5f * pz[i]);
                    py[i] += (float) Math.sin(pa[i]) * .025f * dt;
                    wrapX(i, span);
                    float blink = .5f + .5f * (float) Math.sin(now / 400.0 + i * 1.7);
                    float r = .004f + .004f * pz[i];
                    p.setShader(new RadialGradient(px[i], py[i], r * 4, Gfx.a(0xFFFFF27A, .55f * blink), 0x00FFF27A, Shader.TileMode.CLAMP));
                    c.drawCircle(px[i], py[i], r * 4, p);
                    p.setShader(null);
                    p.setColor(Gfx.a(0xFFFFFDE0, .9f * blink));
                    c.drawCircle(px[i], py[i], r * .6f, p);
                }
                break;
            }
            case METEOR: {
                p.setColor(0x3300020A);
                c.drawRect(-3, -3, 3, 3, p);
                for (int i = 0; i < n; i++) {
                    px[i] += shift * .3f;
                    wrapX(i, span);
                    float tw = .5f + .5f * (float) Math.sin(now / 300.0 + i);
                    p.setColor(Gfx.a(0xFFFFFFFF, (.25f + .5f * pz[i]) * tw));
                    c.drawCircle(px[i], py[i], .0015f + .002f * pz[i], p);
                }
                if (meteorT < 0 && rnd.nextFloat() < dt * (.3f + amt)) {
                    meteorT = 0; meteorX = (rnd.nextFloat() * 2 - 1) * halfW; meteorY = -.45f + rnd.nextFloat() * .2f;
                }
                if (meteorT >= 0) {
                    meteorT += dt;
                    float t = meteorT / .7f;
                    float x = meteorX + t * .45f, y = meteorY + t * .22f;
                    float a = (float) Math.sin(Math.min(1, t) * Math.PI);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeWidth(.003f);
                    p.setColor(Gfx.a(0xFFFFFFFF, a));
                    c.drawLine(x, y, x - .12f, y - .06f, p);
                    p.setStyle(Paint.Style.FILL);
                    if (t >= 1) meteorT = -1;
                }
                break;
            }
            case LEAVES: {
                int[] cols = {0xFFE07A2E, 0xFFC8452B, 0xFFE8B23A, 0xFF9C5A2B};
                for (int i = 0; i < n; i++) {
                    float z = pz[i];
                    py[i] += (.05f + .07f * z) * dt;
                    pa[i] += dt * (1.2f + pv[i]);
                    px[i] += (float) Math.sin(pa[i]) * .05f * dt + shift * (.4f + .6f * z);
                    if (py[i] > .6f) { py[i] = -.6f; px[i] = (rnd.nextFloat() * 2 - 1) * span; }
                    wrapX(i, span);
                    c.save();
                    c.translate(px[i], py[i]);
                    c.rotate((float) Math.toDegrees(pa[i] * 1.5f));
                    float s = .006f + .008f * z;
                    p.setColor(Gfx.a(cols[i % cols.length], .7f + .3f * z));
                    c.drawOval(-s, -s * .45f, s, s * .45f, p);
                    c.restore();
                }
                break;
            }
        }
    }

    private int count(int k) {
        switch (k) {
            case RAIN: return 260;
            case STORM: return 420;
            case SNOW: return 260;
            case FIREFLY: return 45;
            case METEOR: return 160;
            case LEAVES: return 70;
            default: return 9;
        }
    }

    private void wrapX(int i, float span) {
        if (px[i] > span) px[i] -= 2 * span;
        else if (px[i] < -span) px[i] += 2 * span;
    }

    private void reset(int k, float halfW) {
        float span = halfW + .15f;
        for (int i = 0; i < MAX; i++) {
            px[i] = (rnd.nextFloat() * 2 - 1) * span;
            py[i] = k == METEOR ? -.55f + rnd.nextFloat() * .5f : -.6f + rnd.nextFloat() * 1.2f;
            pz[i] = rnd.nextFloat();
            pv[i] = rnd.nextFloat();
            pa[i] = rnd.nextFloat() * 6.28f;
        }
        flash = 0; bolt = null; meteorT = -1;
        nextBolt = android.os.SystemClock.uptimeMillis() + 1500;
    }

    /* ================= som da chuva e trovões (gerado na hora) ================= */

    static final class Sound implements Runnable {
        private volatile float vol, storm;
        private volatile float thunder;
        private Thread thread;
        private AudioTrack track;

        synchronized void set(float v, float s) {
            vol = v; storm = s;
            if (v > 0 && thread == null) { thread = new Thread(this, "som-clima"); thread.start(); }
        }

        void thunder(float a) { thunder = Math.max(thunder, a); }

        @Override public void run() {
            int sr = 22050;
            int buf = Math.max(AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), 4096);
            try {
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(new AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(buf).build();
                track.play();
            } catch (Throwable t) { synchronized (this) { thread = null; } return; }
            short[] out = new short[1024];
            Random r = new Random();
            float lp = 0, lp2 = 0, cur = 0, rum = 0, rumLp = 0, idle = 0;
            while (true) {
                float target = vol;
                if (target <= 0 && cur < .002f) {
                    idle += out.length / (float) sr;
                    if (idle > 2) break;                       // 2 s em silêncio: desliga
                } else idle = 0;
                for (int i = 0; i < out.length; i++) {
                    cur += (target - cur) * .0005f;
                    float w = r.nextFloat() * 2 - 1;
                    lp += (w - lp) * .35f;                     // chiado da chuva
                    lp2 += (lp - lp2) * .08f;
                    float rain = (lp - lp2 * .6f) * cur * .5f;
                    if (r.nextFloat() < .0015f * cur) rain += (r.nextFloat() - .5f) * .6f * cur;   // pingos
                    float th = thunder;
                    if (th > .001f) {
                        rumLp += (w - rumLp) * .01f;           // ronco grave
                        rum = rumLp * th * 6f;
                        thunder = th * .99995f;
                    } else rum *= .999f;
                    float v = Math.max(-1, Math.min(1, rain + rum));
                    out[i] = (short) (v * 30000);
                }
                try { track.write(out, 0, out.length); } catch (Throwable t) { break; }
            }
            try { track.stop(); track.release(); } catch (Throwable ignored) { }
            synchronized (this) { thread = null; }
        }
    }
}
