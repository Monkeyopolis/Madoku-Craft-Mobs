package madoku.craft.mobs.system;

import java.util.ArrayList;
import java.util.UUID;

import madoku.craft.API.system.MadokuTickSystem;
import madoku.craft.mobs.MadokuCraftMobs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;

/**
 * Applies JSON-driven spider stats when entities are loaded.
 */
public final class SpiderMobSystem {
	private static final String LOG_SOURCE = "MOBS.Spider";
	private static SpiderMobConfig activeConfig;

	private SpiderMobSystem() {
	}

	public static void init() {
		reloadConfig();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> reloadConfig());

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof SpiderEntity spider)) {
				return;
			}
			if (spider.getType() != EntityType.SPIDER && spider.getType() != EntityType.CAVE_SPIDER) {
				return;
			}
			SpiderMobConfig config = activeConfig;
			if (config != null && config.enabled() && world instanceof ServerWorld serverWorld) {
				applyConfig(spider, config, serverWorld.getDifficulty());
			}
		});

		MadokuCraftMobs.LOGGER.info("Madoku Craft Mobs: spider system hooks registered.");
	}

	private static void reloadConfig() {
		activeConfig = SpiderMobConfig.load();
		if (activeConfig.enabled()) {
			MadokuCraftMobs.infoDebug(
				LOG_SOURCE,
				"Config loaded. spider(enabled={}, health={}, damage={}, difficultyStep={}), cave(enabled={}, health={}, damage={}, difficultyStep={}), spiderWeights(spider={}, cave={}, jockey={}), specialWeightStep={}.",
				activeConfig.spiderEnabled(),
				activeConfig.spider().stats().health(),
				activeConfig.spider().stats().damage(),
				activeConfig.spiderScaleDifficultyStep(),
				activeConfig.caveSpiderEnabled(),
				activeConfig.caveSpider().stats().health(),
				activeConfig.caveSpider().stats().damage(),
				activeConfig.caveSpiderScaleDifficultyStep(),
				activeConfig.spiderSpawnWeight(),
				activeConfig.caveSpiderSpawnWeight(),
				activeConfig.spiderJockeySpawnWeight(),
				MobSystemUtil.SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
			);
		} else {
			MadokuCraftMobs.infoDebug(LOG_SOURCE, "Spider system disabled in config.");
		}
	}

	public static void applySpawnOverrides(
		SpiderEntity spider,
		ServerWorldAccess world,
		LocalDifficulty difficulty,
		SpawnReason spawnReason,
		EntityData entityData
	) {
		SpiderMobConfig config = activeConfig;
		if (spider == null
			|| spider.getType() != EntityType.SPIDER
			|| world == null
			|| difficulty == null
			|| spawnReason == null
			|| spawnReason == SpawnReason.JOCKEY
			|| config == null
			|| !config.spiderEnabled()) {
			return;
		}

		clearExistingSkeletonRider(spider);

		Difficulty globalDifficulty = difficulty.getGlobalDifficulty();
		boolean hardcore = MobSystemUtil.isHardcoreWorld(world.toServerWorld());
		Random random = world.getRandom();
		MobSystemUtil.SpawnWeightPair caveAdjusted = MobSystemUtil.resolveDifficultyShiftedSpawnWeights(
			config.spiderSpawnWeight(),
			config.caveSpiderSpawnWeight(),
			globalDifficulty,
			hardcore,
			MobSystemUtil.SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		MobSystemUtil.SpawnWeightPair jockeyAdjusted = MobSystemUtil.resolveDifficultyShiftedSpawnWeights(
			caveAdjusted.regularWeight(),
			config.spiderJockeySpawnWeight(),
			globalDifficulty,
			hardcore,
			MobSystemUtil.SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		double spiderWeight = jockeyAdjusted.regularWeight();
		double caveSpiderWeight = caveAdjusted.specialWeight();
		double spiderJockeyWeight = jockeyAdjusted.specialWeight();
		SpawnOutcome outcome = rollSpawnOutcome(
			random,
			spiderWeight,
			caveSpiderWeight,
			spiderJockeyWeight
		);

		if (outcome == SpawnOutcome.CAVE_SPIDER) {
			queueCaveSpiderReplacement(spider, spawnReason);
		} else if (outcome == SpawnOutcome.SPIDER_JOCKEY) {
			spawnSpiderJockey(spider, world, difficulty);
		}
		MadokuCraftMobs.infoDebug(
			LOG_SOURCE,
			"Spawn result={}, reason={}, difficulty={}, hardcore={}, weights(spider={}, cave={}, jockey={}).",
			outcome.name(),
			spawnReason.name(),
			globalDifficulty.name(),
			hardcore,
			MobSystemUtil.roundToTwoDecimals(spiderWeight),
			MobSystemUtil.roundToTwoDecimals(caveSpiderWeight),
			MobSystemUtil.roundToTwoDecimals(spiderJockeyWeight)
		);
	}

	private static void queueCaveSpiderReplacement(SpiderEntity spider, SpawnReason spawnReason) {
		if (spider == null || spawnReason == null) {
			return;
		}
		UUID spiderId = spider.getUuid();
		MadokuTickSystem.enqueue(
			MadokuTickSystem.Phase.START,
			server -> runQueuedCaveSpiderReplacement(server, spiderId, spawnReason)
		);
	}

	private static void runQueuedCaveSpiderReplacement(MinecraftServer server, UUID spiderId, SpawnReason spawnReason) {
		if (server == null || spiderId == null || spawnReason == null) {
			return;
		}
		SpiderEntity spider = findQueuedSpider(server, spiderId);
		if (spider == null || spider.isRemoved()) {
			return;
		}
		if (!(spider.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		replaceLoadedSpiderWithCaveSpider(spider, serverWorld, spawnReason);
	}

	private static SpiderEntity findQueuedSpider(MinecraftServer server, UUID spiderId) {
		for (ServerWorld world : server.getWorlds()) {
			Entity entity = world.getEntity(spiderId);
			if (entity instanceof SpiderEntity spider && spider.getType() == EntityType.SPIDER) {
				return spider;
			}
		}
		return null;
	}

	private static void replaceLoadedSpiderWithCaveSpider(SpiderEntity spider, ServerWorld serverWorld, SpawnReason spawnReason) {
		if (spider == null || serverWorld == null || spider.isRemoved()) {
			return;
		}

		LocalDifficulty localDifficulty = serverWorld.getLocalDifficulty(spider.getBlockPos());
		spawnReplacementCaveSpider(spider, serverWorld, localDifficulty, spawnReason);
		spider.discard();
	}

	private static void applyConfig(SpiderEntity spider, SpiderMobConfig config, Difficulty difficulty) {
		boolean cave = spider.getType() == EntityType.CAVE_SPIDER;
		if (cave && !config.caveSpiderEnabled()) {
			return;
		}
		if (!cave && !config.spiderEnabled()) {
			return;
		}

		double oldMaxHealth = spider.getMaxHealth();
		SpiderMobConfig.SpiderVariantConfig variant = cave ? config.caveSpider() : config.spider();
		double scaleStep = cave ? config.caveSpiderScaleDifficultyStep() : config.spiderScaleDifficultyStep();
		MobConfigJsonUtil.UniversalMobStats stats = variant.stats();
		boolean hardcore = MobSystemUtil.isHardcoreWorld(spider.getEntityWorld());

		MobSystemUtil.applyUniversalStats(spider, stats, difficulty);
		if (stats.scale() != null) {
			MobSystemUtil.setBaseValue(
				spider,
				EntityAttributes.SCALE,
				resolveScaleByDifficulty(stats.scale(), scaleStep, difficulty, hardcore)
			);
		}
		MobSystemUtil.rescaleCurrentHealth(spider, oldMaxHealth);
	}

	private static double resolveScaleByDifficulty(double baseScale, double step, Difficulty difficulty, boolean hardcore) {
		return MobSystemUtil.resolveDifficultyAdjustedValue(difficulty, hardcore, baseScale, step, 0.05);
	}

	private static SpawnOutcome rollSpawnOutcome(
		Random random,
		double spiderWeight,
		double caveSpiderWeight,
		double spiderJockeyWeight
	) {
		double clampedSpider = Math.max(0.0, spiderWeight);
		double clampedCave = Math.max(0.0, caveSpiderWeight);
		double clampedJockey = Math.max(0.0, spiderJockeyWeight);
		double total = clampedSpider + clampedCave + clampedJockey;
		if (total <= 0.0) {
			return SpawnOutcome.SPIDER;
		}

		double roll = random.nextDouble() * total;
		if (roll < clampedSpider) {
			return SpawnOutcome.SPIDER;
		}

		roll -= clampedSpider;
		if (roll < clampedCave) {
			return SpawnOutcome.CAVE_SPIDER;
		}

		return SpawnOutcome.SPIDER_JOCKEY;
	}

	private static void clearExistingSkeletonRider(SpiderEntity spider) {
		for (Entity passenger : new ArrayList<>(spider.getPassengerList())) {
			if (passenger instanceof SkeletonEntity skeleton) {
				skeleton.stopRiding();
				skeleton.discard();
			}
		}
	}

	private static void spawnReplacementCaveSpider(
		SpiderEntity spider,
		ServerWorldAccess world,
		LocalDifficulty difficulty,
		SpawnReason spawnReason
	) {
		ServerWorld serverWorld = world.toServerWorld();
		CaveSpiderEntity caveSpider = EntityType.CAVE_SPIDER.create(serverWorld, spawnReason);
		if (caveSpider == null) {
			return;
		}

		caveSpider.refreshPositionAndAngles(
			spider.getX(),
			spider.getY(),
			spider.getZ(),
			spider.getYaw(),
			spider.getPitch()
		);
		caveSpider.initialize(world, difficulty, spawnReason, null);
		serverWorld.spawnEntityAndPassengers(caveSpider);
	}

	private static void spawnSpiderJockey(SpiderEntity spider, ServerWorldAccess world, LocalDifficulty difficulty) {
		ServerWorld serverWorld = world.toServerWorld();
		SkeletonEntity skeleton = EntityType.SKELETON.create(serverWorld, SpawnReason.JOCKEY);
		if (skeleton == null) {
			return;
		}

		skeleton.refreshPositionAndAngles(
			spider.getX(),
			spider.getY(),
			spider.getZ(),
			spider.getYaw(),
			0.0F
		);
		skeleton.initialize(world, difficulty, SpawnReason.JOCKEY, null);
		SkeletonMobSystem.ensureBowEquipped(skeleton);
		// The spider is still in initialize() and not added yet; let vanilla spawn this passenger with the spider.
		skeleton.startRiding(spider);
	}

	private enum SpawnOutcome {
		SPIDER,
		CAVE_SPIDER,
		SPIDER_JOCKEY
	}

}
