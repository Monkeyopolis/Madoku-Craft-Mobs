package madoku.craft.mobs.difficulty.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MadokuDifficultyConfig {
	public static final int DEFAULT_UNKNOWN_ADJUSTMENT = 2;
	public static final int TIME_UNBOUNDED_MAX_DAY = -1;

	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_BIOMES_ENABLED = "biomes_enabled";
	public static final String FIELD_STRUCTURES_ENABLED = "structures_enabled";
	public static final String FIELD_TIME_ENABLED = "time_enabled";
	public static final String FIELD_DEFAULT_UNKNOWN_ADJUSTMENT = "default_unknown_adjustment";

	public static final String FIELD_DIFFICULTY_SCALING = "difficulty_scaling";
	public static final String FIELD_HEALTH = "health";
	public static final String FIELD_MOVEMENT_SPEED = "movement_speed";
	public static final String FIELD_SCALE = "scale";
	public static final String FIELD_ARMOR = "armor";
	public static final String FIELD_DAMAGE = "damage";
	public static final String FIELD_KNOCKBACK_RESISTANCE = "knockback_resistance";
	public static final String FIELD_EXPERIENCE_DROP = "experience_drop";
	public static final String FIELD_RANGED_DAMAGE = "ranged_damage";
	public static final String FIELD_ATTACK_ACCURACY = "attack_accuracy";
	public static final String FIELD_EXPLOSION_POWER = "explosion_power";

	public static final String FIELD_ADJUSTMENT = "adjustment";
	public static final String FIELD_BIOME_LIST = "biome_list";
	public static final String FIELD_STRUCTURE_LIST = "structure_list";
	public static final String FIELD_MIN_DAY = "min_day";
	public static final String FIELD_MAX_DAY = "max_day";
	public static final String FIELD_MOB_ID = "mob_id";

	public static final double DEFAULT_HEALTH_INCREMENT = 4.0d;
	public static final double DEFAULT_MOVEMENT_SPEED_INCREMENT = 1.0d;
	public static final double DEFAULT_SCALE_INCREMENT = 0.0d;
	public static final double DEFAULT_ARMOR_INCREMENT = 1.0d;
	public static final double DEFAULT_DAMAGE_INCREMENT = 1.0d;
	public static final double DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT = 1.0d;
	public static final double DEFAULT_EXPERIENCE_DROP_INCREMENT = 1.0d;
	public static final double DEFAULT_EXPLOSION_POWER_INCREMENT = 1.0d;

	private MadokuDifficultyConfig() {
	}

	public static JsonObject buildSettingsDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_BIOMES_ENABLED, true);
		root.addProperty(FIELD_STRUCTURES_ENABLED, true);
		root.addProperty(FIELD_TIME_ENABLED, true);
		root.addProperty(FIELD_DEFAULT_UNKNOWN_ADJUSTMENT, DEFAULT_UNKNOWN_ADJUSTMENT);

			JsonObject scaling = new JsonObject();
			scaling.addProperty(FIELD_HEALTH, DEFAULT_HEALTH_INCREMENT);
			scaling.addProperty(FIELD_MOVEMENT_SPEED, DEFAULT_MOVEMENT_SPEED_INCREMENT);
			scaling.addProperty(FIELD_SCALE, DEFAULT_SCALE_INCREMENT);
			scaling.addProperty(FIELD_ARMOR, DEFAULT_ARMOR_INCREMENT);
			scaling.addProperty(FIELD_KNOCKBACK_RESISTANCE, DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT);
			scaling.addProperty(FIELD_EXPERIENCE_DROP, DEFAULT_EXPERIENCE_DROP_INCREMENT);
			root.add(FIELD_DIFFICULTY_SCALING, scaling);
			return root;
		}

	public static Map<String, JsonObject> buildDefaultBiomeFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("difficulty-one", buildBiomeRuleDefaults(0, List.of(
			"mushroom_fields", "meadow", "cherry_grove", "forest", "flower_forest",
			"taiga", "birch_forest", "sparse_jungle", "river", "beach", "plains",
			"sunflower_plains", "savanna", "old_growth_pine_taiga", "old_growth_spruce_taiga",
			"old_growth_birch_forest"
		)));
		defaults.put("difficulty-two", buildBiomeRuleDefaults(1, List.of(
			"grove", "windswept_forest", "frozen_river", "snowy_beach", "snowy_plains",
			"lush_caves", "ocean", "cold_ocean", "lukewarm_ocean", "warm_ocean",
			"jungle", "swamp", "desert", "snowy_taiga"
		)));
		defaults.put("difficulty-three", buildBiomeRuleDefaults(2, List.of(
			"stony_peaks", "windswept_hills", "windswept_gravelly_hills", "stony_shore",
			"savanna_plateau", "wooded_badlands", "dripstone_caves", "nether_wastes",
			"deep_ocean", "frozen_ocean", "deep_cold_ocean", "deep_lukewarm_ocean",
			"bamboo_jungle", "dark_forest", "deep_frozen_ocean", "windswept_savanna",
			"snowy_slopes"
		)));
		defaults.put("difficulty-four", buildBiomeRuleDefaults(3, List.of(
			"frozen_peaks", "ice_spikes", "badlands", "crimson_forest", "warped_forest",
			"end_midlands", "end_highlands", "small_end_islands", "end_barrens",
			"jagged_peaks", "pale_garden", "eroded_badlands", "deep_dark",
			"soul_sand_valley", "basalt_deltas", "the_end"
		)));
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultStructureFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("difficulty-one", buildStructureRuleDefaults(0, List.of(
			"igloo", "swamp_hut", "buried_treasure", "nether_fossil", "ocean_ruin_cold", "ocean_ruin_warm",
			"village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga"
		)));
		defaults.put("difficulty-two", buildStructureRuleDefaults(1, List.of(
			"trial_ruins", "shipwreck", "shipwreck_beached", "ruined_portal", "ruined_portal_desert",
			"ruined_portal_jungle", "ruined_portal_mountain", "ruined_portal_nether",
			"ruined_portal_ocean", "ruined_portal_swamp"
		)));
		defaults.put("difficulty-three", buildStructureRuleDefaults(2, List.of(
			"jungle_pyramid", "desert_pyramid", "pillager_outpost", "mineshaft", "mineshaft_mesa"
		)));
		defaults.put("difficulty-four", buildStructureRuleDefaults(3, List.of(
			"fortress", "mansion", "monument", "stronghold", "trial_chambers", "end_city",
			"ancient_city", "bastion_remnant"
		)));
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultTimeFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("difficulty-one", buildTimeRuleDefaults(0, 28, 0));
		defaults.put("difficulty-two", buildTimeRuleDefaults(29, 168, 1));
		defaults.put("difficulty-three", buildTimeRuleDefaults(169, 336, 2));
		defaults.put("difficulty-four", buildTimeRuleDefaults(337, TIME_UNBOUNDED_MAX_DAY, 3));
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultMobScalingFileDefaults() {
		return buildDefaultMobScalingFileDefaults(
			DEFAULT_HEALTH_INCREMENT,
			DEFAULT_MOVEMENT_SPEED_INCREMENT,
			DEFAULT_ARMOR_INCREMENT,
			DEFAULT_DAMAGE_INCREMENT,
			DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
			DEFAULT_EXPERIENCE_DROP_INCREMENT
		);
	}

	public static Map<String, JsonObject> buildDefaultMobScalingFileDefaults(
		double health,
		double movementSpeed,
		double armor,
		double damage,
		double knockbackResistance,
		double experienceDrop
	) {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("creeper", buildMobScalingDefaults("minecraft:creeper", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null, damage));
		defaults.put("skeleton", buildMobScalingDefaults("minecraft:skeleton", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null));
		defaults.put("stray", buildMobScalingDefaults("minecraft:stray", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null));
		defaults.put("bogged", buildMobScalingDefaults("minecraft:bogged", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null));
		defaults.put("parched", buildMobScalingDefaults("minecraft:parched", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null));
		defaults.put("wither-skeleton", buildMobScalingDefaults("minecraft:wither_skeleton", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null));
		defaults.put("spider", buildMobScalingDefaults("minecraft:spider", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, 1.0d));
		defaults.put("cave-spider", buildMobScalingDefaults("minecraft:cave_spider", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, 1.0d));
		defaults.put("zombie", buildMobScalingDefaults("minecraft:zombie", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null));
		defaults.put("husk", buildMobScalingDefaults("minecraft:husk", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null));
		defaults.put("drowned", buildMobScalingDefaults("minecraft:drowned", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null));
		defaults.put("zombie-villager", buildMobScalingDefaults("minecraft:zombie_villager", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null));
		defaults.put("pillager", buildMobScalingDefaults("minecraft:pillager", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null));
		defaults.put("piglin", buildMobScalingDefaults("minecraft:piglin", health, movementSpeed, armor, damage, knockbackResistance, experienceDrop, null));
		addRangedScalingDefaults(defaults.get("skeleton"));
		addRangedScalingDefaults(defaults.get("stray"));
		addRangedScalingDefaults(defaults.get("bogged"));
		addRangedScalingDefaults(defaults.get("parched"));
		addRangedScalingDefaults(defaults.get("wither-skeleton"));
		addRangedScalingDefaults(defaults.get("pillager"));
		addRangedScalingDefaults(defaults.get("piglin"));
		return defaults;
	}

	public static JsonObject buildDynamicMobScalingDefaults(String fileKey) {
		String mobId = resolveMobIdFromFileKey(fileKey);
		if ("minecraft:creeper".equals(mobId)) {
				return buildMobScalingDefaults(
					mobId,
					DEFAULT_HEALTH_INCREMENT,
					DEFAULT_MOVEMENT_SPEED_INCREMENT,
						DEFAULT_ARMOR_INCREMENT,
					DEFAULT_DAMAGE_INCREMENT,
					DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
					DEFAULT_EXPERIENCE_DROP_INCREMENT,
					null,
						DEFAULT_EXPLOSION_POWER_INCREMENT
					);
				}
				if ("minecraft:skeleton".equals(mobId)
					|| "minecraft:stray".equals(mobId)
					|| "minecraft:bogged".equals(mobId)
					|| "minecraft:parched".equals(mobId)
					|| "minecraft:wither_skeleton".equals(mobId)
					|| "minecraft:pillager".equals(mobId)
					|| "minecraft:piglin".equals(mobId)) {
					JsonObject defaults = buildMobScalingDefaults(mobId);
					addRangedScalingDefaults(defaults);
					return defaults;
				}
				if ("minecraft:spider".equals(mobId) || "minecraft:cave_spider".equals(mobId)) {
					return buildMobScalingDefaults(
						mobId,
					DEFAULT_HEALTH_INCREMENT,
					DEFAULT_MOVEMENT_SPEED_INCREMENT,
					DEFAULT_ARMOR_INCREMENT,
					DEFAULT_DAMAGE_INCREMENT,
					DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
					DEFAULT_EXPERIENCE_DROP_INCREMENT,
					1.0d,
					null
				);
			}
		return buildMobScalingDefaults(mobId);
	}

	public static JsonObject buildBiomeRuleDefaults(int adjustment, List<String> biomeList) {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ADJUSTMENT, Math.max(0, adjustment));
		root.add(FIELD_BIOME_LIST, toStringArray(biomeList));
		return root;
	}

	public static JsonObject buildStructureRuleDefaults(int adjustment, List<String> structureList) {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ADJUSTMENT, Math.max(0, adjustment));
		root.add(FIELD_STRUCTURE_LIST, toStringArray(structureList));
		return root;
	}

	public static JsonObject buildTimeRuleDefaults(int minDay, int maxDay, int adjustment) {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_MIN_DAY, Math.max(0, minDay));
		root.addProperty(FIELD_MAX_DAY, maxDay < 0 ? TIME_UNBOUNDED_MAX_DAY : Math.max(minDay, maxDay));
		root.addProperty(FIELD_ADJUSTMENT, Math.max(0, adjustment));
		return root;
	}

	public static JsonObject buildMobScalingDefaults(String mobId) {
		return buildMobScalingDefaults(
			mobId,
			DEFAULT_HEALTH_INCREMENT,
			DEFAULT_MOVEMENT_SPEED_INCREMENT,
			DEFAULT_ARMOR_INCREMENT,
			DEFAULT_DAMAGE_INCREMENT,
			DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
			DEFAULT_EXPERIENCE_DROP_INCREMENT,
			null,
			null
		);
	}

	public static JsonObject buildMobScalingDefaults(
		String mobId,
		double health,
		double movementSpeed,
		double armor,
		double damage,
		double knockbackResistance,
		double experienceDrop,
		Double scale
	) {
		return buildMobScalingDefaults(
			mobId,
			health,
			movementSpeed,
			armor,
			damage,
			knockbackResistance,
			experienceDrop,
			scale,
			null
		);
	}

	public static JsonObject buildMobScalingDefaults(
		String mobId,
		double health,
		double movementSpeed,
		double armor,
		double damage,
		double knockbackResistance,
		double experienceDrop,
		Double scale,
		Double explosionPower
	) {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_MOB_ID, normalizeMobId(mobId));
		root.addProperty(FIELD_HEALTH, health);
		root.addProperty(FIELD_MOVEMENT_SPEED, movementSpeed);
		root.addProperty(FIELD_ARMOR, armor);
		root.addProperty(FIELD_DAMAGE, damage);
		root.addProperty(FIELD_KNOCKBACK_RESISTANCE, knockbackResistance);
		root.addProperty(FIELD_EXPERIENCE_DROP, experienceDrop);
		if (scale != null) {
			root.addProperty(FIELD_SCALE, scale);
		}
		if (explosionPower != null) {
			root.addProperty(FIELD_EXPLOSION_POWER, explosionPower);
		}
		return root;
	}

	public static List<TimeTierDefinition> defaultTimeTiers() {
		return List.of(
			new TimeTierDefinition(0, 28, 0),
			new TimeTierDefinition(29, 168, 1),
			new TimeTierDefinition(169, 336, 2),
			new TimeTierDefinition(337, TIME_UNBOUNDED_MAX_DAY, 3)
		);
	}

	private static JsonArray toStringArray(List<String> values) {
		JsonArray array = new JsonArray();
		if (values == null) {
			return array;
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				array.add(value);
			}
		}
		return array;
	}

	private static String resolveMobIdFromFileKey(String fileKey) {
		String normalized = fileKey == null ? "" : fileKey.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return "minecraft:unknown";
		}
		if (normalized.contains(":")) {
			return normalizeMobId(normalized);
		}
		return "minecraft:" + normalized.replace('-', '_');
	}

	private static String normalizeMobId(String rawMobId) {
		String normalized = rawMobId == null ? "" : rawMobId.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return "minecraft:unknown";
		}
		if (!normalized.contains(":")) {
			normalized = "minecraft:" + normalized.replace('-', '_');
		}
		return normalized;
	}

		public record TimeTierDefinition(int minDay, int maxDay, int adjustment) {
		}

		private static void addRangedScalingDefaults(JsonObject root) {
			if (root == null) {
				return;
			}
			root.addProperty(FIELD_RANGED_DAMAGE, 1.0d);
			root.addProperty(FIELD_ATTACK_ACCURACY, 1.0d);
		}
	}

