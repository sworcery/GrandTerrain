package com.grandterrain.worldgen.noise;

/**
 * Avalanche integer hashes for worldgen dithering.
 *
 * Linear hashes of the form (x*A + z*B) & mask reduce, modulo a power of
 * two, to straight diagonal stripe patterns — measured as exact repeats every
 * few blocks that tile the world and read as printed lines (glaring in Voxy
 * LOD color maps). These mix bits properly. Consumers that need several
 * independent dithers at one position should take DISJOINT bit ranges of a
 * single hash rather than calling with different multipliers, which merely
 * shifts the same field.
 */
public final class WorldgenHash {
    private WorldgenHash() {}

    public static int mix2(int x, int z) {
        int h = x * 0x85EBCA6B + z * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x27D4EB2F;
        h ^= h >>> 13;
        return h;
    }

    public static int mix3(int x, int y, int z) {
        int h = x * 0x85EBCA6B + y * 0x9E3779B9 + z * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x27D4EB2F;
        h ^= h >>> 13;
        return h;
    }
}
