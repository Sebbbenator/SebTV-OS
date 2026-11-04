package dk.selveste.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Lyde ved navigation (slås til under Funktioner): et kort, blødt klik ved fokus og en lys tone ved OK.
 * Lydene regnes ud på sticken første gang, så der ikke ligger lydfiler i appen.
 */
final class Sounds {

    private Sounds() {
    }

    private static SoundPool pool;
    private static int move, select;
    private static long lastMove;
    /** Temaet, lydene er lavet til; hvert tema har sine egne (Skin.sounds). */
    private static String loadedFor;

    static synchronized void init(Context context) {
        if (pool != null && Skin.key.equals(loadedFor)) return;
        if (pool == null) {
            pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        } else {
            if (move != 0) pool.unload(move);
            if (select != 0) pool.unload(select);
            move = select = 0;
        }
        loadedFor = Skin.key;
        try {
            double[][] spec = Skin.sounds();
            if (spec == null) {
                move = pool.load(wav(context, "lyd_flyt.wav", 1500, 0.028, 0.22), 1);
                select = pool.load(wav(context, "lyd_ok.wav", 2200, 0.07, 0.25), 1);
            } else {
                move = pool.load(synth(context, "lyd_" + Skin.key + "_flyt.wav", spec[0]), 1);
                select = pool.load(synth(context, "lyd_" + Skin.key + "_ok.wav", spec[1]), 1);
            }
        } catch (Exception e) {
            ErrorLog.note("Lyde", e);
        }
    }

    /**
     * Temaernes lyde, regnet ud her: blip (kort tone), chirp (glidende tone), støj (spind, vind),
     * dump (dyb tone, der falder), klik (projektor, ur), klokke (overtoner), dråbe (vand) og bjælder.
     */
    static String synth(Context context, String name, double[] spec) throws Exception {
        File f = new File(context.getCacheDir(), name);
        if (f.isFile()) return f.getAbsolutePath();
        int kind = (int) spec[0];
        double f0 = spec[1], f1 = spec[2], seconds = spec[3], volume = spec[4];
        int rate = 44100;
        int n = (int) (rate * seconds);
        java.util.Random rnd = new java.util.Random(7);
        double phase = 0, low = 0, prev = 0;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) rate, p = t / seconds;
            double hz = f0 + (f1 - f0) * p;
            phase += 2 * Math.PI * hz / rate;
            double attack = Math.min(1, i / (rate * 0.003));
            double v;
            switch (kind) {
                case 1: // chirp: tonen glider, med en svag overtone
                    v = (Math.sin(phase) + 0.25 * Math.sin(phase * 2)) * Math.sin(Math.PI * Math.min(1, p * 1.1));
                    break;
                case 2: { // støj gennem et filter, der følger frekvensen
                    double a = Math.min(1, 2 * Math.PI * hz / rate);
                    low += a * ((rnd.nextDouble() * 2 - 1) - low);
                    double hp = low - prev;
                    prev = low;
                    v = (low * 0.6 + hp * 1.6) * Math.exp(-p * 3) * (p < 0.08 ? p / 0.08 : 1);
                    break;
                }
                case 3: // dump: dyb sinus der falder, hurtig udtoning
                    v = Math.sin(phase) * Math.exp(-t / (seconds / 3));
                    break;
                case 4: { // klik: et kort knæk og (ved lange) projektorens tikken
                    double click = rnd.nextDouble() * 2 - 1;
                    v = seconds < 0.05 ? click * Math.exp(-t / 0.002)
                            : click * Math.exp(-((t * 24) % 1) / 0.04) * 0.7;
                    break;
                }
                case 5: // klokke: grundtone og overtoner, der klinger ud
                    v = (Math.sin(phase) + 0.5 * Math.sin(phase * 2.76) + 0.25 * Math.sin(phase * 5.4)) / 1.75
                            * Math.exp(-t / (seconds / 4));
                    break;
                case 6: // dråbe: tonen stiger hurtigt og dør ud
                    v = Math.sin(phase) * Math.exp(-t / (seconds / 3.5));
                    break;
                case 7: { // bjælder: tre små klokker hurtigt efter hinanden
                    v = 0;
                    for (int k = 0; k < 3; k++) {
                        double tk = t - k * 0.07;
                        if (tk < 0) continue;
                        double fk = f0 + (f1 - f0) * k / 2.0;
                        v += (Math.sin(2 * Math.PI * fk * tk) + 0.4 * Math.sin(2 * Math.PI * fk * 2.4 * tk)) * Math.exp(-tk / 0.08);
                    }
                    v /= 2;
                    break;
                }
                default: // blip
                    v = Math.sin(phase) * Math.exp(-t / (seconds / 4));
            }
            out[i] = (short) (Math.max(-1, Math.min(1, v * attack * volume)) * Short.MAX_VALUE);
        }
        ByteBuffer b = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes()).putInt(n * 2);
        for (short x : out) b.putShort(x);
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(b.array());
        }
        return f.getAbsolutePath();
    }

    static void move(Context context, Prefs prefs) {
        if (!Features.on(prefs, Features.SOUNDS)) return;
        long now = System.currentTimeMillis();
        // Holdes en piletast nede, klikker det ikke 30 gange i sekundet
        if (now - lastMove < 60) return;
        lastMove = now;
        init(context);
        if (move != 0) pool.play(move, 0.5f, 0.5f, 1, 0, 1f);
    }

    static void select(Context context, Prefs prefs) {
        if (!Features.on(prefs, Features.SOUNDS)) return;
        init(context);
        if (select != 0) pool.play(select, 0.6f, 0.6f, 1, 0, 1f);
    }

    /** En sinus tone med blød start og hurtig udtoning, gemt som 16 bit mono WAV i cachen. */
    private static String wav(Context context, String name, double hz, double seconds, double volume) throws Exception {
        File f = new File(context.getCacheDir(), name);
        if (f.isFile()) return f.getAbsolutePath();
        int rate = 44100;
        int n = (int) (rate * seconds);
        ByteBuffer b = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes()).putInt(n * 2);
        for (int i = 0; i < n; i++) {
            double t = i / (double) rate;
            double attack = Math.min(1, i / (rate * 0.002));
            double decay = Math.exp(-t / (seconds / 4));
            double v = Math.sin(2 * Math.PI * hz * t) * attack * decay * volume;
            b.putShort((short) (v * Short.MAX_VALUE));
        }
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(b.array());
        }
        return f.getAbsolutePath();
    }
}
