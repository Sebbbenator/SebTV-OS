package dk.selveste.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.content.Context;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Bevægelse på forsiden, styret af effektniveauet (Indstillinger > Udseende, eller /rolig fra HA):
 * Fuld = fjedrende fokus, rækker der glider ind og cirkel ved temaskift.
 * Let = korte, rolige overgange (svage sticks).
 * Ingen = intet bevæger sig.
 * Automatisk vælger Let på sticks, Android selv kalder svage (lidt RAM), ellers Fuld.
 */
final class Motion {

    private Motion() {
    }

    static final String FULL = "fuld";
    static final String LIGHT = "let";
    static final String NONE = "ingen";

    private static final OvershootInterpolator SPRING = new OvershootInterpolator(2.2f);
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    static String level(Context context, Prefs prefs) {
        String l = prefs.effects();
        if (FULL.equals(l) || LIGHT.equals(l) || NONE.equals(l)) return l;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return am != null && am.isLowRamDevice() ? LIGHT : FULL;
    }

    /** Fokus på et kort: fjedrer lidt forbi og falder på plads ved Fuld. */
    static void focus(View v, boolean has, String level) {
        // Apple TV løfter ikonet tydeligere frem end de andre temaer
        float s = has ? (Ui.apple() ? 1.1f : 1.08f) : 1f;
        if (NONE.equals(level)) {
            v.animate().cancel();
            v.setScaleX(s);
            v.setScaleY(s);
            return;
        }
        if (Ui.apple()) {
            // tvOS glider roligt op uden at fjedre
            v.animate().scaleX(s).scaleY(s).setDuration(200).setInterpolator(EASE).start();
            return;
        }
        if (FULL.equals(level) && has) {
            v.animate().scaleX(s).scaleY(s).setDuration(280).setInterpolator(SPRING).start();
        } else {
            v.animate().scaleX(s).scaleY(s).setDuration(has ? 140 : 180).setInterpolator(EASE).start();
        }
    }

    /** Kortene i en række glider ind et ad gangen, første gang rækken vises. */
    static void stagger(ViewGroup row, String level) {
        if (!FULL.equals(level)) return;
        row.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                row.getViewTreeObserver().removeOnPreDrawListener(this);
                float shift = 24 * row.getResources().getDisplayMetrics().density;
                int n = Math.min(row.getChildCount(), 8);
                for (int i = 0; i < row.getChildCount(); i++) {
                    View c = row.getChildAt(i);
                    if (i >= n) break;
                    c.setAlpha(0f);
                    c.setTranslationX(shift);
                    c.animate().alpha(1f).translationX(0).setStartDelay(40L * i).setDuration(320).setInterpolator(EASE).start();
                }
                return true;
            }
        });
    }

    /** Ny forside efter temaskift: åbner sig som en cirkel fra midten (eller fokus). */
    static void reveal(View root, String level, int cx, int cy) {
        if (NONE.equals(level)) return;
        root.setVisibility(View.INVISIBLE);
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                int w = root.getWidth(), h = root.getHeight();
                int x = cx < 0 ? w / 2 : cx, y = cy < 0 ? h / 2 : cy;
                float r = (float) Math.hypot(Math.max(x, w - x), Math.max(y, h - y));
                root.setVisibility(View.VISIBLE);
                if (!root.isAttachedToWindow()) return true;
                Animator a = ViewAnimationUtils.createCircularReveal(root, x, y, 0, r);
                a.setDuration(LIGHT.equals(level) ? 350 : 650);
                a.setInterpolator(EASE);
                a.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        root.setVisibility(View.VISIBLE);
                    }
                });
                a.start();
                return true;
            }
        });
    }
}
