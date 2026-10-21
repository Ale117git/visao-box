package br.visaobox.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Visão, o assistente pessoal.
 * - Fica ouvindo "ei visão" sem internet (Vosk, só com as palavras de chamada, gasta pouco).
 * - Fala com voz grave e lenta (TextToSpeech do celular com tom mais baixo).
 * - Responde perguntas simples (hora, data, bateria, contas, piadas) sozinho.
 */
final class Assistant {

    interface Callback {
        void onWake();              // ouviu "ei visão"
        boolean micBusy();          // gravando vídeo ou já ouvindo um comando
        void info(String msg);      // aviso curto na tela
    }

    private static final String MODEL_ASSET = "vosk-pt", MODEL_VERSION = "pt-small-0.3";

    private final Context ctx;
    private final Config cfg;
    private final Callback cb;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // ---- chamada "ei visão" ----
    private org.vosk.Model model;
    private org.vosk.Recognizer wakeRec;
    private org.vosk.android.SpeechService wake;
    private volatile boolean wakeWanted, loading, noModel;
    private long lastWake;

    // ---- voz ----
    private TextToSpeech tts;
    private volatile boolean ttsReady;
    private final List<Voice> voices = new ArrayList<>();
    private volatile boolean speaking;
    private volatile String spoken = "";
    private volatile long spokenUntil;
    private int utt;

