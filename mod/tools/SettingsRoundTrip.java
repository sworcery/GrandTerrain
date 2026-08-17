import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.config.GrandterrainConfig;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

/**
 * Proves the world-settings codec round-trips and — the actual bug it fixes —
 * that a world's persisted settings survive being loaded under a DIFFERENT
 * local config. Exits non-zero on any mismatch so it can gate CI.
 *
 *   javac -cp <dfu>:<gson> -sourcepath mod/src/main/java -d /tmp/gt-tools mod/tools/SettingsRoundTrip.java
 *   java  -cp <dfu>:<gson>:/tmp/gt-tools SettingsRoundTrip
 */
public final class SettingsRoundTrip {
    public static void main(String[] args) {
        // "Alpine" preset — the kind of non-default world the worker ships.
        GrandterrainConfig alpine = new GrandterrainConfig();
        alpine.mountainHeightScale = 8.0f;
        alpine.continentalScale = 1.5f;
        alpine.erosionStrength = 2.0f;
        alpine.seaLevel = 100;
        alpine.snowLineBase = 300;
        alpine.cavernCenterY = -150;
        alpine.enableWatchtowers = false;
        alpine.validate();
        ConfigSnapshot world = ConfigSnapshot.from(alpine);

        // Encode as the world would persist it.
        JsonElement json = ConfigSnapshot.CODEC.encodeStart(JsonOps.INSTANCE, world)
                .getOrThrow(msg -> new IllegalStateException("encode failed: " + msg));

        // Decode — this is what a client with DEFAULT local config does at load.
        ConfigSnapshot loaded = ConfigSnapshot.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(msg -> new IllegalStateException("decode failed: " + msg));

        int failures = 0;
        if (!loaded.equals(world)) {
            System.err.println("MISMATCH after round-trip:\n  in : " + world + "\n  out: " + loaded);
            failures++;
        }
        ConfigSnapshot local = ConfigSnapshot.from(new GrandterrainConfig());
        if (loaded.seaLevel() == local.seaLevel() || loaded.mountainHeightScale() == local.mountainHeightScale()) {
            System.err.println("Persisted settings collapsed to local defaults — the pregen-boundary seam bug");
            failures++;
        }
        // Every field must be represented (a missing key would silently drop a setting).
        String s = json.toString();
        for (String key : new String[]{"sea_level", "mountain_height_scale", "snow_line_base",
                "cavern_center_y", "enable_watchtowers", "world_height", "river_depth"}) {
            if (!s.contains("\"" + key + "\"")) { System.err.println("missing key: " + key); failures++; }
        }
        System.out.println("encoded: " + s);
        System.out.println(failures == 0
                ? "OK: settings round-trip exactly; a world keeps its own settings under a different local config"
                : failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
