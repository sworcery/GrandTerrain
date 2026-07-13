import com.grandterrain.worldgen.noise.ClimateNoise;

import java.util.Arrays;

/**
 * Measures the ClimateNoise temperature/humidity distribution and the share
 * of the map that falls into each biome-table band. The bands were written
 * assuming uniform [-1.5,1.5] noise, but FBm concentrates near 0 — this shows
 * how lopsided the resulting biome shares really are.
 */
public final class ClimateProbe {
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20260712L;
        ClimateNoise cn = new ClimateNoise(seed);

        int N = 1200, step = 24, off = 50000;
        float[] temp = new float[N * N];
        float[] humid = new float[N * N];
        int i = 0;
        for (int x = 0; x < N; x++)
            for (int z = 0; z < N; z++) {
                temp[i] = (float) cn.temperature(off + x * step, off + z * step);
                humid[i] = (float) cn.humidity(off + x * step, off + z * step);
                i++;
            }
        report("temperature", temp);
        report("humidity", humid);

        System.out.println("\ncurrent temperature bands (share of map):");
        band("FROZEN  (-1.5..-0.3)", temp, -1.5f, -0.3f);
        band("COOL    (-0.3..0.15)", temp, -0.3f, 0.15f);
        band("WARM    (0.15..0.5) ", temp, 0.15f, 0.5f);
        band("HOT     (0.5..1.5)  ", temp, 0.5f, 1.5f);
    }

    static void report(String label, float[] v) {
        float[] s = v.clone();
        Arrays.sort(s);
        int n = s.length;
        System.out.printf("%-12s min=%+.3f p5=%+.3f p10=%+.3f p25=%+.3f p50=%+.3f p75=%+.3f p90=%+.3f p95=%+.3f max=%+.3f%n",
                label, s[0], s[n / 20], s[n / 10], s[n / 4], s[n / 2], s[3 * n / 4], s[9 * n / 10], s[19 * n / 20], s[n - 1]);
    }

    static void band(String label, float[] v, float lo, float hi) {
        long c = 0;
        for (float x : v) if (x >= lo && x < hi) c++;
        System.out.printf("  %-22s %6.2f%%%n", label, 100.0 * c / v.length);
    }
}
