package br.visaobox.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Visão Box: câmera (CameraX) + reconhecimento de mãos (MediaPipe) + interface VR (VrView).
 */
public class MainActivity extends ComponentActivity {

    private static final int REQ_CAMERA = 7;

    private VrView vr;
    private ExecutorService exec;
    private volatile HandLandmarker landmarker;
    private ProcessCameraProvider provider;
    private boolean front = false;
    private long lastTs = 0;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences("visaobox", MODE_PRIVATE);
        vr = new VrView(this);
        vr.stereo = prefs.getBoolean("stereo", true);
        vr.skel = prefs.getBoolean("skel", true);
        vr.ipd = prefs.getFloat("ipd", 0f);
        vr.zoom = prefs.getFloat("zoom", 1f);
        vr.depth = prefs.getFloat("depth", 0.012f);
        front = prefs.getBoolean("front", false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(vr, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button gear = new Button(this);
        gear.setText("⚙");
        gear.setTextColor(Color.WHITE);
        gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        gear.setPadding(0, 0, 0, 0);
        gear.setAlpha(0.6f);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x99202230);
        bg.setStroke(dp(1), 0x40FFFFFF);
        gear.setBackground(bg);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.END);
        glp.setMargins(dp(10), dp(10), dp(10), dp(10));
        root.addView(gear, glp);
        gear.setOnClickListener(v -> showSettings());

        setContentView(root);
        hideBars();

        exec = Executors.newSingleThreadExecutor();
        exec.execute(this::initModel);

        if (hasCamera()) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private boolean hasCamera() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_CAMERA) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else vr.error = "Permissão da câmera negada. Libere em Configurações > Apps > Visão Box > Permissões.";
    }

    /* ---------------- reconhecimento de mãos ---------------- */

    private void initModel() {
        try {
            BaseOptions base = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .setDelegate(Delegate.CPU)
                    .build();
            HandLandmarker.HandLandmarkerOptions opts = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(2)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener(this::onHands)
                    .setErrorListener(e -> vr.error = "Mãos: " + e.getMessage())
                    .build();
            landmarker = HandLandmarker.createFromOptions(this, opts);
            vr.modelReady = true;
        } catch (Throwable t) {
            vr.error = "Falha ao carregar o modelo de mãos: " + t.getMessage();
        }
    }

    private void onHands(HandLandmarkerResult result, MPImage input) {
        List<List<NormalizedLandmark>> all = result.landmarks();
        float[][] out = new float[all.size()][];
        for (int i = 0; i < all.size(); i++) {
            List<NormalizedLandmark> lm = all.get(i);
            float[] a = new float[42];
            for (int j = 0; j < 21 && j < lm.size(); j++) {
                a[2 * j] = lm.get(j).x();
                a[2 * j + 1] = lm.get(j).y();
            }
            out[i] = a;
        }
        vr.rawHands = out;
        vr.rawStamp = SystemClock.uptimeMillis();
    }

    /* ---------------- câmera ---------------- */

    private void startCamera() {
        vr.error = null;
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                provider = future.get();
                bindCamera();
            } catch (Exception e) {
                vr.error = "Câmera: " + e.getMessage();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (provider == null) return;
        provider.unbindAll();
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

        CameraSelector wanted = front ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
        CameraSelector other = front ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
        try {
            provider.bindToLifecycle(this, wanted, analysis);
            vr.error = null;
        } catch (Exception e1) {
            try {
                front = !front;
                provider.bindToLifecycle(this, other, analysis);
                vr.error = null;
            } catch (Exception e2) {
                vr.error = "Não consegui abrir a câmera: " + e2.getMessage();
            }
        }
    }

    private void analyze(ImageProxy img) {
        try {
            Bitmap src = img.toBitmap();
            int rot = img.getImageInfo().getRotationDegrees();
            Matrix m = new Matrix();
            if (rot != 0) m.postRotate(rot);
            if (front) m.postScale(-1f, 1f);
            Bitmap frame = (rot == 0 && !front) ? src
                    : Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
            vr.frame = frame;
            HandLandmarker lm = landmarker;
            if (lm != null) {
                long ts = Math.max(SystemClock.uptimeMillis(), lastTs + 1);
                lastTs = ts;
                MPImage mp = new BitmapImageBuilder(frame).build();
                lm.detectAsync(mp, ts);
            }
        } catch (Throwable t) {
            vr.error = "Imagem da câmera: " + t.getMessage();
        } finally {
            img.close();
        }
    }

    /* ---------------- ajustes ---------------- */

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));

        Switch sMode = new Switch(this);
        sMode.setText("Modo VR box (2 olhos)");
        sMode.setChecked(vr.stereo);
        sMode.setOnCheckedChangeListener((b, c) -> vr.stereo = c);
        box.addView(sMode);

        Switch sSkel = new Switch(this);
        sSkel.setText("Mostrar esqueleto da mão");
        sSkel.setChecked(vr.skel);
        sSkel.setOnCheckedChangeListener((b, c) -> vr.skel = c);
        box.addView(sSkel);

        addSlider(box, "Separação dos olhos", -80, 80, Math.round(vr.ipd), v -> { vr.ipd = v; return (v > 0 ? "+" : "") + v + " px"; });
        addSlider(box, "Zoom", 60, 150, Math.round(vr.zoom * 100), v -> { vr.zoom = v / 100f; return v + "%"; });
        addSlider(box, "Efeito 3D", 0, 40, Math.round(vr.depth * 1000), v -> { vr.depth = v / 1000f; return String.valueOf(v); });

        Button bCam = new Button(this);
        bCam.setText("Trocar câmera (agora: " + (front ? "frontal" : "traseira") + ")");
        bCam.setOnClickListener(v -> {
            front = !front;
            vr.resetHands();
            bindCamera();
            bCam.setText("Trocar câmera (agora: " + (front ? "frontal" : "traseira") + ")");
        });
        box.addView(bCam);

        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Ajustes")
                .setView(box)
                .setPositiveButton("Fechar", null)
                .create();
        d.setOnDismissListener(x -> { savePrefs(); hideBars(); });
        d.show();
    }

    private interface Change { String apply(int v); }

    private void addSlider(LinearLayout box, String label, int min, int max, int value, Change ch) {
        TextView t = new TextView(this);
        t.setPadding(0, dp(12), 0, 0);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, value - min)));
        t.setText(label + ": " + ch.apply(sb.getProgress() + min));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { t.setText(label + ": " + ch.apply(p + min)); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        box.addView(t);
        box.addView(sb);
    }

    private void savePrefs() {
        prefs.edit()
                .putBoolean("stereo", vr.stereo)
                .putBoolean("skel", vr.skel)
                .putFloat("ipd", vr.ipd)
                .putFloat("zoom", vr.zoom)
                .putFloat("depth", vr.depth)
                .putBoolean("front", front)
                .apply();
    }

    /* ---------------- tela cheia ---------------- */

    @SuppressWarnings("deprecation")
    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (provider != null) provider.unbindAll();
        exec.execute(() -> { if (landmarker != null) landmarker.close(); });
        exec.shutdown();
    }
}
