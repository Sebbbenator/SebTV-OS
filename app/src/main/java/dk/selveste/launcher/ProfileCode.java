package dk.selveste.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Profilkoden: alle indstillinger (tema, rækker, navn, HA valg) pakket i en kort tekst, der kan
 * gemmes på telefonen, deles med en ven eller ligge på ens egen Jellyfin. Login og tokens er aldrig
 * med, så en delt kode ikke åbner nogens servere.
 */
final class ProfileCode {

    private ProfileCode() {
    }

    static final String PREFIX = "SEBTV1:";

    /** Nøgler der aldrig kommer med: hemmeligheder, denne sticks egne tal og ting der bare er tilstand. */
    private static final String[] SKIP_EXACT = {
            "ha_token", "ha_refresh", "ha_access", "ha_access_until", "ha_client", "jf_token", "jf_user", "device_id",
            "bg_current", "bg_changed", "last_seen_version", "last_crash", "last_message", "last_greeting",
            "last_update_check", "old_defaults_kept", "setup_done", "tts_engine", "channel", "jf_profile_pushed",
            "effects_measured", Profiles.KEY, Profiles.ACTIVE,
    };
    private static final String[] SKIP_PREFIX = {"last_", "uses_", "cache_"};

    static boolean skip(String key) {
        for (String k : SKIP_EXACT) if (k.equals(key)) return true;
        for (String p : SKIP_PREFIX) if (key.startsWith(p)) return true;
        return false;
    }

    static String export(Context context) throws Exception {
        SharedPreferences sp = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE);
        JSONObject j = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            if (skip(e.getKey())) continue;
            Object v = e.getValue();
            if (v instanceof Set) {
                JSONArray a = new JSONArray();
                for (Object o : (Set<?>) v) a.put(String.valueOf(o));
                j.put(e.getKey(), new JSONObject().put("set", a));
            } else if (v instanceof Integer) {
                j.put(e.getKey(), new JSONObject().put("int", v));
            } else if (v instanceof Long) {
                j.put(e.getKey(), new JSONObject().put("long", v));
            } else if (v instanceof Float) {
                j.put(e.getKey(), new JSONObject().put("float", (double) (Float) v));
            } else {
                j.put(e.getKey(), v);
            }
        }
        return encode(j);
    }

    /** Det, der kun handler om personen selv: navn, hilsen, kæreste, mærkedage, egen opstartstekst og byen. Bliver hjemme, når koden deles med en ven. */
    static final String[] PERSONAL = {"profile_name", "profile_greeting", "profile_partner", "profile_together", "boot_title", "boot_sub",
            Days.KEY, Weather.CITY, Weather.LAT, Weather.LON, Weather.TEXT, Weather.AT};

    /** Samme kode uden det personlige, til at dele med venner. */
    static String forFriend(String code) throws Exception {
        JSONObject j = decode(code);
        for (String k : PERSONAL) j.remove(k);
        return encode(j);
    }

    static String encode(JSONObject j) {
        byte[] raw = j.toString().getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return PREFIX + Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    static JSONObject decode(String code) throws Exception {
        String c = code.trim();
        int i = c.indexOf(PREFIX);
        if (i < 0) throw new IllegalArgumentException("Det er ikke en profilkode");
        byte[] packed = Base64.decode(c.substring(i + PREFIX.length()).replaceAll("\\s", ""), Base64.URL_SAFE);
        Inflater inf = new Inflater();
        inf.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!inf.finished()) {
            int n = inf.inflate(buf);
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
            out.write(buf, 0, n);
            if (out.size() > 256 * 1024) throw new IllegalArgumentException("Koden er for stor");
        }
        inf.end();
        return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
    }

    /** Lægger koden ind oven på de nuværende indstillinger. Login og tokens røres ikke. */
    static void apply(Context context, String code) throws Exception {
        JSONObject j = decode(code);
        SharedPreferences.Editor e = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit();
        Iterator<String> keys = j.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (skip(k)) continue;
            Object v = j.get(k);
            if (v instanceof JSONObject) {
                JSONObject o = (JSONObject) v;
                if (o.has("set")) {
                    Set<String> set = new HashSet<>();
                    JSONArray a = o.getJSONArray("set");
                    for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                    e.putStringSet(k, set);
                } else if (o.has("int")) e.putInt(k, o.getInt("int"));
                else if (o.has("long")) e.putLong(k, o.getLong("long"));
                else if (o.has("float")) e.putFloat(k, (float) o.getDouble("float"));
            } else if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
            else e.putString(k, String.valueOf(v));
        }
        e.putBoolean("setup_done", true);
        e.commit();
        MainActivity.appsDirty = true;
    }

    // Synk via brugerens egen Jellyfin: Jellyfin gemmer app-indstillinger pr. bruger (DisplayPreferences)

    private static final String JF_PREFS = "/DisplayPreferences/sebtv?client=sebtv&userId=";

    /** Gemmer koden på Jellyfin, hvis den har ændret sig siden sidst. Kaldes fra en baggrundstråd. */
    static void pushToJellyfin(Context context) throws Exception {
        Prefs prefs = new Prefs(context);
        Jellyfin jf = new Jellyfin(prefs);
        if (!jf.configured()) return;
        String code = export(context);
        if (code.equals(prefs.str("jf_profile_pushed", ""))) return;
        JSONObject dto;
        try {
            dto = new JSONObject(jf.raw("GET", JF_PREFS + prefs.jfUser(), null));
        } catch (Exception e) {
            dto = new JSONObject();
        }
        dto.put("Id", "sebtv");
        dto.put("Client", "sebtv");
        JSONObject custom = dto.optJSONObject("CustomPrefs");
        if (custom == null) custom = new JSONObject();
        // Delt i stykker, hvis Jellyfin har en grænse på feltlængden
        Iterator<String> old = custom.keys();
        java.util.List<String> remove = new java.util.ArrayList<>();
        while (old.hasNext()) {
            String k = old.next();
            if (k.startsWith("profil")) remove.add(k);
        }
        for (String k : remove) custom.remove(k);
        int part = 0;
        for (int i = 0; i < code.length(); i += 900) custom.put("profil" + part++, code.substring(i, Math.min(code.length(), i + 900)));
        custom.put("profil_dele", String.valueOf(part));
        dto.put("CustomPrefs", custom);
        jf.raw("POST", JF_PREFS + prefs.jfUser(), dto.toString());
        prefs.setStr("jf_profile_pushed", code);
    }

    /** Koden fra Jellyfin, eller null. Kaldes fra en baggrundstråd. */
    static String pullFromJellyfin(Context context) throws Exception {
        Prefs prefs = new Prefs(context);
        Jellyfin jf = new Jellyfin(prefs);
        if (!jf.configured()) return null;
        JSONObject dto = new JSONObject(jf.raw("GET", JF_PREFS + prefs.jfUser(), null));
        JSONObject custom = dto.optJSONObject("CustomPrefs");
        if (custom == null) return null;
        int parts = Integer.parseInt(custom.optString("profil_dele", "0"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts; i++) sb.append(custom.optString("profil" + i, ""));
        String code = sb.toString();
        return code.startsWith(PREFIX) ? code : null;
    }
}
