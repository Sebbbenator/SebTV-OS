package dk.selveste.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Finder installerede apps og fordeler dem i rækkerne på forsiden. */
final class AppRepository {

    /** En favorit matches på pakkenavn først og ellers på et ord i navnet. */
    static final class Favorite {
        final String keyword;
        final String[] packages;

        Favorite(String keyword, String... packages) {
            this.keyword = keyword;
            this.packages = packages;
        }
    }

    static final Favorite[] APPS = {
            new Favorite("wholphin", "com.github.damontecres.wholphin"),
            new Favorite("jellyfin", "org.jellyfin.androidtv"),
            new Favorite("netflix", "com.netflix.ninja", "com.netflix.mediaclient"),
            new Favorite("smarttube", "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"),
            new Favorite("twitch", "tv.twitch.android.viewer", "tv.twitch.android.app"),
    };

    /** Almindelige danske streaming apps, sat forrest i Hjem udgaven, hvis de er installeret. */
    static final Favorite[] STREAMING = {
            new Favorite("dr tv", "dk.dr.tvplayer", "dk.dr.drtv"),
            new Favorite("tv 2 play", "dk.tv2.tv2playtv", "dk.tv2.play"),
            new Favorite("viaplay", "com.viaplay.android"),
            new Favorite("disney", "com.disney.disneyplus"),
            new Favorite("hbo max", "com.wbd.stream", "com.hbo.hbonow"),
            new Favorite("prime video", "com.amazon.avod"),
            new Favorite("youtube", "com.amazon.firetv.youtube", "com.google.android.youtube.tv"),
            new Favorite("spotify", "com.spotify.tv.android"),
    };

    static final Favorite[] TOOLS = {
            new Favorite("downloader", "com.esaba.downloader"),
            new Favorite("tailscale", "com.tailscale.ipn"),
            new Favorite("hyperion", "com.abrenoch.hyperiongrabber"),
    };

    static final String SETTINGS_KEY = "jarvis:firetv";
    static final String JARVIS_SETTINGS_KEY = "jarvis:settings";
    static final String DICE_KEY = "jarvis:dice";

    static final String ROW_APPS = "apps";
    static final String ROW_TOOLS = "tools";
    static final String ROW_REST = "rest";

    final List<AppEntry> apps = new ArrayList<>();
    final List<AppEntry> tools = new ArrayList<>();
    final List<AppEntry> rest = new ArrayList<>();

    private final Context context;
    private final PackageManager pm;

    AppRepository(Context context) {
        this.context = context;
        this.pm = context.getPackageManager();
    }

    void load(boolean dice) {
        apps.clear();
        tools.clear();
        rest.clear();
        Prefs prefs = new Prefs(context);

        Map<String, AppEntry> all = new LinkedHashMap<>();
        collect(all, Intent.CATEGORY_LEANBACK_LAUNCHER);
        collect(all, Intent.CATEGORY_LAUNCHER);
        // Egne navne fra "Omdøb" i appens menu
        for (Map.Entry<String, AppEntry> en : all.entrySet()) {
            String own = prefs.str("label:" + en.getKey(), "");
            AppEntry e = en.getValue();
            if (!own.isEmpty()) en.setValue(new AppEntry(e.packageName, own, e.banner, e.icon, e.launchIntent));
        }
        all.remove(context.getPackageName());
        for (String h : prefs.hidden()) all.remove(h);

        pick(all, APPS, apps);
        if (!prefs.isJarvis()) pick(all, STREAMING, apps);
        pick(all, TOOLS, tools);
        rest.addAll(all.values());
        Collator collator = Collator.getInstance(new Locale("da", "DK"));
        Collections.sort(rest, (a, b) -> collator.compare(a.label, b.label));

        // Apps flyttet til en anden række med langt tryk
        List<AppEntry> everything = new ArrayList<>();
        everything.addAll(apps);
        everything.addAll(tools);
        everything.addAll(rest);
        for (AppEntry e : everything) {
            String row = prefs.rowOverride(e.packageName);
            if (row == null) continue;
            apps.remove(e);
            tools.remove(e);
            rest.remove(e);
            list(row).add(e);
        }

        java.util.Set<String> hidden = prefs.hidden();
        if (dice && !hidden.contains(DICE_KEY)) apps.add(special(DICE_KEY, "Hvad skal vi se?", android.R.drawable.ic_menu_help));
        if (!hidden.contains(SETTINGS_KEY)) tools.add(special(SETTINGS_KEY, FireTvMenu.platform(), android.R.drawable.ic_menu_manage));
        if (!hidden.contains(JARVIS_SETTINGS_KEY)) tools.add(special(JARVIS_SETTINGS_KEY, prefs.isJarvis() ? "Jarvis indstillinger" : "Indstillinger", android.R.drawable.ic_menu_preferences));

        applyOrder(prefs, ROW_APPS, apps);
        applyOrder(prefs, ROW_TOOLS, tools);
        applyOrder(prefs, ROW_REST, rest);

        String sort = prefs.appSort();
        if (!"egen".equals(sort)) {
            // Stabil sortering: apps der aldrig er åbnet beholder deres egen rækkefølge bagerst
            boolean recent = "senest".equals(sort);
            Collections.sort(apps, (a, b) -> Long.compare(prefs.usage(b.packageName, recent), prefs.usage(a.packageName, recent)));
        }
    }

