package dk.selveste.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import org.json.JSONObject;

/** Nyt i denne version, søg efter opdatering og installér, med dialogerne på tv'et. */
final class AppUpdates {

    private AppUpdates() {
    }

    static void maybeShowChangelog(Activity a, Prefs prefs) {
        int installed = Updater.installedVersion(a);
        if (installed <= prefs.lastSeenVersion()) return;
        prefs.setLastSeenVersion(installed);
        showChangelog(a, installed);
    }

    /** Viser release noterne for en version, hentet fra GitHub. */
    static void showChangelog(Activity a, int version) {
        Net.async(() -> {
            try {
                return new JSONObject(Net.request("GET",
                        "https://api.github.com/repos/" + Updater.REPO + "/releases/tags/launcher-v" + version, null, null))
                        .optString("body", "");
            } catch (java.io.IOException e) {
                return new JSONObject(Net.request("GET",
                        "https://api.github.com/repos/" + Updater.OLD_REPO + "/releases/tags/launcher-v" + version, null, null))
                        .optString("body", "");
            }
        }, (body, err) -> {
            if (a.isFinishing()) return;
            if (body == null || body.trim().isEmpty()) {
                if (err != null) Toast.makeText(a, "Kunne ikke hente noterne", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Nyt i " + new Prefs(a).appName() + " " + version)
                    .setMessage(body.trim())
                    .setPositiveButton("Fedt", null)
                    .show();
        });
    }

    static void maybeCheckUpdate(Activity a, Prefs prefs) {
        if (!Features.on(prefs, Features.AUTO_UPDATE)) return;
        if (System.currentTimeMillis() - prefs.lastUpdateCheck() < 20L * 60 * 60 * 1000) return;
        prefs.setLastUpdateCheck(System.currentTimeMillis());
        Net.async(() -> Updater.check(a), (r, err) -> {
            if (r == null || a.isFinishing()) return;
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Ny version af " + prefs.appName())
                    .setMessage("Version " + r.number + " er klar. Installér nu?")
                    .setPositiveButton("Installér", (d, w) -> installUpdate(a, r))
                    .setNegativeButton("Senere", null)
                    .show();
        });
    }

    static void installUpdate(Context context, Updater.Release r) {
        Toast.makeText(context, "Henter version " + r.number + "...", Toast.LENGTH_SHORT).show();
        Net.async(() -> {
            Updater.install(context, r);
            return true;
        }, (ok, err) -> {
            if (err != null) Toast.makeText(context, "Opdatering fejlede: " + err.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}
