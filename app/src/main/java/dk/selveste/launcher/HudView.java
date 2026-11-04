package dk.selveste.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

/** Jarvis opstart: drejende HUD ringe, "SYSTEMS ONLINE" og en hilsen, så toner den ud. */
final class HudView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final String greeting;
    private final int color;
    /** Overskriften i midten, fx J.A.R.V.I.S.; tom i den bløde udgave uden assistent. */
    private String title = "J.A.R.V.I.S.";
    private String subtitle = "SYSTEMS ONLINE";
    /** Blød opstart til Hjem udgaven: rolige ringe der breder sig, et hjerte og en hilsen. */
    private boolean soft;
    private float t;
    private long duration = 3200;

    HudView(Context context, String greeting, int color) {
        super(context);
        this.greeting = greeting;
        this.color = color;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        setBackgroundColor(Ui.bg(0.95f));
    }

    HudView titled(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
        return this;
    }

    HudView lasting(long ms) {
        duration = ms;
        return this;
    }

    HudView soft() {
        soft = true;
        text.setTypeface(Ui.display);
        return this;
    }

    /** Opstarten der passer til udgaven og assistentens navn. */
    static HudView forPrefs(Context c, Prefs prefs, String greeting, int color) {
        HudView h = new HudView(c, greeting, color);
        if (prefs.isJarvis()) {
            String name = prefs.assistantName().toUpperCase(java.util.Locale.ROOT);
            StringBuilder spaced = new StringBuilder();
            for (char ch : name.toCharArray()) spaced.append(ch).append('.');
            return h.titled(name.isEmpty() ? "" : spaced.toString(), "SYSTEMS ONLINE");
        }
        return h.soft().titled(prefs.assistantName(), "Velkommen hjem");
    }

    void play(ViewGroup parent) {
        parent.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(duration);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(v -> {
            t = (float) v.getAnimatedValue();
            if (t > 0.82f) setAlpha(1f - (t - 0.82f) / 0.18f);
            invalidate();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                parent.removeView(HudView.this);
            }
        });
        a.start();
    }

    private void drawSoft(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f - getHeight() * 0.06f;
        float base = Math.min(getWidth(), getHeight()) * 0.14f;
        float d = getResources().getDisplayMetrics().density;
        ring.setStrokeWidth(1.5f * d);
        // Tre ringe der breder sig og toner ud, som ringe i vand
        for (int i = 0; i < 3; i++) {
            float p = (t * 1.6f + i / 3f) % 1f;
            float radius = base * (0.6f + p * 1.6f);
            ring.setColor(withAlpha(color, 0.5f * (1f - p) * Math.min(1f, t * 4f)));
            c.drawCircle(cx, cy, radius, ring);
        }
        text.setColor(withAlpha(color, Math.min(1f, t * 3f)));
        text.setLetterSpacing(0f);
        text.setTextSize(44 * d * (0.9f + 0.1f * (float) Math.sin(t * Math.PI * 3)));
        c.drawText("\u2665", cx, cy + 15 * d, text);
        if (t > 0.3f) {
            text.setLetterSpacing(0.15f);
            text.setTextSize(15 * d);
            text.setColor(withAlpha(Ui.TEXT_DIM, Math.min(1f, (t - 0.3f) * 3f)));
            c.drawText(subtitle, cx, cy + base * 2.4f, text);
        }
        if (t > 0.45f) {
            text.setLetterSpacing(0.02f);
            text.setTextSize(30 * d);
            text.setColor(withAlpha(Ui.TEXT, Math.min(1f, (t - 0.45f) * 3f)));
            c.drawText(greeting, cx, cy + base * 2.4f + 44 * d, text);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (soft) {
            drawSoft(c);
            return;
        }
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f - getHeight() * 0.05f;
        float base = Math.min(getWidth(), getHeight()) * 0.16f;
        float grow = Math.min(1f, t * 2.5f);

        float[][] rings = {
                {1.00f, 260f, 1f, 3f},
                {1.25f, 200f, -1.6f, 2f},
                {1.55f, 120f, 0.8f, 6f},
                {1.85f, 300f, -0.5f, 1.5f},
        };
        for (int i = 0; i < rings.length; i++) {
            float[] r = rings[i];
            float radius = base * r[0] * grow;
            oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
            ring.setStrokeWidth(r[3] * getResources().getDisplayMetrics().density);
            ring.setColor(withAlpha(color, 0.35f + 0.15f * i));
            float start = t * 720f * r[2] + i * 47f;
            canvas(c, start, r[1] * grow);
            ring.setColor(withAlpha(color, 0.12f));
            c.drawArc(oval, start + r[1] * grow + 12f, 360f - r[1] * grow - 24f, false, ring);
        }

        float d = getResources().getDisplayMetrics().density;
        text.setColor(withAlpha(color, Math.min(1f, t * 3f)));
        text.setTextSize(26 * d);
        text.setLetterSpacing(0.4f);
        c.drawText(title, cx, cy + 9 * d, text);

        if (t > 0.35f) {
            text.setLetterSpacing(0.3f);
            text.setTextSize(14 * d);
            text.setColor(withAlpha(color, Math.min(1f, (t - 0.35f) * 4f)));
            c.drawText(typed(subtitle, (t - 0.35f) * 3f), cx, cy + base * 2.3f, text);
        }
        if (t > 0.55f) {
            text.setLetterSpacing(0.05f);
            text.setTextSize(24 * d);
            text.setColor(withAlpha(Ui.TEXT, Math.min(1f, (t - 0.55f) * 4f)));
            c.drawText(greeting, cx, cy + base * 2.3f + 40 * d, text);
        }
    }

    private void canvas(Canvas c, float start, float sweep) {
        c.drawArc(oval, start, sweep, false, ring);
    }

    private static String typed(String s, float p) {
        int n = Math.max(0, Math.min(s.length(), Math.round(s.length() * p)));
        return s.substring(0, n);
    }

    static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255), Color.red(color), Color.green(color), Color.blue(color));
    }
}
