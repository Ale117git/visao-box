package br.visaobox.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Painel de ajustes ao lado da imagem: tudo muda na hora, então dá para ver o efeito enquanto ajusta. */
final class SettingsPanel extends ScrollView {

    interface Actions {
        void closePanel();
        void recenter();
        void switchCamera();
        void setCalibration(boolean on);
        boolean calibrating();
        void saveProfile();
        void loadProfile();
        void resetDefaults();
        void setPlacement(int p);
        void setTracking(boolean on);
        void changed();
    }

    interface FGet { float get(); }
    interface FSet { void set(float v); }
    interface BGet { boolean get(); }
    interface BSet { void set(boolean v); }
    interface IGet { int get(); }
    interface ISet { void set(int v); }
    interface Fmt { String f(float v); }

    private final Config cfg;
    private final Actions act;
    private final LinearLayout box;

    SettingsPanel(Context ctx, Config cfg, Actions act) {
        super(ctx);
        this.cfg = cfg;
        this.act = act;
        setBackgroundColor(0xF0101218);
        box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(14), dp(18), dp(28));
        addView(box, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        build();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    void build() {
        box.removeAllViews();
        TextView h = new TextView(getContext());
        h.setText("Ajustes");
        h.setTextColor(Color.WHITE);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(h);
        note("As mudanças aparecem na hora na imagem ao lado.");

        LinearLayout top = hrow();
        top.addView(button(act.calibrating() ? "Sair da calibração" : "Calibrar VR box", true, () -> { act.setCalibration(!act.calibrating()); build(); }));
        top.addView(button("Recentralizar", false, act::recenter));
        top.addView(button("Fechar", false, act::closePanel));
        box.addView(top);

        section("Visão (VR box)");
        toggle("Dois olhos (VR box)", () -> cfg.stereo, v -> cfg.stereo = v);
        slider("Distância entre os olhos (IPD)", 50, 76, .5f, () -> cfg.ipdMm, v -> cfg.ipdMm = v, v -> String.format(Locale.US, "%.1f mm", v));
        slider("Tamanho de cada imagem", .6f, 1.1f, .01f, () -> cfg.eyeScale, v -> cfg.eyeScale = v, v -> Math.round(v * 100) + "%");
        slider("Campo de visão", 50, 120, 1, () -> cfg.fovDeg, v -> cfg.fovDeg = v, v -> Math.round(v) + "°");
        toggle("Correção da lente (distorção)", () -> cfg.distortion, v -> cfg.distortion = v);
        slider("Distorção da lente", 0, .6f, .01f, () -> cfg.k1, v -> cfg.k1 = v, v -> String.format(Locale.US, "%.2f", v));
        slider("Curvatura das bordas", 0, .4f, .01f, () -> cfg.k2, v -> cfg.k2 = v, v -> String.format(Locale.US, "%.2f", v));
        slider("Alinhamento horizontal", -10, 10, .5f, () -> cfg.alignXmm, v -> cfg.alignXmm = v, v -> String.format(Locale.US, "%+.1f mm", v));
        slider("Alinhamento vertical", -10, 10, .5f, () -> cfg.alignYmm, v -> cfg.alignYmm = v, v -> String.format(Locale.US, "%+.1f mm", v));
        slider("Escurecer as bordas", 0, 1, .05f, () -> cfg.vignette, v -> cfg.vignette = v, v -> Math.round(v * 100) + "%");
        slider("Intensidade do 3D", 0, 2, .05f, () -> cfg.stereo3d, v -> cfg.stereo3d = v, v -> Math.round(v * 100) + "%");
        slider("Nitidez (resolução)", .4f, 1, .05f, () -> cfg.resScale, v -> cfg.resScale = v, v -> Math.round(v * 100) + "%");
        note("Valores maiores de nitidez deixam a imagem mais definida, mas podem reduzir os quadros por segundo.");

        section("Conforto");
        slider("Brilho", -.4f, .4f, .02f, () -> cfg.brightness, v -> cfg.brightness = v, v -> Math.round(v * 100) + "");
        slider("Contraste", .5f, 1.5f, .02f, () -> cfg.contrast, v -> cfg.contrast = v, v -> Math.round(v * 100) + "%");
        slider("Intensidade das cores", 0, 2, .05f, () -> cfg.saturation, v -> cfg.saturation = v, v -> Math.round(v * 100) + "%");
        slider("Tamanho da interface", .6f, 1.6f, .05f, () -> cfg.uiScale, v -> cfg.uiScale = v, v -> Math.round(v * 100) + "%");
        slider("Distância mínima das janelas", .5f, 2f, .05f, () -> cfg.minDist, v -> cfg.minDist = v, v -> String.format(Locale.US, "%.2f m", v));
        slider("Velocidade das animações", .3f, 2f, .05f, () -> cfg.animSpeed, v -> cfg.animSpeed = v, v -> Math.round(v * 100) + "%");
        slider("Sensibilidade da cabeça", .5f, 2f, .05f, () -> cfg.headSens, v -> cfg.headSens = v, v -> Math.round(v * 100) + "%");
        slider("Suavização do movimento", 0, 1, .05f, () -> cfg.headSmooth, v -> cfg.headSmooth = v, v -> Math.round(v * 100) + "%");

        section("Fundo e câmera");
        choice("Fundo", new String[]{"Câmera", "Virtual 3D", "Escuro"}, () -> cfg.background, v -> cfg.background = v);
        slider("Brilho da câmera", -.5f, .5f, .02f, () -> cfg.camBrightness, v -> cfg.camBrightness = v, v -> Math.round(v * 100) + "");
        slider("Contraste da câmera", .5f, 1.6f, .02f, () -> cfg.camContrast, v -> cfg.camContrast = v, v -> Math.round(v * 100) + "%");
        slider("Opacidade da câmera", 0, 1, .05f, () -> cfg.camOpacity, v -> cfg.camOpacity = v, v -> Math.round(v * 100) + "%");
        slider("Zoom da câmera", .6f, 2f, .05f, () -> cfg.camZoom, v -> cfg.camZoom = v, v -> Math.round(v * 100) + "%");
        LinearLayout cr = hrow();
        cr.addView(button("Trocar câmera", false, act::switchCamera));
        box.addView(cr);
        choice("Recorte de pessoas", new String[]{"Desligado", "Recorte", "Silhueta", "Contorno"}, () -> cfg.segMode, v -> cfg.segMode = v);
        note("O recorte separa as pessoas do fundo para colocar janelas atrás delas (botão ◐ na janela). É uma aproximação visual: a câmera não mede a distância real.");

        section("Janelas e interação");
        choice("Posição das janelas", new String[]{"HUD", "Espacial", "Cinema"}, () -> cfg.placement, act::setPlacement);
        toggle("Rastrear a cabeça (giroscópio)", () -> cfg.headTracking, act::setTracking);
        toggle("Rastrear as mãos (pinça) — desligado deixa o app mais leve", () -> cfg.handInput, v -> cfg.handInput = v);
        toggle("Mostrar esqueleto da mão", () -> cfg.skeleton, v -> cfg.skeleton = v);
        toggle("Ponteiro no centro da visão", () -> cfg.gazePointer, v -> cfg.gazePointer = v);
        toggle("Tocar olhando parado", () -> cfg.dwell, v -> cfg.dwell = v);
        toggle("Navegação simplificada (alvos grandes)", () -> cfg.simple, v -> cfg.simple = v);
        note("Controle de Xbox / Bluetooth: A confirma na mira (segurar A na barra arrasta), B volta ou cancela, X abre o menu, "
                + "Y o teclado, LB/RB afastam ou aproximam a janela, LT segurado arrasta qualquer janela, RT também confirma, "
                + "Start abre os Ajustes e View recentraliza. Analógico esquerdo ajusta a mira (L3 centraliza), o direito rola a página. "
                + "O app Controle, no menu, mostra os botões ao vivo.");

        section("Dock");
        toggle("Dock segue a cabeça (desligado = fixo no ambiente)", () -> cfg.dockFollow, v -> cfg.dockFollow = v);
        slider("Posição horizontal", -.45f, .45f, .01f, () -> cfg.dockX, v -> cfg.dockX = v, v -> Math.round(v * 100) + "");
        slider("Altura", -.42f, .46f, .01f, () -> cfg.dockY, v -> cfg.dockY = v, v -> Math.round(v * 100) + "");
        note("Você também pode arrastar o dock na própria tela: faça a pinça na barrinha embaixo dele e mova. No modo espacial ele fica parado no ambiente; Volume − traz tudo para a sua frente.");
        slider("Largura", .7f, 1.4f, .02f, () -> cfg.dockWidth, v -> cfg.dockWidth = v, v -> Math.round(v * 100) + "%");
        slider("Distância", .6f, 2.5f, .05f, () -> cfg.dockDist, v -> cfg.dockDist = v, v -> String.format(Locale.US, "%.2f m", v));
        slider("Escala", .6f, 1.6f, .05f, () -> cfg.dockScale, v -> cfg.dockScale = v, v -> Math.round(v * 100) + "%");

        section("Perfil");
        LinearLayout pr = hrow();
        pr.addView(button("Salvar perfil", true, () -> { act.saveProfile(); }));
        pr.addView(button("Carregar perfil", false, () -> { act.loadProfile(); build(); }));
        pr.addView(button("Restaurar padrão", false, () -> { act.resetDefaults(); build(); }));
        box.addView(pr);

        section("Limites do hardware");
        note("Os ajustes digitais melhoram o alinhamento, mas não mudam a distância física entre as lentes, o foco nem a qualidade das lentes do VR box. "
                + "O giroscópio mede só a rotação da cabeça, não a sua posição no espaço.");
    }

    /* ---------------- componentes ---------------- */

    private LinearLayout hrow() {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(0, dp(8), 0, dp(4));
        return l;
    }

    private Button button(String text, boolean primary, Runnable r) {
        Button b = new Button(getContext());
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTextColor(primary ? 0xFF0B1220 : Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(primary ? 0xFFBFE3FF : 0x26FFFFFF);
        b.setBackground(bg);
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        lp.setMargins(0, 0, dp(8), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> { r.run(); act.changed(); });
        return b;
    }

    private void section(String title) {
        TextView t = new TextView(getContext());
        t.setText(title.toUpperCase(new Locale("pt", "BR")));
        t.setTextColor(0xFF7CC4FF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setLetterSpacing(.08f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(20), 0, dp(4));
        box.addView(t);
    }

    private void note(String s) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextColor(0x99FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setPadding(0, dp(4), 0, dp(4));
        box.addView(t);
    }

    private void slider(String label, float min, float max, float step, FGet get, FSet set, Fmt fmt) {
        TextView t = new TextView(getContext());
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(8), 0, 0);
        SeekBar sb = new SeekBar(getContext());
        int steps = Math.max(1, Math.round((max - min) / step));
        sb.setMax(steps);
        sb.setProgress(Math.max(0, Math.min(steps, Math.round((get.get() - min) / step))));
        t.setText(label + ": " + fmt.f(get.get()));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                float v = min + p * step;
                set.set(v);
                t.setText(label + ": " + fmt.f(v));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { act.changed(); }
        });
        box.addView(t);
        box.addView(sb);
    }

