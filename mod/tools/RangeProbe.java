import com.grandterrain.worldgen.noise.FastNoiseLite;

import java.util.Arrays;

/**
 * Empirically measures the output range/distribution of every FastNoiseLite
 * configuration the mod's Voronoi consumers rely on. The cave/river/erosion
 * thresholds were written against assumed ranges; this prints the real ones.
 */
public final class RangeProbe {
    public static void main(String[] args) {
        probe("Cellular Distance  f=1/500 (CavernGenerator)", cellular(1f / 500f, FastNoiseLite.CellularReturnType.Distance));
        probe("Cellular Dist2Sub  f=1/300 (UndergroundRiver)", cellular(1f / 300f, FastNoiseLite.CellularReturnType.Distance2Sub));
        probe("Cellular Dist2Sub  f=1/800 (SurfaceRivers)", cellular(1f / 800f, FastNoiseLite.CellularReturnType.Distance2Sub));
        probe("Cellular Dist2Sub  f=1/900 (ErosionValley?)", cellular(1f / 900f, FastNoiseLite.CellularReturnType.Distance2Sub));
        probe("OS2S FBm3          f=1/200 (CheeseCave)", fbm(3, 1f / 200f));
        probe("OS2S FBm4          f=1/100 (Spaghetti)", fbm(4, 1f / 100f));
    }

    static FastNoiseLite cellular(float freq, FastNoiseLite.CellularReturnType rt) {
        FastNoiseLite n = new FastNoiseLite(12345);
        n.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        n.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
        n.SetCellularReturnType(rt);
        n.SetFrequency(freq);
        return n;
    }

    static FastNoiseLite fbm(int oct, float freq) {
        FastNoiseLite n = new FastNoiseLite(12345);
        n.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        n.SetFractalType(FastNoiseLite.FractalType.FBm);
        n.SetFractalOctaves(oct);
        n.SetFractalLacunarity(2.0f);
        n.SetFractalGain(0.5f);
        n.SetFrequency(freq);
        return n;
    }

    static void probe(String label, FastNoiseLite n) {
        int N = 1500;
        float[] vals = new float[N * N];
        int i = 0;
        for (int x = 0; x < N; x++)
            for (int z = 0; z < N; z++)
                vals[i++] = n.GetNoise(x * 3.1f + 100000, z * 3.7f + 100000);
        Arrays.sort(vals);
        System.out.printf("%-46s min=%+.3f p1=%+.3f p25=%+.3f p50=%+.3f p75=%+.3f p99=%+.3f max=%+.3f%n",
                label, vals[0], vals[N * N / 100], vals[N * N / 4], vals[N * N / 2],
                vals[3 * N * N / 4], vals[99 * N * N / 100], vals[N * N - 1]);
        // fractions relevant to current thresholds
        System.out.printf("%-46s frac(v<=0.08)=%.3f  frac(|v|<0.12)=%.3f  frac(|v|<0.08)=%.3f  frac(v>0.45)=%.4f%n%n",
                "", frac(vals, v -> v <= 0.08f), frac(vals, v -> Math.abs(v) < 0.12f),
                frac(vals, v -> Math.abs(v) < 0.08f), frac(vals, v -> v > 0.45f));
    }

    interface P { boolean t(float v); }

    static double frac(float[] vals, P p) {
        long c = 0;
        for (float v : vals) if (p.t(v)) c++;
        return (double) c / vals.length;
    }
}
