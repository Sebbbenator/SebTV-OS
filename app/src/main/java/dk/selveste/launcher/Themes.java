package dk.selveste.launcher;

/**
 * Temaer med ét tryk. Hvert tema har sin egen tegnede scene, skrift, card stil og farver, så de ikke
 * ligner hinanden. Alt kan rettes enkeltvis bagefter under Udseende.
 */
final class Themes {

    /** nøgle, navn, beskrivelse, udgave */
    static final String[][] ALL = {
            {"efteraar", "Efterår", "Tartan i rust og sennep, Playfair og faldende blade", Prefs.EDITION_HOME},
            {"rose", "Rosé", "Rosentryk i to farver som en risograf, rosenblade", Prefs.EDITION_HOME},
            {"hygge", "Hygge", "Strik med nordisk bort og ur i håndskrift", Prefs.EDITION_HOME},
            {"salvie", "Salvie", "Eukalyptus tegnet med streg på lyst papir", Prefs.EDITION_HOME},
            {"natte", "Nattehimmel", "Stjernekort med stjernebilleder og månesegl", Prefs.EDITION_HOME},
            {"jul", "Jul", "Julesweater i masker med stjerner og træer", Prefs.EDITION_HOME},
            {"hav", "Havbrise", "Japanske bølger og sol på lyst papir", Prefs.EDITION_HOME},
            {"jarvis", "Jarvis", "Teknisk tegning, ring ur og HUD skrift", Prefs.EDITION_JARVIS},
            {"ironman", "Iron Man", "Rustningsplader og reaktor i rødt og guld", Prefs.EDITION_JARVIS},
            {"spiderman", "Spider-Man", "Tegneserierude med net, raster og sorte kanter", Prefs.EDITION_JARVIS},
            {"batman", "Batman", "Gotham om natten, signalet i skyerne og regn", Prefs.EDITION_JARVIS},
            {"superman", "Superman", "Retroplakat med solstråler og kappe", Prefs.EDITION_JARVIS},
            {"biograf", "Biograf", "Rødt tæppe, pærer og plakatskrift", Prefs.EDITION_JARVIS},
            {"minimal", "Minimal", "Grafit, tyndt ur og ingen effekter", Prefs.EDITION_JARVIS},
    };

    /** tema: tone, accent, ur, skrift, cards størrelse, scene, effekter, card stil, hjørner, mørklægning */
    private static final String[][] LOOK = {
            {"efteraar", "creme", "rust", "analog", "playfair", "normal", "tartan", "blade", "papir", "rund", "let"},
            {"rose", "rose", "kirsebaer", "tynd", "playfair", "normal", "riso", "kronblade", "papir", "rund", "ingen"},
            {"hygge", "uld", "sennep", "stor", "caveat", "normal", "strik", "ingen", "fast", "rund", "let"},
            {"salvie", "salvie", "eukalyptus", "tynd", "quicksand", "kompakt", "botanisk", "ingen", "streg", "rund", "ingen"},
            {"natte", "nat", "maane", "tynd", "quicksand", "normal", "stjernekort", "stjerner", "streg", "rund", "ingen"},
            {"jul", "skov", "jul", "analog", "playfair", "normal", "julestrik", "sne", "papir", "normal", "let"},
            {"hav", "hav", "petrol", "tynd", "quicksand", "normal", "boelger", "ingen", "papir", "rund", "ingen"},
            {"jarvis", "kold", "cyan", "hud", "orbitron", "normal", "tegning", "auto", "streg", "skarp", "ingen"},
            {"ironman", "reaktor", "guld", "hud", "orbitron", "normal", "reaktor", "auto", "fast", "skarp", "let"},
            {"spiderman", "spider", "spider", "stor", "bangers", "normal", "spindel", "ingen", "tegneserie", "skarp", "ingen"},
            {"batman", "gotham", "flagermus", "tynd", "cinzel", "normal", "gotham", "regn", "streg", "skarp", "ingen"},
            {"superman", "krypton", "sol", "stor", "bebas", "normal", "solstraaler", "ingen", "tegneserie", "skarp", "ingen"},
            {"biograf", "biograf", "guld", "stor", "bebas", "stor", "biograf", "ingen", "fast", "skarp", "let"},
            {"minimal", "grafit", "kridt", "tynd", "system", "kompakt", "grafit", "ingen", "papir", "skarp", "ingen"},
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
        prefs.setStr("scene", l[6]);
        prefs.setStr("bg_source", "scene");
        prefs.setParticles(l[7]);
        prefs.setStr("cards", l[8]);
        prefs.setStr("corners", l[9]);
        prefs.setStr("shade", l[10]);
        // Scenerne er tegnet skarpt, så de skal ikke zoome; højtiderne må stadig komme
        prefs.setFlag(Features.KEN_BURNS, false);
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
