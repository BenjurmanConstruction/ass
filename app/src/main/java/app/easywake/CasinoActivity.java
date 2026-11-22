package app.easywake;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Walk around the casino in 3D: a joystick on the left, drag on the right to look around,
 * walk up to a machine or table and press Play. And yes, you can punch people.
 */
public class CasinoActivity extends Activity {
    private CasinoWorld world;
    private GLSurfaceView gl;
    private Overlay overlay;
    /** The last camera, copied for the overlay to project names and speech onto. */
    private final float[] viewProj = new float[16];
    private int glW = 1, glH = 1;
    private volatile float stickX, stickY;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        world = new CasinoWorld(System.nanoTime());
        FrameLayout root = new FrameLayout(this);
        gl = new GLSurfaceView(this);
        gl.setEGLContextClientVersion(2);
        gl.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        gl.setPreserveEGLContextOnPause(true);
        gl.setRenderer(new Renderer());
        root.addView(gl);
        overlay = new Overlay(this);
        root.addView(overlay);
        setContentView(root);
        hideBars();
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gl.onResume();
        overlay.refreshJackpots = true;
    }

    @Override
    protected void onPause() {
        gl.onPause();
        super.onPause();
    }

    /** Opens the game for a spot, or says what it takes to unlock it. */
    private void play(CasinoWorld.Spot s) {
        boolean open;
        if (s.unlock == null) open = true;
        else if ("parlor".equals(s.unlock)) open = ParlorActivity.owned(this);
        else open = Tree.owned(this, s.unlock);
        if (!open) {
            String where = "parlor".equals(s.unlock)
                    ? "Unlock the Slot Parlor in My tools (" + ParlorActivity.COST + " KP)."
                    : "Unlock " + s.label + " on the skill tree first.";
            overlay.show(where);
            return;
        }
        Intent i;
        switch (s.key) {
            case "penny":
                i = new Intent(this, PennyActivity.class).putExtra("machine", s.extra);
                break;
            case "machine":
                i = new Intent(this, MachineActivity.class).putExtra("machine", s.extra);
                break;
            case "cashier":
                i = new Intent(this, WalletActivity.class);
                break;
            default:
                Class<?> c = Tree.screen(s.key);
                if (c == null) return;
                i = new Intent(this, c);
                break;
        }
        // Step back from the machine so you're not standing in it when you come back.
        synchronized (world) {
            world.player.x = s.x + (float) Math.sin(s.yaw + Math.PI) * 0.3f;
            world.player.z = s.z - (float) Math.cos(s.yaw + Math.PI) * 0.3f;
        }
        startActivity(i);
    }

    // ================================================================== drawing in 3D

    private static final String VERTEX =
            "uniform mat4 uVP; uniform mat4 uModel; uniform vec3 uTint;\n"
                    + "attribute vec3 aPos; attribute vec3 aNormal; attribute vec3 aColor;\n"
                    + "attribute vec2 aUv; attribute float aEmit; attribute float aTex;\n"
                    + "varying vec3 vWorld; varying vec3 vNormal; varying vec3 vColor;\n"
                    + "varying vec2 vUv; varying float vEmit; varying float vTex;\n"
                    + "void main() {\n"
                    + "  vec4 w = uModel * vec4(aPos, 1.0);\n"
                    + "  vWorld = w.xyz;\n"
                    + "  vNormal = mat3(uModel) * aNormal;\n"
                    + "  vColor = aColor * uTint; vUv = aUv; vEmit = aEmit; vTex = aTex;\n"
                    + "  gl_Position = uVP * w;\n"
                    + "}\n";

    /** Must match Preview.shade on the desktop. */
    private static final String FRAGMENT =
            "precision mediump float;\n"
                    + "uniform sampler2D uAtlas; uniform sampler2D uCarpet; uniform vec3 uEye;\n"
                    + "uniform float uTime;\n"
                    + "varying vec3 vWorld; varying vec3 vNormal; varying vec3 vColor;\n"
                    + "varying vec2 vUv; varying float vEmit; varying float vTex;\n"
                    + "void main() {\n"
                    + "  vec3 base = vColor;\n"
                    + "  if (vTex > 1.5) base *= texture2D(uCarpet, vUv).rgb;\n"
                    + "  else if (vTex > 0.5) base *= texture2D(uAtlas, vUv).rgb;\n"
                    + "  vec3 n = normalize(vNormal);\n"
                    + "  float h = n.y * 0.5 + 0.5;\n"
                    + "  vec3 amb = mix(vec3(0.16, 0.12, 0.11), vec3(0.36, 0.30, 0.26), h);\n"
                    + "  float dif = max(0.0, dot(n, normalize(vec3(0.25, 0.9, 0.35))));\n"
                    + "  vec2 g = vec2(vWorld.x - floor(vWorld.x / 7.0 + 0.5) * 7.0,\n"
                    + "      vWorld.z + 10.0 - floor((vWorld.z + 10.0) / 6.5 + 0.5) * 6.5);\n"
                    + "  float pool = exp(-dot(g, g) / 9.0) * 0.35;\n"
                    + "  vec3 lit = amb + dif * (vec3(0.62, 0.54, 0.44) + pool * vec3(1.0, 0.85, 0.6));\n"
                    + "  float flick = 1.0 + 0.04 * sin(uTime * 6.0 + vWorld.x * 3.0) * vEmit;\n"
                    + "  vec3 col = base * lit * (1.0 - vEmit) + base * vEmit * 1.1 * flick;\n"
                    + "  float fog = clamp((distance(vWorld, uEye) - 10.0) / 30.0, 0.0, 1.0) * 0.8;\n"
                    + "  gl_FragColor = vec4(mix(col, vec3(0.035, 0.028, 0.026), fog), 1.0);\n"
                    + "}\n";

    private static final String SHADOW_VERTEX =
            "uniform mat4 uVP; uniform vec3 uAt;\n"
                    + "attribute vec3 aPos;\n"
                    + "varying float vK;\n"
                    + "void main() {\n"
                    + "  vK = aPos.y;\n"
                    + "  gl_Position = uVP * vec4(uAt.x + aPos.x * uAt.z, 0.012, uAt.y + aPos.z * uAt.z, 1.0);\n"
                    + "}\n";
    private static final String SHADOW_FRAGMENT =
            "precision mediump float; varying float vK;\n"
                    + "void main() { gl_FragColor = vec4(0.0, 0.0, 0.0, 0.55 * pow(vK, 0.8)); }\n";

    private final class Renderer implements GLSurfaceView.Renderer {
        private int prog, shadowProg;
        private int staticVbo, cubeVbo, diskVbo, staticCount, cubeCount, diskCount;
        private int atlasTex, carpetTex;
        private final float[] view = new float[16], proj = new float[16], vp = new float[16],
                ident = new float[16], eye = new float[3];
        private long last;
        private float jackpotTimer;
        private Bitmap board;

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            prog = program(VERTEX, FRAGMENT);
            shadowProg = program(SHADOW_VERTEX, SHADOW_FRAGMENT);
            CasinoWorld.Mesh m = world.mesh;
            staticVbo = upload(m.data, m.floats);
            staticCount = m.vertices();
            CasinoWorld.Mesh cube = new CasinoWorld.Mesh();
            cube.box(0, 0, (float) Math.PI, 0.5f, 0.5f, -0.5f, 0.5f, 0xFFFFFFFF, 0);
            cube.quad(new float[]{-0.5f, -0.5f, -0.5f}, new float[]{0.5f, -0.5f, -0.5f},
                    new float[]{0.5f, -0.5f, 0.5f}, new float[]{-0.5f, -0.5f, 0.5f},
                    0xFFFFFFFF, 0);
            cubeVbo = upload(cube.data, cube.floats);
            cubeCount = cube.vertices();
            // A disc for shadows: x and z on a unit circle, y holds how dark (1 middle).
            int segs = 20;
            float[] disk = new float[segs * 9];
            for (int i = 0; i < segs; i++) {
                double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
                float[] t = {0, 1, 0, (float) Math.cos(a0), 0, (float) Math.sin(a0),
                        (float) Math.cos(a1), 0, (float) Math.sin(a1)};
                System.arraycopy(t, 0, disk, i * 9, 9);
            }
            diskVbo = upload(disk, disk.length);
            diskCount = segs * 3;

            Bitmap atlas = Bitmap.createBitmap(CasinoArt.ATLAS, CasinoArt.ATLAS,
                    Bitmap.Config.ARGB_8888);
            CasinoArt.paintAtlas(new CanvasPainter(new Canvas(atlas)), jackpots());
            atlasTex = texture(atlas, false);
            atlas.recycle();
            Bitmap carpet = Bitmap.createBitmap(CasinoArt.CARPET, CasinoArt.CARPET,
                    Bitmap.Config.ARGB_8888);
            CasinoArt.paintCarpet(new CanvasPainter(new Canvas(carpet)));
            carpetTex = texture(carpet, true);
            carpet.recycle();
            CasinoWorld.Mat.identity(ident);
            last = SystemClock.uptimeMillis();
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int w, int h) {
            GLES20.glViewport(0, 0, w, h);
            glW = w;
            glH = h;
        }

        @Override
        public void onDrawFrame(GL10 unused) {
            long now = SystemClock.uptimeMillis();
            float dt = (now - last) / 1000f;
            last = now;
            final int sounds;
            synchronized (world) {
                world.update(dt, stickX, stickY);
                sounds = world.sounds;
                world.camera(glW / (float) Math.max(1, glH), view, proj, eye);
                CasinoWorld.Mat.mul(vp, proj, view);
                System.arraycopy(vp, 0, viewProj, 0, 16);
            }
            if (sounds != 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if ((sounds & 1) != 0) Sfx.play(CasinoActivity.this, Sfx.PUNCH);
                        else Sfx.play(CasinoActivity.this, Sfx.WHOOSH);
                    }
                });
            }
            jackpotTimer -= dt;
            if (jackpotTimer <= 0 || overlay.refreshJackpots) {
                jackpotTimer = 2;
                overlay.refreshJackpots = false;
                refreshBoard();
            }

            GLES20.glClearColor(0.035f, 0.028f, 0.026f, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glDepthMask(true);

            GLES20.glUseProgram(prog);
            GLES20.glUniformMatrix4fv(loc(prog, "uVP"), 1, false, vp, 0);
            GLES20.glUniform3f(loc(prog, "uEye"), eye[0], eye[1], eye[2]);
            GLES20.glUniform1f(loc(prog, "uTime"), (now % 100000) / 1000f);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex);
            GLES20.glUniform1i(loc(prog, "uAtlas"), 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, carpetTex);
            GLES20.glUniform1i(loc(prog, "uCarpet"), 1);

            final int uModel = loc(prog, "uModel"), uTint = loc(prog, "uTint");
            GLES20.glUniformMatrix4fv(uModel, 1, false, ident, 0);
            GLES20.glUniform3f(uTint, 1, 1, 1);
            bindMain(staticVbo);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, staticCount);

            bindMain(cubeVbo);
            final float[] shadows = new float[3 * 64];
            final int[] shadowCount = {0};
            synchronized (world) {
                world.drawPeople(new CasinoWorld.PartSink() {
                    @Override
                    public void part(float[] model, int color) {
                        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
                        GLES20.glUniform3f(uTint, ((color >> 16) & 255) / 255f,
                                ((color >> 8) & 255) / 255f, (color & 255) / 255f);
                        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, cubeCount);
                    }

                    @Override
                    public void shadow(float x, float z, float r) {
                        int i = shadowCount[0];
                        if (i >= 64) return;
                        shadows[i * 3] = x;
                        shadows[i * 3 + 1] = z;
                        shadows[i * 3 + 2] = r;
                        shadowCount[0] = i + 1;
                    }
                });
            }

            // Soft shadows under everyone.
            GLES20.glUseProgram(shadowProg);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDepthMask(false);
            GLES20.glUniformMatrix4fv(loc(shadowProg, "uVP"), 1, false, vp, 0);
            int aPos = GLES20.glGetAttribLocation(shadowProg, "aPos");
            for (int k = 0; k < 6; k++) GLES20.glDisableVertexAttribArray(k);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, diskVbo);
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, 0);
            int uAt = loc(shadowProg, "uAt");
            for (int i = 0; i < shadowCount[0]; i++) {
                GLES20.glUniform3f(uAt, shadows[i * 3], shadows[i * 3 + 1], shadows[i * 3 + 2]);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, diskCount);
            }
            GLES20.glDisableVertexAttribArray(aPos);
            GLES20.glDepthMask(true);
            GLES20.glDisable(GLES20.GL_BLEND);
            overlay.postInvalidate();
        }

        /** Redraws the live jackpot board on the art sheet. */
        private void refreshBoard() {
            CasinoArt.Region r = CasinoArt.JACKPOT_BOARD;
            if (board == null) board = Bitmap.createBitmap(r.w, r.h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(board);
            c.translate(-r.x, -r.y);
            CasinoArt.paintJackpots(new CanvasPainter(c), jackpots());
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex);
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, r.x, r.y, board);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        }

        private void bindMain(int vbo) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
            int stride = CasinoWorld.Mesh.STRIDE * 4;
            attr("aPos", 3, stride, 0);
            attr("aNormal", 3, stride, 3);
            attr("aColor", 3, stride, 6);
            attr("aUv", 2, stride, 9);
            attr("aEmit", 1, stride, 11);
            attr("aTex", 1, stride, 12);
        }

        private void attr(String name, int size, int stride, int offsetFloats) {
            int a = GLES20.glGetAttribLocation(prog, name);
            if (a < 0) return;
            GLES20.glEnableVertexAttribArray(a);
            GLES20.glVertexAttribPointer(a, size, GLES20.GL_FLOAT, false, stride,
                    offsetFloats * 4);
        }
    }

    private double[] jackpots() {
        JackpotBank bank = JackpotBank.load(Prefs.getString(this, PennyActivity.BANK),
                new Random());
        return bank.meter.clone();
    }

    private static int loc(int prog, String name) {
        return GLES20.glGetUniformLocation(prog, name);
    }

    private static int program(String vs, String fs) {
        int v = shader(GLES20.GL_VERTEX_SHADER, vs);
        int f = shader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            throw new RuntimeException("Casino shader link: " + GLES20.glGetProgramInfoLog(p));
        }
        return p;
    }

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            throw new RuntimeException("Casino shader: " + GLES20.glGetShaderInfoLog(s));
        }
        return s;
    }

    private static int upload(float[] data, int floats) {
        FloatBuffer fb = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.put(data, 0, floats).position(0);
        int[] id = new int[1];
        GLES20.glGenBuffers(1, id, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, id[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, floats * 4, fb, GLES20.GL_STATIC_DRAW);
        return id[0];
    }

    private static int texture(Bitmap b, boolean repeat) {
        int[] id = new int[1];
        GLES20.glGenTextures(1, id, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        int wrap = repeat ? GLES20.GL_REPEAT : GLES20.GL_CLAMP_TO_EDGE;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        return id[0];
    }

    /** CasinoArt's painter on an Android Canvas. */
    static final class CanvasPainter implements CasinoArt.Painter {
        private final Canvas c;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        CanvasPainter(Canvas c) {
            this.c = c;
        }

        @Override
        public void rect(float x, float y, float w, float h, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawRect(x, y, x + w, y + h, p);
        }

        @Override
        public void roundRect(float x, float y, float w, float h, float rad, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            r.set(x, y, x + w, y + h);
            c.drawRoundRect(r, rad, rad, p);
        }

        @Override
        public void oval(float cx, float cy, float rx, float ry, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            r.set(cx - rx, cy - ry, cx + rx, cy + ry);
            c.drawOval(r, p);
        }

        @Override
        public void line(float x1, float y1, float x2, float y2, float width, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setColor(color);
            c.drawLine(x1, y1, x2, y2, p);
            p.setStyle(Paint.Style.FILL);
        }

        @Override
        public void vgradient(float x, float y, float w, float h, int top, int bottom) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(x, y, x, y + h, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(x, y, x + w, y + h, p);
            p.setShader(null);
        }

        @Override
        public void text(String s, float x, float y, float size, int color, boolean bold,
                         int align) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            p.setTextAlign(align == 0 ? Paint.Align.LEFT
                    : (align == 1 ? Paint.Align.CENTER : Paint.Align.RIGHT));
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(s, x, y - (fm.ascent + fm.descent) / 2, p);
        }

        @Override
        public float measure(String s, float size, boolean bold) {
            p.setTextSize(size);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            return p.measureText(s);
        }
    }

    // ================================================================== controls on top

    /** Joystick, look-drag, buttons, speech bubbles and messages, drawn over the 3D view. */
    private final class Overlay extends View {
        volatile boolean refreshJackpots;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] scratch = new float[3];
        private final float[] vpCopy = new float[16];
        private int stickId = -1, lookId = -1;
        private float stickCx, stickCy, stickPx, stickPy, lookX, lookY;
        private final RectF punchBtn = new RectF(), playBtn = new RectF(),
                backBtn = new RectF();
        private String message;
        private long messageUntil;
        private CasinoWorld.Spot spot;
        private final float d;

        Overlay(Context c) {
            super(c);
            d = getResources().getDisplayMetrics().density;
        }

        void show(String s) {
            message = s;
            messageUntil = SystemClock.uptimeMillis() + 3500;
            postInvalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float escort;
            String toast;
            synchronized (world) {
                System.arraycopy(viewProj, 0, vpCopy, 0, 16);
                spot = world.nearbySpot();
                escort = world.escort;
                toast = world.toast;
                world.toast = null;
                // Speech over heads.
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(13 * d);
                paint.setTextAlign(Paint.Align.CENTER);
                for (CasinoWorld.Person p : world.people) {
                    if (!p.active) continue;
                    float y = CasinoWorld.headY(p);
                    if (!CasinoWorld.Mat.project(vpCopy, p.x, y, p.z, w, h, scratch)) continue;
                    if (scratch[2] > 16) continue;
                    if (p.bubble != null && p.bubbleT > 0) {
                        bubble(c, p.bubble, scratch[0], scratch[1],
                                Math.min(1, p.bubbleT * 3));
                    } else if (p.name != null && scratch[2] < 7) {
                        paint.setTextSize(11 * d);
                        paint.setColor(0xB3FFFFFF);
                        c.drawText(p.name, scratch[0], scratch[1], paint);
                        paint.setTextSize(13 * d);
                    }
                }
            }
            if (toast != null) show(toast);

            // Back.
            backBtn.set(12 * d, 12 * d, 56 * d, 56 * d);
            paint.setColor(0x88000000);
            c.drawRoundRect(backBtn, 22 * d, 22 * d, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(20 * d);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("‹", backBtn.centerX(), backBtn.centerY() + 7 * d, paint);

            // Joystick.
            if (stickId >= 0) {
                paint.setColor(0x33FFFFFF);
                c.drawCircle(stickCx, stickCy, 56 * d, paint);
                paint.setColor(0x88FFFFFF);
                c.drawCircle(stickPx, stickPy, 24 * d, paint);
            } else {
                paint.setColor(0x22FFFFFF);
                c.drawCircle(100 * d, h - 100 * d, 56 * d, paint);
                paint.setColor(0x44FFFFFF);
                c.drawCircle(100 * d, h - 100 * d, 24 * d, paint);
            }

            // Punch.
            float pr = 38 * d;
            punchBtn.set(w - 24 * d - 2 * pr, h - 24 * d - 2 * pr, w - 24 * d, h - 24 * d);
            paint.setColor(0x99B71C1C);
            c.drawOval(punchBtn, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(14 * d);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("PUNCH", punchBtn.centerX(), punchBtn.centerY() + 5 * d, paint);

            // Play prompt.
            if (spot != null) {
                String label = "Play " + spot.label;
                paint.setTextSize(16 * d);
                float tw = paint.measureText(label);
                playBtn.set(w / 2f - tw / 2 - 24 * d, h - 84 * d, w / 2f + tw / 2 + 24 * d,
                        h - 32 * d);
                paint.setColor(0xE6E6B869);
                c.drawRoundRect(playBtn, 26 * d, 26 * d, paint);
                paint.setColor(0xFF1A120C);
                c.drawText(label, playBtn.centerX(), playBtn.centerY() + 6 * d, paint);
            } else {
                playBtn.setEmpty();
            }

            // Messages.
            if (message != null && SystemClock.uptimeMillis() < messageUntil) {
                paint.setTextSize(15 * d);
                float tw = paint.measureText(message);
                RectF m = new RectF(w / 2f - tw / 2 - 16 * d, 18 * d, w / 2f + tw / 2 + 16 * d,
                        58 * d);
                paint.setColor(0xDD000000);
                c.drawRoundRect(m, 20 * d, 20 * d, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawText(message, m.centerX(), m.centerY() + 5 * d, paint);
            }

            // Fade while security walks you out.
            if (escort > 0) {
                float k = escort > 0.6f ? (1.4f - escort) / 0.8f : escort / 0.6f;
                paint.setColor(((int) (Math.max(0, Math.min(1, k)) * 255) << 24));
                c.drawRect(0, 0, w, h, paint);
            }
        }

        private void bubble(Canvas c, String s, float x, float y, float alpha) {
            float tw = paint.measureText(s);
            RectF r = new RectF(x - tw / 2 - 10 * d, y - 34 * d, x + tw / 2 + 10 * d, y - 8 * d);
            paint.setColor(((int) (alpha * 0xEE) << 24) | 0x111111);
            c.drawRoundRect(r, 12 * d, 12 * d, paint);
            paint.setColor(((int) (alpha * 255) << 24) | 0xFFFFFF);
            c.drawText(s, x, r.centerY() + 5 * d, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            int idx = e.getActionIndex();
            int id = e.getPointerId(idx);
            float x = e.getX(idx), y = e.getY(idx);
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (backBtn.contains(x, y)) {
                        finish();
                        return true;
                    }
                    if (punchBtn.contains(x, y)) {
                        synchronized (world) {
                            world.punch();
                        }
                        return true;
                    }
                    if (!playBtn.isEmpty() && playBtn.contains(x, y) && spot != null) {
                        stickX = stickY = 0;
                        stickId = -1;
                        play(spot);
                        return true;
                    }
                    if (x < getWidth() * 0.45f && stickId < 0) {
                        stickId = id;
                        stickCx = stickPx = x;
                        stickCy = stickPy = y;
                    } else if (lookId < 0) {
                        lookId = id;
                        lookX = x;
                        lookY = y;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        int pid = e.getPointerId(i);
                        float px = e.getX(i), py = e.getY(i);
                        if (pid == stickId) {
                            float dx = px - stickCx, dy = py - stickCy;
                            float max = 56 * d;
                            float l = (float) Math.hypot(dx, dy);
                            if (l > max) {
                                dx *= max / l;
                                dy *= max / l;
                            }
                            stickPx = stickCx + dx;
                            stickPy = stickCy + dy;
                            stickX = dx / max;
                            stickY = dy / max;
                        } else if (pid == lookId) {
                            float dx = px - lookX, dy = py - lookY;
                            lookX = px;
                            lookY = py;
                            synchronized (world) {
                                world.orbit(dx * 0.006f, dy * 0.004f);
                            }
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (id == stickId || action == MotionEvent.ACTION_CANCEL
                            || action == MotionEvent.ACTION_UP) {
                        if (id == stickId || action != MotionEvent.ACTION_POINTER_UP) {
                            stickId = -1;
                            stickX = stickY = 0;
                        }
                    }
                    if (id == lookId || action == MotionEvent.ACTION_UP
                            || action == MotionEvent.ACTION_CANCEL) {
                        lookId = -1;
                    }
                    return true;
                default:
                    return true;
            }
        }
    }
}
