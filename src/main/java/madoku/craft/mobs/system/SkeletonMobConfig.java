package madoku.craft.mobs.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import madoku.craft.API.system.MadokuJSONSystem;
import net.minecraft.entity.EntityType;

/**
 * Loads skeleton-family settings from separate JSON files per mob type.
 */
public final class SkeletonMobConfig {
	private static final String JSON_FOLDER_ID = "Mobs";
	private static final String JSON_FILE_SKELETON = "skeleton";
	private static final String JSON_FILE_STRAY = "stray";
	private static final String JSON_FILE_BOGGED = "bogged";
	private static final String JSON_FILE_PARCHED = "parched";

	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_WITH_BOW_SPAWN_WEIGHT = "with_bow_spawn_weight";
	private static final String KEY_WITHOUT_BOW_SPAWN_WEIGHT = "without_bow_spawn_weight";
	private static final String KEY_SPIDER_JOCKEY_SPAWN_WEIGHT = "spider_jockey_spawn_weight";
	private static final String KEY_REGULAR_SPAWN_WEIGHT = "regular_spawn_weight";
	private static final String KEY_RANGED_DAMAGE = "ranged_damage";
	private static final String KEY_ATTACK_INTERVAL = "attack_interval";
	private static final String KEY_ATTACK_ACCURACY = "attack_accuracy";
	private static final String KEY_CHARGE_UP_TICKS = "charge_up_ticks";

	private final SkeletonTypeConfig skeleton;
	private final SkeletonTypeConfig stray;
	private final SkeletonTypeConfig bogged;
	private final SkeletonTypeConfig parched;

	private SkeletonMobConfig(
		SkeletonTypeConfig skeleton,
		SkeletonTypeConfig stray,
		SkeletonTypeConfig bogged,
		SkeletonTypeConfig parched
	) {
		this.skeleton = skeleton;
		this.stray = stray;
		this.bogged = bogged;
		this.parched = parched;
	}

	public static SkeletonMobConfig load() {
		return new SkeletonMobConfig(
			loadTypeConfig(JSON_FILE_SKELETON, defaultSkeletonStats(), 5.0),
			loadTypeConfig(JSON_FILE_STRAY, defaultVariantStats(), 4.0),
			loadTypeConfig(JSON_FILE_BOGGED, defaultVariantStats(), 4.0),
			loadTypeConfig(JSON_FILE_PARCHED, defaultVariantStats(), 4.0)
		);
	}

	public SkeletonTypeConfig skeleton() {
		return skeleton;
	}

	public SkeletonTypeConfig stray() {
		return stray;
	}

	public SkeletonTypeConfig bogged() {
		return bogged;
	}

	public SkeletonTypeConfig parched() {
		return parched;
	}

	public boolean anyEnabled() {
		return skeleton.enabled() || stray.enabled() || bogged.enabled() || parched.enabled();
	}

	public SkeletonTypeConfig resolveVariant(EntityType<?> type) {
		if (type == EntityType.SKELETON) {
			return skeleton;
		}
		if (type == EntityType.STRAY) {
			return stray;
		}
		if (type == EntityType.BOGGED) {
			return bogged;
		}
		if (type == EntityType.PARCHED) {
			return parched;
		}
		return null;
	}

	private static SkeletonTypeConfig loadTypeConfig(
		String fileId,
		MobConfigJsonUtil.UniversalMobStats defaultStats,
		double defaultRangedDamage
	) {
		JsonObject defaults = buildDefaults(defaultStats, defaultRangedDamage);
		MadokuJSONSystem.ManagedJSON managed = MadokuJSONSystem.load(JSON_FOLDER_ID, fileId, defaults);
		JsonObject root = managed.getRoot();

		boolean changed = false;

		boolean enabled = MobConfigJsonUtil.readBoolean(root, KEY_ENABLED, true);
		changed |= MobConfigJsonUtil.setBoolean(root, KEY_ENABLED, enabled);

		double withBowSpawnWeight = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(root, KEY_WITH_BOW_SPAWN_WEIGHT, 95.0),
			95.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_WITH_BOW_SPAWN_WEIGHT, withBowSpawnWeight);

