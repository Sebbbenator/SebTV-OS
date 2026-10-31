package dk.selveste.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Jellyfin API. Login sker med Quick Connect, så der aldrig tastes password på tv'et. */
final class Jellyfin {

    static final class Item {
        String id;
        String title;
        String subtitle;
        String imageUrl;
        String overview;
        int progress;
        /** Hvor langt man er nået, i millisekunder (0 = fra start). */
        long positionMs;
    }

    private final Prefs prefs;

    Jellyfin(Prefs prefs) {
        this.prefs = prefs;
    }

    boolean configured() {
        return !prefs.jfToken().isEmpty() && !prefs.jfUser().isEmpty();
    }

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        String auth = "MediaBrowser Client=\"Jarvis TV\", Device=\"Fire TV\", DeviceId=\"" + prefs.deviceId()
                + "\", Version=\"1.0\"";
        if (!prefs.jfToken().isEmpty()) auth += ", Token=\"" + prefs.jfToken() + "\"";
        h.put("Authorization", auth);
        return h;
    }

    private String get(String... paths) throws IOException {
        IOException last = null;
        for (String p : paths) {
            try {
                return Net.request("GET", prefs.jfUrl() + p, headers(), null);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    /**
     * Finder serveren: først den gemte adresse, så de ekstra adresser (fx Tailscale) og til sidst
     * en søgning på netværket. Gør at samme app virker hjemme, ude og hos venner uden opsætning.
     */
    String findServer() {
        java.util.LinkedHashSet<String> tries = new java.util.LinkedHashSet<>();
        if (!prefs.jfUrl().isEmpty()) tries.add(prefs.jfUrl());
        tries.addAll(prefs.jfFallbacks());
        for (String base : tries) {
            if (answers(base)) {
                if (!base.equals(prefs.jfUrl())) prefs.setJfUrl(base);
                return base;
            }
        }
        for (String base : discover()) {
            if (answers(base)) {
                prefs.setJfUrl(base);
                return base;
            }
        }
        return null;
    }

    private static boolean answers(String base) {
        try {
            Net.request("GET", base + "/System/Info/Public", null, null);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Jellyfin svarer på UDP broadcast "who is JellyfinServer?" på port 7359 med sin adresse.
     * Det er sådan Jellyfins egne apps finder serveren på wifi'et.
     */
    static List<String> discover() {
        List<String> found = new ArrayList<>();
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(1500);
            byte[] ask = "who is JellyfinServer?".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            socket.send(new java.net.DatagramPacket(ask, ask.length, java.net.InetAddress.getByName("255.255.255.255"), 7359));
            long until = System.currentTimeMillis() + 1500;
            byte[] buf = new byte[2048];
            while (System.currentTimeMillis() < until) {
                java.net.DatagramPacket p = new java.net.DatagramPacket(buf, buf.length);
                try {
                    socket.receive(p);
                } catch (java.net.SocketTimeoutException e) {
                    break;
                }
                try {
                    JSONObject j = new JSONObject(new String(p.getData(), 0, p.getLength(), java.nio.charset.StandardCharsets.UTF_8));
                    String address = j.optString("Address", "").replaceAll("/+$", "");
                    if (!address.isEmpty() && !found.contains(address)) found.add(address);
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return found;
    }

    // Quick Connect

    JSONObject initiateQuickConnect() throws Exception {
        return new JSONObject(Net.request("POST", prefs.jfUrl() + "/QuickConnect/Initiate", headers(), "{}"));
    }

    boolean quickConnectApproved(String secret) throws Exception {
        JSONObject j = new JSONObject(get("/QuickConnect/Connect?secret=" + secret));
        return j.optBoolean("Authenticated", false);
    }

    void finishQuickConnect(String secret) throws Exception {
        JSONObject res = new JSONObject(Net.request("POST", prefs.jfUrl() + "/Users/AuthenticateWithQuickConnect",
                headers(), new JSONObject().put("Secret", secret).toString()));
        prefs.setJellyfin(prefs.jfUrl(), res.getString("AccessToken"), res.getJSONObject("User").getString("Id"));
    }

    // Rækker

    List<Item> resume() throws Exception {
        String u = prefs.jfUser();
        String q = "limit=12&mediaTypes=Video&enableUserData=true";
        return items(get("/UserItems/Resume?userId=" + u + "&" + q, "/Users/" + u + "/Items/Resume?" + q));
    }

    /** Næste afsnit af serier, der er i gang, lagt efter dem med fremskridt. */
    void addNextUp(List<Item> into) throws Exception {
        String u = prefs.jfUser();
        String q = "limit=12&enableResumable=false&enableUserData=true";
        List<Item> next = items(get("/Shows/NextUp?userId=" + u + "&" + q));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Item it : into) seen.add(it.title);
        for (Item it : next) {
            // Én flise pr. serie: står serien allerede i Fortsæt at se, springes næste afsnit over
            if (seen.add(it.title) && into.size() < 16) into.add(it);
        }
    }

    List<Item> latest() throws Exception {
        String u = prefs.jfUser();
        String q = "limit=16&includeItemTypes=Movie,Episode&groupItems=true";
        return items(get("/Items/Latest?userId=" + u + "&" + q, "/Users/" + u + "/Items/Latest?" + q));
    }

    /** Hvad der afspilles lige nu på andre skærme (kræver at brugeren er administrator for at se andres). */
    List<Item> nowPlaying() throws Exception {
        JSONArray arr = new JSONArray(get("/Sessions?activeWithinSeconds=90"));
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.getJSONObject(i);
            JSONObject np = s.optJSONObject("NowPlayingItem");
            if (np == null || prefs.deviceId().equals(s.optString("DeviceId"))) continue;
            Item it = item(np);
            String where = s.optString("DeviceName", "");
            String who = s.optString("UserName", "");
            JSONObject ps = s.optJSONObject("PlayState");
            boolean paused = ps != null && ps.optBoolean("IsPaused", false);
            it.subtitle = (paused ? "Pause" : "Afspilles") + (where.isEmpty() ? "" : " på " + where) + (who.isEmpty() ? "" : " · " + who)
                    + (np.optString("Type").equals("Episode") ? " · " + it.subtitle : "");
            long run = np.optLong("RunTimeTicks", 0);
            long pos = ps == null ? 0 : ps.optLong("PositionTicks", 0);
            if (run > 0) it.progress = (int) (pos * 100 / run);
            out.add(it);
        }
        return out;
    }

    Item randomUnwatchedMovie() throws Exception {
        String u = prefs.jfUser();
        String q = "includeItemTypes=Movie&isPlayed=false&sortBy=Random&limit=1&recursive=true&fields=Overview";
        List<Item> list = items(get("/Items?userId=" + u + "&" + q, "/Users/" + u + "/Items?" + q));
        return list.isEmpty() ? null : list.get(0);
    }

    String backdropUrl(String id) {
        return prefs.jfUrl() + "/Items/" + id + "/Images/Backdrop?maxWidth=1280&quality=85";
    }

    private List<Item> items(String json) throws Exception {
        String trimmed = json.trim();
        JSONArray arr = trimmed.startsWith("[") ? new JSONArray(trimmed) : new JSONObject(trimmed).optJSONArray("Items");
        List<Item> out = new ArrayList<>();
        for (int i = 0; arr != null && i < arr.length(); i++) out.add(item(arr.getJSONObject(i)));
        return out;
    }

    private Item item(JSONObject o) {
        Item it = new Item();
        it.id = o.optString("Id");
        String type = o.optString("Type");
        if ("Episode".equals(type)) {
            it.title = o.optString("SeriesName", o.optString("Name"));
            it.subtitle = "S" + o.optInt("ParentIndexNumber", 0) + ":E" + o.optInt("IndexNumber", 0) + " " + o.optString("Name");
        } else {
            it.title = o.optString("Name");
            int year = o.optInt("ProductionYear", 0);
            it.subtitle = year > 0 ? String.valueOf(year) : "";
        }
        it.overview = o.optString("Overview", "");
        JSONObject ud = o.optJSONObject("UserData");
        if (ud != null) {
            it.progress = (int) Math.round(ud.optDouble("PlayedPercentage", 0));
            it.positionMs = ud.optLong("PlaybackPositionTicks", 0) / 10000;
        }

        JSONObject tags = o.optJSONObject("ImageTags");
        JSONArray backdrops = o.optJSONArray("BackdropImageTags");
        String base = prefs.jfUrl() + "/Items/";
        String size = "?maxWidth=480&quality=80";
        if (tags != null && tags.has("Thumb")) it.imageUrl = base + it.id + "/Images/Thumb" + size;
        else if (o.has("ParentThumbItemId")) it.imageUrl = base + o.optString("ParentThumbItemId") + "/Images/Thumb" + size;
        else if (backdrops != null && backdrops.length() > 0) it.imageUrl = base + it.id + "/Images/Backdrop" + size;
        else if (o.has("ParentBackdropItemId")) it.imageUrl = base + o.optString("ParentBackdropItemId") + "/Images/Backdrop" + size;
        else it.imageUrl = base + it.id + "/Images/Primary" + size;
        return it;
    }
}
