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
import android.graphics.Typeface;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * Temaernes egne ure, tegnet af appen: Iron Mans buereaktor, Spider-Mans taleboble, Batsignalet,
 * Supermans sol, biografens vippetavle, Rosés rosette, Hygges stearinlys, Salvies kvist, Nattehimlens
 * måne, Julens julekugle og Havets bølge. Nogle bevæger sig en smule (flammen, kuglen, bølgen).
 */
final class ThemeClock extends View {

    static final String[] STYLES = {"reaktor", "boble", "signal", "sol", "tavle", "rosette", "lys", "blad", "maane", "kugle", "boelge", "blade"};

    static boolean has(String style) {
        for (String s : STYLES) if (s.equals(style)) return true;
        return false;
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final float d;
    private String style = "";
    private boolean animate = true;
    private long start = System.currentTimeMillis();

    ThemeClock(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.LEFT);
    }

    /** Uret står midt på skærmen (Superman, Biograf, Jul, Nattehimmel). */
    private boolean centered;

    void setCentered(boolean c) {
        centered = c;
        invalidate();
    }

    /** Et ægte foto til uret (fx en julekugle); erstatter den tegnede figur. */
    private android.graphics.Bitmap art;

    void setArt(android.graphics.Bitmap b) {
        art = b;
        invalidate();
    }

    private boolean photo() {
        return art != null && !"tavle".equals(style) && !"boble".equals(style);
    }

    void setStyle(String s) {
        style = s;
        invalidate();
    }

    /** Ingen bevægelse ved effektniveau Ingen. */
    void setAnimate(boolean a) {
        animate = a;
        invalidate();
    }

    private boolean moving() {
        return !photo() && ("lys".equals(style) || "kugle".equals(style) || "boelge".equals(style) || "reaktor".equals(style));
    }

    @Override
    protected void onDraw(Canvas c) {
        Calendar now = Calendar.getInstance();
        String hm = String.format(Locale.ROOT, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        float t = animate ? (System.currentTimeMillis() - start) / 1000f : 0f;
        int w = getWidth(), h = getHeight();
        text.setShader(null);
        text.setShadowLayer(0, 0, 0, 0);
        p.setShader(null);
        p.setShadowLayer(0, 0, 0, 0);
        // Står uret i højre side (Batman, Iron Man), skal tegningen flugte med højre kant
        c.save();
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) c.translate(w - contentWidth(h, hm) - 4 * d, 0);
        else if (centered) c.translate((w - contentWidth(h, hm)) / 2f, 0);
        if (photo()) {
            medallion(c, h, hm);
            c.restore();
            return;
        }
        switch (style) {
            case "reaktor": reactor(c, h, hm, now, t); break;
            case "boble": bubble(c, w, h, hm); break;
            case "signal": signal(c, w, h, hm); break;
            case "sol": sun(c, w, h, hm, t); break;
            case "tavle": board(c, h, hm); break;
            case "rosette": rosette(c, h, hm); break;
            case "lys": candle(c, h, hm, t); break;
            case "blad": sprig(c, h, hm); break;
            case "maane": moon(c, h, hm, now); break;
            case "kugle": bauble(c, h, hm, t); break;
            case "boelge": wave(c, w, h, hm, t); break;
            case "blade": rosette(c, h, hm); break;
        }
        c.restore();
        if (animate && moving() && isShown()) postInvalidateDelayed(40);
    }

    /** Hvor bred tegningen er, så den kan flugte højre. */
    private float contentWidth(int h, String hm) {
        if (photo()) return h * 0.9f + 22 * d + big(hm, h * 0.56f, Ui.display);
        switch (style) {
            case "reaktor": return h + 18 * d + big(hm, h * 0.42f, Ui.display);
            case "signal": return Math.max(big(hm, h * 0.34f, Ui.display) * 1.5f, h * 1.5f) + 6 * d;
            case "sol": return big(hm, h * 0.7f, Ui.display) + 13 * d;
            case "tavle": return 4 * (h * 0.5f + 6 * d) + 16 * d + 4 * d;
            case "kugle": return h * 0.8f + 12 * d;
            case "maane": return h * 0.95f + big(hm, h * 0.58f, Typeface.create("sans-serif-thin", Typeface.NORMAL));
            case "boble": return big(hm, h * 0.5f, Ui.display) + 66 * d;
            case "rosette": case "boelge": return big(hm, h * (style.equals("rosette") ? 0.62f : 0.6f), Ui.display) + 8 * d;
            case "lys": return h * 0.62f + big(hm, h * 0.62f, Ui.display);
            case "blad": return h * 0.62f + big(hm, h * 0.6f, Ui.display);
            default: return getWidth() - 4 * d;
        }
    }

