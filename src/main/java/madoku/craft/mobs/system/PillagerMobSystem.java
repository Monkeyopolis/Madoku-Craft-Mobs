package madoku.craft.mobs.system;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import madoku.craft.API.system.MadokuTickSystem;
import madoku.craft.mobs.MadokuCraftMobs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

/**
 * Applies JSON-driven pillager stats and ranged combat behavior when entities are loaded.
 */
public final class PillagerMobSystem {
	private static final String LOG_SOURCE = "MOBS.Pillager";
	private static final double RANGED_DAMAGE_DIFFICULTY_STEP = 1.0;
	private static final double ATTACK_ACCURACY_DIFFICULTY_STEP = 0.05;
	private static final double ATTACK_INTERVAL_DIFFICULTY_STEP = 2.0;
	private static final double CHARGE_UP_TICKS_DIFFICULTY_STEP = 1.0;
	private static final double MIN_HOMING_SPEED = 0.75;
	private static final int HOMING_LIFETIME_TICKS = 60;
	private static final MadokuTickSystem.TickHandler HOMING_TICK_TASK = PillagerMobSystem::runQueuedHomingTick;

	private static PillagerMobConfig activeConfig;
	private static final Map<UUID, Integer> ATTACK_COOLDOWNS = new ConcurrentHashMap<>();
	private static final Map<UUID, HomingArrowState> HOMING_ARROWS = new ConcurrentHashMap<>();
	private static final AtomicBoolean HOMING_TICK_ACTIVE = new AtomicBoolean(false);

	private PillagerMobSystem() {
	}

