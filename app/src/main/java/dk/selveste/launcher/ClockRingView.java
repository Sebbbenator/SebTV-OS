package dk.selveste.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Calendar;

/** Jarvis ur: ring med sekundbue og streger, tiden i midten. Tegnes om én gang i sekundet. */
final class ClockRingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ticks = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int color = Ui.CYAN;
    /** Analogt ur med visere (efterårstemaet) i stedet for Jarvis ringen. */
    private boolean analog;
    private final Paint hands = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);

    ClockRingView(Context context) {
        super(context);
        track.setStyle(Paint.Style.STROKE);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        ticks.setStyle(Paint.Style.STROKE);
        time.setTextAlign(Paint.Align.CENTER);
        time.setColor(Ui.TEXT);
    }

    void setAnalog(boolean a) {
        if (a == analog) return;
        analog = a;
        invalidate();
    }

    private void drawAnalog(Canvas c, float cx, float cy, float r, Calendar now) {
        float d = getResources().getDisplayMetrics().density;
        face.setStyle(Paint.Style.FILL);
        face.setColor(HudView.withAlpha(Ui.CARD_FOCUS, 0.85f));
        c.drawCircle(cx, cy, r, face);
        face.setStyle(Paint.Style.STROKE);
        face.setStrokeWidth(3 * d);
        face.setColor(color);
        c.drawCircle(cx, cy, r, face);
        ticks.setStrokeWidth(2 * d);
        ticks.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 12; i++) {
            ticks.setColor(HudView.withAlpha(Ui.TEXT, i % 3 == 0 ? 0.9f : 0.45f));
            double a = Math.toRadians(i * 30 - 90);
            float r1 = r * 0.86f, r2 = r * (i % 3 == 0 ? 0.72f : 0.78f);
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, ticks);
        }
        float min = now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f;
        float hour = now.get(Calendar.HOUR) + min / 60f;
        hands.setStrokeCap(Paint.Cap.ROUND);
        hand(c, cx, cy, hour * 30f, r * 0.45f, 5 * d, Ui.TEXT);
        hand(c, cx, cy, min * 6f, r * 0.68f, 3.5f * d, Ui.TEXT);
        hand(c, cx, cy, now.get(Calendar.SECOND) * 6f, r * 0.74f, 1.5f * d, color);
        face.setStyle(Paint.Style.FILL);
        face.setColor(color);
        c.drawCircle(cx, cy, 4 * d, face);
    }

    private void hand(Canvas c, float cx, float cy, float deg, float len, float width, int col) {
        double a = Math.toRadians(deg - 90);
        hands.setStrokeWidth(width);
        hands.setColor(col);
        c.drawLine(cx, cy, cx + (float) Math.cos(a) * len, cy + (float) Math.sin(a) * len, hands);
    }

    void setColor(int c) {
        if (c == color) return;
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float stroke = size * 0.035f;
        float r = size / 2f - stroke * 2;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        Calendar now = Calendar.getInstance();
        int sec = now.get(Calendar.SECOND);
        if (analog) {
            drawAnalog(c, cx, cy, size / 2f - stroke, now);
            return;
        }

        track.setStrokeWidth(stroke);
        track.setColor(HudView.withAlpha(color, 0.18f));
        c.drawOval(oval, track);

        arc.setStrokeWidth(stroke);
        arc.setColor(color);
        arc.setShadowLayer(stroke * 1.5f, 0, 0, HudView.withAlpha(color, 0.7f));
        c.drawArc(oval, -90, Math.max(2f, sec * 6f), false, arc);

        // Streger for hvert 5. sekund lige inden for ringen
        ticks.setStrokeWidth(stroke * 0.4f);
        for (int i = 0; i < 12; i++) {
            ticks.setColor(HudView.withAlpha(color, i * 5 <= sec ? 0.9f : 0.3f));
            double a = Math.toRadians(i * 30 - 90);
            float r1 = r - stroke * 2.2f, r2 = r - stroke * (i % 3 == 0 ? 4.2f : 3.2f);
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, ticks);
        }

        time.setTypeface(Ui.display);
        time.setTextSize(size * 0.22f);
        String hm = String.format(java.util.Locale.ROOT, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        c.drawText(hm, cx, cy - (time.descent() + time.ascent()) / 2f, time);
    }
}
