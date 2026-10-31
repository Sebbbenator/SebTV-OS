package dk.selveste.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Alle indstillinger på sticken. Nøgler til Jellyfin og Home Assistant ligger kun her, aldrig i repoet. */
final class Prefs {

    static final String BG_FIXED = "fast";
    static final String BG_EVERY_HOME = "home";
    static final String BG_HOURLY = "time";
    static final String BG_DAILY = "dag";

    /** Standard HA adresse på en ny stick: tom, indtil brugeren selv forbinder. */
    static final String DEFAULT_HA_URL = "";
    static final String DEFAULT_JF_URL = "";

    /** Accentfarver: nøgle, navn, farve. "auto" tager farven fra baggrunden. */
    static final String[][] ACCENTS = {
            {"cyan", "Jarvis cyan", "#4FD8FF"},
            {"is", "Is", "#BDF3FF"},
            {"guld", "Guld", "#FFC640"},
            {"roed", "Iron Man rød", "#FF5A5F"},
            {"groen", "Grøn", "#5CFFB0"},
            {"lilla", "Lilla", "#B48CFF"},
            {"rosa", "Rosa", "#FF8FB1"},
            {"fersken", "Fersken", "#FFB38A"},
            {"salvie", "Salvie", "#A8D8B0"},
            {"lavendel", "Lavendel", "#C3B1FF"},
            {"karamel", "Karamel", "#D9945A"},
            {"spider", "Spider rød", "#E62429"},
            {"flagermus", "Signalgul", "#F2C230"},
            {"sol", "Solgul", "#F6C232"},
            {"kridt", "Kridt", "#EDEBE6"},
            {"rust", "Rust", "#B4532A"},
            {"kirsebaer", "Kirsebær", "#C2334D"},
            {"sennep", "Sennep", "#D9A441"},
            {"eukalyptus", "Eukalyptus", "#4F7A55"},
            {"maane", "Måneskin", "#F1E6C8"},
            {"jul", "Julerød", "#D23B33"},
            {"petrol", "Petrol", "#1F7A8C"},
            {"skum", "Havskum", "#7FD6DC"},
            {"auto", "Fra baggrunden", ""},
    };

    /** Flisestørrelser: nøgle, navn, bredde på Apps rækken, bredde på de andre (dp). */
    static final String[][] TILE_SIZES = {
            {"kompakt", "Kompakt", "176", "136"},
            {"normal", "Normal", "208", "160"},
            {"stor", "Stor", "240", "192"},
    };

    private final SharedPreferences sp;

    private static boolean migrated;

