package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Appernes cards i temaets eget sprog: avisartikler med rasterfoto, tegneseriefelter, sagsmapper med
 * faneblad, HUD paneler med afskårne hjørner, filmplakater, kalenderlåger og indrammede billeder på en
 * hylde. Fokus er temaets egen: fotoet får farve, feltet springer ud, lågen åbner sig.
 */
final class Tiles {

    private Tiles() {
    }

    static final int APP = 0, SMALL = 1, MEDIA = 2;
    private static final Locale DA = new Locale("da", "DK");

    static final int INK = Color.parseColor("#151515");
    static final int NEWS_RED = Color.parseColor("#C8102E");
    static final int NEWS_BLUE = Color.parseColor("#0B3D91");
    static final int COMIC_INK = Color.parseColor("#111111");
    static final int COMIC_RED = Color.parseColor("#C8102E");
    static final int COMIC_YELLOW = Color.parseColor("#FFE14D");
    static final int AMBER = Color.parseColor("#F5C518");
    static final int SLATE = Color.parseColor("#14171C");
    static final int SLATE_LINE = Color.parseColor("#2B3038");
    static final int CYAN = Color.parseColor("#5EF2FF");
    static final int GOLD = Color.parseColor("#F2B33D");
    static final int CINEMA_GOLD = Color.parseColor("#C9A24F");
    static final int CREAM = Color.parseColor("#F1E3C0");
    static final int WINE = Color.parseColor("#3B0A10");
    static final int XMAS_GOLD = Color.parseColor("#E2BD62");
    static final int XMAS_RED = Color.parseColor("#8F1D2A");
    static final int XMAS_GREEN = Color.parseColor("#174A35");
    static final int XMAS_CREAM = Color.parseColor("#F6ECD6");
    static final int WALL_INK = Color.parseColor("#3A2C22");
    static final int WALL_BROWN = Color.parseColor("#7A4A2C");

    static boolean on() {
        return Stage.on();
    }

    static int dp(Context c, float v) {
        return Ui.dp(c, v);
    }

    /** Størrelsen på app nr. index i dp {bredde, højde}. Hyggens rammer er ikke lige store. */
    static int[] appSize(int index) {
        switch (Skin.key) {
            case "superman": return new int[]{163, 92};
            case "spiderman": return new int[]{168, 100};
            case "batman": return new int[]{136, 82};
            case "jarvis": return new int[]{118, 76};
            case "biograf": return new int[]{110, 165};
            case "jul": return new int[]{132, 120};
            case "hygge": {
                int[][] sizes = {{168, 116}, {150, 124}, {172, 112}, {156, 128}, {164, 118}, {148, 120}};
                return sizes[index % sizes.length];
            }
            default: return new int[]{208, 117};
        }
    }

    /** Afstand mellem cards i dp. */
    static int gap() {
        switch (Skin.key) {
            case "superman": return 22;
            case "hygge": return 22;
            case "batman": case "jul": return 16;
            default: return 18;
        }
    }

    /** Hvor meget et card vokser i fokus (1 = slet ikke; Superman og Hygge løfter sig i stedet). */
    static float scale(int kind) {
        switch (Skin.key) {
            case "superman": case "hygge": return 1f;
            case "jul": return kind == APP ? 1.03f : 1.05f;
            case "batman": case "jarvis": return 1.05f;
            default: return 1.08f;
        }
    }

    // Overskrifter

    /** Rækkernes navne i temaets sprog. */
    static String rename(String title) {
        switch (Skin.key) {
            case "superman":
                if ("APPS".equals(title)) return "DAGENS FORSIDE";
                if ("FORTSÆT AT SE".equals(title)) return "FORTSAT FRA SIDE 1";
                if ("NYT PÅ SERVEREN".equals(title)) return "NYHEDER";
                return title;
            case "batman":
                if ("APPS".equals(title)) return "FILER";
                if ("FORTSÆT AT SE".equals(title)) return "AKTIVE SAGER";
                if ("VÆRKTØJER".equals(title)) return "UDSTYR";
                return title;
            case "biograf":
                if ("APPS".equals(title)) return "PÅ PLAKATEN";
                if ("FORTSÆT AT SE".equals(title)) return "DINE BILLETTER";
                if ("NYT PÅ SERVEREN".equals(title)) return "PREMIERER";
                return title;
            case "jul":
                if ("APPS".equals(title)) return "LÅGERNE";
                if ("FORTSÆT AT SE".equals(title)) return "FORTSÆT";
                return title;
            case "jarvis":
            case "spiderman":
            case "hygge":
                if ("FORTSÆT AT SE".equals(title)) return "FORTSÆT";
                return title;
            default:
                return title;
        }
    }

