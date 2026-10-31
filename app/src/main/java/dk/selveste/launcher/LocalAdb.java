package dk.selveste.launcher;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * En lille ADB klient, så tv'et kan give sig selv tilladelser uden en pc: den forbinder til sin egen
 * ADB (127.0.0.1:5555), som når en pc gør det. Første gang viser tv'et "Tillad fejlfinding?", og man
 * trykker Tillad. Bagefter kan ADB slås fra igen; det tilladelserne giver, bliver.
 * Protokollen: https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/protocol.txt
 */
final class LocalAdb implements AutoCloseable {

    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int VERSION = 0x01000000, MAX_DATA = 4096;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    /** SHA-1 DigestInfo; ADB signerer tokenet, som om det var en SHA-1 værdi. */
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int nextLocal = 1;

    private LocalAdb(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = socket.getOutputStream();
    }

    /**
     * Forbinder og logger ind. Er nøglen ikke godkendt endnu, viser tv'et sin "Tillad" dialog, og vi
     * venter op til waitMs på, at man trykker. Kaldes fra en baggrundstråd.
     */
    static LocalAdb connect(Context context, int waitMs) throws Exception {
        KeyPair keys = keys(context);
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
        } catch (IOException e) {
            s.close();
            throw new IOException("ADB er slået fra. Slå \"ADB debugging\" til under Indstillinger > My Fire TV > Developer options.");
        }
        LocalAdb adb = new LocalAdb(s);
        try {
            s.setSoTimeout(10_000);
            adb.write(CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.US_ASCII));
            boolean sentSignature = false;
            while (true) {
                Msg m = adb.read();
                if (m.cmd == CNXN) break;
                if (m.cmd != AUTH || m.arg0 != AUTH_TOKEN) throw new IOException("Uventet svar fra ADB");
                if (!sentSignature) {
                    sentSignature = true;
                    adb.write(AUTH, AUTH_SIGNATURE, 0, sign((RSAPrivateKey) keys.getPrivate(), m.data));
                } else {
                    // Nøglen kendes ikke: send den, så tv'et spørger, og vent på svaret
                    adb.write(AUTH, AUTH_RSAPUBLICKEY, 0, publicKey((RSAPublicKey) keys.getPublic()));
                    s.setSoTimeout(waitMs);
                }
            }
            s.setSoTimeout(15_000);
            return adb;
        } catch (java.net.SocketTimeoutException e) {
            adb.close();
            throw new IOException("Der blev ikke trykket Tillad på tv'et");
        } catch (Exception e) {
            adb.close();
            throw e;
        }
    }

    /** Kører en shell kommando og giver dens output. */
    String shell(String command) throws IOException {
        int local = nextLocal++;
        write(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int remote = 0;
        while (true) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == OKAY) {
                remote = m.arg0;
            } else if (m.cmd == WRTE) {
                result.write(m.data);
                write(OKAY, local, m.arg0, new byte[0]);
            } else if (m.cmd == CLSE) {
                if (remote != 0) write(CLSE, local, remote, new byte[0]);
                return new String(result.toByteArray(), StandardCharsets.UTF_8).trim();
            }
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    // Beskeder: 24 bytes hoved (little endian) + data

    private static final class Msg {
        int cmd, arg0, arg1;
        byte[] data;
    }

    private void write(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        for (byte x : data) sum += x & 0xff;
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~cmd).put(data);
        out.write(b.array());
        out.flush();
    }

    private Msg read() throws IOException {
        byte[] head = new byte[24];
        in.readFully(head);
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        if (len < 0 || len > 1024 * 1024) throw new IOException("Ugyldig ADB besked");
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    // Nøgler: et RSA par, der ligger i appens egne filer og huskes, så tv'et kun spørger én gang

    static KeyPair keys(Context context) throws Exception {
        File priv = new File(context.getFilesDir(), "adbkey");
        File pub = new File(context.getFilesDir(), "adbkey.pub.der");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv.isFile() && pub.isFile()) {
            try {
                return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(readFile(pub))),
                        kf.generatePrivate(new PKCS8EncodedKeySpec(readFile(priv))));
            } catch (Exception ignored) {
                // Ødelagt nøgle: lav en ny (tv'et spørger så igen)
            }
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        try (FileOutputStream o = new FileOutputStream(priv)) {
            o.write(kp.getPrivate().getEncoded());
        }
        try (FileOutputStream o = new FileOutputStream(pub)) {
            o.write(kp.getPublic().getEncoded());
        }
        return kp;
    }

    private static byte[] readFile(File f) throws IOException {
        try (InputStream i = new FileInputStream(f)) {
            return Net.readAll(i);
        }
    }

    /** PKCS#1 v1.5 signatur af tokenet, regnet direkte, så det ikke afhænger af Androids krypto udbydere. */
    static byte[] sign(RSAPrivateKey key, byte[] token) {
        int k = (key.getModulus().bitLength() + 7) / 8;
        byte[] em = new byte[k];
        int t = SHA1_PREFIX.length + token.length;
        em[0] = 0;
        em[1] = 1;
        for (int i = 2; i < k - t - 1; i++) em[i] = (byte) 0xff;
        em[k - t - 1] = 0;
        System.arraycopy(SHA1_PREFIX, 0, em, k - t, SHA1_PREFIX.length);
        System.arraycopy(token, 0, em, k - token.length, token.length);
        BigInteger s = new BigInteger(1, em).modPow(key.getPrivateExponent(), key.getModulus());
        byte[] raw = s.toByteArray();
        byte[] sig = new byte[k];
        int copy = Math.min(raw.length, k);
        System.arraycopy(raw, raw.length - copy, sig, k - copy, copy);
        return sig;
    }

    /** Den offentlige nøgle i ADB's eget format (som ~/.android/adbkey.pub), base64 + navn. */
    static byte[] publicKey(RSAPublicKey key) {
        BigInteger n = key.getModulus();
        int words = n.bitLength() / 32;
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(words * 32 * 2).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n0inv.intValue());
        putWords(b, n, words);
        putWords(b, rr, words);
        b.putInt(key.getPublicExponent().intValue());
        String encoded = android.util.Base64.encodeToString(b.array(), android.util.Base64.NO_WRAP);
        return (encoded + " jarvis-tv@" + android.os.Build.MODEL.replace(' ', '_') + "\0").getBytes(StandardCharsets.US_ASCII);
    }

    private static void putWords(ByteBuffer b, BigInteger v, int words) {
        BigInteger mask = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
        for (int i = 0; i < words; i++) b.putInt(v.shiftRight(32 * i).and(mask).intValue());
    }
}
