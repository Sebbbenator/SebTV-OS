package dk.selveste.launcher;

import android.animation.ValueAnimator;
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
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import java.util.Calendar;
import java.util.Locale;

/**
 * Opstarten, når tv'et tændes. Hver person vælger selv: temaets egen (avisens EKSTRA!, tegneseriens THWIP,
 * Batcomputeren, biografens tæppe, julehjertet, stearinlyset), Jarvis' ringe, den bløde med hjerte, kun en
 * hilsen eller ingenting. Overskrift og undertekst kan skrives om, og den kan vises hver gang, første gang om
 * dagen eller kun om morgenen.
 */
final class BootIntro extends View {

    static final String[][] STYLES = {{"tema", "Temaets egen"}, {"jarvis", "Jarvis ringe"}, {"blod", "Blød med hjerte"},
            {"enkel", "Kun en hilsen"}, {"fra", "Ingen opstart"}};
    static final String[][] WHEN = {{"altid", "Hver gang tv'et tændes"}, {"dag", "Første gang hver dag"},
            {"morgen", "Kun om morgenen (5 til 11)"}};
    static final String[][] LENGTH = {{"kort", "Kort (2 sekunder)"}, {"normal", "Normal (3 sekunder)"}, {"lang", "Lang (5 sekunder)"}};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private final String skin, greeting, title, subtitle;
    private final int accent;
    private final long duration;
    private float t;

