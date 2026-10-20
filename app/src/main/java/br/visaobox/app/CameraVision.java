package br.visaobox.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Size;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapExtractor;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.ByteBufferExtractor;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Captura da câmera (CameraX), mãos (MediaPipe Hand Landmarker) e recorte de pessoas (Image Segmenter). */
final class CameraVision {

    private final ComponentActivity act;
    private final VrView vr;
    private final Config cfg;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private volatile HandLandmarker hand;
    private volatile ImageSegmenter seg;
    private volatile boolean handBusy, segBusy;
    private long lastTs = 0, handSent = 0, segSent = 0;
    private int frameNo = 0;

    private ProcessCameraProvider provider;
    private final List<CameraSelector> cams = new ArrayList<>();
    private final List<String> camNames = new ArrayList<>();
    private int camIdx = 0;
    private volatile boolean mirror = false;

    CameraVision(ComponentActivity act, VrView vr, Config cfg) {
        this.act = act; this.vr = vr; this.cfg = cfg;
    }

    /* ---------------- modelos ---------------- */

    void initModels() {
        exec.execute(() -> {
            try {
                BaseOptions base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(Delegate.CPU).build();
                HandLandmarker.HandLandmarkerOptions o = HandLandmarker.HandLandmarkerOptions.builder()
                        .setBaseOptions(base)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumHands(2)
                        .setMinHandDetectionConfidence(.5f)
                        .setMinHandPresenceConfidence(.5f)
                        .setMinTrackingConfidence(.5f)
                        .setResultListener(this::onHands)
                        .setErrorListener(e -> { handBusy = false; vr.error = "Mãos: " + e.getMessage(); })
                        .build();
                hand = HandLandmarker.createFromOptions(act, o);
                vr.handReady = true;
            } catch (Throwable t) {
                vr.error = "Reconhecimento de mãos indisponível: " + t.getMessage();
            }
            try {
                BaseOptions base = BaseOptions.builder().setModelAssetPath("selfie_segmenter.tflite").setDelegate(Delegate.CPU).build();
                ImageSegmenter.ImageSegmenterOptions o = ImageSegmenter.ImageSegmenterOptions.builder()
                        .setBaseOptions(base)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setOutputCategoryMask(false)
                        .setOutputConfidenceMasks(true)
                        .setResultListener(this::onSegment)
                        .setErrorListener(e -> segBusy = false)
                        .build();
                seg = ImageSegmenter.createFromOptions(act, o);
                vr.segReady = true;
            } catch (Throwable t) {
                vr.segReady = false; // sem segmentação: o recorte de pessoas fica indisponível
            }
        });
    }

    private void onHands(HandLandmarkerResult result, MPImage input) {
        handBusy = false;
        List<List<NormalizedLandmark>> all = result.landmarks();
        float[][] out = new float[all.size()][];
        for (int i = 0; i < all.size(); i++) {
            List<NormalizedLandmark> lm = all.get(i);
            float[] a = new float[42];
            for (int j = 0; j < 21 && j < lm.size(); j++) { a[2 * j] = lm.get(j).x(); a[2 * j + 1] = lm.get(j).y(); }
            out[i] = a;
        }
        vr.rawHands = out;
        vr.rawStamp = SystemClock.uptimeMillis();
    }

    private void onSegment(ImageSegmenterResult result, MPImage input) {
        try {
            Optional<List<MPImage>> om = result.confidenceMasks();
            if (!om.isPresent() || om.get().isEmpty()) return;
            MPImage m = om.get().get(0);
            int w = m.getWidth(), h = m.getHeight();
            ByteBuffer bb = ByteBufferExtractor.extract(m);
            bb.order(ByteOrder.nativeOrder());
            bb.rewind();
            FloatBuffer fb = bb.asFloatBuffer();
            int[] px = new int[w * h];
            for (int i = 0; i < px.length && i < fb.capacity(); i++) {
                float v = fb.get(i);
                float t = Gfx.clamp((v - .35f) / .35f, 0, 1);
                t = t * t * (3 - 2 * t);
                px[i] = (Math.round(t * 255) << 24) | 0xFFFFFF;
            }
            Bitmap mask = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            Bitmap fr = BitmapExtractor.extract(input);
            int cw = 360, ch = Math.max(1, Math.round(360f * fr.getHeight() / fr.getWidth()));
            Bitmap cut = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
            Canvas cc = new Canvas(cut);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            Rect dst = new Rect(0, 0, cw, ch);
            cc.drawBitmap(fr, null, dst, p);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            cc.drawBitmap(mask, null, dst, p);
            vr.segMask = mask;
            vr.segCut = cut;
        } catch (Throwable t) {
            vr.segCut = null;
        } finally {
            segBusy = false;
        }
    }

