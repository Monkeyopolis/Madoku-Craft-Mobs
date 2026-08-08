package madoku.craft.mobs.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.mobs.mixin.CreeperAccessor;
import madoku.craft.mobs.mixin.CreeperPoweredAccessor;
import madoku.craft.mobs.mixin.MobExperienceAccessor;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.concurrent.ConcurrentHashMap;

public final class MobEntityManager {

	public interface DifficultyState {
		int madokuCraft$getSpawnDifficultyAdjustment();

		void madokuCraft$setSpawnDifficultyAdjustment(int adjustment);

		boolean madokuCraft$isWorldDifficultyScalingApplied();

		void madokuCraft$setWorldDifficultyScalingApplied(boolean applied);
	}
	private static final String TASK_TYPE_MOB_RUNTIME_TICK = "mob_runtime_tick";
	private static final String MOB_SCHEDULER_OWNER_ID = "madoku-mob-runtime";
	private static final double CREEPER_POWER_PER_DAMAGE = 0.2D;
	private static final double MIN_HOMING_SPEED = 0.75D;
	private static final int HOMING_LIFETIME_TICKS = 60;
	private static final int MOB_ARROW_LIFETIME_TICKS = 15 * 20;
	private static final String HOMING_PROJECTILE_TAG = "madoku-craft.projectile.homing";
	private static final String NESTED_VARIANT_TAG_PREFIX = "madoku-craft.nested-variant:";
	private static final String FIELD_FLYING_SPEED = MobConfigManager.FIELD_FLYING_SPEED;

	private static final Map<UUID, HomingArrowState> HOMING_ARROWS = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> FIXED_ARROW_DAMAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> MANAGED_MOB_ARROWS = new ConcurrentHashMap<>();
	private static final java.util.Set<UUID> INVULNERABILITY_BYPASS_ARROWS = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, EntitySpawnReason> PENDING_CAVE_SPIDER_REPLACEMENTS = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingZombieReplacement> PENDING_ZOMBIE_REPLACEMENTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Entity> TRACKED_ENTITIES = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> CONFIGURED_MOB_BABY_STATES = new ConcurrentHashMap<>();
	private static final java.util.Set<UUID> APPLIED_SPAWN_OVERRIDES = ConcurrentHashMap.newKeySet();

	private static volatile String runtimeSchedulerId = "";
	private static volatile boolean runtimeTaskScheduled = false;

	private MobEntityManager() {
	}

