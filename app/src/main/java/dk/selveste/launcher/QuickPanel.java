package dk.selveste.launcher;

import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/** Hurtigpanelet i højre side: det man oftest skal bruge, uden at gå ind i menuer. */
final class QuickPanel {

    private QuickPanel() {
    }

    /** Sleep timer: kører i appens proces, som HomeService holder i live, også når en anden app er åben. */
    private static final Handler TIMER = new Handler(Looper.getMainLooper());
    private static long sleepAt;
    private static Runnable sleepJob;

    static void show(MainActivity a) {
        android.app.Dialog d = new android.app.Dialog(a);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        int c = a.currentAccent();
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(24), a.dp(8), a.dp(24), a.dp(24));
        box.setBackground(Ui.card(a, Ui.bg(0.94f), Ui.CARD_STROKE, 1));
        box.addView(Ui.header(a, "HURTIGPANEL", c));

        Map<String, JSONObject> states = a.lastStates;
        JSONObject mode = states == null ? null : states.get(a.prefs.haModes());
        JSONObject modeAttrs = mode == null ? null : mode.optJSONObject("attributes");
        JSONArray modeOptions = modeAttrs == null ? null : modeAttrs.optJSONArray("options");
        if (modeOptions != null && modeOptions.length() > 0) {
            box.addView(Ui.row(a, "Tilstand", mode.optString("state"), c, v -> {
                d.dismiss();
                a.chooseOption(a.prefs.haModes(), "Tilstand", modeOptions, mode.optString("state"));
            }));
        }
        // Hurtigknapper der kan tændes og slukkes, fx lys sync
        for (String id : Prefs.split(a.prefs.haButtons())) {
            JSONObject st = states == null ? null : states.get(id);
            String state = st == null ? "" : st.optString("state");
            if (!"on".equals(state) && !"off".equals(state)) continue;
            boolean on = "on".equals(state);
            View[] row = new View[1];
            row[0] = Ui.row(a, HomeAssistant.friendly(st, id), on ? "Til" : "Fra", c, v -> {
                Ui.setValue(row[0], "...");
                Net.async(() -> {
                    a.ha.press(id);
                    return true;
                }, (ok, err) -> {
                    Ui.setValue(row[0], err != null ? "Fejl" : (on ? "Fra" : "Til"));
                    a.handler.postDelayed(a::refreshRemote, 700);
                });
            });
            box.addView(row[0]);
        }
        if ("billeder".equals(a.prefs.str("bg_source", "billeder")) && a.backgrounds.list().size() > 1) {
            box.addView(Ui.row(a, "Næste baggrund", "", c, v -> {
                List<String> all = a.backgrounds.list();
                a.prefs.setBgCurrent(a.backgrounds.next(all, a.prefs.bgCurrent() == null ? all.get(0) : a.prefs.bgCurrent()));
                a.loadBackground(false);
            }));
        }
        List<String> profiles = Profiles.names(a.prefs);
        if (profiles.size() > 1) {
            String active = Profiles.active(a.prefs);
            box.addView(Ui.row(a, "Profil", active.isEmpty() ? "Vælg" : active, c, v -> {
                d.dismiss();
                String[] names = profiles.toArray(new String[0]);
                new android.app.AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Profil")
                        .setSingleChoiceItems(names, profiles.indexOf(active), (dd, which) -> {
                            dd.dismiss();
                            try {
                                Profiles.load(a, names[which]);
                                MainActivity.lookChangedFromOutside();
                            } catch (Exception e) {
                                ErrorLog.note("Profil", e);
                            }
                        })
                        .show();
            }));
        }
        box.addView(Ui.row(a, "Søg", "Apps, film og værelset", c, v -> {
            d.dismiss();
            Search.show(a);
        }));
        box.addView(Ui.row(a, "Rolig tilstand", "", c, v -> {
            d.dismiss();
            a.setAmbient(true);
        }));
        box.addView(Ui.row(a, "Sov nu", "", c, v -> {
            d.dismiss();
            FireTvMenu.sleep(a);
        }));
        View[] timer = new View[1];
        timer[0] = Ui.row(a, "Sleep timer", sleepLabel(), c, v -> {
            cycleSleep(a);
            Ui.setValue(timer[0], sleepLabel());
        });
        box.addView(timer[0]);
        box.addView(Ui.row(a, "Wi-Fi", "", c, v -> {
            d.dismiss();
            FireTvMenu.open(a, Settings.ACTION_WIFI_SETTINGS);
        }));
        box.addView(Ui.row(a, "Bluetooth og fjernbetjening", "", c, v -> {
            d.dismiss();
            FireTvMenu.open(a, Settings.ACTION_BLUETOOTH_SETTINGS);
        }));
        box.addView(Ui.row(a, "Alle indstillinger", "", c, v -> {
            d.dismiss();
            a.startActivity(new Intent(a, SettingsActivity.class));
        }));

        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        d.setContentView(sv);
        // Menuknappen lukker panelet igen, men ikke ved det første slip, der åbnede det
        boolean[] firstUp = {true};
        d.setOnKeyListener((dialog, keyCode, e) -> {
            if (keyCode != KeyEvent.KEYCODE_MENU) return false;
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (firstUp[0]) firstUp[0] = false;
                else dialog.dismiss();
            }
            return true;
        });
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            w.setLayout(a.dp(480), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.45f);
        }
        d.show();
        View first = MainActivity.firstFocusable(box);
        if (first != null) first.requestFocus();
        }

    static String sleepLabel() {
        if (sleepAt == 0) return "Fra";
        long min = Math.max(1, (sleepAt - System.currentTimeMillis()) / 60000);
        return "Om " + min + " min";
    }

    /** Fra, 30, 60, 90 minutter og tilbage til fra. */
    static void cycleSleep(MainActivity a) {
        int[] steps = {30, 60, 90};
        long left = sleepAt == 0 ? 0 : (sleepAt - System.currentTimeMillis()) / 60000;
        int next = 0;
        for (int st : steps) {
            if (left < st - 1) {
                next = st;
                break;
            }
        }
        if (sleepJob != null) TIMER.removeCallbacks(sleepJob);
        sleepAt = 0;
        if (next == 0) {
            Toast.makeText(a, "Sleep timer slået fra", Toast.LENGTH_SHORT).show();
            return;
        }
        sleepJob = () -> {
            sleepAt = 0;
            HomeService.sleepNow();
        };
        sleepAt = System.currentTimeMillis() + next * 60000L;
        TIMER.postDelayed(sleepJob, next * 60000L);
        Toast.makeText(a, "Tv'et slukker om " + next + " minutter", Toast.LENGTH_SHORT).show();
    }
}
