package dk.selveste.launcher;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import app.rive.runtime.kotlin.RiveAnimationView;
import app.rive.runtime.kotlin.core.Alignment;
import app.rive.runtime.kotlin.core.Artboard;
import app.rive.runtime.kotlin.core.Direction;
import app.rive.runtime.kotlin.core.Fit;
import app.rive.runtime.kotlin.core.Loop;
import app.rive.runtime.kotlin.core.RendererType;

/**
 * Temaets egne Rive animationer. Ligger der en fil med temaets navn (fx jul.riv, lagt op fra
 * telefonsiden), bruges dens artboards de faste steder:
 * <ul>
 * <li>Opstart: i stedet for temaets egen opstart</li>
 * <li>Baggrund: hele skærmen mellem fotoet og forsidens cards (i stedet for temaets stemning)</li>
 * <li>Ur: i stedet for temaets scene (de otte temaer) eller uret (de andre)</li>
 * <li>Fokus: oven på det card, der har fokus</li>
 * </ul>
 * Appen skriver klokken, datoen, hilsenen, navnet, vejret og dagens mærkedag i tekstfelter med de
 * navne og sætter inputs i state machinen "Styring". Mangler noget i filen, springes det bare over.
 */
final class RiveTheme {

    private RiveTheme() {
    }

    static final String BOOT = "Opstart", BACKGROUND = "Baggrund", CLOCK = "Ur", FOCUS = "Fokus";
    static final String MACHINE = "Styring";

    /** Temaets Rive fil, hvis den findes. */
    static File file(Context c) {
        File f = new File(RiveTest.dir(c), Skin.key + ".riv");
        return f.isFile() ? f : null;
    }

    /** Ændrer sig, når filen bliver lagt op igen eller temaet skiftes. */
    static String signature(Context c) {
        File f = file(c);
        return Skin.key + "|" + (f == null ? 0 : f.lastModified());
    }

    private static String loadedSig;
    private static app.rive.runtime.kotlin.core.File loaded;

    /** Den indlæste fil, genbrugt så længe den ikke er ændret (Rive filer ligger i native hukommelse). */
    static synchronized app.rive.runtime.kotlin.core.File load(Context c) throws Exception {
        File f = file(c);
        if (f == null) return null;
        String sig = signature(c);
        if (sig.equals(loadedSig) && loaded != null) return loaded;
        RiveTest.init(c);
        byte[] bytes;
        try (InputStream in = new FileInputStream(f)) {
            bytes = Net.readAll(in);
        }
        loaded = new app.rive.runtime.kotlin.core.File(bytes, RendererType.Rive, null);
        loadedSig = sig;
        return loaded;
    }

