package br.visaobox.app;

import android.graphics.Bitmap;

import java.io.File;
import java.util.List;

/** O que a interface pede ao aplicativo (navegador, galeria, arquivos, câmera, ajustes). */
interface Host {
    // navegadores (id = app: Navegador ou YouTube)
    void webOpen(int id);
    Bitmap webBitmap(int id);
    float[] webCrop(int id);                  // parte útil do quadro {largura, altura} (0..1)
    String webTitle(int id);
    void webLoad(int id, String url);
    void webBack(int id);
    void webReload(int id);
    void webTouch(int id, int action, float fx, float fy);
    void webAspect(int id, float aspect);
    String webUrl(int id);
    void webInsert(int id, String text);
    void webKey(int id, int key);          // KEY_DEL ou KEY_ENTER
    boolean webInputFocused(int id);
    void webScroll(int id, int dx, int dy);
    void webClose(int id);                    // fecha a página (para o som parar)
    void webHome(int id);                     // volta para a página inicial
    boolean webIsHome(int id);
    void webDesktop(int id, boolean on);      // modo computador
    boolean webIsDesktop(int id);
    java.util.List<String[]> favorites();     // {título, endereço}
    java.util.List<String[]> history();
    boolean toggleFavorite(String title, String url);   // devolve true se ficou favorito
    boolean isFavorite(String url);
    float[] webMetrics(int id);               // [posição, altura total, altura visível] ou null
    void webScrollTo(int id, float frac);     // 0 = topo, 1 = fim
    int KEY_DEL = 1, KEY_ENTER = 2;

    // galeria e arquivos
    List<GalleryItem> galleryItems();
    boolean galleryAllowed();
    void requestGallery();
    void loadLarge(GalleryItem item);
    List<File> listFiles();
    void savePhoto(Bitmap b);

    // gravação da realidade mista (tela cheia, sem dividir os olhos)
    void startRecording();
    void stopRecording();
    boolean isRecording();
    long recordingSeconds();

    // voz
    void startVoice();
    boolean isListening();
    String voiceText();

    // assistente Visão
    void speak(String text);                  // fala com a voz do Visão
    String assistantCaption();                // o que ele está dizendo (ou null)
    String assistantChat(String plainText);   // resposta de conversa (ou null)
    Bitmap logo();

    // apps instalados no celular
    // computador (tela do Mac/PC por vídeo)
    PcLink pcLink();
    void pcOpen();
    void pcClose();
    java.util.List<PhoneApp> phoneApps();     // carrega na primeira vez (lista vazia enquanto carrega)
    void launchApp(String pkg);

    final class PhoneApp {
        String label = "", pkg = "";
        volatile Bitmap icon;
    }

    /** Ajustes (os mesmos do painel da engrenagem). */
    SettingsModel.Env settingsEnv();

    // sistema
    void openSettings();
    void startCalibration();
    void recenter();
    void switchCamera();
    String cameraLabel();
    void configChanged();

    // mídia do celular
    boolean mediaAllowed();
    void requestMedia();
    void loadMedia();
    java.util.List<MediaItem> mediaList(int kind);     // 0 = vídeos, 1 = músicas
    void playMedia(MediaItem item);

    final class MediaItem {
        int kind;
        String title = "", artist = "";
        long durationMs;
        android.net.Uri uri;
        volatile Bitmap thumb;
    }

    final class GalleryItem {
        String name;
        File file;
        android.net.Uri uri;
        volatile Bitmap thumb;
        volatile Bitmap large;
        volatile boolean loadingLarge;
    }
}
