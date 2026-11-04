package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Det temaet står på: avispapir, tegneseriepapir, en kalket væg, biografens fløjlstæpper, julekalenderens
 * røde karton med guldkant, Batcomputerens sorte skærm eller Jarvis' gitter. Tegnes én gang i en
 * bitmap og står stille bagefter.
 */
final class Backdrop extends View {

    private final float density;
    private Bitmap cache;
    private final String skin;

    Backdrop(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        skin = Skin.key;
    }

    /** Temaer, hvor baggrundsfotoet ikke hører til (papir, væg, karton). */
    static boolean hidesPhoto() {
        switch (Skin.key) {
            case "superman": case "spiderman": case "biograf": case "hygge": case "jul": case "batman": return true;
            default: return false;
        }
    }

    private float d(float v) {
        return v * density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cache = null;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (cache == null) {
            try {
                cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                paint(new Canvas(cache), w, h);
            } catch (OutOfMemoryError e) {
                paint(c, w, h);
                return;
            }
        }
        c.drawBitmap(cache, 0, 0, null);
    }

    /** Giver bitmappen fri; den tegnes igen næste gang skærmen vises. */
    void trim() {
        cache = null;
        invalidate();
    }

    void paint(Canvas c, int w, int h) {
        draw(getContext(), skin, c, w, h, density);
    }

    /** Tegner temaets baggrund; bruges også bag indstillingerne. */
    static void draw(Context ctx, String skin, Canvas c, int w, int h, float density) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        switch (skin) {
            case "superman": {
                p.setShader(new LinearGradient(0, 0, w, h, Color.parseColor("#F4EFE3"), Color.parseColor("#E6DDC8"), Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(new RadialGradient(w * 0.3f, h * 0.2f, w * 0.7f, Color.argb(60, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                break;
            }
            case "spiderman": {
                c.drawColor(Color.parseColor("#F6EDD3"));
                p.setShader(Stage.dots(density, Color.argb(14, 0, 0, 0), 10, 1.4f));
                c.drawRect(0, 0, w, h, p);
                break;
            }
            case "batman": {
                c.drawColor(Color.parseColor("#060708"));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1, density * 0.8f));
                p.setColor(Color.argb(10, 255, 255, 255));
                long seed = 7;
                for (int i = 0; i < 160; i++) {
                    seed = (seed * 9301 + 49297) % 233280;
                    float x = w * (seed / 233280f);
                    seed = (seed * 9301 + 49297) % 233280;
                    float y = h * (seed / 233280f);
                    float len = density * (30 + 50 * (seed % 7) / 7f);
                    c.drawLine(x, y, x - len * 0.24f, y + len, p);
                }
                p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(w * 0.85f, h * 0.45f, w * 0.6f, Color.argb(26, 245, 197, 24), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                break;
            }
            case "jarvis": {
                // Fotoet ligger bagved; her lægges mørke, gitter, lys og hjørneklammer over
                c.drawColor(Color.argb(150, 2, 7, 11));
                p.setShader(new RadialGradient(w * 0.17f, h * 0.48f, w * 0.42f, Color.argb(56, 40, 200, 235), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1);
                p.setColor(Color.argb(12, 94, 242, 255));
                float step = 24 * density;
                for (float x = 0; x < w; x += step) c.drawLine(x, 0, x, h, p);
                for (float y = 0; y < h; y += step) c.drawLine(0, y, w, y, p);
                p.setStrokeWidth(1.6f * density);
                p.setColor(Color.parseColor("#5EF2FF"));
                float m = 20 * density, l = 32 * density;
                c.drawLine(m, m, m + l, m, p);
                c.drawLine(m, m, m, m + l, p);
                c.drawLine(w - m, m, w - m - l, m, p);
                c.drawLine(w - m, m, w - m, m + l, p);
                c.drawLine(m, h - m, m + l, h - m, p);
                c.drawLine(m, h - m, m, h - m - l, p);
                c.drawLine(w - m, h - m, w - m - l, h - m, p);
                c.drawLine(w - m, h - m, w - m, h - m - l, p);
                break;
            }
            case "biograf": {
                c.drawColor(Color.parseColor("#140509"));
                p.setShader(new RadialGradient(w / 2f, 0, w * 0.55f, Color.argb(42, 255, 200, 120), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                float cw = 75 * density;
                curtain(c, 0, cw, h, density);
                curtain(c, w - cw, w, h, density);
                break;
            }
            case "jul": {
                p.setShader(new RadialGradient(w * 0.5f, h * 0.3f, w * 0.75f, Color.parseColor("#8A1522"), Color.parseColor("#5C0B15"), Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                int gold = Color.parseColor("#E2BD62");
                p.setColor(gold);
                p.setStrokeWidth(1.6f * density);
                float m = 12 * density;
                c.drawRect(m, m, w - m, h - m, p);
                p.setColor(Color.argb(150, 226, 189, 98));
                p.setStrokeWidth(0.8f * density);
                c.drawRect(m + 6 * density, m + 6 * density, w - m - 6 * density, h - m - 6 * density, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.parseColor("#F3D989"));
                p.setTypeface(Fonts.frauncesItalic(ctx));
                p.setTextSize(42 * density * ctx.getResources().getConfiguration().fontScale);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText("Julekalender", w / 2f, 56 * density, p);
                float tw = p.measureText("Julekalender");
                Stage.heart(c, w / 2f - tw / 2 - 40 * density, 38 * density, 46 * density, -8);
                Stage.heart(c, w / 2f + tw / 2 + 40 * density, 38 * density, 46 * density, 8);
                break;
            }
            case "hygge": {
                c.drawColor(Color.parseColor("#E6DBC9"));
                p.setShader(new RadialGradient(w * 0.3f, h * 0.25f, w * 0.55f, Color.argb(200, 239, 230, 215), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(new RadialGradient(w * 0.8f, h * 0.7f, w * 0.5f, Color.argb(160, 221, 208, 188), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f, new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(90, 80, 50, 25)},
                        new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                break;
            }
            default:
                break;
        }
    }

    private static void curtain(Canvas c, float l, float r, int h, float density) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(l, 0, l + 30 * density, 0, new int[]{Color.parseColor("#3A070C"), Color.parseColor("#6A1019"), Color.parseColor("#3A070C")},
                null, Shader.TileMode.MIRROR));
        c.drawRect(l, 0, r, h, p);
        p.setShader(new LinearGradient(0, h * 0.5f, 0, h, Color.TRANSPARENT, Color.argb(160, 0, 0, 0), Shader.TileMode.CLAMP));
        c.drawRect(l, 0, r, h, p);
        p.setShader(new LinearGradient(0, 0, 0, 40 * density, Color.argb(120, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(l, 0, r, 40 * density, p);
    }

    static RectF inset(RectF r, float d) {
        return new RectF(r.left + d, r.top + d, r.right - d, r.bottom - d);
    }
}
