package dk.selveste.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vejret for en valgt by fra Open-Meteo (gratis, ingen konto og ingen nøgle), til dem uden Home Assistant.
 * Har Home Assistant et vejr på statuslinjen, vinder det.
 */
final class Weather {

    private Weather() {
    }

    static final String CITY = "wx_city", LAT = "wx_lat", LON = "wx_lon", TEXT = "wx_text", AT = "wx_at";

    static final class Place {
        final String name, area;
        final double lat, lon;

        Place(String name, String area, double lat, double lon) {
            this.name = name;
            this.area = area;
            this.lat = lat;
            this.lon = lon;
        }

        String label() {
            return area.isEmpty() ? name : name + ", " + area;
        }
    }

    static boolean on(Prefs prefs) {
        return !prefs.str(LAT, "").isEmpty();
    }

    static String city(Prefs prefs) {
        return prefs.str(CITY, "");
    }

    static void set(Prefs prefs, Place p) {
        if (p == null) {
            prefs.setStr(CITY, "");
            prefs.setStr(LAT, "");
            prefs.setStr(LON, "");
            prefs.setStr(TEXT, "");
            return;
        }
        prefs.setStr(CITY, p.name);
        prefs.setStr(LAT, String.format(Locale.ROOT, "%.4f", p.lat));
        prefs.setStr(LON, String.format(Locale.ROOT, "%.4f", p.lon));
        prefs.setStr(TEXT, "");
        prefs.setStr(AT, "0");
    }

    /** Byer, der passer på navnet. Kaldes fra en baggrundstråd. */
    static List<Place> search(String name) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=6&language=da&format=json&name="
                + URLEncoder.encode(name.trim(), "UTF-8");
        JSONArray a = new JSONObject(Net.request("GET", url, null, null)).optJSONArray("results");
        List<Place> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i);
            String area = r.optString("admin1", "");
            String country = r.optString("country", "");
            if (!country.isEmpty() && !"Danmark".equals(country)) area = area.isEmpty() ? country : area + ", " + country;
            out.add(new Place(r.optString("name"), area, r.optDouble("latitude"), r.optDouble("longitude")));
        }
        return out;
    }

    /** Sidst kendte vejr, fx "Let skyet 9°", eller tom. */
    static String cached(Prefs prefs) {
        if (!on(prefs)) return "";
        long at = parse(prefs.str(AT, "0"));
        // Et vejr på over tre timer er ikke vejret længere
        if (System.currentTimeMillis() - at > 3 * 3600_000L) return "";
        return prefs.str(TEXT, "");
    }

    static boolean stale(Prefs prefs) {
        return on(prefs) && System.currentTimeMillis() - parse(prefs.str(AT, "0")) > 20 * 60_000L;
    }

    /** Henter vejret nu og gemmer det. Kaldes fra en baggrundstråd. */
    static String fetch(Prefs prefs) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code,is_day,wind_speed_10m"
                + "&wind_speed_unit=ms&timezone=auto&latitude=" + prefs.str(LAT, "") + "&longitude=" + prefs.str(LON, "");
        JSONObject cur = new JSONObject(Net.request("GET", url, null, null)).getJSONObject("current");
        String text = describe(cur.optInt("weather_code", -1), cur.optInt("is_day", 1) == 1, cur.optDouble("wind_speed_10m", 0))
                + " " + Math.round(cur.optDouble("temperature_2m", 0)) + "°";
        prefs.setStr(TEXT, text);
        prefs.setStr(AT, String.valueOf(System.currentTimeMillis()));
        return text;
    }

    /** WMO vejrkoder med de samme ord som Home Assistant vejret, så temaerne forstår dem ens. */
    static String describe(int code, boolean day, double wind) {
        String w;
        if (code == 0 || code == 1) w = day ? "Sol" : "Klart";
        else if (code == 2) w = "Let skyet";
        else if (code == 3) w = "Skyet";
        else if (code == 45 || code == 48) w = "Tåge";
        else if (code >= 51 && code <= 55) w = "Regn";
        else if (code == 56 || code == 57 || code == 66 || code == 67) w = "Slud";
        else if (code == 61 || code == 63 || code == 80 || code == 81) w = "Regn";
        else if (code == 65 || code == 82) w = "Skybrud";
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) w = "Sne";
        else if (code >= 95) w = "Torden";
        else w = "Skyet";
        if (wind >= 11 && (w.equals("Sol") || w.equals("Klart") || w.equals("Let skyet") || w.equals("Skyet"))) w = "Blæst";
        return w;
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
