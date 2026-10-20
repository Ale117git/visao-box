package br.visaobox.app;

import android.graphics.Bitmap;

/** Ponte para o motor nativo em C++ (app/src/main/cpp/vrcore.cpp). */
final class NativeVr {

    static final boolean loaded;

    static {
        boolean ok;
        try { System.loadLibrary("vrcore"); ok = true; }
        catch (Throwable t) { ok = false; }
        loaded = ok;
    }

    private NativeVr() { }

    // composição (thread do OpenGL)
    static native boolean init();
    static native String error();
    static native void setMesh(int w, int h, int eyes, float eyeW, float eyeH, float cx0, float cx1, float cy,
                               float k1, float k2, boolean dist);
    static native boolean uploadCamera(Bitmap bitmap);
    static native void uploadMask(byte[] data, int w, int h);
    static native void composite(int uiTex, float[] uiMat, int backTex, float[] backMat,
                                 int bgMode, boolean useBack, int seg, float[] params);

    // rastreamento da cabeça (thread própria a 200 Hz)
    static native boolean headStart(int displayRotation);
    static native void headStop();
    static native void headRecenter();
    static native boolean headGet(float[] out);
}
