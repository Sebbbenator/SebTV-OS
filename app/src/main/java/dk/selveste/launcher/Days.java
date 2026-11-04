package dk.selveste.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Mærkedage: fødselsdage, faste dage hvert år og nedtællinger. Forsiden viser kun de nærmeste,
 * og opstarten siger det på selve dagen. Ren tekst, så det kan testes uden tv.
 */
final class Days {

    private Days() {
    }

    static final String KEY = "days_json";
    static final String BIRTHDAY = "birthday", YEARLY = "yearly", ONCE = "once";
    static final String[][] KINDS = {{BIRTHDAY, "Fødselsdag"}, {YEARLY, "Hvert år"}, {ONCE, "Én gang (nedtælling)"}};

    /** Fødselsdage og faste dage vises fra to uger før; nedtællinger hele vejen. */
    static final int SOON = 14;

    static final class Day {
        final String name, kind;
        /** År (0 = ukendt), måned (0-11), dag. */
        final int year, month, day;

        Day(String name, String kind, int year, int month, int day) {
            this.name = name;
            this.kind = kind;
            this.year = year;
            this.month = month;
            this.day = day;
        }

        String dateText() {
            return year > 0 ? String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)
                    : String.format(Locale.ROOT, "%02d-%02d", day, month + 1);
        }