    /** Overskrift over en række; true hvis temaet har sin egen. */
    static boolean header(Context c, TextView t, String text) {
        if (!on()) return false;
        String name = rename(text);
        String lower = name.toLowerCase(DA);
        String title = lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        t.setBackground(null);
        t.setRotation(0);
        t.setShadowLayer(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switch (Skin.key) {
            case "jarvis":
                t.setText("[ " + name + " ]");
                t.setTypeface(Fonts.rajdhani(c));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                t.setLetterSpacing(0.32f);
                t.setTextColor(CYAN);
                t.setPadding(0, dp(c, 20), 0, dp(c, 4));
                break;
            case "spiderman": {
                t.setText(name);
                t.setTypeface(Fonts.comic(c));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                t.setLetterSpacing(0.02f);
                t.setTextColor(COMIC_INK);
                GradientDrawable box = new GradientDrawable();
                box.setColor(COMIC_YELLOW);
                box.setStroke(dp(c, 2.5f), COMIC_INK);
                t.setBackground(box);
                t.setPadding(dp(c, 10), dp(c, 3), dp(c, 10), dp(c, 3));
                t.setRotation(-1.5f);
                lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(c, 18);
                lp.bottomMargin = dp(c, 2);
                break;
            }
            case "batman":
                t.setText(name);
                t.setTypeface(Fonts.shoulders(c));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                t.setLetterSpacing(0.24f);
                t.setTextColor(Color.parseColor("#E9E4D6"));
                t.setPadding(0, dp(c, 22), 0, dp(c, 12));
                t.setBackground(new LineAfter(t, Color.parseColor("#3A3420"), dp(c, 1), false));
                break;
            case "superman":
                t.setText(name);
                t.setTypeface(Fonts.oldStandardBold(c));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                t.setLetterSpacing(0.16f);
                t.setTextColor(INK);
                t.setPadding(0, dp(c, 20), 0, dp(c, 8));
                t.setBackground(new LineAfter(t, INK, dp(c, 1), false));
                break;
            case "biograf":
                t.setText(title);
                t.setTypeface(Fonts.limelight(c));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                t.setLetterSpacing(0);
                t.setTextColor(Color.parseColor("#E8C36A"));
                t.setGravity(Gravity.CENTER_HORIZONTAL);
                t.setPadding(0, dp(c, 18), 0, dp(c, 4));
                t.setBackground(new LineAfter(t, CINEMA_GOLD, dp(c, 1), true));
                break;
            case "jul":
                t.setText(title);
                t.setTypeface(Fonts.caveat(c));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
                t.setLetterSpacing(0);
                t.setTextColor(Color.parseColor("#F3D989"));
                t.setPadding(0, dp(c, 14), 0, dp(c, 2));
                // Lågerne står lige under kalenderens titel og behøver ingen overskrift
                if ("APPS".equals(text)) t.setVisibility(View.GONE);
                break;
            case "hygge":
                t.setText(lower);
                t.setTypeface(Fonts.reenie(c));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27);
                t.setLetterSpacing(0);
                t.setTextColor(Color.parseColor("#8A7560"));
                t.setPadding(dp(c, 6), dp(c, 12), 0, 0);
                break;
            default:
                return false;
        }
        t.setLayoutParams(lp);
        return true;
    }

    /** En streg efter overskriften (avis, Batman) eller på begge sider af den (Biograf). */
    static final class LineAfter extends Drawable {
        private final TextView t;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean around;

        LineAfter(TextView t, int color, float width, boolean around) {
            this.t = t;
            this.around = around;
            p.setColor(color);
            p.setStrokeWidth(Math.max(1, width));
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            Paint tp = t.getPaint();
            float tw = tp.measureText(t.getText().toString());
            android.graphics.Paint.FontMetrics fm = tp.getFontMetrics();
            float y = t.getPaddingTop() + (-fm.ascent) * 0.62f;
            float gap = 14 * t.getResources().getDisplayMetrics().density;
            if (around) {
                float cx = b.width() / 2f;
                c.drawLine(b.left, y, cx - tw / 2 - gap, y, p);
                c.drawLine(cx + tw / 2 + gap, y, b.right - gap * 3, y, p);
            } else {
                c.drawLine(t.getPaddingLeft() + tw + gap, y, b.right - gap * 3, y, p);
            }
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // Cards

    private static GradientDrawable box(int fill, int stroke, float strokePx, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if (strokePx > 0) g.setStroke((int) strokePx, stroke);
        g.setCornerRadius(radius);
        return g;
    }

    /** Fladen bag et card (hvile og fokus). */
    static Drawable background(Context c, int kind, boolean focused, int index) {
        switch (Skin.key) {
            case "superman":
                if (kind == APP) return null;
                return box(Color.parseColor(focused ? "#FBF8F0" : "#40FFFFFF"), focused ? NEWS_RED : INK, dp(c, focused ? 3 : 1), 0);
            case "spiderman":
                return box(kind == SMALL && focused ? COMIC_YELLOW : Color.WHITE, COMIC_INK, dp(c, focused && kind == APP ? 4.5f : 3.5f), 0);
            case "batman":
                return box(focused ? Color.parseColor("#1A1810") : SLATE, focused ? AMBER : SLATE_LINE, dp(c, focused ? 2 : 1), 0);
            case "jarvis":
                return new Pod(c, focused);
            case "biograf":
                if (kind == APP) {
                    GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{Color.parseColor("#5A0D14"), Color.parseColor("#2A0508"), Color.parseColor("#120204")});
                    g.setStroke(dp(c, focused ? 2 : 1), focused ? Color.parseColor("#FFD98A") : CINEMA_GOLD);
                    return g;
                }
                return box(CREAM, focused ? Color.parseColor("#FFD98A") : CREAM, dp(c, focused ? 2.5f : 0), dp(c, 2));
            case "jul":
                if (kind == APP) return box(Color.parseColor("#C99A3A"), focused ? Color.parseColor("#FFE9A8") : XMAS_GOLD, dp(c, focused ? 3 : 2), 0);
                return box(XMAS_CREAM, focused ? XMAS_GOLD : XMAS_CREAM, dp(c, focused ? 3 : 0), dp(c, 3));
            case "hygge": {
                if (kind == SMALL) return box(Color.parseColor("#FBF6EA"), Color.parseColor("#E5DCCD"), dp(c, 1), dp(c, 2));
                int[] frames = {Color.parseColor("#2C241E"), Color.parseColor("#B98A58"), Color.parseColor("#F3EFE7")};
                return box(Color.parseColor("#F8F4EC"), frames[index % frames.length], dp(c, 8), 0);
            }
            default:
                return null;
        }
    }

    /** Polygon med afskårne hjørner (Jarvis); guld og sigteklammer i fokus. */
    static final class Pod extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float cut, density;
        private final boolean focused;

        Pod(Context c, boolean focused) {
            this.focused = focused;
            density = c.getResources().getDisplayMetrics().density;
            cut = 10 * density;
            fill.setColor(focused ? Color.argb(235, 28, 20, 6) : Color.argb(205, 5, 20, 28));
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(focused ? 2 * density : Math.max(1, density));
            line.setColor(focused ? GOLD : Color.argb(150, 94, 242, 255));
        }

