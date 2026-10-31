package dk.selveste.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kommandoer fra Home Assistant: skriver HA en besked, der starter med "/", i beskedfeltet, gør tv'et
 * noget i stedet for at vise et banner. Fx fra en automation eller Jarvis stemmen.
 *
 *   /tema ironman      skift tema (nøgle eller navn)
 *   /rolig             slå bevægelse og effekter fra   (/rolig fra = tilbage til normal)
 *   /app netflix       åbn en app (navn eller pakke)
 *   /hjem              tilbage til forsiden
 *   /sov               sluk skærmen
 */
final class TvCommands {

    private TvCommands() {
    }

    static boolean isCommand(String text) {
        return text != null && text.startsWith("/") && text.length() > 1;
    }

    /** Udfører kommandoen. Svarer med en kort tekst til fejl-loggen, hvis den ikke kunne udføres. */
    static String run(Context context, String text) {
        String t = text.trim().substring(1).trim();
        int sp = t.indexOf(' ');
        String cmd = (sp < 0 ? t : t.substring(0, sp)).toLowerCase(Locale.ROOT);
        String arg = sp < 0 ? "" : t.substring(sp + 1).trim();
        Prefs prefs = new Prefs(context);
        switch (cmd) {
            case "tema":
            case "theme": {
                String key = theme(arg);
                if (key == null) return "Ukendt tema: " + arg;
                Themes.apply(prefs, key);
                MainActivity.lookChangedFromOutside();
                return null;
            }
            case "rolig":
            case "calm": {
                boolean off = arg.equalsIgnoreCase("fra") || arg.equalsIgnoreCase("off");
                prefs.setEffects(off ? "auto" : "ingen");
                MainActivity.lookChangedFromOutside();
                return null;
            }
            case "effekter":
            case "effects": {
                String level = arg.toLowerCase(Locale.ROOT);
                for (String[] e : Prefs.EFFECTS) {
                    if (e[0].equals(level)) {
                        prefs.setEffects(level);
                        MainActivity.lookChangedFromOutside();
                        return null;
                    }
                }
                return "Ukendt effektniveau: " + arg;
            }
            case "app":
            case "aabn":
            case "åbn": {
                Intent i = findApp(context, arg);
                if (i == null) return "Fandt ingen app: " + arg;
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                return null;
            }
            case "hjem":
            case "home": {
                Intent i = new Intent(context, MainActivity.class)
                        .putExtra(MainActivity.EXTRA_HOME, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                context.startActivity(i);
                return null;
            }
            case "sov":
            case "sleep":
                return HomeService.sleepNow() ? null : "Kan ikke slukke skærmen herfra";
            default:
                return "Ukendt kommando: /" + cmd;
        }
    }

    static String theme(String arg) {
        String a = norm(arg);
        if (a.isEmpty()) return null;
        for (String[] t : Themes.ALL) if (norm(t[0]).equals(a) || norm(t[1]).equals(a)) return t[0];
        for (String[] t : Themes.ALL) if (norm(t[1]).startsWith(a) || norm(t[0]).startsWith(a)) return t[0];
        return null;
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "").replace("é", "e")
                .replace("æ", "ae").replace("ø", "oe").replace("å", "aa");
    }

    /** Pakkenavn, præcist navn eller begyndelsen af navnet; tv apps (Leanback) foretrækkes. */
    static Intent findApp(Context context, String arg) {
        if (arg.isEmpty()) return null;
        PackageManager pm = context.getPackageManager();
        Intent direct = pm.getLeanbackLaunchIntentForPackage(arg);
        if (direct == null) direct = pm.getLaunchIntentForPackage(arg);
        if (direct != null) return direct;
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        String a = norm(arg);
        String startsWith = null;
        for (ResolveInfo r : all) {
            String label = norm(String.valueOf(r.loadLabel(pm)));
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(context.getPackageName())) continue;
            if (label.equals(a)) return launch(pm, pkg);
            if (startsWith == null && (label.startsWith(a) || pkg.toLowerCase(Locale.ROOT).contains(a))) startsWith = pkg;
        }
        return startsWith == null ? null : launch(pm, startsWith);
    }

    private static Intent launch(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }
}
