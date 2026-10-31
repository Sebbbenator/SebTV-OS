package dk.selveste.launcher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Henter nyeste Jarvis TV fra GitHub releases og installerer den uden pc. */
final class Updater {

    static final String REPO = "Sebbbenator/SebTV-OS";
    /** Hvor launcheren boede til og med version 9; bruges kun til gamle release noter. */
    static final String OLD_REPO = "Sebbbenator/CSS-Jellyfin";
    static final String RELEASES = "https://api.github.com/repos/" + REPO + "/releases/latest";
    /** Alle udgivelser, også test (pre-release) versioner fra udviklingsgrenen. */
    static final String ALL_RELEASES = "https://api.github.com/repos/" + REPO + "/releases?per_page=15";

    /** Sat ved natlig opdatering: indtil da trykker HomeService selv på Installér i Fire OS dialogen. */
    static volatile long autoConfirmUntil;

    static final class Release {
        int number;
        String apkUrl;
    }

    static int installedVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    static String installedName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    /**
     * Nyere release end den installerede, ellers null. Kaldes fra en baggrundstråd.
     * Kanal "stabil" følger kun den udgivelse, der er markeret som nyeste på GitHub.
     * Kanal "test" tager også test versioner (pre-releases), så nyt kan prøves på én stick først.
     */
    static Release check(Context context) throws Exception {
        Release best;
        if (testChannel(context)) {
            best = null;
            JSONArray all = new JSONArray(Net.request("GET", ALL_RELEASES, null, null));
            for (int i = 0; i < all.length(); i++) {
                Release r = parse(all.getJSONObject(i));
                if (r != null && (best == null || r.number > best.number)) best = r;
            }
        } else {
            best = parse(new JSONObject(Net.request("GET", RELEASES, null, null)));
        }
        if (best == null || best.number <= installedVersion(context)) return null;
        return best;
    }

    /** Test kanal er valgt, eller (indtil der vælges) appen selv er en test version. */
    static boolean testChannel(Context context) {
        Prefs p = new Prefs(context);
        return p.channelChosen() ? p.testChannel() : installedName(context).endsWith("-test");
    }

    private static Release parse(JSONObject j) {
        String tag = j.optString("tag_name", "");
        if (!tag.startsWith("launcher-v") || j.optBoolean("draft", false)) return null;
        Release r = new Release();
        try {
            r.number = Integer.parseInt(tag.substring("launcher-v".length()));
        } catch (NumberFormatException e) {
            return null;
        }
        JSONArray assets = j.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && "jarvis-tv.apk".equals(a.optString("name"))) r.apkUrl = a.optString("browser_download_url");
        }
        return r.apkUrl == null ? null : r;
    }

    /**
     * Natlig opdatering (kl. ca. 4, kaldt fra NightUpdate): hent og installer, og lad HomeService
     * bekræfte dialogen, så det sker uden et tryk. Noterne vises næste gang forsiden åbnes.
     */
    static void installUnattended(Context context, Release r) throws Exception {
        autoConfirmUntil = System.currentTimeMillis() + 3 * 60 * 1000;
        install(context, r);
    }

    /** Hent og installer. Fire OS viser selv en bekræftelse, som InstallReceiver åbner. */
    static void install(Context context, Release r) throws Exception {
        File apk = new File(context.getCacheDir(), "jarvis-tv-update.apk");
        Net.download(r.apkUrl, apk);

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("jarvis-tv.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pi = PendingIntent.getBroadcast(context, id,
                    new Intent(context, InstallReceiver.class), flags);
            session.commit(pi.getIntentSender());
        }
    }
}
