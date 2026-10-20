// Visão Box — motor nativo (C++ / NDK)
//
//  - Composição final em OpenGL ES 2: câmera, janelas atrás da pessoa, recorte, interface,
//    cores e vinheta num shader só, com a malha de distorção da lente.
//  - Envio da câmera e da máscara de pessoas para a placa de vídeo direto da memória do Bitmap.
//  - Rastreamento rotacional da cabeça numa thread própria (sensor a 200 Hz), sem passar pelo Java.
//
// A interface (janelas, dock, teclado) continua sendo desenhada no Java com Canvas acelerado;
// aqui chega só o número da textura dela.

#include <jni.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <android/bitmap.h>
#include <android/looper.h>
#include <android/sensor.h>
#include <android/log.h>
#include <pthread.h>

#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "vrcore"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

/* ===================== OpenGL ===================== */

const char* kVs =
    "attribute vec2 aPos;\n"
    "attribute vec2 aScene;\n"
    "uniform vec3 uUiS; uniform vec3 uUiT; uniform mat4 uUiMat; uniform mat4 uBackMat;\n"
    "uniform vec3 uUpS; uniform vec3 uUpT;\n"
    "varying vec2 vUi; varying vec2 vBack; varying vec2 vUp; varying vec2 vScene;\n"
    "void main() {\n"
    "  vec3 p = vec3(aScene, 1.0);\n"
    "  vec4 u = vec4(dot(p, uUiS), dot(p, uUiT), 0.0, 1.0);\n"
    "  vUi = (uUiMat * u).xy;\n"
    "  vBack = (uBackMat * u).xy;\n"
    "  vUp = vec2(dot(p, uUpS), dot(p, uUpT));\n"
    "  vScene = aScene;\n"
    "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
    "}\n";

const char* kFs =
    "#extension GL_OES_EGL_image_external : require\n"
    "precision mediump float;\n"
    "uniform sampler2D uCam2D;\n"
    "uniform samplerExternalOES uUi; uniform samplerExternalOES uBack; uniform sampler2D uMask;\n"
    "uniform int uBg; uniform int uHasBack; uniform int uSeg;\n"
    "uniform vec2 uMaskTexel; uniform vec4 uCamAdj; uniform vec4 uAdj; uniform float uVigR;\n"
    "varying vec2 vUi; varying vec2 vBack; varying vec2 vUp; varying vec2 vScene;\n"
    "void main() {\n"
    "  vec4 ui = texture2D(uUi, vUi);\n"
    "  vec3 col;\n"
    "  if (uBg == 2) {\n"
    "    col = ui.rgb;\n"
    "  } else {\n"
    "    bool inside = vUp.x >= 0.0 && vUp.x <= 1.0 && vUp.y >= 0.0 && vUp.y <= 1.0;\n"
    "    vec3 cam = texture2D(uCam2D, vUp).rgb;\n"
    "    if (!inside) cam = vec3(0.03);\n"
    "    cam = clamp((cam - 0.5) * uCamAdj.y + 0.5 + uCamAdj.x, 0.0, 1.0) * uCamAdj.z * uCamAdj.w;\n"
    "    vec3 base = cam;\n"
    "    if (uHasBack == 1) { vec4 b = texture2D(uBack, vBack); base = b.rgb + base * (1.0 - b.a); }\n"
    "    if (uSeg > 0 && inside) {\n"
    "      float m = texture2D(uMask, vUp).r;\n"
    "      vec3 person = cam;\n"
    "      if (uSeg == 2) person = mix(cam, vec3(0.49, 0.77, 1.0), 0.45);\n"
    "      base = mix(base, person, m);\n"
    "      if (uSeg == 3) {\n"
    "        vec2 dx = vec2(uMaskTexel.x * 2.0, 0.0); vec2 dy = vec2(0.0, uMaskTexel.y * 2.0);\n"
    "        float nb = texture2D(uMask, vUp + dx).r + texture2D(uMask, vUp - dx).r\n"
    "                 + texture2D(uMask, vUp + dy).r + texture2D(uMask, vUp - dy).r;\n"
    "        float e = clamp(abs(m - nb * 0.25) * 6.0, 0.0, 1.0);\n"
    "        base = mix(base, vec3(0.49, 0.77, 1.0), e);\n"
    "      }\n"
    "    }\n"
    "    col = ui.rgb + base * (1.0 - ui.a);\n"
    "  }\n"
    "  float l = dot(col, vec3(0.299, 0.587, 0.114));\n"
    "  col = mix(vec3(l), col, uAdj.z);\n"
    "  col = (col - 0.5) * uAdj.y + 0.5 + uAdj.x;\n"
    "  float r = length(vScene) / uVigR;\n"
    "  col *= 1.0 - uAdj.w * smoothstep(0.72, 1.0, r);\n"
    "  gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);\n"
    "}\n";

