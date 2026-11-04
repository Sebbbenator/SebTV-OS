package dk.selveste.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Indstillingerne i temaernes egen verden: Jarvis' skråskårne panel med prikkede rækker, Batmans
 * sagsmappe, Spider-Mans fortællerbokse, Supermans rubrikannoncer, biografens trykte program,
 * julens ønskeseddel og Hygges notesbog. Listen bygges som før; her får den bare papir, skrift og fokus.
 */
final class Sheet {

    private Sheet() {
    }

    private static final Locale DA = new Locale("da", "DK");

    /** Temaerne med en scene på forsiden har også deres egne indstillinger. */
    static boolean on() {
        return Stage.place() != Stage.NONE;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Skrift, blæk og den svage tekst på temaets papir. */
    static int ink() {
        switch (Skin.key) {
            case "superman": return Tiles.INK;
            case "spiderman": return Tiles.COMIC_INK;
            case "jul": return Color.parseColor("#2A1A12");
            case "hygge": return Color.parseColor("#3A2C22");
            case "biograf": return Tiles.CREAM;
            case "batman": return Color.parseColor("#E9E4D6");
            default: return Color.parseColor("#D8F6FF");
        }
    }

    static int dim() {
        switch (Skin.key) {
            case "superman": return Color.parseColor("#4A4540");
            case "spiderman": return Color.parseColor("#3A3A3A");
            case "jul": return Color.parseColor("#6E5A48");
            case "hygge": return Color.parseColor("#7A6250");
            case "biograf": return Color.parseColor("#B89A6A");
            case "batman": return Color.parseColor("#8C8676");
            default: return Color.parseColor("#7FB8C6");
        }
    }

    /** Værdien til højre i en række. */
    static int value() {
        switch (Skin.key) {
            case "superman": return Tiles.NEWS_RED;
            case "spiderman": return Tiles.COMIC_RED;
            case "jul": return Color.parseColor("#9E1B2A");
            case "hygge": return Color.parseColor("#9A5B3A");
            case "biograf": return Tiles.CINEMA_GOLD;
            case "batman": return Tiles.AMBER;
            default: return Tiles.CYAN;
        }
    }

    static Typeface titleFace(Context c) {
        switch (Skin.key) {
            case "superman": return Fonts.playfairBlack(c);
            case "spiderman": return Fonts.bangers(c);
            case "jul": return Fonts.caveat(c);
            case "hygge": return Fonts.cormorantItalic(c);
            case "biograf": return Fonts.limelight(c);
            case "batman": return Fonts.shoulders(c);
            default: return Fonts.rajdhani(c);
        }
    }

    static Typeface rowFace(Context c) {
        switch (Skin.key) {
            case "superman": return Fonts.playfair(c);
            case "spiderman": return Fonts.comic(c);
            case "jul": return Fonts.caveat(c);
            case "hygge": return Fonts.cormorant(c);
            case "biograf": return Fonts.bebas(c);
            case "batman": return Fonts.jetMono(c);
            default: return Fonts.rajdhani(c);
        }
    }

    static Typeface valueFace(Context c) {
        switch (Skin.key) {
            case "superman": return Fonts.oldStandard(c);
            case "spiderman": return Fonts.comic(c);
            case "jul": return Fonts.caveat(c);
            case "hygge": return Fonts.cormorantItalic(c);
            case "biograf": return Fonts.bebas(c);
            case "batman": return Fonts.jetMono(c);
            default: return Fonts.techMono(c);
        }
    }

    /**
     * Skærmen: temaets baggrund, papiret med titel og liste, og pynten rundt om. Titlen returneres
     * via {@code title[0]}.
     */
    static View layout(Activity a, ScrollView scroll, LinearLayout list, TextView[] title) {
        String key = Skin.key;
        FrameLayout root = new FrameLayout(a);
        root.addView(new Back(a, key), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout paper = new LinearLayout(a);
        paper.setOrientation(LinearLayout.VERTICAL);
        paper.setBackground(new Paper(a, key));
        paper.setClipChildren(false);
        paper.setClipToPadding(false);
        int[] pad = paperPadding(key);
        paper.setPadding(dp(a, pad[0]), dp(a, pad[1]), dp(a, pad[2]), dp(a, pad[3]));

        TextView t = new TextView(a);
        t.setIncludeFontPadding(false);
        paper.addView(t, new LinearLayout.LayoutParams(-1, -2));
        title[0] = t;

        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setFadingEdgeLength(dp(a, 24));
        scroll.setVerticalFadingEdgeEnabled(true);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(dp(a, 6), dp(a, 10), dp(a, 6), dp(a, 40));
        scroll.addView(list);
        paper.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        int[] place = paperPlace(key);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(a, place[0]), -1, place[1]);
        lp.topMargin = dp(a, place[2]);
        lp.bottomMargin = dp(a, place[3]);
        lp.leftMargin = dp(a, place[4]);
        lp.rightMargin = dp(a, place[4]);
        root.addView(paper, lp);

        // Hver række, der kommer i listen, får temaets skrift og fokus
        list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                dress(a, child);
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
            }
        });
        return root;
    }

    /** Papirets indre luft i dp: venstre, top, højre, bund. */
    private static int[] paperPadding(String key) {
        switch (key) {
            case "superman": return new int[]{30, 84, 30, 0};
            case "spiderman": return new int[]{28, 22, 28, 0};
            case "batman": return new int[]{30, 52, 30, 0};
            case "biograf": return new int[]{40, 30, 40, 0};
            case "jul": return new int[]{44, 26, 40, 0};
            case "hygge": return new int[]{78, 34, 40, 0};
            default: return new int[]{36, 30, 36, 0};
        }
    }

    /** Papirets bredde, tyngde, top, bund og sidemargen i dp. */
    private static int[] paperPlace(String key) {
        switch (key) {
            case "superman": return new int[]{560, Gravity.CENTER_HORIZONTAL, 26, 0, 0};
            case "spiderman": return new int[]{560, Gravity.END, 30, 30, 48};
            case "batman": return new int[]{540, Gravity.START, 34, 0, 56};
            case "biograf": return new int[]{500, Gravity.CENTER_HORIZONTAL, 34, 0, 0};
            case "jul": return new int[]{500, Gravity.CENTER_HORIZONTAL, 40, 0, 0};
            case "hygge": return new int[]{560, Gravity.CENTER_HORIZONTAL, 30, 0, 0};
            default: return new int[]{560, Gravity.END, 34, 34, 48};
        }
    }

    /** Sidens titel i temaets sprog. */
    static void title(Context c, TextView t, String text) {
        t.setTypeface(titleFace(c));
        t.setShadowLayer(0, 0, 0, 0);
        t.setLetterSpacing(0);
        t.setGravity(Gravity.START);
        t.setBackground(null);
        t.setPadding(0, 0, 0, dp(c, 8));
        String up = text.toUpperCase(DA);
        switch (Skin.key) {
            case "superman":
                t.setText(up);
                t.setTextColor(Tiles.INK);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
                t.setGravity(Gravity.CENTER_HORIZONTAL);
                break;
            case "spiderman":
                t.setText(up + "!");
                t.setTextColor(Color.WHITE);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
                t.setLetterSpacing(0.03f);
                t.setShadowLayer(0.01f, dp(c, 3), dp(c, 3), Tiles.COMIC_INK);
                break;
            case "batman":
                t.setText(up);
                t.setTextColor(Color.parseColor("#E9E4D6"));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                t.setLetterSpacing(0.2f);
                break;
            case "biograf":
                t.setText(text);
                t.setTextColor(Color.parseColor("#E8C36A"));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                t.setGravity(Gravity.CENTER_HORIZONTAL);
                break;
            case "jul":
                t.setText(text);
                t.setTextColor(Color.parseColor("#9E1B2A"));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
                break;
            case "hygge":
                t.setText(text);
                t.setTextColor(Color.parseColor("#3A2C22"));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
                break;
            default:
                t.setText("// " + up);
                t.setTextColor(Tiles.CYAN);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                t.setLetterSpacing(0.22f);
                break;
        }
    }

    /** Giver en ny ting i listen temaets skrift, farver og fokus. */
    static void dress(Context c, View v) {
        if (v.isFocusable() && v instanceof ViewGroup) {
            row(c, (ViewGroup) v);
        } else if (v.isFocusable()) {
            v.setBackground(rowBackground(c));
            if (v instanceof TextView) {
                TextView t = (TextView) v;
                t.setTypeface(rowFace(c));
                t.setTextColor(ink());
            }
        } else if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (t.getLetterSpacing() > 0.1f || t.getBackground() != null) header(c, t);
            else {
                t.setTextColor(dim());
                t.setTypeface(valueFace(c));
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) dress(c, g.getChildAt(i));
        }
    }

    /** Overskrifter i listen: samme sprog som forsidens, men på temaets papir. */
    private static void header(Context c, TextView t) {
        switch (Skin.key) {
            case "jul":
                t.setTextColor(Color.parseColor("#9E1B2A"));
                t.setVisibility(View.VISIBLE);
                break;
            case "hygge":
                t.setTextColor(Color.parseColor("#9A5B3A"));
                break;
            case "biograf":
                t.setGravity(Gravity.START);
                t.setBackground(null);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                break;
            case "superman":
                t.setTextColor(Tiles.INK);
                break;
            default:
                break;
        }
    }

    private static void row(Context c, ViewGroup row) {
        row.setBackground(rowBackground(c));
        int h = dp(c, 16), vpad = dp(c, Skin.is("jul") || Skin.is("hygge") ? 8 : 12);
        if (Skin.is("jul")) row.setPadding(dp(c, 22), vpad, dp(c, 22), vpad);
        else row.setPadding(h, vpad, h, vpad);
        ViewGroup.LayoutParams lp = row.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lp).bottomMargin = dp(c, Skin.is("spiderman") ? 12 : Skin.is("superman") ? 10 : 4);
        }
        TextView title = null, value = null;
        for (int i = 0; i < row.getChildCount(); i++) {
            View ch = row.getChildAt(i);
            if (!(ch instanceof TextView)) continue;
            if ("value".equals(ch.getTag())) value = (TextView) ch;
            else if (title == null) title = (TextView) ch;
        }
        float ts = Skin.is("jul") ? 24 : Skin.is("hygge") ? 21 : Skin.is("biograf") ? 21 : Skin.is("batman") ? 15 : 18;
        if (title != null) {
            title.setTypeface(rowFace(c));
            title.setTextColor(ink());
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ts);
            title.setLetterSpacing(Skin.is("batman") || Skin.is("biograf") ? 0.08f : 0);
            if (Skin.is("batman") || Skin.is("biograf") || Skin.is("spiderman")) title.setAllCaps(true);
        }
        if (value != null) {
            value.setTypeface(valueFace(c));
            value.setTextColor(value());
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, Skin.is("jul") ? 22 : Skin.is("biograf") ? 19 : 15);
            value.setLetterSpacing(Skin.is("batman") || Skin.is("jarvis") ? 0.06f : 0);
            // Skråskrift (Caveat, Cormorant kursiv) hælder ud over kanten: lidt luft til højre
            value.setPadding(dp(c, 6), 0, dp(c, Skin.is("jul") ? 16 : 6), 0);
        }
        TextView t = title, v = value;
        View.OnFocusChangeListener old = row.getOnFocusChangeListener();
        row.setOnFocusChangeListener((view, has) -> {
            if (old != null) old.onFocusChange(view, has);
            focusText(t, v, has);
            if (Skin.is("spiderman")) view.setRotation(has ? -1f : 0);
        });
    }

    /** Teksten skifter farve, hvor fokus fylder rækken (avis, sagsmappe, tegneserie, billet). */
    private static void focusText(TextView t, TextView v, boolean has) {
        int ti = ink(), vi = value();
        if (has) {
            switch (Skin.key) {
                case "superman": ti = Color.parseColor("#F4EFE3"); vi = Color.parseColor("#F4EFE3"); break;
                case "batman": ti = Color.BLACK; vi = Color.BLACK; break;
                case "spiderman": ti = Color.WHITE; vi = Tiles.COMIC_YELLOW; break;
                case "biograf": ti = Tiles.WINE; vi = Color.parseColor("#8A1A22"); break;
                case "jarvis": ti = Color.WHITE; break;
                default: break;
            }
        }
        if (t != null) t.setTextColor(ti);
        if (v != null) v.setTextColor(vi);
    }

    static StateListDrawable rowBackground(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, new RowMark(c, Skin.key, true));
        s.addState(new int[]{}, new RowMark(c, Skin.key, false));
        return s;
    }

    /** En række i hvile eller i fokus, tegnet som temaets ting. */
    static final class RowMark extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private final String key;
        private final boolean focus;

        RowMark(Context c, String key, boolean focus) {
            d = c.getResources().getDisplayMetrics().density;
            this.key = key;
            this.focus = focus;
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            RectF r = new RectF(b);
            p.setPathEffect(null);
            p.setShader(null);
            switch (key) {
                case "jarvis": {
                    if (focus) {
                        p.setStyle(Paint.Style.FILL);
                        p.setShader(new LinearGradient(r.left, 0, r.right, 0, Color.argb(70, 94, 242, 255), Color.argb(8, 94, 242, 255), Shader.TileMode.CLAMP));
                        c.drawRect(r, p);
                        p.setShader(null);
                        p.setColor(Tiles.CYAN);
                        c.drawRect(r.left, r.top, r.left + 3 * d, r.bottom, p);
                        p.setColor(Tiles.GOLD);
                        c.drawRect(r.right - 8 * d, r.top, r.right, r.top + 2 * d, p);
                        c.drawRect(r.right - 2 * d, r.top, r.right, r.top + 8 * d, p);
                        c.drawRect(r.right - 8 * d, r.bottom - 2 * d, r.right, r.bottom, p);
                        c.drawRect(r.right - 2 * d, r.bottom - 8 * d, r.right, r.bottom, p);
                    } else {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(Math.max(1, d));
                        p.setColor(Color.argb(70, 94, 242, 255));
                        p.setPathEffect(new DashPathEffect(new float[]{2 * d, 4 * d}, 0));
                        c.drawLine(r.left + 16 * d, r.bottom - 1, r.right - 16 * d, r.bottom - 1, p);
                    }
                    break;
                }
                case "batman": {
                    p.setStyle(Paint.Style.FILL);
                    if (focus) {
                        p.setColor(Tiles.AMBER);
                        c.drawRect(r, p);
                    } else {
                        p.setColor(Color.argb(10, 255, 255, 255));
                        c.drawRect(r, p);
                        p.setColor(Tiles.SLATE_LINE);
                        c.drawRect(r.left, r.bottom - Math.max(1, d), r.right, r.bottom, p);
                    }
                    break;
                }
                case "spiderman": {
                    p.setStyle(Paint.Style.FILL);
                    float sh = focus ? 5 * d : 4 * d;
                    p.setColor(Tiles.COMIC_INK);
                    c.drawRect(r.left + sh, r.top + sh, r.right, r.bottom, p);
                    p.setColor(focus ? Tiles.COMIC_RED : Tiles.COMIC_YELLOW);
                    RectF f = new RectF(r.left, r.top, r.right - sh, r.bottom - sh);
                    c.drawRect(f, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.5f * d);
                    p.setColor(Tiles.COMIC_INK);
                    c.drawRect(f, p);
                    break;
                }
                case "superman": {
                    if (focus) {
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(Tiles.INK);
                        c.drawRect(r, p);
                        p.setColor(Tiles.NEWS_RED);
                        c.drawRect(r.left, r.top, r.left + 6 * d, r.bottom, p);
                    } else {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(Math.max(1, d));
                        p.setColor(Tiles.INK);
                        c.drawRect(r.left + d, r.top + d, r.right - d, r.bottom - d, p);
                    }
                    break;
                }
                case "biograf": {
                    if (focus) {
                        // Billetten: creme med afrevne hak i siderne
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(Tiles.CREAM);
                        Path t = new Path();
                        t.addRect(r, Path.Direction.CW);
                        Path notch = new Path();
                        float nr = 7 * d;
                        notch.addCircle(r.left, r.centerY(), nr, Path.Direction.CW);
                        notch.addCircle(r.right, r.centerY(), nr, Path.Direction.CW);
                        t.op(notch, Path.Op.DIFFERENCE);
                        c.drawPath(t, p);
                    } else {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(Math.max(1, d));
                        p.setColor(Color.argb(110, 201, 162, 79));
                        p.setPathEffect(new DashPathEffect(new float[]{1.5f * d, 4 * d}, 0));
                        c.drawLine(r.left + 8 * d, r.bottom - 1, r.right - 8 * d, r.bottom - 1, p);
                    }
                    break;
                }
                case "jul": {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb(70, 158, 27, 42));
                    c.drawRect(r.left + 10 * d, r.bottom - Math.max(1, 0.8f * d), r.right - 10 * d, r.bottom, p);
                    if (focus) {
                        // En rød kuglepen-ring om ønsket, ikke helt lukket
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(2.6f * d);
                        p.setStrokeCap(Paint.Cap.ROUND);
                        p.setColor(Color.parseColor("#C0262F"));
                        c.save();
                        c.rotate(-1.2f, r.centerX(), r.centerY());
                        c.drawArc(new RectF(r.left + 3 * d, r.top + 2 * d, r.right - 3 * d, r.bottom - 2 * d), 200, 330, false, p);
                        c.drawArc(new RectF(r.left + 6 * d, r.top + 4 * d, r.right - 1 * d, r.bottom), 160, 70, false, p);
                        c.restore();
                    }
                    break;
                }
                case "hygge": {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb(70, 70, 110, 160));
                    c.drawRect(r.left - 40 * d, r.bottom - Math.max(1, 0.8f * d), r.right + 40 * d, r.bottom, p);
                    if (focus) {
                        // Overstregning med en varm tusch
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(Color.argb(70, 214, 140, 90));
                        c.drawRoundRect(new RectF(r.left, r.top + r.height() * 0.2f, r.right, r.bottom - r.height() * 0.1f), 6 * d, 6 * d, p);
                        p.setColor(Color.parseColor("#9A5B3A"));
                        c.drawRect(r.left + 4 * d, r.bottom - 3 * d, r.right - 4 * d, r.bottom - 1.5f * d, p);
                    }
                    break;
                }
                default:
                    break;
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

    /** Selve papiret: panel, sagsmappe, side, program, ønskeseddel eller notesbog. */
    static final class Paper extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Context ctx;
        private final float d;
        private final String key;

        Paper(Context c, String key) {
            ctx = c;
            d = c.getResources().getDisplayMetrics().density;
            this.key = key;
        }

        @Override
        public void draw(Canvas c) {
            Rect bb = getBounds();
            RectF r = new RectF(bb);
            p.setPathEffect(null);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            switch (key) {
                case "jarvis": {
                    float cut = 26 * d;
                    Path s = new Path();
                    s.moveTo(r.left + cut, r.top);
                    s.lineTo(r.right, r.top);
                    s.lineTo(r.right, r.bottom - cut);
                    s.lineTo(r.right - cut, r.bottom);
                    s.lineTo(r.left, r.bottom);
                    s.lineTo(r.left, r.top + cut);
                    s.close();
                    p.setColor(Color.argb(205, 3, 14, 20));
                    c.drawPath(s, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.4f * d);
                    p.setColor(Color.argb(150, 94, 242, 255));
                    c.drawPath(s, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Tiles.CYAN);
                    c.drawRect(r.left + cut + 8 * d, r.top - 1.5f * d, r.left + cut + 90 * d, r.top + 1.5f * d, p);
                    p.setColor(Tiles.GOLD);
                    c.drawRect(r.right - 70 * d, r.bottom - 1.5f * d, r.right - cut - 6 * d, r.bottom + 1.5f * d, p);
                    break;
                }
                case "batman": {
                    // Sagsmappen: en fane øverst, mørkt omslag og et stempel i hjørnet
                    float tab = 34 * d;
                    p.setColor(Color.parseColor("#1A1C20"));
                    c.drawRect(r.left, r.top, r.left + 200 * d, r.top + tab, p);
                    p.setColor(Color.parseColor("#111316"));
                    c.drawRect(r.left, r.top + tab, r.right, r.bottom, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(1, d));
                    p.setColor(Tiles.SLATE_LINE);
                    c.drawRect(r.left, r.top + tab, r.right, r.bottom + 2 * d, p);
                    p.setStyle(Paint.Style.FILL);
                    text(c, "SAG 2026 · HULEN", Fonts.jetMono(ctx), 10, Color.parseColor("#8C8676"), r.left + 14 * d, r.top + 22 * d, Paint.Align.LEFT, 0.12f, 0);
                    // Stempel
                    c.save();
                    c.rotate(-8, r.right - 90 * d, r.top + tab + 40 * d);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2 * d);
                    p.setColor(Color.argb(150, 245, 197, 24));
                    RectF st = new RectF(r.right - 160 * d, r.top + tab + 22 * d, r.right - 22 * d, r.top + tab + 58 * d);
                    c.drawRect(st, p);
                    p.setStyle(Paint.Style.FILL);
                    text(c, "FORTROLIGT", Fonts.shoulders(ctx), 17, Color.argb(170, 245, 197, 24), st.centerX(), st.bottom - 10 * d, Paint.Align.CENTER, 0.18f, 0);
                    c.restore();
                    break;
                }
                case "spiderman": {
                    // Et tegneseriefelt: rødt med raster, tyk sort kant og hård skygge
                    p.setColor(Tiles.COMIC_INK);
                    c.drawRect(r.left + 8 * d, r.top + 8 * d, r.right, r.bottom, p);
                    RectF f = new RectF(r.left, r.top, r.right - 8 * d, r.bottom - 8 * d);
                    p.setColor(Color.parseColor("#D7262E"));
                    c.drawRect(f, p);
                    p.setShader(Stage.dots(d, Color.argb(40, 0, 0, 0), 9, 1.6f));
                    c.drawRect(f, p);
                    p.setShader(null);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(4 * d);
                    p.setColor(Tiles.COMIC_INK);
                    c.drawRect(f, p);
                    break;
                }
                case "superman": {
                    // Rubrikannoncerne: avisens spalte med hoved og dobbelte linjer
                    p.setColor(Color.argb(120, 255, 255, 255));
                    c.drawRect(r, p);
                    text(c, "Daily Planet", Fonts.fraktur(ctx), 30, Tiles.INK, r.centerX(), r.top + 40 * d, Paint.Align.CENTER, 0, 0);
                    p.setColor(Tiles.INK);
                    c.drawRect(r.left + 30 * d, r.top + 52 * d, r.right - 30 * d, r.top + 54 * d, p);
                    c.drawRect(r.left + 30 * d, r.top + 56 * d, r.right - 30 * d, r.top + 57 * d, p);
                    text(c, "RUBRIKANNONCER", Fonts.oldStandardBold(ctx), 9.5f, Tiles.INK, r.centerX(), r.top + 72 * d, Paint.Align.CENTER, 0.3f, 0);
                    break;
                }
                case "biograf": {
                    // Det trykte program: vinrødt karton med dobbelt guldkant
                    p.setColor(Color.parseColor("#2A0A10"));
                    c.drawRect(r, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(Tiles.CINEMA_GOLD);
                    p.setStrokeWidth(1.6f * d);
                    c.drawRect(r.left + 8 * d, r.top + 8 * d, r.right - 8 * d, r.bottom + 4 * d, p);
                    p.setStrokeWidth(0.8f * d);
                    c.drawRect(r.left + 14 * d, r.top + 14 * d, r.right - 14 * d, r.bottom + 4 * d, p);
                    break;
                }
                case "jul": {
                    // Ønskesedlen: cremet papir med linjer og et flettet hjerte i hjørnet
                    p.setColor(Color.parseColor("#FBF4E4"));
                    c.drawRect(r, p);
                    Stage.heart(c, r.right - 40 * d, r.top + 40 * d, 44 * d, 10);
                    break;
                }
                case "hygge": {
                    // Notesbogen: linjeret side, rød margen og huller til spiralen
                    p.setColor(Color.parseColor("#FBF6EA"));
                    c.drawRect(r, p);
                    p.setColor(Color.argb(120, 200, 80, 70));
                    c.drawRect(r.left + 62 * d, r.top, r.left + 63.5f * d, r.bottom, p);
                    p.setColor(Color.parseColor("#E6DBC9"));
                    for (float y = r.top + 30 * d; y < r.bottom; y += 40 * d) c.drawCircle(r.left + 24 * d, y, 7 * d, p);
                    break;
                }
                default:
                    break;
            }
        }

        private void text(Canvas c, String s, Typeface face, float sp, int color, float x, float y, Paint.Align al, float spacing, float rot) {
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setTypeface(face);
            t.setTextSize(sp * d * ctx.getResources().getConfiguration().fontScale);
            t.setColor(color);
            t.setTextAlign(al);
            t.setLetterSpacing(spacing);
            c.drawText(s, x, y, t);
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

    /** Baggrunden bag papiret: forsidens, plus pynt der hører til indstillingerne. */
    static final class Back extends View {
        private final String key;
        private android.graphics.Bitmap cache;

        Back(Context c, String key) {
            super(c);
            this.key = key;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            if (cache == null || cache.getWidth() != w || cache.getHeight() != h) {
                try {
                    cache = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                    paint(new Canvas(cache), w, h);
                } catch (OutOfMemoryError e) {
                    paint(c, w, h);
                    return;
                }
            }
            c.drawBitmap(cache, 0, 0, null);
        }

        private void paint(Canvas c, int w, int h) {
            float d = getResources().getDisplayMetrics().density;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            switch (key) {
                case "jarvis": {
                    c.drawColor(Color.parseColor("#02080D"));
                    Backdrop.draw(getContext(), key, c, w, h, d);
                    // Reaktorringen til venstre, stille
                    float cx = w * 0.2f, cy = h * 0.52f, R = 150 * d;
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(Color.argb(90, 94, 242, 255));
                    p.setStrokeWidth(1.2f * d);
                    c.drawCircle(cx, cy, R, p);
                    p.setStrokeWidth(10 * d);
                    p.setColor(Color.argb(70, 94, 242, 255));
                    p.setPathEffect(new DashPathEffect(new float[]{38 * d, 8 * d}, 0));
                    c.drawCircle(cx, cy, R * 0.72f, p);
                    p.setPathEffect(null);
                    p.setStrokeWidth(3 * d);
                    p.setColor(Tiles.GOLD);
                    c.drawArc(new RectF(cx - R * 0.88f, cy - R * 0.88f, cx + R * 0.88f, cy + R * 0.88f), -90, 250, false, p);
                    p.setStrokeWidth(1.2f * d);
                    p.setColor(Color.argb(120, 94, 242, 255));
                    c.drawCircle(cx, cy, R * 0.5f, p);
                    Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
                    t.setTypeface(Fonts.rajdhani(getContext()));
                    t.setTextAlign(Paint.Align.CENTER);
                    t.setColor(Color.WHITE);
                    t.setTextSize(30 * d);
                    t.setLetterSpacing(0.2f);
                    c.drawText("SYSTEM", cx, cy + 4 * d, t);
                    t.setTypeface(Fonts.techMono(getContext()));
                    t.setTextSize(11 * d);
                    t.setColor(Tiles.CYAN);
                    c.drawText("KONFIGURATION", cx, cy + 26 * d, t);
                    break;
                }
                case "jul": {
                    p.setShader(new RadialGradient(w * 0.5f, h * 0.3f, w * 0.75f, Color.parseColor("#8A1522"), Color.parseColor("#5C0B15"), Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h, p);
                    p.setShader(null);
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(Tiles.XMAS_GOLD);
                    p.setStrokeWidth(1.6f * d);
                    float m = 12 * d;
                    c.drawRect(m, m, w - m, h - m, p);
                    Stage.heart(c, w * 0.16f, h * 0.3f, 70 * d, -10);
                    Stage.heart(c, w * 0.84f, h * 0.68f, 60 * d, 12);
                    Stage.heart(c, w * 0.12f, h * 0.78f, 44 * d, 6);
                    break;
                }
                case "spiderman": {
                    Backdrop.draw(getContext(), key, c, w, h, d);
                    // Et spind fra hjørnet bag felterne
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.4f * d);
                    p.setColor(Color.argb(60, 0, 0, 0));
                    Stage.web(c, p, 0, 0, h * 0.55f, 1, 1);
                    break;
                }
                default:
                    Backdrop.draw(getContext(), key, c, w, h, d);
                    break;
            }
        }
    }
}
