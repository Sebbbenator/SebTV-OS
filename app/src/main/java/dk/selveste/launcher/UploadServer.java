package dk.selveste.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lille webside på sticken: telefonsiden og fjernbetjeningen. Kører hele tiden (startes af Home tjenesten).
 * En ny telefon parres med den firecifrede kode, der står på tv'et, mens Telefon skærmen er åben, og får
 * så en lang tilfældig nøgle, den husker. Koden virker kun under parringen.
 */
final class UploadServer {

    static final int PORT = 8765;
    private static final int MAX_BODY = 20 * 1024 * 1024;

    interface Listener {
        void changed();
    }

    /** Koden på tv'et; virker kun, mens parringen er åben. */
    volatile String pin;
    private volatile long pairUntil;
    private volatile long lockedUntil;
    private final Context context;
    private final Backgrounds backgrounds;
    private final Prefs prefs;
    private volatile Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ServerSocket socket;
    private volatile boolean running;
    private final java.util.concurrent.atomic.AtomicInteger wrongPins = new java.util.concurrent.atomic.AtomicInteger();
    /** Egne tråde, så en langsom telefon aldrig blokerer forsidens Jellyfin og HA kald i Net.POOL. */
    private final java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(2);

    private static String onceKey;
    private static String onceCode;
    private static long onceUntil;

    /** Giver en nøgle, som telefonen kan bytte til koden én gang inden for 10 minutter. */
    static synchronized String offerProfileCode(String code) {
        byte[] b = new byte[9];
        new SecureRandom().nextBytes(b);
        onceKey = android.util.Base64.encodeToString(b, android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        onceCode = code;
        onceUntil = System.currentTimeMillis() + 10 * 60 * 1000;
        return onceKey;
    }

    private static synchronized String takeProfileCode(String key) {
        if (key == null || onceKey == null || !onceKey.equals(key) || System.currentTimeMillis() > onceUntil) return null;
        String c = onceCode;
        onceKey = null;
        onceCode = null;
        return c;
    }

    UploadServer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.backgrounds = new Backgrounds(context);
        this.prefs = new Prefs(context);
        this.listener = listener;
        this.pin = String.format(Locale.ROOT, "%04d", new SecureRandom().nextInt(10000));
    }

    private static UploadServer shared;

    /** Den fælles server; startes første gang. */
    static synchronized UploadServer shared(Context context) {
        if (shared == null || !shared.running) {
            UploadServer s = new UploadServer(context, null);
            try {
                s.start();
            } catch (IOException e) {
                ErrorLog.note("Telefonside", e);
                return s;
            }
            shared = s;
        }
        return shared;
    }

    static synchronized void stopShared() {
        if (shared != null) shared.stop();
        shared = null;
    }

    /** Telefon skærmen er åben: ny kode, der virker i 10 minutter. */
    synchronized String openPairing(Listener l) {
        pin = String.format(Locale.ROOT, "%04d", new SecureRandom().nextInt(10000));
        pairUntil = System.currentTimeMillis() + 10 * 60_000L;
        wrongPins.set(0);
        lockedUntil = 0;
        listener = l;
        return pin;
    }

    /** Skærmen er lukket: koden virker et minut mere, så en langsom telefon kan nå det. */
    synchronized void closePairing() {
        pairUntil = Math.min(pairUntil, System.currentTimeMillis() + 60_000L);
        listener = null;
    }

    static final String TOKENS = "phone_tokens";

