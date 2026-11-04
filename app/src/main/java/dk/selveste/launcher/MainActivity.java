package dk.selveste.launcher;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    static final String EXTRA_HOME = "jarvis.home";

    /** Sat af HomeService når tv'et tændes. */
    static volatile boolean wokeUp;
    /** Sat når apps installeres eller fjernes, eller indstillinger ændres; ellers genbruges rækkerne. */
    static volatile boolean appsDirty = true;
    /** Næste forside åbner med en cirkel (efter temaskift). */
    private static boolean revealNext;
    /** Effektniveau: Motion.FULL, LIGHT eller NONE. */
    private String motion = Motion.FULL;
    /** Rækker der allerede er gledet ind; senere opdateringer skal ikke animere igen. */
    private final java.util.Set<LinearLayout> staggered = new java.util.HashSet<>();
    private static MainActivity visible;

    private static final Locale DANISH = new Locale("da", "DK");
    private static final String WHOLPHIN = "com.github.damontecres.wholphin";
    private static final int[] KONAMI = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT};

    final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", DANISH);
    private final SimpleDateFormat secondsFormat = new SimpleDateFormat("HH:mm:ss", DANISH);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE d. MMMM", DANISH);
    /** Plakater og billeder til cards, højst ca. 24 MB, så sticken ikke løber tør for hukommelse. */
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    Prefs prefs;
    AppRepository repository;
    Backgrounds backgrounds;
    HomeAssistant ha;
    Jellyfin jf;

    private FrameLayout root;
    private ImageView background;
    private ObjectAnimator kenBurns;
    private ParticleView particles;
    private TextView clock;
    private ClockRingView clockRing;
    private ImageView avatar;
    private String appliedLook;
    private String builtTone;
    private long lastNowPlaying;
    private String nowPlayingIds = "";
    private TextView date;
    private TextView greeting;
    private TextView together;
    private TextView status;
    private ScrollView scroller;
    private LinearLayout sectionFavorites;
    private LinearLayout sectionNowPlaying, sectionResume, sectionApps, sectionModes, sectionHa, sectionLatest, sectionUpcoming,
            sectionTools, sectionRest, sectionMore, moreBox;
    private boolean moreOpen;
    private final java.util.Set<String> photosFetched = new java.util.HashSet<>();
    Map<String, JSONObject> lastStates;
    private boolean menuHandled;
    private long lastBack;
    private final Runnable menuLongPress = () -> {
        if (menuHandled) return;
        menuHandled = true;
        showQuickPanel();
    };
    private FrameLayout bgFrame;
    private LinearLayout column;
    private View appleDim;
    private ThemeClock themeClock;
    private Ambient ambientView;
    /** Temaets scene (avisforside, markise, reaktor ...) og det den står på; null uden. */
    private Stage stage;
    private Backdrop backdrop;
    private List<String> stageParts = new ArrayList<>();
    /** Hvilken slags card og nummer i rækken, næste tile() laver (til temaernes egne cards). */
    private int tileKind = Tiles.SMALL, tileIndex;
    /** Apple TV: margen i siderne og afstand mellem ikonerne (dp). */
    private static final int APPLE_MARGIN = 26, APPLE_GAP = 14, APPLE_DOCK_PAD = 12;
    private static final int LABEL_ALWAYS = 0, LABEL_NONE = 1, LABEL_FOCUS = 2;
    /** Hvordan næste appflise viser sit navn (kun Apple). */
    private int appleLabels = LABEL_ALWAYS;
    private TextView ambientClock;
    /** Over uret i rolig tilstand: vejret og det første fra statuslinjen. */
    private TextView ambientInfo;
    private String ambientText = "";
    private boolean ambient;
    private FrameLayout night;
    private TextView nightClock;

    private int accent = Ui.CYAN;
    private String shownBackground;
    private boolean pendingHome;
    private long lastKey = System.currentTimeMillis();
    private long lastRemoteRefresh;
    private int konamiPos;
    private long ironManUntil;
    private Runnable lightFollow;
    private MediaPlayer voice;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            String hm = timeFormat.format(now);
            boolean newMinute = !hm.contentEquals(nightClock.getText());
            set(nightClock, hm);
            set(clock, "sekunder".equals(prefs.clockStyle()) ? secondsFormat.format(now) : hm);
            if (clockRing.getVisibility() == View.VISIBLE && !ambient) clockRing.invalidate();
            if (themeClock.getVisibility() == View.VISIBLE && !ambient) themeClock.invalidate();
            if (stage != null && !ambient) stage.tick(now);
            updateNight();
            if (newMinute) {
                String d = dateFormat.format(now);
                set(date, d.substring(0, 1).toUpperCase(DANISH) + d.substring(1));
                updateGreeting();
                updateSeason();
                if (Prefs.BG_HOURLY.equals(prefs.bgMode()) || Prefs.BG_DAILY.equals(prefs.bgMode())) loadBackground(false);
            }
            if (System.currentTimeMillis() - lastRemoteRefresh > 5 * 60 * 1000) refreshRemote();
            else if (System.currentTimeMillis() - lastNowPlaying > 45 * 1000) refreshNowPlaying();
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AppRepository.forget();
            appsDirty = true;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(packages);
        } catch (Exception ignored) {
        }
    }

    /** Sætter kun teksten, når den ændrer sig, så layoutet ikke regnes om hvert sekund. */
    private static boolean set(TextView v, String text) {
        if (text.contentEquals(v.getText())) return false;
        v.setText(text);
        return true;
    }

    static void onScreenOn() {
        MainActivity a = visible;
        if (a != null) a.handler.post(a::bootIfWoken);
    }

    /** Udseendet er ændret udefra (HA kommando): byg forsiden forfra, hvis den vises. */
    static void lookChangedFromOutside() {
        appsDirty = true;
        MainActivity a = visible;
        if (a != null) a.handler.post(() -> {
            revealNext = true;
            a.recreate();
        });
    }

    /** Ændringer fra HA's websocket: kun det der vises på forsiden, tegnes igen. */
    private final HaSocket.Listener haLive = changed -> {
        if (lastStates == null) return;
        java.util.Set<String> needed = ha.needed();
        boolean relevant = false;
        for (String id : changed.keySet()) if (needed.contains(id)) relevant = true;
        if (!relevant) return;
        Map<String, JSONObject> merged = new java.util.LinkedHashMap<>(lastStates);
        merged.putAll(changed);
        lastStates = merged;
        fillStatus(merged);
        fillModes(merged);
        fillHaButtons(merged);
        RemoteCache.saveStates(this, merged);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        repository = new AppRepository(this);
        backgrounds = new Backgrounds(this);
        ha = new HomeAssistant(prefs);
        jf = new Jellyfin(prefs);
        ErrorLog.init(this);
        installCrashHandler(this);
        if (!"2".equals(prefs.str("skin_v", ""))) {
            // Første gang med temaernes egne ure, vejr og lyde: giv det valgte tema dem med det samme
            prefs.setStr("skin_v", "2");
            String own = Skin.clockFor(prefs.theme());
            String cur = prefs.clockStyle();
            if (own != null && ("stor".equals(cur) || "tynd".equals(cur) || "analog".equals(cur) || "hud".equals(cur))) prefs.setClockStyle(own);
            prefs.setFlag(Features.SOUNDS, true);
        }
        if (!"3".equals(prefs.str("skin_v3", ""))) {
            // Første gang med de gennemførte temaer: læg temaets nye farver, skrift og papir på
            prefs.setStr("skin_v3", "3");
            if (Themes.main(prefs.theme()) && !"apple".equals(prefs.theme())) Themes.apply(prefs, prefs.theme());
        }
        if (!"4".equals(prefs.str("skin_v4", ""))) {
            // De otte temaer står stille: intet langsomt zoom i fotoet
            prefs.setStr("skin_v4", "4");
            if (Themes.main(prefs.theme())) prefs.setFlag(Features.KEN_BURNS, false);
        }
        builtTone = Looks.layoutSignature(prefs);
        motion = Motion.level(this, prefs);
        Ui.loadFont(this, prefs.font());
        setContentView(buildLayout());
        // En ny skærm har tomme rækker: appsene skal altid fyldes i første onResume
        appsDirty = true;
        if (revealNext) {
            revealNext = false;
            Motion.reveal(root, motion, -1, -1);
        }
        applyLook();
        updateMore();
        showCached();
        NightUpdate.schedule(this);
        Net.async(() -> {
            Updater.cleanUp(this);
            return null;
        }, null);
        wokeUp = true;
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addDataScheme("package");
        Ui.registerSystemReceiver(this, packages, pkgFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = this;
        if (ambientView != null) ambientView.setRunning(true);
        backgrounds.importShared();
        loadBackground(pendingHome);
        pendingHome = false;
        if (!prefs.setupDone()) {
            // Ny stick: velkomstguiden først (navn, udgave, tema)
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }
        if (!Looks.layoutSignature(prefs).equals(builtTone)) {
            // Ny farvetone: skyggerne og kortene er bygget med den gamle, så byg skærmen forfra
            revealNext = true;
            recreate();
            return;
        }
        if (appsDirty) {
            // Indstillinger kan have ændret accentfarve eller fliser: læs baggrund og farve igen
            shownBackground = null;
            accent = prefs.accentColor();
            motion = Motion.level(this, prefs);
            applyLook();
            updateMore();
            backgrounds.importShared();
            loadBackground(false);
            refreshApps();
            // Indstillinger er ændret: gem profilen på Jellyfin, hvis den bruges
            Net.async(() -> {
                ProfileCode.pushToJellyfin(this);
                return null;
            }, null);
        }
        // Home tjenesten slås fra ved force-stop; har appen fået lov, tændes den bare igen
        if (!HomeService.isRunning()) HomeSetup.repair(this);
        refreshRemote();
        HaSocket socket = HaSocket.get(this);
        socket.addListener(haLive);
        socket.start();
        handler.removeCallbacks(tick);
        handler.post(tick);
        bootIfWoken();
        AppUpdates.maybeCheckUpdate(this, prefs);
        AppUpdates.maybeShowChangelog(this, prefs);
    }

    /**
     * En app fylder skærmen: slip forsidens store billeder (baggrund, papir, Jarvis' ur), så Fire TV'et
     * har mere hukommelse til appen og ikke lukker launcheren. De tegnes igen, når Home vises.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_UI_HIDDEN || visible == this && hasWindowFocus()) return;
        if (backdrop != null) backdrop.trim();
        if (stage != null) stage.trim();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (visible == this) visible = null;
        if (konamiVideo != null) konamiVideo.stop();
        HaSocket.get(this).removeListener(haLive);
        handler.removeCallbacks(tick);
        if (kenBurns != null) kenBurns.pause();
        if (ambientView != null) ambientView.setRunning(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_HOME, false)) pendingHome = true;
        if (moreOpen) {
            moreOpen = false;
            updateMore();
        }
        if (scroller != null) scroller.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override
    public void onBackPressed() {
        long now = System.currentTimeMillis();
        boolean twice = now - lastBack < 2000;
        lastBack = now;
        if (twice) {
            // Andet tryk lige efter: rolig tilstand, kun baggrund og ur
            lastBack = 0;
            setAmbient(true);
            return;
        }
        if (moreOpen) {
            moreOpen = false;
            updateMore();
        }
        if (scroller != null) scroller.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (konamiVideo != null && code == KeyEvent.KEYCODE_BACK) {
            // Tilbage stopper klippet; forsiden skifter alligevel
            if (event.getAction() == KeyEvent.ACTION_UP) konamiVideo.stop();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            // Kort tryk på ☰: indstillinger. Langt tryk: hurtigpanelet.
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                lastKey = System.currentTimeMillis();
                menuHandled = wakeUp();
                if (!menuHandled) handler.postDelayed(menuLongPress, 550);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(menuLongPress);
                if (!menuHandled) {
                    menuHandled = true;
                    startActivity(new Intent(this, SettingsActivity.class));
                }
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            lastKey = System.currentTimeMillis();
            if (wakeUp()) return true;
            trackKonami(code);
            if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_9 && event.getRepeatCount() == 0) {
                // Talknapper åbner app nummer 1 til 9 i Apps, ligesom kanaler
                int i = code - KeyEvent.KEYCODE_1;
                if (i < repository.apps.size()) launch(repository.apps.get(i));
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** Vækker skærmen fra natteur eller rolig tilstand. Sand hvis tasten blev brugt til det. */
    private boolean wakeUp() {
        if (night.getVisibility() == View.VISIBLE) {
            night.setVisibility(View.GONE);
            return true;
        }
        if (ambient) {
            setAmbient(false);
            return true;
        }
        return false;
    }

    /** Hurtigpanel i højre side (langt tryk på ☰), se QuickPanel. */
    private void showQuickPanel() {
        QuickPanel.show(this);
    }

    /** "Mere" kortet: viser eller skjuler rækkerne under Tilstande. */
    private void updateMore() {
        if (!Features.on(prefs, Features.COMPACT)) {
            sectionMore.removeAllViews();
            moreBox.setVisibility(View.VISIBLE);
            return;
        }
        moreBox.setVisibility(moreOpen ? View.VISIBLE : View.GONE);
        boolean focused = getCurrentFocus() != null && "more".equals(getCurrentFocus().getTag());
        LinearLayout row = startRow(sectionMore, null);
        FrameLayout[] out = new FrameLayout[1];
        LinearLayout holder = tile("more", dp(150), dp(48), "", null, out);
        TextView t = Ui.text(this, moreOpen ? "Mindre  ▴" : "Mere  ▾", 16, Ui.TEXT_DIM);
        t.setGravity(Gravity.CENTER);
        out[0].addView(t, match());
        out[0].setOnClickListener(v -> {
            moreOpen = !moreOpen;
            updateMore();
        });
        row.addView(holder);
        if (focused) restoreFocus("more");
    }

    // Layout

    private View buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        String shadeKey = prefs.str("shade", "normal");
        float shade = "ingen".equals(shadeKey) ? 0f : "let".equals(shadeKey) ? 0.55f : "kraftig".equals(shadeKey) ? 1.3f : 1f;

        // Rammen er en anelse større end skærmen, så den kan glide med fokus uden kanter
        bgFrame = new FrameLayout(this);
        bgFrame.setScaleX(1.05f);
        bgFrame.setScaleY(1.05f);
        root.addView(bgFrame, match());
        background = new ImageView(this);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgFrame.addView(background, match());

        boolean apple = Ui.apple();
        boolean staged = !apple && Stage.on();
        if (staged && Backdrop.hidesPhoto()) background.setVisibility(View.INVISIBLE);
        View shadeBottom = new View(this);
        shadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, apple
                // Apple: fotoet står klart hele vejen ned til ikonerne, kun bunden mørkner en anelse
                ? new int[]{Color.argb((int) (150 * Math.min(1f, shade)), 0, 0, 0), Color.argb((int) (40 * Math.min(1f, shade)), 0, 0, 0), 0, 0}
                : new int[]{Ui.bg(Math.min(1f, 0.95f * shade)), Ui.bg(Math.min(1f, 0.6f * shade)), Ui.bg(0f)}));
        if (!staged) root.addView(shadeBottom, match());
        if (!apple && !staged) {
            View shadeTop = new View(this);
            shadeTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Ui.bg(Math.min(1f, 0.6f * shade)), Ui.bg(0f), Ui.bg(0f)}));
            root.addView(shadeTop, match());
        }
        // Apple: fotoet mørkner og sløres, når man går ned under ikonrækken
        appleDim = new View(this);
        appleDim.setBackgroundColor(Color.BLACK);
        appleDim.setAlpha(0f);
        root.addView(appleDim, match());

        if (staged) {
            // Temaets papir, væg, karton eller skærm, tegnet én gang
            backdrop = new Backdrop(this);
            root.addView(backdrop, match());
        }
        particles = new ParticleView(this);
        particles.setVisibility(View.GONE);
        root.addView(particles, match());
        // Temaets stemning: spind, lyn, tæpper, lyskæde, bølger
        ambientView = new Ambient(this);
        if (staged) ambientView.setVisibility(View.GONE);
        root.addView(ambientView, match());

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(apple ? APPLE_MARGIN : 56), apple ? 0 : dp(32), 0, 0);
        if (staged) {
            stage = new Stage(this);
            int place = Stage.place();
            if (place == Stage.TOP) {
                // Scenen øverst i fuld bredde; rækkerne ruller under den
                column.setPadding(0, 0, 0, 0);
                column.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(Stage.sizeDp())));
                root.addView(column, match());
            } else {
                // Scenen i siden: Jarvis og Jul til venstre, Batman til højre
                LinearLayout body = new LinearLayout(this);
                body.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(Stage.sizeDp()), ViewGroup.LayoutParams.MATCH_PARENT);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                int topPad = Skin.is("jul") ? 92 : Skin.is("jarvis") ? 30 : 26;
                if (place == Stage.LEFT) {
                    column.setPadding(dp(Skin.is("jul") ? 16 : 8), dp(topPad), 0, 0);
                    body.addView(stage, sp);
                    body.addView(column, cp);
                } else {
                    column.setPadding(dp(48), dp(topPad), 0, 0);
                    body.addView(column, cp);
                    body.addView(stage, sp);
                }
                root.addView(body, match());
            }
        } else {
            root.addView(column, match());
        }

        // Top: ur til venstre, hilsen og status til højre (Apple TV viser ingen af delene)
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        if (apple || staged) top.setVisibility(View.GONE);
        column.addView(top);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clock = Ui.text(this, "", 64, Ui.TEXT);
        clock.setIncludeFontPadding(false);
        left.addView(clock);
        clockRing = new ClockRingView(this);
        clockRing.setVisibility(View.GONE);
        left.addView(clockRing, new LinearLayout.LayoutParams(dp(150), dp(150)));
        themeClock = new ThemeClock(this);
        themeClock.setVisibility(View.GONE);
        left.addView(themeClock, new LinearLayout.LayoutParams(dp(420), dp(140)));
        date = Ui.text(this, "", 18, Ui.TEXT_DIM);
        date.setLetterSpacing(0.08f);
        left.addView(date);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.setPadding(0, dp(8), dp(24), 0);
        top.addView(right);
        avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        avatar.setVisibility(View.GONE);
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dp(72), dp(72));
        avp.setMargins(0, dp(8), dp(56), 0);
        top.addView(avatar, avp);
        greeting = Ui.text(this, "", 22, Ui.TEXT);
        greeting.setGravity(Gravity.END);
        right.addView(greeting);
        together = Ui.text(this, "", 15, Ui.TEXT_DIM);
        together.setGravity(Gravity.END);
        right.addView(together);
        status = Ui.text(this, "", 15, Ui.TEXT_DIM);
        status.setGravity(Gravity.END);
        status.setPadding(0, dp(6), 0, 0);
        right.addView(status);
        arrangeTop(top, left, right);

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(0, dp(16), 0, dp(32));
        scroller.setFillViewport(true);
        column.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setGravity(apple || staged ? Gravity.TOP : Gravity.BOTTOM);
        if (staged && Stage.place() == Stage.TOP) rows.setPadding(dp(contentLeft()), 0, 0, 0);
        scroller.addView(rows, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (apple) {
            // Ikonrækken står lidt under midten med fotoet over sig, resten følger nedenunder
            scroller.setPadding(0, 0, 0, dp(48));
            View shelf = new View(this);
            rows.addView(shelf, new LinearLayout.LayoutParams(1, (int) (getResources().getDisplayMetrics().heightPixels * 0.68f)));
            sectionApps = section(rows);
            sectionNowPlaying = section(rows);
            sectionResume = section(rows);
            scroller.getViewTreeObserver().addOnScrollChangedListener(this::appleScrolled);
            // Ikonerne vokser i fokus; ingen af lagene må klippe dem eller skyggen
            for (ViewGroup g : new ViewGroup[]{column, scroller, rows, sectionApps}) {
                g.setClipChildren(false);
                g.setClipToPadding(false);
            }
        } else {
            sectionNowPlaying = section(rows);
            sectionResume = section(rows);
            sectionApps = section(rows);
        }
        sectionModes = section(rows);
        // Resten ligger i en boks, der foldes ud med "Mere"
        sectionMore = section(rows);
        moreBox = section(rows);
        sectionHa = section(moreBox);
        sectionLatest = section(moreBox);
        sectionFavorites = section(moreBox);
        sectionUpcoming = section(moreBox);
        sectionTools = section(moreBox);
        sectionRest = section(moreBox);

        ambientClock = Ui.text(this, "", 44, Ui.TEXT);
        ambientClock.setShadowLayer(dp(10), 0, 0, Color.BLACK);
        ambientClock.setAlpha(0f);
        FrameLayout.LayoutParams acp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        acp.setMargins(0, 0, dp(56), dp(40));
        root.addView(ambientClock, acp);
        ambientInfo = Ui.text(this, "", 20, Ui.TEXT);
        ambientInfo.setShadowLayer(dp(8), 0, 0, Color.BLACK);
        ambientInfo.setAlpha(0f);
        FrameLayout.LayoutParams aip = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        aip.setMargins(0, 0, dp(58), dp(100));
        root.addView(ambientInfo, aip);

        night = new FrameLayout(this);
        night.setBackgroundColor(Color.BLACK);
        night.setVisibility(View.GONE);
        nightClock = Ui.text(this, "", 120, HudView.withAlpha(Ui.TEXT, 0.25f));
        nightClock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        night.addView(nightClock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(night, match());
        return root;
    }

    /**
     * Toppen er bygget forskelligt fra tema til tema: Batman og Iron Man har uret til højre og hilsenen
     * til venstre; Superman, Biograf, Jul og Nattehimmel har uret i midten med hilsenen under, som en
     * overskrift eller en biografs skilt.
     */
    private void arrangeTop(LinearLayout top, LinearLayout left, LinearLayout right) {
        switch (Skin.key) {
            case "batman":
            case "ironman":
                top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                break;
            case "superman":
            case "biograf":
            case "jul":
            case "natte": {
                top.setOrientation(LinearLayout.VERTICAL);
                top.setGravity(Gravity.CENTER_HORIZONTAL);
                column.setPadding(dp(56), dp(28), 0, 0);
                top.setPadding(0, 0, dp(56), 0);
                left.setGravity(Gravity.CENTER_HORIZONTAL);
                left.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                right.setGravity(Gravity.CENTER_HORIZONTAL);
                right.setPadding(0, dp(4), 0, 0);
                for (TextView t : new TextView[]{greeting, together, status}) t.setGravity(Gravity.CENTER_HORIZONTAL);
                date.setGravity(Gravity.CENTER_HORIZONTAL);
                themeClock.setCentered(true);
                avatar.setVisibility(View.GONE);
                avatar.setTag("skjult");
                break;
            }
        }
    }

    /** Apple: jo længere ned, jo mørkere og mere sløret bliver fotoet bag rækkerne. */
    private void appleScrolled() {
        if (appleDim == null || scroller == null) return;
        float p = Math.min(1f, scroller.getScrollY() / (getResources().getDisplayMetrics().heightPixels * 0.35f));
        appleDim.setAlpha(0.55f * p);
        if (android.os.Build.VERSION.SDK_INT >= 31 && background != null) {
            float r = 28 * p * getResources().getDisplayMetrics().density;
            background.setRenderEffect(r < 1 ? null : android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP));
        }
    }

    /** Skrift, ur stil og profilbillede fra indstillingerne. */
    private void applyLook() {
        Ui.loadFont(this, prefs.font());
        String look = prefs.font() + "|" + prefs.clockStyle() + "|" + ProfilePicture.file(this).lastModified();
        clockRing.setColor(currentAccent());
        if (look.equals(appliedLook)) return;
        appliedLook = look;
        String style = prefs.clockStyle();
        boolean ring = "hud".equals(style) || "analog".equals(style);
        boolean drawn = ThemeClock.has(style);
        clockRing.setAnalog("analog".equals(style));
        clock.setVisibility(ring || drawn ? View.GONE : View.VISIBLE);
        clockRing.setVisibility(ring ? View.VISIBLE : View.GONE);
        themeClock.setVisibility(drawn ? View.VISIBLE : View.GONE);
        themeClock.setStyle(style);
        themeClock.setAnimate(!Motion.NONE.equals(motion));
        if (drawn) {
            String artTheme = prefs.theme();
            Net.async(() -> ThemePhotos.art(this, artTheme), (bmp, err) -> themeClock.setArt(bmp));
        }
        ambientView.setAnimate(Motion.FULL.equals(motion));
        Typeface thin = "system".equals(prefs.font()) ? Typeface.create("sans-serif-thin", Typeface.NORMAL) : Ui.display;
        clock.setTypeface("tynd".equals(style) ? thin : Ui.display);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, "tynd".equals(style) ? 96 : "sekunder".equals(style) ? 56 : 64);
        clock.setFontFeatureSettings("tnum");
        clock.setText("");
        greeting.setTypeface(Ui.display);
        date.setTypeface(Ui.display);
        ambientClock.setTypeface(Ui.display);
        nightClock.setTypeface("system".equals(prefs.font()) ? Typeface.create("sans-serif-thin", Typeface.NORMAL) : Ui.display);
        Net.async(() -> ProfilePicture.load(this), (bmp, err) -> {
            avatar.setImageBitmap(bmp);
            avatar.setVisibility(bmp == null || "skjult".equals(avatar.getTag()) ? View.GONE : View.VISIBLE);
        });
    }

    /** Venstre margen for rækkerne, når scenen står øverst (biografens tæpper fylder i siderne). */
    private static int contentLeft() {
        switch (Skin.key) {
            case "biograf": return 92;
            case "spiderman": return 26;
            default: return 48;
        }
    }

    private LinearLayout section(LinearLayout parent) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        parent.addView(s);
        return s;
    }

    // Apps

    private void refreshApps() {
        appsDirty = false;
        String focusedKey = focusedKey();
        repository.load(Features.on(prefs, Features.DICE) && jf.configured());
        if (Ui.apple()) {
            // Apple TV har ét samlet gitter: Apps, så værktøjer og (hvis valgt) alle apps
            List<AppEntry> all = new ArrayList<>(repository.apps);
            all.addAll(repository.tools);
            if (Features.on(prefs, Features.ALL_APPS)) all.addAll(repository.rest);
            fillAppleApps(all);
            sectionTools.removeAllViews();
            sectionRest.removeAllViews();
        } else if (stage != null && Stage.gridColumns() > 0) {
            fillGrid(sectionApps, "APPS", repository.apps, Stage.gridColumns());
            fillRow(sectionTools, "VÆRKTØJER", repository.tools, false);
            if (Features.on(prefs, Features.ALL_APPS)) fillRow(sectionRest, "ALLE APPS", repository.rest, false);
            else sectionRest.removeAllViews();
        } else {
            fillRow(sectionApps, "APPS", repository.apps, true);
            fillRow(sectionTools, "VÆRKTØJER", repository.tools, false);
            if (Features.on(prefs, Features.ALL_APPS)) fillRow(sectionRest, "ALLE APPS", repository.rest, false);
            else sectionRest.removeAllViews();
        }
        if (!restoreFocus(focusedKey)) focusFirst();
    }

    private String focusedKey() {
        View f = getCurrentFocus();
        if (f == null) return null;
        Object tag = f.getTag();
        if (tag instanceof AppEntry) return ((AppEntry) tag).packageName;
        if (tag instanceof String) return (String) tag;
        return null;
    }

    private boolean restoreFocus(String key) {
        if (key == null) return false;
        View v = findByKey(root, key);
        return v != null && v.requestFocus();
    }

    private static View findByKey(View v, String key) {
        Object tag = v.getTag();
        if (tag instanceof AppEntry && key.equals(((AppEntry) tag).packageName)) return v;
        if (key.equals(tag)) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findByKey(g.getChildAt(i), key);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private void focusFirst() {
        LinearLayout[] order = Ui.apple() ? new LinearLayout[]{sectionApps, sectionResume} : new LinearLayout[]{sectionResume, sectionApps};
        for (LinearLayout s : order) {
            View first = firstFocusable(s);
            if (first != null) {
                first.requestFocus();
                return;
            }
        }
    }

    static View firstFocusable(View v) {
        if (v.isFocusable() && v.getVisibility() == View.VISIBLE) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = firstFocusable(g.getChildAt(i));
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private LinearLayout startRow(LinearLayout section, String title) {
        section.removeAllViews();
        LinearLayout target = section;
        GradientDrawable frame = stage != null ? null : rowFrame();
        if (frame != null) {
            // Temaer med rammer om rækkerne: tegneseriestriber (Spider-Man) eller HUD paneler (Jarvis)
            target = new LinearLayout(this);
            target.setOrientation(LinearLayout.VERTICAL);
            target.setBackground(frame);
            target.setPadding(dp(16), dp(4), 0, dp(6));
            target.setClipChildren(false);
            target.setClipToPadding(false);
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fp.rightMargin = dp(56);
            fp.topMargin = dp(12);
            section.addView(target, fp);
        }
        if (title != null) target.addView(Ui.header(this, title, accent));
        LinearLayout owner = section;
        HorizontalScrollView hscroll = new HorizontalScrollView(this);
        hscroll.setHorizontalScrollBarEnabled(false);
        hscroll.setClipToPadding(false);
        hscroll.setClipChildren(false);
        hscroll.setPadding(0, dp(10), dp(56), dp(10));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        hscroll.addView(row);
        target.addView(hscroll);
        if (frame != null) hscroll.setPadding(0, dp(10), dp(16), dp(10));
        if (stage != null) {
            hscroll.setPadding(0, dp(Skin.is("batman") ? 18 : 12), dp(48), dp(14));
            if (Skin.is("hygge") && section != sectionMore) {
                // Rammerne står på en hylde
                row.setGravity(Gravity.BOTTOM);
                row.setPadding(dp(14), dp(10), dp(8), dp(13));
                row.setBackground(new Tiles.Shelf(this));
            }
        }
        if (staggered.add(owner)) Motion.stagger(row, motion);
        return row;
    }

    private void fillRow(LinearLayout section, String title, List<AppEntry> entries, boolean large) {
        if (entries.isEmpty()) {
            section.removeAllViews();
            return;
        }
        LinearLayout row = startRow(section, title);
        int[] widths = prefs.tileWidths();
        int w = large ? widths[0] : widths[1];
        int i = 0;
        for (AppEntry e : entries) {
            if (stage != null) {
                // Temaets egne cards: artikler, felter, plakater, rammer
                int[] size = Tiles.appSize(i);
                float f = large ? 1f : 0.8f;
                tileIndex = i++;
                row.addView(appTile(e, dp(Math.round(size[0] * f)), dp(Math.round(size[1] * f))));
            } else row.addView(appTile(e, dp(w), dp(w * 9 / 16)));
        }
    }

    /** Apps i et gitter med et fast antal pr. række (Batmans sagsmapper, julekalenderens låger). */
    private void fillGrid(LinearLayout section, String title, List<AppEntry> entries, int columns) {
        section.removeAllViews();
        if (entries.isEmpty()) return;
        section.addView(Ui.header(this, title, accent));
        LinearLayout row = null;
        for (int i = 0; i < entries.size(); i++) {
            if (i % columns == 0) {
                row = appleRow();
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = dp(i == 0 ? 8 : 16);
                section.addView(row, rp);
            }
            int[] size = Tiles.appSize(i);
            tileIndex = i;
            row.addView(appTile(entries.get(i), dp(size[0]), dp(size[1])));
        }
        section.setClipChildren(false);
    }

    /**
     * Apple TV: de første fem apps i en lys, gennemsigtig boks med runde hjørner; resten (også
     * værktøjer og alle apps) i et gitter med fem pr. række nedenunder, som på tvOS.
     */
    private void fillAppleApps(List<AppEntry> entries) {
        LinearLayout section = sectionApps;
        section.removeAllViews();
        if (entries.isEmpty()) return;
        int screen = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        int w = dp((screen - 2 * APPLE_MARGIN - 2 * APPLE_DOCK_PAD - 4 * APPLE_GAP) / 5);
        int h = w * 3 / 5;

        FrameLayout dock = new FrameLayout(this);
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(dp(26));
        glass.setColor(Color.argb(92, 235, 235, 240));
        glass.setStroke(Math.max(1, dp(1) / 2), Color.argb(70, 255, 255, 255));
        dock.setBackground(glass);
        dock.setPadding(dp(APPLE_DOCK_PAD), dp(APPLE_DOCK_PAD), dp(APPLE_DOCK_PAD), dp(APPLE_DOCK_PAD));
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        LinearLayout dockRow = appleRow();
        dock.addView(dockRow);
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockParams.rightMargin = dp(APPLE_MARGIN);
        section.addView(dock, dockParams);

        LinearLayout row = dockRow;
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0 && i % 5 == 0) {
                // Ny gitterrække under boksen, med ikonerne lige under dem i boksen
                row = appleRow();
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.leftMargin = dp(APPLE_DOCK_PAD);
                rp.topMargin = dp(i == 5 ? 34 : 6);
                section.addView(row, rp);
            }
            appleLabels = i < 5 ? LABEL_NONE : LABEL_FOCUS;
            View t = appTile(entries.get(i), w, h);
            if (i % 5 == 4) ((LinearLayout.LayoutParams) t.getLayoutParams()).rightMargin = 0;
            row.addView(t);
        }
        appleLabels = LABEL_ALWAYS;
    }

    private LinearLayout appleRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        return r;
    }

    /** Bevægelsen, der hører temaet til: Spider-Man vipper, Superman letter, Havbrise vugger. */
    private void skinFocus(View v, boolean has) {
        if (Motion.NONE.equals(motion)) return;
        switch (Skin.key) {
            case "spiderman":
                v.animate().rotation(has ? -2.5f : 0f).setDuration(180).start();
                break;
            case "superman":
                v.animate().translationY(has ? -dp(12) : 0).setDuration(has ? 260 : 180).start();
                break;
            case "hav":
                v.animate().translationY(has ? -dp(5) : 0).setDuration(600)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
                break;
        }
    }

    /** Teksten i et Apple kort bliver mørk på den lyse fokusflade og får sin farve igen bagefter. */
    private static void appleTint(View v, boolean dark, Map<TextView, Integer> saved) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (dark) {
                if (!saved.containsKey(t)) saved.put(t, t.getCurrentTextColor());
                t.setTextColor(Ui.APPLE_DARK);
            } else {
                Integer c = saved.remove(t);
                if (c != null) t.setTextColor(c);
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) appleTint(g.getChildAt(i), dark, saved);
        }
    }

    private GradientDrawable rowFrame() {
        switch (Skin.key) {
            case "spiderman": {
                GradientDrawable g = new GradientDrawable();
                g.setColor(Color.argb(150, 12, 20, 60));
                g.setStroke(dp(4), Color.parseColor("#111111"));
                return g;
            }
            case "jarvis": {
                GradientDrawable g = new GradientDrawable();
                g.setColor(Color.argb(40, 2, 20, 30));
                g.setStroke(Math.max(1, dp(1)), Color.argb(70, 79, 216, 255));
                return g;
            }
            default:
                return null;
        }
    }

    /** Fælles flise: kort med fokusramme og tekst under. */
    private LinearLayout tile(Object tag, int width, int height, String label, String sublabel, FrameLayout[] cardOut) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setClipChildren(false);
        boolean apple = Ui.apple();
        // Apple: ingen navne under ikonerne i boksen; i gitteret kommer navnet frem ved fokus
        int labels = apple && tag instanceof AppEntry ? appleLabels : LABEL_ALWAYS;
        // Apple: knapper med tekst (tilstande, Home Assistant, Mere) bliver lyse i fokus som på tvOS
        boolean plain = apple && !(tag instanceof AppEntry) && !(tag instanceof String && ((String) tag).startsWith("jf:"));
        Map<TextView, Integer> tinted = new java.util.HashMap<>();
        android.content.res.ColorStateList[] baseFill = new android.content.res.ColorStateList[1];
        final int kind = tileKind, index = tileIndex;
        final boolean staged = stage != null;
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.rightMargin = dp(apple ? APPLE_GAP : staged ? Tiles.gap() : 18);
        holder.setLayoutParams(hp);

        FrameLayout card = new FrameLayout(this);
        card.setTag(tag);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        GradientDrawable bg = Ui.card(this, Ui.CARD, Ui.CARD_STROKE, 1);
        GradientDrawable shadow = null;
        if ("tegneserie".equals(Ui.cardStyle) && !staged) {
            // Tegneserie: tyk sort kant og en hård skygge forskudt ned til højre, der bliver til accentfarven i fokus
            shadow = Ui.card(this, Ui.INK, Ui.INK, 0);
            int o = dp(7);
            android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(new Drawable[]{shadow, bg});
            layers.setLayerInset(0, o, o, 0, 0);
            layers.setLayerInset(1, 0, 0, o, o);
            card.setBackground(layers);
            card.setPadding(dp(3), dp(3), o + dp(3), o + dp(3));
            card.setClipToOutline(false);
        } else {
            card.setBackground(bg);
        }
        final GradientDrawable shadowF = shadow;
        holder.addView(card, new LinearLayout.LayoutParams(width, height));
        // Temaets egen fokus (klammer, spind, pærer, gavebånd ...) ligger øverst i cardet
        final Decor decor = !apple && !staged && Decor.active() ? new Decor(this, !Motion.NONE.equals(motion)) : null;

        TextView l = Ui.text(this, label, 13, Ui.TEXT_DIM);
        l.setSingleLine(true);
        l.setPadding(dp(2), dp(8), 0, 0);
        l.setAlpha(0.75f);
        if (labels == LABEL_NONE) l.setVisibility(View.GONE);
        if (labels == LABEL_FOCUS) {
            l.setAlpha(0f);
            l.setTextColor(Ui.TEXT);
            l.setGravity(Gravity.CENTER_HORIZONTAL);
            l.setShadowLayer(dp(6), 0, dp(1), Color.argb(160, 0, 0, 0));
        }
        holder.addView(l);
        if (staged && kind == Tiles.SMALL) Tiles.dressSmall(this, card);
        TextView sub = null;
        if (sublabel != null && !sublabel.isEmpty() && !staged) {
            sub = Ui.text(this, sublabel, 12, Ui.TEXT_DIM);
            sub.setSingleLine(true);
            sub.setPadding(dp(2), 0, 0, 0);
            sub.setAlpha(0.6f);
            holder.addView(sub);
        }
        final TextView subF = sub;
        card.setOnFocusChangeListener((v, has) -> {
            if (has) Sounds.move(this, prefs);
            if (staged) {
                Tiles.focus(this, kind, holder, card, l, has, index, motion);
                if (has && tag instanceof AppEntry) lightFollow((AppEntry) tag);
                return;
            }
            Motion.focus(v, has, motion);
            if (apple) {
                // Ingen ramme: ikonet løftes frem med en blød skygge
                v.setElevation(has ? dp(14) : 0);
                if (labels == LABEL_FOCUS) l.animate().alpha(has ? 1f : 0f).setDuration(160).start();
                else if (labels == LABEL_ALWAYS) l.setAlpha(has ? 1f : 0.75f);
                if (plain && card.getBackground() instanceof GradientDrawable) {
                    GradientDrawable g = (GradientDrawable) card.getBackground();
                    if (has) {
                        if (baseFill[0] == null) baseFill[0] = g.getColor();
                        g.setColor(Ui.APPLE_LIGHT);
                    } else if (baseFill[0] != null) {
                        g.setColor(baseFill[0]);
                        baseFill[0] = null;
                    }
                    appleTint(card, has, tinted);
                }
            } else {
                if (decor != null) {
                    if (decor.getParent() == null) card.addView(decor, match());
                    decor.bringToFront();
                    decor.setFocused(has);
                    skinFocus(v, has);
                }
                if (shadowF != null) shadowF.setColor(has ? currentAccent() : Ui.INK);
                else if (decor == null) bg.setStroke(dp((has ? 3 : 1) + Ui.restStroke()), has ? currentAccent() : Ui.CARD_STROKE);
                l.setTextColor(has ? Ui.TEXT : Ui.TEXT_DIM);
                l.setAlpha(has ? 1f : 0.75f);
            }
            if (subF != null) subF.setAlpha(has ? 0.9f : 0.6f);
            if (has && tag instanceof AppEntry) lightFollow((AppEntry) tag);
            if (has) parallax(v);
        });
        cardOut[0] = card;
        return holder;
    }

    private View appTile(AppEntry entry, int width, int height) {
        FrameLayout[] out = new FrameLayout[1];
        int index = tileIndex;
        tileKind = Tiles.APP;
        LinearLayout holder = tile(entry, width, height, entry.label, null, out);
        tileKind = Tiles.SMALL;
        FrameLayout card = out[0];
        ImageView image = new ImageView(this);
        if (entry.banner != null) {
            image.setImageDrawable(entry.banner);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(image, match());
        } else {
            image.setImageDrawable(entry.icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (AppRepository.isSpecial(entry)) image.setColorFilter(currentAccent());
            int size = Math.min(height - dp(16), dp(56));
            card.addView(image, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        }
        if (stage != null) Tiles.dressApp(this, holder, card, image, (TextView) holder.getChildAt(1), entry.label, index);
        card.setOnClickListener(v -> launch(entry));
        card.setOnLongClickListener(v -> {
            tileMenu(entry);
            return true;
        });
        return holder;
    }

    void launch(AppEntry entry) {
        Sounds.select(this, prefs);
        switch (entry.packageName) {
            case AppRepository.SETTINGS_KEY:
                FireTvMenu.show(this);
                return;
            case AppRepository.JARVIS_SETTINGS_KEY:
                startActivity(new Intent(this, SettingsActivity.class));
                return;
            case AppRepository.DICE_KEY:
                rollDice();
                return;
        }
        try {
            startActivity(entry.launchIntent);
            prefs.noteLaunch(entry.packageName);
            if (!"egen".equals(prefs.appSort())) appsDirty = true;
        } catch (Exception e) {
            Toast.makeText(this, "Kunne ikke åbne " + entry.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void tileMenu(AppEntry entry) {
        String row = repository.rowOf(entry);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (!(AppRepository.ROW_APPS.equals(row) && !"egen".equals(prefs.appSort()))) {
            labels.add("Flyt til venstre");
            actions.add(() -> repository.move(entry, -1));
            labels.add("Flyt til højre");
            actions.add(() -> repository.move(entry, 1));
        }
        if (AppRepository.isSpecial(entry)) {
            // Genvejsfliserne kan skjules, nu hvor menuknappen åbner indstillingerne
            labels.add("Skjul");
            actions.add(() -> {
                java.util.Set<String> h = prefs.hidden();
                h.add(entry.packageName);
                prefs.setHidden(h);
                Toast.makeText(this, entry.label + " er skjult. Menuknappen åbner stadig indstillingerne.", Toast.LENGTH_LONG).show();
            });
        } else {
            for (String[] r : new String[][]{{AppRepository.ROW_APPS, "Flyt til Apps"},
                    {AppRepository.ROW_TOOLS, "Flyt til Værktøjer"}, {AppRepository.ROW_REST, "Flyt til Alle apps"}}) {
                if (r[0].equals(row)) continue;
                labels.add(r[1]);
                actions.add(() -> prefs.setRowOverride(entry.packageName, r[0]));
            }
            labels.add("Skjul");
            actions.add(() -> {
                java.util.Set<String> h = prefs.hidden();
                h.add(entry.packageName);
                prefs.setHidden(h);
                Toast.makeText(this, entry.label + " er skjult. Vis den igen under Indstillinger.", Toast.LENGTH_LONG).show();
            });
            labels.add("Omdøb");
            actions.add(() -> renameApp(entry));
            if (!prefs.str("label:" + entry.packageName, "").isEmpty()) {
                labels.add("Brug det oprindelige navn");
                actions.add(() -> prefs.setStr("label:" + entry.packageName, ""));
            }
            labels.add("App info");
            actions.add(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", entry.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
            });
        }
        new AlertDialog.Builder(this, Ui.dialogTheme())
                .setTitle(entry.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    actions.get(which).run();
                    refreshApps();
                    restoreFocus(entry.packageName);
                })
                .show();
    }

    /** Eget navn til en app, fx "TV 2" i stedet for "TV 2 Play", som det står på flisen og i temaets overskrifter. */
    private void renameApp(AppEntry entry) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setText(entry.label);
        input.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        new AlertDialog.Builder(this, Ui.dialogTheme())
                .setTitle("Omdøb " + entry.label)
                .setView(box)
                .setPositiveButton("Gem", (d, w) -> {
                    String name = input.getText().toString().trim();
                    prefs.setStr("label:" + entry.packageName, name);
                    appsDirty = true;
                    refreshApps();
                    restoreFocus(entry.packageName);
                })
                .setNegativeButton("Annullér", null)
                .show();
    }

    // Jellyfin og Home Assistant

    /** Sidste kendte forside fra disken, vist med det samme ved start; refreshRemote henter det nye bagefter. */
    private void showCached() {
        if (jf.configured() && Features.on(prefs, Features.RESUME)) {
            List<Jellyfin.Item> r = RemoteCache.loadItems(this, "resume");
            if (r != null) fillMedia(sectionResume, "FORTSÆT AT SE", r, true, true);
        }
        if (jf.configured() && Features.on(prefs, Features.LATEST)) {
            List<Jellyfin.Item> l = RemoteCache.loadItems(this, "latest");
            if (l != null) fillMedia(sectionLatest, "NYT PÅ SERVEREN", l, false, false);
        }
        if (jf.configured() && Features.on(prefs, Features.FAVORITES)) {
            List<Jellyfin.Item> l = RemoteCache.loadItems(this, "favorites");
            if (l != null) fillMedia(sectionFavorites, "MIN LISTE", l, false, false);
        }
        if (ha.configured()) {
            Map<String, JSONObject> states = RemoteCache.loadStates(this);
            if (states != null) {
                lastStates = states;
                fillStatus(states);
                fillModes(states);
                fillHaButtons(states);
            }
            if (Features.on(prefs, Features.UPCOMING)) {
                List<HomeAssistant.Upcoming> u = RemoteCache.loadUpcoming(this);
                if (u != null) fillUpcoming(u);
            }
        }
    }

    void refreshRemote() {
        lastRemoteRefresh = System.currentTimeMillis();
        refreshNowPlaying();
        if (jf.configured()) {
            if (Features.on(prefs, Features.RESUME)) {
                boolean nextUp = Features.on(prefs, Features.NEXT_UP);
                Net.async(() -> {
                    List<Jellyfin.Item> items = jf.resume();
                    if (nextUp) {
                        try {
                            jf.addNextUp(items);
                        } catch (Exception ignored) {
                        }
                    }
                    return items;
                }, (items, err) -> {
                    if (items == null && err != null) statusRow(sectionResume, "FORTSÆT AT SE", "Jellyfin svarer ikke");
                    else {
                        fillMedia(sectionResume, "FORTSÆT AT SE", items, true, true);
                        if (items != null) RemoteCache.saveItems(this, "resume", items);
                    }
                    // Serveren svarer ikke på den gemte adresse: prøv LAN og Tailscale til næste gang
                    if (err instanceof java.io.IOException) Net.async(jf::findServer, null);
                });
            } else sectionResume.removeAllViews();
            if (Features.on(prefs, Features.LATEST)) {
                Net.async(jf::latest, (items, err) -> {
                    fillMedia(sectionLatest, "NYT PÅ SERVEREN", items, false, false);
                    if (items != null) RemoteCache.saveItems(this, "latest", items);
                });
            } else sectionLatest.removeAllViews();
            if (Features.on(prefs, Features.FAVORITES)) {
                Net.async(jf::favorites, (items, err) -> {
                    if (items == null) return;
                    fillMedia(sectionFavorites, "MIN LISTE", items, false, false);
                    RemoteCache.saveItems(this, "favorites", items);
                });
            } else sectionFavorites.removeAllViews();
        } else {
            sectionResume.removeAllViews();
            sectionLatest.removeAllViews();
            sectionFavorites.removeAllViews();
        }
        if (ha.configured() && (Features.on(prefs, Features.HA_STATUS) || Features.on(prefs, Features.HA_BUTTONS)
                || Features.on(prefs, Features.MODES))) {
            java.util.Set<String> needed = ha.needed();
            Net.async(() -> ha.statesFor(needed), (states, err) -> {
                if (states == null) {
                    status.setText(err == null ? "" : "Home Assistant svarer ikke");
                    sectionHa.removeAllViews();
                    if (err != null && Features.on(prefs, Features.MODES)) {
                        statusRow(sectionModes, "TILSTANDE", "Home Assistant svarer ikke");
                    } else sectionModes.removeAllViews();
                    return;
                }
                lastStates = states;
                fillStatus(states);
                fillModes(states);
                fillHaButtons(states);
                RemoteCache.saveStates(this, states);
            });
        } else {
            status.setText("");
            sectionHa.removeAllViews();
            sectionModes.removeAllViews();
        }
        if (ha.configured() && Features.on(prefs, Features.UPCOMING)) {
            Net.async(ha::upcoming, (list, err) -> {
                fillUpcoming(list);
                if (list != null) RemoteCache.saveUpcoming(this, list);
            });
        } else {
            sectionUpcoming.removeAllViews();
        }
    }

    private void refreshNowPlaying() {
        lastNowPlaying = System.currentTimeMillis();
        if (!jf.configured() || !Features.on(prefs, Features.NOW_PLAYING)) {
            sectionNowPlaying.removeAllViews();
            nowPlayingIds = "";
            return;
        }
        Net.async(jf::nowPlaying, (items, err) -> {
            if (items == null) return;
            // Rækken bygges kun om, når noget nyt starter eller stopper, så fokus ikke hopper
            StringBuilder ids = new StringBuilder();
            for (Jellyfin.Item it : items) ids.append(it.id).append(it.subtitle).append(it.progress / 5).append(',');
            if (ids.toString().equals(nowPlayingIds)) return;
            nowPlayingIds = ids.toString();
            fillMedia(sectionNowPlaying, "AFSPILLES NU", items, true, false);
        });
    }

    /** Tilstande: ét card pr. valg i den valgte input_select; den aktive lyser i accentfarven. */
    private void fillModes(Map<String, JSONObject> states) {
        JSONObject st = states.get(prefs.haModes());
        JSONObject attrs = st == null ? null : st.optJSONObject("attributes");
        JSONArray options = attrs == null ? null : attrs.optJSONArray("options");
        if (!Features.on(prefs, Features.MODES) || options == null || options.length() == 0) {
            sectionModes.removeAllViews();
            return;
        }
        String focusedKey = focusedKey();
        String active = st.optString("state");
        LinearLayout row = startRow(sectionModes, "TILSTANDE");
        for (int i = 0; i < options.length(); i++) {
            String option = options.optString(i);
            boolean on = option.equals(active);
            FrameLayout[] out = new FrameLayout[1];
            LinearLayout holder = tile("mode:" + option, dp(150), dp(56), "", null, out);
            FrameLayout card = out[0];
            if (on) card.setBackground(Ui.apple() ? Ui.card(this, Color.argb(92, 255, 255, 255), Color.TRANSPARENT, 0)
                    : Ui.card(this, HudView.withAlpha(currentAccent(), 0.30f), currentAccent(), 2));
            TextView t = Ui.text(this, option, 17, on || Ui.apple() ? Ui.TEXT : Ui.TEXT_DIM);
            t.setTypeface(on ? (Ui.apple() ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Ui.headerFace) : Typeface.DEFAULT);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            card.addView(t, match());
            card.setOnClickListener(v -> {
                if (on) return;
                t.setText("...");
                Net.async(() -> {
                    ha.selectOption(prefs.haModes(), option);
                    return true;
                }, (ok, err) -> {
                    if (err != null) Toast.makeText(this, "HA: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                    handler.postDelayed(this::refreshRemote, 600);
                });
            });
            row.addView(holder);
        }
        restoreFocus(focusedKey);
    }

    private void fillUpcoming(List<HomeAssistant.Upcoming> list) {
        if (list == null || list.isEmpty()) {
            sectionUpcoming.removeAllViews();
            return;
        }
        String focusedKey = focusedKey();
        LinearLayout row = startRow(sectionUpcoming, "KOMMER SNART");
        SimpleDateFormat day = new SimpleDateFormat("EEEE d/M", DANISH);
        for (HomeAssistant.Upcoming u : list) {
            FrameLayout[] out = new FrameLayout[1];
            LinearLayout holder = tile("up:" + u.title + u.when, dp(220), dp(84), "", null, out);
            FrameLayout card = out[0];
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(dp(14), dp(10), dp(14), dp(10));
            boolean apple = Ui.apple();
            String when = whenText(u.when, day);
            TextView w = Ui.text(this, apple ? Character.toUpperCase(when.charAt(0)) + when.substring(1) : when.toUpperCase(DANISH),
                    apple ? 13 : 12, apple ? Ui.APPLE_GRAY : currentAccent());
            w.setTypeface(apple ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Ui.headerFace);
            w.setLetterSpacing(apple ? 0f : 0.12f);
            inner.addView(w);
            TextView t = Ui.text(this, u.title, 16, Ui.TEXT);
            t.setSingleLine(true);
            inner.addView(t);
            TextView d = Ui.text(this, u.detail, 13, Ui.TEXT_DIM);
            d.setSingleLine(true);
            inner.addView(d);
            card.addView(inner, match());
            row.addView(holder);
        }
        restoreFocus(focusedKey);
    }

    private static String whenText(long when, SimpleDateFormat day) {
        Calendar today = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(when);
        int diff = c.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR)
                + 365 * (c.get(Calendar.YEAR) - today.get(Calendar.YEAR));
        if (diff <= 0) return "I dag";
        if (diff == 1) return "I morgen";
        return day.format(c.getTime());
    }

    /** Et lille card i stedet for en række, når serveren ikke svarer. Tryk for at prøve igen. */
    private void statusRow(LinearLayout section, String title, String message) {
        String focusedKey = focusedKey();
        LinearLayout row = startRow(section, title);
        FrameLayout[] out = new FrameLayout[1];
        LinearLayout holder = tile("status:" + title, dp(300), dp(76), "", null, out);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(14), dp(10), dp(14), dp(10));
        inner.addView(Ui.text(this, message, 16, Ui.TEXT));
        inner.addView(Ui.text(this, "Tryk for at prøve igen", 13, Ui.TEXT_DIM));
        out[0].addView(inner, match());
        out[0].setOnClickListener(v -> {
            Net.async(jf::findServer, (ok, err) -> refreshRemote());
            Toast.makeText(this, "Prøver igen ...", Toast.LENGTH_SHORT).show();
        });
        row.addView(holder);
        restoreFocus(focusedKey);
    }

    private void fillMedia(LinearLayout section, String title, List<Jellyfin.Item> items, boolean progress, boolean play) {
        if (items == null || items.isEmpty()) {
            section.removeAllViews();
            return;
        }
        if (stage != null) {
            stagedMedia(section, title, items, progress, play);
            return;
        }
        String focusedKey = focusedKey();
        LinearLayout row = startRow(section, title);
        int w = dp(256), h = dp(144);
        for (Jellyfin.Item it : items) {
            FrameLayout[] out = new FrameLayout[1];
            LinearLayout holder = tile("jf:" + it.id, w, h, it.title, it.subtitle, out);
            FrameLayout card = out[0];
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, match());
            loadImage(it.imageUrl, img);
            if (progress && it.progress > 0) {
                View bar = new View(this);
                bar.setBackgroundColor(currentAccent());
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w * Math.min(it.progress, 100) / 100, dp(4), Gravity.BOTTOM);
                card.addView(bar, lp);
            }
            if (play) {
                // OK starter afsnittet med det samme; langt tryk åbner siden om filmen eller serien
                card.setOnClickListener(v -> playInWholphin(it));
                card.setOnLongClickListener(v -> {
                    openInWholphin(it.id);
                    return true;
                });
            } else card.setOnClickListener(v -> openInWholphin(it.id));
            row.addView(holder);
        }
        restoreFocus(focusedKey);
    }

    /** Fortsæt at se i temaets sprog: billetter, gavemærker, sagslister, avisnotitser, rammer. */
    private void stagedMedia(LinearLayout section, String title, List<Jellyfin.Item> items, boolean progress, boolean play) {
        String focusedKey = focusedKey();
        int[] size = Tiles.mediaSize();
        boolean list = Tiles.mediaList();
        LinearLayout row;
        if (list) {
            section.removeAllViews();
            section.addView(Ui.header(this, title, accent));
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setClipChildren(false);
            row.setPadding(0, dp(8), 0, dp(4));
            section.addView(row);
        } else row = startRow(section, title);
        int i = 0;
        for (Jellyfin.Item it : items) {
            tileKind = Tiles.MEDIA;
            tileIndex = i;
            FrameLayout[] out = new FrameLayout[1];
            LinearLayout holder = tile("jf:" + it.id, list ? ViewGroup.LayoutParams.MATCH_PARENT : dp(size[0]), dp(size[1]), it.title, null, out);
            tileKind = Tiles.SMALL;
            FrameLayout card = out[0];
            ImageView img = Tiles.mediaContent(this, card, holder, (TextView) holder.getChildAt(1), it.title, it.subtitle, progress ? it.progress : 0, i);
            if (img != null) loadImage(it.imageUrl, img);
            if (list) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) holder.getLayoutParams();
                lp.rightMargin = dp(48);
                lp.bottomMargin = dp(8);
            }
            if (play) {
                card.setOnClickListener(v -> playInWholphin(it));
                card.setOnLongClickListener(v -> {
                    openInWholphin(it.id);
                    return true;
                });
            } else card.setOnClickListener(v -> openInWholphin(it.id));
            row.addView(holder);
            i++;
        }
        restoreFocus(focusedKey);
    }

    private void loadImage(String url, ImageView into) {
        Bitmap cached = images.get(url);
        if (cached != null) {
            into.setImageBitmap(cached);
            return;
        }
        into.setTag(url);
        Net.async(() -> Net.bitmap(url, null), (bmp, err) -> {
            if (bmp == null) return;
            images.put(url, bmp);
            if (url.equals(into.getTag())) into.setImageBitmap(bmp);
        });
    }

    private void playInWholphin(Jellyfin.Item it) {
        Intent play = new Intent("com.github.damontecres.wholphin.PLAYBACK",
                Uri.parse("wholphin://play?itemId=" + it.id + "&position=" + it.positionMs))
                .setPackage(WHOLPHIN)
                .putExtra("itemId", it.id)
                .putExtra("position", it.positionMs)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(play);
        } catch (Exception e) {
            openInWholphin(it.id);
        }
    }

    void openInWholphin(String itemId) {
        Intent deep = new Intent(Intent.ACTION_VIEW, Uri.parse("wholphin://item/" + itemId))
                .setPackage(WHOLPHIN)
                .putExtra("itemId", itemId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(deep);
            return;
        } catch (Exception ignored) {
        }
        Intent app = getPackageManager().getLeanbackLaunchIntentForPackage(WHOLPHIN);
        if (app == null) app = getPackageManager().getLaunchIntentForPackage(WHOLPHIN);
        if (app != null) startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void fillStatus(Map<String, JSONObject> states) {
        if (!Features.on(prefs, Features.HA_STATUS)) {
            status.setText("");
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String id : statusEntities(states)) {
            JSONObject st = states.get(id);
            if (st == null) continue;
            String part = statusText(id, st);
            if (part != null) parts.add(part);
        }
        status.setText(String.join("  ·  ", parts));
        stageParts = parts;
        updateStage();
        ambientText = String.join("  ·  ", parts.subList(0, Math.min(2, parts.size())));
        if (ambient) ambientInfo.setText(ambientText);
    }

    /** De valgte entiteter; "auto" er det første vejr, HA har. */
    private List<String> statusEntities(Map<String, JSONObject> states) {
        List<String> out = new ArrayList<>();
        for (String id : prefs.haStatus()) {
            if (!"auto".equals(id)) {
                out.add(id);
                continue;
            }
            for (String k : states.keySet()) {
                if (k.startsWith("weather.")) {
                    out.add(k);
                    break;
                }
            }
        }
        return out;
    }

    /** Kort tekst til statuslinjen: vejr som "Sol 12°", til/fra som "Lys sync til", tal med enhed. */
    static String statusText(String id, JSONObject st) {
        String state = st.optString("state");
        if (state.isEmpty() || state.equals("unknown") || state.equals("unavailable")) return null;
        JSONObject a = st.optJSONObject("attributes");
        if (id.startsWith("weather.")) {
            String temp = a == null ? "" : a.optString("temperature", "");
            return HomeAssistant.weatherDa(state) + (temp.isEmpty() ? "" : " " + temp + "°");
        }
        String name = HomeAssistant.friendly(st, id);
        if (state.equals("on") || state.equals("off")) return name + " " + (state.equals("on") ? "til" : "fra");
        // En tæller på 0 (fx pakker på vej) fylder bare
        if (state.equals("0")) return null;
        if (state.length() > 14) return null;
        String unit = a == null ? "" : a.optString("unit_of_measurement", "");
        return name + " " + state + (unit.isEmpty() ? "" : " " + unit);
    }

    private void fillHaButtons(Map<String, JSONObject> states) {
        if (!Features.on(prefs, Features.HA_BUTTONS)) {
            sectionHa.removeAllViews();
            return;
        }
        String focusedKey = focusedKey();
        LinearLayout row = startRow(sectionHa, "VÆRELSET");
        int n = 0;
        for (String id : prefs.haButtons().split(",")) {
            JSONObject st = states.get(id);
            // Knapper til enheder, der ikke findes (længere), vises ikke
            if (id.isEmpty() || st == null || ++n > 4) continue;
            String name = HomeAssistant.friendly(st, id);
            String state = stateDa(st.optString("state"));
            boolean on = st != null && "on".equals(st.optString("state"));
            FrameLayout[] out = new FrameLayout[1];
            LinearLayout holder = tile("ha:" + id, dp(176), dp(76), "", null, out);
            FrameLayout card = out[0];
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(dp(14), dp(10), dp(14), dp(10));
            TextView t = Ui.text(this, name, 16, Ui.TEXT);
            t.setSingleLine(true);
            inner.addView(t);
            TextView s = Ui.text(this, state, 14, on ? currentAccent() : Ui.TEXT_DIM);
            s.setSingleLine(true);
            inner.addView(s);
            card.addView(inner, match());
            JSONObject attrs = st.optJSONObject("attributes");
            JSONArray options = attrs == null ? null : attrs.optJSONArray("options");
            if (options != null) s.setTextColor(currentAccent());
            card.setOnClickListener(v -> {
                if (options != null && options.length() > 0) {
                    chooseOption(id, name, options, st.optString("state"));
                    return;
                }
                s.setText("...");
                Net.async(() -> {
                    ha.press(id);
                    return true;
                }, (ok, err) -> {
                    if (err != null) Toast.makeText(this, "HA: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                    handler.postDelayed(this::refreshRemote, 700);
                });
            });
            row.addView(holder);
        }
        restoreFocus(focusedKey);
    }

    /** Valglister (fx tilstande) åbner en liste med det aktive valg markeret. */
    void chooseOption(String id, String name, JSONArray options, String active) {
        String[] names = new String[options.length()];
        int checked = -1;
        for (int i = 0; i < names.length; i++) {
            names[i] = options.optString(i);
            if (names[i].equals(active)) checked = i;
        }
        new AlertDialog.Builder(this, Ui.dialogTheme())
                .setTitle(name)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    d.dismiss();
                    Net.async(() -> {
                        ha.selectOption(id, names[which]);
                        return true;
                    }, (ok, err) -> {
                        if (err != null) Toast.makeText(this, "HA: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                        handler.postDelayed(this::refreshRemote, 600);
                    });
                })
                .show();
    }

    private static String stateDa(String s) {
        switch (s) {
            case "on": return "Til";
            case "off": return "Fra";
            case "unavailable": return "Ikke tilgængelig";
            case "unknown": return "Ukendt";
            default: return s;
        }
    }

    // Baggrund

    void loadBackground(boolean homePressed) {
        String source = prefs.str("bg_source", "billeder");
        // Ældre værdier (tegnede scener) bruger nu temaets fotos
        boolean themed = "tema".equals(source) || "scene".equals(source) || "ternet".equals(source);
        Backgrounds from = backgrounds;
        String photoTheme = prefs.str("photo_theme", prefs.theme());
        if (themed) {
            from = new Backgrounds(this, ThemePhotos.dir(this, photoTheme));
            if (from.list().size() < 2 && !photosFetched.contains(photoTheme)) {
                // Første gang: tv'et henter temaets fotos selv og viser dem, når de er nede
                photosFetched.add(photoTheme);
                Net.async(() -> ThemePhotos.download(this, photoTheme), (got, err) -> {
                    if (Boolean.TRUE.equals(got)) {
                        shownBackground = null;
                        loadBackground(false);
                    }
                });
            }
        }
        String pick = "ensfarvet".equals(source) ? null : from.current(homePressed);
        String name = (themed ? "tema:" + photoTheme + ":" : "") + (pick == null ? "ingen" : pick);
        if (name.equals(shownBackground)) {
            if (kenBurns != null) kenBurns.resume();
            return;
        }
        shownBackground = name;
        File f = pick == null ? null : from.file(pick);
        int width = Math.max(getResources().getDisplayMetrics().widthPixels, 1280);
        boolean wantAccent = "auto".equals(prefs.accentKey());
        int fixedAccent = prefs.accentColor();
        // Billedet læses på en baggrundstråd, så Home ikke hakker, når baggrunden skifter
        // Papirtemaerne viser ikke fotoet; Spider-Man og Batman viser det kun i et felt, så et mindre billede er nok
        boolean paper = stage != null && Backdrop.hidesPhoto();
        boolean need = !paper || Stage.wantsPhoto() || wantAccent;
        int decodeWidth = paper ? width / 2 : width;
        Net.async(() -> {
            Bitmap bmp = f == null || !need ? null : Backgrounds.decode(f, decodeWidth);
            return new Object[]{bmp, wantAccent ? Backgrounds.accent(bmp, Ui.CYAN) : fixedAccent};
        }, (res, err) -> {
            if (res == null || isDestroyed() || !name.equals(shownBackground)) return;
            Bitmap bmp = (Bitmap) res[0];
            if (stage != null && Stage.wantsPhoto() && !Stage.photoBehind()) stage.setPhoto(bmp);
            background.setAlpha(0f);
            background.setImageBitmap(bmp);
            background.animate().alpha(1f).setDuration(400).start();
            int newAccent = (Integer) res[1];
            if (newAccent != accent) {
                accent = newAccent;
                refreshApps();
            }
            // En diskret skygge, så uret kan læses på lyse fotos
            clock.setShadowLayer(dp(6), 0, dp(1), Color.argb(110, 0, 0, 0));
            startKenBurns(bmp != null && Features.on(prefs, Features.KEN_BURNS) && !Motion.NONE.equals(motion)
                    && background.getVisibility() == View.VISIBLE);
        });
    }

    private void startKenBurns(boolean on) {
        if (kenBurns != null) kenBurns.cancel();
        // Hardware lag: zoomen flytter bare en færdig tekstur i stedet for at tegne billedet om
        background.setLayerType(on ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
        background.setScaleX(1f);
        background.setScaleY(1f);
        background.setTranslationX(0);
        if (!on) return;
        kenBurns = ObjectAnimator.ofPropertyValuesHolder(background,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, -dp(30)));
        kenBurns.setDuration(45000);
        kenBurns.setRepeatCount(ValueAnimator.INFINITE);
        kenBurns.setRepeatMode(ValueAnimator.REVERSE);
        kenBurns.start();
    }

    int currentAccent() {
        return System.currentTimeMillis() < ironManUntil ? Color.parseColor("#FFC640") : accent;
    }

    // Gimmicks

    private String greetingText() {
        return Greeting.text(prefs);
    }

    private void updateGreeting() {
        set(greeting, greetingText());
        set(together, Greeting.together(prefs));
        updateStage();
    }

    private void updateStage() {
        if (stage == null) return;
        stage.setInfo(greetingText(), stageParts, prefs.profileName());
        stage.tick(new Date());
    }

    private void updateSeason() {
        int mode = Features.on(prefs, Features.SEASONS) ? ParticleView.modeFor(Calendar.getInstance()) : ParticleView.NONE;
        // Efterårstemaet: blade resten af året, når der ikke er sne eller fyrværkeri
        int themed = themedParticles(prefs.particles());
        if ((mode == ParticleView.NONE || mode == ParticleView.EMBERS) && themed != ParticleView.NONE) mode = themed;
        if ("ingen".equals(prefs.particles()) && mode != ParticleView.FIREWORKS) mode = ParticleView.NONE;
        // Let: kun nytårets fyrværkeri. Ingen: slet ingenting
        if (Motion.LIGHT.equals(motion) && mode != ParticleView.FIREWORKS) mode = ParticleView.NONE;
        if (Motion.NONE.equals(motion)) mode = ParticleView.NONE;
        if (System.currentTimeMillis() < ironManUntil) mode = ParticleView.FIREWORKS;
        // Temaer med en scene har deres stemning i selve designet; kun nytårets fyrværkeri kommer forbi
        if (stage != null && mode != ParticleView.FIREWORKS) mode = ParticleView.NONE;
        particles.setMode(mode);
    }

    /** Temaets egne effekter; højtiderne (sne, fyrværkeri) vinder kun, når der ikke er valgt noget. */
    private static int themedParticles(String key) {
        switch (key) {
            case "blade": return ParticleView.LEAVES;
            case "kronblade": return ParticleView.PETALS;
            case "stjerner": return ParticleView.STARS;
            case "regn": return ParticleView.RAIN;
            case "sne": return ParticleView.SNOW;
            default: return ParticleView.NONE;
        }
    }

    private void updateNight() {
        long idleMs = System.currentTimeMillis() - lastKey;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (Features.on(prefs, Features.NIGHT_CLOCK) && h < 6 && idleMs > 2 * 60 * 1000) {
            if (night.getVisibility() != View.VISIBLE) night.setVisibility(View.VISIBLE);
            return;
        }
        if (Features.on(prefs, Features.AMBIENT) && !ambient && idleMs > 3 * 60 * 1000) setAmbient(true);
        if (ambient) set(ambientClock, timeFormat.format(new Date()));
    }

    /** Rolig tilstand: kun baggrunden og et lille ur, som en pauseskærm inde i forsiden. */
    void setAmbient(boolean on) {
        ambient = on;
        column.animate().alpha(on ? 0f : 1f).setDuration(on ? 1500 : 250).start();
        ambientClock.setText(timeFormat.format(new Date()));
        ambientClock.animate().alpha(on ? 1f : 0f).setDuration(on ? 1500 : 250).start();
        ambientInfo.setText(ambientText);
        ambientInfo.animate().alpha(on ? 0.85f : 0f).setDuration(on ? 1500 : 250).start();
        if (!on) lastKey = System.currentTimeMillis();
    }

    /** Baggrunden glider en smule modsat fokus, så forsiden føles som et rum. */
    private void parallax(View focused) {
        if (!Features.on(prefs, Features.PARALLAX) || !Motion.FULL.equals(motion)) {
            bgFrame.setTranslationX(0);
            bgFrame.setTranslationY(0);
            return;
        }
        int[] loc = new int[2];
        focused.getLocationOnScreen(loc);
        float w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return;
        float fx = (loc[0] + focused.getWidth() / 2f) / w - 0.5f;
        float fy = (loc[1] + focused.getHeight() / 2f) / h - 0.5f;
        bgFrame.animate().translationX(-fx * dp(36)).translationY(-fy * dp(20)).setDuration(600).start();
    }

    private void bootIfWoken() {
        if (!wokeUp || visible != this) return;
        wokeUp = false;
        if (Features.on(prefs, Features.BOOT) && !Motion.NONE.equals(motion)) HudView.forPrefs(this, prefs, greetingText(), currentAccent()).play(root);
        speakGreeting();
    }

    private void speakGreeting() {
        if (!Features.on(prefs, Features.VOICE) || !ha.configured()) return;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 8 || h >= 22) return;
        if (System.currentTimeMillis() - prefs.lastGreeting() < 2 * 60 * 60 * 1000) return;
        prefs.setLastGreeting(System.currentTimeMillis());
        String part = h < 10 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        String name = prefs.profileName();
        String message = part + (name.isEmpty() ? "" : ", " + name) + ". All systems online.";
        Net.async(() -> {
            String engine = ha.ttsEngine();
            if (engine == null) return null;
            return ha.ttsUrl(message, engine);
        }, (url, err) -> {
            if (url == null) return;
            try {
                if (voice != null) voice.release();
                voice = new MediaPlayer();
                voice.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).build());
                voice.setDataSource(url);
                voice.setOnPreparedListener(MediaPlayer::start);
                voice.prepareAsync();
            } catch (Exception ignored) {
            }
        });
    }

    private void trackKonami(int key) {
        if (!Features.on(prefs, Features.KONAMI)) return;
        if (key == KONAMI[konamiPos]) {
            konamiPos++;
            if (konamiPos == KONAMI.length) {
                konamiPos = 0;
                ironMan();
            }
        } else {
            konamiPos = key == KONAMI[0] ? 1 : 0;
        }
    }

    private AlphaVideoView konamiVideo;
    private boolean suitUp;

    /**
     * Konami sekvensen: klippet fra telefonsiden spiller oven på forsiden, og midt i (under blitzet)
     * skifter forsiden til rød og guld og åbner sig som en cirkel. Uden klip: et guld blitz.
     */
    private void ironMan() {
        if (konamiVideo != null || suitUp) return;
        java.io.File clip = KonamiClip.file(this);
        if (Motion.NONE.equals(motion)) {
            ironManSwitch(true);
            return;
        }
        if (!clip.isFile()) {
            // Uden klip: appens egen hjelm sekvens (Fuld) eller bare et blitz (Let)
            if (Motion.FULL.equals(motion)) {
                suitUp = true;
                new SuitUpView(this, () -> ironManSwitch(false), () -> suitUp = false).play(root);
            } else flash(() -> ironManSwitch(true));
            return;
        }
        boolean[] switched = {false};
        AlphaVideoView video = new AlphaVideoView(this, clip, () -> {
            if (konamiVideo != null) root.removeView(konamiVideo);
            konamiVideo = null;
            if (!switched[0]) ironManSwitch(true);
        });
        konamiVideo = video;
        root.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Runnable watch = new Runnable() {
            @Override
            public void run() {
                if (konamiVideo != video) return;
                if (!switched[0] && video.progress() >= 0.45f) {
                    switched[0] = true;
                    ironManSwitch(false);
                    return;
                }
                handler.postDelayed(this, 40);
            }
        };
        handler.postDelayed(watch, 200);
        // Sikkerhed: et klip der hænger, fjernes efter 30 sekunder
        handler.postDelayed(() -> {
            if (konamiVideo == video) video.stop();
        }, 30_000);
    }

    /** Hvidt-guld blitz over hele skærmen; skiftet sker midt i, når skærmen er helt dækket. */
    private void flash(Runnable then) {
        View f = new View(this);
        f.setBackgroundColor(Color.parseColor("#FFF3D0"));
        f.setAlpha(0f);
        root.addView(f, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.animate().alpha(1f).setDuration(120).withEndAction(() -> {
            then.run();
            f.animate().alpha(0f).setDuration(Motion.LIGHT.equals(motion) ? 300 : 700).withEndAction(() -> root.removeView(f)).start();
        }).start();
    }

    private void ironManSwitch(boolean hud) {
        ironManUntil = System.currentTimeMillis() + 60_000;
        if (hud) new HudView(this, "Iron Man mode", currentAccent()).play(root);
        if (Motion.FULL.equals(motion)) Motion.reveal(column, motion, -1, -1);
        refreshApps();
        updateSeason();
        if (ha.configured()) {
            Net.async(() -> {
                ha.fireEvent("jarvis_konami", new JSONObject().put("kilde", "jarvis_tv"));
                return true;
            }, null);
        }
        handler.postDelayed(() -> {
            refreshApps();
            updateSeason();
        }, 60_500);
    }

    private void lightFollow(AppEntry entry) {
        if (!Features.on(prefs, Features.LIGHT_FOLLOW) || !ha.configured() || AppRepository.isSpecial(entry)) return;
        if (lightFollow != null) handler.removeCallbacks(lightFollow);
        lightFollow = () -> {
            int color = Backgrounds.accent(drawableBitmap(entry.banner != null ? entry.banner : entry.icon), Ui.CYAN);
            String hex = String.format("#%06X", 0xFFFFFF & color);
            Net.async(() -> {
                ha.fireEvent("jarvis_tv_fokus", new JSONObject()
                        .put("app", entry.packageName)
                        .put("navn", entry.label)
                        .put("farve", hex)
                        .put("rgb", new JSONArray().put(Color.red(color)).put(Color.green(color)).put(Color.blue(color))));
                return true;
            }, null);
        };
        handler.postDelayed(lightFollow, 800);
    }

    private static Bitmap drawableBitmap(Drawable d) {
        if (d == null) return null;
        // Tegn en kopi: setBounds på flisens egen drawable ville skrumpe billedet på flisen
        Drawable.ConstantState cs = d.getConstantState();
        Drawable copy = cs != null ? cs.newDrawable().mutate() : null;
        Bitmap b = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (copy != null) {
            copy.setBounds(0, 0, 64, 36);
            copy.draw(c);
        } else {
            android.graphics.Rect old = new android.graphics.Rect(d.getBounds());
            d.setBounds(0, 0, 64, 36);
            d.draw(c);
            d.setBounds(old);
        }
        return b;
    }

    private void rollDice() {
        Toast.makeText(this, "Slår terninger...", Toast.LENGTH_SHORT).show();
        Net.async(jf::randomUnwatchedMovie, (it, err) -> {
            if (it == null) {
                Toast.makeText(this, err == null ? "Ingen usete film" : "Jellyfin svarer ikke", Toast.LENGTH_SHORT).show();
                return;
            }
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
            loadImage(jf.backdropUrl(it.id), img);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.addView(img);
            TextView ov = Ui.text(this, it.overview, 15, Ui.TEXT_DIM);
            ov.setMaxLines(4);
            ov.setPadding(dp(24), dp(12), dp(24), 0);
            box.addView(ov);
            new AlertDialog.Builder(this, Ui.dialogTheme())
                    .setTitle(it.title + (it.subtitle.isEmpty() ? "" : " (" + it.subtitle + ")"))
                    .setView(box)
                    .setPositiveButton("Se den", (d, w) -> openInWholphin(it.id))
                    .setNeutralButton("Slå igen", (d, w) -> rollDice())
                    .setNegativeButton("Luk", null)
                    .show();
        });
    }

    // Fejl og nyheder

    private static boolean crashHandlerInstalled;

    /** Gemmer en fejl, før appen lukker, så den kan ses under Om > Seneste fejl uden adb. */
    static void installCrashHandler(Context context) {
        if (crashHandlerInstalled) return;
        crashHandlerInstalled = true;
        Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(sw));
                String trace = sw.toString();
                if (trace.length() > 2000) trace = trace.substring(0, 2000);
                String when = new SimpleDateFormat("d. MMM HH:mm", DANISH).format(new Date());
                new Prefs(app).setLastCrash(when + " (version " + Updater.installedName(app) + ")\n\n" + trace);
                ErrorLog.note("Nedbrud", error);
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
