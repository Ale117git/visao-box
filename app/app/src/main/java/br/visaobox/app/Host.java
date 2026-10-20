package br.visaobox.app;

import android.graphics.Bitmap;

import java.io.File;
import java.util.List;

/** O que a interface pede ao aplicativo (navegador, galeria, arquivos, câmera, ajustes). */
interface Host {
    // navegador
    void webOpen();
    Bitmap webBitmap();
    String webTitle();
    void webLoad(String url);
    void webBack();
    void webReload();
    void webTouch(int action, float fx, float fy);
    void webAspect(float aspect);
    String webUrl();
    void webInsert(String text);
    void webKey(int key);          // KEY_DEL ou KEY_ENTER
    boolean webInputFocused();
    int KEY_DEL = 1, KEY_ENTER = 2;

    // galeria e arquivos
    List<GalleryItem> galleryItems();
    boolean galleryAllowed();
    void requestGallery();
    void loadLarge(GalleryItem item);
    List<File> listFiles();
    void savePhoto(Bitmap b);

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