	public static void initialize() {
		MadokuSchedulerManager.registerTaskHandler(TASK_TYPE_MOB_RUNTIME_TICK, MobEntityManager::runRuntimeTask);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			TRACKED_ENTITIES.put(entity.getUUID(), entity);
			if (entity instanceof LivingEntity livingEntity) {
				boolean reappliedMobOverrides = applyLoadedEntityRules(livingEntity);
				EntityComponentsManager.applyMobBabyComponent(livingEntity);
				if (livingEntity instanceof AgeableMob ageableMob
					&& ageableMob.isBaby()
					&& EntityComponentsManager.resolveMobBabySettings(livingEntity).configured()) {
					requestRuntimeProcessing(world.getServer(), resolveRuntimeProcessingInterval(world.getServer()));
				}
				applyDifficultyScalingAfterMobOverrides(livingEntity, world, reappliedMobOverrides);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			TRACKED_ENTITIES.remove(entity.getUUID());
			CONFIGURED_MOB_BABY_STATES.remove(entity.getUUID());
			APPLIED_SPAWN_OVERRIDES.remove(entity.getUUID());
			cleanupEntityState(entity);
		});
	}

	public static void onServerStarted(MinecraftServer server) {
		MadokuSchedulerManager.clearAdaptiveDelayState(MOB_SCHEDULER_OWNER_ID);
		runtimeSchedulerId = "";
		runtimeTaskScheduled = false;
		HOMING_ARROWS.clear();
		FIXED_ARROW_DAMAGE.clear();
		MANAGED_MOB_ARROWS.clear();
		INVULNERABILITY_BYPASS_ARROWS.clear();
		PENDING_CAVE_SPIDER_REPLACEMENTS.clear();
		PENDING_ZOMBIE_REPLACEMENTS.clear();
		TRACKED_ENTITIES.clear();
		CONFIGURED_MOB_BABY_STATES.clear();
		APPLIED_SPAWN_OVERRIDES.clear();
		EntityBehaviorsManager.BeeBehavior.resetRuntimeState();
		EntityBehaviorsManager.SkeletonBehavior.resetRuntimeState();
		EntityBehaviorsManager.WitherSkeletonBehavior.resetRuntimeState();
		EntityBehaviorsManager.StrayBehavior.resetRuntimeState();
		EntityBehaviorsManager.BoggedBehavior.resetRuntimeState();
		EntityBehaviorsManager.ParchedBehavior.resetRuntimeState();
		runtimeSchedulerId = MadokuSchedulerManager.createOrGetScheduler(MadokuSchedulerManager.SchedulerBinding.global(MOB_SCHEDULER_OWNER_ID));
		MadokuSchedulerManager.clearQueuedRequests(runtimeSchedulerId);
		requestRuntimeProcessing(server, resolveRuntimeProcessingInterval(server));
	}

	public static void onServerTick(MinecraftServer server) {
		requestRuntimeProcessing(server, resolveRuntimeProcessingInterval(server));
	}

	public static void onServerStopped() {
		MadokuSchedulerManager.clearAdaptiveDelayState(MOB_SCHEDULER_OWNER_ID);
		runtimeSchedulerId = "";
		runtimeTaskScheduled = false;
		HOMING_ARROWS.clear();
		FIXED_ARROW_DAMAGE.clear();
		MANAGED_MOB_ARROWS.clear();
		INVULNERABILITY_BYPASS_ARROWS.clear();
		PENDING_CAVE_SPIDER_REPLACEMENTS.clear();
		PENDING_ZOMBIE_REPLACEMENTS.clear();
		TRACKED_ENTITIES.clear();
		CONFIGURED_MOB_BABY_STATES.clear();
		APPLIED_SPAWN_OVERRIDES.clear();
		EntityBehaviorsManager.BeeBehavior.resetRuntimeState();
		EntityBehaviorsManager.SkeletonBehavior.resetRuntimeState();
		EntityBehaviorsManager.WitherSkeletonBehavior.resetRuntimeState();
		EntityBehaviorsManager.StrayBehavior.resetRuntimeState();
		EntityBehaviorsManager.BoggedBehavior.resetRuntimeState();
		EntityBehaviorsManager.ParchedBehavior.resetRuntimeState();
	}

	public static boolean isEnabled() {
		return MobConfigManager.isEnabled();
	}

	static boolean isDifficultyScalingEligible(LivingEntity entity) {
		if (!(entity instanceof Mob mob)) {
			return false;
		}
		return mob instanceof Enemy || !resolveRegionalDifficultyMobFileKey(mob).isBlank();
	}
	public static void applyMobSpawnOverridesAfterVanilla(
		Mob mob,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (mob == null || !APPLIED_SPAWN_OVERRIDES.add(mob.getUUID())) {
			return;
		}
		if (spawnReason != EntitySpawnReason.JOCKEY) {
			selectConfiguredNestedVariantForRuntime(mob, world.getRandom());
		}
		EntitySpawnRulesManager.applyAfterVanilla(mob, world, difficulty, spawnReason);
		if (hasPendingAlternativeReplacement(mob)) {
			return;
		}
		applyConfiguredEquipmentAtVanillaSpawn(mob, world.getRandom());
		applyConfiguredJockeyAtVanillaSpawn(mob, world, difficulty, spawnReason);
		EntityComponentsManager.applyMobBabyComponent(mob);
		if (isDifficultyScalingEligible(mob)) {
			if (MobRegionalDifficultyManager.isEnabled() && isRegionalDifficultyScalingEnabledForRuntime(mob)) {
				MobRegionalDifficultyManager.applySpawnScalingIfUnscaled(mob, world);
			}
			EntityComponentsManager.applyWorldDifficultyScaling(mob);
			MobRegionalDifficultyManager.roundFinalScalingValues(mob);
		}
	}

	private static boolean hasPendingAlternativeReplacement(Entity entity) {
		return entity != null
			&& (PENDING_CAVE_SPIDER_REPLACEMENTS.containsKey(entity.getUUID())
				|| PENDING_ZOMBIE_REPLACEMENTS.containsKey(entity.getUUID()));
	}

	public static boolean shouldSuppressVanillaJockey(Mob mob) {
		return mob != null
			&& MobConfigManager.isEnabled()
			&& shouldApplyConfiguredSpawnRulesForRuntime(mob);
	}

	private static void applyConfiguredEquipmentAtVanillaSpawn(Mob mob, RandomSource random) {
		if (mob == null || random == null || !EquipmentConfigManager.isCustomEntityEquipmentEnabled()) {
			return;
		}

		String configuredFileKey = resolveRuntimeMobFileKey(mob);
		String equipmentReference = "";
		if (!configuredFileKey.isBlank()) {
			if (!MobConfigManager.isEnabled() || !shouldApplyConfiguredSpawnRulesForRuntime(mob)) {
				return;
			}
			JsonObject variant = resolveConfiguredEntityVariantForRuntime(mob);
			JsonObject spawnRules = readObject(variant, MobConfigManager.FIELD_SPAWN_RULES);
			JsonObject equipmentSet = readObject(spawnRules, MobConfigManager.FIELD_EQUIPMENT_SET);
			if (equipmentSet.entrySet().isEmpty() || !readBoolean(equipmentSet, MobConfigManager.FIELD_ENABLED, false)) {
				return;
			}
			equipmentReference = readString(equipmentSet, MobConfigManager.FIELD_MOB_EQUIPMENT, "");
		}

		EquipmentConfigManager.EquipmentProfile profile = EquipmentConfigManager.resolveProfile(equipmentReference, mob.getType());
		if (profile == null || !profile.enabled()) {
			return;
		}

		Map<EquipmentSlot, ItemStack> selected = EquipmentConfigManager.rollEquipment(profile, random);
		if (selected.isEmpty()) {
			clearEquipmentSlots(mob);
			return;
		}
		clearEquipmentSlots(mob);
		for (Map.Entry<EquipmentSlot, ItemStack> entry : selected.entrySet()) {
			mob.setItemSlot(entry.getKey(), entry.getValue());
		}
	}

	private static void applyConfiguredJockeyAtVanillaSpawn(
		Mob mob,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (mob == null || world == null || difficulty == null || !shouldApplyConfiguredSpawnRulesForRuntime(mob)) {
			return;
		}
		if (spawnReason == EntitySpawnReason.JOCKEY) {
			return;
		}
		if (mob instanceof Spider spider) {
			if (spider.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.SPIDER) {
				applyConfiguredMobJockey(spider, world, difficulty, resolveConfiguredEntityVariantForRuntime(spider), spawnReason);
			}
			return;
		}
		if (mob instanceof Husk husk) {
			applyConfiguredMobJockey(husk, world, difficulty, resolveConfiguredEntityVariantForRuntime(husk), spawnReason);
			return;
		}
		if (mob instanceof Zombie zombie && !(zombie instanceof ZombieVillager)) {
			applyConfiguredMobJockey(zombie, world, difficulty, resolveConfiguredEntityVariantForRuntime(zombie), spawnReason);
			return;
		}
		if (mob instanceof AbstractSkeleton skeleton) {
			applyConfiguredMobJockey(skeleton, world, difficulty, resolveConfiguredEntityVariantForRuntime(skeleton), spawnReason);
		}
	}

	public static void selectConfiguredTopLevelVariantForRuntime(
		LivingEntity entity,
		RandomSource random,
		EntitySpawnReason spawnReason
	) {
		if (entity == null || random == null || !MobConfigManager.isEnabled()) {
			return;
		}
		if (spawnReason == EntitySpawnReason.JOCKEY) {
			return;
		}
		String fileKey = resolveRegionalDifficultyMobFileKey(entity);
		if (fileKey.isBlank() || !isMobFileEnabled(fileKey)) {
			return;
		}
		JsonObject fileRoot = root(fileKey);
		JsonObject fileConfigRoot = resolveMobFileConfigRootForRuntime(fileKey);
		if (!readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)) {
			return;
		}

		String storedVariant = readStoredVariantKeyForRuntime(entity, fileKey);
		if (storedVariant.isBlank()) {
			String selectedVariant = selectWeightedVariantKey(
				fileRoot,
				random,
				normalizedKey -> false,
				variantRoot -> resolveVariantSpawnWeight(variantRoot, 0.0D)
			);
			String resolvedVariant = selectedVariant.isBlank()
				? EntityConfigManager.resolvePrimaryVariantKeyForRuntime(fileRoot)
				: selectedVariant;
			if (!resolvedVariant.isBlank()) {
				writeStoredVariantKeyForRuntime(entity, fileKey, resolvedVariant);
			}
		}
	}

	static void selectConfiguredNestedVariantForRuntime(LivingEntity entity, RandomSource random) {
		if (entity == null || random == null || !MobConfigManager.isEnabled()) {
			return;
		}
		String fileKey = resolveRegionalDifficultyMobFileKey(entity);
		if (fileKey.isBlank() || !isMobFileEnabled(fileKey)) {
			return;
		}
		JsonObject fileRoot = root(fileKey);
		JsonObject fileConfigRoot = resolveMobFileConfigRootForRuntime(fileKey);
		if (!readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)) {
			return;
		}

		JsonObject variantCatalog = EntityConfigManager.resolvePrimaryVariant(fileRoot);
		JsonObject variantGroup = EntityConfigManager.resolvePrimaryVariantOnly(fileRoot);
		String storedVariant = readStoredVariantKeyForRuntime(entity, fileKey);
		if (!storedVariant.isBlank()) {
			JsonObject selectedVariant = readObject(variantCatalog, storedVariant);
			if (!selectedVariant.entrySet().isEmpty()) {
				variantGroup = selectedVariant;
			}
		}
		resolveNestedVariantForRuntime(variantGroup, entity, random, true);
	}
	public static void applyZombieSpawnOverrides(
		Zombie zombie,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (zombie instanceof ZombieVillager zombieVillager) {
			EntityBehaviorsManager.ZombieVillagerBehavior.applySpawnOverrides(zombieVillager, world, difficulty, spawnReason);
			return;
		}
		EntityBehaviorsManager.ZombieBehavior.applySpawnOverrides(zombie, world, difficulty, spawnReason);
	}

	public static void applyDrownedSpawnOverrides(
		Drowned drowned,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (drowned == null || world == null || difficulty == null) {
			return;
		}
		EntityBehaviorsManager.DrownedBehavior.applySpawnOverrides(drowned, world, difficulty, spawnReason);
	}

	public static boolean applyConfiguredMobJockey(
		Mob sourceMob,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		JsonObject variantRoot,
		EntitySpawnReason spawnReason
	) {
		if (sourceMob == null || world == null || difficulty == null || variantRoot == null || !sourceMob.isAlive() || sourceMob.getVehicle() != null) {
			return false;
		}

		JsonObject spawnRules = readObject(variantRoot, MobConfigManager.FIELD_SPAWN_RULES);
		JsonObject jockeyRoot = readObject(spawnRules, MobConfigManager.FIELD_MOB_JOCKEY);
		if (jockeyRoot.entrySet().isEmpty() || !readBoolean(jockeyRoot, MobConfigManager.FIELD_ENABLED, false)) {
			return false;
		}

		JsonObject passengersRoot = readObject(jockeyRoot, MobConfigManager.FIELD_JOCKEY_PASSENGERS);
		JsonObject mountRoot = readObject(jockeyRoot, MobConfigManager.FIELD_JOCKEY_MOUNT);
		List<JsonObject> passengerDefinitions = readConfiguredJockeyPassengers(passengersRoot);
		if (passengerDefinitions.isEmpty() || passengerDefinitions.size() > 2) {
			return false;
		}

		EntityType<?> mountType = resolveConfiguredJockeyMobEntityType(mountRoot);
		if (mountType == null) {
			return false;
		}
		List<EntityType<?>> passengerTypes = new ArrayList<>();
		for (JsonObject passengerDefinition : passengerDefinitions) {
			EntityType<?> passengerType = resolveConfiguredJockeyMobEntityType(passengerDefinition);
			if (passengerType == null) {
				return false;
			}
			passengerTypes.add(passengerType);
		}

		ServerLevel level = world.getLevel();
		EntitySpawnReason jockeyReason = EntitySpawnReason.JOCKEY;
		int sourcePassengerIndex = -1;
		for (int index = 0; index < passengerTypes.size(); index++) {
			if (passengerTypes.get(index) == sourceMob.getType()) {
				sourcePassengerIndex = index;
				break;
			}
		}

		boolean sourceIsMount = sourceMob.getType() == mountType && sourcePassengerIndex < 0;
		if (sourceIsMount) {
			applyConfiguredJockeyMobWeapon(sourceMob, mountRoot);
			boolean applied = false;
			for (JsonObject passengerDefinition : passengerDefinitions) {
				Entity passenger = spawnConfiguredJockeyPartner(sourceMob, world, difficulty, level, jockeyReason,
					resolveConfiguredJockeyMobEntityType(passengerDefinition), passengerDefinition);
				if (passenger == null) {
					continue;
				}
				if (!passenger.startRiding(sourceMob) && passenger.isAlive()) {
					passenger.discard();
					continue;
				}
				applied = true;
			}
			return applied;
		}

		if (sourcePassengerIndex < 0) {
			return false;
		}
		applyConfiguredJockeyMobWeapon(sourceMob, passengerDefinitions.get(sourcePassengerIndex));
		Entity mount = spawnConfiguredJockeyPartner(sourceMob, world, difficulty, level, jockeyReason, mountType, mountRoot);
		if (mount == null || (!sourceMob.startRiding(mount) && sourceMob.isAlive())) {
			if (mount != null && mount.isAlive()) {
				mount.discard();
			}
			return false;
		}
		for (int index = 0; index < passengerDefinitions.size(); index++) {
			if (index == sourcePassengerIndex) {
				continue;
			}
			JsonObject passengerDefinition = passengerDefinitions.get(index);
			Entity passenger = spawnConfiguredJockeyPartner(sourceMob, world, difficulty, level, jockeyReason,
				passengerTypes.get(index), passengerDefinition);
			if (passenger != null && !passenger.startRiding(mount) && passenger.isAlive()) {
				passenger.discard();
			}
		}
		return true;
	}

	static void queueZombieReplacement(Zombie zombie, EntityType<?> replacementType, EntitySpawnReason spawnReason) {
		if (zombie == null || replacementType == null || replacementType == madoku.craft.mobs.mob.MadokuMobEntityTypes.ZOMBIE || spawnReason == null) {
			return;
		}
		PENDING_ZOMBIE_REPLACEMENTS.put(zombie.getUUID(), new PendingZombieReplacement(replacementType, spawnReason));
	}

	static void queueCaveSpiderReplacement(Spider spider, EntitySpawnReason spawnReason) {
		if (spider == null || spawnReason == null || spider.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.SPIDER) {
			return;
		}
		PENDING_CAVE_SPIDER_REPLACEMENTS.put(spider.getUUID(), spawnReason);
	}

	public static boolean replacePendingEntityBeforeVanillaAdd(ServerLevel level, Entity source) {
		if (level == null || source == null) {
			return false;
		}

		EntitySpawnReason spawnReason = null;
		EntityType<?> replacementType = null;
		if (source instanceof Zombie) {
			PendingZombieReplacement replacement = PENDING_ZOMBIE_REPLACEMENTS.remove(source.getUUID());
			if (replacement != null) {
				replacementType = replacement.replacementType();
				spawnReason = replacement.reason();
			}
		} else if (source instanceof Spider) {
			spawnReason = PENDING_CAVE_SPIDER_REPLACEMENTS.remove(source.getUUID());
			if (spawnReason != null) {
				replacementType = madoku.craft.mobs.mob.MadokuMobEntityTypes.CAVE_SPIDER;
			}
		}
		if (replacementType == null) {
			return false;
		}

		Entity replacement = replacementType.create(level, spawnReason == null ? EntitySpawnReason.NATURAL : spawnReason);
		if (replacement == null) {
			return false;
		}
		replacement.setPos(source.getX(), source.getY(), source.getZ());
		replacement.setYRot(source.getYRot());
		replacement.setXRot(source.getXRot());
		if (replacement instanceof Zombie replacementZombie && source instanceof Zombie zombie) {
			replacementZombie.setBaby(zombie.isBaby());
		}
		if (replacement instanceof Mob mob) {
			EntitySpawnReason reason = spawnReason == null ? EntitySpawnReason.NATURAL : spawnReason;
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(source.position())), reason, null);
		}
		if (!level.tryAddFreshEntityWithPassengers(replacement)) {
			replacement.discard();
			return false;
		}
		source.discard();
		return true;
	}

	public static boolean applySpiderSpawnOverrides(
		Spider spider,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		return EntityBehaviorsManager.SpiderBehavior.applySpawnOverrides(spider, world, difficulty, spawnReason);
	}

	public static EntityType<?> resolveConfiguredMobEntityType(JsonObject mobRoot) {
		return resolveConfiguredMobEntityType(mobRoot, false);
	}

	public static EntityType<?> resolveConfiguredMobEntityType(JsonObject mobRoot, boolean preferBabyVariant) {
		if (mobRoot == null || mobRoot.entrySet().isEmpty()) {
			return null;
		}
		JsonElement mobElement = mobRoot.get(MobConfigManager.FIELD_MOB);
		if (mobElement == null || mobElement.isJsonNull()) {
			return null;
		}

		String mobId = "";
		if (mobElement.isJsonPrimitive() && mobElement.getAsJsonPrimitive().isString()) {
			mobId = mobElement.getAsString();
		} else if (mobElement.isJsonObject()) {
			JsonObject byAge = mobElement.getAsJsonObject();
			String primaryKey = preferBabyVariant ? MobConfigManager.FIELD_BABY_GROUP : MobConfigManager.FIELD_ADULT_GROUP;
			String fallbackKey = preferBabyVariant ? MobConfigManager.FIELD_ADULT_GROUP : MobConfigManager.FIELD_BABY_GROUP;
			mobId = readString(byAge, primaryKey, "");
			if (mobId.isBlank()) {
				mobId = readString(byAge, fallbackKey, "");
			}
		}
		return resolveEntityTypeById(mobId);
	}

	private static List<JsonObject> readConfiguredJockeyPassengers(JsonObject passengersRoot) {
		List<JsonObject> passengers = new ArrayList<>();
		if (passengersRoot == null || passengersRoot.entrySet().isEmpty()) {
			return passengers;
		}
		JsonElement mobsElement = passengersRoot.get(MobConfigManager.FIELD_JOCKEY_MOBS);
		if (mobsElement == null || mobsElement.isJsonNull()) {
			return passengers;
		}
		if (mobsElement.isJsonArray()) {
			JsonArray mobs = mobsElement.getAsJsonArray();
			for (JsonElement mobElement : mobs) {
				if (mobElement != null && mobElement.isJsonObject()) {
					passengers.add(mobElement.getAsJsonObject());
				}
			}
			return passengers;
		}
		if (mobsElement.isJsonObject()) {
			JsonObject mobs = mobsElement.getAsJsonObject();
			if (mobs.has(MobConfigManager.FIELD_MOB_ID)) {
				passengers.add(mobs);
				return passengers;
			}
			for (Map.Entry<String, JsonElement> entry : mobs.entrySet()) {
				JsonElement mobElement = entry.getValue();
				if (mobElement != null && mobElement.isJsonObject()) {
					passengers.add(mobElement.getAsJsonObject());
				}
			}
		}
		return passengers;
	}

	private static EntityType<?> resolveConfiguredJockeyMobEntityType(JsonObject mobDefinition) {
		if (mobDefinition == null || mobDefinition.entrySet().isEmpty()) {
			return null;
		}
		return resolveEntityTypeById(readString(mobDefinition, MobConfigManager.FIELD_MOB_ID, ""));
	}

	public static void applySkeletonSpawnOverrides(
		AbstractSkeleton skeleton,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (skeleton == null || world == null || difficulty == null || !MobConfigManager.isEnabled()) {
			return;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON) {
			EntityBehaviorsManager.WitherSkeletonBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
			return;
		}
		if (!isSupportedSkeletonRuntimeType(skeleton)) {
			return;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY) {
			EntityBehaviorsManager.StrayBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED) {
			EntityBehaviorsManager.BoggedBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED) {
			EntityBehaviorsManager.ParchedBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else {
			EntityBehaviorsManager.SkeletonBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		}
	}

	public static boolean applyCustomSkeletonRangedAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
		if (skeleton == null) {
			return false;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY) {
			return EntityBehaviorsManager.StrayBehavior.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED) {
			return EntityBehaviorsManager.BoggedBehavior.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED) {
			return EntityBehaviorsManager.ParchedBehavior.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON) {
			return EntityBehaviorsManager.SkeletonBehavior.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
		}
		return EntityBehaviorsManager.SkeletonBehavior.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
	}

	public static int resolveSkeletonRangedAttackIntervalTicks(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return -1;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY) {
			return EntityBehaviorsManager.StrayBehavior.resolveBowAttackIntervalTicks(skeleton);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED) {
			return EntityBehaviorsManager.BoggedBehavior.resolveBowAttackIntervalTicks(skeleton);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED) {
			return EntityBehaviorsManager.ParchedBehavior.resolveBowAttackIntervalTicks(skeleton);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON) {
			return EntityBehaviorsManager.SkeletonBehavior.resolveBowAttackIntervalTicks(skeleton);
		}
		return EntityBehaviorsManager.SkeletonBehavior.resolveBowAttackIntervalTicks(skeleton);
	}

	public static int resolveSkeletonChargeUpTicks(Monster attacker) {
		if (attacker instanceof AbstractSkeleton skeleton) {
			if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY) {
				return EntityBehaviorsManager.StrayBehavior.resolveBowChargeUpTicks(skeleton);
			}
			if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED) {
				return EntityBehaviorsManager.BoggedBehavior.resolveBowChargeUpTicks(skeleton);
			}
			if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED) {
				return EntityBehaviorsManager.ParchedBehavior.resolveBowChargeUpTicks(skeleton);
			}
			if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON) {
				return EntityBehaviorsManager.SkeletonBehavior.resolveBowChargeUpTicks(skeleton);
			}
			return EntityBehaviorsManager.SkeletonBehavior.resolveBowChargeUpTicks(attacker);
		}
		return EntityBehaviorsManager.SkeletonBehavior.resolveBowChargeUpTicks(attacker);
	}

	public static void applyWitherSkeletonArrowHitEffect(LivingEntity target, Entity attacker) {
		EntityBehaviorsManager.WitherSkeletonBehavior.applyWitherSkeletonHitEffect(target, attacker);
	}

	public static boolean applyWitherSkeletonMeleeHitEffect(LivingEntity target, Entity attacker) {
		return EntityBehaviorsManager.WitherSkeletonBehavior.applyWitherSkeletonHitEffect(target, attacker);
	}

	public static void applySkeletonArrowHitEffect(LivingEntity target, Entity attacker) {
		if (target == null || attacker == null || target.level().isClientSide() || !MobConfigManager.isEnabled()) {
			return;
		}
		if (!(attacker instanceof AbstractSkeleton skeleton)) {
			return;
		}
		JsonObject runtimeRoot = resolveSkeletonVariantRuntimeRoot(skeleton);
		if (runtimeRoot.entrySet().isEmpty()) {
			return;
		}
		MobEffectInstance configuredEffect = resolveConfiguredMobEffectInstance(readMobComponentsRoot(runtimeRoot), null);
		if (configuredEffect != null) {
			target.addEffect(configuredEffect, skeleton);
		}
	}

	public static void handleMobDamaged(LivingEntity victim, DamageSource source) {
		if (victim == null || source == null || !MobConfigManager.isEnabled() || victim.level().isClientSide()) {
			return;
		}
		LivingEntity attacker = resolveDamageSourceLivingAttacker(source);
		if (attacker == null || !attacker.isAlive() || attacker == victim) {
			return;
		}
		if (!(victim.level() instanceof ServerLevel)) {
			return;
		}

	}

	public static float resolveProjectileDamageOverride(AbstractArrow arrow, float fallbackDamage) {
		if (arrow == null) {
			return fallbackDamage;
		}
		Float fixed = FIXED_ARROW_DAMAGE.get(arrow.getUUID());
		if (fixed != null) {
			return Math.max(0.0F, fixed);
		}
		if (arrow.getOwner() instanceof AbstractSkeleton skeleton && MobConfigManager.isEnabled() && isBowAttackEnabledForRuntimeSkeleton(skeleton)) {
			JsonObject root = resolveSkeletonVariantRuntimeRoot(skeleton);
			if (!root.entrySet().isEmpty()) {
				return (float) Math.max(0.0D, resolveSkeletonRangedDamage(skeleton, root));
			}
		}
		return fallbackDamage;
	}

	public static void setProjectileDamageOverride(AbstractArrow arrow, float damage) {
		if (arrow == null) {
			return;
		}
		FIXED_ARROW_DAMAGE.put(arrow.getUUID(), Math.max(0.0F, damage));
	}

	public static void startProjectileHoming(AbstractArrow arrow, LivingEntity target, MinecraftServer server) {
		if (arrow == null || target == null || server == null) {
			return;
		}
		double homingSpeed = Math.max(MIN_HOMING_SPEED, arrow.getDeltaMovement().length());
		arrow.setNoGravity(true);
		HOMING_ARROWS.put(arrow.getUUID(), new HomingArrowState(target.getUUID(), homingSpeed, HOMING_LIFETIME_TICKS));
		requestRuntimeProcessing(server, resolveRuntimeProcessingInterval(server));
	}

	public static void clearProjectileHoming(AbstractArrow arrow) {
		if (arrow == null) {
			return;
		}
		HOMING_ARROWS.remove(arrow.getUUID());
		if (arrow.isAlive()) {
			arrow.setNoGravity(false);
		}
	}

	public static boolean spawnManagedHomingArrow(
		LivingEntity shooter,
		LivingEntity target,
		Vec3 spawnPosition,
		float speed,
		float damage
	) {
		if (shooter == null || target == null || !target.isAlive() || spawnPosition == null || !(shooter.level() instanceof ServerLevel level)) {
			return false;
		}

		Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		arrow.setPos(spawnPosition.x, spawnPosition.y, spawnPosition.z);
		Vec3 desired = target.getEyePosition().subtract(spawnPosition);
		if (desired.lengthSqr() <= 1.0E-6D) {
			desired = shooter.getLookAngle();
		}
		arrow.shoot(desired.x, desired.y, desired.z, Math.max(0.1F, speed), 0.0F);
		arrow.setCritArrow(false);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		FIXED_ARROW_DAMAGE.put(arrow.getUUID(), Math.max(0.0F, damage));
		INVULNERABILITY_BYPASS_ARROWS.add(arrow.getUUID());
		trackManagedMobArrow(arrow, level.getServer());
		double homingSpeed = Math.max(MIN_HOMING_SPEED, arrow.getDeltaMovement().length());
		arrow.setNoGravity(true);
		arrow.addTag(HOMING_PROJECTILE_TAG);
		HOMING_ARROWS.put(arrow.getUUID(), new HomingArrowState(target.getUUID(), homingSpeed, HOMING_LIFETIME_TICKS));
		requestRuntimeProcessing(level.getServer(), resolveRuntimeProcessingInterval(level.getServer()));
		shooter.level().addFreshEntity(arrow);
		return true;
	}

	public static boolean shouldBypassInvulnerability(AbstractArrow arrow) {
		return arrow != null && INVULNERABILITY_BYPASS_ARROWS.contains(arrow.getUUID());
	}

	public static boolean isManagedHomingArrow(AbstractArrow arrow) {
		return arrow != null && (HOMING_ARROWS.containsKey(arrow.getUUID()) || arrow.entityTags().contains(HOMING_PROJECTILE_TAG));
	}

	public static void clearInvulnerabilityBypass(AbstractArrow arrow) {
		if (arrow != null) {
			INVULNERABILITY_BYPASS_ARROWS.remove(arrow.getUUID());
		}
	}

	public static void applyCreeperExplosionOverride(
		Creeper creeper,
		ServerLevel level,
		Entity source,
		double x,
		double y,
		double z,
		float vanillaPower,
		Level.ExplosionInteraction vanillaInteraction
	) {
		if (level == null || creeper == null || !MobConfigManager.isEnabled()) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER) || !shouldUseCreeperMobExplodeBehavior(creeper, root)) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, root);
		JsonObject mobExplode = resolveCreeperMobExplodeRoot(variant);
		Double baseChance = clampOptional(readOptionalDouble(mobExplode, MobConfigManager.FIELD_DESTRUCTION_CHANCE), 0.0D, 1.0D);
		if (baseChance == null) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		double chance = Mth.clamp(
			resolveDifficultyAdjustedValue(
				level.getDifficulty(),
				isHardcoreWorld(level),
				baseChance,
				0.2D,
				0.0D
			),
			0.0D,
			1.0D
		);
		float power = (float) resolveCreeperExplosionPower(creeper, root, variant, vanillaPower);
		Level.ExplosionInteraction interaction = level.getRandom().nextDouble() < chance
			? Level.ExplosionInteraction.MOB
			: Level.ExplosionInteraction.NONE;
		level.explode(source, x, y, z, power, interaction);
	}

	public static float resolveCreeperGriefExplosionRadius(ServerExplosion explosion, float fallbackRadius) {
		if (explosion == null || !(explosion.getDirectSourceEntity() instanceof Creeper creeper) || !MobConfigManager.isEnabled()) {
			return Math.max(0.0F, fallbackRadius);
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER) || !shouldUseCreeperMobExplodeBehavior(creeper, root)) {
			return Math.max(0.0F, fallbackRadius);
		}
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, root);
		JsonObject mobExplode = resolveCreeperMobExplodeRoot(variant);
		Double griefPower = readOptionalDouble(mobExplode, MobConfigManager.FIELD_GREIF_POWER);
		return (float) (Math.max(0.0F, fallbackRadius)
			* Mth.clamp(griefPower == null ? 0.5D : griefPower, 0.0D, 1.0D));
	}

	public static float resolveFixedPlayerExplosionDamage(Creeper creeper, float fallbackExplosionRadius) {
		double explosionPower = Math.max(0.0D, fallbackExplosionRadius);
		if (creeper == null || !MobConfigManager.isEnabled()) {
			return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER) || !shouldUseCreeperMobExplodeBehavior(creeper, root)) {
			return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
		}
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, root);
		explosionPower = resolveCreeperExplosionPower(creeper, root, variant, explosionPower);
		return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
	}

	public static void ensureBowEquipped(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY) {
			EntityBehaviorsManager.StrayBehavior.ensureBowEquipped(skeleton);
			return;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED) {
			EntityBehaviorsManager.BoggedBehavior.ensureBowEquipped(skeleton);
			return;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED) {
			EntityBehaviorsManager.ParchedBehavior.ensureBowEquipped(skeleton);
			return;
		}
		EntityBehaviorsManager.SkeletonBehavior.ensureBowEquipped(skeleton);
	}

	public static boolean shouldSkeletonMeleeIgnoreArmor(DamageSource source) {
		if (!MobConfigManager.isEnabled() || source == null) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof AbstractSkeleton skeleton) || source.getDirectEntity() != attacker) {
			return false;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON) {
			return false;
		}
		return resolveBowHand(skeleton) == null;
	}

	public static boolean shouldBypassArmorForMobDamage(DamageSource source) {
		if (!MobConfigManager.isEnabled() || source == null) {
			return false;
		}
		if (shouldSkeletonMeleeIgnoreArmor(source)) {
			return true;
		}
		LivingEntity attacker = resolveDamageSourceLivingAttacker(source);
		if (attacker == null || !attacker.isAlive()) {
			return false;
		}
		return hasTrueDamageConfigured(attacker);
	}

	private static boolean applyLoadedEntityRules(LivingEntity entity) {
		boolean configuredComponentsApplied = shouldApplyConfiguredComponentsForRuntime(entity)
			&& EntityComponentsManager.applyConfiguredComponents(entity, resolveConfiguredEntityVariantForRuntime(entity));
		boolean configuredLoadedRulesApplied = applyConfiguredLoadedEntityRules(entity);
		EntityComponentsManager.applyMobBabyComponent(entity);
		if ((configuredComponentsApplied || configuredLoadedRulesApplied) && entity instanceof MobEntityManager.DifficultyState state) {
			state.madokuCraft$setWorldDifficultyScalingApplied(false);
		}
		return configuredComponentsApplied || configuredLoadedRulesApplied;
	}

	private static boolean applyConfiguredLoadedEntityRules(LivingEntity entity) {
		if (entity == null || entity.level().isClientSide() || !MobConfigManager.isEnabled()) {
			return false;
		}
		if (entity instanceof ZombieVillager zombieVillager) {
			return EntityBehaviorsManager.ZombieVillagerBehavior.applyLoadedEntityOverrides(zombieVillager);
		}
		if (entity instanceof Drowned drowned) {
			return EntityBehaviorsManager.DrownedBehavior.applyLoadedEntityOverrides(drowned);
		}
		if (entity instanceof Husk husk) {
			return EntityBehaviorsManager.HuskBehavior.applyLoadedEntityOverrides(husk);
		}
		if (entity instanceof Zombie zombie) {
			return EntityBehaviorsManager.ZombieBehavior.applyLoadedEntityOverrides(zombie);
		}
		if (entity instanceof Spider spider) {
			if (spider.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.CAVE_SPIDER) {
				return EntityBehaviorsManager.CaveSpiderBehavior.applyLoadedEntityOverrides(spider);
			}
			JsonObject root = fileMobRoot(MobConfigManager.FILE_SPIDER);
			return isMobFileEnabled(MobConfigManager.FILE_SPIDER) && applyUniversalBaseStatsForRuntime(spider, root);
		}
		if (entity instanceof AbstractSkeleton skeleton) {
			if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON) {
				return EntityBehaviorsManager.WitherSkeletonBehavior.applyLoadedEntityOverrides(skeleton);
			}
			if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY) {
				return EntityBehaviorsManager.StrayBehavior.applyLoadedEntityOverrides(skeleton);
			}
			if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED) {
				return EntityBehaviorsManager.BoggedBehavior.applyLoadedEntityOverrides(skeleton);
			}
			if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED) {
				return EntityBehaviorsManager.ParchedBehavior.applyLoadedEntityOverrides(skeleton);
			}
			return EntityBehaviorsManager.SkeletonBehavior.applyLoadedEntityOverrides(skeleton);
		}
		if (entity instanceof Creeper creeper) {
			return EntityBehaviorsManager.CreeperBehavior.applyLoadedEntityOverrides(creeper);
		}
			if (entity.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BEE) {
				return EntityBehaviorsManager.BeeBehavior.applyLoadedEntityOverrides(entity);
			}
			return false;
	}

	private static void applyDifficultyScalingAfterMobOverrides(LivingEntity entity, ServerLevel level, boolean loadedMobOverridesApplied) {
		if (!MobConfigManager.isEnabled() || entity == null || !(entity instanceof Mob mob)
			|| !isDifficultyScalingEligible(mob) || level == null) {
			return;
		}
		if (MobRegionalDifficultyManager.isEnabled() && isRegionalDifficultyScalingEnabledForMob(entity)) {
			if (loadedMobOverridesApplied && mob instanceof DifficultyState scaledMob && scaledMob.madokuCraft$getSpawnDifficultyAdjustment() > 0) {
				MobRegionalDifficultyManager.reapplySpawnScalingFromStoredAdjustment(mob);
			} else {
				MobRegionalDifficultyManager.applySpawnScalingIfUnscaled(mob, level);
			}
		}
		applyLoadedEntityDifficultyScaling(entity);
		MobRegionalDifficultyManager.roundFinalScalingValues(mob);
	}

	public static boolean isRegionalDifficultyScalingEnabledForRuntime(Mob mob) {
		return isDifficultyScalingEligible(mob) && isRegionalDifficultyScalingEnabledForMob(mob);
	}

	static double resolveWorldDifficultyValueForRuntime(LivingEntity entity, String attribute, double baseValue) {
		if (!isDifficultyScalingEligible(entity) || !EntityConfigManager.isWorldDifficultyScalingEnabled(
			resolveMobFileConfigRootForRuntime(resolveRuntimeMobFileKey(entity)))) {
			return MobConfigManager.roundDifficultyScaleValue(Math.max(0.0D, baseValue));
		}
		boolean hardcore = entity.level().getServer() != null && entity.level().getServer().isHardcore();
		double sanitizedBase = Math.max(0.0D, baseValue);
		double resolved = MobWorldDifficultyManager.resolveValue(attribute, sanitizedBase, entity.level().getDifficulty(), hardcore);
		return resolved;
	}

	private static void applyLoadedEntityDifficultyScaling(LivingEntity entity) {
		if (entity == null || entity.level().isClientSide() || !MobConfigManager.isEnabled()) {
			return;
		}
		EntityComponentsManager.applyWorldDifficultyScaling(entity);
		if (entity instanceof Creeper creeper) {
			EntityBehaviorsManager.CreeperBehavior.applyLoadedExplosionDifficultyScaling(creeper);
		}
	}

	static void applyCreeperSpawnOverrides(Creeper creeper, ServerLevelAccessor world, DifficultyInstance difficulty) {
		if (creeper == null || world == null || difficulty == null || !MobConfigManager.isEnabled() || creeper.isPowered()) {
			return;
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER)) {
			return;
		}
		JsonObject creeperCatalog = EntityConfigManager.resolvePrimaryVariant(root);
		JsonObject creeperVariant = EntityConfigManager.resolvePrimaryVariantOnly(root);
		JsonObject chargedVariant = readObject(creeperCatalog, MobConfigManager.FIELD_CHARGED_CREEPER);
		double regularWeight = Math.max(0.0D, readSpawnRuleDouble(creeperVariant, MobConfigManager.FIELD_SPAWN_WEIGHT, 95.0D));
		double specialWeight = Math.max(0.0D, readSpawnRuleDouble(chargedVariant, MobConfigManager.FIELD_SPAWN_WEIGHT, 5.0D));
		double total = regularWeight + specialWeight;
		if (total <= 0.0D) {
			return;
		}
		boolean charged = (world.getRandom().nextDouble() * total) >= regularWeight;
		if (charged) {
			creeper.getEntityData().set(CreeperPoweredAccessor.madokuCraft$getDataIsPowered(), true);
		}
	}

	static void applyBeeSpawnOverrides(Mob mob, ServerLevelAccessor world) {
		if (mob == null || world == null || !MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return;
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		JsonObject resolved = resolveBeeRoot(mob, beeFileRoot, world.getRandom(), true);
		applyBeeOverrides(mob, beeFileRoot, resolved);
	}

	static boolean applyBeeLoadedEntityOverrides(LivingEntity entity) {
		if (entity == null || entity.level().isClientSide() || !MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		JsonObject resolved = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		return applyBeeBaseOverrides(entity, beeFileRoot, resolved);
	}

	static boolean applyBeeBaseOverrides(LivingEntity entity, JsonObject beeFileRoot, JsonObject resolvedRoot) {
		if (entity == null || beeFileRoot == null || resolvedRoot == null || resolvedRoot.entrySet().isEmpty()) {
			return false;
		}
		boolean overrideStats = readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
		if (!overrideStats) {
			return false;
		}
		return applyUniversalBaseStatsForRuntime(entity, resolvedRoot);
	}

	static JsonObject resolveBeeBehaviorRoot(LivingEntity entity) {
		if (entity == null || !MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return new JsonObject();
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		if (!readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIORS, true)) {
			return new JsonObject();
		}
		JsonObject resolved = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		return readMobBehaviorRoot(resolved);
	}

	static boolean isBeeBehaviorOverrideEnabled() {
		if (!MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		return readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIORS, true);
	}

	static boolean isBeeGoalsOverrideEnabled() {
		if (!MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		return readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_GOALS, true);
	}

	public static boolean isBeeCustomMobDropsEnabled(LivingEntity entity) {
		if (entity == null
			|| entity.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.BEE
			|| !MobConfigManager.isEnabled()
			|| !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		JsonObject resolvedBeeRoot = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		return readBoolean(
			resolvedBeeRoot,
			MobConfigManager.FIELD_CUSTOM_MOB_DROPS,
			readBoolean(beeFileRoot, MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true)
		);
	}

	public static String resolveBeeMobDropsConfigReference(LivingEntity entity) {
		if (entity == null || entity.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.BEE) {
			return "";
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		JsonObject resolvedBeeRoot = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		JsonObject componentsRoot = readMobComponentsRoot(resolvedBeeRoot);
		return readString(componentsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
	}

	public static JsonObject resolveBeeRootForRuntime(LivingEntity entity) {
		if (entity == null || entity.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.BEE || !MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return new JsonObject();
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		return resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
	}

	public static MobEffectInstance resolveBeeAttackEffect(LivingEntity entity, MobEffectInstance fallbackEffect) {
		if (entity == null || entity.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.BEE || fallbackEffect == null) {
			return fallbackEffect;
		}
		JsonObject resolvedBeeRoot = resolveBeeRootForRuntime(entity);
		if (resolvedBeeRoot.entrySet().isEmpty()) {
			return fallbackEffect;
		}
		return resolveConfiguredMobEffectInstance(readMobComponentsRoot(resolvedBeeRoot), fallbackEffect);
	}

	public static boolean isZombieCustomMobDropsEnabled(LivingEntity entity) {
		if (entity instanceof ZombieVillager zombieVillager) {
			return EntityBehaviorsManager.ZombieVillagerBehavior.isCustomMobDropsEnabled(zombieVillager);
		}
		if (entity instanceof Drowned drowned) {
			return EntityBehaviorsManager.DrownedBehavior.isCustomMobDropsEnabled(drowned);
		}
		if (entity instanceof Husk husk) {
			return EntityBehaviorsManager.HuskBehavior.isCustomMobDropsEnabled(husk);
		}
		return EntityBehaviorsManager.ZombieBehavior.isCustomMobDropsEnabled(entity);
	}

	public static String resolveZombieMobDropsConfigReference(LivingEntity entity) {
		if (entity instanceof ZombieVillager zombieVillager) {
			return EntityBehaviorsManager.ZombieVillagerBehavior.resolveMobDropsConfigReference(zombieVillager);
		}
		if (entity instanceof Drowned drowned) {
			return EntityBehaviorsManager.DrownedBehavior.resolveMobDropsConfigReference(drowned);
		}
		if (entity instanceof Husk husk) {
			return EntityBehaviorsManager.HuskBehavior.resolveMobDropsConfigReference(husk);
		}
		return EntityBehaviorsManager.ZombieBehavior.resolveMobDropsConfigReference(entity);
	}

	private static boolean isRegionalDifficultyScalingEnabledForMob(LivingEntity entity) {
		if (entity == null) {
			return false;
		}
		String fileKey = resolveRegionalDifficultyMobFileKey(entity);
		return fileKey.isBlank() || EntityConfigManager.isRegionalDifficultyScalingEnabled(root(fileKey));
	}

	private static String resolveRegionalDifficultyMobFileKey(LivingEntity entity) {
		if (entity == null) {
			return "";
		}
		if (entity instanceof Spider spider) {
			return spider.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.CAVE_SPIDER ? MobConfigManager.FILE_CAVE_SPIDER : MobConfigManager.FILE_SPIDER;
		}
		if (entity instanceof AbstractSkeleton skeleton) {
			return skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.SKELETON ? MobConfigManager.FILE_SKELETON
				: skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY ? MobConfigManager.FILE_STRAY
				: skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED ? MobConfigManager.FILE_BOGGED
				: skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED ? MobConfigManager.FILE_PARCHED
				: skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON ? MobConfigManager.FILE_WITHER_SKELETON
				: "";
		}
		if (entity instanceof ZombieVillager) {
			return MobConfigManager.FILE_ZOMBIE_VILLAGER;
		}
		if (entity instanceof Drowned) {
			return MobConfigManager.FILE_DROWNED;
		}
		if (entity instanceof Zombie zombie) {
			return zombie.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.ZOMBIE ? MobConfigManager.FILE_ZOMBIE
				: zombie.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.HUSK ? MobConfigManager.FILE_HUSK
				: "";
		}
		if (entity instanceof Creeper) {
			return MobConfigManager.FILE_CREEPER;
		}
		if (entity.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BEE) {
			return MobConfigManager.FILE_BEE;
		}
		return "";
	}

	private static boolean applyBeeOverrides(LivingEntity entity, JsonObject beeFileRoot, JsonObject resolvedRoot) {
		if (entity == null || beeFileRoot == null || resolvedRoot == null || resolvedRoot.entrySet().isEmpty()) {
			return false;
		}
		boolean overrideStats = readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
		if (!overrideStats) {
			return false;
		}
		return applyUniversalBaseStatsForRuntime(entity, resolvedRoot);
	}

	static boolean applyUniversalBaseStatsForRuntime(LivingEntity entity, JsonObject root) {
		if (entity == null || root == null) {
			return false;
		}
		boolean modified = false;
		double oldMaxHealth = entity.getMaxHealth();
		JsonObject componentsRoot = readMobComponentsRoot(root);
		modified |= setBaseValueIfPresent(entity, Attributes.MAX_HEALTH, readOptionalPositive(componentsRoot, MobConfigManager.FIELD_HEALTH));
		modified |= setBaseValueIfPresent(entity, Attributes.ARMOR, readOptionalNonNegative(componentsRoot, MobConfigManager.FIELD_ARMOR));
		modified |= setBaseValueIfPresent(entity, Attributes.ATTACK_DAMAGE, readOptionalNonNegative(componentsRoot, MobConfigManager.FIELD_DAMAGE));
		modified |= setBaseValueIfPresent(entity, Attributes.MOVEMENT_SPEED, readOptionalPositive(componentsRoot, MobConfigManager.FIELD_MOVEMENT_SPEED));
		modified |= setBaseValueIfPresent(entity, Attributes.FLYING_SPEED, readOptionalPositive(componentsRoot, FIELD_FLYING_SPEED));
		modified |= setBaseValueIfPresent(entity, Attributes.WATER_MOVEMENT_EFFICIENCY, readOptionalNonNegative(componentsRoot, MobConfigManager.FIELD_SWIMMING_SPEED));
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.KNOCKBACK_RESISTANCE,
			clampOptional(readOptionalDouble(componentsRoot, MobConfigManager.FIELD_KNOCKBACK_RESISTANCE), 0.0D, 1.0D)
		);
		Double baseScale = readOptionalPositive(componentsRoot, MobConfigManager.FIELD_SCALE);
		if (baseScale != null) {
			modified |= setBaseValue(entity, Attributes.SCALE, baseScale);
		}
		Integer baseExperienceDrop = readOptionalIntNonNegative(componentsRoot, MobConfigManager.FIELD_EXPERIENCE_DROP);
		if (baseExperienceDrop != null) {
			applyExperienceDropForRuntime(entity, Math.max(0, baseExperienceDrop));
			modified = true;
		}
		modified |= applyConfiguredMobWeapon(entity, root);
		rescaleCurrentHealth(entity, oldMaxHealth);
		return modified;
	}

	static boolean applyCreeperLoadedEntityDifficultyOverrides(Creeper creeper) {
		if (creeper == null || creeper.level().isClientSide() || !MobConfigManager.isEnabled()) {
			return false;
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER)) {
			return false;
		}
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, root);
		Double explosionPower = readOptionalDouble(readMobComponentsRoot(variant), MobConfigManager.FIELD_EXPLOSION_POWER);
		if (explosionPower == null) {
			return false;
		}
		double resolvedPower = resolveCreeperExplosionPower(creeper, root, variant, explosionPower);
		CreeperAccessor accessor = (CreeperAccessor) creeper;
		accessor.madokuCraft$setExplosionRadius(Math.max(0, (int) Math.round(resolvedPower)));
		return true;
	}

	static boolean applyCreeperRuntimeStats(Creeper creeper, JsonObject root) {
		boolean modified = false;
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, root);
		if (variant.entrySet().isEmpty()) {
			return false;
		}
		modified |= applyUniversalBaseStatsForRuntime(creeper, variant);
		CreeperAccessor accessor = (CreeperAccessor) creeper;
		Double fuseLength = readOptionalPositive(readMobComponentsRoot(variant), MobConfigManager.FIELD_FUSE_LENGTH);
		if (fuseLength != null) {
			int fuse = Math.max(1, (int) Math.round(fuseLength));
			accessor.madokuCraft$setMaxSwell(fuse);
			if (accessor.madokuCraft$getSwell() > fuse) {
				accessor.madokuCraft$setSwell(fuse);
			}
			if (accessor.madokuCraft$getOldSwell() > fuse) {
				accessor.madokuCraft$setOldSwell(fuse);
			}
		}
		Double explosionPower = readOptionalDouble(readMobComponentsRoot(variant), MobConfigManager.FIELD_EXPLOSION_POWER);
		if (explosionPower != null) {
			double resolvedPower = resolveCreeperExplosionPower(creeper, root, variant, explosionPower);
			int radius = Math.max(0, (int) Math.round(resolvedPower));
			accessor.madokuCraft$setExplosionRadius(radius);
		}
		return modified;
	}

	private static JsonObject resolveCreeperRuntimeVariantRoot(Creeper creeper, JsonObject fileRoot) {
		if (creeper == null || fileRoot == null || fileRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		JsonObject creeperRoot = EntityConfigManager.resolvePrimaryVariantOnly(fileRoot);
		JsonObject creeperCatalog = EntityConfigManager.resolvePrimaryVariant(fileRoot);
		JsonObject defaultGroup = creeperRoot;
		if (defaultGroup.entrySet().isEmpty()) {
			return new JsonObject();
		}
		if (!creeper.isPowered()) {
			return defaultGroup;
		}
		JsonObject chargedVariant = readObject(creeperCatalog, MobConfigManager.FIELD_CHARGED_CREEPER);
		if (chargedVariant.entrySet().isEmpty()) {
			return defaultGroup;
		}
		return resolveVariantGroupRoot(defaultGroup, chargedVariant);
	}

	public static boolean shouldUseCreeperMobExplodeBehavior(Creeper creeper) {
		if (creeper == null || !MobConfigManager.isEnabled()) {
			return false;
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		return isMobFileEnabled(MobConfigManager.FILE_CREEPER) && shouldUseCreeperMobExplodeBehavior(creeper, root);
	}

	private static boolean shouldUseCreeperMobExplodeBehavior(Creeper creeper, JsonObject fileRoot) {
		JsonObject mobExplode = resolveCreeperMobExplodeRoot(creeper, fileRoot);
		return !mobExplode.entrySet().isEmpty() && readBoolean(mobExplode, MobConfigManager.FIELD_ENABLED, true);
	}

	private static JsonObject resolveCreeperMobExplodeRoot(Creeper creeper, JsonObject fileRoot) {
		if (creeper == null || fileRoot == null || fileRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, fileRoot);
		return resolveCreeperMobExplodeRoot(variant);
	}

	private static JsonObject resolveCreeperMobExplodeRoot(JsonObject variantRoot) {
		if (variantRoot == null || variantRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		return readObject(readMobBehaviorRoot(variantRoot), MobConfigManager.FIELD_MOB_EXPLODE);
	}

	private static double resolveCreeperExplosionPower(Creeper creeper, JsonObject fileRoot, JsonObject variantRoot, double fallbackPower) {
		double explosionPower = Math.max(0.0D, fallbackPower);
		if (creeper == null || fileRoot == null || variantRoot == null || variantRoot.entrySet().isEmpty()) {
			return explosionPower;
		}
		JsonObject componentsRoot = readMobComponentsRoot(variantRoot);
		Double configuredPower = readOptionalDouble(componentsRoot, MobConfigManager.FIELD_EXPLOSION_POWER);
		if (configuredPower != null) {
			explosionPower = Math.max(0.0D, configuredPower);
		}
		explosionPower = Math.max(0.0D, explosionPower + MobRegionalDifficultyManager.resolveCreeperExplosionPowerScaling(creeper, explosionPower));
		return resolveWorldDifficultyValueForRuntime(
			creeper,
			MobConfigManager.FIELD_EXPLOSION_POWER,
			explosionPower
		);
	}

	private static void disableZombieReinforcements(Zombie zombie) {
		AttributeInstance instance = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (instance == null) {
			return;
		}
		instance.removeModifiers();
		instance.setBaseValue(0.0D);
	}

	static void disableZombieReinforcementsForRuntime(Zombie zombie) {
		disableZombieReinforcements(zombie);
	}

	static void clearArmorSlotsForRuntime(Mob mob) {
		clearArmorSlots(mob);
	}

	static void clearEquipmentSlotsForRuntime(Mob mob) {
		clearEquipmentSlots(mob);
	}

	private static void clearArmorSlots(Mob mob) {
		if (mob == null) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
	}

	private static void clearEquipmentSlots(Mob mob) {
		clearArmorSlots(mob);
		if (mob == null) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
	}

	static void applyExperienceDropForRuntime(LivingEntity entity, Integer experienceDrop) {
		if (entity instanceof Mob mob && experienceDrop != null) {
			((MobExperienceAccessor) mob).madokuCraft$setXpReward(Math.max(0, experienceDrop));
		}
	}

	static int resolveMobExperienceDropForRuntime(LivingEntity entity) {
		if (entity instanceof Mob mob) {
			return ((MobExperienceAccessor) mob).madokuCraft$getXpReward();
		}
		return 0;
	}

	private static boolean setBaseValue(LivingEntity entity, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null || Double.compare(instance.getBaseValue(), value) == 0) {
			return false;
		}
		instance.setBaseValue(value);
		return true;
	}

	private static boolean setBaseValueIfPresent(LivingEntity entity, Holder<Attribute> attribute, Double value) {
		if (value != null) {
			return setBaseValue(entity, attribute, value);
		}
		return false;
	}

	private static void rescaleCurrentHealth(LivingEntity entity, double oldMaxHealth) {
		double newMax = entity.getMaxHealth();
		if (newMax <= 0.0D) {
			return;
		}
		float scaled = oldMaxHealth > 0.0D ? (float) (newMax * (entity.getHealth() / oldMaxHealth)) : (float) newMax;
		entity.setHealth(Math.min((float) newMax, Math.max(0.0F, scaled)));
	}

	private static void cleanupEntityState(Entity entity) {
		if (entity == null) {
			return;
		}
		UUID id = entity.getUUID();
		if (entity instanceof AbstractArrow) {
			HOMING_ARROWS.remove(id);
			FIXED_ARROW_DAMAGE.remove(id);
			MANAGED_MOB_ARROWS.remove(id);
		}
		PENDING_CAVE_SPIDER_REPLACEMENTS.remove(id);
		PENDING_ZOMBIE_REPLACEMENTS.remove(id);
		EntityBehaviorsManager.SkeletonBehavior.onEntityCleanup(entity);
		EntityBehaviorsManager.WitherSkeletonBehavior.onEntityCleanup(entity);
		EntityBehaviorsManager.StrayBehavior.onEntityCleanup(entity);
		EntityBehaviorsManager.BoggedBehavior.onEntityCleanup(entity);
		EntityBehaviorsManager.ParchedBehavior.onEntityCleanup(entity);
		EntityBehaviorsManager.BeeBehavior.onEntityCleanup(entity);
		CONFIGURED_MOB_BABY_STATES.remove(entity.getUUID());
	}

	private static void runRuntimeTask(MinecraftServer server, MadokuSchedulerManager.TaskContext context, JsonObject payload) {
		if (context != null) {
			runtimeSchedulerId = context.getSchedulerId();
		}
		runtimeTaskScheduled = false;
		tickManagedMobArrows(server);
		tickHomingProjectiles(server);
		boolean beeRuntimeActive = EntityBehaviorsManager.BeeBehavior.tickRuntime(
			server,
			TRACKED_ENTITIES.values(),
			MobConfigManager.isEnabled(),
			isMobFileEnabled(MobConfigManager.FILE_BEE)
		);
		boolean mobBabyRuntimeActive = tickConfiguredMobBabyStates();
		if (!MANAGED_MOB_ARROWS.isEmpty()
			|| !HOMING_ARROWS.isEmpty()
			|| beeRuntimeActive
			|| mobBabyRuntimeActive) {
			requestRuntimeProcessing(server, resolveRuntimeProcessingInterval(server));
		}
	}

	private static boolean tickConfiguredMobBabyStates() {
		boolean active = false;
		for (Entity entity : TRACKED_ENTITIES.values()) {
			if (!(entity instanceof AgeableMob ageableMob)
				|| !(entity instanceof LivingEntity livingEntity)
				|| !entity.isAlive()
				) {
				continue;
			}
			EntityComponentsManager.MobBabySettings settings = EntityComponentsManager.resolveMobBabySettings(livingEntity);
			if (!settings.configured()) {
				CONFIGURED_MOB_BABY_STATES.remove(entity.getUUID());
				continue;
			}
			boolean baby = ageableMob.isBaby();
			Boolean previousBaby = CONFIGURED_MOB_BABY_STATES.put(entity.getUUID(), baby);
			if (baby) {
				active = true;
				if (previousBaby == null) {
					EntityComponentsManager.applyMobBabyComponent(livingEntity);
				}
				if (!settings.ageable()) {
					ageableMob.setAge(AgeableMob.BABY_START_AGE);
				}
			} else if (Boolean.TRUE.equals(previousBaby)) {
				boolean reappliedMobOverrides = applyLoadedEntityRules(livingEntity);
				if (livingEntity.level() instanceof ServerLevel level) {
					applyDifficultyScalingAfterMobOverrides(livingEntity, level, reappliedMobOverrides);
				}
			}
		}
		return active;
	}

	private static long resolveRuntimeProcessingInterval(MinecraftServer server) {
		return MadokuSchedulerManager.resolveAdaptiveDelayTicks(
			server,
			MOB_SCHEDULER_OWNER_ID,
			1L,
			5L
		);
	}

	private static void requestRuntimeProcessing(MinecraftServer server, long delayTicks) {
		if (server == null || !MobConfigManager.isEnabled() || runtimeTaskScheduled) {
			return;
		}
		String schedulerId = ensureRuntimeSchedulerExists();
		if (enqueueRuntimeTask(schedulerId, delayTicks)) {
			runtimeTaskScheduled = true;
			return;
		}
		runtimeSchedulerId = MadokuSchedulerManager.createOrGetScheduler(MadokuSchedulerManager.SchedulerBinding.global(MOB_SCHEDULER_OWNER_ID));
		if (enqueueRuntimeTask(runtimeSchedulerId, delayTicks)) {
			runtimeTaskScheduled = true;
		}
	}

	private static String ensureRuntimeSchedulerExists() {
		if (runtimeSchedulerId == null || runtimeSchedulerId.isBlank()) {
			runtimeSchedulerId = MadokuSchedulerManager.createOrGetScheduler(MadokuSchedulerManager.SchedulerBinding.global(MOB_SCHEDULER_OWNER_ID));
		}
		return runtimeSchedulerId;
	}

	private static boolean enqueueRuntimeTask(String schedulerId, long delayTicks) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}
		MadokuSchedulerManager.EnqueueStatus status = MadokuSchedulerManager.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_MOB_RUNTIME_TICK,
			new JsonObject(),
			MadokuSchedulerManager.TickDomain.GAMEPLAY
		);
		return status == MadokuSchedulerManager.EnqueueStatus.ACCEPTED || status == MadokuSchedulerManager.EnqueueStatus.QUEUE_FULL;
	}

	private static void tickHomingProjectiles(MinecraftServer server) {
		if (server == null || HOMING_ARROWS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, HomingArrowState> entry : HOMING_ARROWS.entrySet()) {
			UUID arrowId = entry.getKey();
			HomingArrowState state = entry.getValue();
			if (state.remainingTicks <= 0) {
				releaseHomingArrow(arrowId, findArrow(server, arrowId));
				continue;
			}
			AbstractArrow arrow = findArrow(server, arrowId);
			if (arrow == null || !arrow.isAlive() || arrow.onGround()) {
				HOMING_ARROWS.remove(arrowId);
				FIXED_ARROW_DAMAGE.remove(arrowId);
				continue;
			}
			Entity target = findEntity(server, state.targetUuid);
			if (!(target instanceof LivingEntity living) || !living.isAlive()) {
				releaseHomingArrow(arrowId, arrow);
				continue;
			}
			Vec3 toTarget = new Vec3(target.getX() - arrow.getX(), target.getY(0.5D) - arrow.getY(), target.getZ() - arrow.getZ());
			if (toTarget.lengthSqr() <= 1.0E-6D) {
				releaseHomingArrow(arrowId, arrow);
				continue;
			}
			double speed = Math.max(state.speed, arrow.getDeltaMovement().length());
			arrow.setNoGravity(true);
			arrow.setDeltaMovement(toTarget.normalize().scale(speed));
			HOMING_ARROWS.put(arrowId, new HomingArrowState(state.targetUuid, speed, state.remainingTicks - 1));
		}
	}

	private static void tickManagedMobArrows(MinecraftServer server) {
		if (server == null || MANAGED_MOB_ARROWS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Integer> entry : MANAGED_MOB_ARROWS.entrySet()) {
			UUID arrowId = entry.getKey();
			int remainingTicks = entry.getValue() == null ? 0 : entry.getValue();
			AbstractArrow arrow = findArrow(server, arrowId);
			if (arrow == null || !arrow.isAlive()) {
				removeArrowRuntimeState(arrowId);
				continue;
			}
			if (remainingTicks <= 0) {
				arrow.discard();
				removeArrowRuntimeState(arrowId);
				continue;
			}
			MANAGED_MOB_ARROWS.put(arrowId, remainingTicks - 1);
		}
	}

	private static Entity findEntity(MinecraftServer server, UUID entityId) {
		if (server == null || entityId == null) {
			return null;
		}
		Entity entity = TRACKED_ENTITIES.get(entityId);
		if (entity != null && entity.isAlive()) {
			return entity;
		}
		if (entity != null && !entity.isAlive()) {
			TRACKED_ENTITIES.remove(entityId);
		}
		return null;
	}

	private static AbstractArrow findArrow(MinecraftServer server, UUID arrowId) {
		Entity entity = findEntity(server, arrowId);
		return entity instanceof AbstractArrow arrow ? arrow : null;
	}

	private static void releaseHomingArrow(UUID arrowId, AbstractArrow arrow) {
		HOMING_ARROWS.remove(arrowId);
		if (arrow != null && arrow.isAlive()) {
			arrow.setNoGravity(false);
		}
	}

	private static void trackManagedMobArrow(AbstractArrow arrow, MinecraftServer server) {
		if (arrow == null) {
			return;
		}
		MANAGED_MOB_ARROWS.put(arrow.getUUID(), MOB_ARROW_LIFETIME_TICKS);
		requestRuntimeProcessing(server, resolveRuntimeProcessingInterval(server));
	}

	static void trackManagedMobArrowForRuntime(AbstractArrow arrow, MinecraftServer server) {
		trackManagedMobArrow(arrow, server);
	}

	private static void removeArrowRuntimeState(UUID arrowId) {
		if (arrowId == null) {
			return;
		}
		HOMING_ARROWS.remove(arrowId);
		FIXED_ARROW_DAMAGE.remove(arrowId);
		MANAGED_MOB_ARROWS.remove(arrowId);
		INVULNERABILITY_BYPASS_ARROWS.remove(arrowId);
	}

	private record PendingZombieReplacement(EntityType<?> replacementType, EntitySpawnReason reason) {}

	private static LivingEntity resolveDamageSourceLivingAttacker(DamageSource source) {
		if (source == null) {
			return null;
		}
		Entity owner = source.getEntity();
		if (owner instanceof LivingEntity livingOwner) {
			return livingOwner;
		}
		Entity direct = source.getDirectEntity();
		return direct instanceof LivingEntity livingDirect ? livingDirect : null;
	}

	private static boolean hasTrueDamageConfigured(LivingEntity attacker) {
		JsonObject componentsRoot = readMobComponentsRoot(resolveMobAttackRoot(attacker));
		if (componentsRoot.entrySet().isEmpty()) {
			return false;
		}
		JsonElement element = componentsRoot.get(MobConfigManager.FIELD_TRUE_DAMAGE);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
			return false;
		}
		if (element.getAsJsonPrimitive().isBoolean()) {
			return element.getAsBoolean();
		}
		if (!element.getAsJsonPrimitive().isNumber()) {
			return false;
		}
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) && value > 0.0D;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static JsonObject resolveMobAttackRoot(LivingEntity attacker) {
		if (attacker == null) {
			return new JsonObject();
		}
		if (attacker.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BEE) {
			return resolveBeeRootForRuntime(attacker);
		}
		if (attacker instanceof ZombieVillager zombieVillager) {
			if (!isMobFileEnabled(MobConfigManager.FILE_ZOMBIE_VILLAGER)) {
				return new JsonObject();
			}
			JsonObject root = EntityConfigManager.resolvePrimaryVariantOnly(root(MobConfigManager.FILE_ZOMBIE_VILLAGER));
			return resolveNestedVariantForRuntime(root, zombieVillager, null, false);
		}
		if (attacker instanceof Drowned drowned) {
			if (!isMobFileEnabled(MobConfigManager.FILE_DROWNED)) {
				return new JsonObject();
			}
			JsonObject root = EntityConfigManager.resolvePrimaryVariantOnly(root(MobConfigManager.FILE_DROWNED));
			return resolveNestedVariantForRuntime(root, drowned, null, false);
		}
		if (attacker instanceof Husk husk) {
			if (!isMobFileEnabled(MobConfigManager.FILE_HUSK)) {
				return new JsonObject();
			}
			JsonObject root = EntityConfigManager.resolvePrimaryVariantOnly(root(MobConfigManager.FILE_HUSK));
			return resolveNestedVariantForRuntime(root, husk, null, false);
		}
		if (attacker instanceof Zombie zombie) {
			if (!isMobFileEnabled(MobConfigManager.FILE_ZOMBIE)) {
				return new JsonObject();
			}
			JsonObject root = EntityConfigManager.resolvePrimaryVariantOnly(root(MobConfigManager.FILE_ZOMBIE));
			return resolveNestedVariantForRuntime(root, zombie, null, false);
		}
		if (attacker instanceof Creeper creeper) {
			if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER)) {
				return new JsonObject();
			}
			return resolveCreeperRuntimeVariantRoot(creeper, root(MobConfigManager.FILE_CREEPER));
		}
		if (attacker instanceof AbstractSkeleton skeleton) {
			return resolveSkeletonVariantRuntimeRoot(skeleton);
		}
		if (attacker instanceof Spider spider) {
			String fileKey = spider.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.CAVE_SPIDER
				? MobConfigManager.FILE_CAVE_SPIDER
				: MobConfigManager.FILE_SPIDER;
			if (!isMobFileEnabled(fileKey)) {
				return new JsonObject();
			}
			JsonObject fileRoot = spider.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.CAVE_SPIDER
				? fileMobRoot(MobConfigManager.FILE_CAVE_SPIDER)
				: fileMobRoot(MobConfigManager.FILE_SPIDER);
			return fileRoot;
		}
		return new JsonObject();
	}

    private static InteractionHand resolveBowHand(AbstractSkeleton skeleton) {
		if (skeleton.getMainHandItem().is(Items.BOW)) {
			return InteractionHand.MAIN_HAND;
		}
		if (skeleton.getOffhandItem().is(Items.BOW)) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	private static double resolveDifficultyAdjustedValue(Difficulty difficulty, boolean hardcore, double baseValue, double step, double minimum) {
		double resolved = Math.max(minimum, baseValue + (step * resolveDifficultyTier(difficulty, hardcore)));
		return roundDifficultyScaleValue(resolved);
	}

	private static double resolveSkeletonRangedDamage(AbstractSkeleton skeleton, JsonObject root) {
		if (skeleton == null) {
			return 0.0D;
		}
		double rangedDamage = MobRegionalDifficultyManager.resolveMobRangedDamageScaling(
			skeleton,
			readMobStatDouble(root, MobConfigManager.FIELD_RANGED_DAMAGE, 4.0D)
		);
		return MobEntityManager.resolveWorldDifficultyValueForRuntime(
			skeleton,
			MobConfigManager.FIELD_RANGED_DAMAGE,
			rangedDamage
		);
	}

	private static double roundDifficultyScaleValue(double value) {
		if (!Double.isFinite(value)) {
			return value;
		}
		double step = isWholeNumber(value) ? 0.05D : 0.005D;
		return Math.round(value / step) * step;
	}

	private static boolean isWholeNumber(double value) {
		return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
	}

	private static boolean isHardcoreWorld(Level level) {
		return level != null && level.getServer() != null && level.getServer().isHardcore();
	}

	private static int resolveDifficultyTier(Difficulty difficulty, boolean hardcore) {
		Difficulty resolved = difficulty == null ? Difficulty.NORMAL : difficulty;
		return switch (resolved) {
			case PEACEFUL -> -2;
			case EASY -> -1;
			case NORMAL -> 0;
			case HARD -> hardcore ? 2 : 1;
		};
	}

	private static JsonObject root(String key) {
		return MobConfigManager.getRuntimeMobFiles().getOrDefault(normalizeKey(key), new JsonObject());
	}

	private static boolean isMobFileEnabled(String fileKey) {
		return readBoolean(root(fileKey), MobConfigManager.FIELD_ENABLED, true);
	}

	static boolean isMobFileEnabledForRuntime(String fileKey) {
		return isMobFileEnabled(fileKey);
	}

	static JsonObject resolveMobFileConfigRootForRuntime(String fileKey) {
		return root(fileKey);
	}

	static JsonObject resolveMobFileSectionForRuntime(String fileKey) {
		return fileMobRoot(fileKey);
	}

	private static JsonObject fileMobRoot(String fileKey) {
		return EntityConfigManager.resolvePrimaryVariant(root(fileKey));
	}

	private static boolean isSupportedSkeletonRuntimeType(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return false;
		}
		return skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.SKELETON
			|| skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY
			|| skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED
			|| skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED
			|| skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON;
	}

	private static boolean isBowAttackEnabledForRuntimeSkeleton(AbstractSkeleton skeleton) {
		if (skeleton == null || !MobConfigManager.isEnabled()) {
			return false;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY) {
			return EntityBehaviorsManager.StrayBehavior.isBowAttackEnabled(skeleton);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED) {
			return EntityBehaviorsManager.BoggedBehavior.isBowAttackEnabled(skeleton);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED) {
			return EntityBehaviorsManager.ParchedBehavior.isBowAttackEnabled(skeleton);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON) {
			return EntityBehaviorsManager.SkeletonBehavior.isBowAttackEnabled(skeleton);
		}
		return EntityBehaviorsManager.SkeletonBehavior.isBowAttackEnabled(skeleton);
	}

	private static JsonObject resolveSkeletonVariantRuntimeRoot(AbstractSkeleton skeleton) {
		if (skeleton == null || !MobConfigManager.isEnabled()) {
			return new JsonObject();
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY) {
			return EntityBehaviorsManager.StrayBehavior.resolveRuntimeRoot(skeleton);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED) {
			return EntityBehaviorsManager.BoggedBehavior.resolveRuntimeRoot(skeleton);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED) {
			return EntityBehaviorsManager.ParchedBehavior.resolveRuntimeRoot(skeleton);
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON) {
			return EntityBehaviorsManager.WitherSkeletonBehavior.resolveRuntimeRoot(skeleton);
		}
		return EntityBehaviorsManager.SkeletonBehavior.resolveRuntimeRoot(skeleton);
	}

	private static JsonObject zombieRoot(EntityType<?> type) {
		if (type == madoku.craft.mobs.mob.MadokuMobEntityTypes.ZOMBIE) {
			return fileMobRoot(MobConfigManager.FILE_ZOMBIE);
		}
		return new JsonObject();
	}

	static JsonObject resolveZombieRootForRuntime(EntityType<?> type) {
		return zombieRoot(type);
	}

	private static JsonObject drownedRoot(EntityType<?> type) {
		if (type == madoku.craft.mobs.mob.MadokuMobEntityTypes.DROWNED) {
			return fileMobRoot(MobConfigManager.FILE_DROWNED);
		}
		return new JsonObject();
	}

	static JsonObject resolveDrownedRootForRuntime(EntityType<?> type) {
		return drownedRoot(type);
	}

	private static JsonObject zombieVillagerRoot(EntityType<?> type) {
		if (type == madoku.craft.mobs.mob.MadokuMobEntityTypes.ZOMBIE_VILLAGER) {
			return fileMobRoot(MobConfigManager.FILE_ZOMBIE_VILLAGER);
		}
		return new JsonObject();
	}

	static JsonObject resolveZombieVillagerRootForRuntime(EntityType<?> type) {
		return zombieVillagerRoot(type);
	}

	private static JsonObject huskRoot(EntityType<?> type) {
		if (type == madoku.craft.mobs.mob.MadokuMobEntityTypes.HUSK) {
			return fileMobRoot(MobConfigManager.FILE_HUSK);
		}
		return new JsonObject();
	}

	static JsonObject resolveHuskRootForRuntime(EntityType<?> type) {
		return huskRoot(type);
	}

	public static MobEffectInstance resolveHuskAttackEffect(Husk husk, MobEffectInstance fallbackEffect) {
		if (husk == null || fallbackEffect == null || !MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_HUSK)) {
			return fallbackEffect;
		}
		JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(root(MobConfigManager.FILE_HUSK));
		if (defaultGroup.entrySet().isEmpty()) {
			return fallbackEffect;
		}
		JsonObject variant = resolveNestedVariantForRuntime(defaultGroup, husk, null, false);
		if (variant.entrySet().isEmpty()) {
			return fallbackEffect;
		}
		return resolveConfiguredMobEffectInstance(readMobComponentsRoot(variant), fallbackEffect);
	}

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static JsonObject readObject(JsonObject root, String key) {
		if (root == null || key == null) {
			return new JsonObject();
		}
		JsonElement element = EntityConfigManager.resolveConfiguredElement(root, key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static JsonObject readMobComponentsRoot(JsonObject root) {
		return readObject(root, MobConfigManager.FIELD_MOB_COMPONENTS);
	}

	private static MobEffectInstance resolveConfiguredMobEffectInstance(JsonObject componentsRoot, MobEffectInstance fallbackEffect) {
		if (componentsRoot == null || componentsRoot.entrySet().isEmpty() || fallbackEffect == null) {
			return fallbackEffect;
		}
		JsonObject mobEffectRoot = readObject(componentsRoot, MobConfigManager.FIELD_MOB_EFFECT);
		if (mobEffectRoot.entrySet().isEmpty()) {
			return fallbackEffect;
		}
		String effectId = normalizeKey(readString(mobEffectRoot, MobConfigManager.FIELD_EFFECT, ""));
		if (effectId.isBlank()) {
			return fallbackEffect;
		}
		Identifier effectIdentifier = Identifier.tryParse(effectId);
		if (effectIdentifier == null || !BuiltInRegistries.MOB_EFFECT.containsKey(effectIdentifier)) {
			return fallbackEffect;
		}
		MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.getValue(effectIdentifier);
		if (mobEffect == null) {
			return fallbackEffect;
		}
		Integer durationSeconds = readOptionalIntNonNegative(mobEffectRoot, MobConfigManager.FIELD_DURATION);
		if (durationSeconds == null || durationSeconds <= 0) {
			return fallbackEffect;
		}
		long durationTicks = Math.min(Integer.MAX_VALUE, durationSeconds.longValue() * 20L);
		return new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(mobEffect), (int) durationTicks);
	}

	private static JsonObject readMobBehaviorRoot(JsonObject root) {
		return readObject(root, MobConfigManager.FIELD_MOB_BEHAVIORS);
	}

	static JsonObject readMobBehaviorRootForRuntime(JsonObject root) {
		return readMobBehaviorRoot(root);
	}

	private static JsonObject readMobGoalsRoot(JsonObject root) {
		return readObject(root, MobConfigManager.FIELD_MOB_GOALS);
	}

	static JsonObject readMobGoalsRootForRuntime(JsonObject root) {
		return readMobGoalsRoot(root);
	}

	private static JsonObject readSpawnRulesRoot(JsonObject root) {
		return readObject(root, MobConfigManager.FIELD_SPAWN_RULES);
	}

	static JsonObject resolveVariantGroupRoot(JsonObject defaultGroupRoot, JsonObject variantGroupRoot) {
		if (variantGroupRoot == null || variantGroupRoot.entrySet().isEmpty()) {
			return defaultGroupRoot == null ? new JsonObject() : defaultGroupRoot.deepCopy();
		}
		// Top-level variants are siblings. They must resolve independently.
		return variantGroupRoot.deepCopy();
	}

	static String resolveRuntimeMobFileKey(LivingEntity entity) {
		return resolveRegionalDifficultyMobFileKey(entity);
	}

	static boolean shouldApplyConfiguredComponentsForRuntime(LivingEntity entity) {
		if (entity == null || !MobConfigManager.isEnabled()) {
			return false;
		}
		String fileKey = resolveRegionalDifficultyMobFileKey(entity);
		if (fileKey.isBlank() || !isMobFileEnabled(fileKey)) {
			return false;
		}
		return readBoolean(
			resolveMobFileConfigRootForRuntime(fileKey),
			MobConfigManager.FIELD_OVERRIDE_COMPONENTS,
			true
		);
	}

	private static boolean shouldApplyConfiguredSpawnRulesForRuntime(LivingEntity entity) {
		if (entity == null || !MobConfigManager.isEnabled()) {
			return false;
		}
		String fileKey = resolveRegionalDifficultyMobFileKey(entity);
		if (fileKey.isBlank() || !isMobFileEnabled(fileKey)) {
			return false;
		}
		return readBoolean(
			resolveMobFileConfigRootForRuntime(fileKey),
			MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES,
			true
		);
	}

	static JsonObject resolveConfiguredEntityVariantForRuntime(LivingEntity entity) {
		String fileKey = resolveRegionalDifficultyMobFileKey(entity);
		if (fileKey.isBlank() || !isMobFileEnabled(fileKey)) return new JsonObject();
		JsonObject variantCatalog = EntityConfigManager.resolvePrimaryVariant(root(fileKey));
		JsonObject variantGroup = EntityConfigManager.resolvePrimaryVariantOnly(root(fileKey));
		String storedVariantKey = readStoredVariantKeyForRuntime(entity, fileKey);
		if (!storedVariantKey.isBlank()) {
			JsonObject selectedVariant = readObject(variantCatalog, storedVariantKey);
			if (!selectedVariant.entrySet().isEmpty()) {
				variantGroup = selectedVariant;
			}
		}
		JsonObject resolved = resolveNestedVariantForRuntime(variantGroup, entity, null, false);
		return reconcileBabyVariantForRuntime(variantGroup, resolved, entity);
	}

	private static String readStoredVariantKeyForRuntime(LivingEntity entity, String fileKey) {
		if (entity == null || fileKey == null || fileKey.isBlank()) {
			return "";
		}
		String prefix = "madoku-craft." + fileKey + ".variant:";
		for (String tag : entity.entityTags()) {
			if (tag != null && tag.startsWith(prefix)) {
				String key = tag.substring(prefix.length()).trim().toLowerCase(Locale.ROOT);
				if (!key.isBlank()) {
					return key;
				}
			}
		}
		return "";
	}

	private static void writeStoredVariantKeyForRuntime(LivingEntity entity, String fileKey, String variantKey) {
		if (entity == null || fileKey == null || fileKey.isBlank() || variantKey == null || variantKey.isBlank()) {
			return;
		}
		String prefix = "madoku-craft." + fileKey + ".variant:";
		String existing = null;
		for (String tag : entity.entityTags()) {
			if (tag != null && tag.startsWith(prefix)) {
				existing = tag;
				break;
			}
		}
		if (existing != null) {
			entity.removeTag(existing);
		}
		entity.addTag(prefix + normalizeKey(variantKey));
	}

	static JsonObject resolveNestedVariantRoot(JsonObject variantGroupRoot, String nestedVariantKey) {
		if (variantGroupRoot == null || variantGroupRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		JsonObject nestedVariant = readObject(variantGroupRoot, nestedVariantKey);
		if (nestedVariant.entrySet().isEmpty()) {
			return removeNestedVariantEntries(variantGroupRoot);
		}
		return mergeJsonWithOverride(removeNestedVariantEntries(variantGroupRoot), nestedVariant);
	}

	static JsonObject resolveNestedVariantForRuntime(
		JsonObject variantGroupRoot,
		LivingEntity entity,
		RandomSource random,
		boolean spawnContext
	) {
		if (variantGroupRoot == null || variantGroupRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		Map<String, JsonObject> nestedVariants = collectNestedVariantRoots(variantGroupRoot);
		if (nestedVariants.isEmpty()) {
			return removeNestedVariantEntries(variantGroupRoot);
		}

		String selectedKey = readNestedVariantTag(entity);
		if (selectedKey.isBlank() && spawnContext && random != null) {
			selectedKey = selectNestedVariantKey(nestedVariants, random);
		}
		if (selectedKey.isBlank() && supportsBabyVariant(entity) && hasMobBabyVariant(nestedVariants)) {
			boolean baby = isBabyVariantEntity(entity);
			for (Map.Entry<String, JsonObject> entry : nestedVariants.entrySet()) {
				if (hasMobBabyComponent(entry.getValue()) == baby) {
					selectedKey = entry.getKey();
					break;
				}
			}
		}
		if (selectedKey.isBlank()) {
			return removeNestedVariantEntries(variantGroupRoot);
		}
		JsonObject selected = nestedVariants.get(normalizeKey(selectedKey));
		if (selected == null || selected.entrySet().isEmpty()) {
			return removeNestedVariantEntries(variantGroupRoot);
		}
		if (spawnContext) {
			writeNestedVariantTag(entity, selectedKey);
			if (supportsBabyVariant(entity) && hasMobBabyVariant(nestedVariants)) {
				setBabyVariantEntity(entity, hasMobBabyComponent(selected));
			}
		}
		return mergeJsonWithOverride(removeNestedVariantEntries(variantGroupRoot), selected);
	}

	private static Map<String, JsonObject> collectNestedVariantRoots(JsonObject root) {
		Map<String, JsonObject> variants = new LinkedHashMap<>();
		if (root == null) {
			return variants;
		}
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || !entry.getValue().isJsonObject()) {
				continue;
			}
			String key = normalizeKey(entry.getKey());
			if (EntityConfigManager.isVariantKey(key)) {
				variants.putIfAbsent(key, entry.getValue().getAsJsonObject());
			}
		}
		return variants;
	}

	private static String selectNestedVariantKey(Map<String, JsonObject> variants, RandomSource random) {
		if (variants == null || variants.isEmpty() || random == null) {
			return "";
		}
		double total = 0.0D;
		Map<String, Double> weights = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> entry : variants.entrySet()) {
			double weight = Math.max(0.0D, resolveVariantSpawnWeight(entry.getValue(), 0.0D));
			if (weight > 0.0D) {
				weights.put(entry.getKey(), weight);
				total += weight;
			}
		}
		if (total <= 0.0D) {
			return "";
		}
		double roll = random.nextDouble() * total;
		String selectedKey = "";
		for (Map.Entry<String, Double> entry : weights.entrySet()) {
			if (roll < entry.getValue()) {
				selectedKey = entry.getKey();
				break;
			}
			roll -= entry.getValue();
		}
		return selectedKey;
	}

	private static boolean hasMobBabyVariant(Map<String, JsonObject> variants) {
		for (JsonObject variant : variants.values()) {
			if (hasMobBabyComponent(variant)) {
				return true;
			}
		}
		return false;
	}

	private static JsonObject reconcileBabyVariantForRuntime(
		JsonObject variantGroupRoot,
		JsonObject resolved,
		LivingEntity entity
	) {
		if (!supportsBabyVariant(entity)) {
			return resolved;
		}
		Map<String, JsonObject> nestedVariants = collectNestedVariantRoots(variantGroupRoot);
		if (nestedVariants.isEmpty() || !hasMobBabyVariant(nestedVariants)) {
			return resolved;
		}

		boolean baby = isBabyVariantEntity(entity);
		if (hasMobBabyComponent(resolved) == baby) {
			return resolved;
		}
		for (Map.Entry<String, JsonObject> entry : nestedVariants.entrySet()) {
			if (hasMobBabyComponent(entry.getValue()) != baby) {
				continue;
			}
			writeNestedVariantTag(entity, entry.getKey());
			return mergeJsonWithOverride(removeNestedVariantEntries(variantGroupRoot), entry.getValue());
		}
		return resolved;
	}

	private static boolean supportsBabyVariant(LivingEntity entity) {
		return entity instanceof AgeableMob || entity instanceof Zombie;
	}

	private static boolean isBabyVariantEntity(LivingEntity entity) {
		if (entity instanceof Zombie zombie) {
			return zombie.isBaby();
		}
		return entity instanceof AgeableMob ageableMob && ageableMob.isBaby();
	}

	private static void setBabyVariantEntity(LivingEntity entity, boolean baby) {
		if (entity instanceof Zombie zombie) {
			zombie.setBaby(baby);
		} else if (entity instanceof AgeableMob ageableMob) {
			ageableMob.setBaby(baby);
		}
	}

	private static boolean hasMobBabyComponent(JsonObject variant) {
		JsonObject components = readObject(variant, MobConfigManager.FIELD_MOB_COMPONENTS);
		JsonElement mobBaby = components.get(MobConfigManager.FIELD_MOB_BABY);
		return mobBaby != null && mobBaby.isJsonObject();
	}

	private static String readNestedVariantTag(LivingEntity entity) {
		if (entity == null) {
			return "";
		}
		for (String tag : entity.entityTags()) {
			if (tag != null && tag.startsWith(NESTED_VARIANT_TAG_PREFIX)) {
				String key = normalizeKey(tag.substring(NESTED_VARIANT_TAG_PREFIX.length()));
				if (!key.isBlank()) {
					return key;
				}
			}
		}
		return "";
	}

	private static void writeNestedVariantTag(LivingEntity entity, String variantKey) {
		if (entity == null || variantKey == null || variantKey.isBlank()) {
			return;
		}
		String existing = null;
		for (String tag : entity.entityTags()) {
			if (tag != null && tag.startsWith(NESTED_VARIANT_TAG_PREFIX)) {
				existing = tag;
				break;
			}
		}
		if (existing != null) {
			entity.removeTag(existing);
		}
		entity.addTag(NESTED_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
	}

	private static JsonObject removeNestedVariantEntries(JsonObject root) {
		JsonObject sharedRoot = root == null ? new JsonObject() : root.deepCopy();
		for (String key : new ArrayList<>(sharedRoot.keySet())) {
			JsonElement value = sharedRoot.get(key);
			if (value != null && value.isJsonObject() && EntityConfigManager.isVariantKey(key)) {
				sharedRoot.remove(key);
			}
		}
		return sharedRoot;
	}

	static JsonObject resolveVariantRootByKey(JsonObject root, String variantKey, Predicate<String> reservedKeyPredicate) {
		if (root == null || variantKey == null || variantKey.isBlank()) {
			return new JsonObject();
		}
		if (reservedKeyPredicate != null && reservedKeyPredicate.test(normalizeKey(variantKey))) {
			return new JsonObject();
		}
		return EntityConfigManager.resolveTopLevelVariant(root, variantKey);
	}

	static String selectWeightedVariantKey(
		JsonObject root,
		RandomSource random,
		Predicate<String> reservedKeyPredicate,
		ToDoubleFunction<JsonObject> weightResolver
	) {
		if (root == null || random == null) {
			return "";
		}
		JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(root);
		double defaultWeight = Math.max(0.0D, weightResolver == null ? 0.0D : weightResolver.applyAsDouble(defaultGroup));
		double total = defaultWeight;
		java.util.List<WeightedVariant> weightedVariants = new ArrayList<>();
		String primaryKey = EntityConfigManager.resolvePrimaryVariantKeyForRuntime(root);
		for (Map.Entry<String, JsonObject> entry : EntityConfigManager.collectTopLevelVariantRoots(root).entrySet()) {
			if (entry.getKey().equalsIgnoreCase(primaryKey)) {
				continue;
			}
			if (reservedKeyPredicate != null && reservedKeyPredicate.test(normalizeKey(entry.getKey()))) {
				continue;
			}
			double weight = Math.max(0.0D, weightResolver == null ? 0.0D : weightResolver.applyAsDouble(entry.getValue()));
			if (weight <= 0.0D) {
				continue;
			}
			total += weight;
			weightedVariants.add(new WeightedVariant(entry.getKey(), weight));
		}
		if (total <= 0.0D) {
			return "";
		}
		double roll = random.nextDouble() * total;
		String selectedKey = "";
		if (roll < defaultWeight) {
			selectedKey = "";
		} else {
			double cursor = defaultWeight;
			for (WeightedVariant variant : weightedVariants) {
				cursor += variant.weight();
				if (roll < cursor) {
					selectedKey = variant.key();
					break;
				}
			}
		}
		return selectedKey;
	}

	static double resolveVariantSpawnWeight(JsonObject variantRoot, double fallback) {
		if (variantRoot == null || variantRoot.entrySet().isEmpty()) {
			return fallback;
		}
		JsonObject spawnRules = readSpawnRulesRoot(variantRoot);
		Double nested = readOptionalDouble(spawnRules, MobConfigManager.FIELD_SPAWN_WEIGHT);
		if (nested != null) {
			return nested;
		}
		Double direct = readOptionalDouble(variantRoot, MobConfigManager.FIELD_SPAWN_WEIGHT);
		if (direct != null) {
			return direct;
		}
		double summed = 0.0D;
		for (JsonObject nestedVariant : collectNestedVariantRoots(variantRoot).values()) {
			summed += Math.max(0.0D, readSpawnRuleDouble(nestedVariant, MobConfigManager.FIELD_SPAWN_WEIGHT, 0.0D));
		}
		return summed > 0.0D ? summed : fallback;
	}

	private static JsonObject resolveBeeRoot(LivingEntity entity, JsonObject beeFileRoot, RandomSource random, boolean spawnContext) {
		if (entity == null || beeFileRoot == null) {
			return new JsonObject();
		}
		JsonObject beeRoot = EntityConfigManager.resolvePrimaryVariantOnly(beeFileRoot);
		if (beeRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		boolean selectNested = spawnContext && readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		return resolveNestedVariantForRuntime(beeRoot, entity, selectNested ? random : null, selectNested);
	}

	private static JsonObject mergeJsonWithOverride(JsonObject base, JsonObject override) {
		JsonObject merged = base == null ? new JsonObject() : base.deepCopy();
		if (override == null) {
			return merged;
		}
		deepMergeOverride(merged, override);
		return merged;
	}

	private static void deepMergeOverride(JsonObject target, JsonObject override) {
		if (target == null || override == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			if (value != null
				&& value.isJsonObject()
				&& target.has(key)
				&& target.get(key).isJsonObject()) {
				deepMergeOverride(target.getAsJsonObject(key), value.getAsJsonObject());
				continue;
			}
			target.add(key, value == null ? com.google.gson.JsonNull.INSTANCE : value.deepCopy());
		}
	}

	private static double readMobStatDouble(JsonObject root, String key, double fallback) {
		return readDouble(readMobComponentsRoot(root), key, fallback);
	}

	private static double readSpawnRuleDouble(JsonObject root, String key, double fallback) {
		return readDouble(readSpawnRulesRoot(root), key, fallback);
	}

	static double readSpawnRuleDoubleForRuntime(JsonObject root, String key, double fallback) {
		return readSpawnRuleDouble(root, key, fallback);
	}

	private static boolean readMobBehaviorBoolean(JsonObject root, String key, boolean fallback) {
		return readBoolean(readMobBehaviorRoot(root), key, fallback);
	}

	static boolean readMobBehaviorBooleanForRuntime(JsonObject root, String key, boolean fallback) {
		return readMobBehaviorBoolean(root, key, fallback);
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

	private static double readDouble(JsonObject root, String key, double fallback) {
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

	private static Double readOptionalDouble(JsonObject root, String key) {
		if (root == null) {
			return null;
		}
		JsonElement element = root.get(key);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static Double readOptionalPositive(JsonObject root, String key) {
		Double value = readOptionalDouble(root, key);
		return value != null && value > 0.0D ? value : null;
	}

	private static Double readOptionalNonNegative(JsonObject root, String key) {
		Double value = readOptionalDouble(root, key);
		if (value == null && MobConfigManager.FIELD_MOVEMENT_SPEED.equals(key)) {
			value = readOptionalDouble(root, "movement_speed");
		}
		if (value == null && MobConfigManager.FIELD_EXPERIENCE_DROP.equals(key)) {
			value = readOptionalDouble(root, "experience_drop");
		}
		return value != null && value >= 0.0D ? value : null;
	}

	private static Integer readOptionalIntNonNegative(JsonObject root, String key) {
		if (root == null) {
			return null;
		}
		JsonElement element = root.get(key);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		try {
			int value = element.getAsInt();
			return value >= 0 ? value : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static Double clampOptional(Double value, double min, double max) {
		if (value == null || !Double.isFinite(value)) {
			return null;
		}
		return Mth.clamp(value, min, max);
	}

	private static Entity spawnConfiguredJockeyPartner(
		Mob sourceMob,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		ServerLevel level,
		EntitySpawnReason spawnReason,
		EntityType<?> partnerType,
		JsonObject partnerDefinition
	) {
		if (sourceMob == null || world == null || difficulty == null || level == null || partnerType == null) {
			return null;
		}

		Entity partner = partnerType.create(level, spawnReason);
		if (partner == null) {
			return null;
		}

		partner.setPos(sourceMob.getX(), sourceMob.getY(), sourceMob.getZ());
		partner.setYRot(sourceMob.getYRot());
		partner.setXRot(sourceMob.getXRot());
		if (partner instanceof Mob mobPartner) {
			mobPartner.finalizeSpawn(world, difficulty, spawnReason, null);
			applyConfiguredJockeyMobWeapon(mobPartner, partnerDefinition);
		}
		level.tryAddFreshEntityWithPassengers(partner);
		return partner;
	}

	private static void applyConfiguredJockeyMobWeapon(Mob mob, JsonObject mobDefinition) {
		if (mob == null || mobDefinition == null || mobDefinition.entrySet().isEmpty()) {
			return;
		}
		JsonElement weaponElement = mobDefinition.get(MobConfigManager.FIELD_MOB_WEAPON);
		if (weaponElement == null || weaponElement.isJsonNull() || !weaponElement.isJsonObject()) {
			return;
		}
		String itemId = readString(weaponElement.getAsJsonObject(), MobConfigManager.FIELD_ITEM, "");
		if (itemId.isBlank()) {
			return;
		}
		ItemStack stack = resolveItemStackById(itemId);
		if (!stack.isEmpty()) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
		}
	}

	private static boolean applyConfiguredMobWeapon(LivingEntity entity, JsonObject root) {
		if (!(entity instanceof Mob mob) || root == null || root.entrySet().isEmpty()) {
			return false;
		}
		JsonObject componentsRoot = readMobComponentsRoot(root);
		JsonObject weaponRoot = readObject(componentsRoot, MobConfigManager.FIELD_MOB_WEAPON);
		if (weaponRoot.entrySet().isEmpty()) {
			return false;
		}
		String itemId = readString(weaponRoot, MobConfigManager.FIELD_ITEM, "");
		String normalizedItemId = normalizeKey(itemId);
		if (normalizedItemId.isBlank()) {
			return false;
		}
		if ("hand".equals(normalizedItemId)) {
			return true;
		}
		if ("empty".equals(normalizedItemId)) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			return true;
		}
		ItemStack stack = resolveItemStackById(itemId);
		if (stack.isEmpty()) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
		if (!readBoolean(root, MobConfigManager.FIELD_WEAPON_DAMAGE, true)) {
			stripHeldAttackDamageModifiers(mob, EquipmentSlot.MAINHAND);
			stripHeldAttackDamageModifiers(mob, EquipmentSlot.OFFHAND);
		}
		return true;
	}

	private static void stripHeldAttackDamageModifiers(Mob mob, EquipmentSlot slot) {
		if (mob == null || slot == null) {
			return;
		}
		ItemStack stack = mob.getItemBySlot(slot);
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack normalized = stack.copy();
		normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		mob.setItemSlot(slot, normalized);
	}

	private static EntityType<?> resolveEntityTypeById(String entityTypeId) {
		if (entityTypeId == null || entityTypeId.isBlank()) {
			return null;
		}
		Identifier identifier = Identifier.tryParse(entityTypeId.trim());
		if (identifier == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
			return null;
		}
		return BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
	}

	private static ItemStack resolveItemStackById(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return ItemStack.EMPTY;
		}
		Identifier identifier = Identifier.tryParse(itemId.trim());
		if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
			return ItemStack.EMPTY;
		}
		net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(identifier);
		if (item == null || item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(item);
	}

	static String resolveItemIdForRuntime(ItemStack stack) {
		if (stack == null || stack.isEmpty() || stack.getItem() == null) {
			return "empty";
		}
		Identifier identifier = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return identifier == null ? "unknown" : identifier.toString();
	}

	private record HomingArrowState(UUID targetUuid, double speed, int remainingTicks) {}
	private record WeightedVariant(String key, double weight) {}

}

