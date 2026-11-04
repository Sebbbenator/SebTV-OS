package dk.selveste.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.util.Calendar;

/** Den del af appen, der kan testes uden et tv: tekster, kommandoer, websocket rammer og ADB signatur. */
public class LogicTest {

    private static Calendar day(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d);
        return c;
    }

    @Test
    public void dageSammen() {
        int[] since = {2026, 0, 4};
        assertEquals("♥ 300 dage sammen med Anna  ·  I dag: 300 dage!", Greeting.together(since, "Anna", day(2026, 10, 31)));
        assertTrue(Greeting.together(since, "", day(2026, 11, 1)).startsWith("♥ 301 dage sammen  ·  "));
        assertTrue(Greeting.together(since, "", day(2027, 1, 4)).contains("I dag: 1 år sammen!"));
        assertEquals("", Greeting.together(since, "", day(2025, 12, 1)));
    }

    @Test
    public void temaKommandoer() {
        assertEquals("ironman", TvCommands.theme("Iron Man"));
        assertEquals("ironman", TvCommands.theme("ironman"));
        assertEquals("efteraar", TvCommands.theme("efterår"));
        assertEquals("spiderman", TvCommands.theme("spider"));
        assertNull(TvCommands.theme("findes ikke"));
        assertTrue(TvCommands.isCommand("/rolig"));
        assertTrue(!TvCommands.isCommand("Aftensmad er klar"));
    }

    @Test
    public void adresserRettes() {
        assertEquals("http://192.168.111.25:8096", Prefs.normalizeUrl(" 192.168.111.25:8096/ "));
        assertEquals("https://ha.example.dk", Prefs.normalizeUrl("HTTPS://ha.example.dk//"));
        assertEquals("http://homeassistant.local:8123", Prefs.normalizeUrl("http://homeassistant.local:8123"));
        assertEquals("", Prefs.normalizeUrl("  "));
    }

    @Test
    public void tidspunktSammenlignes() {
        assertEquals("2026-10-31T12:00:05", HaSocket.second("2026-10-31T12:00:05.123456+00:00"));
    }

    @Test
    public void websocketRammer() throws Exception {
        // Klientens rammer er maskerede; læseren skal kunne læse både dem og serverens umaskerede
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String big = new String(new char[70000]).replace('\0', 'x');
        HaSocket.send(out, "{\"type\":\"auth\"}");
        HaSocket.send(out, big);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        ByteArrayOutputStream ignored = new ByteArrayOutputStream();
        assertEquals("{\"type\":\"auth\"}", HaSocket.readMessage(in, ignored));
        assertEquals(big, HaSocket.readMessage(in, ignored));

        // Server: fragmenteret besked med en ping imellem, som skal besvares med pong
        byte[] a = "{\"a\":".getBytes(StandardCharsets.UTF_8);
        byte[] b = "1}".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream server = new ByteArrayOutputStream();
        server.write(0x01);
        server.write(a.length);
        server.write(a);
        server.write(0x89);
        server.write(0);
        server.write(0x80);
        server.write(b.length);
        server.write(b);
        ByteArrayOutputStream pong = new ByteArrayOutputStream();
        assertEquals("{\"a\":1}", HaSocket.readMessage(new ByteArrayInputStream(server.toByteArray()), pong));
        assertEquals(0x8A, pong.toByteArray()[0] & 0xff);
    }

    @Test
    public void adbSignatur() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        byte[] token = new byte[20];
        for (int i = 0; i < token.length; i++) token[i] = (byte) (i * 7);
        byte[] sig = LocalAdb.sign((RSAPrivateKey) kp.getPrivate(), token);
        byte[] prefix = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
        byte[] info = new byte[prefix.length + token.length];
        System.arraycopy(prefix, 0, info, 0, prefix.length);
        System.arraycopy(token, 0, info, prefix.length, token.length);
        Signature v = Signature.getInstance("NONEwithRSA");
        v.initVerify(kp.getPublic());
        v.update(info);
        assertTrue(v.verify(sig));
        assertEquals(256, sig.length);
        assertTrue(new BigInteger(1, sig).signum() > 0);
    }

    @Test
    public void maerkedage() {
        assertTrue(java.util.Arrays.equals(new int[]{1970, 2, 12}, Days.parse("1970-03-12")));
        assertTrue(java.util.Arrays.equals(new int[]{1970, 2, 12}, Days.parse("12-03-1970")));
        assertTrue(java.util.Arrays.equals(new int[]{0, 2, 12}, Days.parse("12/3")));
        assertNull(Days.parse("32-13-2000"));
        Calendar today = day(2026, 3, 9);
        Days.Day mor = new Days.Day("Mor", Days.BIRTHDAY, 1970, 2, 12);
        assertEquals("Mor fylder 56 om 3 dage", Days.phrase(mor, today, "Seb"));
        assertEquals("I dag fylder Mor 56", Days.phrase(mor, day(2026, 3, 12), "Seb"));
        Days.Day hans = new Days.Day("Hans", Days.BIRTHDAY, 0, 2, 10);
        assertEquals("Hans' fødselsdag i morgen", Days.phrase(hans, today, "Seb"));
        Days.Day mig = new Days.Day("seb", Days.BIRTHDAY, 2000, 2, 9);
        assertEquals("Tillykke med dagen!", Days.phrase(mig, today, "Seb"));
        Days.Day rf = new Days.Day("Roskilde", Days.ONCE, 2026, 5, 27);
        assertEquals("Roskilde om 110 dage", Days.phrase(rf, today, "Seb"));
        assertEquals(-1, Days.until(rf, day(2026, 7, 1)));
        // Årsskifte og skuddag
        assertEquals(1, Days.until(new Days.Day("Nytår", Days.YEARLY, 0, 0, 1), day(2026, 12, 31)));
        assertEquals(0, Days.until(new Days.Day("Leap", Days.BIRTHDAY, 2000, 1, 29), day(2027, 2, 28)));
    }

    @Test
    public void vejrkoder() {
        assertEquals("Sol", Weather.describe(0, true, 2));
        assertEquals("Klart", Weather.describe(1, false, 2));
        assertEquals("Blæst", Weather.describe(3, true, 14));
        assertEquals("Regn", Weather.describe(61, true, 14));
        assertEquals("Sne", Weather.describe(73, true, 2));
    }
}
