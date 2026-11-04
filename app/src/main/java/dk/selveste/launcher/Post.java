package dk.selveste.launcher;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Små beskeder mellem tv'er. Hvert tv har en adresse: et tilfældigt kanalnavn og en nøgle. Beskeden krypteres
 * med nøglen (AES-GCM) og lægges på ntfy.sh, en gratis beskedtjeneste uden konto, som kun ser volapyk.
 * Modtageren kigger efter nye beskeder hvert minut, mens skærmen er tændt. ntfy.sh gemmer en besked i 12 timer.
 */
final class Post {

    private Post() {
    }

    static final String PREFIX = "SEBTV-POST:";
    static final String TOPIC = "post_topic", KEY = "post_key", SINCE = "post_since", INBOX = "post_inbox",
            CONTACTS = "post_contacts", SERVER = "post_server";
    static final String DEFAULT_SERVER = "https://ntfy.sh";
    static final int MAX_TEXT = 300;

    static final class Message {
        final String from, text;
        final long at;
        boolean seen;

        Message(String from, String text, long at, boolean seen) {
            this.from = from;
            this.text = text;
            this.at = at;
            this.seen = seen;
        }
    }

    private static String server(Prefs prefs) {
        String s = prefs.str(SERVER, "").trim();
        return s.isEmpty() ? DEFAULT_SERVER : s.replaceAll("/+$", "");
    }

    /** Dette tv's adresse; laves første gang. */
    static synchronized String address(Prefs prefs) {
        String topic = prefs.str(TOPIC, ""), key = prefs.str(KEY, "");
        if (topic.isEmpty() || key.isEmpty()) {
            SecureRandom r = new SecureRandom();
            String abc = "abcdefghijkmnpqrstuvwxyz23456789";
            StringBuilder t = new StringBuilder("sebtv-");
            for (int i = 0; i < 22; i++) t.append(abc.charAt(r.nextInt(abc.length())));
            byte[] k = new byte[16];
            r.nextBytes(k);
            topic = t.toString();
            key = b64(k);
            prefs.setStr(TOPIC, topic);
            prefs.setStr(KEY, key);
        }
        return PREFIX + topic + "." + key;
    }

    /** {kanal, nøgle} fra en adresse, eller null. */
    static String[] parse(String address) {
        if (address == null) return null;
        String a = address.trim();
        int i = a.indexOf(PREFIX);
        if (i < 0) return null;
        String rest = a.substring(i + PREFIX.length()).trim();
        int dot = rest.lastIndexOf('.');
        if (dot <= 0) return null;
        String topic = rest.substring(0, dot), key = rest.substring(dot + 1).replaceAll("\\s", "");
        if (!topic.matches("[a-zA-Z0-9_-]{8,64}")) return null;
        try {
            if (unb64(key).length != 16) return null;
        } catch (Exception e) {
            return null;
        }
        return new String[]{topic, key};
    }

    // Kontakter: navn -> adresse

    static Map<String, String> contacts(Prefs prefs) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            JSONObject j = new JSONObject(prefs.str(CONTACTS, "{}"));
            Iterator<String> it = j.keys();
            while (it.hasNext()) {
                String k = it.next();
                out.put(k, j.getString(k));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static boolean addContact(Prefs prefs, String name, String address) {
        if (name == null || name.trim().isEmpty() || parse(address) == null) return false;
        Map<String, String> c = contacts(prefs);
        c.put(name.trim(), address.trim());
        prefs.setStr(CONTACTS, new JSONObject(c).toString());
        return true;
    }

    static void removeContact(Prefs prefs, String name) {
        Map<String, String> c = contacts(prefs);
        c.remove(name);
        prefs.setStr(CONTACTS, new JSONObject(c).toString());
    }

    // Afsendelse og modtagelse

    /** Sender en besked. Til "dette tv" (address = null) lægges den bare i indbakken. Kaldes fra en baggrundstråd. */
    static void send(Prefs prefs, String address, String from, String text) throws Exception {
        String t = text.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Skriv en besked");
        if (t.length() > MAX_TEXT) t = t.substring(0, MAX_TEXT);
        if (address == null) {
            store(prefs, new Message(from, t, System.currentTimeMillis(), false));
            return;
        }
        String[] a = parse(address);
        if (a == null) throw new IllegalArgumentException("Adressen kan ikke læses");
        JSONObject body = new JSONObject().put("f", from).put("t", t).put("at", System.currentTimeMillis());
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "text/plain");
        Net.request("POST", server(prefs) + "/" + a[0], h, seal(unb64(a[1]), body.toString()));
    }

