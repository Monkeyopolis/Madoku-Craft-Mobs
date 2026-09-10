package madoku.craft.java.mob;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared configuration helpers for the regional-difficulty groups. */
public final class RegionalDifficultyConfigManager {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_DIFFICULTY_SCALING = "regional-difficulty-scaling";
	public static final String FIELD_HEALTH = "health";
	public static final String FIELD_MOVEMENT_SPEED = "movement-speed";
	public static final String FIELD_SWIMMING_SPEED = "swimming-speed";
	public static final String FIELD_FLYING_SPEED = "flying-speed";
	public static final String FIELD_SCALE = "scale";
	public static final String FIELD_ARMOR = "armor";
	public static final String FIELD_DAMAGE = "damage";
	public static final String FIELD_KNOCKBACK_RESISTANCE = "knockback-resistance";
	public static final String FIELD_EXPERIENCE_DROP = "experience-drop";
	public static final String FIELD_RANGED_DAMAGE = "ranged-damage";
	public static final String FIELD_RANGED_ACCURACY = "ranged-accuracy";
	public static final String FIELD_EXPLOSION_POWER = "explosion-power";
	public static final String FIELD_SCALING_TYPE = "type";
	public static final String FIELD_SCALING_VALUE = "value";
	public static final String SCALING_TYPE_ADD = "flat";
	public static final String SCALING_TYPE_PERCENTAGE = "percentage";
	public static final String FIELD_ADJUSTMENT = "adjustment";
	public static final String FIELD_BIOME_LIST = "biome-list";
	public static final String FIELD_STRUCTURE_LIST = "structure-list";
	public static final String FIELD_DAY_LIST = "day-list";
	public static final String FIELD_DAY_COUNT = "day-count";
	public static final String FIELD_MOB_ID = "mob-id";

	public static final double DEFAULT_HEALTH_INCREMENT = 0.20D;
	public static final double DEFAULT_MOVEMENT_SPEED_INCREMENT = 0.02D;
	public static final double DEFAULT_SWIMMING_SPEED_INCREMENT = 0.0D;
	public static final double DEFAULT_FLYING_SPEED_INCREMENT = 0.0D;
	public static final double DEFAULT_SCALE_INCREMENT = 0.0D;
	public static final double DEFAULT_ARMOR_INCREMENT = 0.02D;
	public static final double DEFAULT_DAMAGE_INCREMENT = 0.05D;
	public static final double DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT = 0.02D;
	public static final double DEFAULT_EXPERIENCE_DROP_INCREMENT = 0.10D;
	public static final int DEFAULT_UNKNOWN_ADJUSTMENT = 0;

	private RegionalDifficultyConfigManager() {
	}

