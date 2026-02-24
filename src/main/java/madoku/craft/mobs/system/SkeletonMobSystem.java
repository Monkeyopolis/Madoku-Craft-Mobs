package madoku.craft.mobs.system;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

import madoku.craft.API.system.MadokuTickSystem;
import madoku.craft.mobs.MadokuCraftMobs;
import madoku.craft.mobs.mixin.AbstractSkeletonEntityArrowInvoker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

/**
 * Applies JSON-driven skeleton-family stats and ranged combat behavior when entities are loaded.
 */
public final class SkeletonMobSystem {
	private static final String LOG_SOURCE = "MOBS.Skeleton";
	private static final double MIN_HOMING_SPEED = 0.75;
	private static final int HOMING_LIFETIME_TICKS = 60;
	private static final double RANGED_DAMAGE_DIFFICULTY_STEP = 1.0;
	private static final double ATTACK_ACCURACY_DIFFICULTY_STEP = 0.05;
	private static final double ATTACK_INTERVAL_DIFFICULTY_STEP = 2.0;
	private static final double CHARGE_UP_TICKS_DIFFICULTY_STEP = 1.0;
	private static final MadokuTickSystem.TickHandler HOMING_TICK_TASK = SkeletonMobSystem::runQueuedHomingTick;
	private static final AtomicBoolean HOMING_TICK_ACTIVE = new AtomicBoolean(false);

	private static SkeletonMobConfig activeConfig;
	private static final Map<UUID, HomingArrowState> HOMING_ARROWS = new ConcurrentHashMap<>();
	private static final Map<UUID, Double> FIXED_DAMAGE_ARROWS = new ConcurrentHashMap<>();

	private SkeletonMobSystem() {
	}

