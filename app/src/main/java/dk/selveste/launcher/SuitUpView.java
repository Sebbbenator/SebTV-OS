package dk.selveste.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

/**
 * Konami sekvensen uden klip: to røde og guld hjelmplader lukker sig over skærmen, HUD'en inde i
 * hjelmen starter op, et blitz, og pladerne åbner sig igen til det nye tema. Tegnes af appen selv,
 * så den er skarp i alle opløsninger og ikke kræver filer.
 */
final class SuitUpView extends View {

    interface Callback {
        void run();
    }

    private static final long DURATION = 4600;
    /** Tidspunkterne i forløbet (0..1). */
    private static final float CLOSED = 0.18f, HUD_END = 0.68f, FLASH = 0.70f, OPEN_START = 0.74f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Callback onSwitch;
    private final Callback onDone;
    private final float d;
    private boolean switched;
    private float t;
    private Shader redLeft, redRight, gold;
    private int shaderW;

    SuitUpView(Context context, Callback onSwitch, Callback onDone) {
        super(context);
        this.onSwitch = onSwitch;
        this.onDone = onDone;
        d = context.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
    }

    void play(ViewGroup parent) {
        parent.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(DURATION);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(v -> {
            t = (float) v.getAnimatedValue();
            if (!switched && t >= FLASH) {
                switched = true;
                onSwitch.run();
            }
            invalidate();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!switched) {
                    switched = true;
                    onSwitch.run();
                }
                parent.removeView(SuitUpView.this);
                onDone.run();
            }
        });
        a.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        if (shaderW != w) makeShaders(w, h);

        // Hvor lukkede pladerne er (0 = åbne, 1 = lukkede): hurtigt ind med et lille slag, langsomt ud
        float closed;
        if (t < CLOSED) {
            float p = t / CLOSED;
            closed = p * p * (3 - 2 * p);
            closed = Math.min(1f, closed * 1.04f);
        } else if (t < OPEN_START) {
            closed = 1f;
        } else {
            float p = (t - OPEN_START) / (1f - OPEN_START);
            closed = 1f - p * p * (3 - 2 * p);
        }
        // Rystelse i det øjeblik pladerne smækker i
        float shake = 0;
        if (t > CLOSED && t < CLOSED + 0.04f) shake = (float) Math.sin((t - CLOSED) * 600) * 6 * d * (1 - (t - CLOSED) / 0.04f);
        c.save();
        c.translate(shake, 0);

        // Inde i hjelmen: HUD'en, kun mens pladerne er lukkede
        if (t >= CLOSED && t < FLASH + 0.02f) drawHud(c, w, h);
        else {
            drawPlate(c, w, h, closed, true);
            drawPlate(c, w, h, closed, false);
        }
        c.restore();

        // Blitzet, hvor temaet skifter bagved
        float f = Math.abs(t - FLASH);
        if (f < 0.05f) {
            fill.setShader(null);
            fill.setColor(Color.argb((int) (255 * (1 - f / 0.05f)), 255, 244, 214));
            c.drawRect(0, 0, w, h, fill);
        }
    }

    private void makeShaders(int w, int h) {
        shaderW = w;
        int[] red = {Color.parseColor("#4A0707"), Color.parseColor("#9E1414"), Color.parseColor("#D42A22"), Color.parseColor("#7A0D0D")};
        float[] stops = {0f, 0.45f, 0.62f, 1f};
        redLeft = new LinearGradient(0, 0, 0, h, red, stops, Shader.TileMode.CLAMP);
        redRight = new LinearGradient(0, h, 0, 0, red, stops, Shader.TileMode.CLAMP);
        gold = new LinearGradient(0, 0, 0, h, new int[]{Color.parseColor("#8C5A14"), Color.parseColor("#F2C35A"),
                Color.parseColor("#FFE7A3"), Color.parseColor("#A8701E")}, new float[]{0f, 0.4f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    /** En plade fra venstre eller højre; kanten mod midten har en guldliste og pladerne har samlinger. */
    private void drawPlate(Canvas c, int w, int h, float closed, boolean left) {
        if (closed <= 0.001f) return;
        float half = w / 2f;
        float edge = left ? half * closed : w - half * closed;
        float trim = 14 * d;
        fill.setShader(left ? redLeft : redRight);
        if (left) c.drawRect(0, 0, edge - trim, h, fill);
        else c.drawRect(edge + trim, 0, w, h, fill);
        fill.setShader(gold);
        if (left) c.drawRect(edge - trim, 0, edge, h, fill);
        else c.drawRect(edge, 0, edge + trim, h, fill);

        // Samlinger i pladen, der følger med kanten
        line.setShader(null);
        line.setStrokeWidth(2 * d);
        line.setColor(Color.argb(110, 20, 0, 0));
        float[] offs = {0.22f, 0.48f, 0.75f};
        for (float o : offs) {
            float x = left ? edge - half * o : edge + half * o;
            c.drawLine(x, 0, x, h, line);
        }
        float[] rows = {0.28f, 0.66f};
        for (float r : rows) {
            float y = h * r;
            if (left) c.drawLine(Math.max(0, edge - half * 0.75f), y, edge - trim, y, line);
            else c.drawLine(edge + trim, y, Math.min(w, edge + half * 0.75f), y, line);
        }
        // Lys kant på den gyldne liste
        line.setColor(Color.argb(160, 255, 240, 200));
        line.setStrokeWidth(1.5f * d);
        float lx = left ? edge - trim + 2 * d : edge + 2 * d;
        c.drawLine(lx, 0, lx, h, line);
    }

    /** Hjelmens indre: mørkt med HUD i blåt, der tænder og tæller op. */
    private void drawHud(Canvas c, int w, int h) {
        float p = (t - CLOSED) / (HUD_END - CLOSED);
        if (p > 1) p = 1;
        fill.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.7f,
                Color.parseColor("#0B1C2A"), Color.parseColor("#020508"), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);

        int hud = Color.parseColor("#7FD8FF");
        float on = Math.min(1f, p * 4);
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) * 0.22f;

        // Ringe, der tegnes op og drejer
        line.setShader(null);
        line.setColor(withAlpha(hud, 0.85f * on));
        line.setStrokeWidth(3 * d);
        oval.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(oval, -90 + p * 220, 300 * Math.min(1f, p * 2), false, line);
        line.setStrokeWidth(1.5f * d);
        line.setColor(withAlpha(hud, 0.5f * on));
        float r2 = r * 1.25f;
        oval.set(cx - r2, cy - r2, cx + r2, cy + r2);
        for (int i = 0; i < 12; i++) c.drawArc(oval, i * 30 - p * 140, 18, false, line);
        float r3 = r * 0.6f;
        oval.set(cx - r3, cy - r3, cx + r3, cy + r3);
        line.setStrokeWidth(2 * d);
        c.drawArc(oval, 90 - p * 300, 220, false, line);

        // Sigtekorn
        line.setColor(withAlpha(hud, 0.9f * on));
        float k = 18 * d;
        c.drawLine(cx - k, cy, cx - k / 3, cy, line);
        c.drawLine(cx + k / 3, cy, cx + k, cy, line);
        c.drawLine(cx, cy - k, cx, cy - k / 3, line);
        c.drawLine(cx, cy + k / 3, cx, cy + k, line);

        // Scanlinje, der løber ned over synsfeltet
        float sy = h * ((p * 1.6f) % 1f);
        line.setColor(withAlpha(hud, 0.18f * on));
        line.setStrokeWidth(2 * d);
        c.drawLine(0, sy, w, sy, line);

        // Tekst i hjørnerne, der kommer frem efterhånden
        text.setColor(withAlpha(hud, on));
        text.setTextSize(15 * d);
        float m = 56 * d;
        int power = Math.min(100, (int) (p * 125));
        String[] left = {"MARK LXXXV", "POWER " + power + "%", "REPULSORS " + (p > 0.45f ? "ONLINE" : "CHARGING"), "FLIGHT " + (p > 0.7f ? "READY" : "CALIBRATING")};
        for (int i = 0; i < left.length; i++) {
            if (p < i * 0.15f) break;
            c.drawText(left[i], m, h * 0.3f + i * 26 * d, text);
        }
        text.setTextAlign(Paint.Align.RIGHT);
        String[] right = {"ARC REACTOR", "OUTPUT " + String.format(java.util.Locale.ROOT, "%.1f", p * 3.0f) + " GJ/s", "HUD SYNC OK"};
        for (int i = 0; i < right.length; i++) {
            if (p < 0.1f + i * 0.18f) break;
            c.drawText(right[i], w - m, h * 0.3f + i * 26 * d, text);
        }
        text.setTextAlign(Paint.Align.LEFT);

        // Effektbjælke under sigtekornet
        float bw = r * 1.6f, by = cy + r * 1.55f;
        line.setColor(withAlpha(hud, 0.35f * on));
        line.setStrokeWidth(6 * d);
        c.drawLine(cx - bw / 2, by, cx + bw / 2, by, line);
        line.setColor(withAlpha(hud, on));
        c.drawLine(cx - bw / 2, by, cx - bw / 2 + bw * power / 100f, by, line);
    }

    private static int withAlpha(int color, float a) {
        return Color.argb((int) (255 * Math.max(0, Math.min(1, a))), Color.red(color), Color.green(color), Color.blue(color));
    }
}
