package dk.selveste.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "Send til tv'et" fra telefonen: links åbnes i den rigtige app, film og serier i Wholphin. */
final class Send {

    private Send() {
    }

    static final String WHOLPHIN = "com.github.damontecres.wholphin";
    /** YouTube apps på Fire TV og Google TV, de reklamefri først. */
    static final String[] YOUTUBE = {"com.teamsmart.videomanager.tv", "org.smarttube.stable", "org.smarttube.beta",
            "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv", "com.amazon.firetv.youtube",
            "com.google.android.youtube.tv", "com.google.android.youtube.googletv"};

    private static final Pattern YT = Pattern.compile("(?:youtu\\.be/|[?&]v=|/shorts/|/live/|/embed/)([A-Za-z0-9_-]{11})");
    private static final Pattern JF_ID = Pattern.compile("[?&]id=([0-9a-fA-F]{32})");

    static String youtubeId(String url) {
        if (url == null || !url.toLowerCase(java.util.Locale.ROOT).contains("youtu")) return null;
        Matcher m = YT.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    static String jellyfinId(String url) {
        Matcher m = JF_ID.matcher(url == null ? "" : url);
        return m.find() ? m.group(1).toLowerCase(java.util.Locale.ROOT) : null;
    }

    /** Åbner et link på tv'et. Giver navnet på appen, der fik det. Kaster med en læsbar fejl. */
    static String open(Context c, String raw) throws Exception {
        String url = raw == null ? "" : raw.trim();
        // Delt tekst kan have mere end linket med: tag det første link
        Matcher any = Pattern.compile("https?://\\S+").matcher(url);
        if (any.find()) url = any.group();
        if (!url.startsWith("http")) throw new IllegalArgumentException("Det ligner ikke et link");
        PackageManager pm = c.getPackageManager();
        String yt = youtubeId(url);
        if (yt != null) {
            Uri u = Uri.parse("https://www.youtube.com/watch?v=" + yt);
            for (String pkg : YOUTUBE) {
                if (!installed(pm, pkg)) continue;
                Intent i = new Intent(Intent.ACTION_VIEW, u).setPackage(pkg);
                if (i.resolveActivity(pm) == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + yt)).setPackage(pkg);
                if (i.resolveActivity(pm) != null && start(c, i)) return label(pm, pkg);
            }
            throw new IllegalStateException("Tv'et har ingen YouTube app");
        }
        String jf = jellyfinId(url);
        if (jf != null && installed(pm, WHOLPHIN)) {
            if (start(c, item(jf))) return "Wholphin";
        }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (i.resolveActivity(pm) == null) throw new IllegalStateException("Tv'et har ingen app, der kan åbne det link");
        if (!start(c, i)) throw new IllegalStateException("Tv'et ville ikke åbne linket");
        android.content.pm.ResolveInfo r = pm.resolveActivity(i, 0);
        return r == null ? "tv'et" : String.valueOf(r.loadLabel(pm));
    }

    /** Afspiller en film eller et afsnit fra hvor man nåede til; en serie åbnes på sin side. */
    static void play(Context c, String id, String type, long positionMs) throws Exception {
        if (!installed(c.getPackageManager(), WHOLPHIN)) throw new IllegalStateException("Wholphin er ikke installeret på tv'et");
        if (!"Series".equals(type) && !"Season".equals(type)) {
            Intent play = new Intent("com.github.damontecres.wholphin.PLAYBACK",
                    Uri.parse("wholphin://play?itemId=" + id + "&position=" + positionMs))
                    .setPackage(WHOLPHIN).putExtra("itemId", id).putExtra("position", positionMs);
            if (start(c, play)) return;
        }
        if (!start(c, item(id))) throw new IllegalStateException("Wholphin ville ikke åbne");
    }

    private static Intent item(String id) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("wholphin://item/" + id)).setPackage(WHOLPHIN).putExtra("itemId", id);
    }

    private static boolean start(Context c, Intent i) {
        if (HomeService.launch(i)) return true;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return "YouTube";
        }
    }
}