    private void toggle(String label, BGet get, BSet set) {
        Switch s = new Switch(getContext());
        s.setText(label);
        s.setTextColor(Color.WHITE);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        s.setPadding(0, dp(8), 0, dp(8));
        s.setChecked(get.get());
        s.setOnCheckedChangeListener((b, c) -> { set.set(c); act.changed(); });
        box.addView(s);
    }

    private void choice(String label, String[] opts, IGet get, ISet set) {
        TextView t = new TextView(getContext());
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(10), 0, dp(2));
        box.addView(t);
        LinearLayout row = hrow();
        row.setGravity(Gravity.START);
        List<Button> bs = new ArrayList<>();
        for (int i = 0; i < opts.length; i++) {
            final int idx = i;
            Button b = new Button(getContext());
            b.setText(opts[i]);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            lp.setMargins(0, 0, dp(6), 0);
            b.setLayoutParams(lp);
            b.setPadding(dp(12), 0, dp(12), 0);
            bs.add(b);
            row.addView(b);
            b.setOnClickListener(v -> {
                set.set(idx);
                paintChoice(bs, get.get());
                act.changed();
            });
        }
        paintChoice(bs, get.get());
        box.addView(row);
    }

    private void paintChoice(List<Button> bs, int sel) {
        for (int i = 0; i < bs.size(); i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(16));
            bg.setColor(i == sel ? 0xFFF2F4F8 : 0x1FFFFFFF);
            bs.get(i).setBackground(bg);
            bs.get(i).setTextColor(i == sel ? 0xFF0B1220 : Color.WHITE);
        }
    }
}
