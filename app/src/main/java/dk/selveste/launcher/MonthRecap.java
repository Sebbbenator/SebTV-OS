package dk.selveste.launcher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Månedens tal: hvad profilen så på Jellyfin i sidste måned. Vises én gang, første gang tv'et tændes
 * i en ny måned (de første syv dage), og kan vises igen fra telefonsiden.
 */
final class MonthRecap {

    private MonthRecap() {
    }

    static final String SHOWN = "recap_shown";
    private static final int DIM = 0xB3FFFFFF;
    static final String[] MONTHS = {"januar", "februar", "marts", "april", "maj", "juni", "juli", "august",
            "september", "oktober", "november", "december"};

    static final class Stats {
        int year, month;
        long minutes;
        int episodes, movies;
        String topSeries = "";
        int topCount;
        String lastMovie = "";

        boolean empty() {
            return episodes + movies == 0;
        }

        String monthName() {
            return MONTHS[month];
        }

        JSONObject json() throws Exception {
            return new JSONObject().put("month", monthName()).put("year", year).put("hours", hours()).put("minutes", minutes)
                    .put("episodes", episodes).put("movies", movies).put("top", topSeries).put("topCount", topCount)
                    .put("lastMovie", lastMovie);
        }

        /** Hele timer, men mindst 1 når der er set noget. */
        long hours() {
            return minutes == 0 ? 0 : Math.max(1, Math.round(minutes / 60.0));
        }
    }

    /** Måneden før den, today ligger i. */
    static int[] lastMonth(Calendar today) {
        int y = today.get(Calendar.YEAR), m = today.get(Calendar.MONTH) - 1;
        if (m < 0) {
            m = 11;
            y--;
        }
        return new int[]{y, m};
    }

