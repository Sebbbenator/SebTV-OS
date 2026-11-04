package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Temaets "scene" på forsiden: den del, der gør temaet til en ting fra sin egen verden. Daily Planets
 * forside med klokken som overskrift, tegneseriefelter, biografens markise, en seddel med tape, Starks
 * buereaktor, Batcomputerens skærm og julekalenderens store låge. Tegnes kun, når minuttet skifter
 * (Jarvis hvert sekund for sekundbuen), så intet hakker.
 */
final class Stage extends View {

    static final int NONE = 0, TOP = 1, LEFT = 2, RIGHT = 3;
    private static final Locale DA = new Locale("da", "DK");

    /** Hvor scenen står: øverst, i venstre eller i højre side. */
    static int place() {
        switch (Skin.key) {
            case "superman": case "spiderman": case "biograf": case "hygge": return TOP;
            case "jarvis": case "jul": return LEFT;
            case "batman": return RIGHT;
            default: return NONE;
        }
    }

    static boolean on() {
        return place() != NONE;
    }

    /** Højde (øverst) eller bredde (i siden) i dp. */
    static int sizeDp() {
        switch (Skin.key) {
            case "superman": return 268;
            case "spiderman": return 232;
            case "biograf": return 188;
            case "hygge": return 176;
            case "jarvis": return 330;
            case "jul": return 320;
            case "batman": return 300;
            default: return 0;
        }
    }

    /** Apps i et gitter (Batmans sagsmapper, julekalenderens låger) i stedet for én lang række. */
    static int gridColumns() {
        switch (Skin.key) {
            case "batman": case "jul": return 4;
            default: return 0;
        }
    }

    /** Kun Jarvis har fotoet bag hele skærmen; de andre bruger det inde i scenen eller slet ikke. */
    static boolean photoBehind() {
        return Skin.is("jarvis");
    }

    /** Temaets eget fotoserie bruges inde i scenen (vejrfeltet, overvågningsskærmen). */
    static boolean wantsPhoto() {
        return Skin.is("spiderman") || Skin.is("batman") || Skin.is("jarvis");
    }

    private final float density;
    private Date now = new Date();
    private String shownMinute = "";
    private String greeting = "", status = "", weather = "", name = "";
    private Bitmap photo;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private Shader dotsDark, dotsLight;
    private final SimpleDateFormat MINUTE = new SimpleDateFormat("yyyyMMddHHmm", DA);
    /** Jarvis: alt det stille ligger i en bitmap, så hvert sekund kun tegner sekundbuen og tallet. */
    private Bitmap still;
    private boolean stillDirty = true;
    private static final int ALL = 0, STILL = 1, MOVING = 2;
    private int pass = ALL;

    Stage(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
    }

    float d(float v) {
        return v * density;
    }

    float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    /** Kaldes hvert sekund; tegner kun igen, når der er noget nyt at vise. */
    void tick(Date date) {
        now = date;
        String minute = MINUTE.format(date);
        if (!minute.equals(shownMinute)) {
            shownMinute = minute;
            stillDirty = true;
            invalidate();
        } else if (Skin.is("jarvis")) {
            invalidate();
        }
    }

    void setInfo(String greeting, List<String> statusParts, String name) {
        String w = "";
        StringBuilder rest = new StringBuilder();
        for (String part : statusParts) {
            if (w.isEmpty() && part.contains("°") && !part.contains(" til") && !part.contains(" fra")) {
                w = part;
                continue;
            }
            if (rest.length() > 0) rest.append("  ·  ");
            rest.append(part);
        }
        String g = greeting == null ? "" : greeting;
        String st = rest.toString();
        String n = name == null ? "" : name;
        if (g.equals(this.greeting) && st.equals(status) && w.equals(weather) && n.equals(this.name)) return;
        this.greeting = g;
        this.status = st;
        this.weather = w;
        this.name = n;
        stillDirty = true;
        invalidate();
    }

    /** Giver Jarvis' stille lag fri; det tegnes igen næste gang. */
    void trim() {
        still = null;
        stillDirty = true;
        invalidate();
    }

    void setPhoto(Bitmap bmp) {
        photo = bmp;
        stillDirty = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        switch (Skin.key) {
            case "superman": superman(c, w, h); break;
            case "spiderman": spiderman(c, w, h); break;
            case "batman": batman(c, w, h); break;
            case "jarvis": {
                if (still == null || still.getWidth() != w || still.getHeight() != h) {
                    try {
                        still = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    } catch (OutOfMemoryError e) {
                        still = null;
                    }
                    stillDirty = true;
                }
                if (still == null) {
                    pass = ALL;
                    jarvis(c, w, h);
                    break;
                }
                if (stillDirty) {
                    still.eraseColor(Color.TRANSPARENT);
                    pass = STILL;
                    jarvis(new Canvas(still), w, h);
                    stillDirty = false;
                }
                c.drawBitmap(still, 0, 0, null);
                pass = MOVING;
                jarvis(c, w, h);
                pass = ALL;
                break;
            }
            case "biograf": biograf(c, w, h); break;
            case "jul": jul(c, w, h); break;
            case "hygge": hygge(c, w, h); break;
        }
    }

    // Fælles

    private String fmt(String pattern) {
        return new SimpleDateFormat(pattern, DA).format(now);
    }

    private String time() {
        return fmt("HH:mm");
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String up(String s) {
        return s.toUpperCase(DA);
    }

    private void text(Canvas c, String t, Typeface face, float sizeSp, int color, float x, float y, Paint.Align align, float spacing) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(face);
        p.setTextSize(sp(sizeSp));
        p.setColor(color);
        p.setTextAlign(align);
        p.setLetterSpacing(spacing);
        p.clearShadowLayer();
        c.drawText(t, x, y, p);
    }

