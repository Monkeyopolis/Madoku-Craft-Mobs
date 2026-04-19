package madoku.craft.mobs.difficulty.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.config.DynamicJsonSystem;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.mobs.mixin.MobExperienceAccessor;
import madoku.craft.mobs.mob.system.MadokuMob;
import madoku.craft.scheduler.MadokuScheduler;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class MadokuDifficulty {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuDifficulty.class);

	private static final String DIFFICULTY_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-difficulty";
	private static final String DIFFICULTY_CONFIG_SETTINGS_FILE_NAME = "madoku-difficulty";
	private static final String DIFFICULTY_CONFIG_RULES_FOLDER_NAME = "madoku-difficulty";
	private static final String DIFFICULTY_CONFIG_MOB_SCALING_FOLDER_NAME = "madoku-scaling";
	private static final String BIOME_RULES_FOLDER_NAME = "biome-rules";
	private static final String STRUCTURE_RULES_FOLDER_NAME = "structure-rules";
	private static final String TIME_RULES_FOLDER_NAME = "time-rules";
	private static final String TASK_TYPE_TIME_TICK = "difficulty_time_tick";
	private static final String TIME_SCHEDULER_OWNER_ID = "madoku-difficulty-time";
	private static final int TIME_SCHEDULER_INTERVAL_TICKS = 20;
	private static final double HEALTH_SCALE_STEP_PERCENT = 0.05D;
	private static final double MOVEMENT_SPEED_SCALE_STEP_PERCENT = 0.02D;
	private static final double DAMAGE_SCALE_STEP_PERCENT = 0.05D;
	private static final double RANGED_DAMAGE_SCALE_STEP_PERCENT = 0.05D;
	private static final double ATTACK_ACCURACY_SCALE_STEP = 0.02D;
	private static final double SCALE_SCALE_STEP = 0.02D;
	private static final double CREEPER_EXPLOSION_POWER_SCALE_STEP = 0.2D;
	private static final double ARMOR_SCALE_STEP = 0.2D;
	private static final double KNOCKBACK_RESISTANCE_SCALE_STEP = 0.02D;
	private static final double EXPERIENCE_DROP_SCALE_STEP_PERCENT = 0.10D;
	private static final double SCALING_ROUND_STEP = 0.05D;

	private static final long TICKS_PER_DAY = 24000L;

	private static volatile Snapshot snapshot = Snapshot.disabled();
	private static volatile String timeSchedulerId = "";
	private static volatile boolean timeTaskScheduled = false;
	private static volatile long cachedTimeDayCount = Long.MIN_VALUE;
	private static volatile int cachedTimeAdjustment = 0;

	private MadokuDifficulty() {
	}

	public static void initialize() {
		loadConfig();
		MadokuScheduler.registerTaskHandler(TASK_TYPE_TIME_TICK, MadokuDifficulty::runTimeTask);
	}

	public static void onServerStarted(MinecraftServer server) {
		timeSchedulerId = "";
		timeTaskScheduled = false;
		cachedTimeDayCount = Long.MIN_VALUE;
		cachedTimeAdjustment = 0;
		refreshCachedTimeAdjustment(server, snapshot);
		timeSchedulerId = MadokuScheduler.createOrGetScheduler(MadokuScheduler.SchedulerOwner.global(TIME_SCHEDULER_OWNER_ID));
		MadokuScheduler.clearQueuedRequests(timeSchedulerId);
		requestTimeProcessing(server, 1L);
	}

	public static void onServerTick(MinecraftServer server) {
		requestTimeProcessing(server, TIME_SCHEDULER_INTERVAL_TICKS);
	}

	public static void onServerStopped() {
		timeSchedulerId = "";
		timeTaskScheduled = false;
		cachedTimeDayCount = Long.MIN_VALUE;
		cachedTimeAdjustment = 0;
	}

	public static boolean isEnabled() {
		return snapshot.enabled();
	}

	public static int resolveHudDifficultyLevel(ServerLevel world, net.minecraft.core.BlockPos pos) {
		if (world == null || pos == null) {
			return 1;
		}
		Snapshot config = snapshot;
		if (!config.enabled()) {
			return 1;
		}
		Identifier biomeId = resolveBiomeId(world, pos);
		int biomeAdjustment = config.biomeAdjustment(biomeId);
		int timeAdjustment = resolveTimeAdjustment(world, config);
		StructureContext structureContext = config.structuresEnabled()
			? resolveStructureContext(world, pos, config.structureAdjustments(), config.defaultUnknownAdjustment())
			: StructureContext.NONE;
		int totalAdjustment = Math.max(0, biomeAdjustment + structureContext.adjustment() + timeAdjustment);
		return Math.max(1, 1 + totalAdjustment);
	}

	public static int resolveHudDifficultyLevel(ServerPlayer player) {
		if (player == null) {
			return 1;
		}
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return 1;
		}
		return resolveHudDifficultyLevel(serverLevel, player.blockPosition());
	}

	public static int resolveCurrentTimeAdjustment(ServerLevel world) {
		Snapshot config = snapshot;
		if (world == null || !config.enabled() || !config.timeEnabled()) {
			return 0;
		}
		if (cachedTimeDayCount == Long.MIN_VALUE) {
			long dayCount = resolveDifficultyDayCount(world);
			cachedTimeDayCount = dayCount;
			cachedTimeAdjustment = config.timeAdjustment(dayCount);
		}
		return Math.max(0, cachedTimeAdjustment);
	}

	public static long resolveDifficultyDayCount(ServerLevel world) {
		if (world == null) {
			return 0L;
		}
		MinecraftServer server = world.getServer();
		if (server != null) {
			return resolveDifficultyDayCount(server);
		}
		return Math.floorDiv(world.getDayTime(), TICKS_PER_DAY);
	}

	public static void applySpawnScaling(Mob mob, ServerLevelAccessor worldAccess) {
		if (mob == null || worldAccess == null || !(mob instanceof DifficultyScaledMob scaledMob)) {
			return;
		}

		scaledMob.madokuCraft$setSpawnDifficultyAdjustment(0);
		Snapshot config = snapshot;
		if (!config.enabled()) {
			return;
		}

		ServerLevel world = worldAccess.getLevel();
		if (world == null || world.isClientSide()) {
			return;
		}

		Identifier biomeId = resolveBiomeId(world, mob.blockPosition());
		int biomeAdjustment = config.biomeAdjustment(biomeId);
		int timeAdjustment = resolveTimeAdjustment(world, config);
		StructureContext structureContext = config.structuresEnabled()
			? resolveStructureContext(world, mob.blockPosition(), config.structureAdjustments(), config.defaultUnknownAdjustment())
			: StructureContext.NONE;
		int structureAdjustment = structureContext.adjustment();
		int baseAdjustment = 1;
		int totalAdjustment = Math.max(0, baseAdjustment + biomeAdjustment + structureAdjustment + timeAdjustment);
		scaledMob.madokuCraft$setSpawnDifficultyAdjustment(totalAdjustment);
		if (totalAdjustment <= 0 || isHealthOnlyBoss(mob)) {
			return;
		}

		boolean fullStatScaling = mob instanceof Monster;
		ResolvedIncrements resolvedIncrements = fullStatScaling
			? config.resolveIncrements(mob)
			: new ResolvedIncrements(config.increments(), "global_all_mobs");
		ScalingApplication applied = applyScalingAdjustments(mob, resolvedIncrements, totalAdjustment, fullStatScaling);
		if (applied == null) {
			return;
		}

		emitSpawnScaled(
			mob,
			biomeId,
			structureContext,
			baseAdjustment,
			biomeAdjustment,
			structureAdjustment,
			timeAdjustment,
			totalAdjustment,
			resolvedIncrements.sourceKey(),
			applied.increments(),
			applied.healthAddition(),
			applied.movementSpeedAddition(),
			applied.scaleAddition(),
			applied.armorAddition(),
			applied.armorBaseBefore(),
			applied.armorBaseAfter(),
			applied.damageAddition(),
			applied.knockbackResistanceAddition(),
			applied.experienceBaseBefore(),
			applied.experienceBaseAfter(),
			applied.experienceDropAddition()
		);
	}

	public static void applySpawnScalingIfUnscaled(Mob mob, ServerLevelAccessor worldAccess) {
		if (!(mob instanceof DifficultyScaledMob scaledMob) || scaledMob.madokuCraft$getSpawnDifficultyAdjustment() > 0) {
			return;
		}
		applySpawnScaling(mob, worldAccess);
	}

	public static void reapplySpawnScalingFromStoredAdjustment(Mob mob) {
		if (mob == null || !(mob instanceof DifficultyScaledMob scaledMob)) {
			return;
		}
		Snapshot config = snapshot;
		if (!config.enabled()) {
			return;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0 || isHealthOnlyBoss(mob)) {
			return;
		}

		boolean fullStatScaling = mob instanceof Monster;
		ResolvedIncrements resolvedIncrements = fullStatScaling
			? config.resolveIncrements(mob)
			: new ResolvedIncrements(config.increments(), "global_all_mobs");
		applyScalingAdjustments(mob, resolvedIncrements, totalAdjustment, fullStatScaling);
	}

	public static double resolveCreeperExplosionPowerScaling(Mob mob) {
		if (mob == null || mob.getType() != EntityType.CREEPER || !snapshot.enabled()) {
			return 0.0D;
		}
		if (!(mob instanceof DifficultyScaledMob scaledMob)) {
			return 0.0D;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0) {
			return 0.0D;
		}
		StatIncrements increments = snapshot.resolveIncrements(mob).increments();
		double powerAddition = Math.max(0.0D, increments.explosionPower()) * CREEPER_EXPLOSION_POWER_SCALE_STEP * totalAdjustment;
		return roundToNearestStep(powerAddition, SCALING_ROUND_STEP);
	}

	public static double resolveMobRangedDamageScaling(Mob mob, double baseDamage) {
		double sanitizedBase = Math.max(0.0D, baseDamage);
		if (mob == null || !snapshot.enabled()) {
			return sanitizedBase;
		}
		if (!(mob instanceof DifficultyScaledMob scaledMob)) {
			return sanitizedBase;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0) {
			return sanitizedBase;
		}
		StatIncrements increments = snapshot.resolveIncrements(mob).increments();
		double addition = sanitizedBase
			* Math.max(0.0D, increments.rangedDamage())
			* RANGED_DAMAGE_SCALE_STEP_PERCENT
			* totalAdjustment;
		return Math.max(0.0D, sanitizedBase + roundToNearestStep(addition, SCALING_ROUND_STEP));
	}

	public static double resolveMobAttackAccuracyScaling(Mob mob, double baseAccuracy) {
		double sanitizedBase = Mth.clamp(baseAccuracy, 0.0D, 1.0D);
		if (mob == null || !snapshot.enabled()) {
			return sanitizedBase;
		}
		if (!(mob instanceof DifficultyScaledMob scaledMob)) {
			return sanitizedBase;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0) {
			return sanitizedBase;
		}
		StatIncrements increments = snapshot.resolveIncrements(mob).increments();
		double addition = Math.max(0.0D, increments.attackAccuracy()) * ATTACK_ACCURACY_SCALE_STEP * totalAdjustment;
		return Mth.clamp(sanitizedBase + addition, 0.0D, 1.0D);
	}

	private static void loadConfig() {
		try {
			Path rootDirectory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(DIFFICULTY_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, DIFFICULTY_CONFIG_SETTINGS_FILE_NAME);
				JsonObject settingsRoot = StaticJsonSystem.ensureManagedFile(
					settingsFile,
					MadokuDifficultyConfig.buildSettingsDefaults()
				);
				JsonObject settingsScaling = readObject(settingsRoot, MadokuDifficultyConfig.FIELD_DIFFICULTY_SCALING);
				double defaultHealthIncrement = readFiniteDouble(
					settingsScaling,
					MadokuDifficultyConfig.FIELD_HEALTH,
					MadokuDifficultyConfig.DEFAULT_HEALTH_INCREMENT
				);
				double defaultMovementSpeedIncrement = readFiniteDouble(
					settingsScaling,
					MadokuDifficultyConfig.FIELD_MOVEMENT_SPEED,
					MadokuDifficultyConfig.DEFAULT_MOVEMENT_SPEED_INCREMENT
				);
				double defaultArmorIncrement = readFiniteDouble(
					settingsScaling,
					MadokuDifficultyConfig.FIELD_ARMOR,
					MadokuDifficultyConfig.DEFAULT_ARMOR_INCREMENT
				);
				double defaultDamageIncrement = readFiniteDouble(
					settingsScaling,
					MadokuDifficultyConfig.FIELD_DAMAGE,
					MadokuDifficultyConfig.DEFAULT_DAMAGE_INCREMENT
				);
					double defaultKnockbackResistanceIncrement = readFiniteDouble(
						settingsScaling,
						MadokuDifficultyConfig.FIELD_KNOCKBACK_RESISTANCE,
						MadokuDifficultyConfig.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT
					);
					double defaultExperienceDropIncrement = readFiniteDouble(
						settingsScaling,
						MadokuDifficultyConfig.FIELD_EXPERIENCE_DROP,
						MadokuDifficultyConfig.DEFAULT_EXPERIENCE_DROP_INCREMENT
					);

			Path rulesDirectory = rootDirectory.resolve(DIFFICULTY_CONFIG_RULES_FOLDER_NAME);
			Path biomeDirectory = rulesDirectory.resolve(BIOME_RULES_FOLDER_NAME);
			Path structureDirectory = rulesDirectory.resolve(STRUCTURE_RULES_FOLDER_NAME);
			Path timeDirectory = rulesDirectory.resolve(TIME_RULES_FOLDER_NAME);

			Map<String, JsonObject> normalizedBiomes = DynamicJsonSystem.ensureManagedFolder(
				biomeDirectory,
				MadokuDifficultyConfig.buildDefaultBiomeFileDefaults(),
				ignored -> MadokuDifficultyConfig.buildBiomeRuleDefaults(0, List.of()),
				MadokuDifficulty::isSupportedBiomeRuleFile,
				null
			);
			Map<String, JsonObject> normalizedStructures = DynamicJsonSystem.ensureManagedFolder(
				structureDirectory,
				MadokuDifficultyConfig.buildDefaultStructureFileDefaults(),
				ignored -> MadokuDifficultyConfig.buildStructureRuleDefaults(0, List.of()),
				MadokuDifficulty::isSupportedStructureRuleFile,
				null
			);
			Map<String, JsonObject> normalizedTime = DynamicJsonSystem.ensureManagedFolder(
				timeDirectory,
				MadokuDifficultyConfig.buildDefaultTimeFileDefaults(),
				ignored -> MadokuDifficultyConfig.buildTimeRuleDefaults(0, MadokuDifficultyConfig.TIME_UNBOUNDED_MAX_DAY, 0),
				MadokuDifficulty::isSupportedTimeRuleFile,
				null
			);
				Map<String, JsonObject> normalizedMobScaling = DynamicJsonSystem.ensureManagedFolder(
					rootDirectory.resolve(DIFFICULTY_CONFIG_MOB_SCALING_FOLDER_NAME),
						MadokuDifficultyConfig.buildDefaultMobScalingFileDefaults(
							defaultHealthIncrement,
							defaultMovementSpeedIncrement,
							defaultArmorIncrement,
							defaultDamageIncrement,
							defaultKnockbackResistanceIncrement,
							defaultExperienceDropIncrement
						),
					MadokuDifficultyConfig::buildDynamicMobScalingDefaults,
					MadokuDifficulty::isSupportedMobScalingFile,
					null
				);

			snapshot = buildSnapshot(settingsRoot, normalizedBiomes, normalizedStructures, normalizedTime, normalizedMobScaling);
			emitConfigLoaded();
		} catch (IOException | RuntimeException exception) {
			snapshot = Snapshot.disabled();
			LOGGER.error("Failed to load MadokuDifficulty config; disabling difficulty scaling.", exception);
		}
	}

	private static Snapshot buildSnapshot(
		JsonObject settingsRoot,
		Map<String, JsonObject> biomeRulesByFile,
		Map<String, JsonObject> structureRulesByFile,
		Map<String, JsonObject> timeRulesByFile,
		Map<String, JsonObject> mobScalingByFile
	) {
		boolean enabled = readBoolean(settingsRoot, MadokuDifficultyConfig.FIELD_ENABLED, true);
		boolean biomesEnabled = readBoolean(settingsRoot, MadokuDifficultyConfig.FIELD_BIOMES_ENABLED, true);
		boolean structuresEnabled = readBoolean(settingsRoot, MadokuDifficultyConfig.FIELD_STRUCTURES_ENABLED, true);
		boolean timeEnabled = readBoolean(settingsRoot, MadokuDifficultyConfig.FIELD_TIME_ENABLED, true);
		int defaultUnknownAdjustment = Math.max(
			0,
			readInt(settingsRoot, MadokuDifficultyConfig.FIELD_DEFAULT_UNKNOWN_ADJUSTMENT, MadokuDifficultyConfig.DEFAULT_UNKNOWN_ADJUSTMENT)
		);

		JsonObject scaling = readObject(settingsRoot, MadokuDifficultyConfig.FIELD_DIFFICULTY_SCALING);
			StatIncrements increments = new StatIncrements(
				readFiniteDouble(scaling, MadokuDifficultyConfig.FIELD_HEALTH, MadokuDifficultyConfig.DEFAULT_HEALTH_INCREMENT),
				readFiniteDouble(scaling, MadokuDifficultyConfig.FIELD_MOVEMENT_SPEED, MadokuDifficultyConfig.DEFAULT_MOVEMENT_SPEED_INCREMENT),
				readFiniteDouble(scaling, MadokuDifficultyConfig.FIELD_SCALE, MadokuDifficultyConfig.DEFAULT_SCALE_INCREMENT),
				readFiniteDouble(scaling, MadokuDifficultyConfig.FIELD_ARMOR, MadokuDifficultyConfig.DEFAULT_ARMOR_INCREMENT),
				0.0D,
				readFiniteDouble(scaling, MadokuDifficultyConfig.FIELD_KNOCKBACK_RESISTANCE, MadokuDifficultyConfig.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT),
				readFiniteDouble(scaling, MadokuDifficultyConfig.FIELD_EXPERIENCE_DROP, MadokuDifficultyConfig.DEFAULT_EXPERIENCE_DROP_INCREMENT),
				0.0D,
				0.0D,
				0.0D
			);

		Map<Identifier, Integer> biomeAdjustments = parseGroupedIdentifierAdjustments(
			biomeRulesByFile,
			MadokuDifficultyConfig.FIELD_BIOME_LIST,
			defaultUnknownAdjustment
		);
		Map<Identifier, Integer> structureAdjustments = parseGroupedIdentifierAdjustments(
			structureRulesByFile,
			MadokuDifficultyConfig.FIELD_STRUCTURE_LIST,
			defaultUnknownAdjustment
		);
		TimeScaling timeScaling = parseTimeScaling(timeRulesByFile, defaultUnknownAdjustment);
		Map<String, StatIncrements> mobScalingIncrements = parseMobScaling(mobScalingByFile, increments);

		return new Snapshot(
			enabled,
			increments,
			defaultUnknownAdjustment,
			biomesEnabled,
			Map.copyOf(biomeAdjustments),
			structuresEnabled,
			Map.copyOf(structureAdjustments),
			timeEnabled,
			timeScaling,
			Map.copyOf(mobScalingIncrements)
		);
	}

	private static Map<Identifier, Integer> parseGroupedIdentifierAdjustments(
		Map<String, JsonObject> rulesByFile,
		String listField,
		int defaultUnknownAdjustment
	) {
		Map<Identifier, Integer> resolved = new LinkedHashMap<>();
		for (JsonObject root : rulesByFile.values()) {
			if (root == null) {
				continue;
			}

			int adjustment = Math.max(0, readInt(root, MadokuDifficultyConfig.FIELD_ADJUSTMENT, defaultUnknownAdjustment));
			Set<Identifier> ids = parseIdentifierList(root.get(listField));
			for (Identifier id : ids) {
				resolved.merge(id, adjustment, Math::max);
			}
		}
		return resolved;
	}

	private static Set<Identifier> parseIdentifierList(JsonElement source) {
		Set<Identifier> parsed = new LinkedHashSet<>();
		if (!(source instanceof JsonArray array)) {
			return parsed;
		}
		for (JsonElement entry : array) {
			if (entry == null || !entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
				continue;
			}
			Identifier identifier = normalizeIdentifier(entry.getAsString());
			if (identifier != null) {
				parsed.add(identifier);
			}
		}
		return parsed;
	}

	private static TimeScaling parseTimeScaling(Map<String, JsonObject> timeRulesByFile, int defaultUnknownAdjustment) {
		List<TimeTier> tiers = new ArrayList<>();
		for (JsonObject root : timeRulesByFile.values()) {
			if (root == null) {
				continue;
			}
			int adjustment = Math.max(0, readInt(root, MadokuDifficultyConfig.FIELD_ADJUSTMENT, defaultUnknownAdjustment));
			int minDay = Math.max(0, readInt(root, MadokuDifficultyConfig.FIELD_MIN_DAY, 0));
			int configuredMaxDay = readInt(root, MadokuDifficultyConfig.FIELD_MAX_DAY, MadokuDifficultyConfig.TIME_UNBOUNDED_MAX_DAY);
			int maxDay = configuredMaxDay < 0 ? Integer.MAX_VALUE : Math.max(minDay, configuredMaxDay);
			tiers.add(new TimeTier(minDay, maxDay, adjustment));
		}

		if (tiers.isEmpty()) {
			for (MadokuDifficultyConfig.TimeTierDefinition definition : MadokuDifficultyConfig.defaultTimeTiers()) {
				int maxDay = definition.maxDay() < 0 ? Integer.MAX_VALUE : Math.max(definition.minDay(), definition.maxDay());
				tiers.add(new TimeTier(Math.max(0, definition.minDay()), maxDay, Math.max(0, definition.adjustment())));
			}
		}

		tiers.sort(Comparator.comparingInt(TimeTier::minDay));
		return new TimeScaling(List.copyOf(tiers));
	}

	private static Map<String, StatIncrements> parseMobScaling(
		Map<String, JsonObject> mobScalingByFile,
		StatIncrements fallbackIncrements
	) {
		Map<String, StatIncrements> resolved = new LinkedHashMap<>();
		if (mobScalingByFile == null || mobScalingByFile.isEmpty()) {
			return resolved;
		}
		for (Map.Entry<String, JsonObject> entry : mobScalingByFile.entrySet()) {
			String fileKey = normalizeFileKey(entry.getKey());
			JsonObject root = entry.getValue();
			if (fileKey.isBlank() || root == null || !readBoolean(root, MadokuDifficultyConfig.FIELD_ENABLED, true)) {
				continue;
			}

			StatIncrements increments = parseStatIncrementsFromMobScalingRoot(root, fallbackIncrements);
			resolved.put(fileKey, increments);

			Identifier configuredMobId = normalizeIdentifier(readString(root, MadokuDifficultyConfig.FIELD_MOB_ID, ""));
			if (configuredMobId != null) {
				for (String alias : resolveMobScalingFileKeys(configuredMobId)) {
					if (!alias.isBlank()) {
						resolved.put(alias, increments);
					}
				}
			}
		}
		return resolved;
	}

	private static StatIncrements parseStatIncrementsFromMobScalingRoot(JsonObject root, StatIncrements fallbackIncrements) {
		return new StatIncrements(
			readFiniteDouble(root, MadokuDifficultyConfig.FIELD_HEALTH, fallbackIncrements.health()),
			readFiniteDouble(root, MadokuDifficultyConfig.FIELD_MOVEMENT_SPEED, fallbackIncrements.movementSpeed()),
			readFiniteDouble(root, MadokuDifficultyConfig.FIELD_SCALE, fallbackIncrements.scale()),
			readFiniteDouble(root, MadokuDifficultyConfig.FIELD_ARMOR, fallbackIncrements.armor()),
			readFiniteDouble(root, MadokuDifficultyConfig.FIELD_DAMAGE, fallbackIncrements.damage()),
			readFiniteDouble(root, MadokuDifficultyConfig.FIELD_KNOCKBACK_RESISTANCE, fallbackIncrements.knockbackResistance()),
			readFiniteDouble(root, MadokuDifficultyConfig.FIELD_EXPERIENCE_DROP, fallbackIncrements.experienceDrop()),
			readFiniteDouble(root, MadokuDifficultyConfig.FIELD_RANGED_DAMAGE, fallbackIncrements.rangedDamage()),
			readFiniteDouble(root, MadokuDifficultyConfig.FIELD_ATTACK_ACCURACY, fallbackIncrements.attackAccuracy()),
			readFiniteDouble(root, MadokuDifficultyConfig.FIELD_EXPLOSION_POWER, fallbackIncrements.explosionPower())
		);
	}

	private static boolean isSupportedBiomeRuleFile(String fileKey, JsonObject sourceRoot) {
		return isRuleFileWithArrayField(sourceRoot, MadokuDifficultyConfig.FIELD_BIOME_LIST);
	}

	private static boolean isSupportedStructureRuleFile(String fileKey, JsonObject sourceRoot) {
		return isRuleFileWithArrayField(sourceRoot, MadokuDifficultyConfig.FIELD_STRUCTURE_LIST);
	}

	private static boolean isSupportedTimeRuleFile(String fileKey, JsonObject sourceRoot) {
		return sourceRoot != null;
	}

	private static boolean isSupportedMobScalingFile(String fileKey, JsonObject sourceRoot) {
		return sourceRoot != null;
	}

	private static boolean isRuleFileWithArrayField(JsonObject sourceRoot, String fieldName) {
		if (sourceRoot == null) {
			return false;
		}
		JsonElement field = sourceRoot.get(fieldName);
		return field != null && field.isJsonArray();
	}

	private static Identifier resolveBiomeId(ServerLevel world, net.minecraft.core.BlockPos pos) {
		try {
			Holder<Biome> biomeEntry = world.getBiome(pos);
			return biomeEntry.unwrapKey()
				.map(ResourceKey::identifier)
				.orElseGet(() -> {
					Registry<Biome> biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
					return biomeRegistry.getKey(biomeEntry.value());
				});
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static int resolveTimeAdjustment(ServerLevel world, Snapshot config) {
		if (world == null || !config.timeEnabled() || !config.enabled()) {
			return 0;
		}
		return resolveCurrentTimeAdjustment(world);
	}

	private static long resolveDifficultyDayCount(MinecraftServer server) {
		if (server == null) {
			return 0L;
		}
		if (MadokuTime.isEnabled()) {
			return Math.floorDiv(MadokuTime.getCurrentAbsoluteDayTime(), TICKS_PER_DAY);
		}
		ServerLevel overworld = server.overworld();
		if (overworld != null) {
			return Math.floorDiv(overworld.getDayTime(), TICKS_PER_DAY);
		}
		return 0L;
	}

	private static void runTimeTask(MinecraftServer server, MadokuScheduler.TaskContext context, JsonObject payload) {
		if (context == null) {
			return;
		}
		timeSchedulerId = context.getSchedulerId();
		timeTaskScheduled = false;

		Snapshot config = snapshot;
		if (!config.enabled() || !config.timeEnabled()) {
			cachedTimeDayCount = Long.MIN_VALUE;
			cachedTimeAdjustment = 0;
			return;
		}
		refreshCachedTimeAdjustment(server, config);
		requestTimeProcessing(server, TIME_SCHEDULER_INTERVAL_TICKS);
	}

	private static void refreshCachedTimeAdjustment(MinecraftServer server, Snapshot config) {
		if (server == null || config == null || !config.enabled() || !config.timeEnabled()) {
			cachedTimeDayCount = Long.MIN_VALUE;
			cachedTimeAdjustment = 0;
			return;
		}
		long dayCount = resolveDifficultyDayCount(server);
		cachedTimeDayCount = dayCount;
		cachedTimeAdjustment = config.timeAdjustment(dayCount);
	}

	private static void requestTimeProcessing(MinecraftServer server, long delay) {
		Snapshot config = snapshot;
		if (server == null || !config.enabled() || !config.timeEnabled() || timeTaskScheduled) {
			return;
		}

		String schedulerId = ensureTimeSchedulerExists();
		if (enqueueTimeTask(schedulerId, delay)) {
			timeTaskScheduled = true;
			return;
		}

		timeSchedulerId = MadokuScheduler.createScheduler(MadokuScheduler.SchedulerOwner.global(TIME_SCHEDULER_OWNER_ID));
		if (enqueueTimeTask(timeSchedulerId, delay)) {
			timeTaskScheduled = true;
			return;
		}
		LOGGER.error("Failed to enqueue MadokuDifficulty time scheduler task.");
	}

	private static String ensureTimeSchedulerExists() {
		if (timeSchedulerId == null || timeSchedulerId.isBlank()) {
			timeSchedulerId = MadokuScheduler.createOrGetScheduler(MadokuScheduler.SchedulerOwner.global(TIME_SCHEDULER_OWNER_ID));
		}
		return timeSchedulerId;
	}

	private static boolean enqueueTimeTask(String schedulerId, long delay) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}
		MadokuScheduler.EnqueueStatus status = MadokuScheduler.enqueue(
			schedulerId,
			Math.max(0L, delay),
			TASK_TYPE_TIME_TICK,
			new JsonObject(),
			MadokuScheduler.TickDomain.GAMEPLAY
		);
		return status == MadokuScheduler.EnqueueStatus.ACCEPTED
			|| status == MadokuScheduler.EnqueueStatus.QUEUE_FULL;
	}

	private static boolean isHealthOnlyBoss(Mob mob) {
		if (mob == null) {
			return false;
		}
		EntityType<?> type = mob.getType();
		return type == EntityType.ENDER_DRAGON
			|| type == EntityType.ELDER_GUARDIAN
			|| type == EntityType.WITHER
			|| type == EntityType.WARDEN;
	}

	private static StructureContext resolveStructureContext(
		ServerLevel world,
		net.minecraft.core.BlockPos pos,
		Map<Identifier, Integer> configuredAdjustments,
		int defaultUnknownAdjustment
	) {
		if (world == null || pos == null) {
			return StructureContext.NONE;
		}

		if (!configuredAdjustments.isEmpty()) {
			Predicate<Holder<Structure>> configuredPredicate = entry -> entry.unwrapKey()
				.map(ResourceKey::identifier)
				.map(configuredAdjustments::containsKey)
				.orElse(false);
			StructureStart configuredStart = findStructureContaining(world, pos, configuredPredicate);
			if (isValidStructureStart(configuredStart)) {
				Identifier structureId = resolveStructureId(world, configuredStart);
				return structureContextFromId(structureId, configuredAdjustments, defaultUnknownAdjustment);
			}
		}

		StructureStart start = findStructureContaining(world, pos, entry -> true);
		if (!isValidStructureStart(start)) {
			return StructureContext.NONE;
		}

		Identifier structureId = resolveStructureId(world, start);
		return structureContextFromId(structureId, configuredAdjustments, defaultUnknownAdjustment);
	}

	private static StructureStart findStructureContaining(
		ServerLevel world,
		net.minecraft.core.BlockPos pos,
		Predicate<Holder<Structure>> predicate
	) {
		try {
			return world.structureManager().getStructureWithPieceAt(pos, predicate);
		} catch (RuntimeException exception) {
			return StructureStart.INVALID_START;
		}
	}

	private static boolean isValidStructureStart(StructureStart start) {
		return start != null && start != StructureStart.INVALID_START && start.isValid();
	}

	private static Identifier resolveStructureId(ServerLevel world, StructureStart start) {
		if (world == null || start == null) {
			return null;
		}
		Registry<Structure> structureRegistry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		return structureRegistry.getKey(start.getStructure());
	}

	private static StructureContext structureContextFromId(
		Identifier structureId,
		Map<Identifier, Integer> configuredAdjustments,
		int defaultUnknownAdjustment
	) {
		if (structureId == null) {
			return new StructureContext(null, defaultUnknownAdjustment);
		}
		return new StructureContext(
			structureId,
			configuredAdjustments.getOrDefault(structureId, defaultUnknownAdjustment)
		);
	}

	private static boolean addAttribute(Mob mob, Holder<Attribute> attribute, double amount) {
		if (mob == null || attribute == null || !Double.isFinite(amount) || amount == 0.0D) {
			return false;
		}
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null) {
			return false;
		}

		double newBase = instance.getBaseValue() + amount;
		if (attribute.value() == Attributes.KNOCKBACK_RESISTANCE.value()) {
			newBase = Math.max(0.0D, Math.min(1.0D, newBase));
		}
		instance.setBaseValue(newBase);
		return true;
	}

	private static ScalingApplication applyScalingAdjustments(
		Mob mob,
		ResolvedIncrements resolvedIncrements,
		int totalAdjustment,
		boolean fullStatScaling
	) {
		if (mob == null || resolvedIncrements == null || totalAdjustment <= 0) {
			return null;
		}
		StatIncrements increments = resolvedIncrements.increments();
		double armorBaseBefore = readAttributeBaseValue(mob, Attributes.ARMOR);
		double healthAddition = resolveHealthScalingAmount(mob, increments, totalAdjustment);
		double movementSpeedAddition = fullStatScaling ? resolveMovementSpeedScalingAmount(mob, increments, totalAdjustment) : 0.0D;
		double scaleAddition = fullStatScaling ? resolveScaleScalingAmount(increments, totalAdjustment) : 0.0D;
		double armorAddition = fullStatScaling ? resolveArmorScalingAmount(increments, totalAdjustment) : 0.0D;
		double damageAddition = fullStatScaling ? resolveDamageScalingAmount(mob, increments, totalAdjustment) : 0.0D;
		double knockbackResistanceAddition = fullStatScaling ? resolveKnockbackResistanceScalingAmount(increments, totalAdjustment) : 0.0D;
		int experienceBaseBefore = resolveMobExperienceDrop(mob);
		int experienceDropAddition = resolveExperienceDropScalingAmount(experienceBaseBefore, increments, totalAdjustment);

		boolean healthChanged = addAttribute(mob, Attributes.MAX_HEALTH, healthAddition);
		if (fullStatScaling) {
			addAttribute(mob, Attributes.MOVEMENT_SPEED, movementSpeedAddition);
			addAttribute(mob, Attributes.SCALE, scaleAddition);
			addAttribute(mob, Attributes.ARMOR, armorAddition);
			addAttribute(mob, Attributes.ATTACK_DAMAGE, damageAddition);
			addAttribute(mob, Attributes.KNOCKBACK_RESISTANCE, knockbackResistanceAddition);
		}
		int experienceBaseAfter = applyExperienceDropScaling(mob, experienceBaseBefore, experienceDropAddition);
		double armorBaseAfter = readAttributeBaseValue(mob, Attributes.ARMOR);

		if (healthChanged) {
			double maxHealth = mob.getAttributeValue(Attributes.MAX_HEALTH);
			mob.setHealth((float) maxHealth);
		}

		return new ScalingApplication(
			increments,
			healthAddition,
			movementSpeedAddition,
			scaleAddition,
			armorAddition,
			armorBaseBefore,
			armorBaseAfter,
			damageAddition,
			knockbackResistanceAddition,
			experienceBaseBefore,
			experienceBaseAfter,
			experienceDropAddition
		);
	}

	private static double readAttributeBaseValue(Mob mob, Holder<Attribute> attribute) {
		if (mob == null || attribute == null) {
			return 0.0D;
		}
		AttributeInstance instance = mob.getAttribute(attribute);
		return instance == null ? 0.0D : instance.getBaseValue();
	}

	private static double resolveHealthScalingAmount(
		Mob mob,
		StatIncrements increments,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		double currentMaxHealth = Math.max(1.0D, mob.getAttributeValue(Attributes.MAX_HEALTH));
		double healthScaleRatio = Math.max(0.0D, increments.health()) * HEALTH_SCALE_STEP_PERCENT;
		return roundToNearestStep(currentMaxHealth * healthScaleRatio * totalAdjustment, SCALING_ROUND_STEP);
	}

	private static double resolveDamageScalingAmount(
		Mob mob,
		StatIncrements increments,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		double currentDamage = Math.max(0.0D, mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
		double damageScaleRatio = Math.max(0.0D, increments.damage()) * DAMAGE_SCALE_STEP_PERCENT;
		return roundToNearestStep(currentDamage * damageScaleRatio * totalAdjustment, SCALING_ROUND_STEP);
	}

	private static double resolveMovementSpeedScalingAmount(
		Mob mob,
		StatIncrements increments,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		double currentSpeed = Math.max(0.0D, mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
		double speedScaleRatio = Math.max(0.0D, increments.movementSpeed()) * MOVEMENT_SPEED_SCALE_STEP_PERCENT;
		return roundToNearestStep(currentSpeed * speedScaleRatio * totalAdjustment, SCALING_ROUND_STEP);
	}

	private static double resolveScaleScalingAmount(StatIncrements increments, int totalAdjustment) {
		return roundToNearestStep(Math.max(0.0D, increments.scale()) * SCALE_SCALE_STEP * totalAdjustment, SCALING_ROUND_STEP);
	}

	private static double resolveArmorScalingAmount(StatIncrements increments, int totalAdjustment) {
		return roundToNearestStep(Math.max(0.0D, increments.armor()) * ARMOR_SCALE_STEP * totalAdjustment, SCALING_ROUND_STEP);
	}

	private static double resolveKnockbackResistanceScalingAmount(StatIncrements increments, int totalAdjustment) {
		return roundToNearestStep(
			Math.max(0.0D, increments.knockbackResistance()) * KNOCKBACK_RESISTANCE_SCALE_STEP * totalAdjustment,
			SCALING_ROUND_STEP
		);
	}

	private static int resolveExperienceDropScalingAmount(int baseExperienceDrop, StatIncrements increments, int totalAdjustment) {
		if (baseExperienceDrop <= 0) {
			return 0;
		}
		double scaleRatio = Math.max(0.0D, increments.experienceDrop()) * EXPERIENCE_DROP_SCALE_STEP_PERCENT;
		double addition = baseExperienceDrop * scaleRatio * totalAdjustment;
		if (!Double.isFinite(addition) || addition <= 0.0D) {
			return 0;
		}
		return Math.max(0, (int) Math.round(addition));
	}

	private static int applyExperienceDropScaling(Mob mob, int baseExperienceDrop, int experienceDropAddition) {
		if (!(mob instanceof MobExperienceAccessor accessor)) {
			return Math.max(0, baseExperienceDrop);
		}
		int resolvedBase = Math.max(0, baseExperienceDrop);
		int resolvedAddition = Math.max(0, experienceDropAddition);
		int scaled = Math.max(0, resolvedBase + resolvedAddition);
		accessor.madokuCraft$setXpReward(scaled);
		return scaled;
	}

	private static int resolveMobExperienceDrop(Mob mob) {
		if (!(mob instanceof MobExperienceAccessor accessor)) {
			return 0;
		}
		return Math.max(0, accessor.madokuCraft$getXpReward());
	}

	private static double roundToNearestStep(double value, double step) {
		if (!Double.isFinite(value) || !Double.isFinite(step) || step <= 0.0D) {
			return value;
		}
		return Math.round(value / step) * step;
	}

	private static Identifier normalizeIdentifier(String rawValue) {
		if (rawValue == null) {
			return null;
		}
		String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return null;
		}
		if (!normalized.contains(":")) {
			normalized = "minecraft:" + normalized;
		}
		return Identifier.tryParse(normalized);
	}

	private static String normalizeFileKey(String rawValue) {
		return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
	}

	private static List<String> resolveMobScalingFileKeys(EntityType<?> type) {
		if (type == null) {
			return List.of();
		}
		Identifier entityId = EntityType.getKey(type);
		if (entityId == null) {
			return List.of();
		}
		return resolveMobScalingFileKeys(entityId);
	}

	private static List<String> resolveMobScalingFileKeys(Identifier entityId) {
		if (entityId == null) {
			return List.of();
		}
		String namespace = normalizeFileKey(entityId.getNamespace());
		String pathRaw = normalizeFileKey(entityId.getPath());
		if (pathRaw.isBlank()) {
			return List.of();
		}
		String pathHyphen = pathRaw.replace('_', '-');
		Set<String> keys = new LinkedHashSet<>();
		keys.add(pathHyphen);
		keys.add(pathRaw);
		if (!"minecraft".equals(namespace) && !namespace.isBlank()) {
			keys.add(namespace + "-" + pathHyphen);
			keys.add(namespace + "-" + pathRaw);
		}
		return List.copyOf(keys);
	}

	private static JsonObject readObject(JsonObject root, String key) {
		if (root == null) {
			return new JsonObject();
		}
		JsonElement element = root.get(key);
		if (element != null && element.isJsonObject()) {
			return element.getAsJsonObject();
		}
		return new JsonObject();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		try {
			String value = element.getAsString();
			return value == null ? fallback : value;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static int readInt(JsonObject root, String key, int fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsInt();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static double readFiniteDouble(JsonObject root, String key, double fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static void emitConfigLoaded() {
		String metricId = "difficulty.config_loaded";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, metricId)) {
			return;
		}
		Snapshot config = snapshot;
			MadokuDebug.event(metricId, MadokuDebug.Domain.MOB)
				.side(MadokuDebug.Side.SERVER)
				.subject("difficulty:global")
				.field("enabled", config.enabled())
				.field("biomes_enabled", config.biomesEnabled())
				.field("structures_enabled", config.structuresEnabled())
				.field("time_enabled", config.timeEnabled())
				.field("biome_rules", config.biomeAdjustments().size())
				.field("structure_rules", config.structureAdjustments().size())
				.field("mob_scaling_profiles", config.mobScalingIncrements().size())
				.log();
		}

	private static void emitSpawnScaled(
		Mob mob,
		Identifier biomeId,
		StructureContext structureContext,
		int baseAdjustment,
		int biomeAdjustment,
		int structureAdjustment,
		int timeAdjustment,
		int totalAdjustment,
		String scalingSource,
		StatIncrements increments,
		double healthAddition,
		double movementSpeedAddition,
		double scaleAddition,
		double armorAddition,
		double armorBaseBefore,
		double armorBaseAfter,
		double damageAddition,
		double knockbackResistanceAddition,
		int experienceBaseBefore,
		int experienceBaseAfter,
		int experienceDropAddition
	) {
		String metricId = "difficulty.spawn_scaled";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, metricId) || mob == null || increments == null) {
			return;
		}

		MadokuDebug.event(metricId, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("mob:" + mob.getType().toShortString())
			.field("base_adj", baseAdjustment)
			.field("biome", biomeId == null ? "unknown" : biomeId)
			.field("biome_adj", biomeAdjustment)
			.field("structure", structureContext.structureId() == null ? "none" : structureContext.structureId())
			.field("structure_adj", structureAdjustment)
			.field("time_adj", timeAdjustment)
			.field("total_adj", totalAdjustment)
			.field("scaling_source", scalingSource == null || scalingSource.isBlank() ? "global" : scalingSource)
				.field("health_cfg", increments.health())
				.field("movement_speed_cfg", increments.movementSpeed())
				.field("scale_cfg", increments.scale())
				.field("armor_cfg", increments.armor())
			.field("damage_cfg", increments.damage())
				.field("knockback_resistance_cfg", increments.knockbackResistance())
				.field("experience_drop_cfg", increments.experienceDrop())
				.field("ranged_damage_cfg", increments.rangedDamage())
				.field("attack_accuracy_cfg", increments.attackAccuracy())
				.field("explosion_power_cfg", increments.explosionPower())
				.field("health_add", healthAddition)
				.field("movement_speed_add", movementSpeedAddition)
				.field("scale_add", scaleAddition)
				.field("armor_add", armorAddition)
			.field("armor_base_before", armorBaseBefore)
			.field("armor_base_after", armorBaseAfter)
			.field("damage_add", damageAddition)
			.field("knockback_resistance_add", knockbackResistanceAddition)
			.field("experience_drop_base_before", experienceBaseBefore)
			.field("experience_drop_base_after", experienceBaseAfter)
			.field("experience_drop_add", experienceDropAddition)
			.log();
	}

	private record Snapshot(
		boolean enabled,
		StatIncrements increments,
		int defaultUnknownAdjustment,
		boolean biomesEnabled,
		Map<Identifier, Integer> biomeAdjustments,
		boolean structuresEnabled,
		Map<Identifier, Integer> structureAdjustments,
		boolean timeEnabled,
		TimeScaling timeScaling,
		Map<String, StatIncrements> mobScalingIncrements
	) {
			private static Snapshot disabled() {
					return new Snapshot(
						false,
						new StatIncrements(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D),
						MadokuDifficultyConfig.DEFAULT_UNKNOWN_ADJUSTMENT,
						false,
					Map.of(),
				false,
				Map.of(),
				false,
				TimeScaling.defaults(),
				Map.of()
			);
		}

		private ResolvedIncrements resolveIncrements(Mob mob) {
			if (!MadokuMob.isEnabled() || mob == null || mobScalingIncrements.isEmpty()) {
				return new ResolvedIncrements(increments, "global");
			}
			for (String key : resolveMobScalingFileKeys(mob.getType())) {
				StatIncrements specific = mobScalingIncrements.get(key);
				if (specific != null) {
					return new ResolvedIncrements(specific, key);
				}
			}
			return new ResolvedIncrements(increments, "global_fallback");
		}

		private int biomeAdjustment(Identifier biomeId) {
			if (!biomesEnabled) {
				return 0;
			}
			if (biomeId == null) {
				return defaultUnknownAdjustment;
			}
			return biomeAdjustments.getOrDefault(biomeId, defaultUnknownAdjustment);
		}

		private int timeAdjustment(long dayCount) {
			if (!timeEnabled) {
				return 0;
			}
			return timeScaling.resolveAdjustment(dayCount);
		}
	}

	private record StatIncrements(
		double health,
		double movementSpeed,
		double scale,
		double armor,
		double damage,
		double knockbackResistance,
		double experienceDrop,
		double rangedDamage,
		double attackAccuracy,
		double explosionPower
	) {
	}

	private record ResolvedIncrements(StatIncrements increments, String sourceKey) {
	}

	private record ScalingApplication(
		StatIncrements increments,
		double healthAddition,
		double movementSpeedAddition,
		double scaleAddition,
		double armorAddition,
		double armorBaseBefore,
		double armorBaseAfter,
		double damageAddition,
		double knockbackResistanceAddition,
		int experienceBaseBefore,
		int experienceBaseAfter,
		int experienceDropAddition
	) {
	}

	private record TimeTier(int minDay, int maxDay, int adjustment) {
		private boolean matches(long dayCount) {
			return dayCount >= minDay && dayCount <= maxDay;
		}
	}

	private record TimeScaling(List<TimeTier> tiers) {
		private static TimeScaling defaults() {
			List<TimeTier> defaults = new ArrayList<>();
			for (MadokuDifficultyConfig.TimeTierDefinition definition : MadokuDifficultyConfig.defaultTimeTiers()) {
				int maxDay = definition.maxDay() < 0 ? Integer.MAX_VALUE : Math.max(definition.minDay(), definition.maxDay());
				defaults.add(new TimeTier(Math.max(0, definition.minDay()), maxDay, Math.max(0, definition.adjustment())));
			}
			return new TimeScaling(List.copyOf(defaults));
		}

		private int resolveAdjustment(long dayCount) {
			long safeDayCount = Math.max(0L, dayCount);
			for (TimeTier tier : tiers) {
				if (tier.matches(safeDayCount)) {
					return tier.adjustment();
				}
			}
			return tiers.isEmpty() ? 0 : tiers.get(tiers.size() - 1).adjustment();
		}
	}

	private record StructureContext(Identifier structureId, int adjustment) {
		private static final StructureContext NONE = new StructureContext(null, 0);
	}
}

