package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Det, der sker på et card i fokus, i temaets eget sprog: Jarvis' sigteklammer, Iron Mans guldramme,
 * Spider-Mans rasterprikker og spind, Batsignalets lys, Supermans fartstriber, biografens pærer,
 * en julegave med sløjfe og så videre. Ligger øverst i cardet og tegnes kun, mens det har fokus.
 */
final class Decor extends View {

    /** Temaer, der har deres egen fokus i stedet for accentrammen. */
    static boolean active() {
        switch (Skin.key) {
            case "jarvis": case "ironman": case "spiderman": case "batman": case "superman": case "biograf":
            case "minimal": case "efteraar": case "rose": case "hygge": case "salvie": case "natte": case "jul": case "hav":
                return true;
            default:
                return false;
        }
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final float d;
    private boolean focused;
    private long since;
    private final boolean animate;

    Decor(Context c, boolean animate) {
        super(c);
        this.animate = animate;
        d = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
    }

    void setFocused(boolean f) {
        focused = f;
        since = System.currentTimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!focused) return;
        float t = animate ? (System.currentTimeMillis() - since) / 1000f : 10f;
        float in = Math.min(1f, t / 0.25f);
        in = in * (2 - in);
        boolean again = paint(c, getWidth(), getHeight(), t, in);
        // Indgangen tegnes glat; det, der bliver ved (pærer, stjerner, lys), roligt, så resten ikke hakker
        if (animate && in < 1f) postInvalidateDelayed(16);
        else if (animate && again && t < 1.2f) postInvalidateDelayed(33);
        else if (animate && again) postInvalidateDelayed(120);
    }

