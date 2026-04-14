package madoku.craft.mobs.mob.system;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MadokuMobConfig {
	public static final String FIELD_MOB_SYSTEM_ENABLED = "mobSystemEnabled";
	public static final String FIELD_ENABLED = "enabled";

	public static final String FIELD_HEALTH = "health";
	public static final String FIELD_ARMOR = "armor";
	public static final String FIELD_DAMAGE = "damage";
	public static final String FIELD_MOVEMENT_SPEED = "movement_speed";
	public static final String FIELD_KNOCKBACK_RESISTANCE = "knockback_resistance";
	public static final String FIELD_SCALE = "scale";
	public static final String FIELD_EXPERIENCE_DROP = "experience_drop";

	public static final String FILE_CREEPER = "creeper";
	public static final String FILE_SKELETON = "skeleton";
	public static final String FILE_STRAY = "stray";
	public static final String FILE_BOGGED = "bogged";
	public static final String FILE_PARCHED = "parched";
	public static final String FILE_SPIDER = "spider";
	public static final String FILE_CAVE_SPIDER = "cave-spider";
	public static final String FILE_ZOMBIE = "zombie";
	public static final String FILE_HUSK = "husk";
	public static final String FILE_DROWNED = "drowned";
	public static final String FILE_ZOMBIE_VILLAGER = "zombie-villager";
	public static final String FILE_PIGLIN = "piglin";
	public static final String FILE_PILLAGER = "pillager";
	public static final String FILE_WITHER_SKELETON = "wither-skeleton";

	public static final String FIELD_USE_CUSTOM_BABY_SPAWN_CHANCE = "use_custom_baby_spawn_chance";
	public static final String FIELD_CAN_BREAK_DOORS = "can_break_doors";
	public static final String FIELD_CAN_PICK_UP_LOOT = "can_pick_up_loot";
	public static final String FIELD_SPAWN_WEIGHT = "spawn_weight";
	public static final String FIELD_ARMOR_SPAWN_WEIGHT = "armor_spawn_weight";
	public static final String FIELD_NO_ARMOR_SPAWN_WEIGHT = "no_armor_spawn_weight";
	public static final String FIELD_ARMOR_NETHERITE_WEIGHT = "armor_netherite_weight";
	public static final String FIELD_ARMOR_DIAMOND_WEIGHT = "armor_diamond_weight";
	public static final String FIELD_ARMOR_GOLD_WEIGHT = "armor_gold_weight";
	public static final String FIELD_ARMOR_IRON_WEIGHT = "armor_iron_weight";
	public static final String FIELD_ARMOR_COPPER_WEIGHT = "armor_copper_weight";
	public static final String FIELD_ARMOR_LEATHER_WEIGHT = "armor_leather_weight";
	public static final String FIELD_ARMOR_HELMET_ONLY_WEIGHT = "armor_helmet_only_weight";
	public static final String FIELD_ARMOR_HELMET_BOOTS_WEIGHT = "armor_helmet_boots_weight";
	public static final String FIELD_ARMOR_FULL_SET_WEIGHT = "armor_full_set_weight";
	public static final String FIELD_CROSSBOW_SPAWN_WEIGHT = "crossbow_spawn_weight";
	public static final String FIELD_GOLDEN_SWORD_SPAWN_WEIGHT = "golden_sword_spawn_weight";
	public static final String FIELD_ADULT_PIGLIN_SPAWN_WEIGHT = "adult_spawn_weight";
	public static final String FIELD_BABY_PIGLIN_SPAWN_WEIGHT = "baby_spawn_weight";
	public static final String FIELD_ADULT_PIGLIN = "adult_piglin";
	public static final String FIELD_BABY_PIGLIN = "baby_piglin";

	public static final String FIELD_ADULT_ZOMBIE = "adult_zombie";
	public static final String FIELD_BABY_ZOMBIE = "baby_zombie";
	public static final String FIELD_ADULT_HUSK = "adult_husk";
	public static final String FIELD_BABY_HUSK = "baby_husk";
	public static final String FIELD_ADULT_DROWNED = "adult_drowned";
	public static final String FIELD_BABY_DROWNED = "baby_drowned";
	public static final String FIELD_ADULT_ZOMBIE_VILLAGER = "adult_zombie_villager";
	public static final String FIELD_BABY_ZOMBIE_VILLAGER = "baby_zombie_villager";

	public static final String FIELD_SCALE_DIFFICULTY_STEP = "scale_difficulty_step";
	public static final String FIELD_SPIDER_SPAWN_WEIGHT = "spider_spawn_weight";
	public static final String FIELD_CAVE_SPIDER_SPAWN_WEIGHT = "cave_spider_spawn_weight";
	public static final String FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT = "spider_jockey_spawn_weight";

	public static final String FIELD_WITH_BOW_SPAWN_WEIGHT = "with_bow_spawn_weight";
	public static final String FIELD_WITHOUT_BOW_SPAWN_WEIGHT = "without_bow_spawn_weight";
	public static final String FIELD_WITHER_SWORD_SPAWN_WEIGHT = "wither_sword_spawn_weight";
	public static final String FIELD_WITHER_BOW_SPAWN_WEIGHT = "wither_bow_spawn_weight";
	public static final String FIELD_REGULAR_SPAWN_WEIGHT = "regular_spawn_weight";
	public static final String FIELD_RANGED_DAMAGE = "ranged_damage";
	public static final String FIELD_ATTACK_INTERVAL = "attack_interval";
	public static final String FIELD_ATTACK_ACCURACY = "attack_accuracy";
	public static final String FIELD_CHARGE_UP_TICKS = "charge_up_ticks";

	public static final String FIELD_GRIEF_POWER_MULTIPLIER = "grief_power_multiplier";
	public static final String FIELD_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP = "explosion_destruction_difficulty_step";
	public static final String FIELD_CREEPER_SPAWN_WEIGHT = "creeper_spawn_weight";
	public static final String FIELD_CHARGED_CREEPER_SPAWN_WEIGHT = "charged_creeper_spawn_weight";
	public static final String FIELD_CREEPER = "creeper";
	public static final String FIELD_CHARGED_CREEPER = "charged_creeper";
	public static final String FIELD_EXPLOSION_POWER = "explosion_power";
	public static final String FIELD_EXPLOSION_DESTRUCTION_CHANCE = "explosion_destruction_chance";
	public static final String FIELD_FUSE_LENGTH = "fuse_length";

	private static final Map<String, JsonObject> DEFAULT_FILE_DEFAULTS = buildDefaults();

	private MadokuMobConfig() {
	}

	public static JsonObject buildMobSystemDefaults() {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(FIELD_MOB_SYSTEM_ENABLED, true);
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultMobFileDefaults() {
		Map<String, JsonObject> copy = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> entry : DEFAULT_FILE_DEFAULTS.entrySet()) {
			copy.put(entry.getKey(), entry.getValue().deepCopy());
		}
		return copy;
	}

	public static JsonObject buildDynamicMobDefaults(String fileKey) {
		if (fileKey == null) {
			return buildUniversalOnlyDefaults();
		}
		JsonObject known = DEFAULT_FILE_DEFAULTS.get(fileKey.trim().toLowerCase());
		if (known != null) {
			return known.deepCopy();
		}
		return buildUniversalOnlyDefaults();
	}

	private static Map<String, JsonObject> buildDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put(FILE_CREEPER, buildCreeperDefaults());
		defaults.put(FILE_SKELETON, buildSkeletonDefaults(16.0d, 0.0d, 3.0d, 0.25d, 0.0d, 1.0d, 7, 5.0d));
		defaults.put(FILE_STRAY, buildSkeletonDefaults(12.0d, 0.0d, 2.0d, 0.25d, 0.0d, 1.0d, 7, 4.0d));
		defaults.put(FILE_BOGGED, buildSkeletonDefaults(12.0d, 0.0d, 2.0d, 0.25d, 0.0d, 1.0d, 7, 4.0d));
		defaults.put(FILE_PARCHED, buildSkeletonDefaults(12.0d, 0.0d, 2.0d, 0.25d, 0.0d, 1.0d, 7, 4.0d));
		defaults.put(FILE_SPIDER, buildSpiderDefaults());
		defaults.put(FILE_CAVE_SPIDER, buildCaveSpiderDefaults());
		defaults.put(FILE_ZOMBIE, buildZombieTypeDefaults(FIELD_ADULT_ZOMBIE, FIELD_BABY_ZOMBIE, 24.0d, 12.0d, 1.0d, 6.0d, 3.0d, 0.2d, 0.2d, 1.0d, 7));
		defaults.put(FILE_HUSK, buildZombieTypeDefaults(FIELD_ADULT_HUSK, FIELD_BABY_HUSK, 20.0d, 10.0d, 1.0d, 5.0d, 2.5d, 0.2d, 0.25d, 1.0d, 7));
		defaults.put(FILE_DROWNED, buildZombieTypeDefaults(FIELD_ADULT_DROWNED, FIELD_BABY_DROWNED, 20.0d, 10.0d, 0.0d, 5.0d, 2.5d, 0.25d, 0.25d, 1.0d, 7));
		defaults.put(
			FILE_ZOMBIE_VILLAGER,
			buildZombieTypeDefaults(FIELD_ADULT_ZOMBIE_VILLAGER, FIELD_BABY_ZOMBIE_VILLAGER, 20.0d, 10.0d, 0.0d, 5.0d, 2.5d, 0.25d, 0.25d, 1.0d, 7)
		);
		defaults.put(FILE_PIGLIN, buildPiglinDefaults());
		defaults.put(FILE_PILLAGER, buildPillagerDefaults());
		defaults.put(FILE_WITHER_SKELETON, buildWitherSkeletonDefaults());
		return defaults;
	}

	private static JsonObject buildUniversalOnlyDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.add(FIELD_HEALTH, null);
		root.add(FIELD_ARMOR, null);
		root.add(FIELD_DAMAGE, null);
		root.add(FIELD_MOVEMENT_SPEED, null);
		root.add(FIELD_KNOCKBACK_RESISTANCE, null);
		root.add(FIELD_SCALE, null);
		root.add(FIELD_EXPERIENCE_DROP, null);
		return root;
	}

	private static JsonObject buildUniversalDefaults(
		Double health,
		Double armor,
		Double damage,
		Double movementSpeed,
		Double knockbackResistance,
		Double scale,
		Integer experienceDrop
	) {
		JsonObject root = new JsonObject();
		if (isNonZero(health)) {
			root.addProperty(FIELD_HEALTH, health);
		}
		if (isNonZero(armor)) {
			root.addProperty(FIELD_ARMOR, armor);
		}
		if (isNonZero(damage)) {
			root.addProperty(FIELD_DAMAGE, damage);
		}
		if (isNonZero(movementSpeed)) {
			root.addProperty(FIELD_MOVEMENT_SPEED, movementSpeed);
		}
		if (isNonZero(knockbackResistance)) {
			root.addProperty(FIELD_KNOCKBACK_RESISTANCE, knockbackResistance);
		}
		if (isNonZero(scale)) {
			root.addProperty(FIELD_SCALE, scale);
		}
		if (experienceDrop != null) {
			root.addProperty(FIELD_EXPERIENCE_DROP, experienceDrop);
		}
		return root;
	}

	private static boolean isNonZero(Double value) {
		return value != null && Double.isFinite(value) && Double.compare(value, 0.0D) != 0;
	}

	private static JsonObject buildCreeperDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_GRIEF_POWER_MULTIPLIER, 0.5d);
		root.addProperty(FIELD_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP, 0.2d);
		root.addProperty(FIELD_CREEPER_SPAWN_WEIGHT, 95.0d);
		root.addProperty(FIELD_CHARGED_CREEPER_SPAWN_WEIGHT, 5.0d);

		JsonObject creeper = buildUniversalDefaults(12.0d, 1.0d, null, 0.25d, 0.1d, null, 7);
		creeper.addProperty(FIELD_EXPLOSION_POWER, 3.0d);
		creeper.addProperty(FIELD_EXPLOSION_DESTRUCTION_CHANCE, 0.4d);
		creeper.addProperty(FIELD_FUSE_LENGTH, 30.0d);

		JsonObject charged = buildUniversalDefaults(12.0d, 1.0d, null, 0.3d, 0.2d, null, 7);
		charged.addProperty(FIELD_EXPLOSION_POWER, 5.0d);
		charged.addProperty(FIELD_EXPLOSION_DESTRUCTION_CHANCE, 0.6d);
		charged.addProperty(FIELD_FUSE_LENGTH, 24.0d);
		charged.addProperty(FIELD_EXPERIENCE_DROP, 11);

		root.add(FIELD_CREEPER, creeper);
		root.add(FIELD_CHARGED_CREEPER, charged);
		return root;
	}

	private static JsonObject buildSkeletonDefaults(
		double health,
		double armor,
		double damage,
		double movementSpeed,
		double knockbackResistance,
		double scale,
		int experience,
		double rangedDamage
	) {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_WITH_BOW_SPAWN_WEIGHT, 95.0d);
		root.addProperty(FIELD_WITHOUT_BOW_SPAWN_WEIGHT, 5.0d);
		root.addProperty(FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0d);
		root.addProperty(FIELD_REGULAR_SPAWN_WEIGHT, 95.0d);
		addArmorSpawnDefaults(root);
		root.addProperty(FIELD_RANGED_DAMAGE, rangedDamage);
		root.addProperty(FIELD_ATTACK_INTERVAL, 20.0d);
		root.addProperty(FIELD_ATTACK_ACCURACY, 0.7d);
		root.addProperty(FIELD_CHARGE_UP_TICKS, 10.0d);
		for (Map.Entry<String, com.google.gson.JsonElement> entry : buildUniversalDefaults(health, armor, damage, movementSpeed, knockbackResistance, scale, experience).entrySet()) {
			root.add(entry.getKey(), entry.getValue());
		}
		return root;
	}

	private static JsonObject buildSpiderDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_SCALE_DIFFICULTY_STEP, 0.05d);
		root.addProperty(FIELD_SPIDER_SPAWN_WEIGHT, 90.0d);
		root.addProperty(FIELD_CAVE_SPIDER_SPAWN_WEIGHT, 5.0d);
		root.addProperty(FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0d);
		for (Map.Entry<String, com.google.gson.JsonElement> entry : buildUniversalDefaults(16.0d, 0.0d, 4.0d, 0.3d, 0.0d, 0.7d, 7).entrySet()) {
			root.add(entry.getKey(), entry.getValue());
		}
		return root;
	}

	private static JsonObject buildCaveSpiderDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_SCALE_DIFFICULTY_STEP, 0.05d);
		for (Map.Entry<String, com.google.gson.JsonElement> entry : buildUniversalDefaults(12.0d, 0.0d, 3.0d, 0.3d, 0.0d, 0.7d, 7).entrySet()) {
			root.add(entry.getKey(), entry.getValue());
		}
		return root;
	}

	private static JsonObject buildZombieTypeDefaults(
		String adultKey,
		String babyKey,
		double adultHealth,
		double babyHealth,
		double armor,
		double adultDamage,
		double babyDamage,
		double adultSpeed,
		double babySpeed,
		double scale,
		int experience
	) {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_USE_CUSTOM_BABY_SPAWN_CHANCE, true);
		addArmorSpawnDefaults(root);
		root.add(adultKey, buildZombieVariant(adultHealth, armor, adultDamage, adultSpeed, scale, experience, false, false, 95.0d));
		root.add(babyKey, buildZombieBabyVariant(babyHealth, babyDamage, babySpeed, 5, false, false, 5.0d));
		return root;
	}

	private static JsonObject buildZombieVariant(
		double health,
		double armor,
		double damage,
		double movementSpeed,
		double scale,
		int experience,
		boolean canBreakDoors,
		boolean canPickUpLoot,
		double spawnWeight
	) {
		JsonObject root = buildUniversalDefaults(health, armor, damage, movementSpeed, armor > 0.0d ? 0.2d : 0.0d, scale, experience);
		root.addProperty(FIELD_CAN_BREAK_DOORS, canBreakDoors);
		root.addProperty(FIELD_CAN_PICK_UP_LOOT, canPickUpLoot);
		root.addProperty(FIELD_SPAWN_WEIGHT, spawnWeight);
		return root;
	}

	private static JsonObject buildZombieBabyVariant(
		double health,
		double damage,
		double movementSpeed,
		int experience,
		boolean canBreakDoors,
		boolean canPickUpLoot,
		double spawnWeight
	) {
		JsonObject root = buildUniversalDefaults(health, null, damage, movementSpeed, null, null, experience);
		root.addProperty(FIELD_CAN_BREAK_DOORS, canBreakDoors);
		root.addProperty(FIELD_CAN_PICK_UP_LOOT, canPickUpLoot);
		root.addProperty(FIELD_SPAWN_WEIGHT, spawnWeight);
		return root;
	}

	private static JsonObject buildPillagerDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_RANGED_DAMAGE, 6.0d);
		root.addProperty(FIELD_ATTACK_INTERVAL, 20.0d);
		root.addProperty(FIELD_ATTACK_ACCURACY, 0.7d);
		root.addProperty(FIELD_CHARGE_UP_TICKS, 10.0d);
		for (Map.Entry<String, com.google.gson.JsonElement> entry : buildUniversalDefaults(20.0d, 1.0d, 5.0d, 0.25d, 0.1d, 1.0d, 11).entrySet()) {
			root.add(entry.getKey(), entry.getValue());
		}
		return root;
	}

	private static JsonObject buildWitherSkeletonDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		addArmorSpawnDefaults(root);
		root.addProperty(FIELD_WITHER_SWORD_SPAWN_WEIGHT, 90.0d);
		root.addProperty(FIELD_WITHER_BOW_SPAWN_WEIGHT, 10.0d);
		root.addProperty(FIELD_RANGED_DAMAGE, 6.0d);
		root.addProperty(FIELD_ATTACK_INTERVAL, 20.0d);
		root.addProperty(FIELD_ATTACK_ACCURACY, 0.7d);
		root.addProperty(FIELD_CHARGE_UP_TICKS, 10.0d);
		for (Map.Entry<String, com.google.gson.JsonElement> entry : buildUniversalDefaults(20.0d, 0.0d, 7.0d, 0.25d, 0.0d, 1.0d, 11).entrySet()) {
			root.add(entry.getKey(), entry.getValue());
		}
		return root;
	}

	private static JsonObject buildPiglinDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		addArmorSpawnDefaults(root);
		root.addProperty(FIELD_ADULT_PIGLIN_SPAWN_WEIGHT, 90.0d);
		root.addProperty(FIELD_BABY_PIGLIN_SPAWN_WEIGHT, 10.0d);
		root.addProperty(FIELD_RANGED_DAMAGE, 5.0d);
		root.addProperty(FIELD_ATTACK_INTERVAL, 20.0d);
		root.addProperty(FIELD_ATTACK_ACCURACY, 0.7d);
		root.addProperty(FIELD_CHARGE_UP_TICKS, 10.0d);
		root.add(FIELD_ADULT_PIGLIN, buildPiglinAdultVariant());
		root.add(FIELD_BABY_PIGLIN, buildPiglinBabyVariant());
		return root;
	}

	private static JsonObject buildPiglinAdultVariant() {
		JsonObject root = buildUniversalDefaults(24.0d, 1.0d, 6.0d, 0.25d, 0.1d, 1.0d, 11);
		root.addProperty(FIELD_CROSSBOW_SPAWN_WEIGHT, 50.0d);
		root.addProperty(FIELD_GOLDEN_SWORD_SPAWN_WEIGHT, 50.0d);
		return root;
	}

	private static JsonObject buildPiglinBabyVariant() {
		return buildUniversalDefaults(12.0d, 0.0d, 3.5d, 0.25d, 0.0d, 1.0d, 3);
	}

	private static void addArmorSpawnDefaults(JsonObject root) {
		if (root == null) {
			return;
		}
		root.addProperty(FIELD_ARMOR_SPAWN_WEIGHT, 10.0d);
		root.addProperty(FIELD_NO_ARMOR_SPAWN_WEIGHT, 90.0d);
		root.addProperty(FIELD_ARMOR_NETHERITE_WEIGHT, 1.0d);
		root.addProperty(FIELD_ARMOR_DIAMOND_WEIGHT, 5.0d);
		root.addProperty(FIELD_ARMOR_GOLD_WEIGHT, 10.0d);
		root.addProperty(FIELD_ARMOR_IRON_WEIGHT, 17.0d);
		root.addProperty(FIELD_ARMOR_COPPER_WEIGHT, 28.0d);
		root.addProperty(FIELD_ARMOR_LEATHER_WEIGHT, 39.0d);
		root.addProperty(FIELD_ARMOR_HELMET_ONLY_WEIGHT, 60.0d);
		root.addProperty(FIELD_ARMOR_HELMET_BOOTS_WEIGHT, 30.0d);
		root.addProperty(FIELD_ARMOR_FULL_SET_WEIGHT, 10.0d);
	}
}
