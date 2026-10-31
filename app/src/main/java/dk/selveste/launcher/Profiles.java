package dk.selveste.launcher;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Flere profiler på samme stick (fx "Seb", "Film aften", "Gæst"): hver er bare en gemt profilkode.
 * Et skift lægger koden ind oven på indstillingerne; login og tokens røres ikke.
 */
final class Profiles {

    private Profiles() {
    }

    static final String KEY = "profiles_json";
    static final String ACTIVE = "profile_active";

    private static JSONObject all(Prefs prefs) {
        try {
            return new JSONObject(prefs.str(KEY, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static List<String> names(Prefs prefs) {
        List<String> out = new ArrayList<>();
        Iterator<String> it = all(prefs).keys();
        while (it.hasNext()) out.add(it.next());
        return out;
    }

    static String active(Prefs prefs) {
        return prefs.str(ACTIVE, "");
    }

    /** Gemmer de nuværende indstillinger under et navn (overskriver en med samme navn). */
    static void save(Context context, String name) throws Exception {
        Prefs prefs = new Prefs(context);
        JSONObject j = all(prefs);
        j.put(name, ProfileCode.export(context));
        prefs.setStr(KEY, j.toString());
        prefs.setStr(ACTIVE, name);
    }

    /** Skifter til en profil. Den nuværende gemmes først, så intet går tabt. */
    static void load(Context context, String name) throws Exception {
        Prefs prefs = new Prefs(context);
        String current = active(prefs);
        if (!current.isEmpty() && !current.equals(name) && all(prefs).has(current)) save(context, current);
        String code = all(prefs).optString(name, "");
        if (code.isEmpty()) return;
        ProfileCode.apply(context, code);
        prefs.setStr(ACTIVE, name);
    }

    static void delete(Context context, String name) {
        Prefs prefs = new Prefs(context);
        JSONObject j = all(prefs);
        j.remove(name);
        prefs.setStr(KEY, j.toString());
        if (name.equals(active(prefs))) prefs.setStr(ACTIVE, "");
    }
}
