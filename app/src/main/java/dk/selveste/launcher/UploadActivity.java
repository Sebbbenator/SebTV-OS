package dk.selveste.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

import io.nayuki.qrcodegen.QrCode;

/** Viser QR kode, adresse og kode til telefonsiden. Serveren kører kun mens skærmen er åben. */
public class UploadActivity extends Activity {

    private UploadServer server;
    private TextView status;
    private int uploads;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        root.setBackground(Ui.screenBackground());
        int pad = Ui.dp(this, 56);
        root.setPadding(pad, pad, pad, pad);

        ImageView qr = new ImageView(this);
        int qrSize = Ui.dp(this, 260);
        root.addView(qr, new LinearLayout.LayoutParams(qrSize, qrSize));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 48), 0, 0, 0);
        root.addView(col);

        // Temaets egen farve (hvid i Apple), ikke Jarvis' cyan i alle temaer
        int accent = Ui.apple() ? Ui.TEXT : new Prefs(this).accentColor();
        col.addView(Ui.header(this, "TELEFONSIDEN", accent));
        TextView url = Ui.text(this, "", 28, Ui.TEXT);
        col.addView(url);
        // Hjemme virker wifi. Er tv'et på et andet net end hjemmenettet (fx hos kæresten), kan wifi'et
        // blokere telefonen, så QR koden peger på Tailscale, hvis tv'et har det
        String wifi = ip();
        String ts = tailscaleIp();
        String home = new Prefs(this).homeSubnet();
        boolean useTailscale = ts != null && !home.isEmpty() && !wifi.startsWith(home);
        String primary = useTailscale ? ts : wifi;
        String other = useTailscale ? wifi : ts;
        col.addView(Ui.text(this, useTailscale
                ? "Scan koden med telefonen. Tailscale skal være tændt på telefonen."
                : "Scan koden med telefonen. Telefonen skal være på samme wifi som tv'et.", 16, Ui.TEXT_DIM));
        if (other != null && !other.equals(primary)) {
            col.addView(Ui.text(this, (useTailscale ? "Uden Tailscale, på samme wifi: http://" : "Med Tailscale på telefonen: http://")
                    + other + ":" + UploadServer.PORT, 15, Ui.TEXT_DIM));
        }
        col.addView(Ui.header(this, "KODE", accent));
        TextView pin = Ui.text(this, "", 56, accent);
        pin.setTypeface(Typeface.MONOSPACE);
        pin.setLetterSpacing(0.3f);
        col.addView(pin);
        status = Ui.text(this, "Her lægger du baggrunde op, logger ind på Home Assistant og gemmer eller indlæser din profilkode.", 16, Ui.TEXT_DIM);
        status.setPadding(0, Ui.dp(this, 24), 0, 0);
        col.addView(status);
        setContentView(root);

        server = new UploadServer(this, () -> {
            uploads++;
            status.setText("Gemt (" + uploads + "). Tryk Tilbage, når du er færdig.");
        });
        try {
            server.start();
        } catch (Exception e) {
            status.setText("Kunne ikke starte siden: " + e.getMessage());
        }
        String address = "http://" + primary + ":" + UploadServer.PORT;
        url.setText(address);
        pin.setText(server.pin);
        qr.setImageBitmap(qrBitmap(address, qrSize));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MainActivity.appsDirty = true;
        if (server != null) server.stop();
    }

    /**
     * Adressen telefonen skal bruge. Wifi (192.168.x, 10.x, 172.16-31.x) går forud for Tailscale (100.x),
     * så det virker for en telefon på samme wifi, også når tv'et har Tailscale tændt.
     */
    static String ip() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    if (a.isSiteLocalAddress()) return a.getHostAddress();
                    if (fallback == null) fallback = a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return fallback != null ? fallback : "127.0.0.1";
    }

    /** Tailscale adressen (100.64-127.x), hvis tv'et har en, til en telefon uden for wifi'et. */
    static String tailscaleIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address)) continue;
                    byte[] b = a.getAddress();
                    if ((b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static Bitmap qrBitmap(String text, int px) {
        QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int border = 2;
        int n = qr.size + border * 2;
        Bitmap small = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                boolean dark = qr.getModule(x - border, y - border);
                small.setPixel(x, y, dark ? Ui.BG : Color.parseColor("#D8F6FF"));
            }
        }
        return Bitmap.createScaledBitmap(small, px, px, false);
    }
}
