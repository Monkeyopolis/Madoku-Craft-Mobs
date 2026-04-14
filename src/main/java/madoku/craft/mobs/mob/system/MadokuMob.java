package madoku.craft.mobs.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.config.DynamicJsonSystem;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.mobs.difficulty.system.DifficultyScaledMob;
import madoku.craft.mobs.difficulty.system.MadokuDifficulty;
import madoku.craft.mobs.mixin.AbstractSkeletonArrowInvoker;
import madoku.craft.mobs.mixin.CreeperAccessor;
import madoku.craft.mobs.mixin.CreeperPoweredAccessor;
import madoku.craft.mobs.mixin.MobExperienceAccessor;
import madoku.craft.scheduler.MadokuScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MadokuMob {
	private static final String MOB_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-mobs";
	private static final String MOB_CONFIG_SETTINGS_FILE_NAME = "madoku-mobs";
	private static final String MOB_CONFIG_MOBS_FOLDER_NAME = "madoku-mobs";
	private static final String TASK_TYPE_MOB_RUNTIME_TICK = "mob_runtime_tick";
	private static final String MOB_SCHEDULER_OWNER_ID = "madoku-mob-runtime";
	private static final double HEALTH_DIFFICULTY_STEP = 4.0D;
	private static final double MOVEMENT_SPEED_DIFFICULTY_STEP = 0.01D;
	private static final double DAMAGE_DIFFICULTY_STEP = 1.0D;
	private static final double ARMOR_DIFFICULTY_STEP = 0.5D;
	private static final double KNOCKBACK_RESISTANCE_DIFFICULTY_STEP = 0.1D;
	private static final double SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP = 2.0D;
	private static final double RANGED_DAMAGE_DIFFICULTY_STEP = 1.0D;
	private static final double ATTACK_ACCURACY_DIFFICULTY_STEP = 0.05D;
	private static final double CREEPER_EXPLOSION_POWER_DIFFICULTY_STEP = 1.0D;
	private static final double CREEPER_POWER_PER_DAMAGE = 0.2D;
	private static final double MIN_HOMING_SPEED = 0.75D;
	private static final int HOMING_LIFETIME_TICKS = 60;
	private static final int MOB_ARROW_LIFETIME_TICKS = 15 * 20;
	private static final int WITHER_EFFECT_DURATION_TICKS = 5 * 20;
	private static final String HOMING_PROJECTILE_TAG = "madoku-craft.projectile.homing";

	private static final Map<UUID, HomingArrowState> HOMING_ARROWS = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> FIXED_ARROW_DAMAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> MANAGED_MOB_ARROWS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> PILLAGER_ATTACK_COOLDOWNS = new ConcurrentHashMap<>();
	private static final Map<UUID, EntitySpawnReason> PENDING_CAVE_SPIDER_REPLACEMENTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Entity> TRACKED_ENTITIES = new ConcurrentHashMap<>();

	private static volatile Snapshot snapshot = Snapshot.disabled();
	private static volatile String runtimeSchedulerId = "";
	private static volatile boolean runtimeTaskScheduled = false;

	private MadokuMob() {
	}

	public static void initialize() {
		loadConfig();
		MadokuScheduler.registerTaskHandler(TASK_TYPE_MOB_RUNTIME_TICK, MadokuMob::runRuntimeTask);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			TRACKED_ENTITIES.put(entity.getUUID(), entity);
			if (entity instanceof LivingEntity livingEntity) {
				boolean loadedMobOverridesApplied = applyLoadedEntityRules(livingEntity);
				applyDifficultyScalingAfterMobOverrides(livingEntity, world, loadedMobOverridesApplied);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			TRACKED_ENTITIES.remove(entity.getUUID());
			cleanupEntityState(entity);
		});
	}

	public static void onServerStarted(MinecraftServer server) {
		runtimeSchedulerId = "";
		runtimeTaskScheduled = false;
		HOMING_ARROWS.clear();
		FIXED_ARROW_DAMAGE.clear();
		MANAGED_MOB_ARROWS.clear();
		PILLAGER_ATTACK_COOLDOWNS.clear();
		PENDING_CAVE_SPIDER_REPLACEMENTS.clear();
		TRACKED_ENTITIES.clear();
		runtimeSchedulerId = MadokuScheduler.createOrGetScheduler(MadokuScheduler.SchedulerOwner.global(MOB_SCHEDULER_OWNER_ID));
		MadokuScheduler.clearQueuedRequests(runtimeSchedulerId);
		requestRuntimeProcessing(server, 1L);
	}

	public static void onServerTick(MinecraftServer server) {
		requestRuntimeProcessing(server, 1L);
	}

	public static void onServerStopped() {
		runtimeSchedulerId = "";
		runtimeTaskScheduled = false;
		HOMING_ARROWS.clear();
		FIXED_ARROW_DAMAGE.clear();
		MANAGED_MOB_ARROWS.clear();
		PILLAGER_ATTACK_COOLDOWNS.clear();
		PENDING_CAVE_SPIDER_REPLACEMENTS.clear();
		TRACKED_ENTITIES.clear();
	}

	public static boolean isEnabled() {
		return snapshot.enabled;
	}

	public static void applyMobSpawnOverridesFromGenericMixin(
		Mob mob,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (mob instanceof Creeper creeper) {
			applyCreeperSpawnOverrides(creeper, world, difficulty);
		} else if (mob instanceof Piglin piglin) {
			applyPiglinSpawnOverrides(piglin, world);
		}
	}

	public static void applyZombieSpawnOverrides(
		Zombie zombie,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (zombie == null || world == null || difficulty == null || !snapshot.enabled) {
			return;
		}
		JsonObject root = zombieRoot(zombie.getType());
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return;
		}

		boolean useCustomBabyChance = readBoolean(root, MadokuMobConfig.FIELD_USE_CUSTOM_BABY_SPAWN_CHANCE, true);
		if (useCustomBabyChance) {
			JsonObject adult = zombieAdultRoot(zombie.getType(), root);
			JsonObject baby = zombieBabyRoot(zombie.getType(), root);
			double babyChance = resolveBabyChance(
				readDouble(adult, MadokuMobConfig.FIELD_SPAWN_WEIGHT, 95.0D),
				readDouble(baby, MadokuMobConfig.FIELD_SPAWN_WEIGHT, 5.0D),
				difficulty.getDifficulty(),
				isHardcoreWorld(zombie.level())
			);
			boolean shouldBeBaby = world.getRandom().nextFloat() < babyChance;
			zombie.setBaby(shouldBeBaby);
			if (!shouldBeBaby && zombie.getType() == EntityType.ZOMBIE && zombie.getVehicle() != null && zombie.getVehicle().getType() == EntityType.CHICKEN) {
				zombie.stopRiding();
			}
		}

		clearMobEquipment(zombie);
		disableZombieReinforcements(zombie);
		applySpawnArmorLoadout(zombie, root, world.getRandom());
		JsonObject variant = zombie.isBaby() ? zombieBabyRoot(zombie.getType(), root) : zombieAdultRoot(zombie.getType(), root);
		applyUniversalStats(zombie, variant);
		zombie.setCanBreakDoors(readBoolean(variant, MadokuMobConfig.FIELD_CAN_BREAK_DOORS, false));
		zombie.setCanPickUpLoot(readBoolean(variant, MadokuMobConfig.FIELD_CAN_PICK_UP_LOOT, false));
	}

	public static void applySpiderSpawnOverrides(
		Spider spider,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (spider == null || world == null || difficulty == null || !snapshot.enabled) {
			return;
		}
		JsonObject root = root(MadokuMobConfig.FILE_SPIDER);
		if (spider.getType() != EntityType.SPIDER || spawnReason == EntitySpawnReason.JOCKEY || !readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return;
		}

		clearExistingSkeletonPassengers(spider);
		SpawnWeightPair caveShifted = resolveDifficultyShiftedSpawnWeights(
			readDouble(root, MadokuMobConfig.FIELD_SPIDER_SPAWN_WEIGHT, 90.0D),
			readDouble(root, MadokuMobConfig.FIELD_CAVE_SPIDER_SPAWN_WEIGHT, 5.0D),
			difficulty.getDifficulty(),
			isHardcoreWorld(spider.level()),
			SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		SpawnWeightPair jockeyShifted = resolveDifficultyShiftedSpawnWeights(
			caveShifted.regularWeight,
			readDouble(root, MadokuMobConfig.FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0D),
			difficulty.getDifficulty(),
			isHardcoreWorld(spider.level()),
			SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		SpawnOutcome outcome = rollSpiderSpawnOutcome(world.getRandom(), jockeyShifted.regularWeight, caveShifted.specialWeight, jockeyShifted.specialWeight);
		if (outcome == SpawnOutcome.CAVE_SPIDER) {
			PENDING_CAVE_SPIDER_REPLACEMENTS.put(spider.getUUID(), spawnReason);
			requestRuntimeProcessing(world.getLevel().getServer(), 1L);
			return;
		}
		if (outcome == SpawnOutcome.SPIDER_JOCKEY) {
			spawnSpiderJockey(spider, world, difficulty);
		}
	}

	public static void applySkeletonSpawnOverrides(
		AbstractSkeleton skeleton,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (skeleton == null || world == null || difficulty == null || !snapshot.enabled) {
			return;
		}
		if (skeleton.getType() == EntityType.WITHER_SKELETON) {
			applyWitherSkeletonSpawnOverrides(skeleton, world);
			return;
		}
		JsonObject root = skeletonRoot(skeleton.getType());
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return;
		}

		double withBow = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_WITH_BOW_SPAWN_WEIGHT, 95.0D));
		double withoutBow = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_WITHOUT_BOW_SPAWN_WEIGHT, 5.0D));
		if (withBow + withoutBow > 0.0D) {
			boolean spawnWithBow = (world.getRandom().nextDouble() * (withBow + withoutBow)) < withBow;
			if (spawnWithBow) {
				ensureBowEquipped(skeleton);
			} else {
				skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
		}

		if (spawnReason == EntitySpawnReason.JOCKEY) {
			ensureBowEquipped(skeleton);
			applySpawnArmorLoadout(skeleton, root, world.getRandom());
			return;
		}

		SpawnWeightPair jockeyWeights = resolveDifficultyShiftedSpawnWeights(
			readDouble(root, MadokuMobConfig.FIELD_REGULAR_SPAWN_WEIGHT, 95.0D),
			readDouble(root, MadokuMobConfig.FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0D),
			difficulty.getDifficulty(),
			isHardcoreWorld(skeleton.level()),
			SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		if (jockeyWeights.regularWeight + jockeyWeights.specialWeight <= 0.0D || skeleton.getVehicle() != null) {
			applySpawnArmorLoadout(skeleton, root, world.getRandom());
			return;
		}
		boolean spawnJockey = (world.getRandom().nextDouble() * (jockeyWeights.regularWeight + jockeyWeights.specialWeight))
			< jockeyWeights.specialWeight;
		if (!spawnJockey) {
			applySpawnArmorLoadout(skeleton, root, world.getRandom());
			return;
		}

		ServerLevel level = world.getLevel();
		Spider spider = EntityType.SPIDER.create(level, EntitySpawnReason.JOCKEY);
		if (spider == null) {
			applySpawnArmorLoadout(skeleton, root, world.getRandom());
			return;
		}
		spider.setPos(skeleton.getX(), skeleton.getY(), skeleton.getZ());
		spider.setYRot(skeleton.getYRot());
		spider.setXRot(skeleton.getXRot());
		spider.finalizeSpawn(world, difficulty, EntitySpawnReason.JOCKEY, null);
		level.tryAddFreshEntityWithPassengers(spider);
		skeleton.startRiding(spider);
		ensureBowEquipped(skeleton);
		applySpawnArmorLoadout(skeleton, root, world.getRandom());
	}

	public static boolean applyCustomSkeletonRangedAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
		if (skeleton == null || target == null || !target.isAlive() || !snapshot.enabled) {
			return false;
		}
		JsonObject root = skeletonRoot(skeleton.getType());
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return false;
		}
		InteractionHand bowHand = resolveBowHand(skeleton);
		if (bowHand == null) {
			return false;
		}
		ItemStack bowStack = skeleton.getItemInHand(bowHand);
		ItemStack projectileStack = skeleton.getProjectile(bowStack);
		if (projectileStack.isEmpty()) {
			projectileStack = new ItemStack(Items.ARROW);
		}
		AbstractArrow arrow = ((AbstractSkeletonArrowInvoker) skeleton).madokuCraft$invokeGetArrow(projectileStack, pullProgress, bowStack);
		if (arrow == null) {
			return false;
		}

		double accuracy = resolveScaledAttackAccuracy(readDouble(root, MadokuMobConfig.FIELD_ATTACK_ACCURACY, 0.7D), skeleton.level().getDifficulty(), isHardcoreWorld(skeleton.level()));
		double rangedDamage = resolveSkeletonRangedDamage(skeleton, root);
		accuracy = MadokuDifficulty.resolveMobAttackAccuracyScaling(skeleton, accuracy);
		ShotVector shot = resolveShotVector(skeleton, arrow, target, accuracy);
		arrow.shoot(shot.vector.x, shot.vector.y, shot.vector.z, 1.6F, 0.0F);
		arrow.setCritArrow(false);
		if (skeleton.getType() == EntityType.WITHER_SKELETON) {
			arrow.setRemainingFireTicks(0);
		}
		FIXED_ARROW_DAMAGE.put(arrow.getUUID(), (float) Math.max(0.0D, rangedDamage));
		trackManagedMobArrow(arrow, resolveServer(skeleton));
		if (shot.guaranteedHit) {
			double speed = Math.max(MIN_HOMING_SPEED, arrow.getDeltaMovement().length());
			arrow.setNoGravity(true);
			arrow.addTag(HOMING_PROJECTILE_TAG);
			HOMING_ARROWS.put(arrow.getUUID(), new HomingArrowState(target.getUUID(), speed, HOMING_LIFETIME_TICKS));
			requestRuntimeProcessing(resolveServer(skeleton), 1L);
		}
		skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
		skeleton.level().addFreshEntity(arrow);
		return true;
	}

	public static int resolveSkeletonRangedAttackIntervalTicks(AbstractSkeleton skeleton) {
		if (skeleton == null || !snapshot.enabled) {
			return -1;
		}
		JsonObject root = skeletonRoot(skeleton.getType());
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return -1;
		}
		double interval = readDouble(root, MadokuMobConfig.FIELD_ATTACK_INTERVAL, 20.0D);
		return Math.max(1, (int) Math.round(interval));
	}

	public static int resolveSkeletonChargeUpTicks(Monster attacker) {
		if (!(attacker instanceof AbstractSkeleton skeleton) || !snapshot.enabled) {
			return -1;
		}
		JsonObject root = skeletonRoot(skeleton.getType());
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return -1;
		}
		double charge = readDouble(root, MadokuMobConfig.FIELD_CHARGE_UP_TICKS, 10.0D);
		return Math.max(1, (int) Math.round(charge));
	}

	public static boolean applyCustomPillagerRangedShot(Pillager pillager, LivingEntity target, float speed) {
		if (pillager == null || target == null || !target.isAlive() || !snapshot.enabled) {
			return false;
		}
		JsonObject root = root(MadokuMobConfig.FILE_PILLAGER);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return false;
		}
		InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(pillager, Items.CROSSBOW);
		ItemStack stack = pillager.getItemInHand(hand);
		if (!(stack.getItem() instanceof CrossbowItem crossbowItem)) {
			return false;
		}
		float spread = 14.0F - (pillager.level().getDifficulty().getId() * 4.0F);
		crossbowItem.performShooting(pillager.level(), pillager, hand, stack, speed, spread, target);
		pillager.onCrossbowAttackPerformed();
		markCrossbowAttackCooldown(pillager);
		return true;
	}

	public static void applyWitherSkeletonArrowHitEffect(LivingEntity target, Entity attacker) {
		if (target == null || attacker == null || target.level().isClientSide() || !snapshot.enabled) {
			return;
		}
		if (!(attacker instanceof AbstractSkeleton skeleton) || skeleton.getType() != EntityType.WITHER_SKELETON) {
			return;
		}
		JsonObject root = skeletonRoot(skeleton.getType());
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return;
		}
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_EFFECT_DURATION_TICKS), skeleton);
	}

	private static void applyPiglinSpawnOverrides(Piglin piglin, ServerLevelAccessor world) {
		if (piglin == null || world == null || !snapshot.enabled) {
			return;
		}
		JsonObject root = root(MadokuMobConfig.FILE_PIGLIN);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return;
		}
		boolean baby = rollPiglinBabySpawn(root, world);
		piglin.setBaby(baby);
		clearMobEquipment(piglin);
		applySpawnArmorLoadout(piglin, root, world.getRandom());
		if (!baby) {
			equipPiglinWeapon(piglin, piglinAdultRoot(root), world.getRandom());
			normalizePiglinWeapon(piglin);
		}
		applyUniversalStats(piglin, piglinVariantRoot(root, baby));
	}

	public static boolean tickPillagerAttackCooldown(Monster attacker) {
		String fileKey = resolveCrossbowShooterFileKey(attacker);
		if (fileKey == null || !snapshot.enabled) {
			return false;
		}
		JsonObject root = root(fileKey);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return false;
		}
		Integer remaining = PILLAGER_ATTACK_COOLDOWNS.get(attacker.getUUID());
		if (remaining == null || remaining <= 0) {
			PILLAGER_ATTACK_COOLDOWNS.remove(attacker.getUUID());
			return false;
		}
		if (remaining == 1) {
			PILLAGER_ATTACK_COOLDOWNS.remove(attacker.getUUID());
			return false;
		}
		PILLAGER_ATTACK_COOLDOWNS.put(attacker.getUUID(), remaining - 1);
		return true;
	}

	public static void markPillagerAttackCooldownFromShot(Monster attacker) {
		if (attacker != null) {
			markCrossbowAttackCooldown(attacker);
		}
	}

	public static int resolveCrossbowPostChargeDelay(Monster attacker, int vanillaDelay) {
		if (resolveCrossbowShooterFileKey(attacker) == null) {
			return vanillaDelay;
		}
		return resolveCrossbowChargeUpTicks(attacker) > 0 ? 1 : vanillaDelay;
	}

	public static int resolveCrossbowChargeDurationOverride(LivingEntity user) {
		return user instanceof Monster monster ? resolveCrossbowChargeUpTicks(monster) : -1;
	}

	public static boolean applyPillagerProjectileAccuracyOverride(
		Projectile projectile,
		LivingEntity shooter,
		LivingEntity target,
		double velocityX,
		double velocityY,
		double velocityZ,
		float speed,
		float divergence
	) {
		String fileKey = resolveCrossbowShooterFileKey(shooter);
		if (fileKey == null || target == null || !target.isAlive() || !snapshot.enabled) {
			return false;
		}
		JsonObject root = root(fileKey);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return false;
		}
		double accuracy = resolveScaledAttackAccuracy(readDouble(root, MadokuMobConfig.FIELD_ATTACK_ACCURACY, 0.7D), shooter.level().getDifficulty(), isHardcoreWorld(shooter.level()));
		accuracy = shooter instanceof Mob mob ? MadokuDifficulty.resolveMobAttackAccuracyScaling(mob, accuracy) : accuracy;
		if (shooter.getRandom().nextDouble() <= accuracy) {
			projectile.shoot(velocityX, velocityY, velocityZ, speed, 0.0F);
			if (projectile instanceof AbstractArrow arrow) {
				trackManagedMobArrow(arrow, resolveServer(shooter));
				double homingSpeed = Math.max(MIN_HOMING_SPEED, arrow.getDeltaMovement().length());
				arrow.setNoGravity(true);
				arrow.addTag(HOMING_PROJECTILE_TAG);
				HOMING_ARROWS.put(arrow.getUUID(), new HomingArrowState(target.getUUID(), homingSpeed, HOMING_LIFETIME_TICKS));
				requestRuntimeProcessing(resolveServer(shooter), 1L);
				if (shooter instanceof Piglin) {
					arrow.setRemainingFireTicks(100);
				}
			}
			return true;
		}
		Vec3 missed = resolveMissVector(velocityX, velocityY, velocityZ, accuracy, shooter);
		projectile.shoot(missed.x, missed.y, missed.z, speed, 0.0F);
		if (projectile instanceof AbstractArrow arrow) {
			trackManagedMobArrow(arrow, resolveServer(shooter));
			if (shooter instanceof Piglin) {
				arrow.setRemainingFireTicks(100);
			}
			HOMING_ARROWS.remove(arrow.getUUID());
		}
		return true;
	}

	public static float resolveProjectileDamageOverride(AbstractArrow arrow, float fallbackDamage) {
		if (arrow == null) {
			return fallbackDamage;
		}
		Float fixed = FIXED_ARROW_DAMAGE.remove(arrow.getUUID());
		if (fixed != null) {
			return Math.max(0.0F, fixed);
		}
		if (arrow.getOwner() instanceof AbstractSkeleton skeleton && snapshot.enabled) {
			JsonObject root = skeletonRoot(skeleton.getType());
			if (readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
				return (float) Math.max(0.0D, resolveSkeletonRangedDamage(skeleton, root));
			}
		}
		if (!(arrow.getOwner() instanceof LivingEntity owner) || !snapshot.enabled) {
			return fallbackDamage;
		}
		String fileKey = resolveCrossbowShooterFileKey(owner);
		if (fileKey == null) {
			return fallbackDamage;
		}
		JsonObject root = root(fileKey);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return fallbackDamage;
		}
		double damage = resolveScaledRangedDamage(readDouble(root, MadokuMobConfig.FIELD_RANGED_DAMAGE, 6.0D), owner.level().getDifficulty(), isHardcoreWorld(owner.level()));
		damage = owner instanceof Mob mob ? MadokuDifficulty.resolveMobRangedDamageScaling(mob, damage) : damage;
		return (float) Math.max(0.0D, damage);
	}

	public static boolean isManagedHomingArrow(AbstractArrow arrow) {
		return arrow != null && (HOMING_ARROWS.containsKey(arrow.getUUID()) || arrow.entityTags().contains(HOMING_PROJECTILE_TAG));
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
		if (level == null || creeper == null || !snapshot.enabled) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		JsonObject root = root(MadokuMobConfig.FILE_CREEPER);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		JsonObject variant = creeper.isPowered() ? readObject(root, MadokuMobConfig.FIELD_CHARGED_CREEPER) : readObject(root, MadokuMobConfig.FIELD_CREEPER);
		Double baseChance = clampOptional(readOptionalDouble(variant, MadokuMobConfig.FIELD_EXPLOSION_DESTRUCTION_CHANCE), 0.0D, 1.0D);
		if (baseChance == null) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		double chance = Mth.clamp(
			resolveDifficultyAdjustedValue(
				level.getDifficulty(),
				isHardcoreWorld(level),
				baseChance,
				readDouble(root, MadokuMobConfig.FIELD_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP, 0.2D),
				0.0D
			),
			0.0D,
			1.0D
		);
		Double configuredPower = readOptionalDouble(variant, MadokuMobConfig.FIELD_EXPLOSION_POWER);
		float power = configuredPower == null ? vanillaPower : configuredPower.floatValue();
		power = (float) resolveDifficultyAdjustedValue(level.getDifficulty(), isHardcoreWorld(level), Math.max(0.0D, power), CREEPER_EXPLOSION_POWER_DIFFICULTY_STEP, 0.0D);
		power = (float) Math.max(0.0D, power + MadokuDifficulty.resolveCreeperExplosionPowerScaling(creeper));
		Level.ExplosionInteraction interaction = level.getRandom().nextDouble() < chance
			? Level.ExplosionInteraction.MOB
			: Level.ExplosionInteraction.NONE;
		level.explode(source, x, y, z, power, interaction);
	}

	public static float resolveCreeperGriefExplosionRadius(ServerExplosion explosion, float fallbackRadius) {
		if (explosion == null || !(explosion.getDirectSourceEntity() instanceof Creeper) || !snapshot.enabled) {
			return Math.max(0.0F, fallbackRadius);
		}
		JsonObject root = root(MadokuMobConfig.FILE_CREEPER);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return Math.max(0.0F, fallbackRadius);
		}
		return (float) (Math.max(0.0F, fallbackRadius) * Mth.clamp(readDouble(root, MadokuMobConfig.FIELD_GRIEF_POWER_MULTIPLIER, 0.5D), 0.0D, 1.0D));
	}

	public static float resolveFixedPlayerExplosionDamage(Creeper creeper, float fallbackExplosionRadius) {
		double explosionPower = Math.max(0.0D, fallbackExplosionRadius);
		if (creeper == null || !snapshot.enabled) {
			return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
		}
		JsonObject root = root(MadokuMobConfig.FILE_CREEPER);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
		}
		JsonObject variant = creeper.isPowered() ? readObject(root, MadokuMobConfig.FIELD_CHARGED_CREEPER) : readObject(root, MadokuMobConfig.FIELD_CREEPER);
		Double configuredPower = readOptionalDouble(variant, MadokuMobConfig.FIELD_EXPLOSION_POWER);
		if (configuredPower != null) {
			explosionPower = Math.max(0.0D, configuredPower);
		}
		explosionPower = resolveDifficultyAdjustedValue(creeper.level().getDifficulty(), isHardcoreWorld(creeper.level()), explosionPower, CREEPER_EXPLOSION_POWER_DIFFICULTY_STEP, 0.0D);
		explosionPower = Math.max(0.0D, explosionPower + MadokuDifficulty.resolveCreeperExplosionPowerScaling(creeper));
		return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
	}

	public static void ensureBowEquipped(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return;
		}
		ItemStack main = skeleton.getItemBySlot(EquipmentSlot.MAINHAND);
		if (main.isEmpty() || !main.is(Items.BOW)) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
	}

	public static boolean shouldSkeletonMeleeIgnoreArmor(DamageSource source) {
		if (!snapshot.enabled || source == null) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof AbstractSkeleton skeleton) || source.getDirectEntity() != attacker) {
			return false;
		}
		JsonObject root = skeletonRoot(skeleton.getType());
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return false;
		}
		double withoutBow = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_WITHOUT_BOW_SPAWN_WEIGHT, 5.0D));
		if (withoutBow <= 0.0D) {
			return false;
		}
		return resolveBowHand(skeleton) == null;
	}

	private static boolean applyLoadedEntityRules(LivingEntity entity) {
		if (entity == null || entity.level().isClientSide() || !snapshot.enabled) {
			return false;
		}
		if (entity instanceof Zombie zombie) {
			JsonObject root = zombieRoot(zombie.getType());
			if (readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
				disableZombieReinforcements(zombie);
				JsonObject variant = zombie.isBaby() ? zombieBabyRoot(zombie.getType(), root) : zombieAdultRoot(zombie.getType(), root);
				return applyUniversalStats(zombie, variant);
			}
			return false;
		}
		if (entity instanceof Spider spider) {
			if (spider.getType() == EntityType.CAVE_SPIDER) {
				JsonObject root = root(MadokuMobConfig.FILE_CAVE_SPIDER);
				return readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true) && applyUniversalStats(spider, root);
			}
			JsonObject root = root(MadokuMobConfig.FILE_SPIDER);
			return readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true) && applyUniversalStats(spider, root);
		}
		if (entity instanceof AbstractSkeleton skeleton) {
			JsonObject root = skeletonRoot(skeleton.getType());
			boolean modified = readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true) && applyUniversalStats(skeleton, root);
			if (skeleton.getType() == EntityType.WITHER_SKELETON) {
				modified |= normalizeWitherSkeletonWeapon(skeleton);
			}
			return modified;
		}
		if (entity instanceof Pillager pillager) {
			JsonObject root = root(MadokuMobConfig.FILE_PILLAGER);
			return readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true) && applyUniversalStats(pillager, root);
		}
		if (entity instanceof Piglin piglin) {
			JsonObject root = root(MadokuMobConfig.FILE_PIGLIN);
			if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
				return false;
			}
			JsonObject variant = piglinVariantRoot(root, piglin.isBaby());
			boolean modified = applyUniversalStats(piglin, variant);
			if (piglin.isBaby()) {
				clearPiglinMainHand(piglin);
				return true;
			}
			modified |= normalizePiglinWeapon(piglin);
			return modified;
		}
		if (entity instanceof Creeper creeper) {
			JsonObject root = root(MadokuMobConfig.FILE_CREEPER);
			return readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true) && applyCreeperRuntimeStats(creeper, root);
		}
		return false;
	}

	private static void applyDifficultyScalingAfterMobOverrides(LivingEntity entity, ServerLevel level, boolean loadedMobOverridesApplied) {
		if (!snapshot.enabled || entity == null || !(entity instanceof Mob mob) || level == null || !MadokuDifficulty.isEnabled()) {
			return;
		}
		if (loadedMobOverridesApplied && mob instanceof DifficultyScaledMob scaledMob && scaledMob.madokuCraft$getSpawnDifficultyAdjustment() > 0) {
			MadokuDifficulty.reapplySpawnScalingFromStoredAdjustment(mob);
			return;
		}
		MadokuDifficulty.applySpawnScalingIfUnscaled(mob, level);
	}

	private static void applyCreeperSpawnOverrides(Creeper creeper, ServerLevelAccessor world, DifficultyInstance difficulty) {
		if (creeper == null || world == null || difficulty == null || !snapshot.enabled || creeper.isPowered()) {
			return;
		}
		JsonObject root = root(MadokuMobConfig.FILE_CREEPER);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return;
		}
		SpawnWeightPair shifted = resolveDifficultyShiftedSpawnWeights(
			readDouble(root, MadokuMobConfig.FIELD_CREEPER_SPAWN_WEIGHT, 95.0D),
			readDouble(root, MadokuMobConfig.FIELD_CHARGED_CREEPER_SPAWN_WEIGHT, 5.0D),
			difficulty.getDifficulty(),
			isHardcoreWorld(creeper.level()),
			SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		double total = shifted.regularWeight + shifted.specialWeight;
		if (total <= 0.0D) {
			return;
		}
		boolean charged = (world.getRandom().nextDouble() * total) >= shifted.regularWeight;
		if (charged) {
			creeper.getEntityData().set(CreeperPoweredAccessor.madokuCraft$getDataIsPowered(), true);
		}
	}

	private static boolean applyUniversalStats(LivingEntity entity, JsonObject root) {
		if (entity == null || root == null) {
			return false;
		}
		boolean modified = false;
		double oldMaxHealth = entity.getMaxHealth();
		boolean hardcore = isHardcoreWorld(entity.level());
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.MAX_HEALTH,
			resolveUniversalBaseStat(
				readOptionalPositive(root, MadokuMobConfig.FIELD_HEALTH),
				entity.level().getDifficulty(),
				hardcore,
				HEALTH_DIFFICULTY_STEP,
				0.0D
			)
		);
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.ARMOR,
			resolveUniversalBaseStat(
				readOptionalNonNegative(root, MadokuMobConfig.FIELD_ARMOR),
				entity.level().getDifficulty(),
				hardcore,
				ARMOR_DIFFICULTY_STEP,
				0.0D
			)
		);
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.ATTACK_DAMAGE,
			resolveUniversalBaseStat(
				readOptionalNonNegative(root, MadokuMobConfig.FIELD_DAMAGE),
				entity.level().getDifficulty(),
				hardcore,
				DAMAGE_DIFFICULTY_STEP,
				0.0D
			)
		);
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.MOVEMENT_SPEED,
			resolveUniversalBaseStat(
				readOptionalPositive(root, MadokuMobConfig.FIELD_MOVEMENT_SPEED),
				entity.level().getDifficulty(),
				hardcore,
				MOVEMENT_SPEED_DIFFICULTY_STEP,
				0.0D
			)
		);
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.KNOCKBACK_RESISTANCE,
			resolveUniversalBaseStat(
				clampOptional(readOptionalDouble(root, MadokuMobConfig.FIELD_KNOCKBACK_RESISTANCE), 0.0D, 1.0D),
				entity.level().getDifficulty(),
				hardcore,
				KNOCKBACK_RESISTANCE_DIFFICULTY_STEP,
				0.0D
			)
		);
		Double baseScale = readOptionalPositive(root, MadokuMobConfig.FIELD_SCALE);
		Double resolvedScale = baseScale;
		if (baseScale != null) {
			double scaleDifficultyStep = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_SCALE_DIFFICULTY_STEP, 0.0D));
			if (scaleDifficultyStep > 0.0D) {
				resolvedScale = resolveDifficultyAdjustedValue(
					entity.level().getDifficulty(),
					hardcore,
					baseScale,
					scaleDifficultyStep,
					0.01D
				);
			}
		}
		modified |= setBaseValueIfPresent(entity, Attributes.SCALE, resolvedScale);
		applyExperienceDrop(entity, readOptionalIntNonNegative(root, MadokuMobConfig.FIELD_EXPERIENCE_DROP));
		rescaleCurrentHealth(entity, oldMaxHealth);
		return modified;
	}

	private static boolean applyCreeperRuntimeStats(Creeper creeper, JsonObject root) {
		JsonObject variant = creeper.isPowered() ? readObject(root, MadokuMobConfig.FIELD_CHARGED_CREEPER) : readObject(root, MadokuMobConfig.FIELD_CREEPER);
		boolean modified = applyUniversalStats(creeper, variant);
		CreeperAccessor accessor = (CreeperAccessor) creeper;
		Double fuseLength = readOptionalPositive(variant, MadokuMobConfig.FIELD_FUSE_LENGTH);
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
		Double explosionPower = readOptionalDouble(variant, MadokuMobConfig.FIELD_EXPLOSION_POWER);
		if (explosionPower != null) {
			double resolvedPower = resolveDifficultyAdjustedValue(
				creeper.level().getDifficulty(),
				isHardcoreWorld(creeper.level()),
				explosionPower,
				CREEPER_EXPLOSION_POWER_DIFFICULTY_STEP,
				0.0D
			) + MadokuDifficulty.resolveCreeperExplosionPowerScaling(creeper);
			int radius = Math.max(0, (int) Math.round(resolvedPower));
			accessor.madokuCraft$setExplosionRadius(radius);
		}
		return modified;
	}

	private static void disableZombieReinforcements(Zombie zombie) {
		AttributeInstance instance = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (instance == null) {
			return;
		}
		instance.removeModifiers();
		instance.setBaseValue(0.0D);
	}

	private static void clearMobEquipment(Mob mob) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!mob.getItemBySlot(slot).isEmpty()) {
				mob.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	private static void clearArmorSlots(Mob mob) {
		if (mob == null) {
			return;
		}
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!mob.getItemBySlot(slot).isEmpty()) {
				mob.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	private static void applySpawnArmorLoadout(Mob mob, JsonObject root, RandomSource random) {
		if (mob == null || root == null || random == null) {
			return;
		}
		clearArmorSlots(mob);
		double armorWeight = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_ARMOR_SPAWN_WEIGHT, 10.0D));
		double noArmorWeight = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_NO_ARMOR_SPAWN_WEIGHT, 90.0D));
		double total = armorWeight + noArmorWeight;
		if (total <= 0.0D || (random.nextDouble() * total) >= armorWeight) {
			return;
		}
		equipSpawnArmorLoadout(mob, root, random);
	}

	private static void equipSpawnArmorLoadout(Mob mob, JsonObject root, RandomSource random) {
		SpawnArmorMaterial material = rollSpawnArmorMaterial(root, random);
		SpawnArmorCoverage coverage = rollSpawnArmorCoverage(root, random);
		if (material == null || coverage == null) {
			return;
		}
		switch (coverage) {
			case HELMET_ONLY -> equipArmorPiece(mob, material, EquipmentSlot.HEAD);
			case HELMET_BOOTS -> {
				equipArmorPiece(mob, material, EquipmentSlot.HEAD);
				equipArmorPiece(mob, material, EquipmentSlot.FEET);
			}
			case FULL_SET -> {
				equipArmorPiece(mob, material, EquipmentSlot.HEAD);
				equipArmorPiece(mob, material, EquipmentSlot.CHEST);
				equipArmorPiece(mob, material, EquipmentSlot.LEGS);
				equipArmorPiece(mob, material, EquipmentSlot.FEET);
			}
		}
	}

	private static void equipArmorPiece(Mob mob, SpawnArmorMaterial material, EquipmentSlot slot) {
		if (mob == null || material == null || slot == null) {
			return;
		}
		ItemStack stack = armorStack(material, slot);
		if (!stack.isEmpty()) {
			mob.setItemSlot(slot, stack);
		}
	}

	private static ItemStack armorStack(SpawnArmorMaterial material, EquipmentSlot slot) {
		if (material == null || slot == null) {
			return ItemStack.EMPTY;
		}
		return switch (material) {
			case NETHERITE -> switch (slot) {
				case HEAD -> new ItemStack(Items.NETHERITE_HELMET);
				case CHEST -> new ItemStack(Items.NETHERITE_CHESTPLATE);
				case LEGS -> new ItemStack(Items.NETHERITE_LEGGINGS);
				case FEET -> new ItemStack(Items.NETHERITE_BOOTS);
				default -> ItemStack.EMPTY;
			};
			case DIAMOND -> switch (slot) {
				case HEAD -> new ItemStack(Items.DIAMOND_HELMET);
				case CHEST -> new ItemStack(Items.DIAMOND_CHESTPLATE);
				case LEGS -> new ItemStack(Items.DIAMOND_LEGGINGS);
				case FEET -> new ItemStack(Items.DIAMOND_BOOTS);
				default -> ItemStack.EMPTY;
			};
			case GOLD -> switch (slot) {
				case HEAD -> new ItemStack(Items.GOLDEN_HELMET);
				case CHEST -> new ItemStack(Items.GOLDEN_CHESTPLATE);
				case LEGS -> new ItemStack(Items.GOLDEN_LEGGINGS);
				case FEET -> new ItemStack(Items.GOLDEN_BOOTS);
				default -> ItemStack.EMPTY;
			};
			case IRON -> switch (slot) {
				case HEAD -> new ItemStack(Items.IRON_HELMET);
				case CHEST -> new ItemStack(Items.IRON_CHESTPLATE);
				case LEGS -> new ItemStack(Items.IRON_LEGGINGS);
				case FEET -> new ItemStack(Items.IRON_BOOTS);
				default -> ItemStack.EMPTY;
			};
			case COPPER -> switch (slot) {
				case HEAD -> new ItemStack(Items.COPPER_HELMET);
				case CHEST -> new ItemStack(Items.COPPER_CHESTPLATE);
				case LEGS -> new ItemStack(Items.COPPER_LEGGINGS);
				case FEET -> new ItemStack(Items.COPPER_BOOTS);
				default -> ItemStack.EMPTY;
			};
			case LEATHER -> switch (slot) {
				case HEAD -> new ItemStack(Items.LEATHER_HELMET);
				case CHEST -> new ItemStack(Items.LEATHER_CHESTPLATE);
				case LEGS -> new ItemStack(Items.LEATHER_LEGGINGS);
				case FEET -> new ItemStack(Items.LEATHER_BOOTS);
				default -> ItemStack.EMPTY;
			};
		};
	}

	private static SpawnArmorMaterial rollSpawnArmorMaterial(JsonObject root, RandomSource random) {
		double netherite = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_ARMOR_NETHERITE_WEIGHT, 1.0D));
		double diamond = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_ARMOR_DIAMOND_WEIGHT, 5.0D));
		double gold = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_ARMOR_GOLD_WEIGHT, 10.0D));
		double iron = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_ARMOR_IRON_WEIGHT, 17.0D));
		double copper = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_ARMOR_COPPER_WEIGHT, 28.0D));
		double leather = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_ARMOR_LEATHER_WEIGHT, 39.0D));
		double total = netherite + diamond + gold + iron + copper + leather;
		if (total <= 0.0D) {
			return null;
		}
		double roll = random.nextDouble() * total;
		if (roll < netherite) {
			return SpawnArmorMaterial.NETHERITE;
		}
		roll -= netherite;
		if (roll < diamond) {
			return SpawnArmorMaterial.DIAMOND;
		}
		roll -= diamond;
		if (roll < gold) {
			return SpawnArmorMaterial.GOLD;
		}
		roll -= gold;
		if (roll < iron) {
			return SpawnArmorMaterial.IRON;
		}
		roll -= iron;
		if (roll < copper) {
			return SpawnArmorMaterial.COPPER;
		}
		return SpawnArmorMaterial.LEATHER;
	}

	private static SpawnArmorCoverage rollSpawnArmorCoverage(JsonObject root, RandomSource random) {
		double helmetOnly = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_ARMOR_HELMET_ONLY_WEIGHT, 60.0D));
		double helmetBoots = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_ARMOR_HELMET_BOOTS_WEIGHT, 30.0D));
		double fullSet = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_ARMOR_FULL_SET_WEIGHT, 10.0D));
		double total = helmetOnly + helmetBoots + fullSet;
		if (total <= 0.0D) {
			return null;
		}
		double roll = random.nextDouble() * total;
		if (roll < helmetOnly) {
			return SpawnArmorCoverage.HELMET_ONLY;
		}
		roll -= helmetOnly;
		if (roll < helmetBoots) {
			return SpawnArmorCoverage.HELMET_BOOTS;
		}
		return SpawnArmorCoverage.FULL_SET;
	}

	private static void equipPiglinWeapon(Piglin piglin, JsonObject root, RandomSource random) {
		if (piglin == null || root == null || random == null) {
			return;
		}
		ItemStack weapon = rollPiglinSpawnWeapon(root, random);
		if (!weapon.isEmpty()) {
			piglin.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}
	}

	private static ItemStack rollPiglinSpawnWeapon(JsonObject root, RandomSource random) {
		double crossbow = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_CROSSBOW_SPAWN_WEIGHT, 50.0D));
		double goldenSword = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_GOLDEN_SWORD_SPAWN_WEIGHT, 50.0D));
		double total = crossbow + goldenSword;
		if (total <= 0.0D) {
			return ItemStack.EMPTY;
		}
		double roll = random.nextDouble() * total;
		if (roll < crossbow) {
			return new ItemStack(Items.CROSSBOW);
		}
		ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
		sword.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		return sword;
	}

	private static boolean rollPiglinBabySpawn(JsonObject root, ServerLevelAccessor world) {
		if (root == null || world == null) {
			return false;
		}
		double adultWeight = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_ADULT_PIGLIN_SPAWN_WEIGHT, 90.0D));
		double babyWeight = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_BABY_PIGLIN_SPAWN_WEIGHT, 10.0D));
		double total = adultWeight + babyWeight;
		if (total <= 0.0D) {
			return false;
		}
		return (world.getRandom().nextDouble() * total) < babyWeight;
	}

	private static JsonObject piglinVariantRoot(JsonObject root, boolean baby) {
		return baby ? readObject(root, MadokuMobConfig.FIELD_BABY_PIGLIN) : readObject(root, MadokuMobConfig.FIELD_ADULT_PIGLIN);
	}

	private static JsonObject piglinAdultRoot(JsonObject root) {
		return readObject(root, MadokuMobConfig.FIELD_ADULT_PIGLIN);
	}

	private static void clearPiglinMainHand(Piglin piglin) {
		if (piglin != null) {
			piglin.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	private static boolean normalizePiglinWeapon(Piglin piglin) {
		if (piglin == null) {
			return false;
		}
		ItemStack mainHand = piglin.getMainHandItem();
		if (!mainHand.is(Items.GOLDEN_SWORD)) {
			return false;
		}
		mainHand.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		return true;
	}

	private static void applyWitherSkeletonSpawnOverrides(AbstractSkeleton skeleton, ServerLevelAccessor world) {
		if (skeleton == null || world == null) {
			return;
		}
		JsonObject root = root(MadokuMobConfig.FILE_WITHER_SKELETON);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return;
		}
		clearMobEquipment(skeleton);
		applyUniversalStats(skeleton, root);
		applySpawnArmorLoadout(skeleton, root, world.getRandom());
		ItemStack weapon = rollWitherSkeletonSpawnWeapon(root, world.getRandom());
		if (!weapon.isEmpty()) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}
	}

	private static ItemStack rollWitherSkeletonSpawnWeapon(JsonObject root, RandomSource random) {
		double sword = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_WITHER_SWORD_SPAWN_WEIGHT, 90.0D));
		double bow = Math.max(0.0D, readDouble(root, MadokuMobConfig.FIELD_WITHER_BOW_SPAWN_WEIGHT, 10.0D));
		double total = sword + bow;
		if (total <= 0.0D) {
			return ItemStack.EMPTY;
		}
		double roll = random.nextDouble() * total;
		if (roll < sword) {
			ItemStack weapon = new ItemStack(Items.NETHERITE_SWORD);
			weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
			return weapon;
		}
		return new ItemStack(Items.BOW);
	}

	private static boolean normalizeWitherSkeletonWeapon(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return false;
		}
		ItemStack mainHand = skeleton.getMainHandItem();
		if (!mainHand.is(Items.NETHERITE_SWORD)) {
			return false;
		}
		mainHand.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		return true;
	}

	private static void applyExperienceDrop(LivingEntity entity, Integer experienceDrop) {
		if (entity instanceof Mob mob && experienceDrop != null) {
			((MobExperienceAccessor) mob).madokuCraft$setXpReward(Math.max(0, experienceDrop));
		}
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
		if (entity instanceof Pillager || entity instanceof Piglin) {
			PILLAGER_ATTACK_COOLDOWNS.remove(id);
		}
		PENDING_CAVE_SPIDER_REPLACEMENTS.remove(id);
	}

	private static void runRuntimeTask(MinecraftServer server, MadokuScheduler.TaskContext context, JsonObject payload) {
		if (context != null) {
			runtimeSchedulerId = context.getSchedulerId();
		}
		runtimeTaskScheduled = false;
		tickManagedMobArrows(server);
		tickHomingProjectiles(server);
		processPendingCaveSpiderReplacements(server);
		if (!MANAGED_MOB_ARROWS.isEmpty() || !HOMING_ARROWS.isEmpty() || !PENDING_CAVE_SPIDER_REPLACEMENTS.isEmpty()) {
			requestRuntimeProcessing(server, 1L);
		}
	}

	private static void requestRuntimeProcessing(MinecraftServer server, long delayTicks) {
		if (server == null || !snapshot.enabled || runtimeTaskScheduled) {
			return;
		}
		String schedulerId = ensureRuntimeSchedulerExists();
		if (enqueueRuntimeTask(schedulerId, delayTicks)) {
			runtimeTaskScheduled = true;
			return;
		}
		runtimeSchedulerId = MadokuScheduler.createScheduler(MadokuScheduler.SchedulerOwner.global(MOB_SCHEDULER_OWNER_ID));
		if (enqueueRuntimeTask(runtimeSchedulerId, delayTicks)) {
			runtimeTaskScheduled = true;
		}
	}

	private static String ensureRuntimeSchedulerExists() {
		if (runtimeSchedulerId == null || runtimeSchedulerId.isBlank()) {
			runtimeSchedulerId = MadokuScheduler.createOrGetScheduler(MadokuScheduler.SchedulerOwner.global(MOB_SCHEDULER_OWNER_ID));
		}
		return runtimeSchedulerId;
	}

	private static boolean enqueueRuntimeTask(String schedulerId, long delayTicks) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}
		MadokuScheduler.EnqueueStatus status = MadokuScheduler.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_MOB_RUNTIME_TICK,
			new JsonObject(),
			MadokuScheduler.TickDomain.GAMEPLAY
		);
		return status == MadokuScheduler.EnqueueStatus.ACCEPTED || status == MadokuScheduler.EnqueueStatus.QUEUE_FULL;
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
				emitManagedArrowExpired(arrow);
				arrow.discard();
				removeArrowRuntimeState(arrowId);
				continue;
			}
			MANAGED_MOB_ARROWS.put(arrowId, remainingTicks - 1);
		}
	}

	private static void processPendingCaveSpiderReplacements(MinecraftServer server) {
		if (server == null || PENDING_CAVE_SPIDER_REPLACEMENTS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, EntitySpawnReason> entry : PENDING_CAVE_SPIDER_REPLACEMENTS.entrySet()) {
			Entity entity = findEntity(server, entry.getKey());
			if (!(entity instanceof Spider spider) || !spider.isAlive() || spider.getType() != EntityType.SPIDER) {
				PENDING_CAVE_SPIDER_REPLACEMENTS.remove(entry.getKey());
				continue;
			}
			if (!(spider.level() instanceof ServerLevel level)) {
				PENDING_CAVE_SPIDER_REPLACEMENTS.remove(entry.getKey());
				continue;
			}
			EntitySpawnReason reason = entry.getValue() == null ? EntitySpawnReason.NATURAL : entry.getValue();
			CaveSpider caveSpider = EntityType.CAVE_SPIDER.create(level, reason);
			if (caveSpider == null) {
				PENDING_CAVE_SPIDER_REPLACEMENTS.remove(entry.getKey());
				continue;
			}
			caveSpider.setPos(spider.getX(), spider.getY(), spider.getZ());
			caveSpider.setYRot(spider.getYRot());
			caveSpider.setXRot(spider.getXRot());
			caveSpider.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(spider.position())), reason, null);
			level.tryAddFreshEntityWithPassengers(caveSpider);
			spider.discard();
			PENDING_CAVE_SPIDER_REPLACEMENTS.remove(entry.getKey());
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
		emitManagedArrowTracked(arrow);
		requestRuntimeProcessing(server, 1L);
	}

	private static void removeArrowRuntimeState(UUID arrowId) {
		if (arrowId == null) {
			return;
		}
		HOMING_ARROWS.remove(arrowId);
		FIXED_ARROW_DAMAGE.remove(arrowId);
		MANAGED_MOB_ARROWS.remove(arrowId);
	}

	private static void spawnSpiderJockey(Spider spider, ServerLevelAccessor world, DifficultyInstance difficulty) {
		ServerLevel level = world.getLevel();
		AbstractSkeleton skeleton = EntityType.SKELETON.create(level, EntitySpawnReason.JOCKEY);
		if (skeleton == null) {
			return;
		}
		skeleton.setPos(spider.getX(), spider.getY(), spider.getZ());
		skeleton.setYRot(spider.getYRot());
		skeleton.setXRot(0.0F);
		skeleton.finalizeSpawn(world, difficulty, EntitySpawnReason.JOCKEY, null);
		ensureBowEquipped(skeleton);
		skeleton.startRiding(spider);
	}

	private static MinecraftServer resolveServer(Entity entity) {
		if (entity == null || !(entity.level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		return serverLevel.getServer();
	}

	private static SpawnOutcome rollSpiderSpawnOutcome(RandomSource random, double spiderWeight, double caveWeight, double jockeyWeight) {
		double s = Math.max(0.0D, spiderWeight);
		double c = Math.max(0.0D, caveWeight);
		double j = Math.max(0.0D, jockeyWeight);
		double total = s + c + j;
		if (total <= 0.0D) {
			return SpawnOutcome.SPIDER;
		}
		double roll = random.nextDouble() * total;
		if (roll < s) {
			return SpawnOutcome.SPIDER;
		}
		roll -= s;
		return roll < c ? SpawnOutcome.CAVE_SPIDER : SpawnOutcome.SPIDER_JOCKEY;
	}

	private static void clearExistingSkeletonPassengers(Spider spider) {
		for (Entity passenger : new ArrayList<>(spider.getPassengers())) {
			if (passenger.getType() == EntityType.SKELETON) {
				passenger.stopRiding();
				passenger.discard();
			}
		}
	}

	private static void markCrossbowAttackCooldown(Monster attacker) {
		int cooldownTicks = Math.max(0, resolveCrossbowAttackIntervalTicks(attacker));
		if (cooldownTicks <= 0) {
			PILLAGER_ATTACK_COOLDOWNS.remove(attacker.getUUID());
			return;
		}
		PILLAGER_ATTACK_COOLDOWNS.put(attacker.getUUID(), cooldownTicks);
	}

	private static int resolveCrossbowAttackIntervalTicks(Monster attacker) {
		String fileKey = resolveCrossbowShooterFileKey(attacker);
		if (fileKey == null || !snapshot.enabled) {
			return -1;
		}
		JsonObject root = root(fileKey);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return -1;
		}
		double interval = readDouble(root, MadokuMobConfig.FIELD_ATTACK_INTERVAL, 20.0D);
		return Math.max(1, (int) Math.round(interval));
	}

	private static int resolveCrossbowChargeUpTicks(Monster attacker) {
		String fileKey = resolveCrossbowShooterFileKey(attacker);
		if (fileKey == null || !snapshot.enabled) {
			return -1;
		}
		JsonObject root = root(fileKey);
		if (!readBoolean(root, MadokuMobConfig.FIELD_ENABLED, true)) {
			return -1;
		}
		double charge = readDouble(root, MadokuMobConfig.FIELD_CHARGE_UP_TICKS, 10.0D);
		return Math.max(1, (int) Math.round(charge));
	}

	private static String resolveCrossbowShooterFileKey(LivingEntity shooter) {
		if (shooter instanceof Pillager) {
			return MadokuMobConfig.FILE_PILLAGER;
		}
		if (shooter instanceof Piglin) {
			return MadokuMobConfig.FILE_PIGLIN;
		}
		return null;
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

	private static ShotVector resolveShotVector(AbstractSkeleton skeleton, AbstractArrow arrow, LivingEntity target, double accuracy) {
		double dx = target.getX() - skeleton.getX();
		double dz = target.getZ() - skeleton.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double dy = target.getY(1.0D / 3.0D) - arrow.getY() + (horizontal * 0.2D);
		Vec3 desired = new Vec3(dx, dy, dz);
		if (desired.lengthSqr() <= 1.0E-6D) {
			return new ShotVector(desired, true);
		}
		if (skeleton.getRandom().nextDouble() <= Mth.clamp(accuracy, 0.0D, 1.0D)) {
			return new ShotVector(desired, true);
		}
		return new ShotVector(resolveMissVector(desired.x, desired.y, desired.z, accuracy, skeleton), false);
	}

	private static Vec3 resolveMissVector(double velocityX, double velocityY, double velocityZ, double attackAccuracy, LivingEntity shooter) {
		Vec3 desired = new Vec3(velocityX, velocityY, velocityZ);
		if (desired.lengthSqr() <= 1.0E-6D) {
			return desired;
		}
		Vec3 normalized = desired.normalize();
		Vec3 lateral = normalized.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (lateral.lengthSqr() <= 1.0E-6D) {
			lateral = normalized.cross(new Vec3(1.0D, 0.0D, 0.0D));
		}
		lateral = lateral.normalize();
		double missFactor = 1.0D - Mth.clamp(attackAccuracy, 0.0D, 1.0D);
		double sideSign = shooter.getRandom().nextBoolean() ? -1.0D : 1.0D;
		double lateralStrength = Mth.lerp(missFactor, 1.4D, 2.4D);
		double verticalStrength = Mth.lerp(missFactor, 0.25D, 0.9D) * (shooter.getRandom().nextBoolean() ? -1.0D : 1.0D);
		Vec3 backwardBias = normalized.scale(-0.35D);
		Vec3 miss = lateral.scale(sideSign * lateralStrength).add(0.0D, verticalStrength, 0.0D).add(backwardBias);
		return miss.lengthSqr() <= 1.0E-6D ? lateral : miss.normalize();
	}

	private static Double resolveUniversalBaseStat(
		Double baseValue,
		Difficulty difficulty,
		boolean hardcore,
		double step,
		double minimum
	) {
		if (baseValue == null) {
			return null;
		}
		return resolveDifficultyAdjustedValue(difficulty, hardcore, baseValue, step, minimum);
	}

	private static double resolveDifficultyAdjustedValue(Difficulty difficulty, boolean hardcore, double baseValue, double step, double minimum) {
		return Math.max(minimum, baseValue + (step * resolveDifficultyTier(difficulty, hardcore)));
	}

	private static double resolveScaledRangedDamage(double base, Difficulty difficulty, boolean hardcore) {
		return resolveDifficultyAdjustedValue(difficulty, hardcore, Math.max(0.0D, base), RANGED_DAMAGE_DIFFICULTY_STEP, 0.0D);
	}

	private static double resolveSkeletonRangedDamage(AbstractSkeleton skeleton, JsonObject root) {
		if (skeleton == null) {
			return 0.0D;
		}
		double rangedDamage = resolveScaledRangedDamage(
			readDouble(root, MadokuMobConfig.FIELD_RANGED_DAMAGE, 4.0D),
			skeleton.level().getDifficulty(),
			isHardcoreWorld(skeleton.level())
		);
		return MadokuDifficulty.resolveMobRangedDamageScaling(skeleton, rangedDamage);
	}

	private static double resolveScaledAttackAccuracy(double base, Difficulty difficulty, boolean hardcore) {
		return Mth.clamp(resolveDifficultyAdjustedValue(difficulty, hardcore, Mth.clamp(base, 0.0D, 1.0D), ATTACK_ACCURACY_DIFFICULTY_STEP, 0.0D), 0.0D, 1.0D);
	}

	private static SpawnWeightPair resolveDifficultyShiftedSpawnWeights(
		double regularWeight,
		double specialWeight,
		Difficulty difficulty,
		boolean hardcore,
		double difficultyStep
	) {
		double regular = Math.max(0.0D, regularWeight);
		double special = Math.max(0.0D, specialWeight);
		if (difficulty == null) {
			return new SpawnWeightPair(regular, special);
		}
		double shift = Math.max(0.0D, difficultyStep) * resolveDifficultyTier(difficulty, hardcore);
		if (shift > 0.0D) {
			double transferred = Math.min(shift, regular);
			regular -= transferred;
			special += transferred;
		} else if (shift < 0.0D) {
			double transferred = Math.min(-shift, special);
			special -= transferred;
			regular += transferred;
		}
		return new SpawnWeightPair(regular, special);
	}

	private static double resolveBabyChance(double adultWeight, double babyWeight, Difficulty difficulty, boolean hardcore) {
		SpawnWeightPair shifted = resolveDifficultyShiftedSpawnWeights(adultWeight, babyWeight, difficulty, hardcore, SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP);
		double total = shifted.regularWeight + shifted.specialWeight;
		return total <= 0.0D ? 0.05D : Mth.clamp(shifted.specialWeight / total, 0.0D, 1.0D);
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

	private static void loadConfig() {
		try {
			Path rootDirectory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(MOB_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, MOB_CONFIG_SETTINGS_FILE_NAME);
			JsonObject settingsRoot = StaticJsonSystem.ensureManagedFile(settingsFile, MadokuMobConfig.buildMobSystemDefaults());
			boolean enabled = readBoolean(settingsRoot, MadokuMobConfig.FIELD_MOB_SYSTEM_ENABLED, true);
			Path mobsDirectory = rootDirectory.resolve(MOB_CONFIG_MOBS_FOLDER_NAME);
			Map<String, JsonObject> files = DynamicJsonSystem.ensureManagedFolder(
				mobsDirectory,
				MadokuMobConfig.buildDefaultMobFileDefaults(),
				MadokuMobConfig::buildDynamicMobDefaults,
				(fileKey, sourceRoot) -> true,
				MadokuMob::normalizeMobDynamicEntry
			);
			snapshot = enabled ? new Snapshot(true, Map.copyOf(files)) : Snapshot.disabled();
		} catch (IOException | RuntimeException exception) {
			snapshot = Snapshot.disabled();
		}
		emitConfigLoaded();
	}

	private static void emitConfigLoaded() {
		String metricId = "mob.config_loaded";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, metricId)) {
			return;
		}
		Snapshot config = snapshot;
		MadokuDebug.event(metricId, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("mob:global")
			.field("enabled", config.enabled())
			.field("mob_files", config.files().size())
			.log();
	}

	private static void emitManagedArrowTracked(AbstractArrow arrow) {
		String metricId = "mob.arrow_tracked";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, metricId) || arrow == null) {
			return;
		}
		Entity owner = arrow.getOwner();
		MadokuDebug.event(metricId, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("arrow:" + arrow.getUUID())
			.field("owner", owner == null ? "unknown" : owner.getType().toShortString())
			.field("ttl_ticks", MOB_ARROW_LIFETIME_TICKS)
			.log();
	}

	private static void emitManagedArrowExpired(AbstractArrow arrow) {
		String metricId = "mob.arrow_expired";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, metricId) || arrow == null) {
			return;
		}
		Entity owner = arrow.getOwner();
		MadokuDebug.event(metricId, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("arrow:" + arrow.getUUID())
			.field("owner", owner == null ? "unknown" : owner.getType().toShortString())
			.field("reason", "lifetime_reached")
			.log();
	}

	private static JsonObject root(String key) {
		return snapshot.files.getOrDefault(normalizeKey(key), new JsonObject());
	}

	private static JsonObject skeletonRoot(EntityType<?> type) {
		if (type == EntityType.SKELETON) {
			return root(MadokuMobConfig.FILE_SKELETON);
		}
		if (type == EntityType.STRAY) {
			return root(MadokuMobConfig.FILE_STRAY);
		}
		if (type == EntityType.BOGGED) {
			return root(MadokuMobConfig.FILE_BOGGED);
		}
		if (type == EntityType.PARCHED) {
			return root(MadokuMobConfig.FILE_PARCHED);
		}
		if (type == EntityType.WITHER_SKELETON) {
			return root(MadokuMobConfig.FILE_WITHER_SKELETON);
		}
		return new JsonObject();
	}

	private static JsonObject zombieRoot(EntityType<?> type) {
		if (type == EntityType.ZOMBIE) {
			return root(MadokuMobConfig.FILE_ZOMBIE);
		}
		if (type == EntityType.HUSK) {
			return root(MadokuMobConfig.FILE_HUSK);
		}
		if (type == EntityType.DROWNED) {
			return root(MadokuMobConfig.FILE_DROWNED);
		}
		if (type == EntityType.ZOMBIE_VILLAGER) {
			return root(MadokuMobConfig.FILE_ZOMBIE_VILLAGER);
		}
		return new JsonObject();
	}

	private static JsonObject zombieAdultRoot(EntityType<?> type, JsonObject root) {
		String field = type == EntityType.ZOMBIE ? MadokuMobConfig.FIELD_ADULT_ZOMBIE
			: type == EntityType.HUSK ? MadokuMobConfig.FIELD_ADULT_HUSK
			: type == EntityType.DROWNED ? MadokuMobConfig.FIELD_ADULT_DROWNED
			: MadokuMobConfig.FIELD_ADULT_ZOMBIE_VILLAGER;
		return readObject(root, field);
	}

	private static JsonObject zombieBabyRoot(EntityType<?> type, JsonObject root) {
		String field = type == EntityType.ZOMBIE ? MadokuMobConfig.FIELD_BABY_ZOMBIE
			: type == EntityType.HUSK ? MadokuMobConfig.FIELD_BABY_HUSK
			: type == EntityType.DROWNED ? MadokuMobConfig.FIELD_BABY_DROWNED
			: MadokuMobConfig.FIELD_BABY_ZOMBIE_VILLAGER;
		return readObject(root, field);
	}

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static JsonObject readObject(JsonObject root, String key) {
		if (root == null || key == null) {
			return new JsonObject();
		}
		JsonElement element = root.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
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

	private static JsonElement normalizeMobDynamicEntry(String key, JsonElement sourceValue) {
		if (key == null || sourceValue == null || !sourceValue.isJsonPrimitive() || !sourceValue.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
		if (MadokuMobConfig.FIELD_ARMOR.equals(normalizedKey)
			|| MadokuMobConfig.FIELD_KNOCKBACK_RESISTANCE.equals(normalizedKey)
			|| MadokuMobConfig.FIELD_SCALE.equals(normalizedKey)) {
			return sourceValue.deepCopy();
		}
		return null;
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

	private record HomingArrowState(UUID targetUuid, double speed, int remainingTicks) {}
	private record ShotVector(Vec3 vector, boolean guaranteedHit) {}
	private record SpawnWeightPair(double regularWeight, double specialWeight) {}
	private enum SpawnArmorMaterial {
		NETHERITE,
		DIAMOND,
		GOLD,
		IRON,
		COPPER,
		LEATHER
	}
	private enum SpawnArmorCoverage {
		HELMET_ONLY,
		HELMET_BOOTS,
		FULL_SET
	}

	private enum SpawnOutcome {
		SPIDER,
		CAVE_SPIDER,
		SPIDER_JOCKEY
	}

	private record Snapshot(boolean enabled, Map<String, JsonObject> files) {
		private static Snapshot disabled() {
			return new Snapshot(false, Map.of());
		}
	}
}

