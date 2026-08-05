import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.config.GrandterrainConfig;
import com.grandterrain.worldgen.cave.CarveResult;
import com.grandterrain.worldgen.cave.CaveContributor;
import com.grandterrain.worldgen.noise.GrandterrainNoiseRouter;
import com.grandterrain.worldgen.noise.TerrainDensityFunction;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Flood-fills the carved cave volume to answer: are caves a navigable
 * network, or thousands of disconnected bubbles? Reports the size
 * distribution of connected components and what fraction of cave volume is
 * reachable from the largest one.
 *
 *   java -cp /tmp/gt-tools CaveConnectivity [seed] [size] [x0] [z0]
 */
public final class CaveConnectivity {

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20260712L;
        int size = args.length > 1 ? Integer.parseInt(args[1]) : 256;
        int x0 = args.length > 2 ? Integer.parseInt(args[2]) : 100_000;
        int z0 = args.length > 3 ? Integer.parseInt(args[3]) : 100_000;

        GrandterrainConfig cfg = new GrandterrainConfig();
        cfg.validate();
        ConfigSnapshot snap = ConfigSnapshot.from(cfg);
        GrandterrainNoiseRouter router = new GrandterrainNoiseRouter(seed, snap);
        TerrainDensityFunction terrain = router.getTerrainDensity();
        List<CaveContributor> caves = router.getCaves();

        int yMin = snap.worldMinY() + 6, yMax = snap.seaLevel();
        int ny = yMax - yMin;
        // occupancy: true = open (carved or above surface is excluded)
        boolean[] open = new boolean[size * size * ny];

        long carved = 0;
        for (int ix = 0; ix < size; ix++) {
            for (int iz = 0; iz < size; iz++) {
                double wx = x0 + ix, wz = z0 + iz;
                double surf = terrain.computeSurfaceHeight(wx, wz);
                for (int iy = 0; iy < ny; iy++) {
                    int y = yMin + iy;
                    if (y >= surf) continue;           // only solid rock volume
                    CarveResult res = CarveResult.SOLID;
                    for (CaveContributor c : caves) {
                        if (y < c.minY() || y > c.maxY()) continue;
                        CarveResult r = c.sample(wx, y, wz);
                        if (r != CarveResult.SOLID) { res = r; break; }
                    }
                    if (res != CarveResult.SOLID) {
                        open[idx(ix, iy, iz, size, ny)] = true;
                        carved++;
                    }
                }
            }
        }

        // 6-connected flood fill
        int[] compOf = new int[open.length];
        java.util.ArrayList<Long> sizes = new java.util.ArrayList<>();
        int comp = 0;
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        for (int start = 0; start < open.length; start++) {
            if (!open[start] || compOf[start] != 0) continue;
            comp++;
            long n = 0;
            stack.push(start);
            compOf[start] = comp;
            while (!stack.isEmpty()) {
                int cur = stack.pop();
                n++;
                int cy = cur % ny;
                int cz = (cur / ny) % size;
                int cx = cur / (ny * size);
                for (int d = 0; d < 6; d++) {
                    int nx = cx + (d == 0 ? 1 : d == 1 ? -1 : 0);
                    int nz = cz + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    int nyy = cy + (d == 4 ? 1 : d == 5 ? -1 : 0);
                    if (nx < 0 || nx >= size || nz < 0 || nz >= size || nyy < 0 || nyy >= ny) continue;
                    int ni = idx(nx, nyy, nz, size, ny);
                    if (open[ni] && compOf[ni] == 0) {
                        compOf[ni] = comp;
                        stack.push(ni);
                    }
                }
            }
            sizes.add(n);
        }

        sizes.sort((a, b) -> Long.compare(b, a));
        long total = (long) size * size * ny;
        System.out.printf("region %dx%d, y %d..%d (%,d blocks of rock volume sampled)%n",
                size, size, yMin, yMax, total);
        System.out.printf("carved: %,d (%.2f%% of sampled volume)%n", carved, 100.0 * carved / total);
        System.out.printf("connected components: %,d%n", sizes.size());
        if (!sizes.isEmpty()) {
            System.out.printf("largest component: %,d blocks (%.1f%% of all carved space)%n",
                    sizes.get(0), 100.0 * sizes.get(0) / carved);
            long top10 = 0;
            for (int i = 0; i < Math.min(10, sizes.size()); i++) top10 += sizes.get(i);
            System.out.printf("top 10 components: %.1f%% of carved space%n", 100.0 * top10 / carved);
            long tiny = 0;
            for (long s : sizes) if (s < 27) tiny++;
            System.out.printf("isolated pockets (<27 blocks): %,d (%.1f%% of components)%n",
                    tiny, 100.0 * tiny / sizes.size());
        }
        System.out.println("NOTE: components touching the sample edge may merge in the full world;"
                + " treat the largest-component share as a lower bound.");
    }

    static int idx(int x, int y, int z, int size, int ny) {
        return (x * size + z) * ny + y;
    }
}
