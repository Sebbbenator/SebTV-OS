package dk.selveste.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Baggrundsmønstre tegnet i appen, i tonens og accentens farver. */
final class Patterns {

    private Patterns() {
    }

    /** Ternet stof (gingham): bånd begge veje, mørkere hvor de krydser, og tynde tråde imellem. */
    static Bitmap plaid(int w, int h, int accent) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(b);
        boolean light = isLight(Ui.BG);
        c.drawColor(light ? Ui.BG : HudView.withAlpha(Ui.CARD_FOCUS, 1f));
        Paint band = new Paint();
        Paint thread = new Paint();
        int ink = light ? Ui.TEXT_DIM : Ui.TEXT_DIM;
        float unit = w / 40f;
        band.setColor(HudView.withAlpha(ink, light ? 0.28f : 0.18f));
        thread.setColor(HudView.withAlpha(accent, 0.35f));
        thread.setStrokeWidth(Math.max(1f, unit / 10f));
        for (float x = 0; x < w; x += unit * 2) c.drawRect(x, 0, x + unit, h, band);
        for (float y = 0; y < h; y += unit * 2) c.drawRect(0, y, w, y + unit, band);
        for (float x = unit * 1.5f; x < w; x += unit * 4) c.drawLine(x, 0, x, h, thread);
        for (float y = unit * 1.5f; y < h; y += unit * 4) c.drawLine(0, y, w, y, thread);
        // Fin vævning: små prikker giver stoffet struktur
        Paint weave = new Paint();
        weave.setColor(HudView.withAlpha(ink, 0.08f));
        float step = Math.max(3f, unit / 6f);
        for (float y = 0; y < h; y += step) {
            for (float x = ((int) (y / step) % 2) * step / 2; x < w; x += step) c.drawPoint(x, y, weave);
        }
        return b;
    }

    static boolean isLight(int color) {
        return (0.299 * android.graphics.Color.red(color) + 0.587 * android.graphics.Color.green(color)
                + 0.114 * android.graphics.Color.blue(color)) > 150;
    }
}
