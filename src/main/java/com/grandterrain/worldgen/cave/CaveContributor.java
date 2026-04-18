package com.grandterrain.worldgen.cave;

/**
 * Strategy for one kind of cave generation (cheese, spaghetti, mega cavern,
 * underground river, etc.). Implementations are stateless w.r.t. world state;
 * they only read pre-built noise generators.
 */
public interface CaveContributor {

    /**
     * Y range where this contributor can carve. Callers use this as a cheap
     * early-out to skip expensive noise evaluations entirely.
     */
    int minY();
    int maxY();

    /**
     * Sample the carving decision at one block position.
     *
     * @return {@link CarveResult#SOLID} if this contributor leaves the block alone,
     *         or one of the CARVE_* values if it claims the block.
     */
    CarveResult sample(double x, double y, double z);
}
