package madoku.craft.mobs.system;

import com.google.gson.JsonObject;
import madoku.craft.API.system.MadokuJSONSystem;
import net.minecraft.entity.EntityType;
import net.minecraft.world.Difficulty;

/**
 * Loads and sanitizes all zombie-family settings from the Madoku JSON system.
 */
public final class ZombieMobConfig {
	private static final String JSON_FOLDER_ID = "Mobs";
	private static final String JSON_FILE_ZOMBIE = "zombie";
	private static final String JSON_FILE_HUSK = "husk";
	private static final String JSON_FILE_DROWNED = "drowned";
	private static final String JSON_FILE_ZOMBIE_VILLAGER = "zombie-villager";

	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_USE_CUSTOM_BABY_SPAWN_CHANCE = "use_custom_baby_spawn_chance";

	private static final String KEY_ADULT_ZOMBIE = "adult_zombie";
	private static final String KEY_BABY_ZOMBIE = "baby_zombie";
	private static final String KEY_ADULT_HUSK = "adult_husk";
	private static final String KEY_BABY_HUSK = "baby_husk";
	private static final String KEY_ADULT_DROWNED = "adult_drowned";
	private static final String KEY_BABY_DROWNED = "baby_drowned";
	private static final String KEY_ADULT_ZOMBIE_VILLAGER = "adult_zombie_villager";
	private static final String KEY_BABY_ZOMBIE_VILLAGER = "baby_zombie_villager";

	private static final String KEY_CAN_BREAK_DOORS = "can_break_doors";
	private static final String KEY_CAN_PICK_UP_LOOT = "can_pick_up_loot";
	private static final String KEY_SPAWN_WEIGHT = "spawn_weight";

	private static final double DEFAULT_VANILLA_BABY_CHANCE = 0.05;

	private final ZombieTypeConfig zombie;
	private final ZombieTypeConfig husk;
	private final ZombieTypeConfig drowned;
	private final ZombieTypeConfig zombieVillager;

	private ZombieMobConfig(
		ZombieTypeConfig zombie,
		ZombieTypeConfig husk,
		ZombieTypeConfig drowned,
		ZombieTypeConfig zombieVillager
	) {
		this.zombie = zombie;
		this.husk = husk;
		this.drowned = drowned;
		this.zombieVillager = zombieVillager;
	}

	public static ZombieMobConfig load() {
		return new ZombieMobConfig(
			loadType(JSON_FILE_ZOMBIE, KEY_ADULT_ZOMBIE, KEY_BABY_ZOMBIE, defaultZombieAdult(), defaultZombieBaby()),
			loadType(JSON_FILE_HUSK, KEY_ADULT_HUSK, KEY_BABY_HUSK, defaultHuskAdult(), defaultHuskBaby()),
			loadType(JSON_FILE_DROWNED, KEY_ADULT_DROWNED, KEY_BABY_DROWNED, defaultDrownedAdult(), defaultDrownedBaby()),
			loadType(
				JSON_FILE_ZOMBIE_VILLAGER,
				KEY_ADULT_ZOMBIE_VILLAGER,
				KEY_BABY_ZOMBIE_VILLAGER,
				defaultZombieVillagerAdult(),
				defaultZombieVillagerBaby()
			)
		);
	}

	public ZombieTypeConfig zombie() {
		return zombie;
	}

	public ZombieTypeConfig husk() {
		return husk;
	}

	public ZombieTypeConfig drowned() {
		return drowned;
	}

	public ZombieTypeConfig zombieVillager() {
		return zombieVillager;
	}

	public boolean anyEnabled() {
		return zombie.enabled() || husk.enabled() || drowned.enabled() || zombieVillager.enabled();
	}

	public ZombieTypeConfig resolveType(EntityType<?> type) {
		if (type == EntityType.ZOMBIE) {
			return zombie;
		}
		if (type == EntityType.HUSK) {
			return husk;
		}
		if (type == EntityType.DROWNED) {
			return drowned;
		}
		if (type == EntityType.ZOMBIE_VILLAGER) {
			return zombieVillager;
		}
		return null;
	}