		double withoutBowSpawnWeight = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(root, KEY_WITHOUT_BOW_SPAWN_WEIGHT, 5.0),
			5.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_WITHOUT_BOW_SPAWN_WEIGHT, withoutBowSpawnWeight);

		double spiderJockeySpawnWeight = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(root, KEY_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0),
			5.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_SPIDER_JOCKEY_SPAWN_WEIGHT, spiderJockeySpawnWeight);

		double regularSpawnWeight = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(root, KEY_REGULAR_SPAWN_WEIGHT, 95.0),
			95.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_REGULAR_SPAWN_WEIGHT, regularSpawnWeight);

		double rangedDamage = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(root, KEY_RANGED_DAMAGE, defaultRangedDamage),
			defaultRangedDamage
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_RANGED_DAMAGE, rangedDamage);

		double attackInterval = MobConfigJsonUtil.sanitizePositive(
			MobConfigJsonUtil.readDouble(root, KEY_ATTACK_INTERVAL, 20.0),
			20.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_ATTACK_INTERVAL, attackInterval);

		double attackAccuracy = MobConfigJsonUtil.clamp(
			MobConfigJsonUtil.readDouble(root, KEY_ATTACK_ACCURACY, 0.7),
			0.0,
			1.0,
			0.7
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_ATTACK_ACCURACY, attackAccuracy);

		double chargeUpTicks = MobConfigJsonUtil.sanitizePositive(
			MobConfigJsonUtil.readDouble(root, KEY_CHARGE_UP_TICKS, 10.0),
			10.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_CHARGE_UP_TICKS, chargeUpTicks);

		MobConfigJsonUtil.UniversalMobStatsLoadResult statsResult = MobConfigJsonUtil.readUniversalStatOverrides(root);
		changed |= statsResult.changed();

		if (changed) {
			managed.save();
		}

		return new SkeletonTypeConfig(
			enabled,
			withBowSpawnWeight,
			withoutBowSpawnWeight,
			spiderJockeySpawnWeight,
			regularSpawnWeight,
			rangedDamage,
			attackInterval,
			attackAccuracy,
			chargeUpTicks,
			statsResult.stats()
		);
	}

	private static JsonObject buildDefaults(MobConfigJsonUtil.UniversalMobStats defaultStats, double defaultRangedDamage) {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(KEY_ENABLED, true);
		defaults.addProperty(KEY_WITH_BOW_SPAWN_WEIGHT, 95.0);
		defaults.addProperty(KEY_WITHOUT_BOW_SPAWN_WEIGHT, 5.0);
		defaults.addProperty(KEY_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0);
		defaults.addProperty(KEY_REGULAR_SPAWN_WEIGHT, 95.0);
		defaults.addProperty(KEY_RANGED_DAMAGE, defaultRangedDamage);
		defaults.addProperty(KEY_ATTACK_INTERVAL, 20.0);
		defaults.addProperty(KEY_ATTACK_ACCURACY, 0.7);
		defaults.addProperty(KEY_CHARGE_UP_TICKS, 10.0);

		JsonObject universalDefaults = MobConfigJsonUtil.buildUniversalStatDefaults(defaultStats);
		for (Map.Entry<String, JsonElement> entry : universalDefaults.entrySet()) {
			defaults.add(entry.getKey(), entry.getValue());
		}
		return defaults;
	}

	private static MobConfigJsonUtil.UniversalMobStats defaultSkeletonStats() {
		return new MobConfigJsonUtil.UniversalMobStats(16.0, 0.0, 4.0, 0.25, 0.0, 1.0, 7);
	}

	private static MobConfigJsonUtil.UniversalMobStats defaultVariantStats() {
		return new MobConfigJsonUtil.UniversalMobStats(12.0, 0.0, 3.0, 0.25, 0.0, 1.0, 7);
	}

	public record SkeletonTypeConfig(
		boolean enabled,
		double withBowSpawnWeight,
		double withoutBowSpawnWeight,
		double spiderJockeySpawnWeight,
		double regularSpawnWeight,
		double rangedDamage,
		double attackInterval,
		double attackAccuracy,
		double chargeUpTicks,
		MobConfigJsonUtil.UniversalMobStats stats
	) {
	}
}