        String kindName() {
            for (String[] k : KINDS) if (k[0].equals(kind)) return k[1];
            return "";
        }
    }

    static List<Day> all(Prefs prefs) {
        List<Day> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.str(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                int[] d = parse(o.optString("date"));
                if (d != null) out.add(new Day(o.optString("name"), o.optString("kind", BIRTHDAY), d[0], d[1], d[2]));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static void save(Prefs prefs, List<Day> days) {
        JSONArray a = new JSONArray();
        try {
            for (Day d : days) a.put(new JSONObject().put("name", d.name).put("kind", d.kind).put("date", d.dateText()));
        } catch (Exception ignored) {
        }
        prefs.setStr(KEY, a.toString());
    }

    static void add(Prefs prefs, Day d) {
        List<Day> list = all(prefs);
        list.add(d);
        save(prefs, list);
    }

    static void remove(Prefs prefs, int index) {
        List<Day> list = all(prefs);
        if (index >= 0 && index < list.size()) list.remove(index);
        save(prefs, list);
    }

    /** 2026-03-12, 12-03-2026, 12.3.2026, 12/3 eller 12-03 (uden år). Giver {år, måned 0-11, dag} eller null. */
    static int[] parse(String s) {
        if (s == null) return null;
        String[] p = s.trim().split("[-./ ]+");
        try {
            int y, m, d;
            if (p.length == 3 && p[0].length() == 4) {
                y = Integer.parseInt(p[0]);
                m = Integer.parseInt(p[1]);
                d = Integer.parseInt(p[2]);
            } else if (p.length == 3) {
                d = Integer.parseInt(p[0]);
                m = Integer.parseInt(p[1]);
                y = Integer.parseInt(p[2]);
                if (y < 100) y += 2000;
            } else if (p.length == 2) {
                d = Integer.parseInt(p[0]);
                m = Integer.parseInt(p[1]);
                y = 0;
            } else return null;
            if (m < 1 || m > 12 || d < 1 || d > 31) return null;
            return new int[]{y, m - 1, d};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Dage til næste gang (0 = i dag), eller -1 hvis en nedtælling er overstået. */
    static long until(Day d, Calendar today) {
        Calendar t = midnight(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
        Calendar next;
        if (ONCE.equals(d.kind)) {
            if (d.year <= 0) return -1;
            next = midnight(d.year, d.month, d.day);
            if (next.before(t)) return -1;
        } else {
            next = midnight(t.get(Calendar.YEAR), d.month, d.day);
            if (next.before(t)) next = midnight(t.get(Calendar.YEAR) + 1, d.month, d.day);
        }
        return Math.round((next.getTimeInMillis() - t.getTimeInMillis()) / 86_400_000.0);
    }

    /** Hvor gammel en fødselsdag bliver næste gang, eller 0 hvis året ikke kendes. */
    static int turns(Day d, Calendar today, long until) {
        if (d.year <= 0) return 0;
        Calendar next = (Calendar) today.clone();
        next.add(Calendar.DAY_OF_YEAR, (int) until);
        return next.get(Calendar.YEAR) - d.year;
    }

    /** Om navnet er profilens eget, så det er "din" fødselsdag. */
    static boolean mine(Day d, String profileName) {
        return profileName != null && !profileName.isEmpty() && d.name.trim().equalsIgnoreCase(profileName.trim());
    }

    /** Én sætning om en mærkedag, fx "Mor fylder 56 om 3 dage" eller "I dag: Roskilde". */
    static String phrase(Day d, Calendar today, String profileName) {
        long u = until(d, today);
        if (u < 0) return "";
        String when = u == 0 ? "i dag" : u == 1 ? "i morgen" : "om " + u + " dage";
        if (BIRTHDAY.equals(d.kind)) {
            if (mine(d, profileName)) return u == 0 ? "Tillykke med dagen!" : "Din fødselsdag " + when;
            int age = turns(d, today, u);
            if (age > 0) return u == 0 ? "I dag fylder " + d.name + " " + age : d.name + " fylder " + age + " " + when;
            return u == 0 ? "I dag: " + genitive(d.name) + " fødselsdag" : genitive(d.name) + " fødselsdag " + when;
        }
        if (YEARLY.equals(d.kind) && d.year > 0) {
            int years = turns(d, today, u);
            if (years > 0) return u == 0 ? "I dag: " + d.name + ", " + years + " år" : d.name + " (" + years + " år) " + when;
        }
        return u == 0 ? "I dag: " + d.name : u == 1 ? "I morgen: " + d.name : d.name + " " + when;
    }

    /** De nærmeste mærkedage til forsiden, højst max stykker. */
    static List<String> upcoming(Prefs prefs, Calendar today, int max) {
        List<Day> list = all(prefs);
        List<long[]> order = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Day d = list.get(i);
            long u = until(d, today);
            if (u < 0) continue;
            if (!ONCE.equals(d.kind) && u > SOON) continue;
            order.add(new long[]{u, i});
        }
        Collections.sort(order, (a, b) -> Long.compare(a[0], b[0]));
        List<String> out = new ArrayList<>();
        for (long[] o : order) {
            if (out.size() >= max) break;
            String p = phrase(list.get((int) o[1]), today, prefs.profileName());
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    /** Dele til forsiden: dage sammen (kort, når der også er en mærkedag) og de nærmeste mærkedage. */
    static List<String> parts(Prefs prefs) {
        Calendar now = Calendar.getInstance();
        String together = Greeting.together(prefs);
        List<String> soon = upcoming(prefs, now, together.isEmpty() ? 2 : 1);
        List<String> parts = new ArrayList<>();
        if (!together.isEmpty()) {
            if (soon.isEmpty()) parts.add(together);
            else parts.add(together.split(" {2}· {2}")[0].replace(" dage sammen med ", " dage med "));
        }
        parts.addAll(soon);
        return parts;
    }

    static String line(Prefs prefs) {
        return String.join("  ·  ", parts(prefs));
    }

    /** Hvad opstarten skal sige i dag, eller tom. */
    static String today(Prefs prefs) {
        Calendar now = Calendar.getInstance();
        for (Day d : all(prefs)) {
            if (until(d, now) == 0) return phrase(d, now, prefs.profileName());
        }
        return "";
    }

    /** Profilens egen fødselsdag i dag. */
    static boolean birthday(Prefs prefs) {
        Calendar now = Calendar.getInstance();
        for (Day d : all(prefs)) if (BIRTHDAY.equals(d.kind) && mine(d, prefs.profileName()) && until(d, now) == 0) return true;
        return false;
    }

    static String genitive(String name) {
        String n = name.trim();
        if (n.isEmpty()) return n;
        char last = Character.toLowerCase(n.charAt(n.length() - 1));
        return last == 's' || last == 'x' || last == 'z' ? n + "'" : n + "s";
    }

    /** Midnat på datoen; 29. februar bliver 28. i år uden skuddag. */
    private static Calendar midnight(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, 1);
        c.set(Calendar.DAY_OF_MONTH, Math.min(d, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
        return c;
    }
}
