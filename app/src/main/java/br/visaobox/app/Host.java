package br.visaobox.app;

import android.graphics.Bitmap;

import java.io.File;
import java.util.List;

/** O que a interface pede ao aplicativo (navegador, galeria, arquivos, câmera, ajustes). */
interface Host {
    // navegadores (id = app: Navegador ou YouTube)
    void webOpen(int id);
    Bitmap webBitmap(int id);
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

    // sistema
    void openSettings();
    void startCalibration();
    void recenter();
    void switchCamera();
    String cameraLabel();
    void configChanged();

    final class GalleryItem {
        String name;
        File file;
        android.net.Uri uri;
        volatile Bitmap thumb;
        volatile Bitmap large;
        volatile boolean loadingLarge;
    }
}