    List<AppEntry> list(String row) {
        switch (row) {
            case ROW_APPS: return apps;
            case ROW_TOOLS: return tools;
            default: return rest;
        }
    }

    String rowOf(AppEntry e) {
        if (apps.contains(e)) return ROW_APPS;
        if (tools.contains(e)) return ROW_TOOLS;
        return ROW_REST;
    }

    private static void applyOrder(Prefs prefs, String row, List<AppEntry> entries) {
        List<String> order = prefs.order(row);
        if (order.isEmpty()) return;
        List<AppEntry> copy = new ArrayList<>(entries);
        Collections.sort(entries, (a, b) -> {
            int ia = order.indexOf(a.packageName);
            int ib = order.indexOf(b.packageName);
            if (ia < 0) ia = order.size() + copy.indexOf(a);
            if (ib < 0) ib = order.size() + copy.indexOf(b);
            return Integer.compare(ia, ib);
        });
    }

    /** Flyt en flise én plads og gem rækkefølgen. */
    void move(AppEntry e, int delta) {
        String row = rowOf(e);
        List<AppEntry> l = list(row);
        int i = l.indexOf(e);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= l.size()) return;
        Collections.swap(l, i, j);
        List<String> order = new ArrayList<>();
        for (AppEntry x : l) order.add(x.packageName);
        new Prefs(context).setOrder(row, order);
    }

    private void collect(Map<String, AppEntry> into, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
        for (ResolveInfo info : found) {
            ActivityInfo ai = info.activityInfo;
            if (ai == null || into.containsKey(ai.packageName)) continue;
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(new ComponentName(ai.packageName, ai.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            // Bannere og ikoner læses én gang pr. app; listen bygges igen efter hver app, der er åbnet
            String key = ai.packageName + "/" + ai.name;
            Object[] got = CACHE.get(key);
            if (got == null) {
                got = new Object[]{String.valueOf(info.loadLabel(pm)), loadBanner(ai), info.loadIcon(pm)};
                CACHE.put(key, got);
            }
            into.put(ai.packageName, new AppEntry(
                    ai.packageName,
                    (String) got[0],
                    copy((Drawable) got[1]),
                    copy((Drawable) got[2]),
                    launch));
        }
    }

    private static final Map<String, Object[]> CACHE = new java.util.HashMap<>();

    /** Apps er installeret, fjernet eller opdateret: læs bannere og navne forfra næste gang. */
    static void forget() {
        CACHE.clear();
    }

    /** Hver flise får sin egen drawable, men de deler billedet. */
    private Drawable copy(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs == null ? d : cs.newDrawable(context.getResources()).mutate();
    }

    /** Kun til skærmbilleder i test: bannere til opdigtede apps. */
    static java.util.function.Function<String, Drawable> testBanners;

    private Drawable loadBanner(ActivityInfo ai) {
        if (testBanners != null) return testBanners.apply(ai.packageName);
        try {
            Drawable banner = ai.loadBanner(pm);
            if (banner != null) return banner;
            return pm.getApplicationBanner(ai.packageName);
        } catch (Exception e) {
            return null;
        }
    }

    private static void pick(Map<String, AppEntry> all, Favorite[] favorites, List<AppEntry> into) {
        for (Favorite fav : favorites) {
            AppEntry hit = null;
            for (String pkg : fav.packages) {
                hit = all.get(pkg);
                if (hit != null) break;
            }
            if (hit == null) {
                for (AppEntry entry : all.values()) {
                    if (entry.label.toLowerCase(Locale.ROOT).contains(fav.keyword)) {
                        hit = entry;
                        break;
                    }
                }
            }
            if (hit != null) {
                all.remove(hit.packageName);
                into.add(hit);
            }
        }
    }

    private AppEntry special(String key, String label, int icon) {
        return new AppEntry(key, label, null, context.getDrawable(icon), null);
    }

    static boolean isSpecial(AppEntry e) {
        return e.packageName.startsWith("jarvis:");
    }
}
