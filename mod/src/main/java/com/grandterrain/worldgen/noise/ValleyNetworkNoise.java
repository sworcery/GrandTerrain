package com.grandterrain.worldgen.noise;

/**
 * Shared, domain-warped Voronoi edge network used by BOTH erosion valleys and
 * surface rivers. Sharing one network (same seed derivation, same frequency,
 * same warp) means rivers run along the floors of the valleys they carved,
 * instead of two unrelated polygon meshes cross-cutting each other. The warp
 * bends the straight Voronoi edges into meanders.
 *
 * Output convention: {@link #edgeDistance01} is 0 exactly on the edge network
 * and grows toward cell interiors (FNL Distance2Sub measured range [-1,+0.15]
 * with edges at -1; see mod/tools/RangeProbe).
 */
public class ValleyNetworkNoise {

    private static final float WARP_AMPLITUDE = 130.0f;

    private final FastNoiseLite cellular;
    private final FastNoiseLite warpX;
    private final FastNoiseLite warpZ;

    public ValleyNetworkNoise(long seed) {
        cellular = new FastNoiseLite((int) (seed ^ 0x87654321L));
        cellular.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        cellular.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
        cellular.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2Sub);
        cellular.SetFrequency(1.0f / 800.0f);

        warpX = new FastNoiseLite((int) (seed ^ 0xB1BE5EEDL));
        warpX.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warpX.SetFractalType(FastNoiseLite.FractalType.FBm);
        warpX.SetFractalOctaves(2);
        warpX.SetFrequency(1.0f / 350.0f);

        warpZ = new FastNoiseLite((int) (seed ^ 0xB1BE5EEEL));
        warpZ.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warpZ.SetFractalType(FastNoiseLite.FractalType.FBm);
        warpZ.SetFractalOctaves(2);
        warpZ.SetFrequency(1.0f / 350.0f);
    }

    /** 0 on the (warped) valley/river network, growing into cell interiors. */
    public double edgeDistance01(double x, double z) {
        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);
        float wx = fx + warpX.GetNoise(fx, fz) * WARP_AMPLITUDE;
        float wz = fz + warpZ.GetNoise(fx, fz) * WARP_AMPLITUDE;
        return cellular.GetNoise(wx, wz) + 1.0;
    }
}
