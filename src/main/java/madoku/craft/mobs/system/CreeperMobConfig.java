package madoku.craft.mobs.system;

import com.google.gson.JsonObject;

import madoku.craft.API.system.MadokuJSONSystem;
import net.minecraft.world.Difficulty;

/**
 * Loads and sanitizes creeper stat/behavior settings from the Madoku JSON system.
 */
public final class CreeperMobConfig {
	private static final String JSON_FOLDER_ID = "Mobs";
	private static final String JSON_FILE_ID = "creeper";

	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_GRIEF_POWER_MULTIPLIER = "grief_power_multiplier";
	private static final String KEY_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP = "explosion_destruction_difficulty_step";
	private static final String KEY_CREEPER_SPAWN_WEIGHT = "creeper_spawn_weight";
	private static final String KEY_CHARGED_CREEPER_SPAWN_WEIGHT = "charged_creeper_spawn_weight";
	private static final String KEY_CREEPER = "creeper";
	private static final String KEY_CHARGED_CREEPER = "charged_creeper";

	private static final String KEY_EXPLOSION_POWER = "explosion_power";
	private static final String KEY_EXPLOSION_DESTRUCTION_CHANCE = "explosion_destruction_chance";
	private static final String KEY_FUSE_LENGTH = "fuse_length";

	private final boolean enabled;
	private final double griefPowerMultiplier;
	private final double explosionDestructionDifficultyStep;
	private final double creeperSpawnWeight;
	private final double chargedCreeperSpawnWeight;
	private final CreeperVariantConfig creeper;
	private final CreeperVariantConfig chargedCreeper;

	private CreeperMobConfig(
		boolean enabled,
		double griefPowerMultiplier,
		double explosionDestructionDifficultyStep,
		double creeperSpawnWeight,
		double chargedCreeperSpawnWeight,
		CreeperVariantConfig creeper,
		CreeperVariantConfig chargedCreeper
	) {
		this.enabled = enabled;
		this.griefPowerMultiplier = griefPowerMultiplier;
		this.explosionDestructionDifficultyStep = explosionDestructionDifficultyStep;
		this.creeperSpawnWeight = creeperSpawnWeight;
		this.chargedCreeperSpawnWeight = chargedCreeperSpawnWeight;
		this.creeper = creeper;
		this.chargedCreeper = chargedCreeper;
	}

	public static CreeperMobConfig load() {
		JsonObject defaults = buildDefaults();
		MadokuJSONSystem.ManagedJSON managed = MadokuJSONSystem.load(JSON_FOLDER_ID, JSON_FILE_ID, defaults);
		JsonObject root = managed.getRoot();

		boolean changed = false;

		boolean enabled = MobConfigJsonUtil.readBoolean(root, KEY_ENABLED, true);
		changed |= MobConfigJsonUtil.setBoolean(root, KEY_ENABLED, enabled);

		double griefPowerMultiplier = MobConfigJsonUtil.clamp(
			MobConfigJsonUtil.readDouble(root, KEY_GRIEF_POWER_MULTIPLIER, 0.5),
			0.0,
			1.0,
			0.5
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_GRIEF_POWER_MULTIPLIER, griefPowerMultiplier);

		double explosionDestructionDifficultyStep = MobConfigJsonUtil.clamp(
			MobConfigJsonUtil.readDouble(root, KEY_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP, 0.1),
			0.0,
			1.0,
			0.1
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP, explosionDestructionDifficultyStep);

		double creeperSpawnWeight = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(root, KEY_CREEPER_SPAWN_WEIGHT, 95.0),
			95.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_CREEPER_SPAWN_WEIGHT, creeperSpawnWeight);

