package dk.selveste.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.Random;

/**
 * Temaernes baggrunde, tegnet i appen. Flade farver og trykte teknikker (tegneserie raster, strik,
 * tartan, risograf, stjernekort) i stedet for glød og slør, så hvert tema har sit eget udtryk.
 * Alt er tegnet med fast frø, så scenen ser ens ud hver gang.
 */
final class Scenes {

    private Scenes() {
    }

    /** nøgle, navn */
    static final String[][] ALL = {
            {"tegning", "Jarvis tegning"},
            {"reaktor", "Reaktor"},
            {"grafit", "Grafit"},
            {"biograf", "Biograftæppe"},
            {"spindel", "Spindelvæv"},
            {"gotham", "Gotham"},
            {"solstraaler", "Solstråler"},
            {"tartan", "Tartan"},
            {"riso", "Rosentryk"},
            {"strik", "Strik"},
            {"botanisk", "Eukalyptus"},
            {"stjernekort", "Stjernekort"},
            {"julestrik", "Julesweater"},
            {"boelger", "Bølger"},
    };

    static String name(String key) {
        for (String[] s : ALL) if (s[0].equals(key)) return s[1];
        return ALL[0][1];
    }

    static Bitmap draw(String key, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float u = w / 1920f;
        switch (key) {
            case "reaktor": reaktor(c, w, h, u); break;
            case "grafit": grafit(c, w, h, u); break;
            case "biograf": biograf(c, w, h, u); break;
            case "spindel": spindel(c, w, h, u); break;
            case "gotham": gotham(c, w, h, u); break;
            case "solstraaler": solstraaler(c, w, h, u); break;
            case "tartan": tartan(c, w, h, u); break;
            case "riso": riso(c, w, h, u); break;
            case "strik": strik(c, w, h, u); break;
            case "botanisk": botanisk(c, w, h, u); break;
            case "stjernekort": stjernekort(c, w, h, u); break;
            case "julestrik": julestrik(c, w, h, u); break;
            case "boelger": boelger(c, w, h, u); break;
            default: tegning(c, w, h, u);
        }
        return b;
    }

    // Små hjælpere