	public static JsonObject buildSettingsDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, true)
			.object(FIELD_DIFFICULTY_SCALING, scaling -> scaling
				.put(FIELD_HEALTH, buildScalingValueRule(SCALING_TYPE_PERCENTAGE, DEFAULT_HEALTH_INCREMENT))
				.put(FIELD_MOVEMENT_SPEED, buildScalingValueRule(SCALING_TYPE_PERCENTAGE, DEFAULT_MOVEMENT_SPEED_INCREMENT))
				.put(FIELD_ARMOR, buildScalingValueRule(SCALING_TYPE_PERCENTAGE, DEFAULT_ARMOR_INCREMENT))
				.put(FIELD_KNOCKBACK_RESISTANCE, buildScalingValueRule(SCALING_TYPE_ADD, DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT))
				.put(FIELD_EXPERIENCE_DROP, buildScalingValueRule(SCALING_TYPE_PERCENTAGE, DEFAULT_EXPERIENCE_DROP_INCREMENT)))
			.build();
	}

	public static JsonObject buildMobScalingDefaults(String mobId) {
		return buildMobScalingDefaults(
			mobId,
			DEFAULT_HEALTH_INCREMENT,
			DEFAULT_MOVEMENT_SPEED_INCREMENT,
			DEFAULT_SWIMMING_SPEED_INCREMENT,
			DEFAULT_FLYING_SPEED_INCREMENT,
			null,
			DEFAULT_ARMOR_INCREMENT,
			DEFAULT_DAMAGE_INCREMENT,
			DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
			DEFAULT_EXPERIENCE_DROP_INCREMENT,
			null,
			null,
			null
		);
	}

	public static JsonObject buildDynamicMobScalingDefaults(String fileKey) {
		String normalized = fileKey == null ? "" : fileKey.trim().toLowerCase(Locale.ROOT);
		String key = normalized.contains(":") ? normalized.substring(normalized.indexOf(':') + 1) : normalized;
		return buildMobScalingDefaultsForKey(key);
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
		for (String fileKey : new String[] {
			"bee", "bogged", "cave-spider", "creeper", "drowned", "hag", "husk", "parched",
			"skeleton", "spider", "stray", "wither-skeleton", "zombie", "zombie-villager"
		}) {
			defaults.put(fileKey, buildMobScalingDefaultsForKey(
				fileKey, health, movementSpeed, armor, damage, knockbackResistance, experienceDrop
			));
		}
		return defaults;
	}

	public static List<String> resolveMobScalingFileKeys(EntityType<?> type) {
		if (type == null) return List.of();
		return resolveMobScalingFileKeys(EntityType.getKey(type));
	}

	public static List<String> resolveMobScalingFileKeys(Identifier entityId) {
		if (entityId == null) return List.of();
		String namespace = normalizeFileKey(entityId.getNamespace());
		String path = normalizeFileKey(entityId.getPath());
		if (path.isBlank()) return List.of();
		String hyphenatedPath = path.replace('_', '-');
		Set<String> keys = new LinkedHashSet<>();
		keys.add(hyphenatedPath);
		keys.add(path);
		if (!"minecraft".equals(namespace) && !namespace.isBlank()) {
			keys.add(namespace + "-" + hyphenatedPath);
			keys.add(namespace + "-" + path);
		}
		return List.copyOf(keys);
	}

	private static JsonObject buildMobScalingDefaultsForKey(String fileKey) {
		return buildMobScalingDefaultsForKey(
			fileKey,
			DEFAULT_HEALTH_INCREMENT,
			DEFAULT_MOVEMENT_SPEED_INCREMENT,
			DEFAULT_ARMOR_INCREMENT,
			DEFAULT_DAMAGE_INCREMENT,
			DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
			DEFAULT_EXPERIENCE_DROP_INCREMENT
		);
	}

	private static JsonObject buildMobScalingDefaultsForKey(
		String fileKey,
		double health,
		double movementSpeed,
		double armor,
		double damage,
		double knockbackResistance,
		double experienceDrop
	) {
		String normalized = normalizeFileKey(fileKey);
		String mobId = "hag".equals(normalized)
			? "madoku-craft:hag"
			: "minecraft:" + normalized;
		mobId = JSONAPIManager.normalizeRegistryIdentifierForJson(mobId);
		Double swimmingSpeed = null;
		Double flyingSpeed = null;
		Double scale = null;
		Double rangedDamage = null;
		Double attackAccuracy = null;
		Double explosionPower = null;
		switch (normalized) {
			case "bee" -> flyingSpeed = 0.02D;
			case "creeper" -> explosionPower = 0.05D;
			case "skeleton", "stray", "bogged", "parched" -> {
				rangedDamage = 0.05D;
				attackAccuracy = 0.02D;
			}
			case "wither-skeleton" -> {
				rangedDamage = 0.05D;
				attackAccuracy = 0.02D;
			}
			case "drowned" -> {
				swimmingSpeed = 0.02D;
				rangedDamage = 0.05D;
				attackAccuracy = 0.02D;
			}
			case "spider", "cave-spider" -> scale = 0.10D;
			default -> {
			}
		}
		JsonObject root = buildMobScalingDefaults(
			mobId, health, movementSpeed, swimmingSpeed, flyingSpeed, scale, armor, damage,
			knockbackResistance, experienceDrop, rangedDamage, attackAccuracy, explosionPower
		);
		if (attackAccuracy != null) {
			root.add(FIELD_RANGED_ACCURACY, buildScalingValueRule(SCALING_TYPE_PERCENTAGE, attackAccuracy));
		}
		return root;
	}

	private static String normalizeFileKey(String rawValue) {
		return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
	}

	public static JsonObject buildMobScalingDefaults(
		String mobId,
		Double health,
		Double movementSpeed,
		Double swimmingSpeed,
		Double flyingSpeed,
		Double scale,
		Double armor,
		Double damage,
		Double knockbackResistance,
		Double experienceDrop,
		Double rangedDamage,
		Double attackAccuracy,
		Double explosionPower
	) {
		JSONFormatAPIManager.ObjectBuilder root = JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, true)
			.put(FIELD_MOB_ID, normalizeMobId(mobId));
		addScalingEntry(root, FIELD_HEALTH, health, SCALING_TYPE_PERCENTAGE);
		addScalingEntry(root, FIELD_MOVEMENT_SPEED, movementSpeed, SCALING_TYPE_PERCENTAGE);
		addScalingEntry(root, FIELD_SWIMMING_SPEED, swimmingSpeed, SCALING_TYPE_PERCENTAGE);
		addScalingEntry(root, FIELD_FLYING_SPEED, flyingSpeed, SCALING_TYPE_PERCENTAGE);
		addScalingEntry(root, FIELD_SCALE, scale, SCALING_TYPE_PERCENTAGE);
		addScalingEntry(root, FIELD_ARMOR, armor, SCALING_TYPE_PERCENTAGE);
		addScalingEntry(root, FIELD_DAMAGE, damage, SCALING_TYPE_PERCENTAGE);
		addScalingEntry(root, FIELD_KNOCKBACK_RESISTANCE, knockbackResistance, SCALING_TYPE_ADD);
		addScalingEntry(root, FIELD_EXPERIENCE_DROP, experienceDrop, SCALING_TYPE_PERCENTAGE);
		addScalingEntry(root, FIELD_RANGED_DAMAGE, rangedDamage, SCALING_TYPE_PERCENTAGE);
		addScalingEntry(root, FIELD_RANGED_ACCURACY, attackAccuracy, SCALING_TYPE_PERCENTAGE);
		addScalingEntry(root, FIELD_EXPLOSION_POWER, explosionPower, SCALING_TYPE_PERCENTAGE);
		return root.build();
	}

	public static JsonObject buildScalingValueRule(String type, double value) {
		String normalized = type == null ? SCALING_TYPE_ADD : type.trim().toLowerCase(Locale.ROOT);
		if (!SCALING_TYPE_ADD.equals(normalized) && !SCALING_TYPE_PERCENTAGE.equals(normalized)) {
			normalized = SCALING_TYPE_ADD;
		}
		return JSONFormatAPIManager.object()
			.put(FIELD_SCALING_TYPE, normalized)
			.put(FIELD_SCALING_VALUE, value)
			.build();
	}

	private static void addScalingEntry(JSONFormatAPIManager.ObjectBuilder root, String field, Double value, String type) {
		if (value != null && Double.isFinite(value)) root.put(field, buildScalingValueRule(type, value));
	}

	private static String normalizeMobId(String mobId) {
		String value = mobId == null ? "" : mobId.trim().toLowerCase(Locale.ROOT);
		return JSONAPIManager.normalizeRegistryIdentifierForJson(value);
	}
}

