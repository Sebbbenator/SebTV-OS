package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.TextView;

import java.util.Locale;

/**
 * Temaets identitet: hvert tema har sin egen måde at vise overskrifter, ur, fokus, lyde og stemning på,
 * så de ikke bare er samme forside i nye farver. Nøglen er temaets navn (Themes.ALL); "Eget" og ukendte
 * temaer bruger det neutrale udseende.
 */
final class Skin {

    private Skin() {
    }

    /** Det aktive tema; sættes af Prefs, så alle skærme kender det, før noget tegnes. */
    static String key = "";

    static boolean is(String k) {
        return k.equals(key);
    }

    /** Temaets eget ur, som Themes.apply vælger (kan også vælges frit under Udseende). */
    static String clockFor(String theme) {
        switch (theme) {
            case "ironman": return "reaktor";
            case "spiderman": return "boble";
            case "batman": return "signal";
            case "superman": return "sol";
            case "biograf": return "tavle";
            case "rose": return "rosette";
            case "hygge": return "lys";
            case "salvie": return "blad";
            case "natte": return "maane";
            case "jul": return "kugle";
            case "hav": return "boelge";
            case "efteraar": return "analog";
            case "jarvis": return "hud";
            case "minimal": return "tynd";
            default: return null;
        }
    }

    /** Overskrifterne over rækkerne, i temaets eget sprog. */
    static void header(Context c, TextView t, String text, int accent) {
        String lower = text.toLowerCase(Locale.ROOT);
        String title = lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        switch (key) {
            case "jarvis":
                // HUD: klammer og en lille kode foran
                t.setText("[ " + text + " ]");
                t.setLetterSpacing(0.3f);
                break;
            case "ironman":
                // Guld tekst med en rød bjælke foran, som et panel i dragten
                t.setText(text);
                t.setTextColor(Color.parseColor("#F2C35A"));
                t.setCompoundDrawablesRelative(bar(c, Color.parseColor("#D42A22"), 4, 16), null, null, null);
                t.setCompoundDrawablePadding(Ui.dp(c, 10));
                break;
            case "spiderman":
                // Tekstboks fra en tegneserie: gul, sort kant, lidt skæv
                t.setText(text);
                t.setTextColor(Color.parseColor("#111111"));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                t.setLetterSpacing(0.04f);
                GradientDrawable box = new GradientDrawable();
                box.setColor(Color.parseColor("#FFE14D"));
                box.setStroke(Ui.dp(c, 3), Color.parseColor("#111111"));
                t.setBackground(box);
                t.setPadding(Ui.dp(c, 12), Ui.dp(c, 2), Ui.dp(c, 12), Ui.dp(c, 2));
                t.setRotation(-1.5f);
                wrap(c, t, 22, 12);
                break;
            case "batman":
                // Gotisk, spredt og gyldent som et skilt i Gotham
                t.setText(spaced(text));
                t.setTextColor(Color.parseColor("#C9B37E"));
                t.setLetterSpacing(0.35f);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                break;
            case "superman":
                // Rød plakat med hvid tekst
                t.setText(text);
                t.setTextColor(Color.WHITE);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                t.setLetterSpacing(0.08f);
                GradientDrawable red = new GradientDrawable();
                red.setColor(Color.parseColor("#D0202E"));
                red.setCornerRadius(Ui.dp(c, 3));
                t.setBackground(red);
                t.setPadding(Ui.dp(c, 12), Ui.dp(c, 1), Ui.dp(c, 12), 0);
                wrap(c, t, 22, 12);
                break;
            case "biograf":
                // Bogstavtavle over biografens indgang: hvid tavle, sorte bogstaver
                t.setText(text);
                t.setTextColor(Color.parseColor("#111111"));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                t.setLetterSpacing(0.18f);
                GradientDrawable board = new GradientDrawable();
                board.setColor(Color.parseColor("#F4EFE4"));
                board.setStroke(Ui.dp(c, 2), Color.parseColor("#B8892E"));
                board.setCornerRadius(Ui.dp(c, 2));
                t.setBackground(board);
                t.setPadding(Ui.dp(c, 14), Ui.dp(c, 2), Ui.dp(c, 14), 0);
                wrap(c, t, 22, 12);
                break;
            case "minimal":
                // Små, spredte versaler med en tynd streg, som en arkitekttegning
                t.setText("—  " + text);
                t.setLetterSpacing(0.45f);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                t.setTextColor(Ui.TEXT);
                break;
            case "efteraar":
            case "rose":
                // Kursiv serif, som en menu på en café
                t.setText(title);
                t.setLetterSpacing(0.02f);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                t.setTypeface(Typeface.create(Ui.display, Typeface.ITALIC));
                break;
            case "hygge":
                // Håndskrevet, som en seddel på køleskabet
                t.setText(title);
                t.setLetterSpacing(0f);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
                t.setTypeface(Ui.display);
                break;
            case "salvie":
            case "hav":
                // Små bogstaver, luftigt og roligt
                t.setText(lower);
                t.setLetterSpacing(0.12f);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                break;
            case "natte":
                // Spredte versaler mellem to små stjerner
                t.setText("·  " + text + "  ·");
                t.setLetterSpacing(0.4f);
                break;
            case "jul":
                // Rød kursiv serif med en guld streg under
                t.setText(title);
                t.setLetterSpacing(0.02f);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                t.setTypeface(Typeface.create(Ui.display, Typeface.ITALIC));
                t.setTextColor(Color.parseColor("#E8C26B"));
                break;
        }
    }