    /** Regner tallene ud fra Jellyfins liste over sete film og afsnit (nyeste først). Ren logik, kan testes. */
    static Stats compute(JSONArray items, int year, int month) {
        Stats s = new Stats();
        s.year = year;
        s.month = month;
        Map<String, Integer> series = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            JSONObject ud = o.optJSONObject("UserData");
            String when = ud == null ? "" : ud.optString("LastPlayedDate", "");
            int[] d = date(when);
            if (d == null || d[0] != year || d[1] != month) continue;
            s.minutes += o.optLong("RunTimeTicks", 0) / 600_000_000L;
            if ("Episode".equals(o.optString("Type"))) {
                s.episodes++;
                String name = o.optString("SeriesName", "");
                if (!name.isEmpty()) {
                    if (!series.containsKey(name)) order.add(name);
                    series.put(name, series.getOrDefault(name, 0) + 1);
                }
            } else if ("Movie".equals(o.optString("Type"))) {
                s.movies++;
                if (s.lastMovie.isEmpty()) s.lastMovie = o.optString("Name", "");
            }
        }
        for (String name : order) {
            int n = series.get(name);
            if (n > s.topCount) {
                s.topCount = n;
                s.topSeries = name;
            }
        }
        return s;
    }

    /** "2026-10-12T20:11:03.0000000Z" i lokal tid: {år, måned 0-11}. */
    static int[] date(String iso) {
        if (iso == null || iso.length() < 19) return null;
        try {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.clear();
            c.set(Integer.parseInt(iso.substring(0, 4)), Integer.parseInt(iso.substring(5, 7)) - 1, Integer.parseInt(iso.substring(8, 10)),
                    Integer.parseInt(iso.substring(11, 13)), Integer.parseInt(iso.substring(14, 16)), Integer.parseInt(iso.substring(17, 19)));
            Calendar local = Calendar.getInstance();
            local.setTimeInMillis(c.getTimeInMillis());
            return new int[]{local.get(Calendar.YEAR), local.get(Calendar.MONTH)};
        } catch (Exception e) {
            return null;
        }
    }

    /** Henter sidste måneds tal fra Jellyfin. Kaldes fra en baggrundstråd. */
    static Stats fetch(Jellyfin jf) throws Exception {
        int[] lm = lastMonth(Calendar.getInstance());
        return compute(jf.playedRecently(), lm[0], lm[1]);
    }

    private static String key(Prefs prefs) {
        Calendar now = Calendar.getInstance();
        return String.format(Locale.ROOT, "%04d-%02d|%s", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, Profiles.active(prefs));
    }

    /** Skal tallene vises nu? Første gang i en ny måned, de første syv dage. */
    static boolean due(Prefs prefs) {
        if (!Features.on(prefs, Features.RECAP)) return false;
        if (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) > 7) return false;
        return !key(prefs).equals(prefs.str(SHOWN, ""));
    }

    static void markShown(Prefs prefs) {
        prefs.setStr(SHOWN, key(prefs));
    }

    /** Fuld skærm med tallene. OK eller Tilbage lukker; den lukker selv efter 20 sekunder. */
    static View show(Activity a, ViewGroup parent, Stats s, String name, int accent) {
        FrameLayout scrim = new FrameLayout(a);
        scrim.setBackgroundColor(Color.argb(240, 8, 9, 12));
        scrim.setClickable(true);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView kicker = Ui.text(a, s.monthName().toUpperCase(Locale.ROOT) + " " + s.year, 16, accent);
        kicker.setLetterSpacing(0.3f);
        kicker.setGravity(Gravity.CENTER);
        col.addView(kicker);
        TextView title = Ui.text(a, name.isEmpty() ? "Månedens tal" : Days.genitive(name) + " måned på tv'et", 46, Color.WHITE);
        title.setTypeface(Ui.display);
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(a, 40);
        col.addView(row, rowLp);
        List<ValueAnimator> counters = new ArrayList<>();
        counters.add(stat(a, row, s.hours(), s.hours() == 1 ? "time" : "timer", accent));
        if (s.episodes > 0) counters.add(stat(a, row, s.episodes, s.episodes == 1 ? "afsnit" : "afsnit", accent));
        if (s.movies > 0) counters.add(stat(a, row, s.movies, s.movies == 1 ? "film" : "film", accent));

        StringBuilder more = new StringBuilder();
        if (s.topCount > 1) more.append("Mest set: ").append(s.topSeries).append(" · ").append(s.topCount).append(" afsnit");
        if (!s.lastMovie.isEmpty()) {
            if (more.length() > 0) more.append("\n");
            more.append("Sidste film: ").append(s.lastMovie);
        }
        if (more.length() > 0) {
            TextView m = Ui.text(a, more.toString(), 22, DIM);
            m.setGravity(Gravity.CENTER);
            m.setLineSpacing(0, 1.25f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(a, 36);
            col.addView(m, lp);
        }
        TextView hint = Ui.text(a, "Tryk OK for at lukke", 15, DIM);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = Ui.dp(a, 34);
        col.addView(hint, hintLp);
        scrim.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        boolean[] closed = {false};
        Runnable close = () -> {
            if (closed[0]) return;
            closed[0] = true;
            for (ValueAnimator v : counters) v.cancel();
            scrim.animate().alpha(0f).setDuration(240).withEndAction(() -> parent.removeView(scrim)).start();
        };
        scrim.setFocusable(true);
        scrim.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_UP) return true;
            close.run();
            return true;
        });
        scrim.setOnClickListener(v -> close.run());
        parent.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(260).start();
        scrim.post(scrim::requestFocus);
        // Tallene tæller op, én gang, og står så stille
        scrim.postDelayed(() -> {
            for (ValueAnimator v : counters) v.start();
        }, 300);
        scrim.postDelayed(close, 20_000);
        return scrim;
    }

    private static ValueAnimator stat(Activity a, LinearLayout row, long value, String label, int accent) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView num = Ui.text(a, "0", 84, accent);
        num.setTypeface(Ui.display);
        num.setGravity(Gravity.CENTER);
        box.addView(num);
        TextView l = Ui.text(a, label, 20, DIM);
        l.setGravity(Gravity.CENTER);
        box.addView(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 200), ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(box, lp);
        ValueAnimator va = ValueAnimator.ofFloat(0f, value);
        va.setDuration(1100);
        va.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
        va.addUpdateListener(an -> num.setText(String.valueOf(Math.round((float) an.getAnimatedValue()))));
        return va;
    }
}
