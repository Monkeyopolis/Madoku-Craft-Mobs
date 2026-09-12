package madoku.craft.java.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.chunk.ChunkAPIManager;
import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONTypeAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.runtime.AdaptiveIntervalAPIManager;
import madoku.craft.java.core.sync.SyncWorldAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import madoku.craft.mixin.mob.MobExperienceAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class MobRegionalDifficultyManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MobRegionalDifficultyManager.class);

	private static final String TIME_ADAPTIVE_ID = "madoku-regional-difficulty-time";
	private static final long TIME_ADAPTIVE_MIN_INTERVAL_TICKS = 5L;
	private static final long TIME_ADAPTIVE_MAX_INTERVAL_TICKS = 100L;

	private static final long TICKS_PER_DAY = 24000L;

	private static volatile Snapshot snapshot = Snapshot.disabled();
	private static volatile long nextTimeTick = Long.MIN_VALUE;
	private static volatile long cachedTimeDayCount = Long.MIN_VALUE;
	private static volatile int cachedTimeAdjustment = 0;
	private static final Map<UUID, PlayerDifficultyState> LAST_SYNC_STATE_BY_PLAYER = new HashMap<>();

	private MobRegionalDifficultyManager() {
	}

	public static void initialize() {
		loadConfig();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			syncDifficultyToPlayer(server, handler.player, true)
		);
	}

	public static void onServerStarted(MinecraftServer server) {
		AdaptiveIntervalAPIManager.clearSystem(TIME_ADAPTIVE_ID);
		nextTimeTick = Long.MIN_VALUE;
		cachedTimeDayCount = Long.MIN_VALUE;
		cachedTimeAdjustment = 0;
		LAST_SYNC_STATE_BY_PLAYER.clear();
		refreshCachedTimeAdjustment(server, snapshot);
	}

	public static void onServerTick(MinecraftServer server) {
		if (server == null) return;
		Snapshot config = snapshot;
		if (!config.enabled() || !config.timeEnabled()) {
			nextTimeTick = Long.MIN_VALUE;
			cachedTimeDayCount = Long.MIN_VALUE;
			cachedTimeAdjustment = 0;
			return;
		}
		long now = Math.max(0L, TimeAPIManager.getGameplayTicks());
		if (nextTimeTick == Long.MIN_VALUE || now >= nextTimeTick) {
			nextTimeTick = now + Math.max(1L, resolveTimeAdaptiveInterval(server));
			refreshCachedTimeAdjustment(server, config);
		}
	}

	public static void onServerStopped() {
		AdaptiveIntervalAPIManager.clearSystem(TIME_ADAPTIVE_ID);
		nextTimeTick = Long.MIN_VALUE;
		cachedTimeDayCount = Long.MIN_VALUE;
		cachedTimeAdjustment = 0;
		LAST_SYNC_STATE_BY_PLAYER.clear();
	}

	public static void broadcastDifficultyNow(MinecraftServer server) {
		broadcastDifficulty(server, true);
	}

	public static void broadcastDifficultyIfChanged(MinecraftServer server) {
		broadcastDifficulty(server, false);
	}

	private static void broadcastDifficulty(MinecraftServer server, boolean force) {
		if (server == null) {
			return;
		}

		Set<UUID> activePlayers = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			activePlayers.add(player.getUUID());
			syncDifficultyToPlayer(server, player, force);
		}
		LAST_SYNC_STATE_BY_PLAYER.keySet().removeIf(uuid -> !activePlayers.contains(uuid));
	}

	private static void syncDifficultyToPlayer(MinecraftServer server, ServerPlayer player, boolean force) {
		if (server == null || player == null) {
			return;
		}

		UUID playerId = player.getUUID();
		PlayerDifficultyState previous = LAST_SYNC_STATE_BY_PLAYER.get(playerId);
		PlayerDifficultyState currentKey = captureSyncStateKey(player);
		int difficultyLevel = resolveHudDifficultyLevel(player);
		if (currentKey == null || (!force && previous != null
			&& previous.sameKey(currentKey)
			&& previous.difficultyLevel() == difficultyLevel)) {
			return;
		}

		if (force || previous == null || previous.difficultyLevel() != difficultyLevel) {
			SyncWorldAPIManager.send(player, new MobPayloadManager(difficultyLevel));
		}
		LAST_SYNC_STATE_BY_PLAYER.put(playerId, currentKey.withDifficultyLevel(difficultyLevel));
	}

	private static PlayerDifficultyState captureSyncStateKey(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		BlockPos pos = player.blockPosition();
		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;
		if (!ChunkAPIManager.isChunkLoaded(level, chunkX, chunkZ)) {
			return null;
		}
		int timeAdjustment = resolveCurrentTimeAdjustment(level);
		String levelId = level.dimension().identifier().toString();
		return new PlayerDifficultyState(levelId, packChunk(chunkX, chunkZ), timeAdjustment, 1);
	}

	private static long packChunk(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
	}

	private record PlayerDifficultyState(String levelId, long chunkPos, int timeAdjustment, int difficultyLevel) {
		private boolean sameKey(PlayerDifficultyState other) {
			return other != null && chunkPos == other.chunkPos && timeAdjustment == other.timeAdjustment && levelId.equals(other.levelId);
		}

		private PlayerDifficultyState withDifficultyLevel(int level) {
			return new PlayerDifficultyState(levelId, chunkPos, timeAdjustment, Math.max(1, level));
		}
	}

	public static boolean isEnabled() {
		return MobConfigManager.isEnabled() && snapshot.enabled();
	}

	public static int resolveHudDifficultyLevel(ServerLevel world, net.minecraft.core.BlockPos pos) {
		if (world == null || pos == null) {
			return 1;
		}
		Snapshot config = snapshot;
		if (!config.enabled()) {
			return 1;
		}
		if (!isChunkLoadedAt(world, pos)) {
			return 1;
		}
		Identifier biomeId = resolveBiomeId(world, pos);
		int biomeAdjustment = config.biomeAdjustment(biomeId);
		int timeAdjustment = resolveTimeAdjustment(world, config);
		StructureContext structureContext = resolveStructureContext(world, pos, config.structureRuntime());
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
		if (mob == null || worldAccess == null || !(mob instanceof MobEntityManager.DifficultyState scaledMob)) {
			return;
		}

		scaledMob.madokuCraft$setSpawnDifficultyAdjustment(0);
		if (!MobEntityManager.isDifficultyScalingEligible(mob)) {
			return;
		}
		Snapshot config = snapshot;
		if (!config.enabled()) {
			return;
		}

		ServerLevel world = worldAccess.getLevel();
		if (world == null || world.isClientSide()) {
			return;
		}
		if (!isChunkLoadedAt(world, mob.blockPosition())) {
			return;
		}

		Identifier biomeId = resolveBiomeId(world, mob.blockPosition());
		int biomeAdjustment = config.biomeAdjustment(biomeId);
		int timeAdjustment = resolveTimeAdjustment(world, config);
		StructureContext structureContext = resolveStructureContext(world, mob.blockPosition(), config.structureRuntime());
		int structureAdjustment = structureContext.adjustment();
		int baseAdjustment = 1;
		int totalAdjustment = Math.max(0, baseAdjustment + biomeAdjustment + structureAdjustment + timeAdjustment);
		scaledMob.madokuCraft$setSpawnDifficultyAdjustment(totalAdjustment);
		if (totalAdjustment <= 0) {
			return;
		}

		ResolvedIncrements resolvedIncrements = config.resolveIncrements(mob);
		ScalingApplication applied = applyScalingAdjustments(mob, resolvedIncrements, totalAdjustment);
		if (applied == null) {
			return;
		}
	}

	public static void applySpawnScalingIfUnscaled(Mob mob, ServerLevelAccessor worldAccess) {
		if (!(mob instanceof MobEntityManager.DifficultyState scaledMob) || scaledMob.madokuCraft$getSpawnDifficultyAdjustment() > 0) {
			return;
		}
		applySpawnScaling(mob, worldAccess);
	}

	public static int ensureSpawnDifficultyAdjustment(Mob mob, ServerLevelAccessor worldAccess) {
		if (mob == null || worldAccess == null || !(mob instanceof MobEntityManager.DifficultyState scaledMob)) {
			return 0;
		}
		scaledMob.madokuCraft$setSpawnDifficultyAdjustment(0);
		Snapshot config = snapshot;
		if (!config.enabled()) {
			return 0;
		}
		ServerLevel world = worldAccess.getLevel();
		if (world == null || world.isClientSide()) {
			return 0;
		}
		if (!isChunkLoadedAt(world, mob.blockPosition())) {
			return 0;
		}

		Identifier biomeId = resolveBiomeId(world, mob.blockPosition());
		int biomeAdjustment = config.biomeAdjustment(biomeId);
		int timeAdjustment = resolveTimeAdjustment(world, config);
		StructureContext structureContext = resolveStructureContext(world, mob.blockPosition(), config.structureRuntime());
		int structureAdjustment = structureContext.adjustment();
		int baseAdjustment = 1;
		int totalAdjustment = Math.max(0, baseAdjustment + biomeAdjustment + structureAdjustment + timeAdjustment);
		scaledMob.madokuCraft$setSpawnDifficultyAdjustment(totalAdjustment);
		return totalAdjustment;
	}

	public static void reapplySpawnScalingFromStoredAdjustment(Mob mob) {
		if (!MobEntityManager.isDifficultyScalingEligible(mob) || !(mob instanceof MobEntityManager.DifficultyState scaledMob)) {
			return;
		}
		Snapshot config = snapshot;
		if (!config.enabled()) {
			return;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0) {
			return;
		}

		ResolvedIncrements resolvedIncrements = config.resolveIncrements(mob);
		applyScalingAdjustments(mob, resolvedIncrements, totalAdjustment);
	}

	public static double resolveCreeperExplosionPowerScaling(Mob mob, double baseExplosionPower) {
		if (!MobEntityManager.isDifficultyScalingEligible(mob)
			|| mob.getType() != MobEntityTypeAPIManager.CREEPER || !isEnabled()) {
			return 0.0D;
		}
		if (!(mob instanceof MobEntityManager.DifficultyState scaledMob)) {
			return 0.0D;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0) {
			return 0.0D;
		}
		ResolvedIncrements resolvedIncrements = snapshot.resolveIncrements(mob);
		StatIncrements increments = resolvedIncrements.increments();
		StatModes modes = resolvedIncrements.modes();
		double sanitizedBase = Math.max(0.0D, baseExplosionPower);
		double powerAddition = resolveScaledAddition(sanitizedBase, increments.explosionPower(), modes.explosionPowerMode(), totalAdjustment);
		return powerAddition;
	}

	public static double resolveMobRangedDamageScaling(Mob mob, double baseDamage) {
		double sanitizedBase = Math.max(0.0D, baseDamage);
		if (!MobEntityManager.isDifficultyScalingEligible(mob) || !isEnabled()) {
			return sanitizedBase;
		}
		if (!(mob instanceof MobEntityManager.DifficultyState scaledMob)) {
			return sanitizedBase;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0) {
			return sanitizedBase;
		}
		ResolvedIncrements resolvedIncrements = snapshot.resolveIncrements(mob);
		StatIncrements increments = resolvedIncrements.increments();
		StatModes modes = resolvedIncrements.modes();
		double addition = resolveScaledAddition(sanitizedBase, increments.rangedDamage(), modes.rangedDamageMode(), totalAdjustment);
		return Math.max(0.0D, sanitizedBase + addition);
	}

	public static double resolveMobAttackAccuracyScaling(Mob mob, double baseAccuracy) {
		double sanitizedBase = Mth.clamp(baseAccuracy, 0.0D, 1.0D);
		if (!MobEntityManager.isDifficultyScalingEligible(mob) || !isEnabled()) {
			return sanitizedBase;
		}
		if (!(mob instanceof MobEntityManager.DifficultyState scaledMob)) {
			return sanitizedBase;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0) {
			return sanitizedBase;
		}
		ResolvedIncrements resolvedIncrements = snapshot.resolveIncrements(mob);
		StatIncrements increments = resolvedIncrements.increments();
		StatModes modes = resolvedIncrements.modes();
		double addition = resolveScaledAddition(sanitizedBase, increments.attackAccuracy(), modes.attackAccuracyMode(), totalAdjustment);
		double resolved = sanitizedBase + addition;
		return Mth.clamp(MobConfigManager.roundDifficultyScaleValue(resolved), 0.0D, 1.0D);
	}

	private static void loadConfig() {
		try {
			Path rootDirectory = MobConfigManager.getOrCreateMobSystemDirectory(MobConfigManager.REGIONAL_DIFFICULTY_SYSTEM_FOLDER);
			Path mobsRootDirectory = MobConfigManager.getOrCreateMobRootDirectory();
			JsonObject settingsRoot = JSONFormatAPIManager.ensureManagedFile(
				mobsRootDirectory.resolve(MobConfigManager.REGIONAL_DIFFICULTY_SETTINGS_FILE + ".json"),
				RegionalDifficultyConfigManager.buildSettingsDefaults()
			);
			JsonObject biomes = JSONFormatAPIManager.ensureManagedFile(
				rootDirectory.resolve(MobConfigManager.REGIONAL_BIOMES_FILE + ".json"),
				MobConfigManager.buildBiomesDefaults(),
				JSONTypeAPIManager.DYNAMIC_CONFIG,
				(key, value) -> value
			);
			JsonObject structures = JSONFormatAPIManager.ensureManagedFile(
				rootDirectory.resolve(MobConfigManager.REGIONAL_STRUCTURES_FILE + ".json"),
				MobConfigManager.buildStructuresDefaults(),
				JSONTypeAPIManager.DYNAMIC_CONFIG,
				(key, value) -> value
			);
			JsonObject time = JSONFormatAPIManager.ensureManagedFile(
				rootDirectory.resolve(MobConfigManager.REGIONAL_TIME_FILE + ".json"),
				MobConfigManager.buildTimeDefaults(),
				JSONTypeAPIManager.DYNAMIC_CONFIG,
				(key, value) -> value
			);
			JsonObject scaling = readObject(settingsRoot, RegionalDifficultyConfigManager.FIELD_DIFFICULTY_SCALING);
			double defaultHealthIncrement = readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_HEALTH, RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT);
			double defaultMovementSpeedIncrement = readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED, RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT);
			double defaultArmorIncrement = readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_ARMOR, RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT);
			double defaultDamageIncrement = readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_DAMAGE, RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT);
			double defaultKnockbackResistanceIncrement = readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_KNOCKBACK_RESISTANCE, RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT);
			double defaultExperienceDropIncrement = readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP, RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT);
			Map<String, JsonObject> normalizedMobScaling = JSONFormatAPIManager.ensureManagedFolder(
				rootDirectory.resolve(MobConfigManager.REGIONAL_SCALING_FOLDER),
				RegionalDifficultyConfigManager.buildDefaultMobScalingFileDefaults(defaultHealthIncrement, defaultMovementSpeedIncrement, defaultArmorIncrement, defaultDamageIncrement, defaultKnockbackResistanceIncrement, defaultExperienceDropIncrement),
				RegionalDifficultyConfigManager::buildDynamicMobScalingDefaults,
				(fileKey, sourceRoot) -> true,
				null
			);
			snapshot = buildSnapshot(settingsRoot, Map.of("biomes", biomes), Map.of("structures", structures), Map.of("time", time), normalizedMobScaling);
		} catch (IOException | RuntimeException exception) {
			snapshot = Snapshot.disabled();
			LOGGER.error("Failed to load MobRegionalDifficultyManager config; disabling difficulty scaling.", exception);
		}
	}

	private static Snapshot buildSnapshot(
		JsonObject settingsRoot,
		Map<String, JsonObject> biomeRulesByFile,
		Map<String, JsonObject> structureRulesByFile,
		Map<String, JsonObject> timeRulesByFile,
		Map<String, JsonObject> mobScalingByFile
	) {
		boolean enabled = readBoolean(settingsRoot, RegionalDifficultyConfigManager.FIELD_ENABLED, true);
		boolean biomesEnabled = true;
		boolean structuresEnabled = true;
		boolean timeEnabled = true;
		int defaultUnknownAdjustment = RegionalDifficultyConfigManager.DEFAULT_UNKNOWN_ADJUSTMENT;

		JsonObject scaling = readObject(settingsRoot, RegionalDifficultyConfigManager.FIELD_DIFFICULTY_SCALING);
			StatIncrements increments = new StatIncrements(
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_HEALTH, RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED, RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_FLYING_SPEED, RegionalDifficultyConfigManager.DEFAULT_FLYING_SPEED_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_SCALE, RegionalDifficultyConfigManager.DEFAULT_SCALE_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_ARMOR, RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_DAMAGE, RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_KNOCKBACK_RESISTANCE, RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP, RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT),
				0.0D,
				0.0D,
				0.0D
			);
		StatModes globalModes = parseStatModesFromMobScalingRoot(scaling, StatModes.defaults());

		Map<Identifier, Integer> biomeAdjustments = parseGroupedIdentifierAdjustments(
			biomeRulesByFile,
			RegionalDifficultyConfigManager.FIELD_BIOME_LIST,
			defaultUnknownAdjustment
		);
		Map<Identifier, Integer> structureAdjustments = parseGroupedIdentifierAdjustments(
			structureRulesByFile,
			RegionalDifficultyConfigManager.FIELD_STRUCTURE_LIST,
			defaultUnknownAdjustment
		);
		TimeScaling timeScaling = parseTimeScaling(timeRulesByFile, defaultUnknownAdjustment);
		Map<String, ScalingProfile> mobScalingIncrements = parseMobScaling(mobScalingByFile, increments);
		BiomeState biomeRuntime = new BiomeState(
			biomesEnabled,
			defaultUnknownAdjustment,
			Map.copyOf(biomeAdjustments)
		);
		StructureState structureRuntime = new StructureState(
			structuresEnabled,
			defaultUnknownAdjustment,
			Map.copyOf(structureAdjustments)
		);
		TimeState timeRuntime = new TimeState(
			timeEnabled,
			timeScaling.toRuntimeTiers()
		);

		return new Snapshot(
			enabled,
			increments,
			globalModes,
			biomeRuntime,
			structureRuntime,
			timeRuntime,
			Map.copyOf(mobScalingIncrements),
			new HashMap<>()
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

			JsonElement listElement = root.get(listField);
			if (listElement != null && listElement.isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : listElement.getAsJsonObject().entrySet()) {
					Identifier id = normalizeIdentifier(entry.getKey());
					int adjustment = entry.getValue().isJsonObject()
						? Math.max(0, readInt(entry.getValue().getAsJsonObject(), RegionalDifficultyConfigManager.FIELD_ADJUSTMENT, defaultUnknownAdjustment))
						: defaultUnknownAdjustment;
					if (id != null) resolved.merge(id, adjustment, Math::max);
				}
				continue;
			}
			continue;
		}
		return resolved;
	}

	private static TimeScaling parseTimeScaling(Map<String, JsonObject> timeRulesByFile, int defaultUnknownAdjustment) {
		List<TimeTier> tiers = new ArrayList<>();
		for (JsonObject root : timeRulesByFile.values()) {
			if (root == null) {
				continue;
			}
			JsonElement dayList = root.get(RegionalDifficultyConfigManager.FIELD_DAY_LIST);
			if (dayList != null && dayList.isJsonObject()) {
				int previousDay = 0;
				for (JsonElement entry : dayList.getAsJsonObject().entrySet().stream().map(Map.Entry::getValue).toList()) {
					if (!entry.isJsonObject()) continue;
					JsonObject tier = entry.getAsJsonObject();
					int minDay = Math.max(previousDay, readInt(tier, RegionalDifficultyConfigManager.FIELD_DAY_COUNT, previousDay));
					int adjustment = Math.max(0, readInt(tier, RegionalDifficultyConfigManager.FIELD_ADJUSTMENT, defaultUnknownAdjustment));
					tiers.add(new TimeTier(minDay, Integer.MAX_VALUE, adjustment));
					previousDay = minDay;
				}
				for (int index = 0; index < tiers.size(); index++) {
					TimeTier current = tiers.get(index);
					int maxDay = index + 1 < tiers.size() ? Math.max(current.minDay(), tiers.get(index + 1).minDay() - 1) : Integer.MAX_VALUE;
					tiers.set(index, new TimeTier(current.minDay(), maxDay, current.adjustment()));
				}
				continue;
			}
			// The time file is intentionally dynamic, but its schema is always day-list.
		}

		if (tiers.isEmpty()) {
			for (RegionalDifficultyTimeManager.TimeTierDefinition definition : RegionalDifficultyTimeManager.defaultTimeTiers()) {
				int maxDay = definition.maxDay() < 0 ? Integer.MAX_VALUE : Math.max(definition.minDay(), definition.maxDay());
				tiers.add(new TimeTier(Math.max(0, definition.minDay()), maxDay, Math.max(0, definition.adjustment())));
			}
		}

		tiers.sort(Comparator.comparingInt(TimeTier::minDay));
		return new TimeScaling(List.copyOf(tiers));
	}

	private static Map<String, ScalingProfile> parseMobScaling(
		Map<String, JsonObject> mobScalingByFile,
		StatIncrements fallbackIncrements
	) {
		Map<String, ScalingProfile> resolved = new LinkedHashMap<>();
		if (mobScalingByFile == null || mobScalingByFile.isEmpty()) {
			return resolved;
		}
		StatModes fallbackModes = StatModes.defaults();
		for (Map.Entry<String, JsonObject> entry : mobScalingByFile.entrySet()) {
			String fileKey = normalizeFileKey(entry.getKey());
			JsonObject root = entry.getValue();
			if (fileKey.isBlank() || root == null || !readBoolean(root, RegionalDifficultyConfigManager.FIELD_ENABLED, true)) {
				continue;
			}

			StatIncrements increments = parseStatIncrementsFromMobScalingRoot(root, fallbackIncrements);
			StatModes modes = parseStatModesFromMobScalingRoot(root, fallbackModes);
			ScalingProfile profile = new ScalingProfile(increments, modes);
			resolved.put(fileKey, profile);

			Identifier configuredMobId = normalizeIdentifier(readString(root, RegionalDifficultyConfigManager.FIELD_MOB_ID, ""));
			if (configuredMobId != null) {
				for (String alias : RegionalDifficultyConfigManager.resolveMobScalingFileKeys(configuredMobId)) {
					if (!alias.isBlank()) {
						resolved.put(alias, profile);
					}
				}
			}
		}
		return resolved;
	}

	private static StatIncrements parseStatIncrementsFromMobScalingRoot(JsonObject root, StatIncrements fallbackIncrements) {
		return new StatIncrements(
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_HEALTH, fallbackIncrements.health()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED, fallbackIncrements.movementSpeed()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_FLYING_SPEED, fallbackIncrements.flyingSpeed()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_SCALE, fallbackIncrements.scale()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_ARMOR, fallbackIncrements.armor()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_DAMAGE, fallbackIncrements.damage()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_KNOCKBACK_RESISTANCE, fallbackIncrements.knockbackResistance()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP, fallbackIncrements.experienceDrop()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_RANGED_DAMAGE, fallbackIncrements.rangedDamage()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_RANGED_ACCURACY, fallbackIncrements.attackAccuracy()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_EXPLOSION_POWER, fallbackIncrements.explosionPower())
		);
	}

	private static StatModes parseStatModesFromMobScalingRoot(JsonObject root, StatModes fallbackModes) {
		return new StatModes(
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_HEALTH, fallbackModes.healthMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED, fallbackModes.movementSpeedMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_FLYING_SPEED, fallbackModes.flyingSpeedMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_SCALE, fallbackModes.scaleMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_ARMOR, fallbackModes.armorMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_DAMAGE, fallbackModes.damageMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_KNOCKBACK_RESISTANCE, fallbackModes.knockbackResistanceMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP, fallbackModes.experienceDropMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_RANGED_DAMAGE, fallbackModes.rangedDamageMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_RANGED_ACCURACY, fallbackModes.attackAccuracyMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_EXPLOSION_POWER, fallbackModes.explosionPowerMode())
		);
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
		if (TimeAPIManager.isEnabled()) {
			return Math.floorDiv(TimeAPIManager.getCurrentAbsoluteDayTime(), TICKS_PER_DAY);
		}
		ServerLevel overworld = server.overworld();
		if (overworld != null) {
			return Math.floorDiv(overworld.getDayTime(), TICKS_PER_DAY);
		}
		return 0L;
	}

	private static long resolveTimeAdaptiveInterval(MinecraftServer server) {
		return AdaptiveIntervalAPIManager.resolve(
			TIME_ADAPTIVE_ID,
			server,
			TIME_ADAPTIVE_MIN_INTERVAL_TICKS,
			TIME_ADAPTIVE_MAX_INTERVAL_TICKS
		);
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


	private static StructureContext resolveStructureContext(
		ServerLevel world,
		net.minecraft.core.BlockPos pos,
		StructureState structureRuntime
	) {
		if (world == null || pos == null || structureRuntime == null || !structureRuntime.enabled()) {
			return StructureContext.NONE;
		}
		if (!isChunkLoadedAt(world, pos)) {
			return StructureContext.NONE;
		}
		Map<Identifier, Integer> configuredAdjustments = structureRuntime.adjustments();
		int defaultUnknownAdjustment = structureRuntime.defaultUnknownAdjustment();

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

	private static boolean isChunkLoadedAt(ServerLevel world, net.minecraft.core.BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;
		return ChunkAPIManager.isChunkLoaded(world, chunkX, chunkZ);
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
		int totalAdjustment
	) {
		if (mob == null || resolvedIncrements == null || totalAdjustment <= 0) {
			return null;
		}
		StatIncrements increments = resolvedIncrements.increments();
		StatModes modes = resolvedIncrements.modes();
		double armorBaseBefore = increments.armor() == 0.0D ? 0.0D : readAttributeBaseValue(mob, Attributes.ARMOR);
		double healthAddition = increments.health() == 0.0D ? 0.0D : resolveHealthScalingAmount(mob, increments, modes, totalAdjustment);
		double movementSpeedAddition = increments.movementSpeed() == 0.0D ? 0.0D : resolveMovementSpeedScalingAmount(mob, increments, modes, totalAdjustment);
		double flyingSpeedAddition = increments.flyingSpeed() != 0.0D && mob.getType() == MobEntityTypeAPIManager.BEE
			? resolveFlyingSpeedScalingAmount(mob, increments, modes, totalAdjustment)
			: 0.0D;
		double scaleAddition = increments.scale() == 0.0D ? 0.0D : resolveScaleScalingAmount(mob, increments, modes, totalAdjustment);
		double armorAddition = increments.armor() == 0.0D ? 0.0D : resolveArmorScalingAmount(mob, increments, modes, totalAdjustment);
		double damageAddition = increments.damage() == 0.0D ? 0.0D : resolveDamageScalingAmount(mob, increments, modes, totalAdjustment);
		double knockbackResistanceAddition = increments.knockbackResistance() == 0.0D ? 0.0D : resolveKnockbackResistanceScalingAmount(mob, increments, modes, totalAdjustment);
		int experienceBaseBefore = resolveMobExperienceDrop(mob);
		int experienceDropAddition = increments.experienceDrop() == 0.0D
			? 0
			: resolveExperienceDropScalingAmount(experienceBaseBefore, increments, modes, totalAdjustment);

		boolean healthChanged = addAttribute(mob, Attributes.MAX_HEALTH, healthAddition);
		addAttribute(mob, Attributes.MOVEMENT_SPEED, movementSpeedAddition);
		if (mob.getType() == MobEntityTypeAPIManager.BEE) {
			addAttribute(mob, Attributes.FLYING_SPEED, flyingSpeedAddition);
		}
		addAttribute(mob, Attributes.SCALE, scaleAddition);
		addAttribute(mob, Attributes.ARMOR, armorAddition);
		addAttribute(mob, Attributes.ATTACK_DAMAGE, damageAddition);
		addAttribute(mob, Attributes.KNOCKBACK_RESISTANCE, knockbackResistanceAddition);
		int experienceBaseAfter = applyExperienceDropScaling(mob, experienceBaseBefore, experienceDropAddition);
		double armorBaseAfter = increments.armor() == 0.0D ? armorBaseBefore : readAttributeBaseValue(mob, Attributes.ARMOR);

		if (healthChanged) {
			AttributeInstance maxHealthInstance = mob.getAttribute(Attributes.MAX_HEALTH);
			if (maxHealthInstance != null) {
				mob.setHealth((float) maxHealthInstance.getValue());
			}
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
		StatModes modes,
		int totalAdjustment
	) {
		if (!MobEntityManager.isDifficultyScalingEligible(mob)) {
			return 0.0D;
		}
		AttributeInstance instance = mob.getAttribute(Attributes.MAX_HEALTH);
		if (instance == null) {
			return 0.0D;
		}
		double currentMaxHealth = Math.max(1.0D, instance.getValue());
		return resolveScaledAddition(currentMaxHealth, increments.health(), modes.healthMode(), totalAdjustment);
	}

	private static double resolveDamageScalingAmount(
		Mob mob,
		StatIncrements increments,
		StatModes modes,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		AttributeInstance instance = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (instance == null) {
			return 0.0D;
		}
		double currentDamage = Math.max(0.0D, instance.getValue());
		return resolveScaledAddition(currentDamage, increments.damage(), modes.damageMode(), totalAdjustment);
	}

	private static double resolveMovementSpeedScalingAmount(
		Mob mob,
		StatIncrements increments,
		StatModes modes,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		AttributeInstance instance = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (instance == null) {
			return 0.0D;
		}
		double currentSpeed = Math.max(0.0D, instance.getValue());
		return resolveScaledAddition(currentSpeed, increments.movementSpeed(), modes.movementSpeedMode(), totalAdjustment);
	}

	private static double resolveFlyingSpeedScalingAmount(
		Mob mob,
		StatIncrements increments,
		StatModes modes,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		AttributeInstance instance = mob.getAttribute(Attributes.FLYING_SPEED);
		if (instance == null) {
			return 0.0D;
		}
		double currentSpeed = Math.max(0.0D, instance.getValue());
		return resolveScaledAddition(currentSpeed, increments.flyingSpeed(), modes.flyingSpeedMode(), totalAdjustment);
	}

	private static double resolveScaleScalingAmount(
		Mob mob,
		StatIncrements increments,
		StatModes modes,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		AttributeInstance instance = mob.getAttribute(Attributes.SCALE);
		if (instance == null) {
			return 0.0D;
		}
		double currentScale = Math.max(0.0D, instance.getValue());
		return resolveScaledAddition(currentScale, increments.scale(), modes.scaleMode(), totalAdjustment);
	}

	private static double resolveArmorScalingAmount(Mob mob, StatIncrements increments, StatModes modes, int totalAdjustment) {
		if (mob == null) {
			return 0.0D;
		}
		AttributeInstance instance = mob.getAttribute(Attributes.ARMOR);
		if (instance == null) {
			return 0.0D;
		}
		double currentArmor = Math.max(0.0D, instance.getValue());
		return resolveScaledAddition(currentArmor, increments.armor(), modes.armorMode(), totalAdjustment);
	}

	private static double resolveKnockbackResistanceScalingAmount(Mob mob, StatIncrements increments, StatModes modes, int totalAdjustment) {
		if (mob == null) {
			return 0.0D;
		}
		AttributeInstance instance = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (instance == null) {
			return 0.0D;
		}
		double currentKnockbackResistance = Math.max(0.0D, instance.getValue());
		return resolveScaledAddition(currentKnockbackResistance, increments.knockbackResistance(), modes.knockbackResistanceMode(), totalAdjustment);
	}

	private static int resolveExperienceDropScalingAmount(int baseExperienceDrop, StatIncrements increments, StatModes modes, int totalAdjustment) {
		if (baseExperienceDrop <= 0) {
			return 0;
		}
		double addition = resolveScaledAddition(baseExperienceDrop, increments.experienceDrop(), modes.experienceDropMode(), totalAdjustment);
		if (!Double.isFinite(addition) || addition <= 0.0D) {
			return 0;
		}
		return Math.max(0, (int) Math.round(addition));
	}

	private static double resolveScaledAddition(double baseValue, double configuredValue, ScalingMode mode, int totalAdjustment) {
		double safeBase = Math.max(0.0D, baseValue);
		double safeConfigured = Math.max(0.0D, configuredValue);
		if (totalAdjustment <= 0 || safeConfigured <= 0.0D) {
			return 0.0D;
		}
		double addition = switch (mode) {
			case PERCENTAGE -> safeBase * (safeConfigured * totalAdjustment);
			case ADD -> safeConfigured * totalAdjustment;
		};
		return addition;
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

	static void roundFinalScalingValues(Mob mob) {
		if (mob == null) {
			return;
		}
		roundAttribute(mob, Attributes.MAX_HEALTH);
		roundAttribute(mob, Attributes.ARMOR);
		roundAttribute(mob, Attributes.ATTACK_DAMAGE);
		roundAttribute(mob, Attributes.MOVEMENT_SPEED);
		roundAttribute(mob, Attributes.FLYING_SPEED);
		roundAttribute(mob, Attributes.WATER_MOVEMENT_EFFICIENCY);
		roundAttribute(mob, Attributes.SCALE);
		roundAttribute(mob, Attributes.KNOCKBACK_RESISTANCE);
		if (mob instanceof MobExperienceAccessor accessor) {
			accessor.madokuCraft$setXpReward(Math.max(0, (int) Math.round(accessor.madokuCraft$getXpReward())));
		}
		mob.setHealth(Math.min(mob.getHealth(), mob.getMaxHealth()));
	}

	private static void roundAttribute(Mob mob, Holder<Attribute> attribute) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(MobConfigManager.roundDifficultyScaleValue(instance.getBaseValue()));
		}
	}

	private static Identifier normalizeIdentifier(String rawValue) {
		if (rawValue == null) {
			return null;
		}
		String normalized = JSONAPIManager.normalizeRegistryIdentifierForLookup(rawValue);
		if (normalized.isBlank()) {
			return null;
		}
		return Identifier.tryParse(normalized);
	}

	private static String normalizeFileKey(String rawValue) {
		return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
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

	private static ScalingMode readScalingMode(JsonObject root, String statField, ScalingMode fallback) {
		if (root == null || statField == null || statField.isBlank()) {
			return fallback;
		}
		JsonElement statElement = root.get(statField);
		if (statElement == null) {
			if (RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED.equals(statField)) {
				statElement = root.get("movement_speed");
			} else if (RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP.equals(statField)) {
				statElement = root.get("experience_drop");
			}
		}
		if (statElement == null) {
			return fallback;
		}
		if (!statElement.isJsonObject()) {
			return fallback;
		}
		JsonObject statObject = statElement.getAsJsonObject();
		String rawType = readString(statObject, RegionalDifficultyConfigManager.FIELD_SCALING_TYPE, "");
		String normalized = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);
		if (RegionalDifficultyConfigManager.SCALING_TYPE_PERCENTAGE.equals(normalized)) {
			return ScalingMode.PERCENTAGE;
		}
		if (RegionalDifficultyConfigManager.SCALING_TYPE_ADD.equals(normalized)) {
			return ScalingMode.ADD;
		}
		return fallback;
	}

	private static double readScalingValue(JsonObject root, String statField, double fallback) {
		if (root == null || statField == null || statField.isBlank()) {
			return fallback;
		}
		JsonElement statElement = root.get(statField);
		if (statElement == null) {
			if (RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED.equals(statField)) {
				statElement = root.get("movement_speed");
			} else if (RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP.equals(statField)) {
				statElement = root.get("experience_drop");
			}
		}
		if (statElement == null) {
			return fallback;
		}
		if (statElement.isJsonObject()) {
			JsonObject statObject = statElement.getAsJsonObject();
			return readFiniteDouble(statObject, RegionalDifficultyConfigManager.FIELD_SCALING_VALUE, fallback);
		}
		return fallback;
	}

	private record Snapshot(
		boolean enabled,
		StatIncrements increments,
		StatModes globalModes,
		BiomeState biomeRuntime,
		StructureState structureRuntime,
		TimeState timeRuntime,
		Map<String, ScalingProfile> mobScalingIncrements,
		Map<String, ResolvedIncrements> resolvedIncrementsCache
	) {
		private static Snapshot disabled() {
			return new Snapshot(
				false,
				new StatIncrements(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D),
				StatModes.defaults(),
				new BiomeState(false, 0, Map.of()),
				new StructureState(false, 0, Map.of()),
				TimeState.defaults(false),
				Map.of(),
				new HashMap<>()
			);
		}

		private ResolvedIncrements resolveIncrements(Mob mob) {
			if (mob == null || mobScalingIncrements.isEmpty()) {
				return new ResolvedIncrements(increments, globalModes);
			}
			String entityKey = EntityType.getKey(mob.getType()).toString();
			ResolvedIncrements cached = resolvedIncrementsCache.get(entityKey);
			if (cached != null) {
				return cached;
			}
			for (String key : RegionalDifficultyConfigManager.resolveMobScalingFileKeys(mob.getType())) {
				ScalingProfile specific = mobScalingIncrements.get(key);
				if (specific != null) {
					ResolvedIncrements resolved = new ResolvedIncrements(specific.increments(), specific.modes());
					resolvedIncrementsCache.put(entityKey, resolved);
					return resolved;
				}
			}
			ResolvedIncrements resolved = new ResolvedIncrements(increments, globalModes);
			resolvedIncrementsCache.put(entityKey, resolved);
			return resolved;
		}

		private int biomeAdjustment(Identifier biomeId) {
			return biomeRuntime.resolveAdjustment(biomeId);
		}

		private boolean timeEnabled() {
			return timeRuntime.enabled();
		}

		private int timeAdjustment(long dayCount) {
			return timeRuntime.resolveAdjustment(dayCount);
		}
	}

	private record StatIncrements(
		double health,
		double movementSpeed,
		double flyingSpeed,
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

	private record ResolvedIncrements(StatIncrements increments, StatModes modes) {
	}

	private record ScalingProfile(StatIncrements increments, StatModes modes) {
	}

	private record StatModes(
		ScalingMode healthMode,
		ScalingMode movementSpeedMode,
		ScalingMode flyingSpeedMode,
		ScalingMode scaleMode,
		ScalingMode armorMode,
		ScalingMode damageMode,
		ScalingMode knockbackResistanceMode,
		ScalingMode experienceDropMode,
		ScalingMode rangedDamageMode,
		ScalingMode attackAccuracyMode,
		ScalingMode explosionPowerMode
	) {
		private static StatModes defaults() {
			return new StatModes(
				ScalingMode.PERCENTAGE,
				ScalingMode.PERCENTAGE,
				ScalingMode.PERCENTAGE,
				ScalingMode.PERCENTAGE,
				ScalingMode.ADD,
				ScalingMode.PERCENTAGE,
				ScalingMode.ADD,
				ScalingMode.PERCENTAGE,
				ScalingMode.PERCENTAGE,
				ScalingMode.PERCENTAGE,
				ScalingMode.PERCENTAGE
			);
		}
	}

	private enum ScalingMode {
		ADD,
		PERCENTAGE
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
	}

	private record TimeScaling(List<TimeTier> tiers) {
		private List<TimeState.TimeTier> toRuntimeTiers() {
			List<TimeState.TimeTier> runtimeTiers = new ArrayList<>();
			for (TimeTier tier : tiers) {
				runtimeTiers.add(new TimeState.TimeTier(tier.minDay(), tier.maxDay(), tier.adjustment()));
			}
			return List.copyOf(runtimeTiers);
		}
	}

		private record StructureContext(Identifier structureId, int adjustment) {
			private static final StructureContext NONE = new StructureContext(null, 0);
		}

	private record BiomeState(
		boolean enabled,
		int defaultUnknownAdjustment,
		Map<Identifier, Integer> adjustments
	) {
		private int resolveAdjustment(Identifier biomeId) {
			if (!enabled) return 0;
			if (biomeId == null) return defaultUnknownAdjustment;
			return adjustments.getOrDefault(biomeId, defaultUnknownAdjustment);
		}
	}

	private record StructureState(
		boolean enabled,
		int defaultUnknownAdjustment,
		Map<Identifier, Integer> adjustments
	) {
	}

	private record TimeState(
		boolean enabled,
		List<TimeTier> tiers
	) {
		private static TimeState defaults(boolean enabled) {
			List<TimeTier> defaults = new ArrayList<>();
			for (RegionalDifficultyTimeManager.TimeTierDefinition definition : RegionalDifficultyTimeManager.defaultTimeTiers()) {
				int maxDay = definition.maxDay() < 0 ? Integer.MAX_VALUE : Math.max(definition.minDay(), definition.maxDay());
				defaults.add(new TimeTier(Math.max(0, definition.minDay()), maxDay, Math.max(0, definition.adjustment())));
			}
			return new TimeState(enabled, List.copyOf(defaults));
		}

		private int resolveAdjustment(long dayCount) {
			if (!enabled) return 0;
			long safeDayCount = Math.max(0L, dayCount);
			for (TimeTier tier : tiers) {
				if (tier.matches(safeDayCount)) return tier.adjustment();
			}
			return tiers.isEmpty() ? 0 : tiers.get(tiers.size() - 1).adjustment();
		}

		private record TimeTier(int minDay, int maxDay, int adjustment) {
			private boolean matches(long dayCount) {
				return dayCount >= minDay && dayCount <= maxDay;
			}
		}
	}
	}