    /* ---------------- câmera ---------------- */

    void start() {
        vr.camError = null;
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(act);
        future.addListener(() -> {
            try {
                provider = future.get();
                findCameras();
                bind();
            } catch (Exception e) {
                vr.camError = "Câmera: " + e.getMessage();
            }
        }, ContextCompat.getMainExecutor(act));
    }

    private void findCameras() {
        cams.clear(); camNames.clear();
        CameraSelector[] all = {CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA,
                new CameraSelector.Builder().requireLensFacing(2).build()}; // 2 = câmera externa (USB)
        String[] names = {"Câmera traseira", "Câmera frontal", "Câmera externa"};
        for (int i = 0; i < all.length; i++) {
            try { if (provider.hasCamera(all[i])) { cams.add(all[i]); camNames.add(names[i]); } }
            catch (Exception ignored) { }
        }
        camIdx = cams.isEmpty() ? 0 : Math.max(0, Math.min(cams.size() - 1, cfg.cameraIndex));
    }

    private void bind() {
        if (provider == null) return;
        provider.unbindAll();
        if (cams.isEmpty()) { vr.camError = "Nenhuma câmera encontrada neste aparelho"; vr.frame = null; return; }
        ResolutionSelector rs = new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build();
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setResolutionSelector(rs)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();
        analysis.setAnalyzer(exec, this::analyze);
        String name = camNames.get(camIdx);
        mirror = name.equals("Câmera frontal");
        try {
            provider.bindToLifecycle(act, cams.get(camIdx), analysis);
            vr.camError = null;
            vr.camLabel = name;
        } catch (Exception e) {
            vr.camError = "Não consegui abrir a " + name.toLowerCase() + ": " + e.getMessage();
        }
    }

    void next() {
        if (cams.isEmpty()) return;
        camIdx = (camIdx + 1) % cams.size();
        cfg.cameraIndex = camIdx;
        vr.frame = null;
        vr.segCut = null;
        vr.resetHands();
        bind();
    }

    String label() { return vr.camLabel == null || vr.camLabel.isEmpty() ? "Sem câmera" : vr.camLabel; }

    private void analyze(ImageProxy img) {
        try {
            Bitmap src = img.toBitmap();
            int rot = img.getImageInfo().getRotationDegrees();
            Matrix m = new Matrix();
            if (rot != 0) m.postRotate(rot);
            if (mirror) m.postScale(-1f, 1f);
            Bitmap frame = (rot == 0 && !mirror) ? src : Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
            vr.frame = frame;
            frameNo++;
            long ts = Math.max(SystemClock.uptimeMillis(), lastTs + 1);
            lastTs = ts;
            HandLandmarker hl = hand;
            if (handBusy && ts - handSent > 1500) handBusy = false;
            if (segBusy && ts - segSent > 1500) segBusy = false;
            if (cfg.handInput && hl != null && !handBusy) {
                handBusy = true; handSent = ts;
                try { hl.detectAsync(new BitmapImageBuilder(frame).build(), ts); } catch (Throwable t) { handBusy = false; }
            }
            ImageSegmenter is = seg;
            if (cfg.segMode != Config.SEG_OFF && cfg.background == Config.BG_CAMERA && is != null && !segBusy && frameNo % 2 == 0) {
                segBusy = true; segSent = ts;
                try { is.segmentAsync(new BitmapImageBuilder(frame).build(), ts); } catch (Throwable t) { segBusy = false; }
            }
        } catch (Throwable t) {
            vr.camError = "Imagem da câmera: " + t.getMessage();
        } finally {
            img.close();
        }
    }

    void stop() {
        if (provider != null) provider.unbindAll();
        exec.execute(() -> {
            try { if (hand != null) hand.close(); } catch (Throwable ignored) { }
            try { if (seg != null) seg.close(); } catch (Throwable ignored) { }
        });
        exec.shutdown();
    }
}
