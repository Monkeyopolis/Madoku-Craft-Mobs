package madoku.craft.mobs.mob;

import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;

import java.util.List;

/** Configuration group for biome difficulty adjustments. */
public final class RegionalDifficultyBiomesManager {
	private static final List<String> DEFAULT_BIOMES = List.of(
		"mushroom-fields", "meadow", "cherry-grove", "forest", "flower-forest", "taiga", "birch-forest",
		"sparse-jungle", "river", "beach", "plains", "sunflower-plains", "savanna", "old-growth-pine-taiga",
		"old-growth-spruce-taiga", "old-growth-birch-forest", "grove", "windswept-forest", "frozen-river",
		"snowy-beach", "snowy-plains", "lush-caves", "ocean", "cold-ocean", "lukewarm-ocean", "warm-ocean",
		"jungle", "swamp", "desert", "snowy-taiga", "stony-peaks", "windswept-hills", "windswept-gravelly-hills",
		"stony-shore", "savanna-plateau", "wooded-badlands", "dripstone-caves", "nether-wastes", "deep-ocean",
		"frozen-ocean", "deep-cold-ocean", "deep-lukewarm-ocean", "bamboo-jungle", "dark-forest", "deep-frozen-ocean",
		"windswept-savanna", "snowy-slopes", "frozen-peaks", "ice-spikes", "badlands", "crimson-forest",
		"warped-forest", "end-midlands", "end-highlands", "small-end-islands", "end-barrens", "jagged-peaks",
		"pale-garden", "eroded-badlands", "deep-dark", "soul-sand-valley", "basalt-deltas", "the-end"
	);

	private RegionalDifficultyBiomesManager() {
	}

	public static JsonObject buildDefaults() {
		JSONFormatManager.ObjectBuilder list = JSONFormatManager.object();
		for (String biome : DEFAULT_BIOMES) {
			list.object(biome, entry -> entry.put(MobConfigManager.FIELD_ADJUSTMENT, 0));
		}
		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_BIOME_LIST, list.build())
			.build();
	}
}

