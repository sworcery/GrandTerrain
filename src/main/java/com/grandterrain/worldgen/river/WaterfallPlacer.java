package com.grandterrain.worldgen.river;

import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Detects steep terrain transitions along river paths and marks them as waterfall locations.
 * Used during feature generation to place flowing water blocks at cliff edges.
 */
public class WaterfallPlacer {

    private final RiverDensityFunction riverFunction;

    public WaterfallPlacer(RiverDensityFunction riverFunction) {
        this.riverFunction = riverFunction;
    }

    /**
     * Check if this XZ position should have a waterfall.
     * A waterfall occurs where a river crosses steep terrain (>10 block drop over 16 blocks).
     *
     * @param terrainHeightHere terrain height at this position
     * @param terrainHeightNorth terrain height 16 blocks north
     * @param terrainHeightSouth terrain height 16 blocks south
     * @param terrainHeightEast terrain height 16 blocks east
     * @param terrainHeightWest terrain height 16 blocks west
     * @return true if a waterfall should be placed here
     */
    public boolean isWaterfall(double x, double z,
                                double terrainHeightHere,
                                double terrainHeightNorth,
                                double terrainHeightSouth,
                                double terrainHeightEast,
                                double terrainHeightWest) {
        if (!riverFunction.isRiver(x, z)) return false;

        double maxDrop = 0;
        maxDrop = Math.max(maxDrop, terrainHeightHere - terrainHeightNorth);
        maxDrop = Math.max(maxDrop, terrainHeightHere - terrainHeightSouth);
        maxDrop = Math.max(maxDrop, terrainHeightHere - terrainHeightEast);
        maxDrop = Math.max(maxDrop, terrainHeightHere - terrainHeightWest);

        return maxDrop > 10.0;
    }
}
