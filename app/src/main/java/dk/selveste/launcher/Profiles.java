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
    /** Hver profils eget Jellyfin login {navn: {token, user}}. Ligger kun på sticken, aldrig i en kode. */
    static final String LOGINS = "profiles_jf";
    /** Spørg hvem der ser, når tv'et tændes. */
    static final String ASK = "profiles_ask";

    static boolean ask(Prefs prefs) {
        return prefs.flag(ASK, false) && names(prefs).size() > 1;
    }

    private static JSONObject logins(Prefs prefs) {
        try {
            return new JSONObject(prefs.str(LOGINS, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Om profilen har sit eget Jellyfin login. */
    static boolean hasLogin(Prefs prefs, String name) {
        return logins(prefs).has(name);
    }

    /** Husker det nuværende Jellyfin login som profilens eget. */
    private static void keepLogin(Prefs prefs, String name) throws Exception {
        if (name.isEmpty() || prefs.jfToken().isEmpty()) return;
        JSONObject l = logins(prefs);
        l.put(name, new JSONObject().put("token", prefs.jfToken()).put("user", prefs.jfUser()));
        prefs.setStr(LOGINS, l.toString());
    }

    private static void useLogin(Prefs prefs, String name) {
        JSONObject mine = logins(prefs).optJSONObject(name);
        if (mine == null) return;
        prefs.setJellyfin(prefs.jfUrl(), mine.optString("token"), mine.optString("user"));
    }

    /** Profilbilledet pr. profil: files/profiler/<navn>.jpg; det aktive ligger som altid i profil.jpg. */
    static java.io.File picture(Context context, String name) {
        java.io.File dir = new java.io.File(context.getFilesDir(), "profiler");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new java.io.File(dir, name.replaceAll("[^\\p{L}\\p{N} _-]", "_") + ".jpg");
    }

    private static void copy(java.io.File from, java.io.File to) {
        try (java.io.InputStream in = new java.io.FileInputStream(from); java.io.OutputStream out = new java.io.FileOutputStream(to)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception ignored) {
        }
    }

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
        keepLogin(prefs, name);
        java.io.File pic = ProfilePicture.file(context);
        if (pic.exists()) copy(pic, picture(context, name));
        else //noinspection ResultOfMethodCallIgnored
            picture(context, name).delete();
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
        // Profilens eget Jellyfin login, så Fortsæt at se og Min liste følger personen
        useLogin(prefs, name);
        java.io.File pic = picture(context, name);
        if (pic.exists()) copy(pic, ProfilePicture.file(context));
        else ProfilePicture.delete(context);
        MainActivity.appsDirty = true;
    }

    static void delete(Context context, String name) {
        Prefs prefs = new Prefs(context);
        JSONObject j = all(prefs);
        j.remove(name);
        prefs.setStr(KEY, j.toString());
        JSONObject l = logins(prefs);
        l.remove(name);
        prefs.setStr(LOGINS, l.toString());
        //noinspection ResultOfMethodCallIgnored
        picture(context, name).delete();
        if (name.equals(active(prefs))) prefs.setStr(ACTIVE, "");
    }
}