    /** Henter nye beskeder til dette tv og lægger dem i indbakken. Giver de nye. Kaldes fra en baggrundstråd. */
    static List<Message> poll(Prefs prefs) throws Exception {
        address(prefs);
        String topic = prefs.str(TOPIC, ""), key = prefs.str(KEY, "");
        String since = prefs.str(SINCE, "");
        String url = server(prefs) + "/" + topic + "/json?poll=1&since=" + (since.isEmpty() ? "12h" : since);
        String raw = Net.request("GET", url, null, null);
        List<Message> fresh = new ArrayList<>();
        String last = since;
        for (String line : raw.split("\n")) {
            if (line.trim().isEmpty()) continue;
            JSONObject e;
            try {
                e = new JSONObject(line);
            } catch (Exception ex) {
                continue;
            }
            if (!"message".equals(e.optString("event"))) continue;
            last = e.optString("id", last);
            try {
                JSONObject m = new JSONObject(open(unb64(key), e.optString("message")));
                String text = m.optString("t", "").trim();
                if (text.isEmpty()) continue;
                if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
                Message msg = new Message(m.optString("f", "").trim(), text, m.optLong("at", e.optLong("time") * 1000), false);
                fresh.add(msg);
            } catch (Exception ignored) {
                // Ikke til os eller ødelagt: spring over
            }
        }
        if (!last.equals(since)) prefs.setStr(SINCE, last);
        for (Message m : fresh) store(prefs, m);
        return fresh;
    }

    // Indbakken: de sidste 10

    static synchronized List<Message> inbox(Prefs prefs) {
        List<Message> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.str(INBOX, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Message(o.optString("f"), o.optString("t"), o.optLong("at"), o.optBoolean("seen")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static synchronized void save(Prefs prefs, List<Message> list) {
        JSONArray a = new JSONArray();
        try {
            for (int i = Math.max(0, list.size() - 10); i < list.size(); i++) {
                Message m = list.get(i);
                a.put(new JSONObject().put("f", m.from).put("t", m.text).put("at", m.at).put("seen", m.seen));
            }
        } catch (Exception ignored) {
        }
        prefs.setStr(INBOX, a.toString());
    }

    private static synchronized void store(Prefs prefs, Message m) {
        List<Message> list = inbox(prefs);
        list.add(m);
        save(prefs, list);
    }

    static List<Message> unseen(Prefs prefs) {
        List<Message> out = new ArrayList<>();
        for (Message m : inbox(prefs)) if (!m.seen) out.add(m);
        return out;
    }

    static synchronized void markSeen(Prefs prefs) {
        List<Message> list = inbox(prefs);
        for (Message m : list) m.seen = true;
        save(prefs, list);
    }

    static String title(Message m) {
        return m.from.isEmpty() ? "Besked" : "Besked fra " + m.from;
    }

    // Kryptering

    static String seal(byte[] key, String plain) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return "1." + b64(out);
    }

    static String open(byte[] key, String sealed) throws Exception {
        String s = sealed.trim();
        if (!s.startsWith("1.")) throw new IllegalArgumentException("Ukendt format");
        byte[] all = unb64(s.substring(2));
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, all, 0, 12));
        return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
    }

    private static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static byte[] unb64(String s) {
        return Base64.decode(s, Base64.URL_SAFE | Base64.NO_WRAP);
    }

    static String when(long at) {
        long min = (System.currentTimeMillis() - at) / 60_000;
        if (min < 1) return "lige nu";
        if (min < 60) return min + " min siden";
        if (min < 24 * 60) return (min / 60) + " t siden";
        return new java.text.SimpleDateFormat("d. MMM", new Locale("da")).format(new java.util.Date(at));
    }
}