    private static String spaced(String s) {
        return s.toUpperCase(Locale.ROOT);
    }

    /** Overskrifter med baggrund må ikke strække sig over hele rækkens bredde. */
    private static void wrap(Context c, TextView t, int top, int bottom) {
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, top);
        lp.bottomMargin = Ui.dp(c, bottom);
        t.setLayoutParams(lp);
    }

    private static GradientDrawable bar(Context c, int color, int w, int h) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setSize(Ui.dp(c, w), Ui.dp(c, h));
        return g;
    }

    /** Lyde pr. tema: {art, frekvens fra, frekvens til, sekunder, styrke} for flyt og for OK. */
    static double[][] sounds() {
        switch (key) {
            case "jarvis": return new double[][]{{BLIP, 2400, 2600, 0.03, 0.18}, {CHIRP, 1400, 3200, 0.09, 0.2}};
            case "ironman": return new double[][]{{BLIP, 900, 1100, 0.035, 0.2}, {CHIRP, 300, 2600, 0.32, 0.22}};
            case "spiderman": return new double[][]{{NOISE, 5000, 2500, 0.03, 0.18}, {NOISE, 7000, 900, 0.16, 0.3}};
            case "batman": return new double[][]{{THUD, 180, 120, 0.06, 0.4}, {THUD, 110, 45, 0.35, 0.55}};
            case "superman": return new double[][]{{NOISE, 1500, 3500, 0.05, 0.12}, {CHIRP, 400, 1800, 0.25, 0.2}};
            case "biograf": return new double[][]{{CLICK, 2000, 2000, 0.012, 0.35}, {CLICK, 1200, 1200, 0.2, 0.3}};
            case "minimal": return new double[][]{{CLICK, 3000, 3000, 0.006, 0.25}, {BLIP, 1000, 1000, 0.04, 0.18}};
            case "efteraar": return new double[][]{{THUD, 520, 480, 0.05, 0.3}, {THUD, 340, 300, 0.14, 0.35}};
            case "rose": return new double[][]{{BELL, 1760, 1760, 0.12, 0.08}, {BELL, 1318, 1318, 0.6, 0.12}};
            case "hygge": return new double[][]{{NOISE, 1200, 600, 0.025, 0.08}, {BELL, 660, 660, 0.45, 0.12}};
            case "salvie": return new double[][]{{THUD, 880, 860, 0.07, 0.22}, {THUD, 660, 650, 0.25, 0.28}};
            case "natte": return new double[][]{{BELL, 2637, 2637, 0.15, 0.06}, {BELL, 1976, 1976, 0.8, 0.1}};
            case "jul": return new double[][]{{BELL, 2093, 2093, 0.1, 0.07}, {JINGLE, 2400, 3400, 0.45, 0.12}};
            case "hav": return new double[][]{{DROP, 500, 900, 0.06, 0.2}, {DROP, 300, 1100, 0.16, 0.28}};
            default: return null;
        }
    }

    static final double BLIP = 0, CHIRP = 1, NOISE = 2, THUD = 3, CLICK = 4, BELL = 5, DROP = 6, JINGLE = 7;
}
