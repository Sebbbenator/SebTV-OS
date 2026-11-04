package dk.selveste.launcher;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Jarvis beskeder som banner øverst på tv'et, også oven på Netflix og Wholphin.
 * HA skriver beskeden i den valgte input_text (Indstillinger > Home Assistant). Den kommer med det samme via
 * HA's websocket; virker den ikke, kigges der efter ændringer hvert 15. sekund, kun mens skærmen er tændt.
 * Beskeder der starter med "/" er kommandoer til tv'et (se TvCommands). Banneret tegnes som accessibility overlay, så det kræver ingen ekstra tilladelse.
 */
final class JarvisMessages {

    private static final long POLL_MS = 15_000;
    private static final long SHOW_MS = 10_000;

    private final AccessibilityService service;
    private final Prefs prefs;
    private final HomeAssistant ha;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    /** Første svar efter start gemmes bare, så en gammel besked ikke vises igen, når tv'et tændes. */
    private boolean primed;
    private View banner;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            check();
            handler.postDelayed(this, POLL_MS);
        }
    };

    /** Websocket: beskeden kommer i samme øjeblik, HA skriver den. */
    private final HaSocket.Listener live;

    JarvisMessages(AccessibilityService service) {
        this.service = service;
        this.prefs = new Prefs(service);
        this.ha = new HomeAssistant(prefs);
        this.live = changed -> {
            String entity = prefs.haMessage();
            JSONObject st = entity.isEmpty() ? null : changed.get(entity);
            if (st != null && running) received(st);
        };
    }

    void start() {
        if (running) return;
        running = true;
        primed = false;
        HaSocket socket = HaSocket.get(service);
        socket.addListener(live);
        socket.start();
        handler.post(poll);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(poll);
        HaSocket socket = HaSocket.get(service);
        socket.removeListener(live);
        socket.stop();
        hide();
    }

    private void check() {
        String entity = prefs.haMessage();
        if (!Features.on(prefs, Features.MESSAGES) || !ha.configured() || entity.isEmpty()) return;
        HaSocket socket = HaSocket.get(service);
        // Websocketten sørger for beskederne, når den er forbundet; ellers spørger vi som før
        if (socket.connected() && primed) return;
        socket.start();
        Net.async(() -> ha.state(entity), (st, err) -> {
            if (st == null || !running) return;
            received(st);
        });
    }

    private void received(JSONObject st) {
        if (!Features.on(prefs, Features.MESSAGES)) return;
        String text = st.optString("state", "");
        String stamp = HaSocket.second(st.optString("last_changed", "")) + "|" + text;
        boolean fresh = primed && !stamp.equals(prefs.lastMessageStamp());
        primed = true;
        prefs.setLastMessageStamp(stamp);
        if (!fresh || text.isEmpty() || text.equals("unknown") || text.equals("unavailable")) return;
        if (TvCommands.isCommand(text)) {
            String problem = TvCommands.run(service, text);
            if (problem != null) ErrorLog.add("HA kommando · " + problem);
            return;
        }
        show(text, st);
    }

    /** Viser banneret. "Titel | tekst" giver en egen overskrift, ellers assistentens navn. */
    void show(String text, JSONObject state) {
        hide();
        String name = prefs.assistantName();
        String title = name.isEmpty() ? "BESKED" : name.toUpperCase(java.util.Locale.ROOT);
        int bar = text.indexOf('|');
        if (bar > 0) {
            title = text.substring(0, bar).trim().toUpperCase(java.util.Locale.ROOT);
            text = text.substring(bar + 1).trim();
        }
        int accent = prefs.accentColor();
        Ui.loadFont(service, prefs.font());

        LinearLayout box = new LinearLayout(service);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(service, 18);
        box.setPadding(pad, Ui.dp(service, 12), pad, Ui.dp(service, 14));
        GradientDrawable bg = Ui.card(service, HudView.withAlpha(Ui.CARD_FOCUS, 0.95f), accent, 2);
        if (Ui.apple()) {
            // Som Apple TV's notifikationer: mørkegrå, runde hjørner, ingen kant, titel som almindelige ord
            bg = Ui.panel(service, 0.95f);
            String t = title.toLowerCase(java.util.Locale.ROOT);
            title = t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
        box.setBackground(bg);
        box.setElevation(Ui.dp(service, 8));
        TextView head = Ui.header(service, title, accent);
        head.setPadding(0, 0, 0, Ui.dp(service, 4));
        box.addView(head);
        TextView body = Ui.text(service, text, 20, Ui.TEXT);
        body.setMaxLines(3);
        box.addView(body);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Ui.dp(service, 460), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Ui.dp(service, 40);
        lp.y = Ui.dp(service, 32);
        WindowManager wm = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
        try {
            wm.addView(box, lp);
        } catch (Exception e) {
            return;
        }
        banner = box;
        box.setAlpha(0f);
        box.setTranslationX(Ui.dp(service, 60));
        box.animate().alpha(1f).translationX(0).setDuration(350).start();
        handler.postDelayed(() -> {
            if (banner == box) box.animate().alpha(0f).translationX(Ui.dp(service, 60)).setDuration(350)
                    .withEndAction(this::hide).start();
        }, SHOW_MS);
    }

    private void hide() {
        if (banner == null) return;
        try {
            ((WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE)).removeView(banner);
        } catch (Exception ignored) {
        }
        banner = null;
    }
}
