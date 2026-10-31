package dk.selveste.launcher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sidste svar fra Jellyfin og HA gemt på disken, så forsiden er fyldt fra første billede
 * (også lige efter en opdatering eller genstart) og bagefter opdateres, når netværket svarer.
 */
final class RemoteCache {

    private RemoteCache() {
    }

    private static File file(Context c, String key) {
        File dir = new File(c.getCacheDir(), "forside");
        if (!dir.isDirectory()) dir.mkdirs();
        return new File(dir, key + ".json");
    }

    static void save(Context c, String key, String json) {
        try (FileOutputStream out = new FileOutputStream(file(c, key))) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    static String load(Context c, String key) {
        File f = file(c, key);
        if (!f.isFile()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            return new String(Net.readAll(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    static void clear(Context c) {
        File[] all = new File(c.getCacheDir(), "forside").listFiles();
        if (all != null) for (File f : all) f.delete();
    }

    // Jellyfin cards

    static void saveItems(Context c, String key, List<Jellyfin.Item> items) {
        try {
            JSONArray a = new JSONArray();
            for (Jellyfin.Item it : items) {
                a.put(new JSONObject().put("id", it.id).put("title", it.title).put("subtitle", it.subtitle)
                        .put("image", it.imageUrl).put("overview", it.overview).put("progress", it.progress)
                        .put("position", it.positionMs));
            }
            save(c, key, a.toString());
        } catch (Exception ignored) {
        }
    }

    static List<Jellyfin.Item> loadItems(Context c, String key) {
        String raw = load(c, key);
        if (raw == null) return null;
        try {
            JSONArray a = new JSONArray(raw);
            List<Jellyfin.Item> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Jellyfin.Item it = new Jellyfin.Item();
                it.id = o.optString("id");
                it.title = o.optString("title");
                it.subtitle = o.optString("subtitle");
                it.imageUrl = o.optString("image");
                it.overview = o.optString("overview");
                it.progress = o.optInt("progress");
                it.positionMs = o.optLong("position");
                out.add(it);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // Home Assistant

    static void saveStates(Context c, Map<String, JSONObject> states) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, JSONObject> e : states.entrySet()) o.put(e.getKey(), e.getValue());
            save(c, "ha", o.toString());
        } catch (Exception ignored) {
        }
    }

    static Map<String, JSONObject> loadStates(Context c) {
        String raw = load(c, "ha");
        if (raw == null) return null;
        try {
            JSONObject o = new JSONObject(raw);
            Map<String, JSONObject> out = new LinkedHashMap<>();
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                out.put(k, o.getJSONObject(k));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    static void saveUpcoming(Context c, List<HomeAssistant.Upcoming> list) {
        try {
            JSONArray a = new JSONArray();
            for (HomeAssistant.Upcoming u : list) a.put(new JSONObject().put("title", u.title).put("detail", u.detail).put("when", u.when));
            save(c, "upcoming", a.toString());
        } catch (Exception ignored) {
        }
    }

    static List<HomeAssistant.Upcoming> loadUpcoming(Context c) {
        String raw = load(c, "upcoming");
        if (raw == null) return null;
        try {
            JSONArray a = new JSONArray(raw);
            List<HomeAssistant.Upcoming> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                HomeAssistant.Upcoming u = new HomeAssistant.Upcoming();
                u.title = o.optString("title");
                u.detail = o.optString("detail");
                u.when = o.optLong("when");
                if (u.when > now - 6 * 3600_000L) out.add(u);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
