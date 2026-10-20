package br.visaobox.app;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

/** Sons curtos gerados na hora (sem arquivos de áudio). */
final class Synth {
    static final int SR = 22050;
    static final double[] NOTE_F = {261.63, 293.66, 329.63, 392.0, 440.0, 523.25};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final short[][] notes = new short[6][];
    private final short[] click, hover, shutter, open;
    private final short[][] pops = new short[3][];
    private int popIdx = 0;
    boolean enabled = true;

    Synth() {
        for (int i = 0; i < 6; i++) notes[i] = make(NOTE_F[i], .6, 1, .35, 0);
        click = make(1100, .05, 0, .15, 0);
        hover = make(1800, .025, 0, .05, 0);
        shutter = make(1500, .09, 2, .1, 300);
        open = make(520, .16, 0, .14, 880);
        for (int i = 0; i < 3; i++) pops[i] = make(500 + i * 150, .12, 1, .3, 1400);
    }

    private static short[] make(double f, double dur, int type, double vol, double f2) {
        int len = (int) (SR * dur);
        short[] b = new short[len];
        double ph = 0;
        for (int i = 0; i < len; i++) {
            double t = i / (double) SR;
            double fr = f2 > 0 ? f * Math.pow(f2 / f, t / dur) : f;
            ph += 2 * Math.PI * fr / SR;
            double v;
            if (type == 0) v = Math.sin(ph);
            else if (type == 1) v = 2 / Math.PI * Math.asin(Math.sin(ph));
            else v = Math.sin(ph) > 0 ? 1 : -1;
            double env = Math.min(1, t / .01) * Math.exp(-5 * t / dur);
            b[i] = (short) (v * env * vol * 32767);
        }
        return b;
    }

    @SuppressWarnings("deprecation")
    private void play(short[] b) {
        if (!enabled) return;
        try {
            final AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, SR, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, b.length * 2, AudioTrack.MODE_STATIC);
            at.write(b, 0, b.length);
            at.play();
            handler.postDelayed(() -> { try { at.release(); } catch (Exception ignored) { } }, b.length * 1000L / SR + 300);
        } catch (Exception ignored) { }
    }

    void note(int i) { play(notes[Math.max(0, Math.min(5, i))]); }
    void click() { play(click); }
    void hover() { play(hover); }
    void shutter() { play(shutter); }
    void open() { play(open); }
    void pop() { play(pops[popIdx++ % 3]); }
}