    Prefs(Context context) {
        sp = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE);
        if (!migrated) {
            migrated = true;
            keepOldDefaults();
        }
        // Alle skærme læser Prefs først, så tonen er altid sat, før noget tegnes
        Ui.applyStyle(tone(), str("cards", "fast"), str("corners", "normal"));
    }

    /**
     * Til og med v113 var Sebs adresser og entiteter standard i koden. Nu er en ny stick tom, så sticks,
     * der allerede kørte, får de gamle standarder skrevet ind én gang og opfører sig som før.
     * Kan fjernes, når alle sticks har kørt v114 eller nyere.
     */
    private void keepOldDefaults() {
        if (sp.getBoolean("old_defaults_kept", false)) return;
        SharedPreferences.Editor e = sp.edit().putBoolean("old_defaults_kept", true);
        boolean existing = sp.getInt("last_seen_version", 0) > 0 || sp.contains("jf_token") || sp.contains("ha_token")
                || sp.getBoolean("setup_done", false) || sp.contains("profile_name");
        if (existing) {
            String nas = "192.168.111.25";
            if (!sp.contains("ha_url")) e.putString("ha_url", "http://" + nas + ":8123");
            if (!sp.contains("jf_url")) e.putString("jf_url", "http://" + nas + ":8096");
            if (!sp.contains("ha_buttons")) e.putString("ha_buttons", "switch.lys_sync,input_select.vaerelset,light.sebbe_bordlampe");
            if (!sp.contains("profile_name")) e.putString("profile_name", "Seb");
            if (!sp.contains("profile_together")) e.putString("profile_together", "2026-01-04");
            e.putString("jf_fallbacks", "http://" + nas + ":8096,http://100.84.70.72:8096");
            e.putString("ha_modes", "input_select.vaerelset");
            e.putString("ha_message", "input_text.jarvis_tv");
            e.putString("ha_status", "weather.forecast_home,sensor.elpris_overblik,switch.lys_sync,sensor.pakker");
            e.putString("tts_voice", "en-GB-RyanNeural");
            e.putString("home_subnet", "192.168.111.");
        }
        e.apply();
    }

    /** Fri tekstværdi, bruges af Looks til udseendet. */
    String str(String key, String def) {
        return sp.getString(key, def);
    }

    void setStr(String key, String value) {
        sp.edit().putString(key, value).apply();
    }

    // Rækker

    List<String> order(String row) {
        String raw = sp.getString("order_" + row, "");
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) out.addAll(Arrays.asList(raw.split(",")));
        return out;
    }

    void setOrder(String row, List<String> packages) {
        sp.edit().putString("order_" + row, String.join(",", packages)).apply();
    }

    Set<String> hidden() {
        return new HashSet<>(sp.getStringSet("hidden", new HashSet<>()));
    }

    void setHidden(Set<String> hidden) {
        sp.edit().putStringSet("hidden", new HashSet<>(hidden)).apply();
    }

    /** Pakker flyttet til en anden række end standard: pakke -> "apps" eller "tools". */
    String rowOverride(String pkg) {
        return sp.getString("row_" + pkg, null);
    }

    void setRowOverride(String pkg, String row) {
        sp.edit().putString("row_" + pkg, row).apply();
    }

    // Baggrunde

    String bgMode() {
        return sp.getString("bg_mode", BG_EVERY_HOME);
    }

    void setBgMode(String mode) {
        sp.edit().putString("bg_mode", mode).apply();
    }

    boolean bgRandom() {
        return sp.getBoolean("bg_random", true);
    }

    void setBgRandom(boolean v) {
        sp.edit().putBoolean("bg_random", v).apply();
    }

    String bgCurrent() {
        return sp.getString("bg_current", null);
    }

    long bgChangedAt() {
        return sp.getLong("bg_changed", 0);
    }

    void setBgCurrent(String name) {
        sp.edit().putString("bg_current", name).putLong("bg_changed", System.currentTimeMillis()).apply();
    }

    /** Husker hvornår og hvor tit en app åbnes, til sortering af Apps. */
    void noteLaunch(String pkg) {
        sp.edit().putLong("last_" + pkg, System.currentTimeMillis()).putLong("uses_" + pkg, sp.getLong("uses_" + pkg, 0) + 1).apply();
    }

    long usage(String pkg, boolean recent) {
        return sp.getLong((recent ? "last_" : "uses_") + pkg, 0);
    }

    /** egen, senest eller mest */
    String appSort() {
        return sp.getString("app_sort", "egen");
    }

    boolean flag(String key, boolean def) {
        return sp.getBoolean("flag_" + key, def);
    }

    void setFlag(String key, boolean v) {
        sp.edit().putBoolean("flag_" + key, v).apply();
    }

    // Home Assistant

    String haUrl() {
        return sp.getString("ha_url", DEFAULT_HA_URL);
    }

    String haToken() {
        return sp.getString("ha_token", "");
    }

    String haButtons() {
        return sp.getString("ha_buttons", "");
    }

    /** Entiteter i statuslinjen. "auto" = det første vejr i HA. */
    List<String> haStatus() {
        return split(sp.getString("ha_status", "auto"));
    }

    void setHaStatus(List<String> ids) {
        sp.edit().putString("ha_status", String.join(",", ids)).apply();
    }

    /** input_select, der vises som rækken Tilstande. Tom = ingen række. */
    String haModes() {
        return sp.getString("ha_modes", "");
    }

    void setHaModes(String id) {
        sp.edit().putString("ha_modes", id).apply();
    }

    /** input_text, som HA skriver beskeder til tv'et i. Tom = ingen beskeder. */
    String haMessage() {
        return sp.getString("ha_message", "");
    }

    void setHaMessage(String id) {
        sp.edit().putString("ha_message", id).apply();
    }

    /** Stemme til HA's TTS (fx en-GB-RyanNeural). Tom = HA's standard stemme. */
    String ttsVoice() {
        return sp.getString("tts_voice", "");
    }

    void setTtsVoice(String v) {
        sp.edit().putString("tts_voice", v.trim()).apply();
    }

    /** Ekstra Jellyfin adresser at prøve, fx samme server over Tailscale. */
    List<String> jfFallbacks() {
        return split(sp.getString("jf_fallbacks", ""));
    }

    void addJfFallback(String url) {
        List<String> all = jfFallbacks();
        if (url == null || url.isEmpty() || all.contains(url)) return;
        all.add(url);
        sp.edit().putString("jf_fallbacks", String.join(",", all)).apply();
    }

    /**
     * Hjemmenettets begyndelse (fx "192.168.111."). Er tv'et på et andet net, peger telefonsidens QR
     * på Tailscale. Tom = altid wifi først.
     */
    String homeSubnet() {
        return sp.getString("home_subnet", "");
    }

    static List<String> split(String raw) {
        List<String> out = new ArrayList<>();
        for (String id : raw.split(",")) if (!id.trim().isEmpty()) out.add(id.trim());
        return out;
    }

    void setHa(String url, String token, String buttons) {
        SharedPreferences.Editor e = sp.edit();
        if (url != null && !url.trim().isEmpty()) e.putString("ha_url", url.trim().replaceAll("/+$", ""));
        if (token != null && !token.trim().isEmpty()) e.putString("ha_token", token.trim());
        if (buttons != null && !buttons.trim().isEmpty()) e.putString("ha_buttons", buttons.replace(" ", ""));
        e.apply();
    }

    void clearHaToken() {
        sp.edit().remove("ha_token").apply();
    }

    // Jellyfin

    String jfUrl() {
        return sp.getString("jf_url", DEFAULT_JF_URL);
    }

    String jfToken() {
        return sp.getString("jf_token", "");
    }

    String jfUser() {
        return sp.getString("jf_user", "");
    }

    void setJfUrl(String url) {
        sp.edit().putString("jf_url", url).apply();
    }

    void setJellyfin(String url, String token, String user) {
        sp.edit().putString("jf_url", url).putString("jf_token", token).putString("jf_user", user).apply();
    }

    void clearJellyfin() {
        sp.edit().remove("jf_token").remove("jf_user").apply();
    }

    String deviceId() {
        String id = sp.getString("device_id", null);
        if (id == null) {
            id = "jarvis-tv-" + Long.toHexString(new SecureRandom().nextLong());
            sp.edit().putString("device_id", id).apply();
        }
        return id;
    }

    // Udseende

    String accentKey() {
        return sp.getString("accent", "cyan");
    }

    void setAccentKey(String key) {
        sp.edit().putString("accent", key).apply();
    }

    /** Den valgte faste accentfarve, eller cyan når den skal komme fra baggrunden. */
    int accentColor() {
        for (String[] a : ACCENTS) {
            if (a[0].equals(accentKey()) && !a[2].isEmpty()) return android.graphics.Color.parseColor(a[2]);
        }
        return Ui.CYAN;
    }

    String tileSize() {
        return sp.getString("tile_size", "normal");
    }

    void setTileSize(String key) {
        sp.edit().putString("tile_size", key).apply();
    }

    int[] tileWidths() {
        for (String[] t : TILE_SIZES) {
            if (t[0].equals(tileSize())) return new int[]{Integer.parseInt(t[2]), Integer.parseInt(t[3])};
        }
        return new int[]{208, 160};
    }

    void setHaButtons(List<String> ids) {
        sp.edit().putString("ha_buttons", String.join(",", ids)).apply();
    }

    // Profil

    /** Navnet i hilsenen; tomt på en ny stick, indtil velkomstguiden spørger. */
    String profileName() {
        return sp.getString("profile_name", "");
    }

    /** Egen hilsen; tom = Godmorgen/Goddag/Godaften efter tidspunktet. */
    String customGreeting() {
        return sp.getString("profile_greeting", "");
    }

    String partnerName() {
        return sp.getString("profile_partner", "");
    }

    /** Dagen I blev kærester som {år, måned (0-11), dag}, eller null hvis den ikke er sat. */
    int[] togetherSince() {
        String raw = sp.getString("profile_together", "");
        try {
            String[] p = raw.split("-");
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2])};
        } catch (Exception e) {
            return null;
        }
    }

    String togetherSinceText() {
        return sp.getString("profile_together", "");
    }

    void setProfile(String name, String greeting, String partner, String together) {
        SharedPreferences.Editor e = sp.edit();
        if (name != null) e.putString("profile_name", name.trim());
        if (greeting != null) e.putString("profile_greeting", greeting.trim());
        if (partner != null) e.putString("profile_partner", partner.trim());
        if (together != null && together.trim().matches("\\d{4}-\\d{2}-\\d{2}")) e.putString("profile_together", together.trim());
        e.apply();
    }

    // Udgave og velkomst

    static final String EDITION_JARVIS = "jarvis";
    static final String EDITION_HOME = "hjem";

    /** Jarvis: sci-fi HUD. Hjem: blød og rolig uden Jarvis. Vælges i velkomstguiden. */
    String edition() {
        return sp.getString("edition", EDITION_JARVIS);
    }

    void setEdition(String e) {
        sp.edit().putString("edition", e).apply();
    }

    boolean isJarvis() {
        return EDITION_JARVIS.equals(edition());
    }

    /** Navnet på tv'ets assistent (overskrift på beskeder og opstart). Tom = intet navn. */
    String assistantName() {
        return sp.getString("assistant", isJarvis() ? "Jarvis" : "");
    }

    void setAssistantName(String name) {
        sp.edit().putString("assistant", name.trim()).apply();
    }

    String appName() {
        return isJarvis() ? "Jarvis TV" : "Hjem";
    }

    /**
     * Velkomstguiden er vist. En stick, der allerede er sat op (fx Sebs), regnes som færdig,
     * så guiden kun kommer på nye sticks.
     */
    boolean setupDone() {
        if (sp.getBoolean("setup_done", false)) return true;
        if (lastSeenVersion() > 0 || !jfToken().isEmpty() || sp.contains("profile_name")) {
            setSetupDone(true);
            return true;
        }
        return false;
    }

    void setSetupDone(boolean v) {
        sp.edit().putBoolean("setup_done", v).apply();
    }

    /** Farvetone for baggrund, kort og tekst: kold (Jarvis blå), varm eller nat. */
    String tone() {
        return sp.getString("tone", "kold");
    }

    void setTone(String t) {
        sp.edit().putString("tone", t).apply();
    }

    // Ur, skrift og tema

    static final String[][] CLOCKS = {
            {"stor", "Stor"},
            {"tynd", "Stor og tynd"},
            {"hud", "Jarvis ring med sekunder"},
            {"sekunder", "Digital med sekunder"},
            {"analog", "Analogt ur"},
    };

    static final String[][] FONTS = {
            {"system", "Standard"},
            {"orbitron", "Orbitron (HUD)"},
            {"rajdhani", "Rajdhani (smal)"},
            {"serif", "Elegant (serif)"},
            {"playfair", "Playfair (fin serif)"},
            {"quicksand", "Quicksand (blød)"},
            {"caveat", "Caveat (håndskrift)"},
            {"bangers", "Bangers (tegneserie)"},
            {"cinzel", "Cinzel (gotisk)"},
            {"bebas", "Bebas Neue (plakat)"},
    };

    String clockStyle() {
        return sp.getString("clock_style", "stor");
    }

    void setClockStyle(String key) {
        sp.edit().putString("clock_style", key).apply();
    }

    String font() {
        return sp.getString("font", "system");
    }

    void setFont(String key) {
        sp.edit().putString("font", key).apply();
    }

    /** "auto" = kun højtider, "blade" = faldende blade (efterårstemaet). */
    String particles() {
        return sp.getString("particles", "auto");
    }

    void setParticles(String p) {
        sp.edit().putString("particles", p).apply();
    }

    String theme() {
        return sp.getString("theme", "");
    }

    void setTheme(String key) {
        sp.edit().putString("theme", key).apply();
    }

    // Effektniveau: hvor meget der bevæger sig. "auto" vælger ud fra sticken (se Motion).

    static final String[][] EFFECTS = {{"auto", "Automatisk"}, {"fuld", "Fuld"}, {"let", "Let"}, {"ingen", "Ingen bevægelse"}};

    String effects() {
        return sp.getString("effects", "auto");
    }

    void setEffects(String level) {
        sp.edit().putString("effects", level).apply();
    }

    // Jarvis beskeder

    String lastMessageStamp() {
        return sp.getString("last_message", "");
    }

    void setLastMessageStamp(String stamp) {
        sp.edit().putString("last_message", stamp).apply();
    }

    // Version og fejl

    int lastSeenVersion() {
        return sp.getInt("last_seen_version", 0);
    }

    void setLastSeenVersion(int v) {
        sp.edit().putInt("last_seen_version", v).apply();
    }

    String lastCrash() {
        return sp.getString("last_crash", "");
    }

    /** commit, fordi appen er ved at gå ned, når den kaldes. */
    void setLastCrash(String text) {
        sp.edit().putString("last_crash", text).commit();
    }

    void clearLastCrash() {
        sp.edit().remove("last_crash").apply();
    }

    // Diverse

    long lastGreeting() {
        return sp.getLong("last_greeting", 0);
    }

    void setLastGreeting(long t) {
        sp.edit().putLong("last_greeting", t).apply();
    }

    /** Opdateringskanal: "test" henter også test versioner fra udviklingsgrenen. */
    boolean testChannel() {
        return "test".equals(sp.getString("channel", "stabil"));
    }

    boolean channelChosen() {
        return sp.contains("channel");
    }

    void setTestChannel(boolean test) {
        sp.edit().putString("channel", test ? "test" : "stabil").apply();
    }

    long lastUpdateCheck() {
        return sp.getLong("last_update_check", 0);
    }

    void setLastUpdateCheck(long t) {
        sp.edit().putLong("last_update_check", t).apply();
    }
}
