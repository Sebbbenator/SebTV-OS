package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Fælles byggeklodser til Jarvis skærmene, så de ser ens ud. */
final class Ui {

    static final int CYAN = Color.parseColor("#4FD8FF");
    // Farverne skifter med tonen (kold, varm, nat), så de er ikke final
    static int BG = Color.parseColor("#02080D");
    static int TEXT = Color.parseColor("#D8F6FF");
    static int TEXT_DIM = Color.parseColor("#8FC3D1");
    static int CARD = Color.parseColor("#CC061722");
    static int CARD_FOCUS = Color.parseColor("#E60B2633");
    static int CARD_STROKE = Color.parseColor("#334FD8FF");
    static String tone = "kold";

    /** tone, baggrund, kort, kort i fokus, tekst, dæmpet tekst */
    private static final String[][] TONES = {
            {"kold", "#02080D", "#061722", "#0B2633", "#D8F6FF", "#8FC3D1"},
            {"varm", "#0D0807", "#1E1412", "#2E1E1A", "#FBEFEA", "#CDB2A8"},
            {"nat", "#07061A", "#13112E", "#1F1B45", "#ECE8FF", "#ADA5D6"},
            {"chokolade", "#1A0F0A", "#2B1A12", "#3F2719", "#F7EADC", "#CFB49A"},
            {"creme", "#EFE3D3", "#E6D5C0", "#DCC4A6", "#3B2416", "#7A5A44"},
    };

    static float radiusDp = 10;
    private static String style = "";

    /** Tone, kortstil og hjørner. Billig at kalde ofte: gør kun noget, når noget er ændret. */
    static void applyStyle(String toneKey, String cards, String corners) {
        String sig = toneKey + "|" + cards + "|" + corners;
        if (sig.equals(style)) return;
        style = sig;
        applyTone(toneKey);
        if ("glas".equals(cards)) {
            CARD = HudView.withAlpha(CARD, 0.42f);
            CARD_FOCUS = HudView.withAlpha(CARD_FOCUS, 0.7f);
            CARD_STROKE = HudView.withAlpha(TEXT, 0.35f);
        }
        radiusDp = "ekstra".equals(corners) ? 26 : "rund".equals(corners) ? 18 : 10;
    }

    static void applyTone(String key) {
        String[] t = TONES[0];
        for (String[] x : TONES) if (x[0].equals(key)) t = x;
        tone = t[0];
        BG = Color.parseColor(t[1]);
        CARD = HudView.withAlpha(Color.parseColor(t[2]), 0.8f);
        CARD_FOCUS = HudView.withAlpha(Color.parseColor(t[3]), 0.9f);
        TEXT = Color.parseColor(t[4]);
        TEXT_DIM = Color.parseColor(t[5]);
        CARD_STROKE = HudView.withAlpha(TEXT_DIM, 0.25f);
    }

    /** Baggrundsfarven med en given gennemsigtighed, til skygger over billedet. */
    static int bg(float alpha) {
        return HudView.withAlpha(BG, alpha);
    }

    /** Skrift til ur, hilsen og overskrifter; skiftes under Indstillinger > Skrifttype. */
    static Typeface display = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static Typeface headerFace = Typeface.DEFAULT_BOLD;
    private static String loadedFont;

    static void loadFont(Context c, String key) {
        if (key.equals(loadedFont)) return;
        loadedFont = key;
        try {
            switch (key) {
                case "orbitron":
                    display = Typeface.createFromAsset(c.getAssets(), "fonts/Orbitron.ttf");
                    headerFace = display;
                    return;
                case "rajdhani":
                    display = Typeface.createFromAsset(c.getAssets(), "fonts/Rajdhani-Light.ttf");
                    headerFace = Typeface.createFromAsset(c.getAssets(), "fonts/Rajdhani-Medium.ttf");
                    return;
                case "serif":
                    display = Typeface.create("serif", Typeface.NORMAL);
                    headerFace = Typeface.create("serif", Typeface.BOLD);
                    return;
            }
        } catch (Exception ignored) {
        }
        display = Typeface.create("sans-serif-light", Typeface.NORMAL);
        headerFace = Typeface.DEFAULT_BOLD;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView header(Context c, String text, int accent) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(accent);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLetterSpacing(0.25f);
        t.setTypeface(headerFace);
        t.setPadding(0, dp(c, 22), 0, dp(c, 8));
        return t;
    }

    static GradientDrawable card(Context c, int fill, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, radiusDp));
        g.setColor(fill);
        g.setStroke(dp(c, strokeDp), stroke);
        return g;
    }

    static StateListDrawable focusable(Context c, int accent) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, card(c, CARD_FOCUS, accent, 2));
        s.addState(new int[]{}, card(c, CARD, CARD_STROKE, 1));
        return s;
    }

    /** En række i en liste: titel til venstre, værdi til højre. */
    static LinearLayout row(Context c, String title, String value, int accent, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(focusable(c, accent));
        row.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 8);
        row.setLayoutParams(lp);

        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(c);
        v.setTag("value");
        v.setText(value == null ? "" : value);
        v.setTextColor(accent);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(v);

        row.setOnClickListener(click);
        return row;
    }

    static void setValue(View row, String value) {
        TextView v = row.findViewWithTag("value");
        if (v != null) v.setText(value);
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }
}
