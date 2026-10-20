package br.visaobox.app;

import android.content.SharedPreferences;

/** Todas as configurações do sistema (ópticas, conforto, passthrough, modos e dock). */
final class Config {

    // ---- Modos ----
    static final int HUD = 0, ESPACIAL = 1, CINEMA = 2;
    static final int BG_CAMERA = 0, BG_VIRTUAL = 1, BG_ESCURO = 2;
    static final int SEG_OFF = 0, SEG_RECORTE = 1, SEG_SILHUETA = 2, SEG_CONTORNO = 3;

    // ---- Ajustes ópticos ----
    boolean stereo = true;          // 2 olhos (VR box) ou tela única
    float ipdMm = 63f;              // distância interpupilar
    float eyeScale = 0.96f;         // tamanho de cada imagem
    float fovDeg = 90f;             // campo de visão vertical simulado
    boolean distortion = true;      // correção de lente
    float k1 = 0.22f, k2 = 0.10f;   // coeficientes de distorção (barril)
    float alignXmm = 0f, alignYmm = 0f; // correção de alinhamento
    float vignette = 0.45f;         // escurecimento das bordas
    float resScale = 0.7f;          // resolução de renderização (desempenho x nitidez)
    float stereo3d = 1f;            // intensidade da paralaxe

    // ---- Conforto ----
    float brightness = 0f, contrast = 1f, saturation = 1f;
    float uiScale = 1f;
    float minDist = 0.8f;           // distância mínima das janelas (m)
    float animSpeed = 1f;
    float headSens = 1f;
    float headSmooth = 0.5f;

    // ---- Passthrough ----
    int background = BG_CAMERA;
    float camBrightness = 0f, camContrast = 1f, camOpacity = 1f, camZoom = 1f;

    // ---- Modos de uso ----
    int placement = ESPACIAL;
    boolean headTracking = true;
    boolean handInput = true;
    boolean gazePointer = true;
    boolean dwell = false;
    boolean simple = false;
    boolean skeleton = true;
    int segMode = SEG_OFF;
    int cameraIndex = 0;

    // ---- Dock ----
    float dockY = 0.30f, dockScale = 1f, dockDist = 1.2f, dockWidth = 1f;

    void copyFrom(Config o) {
        stereo = o.stereo; ipdMm = o.ipdMm; eyeScale = o.eyeScale; fovDeg = o.fovDeg; distortion = o.distortion;
        k1 = o.k1; k2 = o.k2; alignXmm = o.alignXmm; alignYmm = o.alignYmm; vignette = o.vignette; resScale = o.resScale;
        stereo3d = o.stereo3d; brightness = o.brightness; contrast = o.contrast; saturation = o.saturation; uiScale = o.uiScale;
        minDist = o.minDist; animSpeed = o.animSpeed; headSens = o.headSens; headSmooth = o.headSmooth;
        background = o.background; camBrightness = o.camBrightness; camContrast = o.camContrast; camOpacity = o.camOpacity;
        camZoom = o.camZoom; placement = o.placement; headTracking = o.headTracking; handInput = o.handInput;
        gazePointer = o.gazePointer; dwell = o.dwell; simple = o.simple; skeleton = o.skeleton; segMode = o.segMode;
        cameraIndex = o.cameraIndex; dockY = o.dockY; dockScale = o.dockScale; dockDist = o.dockDist; dockWidth = o.dockWidth;
    }

