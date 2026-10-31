package dk.selveste.launcher;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * De seneste 60 fejl (netværk, HA, Jellyfin, opdatering), så man under Indstillinger > Om kan se,
 * hvorfor en række er tom. Gemmes i files/fejl.log og forlader aldrig sticken.
 */
final class ErrorLog {

    private ErrorLog() {
    }

    private static final int MAX = 60;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static File file;

    static synchronized void init(Context context) {
        if (file != null) return;
        file = new File(context.getApplicationContext().getFilesDir(), "fejl.log");
        try {
            if (file.isFile()) {
                String[] old;
                try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                    old = new String(Net.readAll(in), StandardCharsets.UTF_8).split("\n");
                }
                for (String l : old) if (!l.isEmpty()) LINES.addLast(l);
                while (LINES.size() > MAX) LINES.removeFirst();
            }
        } catch (Exception ignored) {
        }
    }

    static void note(String where, Throwable error) {
        String what = error == null ? "" : error.getClass().getSimpleName() + ": " + error.getMessage();
        add(where + " · " + what);
    }

    static synchronized void add(String text) {
        String line = new SimpleDateFormat("d/M HH:mm:ss", Locale.ROOT).format(new Date()) + "  " + text.replace('\n', ' ');
        if (line.length() > 300) line = line.substring(0, 300);
        // Samme fejl igen og igen fylder bare: kun én linje pr. fejl i træk
        String last = LINES.peekLast();
        if (last != null && last.substring(Math.min(last.length(), 16)).equals(line.substring(Math.min(line.length(), 16)))) return;
        LINES.addLast(line);
        while (LINES.size() > MAX) LINES.removeFirst();
        if (file == null) return;
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    /** Nyeste først. */
    static synchronized String text() {
        if (LINES.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        java.util.Iterator<String> it = LINES.descendingIterator();
        while (it.hasNext()) sb.append(it.next()).append('\n');
        return sb.toString();
    }

    static synchronized void clear() {
        LINES.clear();
        if (file != null) file.delete();
    }
}
