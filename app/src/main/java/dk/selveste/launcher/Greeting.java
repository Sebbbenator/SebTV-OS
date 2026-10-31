package dk.selveste.launcher;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/** Hilsenen og "dage sammen" linjen på forsiden. Ren tekst, så den kan testes uden tv. */
final class Greeting {

    private Greeting() {
    }

    static String text(Prefs prefs) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String custom = prefs.customGreeting();
        if (!custom.isEmpty()) return custom;
        String g = h < 5 ? "Godnat" : h < 10 ? "Godmorgen" : h < 17 ? "Goddag" : h < 23 ? "Godaften" : "Godnat";
        String name = prefs.profileName();
        return name.isEmpty() ? g : g + ", " + name;
    }

    /** "♥ 300 dage sammen med X · ..." eller tom, hvis funktionen er fra eller datoen ikke er sat. */
    static String together(Prefs prefs) {
        if (!Features.on(prefs, Features.TOGETHER)) return "";
        int[] since = prefs.togetherSince();
        if (since == null) return "";
        return together(since, prefs.partnerName(), Calendar.getInstance());
    }

    static String together(int[] since, String partner, Calendar today) {
        Calendar start = Calendar.getInstance();
        start.clear();
        start.set(since[0], since[1], since[2]);
        Calendar todayMidnight = Calendar.getInstance();
        todayMidnight.clear();
        todayMidnight.set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
        long days = Math.round((todayMidnight.getTimeInMillis() - start.getTimeInMillis()) / (double) TimeUnit.DAYS.toMillis(1));
        if (days < 0) return "";

        Calendar anniversary = Calendar.getInstance();
        anniversary.clear();
        anniversary.set(today.get(Calendar.YEAR), since[1], since[2]);
        if (anniversary.before(todayMidnight)) anniversary.add(Calendar.YEAR, 1);
        long toAnniversary = Math.round((anniversary.getTimeInMillis() - todayMidnight.getTimeInMillis()) / (double) TimeUnit.DAYS.toMillis(1));
        int years = anniversary.get(Calendar.YEAR) - since[0];
        long nextHundred = (days / 100 + 1) * 100;
        long toHundred = nextHundred - days;

        String extra;
        if (toAnniversary == 0) extra = "I dag: " + years + " år sammen!";
        else if (days % 100 == 0 && days > 0) extra = "I dag: " + days + " dage!";
        else if (toHundred <= toAnniversary) extra = nextHundred + " dage " + when(toHundred);
        else extra = years + " år " + when(toAnniversary);
        String who = partner == null || partner.isEmpty() ? "" : " med " + partner;
        return "♥ " + days + " dage sammen" + who + "  ·  " + extra;
    }

    private static String when(long days) {
        return days == 1 ? "i morgen" : "om " + days + " dage";
    }
}