    // Fotoet i en rund ramme med en blød skygge og en tynd kant i temaets farve, tiden ved siden af
    private void medallion(Canvas c, int h, String hm) {
        float R = h * 0.45f, cx = R + 4 * d, cy = h / 2f;
        int edge = edgeColor();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.BLACK);
        p.setShadowLayer(14 * d, 0, 4 * d, Color.argb(150, 0, 0, 0));
        c.drawCircle(cx, cy, R, p);
        p.setShadowLayer(0, 0, 0, 0);
        android.graphics.BitmapShader sh = new android.graphics.BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        android.graphics.Matrix m = new android.graphics.Matrix();
        float scale = 2 * R / Math.min(art.getWidth(), art.getHeight());
        m.setScale(scale, scale);
        m.postTranslate(cx - art.getWidth() * scale / 2f, cy - art.getHeight() * scale / 2f);
        sh.setLocalMatrix(m);
        p.setShader(sh);
        c.drawCircle(cx, cy, R, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f * d);
        p.setColor(edge);
        c.drawCircle(cx, cy, R, p);
        big(hm, h * 0.56f, Ui.display);
        text.setColor(Ui.TEXT);
        text.setShadowLayer(8 * d, 0, 2 * d, Color.argb(140, 0, 0, 0));
        c.drawText(hm, cx + R + 18 * d, baseline(cy), text);
    }

    private int edgeColor() {
        switch (Skin.key) {
            case "jul": case "efteraar": case "ironman": return Color.parseColor("#E8C26B");
            case "rose": return Color.parseColor("#E7B7A6");
            case "hygge": return Color.parseColor("#FFB347");
            case "salvie": return Color.parseColor("#8FA88A");
            case "batman": return Color.parseColor("#E8C64A");
            case "superman": return Color.parseColor("#D0202E");
            case "hav": return Color.parseColor("#9CE0E6");
            default: return Color.argb(200, 255, 255, 255);
        }
    }

    private float big(String s, float size, Typeface face) {
        text.setTypeface(face);
        text.setTextSize(size);
        return text.measureText(s);
    }

    private float baseline(float cy) {
        return cy - (text.descent() + text.ascent()) / 2f;
    }

    // Iron Man: buereaktor med ti glødende celler, der tænder med sekunderne; tiden i guld ved siden af
    private void reactor(Canvas c, int h, String hm, Calendar now, float t) {
        float cx = h / 2f, cy = h / 2f, R = h * 0.44f;
        int glow = Color.parseColor("#A8ECFF");
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 2.2);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, R * 1.25f, new int[]{withA(glow, 0.55f * pulse), withA(glow, 0f)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R * 1.25f, p);
        p.setShader(null);
        p.setColor(Color.parseColor("#2A2E33"));
        c.drawCircle(cx, cy, R, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setColor(Color.parseColor("#8E949B"));
        c.drawCircle(cx, cy, R, p);
        int sec = now.get(Calendar.SECOND);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 10; i++) {
            boolean on = i * 6 <= sec;
            double a0 = Math.toRadians(i * 36 - 90 + 4), a1 = Math.toRadians(i * 36 - 90 + 32);
            float ro = R * 0.86f, ri = R * 0.58f;
            path.reset();
            path.moveTo(cx + (float) Math.cos(a0) * ri, cy + (float) Math.sin(a0) * ri);
            path.lineTo(cx + (float) Math.cos(a0) * ro, cy + (float) Math.sin(a0) * ro);
            path.lineTo(cx + (float) Math.cos(a1) * ro, cy + (float) Math.sin(a1) * ro);
            path.lineTo(cx + (float) Math.cos(a1) * ri, cy + (float) Math.sin(a1) * ri);
            path.close();
            p.setColor(on ? withA(glow, 0.95f * pulse) : Color.parseColor("#3C4248"));
            if (on) p.setShadowLayer(6 * d, 0, 0, glow);
            c.drawPath(path, p);
            p.setShadowLayer(0, 0, 0, 0);
        }
        p.setColor(Color.WHITE);
        p.setShadowLayer(12 * d, 0, 0, glow);
        c.drawCircle(cx, cy, R * 0.34f, p);
        p.setShadowLayer(0, 0, 0, 0);
        big(hm, h * 0.42f, Ui.display);
        text.setColor(Color.parseColor("#F2C35A"));
        text.setShadowLayer(8 * d, 0, 0, Color.parseColor("#80D42A22"));
        c.drawText(hm, h + 18 * d, baseline(cy), text);
    }

    // Spider-Man: hvid taleboble med tyk sort kant og hale, tiden med tegneserieskrift
    private void bubble(Canvas c, int w, int h, String hm) {
        float tw = big(hm, h * 0.5f, Ui.display);
        float bw = tw + 56 * d, bh = h * 0.72f;
        r.set(4 * d, 4 * d, 4 * d + bw, 4 * d + bh);
        path.reset();
        path.addOval(r, Path.Direction.CW);
        Path tail = new Path();
        tail.moveTo(r.left + bw * 0.22f, r.bottom - bh * 0.1f);
        tail.lineTo(r.left + bw * 0.08f, h - 2 * d);
        tail.lineTo(r.left + bw * 0.38f, r.bottom - bh * 0.06f);
        tail.close();
        path.op(tail, Path.Op.UNION);
        // Hård skygge forskudt ned til højre, som cardsene
        c.save();
        c.translate(6 * d, 6 * d);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#D0202E"));
        c.drawPath(path, p);
        c.restore();
        p.setColor(Color.WHITE);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4 * d);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(Color.parseColor("#111111"));
        c.drawPath(path, p);
        text.setColor(Color.parseColor("#111111"));
        c.drawText(hm, r.centerX() - tw / 2f, baseline(r.centerY()), text);
    }

    // Batman: Batsignalet på skyerne, med tiden i lyskeglen
    private void signal(Canvas c, int w, int h, String hm) {
        float tw = big(hm, h * 0.34f, Ui.display);
        float ew = Math.max(tw * 1.5f, h * 1.5f), eh = h * 0.86f;
        float cx = ew / 2f + 6 * d, cy = h / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, ew * 0.62f, new int[]{Color.parseColor("#FFF3B0"),
                Color.parseColor("#E8C64A"), Color.parseColor("#00E8C64A")}, new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        r.set(cx - ew / 2f, cy - eh / 2f, cx + ew / 2f, cy + eh / 2f);
        c.drawOval(r, p);
        p.setShader(null);
        // Flagermus over tiden
        p.setColor(Color.parseColor("#14120C"));
        float bw = ew * 0.42f, bh = eh * 0.2f, bx = cx - bw / 2f, by = cy - eh * 0.34f;
        path.reset();
        path.moveTo(cx, by + bh * 0.25f);
        path.lineTo(cx - bw * 0.06f, by);
        path.lineTo(cx - bw * 0.1f, by + bh * 0.3f);
        path.quadTo(cx - bw * 0.3f, by - bh * 0.1f, bx, by + bh * 0.1f);
        path.quadTo(bx + bw * 0.12f, by + bh * 0.5f, bx + bw * 0.05f, by + bh);
        path.quadTo(bx + bw * 0.2f, by + bh * 0.7f, bx + bw * 0.3f, by + bh * 0.95f);
        path.quadTo(bx + bw * 0.4f, by + bh * 0.7f, cx, by + bh * 1.05f);
        path.quadTo(bx + bw * 0.6f, by + bh * 0.7f, bx + bw * 0.7f, by + bh * 0.95f);
        path.quadTo(bx + bw * 0.8f, by + bh * 0.7f, bx + bw * 0.95f, by + bh);
        path.quadTo(bx + bw * 0.88f, by + bh * 0.5f, bx + bw, by + bh * 0.1f);
        path.quadTo(cx + bw * 0.3f, by - bh * 0.1f, cx + bw * 0.1f, by + bh * 0.3f);
        path.lineTo(cx + bw * 0.06f, by);
        path.close();
        c.drawPath(path, p);
        text.setColor(Color.parseColor("#14120C"));
        c.drawText(hm, cx - tw / 2f, baseline(cy + eh * 0.1f), text);
    }

    // Superman: store hvide tal med rød skygge foran en varm sol, og en rød og gul streg under
    private void sun(Canvas c, int w, int h, String hm, float t) {
        float tw = big(hm, h * 0.7f, Ui.display);
        float glow = 1f + 0.06f * (float) Math.sin(t * 1.3);
        p.setStyle(Paint.Style.FILL);
        float sx = tw * 0.75f, sy = h * 0.42f, sr = h * 0.8f * glow;
        p.setShader(new RadialGradient(sx, sy, sr, new int[]{Color.parseColor("#B3FFD34D"), Color.parseColor("#33FF8A1F"),
                Color.parseColor("#00FF8A1F")}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(sx, sy, sr, p);
        p.setShader(null);
        float y = baseline(h * 0.44f);
        text.setColor(Color.parseColor("#D0202E"));
        c.drawText(hm, 4 * d + 5 * d, y + 5 * d, text);
        text.setColor(Color.WHITE);
        c.drawText(hm, 4 * d, y, text);
        p.setColor(Color.parseColor("#D0202E"));
        c.drawRect(4 * d, h * 0.86f, 4 * d + tw * 0.62f, h * 0.86f + 6 * d, p);
        p.setColor(Color.parseColor("#FFD34D"));
        c.drawRect(4 * d + tw * 0.62f, h * 0.86f, 4 * d + tw, h * 0.86f + 6 * d, p);
    }

    // Biograf: vippetavle som på en gammel stationsur eller billetluge
    private void board(Canvas c, int h, String hm) {
        float cw = h * 0.5f, ch = h * 0.78f, gap = 6 * d, x = 4 * d, top = (h - ch) / 2f;
        big("0", ch * 0.78f, Ui.display);
        for (int i = 0; i < hm.length(); i++) {
            char ch1 = hm.charAt(i);
            if (ch1 == ':') {
                p.setColor(Color.parseColor("#E8C26B"));
                c.drawCircle(x + 5 * d, top + ch * 0.35f, 4 * d, p);
                c.drawCircle(x + 5 * d, top + ch * 0.65f, 4 * d, p);
                x += 10 * d + gap;
                continue;
            }
            r.set(x, top, x + cw, top + ch);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, top, 0, top + ch, new int[]{Color.parseColor("#2B2724"), Color.parseColor("#161412"),
                    Color.parseColor("#0E0C0B"), Color.parseColor("#1D1A18")}, new float[]{0f, 0.49f, 0.51f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 6 * d, 6 * d, p);
            p.setShader(null);
            text.setColor(Color.parseColor("#F5E9D8"));
            String s = String.valueOf(ch1);
            float sw = text.measureText(s);
            c.drawText(s, x + (cw - sw) / 2f, baseline(top + ch / 2f), text);
            // Skillelinjen, hvor bladene vipper
            p.setColor(Color.parseColor("#000000"));
            c.drawRect(x, top + ch / 2f - d, x + cw, top + ch / 2f + d, p);
            x += cw + gap;
        }
    }

    // Rosé: tynd serif tid over en fin streg med en lille rose i midten
    private void rosette(Canvas c, int h, String hm) {
        float tw = big(hm, h * 0.62f, Ui.display);
        text.setColor(Ui.TEXT);
        c.drawText(hm, 4 * d, baseline(h * 0.4f), text);
        float y = h * 0.86f, cx = 4 * d + tw / 2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * d);
        p.setColor(withA(Color.parseColor("#E7B7A6"), 0.8f));
        c.drawLine(4 * d, y, cx - 14 * d, y, p);
        c.drawLine(cx + 14 * d, y, 4 * d + tw, y, p);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60);
            p.setColor(withA(Color.parseColor("#D98A9A"), 0.9f));
            c.drawCircle(cx + (float) Math.cos(a) * 5 * d, y + (float) Math.sin(a) * 5 * d, 4.5f * d, p);
        }
        p.setColor(Color.parseColor("#B04A63"));
        c.drawCircle(cx, y, 4 * d, p);
    }

    // Hygge: et stearinlys med en flamme, der blafrer, og tiden i håndskrift
    private void candle(Canvas c, int h, String hm, float t) {
        float cx = h * 0.28f, base = h * 0.95f, top = h * 0.48f, cw = h * 0.26f;
        float flick = (float) (Math.sin(t * 9) * 0.5 + Math.sin(t * 13.3) * 0.3 + Math.sin(t * 4.1) * 0.2);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, top - h * 0.1f, h * 0.55f, new int[]{withA(Color.parseColor("#FFB347"), 0.42f + 0.06f * flick),
                withA(Color.parseColor("#FFB347"), 0f)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, top - h * 0.1f, h * 0.55f, p);
        p.setShader(new LinearGradient(cx - cw / 2, 0, cx + cw / 2, 0, new int[]{Color.parseColor("#E9DCC4"),
                Color.parseColor("#FFF7E8"), Color.parseColor("#D8C8AC")}, null, Shader.TileMode.CLAMP));
        r.set(cx - cw / 2, top, cx + cw / 2, base);
        c.drawRoundRect(r, 4 * d, 4 * d, p);
        p.setShader(null);
        p.setColor(Color.parseColor("#3A2A1E"));
        c.drawRect(cx - d, top - 6 * d, cx + d, top, p);
        float fh = h * 0.24f * (1 + 0.06f * flick), fw = h * 0.07f;
        float fx = cx + flick * 1.5f * d, fy = top - 6 * d;
        path.reset();
        path.moveTo(fx, fy - fh);
        path.quadTo(fx + fw, fy - fh * 0.35f, fx, fy);
        path.quadTo(fx - fw, fy - fh * 0.35f, fx, fy - fh);
        p.setShader(new LinearGradient(0, fy - fh, 0, fy, new int[]{Color.parseColor("#FFF6D5"), Color.parseColor("#FFB02E"),
                Color.parseColor("#E8590C")}, null, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
        big(hm, h * 0.62f, Ui.display);
        text.setColor(Ui.TEXT);
        c.drawText(hm, h * 0.62f, baseline(h * 0.55f), text);
    }

    // Salvie: en kvist eukalyptus til venstre for tiden
    private void sprig(Canvas c, int h, String hm) {
        float x0 = h * 0.12f, y0 = h * 0.92f, x1 = h * 0.42f, y1 = h * 0.1f;
        int leaf = Color.parseColor("#8FA88A"), dark = Color.parseColor("#5E7357");
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * d);
        p.setColor(dark);
        path.reset();
        path.moveTo(x0, y0);
        path.quadTo(h * 0.36f, h * 0.6f, x1, y1);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 5; i++) {
            float k = 0.18f + i * 0.17f;
            float lx = (1 - k) * (1 - k) * x0 + 2 * (1 - k) * k * h * 0.36f + k * k * x1;
            float ly = (1 - k) * (1 - k) * y0 + 2 * (1 - k) * k * h * 0.6f + k * k * y1;
            float side = i % 2 == 0 ? -1 : 1;
            c.save();
            c.translate(lx, ly);
            c.rotate(side * 50 - 20);
            r.set(0, -h * 0.06f, h * 0.2f * side, h * 0.06f);
            r.sort();
            p.setColor(i % 2 == 0 ? leaf : withA(leaf, 0.8f));
            c.drawOval(r, p);
            c.restore();
        }
        big(hm, h * 0.6f, Ui.display);
        text.setColor(Ui.TEXT);
        c.drawText(hm, h * 0.62f, baseline(h * 0.5f), text);
    }

    // Nattehimmel: månen i sin rigtige fase lige nu, med et svagt skær, og tiden i tynd skrift
    private void moon(Canvas c, int h, String hm, Calendar now) {
        float cx = h * 0.42f, cy = h / 2f, R = h * 0.34f;
        double days = (now.getTimeInMillis() - 947182440000L) / 86400000.0;
        double phase = ((days % 29.53059) + 29.53059) % 29.53059 / 29.53059;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, R * 1.9f, new int[]{Color.parseColor("#40ECE8FF"), Color.parseColor("#00ECE8FF")}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R * 1.9f, p);
        p.setShader(null);
        p.setColor(Color.parseColor("#2A2750"));
        c.drawCircle(cx, cy, R, p);
        // Den oplyste del: en halvcirkel plus en ellipse, der vokser og aftager med fasen
        float k = (float) Math.cos(phase * 2 * Math.PI);
        boolean waxing = phase < 0.5;
        path.reset();
        r.set(cx - R, cy - R, cx + R, cy + R);
        path.arcTo(r, -90, waxing ? 180 : -180);
        r.set(cx - R * Math.abs(k), cy - R, cx + R * Math.abs(k), cy + R);
        boolean crescent = k > 0;
        path.arcTo(r, 90, (waxing == crescent) ? -180 : 180);
        path.close();
        p.setColor(Color.parseColor("#F4F1E6"));
        p.setShadowLayer(10 * d, 0, 0, Color.parseColor("#80ECE8FF"));
        c.drawPath(path, p);
        p.setShadowLayer(0, 0, 0, 0);
        big(hm, h * 0.58f, Typeface.create("sans-serif-thin", Typeface.NORMAL));
        text.setColor(Ui.TEXT);
        c.drawText(hm, h * 0.95f, baseline(cy), text);
    }

    // Jul: en rød julekugle, der svinger lidt i sin snor, med tiden indeni
    private void bauble(Canvas c, int h, String hm, float t) {
        float R = h * 0.4f, cx = R + 6 * d, cy = h - R - 2 * d;
        c.save();
        c.rotate((float) Math.sin(t * 1.4) * 3f, cx, 0);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * d);
        p.setColor(Color.parseColor("#E8C26B"));
        c.drawLine(cx, 0, cx, cy - R - 8 * d, p);
        p.setStyle(Paint.Style.FILL);
        r.set(cx - 9 * d, cy - R - 10 * d, cx + 9 * d, cy - R + 3 * d);
        p.setShader(new LinearGradient(r.left, 0, r.right, 0, new int[]{Color.parseColor("#A07A2C"), Color.parseColor("#F5D88A"),
                Color.parseColor("#A07A2C")}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 2 * d, 2 * d, p);
        p.setShader(new RadialGradient(cx - R * 0.35f, cy - R * 0.4f, R * 1.5f, new int[]{Color.parseColor("#F05A5A"),
                Color.parseColor("#B3121F"), Color.parseColor("#5C0710")}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R, p);
        p.setShader(null);
        p.setColor(Color.argb(150, 255, 255, 255));
        r.set(cx - R * 0.6f, cy - R * 0.7f, cx - R * 0.2f, cy - R * 0.45f);
        c.drawOval(r, p);
        float tw = big(hm, R * 0.62f, Ui.display);
        text.setColor(Color.parseColor("#FFF4E0"));
        c.drawText(hm, cx - tw / 2f, baseline(cy + R * 0.05f), text);
        c.restore();
    }

    // Hav: tiden over to bølger, der ruller langsomt
    private void wave(Canvas c, int w, int h, String hm, float t) {
        float tw = big(hm, h * 0.6f, Ui.display);
        text.setColor(Ui.TEXT);
        c.drawText(hm, 4 * d, baseline(h * 0.38f), text);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int k = 0; k < 2; k++) {
            path.reset();
            float y = h * (0.8f + k * 0.1f), amp = (5 - k * 1.5f) * d;
            for (float x = 0; x <= tw; x += 4 * d) {
                float yy = y + (float) Math.sin(x / (22 * d) + t * (1.4f - k * 0.5f) + k) * amp;
                if (x == 0) path.moveTo(4 * d + x, yy);
                else path.lineTo(4 * d + x, yy);
            }
            p.setStrokeWidth((2.5f - k) * d);
            p.setColor(withA(Color.parseColor("#9CE0E6"), 0.9f - k * 0.4f));
            c.drawPath(path, p);
        }
    }

    private static int withA(int color, float a) {
        return HudView.withAlpha(color, a);
    }
}