	public static void init() {
		reloadConfig();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> reloadConfig());

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof LivingEntity livingEntity)) {
				return;
			}
			SkeletonMobConfig config = activeConfig;
			if (config != null) {
				applyConfig(livingEntity, config);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (!(entity instanceof PersistentProjectileEntity projectile)) {
				return;
			}
			UUID projectileId = projectile.getUuid();
			HOMING_ARROWS.remove(projectileId);
			FIXED_DAMAGE_ARROWS.remove(projectileId);
		});

		MadokuCraftMobs.LOGGER.info("Madoku Craft Mobs: skeleton system hooks registered.");
	}

	private static void reloadConfig() {
		activeConfig = SkeletonMobConfig.load();
		HOMING_ARROWS.clear();
		FIXED_DAMAGE_ARROWS.clear();
		HOMING_TICK_ACTIVE.set(false);
		if (activeConfig.anyEnabled()) {
			MadokuCraftMobs.infoDebug(
				LOG_SOURCE,
				"Config loaded. skeleton(enabled={}, health={}, damage={}, bowWeights={}/{}, jockeyWeights={}/{}), stray(enabled={}, health={}, damage={}, bowWeights={}/{}, jockeyWeights={}/{}), bogged(enabled={}, health={}, damage={}, bowWeights={}/{}, jockeyWeights={}/{}), parched(enabled={}, health={}, damage={}, bowWeights={}/{}, jockeyWeights={}/{}).",
				activeConfig.skeleton().enabled(),
				activeConfig.skeleton().stats().health(),
				activeConfig.skeleton().stats().damage(),
				activeConfig.skeleton().withBowSpawnWeight(),
				activeConfig.skeleton().withoutBowSpawnWeight(),
				activeConfig.skeleton().spiderJockeySpawnWeight(),
				activeConfig.skeleton().regularSpawnWeight(),
				activeConfig.stray().enabled(),
				activeConfig.stray().stats().health(),
				activeConfig.stray().stats().damage(),
				activeConfig.stray().withBowSpawnWeight(),
				activeConfig.stray().withoutBowSpawnWeight(),
				activeConfig.stray().spiderJockeySpawnWeight(),
				activeConfig.stray().regularSpawnWeight(),
				activeConfig.bogged().enabled(),
				activeConfig.bogged().stats().health(),
				activeConfig.bogged().stats().damage(),
				activeConfig.bogged().withBowSpawnWeight(),
				activeConfig.bogged().withoutBowSpawnWeight(),
				activeConfig.bogged().spiderJockeySpawnWeight(),
				activeConfig.bogged().regularSpawnWeight(),
				activeConfig.parched().enabled(),
				activeConfig.parched().stats().health(),
				activeConfig.parched().stats().damage(),
				activeConfig.parched().withBowSpawnWeight(),
				activeConfig.parched().withoutBowSpawnWeight(),
				activeConfig.parched().spiderJockeySpawnWeight(),
				activeConfig.parched().regularSpawnWeight()
			);
		} else {
			MadokuCraftMobs.infoDebug(LOG_SOURCE, "Skeleton system disabled in config.");
		}
	}

	public static void applySpawnBowOverride(AbstractSkeletonEntity skeleton, Random random) {
		SkeletonMobConfig config = activeConfig;
		if (skeleton == null || random == null || config == null) {
			return;
		}
		SkeletonMobConfig.SkeletonTypeConfig variant = config.resolveVariant(skeleton.getType());
		if (variant == null || !variant.enabled()) {
			return;
		}

		double withBowWeight = Math.max(0.0, variant.withBowSpawnWeight());
		double withoutBowWeight = Math.max(0.0, variant.withoutBowSpawnWeight());
		double total = withBowWeight + withoutBowWeight;
		if (total <= 0.0) {
			return;
		}

		boolean spawnWithBow = (random.nextDouble() * total) < withBowWeight;
		if (!spawnWithBow) {
			skeleton.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			return;
		}

		ensureBowEquipped(skeleton);
	}

	public static void applySpawnSpiderJockeyOverride(
		AbstractSkeletonEntity skeleton,
		ServerWorldAccess world,
		LocalDifficulty difficulty,
		SpawnReason spawnReason,
		Random random
	) {
		SkeletonMobConfig config = activeConfig;
		if (skeleton == null
			|| world == null
			|| difficulty == null
			|| spawnReason == null
			|| random == null
			|| config == null) {
			return;
		}
		SkeletonMobConfig.SkeletonTypeConfig variant = config.resolveVariant(skeleton.getType());
		if (variant == null) {
			return;
		}

		if (spawnReason == SpawnReason.JOCKEY) {
			ensureBowEquipped(skeleton);
			return;
		}
		if (!variant.enabled()) {
			return;
		}

		MobSystemUtil.SpawnWeightPair jockeyWeights = resolveJockeySpawnWeights(
			variant,
			difficulty.getGlobalDifficulty(),
			MobSystemUtil.isHardcoreWorld(skeleton.getEntityWorld())
		);
		double regularWeight = jockeyWeights.regularWeight();
		double jockeyWeight = jockeyWeights.specialWeight();
		double total = jockeyWeight + regularWeight;
		if (skeleton.hasVehicle() || total <= 0.0) {
			return;
		}

		boolean shouldSpawnAsJockey = (random.nextDouble() * total) < jockeyWeight;
		if (!shouldSpawnAsJockey) {
			return;
		}

		ServerWorld serverWorld = world.toServerWorld();
		SpiderEntity spider = EntityType.SPIDER.create(serverWorld, SpawnReason.JOCKEY);
		if (spider == null) {
			return;
		}

		spider.refreshPositionAndAngles(
			skeleton.getX(),
			skeleton.getY(),
			skeleton.getZ(),
			skeleton.getYaw(),
			skeleton.getPitch()
		);
		spider.initialize(world, difficulty, SpawnReason.JOCKEY, null);
		serverWorld.spawnEntityAndPassengers(spider);
		skeleton.startRiding(spider);
		ensureBowEquipped(skeleton);
	}

	public static void logSpawnDebug(
		AbstractSkeletonEntity skeleton,
		ServerWorldAccess world,
		LocalDifficulty difficulty,
		SpawnReason spawnReason
	) {
		SkeletonMobConfig config = activeConfig;
		if (skeleton == null || world == null || difficulty == null || spawnReason == null || config == null) {
			return;
		}

		SkeletonMobConfig.SkeletonTypeConfig variant = config.resolveVariant(skeleton.getType());
		if (variant == null || !variant.enabled()) {
			return;
		}

		Difficulty globalDifficulty = difficulty.getGlobalDifficulty();
		boolean hardcore = MobSystemUtil.isHardcoreWorld(world.toServerWorld());
		double withBowWeight = Math.max(0.0, variant.withBowSpawnWeight());
		double withoutBowWeight = Math.max(0.0, variant.withoutBowSpawnWeight());
		double regularWeight = Math.max(0.0, variant.regularSpawnWeight());
		double jockeyWeight = Math.max(0.0, variant.spiderJockeySpawnWeight());

		boolean hasBow = skeleton.getMainHandStack().isOf(Items.BOW) || skeleton.getOffHandStack().isOf(Items.BOW);
		boolean spiderJockey = spawnReason == SpawnReason.JOCKEY
			|| (skeleton.hasVehicle() && skeleton.getVehicle() instanceof SpiderEntity);
		MadokuCraftMobs.infoDebug(
			LOG_SOURCE,
			"Spawn result type={}, reason={}, difficulty={}, hardcore={}, bow={}, mount={}, weights(bow={}/{}, jockeyBase={}/{}).",
			skeleton.getType(),
			spawnReason.name(),
			globalDifficulty.name(),
			hardcore,
			hasBow ? "WITH_BOW" : "WITHOUT_BOW",
			spiderJockey ? "SPIDER_JOCKEY" : "REGULAR",
			MobSystemUtil.roundToTwoDecimals(withBowWeight),
			MobSystemUtil.roundToTwoDecimals(withoutBowWeight),
			MobSystemUtil.roundToTwoDecimals(jockeyWeight),
			MobSystemUtil.roundToTwoDecimals(regularWeight)
		);
	}

	public static boolean applyCustomRangedShot(AbstractSkeletonEntity skeleton, LivingEntity target, float pullProgress) {
		SkeletonMobConfig config = activeConfig;
		if (skeleton == null || target == null || !target.isAlive() || config == null) {
			return false;
		}

		SkeletonMobConfig.SkeletonTypeConfig variant = config.resolveVariant(skeleton.getType());
		if (variant == null || !variant.enabled()) {
			return false;
		}

		Hand bowHand = resolveBowHand(skeleton);
		if (bowHand == null) {
			return false;
		}

		ItemStack bowStack = skeleton.getStackInHand(bowHand);
		ItemStack arrowStack = skeleton.getProjectileType(bowStack);
		if (arrowStack.isEmpty()) {
			arrowStack = new ItemStack(Items.ARROW);
		}

		PersistentProjectileEntity projectile = ((AbstractSkeletonEntityArrowInvoker) skeleton)
			.madokuCraftMobs$createArrowProjectile(arrowStack, pullProgress, bowStack);
		if (projectile == null) {
			return false;
		}

		Difficulty difficulty = skeleton.getEntityWorld().getDifficulty();
		boolean hardcore = MobSystemUtil.isHardcoreWorld(skeleton.getEntityWorld());
		double scaledAccuracy = resolveScaledAttackAccuracy(variant.attackAccuracy(), difficulty, hardcore);
		double scaledRangedDamage = resolveScaledRangedDamage(variant.rangedDamage(), difficulty, hardcore);
		ShotVector shotVector = resolveShotVector(skeleton, projectile, target, scaledAccuracy);
		projectile.setVelocity(shotVector.vector().x, shotVector.vector().y, shotVector.vector().z, 1.6F, 0.0F);
		projectile.setCritical(false);
		FIXED_DAMAGE_ARROWS.put(projectile.getUuid(), scaledRangedDamage);

		if (shotVector.guaranteedHit()) {
			double speed = Math.max(MIN_HOMING_SPEED, projectile.getVelocity().length());
			projectile.setNoGravity(true);
			HOMING_ARROWS.put(projectile.getUuid(), new HomingArrowState(target.getUuid(), speed, HOMING_LIFETIME_TICKS));
			ensureHomingTickTaskScheduled();
		} else {
			HOMING_ARROWS.remove(projectile.getUuid());
		}

		skeleton.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
		World world = skeleton.getEntityWorld();
		world.spawnEntity(projectile);
		return true;
	}

	public static int resolveRangedAttackIntervalTicks(AbstractSkeletonEntity skeleton) {
		SkeletonMobConfig config = activeConfig;
		if (skeleton == null || config == null) {
			return -1;
		}

		SkeletonMobConfig.SkeletonTypeConfig variant = config.resolveVariant(skeleton.getType());
		if (variant == null || !variant.enabled()) {
			return -1;
		}

		double interval = MobSystemUtil.resolveDifficultyAdjustedInverseValue(
			skeleton.getEntityWorld().getDifficulty(),
			MobSystemUtil.isHardcoreWorld(skeleton.getEntityWorld()),
			variant.attackInterval(),
			ATTACK_INTERVAL_DIFFICULTY_STEP,
			1.0
		);
		return Math.max(1, (int) Math.round(interval));
	}

	public static int resolveChargeUpTicks(HostileEntity attacker) {
		if (!(attacker instanceof AbstractSkeletonEntity skeleton)) {
			return -1;
		}

		SkeletonMobConfig config = activeConfig;
		if (config == null) {
			return -1;
		}
		SkeletonMobConfig.SkeletonTypeConfig variant = config.resolveVariant(skeleton.getType());
		if (variant == null || !variant.enabled()) {
			return -1;
		}

		double chargeUpTicks = MobSystemUtil.resolveDifficultyAdjustedInverseValue(
			skeleton.getEntityWorld().getDifficulty(),
			MobSystemUtil.isHardcoreWorld(skeleton.getEntityWorld()),
			variant.chargeUpTicks(),
			CHARGE_UP_TICKS_DIFFICULTY_STEP,
			1.0
		);
		return Math.max(1, (int) Math.round(chargeUpTicks));
	}

	private static double resolveScaledRangedDamage(double baseDamage, Difficulty difficulty, boolean hardcore) {
		return MobSystemUtil.resolveDifficultyAdjustedValue(
			difficulty,
			hardcore,
			Math.max(0.0, baseDamage),
			RANGED_DAMAGE_DIFFICULTY_STEP,
			0.0
		);
	}

	private static double resolveScaledAttackAccuracy(double baseAccuracy, Difficulty difficulty, boolean hardcore) {
		return MathHelper.clamp(
			MobSystemUtil.resolveDifficultyAdjustedValue(
				difficulty,
				hardcore,
				MathHelper.clamp(baseAccuracy, 0.0, 1.0),
				ATTACK_ACCURACY_DIFFICULTY_STEP,
				0.0
			),
			0.0,
			1.0
		);
	}

	public static void ensureBowEquipped(AbstractSkeletonEntity skeleton) {
		if (skeleton == null) {
			return;
		}
		ItemStack mainHand = skeleton.getEquippedStack(EquipmentSlot.MAINHAND);
		if (mainHand.isEmpty() || !mainHand.isOf(Items.BOW)) {
			skeleton.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
	}

	private static void tickTrackedProjectiles(MinecraftServer server) {
		if (server == null || HOMING_ARROWS.isEmpty()) {
			return;
		}
		tickHomingProjectiles(server);
	}

	private static void runQueuedHomingTick(MinecraftServer server) {
		tickTrackedProjectiles(server);
		if (HOMING_ARROWS.isEmpty()) {
			HOMING_TICK_ACTIVE.set(false);
			if (!HOMING_ARROWS.isEmpty()) {
				ensureHomingTickTaskScheduled();
			}
			return;
		}
		MadokuTickSystem.enqueue(MadokuTickSystem.Phase.START, HOMING_TICK_TASK);
	}

	private static void ensureHomingTickTaskScheduled() {
		if (!HOMING_TICK_ACTIVE.compareAndSet(false, true)) {
			return;
		}
		if (!MadokuTickSystem.enqueue(MadokuTickSystem.Phase.START, HOMING_TICK_TASK)) {
			HOMING_TICK_ACTIVE.set(false);
		}
	}

	public static Float consumeFixedSkeletonArrowDamage(PersistentProjectileEntity projectile) {
		if (projectile == null) {
			return null;
		}
		Double desiredHitDamage = FIXED_DAMAGE_ARROWS.remove(projectile.getUuid());
		if (desiredHitDamage == null) {
			return null;
		}
		return Math.max(0.0f, desiredHitDamage.floatValue());
	}

	private static void tickHomingProjectiles(MinecraftServer server) {
		if (HOMING_ARROWS.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, HomingArrowState> entry : HOMING_ARROWS.entrySet()) {
			UUID projectileId = entry.getKey();
			HomingArrowState homingState = entry.getValue();
			if (homingState.remainingTicks() <= 0) {
				PersistentProjectileEntity projectile = findTrackedProjectile(server, projectileId);
				releaseHomingProjectile(projectileId, projectile);
				continue;
			}
			PersistentProjectileEntity projectile = findTrackedProjectile(server, projectileId);
			if (projectile == null || projectile.isRemoved() || projectile.isOnGround()) {
				HOMING_ARROWS.remove(projectileId);
				FIXED_DAMAGE_ARROWS.remove(projectileId);
				continue;
			}

			Entity targetEntity = findTrackedEntity(server, homingState.targetUuid());
			if (!(targetEntity instanceof LivingEntity livingTarget) || !livingTarget.isAlive()) {
				releaseHomingProjectile(projectileId, projectile);
				continue;
			}

			Vec3d projectilePos = new Vec3d(projectile.getX(), projectile.getY(), projectile.getZ());
			Vec3d targetPos = new Vec3d(targetEntity.getX(), targetEntity.getBodyY(0.5), targetEntity.getZ());
			Vec3d toTarget = targetPos.subtract(projectilePos);
			if (toTarget.lengthSquared() <= 1.0E-6) {
				releaseHomingProjectile(projectileId, projectile);
				continue;
			}

			double speed = Math.max(entry.getValue().speed(), projectile.getVelocity().length());
			projectile.setNoGravity(true);
			projectile.setVelocity(toTarget.normalize().multiply(speed));
			HOMING_ARROWS.put(projectileId, new HomingArrowState(homingState.targetUuid(), speed, homingState.remainingTicks() - 1));
		}
	}

	private static void releaseHomingProjectile(UUID projectileId, PersistentProjectileEntity projectile) {
		HOMING_ARROWS.remove(projectileId);
		if (projectile == null || projectile.isRemoved()) {
			return;
		}
		projectile.setNoGravity(false);
	}

	private static PersistentProjectileEntity findTrackedProjectile(MinecraftServer server, UUID projectileId) {
		Entity entity = findTrackedEntity(server, projectileId);
		return entity instanceof PersistentProjectileEntity projectile ? projectile : null;
	}

	private static Entity findTrackedEntity(MinecraftServer server, UUID entityId) {
		for (ServerWorld world : server.getWorlds()) {
			Entity entity = world.getEntity(entityId);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	private static ShotVector resolveShotVector(
		AbstractSkeletonEntity skeleton,
		PersistentProjectileEntity projectile,
		LivingEntity target,
		double attackAccuracy
	) {
		double deltaX = target.getX() - skeleton.getX();
		double deltaZ = target.getZ() - skeleton.getZ();
		double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
		double deltaY = target.getBodyY(1.0 / 3.0) - projectile.getY() + (horizontal * 0.2);
		Vec3d desired = new Vec3d(deltaX, deltaY, deltaZ);
		if (desired.lengthSquared() <= 1.0E-6) {
			return new ShotVector(desired, true);
		}

		double accuracy = MathHelper.clamp(attackAccuracy, 0.0, 1.0);
		Random random = skeleton.getRandom();
		if (random.nextDouble() <= accuracy) {
			return new ShotVector(desired, true);
		}

		double missFactor = Math.max(0.05, 1.0 - accuracy);
		Vec3d normalized = desired.normalize();
		Vec3d lateral = normalized.crossProduct(new Vec3d(0.0, 1.0, 0.0));
		if (lateral.lengthSquared() <= 1.0E-6) {
			lateral = normalized.crossProduct(new Vec3d(1.0, 0.0, 0.0));
		}
		lateral = lateral.normalize();

		double horizontalOffset = MathHelper.lerp(missFactor, 0.75, 2.25);
		if (random.nextBoolean()) {
			horizontalOffset *= -1.0;
		}
		double verticalOffset = MathHelper.lerp(missFactor, 0.15, 0.7);
		if (random.nextBoolean()) {
			verticalOffset *= -1.0;
		}

		Vec3d missed = normalized
			.add(lateral.multiply(horizontalOffset))
			.add(0.0, verticalOffset, 0.0)
			.normalize();
		return new ShotVector(missed, false);
	}

	private static Hand resolveBowHand(AbstractSkeletonEntity skeleton) {
		ItemStack mainHand = skeleton.getMainHandStack();
		if (mainHand.isOf(Items.BOW)) {
			return Hand.MAIN_HAND;
		}
		ItemStack offHand = skeleton.getOffHandStack();
		if (offHand.isOf(Items.BOW)) {
			return Hand.OFF_HAND;
		}
		return null;
	}

	private static MobSystemUtil.SpawnWeightPair resolveJockeySpawnWeights(
		SkeletonMobConfig.SkeletonTypeConfig variant,
		Difficulty difficulty,
		boolean hardcore
	) {
		return MobSystemUtil.resolveDifficultyShiftedSpawnWeights(
			variant.regularSpawnWeight(),
			variant.spiderJockeySpawnWeight(),
			difficulty,
			hardcore,
			MobSystemUtil.SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
	}

	private static void applyConfig(LivingEntity entity, SkeletonMobConfig config) {
		SkeletonMobConfig.SkeletonTypeConfig variant = config.resolveVariant(entity.getType());
		if (variant == null || !variant.enabled()) {
			return;
		}

		double oldMaxHealth = entity.getMaxHealth();
		MobConfigJsonUtil.UniversalMobStats stats = variant.stats();

		MobSystemUtil.applyUniversalStats(entity, stats, entity.getEntityWorld().getDifficulty());
		MobSystemUtil.rescaleCurrentHealth(entity, oldMaxHealth);
	}

	private record HomingArrowState(UUID targetUuid, double speed, int remainingTicks) {
	}

	private record ShotVector(Vec3d vector, boolean guaranteedHit) {
	}
}