struct Gl {
    std::string err;
    GLuint prog = 0, camTex = 0, maskTex = 0;
    GLint aPos = -1, aScene = -1;
    GLint uUiS = -1, uUiT = -1, uUiMat = -1, uBackMat = -1, uUpS = -1, uUpT = -1;
    GLint uCam2D = -1, uUi = -1, uBack = -1, uMask = -1, uBg = -1, uHasBack = -1, uSeg = -1;
    GLint uMaskTexel = -1, uCamAdj = -1, uAdj = -1, uVigR = -1;
    int camW = 0, camH = 0, maskW = 0, maskH = 0;
    std::vector<float> pos[2], scene[2];
    std::vector<uint16_t> idx;
    int eyes = 0;
    std::vector<uint8_t> rowCopy;
} G;

GLuint compile(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024] = {0};
        glGetShaderInfoLog(s, sizeof(log) - 1, nullptr, log);
        G.err = std::string("shader: ") + log;
        glDeleteShader(s);
        return 0;
    }
    return s;
}

GLuint tex2D() {
    GLuint t = 0;
    glGenTextures(1, &t);
    glBindTexture(GL_TEXTURE_2D, t);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    const uint8_t black[4] = {0, 0, 0, 255};
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, black);
    return t;
}

bool init() {
    G = Gl();
    GLuint vs = compile(GL_VERTEX_SHADER, kVs);
    GLuint fs = compile(GL_FRAGMENT_SHADER, kFs);
    if (!vs || !fs) return false;
    GLuint p = glCreateProgram();
    glAttachShader(p, vs);
    glAttachShader(p, fs);
    glLinkProgram(p);
    GLint ok = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &ok);
    glDeleteShader(vs);
    glDeleteShader(fs);
    if (!ok) {
        char log[1024] = {0};
        glGetProgramInfoLog(p, sizeof(log) - 1, nullptr, log);
        G.err = std::string("link: ") + log;
        return false;
    }
    G.prog = p;
    G.aPos = glGetAttribLocation(p, "aPos");
    G.aScene = glGetAttribLocation(p, "aScene");
    G.uUiS = glGetUniformLocation(p, "uUiS");
    G.uUiT = glGetUniformLocation(p, "uUiT");
    G.uUiMat = glGetUniformLocation(p, "uUiMat");
    G.uBackMat = glGetUniformLocation(p, "uBackMat");
    G.uUpS = glGetUniformLocation(p, "uUpS");
    G.uUpT = glGetUniformLocation(p, "uUpT");
    G.uCam2D = glGetUniformLocation(p, "uCam2D");
    G.uUi = glGetUniformLocation(p, "uUi");
    G.uBack = glGetUniformLocation(p, "uBack");
    G.uMask = glGetUniformLocation(p, "uMask");
    G.uBg = glGetUniformLocation(p, "uBg");
    G.uHasBack = glGetUniformLocation(p, "uHasBack");
    G.uSeg = glGetUniformLocation(p, "uSeg");
    G.uMaskTexel = glGetUniformLocation(p, "uMaskTexel");
    G.uCamAdj = glGetUniformLocation(p, "uCamAdj");
    G.uAdj = glGetUniformLocation(p, "uAdj");
    G.uVigR = glGetUniformLocation(p, "uVigR");
    G.camTex = tex2D();
    G.maskTex = tex2D();
    return true;
}

