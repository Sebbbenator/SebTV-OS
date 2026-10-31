package dk.selveste.launcher;

/**
 * Alle de ting, man selv kan skrue på i udseendet. Samme liste bruges på tv'et (Indstillinger > Udseende)
 * og på telefonsiden, så de altid viser det samme.
 */
final class Looks {

    static final class Option {
        final String key;
        final String pref;
        final String title;
        final String group;
        final String def;
        final String[][] values;

        Option(String key, String pref, String title, String group, String def, String[][] values) {
            this.key = key;
            this.pref = pref;
            this.title = title;
            this.group = group;
            this.def = def;
            this.values = values;
        }
    }

    static final String[][] TONES = {
            {"kold", "Mørk blå"},
            {"varm", "Mørk varm"},
            {"chokolade", "Chokolade"},
            {"nat", "Nattehimmel"},
            {"creme", "Lys creme"},
            {"grafit", "Grafit"},
            {"reaktor", "Reaktor rød"},
            {"biograf", "Biografmørke"},
            {"spider", "Spider blå"},
            {"gotham", "Gotham sort"},
            {"krypton", "Krypton blå"},
            {"rose", "Mørk rosé"},
            {"uld", "Uld brun"},
            {"salvie", "Lys salvie"},
            {"skov", "Skovgrøn"},
            {"hav", "Dybt hav"},
    };

    static final String[][] CORNERS = {{"skarp", "Skarpe"}, {"normal", "Normale"}, {"rund", "Runde"}, {"ekstra", "Ekstra runde"}};
    static final String[][] CARDS = {{"fast", "Faste"}, {"papir", "Papir (flade, uden kant)"}, {"streg", "Kun streg"},
            {"tegneserie", "Tegneserie (sort kant og skygge)"}, {"glas", "Glas (gennemsigtige)"}};
    static final String[][] SHADE = {{"ingen", "Ingen"}, {"let", "Let"}, {"normal", "Normal"}, {"kraftig", "Kraftig"}};
    static final String[][] BG_SOURCE = {{"tema", "Temaets fotos"}, {"billeder", "Mine billeder"}, {"ensfarvet", "Ensfarvet"}};
    static final String[][] SORT = {{"egen", "Min egen rækkefølge"}, {"senest", "Senest brugt forrest"}, {"mest", "Mest brugt forrest"}};
    static final String[][] PARTICLES = {{"auto", "Kun højtider"}, {"blade", "Faldende blade"}, {"kronblade", "Rosenblade"},
            {"stjerner", "Blinkende stjerner"}, {"regn", "Regn"}, {"sne", "Sne"}, {"ingen", "Ingen"}};

    static final Option[] ALL = {
            new Option("tone", "tone", "Farvetone", "Farver", "kold", TONES),
            new Option("accent", "accent", "Accentfarve", "Farver", "cyan", Prefs.ACCENTS),
            new Option("bg", "bg_source", "Baggrund", "Stemning", "billeder", BG_SOURCE),
            new Option("photos", "photo_theme", "Fotoserie", "Stemning", "jarvis", Themes.ALL),
            new Option("shade", "shade", "Mørklægning af baggrund", "Stemning", "normal", SHADE),
            new Option("particles", "particles", "Effekter på skærmen", "Stemning", "auto", PARTICLES),
            new Option("corners", "corners", "Hjørner", "Form", "normal", CORNERS),
            new Option("cards", "cards", "Cards", "Form", "fast", CARDS),
            new Option("tiles", "tile_size", "Størrelse på cards", "Form", "normal", Prefs.TILE_SIZES),
            new Option("clock", "clock_style", "Ur", "Ur og skrift", "stor", Prefs.CLOCKS),
            new Option("font", "font", "Skrifttype", "Ur og skrift", "system", Prefs.FONTS),
            new Option("sort", "app_sort", "Rækkefølge i Apps", "Forside", "egen", SORT),
    };

    static final String[] GROUPS = {"Farver", "Stemning", "Form", "Ur og skrift", "Forside"};

    static Option find(String key) {
        for (Option o : ALL) if (o.key.equals(key)) return o;
        return null;
    }

    static String get(Prefs prefs, Option o) {
        return prefs.str(o.pref, o.def);
    }

    /** Sætter en enkelt ting. Temaet hedder derefter "Eget", fordi det ikke længere er et rent tema. */
    static void set(Prefs prefs, Option o, String value) {
        for (String[] v : o.values) {
            if (v[0].equals(value)) {
                prefs.setStr(o.pref, value);
                // Sortering er ikke en del af udseendet, så temaet beholder sit navn
                if (!"Forside".equals(o.group)) prefs.setTheme("");
                return;
            }
        }
    }

    static String name(Option o, String value) {
        for (String[] v : o.values) if (v[0].equals(value)) return v[1];
        return o.values[0][1];
    }

    /** Alt der kræver, at forsiden bygges forfra, når det ændres. */
    static String layoutSignature(Prefs prefs) {
        return prefs.tone() + "|" + prefs.str("cards", "fast") + "|" + prefs.str("corners", "normal") + "|" + prefs.str("shade", "normal");
    }
}
