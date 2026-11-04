package dk.selveste.launcher;

import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Én søgning på tværs af apps, Jellyfin og Home Assistant. Åbnes fra hurtigpanelet.
 * Apps og HA søges med det samme på tv'et; Jellyfin spørges, når man holder en pause i skrivningen.
 */
final class Search {

    private Search() {
    }

    /** Domæner man kan trykke på direkte fra søgningen. */
    private static final String[] HA_DOMAINS = {"light.", "switch.", "scene.", "script.", "input_boolean.", "fan.", "automation."};

    static void show(MainActivity a) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        int c = a.currentAccent();
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(24), a.dp(8), a.dp(24), a.dp(24));
        box.setBackground(Ui.panel(a, 0.95f));
        box.addView(Ui.header(a, "SØG", c));
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setHint("Skriv for at søge");
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.TEXT_DIM);
        box.addView(input);
        LinearLayout results = new LinearLayout(a);
        results.setOrientation(LinearLayout.VERTICAL);
        box.addView(results);

        // Alle HA entiteter hentes én gang, når søgningen åbnes
        List<JSONObject> haAll = new ArrayList<>();
        if (a.ha.configured()) {
            Net.async(a.ha::states, (states, err) -> {
                if (states == null) return;
                for (Map.Entry<String, JSONObject> e : states.entrySet()) {
                    for (String dom : HA_DOMAINS) if (e.getKey().startsWith(dom)) haAll.add(e.getValue());
                }
            });
        }

        Handler h = new Handler(Looper.getMainLooper());
        int[] generation = {0};
        Runnable[] jfSearch = {null};
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                int gen = ++generation[0];
                results.removeAllViews();
                if (q.length() < 2) return;
                int shown = 0;
                List<AppEntry> apps = new ArrayList<>(a.repository.apps);
                apps.addAll(a.repository.tools);
                apps.addAll(a.repository.rest);
                for (AppEntry e : apps) {
                    if (shown >= 6 || !e.label.toLowerCase(Locale.ROOT).contains(q)) continue;
                    shown++;
                    results.addView(Ui.row(a, e.label, "App", c, v -> {
                        d.dismiss();
                        a.launch(e);
                    }));
                }
                int ha = 0;
                for (JSONObject st : haAll) {
                    String id = st.optString("entity_id");
                    String name = HomeAssistant.friendly(st, id);
                    if (ha >= 6 || !(name.toLowerCase(Locale.ROOT).contains(q) || id.contains(q))) continue;
                    ha++;
                    String state = st.optString("state");
                    results.addView(Ui.row(a, name, "on".equals(state) ? "Til" : "off".equals(state) ? "Fra" : "Kør", c, v -> {
                        d.dismiss();
                        Net.async(() -> {
                            a.ha.press(id);
                            return true;
                        }, (ok, err) -> a.refreshRemote());
                    }));
                }
                if (jfSearch[0] != null) h.removeCallbacks(jfSearch[0]);
                if (!a.jf.configured()) return;
                jfSearch[0] = () -> Net.async(() -> a.jf.search(q), (items, err) -> {
                    if (items == null || gen != generation[0]) return;
                    for (Jellyfin.Item it : items) {
                        results.addView(Ui.row(a, it.title, it.subtitle == null || it.subtitle.isEmpty() ? "Jellyfin" : it.subtitle, c, v -> {
                            d.dismiss();
                            a.openInWholphin(it.id);
                        }));
                    }
                });
                h.postDelayed(jfSearch[0], 450);
            }
        });

        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        d.setContentView(sv);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(a.dp(620), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        input.requestFocus();
    }
}
