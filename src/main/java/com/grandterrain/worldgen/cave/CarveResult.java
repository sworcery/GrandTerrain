package com.grandterrain.worldgen.cave;

/**
 * Outcome of a {@link CaveContributor#sample} call. Drives block placement
 * in the chunk generator's terrain loop.
 */
public enum CarveResult {
    /** Contributor does not claim this block; keep checking or leave solid. */
    SOLID,
    /** Carve to air (or to lava if y is near bedrock). */
    CARVE_AIR,
    /** Carve to water (underground rivers). */
    CARVE_WATER
}
