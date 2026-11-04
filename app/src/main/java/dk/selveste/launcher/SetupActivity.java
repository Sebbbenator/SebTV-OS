package dk.selveste.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Velkomstguiden på en ny stick: navn, udgave (Hjem eller Jarvis), tema og kæreste.
 * Alt kan ændres bagefter under Indstillinger, og guiden kan køres igen derfra.
 */
public class SetupActivity extends Activity {

    private Prefs prefs;
    private LinearLayout box;
    private int step;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        show(0);
    }

    @Override
    public void onBackPressed() {
        if (step > 0) show(step - 1);
    }

    private void show(int s) {
        step = s;
        // Tonen kan være skiftet i trinnet før, så skærmen bygges helt forfra hver gang
        prefs = new Prefs(this);
        Ui.loadFont(this, prefs.font());
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 56);
        box.setPadding(pad * 4, pad, pad * 4, pad);
        scroll.addView(box);
        setContentView(scroll);
        switch (s) {
            case 0: welcome(); break;
            case 1: name(); break;
            case 2: edition(); break;
            case 3: theme(); break;
            case 4: partner(); break;
            default: done();
        }
        focusFirst();
    }

    private int accent() {
        return prefs.accentColor();
    }

    private void heading(String title, String text) {
        TextView dots = Ui.text(this, progress(), 14, accent());
        dots.setLetterSpacing(0.4f);
        box.addView(dots);
        TextView t = Ui.text(this, title, 40, Ui.TEXT);
        t.setTypeface(Ui.display);
        t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        box.addView(t);
        TextView d = Ui.text(this, text, 18, Ui.TEXT_DIM);
        d.setPadding(0, 0, 0, Ui.dp(this, 24));
        box.addView(d);
    }

    private String progress() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 4; i++) sb.append(i <= step ? "●" : "○").append(' ');
        return sb.toString().trim();
    }

    private void welcome() {
        TextView heart = Ui.text(this, "♥", 64, accent());
        box.addView(heart);
        heading("Hej!", "Lad os gøre tv'et til dit. Det tager et minut, og alt kan ændres bagefter.");
        box.addView(Ui.row(this, "Kom i gang", "", accent(), v -> show(1)));
        box.addView(Ui.row(this, "Jeg har en profilkode", "Indsæt den fra telefonen", accent(), v -> {
            // Telefonsiden har et felt til koden; når den er lagt ind, er guiden overstået
            startActivity(new android.content.Intent(this, UploadActivity.class));
            finish();
        }));
    }

    private void name() {
        heading("Hvad hedder du?", "Navnet bruges i hilsenen på forsiden.");
        EditText input = field(prefs.profileName(), "Dit navn");
        input.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                saveName(input);
                return true;
            }
            return false;
        });
        box.addView(Ui.row(this, "Næste", "", accent(), v -> saveName(input)));
    }

    private void saveName(EditText input) {
        String n = input.getText().toString().trim();
        prefs.setProfile(n, null, null, null);
        show(2);
    }

    private void edition() {
        heading("Hvilken stil?", "Du kan altid skifte under Indstillinger > Udseende.");
        box.addView(Ui.row(this, "Hjem", "Blød, rolig og personlig", accent(), v -> {
            Themes.setEdition(prefs, Prefs.EDITION_HOME);
            show(3);
        }));
        box.addView(Ui.row(this, "Jarvis", "Sci-fi HUD med Iron Man stemning", accent(), v -> {
            Themes.setEdition(prefs, Prefs.EDITION_JARVIS);
            show(3);
        }));
    }

    private void theme() {
        heading("Vælg et tema", "Farverne skifter med det samme, så du kan se dem.");
        for (String[] t : Themes.ALL) {
            if (!t[3].equals(prefs.edition())) continue;
            boolean current = t[0].equals(prefs.theme());
            box.addView(Ui.row(this, t[1], current ? "Valgt" : t[2], accent(), v -> {
                Themes.apply(prefs, t[0]);
                show(3);
                // Fokus bliver på det valgte tema, så man kan bladre videre
                View again = box.findViewWithTag("theme:" + t[0]);
                if (again != null) again.requestFocus();
            }));
            box.getChildAt(box.getChildCount() - 1).setTag("theme:" + t[0]);
        }
        box.addView(Ui.row(this, "Næste", "", accent(), v -> show(4)));
    }

    private void partner() {
        heading("Har du en kæreste?", "Så tæller forsiden jeres dage sammen. Spring over, hvis det ikke passer.");
        EditText who = field(prefs.partnerName(), "Kærestens navn");
        EditText since = field(prefs.togetherSinceText(), "Sammen siden (åååå-mm-dd)");
        box.addView(Ui.row(this, "Gem og afslut", "", accent(), v -> {
            String n = who.getText().toString().trim();
            String d = since.getText().toString().trim();
            prefs.setProfile(null, null, n, d);
            prefs.setFlag(Features.TOGETHER, !n.isEmpty());
            show(5);
        }));
        box.addView(Ui.row(this, "Spring over", "", accent(), v -> {
            prefs.setFlag(Features.TOGETHER, false);
            show(5);
        }));
    }

    private void done() {
        prefs.setSetupDone(true);
        MainActivity.appsDirty = true;
        MainActivity.wokeUp = true;
        finish();
    }

    private EditText field(String value, String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.TEXT_DIM);
        e.setTextSize(22);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setBackground(Ui.focusable(this, accent()));
        if (Ui.apple()) {
            // Feltet bliver lyst i fokus, så teksten skal være mørk
            e.setOnFocusChangeListener((v, has) -> {
                e.setTextColor(has ? Ui.APPLE_DARK : Ui.TEXT);
                e.setHintTextColor(has ? android.graphics.Color.parseColor("#636366") : Ui.TEXT_DIM);
            });
        }
        int p = Ui.dp(this, 16);
        e.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 12);
        box.addView(e, lp);
        return e;
    }

    private void focusFirst() {
        for (int i = 0; i < box.getChildCount(); i++) {
            View v = box.getChildAt(i);
            if (v.isFocusable()) {
                v.requestFocus();
                return;
            }
        }
    }
}