    /** Skriftstørrelse, der får teksten til at fylde højst maxWidth. */
    private float fit(String t, Typeface face, float sizeSp, float spacing, float maxWidth) {
        p.setTypeface(face);
        p.setLetterSpacing(spacing);
        p.setTextSize(sp(sizeSp));
        float wdt = p.measureText(t);
        if (wdt <= maxWidth || wdt == 0) return sizeSp;
        return sizeSp * maxWidth / wdt;
    }

    /** Teksten forkortet med … så den kan stå på maxWidth. */
    private String clip(String t, Typeface face, float sizeSp, float spacing, float maxWidth) {
        p.setTypeface(face);
        p.setLetterSpacing(spacing);
        p.setTextSize(sp(sizeSp));
        if (p.measureText(t) <= maxWidth) return t;
        String x = t;
        while (x.length() > 1 && p.measureText(x + "…") > maxWidth) x = x.substring(0, x.length() - 1);
        return x.trim() + "…";
    }

    private void rect(Canvas c, float l, float t, float rr, float b, int fill) {
        s.setShader(null);
        s.setStyle(Paint.Style.FILL);
        s.setColor(fill);
        s.setPathEffect(null);
        c.drawRect(l, t, rr, b, s);
    }

    private void stroke(Canvas c, RectF box, float width, int color) {
        s.setShader(null);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(width);
        s.setColor(color);
        s.setPathEffect(null);
        c.drawRect(box.left + width / 2, box.top + width / 2, box.right - width / 2, box.bottom - width / 2, s);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2, float width, int color) {
        s.setShader(null);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(width);
        s.setColor(color);
        s.setPathEffect(null);
        c.drawLine(x1, y1, x2, y2, s);
    }

