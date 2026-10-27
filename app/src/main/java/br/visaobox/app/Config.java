package br.visaobox.app;

import android.content.SharedPreferences;

/** Todas as configurações do sistema (ópticas, conforto, passthrough, modos e dock). */
final class Config {

    // ---- Modos ----
    static final int HUD = 0, ESPACIAL = 1, CINEMA = 2;
    static final int BG_CAMERA = 0, BG_VIRTUAL = 1, BG_ESCURO = 2, BG_ESPACO = 3, BG_PRAIA = 4, BG_POR_DO_SOL = 5, BG_CINEMA = 6, BG_IMAGE = 7;
    static final String[] BG_NAMES = {"Câmera", "Grade 3D", "Escuro", "Espaço", "Praia", "Pôr do sol", "Sala de cinema", "Imagem"};
    static final String[] WEATHER = {"Nenhum", "Chuva", "Tempestade", "Neve", "Neblina", "Vaga-lumes", "Estrelas cadentes", "Folhas caindo"};
    static final String[] FORMATS = {"1:1 (quadrado)", "4:3", "3:4", "16:9", "9:16", "Tela cheia"};
    static final float[] FORMAT_RATIO = {1f, 4f / 3f, 3f / 4f, 16f / 9f, 9f / 16f, 0f};   // largura / altura (0 = preencher)
    static final String[] RESOLUTIONS = {"Automática", "480p", "720p", "1080p", "1440p"};
    static final int[] RES_PX = {0, 480, 720, 1080, 1440};
    static final String[] PAGE_SIZES = {"Grande (zoom)", "Médio", "Normal", "Pequeno (vê mais)"};
    static final float[] PAGE_CSS = {560f, 720f, 900f, 1150f};
    static final String[] HAND_STYLES = {"Esqueleto", "Luva", "Mão real (recorte)"};
    static final String[] ASSIST_NAMES = {"Visão", "Jarvis"};
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
    float resScale = 1.0f;          // nitidez (1 = resolução da tela, compensando a lente)
    boolean webGpu = true;
    boolean rememberLayout = true;  // reabre as janelas como estavam
    float stereo3d = 1f;            // intensidade da paralaxe

    // ---- Conforto ----
    float brightness = 0f, contrast = 1f, saturation = 1f;
    float uiScale = 1.15f;          // tamanho das janelas, dock e teclado
    float textScale = 1.25f;        // tamanho dos textos dentro deles
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
    boolean handInput = false;   // mãos desligadas por padrão: mira + controle é mais leve
    boolean gazePointer = true;
    boolean dwell = false;
    boolean simple = false;
    boolean skeleton = true;
    int segMode = SEG_OFF;
    int cameraIndex = 0;

    // ---- Assistente Visão ----
    boolean assistant = true;       // fica ouvindo "ei visão"
    float voicePitch = 0.72f;       // tom (menor = mais grave)
    float voiceRate = 0.9f;         // velocidade da fala
    String voiceName = "";          // voz escolhida ("" = automática)
    String userName = "";           // como o Visão chama a pessoa

    // ---- Fundo em imagem e clima ----
    String bgImage = "preset:0";    // "preset:N" = imagem pronta; senão, caminho de uma foto
    int weather = 0;                // efeito de clima (WEATHER)
    float weatherAmt = .6f;         // intensidade
    boolean weatherSound = true;

    // ---- Início ----
    boolean showStart = true;       // tela de início antes do VR
    boolean twoHands = false;       // duas mãos (pinça em cada uma, ampliar janelas)
    float webCss = 900f;            // largura das páginas (px de CSS): menor = letras maiores
    int assistantName = 0;          // 0 = Visão, 1 = Jarvis
    int format = 0;                 // formato da imagem de cada olho (FORMATS)
    int resolution = 0;             // resolução da imagem (RESOLUTIONS)
    int handStyle = 1;              // aparência das mãos (HAND_STYLES): luva por padrão
    boolean handDepth = true;       // puxar a mão para perto do rosto traz a janela junto
    boolean remoteCtl = true;       // aceita outro celular como controle (Wi-Fi)