	public static double resolveBabySpawnChance(ZombieTypeConfig config, Difficulty difficulty, boolean hardcore) {
		if (config == null || difficulty == null) {
			return DEFAULT_VANILLA_BABY_CHANCE;
		}

		MobSystemUtil.SpawnWeightPair adjustedWeights = resolveAdjustedSpawnWeights(config, difficulty, hardcore);
		double totalWeight = adjustedWeights.regularWeight() + adjustedWeights.specialWeight();
		double chance = totalWeight > 0.0
			? adjustedWeights.specialWeight() / totalWeight
			: DEFAULT_VANILLA_BABY_CHANCE;
		return MobConfigJsonUtil.clamp(chance, 0.0, 1.0, DEFAULT_VANILLA_BABY_CHANCE);
	}

	public static MobSystemUtil.SpawnWeightPair resolveAdjustedSpawnWeights(
		ZombieTypeConfig config,
		Difficulty difficulty,
		boolean hardcore
	) {
		if (config == null) {
			return new MobSystemUtil.SpawnWeightPair(0.0, 0.0);
		}
		return MobSystemUtil.resolveDifficultyShiftedSpawnWeights(
			config.adult().spawnWeight(),
			config.baby().spawnWeight(),
			difficulty,
			hardcore,
			MobSystemUtil.SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
	}

	private static ZombieTypeConfig loadType(
		String fileId,
		String adultKey,
		String babyKey,
		ZombieVariantConfig defaultAdult,
		ZombieVariantConfig defaultBaby
	) {
		JsonObject defaults = buildDefaults(adultKey, babyKey, defaultAdult, defaultBaby);
		MadokuJSONSystem.ManagedJSON managed = MadokuJSONSystem.load(JSON_FOLDER_ID, fileId, defaults);
		JsonObject root = managed.getRoot();

		boolean changed = false;

		boolean enabled = MobConfigJsonUtil.readBoolean(root, KEY_ENABLED, true);
		changed |= MobConfigJsonUtil.setBoolean(root, KEY_ENABLED, enabled);

		boolean useCustomBabySpawnChance = MobConfigJsonUtil.readBoolean(root, KEY_USE_CUSTOM_BABY_SPAWN_CHANCE, true);
		changed |= MobConfigJsonUtil.setBoolean(root, KEY_USE_CUSTOM_BABY_SPAWN_CHANCE, useCustomBabySpawnChance);

		boolean hasAdultNode = root.get(adultKey) instanceof JsonObject;
		JsonObject adultNode = MobConfigJsonUtil.getOrCreateObject(root, adultKey);
		changed |= !hasAdultNode;
		JsonObject babyNode = MobConfigJsonUtil.getOrCreateObject(root, babyKey);

		VariantLoadResult adultResult = readVariant(adultNode, defaultAdult);
		VariantLoadResult babyResult = readVariant(babyNode, defaultBaby);
		changed |= adultResult.changed();
		changed |= babyResult.changed();

		if (changed) {
			managed.save();
		}

		return new ZombieTypeConfig(
			enabled,
			useCustomBabySpawnChance,
			adultResult.variant(),
			babyResult.variant()
		);
	}

	private static JsonObject buildDefaults(
		String adultKey,
		String babyKey,
		ZombieVariantConfig defaultAdult,
		ZombieVariantConfig defaultBaby
	) {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(KEY_ENABLED, true);
		defaults.addProperty(KEY_USE_CUSTOM_BABY_SPAWN_CHANCE, true);
		defaults.add(adultKey, buildVariantDefaults(defaultAdult));
		defaults.add(babyKey, buildVariantDefaults(defaultBaby));
		return defaults;
	}

	private static ZombieVariantConfig defaultZombieAdult() {
		return new ZombieVariantConfig(
			new MobConfigJsonUtil.UniversalMobStats(24.0, 1.0, 6.0, 0.2, 0.2, 1.0, 7),
			false,
			false,
			95.0
		);
	}

	private static ZombieVariantConfig defaultZombieBaby() {
		return new ZombieVariantConfig(
			new MobConfigJsonUtil.UniversalMobStats(12.0, 1.0, 3.0, 0.2, 0.2, 1.0, 7),
			false,
			false,
			5.0
		);
	}

	private static ZombieVariantConfig defaultHuskAdult() {
		return new ZombieVariantConfig(
			new MobConfigJsonUtil.UniversalMobStats(20.0, 1.0, 5.0, 0.2, 0.2, 1.0, 7),
			false,
			false,
			95.0
		);
	}

	private static ZombieVariantConfig defaultHuskBaby() {
		return new ZombieVariantConfig(
			new MobConfigJsonUtil.UniversalMobStats(10.0, 1.0, 2.5, 0.25, 0.2, 1.0, 7),
			false,
			false,
			5.0
		);
	}

	private static ZombieVariantConfig defaultDrownedAdult() {
		return new ZombieVariantConfig(
			new MobConfigJsonUtil.UniversalMobStats(20.0, 0.0, 5.0, 0.25, 0.0, 1.0, 7),
			false,
			false,
			95.0
		);
	}

	private static ZombieVariantConfig defaultDrownedBaby() {
		return new ZombieVariantConfig(
			new MobConfigJsonUtil.UniversalMobStats(10.0, 0.0, 2.5, 0.25, 0.0, 1.0, 7),
			false,
			false,
			5.0
		);
	}

	private static ZombieVariantConfig defaultZombieVillagerAdult() {
		return new ZombieVariantConfig(
			new MobConfigJsonUtil.UniversalMobStats(20.0, 0.0, 5.0, 0.25, 0.0, 1.0, 7),
			false,
			false,
			95.0
		);
	}

	private static ZombieVariantConfig defaultZombieVillagerBaby() {
		return new ZombieVariantConfig(
			new MobConfigJsonUtil.UniversalMobStats(10.0, 0.0, 2.5, 0.25, 0.0, 1.0, 7),
			false,
			false,
			5.0
		);
	}

	private static JsonObject buildVariantDefaults(ZombieVariantConfig defaults) {
		JsonObject node = MobConfigJsonUtil.buildUniversalStatDefaults(defaults.stats());
		node.addProperty(KEY_CAN_BREAK_DOORS, defaults.canBreakDoors());
		node.addProperty(KEY_CAN_PICK_UP_LOOT, defaults.canPickUpLoot());
		node.addProperty(KEY_SPAWN_WEIGHT, defaults.spawnWeight());
		return node;
	}

	private static VariantLoadResult readVariant(JsonObject node, ZombieVariantConfig fallback) {
		boolean changed = false;

		MobConfigJsonUtil.UniversalMobStatsLoadResult statsResult = MobConfigJsonUtil.readUniversalStatOverrides(node);
		changed |= statsResult.changed();

		boolean canBreakDoors = MobConfigJsonUtil.readBoolean(node, KEY_CAN_BREAK_DOORS, fallback.canBreakDoors());
		changed |= MobConfigJsonUtil.setBoolean(node, KEY_CAN_BREAK_DOORS, canBreakDoors);

		boolean canPickUpLoot = MobConfigJsonUtil.readBoolean(node, KEY_CAN_PICK_UP_LOOT, fallback.canPickUpLoot());
		changed |= MobConfigJsonUtil.setBoolean(node, KEY_CAN_PICK_UP_LOOT, canPickUpLoot);

		double spawnWeight = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(node, KEY_SPAWN_WEIGHT, fallback.spawnWeight()),
			fallback.spawnWeight()
		);
		changed |= MobConfigJsonUtil.setDouble(node, KEY_SPAWN_WEIGHT, spawnWeight);

		ZombieVariantConfig variant = new ZombieVariantConfig(
			statsResult.stats(),
			canBreakDoors,
			canPickUpLoot,
			spawnWeight
		);
		return new VariantLoadResult(variant, changed);
	}

	private record VariantLoadResult(ZombieVariantConfig variant, boolean changed) {
	}

	public record ZombieTypeConfig(
		boolean enabled,
		boolean useCustomBabySpawnChance,
		ZombieVariantConfig adult,
		ZombieVariantConfig baby
	) {
	}

	public record ZombieVariantConfig(
		MobConfigJsonUtil.UniversalMobStats stats,
		boolean canBreakDoors,
		boolean canPickUpLoot,
		double spawnWeight
	) {
	}
}
