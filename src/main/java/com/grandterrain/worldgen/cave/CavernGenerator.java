package com.grandterrain.worldgen.cave;

import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.noise.ContinentalNoise;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Rare mega-caverns using Voronoi cellular noise (~500 block spacing,
 * 30-60 block radius chambers).
 */
public class CavernGenerator implements CaveContributor {

    private final FastNoiseLite cellNoise;
    private final FastNoiseLite floorNoise;
    private final boolean enabled;
    private final int seaLevel;
    private final int bedrockFloor;
    private final int cavernCenterY;

    public CavernGenerator(long seed, ConfigSnapshot config) {
        this.enabled = config.enableMegaCaverns();
        this.seaLevel = config.seaLevel();
        this.bedrockFloor = config.worldMinY() + 30;
        this.cavernCenterY = config.cavernCenterY();

        cellNoise = new FastNoiseLite((int) (seed ^ 0xCA4EC0DEL));
        cellNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        cellNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
        cellNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance);
        cellNoise.SetFrequency(1.0f / 500.0f);

        floorNoise = new FastNoiseLite((int) (seed ^ 0xC4F100E7L));
        floorNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        floorNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        floorNoise.SetFractalOctaves(3);
        floorNoise.SetFrequency(1.0f / 30.0f);
    }

    @Override public int minY() { return bedrockFloor; }
    @Override public int maxY() { return seaLevel - 40; }

    @Override
    public CarveResult sample(double x, double y, double z) {
        if (!enabled) return CarveResult.SOLID;

        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);

        double cellDist = cellNoise.GetNoise(fx, fz);
        double cavernThreshold = 0.08;

        if (cellDist > cavernThreshold) return CarveResult.SOLID;

        double proximity = 1.0 - (cellDist / cavernThreshold);
        double cavernRadius = 25.0 + proximity * 35.0;
        double centerY = this.cavernCenterY + floorNoise.GetNoise(fx * 0.5f, fz * 0.5f) * 40.0;
        double verticalDist = Math.abs(y - centerY);
        double verticalRadius = cavernRadius * 0.6;

        if (verticalDist > verticalRadius) return CarveResult.SOLID;

        double verticalFade = 1.0 - (verticalDist / verticalRadius);
        double floorOffset = floorNoise.GetNoise(fx, (float) y, fz) * 8.0;
        double floorY = centerY - verticalRadius + 5.0 + floorOffset;

        if (y < floorY) return CarveResult.SOLID;

        return (proximity * verticalFade - 0.2) > 0
                ? CarveResult.CARVE_AIR
                : CarveResult.SOLID;
    }
}
