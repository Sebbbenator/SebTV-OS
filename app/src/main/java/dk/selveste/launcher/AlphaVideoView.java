package dk.selveste.launcher;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Afspiller et videoklip oven på forsiden med gennemsigtighed. Klippet laves som "stacked alpha":
 * billedet øverst og en sort/hvid maske nederst (dobbelt så høj som bred-format). Et almindeligt klip
 * (bredere end højt) vises bare som det er, hen over hele skærmen.
 * Ingen biblioteker: MediaPlayer tegner i en OpenGL tekstur, og en lille shader bruger masken som alpha.
 */
final class AlphaVideoView extends GLSurfaceView {

    interface Listener {
        /** Kaldes på hovedtråden, når klippet er spillet færdigt eller fejlede. */
        void done();
    }

    private final File clip;
    private final Listener listener;
    private MediaPlayer player;
    private boolean finished;

    AlphaVideoView(Context context, File clip, Listener listener) {
        super(context);
        this.clip = clip;
        this.listener = listener;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        setRenderer(new Renderer());
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    /** Hvor langt klippet er (0..1), så forsiden kan skifte under blitzet. */
    float progress() {
        MediaPlayer p = player;
        try {
            return p == null || p.getDuration() <= 0 ? 0f : p.getCurrentPosition() / (float) p.getDuration();
        } catch (Exception e) {
            return 0f;
        }
    }

    void stop() {
        finish();
    }

    private void finish() {
        post(() -> {
            if (finished) return;
            finished = true;
            MediaPlayer p = player;
            player = null;
            if (p != null) {
                try {
                    p.release();
                } catch (Exception ignored) {
                }
            }
            listener.done();
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        MediaPlayer p = player;
        player = null;
        if (p != null) {
            try {
                p.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static final String VERTEX =
            "attribute vec4 aPos;\n"
                    + "attribute vec2 aTex;\n"
                    + "varying vec2 vTex;\n"
                    + "void main() { gl_Position = aPos; vTex = aTex; }\n";

    /** stacked = 1: farve fra øverste halvdel, alpha fra masken i nederste. */
    private static final String FRAGMENT =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float;\n"
                    + "uniform samplerExternalOES uTex;\n"
                    + "uniform mat4 uSt;\n"
                    + "uniform float uStacked;\n"
                    + "varying vec2 vTex;\n"
                    + "void main() {\n"
                    + "  if (uStacked > 0.5) {\n"
                    + "    vec2 top = vec2(vTex.x, vTex.y * 0.5 + 0.5);\n"
                    + "    vec2 bottom = vec2(vTex.x, vTex.y * 0.5);\n"
                    + "    vec3 c = texture2D(uTex, (uSt * vec4(top, 0.0, 1.0)).xy).rgb;\n"
                    + "    float a = texture2D(uTex, (uSt * vec4(bottom, 0.0, 1.0)).xy).r;\n"
                    + "    gl_FragColor = vec4(c * a, a);\n"
                    + "  } else {\n"
                    + "    gl_FragColor = vec4(texture2D(uTex, (uSt * vec4(vTex, 0.0, 1.0)).xy).rgb, 1.0);\n"
                    + "  }\n"
                    + "}\n";

    private final class Renderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
        private final float[] st = new float[16];
        private final FloatBuffer quad;
        private SurfaceTexture texture;
        private int program, tex, aPos, aTex, uSt, uStacked;
        private volatile boolean frame;
        private volatile boolean stacked;

        Renderer() {
            // x, y, u, v (to trekanter som strimmel)
            float[] v = {-1, -1, 0, 0, 1, -1, 1, 0, -1, 1, 0, 1, 1, 1, 1, 1};
            quad = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            quad.put(v).position(0);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            program = link(VERTEX, FRAGMENT);
            aPos = GLES20.glGetAttribLocation(program, "aPos");
            aTex = GLES20.glGetAttribLocation(program, "aTex");
            uSt = GLES20.glGetUniformLocation(program, "uSt");
            uStacked = GLES20.glGetUniformLocation(program, "uStacked");
            int[] t = new int[1];
            GLES20.glGenTextures(1, t, 0);
            tex = t[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            texture = new SurfaceTexture(tex);
            texture.setOnFrameAvailableListener(this);
            Surface surface = new Surface(texture);
            MediaPlayer p = new MediaPlayer();
            try {
                p.setDataSource(clip.getAbsolutePath());
                p.setSurface(surface);
                p.setOnVideoSizeChangedListener((mp, w, h) -> stacked = h > w);
                p.setOnCompletionListener(mp -> finish());
                p.setOnErrorListener((mp, what, extra) -> {
                    ErrorLog.add("Iron Man klip · kan ikke afspilles (" + what + "/" + extra + ")");
                    finish();
                    return true;
                });
                p.prepare();
                stacked = p.getVideoHeight() > p.getVideoWidth();
                player = p;
                p.start();
            } catch (Exception e) {
                ErrorLog.note("Iron Man klip", e);
                p.release();
                finish();
            } finally {
                surface.release();
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            if (frame) {
                frame = false;
                texture.updateTexImage();
                texture.getTransformMatrix(st);
            }
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
            GLES20.glUniformMatrix4fv(uSt, 1, false, st, 0);
            GLES20.glUniform1f(uStacked, stacked ? 1f : 0f);
            quad.position(0);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(aPos);
            quad.position(2);
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(aTex);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            frame = true;
            requestRender();
        }

        private int link(String vs, String fs) {
            int v = compile(GLES20.GL_VERTEX_SHADER, vs);
            int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, v);
            GLES20.glAttachShader(p, f);
            GLES20.glLinkProgram(p);
            return p;
        }

        private int compile(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) ErrorLog.add("Iron Man klip · shader: " + GLES20.glGetShaderInfoLog(s));
            return s;
        }
    }
}
