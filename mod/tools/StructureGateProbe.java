import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.config.GrandterrainConfig;
import com.grandterrain.worldgen.noise.GrandterrainNoiseRouter;
import com.grandterrain.worldgen.noise.TerrainDensityFunction;

/**
 * Replays each structure's findGenerationPoint height/flatness gates over a
 * large grid of candidate chunk centres and reports the acceptance rate and
 * the resulting expected density. Lets gate changes be evaluated instantly
 * instead of waiting for a multi-km pre-gen (castles are ~1 per 3.3km, so a
 * generated world can't confirm them without ~30km of terrain).
 *
 * Densities assume the structure_set spacings: castle 80, ruin 40,
 * watchtower 50 (chunks). Biome-tag rejection is NOT modelled here — the
 * numbers are gate-only upper bounds, comparable across gate revisions.
 *
 *   java -cp /tmp/gt-tools StructureGateProbe [seed] [samples]
 */
public final class StructureGateProbe {

    static TerrainDensityFunction terrain;
    static final int INTERP_H = 4;

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20260712L;
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 400;

        GrandterrainConfig cfg = new GrandterrainConfig();
        cfg.validate();
        ConfigSnapshot snap = ConfigSnapshot.from(cfg);
        terrain = new GrandterrainNoiseRouter(seed, snap).getTerrainDensity();

        long castle = 0, ruin = 0, watch = 0, total = 0;
        // sample candidate chunk centres on a coarse grid across a wide area
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int x = (i - n / 2) * 137 + 8;
                int z = (j - n / 2) * 149 + 8;
                total++;
                int y = h(x, z);
                if (castleGate(x, z, y)) castle++;
                if (ruinGate(x, z, y)) ruin++;
                if (watchGate(x, z, y)) watch++;
            }
        }

        System.out.printf("samples: %,d%n%n", total);
        report("castle", castle, total, 80);
        report("ruin", ruin, total, 40);
        report("watchtower", watch, total, 50);
        System.out.println("\n(gate-only; biome tags reject a further ~20-40%)");
    }

    static void report(String name, long pass, long total, int spacingChunks) {
        double rate = (double) pass / total;
        // candidate chunks per km^2 = 1e6 / (16*spacing)^2 * 1e6? -> compute directly
        double candidatesPerKm2 = 1e6 / Math.pow(16.0 * spacingChunks, 2);
        System.out.printf("%-12s accept=%.4f  -> %.2f per km^2  (1 per ~%.0f blocks)%n",
                name, rate, rate * candidatesPerKm2,
                Math.sqrt(1e6 / Math.max(1e-9, rate * candidatesPerKm2)));
    }

    /** Mirrors GrandterrainChunkGenerator.getBaseHeight (bilinear over the 4-block grid). */
    static int h(int x, int z) {
        int gx = Math.floorDiv(x, INTERP_H) * INTERP_H;
        int gz = Math.floorDiv(z, INTERP_H) * INTERP_H;
        double h00 = terrain.computeSurfaceHeight(gx, gz);
        double h10 = terrain.computeSurfaceHeight(gx + INTERP_H, gz);
        double h01 = terrain.computeSurfaceHeight(gx, gz + INTERP_H);
        double h11 = terrain.computeSurfaceHeight(gx + INTERP_H, gz + INTERP_H);
        double fx = (x - gx) / (double) INTERP_H;
        double fz = (z - gz) / (double) INTERP_H;
        double a = h00 + (h10 - h00) * fx;
        double b = h01 + (h11 - h01) * fx;
        return (int) Math.ceil(a + (b - a) * fz);
    }

    static boolean castleGate(int x, int z, int y) {
        if (y < 135 || y > 350) return false;
        final int w = 18;
        int[][] ring = {{0, -w}, {0, w}, {w, 0}, {-w, 0},
                        {-w, -w}, {-w, w}, {w, -w}, {w, w}};
        for (int[] o : ring) {
            if (Math.abs(y - h(x + o[0], z + o[1])) > 10) return false;
        }
        return true;
    }

    static boolean ruinGate(int x, int z, int y) {
        if (y < 135 || y > 500) return false;
        int d = Math.max(Math.max(Math.abs(y - h(x, z - 8)), Math.abs(y - h(x, z + 8))),
                Math.max(Math.abs(y - h(x + 8, z)), Math.abs(y - h(x - 8, z))));
        return d <= 4;
    }

    static boolean watchGate(int x, int z, int y) {
        if (y < 135 || y > 500) return false;
        int d = Math.max(Math.max(Math.abs(y - h(x, z - 5)), Math.abs(y - h(x, z + 5))),
                Math.max(Math.abs(y - h(x + 5, z)), Math.abs(y - h(x - 5, z))));
        return d <= 6;
    }
}