    private static boolean hasMachine(app.rive.runtime.kotlin.core.File rf, String artboard) {
        try {
            Artboard ab = rf.artboard(artboard);
            ab.stateMachine(MACHINE);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static RiveAnimationView view(Context c, app.rive.runtime.kotlin.core.File rf, String artboard, Fit fit, Loop loop) {
        RiveAnimationView v = new RiveAnimationView(c, null);
        v.setFocusable(false);
        v.setRiveFile(rf, artboard, null, hasMachine(rf, artboard) ? MACHINE : null, true, false, fit, Alignment.CENTER, loop);
        v.setTag(hasMachine(rf, artboard));
        return v;
    }

    /**
     * Spiller temaets Opstart artboard i hele skærmen. Svarer false, hvis temaet ikke har et (eller
     * filen ikke kan læses), så den indbyggede opstart bruges i stedet.
     */
    static boolean playBoot(Activity a, ViewGroup parent, Prefs prefs, String greeting) {
        if (!"tema".equals(BootIntro.style(prefs))) return false;
        try {
            app.rive.runtime.kotlin.core.File rf = load(a);
            if (rf == null || !rf.getArtboardNames().contains(BOOT)) return false;
            // Længden tages fra den første tidslinje; højst 6 sekunder, så forsiden altid kommer frem
            float secs = 3.2f;
            try {
                Artboard ab = rf.artboard(BOOT);
                if (ab.getAnimationCount() > 0) secs = ab.animation(0).getEffectiveDurationInSeconds();
            } catch (Exception ignored) {
            }
            secs = Math.max(1.5f, Math.min(6f, secs));
            RiveAnimationView v = view(a, rf, BOOT, Fit.COVER, Loop.ONESHOT);
            fill(v, prefs, greeting);
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
    static void fill(RiveAnimationView v, Prefs prefs, String greeting) {
        Calendar now = Calendar.getInstance();
        Locale da = new Locale("da", "DK");
        text(v, "klokken", new SimpleDateFormat("HH:mm", da).format(now.getTime()));
        text(v, "dato", new SimpleDateFormat("EEEE d. MMMM", da).format(now.getTime()));
        text(v, "hilsen", greeting == null ? "" : greeting);
        text(v, "navn", prefs.profileName());
        text(v, "maerkedag", Days.today(prefs));
        if (!Boolean.TRUE.equals(v.getTag())) return;
        int h = now.get(Calendar.HOUR_OF_DAY);
        number(v, "time", h);
        bool(v, "nat", h >= 22 || h < 6);
    }

    private static void text(RiveAnimationView v, String run, String value) {
        try {
            v.setTextRunValue(run, value == null ? "" : value);
        } catch (Throwable ignored) {
        }
    }

    private static void number(RiveAnimationView v, String input, float value) {
        try {
            v.setNumberState(MACHINE, input, value);
        } catch (Throwable ignored) {
        }
    }

    private static void bool(RiveAnimationView v, String input, boolean value) {
        try {
            v.setBooleanState(MACHINE, input, value);
        } catch (Throwable ignored) {
        }
    }

    private static void fire(RiveAnimationView v, String input) {
        try {
            v.fireState(MACHINE, input);
        } catch (Throwable ignored) {
        }
    }

    /** Ting forsiden giver videre, som de levende animationer skal bruge. */
    interface Host {
        String greeting();

        String weather();
    }

    /**
     * Baggrund, Ur og Fokus på forsiden. Laves forfra, når filen eller temaet skifter; release()
     * fjerner animationerne og viser temaets egne ting igen.
     */
    static final class Live {
        private final Activity a;
        private final Prefs prefs;
        private final Host host;
        private final List<RiveAnimationView> all = new ArrayList<>();
        private final List<View> hidden = new ArrayList<>();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private RiveAnimationView clock, focus;
        private View[] clockViews = new View[0];
        private ViewTreeObserver.OnGlobalFocusChangeListener focusListener;
        private ViewTreeObserver.OnPreDrawListener follow;
        private FrameLayout root;
        private View focused;
        private final int[] at = new int[2], rootAt = new int[2];
        final String signature;

        private Live(Activity a, Prefs prefs, Host host, String signature) {
            this.a = a;
            this.prefs = prefs;
            this.host = host;
            this.signature = signature;
        }

        /** Null, hvis temaet ikke har en fil med Baggrund, Ur eller Fokus. */
        static Live create(Activity a, Prefs prefs, Host host) {
            try {
                app.rive.runtime.kotlin.core.File rf = load(a);
                if (rf == null) return null;
                List<String> names = rf.getArtboardNames();
                if (!names.contains(BACKGROUND) && !names.contains(CLOCK) && !names.contains(FOCUS)) return null;
                return new Live(a, prefs, host, signature(a));
            } catch (Throwable t) {
                ErrorLog.note("Rive tema", t);
                return null;
            }
        }

        private boolean has(String artboard) {
            try {
                return load(a).getArtboardNames().contains(artboard);
            } catch (Throwable t) {
                return false;
            }
        }

        private RiveAnimationView make(String artboard, Fit fit) throws Exception {
            RiveAnimationView v = view(a, load(a), artboard, fit, Loop.AUTO);
            all.add(v);
            return v;
        }

        /** Baggrund: hele skærmen på pladsen index i root (over fotoet, under rækkerne). */
        void background(FrameLayout root, int index, View replaces) {
            if (!has(BACKGROUND)) return;
            try {
                RiveAnimationView v = make(BACKGROUND, Fit.COVER);
                root.addView(v, Math.min(index, root.getChildCount()), new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                hide(replaces);
            } catch (Throwable t) {
                ErrorLog.note("Rive baggrund", t);
            }
        }

        /** Ur i de otte temaer: animationen står på scenens plads, og scenen skjules. */
        void scene(View stage) {
            if (!has(CLOCK) || stage == null || !(stage.getParent() instanceof ViewGroup)) return;
            try {
                ViewGroup parent = (ViewGroup) stage.getParent();
                ViewGroup.LayoutParams lp = stage.getLayoutParams();
                ViewGroup.LayoutParams copy = lp instanceof LinearLayout.LayoutParams
                        ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams) lp) : new ViewGroup.LayoutParams(lp);
                clock = make(CLOCK, Fit.CONTAIN);
                parent.addView(clock, parent.indexOfChild(stage), copy);
                hide(stage);
            } catch (Throwable t) {
                ErrorLog.note("Rive ur", t);
            }
        }

        /** Ur i de andre temaer: øverst i urets kasse; temaets ure skjules (også efter applyLook). */
        void clock(ViewGroup box, int widthPx, int heightPx, View... replaces) {
            if (!has(CLOCK) || box == null) return;
            try {
                clock = make(CLOCK, Fit.CONTAIN);
                box.addView(clock, 0, new LinearLayout.LayoutParams(widthPx, heightPx));
                clockViews = replaces;
                hideClocks();
            } catch (Throwable t) {
                ErrorLog.note("Rive ur", t);
            }
        }

        /** Temaet uden plads til et ur (Apple): øverst til venstre oven på forsiden. */
        void clockCorner(FrameLayout root, int widthPx, int heightPx, int marginPx) {
            if (!has(CLOCK)) return;
            try {
                clock = make(CLOCK, Fit.CONTAIN);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(widthPx, heightPx, Gravity.TOP | Gravity.START);
                lp.setMargins(marginPx, marginPx / 2, 0, 0);
                root.addView(clock, lp);
            } catch (Throwable t) {
                ErrorLog.note("Rive ur", t);
            }
        }

        /** Kaldes efter applyLook, som ellers viser temaets ur igen. */
        void hideClocks() {
            for (View v : clockViews) if (v != null) v.setVisibility(View.GONE);
        }

        /**
         * Fokus: én animation, der følger det card, der har fokus. Den er 20 % større end cardet, så
         * glød og kanter kan gå ud over det; cardet fylder midten (400 x 225 af 480 x 270).
         */
        void focus(FrameLayout root, ViewGroup rows, BooleanSupplier active) {
            if (!has(FOCUS)) return;
            try {
                this.root = root;
                focus = make(FOCUS, Fit.FILL);
                focus.setPivotX(0);
                focus.setPivotY(0);
                focus.setVisibility(View.INVISIBLE);
                root.addView(focus, new FrameLayout.LayoutParams(1, 1, Gravity.TOP | Gravity.START));
                focusListener = (old, now) -> {
                    focused = now != null && isIn(now, rows) ? now : null;
                    if (focused == null) return;
                    if (Boolean.TRUE.equals(focus.getTag())) fire(focus, "fokus");
                    else {
                        focus.reset();
                        focus.play(Loop.AUTO, Direction.AUTO, true);
                    }
                };
                root.getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
                // Følger cardet, mens rækkerne ruller og cardet vokser; flytter kun, når noget ændrer sig
                follow = () -> {
                    place(active.getAsBoolean());
                    return true;
                };
                root.getViewTreeObserver().addOnPreDrawListener(follow);
            } catch (Throwable t) {
                ErrorLog.note("Rive fokus", t);
            }
        }

        private static boolean isIn(View v, ViewGroup group) {
            for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
                if (p == group) return true;
            }
            return false;
        }

        private void place(boolean active) {
            View f = focused;
            boolean show = active && f != null && f.isShown() && f.getWidth() > 0;
            int vis = show ? View.VISIBLE : View.INVISIBLE;
            if (focus.getVisibility() != vis) focus.setVisibility(vis);
            if (!show) return;
            f.getLocationInWindow(at);
            root.getLocationInWindow(rootAt);
            float w = f.getWidth() * f.getScaleX(), h = f.getHeight() * f.getScaleY();
            // getLocationInWindow regner skaleringen med; cardet skaleres om sin midte
            float x = at[0] - rootAt[0] - w * 0.1f, y = at[1] - rootAt[1] - h * 0.1f;
            int lw = Math.round(w * 1.2f), lh = Math.round(h * 1.2f);
            ViewGroup.LayoutParams lp = focus.getLayoutParams();
            if (lp.width != lw || lp.height != lh) {
                lp.width = lw;
                lp.height = lh;
                focus.setLayoutParams(lp);
            }
            if (focus.getTranslationX() != x) focus.setTranslationX(x);
            if (focus.getTranslationY() != y) focus.setTranslationY(y);
        }

        private final Runnable minute = new Runnable() {
            @Override
            public void run() {
                refresh();
                handler.postDelayed(this, 20_000);
            }
        };

        /** Tekst og inputs igen (klokken, vejret, hilsenen). */
        void refresh() {
            String g = host.greeting();
            String w = host.weather();
            for (RiveAnimationView v : all) {
                fill(v, prefs, g);
                text(v, "vejr", w);
            }
        }

        void resume() {
            refresh();
            handler.removeCallbacks(minute);
            handler.postDelayed(minute, 20_000);
            for (RiveAnimationView v : all) {
                try {
                    v.play(Loop.AUTO, Direction.AUTO, true);
                } catch (Throwable ignored) {
                }
            }
        }

        void pause() {
            handler.removeCallbacks(minute);
            for (RiveAnimationView v : all) {
                try {
                    v.pause();
                } catch (Throwable ignored) {
                }
            }
        }

        private final Runnable stillAgain = () -> {
            for (RiveAnimationView v : all) bool(v, "navigerer", false);
        };

        /** Fjernbetjeningen bruges: navigerer er sand, til den har været i ro i et sekund. */
        void key(boolean ok) {
            for (RiveAnimationView v : all) {
                if (!Boolean.TRUE.equals(v.getTag())) continue;
                if (ok) fire(v, "ok");
                bool(v, "navigerer", true);
            }
            handler.removeCallbacks(stillAgain);
            handler.postDelayed(stillAgain, 1000);
        }

        void calm(boolean on) {
            for (RiveAnimationView v : all) bool(v, "rolig", on);
        }

        private void hide(View v) {
            if (v == null || v.getVisibility() != View.VISIBLE) return;
            v.setVisibility(View.GONE);
            hidden.add(v);
        }

        /** Fjerner animationerne og viser temaets egne ting igen. */
        void release() {
            pause();
            handler.removeCallbacksAndMessages(null);
            if (root != null) {
                if (focusListener != null) root.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
                if (follow != null) root.getViewTreeObserver().removeOnPreDrawListener(follow);
            }
            for (RiveAnimationView v : all) {
                if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            }
            all.clear();
            for (View v : hidden) v.setVisibility(View.VISIBLE);
            hidden.clear();
            clockViews = new View[0];
        }
    }
}