    private BootIntro(Context c, String skin, String greeting, String title, String subtitle, int accent, long duration) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        this.skin = skin;
        this.greeting = greeting;
        this.title = title;
        this.subtitle = subtitle;
        this.accent = accent;
        this.duration = duration;
    }

    static String style(Prefs prefs) {
        return prefs.str("boot_style", "tema");
    }

    /** Skal opstarten vises nu? Husker også, at den er vist i dag. */
    static boolean due(Prefs prefs) {
        if ("fra".equals(style(prefs))) return false;
        Calendar cal = Calendar.getInstance();
        String when = prefs.str("boot_when", "altid");
        if ("morgen".equals(when)) {
            int h = cal.get(Calendar.HOUR_OF_DAY);
            if (h < 5 || h >= 11) return false;
        }
        String today = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
        if (!"altid".equals(when) && today.equals(prefs.str("boot_last_day", ""))) return false;
        prefs.setStr("boot_last_day", today);
        return true;
    }

    /** Opstarten, som personen har valgt den. */
    static View make(Context c, Prefs prefs, String greeting, int accent) {
        String style = style(prefs);
        String title = prefs.str("boot_title", "").trim();
        String sub = prefs.str("boot_sub", "").trim();
        long ms = "kort".equals(prefs.str("boot_length", "normal")) ? 2000 : "lang".equals(prefs.str("boot_length", "normal")) ? 5000 : 3200;
        String skin = Skin.key;
        if ("jarvis".equals(style) || "blod".equals(style) || ("tema".equals(style) && !own(skin))) {
            // De ældre temaer og de to klassiske: ringene eller hjertet
            boolean rings = "jarvis".equals(style) || ("tema".equals(style) && prefs.isJarvis());
            HudView h = new HudView(c, greeting, accent);
            if (rings) {
                String name = title.isEmpty() ? prefs.assistantName().toUpperCase(Locale.ROOT) : title.toUpperCase(Locale.ROOT);
                StringBuilder spaced = new StringBuilder();
                if (title.isEmpty()) for (char ch : name.toCharArray()) spaced.append(ch).append('.');
                else spaced.append(name);
                return h.titled(spaced.toString(), sub.isEmpty() ? "SYSTEMS ONLINE" : sub).lasting(ms);
            }
            return h.soft().titled(title.isEmpty() ? prefs.assistantName() : title, sub.isEmpty() ? "Velkommen hjem" : sub).lasting(ms);
        }
        if ("enkel".equals(style)) skin = "enkel";
        return new BootIntro(c, skin, greeting, title, sub, accent, ms);
    }

    /** De temaer, der har deres egen opstart. */
    static boolean own(String skin) {
        switch (skin) {
            case "superman": case "spiderman": case "batman": case "jarvis": case "biograf": case "jul": case "hygge": case "apple": return true;
            default: return false;
        }
    }

    static void play(View v, ViewGroup parent) {
        if (v instanceof HudView) {
            ((HudView) v).play(parent);
            return;
        }
        BootIntro b = (BootIntro) v;
        parent.addView(b, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(b.duration);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(x -> {
            b.t = (float) x.getAnimatedValue();
            if (b.t > 0.84f) b.setAlpha(1f - (b.t - 0.84f) / 0.16f);
            b.invalidate();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                parent.removeView(b);
            }
        });
        a.start();
    }

    // Hjælpere

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** 0 til 1 mellem to tidspunkter i animationen. */
    private float ph(float from, float to) {
        return clamp((t - from) / (to - from));
    }

    /** Et lille overslag, når noget smækker på plads. */
    private static float pop(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return (float) (1 + 2.2 * Math.pow(x - 1, 3) + 1.2 * Math.pow(x - 1, 2));
    }

    private void text(Canvas c, String txt, Typeface face, float sizeDp, int color, float x, float y, float spacing, float alpha) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(face);
        p.setTextSize(sizeDp * d);
        p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(spacing);
        p.setColor(HudView.withAlpha(color, alpha * Color.alpha(color) / 255f));
        c.drawText(txt, x, y, p);
    }

    /** Teksten skrumpet, så den kan stå på maxWidth. */
    private float fit(String txt, Typeface face, float sizeDp, float spacing, float maxWidth) {
        p.setTypeface(face);
        p.setLetterSpacing(spacing);
        p.setTextSize(sizeDp * d);
        float w = p.measureText(txt);
        return w <= maxWidth || w == 0 ? sizeDp : sizeDp * maxWidth / w;
    }

    private String or(String own, String def) {
        return own.isEmpty() ? def : own;
    }

    private static String up(String x) {
        return x.toUpperCase(new Locale("da", "DK"));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float cx = w / 2f, cy = h / 2f;
        Context ctx = getContext();
        if (bg == null || bg.getWidth() != w || bg.getHeight() != h) {
            // Baggrunden tegnes én gang; kun det, der bevæger sig, tegnes hvert billede
            try {
                bg = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                background(new Canvas(bg), w, h);
            } catch (OutOfMemoryError e) {
                bg = null;
                background(c, w, h);
            }
        }
        if (bg != null) c.drawBitmap(bg, 0, 0, null);
        switch (skin) {
            case "superman": {
                text(c, "Daily Planet", Fonts.fraktur(ctx), 46, Tiles.INK, cx, cy - 120 * d, 0, ph(0f, 0.15f));
                s.setColor(HudView.withAlpha(Tiles.INK, ph(0.05f, 0.2f)));
                float rw = 300 * d * ph(0.05f, 0.25f);
                c.drawRect(cx - rw, cy - 102 * d, cx + rw, cy - 100 * d, s);
                c.drawRect(cx - rw, cy - 97 * d, cx + rw, cy - 96 * d, s);
                // EKSTRA! smækker ind skråt
                float e = pop(ph(0.12f, 0.32f));
                if (e > 0) {
                    c.save();
                    c.translate(cx, cy - 20 * d);
                    c.rotate(-6 + 6 * (1 - e));
                    c.scale(0.4f + 0.6f * e, 0.4f + 0.6f * e);
                    s.setColor(Tiles.NEWS_RED);
                    r.set(-170 * d, -46 * d, 170 * d, 46 * d);
                    c.drawRect(r, s);
                    text(c, or(up(title), "EKSTRA!"), Fonts.playfairBlack(ctx), fit(or(up(title), "EKSTRA!"), Fonts.playfairBlack(ctx), 64, 0, 310 * d), Color.WHITE, 0, 22 * d, 0, 1);
                    c.restore();
                }
                String g = up(greeting.replaceAll("[!.,]+$", ""));
                text(c, g, Fonts.playfairBlack(ctx), fit(g, Fonts.playfairBlack(ctx), 54, 0, w - 160 * d), Tiles.INK, cx, cy + 92 * d, 0, ph(0.35f, 0.5f));
                text(c, or(subtitle, "Dagens avis er på gaden"), Fonts.playfair(ctx), 18, Color.parseColor("#4A4540"), cx, cy + 128 * d, 0, ph(0.45f, 0.6f));
                break;
            }
            case "spiderman": {
                float e = pop(ph(0.05f, 0.3f));
                // Et stjerneudbrud med et lydord
                c.save();
                c.translate(cx, cy - 40 * d);
                c.scale(e, e);
                c.rotate(-8);
                Path star = new Path();
                int n = 14;
                for (int i = 0; i < n * 2; i++) {
                    double a = Math.PI * i / n;
                    float rad = (i % 2 == 0 ? 190 : 120) * d;
                    float x = (float) (Math.cos(a) * rad * 1.35f), y = (float) (Math.sin(a) * rad * 0.85f);
                    if (i == 0) star.moveTo(x, y);
                    else star.lineTo(x, y);
                }
                star.close();
                s.setStyle(Paint.Style.FILL);
                s.setColor(Tiles.COMIC_YELLOW);
                c.drawPath(star, s);
                s.setShader(Stage.dots(d, Color.argb(50, 200, 40, 40), 9, 1.8f));
                c.drawPath(star, s);
                s.setShader(null);
                s.setStyle(Paint.Style.STROKE);
                s.setStrokeWidth(5 * d);
                s.setColor(Tiles.COMIC_INK);
                c.drawPath(star, s);
                s.setStyle(Paint.Style.FILL);
                String word = or(up(title), "THWIP!");
                float ws = fit(word, Fonts.bangers(ctx), 110, 0.03f, 300 * d);
                p.setTypeface(Fonts.bangers(ctx));
                p.setTextSize(ws * d);
                p.setTextAlign(Paint.Align.CENTER);
                p.setLetterSpacing(0.03f);
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                p.setStrokeWidth(8 * d);
                p.setColor(Tiles.COMIC_INK);
                c.drawText(word, 6 * d, 40 * d, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Tiles.COMIC_RED);
                c.drawText(word, 0, 34 * d, p);
                c.restore();
                // Fortællerboksen med hilsenen
                float g = ph(0.35f, 0.5f);
                if (g > 0) {
                    String gt = up(greeting);
                    float gs = fit(gt, Fonts.comic(ctx), 26, 0.02f, w - 220 * d);
                    p.setTypeface(Fonts.comic(ctx));
                    p.setTextSize(gs * d);
                    float tw = p.measureText(gt);
                    r.set(cx - tw / 2 - 22 * d, cy + 150 * d, cx + tw / 2 + 22 * d, cy + 200 * d);
                    s.setColor(HudView.withAlpha(Tiles.COMIC_INK, g));
                    c.drawRect(r.left + 6 * d, r.top + 6 * d, r.right + 6 * d, r.bottom + 6 * d, s);
                    s.setColor(HudView.withAlpha(Tiles.COMIC_YELLOW, g));
                    c.drawRect(r, s);
                    s.setStyle(Paint.Style.STROKE);
                    s.setStrokeWidth(3 * d);
                    s.setColor(HudView.withAlpha(Tiles.COMIC_INK, g));
                    c.drawRect(r, s);
                    s.setStyle(Paint.Style.FILL);
                    text(c, gt, Fonts.comic(ctx), gs, Tiles.COMIC_INK, cx, r.bottom - 16 * d, 0.02f, g);
                }
                if (!subtitle.isEmpty()) text(c, up(subtitle), Fonts.comic(ctx), 16, Tiles.COMIC_INK, cx, cy + 236 * d, 0.06f, ph(0.5f, 0.6f));
                break;
            }
            case "batman": {
                // En ravgul scanning løber ned over skærmen
                float sy = h * ph(0f, 0.35f);
                s.setShader(new LinearGradient(0, sy - 120 * d, 0, sy, Color.TRANSPARENT, Color.argb(70, 245, 197, 24), Shader.TileMode.CLAMP));
                c.drawRect(0, sy - 120 * d, w, sy, s);
                s.setShader(null);
                s.setColor(Tiles.AMBER);
                c.drawRect(0, sy - 1.5f * d, w, sy + 1.5f * d, s);
                String top = or(up(title), "GOTHAM CITY // ONLINE");
                int n = Math.round(top.length() * ph(0.2f, 0.45f));
                text(c, top.substring(0, n), Fonts.jetMono(ctx), 18, Tiles.AMBER, cx, cy - 60 * d, 0.3f, 1);
                String g = up(greeting.replaceAll("[!.,]+$", ""));
                text(c, g, Fonts.shoulders(ctx), fit(g, Fonts.shoulders(ctx), 84, 0.06f, w - 160 * d), Color.parseColor("#E9E4D6"), cx, cy + 40 * d, 0.06f, ph(0.45f, 0.6f));
                text(c, or(up(subtitle), "ALT ROLIGT I NAT"), Fonts.jetMono(ctx), 13, Color.parseColor("#8C8676"), cx, cy + 84 * d, 0.3f, ph(0.55f, 0.7f));
                break;
            }
            case "jarvis": {
                float g = ph(0f, 0.3f);
                float R = 150 * d;
                s.setStyle(Paint.Style.STROKE);
                s.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 3; i++) {
                    float rad = R * (0.55f + 0.25f * i) * (0.6f + 0.4f * g);
                    r.set(cx - rad, cy - 40 * d - rad, cx + rad, cy - 40 * d + rad);
                    s.setStrokeWidth((i == 1 ? 8 : 2) * d);
                    s.setColor(HudView.withAlpha(Tiles.CYAN, 0.3f + 0.2f * i));
                    c.drawArc(r, t * 400 * (i % 2 == 0 ? 1 : -1) + i * 60, 260 * g, false, s);
                }
                s.setColor(Tiles.GOLD);
                s.setStrokeWidth(3 * d);
                float gr = R * 1.1f;
                r.set(cx - gr, cy - 40 * d - gr, cx + gr, cy - 40 * d + gr);
                c.drawArc(r, -90, 360 * ph(0.05f, 0.6f), false, s);
                s.setStyle(Paint.Style.FILL);
                String name = or(up(title), "J.A.R.V.I.S.");
                text(c, name, Fonts.rajdhani(ctx), 30, Color.WHITE, cx, cy - 30 * d, 0.3f, ph(0.1f, 0.3f));
                String sub = or(up(subtitle), "SYSTEMS ONLINE");
                int n = Math.round(sub.length() * ph(0.35f, 0.55f));
                text(c, sub.substring(0, n), Fonts.techMono(ctx), 14, Tiles.CYAN, cx, cy + 170 * d, 0.3f, 1);
                text(c, up(greeting), Fonts.rajdhani(ctx), 26, Color.WHITE, cx, cy + 210 * d, 0.15f, ph(0.55f, 0.7f));
                break;
            }
            case "biograf": {
                text(c, or(title, "Forestillingen begynder"), Fonts.limelight(ctx), fit(or(title, "Forestillingen begynder"), Fonts.limelight(ctx), 52, 0, w - 240 * d),
                        Color.parseColor("#E8C36A"), cx, cy - 10 * d, 0, ph(0.3f, 0.5f));
                text(c, up(greeting), Fonts.bebas(ctx), 34, Tiles.CREAM, cx, cy + 50 * d, 0.2f, ph(0.4f, 0.55f));
                if (!subtitle.isEmpty()) text(c, up(subtitle), Fonts.bebas(ctx), 20, Tiles.CINEMA_GOLD, cx, cy + 86 * d, 0.3f, ph(0.5f, 0.6f));
                // Tæppet går fra hinanden
                float open = ph(0.08f, 0.45f);
                open = open * open * (3 - 2 * open);
                float half = w / 2f * (1 - open) + 75 * d * open;
                curtain(c, 0, half, h);
                curtain(c, w - half, w, h);
                break;
            }
            case "jul": {
                float e = pop(ph(0f, 0.3f));
                if (e > 0) Stage.heart(c, cx, cy - 70 * d, 150 * d * e, -6 + 6 * e);
                boolean december = Calendar.getInstance().get(Calendar.MONTH) == Calendar.DECEMBER;
                String head = or(title, december ? "Glædelig jul" : "Velkommen hjem");
                text(c, head, Fonts.frauncesItalic(ctx), fit(head, Fonts.frauncesItalic(ctx), 56, 0, w - 200 * d), Color.parseColor("#F3D989"), cx, cy + 90 * d, 0, ph(0.3f, 0.45f));
                text(c, or(subtitle, greeting), Fonts.caveat(ctx), 30, Color.parseColor("#F6EAD2"), cx, cy + 134 * d, 0, ph(0.42f, 0.56f));
                break;
            }
            case "hygge": {
                // Stearinlyset tændes: gløden vokser, og flammen kommer
                float glow = ph(0f, 0.4f);
                float fx = cx, base = cy - 10 * d;
                s.setShader(new RadialGradient(fx, base - 90 * d, 260 * d, Color.argb(Math.round(140 * glow), 255, 190, 100), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawCircle(fx, base - 90 * d, 260 * d, s);
                s.setShader(new LinearGradient(fx - 22 * d, 0, fx + 22 * d, 0, new int[]{Color.parseColor("#EFE6D4"), Color.parseColor("#FBF6EA"), Color.parseColor("#E0D4BF")},
                        new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
                r.set(fx - 22 * d, base - 80 * d, fx + 22 * d, base);
                c.drawRoundRect(r, 4 * d, 4 * d, s);
                s.setShader(null);
                s.setColor(Color.parseColor("#3A2C22"));
                c.drawRect(fx - 1 * d, base - 88 * d, fx + 1 * d, base - 80 * d, s);
                float f = ph(0.15f, 0.35f);
                if (f > 0) {
                    float fb = base - 86 * d, ft = fb - 40 * d * f;
                    Path fl = new Path();
                    fl.moveTo(fx, ft);
                    fl.cubicTo(fx + 5 * d, ft + 12 * d * f, fx + 11 * d, fb - 16 * d * f, fx + 10 * d * f, fb - 7 * d);
                    fl.cubicTo(fx + 9 * d, fb, fx + 4 * d, fb, fx, fb);
                    fl.cubicTo(fx - 4 * d, fb, fx - 9 * d, fb, fx - 10 * d * f, fb - 7 * d);
                    fl.cubicTo(fx - 11 * d, fb - 16 * d * f, fx - 5 * d, ft + 12 * d * f, fx, ft);
                    fl.close();
                    s.setShader(new LinearGradient(0, ft, 0, fb, new int[]{Color.parseColor("#FF8A1E"), Color.parseColor("#FFB23E"), Color.parseColor("#FFD27A")},
                            new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
                    c.drawPath(fl, s);
                    s.setShader(null);
                }
                text(c, or(title, greeting), Fonts.cormorantItalic(ctx), fit(or(title, greeting), Fonts.cormorantItalic(ctx), 58, 0, w - 200 * d),
                        Color.parseColor("#3A2C22"), cx, cy + 80 * d, 0, ph(0.35f, 0.5f));
                text(c, or(subtitle, "tæppe, te og noget godt at se"), Fonts.reenie(ctx), 30, Color.parseColor("#9A5B3A"), cx, cy + 122 * d, 0, ph(0.45f, 0.6f));
                break;
            }
            default: {
                // Apple og "kun en hilsen": roligt, mørkt og stort
                Typeface face = Typeface.create("sans-serif-medium", Typeface.NORMAL);
                String head = or(title, greeting);
                float k = ph(0f, 0.3f);
                c.save();
                c.translate(0, 12 * d * (1 - k));
                text(c, head, face, fit(head, face, 54, 0, w - 200 * d), Color.WHITE, cx, cy + 10 * d, 0, k);
                c.restore();
                if (!subtitle.isEmpty() || !title.isEmpty()) {
                    text(c, title.isEmpty() ? subtitle : (subtitle.isEmpty() ? greeting : subtitle), face, 22, Color.parseColor("#A1A1A6"), cx, cy + 56 * d, 0, ph(0.25f, 0.45f));
                }
                break;
            }
        }
    }

    private android.graphics.Bitmap bg;

    private void background(Canvas c, int w, int h) {
        switch (skin) {
            case "jarvis":
                c.drawColor(Color.parseColor("#02080D"));
                Backdrop.draw(getContext(), skin, c, w, h, d);
                break;
            case "jul": {
                Paint g = new Paint();
                g.setShader(new RadialGradient(w / 2f, h * 0.3f, w * 0.75f, Color.parseColor("#8A1522"), Color.parseColor("#5C0B15"), Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, g);
                break;
            }
            case "superman": case "spiderman": case "batman": case "biograf": case "hygge":
                Backdrop.draw(getContext(), skin, c, w, h, d);
                break;
            default:
                c.drawColor(Color.argb(240, 10, 10, 12));
                break;
        }
    }

    private void curtain(Canvas c, float l, float rr, int h) {
        s.setShader(new LinearGradient(l, 0, l + 30 * d, 0, new int[]{Color.parseColor("#3A070C"), Color.parseColor("#6A1019"), Color.parseColor("#3A070C")},
                null, Shader.TileMode.MIRROR));
        c.drawRect(l, 0, rr, h, s);
        s.setShader(new LinearGradient(0, h * 0.5f, 0, h, Color.TRANSPARENT, Color.argb(160, 0, 0, 0), Shader.TileMode.CLAMP));
        c.drawRect(l, 0, rr, h, s);
        s.setShader(null);
    }
}