/** Malha da lente: cada vértice leva a posição na tela (distorcida) e o ponto da cena (sem distorção). */
void buildMesh(int W, int H, int eyes, float eyeW, float eyeH, const float* cx, float cy,
               float k1, float k2, bool dist) {
    const int M = 32;
    const float aspect = eyeW / eyeH, scale = 1 + k1 + k2;
    G.eyes = eyes;
    for (int e = 0; e < eyes; e++) {
        auto& P = G.pos[e];
        auto& S = G.scene[e];
        P.clear(); S.clear();
        P.reserve((M + 1) * (M + 1) * 2);
        S.reserve((M + 1) * (M + 1) * 2);
        for (int j = 0; j <= M; j++) {
            for (int i = 0; i <= M; i++) {
                float x = (i / (float) M * 2 - 1) * aspect, y = j / (float) M * 2 - 1;
                float f = 1;
                if (dist) {
                    float r2 = x * x + y * y;
                    f = scale / (1 + k1 * r2 + k2 * r2 * r2);
                }
                float px = cx[e] + x * f * eyeH / 2, py = cy + y * f * eyeH / 2;
                P.push_back(px / W * 2 - 1);
                P.push_back(1 - py / H * 2);
                S.push_back(x / 2);
                S.push_back(y / 2);
            }
        }
    }
    G.idx.clear();
    G.idx.reserve(M * M * 6);
    for (int j = 0; j < M; j++) {
        for (int i = 0; i < M; i++) {
            uint16_t a = (uint16_t) (j * (M + 1) + i), b = a + 1, c = (uint16_t) (a + M + 1), d = c + 1;
            G.idx.insert(G.idx.end(), {a, c, b, b, c, d});
        }
    }
}

/** Envia pixels RGBA para uma textura (com cópia por linha se houver sobra no fim de cada linha). */
void uploadRgba(GLuint tex, int& curW, int& curH, const uint8_t* px, int w, int h, int stride) {
    const uint8_t* data = px;
    if (stride != w * 4) {
        G.rowCopy.resize((size_t) w * h * 4);
        for (int y = 0; y < h; y++) memcpy(&G.rowCopy[(size_t) y * w * 4], px + (size_t) y * stride, (size_t) w * 4);
        data = G.rowCopy.data();
    }
    glBindTexture(GL_TEXTURE_2D, tex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    if (w == curW && h == curH) glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, data);
    else {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        curW = w; curH = h;
    }
}

/* Parâmetros da composição (vêm num float[] do Java):
 *  0 n (olhos)   1 aspecto do olho   2 deslocamento olho esq.   3 deslocamento olho dir.
 *  4..6 uS   7..9 uT   10..13 ajuste da câmera   14..17 ajuste final   18 W   19 H */
void composite(GLuint uiTex, const float* uiMat, GLuint backTex, const float* backMat,
               int bgMode, bool useBack, int seg, const float* p) {
    if (!G.prog || G.eyes == 0) return;
    const int n = (int) p[0];
    const float aspect = p[1];
    const int W = (int) p[18], H = (int) p[19];

    glUseProgram(G.prog);
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, G.camTex);
    glUniform1i(G.uCam2D, 1);
    glActiveTexture(GL_TEXTURE4);
    glBindTexture(GL_TEXTURE_2D, G.maskTex);
    glUniform1i(G.uMask, 4);
    glActiveTexture(GL_TEXTURE2);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, uiTex);
    glUniform1i(G.uUi, 2);
    glActiveTexture(GL_TEXTURE3);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, useBack ? backTex : uiTex);
    glUniform1i(G.uBack, 3);

    glUniform3f(G.uUiT, 0, -1, .5f);
    glUniform3f(G.uUpS, p[4], p[5], p[6]);
    glUniform3f(G.uUpT, p[7], p[8], p[9]);
    glUniform1i(G.uBg, bgMode);
    glUniform1i(G.uHasBack, useBack ? 1 : 0);
    glUniform1i(G.uSeg, seg);
    glUniform2f(G.uMaskTexel, 1.f / (G.maskW > 0 ? G.maskW : 1), 1.f / (G.maskH > 0 ? G.maskH : 1));
    glUniform4f(G.uCamAdj, p[10], p[11], p[12], p[13]);
    glUniform4f(G.uAdj, p[14], p[15], p[16], p[17]);
    glUniform1f(G.uVigR, .62f);
    glUniformMatrix4fv(G.uUiMat, 1, GL_FALSE, uiMat);
    glUniformMatrix4fv(G.uBackMat, 1, GL_FALSE, useBack ? backMat : uiMat);

    glEnableVertexAttribArray((GLuint) G.aPos);
    glEnableVertexAttribArray((GLuint) G.aScene);
    if (n == 2) glEnable(GL_SCISSOR_TEST);
    for (int e = 0; e < n && e < G.eyes; e++) {
        if (n == 2) glScissor(e == 0 ? 0 : W / 2, 0, W / 2, H);
        glUniform3f(G.uUiS, 1.f / aspect, 0, .5f - p[2 + e] / aspect);
        glVertexAttribPointer((GLuint) G.aPos, 2, GL_FLOAT, GL_FALSE, 0, G.pos[e].data());
        glVertexAttribPointer((GLuint) G.aScene, 2, GL_FLOAT, GL_FALSE, 0, G.scene[e].data());
        glDrawElements(GL_TRIANGLES, (GLsizei) G.idx.size(), GL_UNSIGNED_SHORT, G.idx.data());
    }
    glDisableVertexAttribArray((GLuint) G.aPos);
    glDisableVertexAttribArray((GLuint) G.aScene);
    if (n == 2) {
        glScissor(W / 2 - 1, 0, 2, (int) (H * .75f));
        glClearColor(.165f, .173f, .2f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
    }
    glActiveTexture(GL_TEXTURE0);
}

