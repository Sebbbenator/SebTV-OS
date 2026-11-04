package dk.selveste.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * "Hvem ser?" når tv'et tændes: én flise pr. profil med billede eller forbogstav. Valget skifter tema,
 * indstillinger og Jellyfin login. Tilbage beholder den, der er i brug.
 */
final class WhoPicker {

    private WhoPicker() {
    }

    interface Chosen {
        /** Navnet, eller null hvis man trykkede Tilbage. */
        void chosen(String name);
    }

    static View show(Activity a, ViewGroup parent, Prefs prefs, int accent, Chosen chosen) {
        List<String> names = Profiles.names(prefs);
        String active = Profiles.active(prefs);

        FrameLayout scrim = new FrameLayout(a);
        scrim.setBackgroundColor(Color.argb(242, 8, 9, 12));
        scrim.setClickable(true);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = Ui.text(a, "Hvem ser?", 40, Color.WHITE);
        title.setTypeface(Ui.display);
        title.setGravity(Gravity.CENTER);
        col.addView(title);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(a, 36);
        col.addView(row, rowLp);
        TextView hint = Ui.text(a, "Tilbage beholder " + (active.isEmpty() ? "den nuværende" : active), 15, Ui.TEXT_DIM);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = Ui.dp(a, 28);
        col.addView(hint, hintLp);
        scrim.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        boolean[] done = {false};
        Runnable close = () -> {
            scrim.animate().alpha(0f).setDuration(220).withEndAction(() -> parent.removeView(scrim)).start();
        };
        View first = null, current = null;
        int size = Ui.dp(a, 150);
        for (String name : names) {
            LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout face = new FrameLayout(a);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(a, 22));
            bg.setColor(shade(accent, names.indexOf(name)));
            face.setBackground(bg);
            face.setClipToOutline(true);
            java.io.File picFile = Profiles.picture(a, name);
            Bitmap pic = picFile.exists() ? Backgrounds.decode(picFile, 320) : null;
            if (pic != null) {
                ImageView img = new ImageView(a);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                img.setImageBitmap(pic);
                face.addView(img, new FrameLayout.LayoutParams(size, size));
            } else {
                TextView letter = Ui.text(a, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT), 64, Color.WHITE);
                letter.setTypeface(Ui.display);
                letter.setGravity(Gravity.CENTER);
                face.addView(letter, new FrameLayout.LayoutParams(size, size));
            }
            cell.addView(face, new LinearLayout.LayoutParams(size, size));
            TextView label = Ui.text(a, name, 20, Ui.TEXT_DIM);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(size + Ui.dp(a, 30), ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = Ui.dp(a, 14);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(label, labelLp);
            cell.setFocusable(true);
            cell.setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
            cell.setOnFocusChangeListener((v, has) -> {
                face.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(160).start();
                GradientDrawable ring = new GradientDrawable();
                ring.setCornerRadius(Ui.dp(a, 22));
                ring.setStroke(has ? Ui.dp(a, 4) : 0, Color.WHITE);
                face.setForeground(has ? ring : null);
                label.setTextColor(has ? Color.WHITE : Ui.TEXT_DIM);
            });
            cell.setOnClickListener(v -> {
                if (done[0]) return;
                done[0] = true;
                close.run();
                chosen.chosen(name);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = lp.rightMargin = Ui.dp(a, 18);
            row.addView(cell, lp);
            if (first == null) first = cell;
            if (name.equals(active)) current = cell;
        }
        scrim.setFocusable(false);
        scrim.setOnKeyListener((v, code, e) -> false);
        // Tilbage: behold den nuværende
        for (int i = 0; i < row.getChildCount(); i++) {
            row.getChildAt(i).setOnKeyListener((v, code, e) -> {
                if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP && !done[0]) {
                    done[0] = true;
                    close.run();
                    chosen.chosen(null);
                    return true;
                }
                return code == KeyEvent.KEYCODE_BACK;
            });
        }
        parent.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(220).start();
        View focus = current != null ? current : first;
        if (focus != null) focus.post(focus::requestFocus);
        return scrim;
    }

    /** Hver profil sin nuance af temaets farve, så fliserne kan skelnes uden billede. */
    private static int shade(int accent, int i) {
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[0] = (hsv[0] + i * 47) % 360;
        hsv[1] = Math.max(0.45f, Math.min(0.8f, hsv[1]));
        hsv[2] = 0.62f;
        return Color.HSVToColor(hsv);
    }
}
