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
            {"grafit", "#111214", "#1B1D20", "#26292D", "#F2F2F0", "#9A9C9F"},
            {"reaktor", "#120606", "#240C0C", "#3A1414", "#FFF1E0", "#D9A88A"},
            {"biograf", "#0E0B0A", "#1C1614", "#2A201D", "#F5E9D8", "#B39C86"},
            {"spider", "#0E1A4A", "#13225C", "#1F2E7A", "#FFFFFF", "#B8C4F0"},
            {"gotham", "#0A0B0D", "#15171B", "#1F2228", "#EDEAE0", "#8E8C84"},
            {"krypton", "#0C2A6E", "#0F3380", "#164099", "#FFFFFF", "#BFD0F2"},
            {"rose", "#1C0E13", "#2A151D", "#3D1E2A", "#FFEFF3", "#D9AFBF"},
            {"uld", "#2A1D17", "#36261E", "#463126", "#F6E9D7", "#C8AE92"},
            {"salvie", "#E6EBE1", "#DCE4D5", "#CBD8C2", "#23331F", "#5E7357"},
            {"skov", "#0F2A1E", "#153526", "#1C4431", "#FBF5EA", "#BFD1C2"},
            {"hav", "#061419", "#0C222A", "#13323D", "#EAF8FA", "#9CC7CF"},
    };

    static float radiusDp = 10;
    /** fast, glas, papir (ingen kant), streg (kun kant) eller tegneserie (sort kant og hård skygge) */
    static String cardStyle = "fast";
    /** Blæk til tegneserie kort: næsten sort på mørke toner, mørk tekstfarve på lyse. */
    static int INK = Color.parseColor("#0A0A14");
    private static String style = "";

    /** Tone, kortstil og hjørner. Billig at kalde ofte: gør kun noget, når noget er ændret. */
    static void applyStyle(String toneKey, String cards, String corners) {
        String sig = toneKey + "|" + cards + "|" + corners;
        if (sig.equals(style)) return;
        style = sig;
        applyTone(toneKey);
        cardStyle = cards;
        INK = Patterns.isLight(BG) ? TEXT : Color.parseColor("#05050A");
        switch (cards) {
            case "glas":
                CARD = HudView.withAlpha(CARD, 0.42f);
                CARD_FOCUS = HudView.withAlpha(CARD_FOCUS, 0.7f);
                CARD_STROKE = HudView.withAlpha(TEXT, 0.35f);
                break;
            case "papir":
                CARD = HudView.withAlpha(CARD, 1f);
                CARD_FOCUS = HudView.withAlpha(CARD_FOCUS, 1f);
                CARD_STROKE = Color.TRANSPARENT;
                break;
            case "streg":
                CARD = HudView.withAlpha(BG, 0.55f);
                CARD_FOCUS = HudView.withAlpha(BG, 0.8f);
                CARD_STROKE = HudView.withAlpha(TEXT, 0.55f);
                break;
            case "apple":
                // Som på Apple TV: lyse, gennemsigtige flader uden kant; fokus vises med lys, størrelse og skygge
                CARD = Color.argb(46, 255, 255, 255);
                CARD_FOCUS = CARD;
                CARD_STROKE = Color.TRANSPARENT;
                break;
            case "tegneserie":
                CARD = HudView.withAlpha(CARD, 1f);
                CARD_FOCUS = HudView.withAlpha(CARD_FOCUS, 1f);
                CARD_STROKE = INK;
                break;
        }
        radiusDp = "ekstra".equals(corners) ? 26 : "rund".equals(corners) ? 18 : "skarp".equals(corners) ? 2 : 10;
        if ("apple".equals(cards)) radiusDp = 14;
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
                case "bangers":
                case "cinzel":
                case "bebas":
                case "playfair":
                case "quicksand": {
                    String file = key.equals("bangers") ? "Bangers" : key.equals("cinzel") ? "Cinzel" : key.equals("bebas") ? "BebasNeue"
                            : key.equals("playfair") ? "PlayfairDisplay" : "Quicksand";
                    display = Typeface.createFromAsset(c.getAssets(), "fonts/" + file + ".ttf");
                    headerFace = display;
                    return;
                }
                case "caveat":
                    // Håndskrift til ur og hilsen; overskrifterne i en rolig skrift, så de kan læses på afstand
                    display = Typeface.createFromAsset(c.getAssets(), "fonts/Caveat.ttf");
                    headerFace = Typeface.createFromAsset(c.getAssets(), "fonts/Quicksand.ttf");
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
        if (apple()) {
            // Apple skriver overskrifter som almindelige ord, grå og halvfede
            String s = text.toLowerCase(java.util.Locale.ROOT);
            t.setText(s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1));
            t.setTextColor(APPLE_GRAY);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            t.setLetterSpacing(0f);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setPadding(0, dp(c, 26), 0, dp(c, 2));
        }
        return t;
    }

    /** Apple TV forsiden: én række store ikoner over et helt foto, uden ur og hilsen. */
    static boolean apple() {
        return "apple".equals(cardStyle);
    }

    static GradientDrawable card(Context c, int fill, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, radiusDp));
        g.setColor(fill);
        g.setStroke(dp(c, strokeDp + restStroke()), stroke);
        return g;
    }

    /** Ekstra kantbredde for stilarter, der lever af stregen. */
    static int restStroke() {
        return "tegneserie".equals(cardStyle) ? 2 : "streg".equals(cardStyle) ? 1 : 0;
    }

    /** Apples farver: lys flade i fokus, mørk tekst på den, grå sekundær tekst. */
    static final int APPLE_LIGHT = Color.parseColor("#F2F2F7");
    static final int APPLE_DARK = Color.parseColor("#1C1C1E");
    static final int APPLE_GRAY = Color.parseColor("#A1A1A6");

    /** Dialogernes udseende: Apples mørkegrå med runde hjørner, ellers systemets. */
    static int dialogTheme() {
        return apple() ? R.style.AppleDialog : android.R.style.Theme_DeviceDefault_Dialog_Alert;
    }

    static StateListDrawable focusable(Context c, int accent) {
        StateListDrawable s = new StateListDrawable();
        if (apple()) {
            // tvOS: knappen bliver lys i fokus, ellers en svag lys flade uden kant
            s.addState(new int[]{android.R.attr.state_focused}, card(c, APPLE_LIGHT, Color.TRANSPARENT, 0));
            s.addState(new int[]{}, card(c, Color.argb(26, 255, 255, 255), Color.TRANSPARENT, 0));
            return s;
        }
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

        if (apple()) {
            // Teksten skifter til mørk på den lyse fokusflade, og rækken løftes en anelse frem
            row.setPadding(dp(c, 24), dp(c, 16), dp(c, 24), dp(c, 16));
            v.setTextColor(APPLE_GRAY);
            row.setOnFocusChangeListener((view, has) -> {
                t.setTextColor(has ? APPLE_DARK : TEXT);
                v.setTextColor(has ? Color.parseColor("#636366") : APPLE_GRAY);
                view.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(150).start();
                view.setElevation(has ? dp(c, 10) : 0);
            });
        }
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

    /** Systemets egne broadcasts (skærm, apps). Fra Android 14 skal det siges, at andre apps ikke må sende hertil. */
    static void registerSystemReceiver(android.content.Context c, android.content.BroadcastReceiver r, android.content.IntentFilter f) {
        if (android.os.Build.VERSION.SDK_INT >= 33) c.registerReceiver(r, f, android.content.Context.RECEIVER_NOT_EXPORTED);
        else c.registerReceiver(r, f);
    }
}
