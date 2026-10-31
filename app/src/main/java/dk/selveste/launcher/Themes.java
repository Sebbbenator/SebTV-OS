package dk.selveste.launcher;

/**
 * Temaer med ét tryk: en fotoserie fra Unsplash, skrift, card stil og farver. Alt kan rettes enkeltvis
 * bagefter under Udseende.
 */
final class Themes {

    /** nøgle, navn, beskrivelse, udgave */
    static final String[][] ALL = {
            {"efteraar", "Efterår", "Kakao, tæpper og blade, Playfair og analogt ur", Prefs.EDITION_HOME},
            {"rose", "Rosé", "Dybe roser og fin serif", Prefs.EDITION_HOME},
            {"hygge", "Hygge", "Levende lys og ur i håndskrift", Prefs.EDITION_HOME},
            {"salvie", "Salvie", "Lyst og roligt med eukalyptus", Prefs.EDITION_HOME},
            {"natte", "Nattehimmel", "Mælkevejen og stjerner", Prefs.EDITION_HOME},
            {"jul", "Jul", "Julelys og pejs", Prefs.EDITION_HOME},
            {"hav", "Havbrise", "Bølger i dybt blågrønt", Prefs.EDITION_HOME},
            {"jarvis", "Jarvis", "Hologrammer, ring ur og HUD skrift", Prefs.EDITION_JARVIS},
            {"ironman", "Iron Man", "Rød og guld, ring ur", Prefs.EDITION_JARVIS},
            {"spiderman", "Spider-Man", "Fotos af Spidey, tegneserieskrift og cards", Prefs.EDITION_JARVIS},
            {"batman", "Batman", "Gotham om natten og gotisk skrift", Prefs.EDITION_JARVIS},
            {"superman", "Superman", "Rød kappe og blå himmel, plakatskrift", Prefs.EDITION_JARVIS},
            {"biograf", "Biograf", "Biografsale og plakatskrift", Prefs.EDITION_JARVIS},
            {"minimal", "Minimal", "Arkitektur i sort og hvid, tyndt ur", Prefs.EDITION_JARVIS},
    };

    /** tema: tone, accent, ur, skrift, cards størrelse, card stil, hjørner */
    private static final String[][] LOOK = {
            {"efteraar", "chokolade", "karamel", "analog", "playfair", "normal", "fast", "rund"},
            {"rose", "rose", "rosa", "tynd", "playfair", "normal", "fast", "rund"},
            {"hygge", "uld", "sennep", "stor", "caveat", "normal", "fast", "rund"},
            {"salvie", "salvie", "eukalyptus", "tynd", "quicksand", "kompakt", "papir", "rund"},
            {"natte", "nat", "maane", "tynd", "quicksand", "normal", "fast", "rund"},
            {"jul", "skov", "jul", "analog", "playfair", "normal", "fast", "rund"},
            {"hav", "hav", "skum", "tynd", "quicksand", "normal", "fast", "rund"},
            {"jarvis", "kold", "cyan", "hud", "orbitron", "normal", "fast", "normal"},
            {"ironman", "reaktor", "guld", "hud", "orbitron", "normal", "fast", "skarp"},
            {"spiderman", "spider", "spider", "stor", "bangers", "normal", "tegneserie", "skarp"},
            {"batman", "gotham", "flagermus", "tynd", "cinzel", "normal", "fast", "skarp"},
            {"superman", "krypton", "sol", "stor", "bebas", "normal", "fast", "skarp"},
            {"biograf", "biograf", "guld", "stor", "bebas", "stor", "fast", "skarp"},
            {"minimal", "grafit", "kridt", "tynd", "system", "kompakt", "papir", "skarp"},
    };

    static void apply(Prefs prefs, String key) {
        String[] l = LOOK[7];
        for (String[] x : LOOK) if (x[0].equals(key)) l = x;
        prefs.setTheme(l[0]);
        prefs.setTone(l[1]);
        prefs.setAccentKey(l[2]);
        prefs.setClockStyle(l[3]);
        prefs.setFont(l[4]);
        prefs.setTileSize(l[5]);
        prefs.setStr("cards", l[6]);
        prefs.setStr("corners", l[7]);
        // Temaets fotos, hentet af tv'et selv; højtiderne (sne, fyrværkeri) må stadig komme
        prefs.setStr("photo_theme", l[0]);
        prefs.setStr("bg_source", "tema");
        prefs.setStr("shade", "normal");
        prefs.setParticles("auto");
        prefs.setFlag(Features.KEN_BURNS, true);
        prefs.setFlag(Features.SEASONS, true);
        prefs.setFlag(Features.PARALLAX, !"minimal".equals(l[0]));
        prefs.setFlag(Features.BOOT, !"minimal".equals(l[0]));
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