/* ===================== Rastreamento da cabeça ===================== */

struct Head {
    std::atomic<bool> running{false};
    std::atomic<bool> has{false};
    std::atomic<bool> recenter{false};
    std::atomic<int> rotation{1};          // Surface.ROTATION_90 = 1, ROTATION_270 = 3
    std::atomic<float> yaw{0}, pitch{0}, roll{0};
    float yaw0 = 0;
    pthread_t thread{};
    std::mutex lock;
} H;

float wrapAngle(float a) {
    while (a > (float) M_PI) a -= (float) (2 * M_PI);
    while (a < (float) -M_PI) a += (float) (2 * M_PI);
    return a;
}

/** Mesma conta do SensorManager.getRotationMatrixFromVector + orientação para o celular deitado. */
void onRotation(const float* v) {
    float q1 = v[0], q2 = v[1], q3 = v[2], q0 = v[3];
    float n2 = q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3;
    if (n2 < .5f || n2 > 1.5f) q0 = std::sqrt(std::fmax(0.f, 1 - q1 * q1 - q2 * q2 - q3 * q3));
    float sq1 = 2 * q1 * q1, sq2 = 2 * q2 * q2, sq3 = 2 * q3 * q3;
    float q1q2 = 2 * q1 * q2, q3q0 = 2 * q3 * q0, q1q3 = 2 * q1 * q3, q2q0 = 2 * q2 * q0, q2q3 = 2 * q2 * q3, q1q0 = 2 * q1 * q0;
    float R[9] = {
        1 - sq2 - sq3, q1q2 - q3q0, q1q3 + q2q0,
        q1q2 + q3q0, 1 - sq1 - sq3, q2q3 - q1q0,
        q1q3 - q2q0, q2q3 + q1q0, 1 - sq1 - sq2};
    float fx = -R[2], fy = -R[5], fz = -R[8];
    float ux, uz, rz;
    if (H.rotation.load() == 3) { ux = -R[0]; uz = -R[6]; rz = R[7]; }
    else { ux = R[0]; uz = R[6]; rz = -R[7]; }
    (void) ux;
    float y = std::atan2(fx, fy);
    float p = std::asin(std::fmax(-1.f, std::fmin(1.f, fz)));
    float r = std::atan2(-rz, uz);
    if (!H.has.load() || H.recenter.exchange(false)) { H.yaw0 = y; H.has = true; }
    H.yaw = wrapAngle(y - H.yaw0);
    H.pitch = p;
    H.roll = r;
}

void* headLoop(void*) {
    ALooper* looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    ASensorManager* sm = ASensorManager_getInstance();
    const ASensor* s = sm ? ASensorManager_getDefaultSensor(sm, ASENSOR_TYPE_GAME_ROTATION_VECTOR) : nullptr;
    if (!s && sm) s = ASensorManager_getDefaultSensor(sm, ASENSOR_TYPE_ROTATION_VECTOR);
    if (!s) { H.running = false; return nullptr; }
    ASensorEventQueue* q = ASensorManager_createEventQueue(sm, looper, 1, nullptr, nullptr);
    if (!q) { H.running = false; return nullptr; }
    ASensorEventQueue_enableSensor(q, s);
    ASensorEventQueue_setEventRate(q, s, 5000);        // 200 Hz
    while (H.running.load()) {
        int ident = ALooper_pollOnce(100, nullptr, nullptr, nullptr);
        if (ident == 1) {
            ASensorEvent ev[8];
            ssize_t got;
            while ((got = ASensorEventQueue_getEvents(q, ev, 8)) > 0) onRotation(ev[got - 1].data);
        }
    }
    ASensorEventQueue_disableSensor(q, s);
    ASensorManager_destroyEventQueue(sm, q);
    return nullptr;
}