        static Path shape(float l, float t, float r, float b, float cut) {
            Path p = new Path();
            p.moveTo(l + cut, t);
            p.lineTo(r, t);
            p.lineTo(r, b - cut);
            p.lineTo(r - cut, b);
            p.lineTo(l, b);
            p.lineTo(l, t + cut);
            p.close();
            return p;
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float half = line.getStrokeWidth() / 2;
            Path p = shape(b.left + half, b.top + half, b.right - half, b.bottom - half, cut);
            c.drawPath(p, fill);
            c.drawPath(p, line);
            if (focused) {
                // Sigteklammer i hjørnerne
                Paint k = new Paint(Paint.ANTI_ALIAS_FLAG);
                k.setStyle(Paint.Style.STROKE);
                k.setStrokeWidth(2.5f * density);
                k.setColor(GOLD);
                float l = 12 * density, in = 4 * density;
                float x1 = b.left + in, y1 = b.top + in, x2 = b.right - in, y2 = b.bottom - in;
                c.drawLine(x2 - l, y1, x2, y1, k);
                c.drawLine(x2, y1, x2, y1 + l, k);
                c.drawLine(x1, y2, x1 + l, y2, k);
                c.drawLine(x1, y2 - l, x1, y2, k);
            }
        }

        @Override
        public void getOutline(Outline outline) {
            android.graphics.Rect b = getBounds();
            outline.setConvexPath(shape(b.left, b.top, b.right, b.bottom, cut));
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Hylden, rammerne står på (Hygge). */
    static final class Shelf extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float h;

        Shelf(Context c) {
            h = 13 * c.getResources().getDisplayMetrics().density;
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float top = b.bottom - h;
            p.setShader(new LinearGradient(0, b.bottom, 0, b.bottom + h * 1.4f, Color.argb(90, 70, 40, 15), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(b.left + h, b.bottom, b.right - h, b.bottom + h * 1.4f, p);
            p.setShader(new LinearGradient(0, top, 0, b.bottom, new int[]{Color.parseColor("#C99A68"), Color.parseColor("#A87A4C"), Color.parseColor("#8A6038")},
                    new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(b.left, top, b.right, b.bottom, p);
            p.setShader(null);
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Et gavemærke med hul og snor (Jul). */
    static final class Tag extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private final boolean focused;

        Tag(Context c, boolean focused) {
            this.focused = focused;
            density = c.getResources().getDisplayMetrics().density;
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float notch = 22 * density;
            Path path = new Path();
            path.moveTo(b.left + notch, b.top);
            path.lineTo(b.right, b.top);
            path.lineTo(b.right, b.bottom);
            path.lineTo(b.left + notch, b.bottom);
            path.lineTo(b.left, b.centerY());
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(XMAS_CREAM);
            c.drawPath(path, p);
            if (focused) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3 * density);
                p.setColor(XMAS_GOLD);
                c.drawPath(path, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.parseColor("#5C0B15"));
            c.drawCircle(b.left + 15 * density, b.centerY(), 5.5f * density, p);
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Lydord i en stjerne (Spider-Man). */
    static final class Burst extends View {
        private final String word;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Burst(Context c, String word) {
            super(c);
            this.word = word;
        }

        private Path star(float w, float h, float inset) {
            Path s = new Path();
            float cx = w / 2, cy = h / 2;
            for (int k = 0; k < 24; k++) {
                double a = Math.toRadians(k * 15);
                float rx = (k % 2 == 0 ? w / 2 : w * 0.34f) - inset, ry = (k % 2 == 0 ? h / 2 : h * 0.34f) - inset;
                float x = (float) (cx + rx * Math.cos(a)), y = (float) (cy + ry * Math.sin(a));
                if (k == 0) s.moveTo(x, y);
                else s.lineTo(x, y);
            }
            s.close();
            return s;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            p.setStyle(Paint.Style.FILL);
            p.setColor(COMIC_INK);
            c.drawPath(star(w, h, 0), p);
            p.setColor(COMIC_YELLOW);
            c.drawPath(star(w, h, 3 * d), p);
            p.setTypeface(Fonts.bangers(getContext()));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(h * 0.3f);
            p.setLetterSpacing(0.03f);
            p.setColor(COMIC_RED);
            c.drawText(word, w / 2, h / 2 + h * 0.11f, p);
        }
    }

    /** Pærer rundt om plakaten i fokus (Biograf). */
    static final class Bulbs extends View {
        Bulbs(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            Stage.bulbs(c, new RectF(4 * d, 4 * d, getWidth() - 4 * d, getHeight() - 4 * d), 14 * d, 2.2f * d);
        }
    }

    private static TextView text(Context c, String s, Typeface face, float sp, int color) {
        TextView t = Ui.text(c, s, sp, color);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        return t;
    }

    private static FrameLayout.LayoutParams at(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    private static final String[] NEWS = {"Fortsæt hvor du slap.", "Redaktionen anbefaler.", "Læs mere på side 3.",
            "Nyt i dag.", "Direkte og i arkivet.", "Eksklusivt.", "Anmeldt til seks stjerner.", "Mest læst i aften."};
    private static final String[] SFX = {"THWIP!", "POW!", "ZAP!", "BAM!", "WHAM!", "THWIP!"};
    private static final String[] POSTER = {"PREMIERE", "NU I BIOGRAFEN", "EKSKLUSIVT", "AFTENENS FILM", "FAMILIEFILM", "SIDSTE CHANCE"};
    /** Lågernes numre: blandet, men altid de samme. */
    private static final int[] DOORS = {7, 13, 2, 19, 11, 4, 22, 16, 9, 24, 1, 17, 5, 20, 14, 3, 21, 8, 12, 23, 6, 18, 10, 15};

    private static ColorMatrix gray() {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0);
        ColorMatrix contrast = new ColorMatrix(new float[]{1.15f, 0, 0, 0, -12, 0, 1.15f, 0, 0, -12, 0, 0, 1.15f, 0, -12, 0, 0, 0, 1, 0});
        m.postConcat(contrast);
        return m;
    }

    /** Appens billede i hvile: rasterfoto, hologram, ravgul overvågning. Null = fuld farve. */
    private static ColorFilter restFilter() {
        switch (Skin.key) {
            case "superman": return new ColorMatrixColorFilter(gray());
            case "jarvis": return new ColorMatrixColorFilter(Stage.tint(Color.parseColor("#7FF6FF"), 1.15f));
            case "batman": return new ColorMatrixColorFilter(Stage.tint(AMBER, 0.85f));
            default: return null;
        }
    }

    /**
     * Klæder et app card på efter temaet. Kaldes, når billedet er lagt i cardet. Elementerne, der skal
     * ændre sig i fokus, får et tag, så focus() kan finde dem.
     */
    static void dressApp(Context c, LinearLayout holder, FrameLayout card, ImageView image, TextView label, String name, int index) {
        if (!on()) return;
        card.setBackground(background(c, APP, false, index));
        card.setClipToOutline(false);
        ColorFilter f = restFilter();
        if (f != null) {
            image.setColorFilter(f);
            image.setTag("t:img");
        }
        switch (Skin.key) {
            case "superman": {
                card.setClipToOutline(true);
                View dots = new View(c);
                dots.setBackground(dotsDrawable(c, Color.argb(80, 0, 0, 0), 3.5f, 1.1f));
                dots.setTag("t:dots");
                card.addView(dots, at(-1, -1, 0));
                label.setText(name);
                label.setTypeface(Fonts.playfairBlack(c));
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                label.setTextColor(INK);
                label.setAlpha(1f);
                label.setSingleLine(false);
                label.setMaxLines(2);
                label.setPadding(0, dp(c, 8), 0, dp(c, 3));
                View rule = new View(c);
                GradientDrawable two = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{NEWS_RED, NEWS_RED, NEWS_BLUE, NEWS_BLUE});
                rule.setBackground(two);
                rule.setTag("t:rule");
                rule.setAlpha(0f);
                holder.addView(rule, new LinearLayout.LayoutParams(-1, dp(c, 5)));
                TextView body = text(c, NEWS[Math.abs(name.hashCode()) % NEWS.length], Fonts.oldStandard(c), 10, Color.parseColor("#333333"));
                body.setPadding(0, dp(c, 5), 0, 0);
                holder.addView(body);
                holder.setPadding(dp(c, 6), dp(c, 6), dp(c, 6), dp(c, 8));
                holder.getLayoutParams().width += dp(c, 12);
                if (index > 0) holder.setBackground(new LineAfterLeft(c));
                break;
            }
            case "spiderman": {
                int pad = dp(c, 3.5f);
                card.setPadding(pad, pad, pad, pad);
                View dots = new View(c);
                dots.setBackground(dotsDrawable(c, Color.argb(34, 0, 0, 0), 6, 1.4f));
                FrameLayout.LayoutParams lp = at(-1, -1, 0);
                lp.setMargins(pad, pad, pad, pad);
                card.addView(dots, lp);
                card.setRotation(index % 2 == 0 ? -0.8f : 0.7f);
                label.setText(name.toUpperCase(DA));
                label.setTypeface(Fonts.comic(c));
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                label.setTextColor(COMIC_INK);
                label.setPadding(dp(c, 2), dp(c, 9), 0, 0);
                Burst burst = new Burst(c, SFX[index % SFX.length]);
                burst.setTag("t:burst");
                burst.setVisibility(View.INVISIBLE);
                card.addView(burst, at(dp(c, 82), dp(c, 58), Gravity.TOP | Gravity.END));
                break;
            }
            case "batman": {
                card.setPadding(dp(c, 1), dp(c, 1), dp(c, 1), dp(c, 1));
                image.setAlpha(0.6f);
                View shade = new View(c);
                shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, Color.argb(225, 10, 12, 15)}));
                card.addView(shade, at(-1, -1, 0));
                TextView n = text(c, name.toUpperCase(DA), Fonts.shoulders(c), 21, Color.parseColor("#E9E4D6"));
                n.setSingleLine(true);
                n.setLetterSpacing(0.03f);
                FrameLayout.LayoutParams nl = at(-2, -2, Gravity.BOTTOM | Gravity.START);
                nl.setMargins(dp(c, 10), 0, dp(c, 8), dp(c, 8));
                card.addView(n, nl);
                LinearLayout bars = new LinearLayout(c);
                bars.setOrientation(LinearLayout.VERTICAL);
                bars.setGravity(Gravity.END);
                for (int w : new int[]{54, 54, 40}) {
                    View b = new View(c);
                    b.setBackgroundColor(Color.BLACK);
                    LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(c, w), dp(c, 4));
                    bl.bottomMargin = dp(c, 4);
                    bars.addView(b, bl);
                }
                FrameLayout.LayoutParams bp = at(-2, -2, Gravity.TOP | Gravity.END);
                bp.setMargins(0, dp(c, 9), dp(c, 9), 0);
                card.addView(bars, bp);
                View scan = new View(c);
                scan.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.TRANSPARENT, AMBER, Color.TRANSPARENT}));
                scan.setTag("t:scan");
                scan.setVisibility(View.INVISIBLE);
                FrameLayout.LayoutParams sl = at(-1, Math.max(1, dp(c, 1.5f)), Gravity.TOP);
                sl.topMargin = dp(c, 44);
                card.addView(scan, sl);
                TextView an = text(c, "ANALYSE ▸", Fonts.jetMono(c), 8, AMBER);
                an.setTag("t:an");
                an.setVisibility(View.INVISIBLE);
                FrameLayout.LayoutParams al = at(-2, -2, Gravity.BOTTOM | Gravity.END);
                al.setMargins(0, 0, dp(c, 8), dp(c, 10));
                card.addView(an, al);
                TextView tab = text(c, String.format(DA, "FIL %02d", index + 1), Fonts.jetMono(c), 8, Color.parseColor("#9A9078"));
                tab.setTag("t:tab");
                tab.setGravity(Gravity.CENTER_VERTICAL);
                tab.setPadding(dp(c, 7), 0, 0, 0);
                tab.setBackground(box(Color.parseColor("#22262D"), 0, 0, 0));
                holder.addView(tab, 0, new LinearLayout.LayoutParams(dp(c, 58), dp(c, 15)));
                label.setVisibility(View.GONE);
                break;
            }
            case "jarvis": {
                card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                card.setClipToOutline(true);
                int pad = dp(c, 2);
                card.setPadding(pad, pad, pad, pad);
                image.setAlpha(0.55f);
                View shade = new View(c);
                shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.argb(60, 2, 10, 16), Color.TRANSPARENT, Color.argb(210, 2, 10, 16)}));
                card.addView(shade, at(-1, -1, 0));
                TextView num = text(c, String.format(DA, "%02d", index + 1), Fonts.techMono(c), 9, Color.argb(190, 94, 242, 255));
                num.setTag("t:num");
                FrameLayout.LayoutParams ml = at(-2, -2, Gravity.TOP | Gravity.START);
                ml.setMargins(dp(c, 14), dp(c, 6), 0, 0);
                card.addView(num, ml);
                TextView n = text(c, name.toUpperCase(DA), Fonts.rajdhani(c), 15, Color.parseColor("#E8FDFF"));
                n.setSingleLine(true);
                n.setLetterSpacing(0.05f);
                FrameLayout.LayoutParams nl = at(-2, -2, Gravity.BOTTOM | Gravity.START);
                nl.setMargins(dp(c, 9), 0, dp(c, 8), dp(c, 6));
                card.addView(n, nl);
                label.setText("◢ MÅL LÅST · " + name.toUpperCase(DA));
                label.setTypeface(Fonts.techMono(c));
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
                label.setLetterSpacing(0.1f);
                label.setTextColor(GOLD);
                label.setAlpha(0f);
                break;
            }
            case "biograf": {
                card.setClipToOutline(false);
                if (image.getScaleType() == ImageView.ScaleType.CENTER_CROP) {
                    FrameLayout.LayoutParams il = (FrameLayout.LayoutParams) image.getLayoutParams();
                    int iw = dp(c, 98);
                    il.width = iw;
                    il.height = iw * 9 / 16;
                    il.gravity = Gravity.CENTER;
                    il.topMargin = -dp(c, 6);
                }
                View frame = new View(c);
                frame.setBackground(box(Color.TRANSPARENT, Color.argb(170, 201, 162, 79), Math.max(1, dp(c, 1)), 0));
                FrameLayout.LayoutParams fl = at(-1, -1, 0);
                fl.setMargins(dp(c, 5), dp(c, 5), dp(c, 5), dp(c, 5));
                card.addView(frame, fl);
                TextView cap = text(c, POSTER[index % POSTER.length], Fonts.bebas(c), 10, Color.argb(200, 255, 255, 255));
                cap.setLetterSpacing(0.18f);
                FrameLayout.LayoutParams cl = at(-2, -2, Gravity.TOP | Gravity.START);
                cl.setMargins(dp(c, 10), dp(c, 11), 0, 0);
                card.addView(cap, cl);
                TextView n = text(c, name, Fonts.limelight(c), 15, Color.WHITE);
                n.setMaxLines(2);
                FrameLayout.LayoutParams nl = at(-1, -2, Gravity.BOTTOM);
                nl.setMargins(dp(c, 10), 0, dp(c, 8), dp(c, 11));
                card.addView(n, nl);
                Bulbs bulbs = new Bulbs(c);
                bulbs.setTag("t:bulbs");
                bulbs.setVisibility(View.INVISIBLE);
                card.addView(bulbs, at(-1, -1, 0));
                label.setText("▶ " + name.toUpperCase(DA) + " · SAL " + (index + 1));
                label.setTypeface(Fonts.bebas(c));
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                label.setLetterSpacing(0.16f);
                label.setTextColor(Color.parseColor("#E8C36A"));
                label.setAlpha(0f);
                break;
            }
            case "jul": {
                int pad = dp(c, 2);
                card.setPadding(pad, pad, pad, pad);
                int[] fills = {XMAS_GREEN, Color.parseColor("#EFE3C8"), XMAS_RED};
                int fill = fills[index % fills.length];
                boolean light = index % fills.length == 1;
                FrameLayout front = new FrameLayout(c);
                front.setBackground(box(fill, XMAS_GOLD, dp(c, 2), 0));
                front.setTag("t:front");
                front.setPivotX(0);
                front.setCameraDistance(8000 * c.getResources().getDisplayMetrics().density);
                TextView num = text(c, String.valueOf(DOORS[index % DOORS.length]), Fonts.fraunces(c), 34, light ? XMAS_RED : Color.parseColor("#F3D989"));
                FrameLayout.LayoutParams nl = at(-2, -2, Gravity.TOP | Gravity.START);
                nl.setMargins(dp(c, 11), dp(c, 6), 0, 0);
                front.addView(num, nl);
                TextView n = text(c, name, Fonts.caveat(c), 18, light ? Color.parseColor("#5C0B15") : XMAS_CREAM);
                n.setSingleLine(true);
                FrameLayout.LayoutParams tl = at(-2, -2, Gravity.BOTTOM | Gravity.START);
                tl.setMargins(dp(c, 11), 0, dp(c, 6), dp(c, 8));
                front.addView(n, tl);
                card.addView(front, at(-1, -1, 0));
                label.setVisibility(View.GONE);
                break;
            }
            case "hygge": {
                int pad = dp(c, 17);
                card.setPadding(pad, pad, pad, pad);
                if (image.getScaleType() == ImageView.ScaleType.CENTER_CROP) {
                    FrameLayout.LayoutParams il = (FrameLayout.LayoutParams) image.getLayoutParams();
                    il.width = -1;
                    il.height = -1;
                    il.gravity = Gravity.CENTER;
                }
                card.setElevation(dp(c, 5));
                // Rammerne står direkte på hylden; billedet i rammen viser, hvilken app det er
                label.setVisibility(View.GONE);
                break;
            }
        }
    }

    /** Tynd lodret streg til venstre for en avisspalte. */
    static final class LineAfterLeft extends Drawable {
        private final Paint p = new Paint();

        LineAfterLeft(Context c) {
            p.setColor(INK);
            p.setStrokeWidth(Math.max(1, c.getResources().getDisplayMetrics().density * 0.6f));
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            c.drawLine(b.left + 1, b.top + 6, b.left + 1, b.bottom - 6, p);
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    static Drawable dotsDrawable(Context c, int color, float spacingDp, float radiusDp) {
        float density = c.getResources().getDisplayMetrics().density;
        Shader sh = Stage.dots(density, color, spacingDp, radiusDp);
        return new Drawable() {
            private final Paint p = new Paint();

            {
                p.setShader(sh);
            }

            @Override
            public void draw(Canvas canvas) {
                canvas.drawRect(getBounds(), p);
            }

            @Override
            public void setAlpha(int a) {
            }

            @Override
            public void setColorFilter(ColorFilter f) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }

    /** Små cards (tilstande, knapper, kommer snart): fladen og skriften efter temaet. */
    static void dressSmall(Context c, FrameLayout card) {
        if (!on()) return;
        card.setBackground(background(c, SMALL, false, 0));
        card.setClipToOutline(false);
        if (Skin.is("hygge")) card.setElevation(dp(c, 3));
        if (Skin.is("jarvis")) {
            card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }
        // Indholdet lægges i cardet bagefter; skriften rettes, når det er der
        card.post(() -> restyle(c, card));
    }

    /** Store tekster får temaets display skrift, små dets brødskrift. */
    static void restyle(Context c, View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            float sp = t.getTextSize() / c.getResources().getDisplayMetrics().scaledDensity;
            boolean big = sp >= 15;
            Typeface face;
            int color;
            switch (Skin.key) {
                case "superman": face = big ? Fonts.playfairBlack(c) : Fonts.oldStandard(c); color = big ? INK : Color.parseColor("#333333"); break;
                case "spiderman": face = big ? Fonts.bangers(c) : Fonts.comic(c); color = COMIC_INK; if (big) t.setLetterSpacing(0.04f); break;
                case "batman": face = big ? Fonts.shoulders(c) : Fonts.jetMono(c); color = big ? Color.parseColor("#E9E4D6") : Color.parseColor("#B9B2A0");
                    if (big) t.setText(t.getText().toString().toUpperCase(DA)); break;
                case "jarvis": face = big ? Fonts.rajdhani(c) : Fonts.techMono(c); color = big ? Color.parseColor("#E8FDFF") : Color.parseColor("#8FDFEA"); break;
                case "biograf": face = Fonts.bebas(c); color = WINE; t.setLetterSpacing(0.06f); if (big) t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp + 3); break;
                case "jul": face = big ? Fonts.caveat(c) : Fonts.frauncesItalic(c); color = big ? Color.parseColor("#3B1D14") : XMAS_RED; if (big) t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp + 4); break;
                case "hygge": face = big ? Fonts.cormorant(c) : Fonts.cormorantItalic(c); color = big ? WALL_INK : WALL_BROWN; if (big) t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp + 2); break;
                default: return;
            }
            // En farve, der betyder noget (aktiv, tændt), beholdes, hvis den ikke er temaets tekstfarve
            boolean meaningful = t.getCurrentTextColor() != Ui.TEXT && t.getCurrentTextColor() != Ui.TEXT_DIM;
            t.setTypeface(face);
            if (!meaningful) t.setTextColor(color);
            else t.setTextColor(accentOn());
            t.setIncludeFontPadding(false);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) restyle(c, g.getChildAt(i));
        }
    }

    /** Farven for "aktiv" og "tændt" på temaets flader. */
    static int accentOn() {
        switch (Skin.key) {
            case "superman": return NEWS_RED;
            case "spiderman": return COMIC_RED;
            case "batman": return AMBER;
            case "jarvis": return GOLD;
            case "biograf": return Color.parseColor("#8A1A22");
            case "jul": return XMAS_RED;
            case "hygge": return Color.parseColor("#9A5B3A");
            default: return Ui.CYAN;
        }
    }

    /** Temaets fokus på et card. */
    static void focus(Context c, int kind, LinearLayout holder, FrameLayout card, TextView label, boolean has, int index, String motion) {
        boolean anim = !Motion.NONE.equals(motion);
        if (kind == MEDIA) {
            mediaFocus(c, card, has, index);
            // Resten som temaets andre cards: Hygges rammer som billeder, de andre som små cards
            kind = Skin.is("hygge") ? APP : SMALL;
        } else {
            Drawable bg = background(c, kind, has, index);
            if (bg != null && !(Skin.is("hygge") && kind != SMALL)) card.setBackground(bg);
        }
        float sc = scale(kind);
        if (sc != 1f) Motion.focusTo(card, has ? sc : 1f, motion);
        ImageView img = card.findViewWithTag("t:img");
        if (img != null) {
            img.setColorFilter(has ? null : restFilter());
            if (Skin.is("jarvis") || Skin.is("batman")) img.setAlpha(has ? 1f : Skin.is("jarvis") ? 0.55f : 0.6f);
        }
        switch (Skin.key) {
            case "superman": {
                View dots = card.findViewWithTag("t:dots");
                if (dots != null) dots.setVisibility(has ? View.INVISIBLE : View.VISIBLE);
                View rule = holder.findViewWithTag("t:rule");
                if (rule != null) rule.setAlpha(has ? 1f : 0f);
                if (kind == APP) {
                    holder.setBackground(has ? box(Color.parseColor("#FAF6EC"), 0, 0, 0) : (index > 0 ? new LineAfterLeft(c) : null));
                    holder.setElevation(has ? dp(c, 10) : 0);
                    move(holder, has ? -dp(c, 6) : 0, anim);
                } else {
                    card.setElevation(has ? dp(c, 8) : 0);
                    move(card, has ? -dp(c, 3) : 0, anim);
                }
                break;
            }
            case "spiderman": {
                View burst = card.findViewWithTag("t:burst");
                if (burst != null) {
                    burst.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
                    if (has && anim) {
                        burst.setScaleX(0.4f);
                        burst.setScaleY(0.4f);
                        burst.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    }
                }
                float base = kind == APP ? (index % 2 == 0 ? -0.8f : 0.7f) : 0f;
                if (anim) card.animate().rotation(has ? -3f : base).setDuration(170).start();
                else card.setRotation(has ? -3f : base);
                card.setElevation(has ? dp(c, 10) : 0);
                break;
            }
            case "batman": {
                TextView tab = holder.findViewWithTag("t:tab");
                if (tab != null) {
                    tab.setBackground(box(has ? AMBER : Color.parseColor("#22262D"), 0, 0, 0));
                    tab.setTextColor(has ? Color.BLACK : Color.parseColor("#9A9078"));
                }
                View scan = card.findViewWithTag("t:scan");
                if (scan != null) scan.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
                View an = card.findViewWithTag("t:an");
                if (an != null) an.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
                break;
            }
            case "biograf": {
                View bulbs = card.findViewWithTag("t:bulbs");
                if (bulbs != null) bulbs.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
                card.setElevation(has ? dp(c, 12) : 0);
                break;
            }
            case "jul": {
                if (kind != APP) card.setElevation(has ? dp(c, 10) : 0);
                View front = card.findViewWithTag("t:front");
                if (front != null) {
                    if (anim) front.animate().rotationY(has ? -64f : 0f).setDuration(has ? 320 : 200).start();
                    else front.setRotationY(has ? -64f : 0f);
                }
                break;
            }
            case "hygge": {
                move(card, has ? -dp(c, 10) : 0, anim);
                card.setElevation(has ? dp(c, 16) : (kind == SMALL ? dp(c, 3) : dp(c, 5)));
                if (kind != SMALL) holder.setBackground(has ? glow(c) : null);
                else card.setBackground(background(c, SMALL, has, index));
                break;
            }
        }
        if (label != null && label.getVisibility() == View.VISIBLE && kind == APP) {
            switch (Skin.key) {
                case "jarvis": case "biograf": case "hygge":
                    if (anim) label.animate().alpha(has ? 1f : 0f).setDuration(160).start();
                    else label.setAlpha(has ? 1f : 0f);
                    break;
            }
        }
    }

    private static void move(View v, float y, boolean anim) {
        if (anim) v.animate().translationY(y).setDuration(220).start();
        else v.setTranslationY(y);
    }

    /** Varmt lys fra stearinlyset bag rammen i fokus. */
    private static Drawable glow(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{Color.argb(120, 255, 190, 100), Color.TRANSPARENT});
        g.setGradientRadius(dp(c, 120));
        return g;
    }

    // Fortsæt at se

    /** Rækken med det, man er i gang med, står som en lodret liste (Jarvis, Batman). */
    static boolean mediaList() {
        return Skin.is("jarvis") || Skin.is("batman");
    }

    /** {bredde, højde} i dp for et "fortsæt" card; bredde 0 = hele rækken. */
    static int[] mediaSize() {
        switch (Skin.key) {
            case "superman": return new int[]{210, 54};
            case "spiderman": return new int[]{290, 96};
            case "biograf": return new int[]{250, 76};
            case "jul": return new int[]{200, 80};
            case "hygge": return new int[]{150, 112};
            case "jarvis": return new int[]{0, 34};
            case "batman": return new int[]{0, 38};
            default: return new int[]{256, 144};
        }
    }

    /**
     * Indholdet i et "fortsæt" card. image er null, når temaet ikke viser plakaten. Giver ImageView'et
     * tilbage, hvis plakaten skal hentes.
     */
    static ImageView mediaContent(Context c, FrameLayout card, LinearLayout holder, TextView label, String title, String sub, int progress, int index) {
        card.setBackground(background(c, Skin.is("hygge") ? APP : SMALL, false, index));
        card.setClipToOutline(false);
        String pct = progress > 0 ? Math.min(progress, 100) + " %" : "ny";
        switch (Skin.key) {
            case "superman": {
                LinearLayout in = column(c, 10, 6);
                in.addView(text(c, title, Fonts.playfairBlack(c), 13, INK));
                in.addView(text(c, (sub == null || sub.isEmpty() ? "" : sub + " · ") + pct, Fonts.oldStandard(c), 10, Color.parseColor("#333333")));
                single(in);
                card.addView(in, at(-1, -1, 0));
                label.setVisibility(View.GONE);
                return null;
            }
            case "spiderman": {
                int pad = dp(c, 3.5f);
                card.setPadding(pad, pad, pad, pad);
                ImageView img = new ImageView(c);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(img, at(dp(c, 112), -1, Gravity.START));
                View sep = new View(c);
                sep.setBackgroundColor(COMIC_INK);
                FrameLayout.LayoutParams spl = at(pad, -1, Gravity.START);
                spl.leftMargin = dp(c, 112);
                card.addView(sep, spl);
                LinearLayout in = column(c, 10, 6);
                in.addView(text(c, title, Fonts.bangers(c), 22, COMIC_INK));
                TextView s = text(c, (sub == null ? "" : sub).toUpperCase(DA), Fonts.comic(c), 10, COMIC_INK);
                s.setPadding(0, dp(c, 3), 0, dp(c, 7));
                in.addView(s);
                FrameLayout bar = new FrameLayout(c);
                bar.setBackground(box(Color.WHITE, COMIC_INK, dp(c, 2), 0));
                View fill = new View(c);
                fill.setBackgroundColor(COMIC_RED);
                bar.addView(fill, new FrameLayout.LayoutParams(dp(c, 150) * Math.max(4, Math.min(progress, 100)) / 100, -1));
                in.addView(bar, new LinearLayout.LayoutParams(dp(c, 150), dp(c, 11)));
                single(in);
                FrameLayout.LayoutParams il = at(-1, -1, 0);
                il.leftMargin = dp(c, 118);
                card.addView(in, il);
                label.setVisibility(View.GONE);
                return img;
            }
            case "biograf": {
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                FrameLayout stub = new FrameLayout(c);
                stub.setBackground(new DashedRight(c));
                TextView seat = text(c, "RÆKKE " + (3 + (Math.abs(title.hashCode()) % 9)), Fonts.bebas(c), 12, WINE);
                seat.setLetterSpacing(0.14f);
                seat.setRotation(-90);
                stub.addView(seat, at(-2, -2, Gravity.CENTER));
                row.addView(stub, new LinearLayout.LayoutParams(dp(c, 50), -1));
                LinearLayout in = column(c, 12, 8);
                TextView t = text(c, title.toUpperCase(DA), Fonts.bebas(c), 23, WINE);
                t.setLetterSpacing(0.03f);
                in.addView(t);
                TextView s = text(c, ((sub == null || sub.isEmpty()) ? "" : sub.toUpperCase(DA) + " · ") + pct, Fonts.bebas(c), 12, Color.parseColor("#8A5A2A"));
                s.setLetterSpacing(0.12f);
                in.addView(s);
                single(in);
                row.addView(in, new LinearLayout.LayoutParams(0, -1, 1f));
                card.addView(row, at(-1, -1, 0));
                label.setVisibility(View.GONE);
                return null;
            }
            case "jul": {
                card.setBackground(new Tag(c, false));
                card.setRotation(index % 2 == 0 ? -2.5f : 2f);
                LinearLayout in = column(c, 34, 10);
                in.addView(text(c, title, Fonts.caveat(c), 22, Color.parseColor("#3B1D14")));
                TextView s = text(c, ((sub == null || sub.isEmpty()) ? "" : sub + " · ") + pct, Fonts.frauncesItalic(c), 10.5f, XMAS_RED);
                s.setPadding(0, dp(c, 4), 0, 0);
                in.addView(s);
                single(in);
                card.addView(in, at(-1, -1, 0));
                label.setVisibility(View.GONE);
                return null;
            }
            case "hygge": {
                int pad = dp(c, 14);
                card.setPadding(pad, pad, pad, pad);
                card.setElevation(dp(c, 5));
                ImageView img = new ImageView(c);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(img, at(-1, -1, 0));
                // En lille seddel i hjørnet af rammen med hvor langt man er
                TextView note = text(c, ((sub == null || sub.isEmpty()) ? title : sub).toLowerCase(DA) + "\n" + pct, Fonts.reenie(c), 15, WALL_INK);
                note.setBackground(box(Color.parseColor("#FBF6EA"), 0, 0, 0));
                note.setPadding(dp(c, 6), dp(c, 3), dp(c, 6), dp(c, 3));
                note.setRotation(-4);
                note.setElevation(dp(c, 2));
                note.setMaxWidth(dp(c, 110));
                FrameLayout.LayoutParams nl = at(-2, -2, Gravity.BOTTOM | Gravity.END);
                nl.setMargins(0, 0, -dp(c, 4), -dp(c, 6));
                card.addView(note, nl);
                card.setClipChildren(false);
                label.setVisibility(View.GONE);
                return img;
            }
            case "jarvis":
            case "batman": {
                boolean bat = Skin.is("batman");
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(c, bat ? 12 : 14), 0, dp(c, 12), 0);
                TextView t = text(c, bat ? title.toUpperCase(DA) : title.toUpperCase(DA), bat ? Fonts.shoulders(c) : Fonts.rajdhani(c), bat ? 17 : 15,
                        bat ? Color.parseColor("#E9E4D6") : Color.parseColor("#E8FDFF"));
                t.setSingleLine(true);
                t.setMaxWidth(dp(c, 230));
                row.addView(t);
                TextView s = text(c, sub == null ? "" : sub.toUpperCase(DA), bat ? Fonts.jetMono(c) : Fonts.techMono(c), 9, bat ? Color.parseColor("#7C7462") : Color.parseColor("#8FDFEA"));
                s.setSingleLine(true);
                s.setPadding(dp(c, 10), 0, dp(c, 12), 0);
                row.addView(s);
                FrameLayout bar = new FrameLayout(c);
                bar.setBackgroundColor(bat ? Color.parseColor("#2A2D33") : Color.argb(46, 94, 242, 255));
                View fill = new View(c);
                fill.setBackgroundColor(bat ? AMBER : CYAN);
                bar.addView(fill, new FrameLayout.LayoutParams(-1, -1));
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, dp(c, bat ? 3 : 2), 1f);
                row.addView(bar, bl);
                bar.post(() -> fill.getLayoutParams().width = bar.getWidth() * Math.max(2, Math.min(progress, 100)) / 100);
                bar.post(fill::requestLayout);
                TextView p = text(c, pct, bat ? Fonts.jetMono(c) : Fonts.techMono(c), 10, bat ? AMBER : Color.parseColor("#E8FDFF"));
                p.setPadding(dp(c, 12), 0, 0, 0);
                row.addView(p);
                card.addView(row, at(-1, -1, 0));
                if (!bat) {
                    View edge = new View(c);
                    edge.setBackgroundColor(CYAN);
                    card.addView(edge, at(dp(c, 3), -1, Gravity.START));
                    card.setBackground(box(Color.argb(12, 94, 242, 255), Color.argb(90, 94, 242, 255), Math.max(1, dp(c, 0.8f)), 0));
                }
                label.setVisibility(View.GONE);
                return null;
            }
            default:
                return null;
        }
    }

    /** Fladen bag et "fortsæt" card i fokus. */
    static void mediaFocus(Context c, FrameLayout card, boolean has, int index) {
        switch (Skin.key) {
            case "jul": card.setBackground(new Tag(c, has)); break;
            case "jarvis": card.setBackground(box(has ? Color.argb(40, 242, 179, 61) : Color.argb(12, 94, 242, 255),
                    has ? GOLD : Color.argb(90, 94, 242, 255), Math.max(1, dp(c, has ? 1.5f : 0.8f)), 0)); break;
            case "hygge": break;
            default: card.setBackground(background(c, SMALL, has, index));
        }
    }

    private static LinearLayout column(Context c, int left, int vertical) {
        LinearLayout in = new LinearLayout(c);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setGravity(Gravity.CENTER_VERTICAL);
        in.setPadding(dp(c, left), dp(c, vertical), dp(c, 10), dp(c, vertical));
        return in;
    }

    private static void single(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            if (g.getChildAt(i) instanceof TextView) {
                ((TextView) g.getChildAt(i)).setSingleLine(true);
                ((TextView) g.getChildAt(i)).setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
        }
    }

    /** Billettens perforering mellem stumpen og resten. */
    static final class DashedRight extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        DashedRight(Context c) {
            density = c.getResources().getDisplayMetrics().density;
            p.setColor(Color.parseColor("#B99B62"));
            p.setStrokeWidth(1.5f * density);
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{4 * density, 3 * density}, 0));
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            c.drawLine(b.right - density, b.top + 6 * density, b.right - density, b.bottom - 6 * density, p);
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
