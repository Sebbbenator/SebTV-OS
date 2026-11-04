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
            {"hygge", "Hygge", "Billedhylde, seddel med tape og stearinlys", Prefs.EDITION_HOME},
            {"salvie", "Salvie", "Lyst og roligt med eukalyptus", Prefs.EDITION_HOME},
            {"natte", "Nattehimmel", "Mælkevejen og stjerner", Prefs.EDITION_HOME},
            {"jul", "Jul", "Julekalender med låger og flettede hjerter", Prefs.EDITION_HOME},
            {"hav", "Havbrise", "Bølger i dybt blågrønt", Prefs.EDITION_HOME},
            {"jarvis", "Jarvis", "Starks HUD med buereaktor og guld", Prefs.EDITION_JARVIS},
            {"ironman", "Iron Man", "Rød og guld, ring ur", Prefs.EDITION_JARVIS},
            {"spiderman", "Spider-Man", "En tegneserieside med felter og lydord", Prefs.EDITION_JARVIS},
            {"batman", "Batman", "Batcomputerens sagsmapper i sort og ravgul", Prefs.EDITION_JARVIS},
            {"superman", "Superman", "Daily Planets forside, klokken er overskriften", Prefs.EDITION_JARVIS},
            {"biograf", "Biograf", "Markise med pærer, filmplakater og billetter", Prefs.EDITION_JARVIS},
            {"minimal", "Minimal", "Arkitektur i sort og hvid, tyndt ur", Prefs.EDITION_JARVIS},
            {"apple", "Apple", "Forsiden som på Apple TV, med Big Sur, Yosemite og Catalina", Prefs.EDITION_JARVIS},
    };

    /** De 8 temaer med hver sin gennemførte opbygning; resten er ældre temaer, der stadig virker. */
    static final String[] MAIN = {"apple", "jarvis", "spiderman", "batman", "superman", "biograf", "jul", "hygge"};

    static boolean main(String key) {
        for (String k : MAIN) if (k.equals(key)) return true;
        return false;
    }

    /** tema: tone, accent, ur, skrift, cards størrelse, card stil, hjørner */
    private static final String[][] LOOK = {
            {"efteraar", "chokolade", "karamel", "blade", "playfair", "normal", "fast", "rund"},
            {"rose", "rose", "rosa", "rosette", "playfair", "normal", "fast", "rund"},
            {"hygge", "kalk", "terrakotta", "lys", "playfair", "normal", "fast", "skarp"},
            {"salvie", "salvie", "eukalyptus", "blad", "quicksand", "kompakt", "papir", "rund"},
            {"natte", "nat", "maane", "maane", "quicksand", "normal", "fast", "rund"},
            {"jul", "kalender", "julegul", "kugle", "playfair", "normal", "fast", "skarp"},
            {"hav", "hav", "skum", "boelge", "quicksand", "normal", "fast", "rund"},
            {"jarvis", "kold", "cyan", "hud", "rajdhani", "normal", "fast", "normal"},
            {"ironman", "reaktor", "guld", "reaktor", "orbitron", "normal", "fast", "skarp"},
            {"spiderman", "tegneserie", "spider", "boble", "bangers", "normal", "fast", "skarp"},
            {"batman", "gotham", "ravgul", "signal", "bebas", "normal", "fast", "skarp"},
            {"superman", "avis", "avisrod", "sol", "playfair", "normal", "fast", "skarp"},
            {"biograf", "biograf", "guld", "tavle", "bebas", "stor", "fast", "skarp"},
            {"minimal", "grafit", "kridt", "tynd", "system", "kompakt", "papir", "skarp"},
            {"apple", "grafit", "kridt", "tynd", "system", "normal", "apple", "normal"},
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
        // Temaets eget vejr: blade, rosenblade, stjerner, regn over Gotham og sne til jul
        String weather = "efteraar".equals(l[0]) ? "blade" : "rose".equals(l[0]) ? "kronblade" : "natte".equals(l[0]) ? "stjerner" : "auto";
        prefs.setParticles(weather);
        // Hvert tema har sine egne lyde, så de slås til sammen med temaet
        prefs.setFlag(Features.SOUNDS, true);
        // Minimal og Apple er stille: ingen glidende baggrund og ingen opstartsanimation
        boolean calm = "minimal".equals(l[0]) || "apple".equals(l[0]);
        // Ken Burns (det langsomme zoom i fotoet) kun i de ældre temaer: de otte står helt stille, så intet hakker
        prefs.setFlag(Features.KEN_BURNS, !"apple".equals(l[0]) && !main(l[0]));
        prefs.setFlag(Features.SEASONS, true);
        prefs.setFlag(Features.PARALLAX, !calm);
        prefs.setFlag(Features.BOOT, !calm);
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
