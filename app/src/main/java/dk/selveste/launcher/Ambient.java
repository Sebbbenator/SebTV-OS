package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * Temaets stemning over fotoet og under rækkerne: Jarvis' sigtekorn og gitter, Spider-Mans spind i
 * hjørnerne, lyn over Gotham, solen bag Superman, biografens fløjlstæpper, julelys langs toppen,
 * bølger i bunden af Havbrise og et stjerneskud på Nattehimlen. Står stille ved effektniveau Let og Ingen.
 */
final class Ambient extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float d;
    private final long start = System.currentTimeMillis();
    private final Random rnd = new Random();
    private boolean animate = true;
    private boolean running = true;

    Ambient(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
    }

    void setAnimate(boolean a) {
        animate = a;
        invalidate();
    }

    void setRunning(boolean r) {
        running = r;
        if (r) invalidate();
    }

    /** Millisekunder til næste tegning, eller 0 hvis billedet står stille. */
    private long nextFrame(float t) {
        switch (Skin.key) {
            case "batman": {
                float cycle = t % 40f;
                return cycle > 38.5f && cycle < 39.5f ? 40 : 500;
            }
            case "natte": {
                float cycle = t % 25f;
                return cycle < 1.25f || cycle > 24.9f ? 40 : 500;
            }
            case "jul":
                return 667;
            default:
                return 0;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float t = animate ? (System.currentTimeMillis() - start) / 1000f : 0f;
        p.setShader(null);
        p.setShadowLayer(0, 0, 0, 0);
        switch (Skin.key) {
            case "jarvis": jarvis(c, w, h, t); break;
            case "ironman": ironman(c, w, h, t); break;
            case "spiderman": webs(c, w, h); break;
            case "batman": gotham(c, w, h, t); break;
            case "superman": sun(c, w, h, t); break;
            case "biograf": cinema(c, w, h, t); break;
            case "minimal": rulers(c, w, h); break;
            case "efteraar": vignette(c, w, h, Color.parseColor("#3A1A06"), 0.55f); break;
            case "rose": vignette(c, w, h, Color.parseColor("#3D1424"), 0.5f); break;
            case "hygge": candlelight(c, w, h, t); break;
            case "salvie": dapple(c, w, h, t); break;
            case "natte": shootingStar(c, w, h, t); break;
            case "jul": lights(c, w, h, t); break;
            case "hav": waves(c, w, h, t); break;
        }
        // Stemningen står stille det meste af tiden, så forsiden ikke hakker på sticks: kun lyn,
        // stjerneskud og julelysenes blink tegnes igen, og kun når de faktisk sker
        long next = nextFrame(t);
        if (animate && running && next > 0 && isShown()) postInvalidateDelayed(next);
    }

    // Jarvis: et svagt gitter og et sigtekorn, der drejer langsomt i hjørnet
    private void jarvis(Canvas c, float w, float h, float t) {
        int cyan = Color.parseColor("#4FD8FF");
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(HudView.withAlpha(cyan, 0.05f));
        float step = 64 * d;
        for (float x = 0; x < w; x += step) c.drawLine(x, 0, x, h, p);
        for (float y = 0; y < h; y += step) c.drawLine(0, y, w, y, p);
        float cx = w - 120 * d, cy = 110 * d, R = 70 * d;
        p.setStrokeWidth(2 * d);
        p.setColor(HudView.withAlpha(cyan, 0.3f));
        c.save();
        c.rotate(t * 12, cx, cy);
        for (int i = 0; i < 3; i++) c.drawArc(cx - R, cy - R, cx + R, cy + R, i * 120, 70, false, p);
        c.restore();
        c.save();
        c.rotate(-t * 20, cx, cy);
        float r2 = R * 0.7f;
        p.setStrokeWidth(d);
        for (int i = 0; i < 12; i++) c.drawArc(cx - r2, cy - r2, cx + r2, cy + r2, i * 30, 14, false, p);
        c.restore();
    }

    // Iron Man: buereaktorens skær i bunden og to guldbuer, der drejer
    private void ironman(Canvas c, float w, float h, float t) {
        p.setStyle(Paint.Style.FILL);
        float pulse = 0.8f + 0.2f * (float) Math.sin(t * 1.6);
        p.setShader(new RadialGradient(w / 2f, h * 1.05f, h * 0.55f, new int[]{HudView.withAlpha(Color.parseColor("#A8ECFF"), 0.22f * pulse),
                Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.4f, w, h, p);
        p.setShader(null);
        float cx = w - 110 * d, cy = 100 * d, R = 62 * d;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setColor(HudView.withAlpha(Color.parseColor("#F2C35A"), 0.35f));
        c.save();
        c.rotate(t * 15, cx, cy);
        c.drawArc(cx - R, cy - R, cx + R, cy + R, 0, 110, false, p);
        c.drawArc(cx - R, cy - R, cx + R, cy + R, 180, 110, false, p);
        c.restore();
    }

    // Spider-Man: spind i øverste venstre og nederste højre hjørne
    private void webs(Canvas c, float w, float h) {
        web(c, 0, 0, 0, Math.min(w, h) * 0.42f);
        web(c, w, h, 180, Math.min(w, h) * 0.32f);
    }

    private void web(Canvas c, float cx, float cy, float base, float R) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f * d);
        p.setColor(Color.argb(80, 255, 255, 255));
        int spokes = 7;
        for (int i = 0; i < spokes; i++) {
            double a = Math.toRadians(base + i * 90.0 / (spokes - 1));
            c.drawLine(cx, cy, cx + (float) Math.cos(a) * R, cy + (float) Math.sin(a) * R, p);
        }
        for (int ring = 1; ring <= 5; ring++) {
            float rr = R * ring / 5.4f;
            path.reset();
            for (int i = 0; i < spokes; i++) {
                double a = Math.toRadians(base + i * 90.0 / (spokes - 1));
                float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                if (i == 0) path.moveTo(x, y);
                else {
                    double am = Math.toRadians(base + (i - 0.5) * 90.0 / (spokes - 1));
                    path.quadTo(cx + (float) Math.cos(am) * rr * 0.86f, cy + (float) Math.sin(am) * rr * 0.86f, x, y);
                }
            }
            c.drawPath(path, p);
        }
    }

    // Batman: tåge der driver i bunden og et lyn over byen en gang imellem
    private void gotham(Canvas c, float w, float h, float t) {
        p.setStyle(Paint.Style.FILL);
        float drift = (float) Math.sin(t * 0.15) * w * 0.1f;
        p.setShader(new LinearGradient(0, h * 0.55f, 0, h, new int[]{Color.TRANSPARENT, Color.argb(110, 40, 44, 52)}, null, Shader.TileMode.CLAMP));
        c.save();
        c.translate(drift, 0);
        c.drawRect(-w * 0.2f, h * 0.55f, w * 1.2f, h, p);
        c.restore();
        p.setShader(null);
        // Et lyn cirka hvert 40. sekund: to hurtige blink
        float cycle = t % 40f;
        if (cycle > 38.6f && cycle < 39.4f) {
            float k = cycle < 38.75f ? 1f : cycle < 38.9f ? 0f : cycle < 39.0f ? 0.7f : Math.max(0, 1 - (cycle - 39.0f) / 0.4f) * 0.5f;
            p.setColor(Color.argb((int) (90 * k), 220, 230, 255));
            c.drawRect(0, 0, w, h, p);
        }
    }

    // Superman: solen øverst til højre, der gløder og sender stråler
    private void sun(Canvas c, float w, float h, float t) {
        float cx = w * 0.88f, cy = h * 0.08f, R = h * 0.55f * (1f + 0.04f * (float) Math.sin(t * 0.8));
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, R, new int[]{Color.argb(120, 255, 236, 170), Color.argb(40, 255, 160, 60), Color.TRANSPARENT},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R, p);
        p.setShader(null);
        p.setStrokeWidth(3 * d);
        for (int i = 0; i < 7; i++) {
            double a = Math.toRadians(100 + i * 14 + Math.sin(t * 0.3 + i) * 3);
            p.setColor(Color.argb(28, 255, 230, 160));
            c.drawLine(cx, cy, cx + (float) Math.cos(a) * R * 1.3f, cy + (float) Math.sin(a) * R * 1.3f, p);
        }
    }

    // Biograf: røde fløjlstæpper i siderne, mørke hjørner og en svag filmkornet flimren
    private void cinema(Canvas c, float w, float h, float t) {
        float cw = 46 * d;
        p.setStyle(Paint.Style.FILL);
        int[] fold = {Color.parseColor("#3A0508"), Color.parseColor("#8A1018"), Color.parseColor("#4A070C"),
                Color.parseColor("#9E1620"), Color.parseColor("#3A0508")};
        p.setShader(new LinearGradient(0, 0, cw, 0, fold, null, Shader.TileMode.MIRROR));
        c.drawRect(0, 0, cw, h, p);
        p.setShader(new LinearGradient(w - cw, 0, w, 0, fold, null, Shader.TileMode.MIRROR));
        c.drawRect(w - cw, 0, w, h, p);
        p.setShader(new LinearGradient(0, 0, 0, 28 * d, new int[]{Color.parseColor("#5E0A10"), Color.parseColor("#2A0306")}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, 28 * d, p);
        p.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f, new int[]{Color.TRANSPARENT, Color.argb(150, 0, 0, 0)},
                new float[]{0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        if (animate) {
            rnd.setSeed((long) (t * 12));
            p.setColor(Color.argb(40, 255, 255, 255));
            for (int i = 0; i < 24; i++) c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, rnd.nextFloat() * 1.5f * d, p);
        }
    }

    // Minimal: tynde hjælpelinjer som på en tegning
    private void rulers(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(HudView.withAlpha(Ui.TEXT, 0.12f));
        c.drawLine(40 * d, 0, 40 * d, h, p);
        c.drawLine(0, 24 * d, w, 24 * d, p);
        c.drawLine(w - 40 * d, 0, w - 40 * d, h, p);
    }

    private void vignette(Canvas c, float w, float h, int color, float a) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(w / 2f, h * 0.45f, Math.max(w, h) * 0.75f, new int[]{Color.TRANSPARENT, HudView.withAlpha(color, a)},
                new float[]{0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    // Hygge: varmt, blafrende stearinlysskær fra bunden af skærmen
    private void candlelight(Canvas c, float w, float h, float t) {
        float f = 0.8f + 0.2f * (float) (Math.sin(t * 5.3) * 0.5 + Math.sin(t * 8.9) * 0.3 + Math.sin(t * 2.1) * 0.2);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(w * 0.5f, h * 1.15f, h * 0.9f, new int[]{Color.argb((int) (95 * f), 255, 160, 60), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        vignette(c, w, h, Color.parseColor("#1A0D05"), 0.55f);
    }

    // Salvie: solpletter, der glider langsomt hen over skærmen som gennem blade
    private void dapple(Canvas c, float w, float h, float t) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 6; i++) {
            float x = (w * (0.1f + i * 0.17f) + (float) Math.sin(t * 0.07 + i) * 60 * d);
            float y = h * (0.15f + (i % 3) * 0.25f) + (float) Math.cos(t * 0.05 + i * 2) * 40 * d;
            float R = (120 + (i % 3) * 50) * d;
            p.setShader(new RadialGradient(x, y, R, new int[]{Color.argb(34, 255, 252, 230), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, R, p);
        }
        p.setShader(null);
    }

    // Nattehimmel: et stjerneskud cirka hvert 25. sekund
    private void shootingStar(Canvas c, float w, float h, float t) {
        float cycle = t % 25f;
        if (cycle > 1.2f) return;
        float k = cycle / 1.2f;
        int n = (int) (t / 25f);
        float sx = w * (0.25f + (n * 37 % 50) / 100f), sy = h * (0.06f + (n * 13 % 20) / 100f);
        float x = sx + k * w * 0.25f, y = sy + k * h * 0.12f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setShader(new LinearGradient(x - w * 0.08f, y - h * 0.04f, x, y, new int[]{Color.TRANSPARENT,
                Color.argb((int) (230 * (1 - k)), 255, 255, 255)}, null, Shader.TileMode.CLAMP));
        c.drawLine(x - w * 0.08f, y - h * 0.04f, x, y, p);
        p.setShader(null);
    }

    // Jul: en lyskæde langs toppen, hvor pærerne blinker skiftevis
    private void lights(Canvas c, float w, float h, float t) {
        int[] colors = {Color.parseColor("#FF4D4D"), Color.parseColor("#FFD24D"), Color.parseColor("#4DD27A"), Color.parseColor("#4DA6FF")};
        float span = 150 * d, sag = 26 * d, top = 4 * d;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * d);
        p.setColor(Color.parseColor("#1E2A1E"));
        path.reset();
        path.moveTo(-10, top);
        for (float x = 0; x < w + span; x += span) path.quadTo(x + span / 2f, top + sag * 2, x + span, top);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        int i = 0;
        for (float x = 0; x < w + span; x += span) {
            for (int k = 1; k <= 4; k++, i++) {
                float u = k / 5f;
                float bx = x + span * u;
                float by = (1 - u) * (1 - u) * top + 2 * (1 - u) * u * (top + sag * 2) + u * u * top;
                boolean on = ((int) (t * 1.5f) + i) % 3 != 0;
                int col = colors[i % colors.length];
                p.setColor(on ? col : HudView.withAlpha(col, 0.35f));
                if (on) p.setShadowLayer(10 * d, 0, 0, col);
                c.drawOval(bx - 4 * d, by + 2 * d, bx + 4 * d, by + 14 * d, p);
                p.setShadowLayer(0, 0, 0, 0);
            }
        }
    }

    // Hav: tre gennemsigtige bølger, der ruller i bunden
    private void waves(Canvas c, float w, float h, float t) {
        p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 3; k++) {
            float base = h - (34 + k * 22) * d, amp = (10 - k * 2) * d, len = (260 + k * 90) * d;
            path.reset();
            path.moveTo(0, h);
            for (float x = 0; x <= w + 8 * d; x += 8 * d) {
                path.lineTo(x, base + (float) Math.sin(x / len * 2 * Math.PI + t * (0.6f + k * 0.25f) + k * 2) * amp);
            }
            path.lineTo(w, h);
            path.close();
            p.setColor(Color.argb(40 - k * 8, 156, 224, 230));
            c.drawPath(path, p);
        }
    }
}