    Assistant(Context ctx, Config cfg, Callback cb) {
        this.ctx = ctx.getApplicationContext();
        this.cfg = cfg;
        this.cb = cb;
        tts = new TextToSpeech(this.ctx, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            tts.setLanguage(new Locale("pt", "BR"));
            loadVoices();
            applyVoice();
            ttsReady = true;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { speaking = true; }
                @Override public void onDone(String id) { finishedSpeaking(); }
                @Override public void onError(String id) { finishedSpeaking(); }
            });
        });
    }

    /* ================= voz ================= */

    private void loadVoices() {
        voices.clear();
        try {
            for (Voice v : tts.getVoices()) {
                Locale l = v.getLocale();
                if (l != null && "pt".equals(l.getLanguage()) && !v.getFeatures().contains("notInstalled")) voices.add(v);
            }
        } catch (Throwable ignored) { }
        Collections.sort(voices, (a, b) -> {
            int pa = "BR".equals(a.getLocale().getCountry()) ? 0 : 1, pb = "BR".equals(b.getLocale().getCountry()) ? 0 : 1;
            return pa != pb ? pa - pb : a.getName().compareTo(b.getName());
        });
    }

    /** Escolhe a voz salva; se não houver, procura uma voz masculina (no Google, a "ptd" é masculina). */
    private void applyVoice() {
        Voice pick = null;
        for (Voice v : voices) if (v.getName().equals(cfg.voiceName)) pick = v;
        if (pick == null) for (Voice v : voices) {
            String n = v.getName().toLowerCase(Locale.US);
            if (n.contains("ptd") || n.contains("male") && !n.contains("female")) { pick = v; break; }
        }
        try { if (pick != null) tts.setVoice(pick); } catch (Throwable ignored) { }
    }

    /** Passa para a próxima voz instalada e devolve o nome dela. */
    String nextVoice() {
        if (!ttsReady || voices.isEmpty()) return "Nenhuma voz em português instalada";
        int i = 0;
        Voice cur = null;
        try { cur = tts.getVoice(); } catch (Throwable ignored) { }
        for (int k = 0; k < voices.size(); k++) if (cur != null && voices.get(k).getName().equals(cur.getName())) i = k + 1;
        Voice v = voices.get(i % voices.size());
        cfg.voiceName = v.getName();
        applyVoice();
        return voiceLabel();
    }

    String voiceLabel() {
        if (!ttsReady) return "carregando…";
        Voice cur = null;
        try { cur = tts.getVoice(); } catch (Throwable ignored) { }
        if (cur == null) return "padrão";
        int idx = voices.indexOf(cur);
        return "Voz " + (idx >= 0 ? (idx + 1) + " de " + voices.size() : "") + " (" + cur.getName() + ")";
    }

    /** Fala com o tom grave e a calma escolhidos nos ajustes. */
    void speak(String text) {
        if (text == null) return;
        String clean = text.replaceAll("[“”\"…★📌]", " ").replace("…", ".").trim();
        if (clean.isEmpty()) return;
        spoken = text;
        spokenUntil = System.currentTimeMillis() + 2500 + text.length() * 70L;
        if (!ttsReady) return;
        main.post(() -> {
            speaking = true;
            killWake();                         // não ouvir a si mesmo
            tts.setPitch(cfg.voicePitch);
            tts.setSpeechRate(cfg.voiceRate);
            android.os.Bundle p = new android.os.Bundle();
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, p, "visao" + (++utt));
        });
    }

    void shutUp() { main.post(() -> { if (tts != null) tts.stop(); finishedSpeaking(); }); }

    private void finishedSpeaking() {
        speaking = false;
        spokenUntil = Math.max(spokenUntil, System.currentTimeMillis() + 1500);
        main.postDelayed(this::ensureWake, 500);
    }

    boolean isSpeaking() { return speaking; }
    /** O que o Visão disse por último, enquanto ainda vale mostrar na tela. */
    String caption() { return speaking || System.currentTimeMillis() < spokenUntil ? spoken : null; }

    /* ================= "ei visão" ================= */

    void startWake() { wakeWanted = cfg.assistant; main.post(this::ensureWake); }
    void stopWake() { wakeWanted = false; main.post(this::killWake); }
    /** Chamado depois de um comando de voz, gravação etc.: volta a ouvir se nada estiver usando o microfone. */
    /** Solta o microfone agora (chamar na thread principal). */
    void pauseNow() { killWake(); }
    void resumeSoon() { main.postDelayed(this::ensureWake, 700); }
    boolean wakeActive() { return wake != null; }
    boolean hasModel() { return !noModel; }

    private void ensureWake() {
        if (!wakeWanted || !cfg.assistant || wake != null || speaking || cb.micBusy()) return;
        if (model == null) { loadModel(); return; }
        try {
            wakeRec = new org.vosk.Recognizer(model, 16000f,
                    "[\"ei visão\", \"oi visão\", \"hey visão\", \"visão\", \"[unk]\"]");
            wake = new org.vosk.android.SpeechService(wakeRec, 16000f);
            wake.startListening(listener);
        } catch (Throwable t) {
            killWake();
            cb.info("Não consegui ligar o \"ei visão\": " + t.getMessage());
        }
    }

    private void killWake() {
        if (wake != null) {
            try { wake.stop(); } catch (Throwable ignored) { }
            try { wake.shutdown(); } catch (Throwable ignored) { }
            wake = null;
        }
        if (wakeRec != null) { try { wakeRec.close(); } catch (Throwable ignored) { } wakeRec = null; }
    }

    private final org.vosk.android.RecognitionListener listener = new org.vosk.android.RecognitionListener() {
        @Override public void onPartialResult(String h) { check(h, "partial"); }
        @Override public void onResult(String h) { check(h, "text"); }
        @Override public void onFinalResult(String h) { check(h, "text"); }
        @Override public void onError(Exception e) { main.post(() -> { killWake(); resumeSoon(); }); }
        @Override public void onTimeout() { main.post(() -> { killWake(); resumeSoon(); }); }
    };

    private void check(String json, String key) {
        String t;
        try { t = new JSONObject(json).optString(key, ""); } catch (Exception e) { return; }
        if (!(t.contains("visão") || t.contains("visao"))) return;
        long now = System.currentTimeMillis();
        if (now - lastWake < 1500) return;
        lastWake = now;
        main.post(() -> {
            killWake();                          // solta o microfone para o comando
            cb.onWake();
        });
    }

    /** Copia o modelo de voz (fica dentro do app) para a memória do celular na primeira vez. */
    private void loadModel() {
        if (loading || noModel) return;
        loading = true;
        io.execute(() -> {
            try {
                AssetManager am = ctx.getAssets();
                String[] list = am.list(MODEL_ASSET);
                if (list == null || list.length == 0) {
                    noModel = true;
                    main.post(() -> cb.info("Assistente: o modelo do \"ei visão\" não veio no app. O botão do microfone continua funcionando."));
                    return;
                }
                File dir = new File(ctx.getFilesDir(), MODEL_ASSET);
                File mark = new File(dir, ".versao");
                if (!mark.exists() || !MODEL_VERSION.equals(read(mark))) {
                    main.post(() -> cb.info("Preparando o assistente Visão…"));
                    deleteAll(dir);
                    copyAssets(am, MODEL_ASSET, dir);
                    try (OutputStream o = new FileOutputStream(mark)) { o.write(MODEL_VERSION.getBytes("UTF-8")); }
                }
                org.vosk.LibVosk.setLogLevel(org.vosk.LogLevel.WARNINGS);
                model = new org.vosk.Model(dir.getAbsolutePath());
                main.post(this::ensureWake);
            } catch (Throwable t) {
                noModel = true;
                main.post(() -> cb.info("Assistente indisponível: " + t.getMessage()));
            } finally {
                loading = false;
            }
        });
    }

    private static void copyAssets(AssetManager am, String path, File out) throws Exception {
        String[] kids = am.list(path);
        if (kids == null || kids.length == 0) {
            out.getParentFile().mkdirs();
            try (InputStream in = am.open(path); OutputStream o = new FileOutputStream(out)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
            }
            return;
        }
        out.mkdirs();
        for (String k : kids) copyAssets(am, path + "/" + k, new File(out, k));
    }

    private static void deleteAll(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        f.delete();
    }

    private static String read(File f) {
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[64];
            int n = in.read(b);
            return n > 0 ? new String(b, 0, n, "UTF-8") : "";
        } catch (Exception e) { return ""; }
    }

    void destroy() {
        stopWake();
        main.post(() -> { if (tts != null) { tts.stop(); tts.shutdown(); } });
        io.execute(() -> { if (model != null) model.close(); });
        io.shutdown();
    }

    /* ================= conversa ================= */

    private static final Random RND = new Random();
    private static final String[] PIADAS = {
            "Por que o computador foi ao médico? Porque estava com vírus.",
            "O que o zero disse para o oito? Que cinto bonito!",
            "Por que a matemática está sempre triste? Porque tem muitos problemas.",
            "Qual é o café mais perigoso do mundo? O ex-presso.",
            "O que o tomate foi fazer no banco? Tirar extrato.",
            "Qual é o animal mais antigo do mundo? A zebra, porque ainda está em preto e branco.",
            "Sabe por que eu uso óculos? Porque eu sou a própria visão."};

    /**
     * Respostas que o Visão dá sozinho. Recebe o texto sem acentos e em minúsculas.
     * Devolve a fala, ou null se não for uma conversa (aí vira comando ou pesquisa).
     */
    static String chat(String t, Config cfg, int battery) {
        String nome = cfg.userName == null || cfg.userName.isEmpty() ? "" : ", " + cfg.userName;
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
        String periodo = h < 12 ? "Bom dia" : h < 18 ? "Boa tarde" : "Boa noite";

        if (t.startsWith("me chame de ") || t.startsWith("meu nome e ") || t.startsWith("me chama de ")) {
            String n = t.replaceFirst("^(me chame de |me chama de |meu nome e )", "").trim();
            if (n.isEmpty()) return null;
            cfg.userName = Character.toUpperCase(n.charAt(0)) + n.substring(1);
            return "Prazer, " + cfg.userName + ". Vou lembrar disso.";
        }
        if (t.contains("que horas") || t.equals("horas") || t.contains("hora certa"))
            return "São " + h + (m == 0 ? " horas em ponto." : " e " + m + ".");
        if (t.contains("que dia") || t.contains("data de hoje") || t.contains("dia e hoje")) {
            String[] dias = {"domingo", "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira", "sábado"};
            String[] meses = {"janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"};
            return "Hoje é " + dias[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH) + " de " + meses[c.get(Calendar.MONTH)] + ".";
        }
        if (t.contains("bateria")) return battery < 0 ? "Não consegui ler a bateria." :
                "A bateria está em " + battery + " por cento." + (battery < 20 ? " Melhor carregar logo." : "");
        if (t.contains("quem e voce") || t.contains("seu nome") || t.contains("quem é você") || t.contains("como voce se chama"))
            return "Eu sou o Visão, o assistente deste óculos. Posso abrir apps, pesquisar, tocar vídeos, tirar fotos e responder umas perguntas.";
        if (t.contains("o que voce faz") || t.contains("o que voce sabe") || t.equals("ajuda") || t.contains("me ajuda"))
            return "Diga, por exemplo: abrir YouTube, tocar uma música, pesquisar alguma coisa, que horas são, quanto é sete vezes oito, tirar foto, gravar ou modo cinema.";
        if (t.contains("piada")) return PIADAS[RND.nextInt(PIADAS.length)];
        if (t.startsWith("obrigad") || t.equals("valeu")) return "Às ordens" + nome + ".";
        if (t.startsWith("tudo bem") || t.startsWith("como voce esta") || t.startsWith("como vai"))
            return "Tudo ótimo por aqui" + nome + ". E com você?";
        if (t.equals("oi") || t.equals("ola") || t.startsWith("bom dia") || t.startsWith("boa tarde") || t.startsWith("boa noite") || t.equals("e ai"))
            return periodo + nome + ". Em que posso ajudar?";
        if (t.contains("eu te amo")) return "Eu sou só um programa, mas fico feliz em ajudar você.";
        String conta = calc(t);
        if (conta != null) return conta;
        return null;
    }

    /** "quanto é 7 vezes 8", "12 mais 30", "100 dividido por 4". */
    private static String calc(String t) {
        String s = t.replace("quanto e ", "").replace("quanto da ", "").replace("calcule ", "").replace("calcular ", "").trim();
        s = s.replace(" dividido por ", " / ").replace(" divido por ", " / ").replace(" vezes ", " * ").replace(" x ", " * ")
                .replace(" mais ", " + ").replace(" menos ", " - ").replace(",", ".");
        String[] p = s.split(" ");
        if (p.length != 3) return null;
        Double a = num(p[0]), b = num(p[2]);
        if (a == null || b == null) return null;
        double r;
        switch (p[1]) {
            case "+": r = a + b; break;
            case "-": r = a - b; break;
            case "*": r = a * b; break;
            case "/": if (b == 0) return "Não dá para dividir por zero."; r = a / b; break;
            default: return null;
        }
        String rs = r == Math.rint(r) && Math.abs(r) < 1e12 ? String.valueOf((long) r) : String.format(new Locale("pt", "BR"), "%.2f", r);
        return "Dá " + rs + ".";
    }

    private static final String[] NUMS = {"zero", "um", "dois", "tres", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez",
            "onze", "doze", "treze", "catorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove", "vinte"};

    private static Double num(String s) {
        try { return Double.parseDouble(s); } catch (Exception ignored) { }
        for (int i = 0; i < NUMS.length; i++) if (NUMS[i].equals(s)) return (double) i;
        if (s.equals("uma")) return 1.0;
        if (s.equals("duas")) return 2.0;
        if (s.equals("quatorze")) return 14.0;
        if (s.equals("cem")) return 100.0;
        if (s.equals("mil")) return 1000.0;
        return null;
    }
}
