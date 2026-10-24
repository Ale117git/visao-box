package br.visaobox.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Lista única de todos os ajustes. A mesma lista aparece:
 *  - no painel da engrenagem (tela do celular),
 *  - na tela de início,
 *  - no app Ajustes dentro do óculos.
 */
final class SettingsModel {

    static final int SLIDER = 1, TOGGLE = 2, CHOICE = 3, ACTION = 4, NOTE = 5;

    interface FGet { float get(); }
    interface FSet { void set(float v); }
    interface BGet { boolean get(); }
    interface BSet { void set(boolean v); }
    interface IGet { int get(); }
    interface ISet { void set(int v); }
    interface Fmt { String f(float v); }

    /** O que os ajustes pedem ao app. */
    interface Env {
        void recenter();
        void calibrate();
        void saveProfile();
        void loadProfile();
        void resetDefaults();
        void setPlacement(int p);
        void setTracking(boolean on);
        void assistantToggled();
        String nextVoice();
        String voiceLabel();
        void testVoice();
        void switchCamera();
        void webSizeChanged();
    }

    static final class Item {
        final int kind; final String label;
        float min, max, step; FGet fget; FSet fset; Fmt fmt;
        BGet bget; BSet bset;
        String[] opts; IGet iget; ISet iset;
        Supplier<String> btn; Runnable run;
        String note;
        boolean quick;            // aparece na tela de início
        Item(int k, String l) { kind = k; label = l; }

        /** Passo usado no óculos (botões − e +): no máximo ~20 toques do mínimo ao máximo. */
        float vrStep() { return Math.max(step, (max - min) / 20f); }
        String value() {
            switch (kind) {
                case SLIDER: return fmt.f(fget.get());
                case TOGGLE: return bget.get() ? "Ligado" : "Desligado";
                case CHOICE: { int i = iget.get(); return i >= 0 && i < opts.length ? opts[i] : "—"; }
                case ACTION: return btn.get();
                default: return "";
            }
        }
    }

    static final class Section {
        final String title; final List<Item> items = new ArrayList<>();
        Section(String t) { title = t; }
    }

    static String pct(float v) { return Math.round(v * 100) + "%"; }

