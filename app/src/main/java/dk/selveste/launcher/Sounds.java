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

    static synchronized void init(Context context) {
        if (pool != null) return;
        pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        try {
            move = pool.load(wav(context, "lyd_flyt.wav", 1500, 0.028, 0.22), 1);
            select = pool.load(wav(context, "lyd_ok.wav", 2200, 0.07, 0.25), 1);
        } catch (Exception e) {
            ErrorLog.note("Lyde", e);
        }
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
