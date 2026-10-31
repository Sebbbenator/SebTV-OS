package dk.selveste.launcher;

import android.content.Context;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * Login på Home Assistant uden at kopiere et token: telefonen logger ind på HA's egen side, HA sender
 * en kode tilbage til tv'ets telefonside, og tv'et bytter den til et token, der fornyer sig selv.
 * Passwordet rører aldrig tv'et, og adgangen kan tilbagekaldes under HA profil > Sikkerhed.
 * Long-lived tokens virker stadig som før.
 */
final class HaLogin {

    private HaLogin() {
    }

    private static String pendingState;
    private static String pendingHa;
    private static String pendingClient;

    /** Adressen telefonen skal åbnes på for at logge ind. tvBase er fx http://192.168.1.20:8765 */
    static synchronized String startUrl(String haUrl, String tvBase) throws Exception {
        byte[] b = new byte[12];
        new SecureRandom().nextBytes(b);
        StringBuilder state = new StringBuilder();
        for (byte x : b) state.append(String.format("%02x", x));
        pendingState = state.toString();
        pendingHa = haUrl.trim().replaceAll("/+$", "");
        pendingClient = tvBase + "/";
        return pendingHa + "/auth/authorize?response_type=code"
                + "&client_id=" + enc(pendingClient)
                + "&redirect_uri=" + enc(tvBase + "/ha-login")
                + "&state=" + pendingState;
    }

    /** HA har sendt telefonen tilbage med en kode: byt den til tokens og gem dem. Svarer med en lille side. */
    static String finish(Context context, Map<String, String> query) {
        String code = query.get("code");
        String state = query.get("state");
        String ha;
        String client;
        synchronized (HaLogin.class) {
            if (code == null || state == null || !state.equals(pendingState)) {
                return page("Login udløbet", "Start login igen fra telefonsiden.");
            }
            ha = pendingHa;
            client = pendingClient;
            pendingState = null;
        }
        try {
            Map<String, String> form = new HashMap<>();
            form.put("grant_type", "authorization_code");
            form.put("code", code);
            form.put("client_id", client);
            JSONObject res = new JSONObject(post(ha + "/auth/token", form));
            Prefs prefs = new Prefs(context);
            prefs.setHa(ha, null, null);
            save(prefs, client, res);
            MainActivity.appsDirty = true;
            return page("Forbundet", "Home Assistant er forbundet. Du kan lukke denne side og trykke Tilbage på tv'et.");
        } catch (Exception e) {
            return page("Login fejlede", String.valueOf(e.getMessage()));
        }
    }

    /**
     * Token til HA kald. Et long-lived token bruges, hvis der er et; ellers fornyes adgangen fra login,
     * når den er ved at udløbe (HA giver 30 minutter ad gangen). Kaldes fra en baggrundstråd.
     */
    static synchronized String token(Prefs prefs) {
        String longLived = prefs.haToken();
        if (!longLived.isEmpty()) return longLived;
        String refresh = prefs.str("ha_refresh", "");
        if (refresh.isEmpty()) return "";
        String access = prefs.str("ha_access", "");
        long expires = Long.parseLong(prefs.str("ha_access_until", "0"));
        if (!access.isEmpty() && System.currentTimeMillis() < expires - 60_000) return access;
        try {
            Map<String, String> form = new HashMap<>();
            form.put("grant_type", "refresh_token");
            form.put("refresh_token", refresh);
            form.put("client_id", prefs.str("ha_client", ""));
            JSONObject res = new JSONObject(post(prefs.haUrl() + "/auth/token", form));
            save(prefs, prefs.str("ha_client", ""), res);
            return res.optString("access_token", "");
        } catch (Exception e) {
            return access;
        }
    }

    static boolean loggedIn(Prefs prefs) {
        return !prefs.str("ha_refresh", "").isEmpty();
    }

    static void forget(Prefs prefs) {
        prefs.setStr("ha_refresh", "");
        prefs.setStr("ha_access", "");
        prefs.setStr("ha_access_until", "0");
    }

    private static void save(Prefs prefs, String client, JSONObject res) {
        if (res.has("refresh_token")) prefs.setStr("ha_refresh", res.optString("refresh_token"));
        prefs.setStr("ha_client", client);
        prefs.setStr("ha_access", res.optString("access_token", ""));
        prefs.setStr("ha_access_until", String.valueOf(System.currentTimeMillis() + res.optLong("expires_in", 1800) * 1000));
    }

    private static String post(String url, Map<String, String> form) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/x-www-form-urlencoded");
        return Net.request("POST", url, h, body.toString());
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String page(String title, String text) {
        return "<!doctype html><html lang=\"da\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + title + "</title><style>body{margin:0;padding:32px 20px;background:#02080d;color:#d8f6ff;font:17px/1.5 system-ui,sans-serif}"
                + "h1{font-size:22px;color:#4fd8ff}</style></head><body><h1>" + esc(title) + "</h1><p>" + esc(text) + "</p></body></html>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