	public static void init() {
		reloadConfig();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> reloadConfig());

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof PillagerEntity pillager) || pillager.getType() != EntityType.PILLAGER) {
				return;
			}
			PillagerMobConfig config = activeConfig;
			if (config != null && config.enabled()) {
				applyConfig(pillager, config);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof PillagerEntity pillager && pillager.getType() == EntityType.PILLAGER) {
				ATTACK_COOLDOWNS.remove(pillager.getUuid());
			}
			if (entity instanceof PersistentProjectileEntity projectile) {
				HOMING_ARROWS.remove(projectile.getUuid());
			}
		});

		MadokuCraftMobs.LOGGER.info("Madoku Craft Mobs: pillager system hooks registered.");
	}

	public static int resolveAttackIntervalTicks(HostileEntity attacker) {
		PillagerEntity pillager = asConfiguredPillager(attacker);
		if (pillager == null) {
			return -1;
		}

		PillagerMobConfig config = activeConfig;
		double interval = MobSystemUtil.resolveDifficultyAdjustedInverseValue(
			pillager.getEntityWorld().getDifficulty(),
			MobSystemUtil.isHardcoreWorld(pillager.getEntityWorld()),
			config.attackInterval(),
			ATTACK_INTERVAL_DIFFICULTY_STEP,
			1.0
		);
		return Math.max(1, (int) Math.round(interval));
	}

	public static boolean tickAttackCooldown(HostileEntity attacker) {
		PillagerEntity pillager = asConfiguredPillager(attacker);
		if (pillager == null) {
			return false;
		}

		UUID pillagerId = pillager.getUuid();
		Integer remainingTicks = ATTACK_COOLDOWNS.get(pillagerId);
		if (remainingTicks == null || remainingTicks <= 0) {
			ATTACK_COOLDOWNS.remove(pillagerId);
			return false;
		}

		int nextRemaining = remainingTicks - 1;
		if (nextRemaining <= 0) {
			ATTACK_COOLDOWNS.remove(pillagerId);
			return false;
		}

		ATTACK_COOLDOWNS.put(pillagerId, nextRemaining);
		return true;
	}

	public static void markAttackCooldownFromShot(HostileEntity attacker) {
		PillagerEntity pillager = asConfiguredPillager(attacker);
		if (pillager == null) {
			return;
		}

		int cooldownTicks = Math.max(0, resolveAttackIntervalTicks(pillager));
		if (cooldownTicks <= 0) {
			ATTACK_COOLDOWNS.remove(pillager.getUuid());
			return;
		}
		ATTACK_COOLDOWNS.put(pillager.getUuid(), cooldownTicks);
	}

	public static int resolveChargeUpTicks(HostileEntity attacker) {
		PillagerEntity pillager = asConfiguredPillager(attacker);
		if (pillager == null) {
			return -1;
		}

		PillagerMobConfig config = activeConfig;
		double chargeUpTicks = MobSystemUtil.resolveDifficultyAdjustedInverseValue(
			pillager.getEntityWorld().getDifficulty(),
			MobSystemUtil.isHardcoreWorld(pillager.getEntityWorld()),
			config.chargeUpTicks(),
			CHARGE_UP_TICKS_DIFFICULTY_STEP,
			1.0
		);
		return Math.max(1, (int) Math.round(chargeUpTicks));
	}

	public static Float resolveFixedProjectileDamage(PersistentProjectileEntity projectile) {
		if (projectile == null) {
			return null;
		}
		Entity owner = projectile.getOwner();
		PillagerEntity pillager = asConfiguredPillager(owner);
		if (pillager == null) {
			return null;
		}

		PillagerMobConfig config = activeConfig;
		double scaledDamage = resolveScaledRangedDamage(
			config.rangedDamage(),
			pillager.getEntityWorld().getDifficulty(),
			MobSystemUtil.isHardcoreWorld(pillager.getEntityWorld())
		);
		return Math.max(0.0f, (float) scaledDamage);
	}

	public static boolean applyProjectileAccuracyOverride(
		ProjectileEntity projectile,
		LivingEntity shooter,
		LivingEntity target,
		double velocityX,
		double velocityY,
		double velocityZ,
		float speed
	) {
		PillagerEntity pillager = asConfiguredPillager(shooter);
		if (projectile == null || pillager == null || target == null || !target.isAlive()) {
			return false;
		}

		PillagerMobConfig config = activeConfig;
		double accuracy = resolveScaledAttackAccuracy(
			config.attackAccuracy(),
			pillager.getEntityWorld().getDifficulty(),
			MobSystemUtil.isHardcoreWorld(pillager.getEntityWorld())
		);
		boolean guaranteedHit = pillager.getRandom().nextDouble() <= accuracy;
		if (guaranteedHit) {
			projectile.setVelocity(velocityX, velocityY, velocityZ, speed, 0.0f);
			if (projectile instanceof PersistentProjectileEntity persistentProjectile) {
				double homingSpeed = Math.max(MIN_HOMING_SPEED, persistentProjectile.getVelocity().length());
				persistentProjectile.setNoGravity(true);
				HOMING_ARROWS.put(
					persistentProjectile.getUuid(),
					new HomingArrowState(target.getUuid(), homingSpeed, HOMING_LIFETIME_TICKS)
				);
				ensureHomingTickTaskScheduled();
			}
			return true;
		}

		Vec3d missDirection = resolveMissVector(velocityX, velocityY, velocityZ, accuracy, shooter);
		projectile.setVelocity(missDirection.x, missDirection.y, missDirection.z, speed, 0.0f);
		if (projectile instanceof PersistentProjectileEntity persistentProjectile) {
			HOMING_ARROWS.remove(persistentProjectile.getUuid());
		}
		return true;
	}

	public static boolean applyCustomRangedShot(PillagerEntity pillager, LivingEntity target, float speed) {
		if (pillager == null || target == null || !target.isAlive()) {
			return false;
		}
		PillagerEntity configuredPillager = asConfiguredPillager(pillager);
		if (configuredPillager == null) {
			return false;
		}

		Hand hand = ProjectileUtil.getHandPossiblyHolding(configuredPillager, Items.CROSSBOW);
		ItemStack crossbow = configuredPillager.getStackInHand(hand);
		if (!(crossbow.getItem() instanceof CrossbowItem crossbowItem)) {
			return false;
		}

		float vanillaSpread = 14.0f - (pillager.getEntityWorld().getDifficulty().getId() * 4.0f);
		crossbowItem.shootAll(
			configuredPillager.getEntityWorld(),
			configuredPillager,
			hand,
			crossbow,
			speed,
			vanillaSpread,
			target
		);
		configuredPillager.postShoot();
		return true;
	}

	private static void reloadConfig() {
		activeConfig = PillagerMobConfig.load();
		ATTACK_COOLDOWNS.clear();
		HOMING_ARROWS.clear();
		HOMING_TICK_ACTIVE.set(false);
		if (activeConfig.enabled()) {
			MadokuCraftMobs.infoDebug(
				LOG_SOURCE,
				"Config loaded. enabled={}, health={}, armor={}, meleeDamage={}, rangedDamage={}, attackInterval={}, attackAccuracy={}%, chargeUpTicks={}.",
				activeConfig.enabled(),
				activeConfig.stats().health(),
				activeConfig.stats().armor(),
				activeConfig.stats().damage(),
				activeConfig.rangedDamage(),
				activeConfig.attackInterval(),
				MobSystemUtil.roundToTwoDecimals(activeConfig.attackAccuracy() * 100.0),
				activeConfig.chargeUpTicks()
			);
		} else {
			MadokuCraftMobs.infoDebug(LOG_SOURCE, "Pillager system disabled in config.");
		}
	}

	private static Vec3d resolveMissVector(
		double velocityX,
		double velocityY,
		double velocityZ,
		double attackAccuracy,
		LivingEntity shooter
	) {
		Vec3d desired = new Vec3d(velocityX, velocityY, velocityZ);
		if (desired.lengthSquared() <= 1.0E-6) {
			return desired;
		}

		double accuracy = MathHelper.clamp(attackAccuracy, 0.0, 1.0);
		double missFactor = Math.max(0.05, 1.0 - accuracy);
		Vec3d normalized = desired.normalize();
		Vec3d lateral = normalized.crossProduct(new Vec3d(0.0, 1.0, 0.0));
		if (lateral.lengthSquared() <= 1.0E-6) {
			lateral = normalized.crossProduct(new Vec3d(1.0, 0.0, 0.0));
		}
		lateral = lateral.normalize();

		double horizontalOffset = MathHelper.lerp(missFactor, 0.75, 2.25);
		if (shooter.getRandom().nextBoolean()) {
			horizontalOffset *= -1.0;
		}
		double verticalOffset = MathHelper.lerp(missFactor, 0.15, 0.7);
		if (shooter.getRandom().nextBoolean()) {
			verticalOffset *= -1.0;
		}

		return normalized
			.add(lateral.multiply(horizontalOffset))
			.add(0.0, verticalOffset, 0.0)
			.normalize();
	}

	private static void applyConfig(PillagerEntity pillager, PillagerMobConfig config) {
		double oldMaxHealth = pillager.getMaxHealth();
		MobSystemUtil.applyUniversalStats(pillager, config.stats(), pillager.getEntityWorld().getDifficulty());
		MobSystemUtil.rescaleCurrentHealth(pillager, oldMaxHealth);
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

	private static void runQueuedHomingTick(MinecraftServer server) {
		tickHomingProjectiles(server);
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

	private static void tickHomingProjectiles(MinecraftServer server) {
		if (server == null || HOMING_ARROWS.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, HomingArrowState> entry : HOMING_ARROWS.entrySet()) {
			UUID projectileId = entry.getKey();
			HomingArrowState homingState = entry.getValue();
			if (homingState.remainingTicks() <= 0) {
				releaseHomingProjectile(projectileId, findTrackedProjectile(server, projectileId));
				continue;
			}

			PersistentProjectileEntity projectile = findTrackedProjectile(server, projectileId);
			if (projectile == null || projectile.isRemoved() || projectile.isOnGround()) {
				HOMING_ARROWS.remove(projectileId);
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

	private static PillagerEntity asConfiguredPillager(Entity entity) {
		if (!(entity instanceof PillagerEntity pillager) || pillager.getType() != EntityType.PILLAGER) {
			return null;
		}
		PillagerMobConfig config = activeConfig;
		if (config == null || !config.enabled()) {
			return null;
		}
		return pillager;
	}

	private record HomingArrowState(UUID targetUuid, double speed, int remainingTicks) {
	}
}