    // ---- Dock ----
    boolean dockFollow = false;      // falso = dock fixo no ambiente (modo espacial)
    float dockX = 0f, dockY = 0.30f, dockScale = 1f, dockDist = 1.2f, dockWidth = 1f;

    void copyFrom(Config o) {
        stereo = o.stereo; ipdMm = o.ipdMm; eyeScale = o.eyeScale; fovDeg = o.fovDeg; distortion = o.distortion;
        k1 = o.k1; k2 = o.k2; alignXmm = o.alignXmm; alignYmm = o.alignYmm; vignette = o.vignette; resScale = o.resScale; webGpu = o.webGpu; rememberLayout = o.rememberLayout;
        stereo3d = o.stereo3d; brightness = o.brightness; contrast = o.contrast; saturation = o.saturation; uiScale = o.uiScale; textScale = o.textScale;
        minDist = o.minDist; animSpeed = o.animSpeed; headSens = o.headSens; headSmooth = o.headSmooth;
        background = o.background; camBrightness = o.camBrightness; camContrast = o.camContrast; camOpacity = o.camOpacity;
        camZoom = o.camZoom; placement = o.placement; headTracking = o.headTracking; handInput = o.handInput;
        gazePointer = o.gazePointer; dwell = o.dwell; simple = o.simple; skeleton = o.skeleton; segMode = o.segMode;
        cameraIndex = o.cameraIndex; dockFollow = o.dockFollow; dockX = o.dockX; dockY = o.dockY; dockScale = o.dockScale; dockDist = o.dockDist; dockWidth = o.dockWidth;
        assistant = o.assistant; voicePitch = o.voicePitch; voiceRate = o.voiceRate; voiceName = o.voiceName; userName = o.userName;
        bgImage = o.bgImage; weather = o.weather; weatherAmt = o.weatherAmt; weatherSound = o.weatherSound;
        showStart = o.showStart; twoHands = o.twoHands; webCss = o.webCss; assistantName = o.assistantName;
        format = o.format; resolution = o.resolution; handStyle = o.handStyle; handDepth = o.handDepth;
        remoteCtl = o.remoteCtl;
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
        resScale = p.getFloat(k + "res2", resScale);
        webGpu = p.getBoolean(k + "webgpu", webGpu);
        rememberLayout = p.getBoolean(k + "remember", rememberLayout);
        stereo3d = p.getFloat(k + "s3d", stereo3d);
        brightness = p.getFloat(k + "bri", brightness);
        contrast = p.getFloat(k + "con", contrast);
        saturation = p.getFloat(k + "sat", saturation);
        uiScale = p.getFloat(k + "ui2", uiScale);
        textScale = p.getFloat(k + "txt", textScale);
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
        handInput = p.getBoolean(k + "hand2", handInput);
        gazePointer = p.getBoolean(k + "gaze", gazePointer);
        dwell = p.getBoolean(k + "dwell", dwell);
        simple = p.getBoolean(k + "simple", simple);
        skeleton = p.getBoolean(k + "skel", skeleton);
        segMode = p.getInt(k + "seg", segMode);
        cameraIndex = p.getInt(k + "cam", cameraIndex);
        dockFollow = p.getBoolean(k + "dfol", dockFollow);
        dockX = p.getFloat(k + "dx", dockX);
        dockY = p.getFloat(k + "dy", dockY);
        dockScale = p.getFloat(k + "ds", dockScale);
        dockDist = p.getFloat(k + "dd", dockDist);
        dockWidth = p.getFloat(k + "dw", dockWidth);
        assistant = p.getBoolean(k + "asst", assistant);
        voicePitch = p.getFloat(k + "vpitch", voicePitch);
        voiceRate = p.getFloat(k + "vrate", voiceRate);
        voiceName = p.getString(k + "vname", voiceName);
        userName = p.getString(k + "uname", userName);
        bgImage = p.getString(k + "bgimg", bgImage);
        weather = p.getInt(k + "wx", weather);
        weatherAmt = p.getFloat(k + "wxa", weatherAmt);
        weatherSound = p.getBoolean(k + "wxs", weatherSound);
        showStart = p.getBoolean(k + "start", showStart);
        twoHands = p.getBoolean(k + "hands2", twoHands);
        webCss = p.getFloat(k + "css2", webCss);
        assistantName = p.getInt(k + "aname", assistantName);
        format = p.getInt(k + "fmt", format);
        resolution = p.getInt(k + "resp", resolution);
        handStyle = p.getInt(k + "hstyle", handStyle);
        handDepth = p.getBoolean(k + "hdepth", handDepth);
        remoteCtl = p.getBoolean(k + "rctl", remoteCtl);
        if (handStyle < 0 || handStyle >= HAND_STYLES.length) handStyle = 1;
        if (format < 0 || format >= FORMATS.length) format = 0;
        if (resolution < 0 || resolution >= RESOLUTIONS.length) resolution = 0;
        if (background < 0 || background >= BG_NAMES.length) background = BG_CAMERA;
        if (weather < 0 || weather >= WEATHER.length) weather = 0;
        if (placement == CINEMA) placement = ESPACIAL; // o cinema começa sempre desligado
    }

