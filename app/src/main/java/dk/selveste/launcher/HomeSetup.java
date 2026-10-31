package dk.selveste.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Gør appen til forside direkte fra tv'et (uden pc og install script) og rydder op i Fire TV.
 * Første gang bruges tv'ets egen ADB (LocalAdb) til at give appen lov til at slå sin Home tjeneste til;
 * derefter kan appen selv tænde den igen, hvis den bliver slået fra (fx ved force-stop), og ADB kan slås fra.
 */
final class HomeSetup {

    private HomeSetup() {
    }

    private static final String WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS";

    static String service(Context context) {
        return context.getPackageName() + "/" + HomeService.class.getName();
    }

    static boolean canWriteSecure(Context context) {
        return context.checkSelfPermission(WRITE_SECURE) == PackageManager.PERMISSION_GRANTED;
    }

    /** Tænder Home tjenesten igen uden ADB, hvis appen har fået lov. Svarer true, hvis den nu er slået til. */
    static boolean repair(Context context) {
        if (HomeService.isRunning() || !canWriteSecure(context)) return HomeService.isRunning();
        try {
            String svc = service(context);
            String cur = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null || !cur.contains(svc)) {
                Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        cur == null || cur.isEmpty() ? svc : cur + ":" + svc);
            }
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            return true;
        } catch (Exception e) {
            ErrorLog.note("Home tjeneste", e);
            return false;
        }
    }

    /** Hele opsætningen via ADB. Kaldes fra en baggrundstråd; tv'et kan vise "Tillad fejlfinding?". */
    static void makeHome(Context context) throws Exception {
        try (LocalAdb adb = LocalAdb.connect(context, 60_000)) {
            adb.shell("pm grant " + context.getPackageName() + " " + WRITE_SECURE);
            String svc = service(context);
            String cur = adb.shell("settings get secure enabled_accessibility_services");
            if (cur.equals("null")) cur = "";
            if (!cur.contains(svc)) {
                adb.shell("settings put secure enabled_accessibility_services " + (cur.isEmpty() ? svc : cur + ":" + svc));
            }
            adb.shell("settings put secure accessibility_enabled 1");
        }
    }

    // Oprydning: Amazons egne apps, der fylder og reklamerer. Kun det, der findes på sticken, vises.

    /** pakke, forklaring */
    static final String[][] BLOAT = {
            {"com.amazon.kso", "Sponsoreret indhold og reklamer"},
            {"com.amazon.bueller.photos", "Amazon Photos"},
            {"com.amazon.ags.app", "Amazon Games"},
            {"com.amazon.bueller.music", "Amazon Music"},
            {"com.amazon.imdb.tv.android.app", "Freevee"},
            {"com.amazon.avod.thirdpartyclient", "Prime Video"},
            {"com.amazon.tv.ottssocompanionapp", "Login hjælper til tv udbydere"},
            {"com.amazon.tv.csapp", "Kundeservice app"},
    };

    static final class Item {
        final String pkg, label;
        final boolean disabled;

        Item(String pkg, String label, boolean disabled) {
            this.pkg = pkg;
            this.label = label;
            this.disabled = disabled;
        }
    }

    static List<Item> bloat(Context context) {
        PackageManager pm = context.getPackageManager();
        List<Item> items = new ArrayList<>();
        for (String[] b : BLOAT) {
            try {
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(b[0], PackageManager.MATCH_DISABLED_COMPONENTS);
                items.add(new Item(b[0], b[1], !ai.enabled));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return items;
    }

    /** Slår de valgte fra og de fravalgte til igen. Kaldes fra en baggrundstråd. */
    static void applyBloat(Context context, List<Item> items, boolean[] off) throws Exception {
        try (LocalAdb adb = LocalAdb.connect(context, 60_000)) {
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                if (off[i] && !it.disabled) adb.shell("pm disable-user --user 0 " + it.pkg);
                else if (!off[i] && it.disabled) adb.shell("pm enable " + it.pkg);
            }
        }
    }
}
