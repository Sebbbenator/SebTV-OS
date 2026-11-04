package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/** Temaernes egne skrifttyper fra assets/fonts, læst én gang og gemt. */
final class Fonts {

    private Fonts() {
    }

    private static final Map<String, Typeface> cache = new HashMap<>();
    /** Kun til skærmbilleder i test: skrifterne læses fra en mappe i stedet for assets. */
    static String testDir;

    static Typeface get(Context c, String file) {
        Typeface t = cache.get(file);
        if (t != null) return t;
        try {
            t = testDir != null ? Typeface.createFromFile(testDir + file)
                    : Typeface.createFromAsset(c.getApplicationContext().getAssets(), "fonts/" + file);
        } catch (Exception e) {
            t = Typeface.DEFAULT;
        }
        cache.put(file, t);
        return t;
    }

    // Jarvis
    static Typeface rajdhani(Context c) { return get(c, "Rajdhani-SemiBold.ttf"); }
    static Typeface techMono(Context c) { return get(c, "ShareTechMono.ttf"); }
    // Spider-Man
    static Typeface bangers(Context c) { return get(c, "Bangers.ttf"); }
    static Typeface comic(Context c) { return get(c, "ComicNeue-Bold.ttf"); }
    // Batman
    static Typeface shoulders(Context c) { return get(c, "BigShoulders.ttf"); }
    static Typeface jetMono(Context c) { return get(c, "JetBrainsMono.ttf"); }
    // Superman
    static Typeface fraktur(Context c) { return get(c, "UnifrakturMaguntia.ttf"); }
    static Typeface playfairBlack(Context c) { return get(c, "PlayfairDisplay-Black.ttf"); }
    static Typeface playfair(Context c) { return get(c, "PlayfairDisplay.ttf"); }
    static Typeface oldStandard(Context c) { return get(c, "OldStandard.ttf"); }
    static Typeface oldStandardBold(Context c) { return get(c, "OldStandard-Bold.ttf"); }
    // Biograf
    static Typeface limelight(Context c) { return get(c, "Limelight.ttf"); }
    static Typeface bebas(Context c) { return get(c, "BebasNeue.ttf"); }
    // Jul
    static Typeface fraunces(Context c) { return get(c, "Fraunces-Black.ttf"); }
    static Typeface frauncesItalic(Context c) { return get(c, "Fraunces-Italic.ttf"); }
    static Typeface caveat(Context c) { return get(c, "Caveat-Bold.ttf"); }
    // Hygge
    static Typeface cormorant(Context c) { return get(c, "Cormorant.ttf"); }
    static Typeface cormorantItalic(Context c) { return get(c, "Cormorant-Italic.ttf"); }
    static Typeface reenie(Context c) { return get(c, "ReenieBeanie.ttf"); }
}