bool headStart(int rotation) {
    std::lock_guard<std::mutex> g(H.lock);
    H.rotation = rotation;
    if (H.running.load()) return true;
    H.running = true;
    H.has = false;
    if (pthread_create(&H.thread, nullptr, headLoop, nullptr) != 0) { H.running = false; return false; }
    return true;
}

void headStop() {
    std::lock_guard<std::mutex> g(H.lock);
    if (!H.running.load()) return;
    H.running = false;
    pthread_join(H.thread, nullptr);
}

}  // namespace

/* ===================== JNI ===================== */

#define JNI(ret, name) extern "C" JNIEXPORT ret JNICALL Java_br_visaobox_app_NativeVr_##name

JNI(jboolean, init)(JNIEnv*, jclass) { return init() ? JNI_TRUE : JNI_FALSE; }

JNI(jstring, error)(JNIEnv* env, jclass) { return env->NewStringUTF(G.err.c_str()); }

JNI(void, setMesh)(JNIEnv*, jclass, jint W, jint Hh, jint eyes, jfloat eyeW, jfloat eyeH,
                   jfloat cx0, jfloat cx1, jfloat cy, jfloat k1, jfloat k2, jboolean dist) {
    const float cx[2] = {cx0, cx1};
    buildMesh(W, Hh, eyes, eyeW, eyeH, cx, cy, k1, k2, dist == JNI_TRUE);
}

JNI(jboolean, uploadCamera)(JNIEnv* env, jclass, jobject bitmap) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    void* px = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &px) != ANDROID_BITMAP_RESULT_SUCCESS || !px) return JNI_FALSE;
    uploadRgba(G.camTex, G.camW, G.camH, (const uint8_t*) px, (int) info.width, (int) info.height, (int) info.stride);
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

JNI(void, uploadMask)(JNIEnv* env, jclass, jbyteArray data, jint w, jint h) {
    if (!data || w <= 0 || h <= 0 || env->GetArrayLength(data) < w * h) return;
    void* px = env->GetPrimitiveArrayCritical(data, nullptr);
    if (!px) return;
    glBindTexture(GL_TEXTURE_2D, G.maskTex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    if (w == G.maskW && h == G.maskH) glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_LUMINANCE, GL_UNSIGNED_BYTE, px);
    else {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, w, h, 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, px);
        G.maskW = w; G.maskH = h;
    }
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    env->ReleasePrimitiveArrayCritical(data, px, JNI_ABORT);
}

JNI(void, composite)(JNIEnv* env, jclass, jint uiTex, jfloatArray uiMat, jint backTex, jfloatArray backMat,
                     jint bgMode, jboolean useBack, jint seg, jfloatArray params) {
    float ui[16], back[16], p[20];
    env->GetFloatArrayRegion(uiMat, 0, 16, ui);
    env->GetFloatArrayRegion(backMat, 0, 16, back);
    env->GetFloatArrayRegion(params, 0, 20, p);
    composite((GLuint) uiTex, ui, (GLuint) backTex, back, bgMode, useBack == JNI_TRUE, seg, p);
}

JNI(jboolean, headStart)(JNIEnv*, jclass, jint rotation) { return headStart(rotation) ? JNI_TRUE : JNI_FALSE; }

JNI(void, headStop)(JNIEnv*, jclass) { headStop(); }

JNI(void, headRecenter)(JNIEnv*, jclass) { H.recenter = true; }

JNI(jboolean, headGet)(JNIEnv* env, jclass, jfloatArray out) {
    float v[3] = {H.yaw.load(), H.pitch.load(), H.roll.load()};
    env->SetFloatArrayRegion(out, 0, 3, v);
    return H.has.load() && H.running.load() ? JNI_TRUE : JNI_FALSE;
}
