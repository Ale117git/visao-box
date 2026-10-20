package br.visaobox.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Câmera (CameraX) + visão computacional (MediaPipe).
 *  - Preview: vai direto para a textura do OpenGL (sem cópia, taxa de quadros cheia).
 *  - Análise: 640x480 já em pé, reaproveitando a memória, para as mãos e o recorte de pessoas.
 *  - Foto: ImageCapture em resolução cheia, quando o aparelho permite.
 */
final class CameraVision {

    interface PhotoListener { void onPhoto(Bitmap b); }

    private final ComponentActivity act;
    private final VrView vr;
    private final Config cfg;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Executor mainExec;

    private volatile HandLandmarker hand;
    private volatile ImageSegmenter seg;
    private volatile boolean handBusy, segBusy, handGpu;
    private long lastTs = 0, handSent = 0, segSent = 0;
    private int frameNo = 0, handErrors = 0;

    private ProcessCameraProvider provider;
    private final List<CameraSelector> cams = new ArrayList<>();
    private final List<String> camNames = new ArrayList<>();
    private int camIdx = 0;
    private volatile boolean mirror = false;
    private volatile SurfaceTexture surfaceTexture;
    private ImageCapture capture;
    private boolean wanted = false;

    // memória reaproveitada
    private final Bitmap[] ring = new Bitmap[3];
    private int ringIdx = 0;
    private Bitmap small;
    private Canvas smallCanvas;
    private final Paint scalePaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    CameraVision(ComponentActivity act, VrView vr, Config cfg) {
        this.act = act; this.vr = vr; this.cfg = cfg;
        this.mainExec = ContextCompat.getMainExecutor(act);
        vr.setCameraSurfaceListener(st -> mainExec.execute(() -> {
            surfaceTexture = st;
            if (wanted && provider != null) bind();
        }));
    }

    /* ---------------- modelos ---------------- */