		double chargedCreeperSpawnWeight = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(root, KEY_CHARGED_CREEPER_SPAWN_WEIGHT, 5.0),
			5.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_CHARGED_CREEPER_SPAWN_WEIGHT, chargedCreeperSpawnWeight);

		JsonObject creeperNode = MobConfigJsonUtil.getOrCreateObject(root, KEY_CREEPER);
		JsonObject chargedNode = MobConfigJsonUtil.getOrCreateObject(root, KEY_CHARGED_CREEPER);

		VariantLoadResult creeperResult = readVariant(creeperNode);
		VariantLoadResult chargedResult = readVariant(chargedNode);
		changed |= creeperResult.changed();
		changed |= chargedResult.changed();

		if (changed) {
			managed.save();
		}

		return new CreeperMobConfig(
			enabled,
			griefPowerMultiplier,
			explosionDestructionDifficultyStep,
			creeperSpawnWeight,
			chargedCreeperSpawnWeight,
			creeperResult.variant(),
			chargedResult.variant()
		);
	}

	public boolean enabled() {
		return enabled;
	}

	public double griefPowerMultiplier() {
		return griefPowerMultiplier;
	}

	public double explosionDestructionDifficultyStep() {
		return explosionDestructionDifficultyStep;
	}

	public double creeperSpawnWeight() {
		return creeperSpawnWeight;
	}

	public double chargedCreeperSpawnWeight() {
		return chargedCreeperSpawnWeight;
	}

	public CreeperVariantConfig creeper() {
		return creeper;
	}

	public CreeperVariantConfig chargedCreeper() {
		return chargedCreeper;
	}

	public CreeperVariantConfig resolveVariant(boolean charged) {
		return charged ? chargedCreeper : creeper;
	}

	public Double resolveExplosionDestructionChance(
		Difficulty difficulty,
		boolean hardcore,
		CreeperVariantConfig variant
	) {
		Double baseChance = variant.explosionDestructionChance();
		if (baseChance == null) {
			return null;
		}
		if (difficulty == null) {
			return baseChance;
		}

		double adjusted = MobSystemUtil.resolveDifficultyAdjustedValue(
			difficulty,
			hardcore,
			baseChance,
			explosionDestructionDifficultyStep,
			0.0
		);
		return MobConfigJsonUtil.clamp(adjusted, 0.0, 1.0, baseChance);
	}

	private static JsonObject buildDefaults() {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(KEY_ENABLED, true);
		defaults.addProperty(KEY_GRIEF_POWER_MULTIPLIER, 0.5);
		defaults.addProperty(KEY_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP, 0.2);
		defaults.addProperty(KEY_CREEPER_SPAWN_WEIGHT, 95.0);
		defaults.addProperty(KEY_CHARGED_CREEPER_SPAWN_WEIGHT, 5.0);
		defaults.add(KEY_CREEPER, buildVariantDefaults(defaultCreeper()));
		defaults.add(KEY_CHARGED_CREEPER, buildVariantDefaults(defaultChargedCreeper()));
		return defaults;
	}

	private static CreeperVariantConfig defaultCreeper() {
		return new CreeperVariantConfig(
			new MobConfigJsonUtil.UniversalMobStats(12.0, 1.0, null, 0.25, 0.1, null, 7),
			3.0,
			0.4,
			32.0
		);
	}

	private static CreeperVariantConfig defaultChargedCreeper() {
		return new CreeperVariantConfig(
			new MobConfigJsonUtil.UniversalMobStats(12.0, 1.0, null, 0.3, 0.2, null, 7),
			5.0,
			0.6,
			28.0
		);
	}

	private static JsonObject buildVariantDefaults(CreeperVariantConfig defaults) {
		JsonObject node = MobConfigJsonUtil.buildUniversalStatDefaults(defaults.stats());
		node.addProperty(KEY_EXPLOSION_POWER, defaults.explosionPower());
		node.addProperty(KEY_EXPLOSION_DESTRUCTION_CHANCE, defaults.explosionDestructionChance());
		node.addProperty(KEY_FUSE_LENGTH, defaults.fuseLength());
		return node;
	}

	private static VariantLoadResult readVariant(JsonObject node) {
		boolean changed = false;

		MobConfigJsonUtil.UniversalMobStatsLoadResult statsResult = MobConfigJsonUtil.readUniversalStatOverrides(node);
		changed |= statsResult.changed();

		Double explosionPower = MobConfigJsonUtil.sanitizeOptionalNonNegative(MobConfigJsonUtil.readOptionalDouble(node, KEY_EXPLOSION_POWER));
		changed |= MobConfigJsonUtil.setNullableDouble(node, KEY_EXPLOSION_POWER, explosionPower);

		Double explosionDestructionChance = MobConfigJsonUtil.clampOptional(
			MobConfigJsonUtil.readOptionalDouble(node, KEY_EXPLOSION_DESTRUCTION_CHANCE),
			0.0,
			1.0
		);
		changed |= MobConfigJsonUtil.setNullableDouble(node, KEY_EXPLOSION_DESTRUCTION_CHANCE, explosionDestructionChance);

		Double fuseLength = MobConfigJsonUtil.sanitizeOptionalPositive(MobConfigJsonUtil.readOptionalDouble(node, KEY_FUSE_LENGTH));
		changed |= MobConfigJsonUtil.setNullableDouble(node, KEY_FUSE_LENGTH, fuseLength);

		return new VariantLoadResult(
			new CreeperVariantConfig(
				statsResult.stats(),
				explosionPower,
				explosionDestructionChance,
				fuseLength
			),
			changed
		);
	}

	private record VariantLoadResult(CreeperVariantConfig variant, boolean changed) {
	}

	public record CreeperVariantConfig(
		MobConfigJsonUtil.UniversalMobStats stats,
		Double explosionPower,
		Double explosionDestructionChance,
		Double fuseLength
	) {
	}
}
