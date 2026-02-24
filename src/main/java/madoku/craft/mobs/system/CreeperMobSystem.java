package madoku.craft.mobs.system;

import madoku.craft.mobs.MadokuCraftMobs;
import madoku.craft.mobs.mixin.CreeperEntityAccessor;
import madoku.craft.mobs.mixin.CreeperEntityTrackedDataAccessor;
import net.minecraft.entity.Entity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

/**
 * Applies JSON-driven creeper stats and explosion behavior when entities are loaded.
 */
public final class CreeperMobSystem {
	private static final String LOG_SOURCE = "MOBS.Creeper";
	private static final double EXPLOSION_POWER_DIFFICULTY_STEP = 1.0;
	private static final double FUSE_LENGTH_DIFFICULTY_STEP = 2.0;
	private static CreeperMobConfig activeConfig;

	private CreeperMobSystem() {
	}

	public static void init() {
		reloadConfig();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> reloadConfig());

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof CreeperEntity creeper) || creeper.getType() != EntityType.CREEPER) {
				return;
			}
			CreeperMobConfig config = activeConfig;
			if (config != null && config.enabled()) {
				applyConfig(creeper, config);
			}
		});

		MadokuCraftMobs.LOGGER.info("Madoku Craft Mobs: creeper system hooks registered.");
	}

	public static void applyExplosionOverride(
		CreeperEntity creeper,
		ServerWorld world,
		double x,
		double y,
		double z,
		float vanillaPower,
		World.ExplosionSourceType vanillaSourceType
	) {
		CreeperMobConfig config = activeConfig;
		if (world == null) {
			return;
		}
		if (creeper == null || config == null || !config.enabled()) {
			world.createExplosion(creeper, x, y, z, vanillaPower, vanillaSourceType);
			return;
		}

		CreeperMobConfig.CreeperVariantConfig variant = config.resolveVariant(creeper.isCharged());
		boolean hardcore = MobSystemUtil.isHardcoreWorld(world);
		Double chanceValue = config.resolveExplosionDestructionChance(world.getDifficulty(), hardcore, variant);
		if (chanceValue == null) {
			world.createExplosion(creeper, x, y, z, vanillaPower, vanillaSourceType);
			return;
		}

		float basePower = variant.explosionPower() != null
			? (float) Math.max(0.0, variant.explosionPower())
			: vanillaPower;
		basePower = (float) resolveScaledExplosionPower(basePower, world.getDifficulty(), hardcore);
		double chance = chanceValue;
		boolean shouldDestroyBlocks = world.getRandom().nextDouble() < chance;
		float griefPower = resolveGriefOnlyExplosionPower(creeper, basePower);
		World.ExplosionSourceType sourceType = shouldDestroyBlocks
			? World.ExplosionSourceType.MOB
			: World.ExplosionSourceType.NONE;

		world.createExplosion(creeper, x, y, z, basePower, sourceType);
		MadokuCraftMobs.infoDebug(
			LOG_SOURCE,
			"Explosion override charged={}, difficulty={}, destroyBlocks={}, chance={}%, power={}, griefPower={}.",
			creeper.isCharged(),
			world.getDifficulty().name(),
			shouldDestroyBlocks,
			MobSystemUtil.roundToTwoDecimals(chance * 100.0),
			MobSystemUtil.roundToTwoDecimals(basePower),
			MobSystemUtil.roundToTwoDecimals(griefPower)
		);
	}

	public static float resolveGriefOnlyExplosionPower(Entity source, float explosionPower) {
		float clampedPower = Math.max(0.0f, explosionPower);
		if (!(source instanceof CreeperEntity)) {
			return clampedPower;
		}

		CreeperMobConfig config = activeConfig;
		if (config == null || !config.enabled()) {
			return clampedPower;
		}
		return (float) (clampedPower * config.griefPowerMultiplier());
	}

	public static float resolveFixedPlayerExplosionDamage(CreeperEntity creeper, float fallbackExplosionPower) {
		if (creeper == null) {
			return Math.max(0.0f, fallbackExplosionPower) * 5.0f;
		}

		double explosionPower = Math.max(0.0f, fallbackExplosionPower);
		boolean hardcore = creeper.getEntityWorld() != null && MobSystemUtil.isHardcoreWorld(creeper.getEntityWorld());
		CreeperMobConfig config = activeConfig;
		if (config != null && config.enabled()) {
			CreeperMobConfig.CreeperVariantConfig variant = config.resolveVariant(creeper.isCharged());
			if (variant.explosionPower() != null) {
				explosionPower = Math.max(0.0, variant.explosionPower());
			}
		}
		explosionPower = resolveScaledExplosionPower(explosionPower, creeper.getEntityWorld().getDifficulty(), hardcore);
		return (float) (explosionPower * 5.0);
	}

	public static void applySpawnOverrides(
		CreeperEntity creeper,
		ServerWorldAccess world,
		LocalDifficulty difficulty,
		SpawnReason spawnReason,
		EntityData entityData
	) {
		CreeperMobConfig config = activeConfig;
		if (creeper == null
			|| creeper.getType() != EntityType.CREEPER
			|| world == null
			|| difficulty == null
			|| spawnReason == null
			|| config == null
			|| !config.enabled()
			|| creeper.isCharged()) {
			return;
		}

		Random random = world.getRandom();
		Difficulty globalDifficulty = difficulty.getGlobalDifficulty();
		boolean hardcore = MobSystemUtil.isHardcoreWorld(world.toServerWorld());
		MobSystemUtil.SpawnWeightPair spawnWeights = MobSystemUtil.resolveDifficultyShiftedSpawnWeights(
			config.creeperSpawnWeight(),
			config.chargedCreeperSpawnWeight(),
			globalDifficulty,
			hardcore,
			MobSystemUtil.SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		double creeperWeight = spawnWeights.regularWeight();
		double chargedWeight = spawnWeights.specialWeight();
		boolean chargedSpawn = shouldSpawnChargedCreeper(random, creeperWeight, chargedWeight);
		if (chargedSpawn) {
			creeper.getDataTracker().set(CreeperEntityTrackedDataAccessor.madokuCraftMobs$getChargedTrackedData(), true);
		}
		MadokuCraftMobs.infoDebug(
			LOG_SOURCE,
			"Spawn result={}, reason={}, difficulty={}, hardcore={}, weights(creeper={}, charged={}).",
			chargedSpawn ? "CHARGED_CREEPER" : "CREEPER",
			spawnReason.name(),
			globalDifficulty.name(),
			hardcore,
			MobSystemUtil.roundToTwoDecimals(creeperWeight),
			MobSystemUtil.roundToTwoDecimals(chargedWeight)
		);
	}

	private static void reloadConfig() {
		activeConfig = CreeperMobConfig.load();
		if (activeConfig.enabled()) {
			MobConfigJsonUtil.UniversalMobStats creeperStats = activeConfig.creeper().stats();
			MobConfigJsonUtil.UniversalMobStats chargedStats = activeConfig.chargedCreeper().stats();
			MadokuCraftMobs.infoDebug(
				LOG_SOURCE,
				"Config loaded. creeper(health={}, power={}, griefChance={}%), charged(health={}, power={}, griefChance={}%), spawnWeights=({}/{}), griefPower={}%, difficultyStep={}%.",
				creeperStats.health(),
				activeConfig.creeper().explosionPower(),
				toPercentOrNull(activeConfig.creeper().explosionDestructionChance()),
				chargedStats.health(),
				activeConfig.chargedCreeper().explosionPower(),
				toPercentOrNull(activeConfig.chargedCreeper().explosionDestructionChance()),
				MobSystemUtil.roundToTwoDecimals(activeConfig.creeperSpawnWeight()),
				MobSystemUtil.roundToTwoDecimals(activeConfig.chargedCreeperSpawnWeight()),
				MobSystemUtil.roundToTwoDecimals(activeConfig.griefPowerMultiplier() * 100.0),
				MobSystemUtil.roundToTwoDecimals(activeConfig.explosionDestructionDifficultyStep() * 100.0)
			);
		} else {
			MadokuCraftMobs.infoDebug(LOG_SOURCE, "Creeper system disabled in config.");
		}
	}

	private static void applyConfig(CreeperEntity creeper, CreeperMobConfig config) {
		double oldMaxHealth = creeper.getMaxHealth();
		CreeperMobConfig.CreeperVariantConfig variant = config.resolveVariant(creeper.isCharged());
		MobConfigJsonUtil.UniversalMobStats stats = variant.stats();

		MobSystemUtil.applyUniversalStats(creeper, stats, creeper.getEntityWorld().getDifficulty());
		MobSystemUtil.rescaleCurrentHealth(creeper, oldMaxHealth);

		CreeperEntityAccessor accessor = (CreeperEntityAccessor) creeper;
		if (variant.fuseLength() != null) {
			boolean hardcore = MobSystemUtil.isHardcoreWorld(creeper.getEntityWorld());
			double scaledFuseTicks = resolveScaledFuseLength(
				variant.fuseLength(),
				creeper.getEntityWorld().getDifficulty(),
				hardcore
			);
			int fuseTicks = Math.max(1, (int) Math.round(scaledFuseTicks));
			accessor.madokuCraftMobs$setFuseTime(fuseTicks);
			if (accessor.madokuCraftMobs$getCurrentFuseTime() > fuseTicks) {
				accessor.madokuCraftMobs$setCurrentFuseTime(fuseTicks);
			}
			if (accessor.madokuCraftMobs$getLastFuseTime() > fuseTicks) {
				accessor.madokuCraftMobs$setLastFuseTime(fuseTicks);
			}
		}

		if (variant.explosionPower() != null) {
			boolean hardcore = MobSystemUtil.isHardcoreWorld(creeper.getEntityWorld());
			double scaledExplosionPower = resolveScaledExplosionPower(
				variant.explosionPower(),
				creeper.getEntityWorld().getDifficulty(),
				hardcore
			);
			accessor.madokuCraftMobs$setExplosionRadius(Math.max(0, (int) Math.round(scaledExplosionPower)));
		}
	}

	private static double resolveScaledExplosionPower(double baseExplosionPower, Difficulty difficulty, boolean hardcore) {
		return MobSystemUtil.resolveDifficultyAdjustedValue(
			difficulty,
			hardcore,
			Math.max(0.0, baseExplosionPower),
			EXPLOSION_POWER_DIFFICULTY_STEP,
			0.0
		);
	}

	private static double resolveScaledFuseLength(double baseFuseLength, Difficulty difficulty, boolean hardcore) {
		return MobSystemUtil.resolveDifficultyAdjustedInverseValue(
			difficulty,
			hardcore,
			Math.max(1.0, baseFuseLength),
			FUSE_LENGTH_DIFFICULTY_STEP,
			1.0
		);
	}

	private static boolean shouldSpawnChargedCreeper(Random random, double creeperWeight, double chargedWeight) {
		if (random == null) {
			return false;
		}

		double clampedRegular = Math.max(0.0, creeperWeight);
		double clampedCharged = Math.max(0.0, chargedWeight);
		double total = clampedRegular + clampedCharged;
		if (total <= 0.0) {
			return false;
		}

		double roll = random.nextDouble() * total;
		return roll >= clampedRegular;
	}

	private static Double toPercentOrNull(Double value) {
		return value == null ? null : MobSystemUtil.roundToTwoDecimals(value * 100.0);
	}
}
