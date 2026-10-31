package dk.selveste.launcher;

/** Små farvehjælpere til scenerne og tonerne. */
final class Patterns {

    private Patterns() {
    }

    static boolean isLight(int color) {
        return (0.299 * android.graphics.Color.red(color) + 0.587 * android.graphics.Color.green(color)
                + 0.114 * android.graphics.Color.blue(color)) > 150;
    }
}