    /** Rasterprikker som i et trykt hæfte; lagt som et mønster, så de ikke tegnes én for én. */
    static Shader dots(float density, int color, float spacingDp, float radiusDp) {
        int n = Math.max(4, Math.round(spacingDp * density));
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint dp = new Paint(Paint.ANTI_ALIAS_FLAG);
        dp.setColor(color);
        cv.drawCircle(n / 2f, n / 2f, radiusDp * density, dp);
        return new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    private void halftone(Canvas c, RectF box, boolean light) {
        if (light) {
            if (dotsLight == null) dotsLight = dots(density, Color.argb(46, 255, 255, 255), 8, 2.2f);
            s.setShader(dotsLight);
        } else {
            if (dotsDark == null) dotsDark = dots(density, Color.argb(56, 0, 0, 0), 9, 2.4f);
            s.setShader(dotsDark);
        }
        s.setStyle(Paint.Style.FILL);
        c.drawRect(box, s);
        s.setShader(null);
    }

    /** Fotoet beskåret til at fylde boksen, med et farvefilter (fx monokrom ravgul). */
    private void photo(Canvas c, RectF box, ColorMatrix m, int alpha) {
        if (photo == null) return;
        float scale = Math.max(box.width() / photo.getWidth(), box.height() / photo.getHeight());
        float pw = box.width() / scale, ph = box.height() / scale;
        Rect src = new Rect((int) ((photo.getWidth() - pw) / 2), (int) ((photo.getHeight() - ph) / 2),
                (int) ((photo.getWidth() + pw) / 2), (int) ((photo.getHeight() + ph) / 2));
        Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
        bp.setAlpha(alpha);
        if (m != null) bp.setColorFilter(new ColorMatrixColorFilter(m));
        c.save();
        c.clipRect(box);
        c.drawBitmap(photo, src, box, bp);
        c.restore();
    }

    /** Monokrom i én farve: lyset i fotoet bliver til farven. */
    static ColorMatrix tint(int color, float strength) {
        float rr = Color.red(color) / 255f * strength, gg = Color.green(color) / 255f * strength, bb = Color.blue(color) / 255f * strength;
        return new ColorMatrix(new float[]{
                0.3f * rr, 0.59f * rr, 0.11f * rr, 0, 0,
                0.3f * gg, 0.59f * gg, 0.11f * gg, 0, 0,
                0.3f * bb, 0.59f * bb, 0.11f * bb, 0, 0,
                0, 0, 0, 1, 0});
    }

    /** Spind fra et hjørne: tråde ud fra hjørnet og buer imellem. */
    static void web(Canvas c, Paint paint, float x, float y, float size, float sx, float sy) {
        double[] a = {0, 14, 32, 52, 72, 90};
        Path w = new Path();
        for (double deg : a) {
            double rad = Math.toRadians(deg);
            w.moveTo(x, y);
            w.lineTo((float) (x + sx * size * 1.5 * Math.cos(rad)), (float) (y + sy * size * 1.5 * Math.sin(rad)));
        }
        for (float rr = size * 0.16f; rr < size * 1.45f; rr += size * 0.17f) {
            for (int i = 0; i < a.length - 1; i++) {
                double a1 = Math.toRadians(a[i]), a2 = Math.toRadians(a[i + 1]), m = (a1 + a2) / 2;
                float rc = rr * 0.84f;
                w.moveTo((float) (x + sx * rr * Math.cos(a1)), (float) (y + sy * rr * Math.sin(a1)));
                w.quadTo((float) (x + sx * rc * Math.cos(m)), (float) (y + sy * rc * Math.sin(m)),
                        (float) (x + sx * rr * Math.cos(a2)), (float) (y + sy * rr * Math.sin(a2)));
            }
        }
        c.drawPath(w, paint);
    }

    /** Et flettet julehjerte (rød og hvid i tern), spidsen nedad. */
    static void heart(Canvas c, float cx, float cy, float size, float rotation) {
        Path h = new Path();
        float u = size / 100f;
        float x0 = cx - 50 * u, y0 = cy - 50 * u;
        h.moveTo(x0 + 50 * u, y0 + 95 * u);
        h.lineTo(x0 + 5 * u, y0 + 50 * u);
        float rr = 31.82f * u;
        h.arcTo(new RectF(x0 + 27.5f * u - rr, y0 + 27.5f * u - rr, x0 + 27.5f * u + rr, y0 + 27.5f * u + rr), 135, 180);
        h.arcTo(new RectF(x0 + 72.5f * u - rr, y0 + 27.5f * u - rr, x0 + 72.5f * u + rr, y0 + 27.5f * u + rr), 225, 180);
        h.close();
        c.save();
        c.rotate(rotation, cx, cy);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.parseColor("#F6EFE2"));
        c.drawPath(h, fill);
        c.save();
        c.clipPath(h);
        c.rotate(45, cx, cy);
        fill.setColor(Color.parseColor("#C1121F"));
        float cell = 10 * u;
        for (int i = -8; i < 8; i++) {
            for (int j = -8; j < 8; j++) {
                if (((i + j) & 1) == 0) continue;
                c.drawRect(cx + i * cell, cy + j * cell, cx + (i + 1) * cell, cy + (j + 1) * cell, fill);
            }
        }
        c.restore();
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1, 1.6f * u));
        edge.setColor(Color.parseColor("#7A0B14"));
        c.drawPath(h, edge);
        c.restore();
    }

    /** Pærer langs kanten af en boks, som på en biografmarkise. */
    static void bulbs(Canvas c, RectF box, float spacing, float radius) {
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setColor(Color.argb(72, 255, 180, 60));
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setColor(Color.parseColor("#FFC75A"));
        Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        core.setColor(Color.parseColor("#FFF6D6"));
        int nx = Math.max(2, Math.round(box.width() / spacing)), ny = Math.max(2, Math.round(box.height() / spacing));
        for (int i = 0; i <= nx; i++) {
            float x = box.left + box.width() * i / nx;
            for (float y : new float[]{box.top, box.bottom}) bulb(c, x, y, radius, glow, ring, core);
        }
        for (int j = 1; j < ny; j++) {
            float y = box.top + box.height() * j / ny;
            for (float x : new float[]{box.left, box.right}) bulb(c, x, y, radius, glow, ring, core);
        }
    }

    private static void bulb(Canvas c, float x, float y, float r, Paint glow, Paint ring, Paint core) {
        c.drawCircle(x, y, r * 2f, glow);
        c.drawCircle(x, y, r * 1.3f, ring);
        c.drawCircle(x, y, r, core);
    }

    // Superman: Daily Planets forside

    private void superman(Canvas c, int w, int h) {
        int ink = Color.parseColor("#151515"), red = Color.parseColor("#C8102E");
        float m = d(48), right = w - d(48);
        Typeface fr = Fonts.fraktur(getContext()), pb = Fonts.playfairBlack(getContext()), ob = Fonts.oldStandardBold(getContext());
        line(c, m, d(17), right, d(17), d(1.2f), ink);
        // Ørerne: EKSTRA! og vejret
        rect(c, m, d(30), m + d(125), d(96), red);
        text(c, "EKSTRA!", pb, 24, Color.WHITE, m + d(62.5f), d(68), Paint.Align.CENTER, 0);
        text(c, "METROPOLIS", ob, 8.5f, Color.WHITE, m + d(62.5f), d(86), Paint.Align.CENTER, 0.16f);
        r.set(right - d(125), d(30), right, d(96));
        stroke(c, r, d(1.2f), ink);
        text(c, "VEJRET", ob, 8.5f, ink, r.centerX(), d(48), Paint.Align.CENTER, 0.16f);
        String wx = weather.isEmpty() ? "KLART" : up(weather);
        text(c, wx, pb, fit(wx, pb, 21, 0, d(110)), ink, r.centerX(), d(78), Paint.Align.CENTER, 0);
        // Avisens navn
        float mast = fit("Daily Planet", fr, 70, 0, right - m - d(290));
        text(c, "Daily Planet", fr, mast, ink, w / 2f, d(90), Paint.Align.CENTER, 0);
        line(c, m, d(108), right, d(108), d(1.6f), ink);
        line(c, m, d(111.5f), right, d(111.5f), d(0.7f), ink);
        String[] dl = {up(fmt("EEEE d. MMMM yyyy")), "METROPOLIS-UDGAVEN", "NR. " + fmt("MMdd"), "PRIS 5 KR."};
        float span = right - m;
        text(c, dl[0], ob, 9.5f, ink, m, d(126), Paint.Align.LEFT, 0.16f);
        text(c, dl[1], ob, 9.5f, ink, m + span * 0.47f, d(126), Paint.Align.CENTER, 0.16f);
        text(c, dl[2], ob, 9.5f, ink, m + span * 0.72f, d(126), Paint.Align.CENTER, 0.16f);
        text(c, dl[3], ob, 9.5f, ink, right, d(126), Paint.Align.RIGHT, 0.16f);
        line(c, m, d(136), right, d(136), d(1), ink);
        // Klokken er dagens hovedoverskrift
        String head = "KLOKKEN ER " + time();
        text(c, head, pb, fit(head, pb, 82, -0.01f, span), ink, w / 2f, d(214), Paint.Align.CENTER, -0.01f);
        String g = greeting.replaceAll("[!.,]+$", "");
        String sub = (g.isEmpty() ? "Redaktionen" : g) + ". Dagens program kan læses herunder.";
        Typeface it = Typeface.create(Fonts.playfair(getContext()), Typeface.ITALIC);
        text(c, sub, it, fit(sub, it, 15, 0, span), Color.parseColor("#333333"), w / 2f, d(243), Paint.Align.CENTER, 0);
        line(c, m, d(262), right, d(262), d(1), ink);
    }

    // Spider-Man: tegneseriefelter

    private void spiderman(Canvas c, int w, int h) {
        int ink = Color.parseColor("#111111"), red = Color.parseColor("#C8102E"), blue = Color.parseColor("#1B3F8F"),
                yellow = Color.parseColor("#FFE14D");
        float pad = d(24), gap = d(12), top = d(10), bottom = h - d(14);
        RectF a = new RectF(pad, top, pad + (w - 2 * pad - gap) * 0.46f, bottom);
        RectF b = new RectF(a.right + gap, top, w - pad, bottom);
        Typeface bang = Fonts.bangers(getContext()), comic = Fonts.comic(getContext());

        // Felt 1: klokken
        rect(c, a.left, a.top, a.right, a.bottom, red);
        halftone(c, a, false);
        c.save();
        c.clipRect(a);
        Paint web = new Paint(Paint.ANTI_ALIAS_FLAG);
        web.setStyle(Paint.Style.STROKE);
        web.setStrokeWidth(d(1.3f));
        web.setColor(Color.argb(130, 17, 17, 17));
        web(c, web, a.left, a.top, d(150), 1, 1);
        c.restore();
        caption(c, up(fmt("EEEE d. MMMM")), a.left + d(12), a.top + d(11), comic);
        String t = time();
        float size = fit(t, bang, 118, 0.03f, a.width() * 0.6f);
        float tx = a.left + d(24), ty = a.bottom - d(26);
        p.setTypeface(bang);
        p.setTextSize(sp(size));
        p.setLetterSpacing(0.03f);
        p.setTextAlign(Paint.Align.LEFT);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(d(5));
        p.setColor(blue);
        c.drawText(t, tx + d(6), ty + d(6), p);
        p.setColor(ink);
        c.drawText(t, tx, ty, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(yellow);
        c.drawText(t, tx, ty, p);
        // Taleboble med hilsenen
        RectF bub = new RectF(a.right - d(146), a.top + d(14), a.right - d(14), a.top + d(92));
        path.reset();
        path.moveTo(bub.left + d(34), bub.bottom - d(10));
        path.lineTo(bub.left + d(12), bub.bottom + d(26));
        path.lineTo(bub.left + d(58), bub.bottom - d(4));
        path.close();
        s.setShader(null);
        s.setPathEffect(null);
        s.setStyle(Paint.Style.FILL);
        s.setColor(Color.WHITE);
        c.drawPath(path, s);
        c.drawOval(bub, s);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(d(2.5f));
        s.setColor(ink);
        c.drawOval(bub, s);
        c.drawLine(bub.left + d(34), bub.bottom - d(9), bub.left + d(12), bub.bottom + d(26), s);
        c.drawLine(bub.left + d(12), bub.bottom + d(26), bub.left + d(56), bub.bottom - d(5), s);
        s.setStyle(Paint.Style.FILL);
        s.setColor(Color.WHITE);
        c.drawRect(bub.left + d(36), bub.bottom - d(12), bub.left + d(54), bub.bottom - d(3), s);
        String g = up(greeting.isEmpty() ? "Hej!" : greeting);
        if (!g.endsWith("!")) g = g.replaceAll("[.,]+$", "") + "!";
        String[] lines = split(g);
        float gs = 19f;
        for (String l : lines) gs = Math.min(gs, fit(l, comic, 19f, 0, bub.width() - d(34)));
        float ly = bub.centerY() - (lines.length - 1) * sp(gs) * 0.6f + sp(gs) * 0.35f;
        for (String l : lines) {
            text(c, l, comic, gs, ink, bub.centerX(), ly, Paint.Align.CENTER, 0);
            ly += sp(gs) * 1.2f;
        }
        stroke(c, a, d(3.5f), ink);

        // Felt 2: vejret derude
        rect(c, b.left, b.top, b.right, b.bottom, blue);
        if (photo != null) {
            photo(c, b, tint(Color.parseColor("#9EB6FF"), 1f), 150);
        } else {
            skyline(c, b, Color.parseColor("#0A1A3D"), false);
        }
        halftone(c, b, true);
        caption(c, "IMENS, DERUDE…", b.left + d(12), b.top + d(11), comic);
        String wx = weather.isEmpty() ? quiet() : weatherWords(weather);
        String big = up(wx);
        float ws = fit(big, bang, 74, 0.03f, b.width() - d(48));
        p.setTypeface(bang);
        p.setTextSize(sp(ws));
        p.setLetterSpacing(0.03f);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeWidth(d(5));
        p.setColor(ink);
        c.drawText(big, b.right - d(22), b.top + d(122), p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        c.drawText(big, b.right - d(22), b.top + d(122), p);
        if (!status.isEmpty()) {
            String part = up(status.split(" {2}· {2}")[0]);
            p.setTypeface(comic);
            p.setTextSize(sp(12));
            p.setLetterSpacing(0.02f);
            float tw = Math.min(p.measureText(part), b.width() - d(60));
            part = clip(part, comic, 12, 0.02f, b.width() - d(60));
            RectF cap = new RectF(b.right - d(20) - tw - d(18), b.bottom - d(44), b.right - d(20), b.bottom - d(16));
            boxed(c, cap, yellow, ink);
            text(c, part, comic, 12, ink, cap.left + d(9), cap.bottom - d(9), Paint.Align.LEFT, 0.02f);
        }
        stroke(c, b, d(3.5f), ink);
    }

    /** Uden vejrdata: hvad byen laver lige nu, som en fortællerboks ville sige det. */
    private static String quiet() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (h < 5) return "Stille i nat";
        if (h < 10) return "Byen vågner";
        if (h < 17) return "Alt er roligt";
        if (h < 22) return "Lysene tændes";
        return "Stille i nat";
    }

    /** "Regn 9°" bliver til "9° og regn", som en tegneserie ville skrive det. */
    private static String weatherWords(String w) {
        int i = w.lastIndexOf(' ');
        if (i > 0 && w.substring(i + 1).contains("°")) return w.substring(i + 1) + " og " + w.substring(0, i).toLowerCase(DA);
        return w;
    }

    private static String[] split(String t) {
        if (t.length() < 13) return new String[]{t};
        int mid = t.length() / 2, best = -1;
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) == ' ' && (best < 0 || Math.abs(i - mid) < Math.abs(best - mid))) best = i;
        }
        if (best < 0) return new String[]{t};
        return new String[]{t.substring(0, best), t.substring(best + 1)};
    }

    private void caption(Canvas c, String t, float x, float y, Typeface comic) {
        p.setTypeface(comic);
        p.setTextSize(sp(12));
        p.setLetterSpacing(0.02f);
        float tw = p.measureText(t);
        RectF cap = new RectF(x, y, x + tw + d(16), y + d(24));
        boxed(c, cap, Color.parseColor("#FFE14D"), Color.parseColor("#111111"));
        text(c, t, comic, 12, Color.parseColor("#111111"), x + d(8), y + d(16.5f), Paint.Align.LEFT, 0.02f);
    }

    private void boxed(Canvas c, RectF box, int fill, int ink) {
        rect(c, box.left, box.top, box.right, box.bottom, fill);
        stroke(c, box, d(2), ink);
    }

    private void skyline(Canvas c, RectF box, int color, boolean outline) {
        s.setShader(null);
        s.setPathEffect(null);
        s.setColor(color);
        s.setStyle(outline ? Paint.Style.STROKE : Paint.Style.FILL);
        s.setStrokeWidth(d(1));
        long seed = 11;
        float x = box.left;
        while (x < box.right) {
            seed = (seed * 9301 + 49297) % 233280;
            float bw = d(18) + d(40) * (seed / 233280f);
            seed = (seed * 9301 + 49297) % 233280;
            float bh = box.height() * (0.25f + 0.6f * (seed / 233280f));
            c.drawRect(x, box.bottom - bh, Math.min(box.right, x + bw - d(3)), box.bottom, s);
            x += bw;
        }
    }

    // Batman: Batcomputerens skærm

    private void batman(Canvas c, int w, int h) {
        int amber = Color.parseColor("#F5C518"), dim = Color.parseColor("#7C7462"), pale = Color.parseColor("#B9B2A0");
        Typeface mono = Fonts.jetMono(getContext()), bs = Fonts.shoulders(getContext());
        float right = w - d(44), left = d(8);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String part = hour < 5 || hour >= 22 ? "NAT" : hour < 10 ? "MORGEN" : hour < 17 ? "DAG" : "AFTEN";
        text(c, "GOTHAM CITY // " + part, mono, 10, HudView.withAlpha(amber, 0.85f), right, d(38), Paint.Align.RIGHT, 0.12f);
        text(c, "SAG " + fmt("yyyy-MMdd") + " · HULEN", mono, 9, dim, right, d(54), Paint.Align.RIGHT, 0.1f);
        text(c, time(), bs, fit(time(), bs, 116, 0.02f, right - left), amber, right, d(158), Paint.Align.RIGHT, 0.02f);
        String info = up(fmt("EEE dd MMM")).replace(".", "") + (weather.isEmpty() ? "" : " · " + up(weather));
        text(c, clip(info, mono, 9.5f, 0.12f, right - left), mono, 9.5f, pale, right, d(180), Paint.Align.RIGHT, 0.12f);

        RectF slab = new RectF(left, d(200), right, Math.min(h - d(78), d(442)));
        rect(c, slab.left, slab.top, slab.right, slab.bottom, Color.parseColor("#101317"));
        c.save();
        c.clipRect(slab);
        if (photo != null) photo(c, slab, tint(amber, 0.95f), 150);
        else skyline(c, new RectF(slab.left, slab.top + slab.height() * 0.3f, slab.right, slab.bottom - d(30)), HudView.withAlpha(amber, 0.55f), true);
        s.setColor(Color.argb(16, 245, 197, 24));
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(1);
        for (float gx = slab.left; gx < slab.right; gx += d(20)) c.drawLine(gx, slab.top, gx, slab.bottom, s);
        for (float gy = slab.top; gy < slab.bottom; gy += d(20)) c.drawLine(slab.left, gy, slab.right, gy, s);
        float scan = slab.top + slab.height() * 0.58f;
        s.setStyle(Paint.Style.FILL);
        s.setShader(new LinearGradient(slab.left, 0, slab.right, 0, new int[]{Color.TRANSPARENT, amber, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        c.drawRect(slab.left, scan - d(1), slab.right, scan + d(1), s);
        s.setShader(new LinearGradient(0, scan - d(14), 0, scan + d(14), new int[]{Color.TRANSPARENT, Color.argb(40, 245, 197, 24), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        c.drawRect(slab.left, scan - d(14), slab.right, scan + d(14), s);
        s.setShader(null);
        rect(c, slab.left, slab.bottom - d(30), slab.right, slab.bottom, Color.argb(220, 16, 19, 23));
        c.restore();
        line(c, slab.left, slab.bottom - d(30), slab.right, slab.bottom - d(30), 1, Color.parseColor("#2B3038"));
        stroke(c, slab, 1, Color.parseColor("#2B3038"));
        text(c, "OVERVÅGNING · SEKTOR 7", mono, 9, amber, slab.left + d(11), slab.top + d(18), Paint.Align.LEFT, 0.14f);
        text(c, "ALT ROLIGT", mono, 9, pale, slab.left + d(11), slab.bottom - d(11), Paint.Align.LEFT, 0.1f);
        text(c, "▮ LIVE", mono, 9, amber, slab.right - d(11), slab.bottom - d(11), Paint.Align.RIGHT, 0.1f);

        String g = up(greeting.replaceAll("[!.,]+$", ""));
        text(c, g, bs, fit(g, bs, 22, 0.06f, right - left), Color.parseColor("#E9E4D6"), right, slab.bottom + d(34), Paint.Align.RIGHT, 0.06f);
        if (!status.isEmpty())
            text(c, clip(up(status), mono, 8.5f, 0.08f, right - left), mono, 8.5f, Color.parseColor("#8E8C84"), right, slab.bottom + d(54), Paint.Align.RIGHT, 0.08f);
    }

    // Jarvis: buereaktoren

    private void jarvis(Canvas c, int w, int h) {
        int cyan = Color.parseColor("#5EF2FF"), gold = Color.parseColor("#F2B33D");
        Typeface mono = Fonts.techMono(getContext()), raj = Fonts.rajdhani(getContext());
        float left = d(56);
        float rad = Math.min(d(134), (w - left) / 2f - d(4));
        float cx = left + (w - left) / 2f - d(6), cy = d(82) + rad + d(16);
        float ar = rad - d(25);
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        int sec = cal.get(Calendar.SECOND);
        if (pass != STILL) {
            // Det eneste, der flytter sig hvert sekund
            s.setStyle(Paint.Style.STROKE);
            s.setPathEffect(null);
            s.setShader(null);
            s.setStrokeWidth(d(4));
            s.setColor(gold);
            s.setStrokeCap(Paint.Cap.BUTT);
            r.set(cx - ar, cy - ar, cx + ar, cy + ar);
            c.drawArc(r, -90, 360f * Math.max(1, sec) / 60f, false, s);
            text(c, String.format(DA, "%02d SEK", sec), mono, 11, gold, cx, cy + d(48), Paint.Align.CENTER, 0.2f);
        }
        if (pass == MOVING) return;
        text(c, "J.A.R.V.I.S // ONLINE", mono, 10.5f, cyan, left, d(44), Paint.Align.LEFT, 0.12f);
        String info = up(fmt("EEE dd.MM.yyyy")).replace(".", ".") + (weather.isEmpty() ? "" : "  " + up(weather));
        text(c, clip(info, mono, 9.5f, 0.08f, w - left - d(10)), mono, 9.5f, Color.parseColor("#8FDFEA"), left, d(60), Paint.Align.LEFT, 0.08f);
        s.setStyle(Paint.Style.FILL);
        s.setShader(new LinearGradient(left, 0, w, 0, new int[]{cyan, Color.argb(20, 94, 242, 255), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        c.drawRect(left, d(68), w, d(68) + Math.max(1, d(0.6f)), s);
        s.setShader(null);

        s.setStyle(Paint.Style.STROKE);
        s.setPathEffect(null);
        s.setStrokeWidth(d(1));
        s.setColor(HudView.withAlpha(cyan, 0.3f));
        c.drawCircle(cx, cy, rad, s);
        float tr = rad - d(10);
        float circ = (float) (2 * Math.PI * tr);
        s.setStrokeWidth(d(8));
        s.setColor(HudView.withAlpha(cyan, 0.55f));
        s.setPathEffect(new DashPathEffect(new float[]{d(1), circ / 120 - d(1)}, 0));
        c.drawCircle(cx, cy, tr, s);
        s.setPathEffect(null);
        s.setStrokeWidth(d(4));
        s.setColor(HudView.withAlpha(cyan, 0.12f));
        c.drawCircle(cx, cy, ar, s);
        float sr = rad - d(46);
        float scirc = (float) (2 * Math.PI * sr);
        s.setStrokeWidth(d(15));
        s.setColor(HudView.withAlpha(cyan, 0.4f));
        s.setPathEffect(new DashPathEffect(new float[]{scirc / 10 - d(6), d(6)}, 0));
        c.drawCircle(cx, cy, sr, s);
        s.setPathEffect(null);
        s.setStyle(Paint.Style.FILL);
        s.setShader(new RadialGradient(cx, cy, rad - d(55), new int[]{Color.argb(120, 214, 251, 255), Color.argb(26, 43, 215, 240), Color.TRANSPARENT},
                new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, rad - d(55), s);
        s.setShader(null);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(d(0.8f));
        s.setColor(HudView.withAlpha(cyan, 0.4f));
        c.drawCircle(cx, cy, rad - d(62), s);

        text(c, "TID", mono, 10, cyan, cx, cy - d(42), Paint.Align.CENTER, 0.3f);
        p.setShadowLayer(d(14), 0, 0, HudView.withAlpha(cyan, 0.7f));
        p.setTypeface(raj);
        p.setTextSize(sp(fit(time(), raj, 74, 0, (rad - d(62)) * 1.8f)));
        p.setLetterSpacing(0);
        p.setTextAlign(Paint.Align.CENTER);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(Color.parseColor("#EAFDFF"));
        c.drawText(time(), cx, cy + d(22), p);
        p.clearShadowLayer();

        float gy = cy + rad + d(44);
        String g = up(greeting.replaceAll("[!.,]+$", ""));
        text(c, g, raj, fit(g, raj, 20, 0.2f, w - left - d(6)), Color.parseColor("#EAFDFF"), cx, gy, Paint.Align.CENTER, 0.2f);
        String st = status.isEmpty() ? "ALLE SYSTEMER KØRER" : up(status);
        text(c, clip(st, mono, 9.5f, 0.1f, w - left - d(6)), mono, 9.5f, cyan, cx, gy + d(22), Paint.Align.CENTER, 0.1f);
    }

    // Biograf: markisen over indgangen

    private void biograf(Canvas c, int w, int h) {
        int gold = Color.parseColor("#C9A24F"), light = Color.parseColor("#E8C36A"), ink = Color.parseColor("#151515");
        Typeface bebas = Fonts.bebas(getContext()), lime = Fonts.limelight(getContext());
        float mw = Math.min(d(540), w - d(380)), mx = (w - mw) / 2, my = d(14), mh = d(156);
        RectF outer = new RectF(mx, my, mx + mw, my + mh);
        rect(c, outer.left, outer.top, outer.right, outer.bottom, Color.parseColor("#1D1307"));
        stroke(c, outer, d(2.5f), gold);
        RectF band = new RectF(outer.left + d(9), outer.top + d(9), outer.right - d(9), outer.bottom - d(9));
        bulbs(c, band, d(18), d(3.2f));
        RectF board = new RectF(outer.left + d(18), outer.top + d(18), outer.right - d(18), outer.bottom - d(18));
        rect(c, board.left, board.top, board.right, board.bottom, Color.parseColor("#F4EFE3"));
        float row = board.height() / 3.2f;
        line(c, board.left, board.top + row, board.right, board.top + row, d(2), Color.parseColor("#CFC6B2"));
        line(c, board.left, board.bottom - row, board.right, board.bottom - row, d(2), Color.parseColor("#CFC6B2"));
        stroke(c, board, d(2), gold);
        text(c, "NU VISES", bebas, 18, ink, board.centerX(), board.top + row - d(8), Paint.Align.CENTER, 0.3f);
        text(c, time(), bebas, 58, ink, board.centerX(), board.bottom - row - d(6), Paint.Align.CENTER, 0.14f);
        String bottom = up(fmt("EEEE")) + " · SAL " + (name.isEmpty() ? "1" : up(name));
        text(c, bottom, bebas, fit(bottom, bebas, 18, 0.3f, board.width() - d(20)), ink, board.centerX(), board.bottom - d(9), Paint.Align.CENTER, 0.3f);

        float leftX = (d(75) + mx) / 2, rightX = (mx + mw + w - d(75)) / 2;
        text(c, "FOYER", lime, 19, light, leftX, d(80), Paint.Align.CENTER, 0);
        String wx = weather.isEmpty() ? up(fmt("d. MMMM")) : up(weather);
        text(c, wx, bebas, fit(wx, bebas, 13, 0.2f, mx - d(95)), gold, leftX, d(100), Paint.Align.CENTER, 0.2f);
        text(c, "SAL 1", lime, 19, light, rightX, d(80), Paint.Align.CENTER, 0);
        text(c, "→ INDGANG", bebas, 13, gold, rightX, d(100), Paint.Align.CENTER, 0.2f);
    }

    // Jul: dagens store låge

    private void jul(Canvas c, int w, int h) {
        int gold = Color.parseColor("#E2BD62"), cream = Color.parseColor("#F6EAD2"), green = Color.parseColor("#174A35");
        Typeface cav = Fonts.caveat(getContext()), fr = Fonts.fraunces(getContext()), fri = Fonts.frauncesItalic(getContext());
        float left = d(48), right = w - d(12);
        float size = Math.min(right - left, h - d(78) - d(150));
        RectF door = new RectF(left, d(80), left + size, d(80) + size);
        rect(c, door.left, door.top, door.right, door.bottom, green);
        stroke(c, door, d(2), gold);
        RectF inner = new RectF(door.left + d(7), door.top + d(7), door.right - d(7), door.bottom - d(7));
        stroke(c, inner, d(1), HudView.withAlpha(gold, 0.6f));
        s.setStyle(Paint.Style.FILL);
        s.setColor(gold);
        c.drawCircle(door.right - d(16), door.centerY() + d(10), d(5), s);
        String date = cap(fmt("EEEE d. MMMM"));
        text(c, date, cav, fit(date, cav, 25, 0, door.width() - d(30)), gold, door.centerX(), door.top + size * 0.2f, Paint.Align.CENTER, 0);
        text(c, time(), fr, fit(time(), fr, 76, 0, door.width() - d(56)), cream, door.centerX(), door.top + size * 0.58f, Paint.Align.CENTER, 0);
        String count = untilChristmas();
        text(c, count, cav, fit(count, cav, 23, 0, door.width() - d(30)), cream, door.centerX(), door.top + size * 0.8f, Paint.Align.CENTER, 0);
        String g = greeting.replaceAll("[!.,]+$", "");
        text(c, g, cav, fit(g, cav, 22, 0, size), gold, door.centerX(), door.bottom + d(34), Paint.Align.CENTER, 0);
        if (!status.isEmpty())
            text(c, clip(status, fri, 11, 0, size), fri, 11, HudView.withAlpha(cream, 0.8f), door.centerX(), door.bottom + d(54), Paint.Align.CENTER, 0);
        line(c, door.left + d(30), door.bottom + d(66), door.left + d(30), door.bottom + d(84), d(1), gold);
        heart(c, door.left + d(30), door.bottom + d(108), d(52), -6);
    }

    private String untilChristmas() {
        Calendar today = Calendar.getInstance();
        today.setTime(now);
        int y = today.get(Calendar.YEAR);
        Calendar eve = Calendar.getInstance();
        eve.clear();
        eve.set(y, Calendar.DECEMBER, 24);
        int day = today.get(Calendar.DAY_OF_YEAR), target = eve.get(Calendar.DAY_OF_YEAR);
        if (day == target) return "Det er juleaften!";
        if (day > target) return "Glædelig jul";
        int n = target - day;
        return n == 1 ? "1 dag til juleaften" : n + " dage til juleaften";
    }

    // Hygge: seddel med tape og et stearinlys

    private void hygge(Canvas c, int w, int h) {
        int ink = Color.parseColor("#3A2C22"), brown = Color.parseColor("#9A5B3A"), soft = Color.parseColor("#8A7560");
        Typeface hand = Fonts.reenie(getContext()), corm = Fonts.cormorant(getContext()), it = Fonts.cormorantItalic(getContext());
        RectF note = new RectF(d(56), d(24), d(316), d(156));
        c.save();
        c.rotate(-2.5f, note.centerX(), note.centerY());
        s.setShader(null);
        s.setStyle(Paint.Style.FILL);
        s.setColor(Color.argb(46, 60, 35, 15));
        c.drawRect(note.left + d(4), note.top + d(7), note.right + d(4), note.bottom + d(7), s);
        s.setColor(Color.parseColor("#FBF6EA"));
        c.drawRect(note, s);
        c.save();
        c.rotate(4, note.centerX(), note.top);
        s.setColor(Color.argb(190, 226, 206, 160));
        c.drawRect(note.centerX() - d(38), note.top - d(9), note.centerX() + d(38), note.top + d(11), s);
        c.restore();
        text(c, "kl. " + time().replace(':', '.'), hand, 62, ink, note.left + d(20), note.top + d(62), Paint.Align.LEFT, 0);
        String day = fmt("EEEE") + (weather.isEmpty() ? "" : " · " + weatherWords(weather).toLowerCase(DA));
        text(c, day, hand, fit(day, hand, 25, 0, note.width() - d(30)), ink, note.left + d(22), note.top + d(96), Paint.Align.LEFT, 0);
        text(c, mood(), hand, 21, brown, note.left + d(22), note.top + d(120), Paint.Align.LEFT, 0);
        c.restore();

        float gx = d(360);
        float maxW = w - gx - d(170);
        String g = greeting.replaceAll("[!.,]+$", "");
        text(c, g, it, fit(g, it, 40, 0, maxW), ink, gx, d(80), Paint.Align.LEFT, 0);
        String st = status.isEmpty() ? up(fmt("EEEE d. MMMM")) : up(status);
        text(c, clip(st, corm, 12.5f, 0.24f, maxW), corm, 12.5f, soft, gx + d(2), d(108), Paint.Align.LEFT, 0.24f);

        // Stearinlyset i hjørnet
        float cx = w - d(110), base = h - d(6);
        s.setStyle(Paint.Style.FILL);
        s.setShader(new RadialGradient(cx, base - d(80), d(150), new int[]{Color.argb(107, 255, 190, 100), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, base - d(80), d(150), s);
        s.setShader(new LinearGradient(cx - d(17), 0, cx + d(17), 0, new int[]{Color.parseColor("#EFE6D4"), Color.parseColor("#FBF6EA"), Color.parseColor("#E0D4BF")},
                new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
        r.set(cx - d(17), base - d(68), cx + d(17), base);
        c.drawRoundRect(r, d(3), d(3), s);
        s.setShader(null);
        line(c, cx, base - d(68), cx, base - d(75), d(1.5f), ink);
        // Flammen: en dråbe med varm kant og lys kerne, der sidder på vægen
        float fb = base - d(73), ft = fb - d(34);
        s.setStyle(Paint.Style.FILL);
        Path fl = new Path();
        fl.moveTo(cx, ft);
        fl.cubicTo(cx + d(4), ft + d(10), cx + d(10), fb - d(14), cx + d(9), fb - d(7));
        fl.cubicTo(cx + d(8), fb - d(1), cx + d(4), fb, cx, fb);
        fl.cubicTo(cx - d(4), fb, cx - d(8), fb - d(1), cx - d(9), fb - d(7));
        fl.cubicTo(cx - d(10), fb - d(14), cx - d(4), ft + d(10), cx, ft);
        fl.close();
        s.setShader(new LinearGradient(0, ft, 0, fb, new int[]{Color.parseColor("#FF8A1E"), Color.parseColor("#FFB23E"), Color.parseColor("#FFD27A")},
                new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
        c.drawPath(fl, s);
        s.setShader(new RadialGradient(cx, fb - d(9), d(9), new int[]{Color.parseColor("#FFFBEA"), Color.argb(0, 255, 240, 190)}, null, Shader.TileMode.CLAMP));
        r.set(cx - d(5), fb - d(19), cx + d(5), fb - d(1));
        c.drawOval(r, s);
        s.setShader(null);
        s.setColor(Color.argb(120, 70, 110, 200));
        r.set(cx - d(3), fb - d(5), cx + d(3), fb);
        c.drawOval(r, s);
    }

    private String mood() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        int hr = cal.get(Calendar.HOUR_OF_DAY);
        if (hr < 5) return "tid til en sidste";
        if (hr < 10) return "kaffe først";
        if (hr < 17) return "en rolig dag";
        if (hr < 22) return "perfekt til en film";
        return "tæppe og en serie";
    }
}
