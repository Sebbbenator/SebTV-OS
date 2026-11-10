package dk.selveste.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.FrameMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import app.rive.runtime.kotlin.RiveAnimationView;
import app.rive.runtime.kotlin.core.Alignment;
import app.rive.runtime.kotlin.core.Direction;
import app.rive.runtime.kotlin.core.Fit;
import app.rive.runtime.kotlin.core.Loop;
import app.rive.runtime.kotlin.core.RendererType;
import app.rive.runtime.kotlin.core.Rive;

/**
 * Prøve af Rive animationer på tv'et. Viser en .riv fil oven på forsiden og måler imens, hvor mange
 * billeder i sekundet tv'et når, hvor mange der kommer for sent, og hvor meget hukommelse appen bruger.
 * Filerne laves i Rive og lægges op fra telefonsiden; der ligger ingen animationer i repoet.
 */
final class RiveTest {

    private RiveTest() {
    }

    /** Rives eget eksempel (biler), hentes første gang, så der er noget at måle på fra start. */
    static final String SAMPLE_URL = "https://cdn.rive.app/animations/vehicles.riv";
    static final String SAMPLE_NAME = "eksempel-biler.riv";
    static final int MAX = 15 * 1024 * 1024;

    private static boolean ready;
    private static Runnable closeCurrent;

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "rive");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** Alle .riv filer på tv'et, nyeste først. */
    static List<File> files(Context c) {
        File[] all = dir(c).listFiles((d, n) -> n.endsWith(".riv"));
        List<File> out = new ArrayList<>(all == null ? new ArrayList<>() : Arrays.asList(all));
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    /** Rive filer starter med bogstaverne RIVE. */
    static boolean isRive(byte[] b) {
        return b != null && b.length > 4 && b[0] == 'R' && b[1] == 'I' && b[2] == 'V' && b[3] == 'E';
    }

    static String cleanName(String raw) {
        String n = raw == null ? "" : raw.replaceAll(".*[/\\\\]", "").toLowerCase(Locale.ROOT)
                .replaceAll("\\.riv$", "").replaceAll("[^a-z0-9æøå_-]+", "-").replaceAll("^-+|-+$", "");
        if (n.isEmpty()) n = "animation";
        if (n.length() > 40) n = n.substring(0, 40);
        return n + ".riv";
    }

    static String save(Context c, String name, byte[] body) throws IOException {
        if (!isRive(body)) throw new IOException("Det er ikke en Rive fil (.riv)");
        String clean = cleanName(name);
        File tmp = new File(dir(c), clean + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(body);
        }
        File f = new File(dir(c), clean);
        f.delete();
        if (!tmp.renameTo(f)) throw new IOException("Kunne ikke gemme filen");
        return clean;
    }

    static boolean delete(Context c, String name) {
        return new File(dir(c), cleanName(name)).delete();
    }

    /** Henter Rives eksempel, hvis der ikke ligger nogen filer endnu. Kører i baggrunden. */
    static void ensureSample(Context c) throws IOException {
        if (!files(c).isEmpty()) return;
        File f = new File(dir(c), SAMPLE_NAME);
        File tmp = new File(dir(c), SAMPLE_NAME + ".tmp");
        Net.download(SAMPLE_URL, tmp);
        if (!tmp.renameTo(f)) throw new IOException("Kunne ikke gemme eksemplet");
    }

    private static byte[] read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return Net.readAll(in);
        }
    }

    static synchronized void init(Context c) {
        if (ready) return;
        // Rives egen afspiller tegner på grafikchippen (OpenGL)
        Rive.INSTANCE.init(c.getApplicationContext(), RendererType.Rive);
        ready = true;
    }

    /** Lukker prøven, hvis den står fremme (fx når en app åbnes). */
    static void close() {
        Runnable r = closeCurrent;
        if (r != null) r.run();
    }

    static boolean showing() {
        return closeCurrent != null;
    }

    private static final String[] SIZES = {"Midt på skærmen", "Hele skærmen", "Lille i hjørnet"};

    /**
     * Viser prøven oven på forsiden. Venstre og højre skifter fil, op og ned skifter størrelse,
     * OK sætter på pause, Tilbage lukker.
     */
    static void show(Activity a, ViewGroup parent, String startWith) {
        close();
        FrameLayout scrim = new FrameLayout(a);
        scrim.setBackgroundColor(Color.argb(70, 0, 0, 0));
        scrim.setClickable(true);
        scrim.setFocusable(true);

        TextView stats = Ui.text(a, "Henter animation …", 17, Color.WHITE);
        stats.setTypeface(Typeface.MONOSPACE);
        stats.setLineSpacing(0, 1.2f);
        stats.setBackgroundColor(Color.argb(190, 0, 0, 0));
        int pad = Ui.dp(a, 14);
        stats.setPadding(pad, pad, pad, pad);
        FrameLayout.LayoutParams statsLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        statsLp.setMargins(Ui.dp(a, 40), Ui.dp(a, 30), 0, 0);

        parent.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.post(scrim::requestFocus);

        Handler ui = new Handler(Looper.getMainLooper());
        State st = new State();

        // Måling: hvert billede, vinduet tegner (også dem Rive sender op), med hvor lang tid det tog
        float hz = a.getWindowManager().getDefaultDisplay().getRefreshRate();
        long budgetNs = (long) (1_000_000_000L / (hz > 1 ? hz : 60f));
        HandlerThread meter = new HandlerThread("rive-maaling");
        meter.start();
        Handler meterHandler = new Handler(meter.getLooper());
        Window.OnFrameMetricsAvailableListener listener = (w, m, dropped) -> {
            long total = m.getMetric(FrameMetrics.TOTAL_DURATION);
            synchronized (st) {
                st.frames++;
                st.secFrames++;
                if (total > budgetNs) st.late++;
                st.lateDropped += dropped;
                if (total > st.worstNs) st.worstNs = total;
            }
        };
        try {
            a.getWindow().addOnFrameMetricsAvailableListener(listener, meterHandler);
        } catch (Exception ignored) {
        }

        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (st.closed) return;
                int fps;
                long frames, late, worst;
                synchronized (st) {
                    fps = st.secFrames;
                    st.secFrames = 0;
                    frames = st.frames;
                    late = st.late;
                    worst = st.worstNs;
                }
                meterHandler.post(() -> st.pssMb = (int) (Debug.getPss() / 1024));
                if (st.view != null) {
                    String pct = frames == 0 ? "0" : String.format(Locale.ROOT, "%.1f", late * 100.0 / frames).replace('.', ',');
                    stats.setText(st.name + "  (" + (st.index + 1) + " af " + st.files.size() + ")"
                            + (st.paused ? "  ·  PAUSE" : "")
                            + "\n" + SIZES[st.size]
                            + "\n" + fps + " billeder/sek  ·  " + pct + " % for sent  ·  værste " + worst / 1_000_000 + " ms"
                            + "\nHukommelse " + st.pssMb + " MB  ·  skærm " + Math.round(hz) + " Hz"
                            + "\n◀ ▶ skift fil   ▲ ▼ størrelse   OK pause   Tilbage luk");
                }
                ui.postDelayed(this, 1000);
            }
        };

        Runnable close = () -> {
            if (st.closed) return;
            st.closed = true;
            closeCurrent = null;
            ui.removeCallbacks(tick);
            try {
                a.getWindow().removeOnFrameMetricsAvailableListener(listener);
            } catch (Exception ignored) {
            }
            meter.quitSafely();
            parent.removeView(scrim);
        };
        closeCurrent = close;

        scrim.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            switch (code) {
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    close.run();
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!st.files.isEmpty()) load(a, scrim, stats, statsLp, st, (st.index - 1 + st.files.size()) % st.files.size());
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!st.files.isEmpty()) load(a, scrim, stats, statsLp, st, (st.index + 1) % st.files.size());
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    st.size = (st.size + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : SIZES.length - 1)) % SIZES.length;
                    place(a, st);
                    st.reset();
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    if (st.view == null) break;
                    if (st.paused) st.view.play(Loop.AUTO, Direction.AUTO, true);
                    else st.view.pause();
                    st.paused = !st.paused;
                    st.reset();
                    break;
                default:
                    return false;
            }
            return true;
        });
        scrim.setOnClickListener(v -> close.run());

        Net.async(() -> {
            init(a);
            ensureSample(a);
            // Én post pr. artboard, så Opstart, Baggrund, Ur og Fokus i et tema kan ses hver for sig
            List<Entry> entries = new ArrayList<>();
            for (File f : files(a)) {
                List<String> boards = new ArrayList<>();
                try {
                    app.rive.runtime.kotlin.core.File rf = new app.rive.runtime.kotlin.core.File(read(f), RendererType.Rive, null);
                    boards.addAll(rf.getArtboardNames());
                    rf.release();
                } catch (Throwable ignored) {
                }
                if (boards.isEmpty()) boards.add(null);
                for (String b : boards) entries.add(new Entry(f, b));
            }
            return entries;
        }, (list, err) -> {
            if (st.closed) return;
            if (list == null || list.isEmpty()) {
                stats.setText("Kunne ikke starte Rive:\n" + (err == null ? "ingen filer" : err.getMessage())
                        + "\n\nTilbage lukker");
                if (stats.getParent() == null) scrim.addView(stats, statsLp);
                return;
            }
            st.files = list;
            int start = 0;
            for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).file.getName().equals(startWith)) start = i;
            load(a, scrim, stats, statsLp, st, start);
            ui.postDelayed(tick, 1000);
        });
    }

    private static void load(Activity a, FrameLayout scrim, TextView stats, FrameLayout.LayoutParams statsLp, State st, int index) {
        st.index = index;
        Entry e = st.files.get(index);
        File f = e.file;
        st.name = f.getName() + (e.artboard == null ? "" : " · " + e.artboard);
        Net.async(() -> read(f), (bytes, err) -> {
            if (st.closed) return;
            if (st.view != null) scrim.removeView(st.view);
            st.view = null;
            if (bytes == null) {
                stats.setText(f.getName() + "\nKunne ikke læses: " + (err == null ? "" : err.getMessage()));
            } else {
                try {
                    RiveAnimationView v = new RiveAnimationView(a, null);
                    v.setFocusable(false);
                    v.setRiveBytes(bytes, e.artboard, null, null, true, false, Fit.CONTAIN, Alignment.CENTER, Loop.AUTO);
                    st.view = v;
                    scrim.addView(v, 0);
                    place(a, st);
                    st.paused = false;
                } catch (Throwable t) {
                    stats.setText(f.getName() + "\nRive kunne ikke vise filen: " + t.getMessage());
                }
            }
            if (stats.getParent() == null) scrim.addView(stats, statsLp);
            stats.bringToFront();
            st.reset();
        });
    }

    private static void place(Activity a, State st) {
        if (st.view == null) return;
        FrameLayout.LayoutParams lp;
        switch (st.size) {
            case 1:
                lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                break;
            case 2:
                lp = new FrameLayout.LayoutParams(Ui.dp(a, 320), Ui.dp(a, 200), Gravity.BOTTOM | Gravity.END);
                lp.setMargins(0, 0, Ui.dp(a, 48), Ui.dp(a, 40));
                break;
            default:
                int w = a.getResources().getDisplayMetrics().widthPixels * 6 / 10;
                lp = new FrameLayout.LayoutParams(w, w * 9 / 16, Gravity.CENTER);
        }
        st.view.setLayoutParams(lp);
    }

    private static final class Entry {
        final File file;
        final String artboard;

        Entry(File file, String artboard) {
            this.file = file;
            this.artboard = artboard;
        }
    }

    private static final class State {
        List<Entry> files = new ArrayList<>();
        int index;
        String name = "";
        int size;
        boolean paused;
        volatile boolean closed;
        RiveAnimationView view;
        volatile int pssMb;
        long frames, late, lateDropped, worstNs;
        int secFrames;

        synchronized void reset() {
            frames = 0;
            late = 0;
            lateDropped = 0;
            worstNs = 0;
        }
    }
}