    /**
     * Temaets fokus som en tegning, til rækkerne i indstillinger og panelerne: samme udtryk som på
     * forsidens cards, bare stille.
     */
    static android.graphics.drawable.Drawable focusDrawable(Context c) {
        Decor decor = new Decor(c, false);
        return new android.graphics.drawable.Drawable() {
            @Override
            public void draw(Canvas canvas) {
                android.graphics.Rect b = getBounds();
                canvas.save();
                canvas.translate(b.left, b.top);
                decor.paint(canvas, b.width(), b.height(), 10f, 1f);
                canvas.restore();
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter cf) {
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    /** Tegner fokus for det aktive tema. Sand, hvis den bevæger sig og skal tegnes igen. */
    boolean paint(Canvas c, float w, float h, float t, float in) {
        p.setShader(null);
        p.setShadowLayer(0, 0, 0, 0);
        boolean again = false;
        switch (Skin.key) {
            case "jarvis": again = jarvis(c, w, h, t, in); break;
            case "ironman": ironman(c, w, h, in); break;
            case "spiderman": spider(c, w, h, in); break;
            case "batman": bat(c, w, h, in); break;
            case "superman": again = superman(c, w, h, t, in); break;
            case "biograf": again = marquee(c, w, h, t); break;
            case "minimal": minimal(c, w, h, in); break;
            case "efteraar": autumn(c, w, h, in); break;
            case "rose": rose(c, w, h, in); break;
            case "hygge": again = hygge(c, w, h, t); break;
            case "salvie": sage(c, w, h, in); break;
            case "natte": again = stars(c, w, h, t, in); break;
            case "jul": present(c, w, h, in); break;
            case "hav": again = ripple(c, w, h, t); break;
        }
        return again;
    }

    // Jarvis: fire sigteklammer, der lukker sig om cardet, og en scanlinje der løber ned én gang
    private boolean jarvis(Canvas c, float w, float h, float t, float in) {
        int cyan = Color.parseColor("#4FD8FF");
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setColor(cyan);
        p.setShadowLayer(6 * d, 0, 0, cyan);
        float o = 3 * d + (1 - in) * 10 * d, L = Math.min(w, h) * 0.22f;
        corner(c, o, o, L, 1, 1);
        corner(c, w - o, o, L, -1, 1);
        corner(c, o, h - o, L, 1, -1);
        corner(c, w - o, h - o, L, -1, -1);
        p.setShadowLayer(0, 0, 0, 0);
        float scan = t / 0.7f;
        if (scan < 1f) {
            float y = h * scan;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, y - 18 * d, 0, y, new int[]{Color.TRANSPARENT, HudView.withAlpha(cyan, 0.45f)}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, y - 18 * d, w, y, p);
            p.setShader(null);
            return true;
        }
        return false;
    }

    private void corner(Canvas c, float x, float y, float L, int dx, int dy) {
        c.drawLine(x, y, x + dx * L, y, p);
        c.drawLine(x, y, x, y + dy * L, p);
    }

    // Iron Man: guldramme med afskårne hjørner og en kraftbjælke, der lader op i bunden
    private void ironman(Canvas c, float w, float h, float in) {
        float k = 14 * d, s = 2 * d;
        path.reset();
        path.moveTo(k, s);
        path.lineTo(w - k, s);
        path.lineTo(w - s, k);
        path.lineTo(w - s, h - k);
        path.lineTo(w - k, h - s);
        path.lineTo(k, h - s);
        path.lineTo(s, h - k);
        path.lineTo(s, k);
        path.close();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setShader(new LinearGradient(0, 0, w, h, new int[]{Color.parseColor("#A8701E"), Color.parseColor("#FFE7A3"),
                Color.parseColor("#C8902E")}, null, Shader.TileMode.CLAMP));
        p.setAlpha((int) (255 * in));
        c.drawPath(path, p);
        p.setShader(null);
        p.setAlpha(255);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#D42A22"));
        c.drawRect(k, h - 8 * d, k + (w - 2 * k) * in, h - 5 * d, p);
    }

    // Spider-Man: røde rasterprikker i hjørnet og et spind i det modsatte
    private void spider(Canvas c, float w, float h, float in) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(HudView.withAlpha(Color.parseColor("#E23636"), 0.85f * in));
        float step = 9 * d;
        for (float y = step / 2; y < h * 0.6f; y += step) {
            for (float x = step / 2; x < w * 0.45f; x += step) {
                float k = 1f - (x / (w * 0.45f) + y / (h * 0.6f));
                if (k <= 0) continue;
                c.drawCircle(x, y, step * 0.42f * k, p);
            }
        }
        float cx = w, cy = 0, R = Math.min(w, h) * 0.55f * in;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f * d);
        p.setColor(Color.argb(230, 255, 255, 255));
        for (int i = 0; i <= 5; i++) {
            double a = Math.toRadians(90 + i * 18);
            c.drawLine(cx, cy, cx + (float) Math.cos(a) * R, cy + (float) Math.sin(a) * R, p);
        }
        for (int ring = 1; ring <= 3; ring++) {
            float rr = R * ring / 3.4f;
            path.reset();
            for (int i = 0; i <= 5; i++) {
                double a = Math.toRadians(90 + i * 18);
                float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                if (i == 0) path.moveTo(x, y);
                else {
                    double am = Math.toRadians(90 + (i - 0.5) * 18);
                    float sag = rr * 0.86f;
                    path.quadTo(cx + (float) Math.cos(am) * sag, cy + (float) Math.sin(am) * sag, x, y);
                }
            }
            c.drawPath(path, p);
        }
    }

