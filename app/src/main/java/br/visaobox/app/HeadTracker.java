package br.visaobox.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;

/**
 * Rastreamento ROTACIONAL da cabeça (giroscópio + acelerômetro via sensor de rotação).
 * Dá a orientação (guinada, inclinação e rolagem), mas NÃO a posição da cabeça no espaço.
 */
final class HeadTracker implements SensorEventListener {

    private final SensorManager sm;
    private final Sensor sensor;
    private final Sensor magSensor;                  // vetor de rotação com bússola (contra o desvio)
    final YawFusion fusion = new YawFusion();
    private boolean gameMode;
    final boolean available;
    volatile boolean hasData = false;
    volatile int displayRotation = Surface.ROTATION_90;

    private final float[] R = new float[9];
    private volatile float yaw, pitch, roll, yaw0;

    HeadTracker(Context ctx) {
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = null;
        if (sm != null) {
            s = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (s == null) s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        gameMode = s != null && s.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR;
        sensor = s;
        available = s != null;
        magSensor = gameMode ? sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) : null;
    }

    void start() {
        if (available) sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        if (magSensor != null) sm.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_UI);
    }

    /** Liga/desliga a bússola contra o desvio (some sozinha se o óculos tiver ímã). */
    void setCompass(boolean on) { fusion.enabled = on; }

    void stop() {
        if (available) sm.unregisterListener(this);
    }

    /** Define a direção atual como "frente". */
    void recenter() { yaw0 = yaw; }

    float relYaw() { return wrap(yaw - yaw0); }
    float pitch() { return pitch; }
    float roll() { return roll; }

    static float wrap(float a) {
        while (a > Math.PI) a -= (float) (2 * Math.PI);
        while (a < -Math.PI) a += (float) (2 * Math.PI);
        return a;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor == magSensor && magSensor != null) { fusion.compass(RemoteProto.quat(e.values), e.timestamp); return; }
        float[] q = RemoteProto.quat(e.values);
        if (gameMode) { fusion.game(q); q = fusion.corrected(q); }
        SensorManager.getRotationMatrixFromVector(R, q);
        // colunas de R = eixos do aparelho no mundo (x leste, y norte, z para cima)
        float fx = -R[2], fy = -R[5], fz = -R[8];            // frente = para onde a câmera traseira aponta
        float ux, uz, rz;
        if (displayRotation == Surface.ROTATION_270) {       // celular deitado com o topo para a direita
            ux = -R[0]; uz = -R[6];
            rz = R[7];
        } else {                                             // topo para a esquerda (padrão)
            ux = R[0]; uz = R[6];
            rz = -R[7];
        }
        float y = (float) Math.atan2(fx, fy);
        float p = (float) Math.asin(Math.max(-1f, Math.min(1f, fz)));
        float r = (float) Math.atan2(-rz, uz);
        if (!hasData) { yaw0 = y; hasData = true; }
        if (fusion.justInit) { fusion.justInit = false; yaw0 = wrap(yaw0 + wrap(y - yaw)); }   // bússola entrou: sem pulo
        yaw = y; pitch = p; roll = r;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
