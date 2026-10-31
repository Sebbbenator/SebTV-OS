package dk.selveste.launcher;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Klippet til Konami sekvensen (Iron Man dragten der flyver ind). Lægges op fra telefonsiden og ligger
 * kun på sticken; der er ingen klip i repoet. MP4, højst 60 MB.
 */
final class KonamiClip {

    private KonamiClip() {
    }

    static final int MAX = 60 * 1024 * 1024;

    static File file(Context context) {
        return new File(context.getFilesDir(), "konami.mp4");
    }

    static boolean exists(Context context) {
        return file(context).isFile();
    }

    /** Læser præcis length bytes fra telefonen direkte til disken og tjekker, at det er en MP4. */
    static void save(Context context, InputStream in, int length) throws IOException {
        File tmp = new File(context.getFilesDir(), "konami.tmp");
        byte[] head = new byte[12];
        int got = 0;
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            while (got < length) {
                int n = in.read(buf, 0, Math.min(buf.length, length - got));
                if (n < 0) throw new IOException("Overførslen stoppede");
                if (got < head.length) System.arraycopy(buf, 0, head, got, Math.min(n, head.length - got));
                out.write(buf, 0, n);
                got += n;
            }
        }
        // MP4 og MOV har "ftyp" fra byte 4
        if (length < 12 || head[4] != 'f' || head[5] != 't' || head[6] != 'y' || head[7] != 'p') {
            tmp.delete();
            throw new IOException("Det er ikke en MP4 video");
        }
        File f = file(context);
        f.delete();
        if (!tmp.renameTo(f)) throw new IOException("Kunne ikke gemme klippet");
    }

    static void delete(Context context) {
        file(context).delete();
    }
}