    // Batman: Batsignalets lys falder ned over cardet, med en skarp gul kant forneden
    private void bat(Canvas c, float w, float h, float in) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(w / 2f, -h * 0.2f, h * 1.2f, new int[]{HudView.withAlpha(Color.parseColor("#FFE9A0"), 0.42f * in),
                Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(HudView.withAlpha(Color.parseColor("#E8C64A"), in));
        c.drawRect(0, h - 3 * d, w, h, p);
    }

    // Superman: fartstriber, der suser forbi, og en rød og gul kant
    private boolean superman(Canvas c, float w, float h, float t, float in) {
        p.setStyle(Paint.Style.FILL);
        float run = Math.min(1f, t / 0.45f);
        if (run < 1f) {
            for (int i = 0; i < 4; i++) {
                float y = h * (0.2f + i * 0.2f);
                float x = w * (1.2f - run * 1.6f) + i * 18 * d;
                p.setShader(new LinearGradient(x, 0, x + w * 0.5f, 0, new int[]{Color.argb(170, 255, 255, 255), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
                c.drawRect(x, y, x + w * 0.5f, y + 2 * d, p);
            }
            p.setShader(null);
        }
        p.setColor(Color.parseColor("#D0202E"));
        c.drawRect(0, h - 6 * d, w * 0.7f * in, h, p);
        p.setColor(Color.parseColor("#FFD34D"));
        c.drawRect(w * 0.7f * in, h - 6 * d, w * in, h, p);
        return run < 1f;
    }

    // Biograf: pærer hele vejen rundt, der løber som over en biografindgang
    private boolean marquee(Canvas c, float w, float h, float t) {
        float step = 16 * d, inset = 6 * d;
        int frame = (int) (t * 8);
        int i = 0;
        p.setStyle(Paint.Style.FILL);
        float per = 2 * (w + h - 4 * inset);
        for (float s = 0; s < per; s += step, i++) {
            float x, y;
            if (s < w - 2 * inset) { x = inset + s; y = inset; }
            else if (s < w + h - 4 * inset) { x = w - inset; y = inset + s - (w - 2 * inset); }
            else if (s < 2 * w + h - 6 * inset) { x = w - inset - (s - (w + h - 4 * inset)); y = h - inset; }
            else { x = inset; y = h - inset - (s - (2 * w + h - 6 * inset)); }
            boolean on = (i + frame) % 3 != 0;
            p.setColor(on ? Color.parseColor("#FFE7A3") : Color.parseColor("#6B5320"));
            if (on) p.setShadowLayer(5 * d, 0, 0, Color.parseColor("#FFC24A"));
            c.drawCircle(x, y, 2.8f * d, p);
            p.setShadowLayer(0, 0, 0, 0);
        }
        return true;
    }

    // Minimal: en tyk streg, der trækkes ud under cardet
    private void minimal(Canvas c, float w, float h, float in) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.TEXT);
        c.drawRect(0, h - 5 * d, w * in, h, p);
    }

    // Efterår: varm ravgul kant og et blad, der lander i hjørnet
    private void autumn(Canvas c, float w, float h, float in) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setColor(HudView.withAlpha(Color.parseColor("#E39A3B"), in));
        r.set(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d);
        c.drawRoundRect(r, Ui.dp(getContext(), Ui.radiusDp), Ui.dp(getContext(), Ui.radiusDp), p);
        leaf(c, w - 22 * d, 18 * d, 14 * d, -40 + (1 - in) * 90, Color.parseColor("#C8571E"), in);
    }

    private void leaf(Canvas c, float x, float y, float size, float rot, int color, float alpha) {
        c.save();
        c.translate(x, y);
        c.rotate(rot);
        p.setStyle(Paint.Style.FILL);
        p.setColor(HudView.withAlpha(color, alpha));
        path.reset();
        path.moveTo(0, -size);
        path.quadTo(size * 0.8f, -size * 0.2f, 0, size);
        path.quadTo(-size * 0.8f, -size * 0.2f, 0, -size);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * d);
        p.setColor(HudView.withAlpha(Color.parseColor("#5A2A10"), alpha));
        c.drawLine(0, -size * 0.8f, 0, size * 1.3f, p);
        c.restore();
    }

