package dk.selveste.launcher;

import android.content.Context;

import java.io.File;

/**
 * Fotoserier til temaerne fra Unsplash (fri licens). Billederne ligger ikke i appen eller repoet:
 * tv'et henter dem selv første gang et tema bruges og gemmer dem i files/tema/<tema>.
 */
final class ThemePhotos {

    private ThemePhotos() {
    }

    private static final String[][] PHOTOS = {
            {"spiderman", "1760954185931-40d5b65fbb86", "1521714161819-15534968fc5f", "1505925456693-124134d66749", "1715783735932-2aaa7bcfab34", "1668381868969-64447587238f"},
            {"batman", "1509347528160-9a9e33742cdb", "1596284274000-7d3eca888e3e", "1612916628677-475f676a6adf", "1774062741881-ef2600546ed2", "1497124401559-3e75ec2ed794"},
            {"superman", "1650568922476-7e5aa8ed62b1", "1538051046377-5ad74dc62f95", "1613834926943-9e4ac2945744", "1594344041337-0223bd103abe", "1641824170390-e6166204329d"},
            {"ironman", "1635863138275-d9b33299680b", "1776483289460-1ca426d583bf", "1657558045738-21507cf53606", "1561905199-e7adfe8aa7d1"},
            {"jarvis", "1638356554495-0793e8c29cf7", "1638356554316-2f7e43f07225", "1638356554489-decb2e3a4c37", "1620403661092-1750c6d4148a"},
            {"biograf", "1489599849927-2ee91cede3ba", "1595769816263-9b910be24d5f", "1485095329183-d0797cdc5676", "1478720568477-152d9b164e26", "1574267432553-4b4628081c31"},
            {"minimal", "1738844153732-a485f0e78382", "1619857121838-997e82345250", "1665779736808-047a6bbf43a0", "1502473379243-ba6449c3b6c4"},
            {"efteraar", "1574376407492-c3cf78231def", "1636140820128-7e9d29ccd6cf", "1763076704364-f241a97901d4", "1632584125454-f48693ec9861", "1601567564212-3bc62d410bfa"},
            {"rose", "1611566905202-6846de61154e", "1728887042214-55ec07872893", "1788178641703-afcbfb172841", "1769867296240-48453e014cc9"},
            {"hygge", "1602523961358-f9f03dd557db", "1543397838-4843c20f7607", "1601479604588-68d9e6d386b5", "1632489376751-18a62c663af8", "1663331864500-5f172bd6c4f1"},
            {"salvie", "1611255550543-b5ecb01dfddc", "1542728928-ee495082a3c6", "1687910819417-6f1b2b213e2d", "1521571086300-579bd981bbb6"},
            {"natte", "1488866022504-f2584929ca5f", "1464802686167-b939a6910659", "1595520519880-a86c48ea536c", "1522124624696-7ea32eb9592c", "1606247357835-d27fe83fc6a3"},
            {"jul", "1607956853617-d9d248a8f327", "1576536423721-d68a48c8ffd8", "1764276680194-0bcbaa3eb8be", "1703155559816-774e802ec421", "1701387961421-378314b96fd9"},
            // Stederne fra Apples baggrunde: Big Sur, Yosemite (El Capitan), Catalina, Mojave og Monterey
            {"apple", "1623169840544-0982db65d00e", "1691088986876-f2b1b30f690f", "1672167109138-f0ac5cde877d", "1509223276291-a9d41410cf25",
                    "1519335337423-a3357c2cd12e", "1468863823688-36ac11ede785", "1509223397999-28fd1c5f11f5", "1745907429402-e5f700a5480d"},
            {"hav", "1518837695005-2083093ee35b", "1559827260-dc66d52bef19", "1560260240-c6ef90a163a4", "1484387436194-cf7cb70800ce", "1785677516870-de1ebe79f4e9"},
    };

    static File dir(Context c, String theme) {
        File d = new File(new File(c.getFilesDir(), "tema"), safe(theme));
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    private static String safe(String theme) {
        for (String[] p : PHOTOS) if (p[0].equals(theme)) return theme;
        return "jarvis";
    }

    /** Henter de billeder, der mangler. Kaldes på en baggrundstråd. Sand hvis noget nyt kom ned. */
    static boolean download(Context c, String theme) {
        String key = safe(theme);
        File d = dir(c, key);
        int width = Math.max(1920, c.getResources().getDisplayMetrics().widthPixels);
        boolean any = false;
        for (String[] p : PHOTOS) {
            if (!p[0].equals(key)) continue;
            for (int i = 1; i < p.length; i++) {
                File f = new File(d, i + "-" + p[i] + ".jpg");
                if (f.isFile() && f.length() > 0) continue;
                File tmp = new File(d, f.getName() + ".del");
                try {
                    Net.download("https://images.unsplash.com/photo-" + p[i] + "?w=" + width + "&q=82&fm=jpg&fit=crop&crop=entropy&ar=16:9", tmp);
                    any |= tmp.renameTo(f);
                } catch (Exception e) {
                    tmp.delete();
                }
            }
        }
        return any;
    }
}