    private boolean knownToken(String t) {
        if (t == null || t.length() < 20) return false;
        try {
            JSONArray a = new JSONArray(prefs.str(TOKENS, "[]"));
            for (int i = 0; i < a.length(); i++) if (t.equals(a.optString(i))) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private synchronized String newToken() {
        byte[] b = new byte[18];
        new SecureRandom().nextBytes(b);
        String t = android.util.Base64.encodeToString(b, android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        JSONArray out = new JSONArray();
        try {
            JSONArray a = new JSONArray(prefs.str(TOKENS, "[]"));
            for (int i = Math.max(0, a.length() - 11); i < a.length(); i++) out.put(a.getString(i));
        } catch (Exception ignored) {
        }
        out.put(t);
        prefs.setStr(TOKENS, out.toString());
        return t;
    }

    private void forgetToken(String t) {
        JSONArray out = new JSONArray();
        try {
            JSONArray a = new JSONArray(prefs.str(TOKENS, "[]"));
            for (int i = 0; i < a.length(); i++) if (!a.getString(i).equals(t)) out.put(a.getString(i));
        } catch (Exception ignored) {
        }
        prefs.setStr(TOKENS, out.toString());
    }

    /** Glemmer alle parrede telefoner. */
    static void forgetPhones(Prefs prefs) {
        prefs.setStr(TOKENS, "[]");
    }

    void start() throws IOException {
        socket = new ServerSocket(PORT);
        running = true;
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    Socket client = socket.accept();
                    workers.execute(() -> handle(client));
                } catch (IOException e) {
                    if (!running) return;
                }
            }
        }, "jarvis-upload");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        running = false;
        workers.shutdownNow();
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    private void handle(Socket client) {
        try (Socket s = client) {
            s.setSoTimeout(60000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String target = parts[1];

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int i = line.indexOf(':');
                if (i > 0) headers.put(line.substring(0, i).trim().toLowerCase(Locale.ROOT), line.substring(i + 1).trim());
            }
            String path = target;
            Map<String, String> query = new HashMap<>();
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                for (String kv : target.substring(q + 1).split("&")) {
                    int e = kv.indexOf('=');
                    if (e > 0) query.put(kv.substring(0, e), URLDecoder.decode(kv.substring(e + 1), "UTF-8"));
                }
            }

            if (path.equals("/") || path.equals("/index.html")) {
                send(out, 200, "text/html; charset=utf-8", bytes(page()
                        .replace("JARVIS TV", prefs.appName().toUpperCase(Locale.ROOT))
                        .replace("<title>Jarvis TV</title>", "<title>" + prefs.appName() + "</title>")));
                return;
            }
            if (path.startsWith("/font/")) {
                // Temaernes skrifter, så telefonsiden kan klæde sig ud som tv'et
                String name = path.substring(6);
                if (!name.matches("[A-Za-z0-9-]+\\.ttf")) {
                    send(out, 404, "text/plain", bytes("Findes ikke"));
                    return;
                }
                byte[] font;
                try (InputStream fin = context.getAssets().open("fonts/" + name)) {
                    font = readAll(fin);
                } catch (IOException e) {
                    send(out, 404, "text/plain", bytes("Findes ikke"));
                    return;
                }
                send(out, 200, "font/ttf", font, "public, max-age=604800");
                return;
            }
            if (path.equals("/api/profile-once")) {
                // QR'en fra tv'et: koden hentes én gang med en tilfældig nøgle, uden PIN
                String code = takeProfileCode(query.get("key"));
                if (code == null) send(out, 404, "text/plain", bytes("QR koden er udløbet. Vis den igen på tv'et."));
                else send(out, 200, "application/json", bytes(new JSONObject().put("code", code).put("friend", ProfileCode.forFriend(code)).toString()));
                return;
            }
            if (path.equals("/ha-login")) {
                // HA sender brugeren tilbage hertil efter login; koden byttes til et token på tv'et
                send(out, 200, "text/html; charset=utf-8", bytes(haLogin(query)));
                return;
            }

            // Koden tjekkes, før noget af indholdet læses, så en fremmed ikke kan fylde hukommelsen
            String given = headers.getOrDefault("x-pin", query.getOrDefault("pin", ""));
            String issued = null;
            if (!knownToken(given)) {
                long now = System.currentTimeMillis();
                boolean pairing = now < pairUntil && now > lockedUntil;
                if (!pairing || !pin.equals(given)) {
                    if (pairing && wrongPins.incrementAndGet() >= 10) lockedUntil = now + 5 * 60_000L;
                    send(out, 403, "text/plain", bytes(now >= pairUntil
                            ? "Åbn Telefon på tv'et (Indstillinger > Telefon) og skriv koden, der står der"
                            : "Forkert kode"));
                    return;
                }
                if (path.equals("/api/state")) issued = newToken();
            }
            if (path.equals("/api/logout")) {
                forgetToken(given);
                send(out, 200, "application/json", bytes("{}"));
                return;
            }
            if (issued != null && path.equals("/api/state")) {
                JSONObject st = state();
                st.put("token", issued);
                send(out, 200, "application/json", bytes(st.toString()));
                return;
            }

            int length = 0;
            try {
                length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            } catch (NumberFormatException ignored) {
            }
            if (path.equals("/api/konami-clip") && method.equals("POST")) {
                // Video skrives direkte til disken i stedet for at ligge i hukommelsen
                if (length > KonamiClip.MAX || length < 0) {
                    send(out, 413, "text/plain", bytes("Klippet må højst fylde 60 MB"));
                    return;
                }
                try {
                    if (length == 0) KonamiClip.delete(context);
                    else KonamiClip.save(context, in, length);
                } catch (IOException e) {
                    send(out, 400, "text/plain", bytes(String.valueOf(e.getMessage())));
                    return;
                }
                send(out, 200, "application/json", bytes(state().toString()));
                return;
            }
            if (length < 0 || length > MAX_BODY) {
                send(out, 413, "text/plain", bytes("For stort billede"));
                return;
            }
            byte[] body = new byte[length];
            int read = 0;
            while (read < length) {
                int n = in.read(body, read, length - read);
                if (n < 0) break;
                read += n;
            }

            route(method, path, query, body, out);
        } catch (Exception ignored) {
        }
    }

    private void route(String method, String path, Map<String, String> query, byte[] body, OutputStream out) throws Exception {
        if (path.equals("/api/state")) {
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.startsWith("/img/")) {
            File f = backgrounds.file(URLDecoder.decode(path.substring(5), "UTF-8"));
            if (f == null) {
                send(out, 404, "text/plain", bytes("Findes ikke"));
                return;
            }
            try (InputStream fin = new FileInputStream(f)) {
                send(out, 200, "image/jpeg", Net.readAll(fin));
            }
        } else if (path.equals("/api/upload") && method.equals("POST")) {
            if (!isImage(body)) {
                send(out, 415, "text/plain", bytes("Ikke et billede"));
                return;
            }
            String name = backgrounds.save(body);
            backgrounds.select(name);
            notifyChanged();
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/api/profile-picture") && method.equals("POST")) {
            if (body.length > 0 && !isImage(body)) {
                send(out, 415, "text/plain", bytes("Ikke et billede"));
                return;
            }
            if (body.length == 0) ProfilePicture.delete(context);
            else ProfilePicture.save(context, body);
            notifyChanged();
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/profil.jpg")) {
            File pf = ProfilePicture.file(context);
            if (!pf.exists()) {
                send(out, 404, "text/plain", bytes("Intet billede"));
                return;
            }
            try (InputStream fin = new FileInputStream(pf)) {
                send(out, 200, "image/jpeg", Net.readAll(fin));
            }
        } else if (path.equals("/api/select") && method.equals("POST")) {
            backgrounds.select(query.get("name"));
            notifyChanged();
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/api/delete") && method.equals("POST")) {
            backgrounds.delete(query.get("name"));
            notifyChanged();
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/api/post-address")) {
            send(out, 200, "application/json", bytes(new JSONObject().put("address", Post.address(prefs)).toString()));
        } else if (path.equals("/api/post-send") && method.equals("POST")) {
            JSONObject j = new JSONObject(new String(body, StandardCharsets.UTF_8));
            String to = j.optString("to", "");
            String address = to.isEmpty() ? null : Post.contacts(prefs).get(to);
            if (!to.isEmpty() && address == null) {
                send(out, 400, "text/plain", bytes("Kender ikke " + to));
                return;
            }
            String from = prefs.profileName();
            try {
                Post.send(prefs, address, from, j.optString("text", ""));
            } catch (IllegalArgumentException e) {
                send(out, 400, "text/plain", bytes(e.getMessage()));
                return;
            } catch (Exception e) {
                send(out, 502, "text/plain", bytes("Beskeden kunne ikke sendes. Er der internet?"));
                return;
            }
            if (address == null) {
                String text = j.optString("text", "").trim();
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (!MainActivity.postArrived()) HomeService.testMessage((from.isEmpty() ? "Besked" : "Besked fra " + from) + " | " + text);
                });
            }
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/api/post-contact") && method.equals("POST")) {
            JSONObject j = new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (j.has("remove")) Post.removeContact(prefs, j.getString("remove"));
            else if (!Post.addContact(prefs, j.optString("name"), j.optString("address"))) {
                send(out, 400, "text/plain", bytes("Skriv et navn og indsæt adressen (den starter med " + Post.PREFIX + ")"));
                return;
            }
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/api/profile-code")) {
            String code = ProfileCode.export(context);
            send(out, 200, "application/json", bytes(new JSONObject().put("code", code).put("friend", ProfileCode.forFriend(code)).toString()));
        } else if (path.equals("/api/profile-import") && method.equals("POST")) {
            try {
                ProfileCode.apply(context, new String(body, StandardCharsets.UTF_8));
            } catch (Exception e) {
                send(out, 400, "text/plain", bytes("Koden kunne ikke læses: " + e.getMessage()));
                return;
            }
            notifyChanged();
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/api/ha-login-url")) {
            String haUrl = query.getOrDefault("haUrl", prefs.haUrl());
            if (haUrl == null || haUrl.trim().isEmpty()) {
                send(out, 400, "text/plain", bytes("Skriv Home Assistant adressen først"));
                return;
            }
            String host = query.getOrDefault("host", "");
            if (!host.matches("[0-9a-zA-Z.:\\[\\]-]+")) host = UploadActivity.ip() + ":" + PORT;
            send(out, 200, "application/json", bytes(new JSONObject().put("url", HaLogin.startUrl(haUrl, "http://" + host)).toString()));
        } else if (path.equals("/api/remote") && method.equals("POST")) {
            JSONObject j = new JSONObject(new String(body, StandardCharsets.UTF_8));
            String err;
            if (j.has("key")) err = Remote.press(context, j.getString("key"));
            else err = Remote.type(context, j.has("text") ? j.getString("text") : null, j.optBoolean("enter", false));
            if (err != null) send(out, 409, "text/plain", bytes(err));
            else send(out, 200, "application/json", bytes("{}"));
        } else if (path.equals("/api/open") && method.equals("POST")) {
            JSONObject j = new JSONObject(new String(body, StandardCharsets.UTF_8));
            try {
                String app = Send.open(context, j.optString("url"));
                send(out, 200, "application/json", bytes(new JSONObject().put("app", app).toString()));
            } catch (Exception e) {
                send(out, 409, "text/plain", bytes(String.valueOf(e.getMessage())));
            }
        } else if (path.equals("/api/jf-search")) {
            Jellyfin jf = new Jellyfin(prefs);
            if (!jf.configured()) {
                send(out, 409, "text/plain", bytes("Tv'et er ikke logget ind på Jellyfin"));
                return;
            }
            String q = query.getOrDefault("q", "").trim();
            JSONArray res = new JSONArray();
            try {
                for (Jellyfin.Item it : q.isEmpty() ? jf.resume() : jf.search(q)) {
                    res.put(new JSONObject().put("id", it.id).put("type", it.type).put("title", it.title)
                            .put("sub", it.subtitle).put("img", it.imageUrl).put("progress", it.progress).put("pos", it.positionMs));
                }
            } catch (Exception e) {
                send(out, 502, "text/plain", bytes("Jellyfin svarede ikke"));
                return;
            }
            send(out, 200, "application/json", bytes(new JSONObject().put("items", res).toString()));
        } else if (path.equals("/api/jf-play") && method.equals("POST")) {
            JSONObject j = new JSONObject(new String(body, StandardCharsets.UTF_8));
            try {
                Send.play(context, j.getString("id"), j.optString("type"), j.optLong("pos", 0));
                send(out, 200, "application/json", bytes("{}"));
            } catch (Exception e) {
                send(out, 409, "text/plain", bytes(String.valueOf(e.getMessage())));
            }
        } else if (path.equals("/api/rive") && method.equals("POST")) {
            // Rive fil fra telefonen (rå bytes, navnet i ?name=); tom krop + ?delete= sletter
            String del = query.get("delete");
            try {
                if (del != null) RiveTest.delete(context, del);
                else MainActivity.riveFromOutside(RiveTest.save(context, query.get("name"), body));
            } catch (IOException e) {
                send(out, 415, "text/plain", bytes(String.valueOf(e.getMessage())));
                return;
            }
            send(out, 200, "application/json", bytes(state().toString()));
        } else if (path.equals("/api/rive-test") && method.equals("POST")) {
            String name = body.length == 0 ? null : new JSONObject(new String(body, StandardCharsets.UTF_8)).optString("name", null);
            if (MainActivity.riveFromOutside(name)) send(out, 200, "application/json", bytes("{}"));
            else send(out, 409, "text/plain", bytes("Vises, når du går til forsiden på tv'et"));
        } else if (path.equals("/api/recap")) {
            Jellyfin jf = new Jellyfin(prefs);
            if (!jf.configured()) {
                send(out, 409, "text/plain", bytes("Tv'et er ikke logget ind på Jellyfin"));
                return;
            }
            if (method.equals("POST")) {
                if (!MainActivity.recapFromPhone()) send(out, 409, "text/plain", bytes("Gå til forsiden på tv'et først"));
                else send(out, 200, "application/json", bytes("{}"));
                return;
            }
            try {
                send(out, 200, "application/json", bytes(MonthRecap.fetch(jf).json().toString()));
            } catch (Exception e) {
                send(out, 502, "text/plain", bytes("Jellyfin svarede ikke"));
            }
        } else if (path.equals("/api/settings") && method.equals("POST")) {
            JSONObject j = new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (j.has("mode")) prefs.setBgMode(j.getString("mode"));
            if (j.has("random")) prefs.setBgRandom(j.getBoolean("random"));
            JSONObject flags = j.optJSONObject("flags");
            if (flags != null) {
                JSONArray names = flags.names();
                for (int i = 0; names != null && i < names.length(); i++) {
                    String k = names.getString(i);
                    prefs.setFlag(k, flags.getBoolean(k));
                }
            }
            prefs.setHa(j.optString("haUrl", null), j.optString("haToken", null), j.optString("haButtons", null));
            if (j.optBoolean("haForget", false)) prefs.clearHaToken();
            if (j.has("theme")) Themes.apply(prefs, j.getString("theme"));
            JSONObject look = j.optJSONObject("look");
            if (look != null) {
                JSONArray keys = look.names();
                for (int i = 0; keys != null && i < keys.length(); i++) {
                    Looks.Option o = Looks.find(keys.getString(i));
                    if (o != null) Looks.set(prefs, o, look.getString(keys.getString(i)));
                }
                MainActivity.appsDirty = true;
            }
            JSONObject p = j.optJSONObject("profile");
            if (p != null) {
                prefs.setProfile(p.optString("name", null), p.optString("greeting", null),
                        p.optString("partner", null), p.optString("together", null));
                if (p.has("city")) {
                    String city = p.getString("city").trim();
                    if (city.isEmpty()) Weather.set(prefs, null);
                    else if (!city.equalsIgnoreCase(Weather.city(prefs))) {
                        java.util.List<Weather.Place> places = Weather.search(city);
                        if (places.isEmpty()) {
                            send(out, 400, "text/plain", bytes("Fandt ingen by, der hedder " + city));
                            return;
                        }
                        Weather.set(prefs, places.get(0));
                        try {
                            Weather.fetch(prefs);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            JSONObject dayAdd = j.optJSONObject("dayAdd");
            if (dayAdd != null) {
                int[] d = Days.parse(dayAdd.optString("date"));
                if (d == null || dayAdd.optString("name").trim().isEmpty()) {
                    send(out, 400, "text/plain", bytes("Skriv navn og dato"));
                    return;
                }
                Days.add(prefs, new Days.Day(dayAdd.optString("name").trim(), dayAdd.optString("kind", Days.BIRTHDAY), d[0], d[1], d[2]));
            }
            if (j.has("dayRemove")) Days.remove(prefs, j.getInt("dayRemove"));
            JSONObject boot = j.optJSONObject("bootText");
            if (boot != null) {
                prefs.setStr("boot_title", boot.optString("title", "").trim());
                prefs.setStr("boot_sub", boot.optString("sub", "").trim());
            }
            if (j.has("profileAsk")) prefs.setFlag(Profiles.ASK, j.getBoolean("profileAsk"));
            String newName = j.optString("profileNew", "").trim();
            if (!newName.isEmpty()) {
                if (newName.length() > 30) newName = newName.substring(0, 30);
                Profiles.save(context, newName);
            }
            String switchTo = j.optString("profileSwitch", "");
            if (!switchTo.isEmpty()) {
                if (!Profiles.names(prefs).contains(switchTo)) {
                    send(out, 400, "text/plain", bytes("Profilen findes ikke"));
                    return;
                }
                Profiles.load(context, switchTo);
                MainActivity.lookChangedFromOutside();
            }
            String del = j.optString("profileDelete", "");
            if (!del.isEmpty()) Profiles.delete(context, del);
            notifyChanged();
            send(out, 200, "application/json", bytes(state().toString()));
        } else {
            send(out, 404, "text/plain", bytes("Findes ikke"));
        }
    }

    private JSONObject state() throws Exception {
        JSONObject j = new JSONObject();
        j.put("konamiClip", KonamiClip.exists(context));
        JSONArray rive = new JSONArray();
        for (java.io.File f : RiveTest.files(context)) rive.put(new JSONObject().put("name", f.getName()).put("kb", (f.length() + 1023) / 1024));
        j.put("rive", rive);
        JSONArray imgs = new JSONArray();
        for (String n : backgrounds.list()) imgs.put(n);
        j.put("images", imgs);
        j.put("current", prefs.bgCurrent());
        j.put("mode", prefs.bgMode());
        j.put("random", prefs.bgRandom());
        JSONObject flags = new JSONObject();
        for (String[] f : Features.ALL) flags.put(f[0], Features.on(prefs, f[0]));
        j.put("flags", flags);
        JSONArray labels = new JSONArray();
        for (String[] f : Features.ALL) labels.put(new JSONArray().put(f[0]).put(f[1]).put(f.length > 3 ? f[3] : ""));
        j.put("flagLabels", labels);
        j.put("haUrl", prefs.haUrl());
        j.put("haTokenSet", !prefs.haToken().isEmpty());
        j.put("haButtons", prefs.haButtons());
        JSONArray looks = new JSONArray();
        for (Looks.Option o : Looks.ALL) {
            JSONArray values = new JSONArray();
            for (String[] v : o.values) values.put(new JSONArray().put(v[0]).put(v[1]));
            looks.put(new JSONObject().put("key", o.key).put("title", o.title).put("group", o.group)
                    .put("value", Looks.get(prefs, o)).put("values", values));
        }
        j.put("looks", looks);
        JSONArray themes = new JSONArray();
        for (String[] t : Themes.ALL) themes.put(new JSONArray().put(t[0]).put(t[1] + " · " + t[2]));
        j.put("themes", themes);
        j.put("theme", prefs.theme());
        j.put("profile", new JSONObject()
                .put("name", prefs.profileName())
                .put("greeting", prefs.customGreeting())
                .put("partner", prefs.partnerName())
                .put("together", prefs.togetherSinceText())
                .put("picture", ProfilePicture.file(context).exists())
                .put("city", Weather.city(prefs)));
        JSONArray days = new JSONArray();
        java.util.Calendar now = java.util.Calendar.getInstance();
        for (Days.Day d : Days.all(prefs)) {
            long u = Days.until(d, now);
            days.put(new JSONObject().put("name", d.name).put("date", d.dateText()).put("kindName", d.kindName())
                    .put("kind", d.kind).put("pretty", pretty(d)).put("inDays", u)
                    .put("when", u < 0 ? "overstået" : u == 0 ? "i dag" : u == 1 ? "i morgen" : "om " + u + " dage"));
        }
        j.put("days", days);
        String together = Greeting.together(prefs);
        j.put("together", together.isEmpty() ? "" : together.split(" {2}· {2}")[0]);
        j.put("accent", String.format(Locale.ROOT, "#%06X", prefs.accentColor() & 0xFFFFFF));
        String wx = Weather.cached(prefs);
        j.put("wx", wx);
        JSONArray profiles = new JSONArray();
        for (String n : Profiles.names(prefs)) profiles.put(n);
        j.put("profiles", profiles);
        j.put("activeProfile", Profiles.active(prefs));
        j.put("profileAsk", prefs.flag(Profiles.ASK, false));
        j.put("bootTitle", prefs.str("boot_title", ""));
        j.put("bootSub", prefs.str("boot_sub", ""));
        JSONArray contacts = new JSONArray();
        for (String n : Post.contacts(prefs).keySet()) contacts.put(n);
        j.put("contacts", contacts);
        return j;
    }

    private static final String[] MONTHS = {"jan.", "feb.", "marts", "april", "maj", "juni", "juli", "aug.", "sep.", "okt.", "nov.", "dec."};

    /** "12. marts 1970" eller "12. marts". */
    static String pretty(Days.Day d) {
        return d.day + ". " + MONTHS[d.month] + (d.year > 0 ? " " + d.year : "");
    }

    private String page;

    /** Telefonsiden ligger i assets/telefon.html. */
    private synchronized String page() throws IOException {
        if (page == null) {
            try (InputStream in = context.getAssets().open("telefon.html")) {
                page = new String(readAll(in), StandardCharsets.UTF_8);
            }
        }
        return page;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }

    /** Kun rigtige billeder gemmes: Android skal kunne læse deres størrelse. */
    private static boolean isImage(byte[] data) {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, o);
        return o.outWidth > 0 && o.outHeight > 0;
    }

    /** Svar på HA login; fyldes af HaLogin (QR login), indtil da en kort tekst. */
    private String haLogin(Map<String, String> query) {
        return HaLogin.finish(context, query);
    }

    private void notifyChanged() {
        if (listener != null) main.post(listener::changed);
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) return null;
        }
        if (c < 0 && sb.length() == 0) return null;
        return sb.toString();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        send(out, code, type, body, "no-store");
    }

    private static void send(OutputStream out, int code, String type, byte[] body, String cache) throws IOException {
        String status = code == 200 ? "OK" : code == 403 ? "Forbidden" : code == 404 ? "Not Found" : code == 413 ? "Too Large" : "Error";
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: " + cache + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }
}
