import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.config.GrandterrainConfig;
import com.grandterrain.worldgen.cave.CarveResult;
import com.grandterrain.worldgen.cave.CaveContributor;
import com.grandterrain.worldgen.noise.GrandterrainNoiseRouter;

import java.util.List;

/**
 * Reports per-contributor carve percentages by Y band, plus the combined
 * pipeline result (first non-SOLID wins, in router order). Identifies which
 * cave system is responsible for how much removed volume.
 *
 *   javac -sourcepath mod/src/main/java -d /tmp/tp mod/tools/CaveProbe.java
 *   java -cp /tmp/tp CaveProbe <seed>
 */
public final class CaveProbe {
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20260712L;
        GrandterrainConfig cfg = new GrandterrainConfig();
        cfg.validate();
        ConfigSnapshot snap = ConfigSnapshot.from(cfg);
        GrandterrainNoiseRouter router = new GrandterrainNoiseRouter(seed, snap);
        List<CaveContributor> caves = router.getCaves();

        System.out.println("contributors (pipeline order):");
        for (CaveContributor c : caves)
            System.out.printf("  %-28s y[%d..%d]%n", c.getClass().getSimpleName(), c.minY(), c.maxY());

        int bandSize = 32;
        int yMin = snap.worldMinY(), yTop = snap.seaLevel();
        int bands = (yTop - yMin) / bandSize + 1;
        long[][] carve = new long[caves.size()][bands];   // per contributor, independent
        long[][] water = new long[caves.size()][bands];
        long[] combined = new long[bands];
        long[] samples = new long[bands];

        // sample away from origin to avoid the wrap seam confounding results
        int x0 = 100_000, z0 = 100_000, extent = 2048, step = 16;
        for (int dx = 0; dx < extent; dx += step) {
            for (int dz = 0; dz < extent; dz += step) {
                double x = x0 + dx, z = z0 + dz;
                for (int y = yMin + 6; y < yTop; y += 4) {
                    int band = (y - yMin) / bandSize;
                    samples[band]++;
                    boolean claimed = false;
                    for (int ci = 0; ci < caves.size(); ci++) {
                        CaveContributor c = caves.get(ci);
                        if (y < c.minY() || y > c.maxY()) continue;
                        CarveResult r = c.sample(x, y, z);
                        if (r != CarveResult.SOLID) {
                            carve[ci][band]++;
                            if (r == CarveResult.CARVE_WATER) water[ci][band]++;
                            if (!claimed) { combined[band]++; claimed = true; }
                        }
                    }
                }
            }
        }

        System.out.printf("%n%-12s %-9s", "y band", "combined");
        for (CaveContributor c : caves) {
            String n = c.getClass().getSimpleName().replace("Function", "").replace("Generator", "").replace("Carver", "");
            System.out.printf(" %-14s", n);
        }
        System.out.println();
        for (int b = bands - 1; b >= 0; b--) {
            if (samples[b] == 0) continue;
            System.out.printf("%5d..%-5d %7.2f%%", yMin + b * bandSize, yMin + (b + 1) * bandSize, 100.0 * combined[b] / samples[b]);
            for (int ci = 0; ci < caves.size(); ci++)
                System.out.printf(" %6.2f%%%7s", 100.0 * carve[ci][b] / samples[b],
                        water[ci][b] > 0 ? String.format("(w%.1f%%)", 100.0 * water[ci][b] / samples[b]) : "");
            System.out.println();
        }
    }
}
