package dk.selveste.launcher;

/** Temaer med ét tryk: sætter tone, farve, ur, skrift og et par effekter på én gang. Alt kan rettes bagefter. */
final class Themes {

    /** nøgle, navn, beskrivelse, udgave */
    static final String[][] ALL = {
            {"efteraar", "Efterår", "Lys creme, karamel, ternet stof og faldende blade", Prefs.EDITION_HOME},
            {"rose", "Rosé", "Varm og blød med rosa", Prefs.EDITION_HOME},
            {"hygge", "Hygge", "Varme farver som levende lys", Prefs.EDITION_HOME},
            {"salvie", "Salvie", "Rolig grøn og elegant skrift", Prefs.EDITION_HOME},
            {"natte", "Nattehimmel", "Dyb blå med lavendel", Prefs.EDITION_HOME},
            {"jarvis", "Jarvis", "Cyan, ring ur og HUD skrift", Prefs.EDITION_JARVIS},
            {"ironman", "Iron Man", "Rød og guld, ring ur, HUD skrift", Prefs.EDITION_JARVIS},
            {"minimal", "Minimal", "Rolig, tyndt ur, ingen effekter", Prefs.EDITION_JARVIS},
            {"biograf", "Biograf", "Farve fra baggrunden, smal skrift", Prefs.EDITION_JARVIS},
    };

    static void apply(Prefs prefs, String key) {
        prefs.setTheme(key);
        switch (key) {
            case "efteraar":
                look(prefs, "creme", "karamel", "analog", "serif", "normal");
                effects(prefs, true);
                prefs.setFlag(Features.PARALLAX, false);
                prefs.setParticles("blade");
                prefs.setStr("bg_source", "ternet");
                prefs.setStr("corners", "ekstra");
                prefs.setStr("cards", "glas");
                prefs.setStr("shade", "let");
                break;
            case "rose":
                look(prefs, "varm", "rosa", "tynd", "serif", "normal");
                effects(prefs, true);
                prefs.setFlag(Features.PARALLAX, false);
                break;
            case "hygge":
                look(prefs, "varm", "fersken", "stor", "system", "normal");
                effects(prefs, true);
                prefs.setFlag(Features.PARALLAX, false);
                break;
            case "salvie":
                look(prefs, "varm", "salvie", "tynd", "serif", "kompakt");
                effects(prefs, false);
                prefs.setFlag(Features.KEN_BURNS, true);
                break;
            case "natte":
                look(prefs, "nat", "lavendel", "tynd", "system", "normal");
                effects(prefs, true);
                break;
            case "ironman":
                look(prefs, "kold", "roed", "hud", "orbitron", "normal");
                effects(prefs, true);
                break;
            case "minimal":
                look(prefs, "kold", "is", "tynd", "system", "kompakt");
                effects(prefs, false);
                break;
            case "biograf":
                look(prefs, "kold", "auto", "stor", "rajdhani", "stor");
                effects(prefs, true);
                prefs.setFlag(Features.PARALLAX, false);
                prefs.setFlag(Features.LATEST, true);
                prefs.setFlag(Features.NOW_PLAYING, true);
                break;
            default:
                look(prefs, "kold", "cyan", "hud", "orbitron", "normal");
                effects(prefs, true);
        }
    }

    private static void look(Prefs prefs, String tone, String accent, String clock, String font, String tiles) {
        prefs.setParticles("auto");
        prefs.setStr("bg_source", "billeder");
        prefs.setStr("corners", "normal");
        prefs.setStr("cards", "fast");
        prefs.setStr("shade", "normal");
        prefs.setTone(tone);
        prefs.setAccentKey(accent);
        prefs.setClockStyle(clock);
        prefs.setFont(font);
        prefs.setTileSize(tiles);
    }

    private static void effects(Prefs prefs, boolean on) {
        prefs.setFlag(Features.KEN_BURNS, on);
        prefs.setFlag(Features.PARALLAX, on);
        prefs.setFlag(Features.BOOT, on);
        prefs.setFlag(Features.SEASONS, on);
    }

    /** Skifter udgave og slår det fra, der kun giver mening i Jarvis (Konami, stemme). */
    static void setEdition(Prefs prefs, String edition) {
        prefs.setEdition(edition);
        boolean jarvis = Prefs.EDITION_JARVIS.equals(edition);
        prefs.setFlag(Features.KONAMI, jarvis);
        prefs.setFlag(Features.VOICE, jarvis);
        apply(prefs, jarvis ? "jarvis" : "rose");
    }

    static String name(String key) {
        for (String[] t : ALL) if (t[0].equals(key)) return t[1];
        return "Eget";
    }
}