    void save(SharedPreferences p, String k) {
        p.edit()
                .putBoolean(k + "stereo", stereo).putFloat(k + "ipd", ipdMm).putFloat(k + "eyeScale", eyeScale)
                .putFloat(k + "fov", fovDeg).putBoolean(k + "dist", distortion).putFloat(k + "k1", k1).putFloat(k + "k2", k2)
                .putFloat(k + "ax", alignXmm).putFloat(k + "ay", alignYmm).putFloat(k + "vig", vignette)
                .putFloat(k + "res2", resScale).putBoolean(k + "webgpu", webGpu).putBoolean(k + "remember", rememberLayout).putFloat(k + "s3d", stereo3d).putFloat(k + "bri", brightness)
                .putFloat(k + "con", contrast).putFloat(k + "sat", saturation).putFloat(k + "ui2", uiScale).putFloat(k + "txt", textScale)
                .putFloat(k + "minD", minDist).putFloat(k + "anim", animSpeed).putFloat(k + "hs", headSens)
                .putFloat(k + "hsm", headSmooth).putInt(k + "bg", background).putFloat(k + "cbri", camBrightness)
                .putFloat(k + "ccon", camContrast).putFloat(k + "cop", camOpacity).putFloat(k + "czoom", camZoom)
                .putInt(k + "place", placement).putBoolean(k + "track", headTracking).putBoolean(k + "hand2", handInput)
                .putBoolean(k + "gaze", gazePointer).putBoolean(k + "dwell", dwell).putBoolean(k + "simple", simple)
                .putBoolean(k + "skel", skeleton).putInt(k + "seg", segMode).putInt(k + "cam", cameraIndex)
                .putBoolean(k + "dfol", dockFollow).putFloat(k + "dx", dockX).putFloat(k + "dy", dockY).putFloat(k + "ds", dockScale).putFloat(k + "dd", dockDist)
                .putFloat(k + "dw", dockWidth)
                .putBoolean(k + "asst", assistant).putFloat(k + "vpitch", voicePitch).putFloat(k + "vrate", voiceRate)
                .putString(k + "vname", voiceName).putString(k + "uname", userName)
                .putString(k + "bgimg", bgImage).putInt(k + "wx", weather).putFloat(k + "wxa", weatherAmt).putBoolean(k + "wxs", weatherSound)
                .putBoolean(k + "start", showStart).putBoolean(k + "hands2", twoHands).putFloat(k + "css2", webCss).putInt(k + "aname", assistantName)
                .putInt(k + "fmt", format).putInt(k + "resp", resolution).putInt(k + "hstyle", handStyle).putBoolean(k + "hdepth", handDepth)
                .putBoolean(k + "rctl", remoteCtl)
                .putBoolean(k + "exists", true)
                .apply();
    }

    String assistantLabel() { return ASSIST_NAMES[Math.max(0, Math.min(1, assistantName))]; }

    float uiScaleEff() { return uiScale * (simple ? 1.2f : 1f); }
    float hitScale() { return simple ? 1.5f : 1f; }
    long dwellMs() { return simple ? 1400 : 1000; }
}
