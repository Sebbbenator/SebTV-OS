package dk.selveste.launcher;

import android.app.Activity;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import app.rive.runtime.kotlin.RiveAnimationView;
import app.rive.runtime.kotlin.core.Alignment;
import app.rive.runtime.kotlin.core.Artboard;
import app.rive.runtime.kotlin.core.Fit;
import app.rive.runtime.kotlin.core.Loop;
import app.rive.runtime.kotlin.core.RendererType;

/**
 * Temaets egne Rive animationer. Ligger der en fil med temaets navn (fx jul.riv, lagt op fra
 * telefonsiden), bruges dens artboards de faste steder: "Opstart" i stedet for temaets egen opstart.
 * Appen skriver klokken, datoen, hilsenen og navnet i tekstfelter med de navne og sætter inputs i
 * state machinen "Styring". Mangler noget i filen, springes det bare over.
 */
final class RiveTheme {

    private RiveTheme() {
    }

    static final String BOOT = "Opstart";
    static final String MACHINE = "Styring";

    /** Temaets Rive fil, hvis den findes. */
    static File file(Activity a) {
        File f = new File(RiveTest.dir(a), Skin.key + ".riv");
        return f.isFile() ? f : null;
    }

    /**
     * Spiller temaets Opstart artboard i hele skærmen. Svarer false, hvis temaet ikke har et (eller
     * filen ikke kan læses), så den indbyggede opstart bruges i stedet.
     */
    static boolean playBoot(Activity a, ViewGroup parent, Prefs prefs, String greeting) {
        if (!"tema".equals(BootIntro.style(prefs))) return false;
        File f = file(a);
        if (f == null) return false;
        try {
            RiveTest.init(a);
            byte[] bytes;
            try (InputStream in = new FileInputStream(f)) {
                bytes = Net.readAll(in);
            }
            app.rive.runtime.kotlin.core.File rf = new app.rive.runtime.kotlin.core.File(bytes, RendererType.Rive, null);
            if (!rf.getArtboardNames().contains(BOOT)) {
                rf.release();
                return false;
            }
            Artboard ab = rf.artboard(BOOT);
            boolean machine;
            try {
                ab.stateMachine(MACHINE);
                machine = true;
            } catch (Exception e) {
                machine = false;
            }
            // Længden tages fra den første tidslinje; højst 6 sekunder, så forsiden altid kommer frem
            float secs = 3.2f;
            try {
                if (ab.getAnimationCount() > 0) secs = ab.animation(0).getEffectiveDurationInSeconds();
            } catch (Exception ignored) {
            }
            secs = Math.max(1.5f, Math.min(6f, secs));

            RiveAnimationView v = new RiveAnimationView(a, null);
            v.setFocusable(false);
            v.setRiveFile(rf, BOOT, null, machine ? MACHINE : null, true, false, Fit.COVER, Alignment.CENTER, Loop.ONESHOT);
            fill(v, prefs, greeting, machine);
            parent.addView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            v.postDelayed(() -> v.animate().alpha(0f).setDuration(300).withEndAction(() -> parent.removeView(v)).start(),
                    (long) (secs * 1000));
            return true;
        } catch (Throwable t) {
            ErrorLog.note("Rive opstart", t);
            return false;
        }
    }

    /** Skriver tekst og inputs ind i animationen; det, filen ikke har, springes over. */
    static void fill(RiveAnimationView v, Prefs prefs, String greeting, boolean machine) {
        Calendar now = Calendar.getInstance();
        Locale da = new Locale("da", "DK");
        text(v, "klokken", new SimpleDateFormat("HH:mm", da).format(now.getTime()));
        text(v, "dato", new SimpleDateFormat("EEEE d. MMMM", da).format(now.getTime()));
        text(v, "hilsen", greeting == null ? "" : greeting);
        text(v, "navn", prefs.profileName());
        if (!machine) return;
        int h = now.get(Calendar.HOUR_OF_DAY);
        try {
            v.setNumberState(MACHINE, "time", h);
        } catch (Throwable ignored) {
        }
        try {
            v.setBooleanState(MACHINE, "nat", h >= 22 || h < 6);
        } catch (Throwable ignored) {
        }
    }

    private static void text(RiveAnimationView v, String run, String value) {
        try {
            v.setTextRunValue(run, value);
        } catch (Throwable ignored) {
        }
    }
}