    // Rosé: dobbelt tynd ramme i rosaguld
    private void rose(Canvas c, float w, float h, float in) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f * d);
        p.setColor(HudView.withAlpha(Color.parseColor("#E7B7A6"), in));
        float rad = Ui.dp(getContext(), Ui.radiusDp);
        r.set(4 * d, 4 * d, w - 4 * d, h - 4 * d);
        c.drawRoundRect(r, rad * 0.8f, rad * 0.8f, p);
        r.set(9 * d, 9 * d, w - 9 * d, h - 9 * d);
        c.drawRoundRect(r, rad * 0.55f, rad * 0.55f, p);
    }

    // Hygge: varmt lys nedefra, der blafrer som fra et stearinlys
    private boolean hygge(Canvas c, float w, float h, float t) {
        float f = 0.75f + 0.25f * (float) (Math.sin(t * 7) * 0.6 + Math.sin(t * 11.7) * 0.4);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(w / 2f, h * 1.1f, h * 1.1f, new int[]{HudView.withAlpha(Color.parseColor("#FFB347"), 0.5f * f),
                Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        return true;
    }

    // Salvie: blød grøn kant og to små blade i hjørnet
    private void sage(Canvas c, float w, float h, float in) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setColor(HudView.withAlpha(Color.parseColor("#7E9C78"), in));
        float rad = Ui.dp(getContext(), Ui.radiusDp);
        r.set(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d);
        c.drawRoundRect(r, rad, rad, p);
        leaf(c, 18 * d, h - 18 * d, 10 * d, 40, Color.parseColor("#8FA88A"), in);
        leaf(c, 30 * d, h - 14 * d, 8 * d, 80, Color.parseColor("#6E8A68"), in);
    }

    // Nattehimmel: små stjerner, der blinker langs kanten
    private boolean stars(Canvas c, float w, float h, float t, float in) {
        float[][] at = {{0.08f, 0.15f}, {0.3f, 0.06f}, {0.62f, 0.1f}, {0.9f, 0.22f}, {0.94f, 0.7f}, {0.72f, 0.9f}, {0.2f, 0.86f}, {0.05f, 0.55f}};
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < at.length; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 3 + i * 1.7);
            float s = (2.5f + 2.5f * tw) * d * in;
            float x = at[i][0] * w, y = at[i][1] * h;
            p.setColor(Color.argb((int) (255 * (0.4f + 0.6f * tw) * in), 255, 250, 230));
            path.reset();
            path.moveTo(x, y - s);
            path.quadTo(x, y, x + s, y);
            path.quadTo(x, y, x, y + s);
            path.quadTo(x, y, x - s, y);
            path.quadTo(x, y, x, y - s);
            c.drawPath(path, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(d);
        p.setColor(HudView.withAlpha(Color.WHITE, 0.5f * in));
        float rad = Ui.dp(getContext(), Ui.radiusDp);
        r.set(d, d, w - d, h - d);
        c.drawRoundRect(r, rad, rad, p);
        return true;
    }

    // Jul: cardet bliver en gave med rødt bånd og en guldsløjfe
    private void present(Canvas c, float w, float h, float in) {
        float bx = w * 0.8f, by = h * 0.28f, band = 9 * d;
        p.setStyle(Paint.Style.FILL);
        p.setColor(HudView.withAlpha(Color.parseColor("#C41E2A"), 0.95f * in));
        c.drawRect(bx - band / 2, 0, bx + band / 2, h, p);
        c.drawRect(0, by - band / 2, w, by + band / 2, p);
        p.setColor(HudView.withAlpha(Color.parseColor("#F5D88A"), in));
        float s = 13 * d * in;
        r.set(bx - s * 1.6f, by - s * 0.7f, bx, by + s * 0.7f);
        c.drawOval(r, p);
        r.set(bx, by - s * 0.7f, bx + s * 1.6f, by + s * 0.7f);
        c.drawOval(r, p);
        p.setColor(HudView.withAlpha(Color.parseColor("#C8902E"), in));
        c.drawCircle(bx, by, s * 0.45f, p);
    }

    // Hav: ringe i vandet, der breder sig fra midten
    private boolean ripple(Canvas c, float w, float h, float t) {
        p.setStyle(Paint.Style.STROKE);
        for (int k = 0; k < 3; k++) {
            float ph = ((t / 1.6f) + k / 3f) % 1f;
            p.setStrokeWidth(2 * d * (1 - ph) + d);
            p.setColor(Color.argb((int) (150 * (1 - ph)), 220, 248, 250));
            r.set(w / 2f - w * 0.6f * ph, h * 0.85f - h * 0.35f * ph, w / 2f + w * 0.6f * ph, h * 0.85f + h * 0.35f * ph);
            c.drawOval(r, p);
        }
        return true;
    }
}
