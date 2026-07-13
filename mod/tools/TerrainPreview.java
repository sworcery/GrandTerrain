import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.config.GrandterrainConfig;
import com.grandterrain.worldgen.cave.CarveResult;
import com.grandterrain.worldgen.cave.CaveContributor;
import com.grandterrain.worldgen.noise.ClimateNoise;
import com.grandterrain.worldgen.noise.GrandterrainNoiseRouter;
import com.grandterrain.worldgen.noise.TerrainDensityFunction;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless terrain preview. Runs the mod's actual noise/cave/river code (the
 * worldgen packages are Minecraft-free) and renders maps + cross-sections so
 * terrain can be evaluated without booting a server or client.
 *
 * Compile and run from the repo root:
 *   javac -sourcepath mod/src/main/java -d /tmp/tp mod/tools/TerrainPreview.java
 *   java -cp /tmp/tp TerrainPreview <seed> <centerX> <centerZ> <sizeBlocks> <step> <outDir>
 *
 * Outputs: elevation.png (hypsometric + hillshade + rivers), biomes.png
 * (faithful re-implementation of GrandterrainBiomeSource's parameter table),
 * climate.png, crosssection.png (full-depth slice with caves), and a stats
 * report on stdout.
 */
public final class TerrainPreview {

    // --- biome ids / palette -------------------------------------------------
    static final String[] BIOME_NAMES = {
            "deep_ocean", "coastal_cliffs", "lowland_plains", "temperate_forest",
            "alpine_meadow", "mountain_pine_forest", "rocky_highlands", "snow_peaks",
            "deep_valley", "volcanic_region", "desert", "savanna", "swamp",
            "tundra", "birch_forest", "dark_forest"
    };
    static final int[] BIOME_COLORS = {
            0x0b3d63, 0x8a8d91, 0x7fb35b, 0x2e7d32,
            0xa8d08d, 0x1b5e20, 0x9e9e9e, 0xf5f7fa,
            0x558b2f, 0x6d3f2f, 0xe6c780, 0xc4a94b, 0x4e6b43,
            0xcfe3ea, 0xd9e8b8, 0x1a3d1c
    };

    record Entry(float tLo, float tHi, float hLo, float hHi, float cLo, float cHi, int biome) {}

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20260712L;
        int cx = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        int cz = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        int size = args.length > 3 ? Integer.parseInt(args[3]) : 8192;
        int step = args.length > 4 ? Integer.parseInt(args[4]) : 8;
        File outDir = new File(args.length > 5 ? args[5] : "terrain-eval");
        outDir.mkdirs();

        GrandterrainConfig cfg = new GrandterrainConfig();
        cfg.validate();
        ConfigSnapshot snap = ConfigSnapshot.from(cfg);
        GrandterrainNoiseRouter router = new GrandterrainNoiseRouter(seed, snap);
        TerrainDensityFunction terrain = router.getTerrainDensity();
        ClimateNoise climate = new ClimateNoise(seed);

        int n = size / step;
        int x0 = cx - size / 2, z0 = cz - size / 2;
        int seaLevel = snap.seaLevel();

        // Biome threshold derivation — mirrors GrandterrainBiomeSource exactly.
        int t0 = seaLevel + snap.deepOceanOffset();
        int t1 = Math.max(t0 + 5, seaLevel + snap.coastalOffset());
        int t2 = Math.max(t1 + 5, seaLevel + snap.lowlandOffset());
        int str = snap.snowLineBase() - seaLevel;
        int t3 = Math.max(t2 + 5, seaLevel + Math.max(20, str / 4));
        int t4 = Math.max(t3 + 5, seaLevel + Math.max(60, str / 2));
        int t5 = Math.max(t4 + 5, seaLevel + Math.max(100, (str * 3) / 4));
        int t6 = Math.max(t5 + 5, snap.snowLineBase() - 20);
        float range = t6 - t0;
        float altScale = 2.0f / range;
        float altOffset = -1.0f - t0 * altScale;

        float cT = t3 * altScale + altOffset;
        float cA = t4 * altScale + altOffset;
        float cM = t5 * altScale + altOffset;
        float cR = t6 * altScale + altOffset;

        List<Entry> entries = new ArrayList<>();
        // order matches buildBiomeParameters; ids index BIOME_NAMES
        entries.add(new Entry(-1.5f, 1.5f, -1.5f, 1.5f, cR, 1.5f, 7));          // snow_peaks
        entries.add(new Entry(-1.5f, -0.35f, -1.5f, 1.5f, -1.5f, cR, 13));      // tundra
        entries.add(new Entry(-0.35f, 1.5f, -1.5f, 1.5f, cM, cR, 6));           // rocky_highlands
        entries.add(new Entry(-0.35f, 1.5f, -1.5f, 1.5f, cA, cM, 5));           // mountain_pine_forest
        entries.add(new Entry(-0.35f, 1.5f, -1.5f, 0.3f, cT, cA, 4));           // alpine_meadow
        entries.add(new Entry(-0.35f, 1.5f, 0.3f, 1.5f, cT, cA, 8));            // deep_valley
        entries.add(new Entry(0.25f, 1.5f, -1.5f, -0.4f, -1.5f, cT, 9));        // volcanic
        entries.add(new Entry(0.25f, 1.5f, -0.4f, -0.2f, -1.5f, cT, 10));       // desert
        entries.add(new Entry(0.25f, 1.5f, -0.2f, 0.4f, -1.5f, cT, 11));        // savanna
        entries.add(new Entry(0.25f, 1.5f, 0.4f, 1.5f, -1.5f, cT, 12));         // swamp
        entries.add(new Entry(-0.02f, 0.25f, 0.4f, 1.5f, -1.5f, cT, 15));       // dark_forest
        entries.add(new Entry(-0.02f, 0.25f, 0.0f, 0.4f, -1.5f, cT, 3));        // temperate_forest
        entries.add(new Entry(-0.02f, 0.25f, -1.5f, 0.0f, -1.5f, cT, 2));       // lowland_plains
        entries.add(new Entry(-0.35f, -0.02f, 0.2f, 1.5f, -1.5f, cT, 14));      // birch_forest
        entries.add(new Entry(-0.35f, -0.02f, -1.5f, 0.2f, -1.5f, cT, 2));      // lowland_plains

        double[][] height = new double[n][n];
        int[][] biome = new int[n][n];
        boolean[][] river = new boolean[n][n];
        long[] biomeCount = new long[16];
        long landCount = 0;
        double minH = 1e9, maxH = -1e9;
        long[] histo = new long[1100]; // heights offset by 300

        long tStart = System.nanoTime();
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                double x = x0 + ix * step, z = z0 + iz * step;
                double h = terrain.computeSurfaceHeight(x, z);
                height[iz][ix] = h;
                minH = Math.min(minH, h);
                maxH = Math.max(maxH, h);
                int hb = (int) Math.round(h) + 300;
                if (hb >= 0 && hb < histo.length) histo[hb]++;
                if (h >= seaLevel) landCount++;
                // mirror the generator: rivers fade out with altitude
                river[iz][ix] = h < seaLevel + 220 && router.getSurfaceRivers().isRiver(x, z);

                int y = (int) Math.round(h);
                int b;
                if (y < t0) b = 0;
                else if (y < t1) b = 1;
                else {
                    float alt = y * altScale + altOffset;
                    float temp = (float) climate.temperature(x, z);
                    float hum = (float) climate.humidity(x, z);
                    b = classify(entries, temp, hum, alt);
                }
                biome[iz][ix] = b;
                biomeCount[b]++;
            }
        }
        double sampleMs = (System.nanoTime() - tStart) / 1e6;

        // --- elevation map with hillshade + water + rivers -------------------
        BufferedImage elev = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                double h = height[iz][ix];
                int rgb;
                if (h < seaLevel) {
                    double d = Math.min(1.0, (seaLevel - h) / 150.0);
                    rgb = mix(0x2f6db3, 0x061c38, d);
                } else {
                    rgb = hypso(h, seaLevel, snap.snowLineBase());
                    double hx = height[iz][Math.min(ix + 1, n - 1)] - h;
                    double hz = height[Math.min(iz + 1, n - 1)][ix] - h;
                    double shade = clamp(1.0 + (hx + hz) / (2.5 * step), 0.55, 1.35);
                    rgb = scale(rgb, shade);
                    if (river[iz][ix]) rgb = 0x3f78c8;
                }
                elev.setRGB(ix, iz, rgb);
            }
        }
        ImageIO.write(elev, "png", new File(outDir, "elevation.png"));

        // --- biome map --------------------------------------------------------
        BufferedImage bio = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int iz = 0; iz < n; iz++)
            for (int ix = 0; ix < n; ix++) {
                int rgb = BIOME_COLORS[biome[iz][ix]];
                double h = height[iz][ix];
                double hx = height[iz][Math.min(ix + 1, n - 1)] - h;
                double shade = clamp(1.0 + hx / (3.0 * step), 0.75, 1.2);
                bio.setRGB(ix, iz, scale(rgb, shade));
            }
        ImageIO.write(bio, "png", new File(outDir, "biomes.png"));

        // --- climate map (R=temp, B=humidity) ---------------------------------
        BufferedImage cli = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int iz = 0; iz < n; iz++)
            for (int ix = 0; ix < n; ix++) {
                double x = x0 + ix * step, z = z0 + iz * step;
                int r = (int) clamp((climate.temperature(x, z) + 1) * 127, 0, 255);
                int b = (int) clamp((climate.humidity(x, z) + 1) * 127, 0, 255);
                cli.setRGB(ix, iz, (r << 16) | (60 << 8) | b);
            }
        ImageIO.write(cli, "png", new File(outDir, "climate.png"));

        // --- cross-section along X at center Z -------------------------------
        int csW = Math.min(4096, size), csStep = Math.max(1, csW / 2048);
        int yMin = snap.worldMinY(), yMax = snap.worldMinY() + snap.worldHeight();
        BufferedImage cs = new BufferedImage(csW / csStep, yMax - yMin, BufferedImage.TYPE_INT_RGB);
        List<CaveContributor> caves = router.getCaves();
        for (int i = 0; i < csW / csStep; i++) {
            double x = cx - csW / 2.0 + i * csStep;
            double h = terrain.computeSurfaceHeight(x, cz);
            for (int y = yMin; y < yMax; y++) {
                int py = (yMax - 1) - y; // y up
                int rgb;
                if (y < yMin + 5) rgb = 0x1a1a1a;                       // bedrock
                else if (y < h) {
                    rgb = y < yMin + 64 ? 0x37343c : 0x6f6a63;          // deepslate/stone
                    CarveResult carve = CarveResult.SOLID;
                    for (CaveContributor c : caves) {
                        if (y < c.minY() || y > c.maxY()) continue;
                        CarveResult r = c.sample(x, y, cz);
                        if (r != CarveResult.SOLID) { carve = r; break; }
                    }
                    if (carve == CarveResult.CARVE_AIR) rgb = 0x000000;
                    else if (carve == CarveResult.CARVE_WATER) rgb = 0x1d4e89;
                } else if (y < seaLevel) rgb = 0x2f6db3;                // sea
                else rgb = y > snap.snowLineBase() ? 0xcfd8e6 : 0x9db8d9; // sky
                cs.setRGB(i, py, rgb);
            }
        }
        ImageIO.write(cs, "png", new File(outDir, "crosssection.png"));

        // --- stats ------------------------------------------------------------
        long total = (long) n * n;
        System.out.printf("sampled %dx%d (step %d) in %.0f ms%n", n, n, step, sampleMs);
        System.out.printf("height: min=%.1f max=%.1f  sea=%d snow=%d ceiling=%d%n",
                minH, maxH, seaLevel, snap.snowLineBase(), yMax);
        System.out.printf("land: %.1f%%  (above snowline: %.2f%%)%n",
                100.0 * landCount / total, 100.0 * countAbove(histo, snap.snowLineBase() + 300) / total);
        System.out.println("height percentiles:");
        for (int p : new int[]{1, 5, 25, 50, 75, 95, 99, 100})
            System.out.printf("  p%d=%d%n", p, percentile(histo, total, p) - 300);
        System.out.println("biome share:");
        for (int b = 0; b < 16; b++)
            if (biomeCount[b] > 0)
                System.out.printf("  %-22s %6.2f%%%n", BIOME_NAMES[b], 100.0 * biomeCount[b] / total);
    }

    static int classify(List<Entry> entries, float t, float h, float c) {
        int best = 2; double bestD = Double.MAX_VALUE;
        for (Entry e : entries) {
            double d = sq(dist(t, e.tLo, e.tHi)) + sq(dist(h, e.hLo, e.hHi)) + sq(dist(c, e.cLo, e.cHi));
            if (d < bestD) { bestD = d; best = e.biome; }
        }
        return best;
    }

    static double dist(float v, float lo, float hi) { return v < lo ? lo - v : (v > hi ? v - hi : 0); }
    static double sq(double v) { return v * v; }

    static int hypso(double h, int sea, int snow) {
        double t = clamp((h - sea) / (snow + 80.0 - sea), 0, 1);
        if (t < 0.06) return mix(0xd7c98f, 0x74a85a, t / 0.06);
        if (t < 0.35) return mix(0x74a85a, 0x4d7a3a, (t - 0.06) / 0.29);
        if (t < 0.62) return mix(0x4d7a3a, 0x8a7a5c, (t - 0.35) / 0.27);
        if (t < 0.85) return mix(0x8a7a5c, 0x9aa0a8, (t - 0.62) / 0.23);
        return mix(0x9aa0a8, 0xffffff, (t - 0.85) / 0.15);
    }

    static int mix(int a, int b, double t) {
        t = clamp(t, 0, 1);
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int scale(int rgb, double f) {
        int r = (int) clamp((rgb >> 16 & 255) * f, 0, 255);
        int g = (int) clamp((rgb >> 8 & 255) * f, 0, 255);
        int b = (int) clamp((rgb & 255) * f, 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    static long countAbove(long[] histo, int idx) {
        long s = 0;
        for (int i = Math.max(0, idx); i < histo.length; i++) s += histo[i];
        return s;
    }

    static int percentile(long[] histo, long total, int p) {
        long target = (long) Math.ceil(total * (p / 100.0)), acc = 0;
        for (int i = 0; i < histo.length; i++) {
            acc += histo[i];
            if (acc >= target) return i;
        }
        return histo.length - 1;
    }
}