    void initModels() {
        exec.execute(() -> {
            createHand(true);
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
                vr.segReady = false;
            }
        });
    }

    private void createHand(boolean tryGpu) {
        HandLandmarker old = hand;
        hand = null;
        if (old != null) try { old.close(); } catch (Throwable ignored) { }
        for (int attempt = tryGpu ? 0 : 1; attempt < 2; attempt++) {
            try {
                BaseOptions base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task")
                        .setDelegate(attempt == 0 ? Delegate.GPU : Delegate.CPU).build();
                HandLandmarker.HandLandmarkerOptions o = HandLandmarker.HandLandmarkerOptions.builder()
                        .setBaseOptions(base)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumHands(1)                     // uma mão: bem mais leve
                        .setMinHandDetectionConfidence(.5f)
                        .setMinHandPresenceConfidence(.5f)
                        .setMinTrackingConfidence(.5f)
                        .setResultListener(this::onHands)
                        .setErrorListener(this::onHandError)
                        .build();
                hand = HandLandmarker.createFromOptions(act, o);
                handGpu = attempt == 0;
                handErrors = 0;
                vr.handReady = true;
                return;
            } catch (Throwable t) {
                if (attempt == 1) vr.error = "Reconhecimento de mãos indisponível: " + t.getMessage();
            }
        }
    }

    private void onHandError(RuntimeException e) {
        handBusy = false;
        if (handGpu && ++handErrors >= 3) exec.execute(() -> createHand(false));   // GPU falhou: usa o processador
        else if (!handGpu) vr.error = "Mãos: " + e.getMessage();
    }

    private void onHands(HandLandmarkerResult result, MPImage input) {
        handBusy = false;
        handErrors = 0;
        List<List<NormalizedLandmark>> all = result.landmarks();
        float[][] out = new float[all.size()][];
        boolean mir = mirror;
        for (int i = 0; i < all.size(); i++) {
            List<NormalizedLandmark> lm = all.get(i);
            float[] a = new float[42];
            for (int j = 0; j < 21 && j < lm.size(); j++) {
                float x = lm.get(j).x();
                a[2 * j] = mir ? 1 - x : x;
                a[2 * j + 1] = lm.get(j).y();
            }
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
            int count = Math.min(w * h, fb.remaining());
            byte[] out = new byte[w * h];
            for (int i = 0; i < count; i++) {
                float t = (fb.get(i) - .35f) / .35f;
                t = t < 0 ? 0 : (t > 1 ? 1 : t);
                t = t * t * (3 - 2 * t);
                out[i] = (byte) Math.round(t * 255);
            }
            vr.maskW = w;
            vr.maskH = h;
            vr.maskData = out;
            vr.maskStamp = SystemClock.uptimeMillis();
        } catch (Throwable ignored) {
        } finally {
            segBusy = false;
        }
    }

    /* ---------------- câmera ---------------- */

    void start() {
        wanted = true;
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
        }, mainExec);
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

    private Preview makePreview() {
        ResolutionSelector rs = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(new ResolutionStrategy(new Size(1280, 960),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build();
        Preview preview = new Preview.Builder().setResolutionSelector(rs).build();
        preview.setSurfaceProvider(mainExec, request -> {
            SurfaceTexture st = surfaceTexture;
            if (st == null) { request.willNotProvideSurface(); return; }
            Size res = request.getResolution();
            st.setDefaultBufferSize(res.getWidth(), res.getHeight());
            vr.camBufW = res.getWidth();
            vr.camBufH = res.getHeight();
            request.setTransformationInfoListener(mainExec, info -> vr.camRot = info.getRotationDegrees());
            Surface surface = new Surface(st);
            request.provideSurface(surface, mainExec, result -> surface.release());
        });
        return preview;
    }

    private ImageAnalysis makeAnalysis() {
        ResolutionSelector rs = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build();
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setResolutionSelector(rs)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setOutputImageRotationEnabled(true)          // o CameraX já entrega a imagem em pé
                .build();
        analysis.setAnalyzer(exec, this::analyze);
        return analysis;
    }

    private void bind() {
        if (provider == null) return;
        provider.unbindAll();
        capture = null;
        if (cams.isEmpty()) { vr.camError = "Nenhuma câmera encontrada neste aparelho"; vr.frame = null; return; }
        String name = camNames.get(camIdx);
        mirror = name.equals("Câmera frontal");
        vr.mirror = mirror;
        CameraSelector sel = cams.get(camIdx);
        Preview preview = surfaceTexture != null ? makePreview() : null;
        ImageCapture cap = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
        try {
            // 1ª tentativa: imagem ao vivo + análise + foto
            if (preview != null) provider.bindToLifecycle(act, sel, preview, makeAnalysis(), cap);
            else provider.bindToLifecycle(act, sel, makeAnalysis(), cap);
            capture = cap;
        } catch (Exception e1) {
            provider.unbindAll();
            try {
                // 2ª: sem a foto em resolução cheia
                if (preview != null) provider.bindToLifecycle(act, sel, makePreview(), makeAnalysis());
                else provider.bindToLifecycle(act, sel, makeAnalysis());
            } catch (Exception e2) {
                provider.unbindAll();
                try {
                    // 3ª: só a análise (a imagem vai pela textura de reserva)
                    provider.bindToLifecycle(act, sel, makeAnalysis());
                } catch (Exception e3) {
                    vr.camError = "Não consegui abrir a " + name.toLowerCase() + ": " + e3.getMessage();
                    return;
                }
            }
        }
        vr.camError = null;
        vr.camLabel = name;
    }

    void next() {
        if (cams.isEmpty()) return;
        camIdx = (camIdx + 1) % cams.size();
        cfg.cameraIndex = camIdx;
        vr.frame = null;
        vr.maskData = null;
        vr.resetHands();
        bind();
    }

    String label() { String l = vr.camLabel; return l == null || l.isEmpty() ? "Sem câmera" : l; }

    /** Tira uma foto em resolução cheia (ou usa a imagem da análise como reserva). */
    void takePhoto(PhotoListener l) {
        ImageCapture cap = capture;
        if (cap == null) { Bitmap f = vr.frame; if (f != null) l.onPhoto(f.copy(Bitmap.Config.ARGB_8888, false)); return; }
        cap.takePicture(exec, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                try {
                    Bitmap b = image.toBitmap();
                    int rot = image.getImageInfo().getRotationDegrees();
                    if (rot != 0 || mirror) {
                        Matrix m = new Matrix();
                        m.postRotate(rot);
                        if (mirror) m.postScale(-1, 1);
                        b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                    }
                    l.onPhoto(b);
                } catch (Throwable t) {
                    Bitmap f = vr.frame;
                    if (f != null) l.onPhoto(f.copy(Bitmap.Config.ARGB_8888, false));
                } finally {
                    image.close();
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException e) {
                Bitmap f = vr.frame;
                if (f != null) l.onPhoto(f.copy(Bitmap.Config.ARGB_8888, false));
            }
        });
    }

    /** Copia a imagem da análise para um bitmap reaproveitado (sem criar memória nova a cada quadro). */
    private Bitmap toFrame(ImageProxy img) {
        int w = img.getWidth(), h = img.getHeight();
        ImageProxy.PlaneProxy p = img.getPlanes()[0];
        if (p.getPixelStride() == 4 && p.getRowStride() == w * 4) {
            ringIdx = (ringIdx + 1) % ring.length;
            Bitmap b = ring[ringIdx];
            if (b == null || b.getWidth() != w || b.getHeight() != h) {
                b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                ring[ringIdx] = b;
            }
            ByteBuffer buf = p.getBuffer();
            buf.rewind();
            b.copyPixelsFromBuffer(buf);
            return b;
        }
        return img.toBitmap();
    }

    private void analyze(ImageProxy img) {
        try {
            Bitmap frame = toFrame(img);
            int rot = img.getImageInfo().getRotationDegrees();
            if (rot != 0) {                         // aparelhos sem rotação automática
                Matrix m = new Matrix();
                m.postRotate(rot);
                frame = Bitmap.createBitmap(frame, 0, 0, frame.getWidth(), frame.getHeight(), m, true);
            }
            vr.frame = frame;
            vr.frameStamp = SystemClock.uptimeMillis();
            frameNo++;
            long ts = Math.max(SystemClock.uptimeMillis(), lastTs + 1);
            lastTs = ts;
            if (handBusy && ts - handSent > 1500) handBusy = false;
            if (segBusy && ts - segSent > 1500) segBusy = false;

            HandLandmarker hl = hand;
            if (cfg.handInput && hl != null && !handBusy) {
                handBusy = true; handSent = ts;
                try { hl.detectAsync(new BitmapImageBuilder(frame).build(), ts); } catch (Throwable t) { handBusy = false; }
            }
            ImageSegmenter is = seg;
            if (cfg.segMode != Config.SEG_OFF && cfg.background == Config.BG_CAMERA && is != null && !segBusy && frameNo % 2 == 0) {
                segBusy = true; segSent = ts;
                int sw = 256, sh = Math.max(1, Math.round(256f * frame.getHeight() / frame.getWidth()));
                if (small == null || small.getWidth() != sw || small.getHeight() != sh) {
                    small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
                    smallCanvas = new Canvas(small);
                }
                smallCanvas.drawBitmap(frame, null, new Rect(0, 0, sw, sh), scalePaint);
                try { is.segmentAsync(new BitmapImageBuilder(small).build(), ts); } catch (Throwable t) { segBusy = false; }
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
