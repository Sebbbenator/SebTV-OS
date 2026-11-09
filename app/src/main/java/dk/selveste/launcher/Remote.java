package dk.selveste.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Fjernbetjening fra telefonen. Tilbage og Hjem klarer Home tjenesten selv. Pilene og OK går på Android 13
 * og nyere også gennem Home tjenesten; på ældre Fire TV bruges tv'ets egen ADB (som Gør til forside), og
 * er den slået fra, flyttes fokus direkte i appen, der står fremme. Afspil, pause og lyd går via Android.
 * Tekst skrives direkte i det tekstfelt, der har fokus på tv'et.
 */
final class Remote {

    private Remote() {
    }

    static final String[][] KEYS = {
            {"up", "19"}, {"down", "20"}, {"left", "21"}, {"right", "22"}, {"ok", "23"},
            {"back", "4"}, {"home", "3"}, {"menu", "82"},
            {"playpause", "85"}, {"next", "87"}, {"prev", "88"}, {"rew", "89"}, {"ff", "90"},
            {"volup", "24"}, {"voldown", "25"}, {"mute", "164"},
    };

    static int code(String key) {
        for (String[] k : KEYS) if (k[0].equals(key)) return Integer.parseInt(k[1]);
        return -1;
    }

    private static LocalAdb adb;
    private static long adbFailedAt;

    /** Trykker en knap. Giver en fejltekst, eller null hvis det lykkedes. Kaldes fra en baggrundstråd. */
    static synchronized String press(Context context, String key) {
        int code = code(key);
        if (code < 0) return "Ukendt knap";
        HomeService hs = HomeService.get();
        // Tilbage og Hjem: altid via Home tjenesten
        if (hs != null && code == KeyEvent.KEYCODE_BACK) return hs.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ? null : "Tilbage virkede ikke";
        if (hs != null && code == KeyEvent.KEYCODE_HOME) return hs.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ? null : "Hjem virkede ikke";
        // Afspil, spol og lyd: Android sender dem til den app, der spiller
        if (isMedia(code)) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
                // Fire TV sender lyden videre til tv'et over HDMI; ADB giver samme vej som fjernbetjeningen
                if (shell(context, "input keyevent " + code)) return null;
                int dir = code == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE
                        : code == KeyEvent.KEYCODE_VOLUME_DOWN ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_TOGGLE_MUTE;
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI);
                return null;
            }
            long t = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
            return null;
        }
        // Pile og OK
        if (hs != null && Build.VERSION.SDK_INT >= 33) {
            int action = code == 19 ? AccessibilityService.GLOBAL_ACTION_DPAD_UP
                    : code == 20 ? AccessibilityService.GLOBAL_ACTION_DPAD_DOWN
                    : code == 21 ? AccessibilityService.GLOBAL_ACTION_DPAD_LEFT
                    : code == 22 ? AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT
                    : code == 23 ? AccessibilityService.GLOBAL_ACTION_DPAD_CENTER : -1;
            if (action > 0 && hs.performGlobalAction(action)) return null;
        }
        if (shell(context, "input keyevent " + code)) return null;
        if (hs == null) return "Slå Home tjenesten til på tv'et (Indstillinger > Gør til forside)";
        return moveFocus(hs, code) ? null : "Tv'et svarede ikke. Slå ADB til på tv'et, så virker pilene i alle apps";
    }

    private static boolean isMedia(int code) {
        return code == 85 || code == 87 || code == 88 || code == 89 || code == 90 || code == 24 || code == 25 || code == 164;
    }

    /** Uden ADB: flyt fokus selv i den app, der står fremme. Virker i de fleste almindelige apps. */
    private static boolean moveFocus(HomeService hs, int code) {
        AccessibilityNodeInfo focus = hs.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus == null) return false;
        if (code == 23) return focus.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        int dir = code == 19 ? android.view.View.FOCUS_UP : code == 20 ? android.view.View.FOCUS_DOWN
                : code == 21 ? android.view.View.FOCUS_LEFT : android.view.View.FOCUS_RIGHT;
        AccessibilityNodeInfo next = focus.focusSearch(dir);
        return next != null && next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
    }

    /** Skriver teksten i tekstfeltet med fokus (erstatter det, der står). Giver en fejltekst eller null. */
    static synchronized String type(Context context, String text, boolean enter) {
        HomeService hs = HomeService.get();
        AccessibilityNodeInfo field = hs == null ? null : hs.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (field != null && field.isEditable()) {
            if (text != null) {
                Bundle b = new Bundle();
                b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) return "Feltet på tv'et tog ikke imod teksten";
            }
            if (enter) {
                if (Build.VERSION.SDK_INT >= 30
                        && field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId())) return null;
                if (shell(context, "input keyevent 66")) return null;
            }
            return null;
        }
        if (text == null && enter) return shell(context, "input keyevent 66") ? null : "Vælg et tekstfelt på tv'et først";
        return "Vælg et tekstfelt på tv'et først (fx søgefeltet)";
    }

    /** Kører en kommando via tv'ets egen ADB. Forbindelsen holdes åben, så tryk er hurtige. */
    private static boolean shell(Context context, String cmd) {
        // Har ADB lige fejlet, prøves det ikke igen før om et minut, så pilene ikke venter
        if (adb == null && SystemClock.elapsedRealtime() - adbFailedAt < 60_000 && adbFailedAt > 0) return false;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                // Ingen Tillad dialog herfra: kun en nøgle, tv'et allerede kender (fra Gør til forside)
                if (adb == null) adb = LocalAdb.connect(context, 1, false);
                adb.shell(cmd);
                return true;
            } catch (Exception e) {
                if (adb != null) adb.close();
                adb = null;
            }
        }
        adbFailedAt = SystemClock.elapsedRealtime();
        return false;
    }
}