    private static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    private static Paint stroke(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    private static int a(int color, float alpha) {
        return Color.argb(Math.round(255 * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int hex(String s) {
        return Color.parseColor(s);
    }

    /** Papir eller film: små prikker i lys og mørk, så fladen ikke er død. */
    private static void grain(Canvas c, int w, int h, float strength, long seed) {
        Random r = new Random(seed);
        Paint dark = fill(a(Color.BLACK, strength));
        Paint light = fill(a(Color.WHITE, strength));
        int n = w * h / 60;
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * w;
            float y = r.nextFloat() * h;
            c.drawRect(x, y, x + 1.5f, y + 1.5f, r.nextBoolean() ? dark : light);
        }
    }

    // Jarvis: teknisk tegning med gitter, målestreger og ringe

    private static void tegning(Canvas c, int w, int h, float u) {
        int ink = hex("#4FD8FF");
        c.drawColor(hex("#04121C"));
        Paint minor = stroke(a(ink, 0.06f), 1);
        Paint major = stroke(a(ink, 0.14f), 1);
        for (float x = 0; x < w; x += 24 * u) c.drawLine(x, 0, x, h, (Math.round(x / (24 * u)) % 5 == 0) ? major : minor);
        for (float y = 0; y < h; y += 24 * u) c.drawLine(0, y, w, y, (Math.round(y / (24 * u)) % 5 == 0) ? major : minor);

        float cx = w * 0.78f, cy = h * 0.40f;
        Paint thin = stroke(a(ink, 0.30f), 1.5f * u);
        Paint faint = stroke(a(ink, 0.16f), 1.5f * u);
        c.drawCircle(cx, cy, 300 * u, thin);
        c.drawCircle(cx, cy, 250 * u, faint);
        c.drawCircle(cx, cy, 170 * u, thin);
        c.drawCircle(cx, cy, 70 * u, faint);
        for (int i = 0; i < 120; i++) {
            double ang = Math.toRadians(i * 3);
            float len = (i % 10 == 0 ? 22 : 9) * u;
            float r1 = 300 * u, r2 = r1 + len;
            c.drawLine(cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1,
                    cx + (float) Math.cos(ang) * r2, cy + (float) Math.sin(ang) * r2, i % 10 == 0 ? thin : faint);
        }
        Paint arc = stroke(a(ink, 0.55f), 6 * u);
        arc.setStrokeCap(Paint.Cap.BUTT);
        RectF ring = new RectF(cx - 210 * u, cy - 210 * u, cx + 210 * u, cy + 210 * u);
        c.drawArc(ring, -40, 70, false, arc);
        c.drawArc(ring, 150, 35, false, arc);
        c.drawArc(ring, 200, 12, false, arc);
        // Sigtekorn
        c.drawLine(cx - 380 * u, cy, cx - 90 * u, cy, faint);
        c.drawLine(cx + 90 * u, cy, cx + 380 * u, cy, faint);
        c.drawLine(cx, cy - 380 * u, cx, cy - 90 * u, faint);
        c.drawLine(cx, cy + 90 * u, cx, cy + 380 * u, faint);
        // Mållinje med pile
        float y = h * 0.90f, x1 = w * 0.56f, x2 = w * 0.96f;
        c.drawLine(x1, y, x2, y, thin);
        c.drawLine(x1, y - 12 * u, x1, y + 12 * u, thin);
        c.drawLine(x2, y - 12 * u, x2, y + 12 * u, thin);
        for (float x = x1; x < x2; x += 40 * u) c.drawLine(x, y, x, y - 6 * u, faint);
        // Hjørnebeslag
        Paint corner = stroke(a(ink, 0.45f), 2 * u);
        float m = 28 * u, l = 60 * u;
        c.drawLine(m, m, m + l, m, corner);
        c.drawLine(m, m, m, m + l, corner);
        c.drawLine(w - m, m, w - m - l, m, corner);
        c.drawLine(w - m, m, w - m, m + l, corner);
    }

    // Iron Man: rustningsplader og reaktor, guld på rødt

    private static void reaktor(Canvas c, int w, int h, float u) {
        int gold = hex("#E8B04A");
        c.drawColor(hex("#1A0606"));
        int[] plates = {hex("#2B0909"), hex("#230707"), hex("#330B0B"), hex("#1F0606")};
        Paint seam = stroke(a(gold, 0.35f), 2 * u);
        // Skrå plader hen over skærmen
        float step = 260 * u;
        int i = 0;
        for (float x = -h; x < w + h; x += step, i++) {
            Path p = new Path();
            p.moveTo(x, h);
            p.lineTo(x + h * 0.55f, 0);
            p.lineTo(x + h * 0.55f + step, 0);
            p.lineTo(x + step, h);
            p.close();
            c.drawPath(p, fill(plates[i % plates.length]));
            c.drawLine(x, h, x + h * 0.55f, 0, seam);
        }
        // Vandret panelfuge
        c.drawLine(0, h * 0.72f, w, h * 0.72f, seam);
        c.drawLine(0, h * 0.72f + 8 * u, w, h * 0.72f + 8 * u, stroke(a(gold, 0.15f), 2 * u));

        float cx = w * 0.80f, cy = h * 0.42f;
        c.drawCircle(cx, cy, 200 * u, fill(hex("#140404")));
        c.drawCircle(cx, cy, 200 * u, stroke(gold, 6 * u));
        c.drawCircle(cx, cy, 182 * u, stroke(a(gold, 0.5f), 2 * u));
        // Ti segmenter som på reaktoren
        for (int s = 0; s < 10; s++) {
            double a0 = Math.toRadians(s * 36 + 4), a1 = Math.toRadians(s * 36 + 32);
            float r0 = 110 * u, r1 = 170 * u;
            Path seg = new Path();
            seg.moveTo(cx + (float) Math.cos(a0) * r0, cy + (float) Math.sin(a0) * r0);
            seg.lineTo(cx + (float) Math.cos(a0) * r1, cy + (float) Math.sin(a0) * r1);
            seg.lineTo(cx + (float) Math.cos(a1) * r1, cy + (float) Math.sin(a1) * r1);
            seg.lineTo(cx + (float) Math.cos(a1) * r0, cy + (float) Math.sin(a1) * r0);
            seg.close();
            c.drawPath(seg, fill(hex("#3A0E0E")));
            c.drawPath(seg, stroke(gold, 2 * u));
        }
        c.drawCircle(cx, cy, 95 * u, stroke(gold, 4 * u));
        c.drawCircle(cx, cy, 62 * u, fill(hex("#F3E6C8")));
        c.drawCircle(cx, cy, 62 * u, stroke(gold, 4 * u));
        c.drawCircle(cx, cy, 30 * u, stroke(a(gold, 0.6f), 3 * u));
    }

    // Minimal: grafit med filmkorn og én fin streg

    private static void grafit(Canvas c, int w, int h, float u) {
        c.drawColor(hex("#111214"));
        grain(c, w, h, 0.035f, 7);
        c.drawLine(w * 0.06f, h * 0.30f, w * 0.94f, h * 0.30f, stroke(a(Color.WHITE, 0.08f), 1.5f * u));
    }

    // Biograf: rødt tæppe med læg, kappe foroven og pærer

    private static void biograf(Canvas c, int w, int h, float u) {
        c.drawColor(hex("#0E0909"));
        int[] reds = {hex("#5E0E14"), hex("#4A0A10"), hex("#6E141B"), hex("#520C12"), hex("#64101A")};
        // Venstre og højre tæppe, samlet ind mod midten forneden
        for (int side = 0; side < 2; side++) {
            Path drape = new Path();
            if (side == 0) {
                drape.moveTo(0, 0);
                drape.lineTo(w * 0.27f, 0);
                drape.cubicTo(w * 0.25f, h * 0.45f, w * 0.16f, h * 0.70f, w * 0.20f, h);
                drape.lineTo(0, h);
            } else {
                drape.moveTo(w, 0);
                drape.lineTo(w * 0.73f, 0);
                drape.cubicTo(w * 0.75f, h * 0.45f, w * 0.84f, h * 0.70f, w * 0.80f, h);
                drape.lineTo(w, h);
            }
            drape.close();
            c.save();
            c.clipPath(drape);
            int k = 0;
            for (float x = 0; x < w; x += 46 * u, k++) {
                c.drawRect(x, 0, x + 46 * u, h, fill(reds[k % reds.length]));
                c.drawLine(x, 0, x, h, stroke(a(Color.BLACK, 0.35f), 3 * u));
            }
            c.restore();
            c.drawPath(drape, stroke(hex("#2A0508"), 4 * u));
        }
        // Kappe med buer og guldkant
        float vh = 110 * u;
        c.drawRect(0, 0, w, vh, fill(hex("#6E141B")));
        float r = 40 * u;
        for (float x = r; x < w + r; x += r * 2) c.drawCircle(x, vh, r, fill(hex("#6E141B")));
        c.drawLine(0, vh * 0.55f, w, vh * 0.55f, stroke(hex("#C9A04A"), 4 * u));
        // Pærer
        Paint bulb = fill(hex("#F6E7C1"));
        Paint socket = fill(hex("#3A2A12"));
        for (float x = 30 * u; x < w; x += 60 * u) {
            c.drawCircle(x, vh * 0.30f, 7 * u, socket);
            c.drawCircle(x, vh * 0.30f, 5 * u, bulb);
        }
        // Scenegulv
        c.drawRect(0, h * 0.94f, w, h, fill(hex("#1C1210")));
        c.drawLine(0, h * 0.94f, w, h * 0.94f, stroke(hex("#C9A04A"), 2 * u));
    }

    // Spider-Man: tegneserierude, rødt og blåt, net og raster

    private static void spindel(Canvas c, int w, int h, float u) {
        int blue = hex("#13235E"), red = hex("#C4161C"), ink = hex("#0A0A14");
        c.drawColor(blue);
        // Raster i den blå del nede til venstre
        Paint blueDot = fill(hex("#1B2F78"));
        for (float y = 0; y < h; y += 22 * u) {
            for (float x = 0; x < w * 0.6f; x += 22 * u) {
                float off = (Math.round(y / (22 * u)) % 2) * 11 * u;
                float t = Math.max(0, 1 - (float) Math.hypot(x / w, (h - y) / h) * 1.4f);
                if (t > 0.02f) c.drawCircle(x + off, y, 9 * u * t, blueDot);
            }
        }
        // Rød flade med takket kant
        Path redArea = new Path();
        redArea.moveTo(w * 0.50f, 0);
        redArea.lineTo(w, 0);
        redArea.lineTo(w, h);
        redArea.lineTo(w * 0.70f, h);
        Random r = new Random(3);
        int steps = 14;
        for (int i = steps - 1; i > 0; i--) {
            float t = i / (float) steps;
            float x = w * 0.50f + (w * 0.20f) * t;
            float y = h * t;
            float jag = (i % 2 == 0 ? -1 : 1) * (18 + r.nextInt(18)) * u;
            redArea.lineTo(x + jag, y);
        }
        redArea.close();
        c.drawPath(redArea, fill(red));
        c.save();
        c.clipPath(redArea);
        Paint redDot = fill(hex("#9E1016"));
        for (float y = 0; y < h; y += 20 * u) {
            for (float x = w * 0.45f; x < w; x += 20 * u) {
                float off = (Math.round(y / (20 * u)) % 2) * 10 * u;
                float t = 1 - (float) Math.hypot((w - x) / w, y / h) * 1.6f;
                if (t > 0.02f) c.drawCircle(x + off, y, 8 * u * t, redDot);
            }
        }
        // Net ud fra hjørnet oppe til højre
        Paint web = stroke(ink, 3 * u);
        float cx = w, cy = 0;
        int spokes = 11;
        double[] angs = new double[spokes];
        for (int i = 0; i < spokes; i++) angs[i] = Math.toRadians(90 + i * (90.0 / (spokes - 1)));
        float maxR = w * 0.75f;
        for (double ang : angs) c.drawLine(cx, cy, cx + (float) Math.cos(ang) * maxR, cy + (float) Math.sin(ang) * maxR, web);
        for (int k = 1; k < 12; k++) {
            float rad = (float) (120 * u * Math.pow(k, 1.15));
            if (rad > maxR) break;
            for (int i = 0; i < spokes - 1; i++) {
                double a0 = angs[i], a1 = angs[i + 1], am = (a0 + a1) / 2;
                Path seg = new Path();
                seg.moveTo(cx + (float) Math.cos(a0) * rad, cy + (float) Math.sin(a0) * rad);
                // Trådene hænger lidt ind mod midten
                seg.quadTo(cx + (float) Math.cos(am) * rad * 0.86f, cy + (float) Math.sin(am) * rad * 0.86f,
                        cx + (float) Math.cos(a1) * rad, cy + (float) Math.sin(a1) * rad);
                c.drawPath(seg, web);
            }
        }
        c.restore();
        c.drawPath(redArea, stroke(ink, 8 * u));
    }

    // Batman: Gotham om natten, flad skyline og signal

    private static void gotham(Canvas c, int w, int h, float u) {
        int signal = hex("#E8D48A");
        c.drawColor(hex("#101318"));
        float sx = w * 0.56f, sy = h * 0.24f, sr = 120 * u;
        // Lyskeglen som en flad, svag flade
        Path beam = new Path();
        beam.moveTo(w * 0.58f, h);
        beam.lineTo(w * 0.62f, h);
        beam.lineTo(sx + sr * 0.9f, sy + sr * 0.4f);
        beam.lineTo(sx - sr * 0.9f, sy - sr * 0.1f);
        beam.close();
        c.drawPath(beam, fill(a(signal, 0.08f)));
        c.drawCircle(sx, sy, sr, fill(a(signal, 0.88f)));
        c.drawCircle(sx, sy, sr, stroke(a(signal, 0.5f), 3 * u));
        drawBat(c, sx, sy, sr * 1.55f, fill(hex("#101318")));
        // Skyer der skærer ind i signalet
        Paint cloud = fill(hex("#151920"));
        float[][] puffs = {{0.42f, 0.28f, 70}, {0.47f, 0.31f, 55}, {0.66f, 0.18f, 60}, {0.71f, 0.21f, 80}, {0.76f, 0.18f, 50}};
        for (float[] p : puffs) c.drawCircle(w * p[0], h * p[1], p[2] * u, cloud);
        c.drawRect(w * 0.39f, h * 0.29f, w * 0.52f, h * 0.34f, cloud);
        c.drawRect(w * 0.64f, h * 0.18f, w * 0.81f, h * 0.23f, cloud);

        Random r = new Random(11);
        Paint back = fill(hex("#181C23"));
        Paint window = fill(a(signal, 0.28f));
        float x = 0;
        while (x < w) {
            float bw = (50 + r.nextInt(90)) * u;
            float bh = h * (0.22f + r.nextFloat() * 0.30f);
            c.drawRect(x, h - bh, x + bw, h, back);
            if (r.nextInt(3) == 0) c.drawRect(x + bw * 0.45f, h - bh - 40 * u, x + bw * 0.55f, h - bh, back);
            for (float wy = h - bh + 14 * u; wy < h - 10 * u; wy += 16 * u) {
                for (float wx = x + 8 * u; wx < x + bw - 10 * u; wx += 14 * u) {
                    if (r.nextInt(9) == 0) c.drawRect(wx, wy, wx + 5 * u, wy + 7 * u, window);
                }
            }
            x += bw + r.nextInt(8) * u;
        }
        Paint front = fill(hex("#0A0B0D"));
        x = -20 * u;
        while (x < w) {
            float bw = (70 + r.nextInt(120)) * u;
            float bh = h * (0.08f + r.nextFloat() * 0.18f);
            c.drawRect(x, h - bh, x + bw, h, front);
            if (r.nextInt(2) == 0) {
                // Gotisk tårn med spids
                Path spire = new Path();
                spire.moveTo(x + bw * 0.3f, h - bh);
                spire.lineTo(x + bw * 0.5f, h - bh - 70 * u);
                spire.lineTo(x + bw * 0.7f, h - bh);
                spire.close();
                c.drawPath(spire, front);
            }
            x += bw;
        }
    }

    /** Simpel flagermus set forfra: krop, ører og vinger med buet underkant. */
    private static void drawBat(Canvas c, float cx, float cy, float width, Paint p) {
        float s = width / 2f;
        Path bat = new Path();
        bat.moveTo(cx, cy - s * 0.10f);
        bat.lineTo(cx + s * 0.06f, cy - s * 0.24f);
        bat.lineTo(cx + s * 0.09f, cy - s * 0.12f);
        bat.quadTo(cx + s * 0.45f, cy - s * 0.30f, cx + s * 0.62f, cy - s * 0.22f);
        bat.quadTo(cx + s * 0.50f, cy - s * 0.10f, cx + s * 0.55f, cy + s * 0.06f);
        bat.quadTo(cx + s * 0.40f, cy - s * 0.02f, cx + s * 0.32f, cy + s * 0.10f);
        bat.quadTo(cx + s * 0.22f, cy + s * 0.02f, cx + s * 0.14f, cy + s * 0.12f);
        bat.quadTo(cx + s * 0.07f, cy + s * 0.06f, cx, cy + s * 0.24f);
        bat.quadTo(cx - s * 0.07f, cy + s * 0.06f, cx - s * 0.14f, cy + s * 0.12f);
        bat.quadTo(cx - s * 0.22f, cy + s * 0.02f, cx - s * 0.32f, cy + s * 0.10f);
        bat.quadTo(cx - s * 0.40f, cy - s * 0.02f, cx - s * 0.55f, cy + s * 0.06f);
        bat.quadTo(cx - s * 0.50f, cy - s * 0.10f, cx - s * 0.62f, cy - s * 0.22f);
        bat.quadTo(cx - s * 0.45f, cy - s * 0.30f, cx - s * 0.09f, cy - s * 0.12f);
        bat.lineTo(cx - s * 0.06f, cy - s * 0.24f);
        bat.close();
        c.drawPath(bat, p);
    }

    // Superman: retroplakat med solstråler og kappe

    private static void solstraaler(Canvas c, int w, int h, float u) {
        c.drawColor(hex("#10398A"));
        float cx = w * 0.60f, cy = h * 0.24f;
        int rays = 32;
        float big = w * 2.5f;
        Paint ray = fill(hex("#1748A8"));
        for (int i = 0; i < rays; i += 2) {
            double a0 = Math.PI * 2 * i / rays, a1 = Math.PI * 2 * (i + 1) / rays;
            Path p = new Path();
            p.moveTo(cx, cy);
            p.lineTo(cx + (float) Math.cos(a0) * big, cy + (float) Math.sin(a0) * big);
            p.lineTo(cx + (float) Math.cos(a1) * big, cy + (float) Math.sin(a1) * big);
            p.close();
            c.drawPath(p, ray);
        }
        int sun = hex("#F6C232");
        c.drawCircle(cx, cy, 150 * u, stroke(sun, 4 * u));
        c.drawCircle(cx, cy, 120 * u, fill(sun));
        // Flade skyer
        Paint cloud = fill(hex("#E9F0FF"));
        float[][] puffs = {{0.30f, 0.40f, 50}, {0.35f, 0.37f, 70}, {0.41f, 0.40f, 55}, {0.84f, 0.42f, 35}, {0.88f, 0.40f, 48}, {0.92f, 0.42f, 34}};
        for (float[] p : puffs) c.drawCircle(w * p[0], h * p[1], p[2] * u, cloud);
        c.drawRect(w * 0.30f, h * 0.40f, w * 0.41f, h * 0.40f + 50 * u, cloud);
        c.drawRect(w * 0.84f, h * 0.42f, w * 0.92f, h * 0.42f + 34 * u, cloud);
        // Kappen fejer ind forneden
        Path cape = new Path();
        cape.moveTo(-0.05f * w, h * 0.58f);
        cape.cubicTo(w * 0.20f, h * 0.62f, w * 0.40f, h * 0.80f, w * 0.62f, h * 1.02f);
        cape.lineTo(-0.05f * w, h * 1.02f);
        cape.close();
        c.drawPath(cape, fill(hex("#C8202A")));
        Paint fold = stroke(hex("#A3171F"), 10 * u);
        for (int i = 0; i < 3; i++) {
            Path f = new Path();
            float o = i * 0.11f;
            f.moveTo(-0.02f * w, h * (0.70f + o));
            f.cubicTo(w * (0.15f + o * 0.6f), h * (0.74f + o), w * (0.26f + o * 0.6f), h * (0.86f + o), w * (0.38f + o * 0.5f), h * 1.02f);
            c.drawPath(f, fold);
        }
        c.drawPath(cape, stroke(hex("#7E0F15"), 4 * u));
    }

    // Efterår: tartan i rust, sennep og oliven på creme

    private static void tartan(Canvas c, int w, int h, float u) {
        c.drawColor(hex("#E9DCC6"));
        int rust = hex("#A8472A"), olive = hex("#6B6A3A"), mustard = hex("#C99A3B"), cream = hex("#F3EADB"), brown = hex("#5A3420");
        int[] colors = {rust, cream, olive, cream, mustard, olive, cream, brown, cream, olive, cream};
        float[] widths = {64, 8, 26, 8, 6, 26, 8, 4, 8, 26, 8};
        float total = 0;
        for (float wd : widths) total += wd * u;
        for (int pass = 0; pass < 2; pass++) {
            float pos = 0;
            int i = 0;
            float limit = pass == 0 ? w : h;
            while (pos < limit) {
                float wd = widths[i % widths.length] * u;
                Paint p = fill(a(colors[i % colors.length], 0.40f));
                if (pass == 0) c.drawRect(pos, 0, pos + wd, h, p);
                else c.drawRect(0, pos, w, pos + wd, p);
                pos += wd;
                i++;
            }
        }
        // Twill: fine skrå tråde giver vævet struktur
        Paint twill = stroke(a(brown, 0.07f), 1.2f * u);
        for (float x = -h; x < w; x += 5 * u) c.drawLine(x, h, x + h, 0, twill);
        grain(c, w, h, 0.03f, 5);
    }

    // Rosé: risograf tryk med to farver der ikke rammer helt præcist

    private static void riso(Canvas c, int w, int h, float u) {
        int pink = hex("#F08CA0"), red = hex("#C2334D");
        c.drawColor(hex("#F6E3E1"));
        // Rasterflade i pink nede til højre
        Paint dot = fill(a(pink, 0.55f));
        for (float y = h * 0.45f; y < h; y += 16 * u) {
            for (float x = w * 0.55f; x < w; x += 16 * u) {
                float t = (float) Math.hypot((x - w * 0.86f) / (w * 0.32f), (y - h * 0.82f) / (h * 0.45f));
                if (t < 1) c.drawCircle(x, y, 5.5f * u * (1 - t * 0.7f), dot);
            }
        }
        float[][] flowers = {{0.82f, 0.32f, 1.6f}, {0.94f, 0.64f, 1.0f}, {0.70f, 0.80f, 0.8f}, {0.47f, 0.08f, 0.8f}, {0.56f, 0.03f, 0.55f}};
        Random r = new Random(9);
        for (float[] f : flowers) {
            float fx = w * f[0], fy = h * f[1], s = f[2] * u;
            // Stilk og blad i den røde farve
            Path stem = new Path();
            stem.moveTo(fx, fy);
            stem.quadTo(fx - 60 * s, fy + 160 * s, fx - 20 * s, fy + 320 * s);
            c.drawPath(stem, stroke(a(red, 0.75f), 5 * s));
            c.save();
            c.translate(fx - 40 * s, fy + 170 * s);
            c.rotate(-35);
            c.drawOval(new RectF(-50 * s, -16 * s, 50 * s, 16 * s), fill(a(red, 0.55f)));
            c.restore();
            // Kronblade i pink, midten i rød lidt forskudt
            float rot = r.nextFloat() * 72;
            for (int p = 0; p < 5; p++) {
                double ang = Math.toRadians(rot + p * 72);
                c.drawCircle(fx + (float) Math.cos(ang) * 44 * s, fy + (float) Math.sin(ang) * 44 * s, 52 * s, fill(a(pink, 0.62f)));
            }
            c.drawCircle(fx + 7 * s, fy + 5 * s, 26 * s, fill(a(red, 0.85f)));
            c.drawCircle(fx + 7 * s, fy + 5 * s, 12 * s, fill(a(hex("#F6E3E1"), 0.9f)));
        }
        grain(c, w, h, 0.045f, 13);
    }

    // Hygge: strik med nordisk bort

    private static void strik(Canvas c, int w, int h, float u) {
        int base = hex("#2A1D17");
        c.drawColor(base);
        int knit = hex("#3A291F"), knitDark = hex("#33241B"), mustard = hex("#D9A441"), cream = hex("#EADBC4");
        float cw = 22 * u, ch = 20 * u;
        int cols = (int) (w / cw) + 2, rows = (int) (h / ch) + 2;
        int bandTop = (int) (rows * 0.60f), bandBottom = bandTop + 11;
        int topBand = (int) (rows * 0.06f);
        Random r = new Random(4);
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                int color = (i + j) % 7 == 0 ? knitDark : knit;
                if (j >= bandTop && j <= bandBottom) {
                    int mid = (bandTop + bandBottom) / 2;
                    int dx = Math.abs((i % 12) - 6), dy = Math.abs(j - mid);
                    if (j == bandTop || j == bandBottom) color = (i % 2 == 0) ? cream : base;
                    else if (dx + dy == 4) color = mustard;
                    else if (dx + dy == 1) color = cream;
                    else color = hex("#4A3226");
                } else if (j == topBand || j == topBand + 2) {
                    color = (i % 2 == 0) ? mustard : knit;
                } else if (j == topBand + 1) {
                    color = (i % 4 == 1) ? cream : knit;
                }
                stitch(c, i * cw, j * ch, cw, ch, color, r);
            }
        }
    }

    /** Én maske: to skrå ovaler der danner et V, som på strikkede trøjer. */
    private static void stitch(Canvas c, float x, float y, float cw, float ch, int color, Random r) {
        Paint p = fill(color);
        float jitter = (r.nextFloat() - 0.5f) * cw * 0.05f;
        c.save();
        c.translate(x + cw * 0.30f + jitter, y + ch * 0.5f);
        c.rotate(-28);
        c.drawOval(new RectF(-cw * 0.22f, -ch * 0.55f, cw * 0.22f, ch * 0.55f), p);
        c.restore();
        c.save();
        c.translate(x + cw * 0.70f + jitter, y + ch * 0.5f);
        c.rotate(28);
        c.drawOval(new RectF(-cw * 0.22f, -ch * 0.55f, cw * 0.22f, ch * 0.55f), p);
        c.restore();
    }

    // Salvie: eukalyptusgrene tegnet med streg på papir

    private static void botanisk(Canvas c, int w, int h, float u) {
        c.drawColor(hex("#E6EBE1"));
        grain(c, w, h, 0.03f, 21);
        branch(c, u, new float[]{-0.03f * w, 0.55f * h, 0.08f * w, 0.30f * h, 0.12f * w, 0.12f * h, 0.26f * w, -0.04f * h}, 1.0f, 2);
        branch(c, u, new float[]{1.03f * w, 0.30f * h, 0.92f * w, 0.50f * h, 0.90f * w, 0.80f * h, 0.78f * w, 1.04f * h}, 1.1f, 3);
        branch(c, u, new float[]{1.03f * w, 0.02f * h, 0.96f * w, 0.08f * h, 0.90f * w, 0.12f * h, 0.82f * w, 0.20f * h}, 0.55f, 5);
    }

    private static void branch(Canvas c, float u, float[] b, float scale, long seed) {
        int line = hex("#4F6F4A"), leafFill = hex("#C9D6BF"), leafFill2 = hex("#B6C7AA");
        Path stem = new Path();
        stem.moveTo(b[0], b[1]);
        stem.cubicTo(b[2], b[3], b[4], b[5], b[6], b[7]);
        c.drawPath(stem, stroke(line, 3 * u * scale));
        Random r = new Random(seed);
        Paint outline = stroke(line, 2 * u * scale);
        for (int i = 1; i < 12; i++) {
            float t = i / 12f;
            float x = bez(b[0], b[2], b[4], b[6], t), y = bez(b[1], b[3], b[5], b[7], t);
            float dx = bez(b[0], b[2], b[4], b[6], t + 0.01f) - x, dy = bez(b[1], b[3], b[5], b[7], t + 0.01f) - y;
            float len = (float) Math.hypot(dx, dy);
            float nx = -dy / len, ny = dx / len;
            float rad = (34 + r.nextInt(14)) * u * scale * (1.15f - t * 0.45f);
            for (int side = -1; side <= 1; side += 2) {
                float lx = x + nx * side * rad * 1.1f, ly = y + ny * side * rad * 1.1f;
                c.drawLine(x, y, lx, ly, outline);
                c.drawCircle(lx, ly, rad, fill(side < 0 ? leafFill : leafFill2));
                c.drawCircle(lx, ly, rad, outline);
                // Bladets midtribbe
                c.drawLine(lx - nx * side * rad * 0.6f, ly - ny * side * rad * 0.6f, lx + nx * side * rad * 0.5f, ly + ny * side * rad * 0.5f,
                        stroke(a(line, 0.5f), 1.2f * u * scale));
            }
        }
    }

    private static float bez(float p0, float p1, float p2, float p3, float t) {
        float m = 1 - t;
        return m * m * m * p0 + 3 * m * m * t * p1 + 3 * m * t * t * p2 + t * t * t * p3;
    }

    // Nattehimmel: stjernekort med gitter, stjernebilleder og månesegl

    private static void stjernekort(Canvas c, int w, int h, float u) {
        int bg = hex("#0D0F2A"), cream = hex("#F1E6C8");
        c.drawColor(bg);
        float gx = w * 0.5f, gy = h * 1.7f;
        Paint grid = stroke(a(cream, 0.07f), 1.2f * u);
        for (float r = 300 * u; r < w * 1.6f; r += 170 * u) c.drawCircle(gx, gy, r, grid);
        for (int i = -9; i <= 9; i++) {
            double ang = Math.toRadians(-90 + i * 8);
            c.drawLine(gx, gy, gx + (float) Math.cos(ang) * w * 2, gy + (float) Math.sin(ang) * w * 2, grid);
        }
        Random r = new Random(17);
        for (int i = 0; i < 320; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h;
            float s = (0.8f + r.nextFloat() * r.nextFloat() * 2.4f) * u;
            c.drawCircle(x, y, s, fill(a(cream, 0.45f + r.nextFloat() * 0.55f)));
        }
        // Karlsvognen og to mindre billeder
        drawConstellation(c, u, cream, new float[][]{{0.62f, 0.30f}, {0.68f, 0.27f}, {0.73f, 0.29f}, {0.78f, 0.33f}, {0.81f, 0.44f}, {0.90f, 0.43f}, {0.89f, 0.31f}, {0.78f, 0.33f}}, w, h);
        drawConstellation(c, u, cream, new float[][]{{0.30f, 0.08f}, {0.34f, 0.14f}, {0.38f, 0.09f}, {0.42f, 0.15f}, {0.45f, 0.10f}}, w, h);
        drawConstellation(c, u, cream, new float[][]{{0.88f, 0.52f}, {0.92f, 0.46f}, {0.95f, 0.55f}, {0.90f, 0.62f}}, w, h);
        // Månesegl: fuld skive skåret af himmelfarven
        float mx = w * 0.50f, my = h * 0.16f;
        c.drawCircle(mx, my, 70 * u, fill(cream));
        c.drawCircle(mx + 30 * u, my - 16 * u, 64 * u, fill(bg));
    }

    private static void drawConstellation(Canvas c, float u, int color, float[][] pts, int w, int h) {
        Paint line = stroke(a(color, 0.45f), 1.6f * u);
        for (int i = 1; i < pts.length; i++) c.drawLine(pts[i - 1][0] * w, pts[i - 1][1] * h, pts[i][0] * w, pts[i][1] * h, line);
        for (float[] p : pts) {
            c.drawCircle(p[0] * w, p[1] * h, 4.5f * u, fill(color));
            c.drawCircle(p[0] * w, p[1] * h, 9 * u, stroke(a(color, 0.5f), 1.2f * u));
        }
    }

    // Jul: nordisk julesweater i masker

    private static final String[] STAR = {
            "X...X...X",
            ".X..X..X.",
            "..X.X.X..",
            "...XXX...",
            "XXXX.XXXX",
            "...XXX...",
            "..X.X.X..",
            ".X..X..X.",
            "X...X...X"};
    private static final String[] TREE = {
            "...X...",
            "..XXX..",
            ".XXXXX.",
            "..XXX..",
            ".XXXXX.",
            "XXXXXXX",
            "...X..."};

    private static void julestrik(Canvas c, int w, int h, float u) {
        int green = hex("#0F2A1E"), red = hex("#B8322B"), cream = hex("#F4ECDD"), dark = hex("#0B2017");
        c.drawColor(green);
        float cell = 16 * u;
        int cols = (int) (w / cell) + 1, rows = (int) (h / cell) + 1;
        int treeTop = rows - 12;
        int starTop = treeTop - 15;
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                int color = (i + j) % 2 == 0 ? green : dark;
                // Stjernebort foroven
                if (j == starTop - 2 || j == starTop + 10) color = ((i + j) % 2 == 0) ? red : green;
                if (j >= starTop && j < starTop + 9) {
                    int col = i % 14;
                    if (col < 9 && STAR[j - starTop].charAt(col) == 'X') color = cream;
                }
                // Rød bort forneden med træer
                if (j >= treeTop - 2) {
                    color = red;
                    if (j == treeTop - 2 || j == rows - 1) color = (i % 2 == 0) ? cream : red;
                    if (j >= treeTop && j < treeTop + 7) {
                        int col = i % 11;
                        if (col < 7 && TREE[j - treeTop].charAt(col) == 'X') color = cream;
                    }
                }
                // Spredte snefnug i det grønne
                if (j > 2 && j < starTop - 3 && flake(i, j)) color = cream;
                if (j == 1) color = ((i + j) % 2 == 0) ? red : green;
                c.drawRect(i * cell + u, j * cell + u, (i + 1) * cell - u, (j + 1) * cell - u, fill(color));
            }
        }
    }

    /** Snefnug som små plusser spredt i et skævt mønster, uden at danne striber. */
    private static boolean flake(int i, int j) {
        int bi = i / 9, bj = j / 7;
        int h = (bi * 73856093) ^ (bj * 19349663);
        if ((h & 3) != 0) return false;
        int cx = bi * 9 + 2 + ((h >>> 4) & 3), cy = bj * 7 + 2 + ((h >>> 8) & 1);
        return (i == cx && Math.abs(j - cy) <= 1) || (j == cy && Math.abs(i - cx) <= 1);
    }

    // Havbrise: seigaiha bølger og sol på lyst papir

    private static void boelger(Canvas c, int w, int h, float u) {
        int paper = hex("#E9F1F0");
        c.drawColor(paper);
        grain(c, w, h, 0.025f, 31);
        c.drawCircle(w * 0.52f, h * 0.20f, 95 * u, fill(hex("#F2B76B")));
        int[][] palettes = {
                {hex("#6FB3BC"), paper, hex("#6FB3BC"), paper, hex("#6FB3BC")},
                {hex("#2E7F8F"), paper, hex("#2E7F8F"), paper, hex("#6FB3BC")},
                {hex("#1F5F73"), paper, hex("#2E7F8F"), paper, hex("#6FB3BC")},
        };
        float R = 72 * u;
        int row = 0;
        for (float y = h * 0.56f; y < h + R; y += R * 0.5f, row++) {
            int[] pal = palettes[Math.min(2, row / 3)];
            float off = (row % 2) * R;
            for (float x = -R + off; x < w + R; x += R * 2) {
                float[] radii = {1f, 0.82f, 0.64f, 0.46f, 0.28f};
                for (int k = 0; k < radii.length; k++) c.drawCircle(x, y, R * radii[k], fill(pal[k]));
            }
        }
    }
}
