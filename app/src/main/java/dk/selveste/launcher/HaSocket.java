package dk.selveste.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.net.ssl.SSLSocketFactory;

/**
 * Fast forbindelse til Home Assistant (websocket), så tv'et får ændringer i samme øjeblik de sker:
 * tilstande, knapper, statuslinje og beskeder. Erstatter at spørge HA hvert 15. sekund.
 * Lille egen websocket klient uden biblioteker. Kører i HomeService, så den lever, mens tv'et er tændt.
 * Hvis forbindelsen ikke kan laves, spørger appen bare som før.
 */
final class HaSocket {

    interface Listener {
        /** Kaldes på hovedtråden med de entiteter, der har ændret sig. */
        void changed(Map<String, JSONObject> changed);
    }

    private static HaSocket instance;

    static synchronized HaSocket get(Context context) {
        if (instance == null) instance = new HaSocket(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, JSONObject> states = new LinkedHashMap<>();
    private volatile boolean wanted;
    private volatile boolean connected;
    private volatile Socket socket;
    private Set<String> subscribed = new LinkedHashSet<>();
    private Thread thread;
    private int failures;

    private HaSocket(Context context) {
        this.context = context;
    }

    boolean connected() {
        return connected;
    }

    synchronized Map<String, JSONObject> snapshot() {
        return new LinkedHashMap<>(states);
    }

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Start (eller genstart, hvis listen af entiteter er ændret). Kan kaldes mange gange. */
    synchronized void start() {
        Prefs prefs = new Prefs(context);
        HomeAssistant ha = new HomeAssistant(prefs);
        if (!ha.configured()) {
            stop();
            return;
        }
        Set<String> want = wantedEntities(prefs, ha);
        if (wanted && thread != null && thread.isAlive() && want.equals(subscribed)) return;
        stop();
        wanted = true;
        subscribed = want;
        Thread t = new Thread(this::loop, "ha-websocket");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    synchronized void stop() {
        wanted = false;
        connected = false;
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private static Set<String> wantedEntities(Prefs prefs, HomeAssistant ha) {
        Set<String> ids = new LinkedHashSet<>(ha.needed());
        ids.remove("auto");
        if (!prefs.haMessage().isEmpty()) ids.add(prefs.haMessage());
        return ids;
    }

    private void loop() {
        while (wanted) {
            try {
                session();
                failures = 0;
            } catch (Exception e) {
                if (wanted) ErrorLog.note("HA websocket", e);
                failures++;
            }
            connected = false;
            if (!wanted) return;
            // Vent længere og længere ved gentagne fejl (5 sek. til 5 min.)
            long wait = Math.min(300_000L, 5000L * (1L << Math.min(6, failures)));
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void session() throws Exception {
        Prefs prefs = new Prefs(context);
        URI base = new URI(prefs.haUrl());
        boolean tls = "https".equalsIgnoreCase(base.getScheme());
        int port = base.getPort() > 0 ? base.getPort() : (tls ? 443 : 80);
        String host = base.getHost();
        Socket plain = new Socket();
        socket = plain;
        Socket s = plain;
        try {
            plain.connect(new InetSocketAddress(host, port), 5000);
            // TLS oven på den forbundne socket, så værtsnavnet (SNI) kommer med
            if (tls) {
                javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, host, port, true);
                ssl.startHandshake();
                if (!javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ssl.getSession())) {
                    ssl.close();
                    throw new IOException("HA's certifikat passer ikke til " + host);
                }
                s = ssl;
            }
            socket = s;
            s.setSoTimeout(90_000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            handshake(in, out, host, port, (base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "")) + "/api/websocket");

            JSONObject first = new JSONObject(readMessage(in, out));
            if (!"auth_required".equals(first.optString("type"))) throw new IOException("Uventet svar fra HA");
            send(out, new JSONObject().put("type", "auth").put("access_token", HaLogin.token(prefs)).toString());
            JSONObject auth = new JSONObject(readMessage(in, out));
            if (!"auth_ok".equals(auth.optString("type"))) throw new IOException("HA afviste login: " + auth.optString("message"));

            JSONArray ids = new JSONArray();
            for (String id : subscribed) ids.put(id);
            send(out, new JSONObject().put("id", 1).put("type", "subscribe_entities").put("entity_ids", ids).toString());
            connected = true;
            long lastPing = System.currentTimeMillis();
            int nextId = 2;
            while (wanted) {
                String msg;
                try {
                    msg = readMessage(in, out);
                } catch (java.net.SocketTimeoutException e) {
                    msg = null;
                }
                if (System.currentTimeMillis() - lastPing > 45_000) {
                    send(out, new JSONObject().put("id", nextId++).put("type", "ping").toString());
                    lastPing = System.currentTimeMillis();
                }
                if (msg == null) continue;
                JSONObject j = new JSONObject(msg);
                if ("event".equals(j.optString("type")) && j.optInt("id") == 1) apply(j.optJSONObject("event"));
            }
        } finally {
            connected = false;
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** HA's korte format: "a" = nye hele tilstande, "c" = ændringer (+ sat, - fjernet), "r" = fjernet. */
    private void apply(JSONObject ev) throws Exception {
        if (ev == null) return;
        Map<String, JSONObject> changed = new LinkedHashMap<>();
        synchronized (this) {
            JSONObject added = ev.optJSONObject("a");
            if (added != null) {
                Iterator<String> it = added.keys();
                while (it.hasNext()) {
                    String id = it.next();
                    JSONObject c = added.getJSONObject(id);
                    JSONObject st = new JSONObject();
                    st.put("entity_id", id);
                    st.put("state", c.optString("s"));
                    st.put("attributes", c.optJSONObject("a") == null ? new JSONObject() : c.optJSONObject("a"));
                    st.put("last_changed", lastChanged(c));
                    states.put(id, st);
                    changed.put(id, st);
                }
            }
            JSONObject diff = ev.optJSONObject("c");
            if (diff != null) {
                Iterator<String> it = diff.keys();
                while (it.hasNext()) {
                    String id = it.next();
                    JSONObject st = states.get(id);
                    if (st == null) continue;
                    st = new JSONObject(st.toString());
                    JSONObject d = diff.getJSONObject(id);
                    JSONObject plus = d.optJSONObject("+");
                    if (plus != null) {
                        if (plus.has("s")) st.put("state", plus.optString("s"));
                        // HA sender kun "lc", når den afviger fra "lu"; en ny tilstand uden "lc" er ændret ved "lu"
                        if (plus.has("lc") || plus.has("s")) st.put("last_changed", lastChanged(plus));
                        JSONObject a = plus.optJSONObject("a");
                        if (a != null) {
                            JSONObject attrs = st.optJSONObject("attributes");
                            Iterator<String> ak = a.keys();
                            while (ak.hasNext()) {
                                String k = ak.next();
                                attrs.put(k, a.get(k));
                            }
                        }
                    }
                    JSONObject minus = d.optJSONObject("-");
                    JSONArray gone = minus == null ? null : minus.optJSONArray("a");
                    for (int i = 0; gone != null && i < gone.length(); i++) st.optJSONObject("attributes").remove(gone.getString(i));
                    states.put(id, st);
                    changed.put(id, st);
                }
            }
            JSONArray removed = ev.optJSONArray("r");
            for (int i = 0; removed != null && i < removed.length(); i++) states.remove(removed.getString(i));
        }
        if (changed.isEmpty()) return;
        main.post(() -> {
            for (Listener l : listeners) l.changed(changed);
        });
    }

    /** "lc" er sekunder siden 1970; gemmes i samme form som HA's REST svar, så de kan sammenlignes. */
    private static String lastChanged(JSONObject c) {
        double secs = c.has("lc") ? c.optDouble("lc") : c.has("lu") ? c.optDouble("lu") : System.currentTimeMillis() / 1000.0;
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date((long) (secs * 1000))) + "+00:00";
    }

    /** Sekund-præcis nøgle for et tidspunkt fra enten websocket eller REST. */
    static String second(String lastChanged) {
        return lastChanged == null || lastChanged.length() < 19 ? String.valueOf(lastChanged) : lastChanged.substring(0, 19);
    }

    // Websocket rammer (RFC 6455), kun det HA bruger: tekst, fortsættelse, ping/pong og luk

    private static void handshake(InputStream in, OutputStream out, String host, int port, String path) throws IOException {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        String req = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + ":" + port + "\r\n"
                + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP) + "\r\n"
                + "Sec-WebSocket-Version: 13\r\nUser-Agent: JarvisTV\r\n\r\n";
        out.write(req.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        StringBuilder head = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            head.append((char) c);
            if (head.length() > 8192) throw new IOException("For langt svar");
            if (head.toString().endsWith("\r\n\r\n")) break;
        }
        if (!head.toString().startsWith("HTTP/1.1 101")) {
            String line = head.toString().split("\r\n")[0];
            throw new IOException("Websocket afvist: " + line);
        }
    }

    private static void send(OutputStream out, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(0x81);
        if (data.length < 126) {
            f.write(0x80 | data.length);
        } else if (data.length < 65536) {
            f.write(0x80 | 126);
            f.write(data.length >> 8);
            f.write(data.length & 0xff);
        } else {
            f.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) f.write((int) ((long) data.length >> (8 * i)) & 0xff);
        }
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
        f.write(mask);
        for (int i = 0; i < data.length; i++) f.write(data[i] ^ mask[i & 3]);
        synchronized (out) {
            out.write(f.toByteArray());
            out.flush();
        }
    }

    private static void sendControl(OutputStream out, int opcode, byte[] payload) throws IOException {
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(0x80 | opcode);
        f.write(0x80 | payload.length);
        f.write(mask);
        for (int i = 0; i < payload.length; i++) f.write(payload[i] ^ mask[i & 3]);
        synchronized (out) {
            out.write(f.toByteArray());
            out.flush();
        }
    }

    /** Læser én hel tekstbesked (samler fortsættelsesrammer). Svarer selv på ping. */
    private static String readMessage(InputStream in, OutputStream out) throws IOException {
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        while (true) {
            int b0 = readByte(in);
            int b1 = readByte(in);
            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0f;
            long len = b1 & 0x7f;
            if (len == 126) len = (readByte(in) << 8) | readByte(in);
            else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | readByte(in);
            }
            if (len > 8 * 1024 * 1024) throw new IOException("For stor besked fra HA");
            byte[] mask = null;
            if ((b1 & 0x80) != 0) {
                mask = new byte[4];
                for (int i = 0; i < 4; i++) mask[i] = (byte) readByte(in);
            }
            byte[] payload = new byte[(int) len];
            int read = 0;
            while (read < len) {
                int n = in.read(payload, read, (int) len - read);
                if (n < 0) throw new EOFException();
                read += n;
            }
            if (mask != null) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
            if (opcode == 0x8) throw new EOFException("HA lukkede forbindelsen");
            if (opcode == 0x9) {
                sendControl(out, 0xA, payload);
                continue;
            }
            if (opcode == 0xA) continue;
            msg.write(payload);
            if (fin) return new String(msg.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException();
        return b;
    }

    /** Til tests og fejlfinding: hvilke entiteter der lyttes på. */
    synchronized List<String> subscribedList() {
        return new ArrayList<>(subscribed);
    }
}