    void load(SharedPreferences p, String k) {
        stereo = p.getBoolean(k + "stereo", stereo);
        ipdMm = p.getFloat(k + "ipd", ipdMm);
        eyeScale = p.getFloat(k + "eyeScale", eyeScale);
        fovDeg = p.getFloat(k + "fov", fovDeg);
        distortion = p.getBoolean(k + "dist", distortion);
        k1 = p.getFloat(k + "k1", k1);
        k2 = p.getFloat(k + "k2", k2);
        alignXmm = p.getFloat(k + "ax", alignXmm);
        alignYmm = p.getFloat(k + "ay", alignYmm);
        vignette = p.getFloat(k + "vig", vignette);
        resScale = p.getFloat(k + "res", resScale);
        stereo3d = p.getFloat(k + "s3d", stereo3d);
        brightness = p.getFloat(k + "bri", brightness);
        contrast = p.getFloat(k + "con", contrast);
        saturation = p.getFloat(k + "sat", saturation);
        uiScale = p.getFloat(k + "ui", uiScale);
        minDist = p.getFloat(k + "minD", minDist);
        animSpeed = p.getFloat(k + "anim", animSpeed);
        headSens = p.getFloat(k + "hs", headSens);
        headSmooth = p.getFloat(k + "hsm", headSmooth);
        background = p.getInt(k + "bg", background);
        camBrightness = p.getFloat(k + "cbri", camBrightness);
        camContrast = p.getFloat(k + "ccon", camContrast);
        camOpacity = p.getFloat(k + "cop", camOpacity);
        camZoom = p.getFloat(k + "czoom", camZoom);
        placement = p.getInt(k + "place", placement);
        headTracking = p.getBoolean(k + "track", headTracking);
        handInput = p.getBoolean(k + "hand", handInput);
        gazePointer = p.getBoolean(k + "gaze", gazePointer);
        dwell = p.getBoolean(k + "dwell", dwell);
        simple = p.getBoolean(k + "simple", simple);
        skeleton = p.getBoolean(k + "skel", skeleton);
        segMode = p.getInt(k + "seg", segMode);
        cameraIndex = p.getInt(k + "cam", cameraIndex);
        dockY = p.getFloat(k + "dy", dockY);
        dockScale = p.getFloat(k + "ds", dockScale);
        dockDist = p.getFloat(k + "dd", dockDist);
        dockWidth = p.getFloat(k + "dw", dockWidth);
        if (placement == CINEMA) placement = ESPACIAL; // o cinema começa sempre desligado
    }

    void save(SharedPreferences p, String k) {
        p.edit()
                .putBoolean(k + "stereo", stereo).putFloat(k + "ipd", ipdMm).putFloat(k + "eyeScale", eyeScale)
                .putFloat(k + "fov", fovDeg).putBoolean(k + "dist", distortion).putFloat(k + "k1", k1).putFloat(k + "k2", k2)
                .putFloat(k + "ax", alignXmm).putFloat(k + "ay", alignYmm).putFloat(k + "vig", vignette)
                .putFloat(k + "res", resScale).putFloat(k + "s3d", stereo3d).putFloat(k + "bri", brightness)
                .putFloat(k + "con", contrast).putFloat(k + "sat", saturation).putFloat(k + "ui", uiScale)
                .putFloat(k + "minD", minDist).putFloat(k + "anim", animSpeed).putFloat(k + "hs", headSens)
                .putFloat(k + "hsm", headSmooth).putInt(k + "bg", background).putFloat(k + "cbri", camBrightness)
                .putFloat(k + "ccon", camContrast).putFloat(k + "cop", camOpacity).putFloat(k + "czoom", camZoom)
                .putInt(k + "place", placement).putBoolean(k + "track", headTracking).putBoolean(k + "hand", handInput)
                .putBoolean(k + "gaze", gazePointer).putBoolean(k + "dwell", dwell).putBoolean(k + "simple", simple)
                .putBoolean(k + "skel", skeleton).putInt(k + "seg", segMode).putInt(k + "cam", cameraIndex)
                .putFloat(k + "dy", dockY).putFloat(k + "ds", dockScale).putFloat(k + "dd", dockDist)
                .putFloat(k + "dw", dockWidth)
                .putBoolean(k + "exists", true)
                .apply();
    }

    float uiScaleEff() { return uiScale * (simple ? 1.2f : 1f); }
    float hitScale() { return simple ? 1.5f : 1f; }
    long dwellMs() { return simple ? 1400 : 1000; }
}