    static List<Section> build(Config cfg, Env env) {
        List<Section> out = new ArrayList<>();
        Builder b = new Builder(out);

        b.section("Modo de uso");
        b.choice("Como você vai usar", new String[]{"Controle", "Mãos (VR box)"}, () -> cfg.handInput ? 1 : 0,
                v -> { cfg.handInput = v == 1; cfg.twoHands = v == 1; }).quick = false;
        b.toggle("Rastrear as mãos (pinça)", () -> cfg.handInput, v -> cfg.handInput = v);
        b.toggle("Duas mãos (ampliar janelas com as duas)", () -> cfg.twoHands, v -> cfg.twoHands = v);
        b.toggle("Mostrar esqueleto da mão", () -> cfg.skeleton, v -> cfg.skeleton = v);
        b.toggle("Ponteiro no centro da visão", () -> cfg.gazePointer, v -> cfg.gazePointer = v);
        b.toggle("Tocar olhando parado", () -> cfg.dwell, v -> cfg.dwell = v);
        b.toggle("Navegação simplificada (alvos grandes)", () -> cfg.simple, v -> cfg.simple = v);
        b.toggle("Mostrar a tela de início ao abrir", () -> cfg.showStart, v -> cfg.showStart = v);

        b.section("Visão (VR box)");
        b.toggle("Dois olhos (VR box)", () -> cfg.stereo, v -> cfg.stereo = v).quick = true;
        b.slider("Distância entre os olhos (IPD)", 50, 76, .5f, () -> cfg.ipdMm, v -> cfg.ipdMm = v, v -> String.format(Locale.US, "%.1f mm", v)).quick = true;
        b.slider("Tamanho de cada imagem", .6f, 1.1f, .01f, () -> cfg.eyeScale, v -> cfg.eyeScale = v, SettingsModel::pct).quick = true;
        b.slider("Campo de visão", 50, 120, 1, () -> cfg.fovDeg, v -> cfg.fovDeg = v, v -> Math.round(v) + "°");
        b.toggle("Correção da lente (distorção)", () -> cfg.distortion, v -> cfg.distortion = v).quick = true;
        b.slider("Distorção da lente", 0, .6f, .01f, () -> cfg.k1, v -> cfg.k1 = v, v -> String.format(Locale.US, "%.2f", v)).quick = true;
        b.slider("Curvatura das bordas", 0, .4f, .01f, () -> cfg.k2, v -> cfg.k2 = v, v -> String.format(Locale.US, "%.2f", v));
        b.slider("Alinhamento horizontal", -10, 10, .5f, () -> cfg.alignXmm, v -> cfg.alignXmm = v, v -> String.format(Locale.US, "%+.1f mm", v));
        b.slider("Alinhamento vertical", -10, 10, .5f, () -> cfg.alignYmm, v -> cfg.alignYmm = v, v -> String.format(Locale.US, "%+.1f mm", v));
        b.slider("Escurecer as bordas", 0, 1, .05f, () -> cfg.vignette, v -> cfg.vignette = v, SettingsModel::pct);
        b.slider("Intensidade do 3D", 0, 2, .05f, () -> cfg.stereo3d, v -> cfg.stereo3d = v, SettingsModel::pct);
        b.slider("Nitidez (resolução)", .5f, 1.5f, .05f, () -> cfg.resScale, v -> cfg.resScale = v, SettingsModel::pct).quick = true;
        b.action("Tela de calibração", () -> "Calibrar", env::calibrate);

        b.section("Conforto");
        b.slider("Brilho", -.4f, .4f, .02f, () -> cfg.brightness, v -> cfg.brightness = v, v -> String.valueOf(Math.round(v * 100)));
        b.slider("Contraste", .5f, 1.5f, .02f, () -> cfg.contrast, v -> cfg.contrast = v, SettingsModel::pct);
        b.slider("Intensidade das cores", 0, 2, .05f, () -> cfg.saturation, v -> cfg.saturation = v, SettingsModel::pct);
        b.slider("Tamanho das janelas", .8f, 1.8f, .05f, () -> cfg.uiScale, v -> cfg.uiScale = v, SettingsModel::pct).quick = true;
        b.slider("Tamanho do texto", 1f, 1.8f, .05f, () -> cfg.textScale, v -> cfg.textScale = v, SettingsModel::pct).quick = true;
        b.slider("Letras das páginas da internet", 360, 800, 20, () -> 1160 - cfg.webCss, v -> { cfg.webCss = 1160 - v; env.webSizeChanged(); },
                v -> v >= 700 ? "Grandes" : v >= 560 ? "Médias" : "Pequenas");
        b.slider("Distância mínima das janelas", .5f, 2f, .05f, () -> cfg.minDist, v -> cfg.minDist = v, v -> String.format(Locale.US, "%.2f m", v));
        b.slider("Velocidade das animações", .3f, 2f, .05f, () -> cfg.animSpeed, v -> cfg.animSpeed = v, SettingsModel::pct);
        b.slider("Sensibilidade da cabeça", .5f, 2f, .05f, () -> cfg.headSens, v -> cfg.headSens = v, SettingsModel::pct);
        b.slider("Suavização do movimento", 0, 1, .05f, () -> cfg.headSmooth, v -> cfg.headSmooth = v, SettingsModel::pct);

        b.section("Fundo e câmera");
        b.choice("Fundo", Config.BG_NAMES, () -> cfg.background, v -> cfg.background = v).quick = true;
        b.choice("Clima", Config.WEATHER, () -> cfg.weather, v -> cfg.weather = v);
        b.slider("Intensidade do clima", .1f, 1f, .05f, () -> cfg.weatherAmt, v -> cfg.weatherAmt = v, SettingsModel::pct);
        b.toggle("Som do clima", () -> cfg.weatherSound, v -> cfg.weatherSound = v);
        b.slider("Brilho da câmera", -.5f, .5f, .02f, () -> cfg.camBrightness, v -> cfg.camBrightness = v, v -> String.valueOf(Math.round(v * 100)));
        b.slider("Contraste da câmera", .5f, 1.6f, .02f, () -> cfg.camContrast, v -> cfg.camContrast = v, SettingsModel::pct);
        b.slider("Opacidade da câmera", 0, 1, .05f, () -> cfg.camOpacity, v -> cfg.camOpacity = v, SettingsModel::pct);
        b.slider("Zoom da câmera", .6f, 2f, .05f, () -> cfg.camZoom, v -> cfg.camZoom = v, SettingsModel::pct);
        b.action("Câmera", () -> "Trocar câmera", env::switchCamera);
        b.choice("Recorte de pessoas", new String[]{"Desligado", "Recorte", "Silhueta", "Contorno"}, () -> cfg.segMode, v -> cfg.segMode = v);

        b.section("Janelas");
        b.choice("Posição das janelas", new String[]{"HUD", "Espacial", "Cinema"}, () -> cfg.placement, env::setPlacement);
        b.toggle("Rastrear a cabeça (giroscópio)", () -> cfg.headTracking, env::setTracking);
        b.toggle("Lembrar as janelas ao reabrir", () -> cfg.rememberLayout, v -> cfg.rememberLayout = v);
        b.action("Centro da visão", () -> "Recentralizar", env::recenter);
        b.toggle("Dock segue a cabeça", () -> cfg.dockFollow, v -> cfg.dockFollow = v);
        b.slider("Dock: posição horizontal", -.45f, .45f, .01f, () -> cfg.dockX, v -> cfg.dockX = v, v -> String.valueOf(Math.round(v * 100)));
        b.slider("Dock: altura", -.42f, .46f, .01f, () -> cfg.dockY, v -> cfg.dockY = v, v -> String.valueOf(Math.round(v * 100)));
        b.slider("Dock: largura", .7f, 1.4f, .02f, () -> cfg.dockWidth, v -> cfg.dockWidth = v, SettingsModel::pct);
        b.slider("Dock: distância", .6f, 2.5f, .05f, () -> cfg.dockDist, v -> cfg.dockDist = v, v -> String.format(Locale.US, "%.2f m", v));
        b.slider("Dock: escala", .6f, 1.6f, .05f, () -> cfg.dockScale, v -> cfg.dockScale = v, SettingsModel::pct);

        b.section("Assistente");
        b.choice("Nome do assistente", Config.ASSIST_NAMES, () -> cfg.assistantName, v -> { cfg.assistantName = v; env.assistantToggled(); }).quick = true;
        b.toggle("Ouvir a palavra de chamada (ei Visão / ei Jarvis)", () -> cfg.assistant, v -> { cfg.assistant = v; env.assistantToggled(); }).quick = true;
        b.slider("Tom da voz (menor = mais grave)", .4f, 1.3f, .02f, () -> cfg.voicePitch, v -> cfg.voicePitch = v, SettingsModel::pct);
        b.slider("Velocidade da fala", .5f, 1.4f, .02f, () -> cfg.voiceRate, v -> cfg.voiceRate = v, SettingsModel::pct);
        b.action("Voz", env::voiceLabel, env::nextVoice);
        b.action("Ouvir a voz", () -> "Testar", env::testVoice);

        b.section("Sistema");
        b.action("Perfil", () -> "Salvar perfil", env::saveProfile);
        b.action("Perfil salvo", () -> "Carregar perfil", env::loadProfile);
        b.action("Valores de fábrica", () -> "Restaurar padrão", env::resetDefaults);
        b.note("O giroscópio mede só a rotação da cabeça, não a posição. Os ajustes digitais não mudam a distância física entre as lentes nem o foco delas.");
        return out;
    }

    private static final class Builder {
        final List<Section> out; Section cur;
        Builder(List<Section> o) { out = o; }
        void section(String t) { cur = new Section(t); out.add(cur); }
        Item add(Item i) { cur.items.add(i); return i; }
        Item slider(String l, float min, float max, float step, FGet g, FSet s, Fmt f) {
            Item i = new Item(SLIDER, l); i.min = min; i.max = max; i.step = step; i.fget = g; i.fset = s; i.fmt = f; return add(i);
        }
        Item toggle(String l, BGet g, BSet s) { Item i = new Item(TOGGLE, l); i.bget = g; i.bset = s; return add(i); }
        Item choice(String l, String[] o, IGet g, ISet s) { Item i = new Item(CHOICE, l); i.opts = o; i.iget = g; i.iset = s; return add(i); }
        Item action(String l, Supplier<String> btn, Runnable r) { Item i = new Item(ACTION, l); i.btn = btn; i.run = r; return add(i); }
        Item note(String t) { Item i = new Item(NOTE, ""); i.note = t; return add(i); }
    }
}
