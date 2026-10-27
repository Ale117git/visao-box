package br.visaobox.app;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ajustes na tela do celular, em dois formatos:
 *  - painel lateral (engrenagem), com tudo;
 *  - tela de início (antes do VR): escolher Controle ou Mãos, ajustes rápidos e "Começar".
 * As opções vêm do SettingsModel, as mesmas do app Ajustes dentro do óculos.
 */
final class SettingsPanel extends ScrollView {

    interface Actions extends SettingsModel.Env {
        void closePanel();
        void setCalibration(boolean on);
        boolean calibrating();
        void changed();
        void begin();             // tela de início: "Começar"
    }

    private final Config cfg;
    private final Actions act;
    private final boolean start;
    private final LinearLayout box;
    private boolean showAll;
    Button startBtn;

    SettingsPanel(Context ctx, Config cfg, Actions act, boolean start) {
        super(ctx);
        this.cfg = cfg;
        this.act = act;
        this.start = start;
        setBackgroundColor(start ? 0xFF0E1320 : 0xF0101218);
        box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(start ? 28 : 18), dp(14), dp(start ? 28 : 18), dp(28));
        addView(box, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        build();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    void build() {
        box.removeAllViews();
        if (start) buildStart(); else buildPanel();
    }

    /* ================= painel da engrenagem ================= */

    private void buildPanel() {
        TextView h = text("Ajustes", 22, Color.WHITE, true);
        box.addView(h);
        note("As mudanças aparecem na hora na imagem ao lado. Todas estas opções também estão no app Ajustes, dentro do óculos.");
        LinearLayout top = hrow();
        top.addView(button(act.calibrating() ? "Sair da calibração" : "Calibrar VR box", true, () -> { act.setCalibration(!act.calibrating()); build(); }));
        top.addView(button("Recentralizar", false, act::recenter));
        top.addView(button("Fechar", false, act::closePanel));
        box.addView(top);
        for (SettingsModel.Section s : SettingsModel.build(cfg, act)) {
            section(s.title);
            for (SettingsModel.Item it : s.items) item(it);
        }
    }

    /* ================= tela de início ================= */

    private void buildStart() {
        LinearLayout head = hrow();
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(getContext());
        try (InputStream in = getContext().getAssets().open(cfg.assistantName == 1 ? "jarvis_logo.png" : "visao_logo.png")) {
            logo.setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (Exception ignored) { }
        GradientDrawable lb = new GradientDrawable();
        lb.setCornerRadius(dp(14));
        logo.setBackground(lb);
        logo.setClipToOutline(true);
        head.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout tt = new LinearLayout(getContext());
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(14), 0, 0, 0);
        tt.addView(text("Visão Box", 26, Color.WHITE, true));
        tt.addView(text("Escolha como vai usar e ajuste tudo antes de colocar o celular no óculos.", 13, 0xB3FFFFFF, false));
        head.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        startBtn = button("Começar ▶", true, act::begin);
        startBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        startBtn.getLayoutParams().height = dp(48);
        head.addView(startBtn);
        box.addView(head);

        section("Como você vai usar?");
        LinearLayout cards = hrow();
        boolean hands = cfg.handInput;
        cards.addView(card("🎮  Controle", "Controle de Xbox ou Bluetooth. A mira fica no centro da visão: aponte com a cabeça e aperte A.",
                "Mais leve e estável", !hands, () -> { cfg.handInput = false; cfg.twoHands = false; build(); }));
        cards.addView(card("✋  Mãos (VR box)", "As duas mãos na frente da câmera: pinça para clicar e arrastar, e as duas juntas para ampliar janelas.",
                "Mais imersivo, mas pesa mais e pode travar", hands, () -> { cfg.handInput = true; cfg.twoHands = true; build(); }));
        box.addView(cards);
        LinearLayout cards2 = hrow();
        cards2.setPadding(0, dp(10), 0, 0);
        cards2.addView(card("📱  Outro celular como controle", "Abra o Visão Box no outro celular e escolha \"Virar controle\". Pelo Bluetooth, pareie ele nas Configurações deste celular. "
                        + "Pelo Wi-Fi, digite lá: " + act.remoteInfo() + ".",
                "Funciona junto com qualquer um dos modos acima", false, () -> { }));
        cards2.addView(card("🎯  Este celular É o controle", "Transforma este aparelho no controle apontador do outro celular, que fica no óculos.",
                "Toque aqui para virar controle", false, act::becomeController));
        box.addView(cards2);

        List<SettingsModel.Section> all = SettingsModel.build(cfg, act);
        section("Ajustes rápidos");
        for (SettingsModel.Section s : all) for (SettingsModel.Item it : s.items) if (it.quick) item(it);

        LinearLayout more = hrow();
        more.addView(button(showAll ? "Esconder os outros ajustes" : "Ver todos os ajustes", false, () -> { showAll = !showAll; build(); }));
        box.addView(more);
        if (showAll) for (SettingsModel.Section s : all) {
            section(s.title);
            for (SettingsModel.Item it : s.items) if (!it.quick) item(it);
        }
        note("Dentro do óculos, o app Ajustes (engrenagem no dock) tem todas estas opções. Para não ver esta tela ao abrir, desligue \"Mostrar a tela de início\" em Modo de uso.");
        LinearLayout end = hrow();
        end.addView(button("Começar ▶", true, act::begin));
        box.addView(end);
        startBtn.post(() -> startBtn.requestFocus());
    }

    private View card(String title, String desc, String tag, boolean sel, Runnable r) {
        LinearLayout c = new LinearLayout(getContext());
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(sel ? 0x332F8CFF : 0x14FFFFFF);
        bg.setStroke(dp(sel ? 2 : 1), sel ? 0xFF7CC4FF : 0x33FFFFFF);
        c.setBackground(bg);
        c.addView(text(title, 17, Color.WHITE, true));
        TextView d = text(desc, 13, 0xCCFFFFFF, false);
        d.setPadding(0, dp(6), 0, dp(6));
        c.addView(d);
        c.addView(text((sel ? "✓ Escolhido · " : "") + tag, 12, sel ? 0xFF9FD3FF : 0x99FFFFFF, true));
        c.setFocusable(true);
        c.setClickable(true);
        c.setOnClickListener(v -> { r.run(); act.changed(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(0, 0, dp(10), 0);
        c.setLayoutParams(lp);
        return c;
    }

    /* ================= itens ================= */

    private void item(SettingsModel.Item it) {
        switch (it.kind) {
            case SettingsModel.SLIDER: slider(it); break;
            case SettingsModel.TOGGLE: toggle(it); break;
            case SettingsModel.CHOICE: choice(it); break;
            case SettingsModel.ACTION: {
                TextView t = text(it.label, 14, Color.WHITE, false);
                t.setPadding(0, dp(10), 0, 0);
                box.addView(t);
                LinearLayout r = hrow();
                final Button[] holder = new Button[1];
                holder[0] = button(it.btn.get(), false, () -> { it.run.run(); holder[0].setText(it.btn.get()); });
                r.addView(holder[0]);
                box.addView(r);
                break;
            }
            case SettingsModel.NOTE: note(it.note); break;
        }
    }

    private LinearLayout hrow() {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(0, dp(8), 0, dp(4));
        return l;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
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
        b.setPadding(dp(16), dp(6), dp(16), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        lp.setMargins(0, 0, dp(8), 0);
        b.setLayoutParams(lp);
        b.setOnFocusChangeListener((v, f) -> v.setAlpha(f ? 1f : .9f));
        b.setOnClickListener(v -> { r.run(); act.changed(); });
        return b;
    }

    private void section(String title) {
        TextView t = text(title.toUpperCase(new Locale("pt", "BR")), 12, 0xFF7CC4FF, true);
        t.setLetterSpacing(.08f);
        t.setPadding(0, dp(20), 0, dp(4));
        box.addView(t);
    }

    private void note(String s) {
        TextView t = text(s, 12, 0x99FFFFFF, false);
        t.setPadding(0, dp(4), 0, dp(4));
        box.addView(t);
    }

    private void slider(SettingsModel.Item it) {
        TextView t = text(it.label + ": " + it.fmt.f(it.fget.get()), 14, Color.WHITE, false);
        t.setPadding(0, dp(8), 0, 0);
        SeekBar sb = new SeekBar(getContext());
        int steps = Math.max(1, Math.round((it.max - it.min) / it.step));
        sb.setMax(steps);
        sb.setProgress(Math.max(0, Math.min(steps, Math.round((it.fget.get() - it.min) / it.step))));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                float v = it.min + p * it.step;
                it.fset.set(v);
                t.setText(it.label + ": " + it.fmt.f(v));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { act.changed(); }
        });
        box.addView(t);
        box.addView(sb);
    }

    private void toggle(SettingsModel.Item it) {
        Switch s = new Switch(getContext());
        s.setText(it.label);
        s.setTextColor(Color.WHITE);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        s.setPadding(0, dp(8), 0, dp(8));
        s.setChecked(it.bget.get());
        s.setOnCheckedChangeListener((b, c) -> { it.bset.set(c); act.changed(); });
        box.addView(s);
    }

    private void choice(SettingsModel.Item it) {
        TextView t = text(it.label, 14, Color.WHITE, false);
        t.setPadding(0, dp(10), 0, dp(2));
        box.addView(t);
        HorizontalScrollView hs = new HorizontalScrollView(getContext());
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = hrow();
        row.setGravity(Gravity.START);
        List<Button> bs = new ArrayList<>();
        for (int i = 0; i < it.opts.length; i++) {
            final int idx = i;
            Button b = new Button(getContext());
            b.setText(it.opts[i]);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            lp.setMargins(0, 0, dp(6), 0);
            b.setLayoutParams(lp);
            b.setPadding(dp(12), 0, dp(12), 0);
            bs.add(b);
            row.addView(b);
            b.setOnClickListener(v -> {
                it.iset.set(idx);
                paintChoice(bs, it.iget.get());
                act.changed();
                if (start) build();             // a tela de início reflete a escolha (ex.: modo)
            });
        }
        paintChoice(bs, it.iget.get());
        hs.addView(row);
        box.addView(hs);
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
