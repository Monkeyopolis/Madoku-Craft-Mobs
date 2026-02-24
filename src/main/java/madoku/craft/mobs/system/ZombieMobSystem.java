package madoku.craft.mobs.system;

import madoku.craft.mobs.MadokuCraftMobs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;

/**
 * Applies JSON-driven zombie-family stats and behavior when entities are loaded.
 */
public final class ZombieMobSystem {
	private static final String LOG_SOURCE_ZOMBIE = "MOBS.Zombie";
	private static final String LOG_SOURCE_HUSK = "MOBS.Husk";
	private static final String LOG_SOURCE_DROWNED = "MOBS.Drowned";
	private static final String LOG_SOURCE_ZOMBIE_VILLAGER = "MOBS.ZombieVillager";

	private static ZombieMobConfig activeConfig;

	private ZombieMobSystem() {
	}

	public static void init() {
		reloadConfig();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> reloadConfig());

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof ZombieEntity zombie)) {
				return;
			}
			disableReinforcements(zombie);
			applyFamilyConfig(zombie);
		});

		MadokuCraftMobs.LOGGER.info("Madoku Craft Mobs: zombie-family system hooks registered.");
	}

	public static void applyCustomBabySpawnChance(
		ZombieEntity zombie,
		Difficulty difficulty,
		Random random,
		boolean hardcore
	) {
		if (zombie == null || difficulty == null || random == null) {
			return;
		}

		ZombieMobConfig config = activeConfig;
		if (config == null) {
			return;
		}

		EntityType<?> type = zombie.getType();
		ZombieMobConfig.ZombieTypeConfig typeConfig = config.resolveType(type);
		if (typeConfig == null
			|| !MobSystemUtil.canApplyBabySpawnRoll(
				zombie,
				type,
				difficulty,
				random,
				typeConfig.enabled(),
				typeConfig.useCustomBabySpawnChance()
			)) {
			return;
		}

		double chance = ZombieMobConfig.resolveBabySpawnChance(typeConfig, difficulty, hardcore);
		boolean shouldBeBaby = MobSystemUtil.applyBabySpawnRoll(zombie, difficulty, random, chance, resolveLogSource(type));

		if (type == EntityType.ZOMBIE && !shouldBeBaby && zombie.hasVehicle() && zombie.getVehicle() instanceof ChickenEntity) {
			zombie.stopRiding();
			MadokuCraftMobs.infoDebug(LOG_SOURCE_ZOMBIE, "Removed chicken mount because zombie rolled ADULT.");
		}
	}

	public static void sanitizeSpawnState(ZombieEntity zombie) {
		if (zombie == null) {
			return;
		}
		disableReinforcements(zombie);
		clearSpawnEquipment(zombie);
	}

	public static void logSpawnDebug(
		ZombieEntity zombie,
		Difficulty difficulty,
		boolean hardcore,
		SpawnReason spawnReason
	) {
		ZombieMobConfig config = activeConfig;
		if (zombie == null || difficulty == null || spawnReason == null || config == null) {
			return;
		}

		EntityType<?> type = zombie.getType();
		ZombieMobConfig.ZombieTypeConfig typeConfig = config.resolveType(type);
		if (typeConfig == null || !typeConfig.enabled()) {
			return;
		}

		Double babyChance = typeConfig.useCustomBabySpawnChance()
			? ZombieMobConfig.resolveBabySpawnChance(typeConfig, difficulty, hardcore)
			: null;
		MobSystemUtil.SpawnWeightPair adjustedWeights = ZombieMobConfig.resolveAdjustedSpawnWeights(typeConfig, difficulty, hardcore);
		boolean chickenMount = zombie.hasVehicle() && zombie.getVehicle() instanceof ChickenEntity;
		MadokuCraftMobs.infoDebug(
			resolveLogSource(type),
			"Spawn result={}, reason={}, difficulty={}, hardcore={}, chickenMount={}, customBabyChance={}, babyChance={}%, weights(adult={}, baby={}).",
			zombie.isBaby() ? "BABY" : "ADULT",
			spawnReason.name(),
			difficulty.name(),
			hardcore,
			chickenMount,
			typeConfig.useCustomBabySpawnChance(),
			babyChance == null ? null : MobSystemUtil.roundToTwoDecimals(babyChance * 100.0),
			MobSystemUtil.roundToTwoDecimals(adjustedWeights.regularWeight()),
			MobSystemUtil.roundToTwoDecimals(adjustedWeights.specialWeight())
		);
	}

	private static void reloadConfig() {
		activeConfig = ZombieMobConfig.load();
		if (activeConfig.anyEnabled()) {
			MadokuCraftMobs.LOGGER.info("Madoku Craft Mobs: zombie-family configs loaded.");
			MadokuCraftMobs.infoDebug(
				LOG_SOURCE_ZOMBIE,
				"Family loaded. zombie(enabled={}, weights={}/{}), husk(enabled={}, weights={}/{}), drowned(enabled={}, weights={}/{}), zombieVillager(enabled={}, weights={}/{}).",
				activeConfig.zombie().enabled(),
				activeConfig.zombie().adult().spawnWeight(),
				activeConfig.zombie().baby().spawnWeight(),
				activeConfig.husk().enabled(),
				activeConfig.husk().adult().spawnWeight(),
				activeConfig.husk().baby().spawnWeight(),
				activeConfig.drowned().enabled(),
				activeConfig.drowned().adult().spawnWeight(),
				activeConfig.drowned().baby().spawnWeight(),
				activeConfig.zombieVillager().enabled(),
				activeConfig.zombieVillager().adult().spawnWeight(),
				activeConfig.zombieVillager().baby().spawnWeight()
			);
		} else {
			MadokuCraftMobs.LOGGER.info("Madoku Craft Mobs: zombie-family systems disabled by config.");
			MadokuCraftMobs.infoDebug(LOG_SOURCE_ZOMBIE, "Zombie-family systems disabled in config.");
		}
	}

	private static void applyFamilyConfig(ZombieEntity zombie) {
		ZombieMobConfig config = activeConfig;
		if (config == null) {
			return;
		}

		ZombieMobConfig.ZombieTypeConfig typeConfig = config.resolveType(zombie.getType());
		if (typeConfig == null || !typeConfig.enabled()) {
			return;
		}

		applyConfig(zombie, typeConfig);
	}

	private static void applyConfig(ZombieEntity zombie, ZombieMobConfig.ZombieTypeConfig config) {
		double oldMaxHealth = zombie.getMaxHealth();
		ZombieMobConfig.ZombieVariantConfig variant = zombie.isBaby() ? config.baby() : config.adult();
		MobConfigJsonUtil.UniversalMobStats stats = variant.stats();

		MobSystemUtil.applyUniversalStats(zombie, stats, zombie.getEntityWorld().getDifficulty());
		MobSystemUtil.rescaleCurrentHealth(zombie, oldMaxHealth);

		zombie.setCanPickUpLoot(variant.canPickUpLoot());
		zombie.setCanBreakDoors(variant.canBreakDoors());
		disableReinforcements(zombie);
	}

	private static String resolveLogSource(EntityType<?> type) {
		if (type == EntityType.HUSK) {
			return LOG_SOURCE_HUSK;
		}
		if (type == EntityType.DROWNED) {
			return LOG_SOURCE_DROWNED;
		}
		if (type == EntityType.ZOMBIE_VILLAGER) {
			return LOG_SOURCE_ZOMBIE_VILLAGER;
		}
		return LOG_SOURCE_ZOMBIE;
	}

	private static void disableReinforcements(ZombieEntity zombie) {
		EntityAttributeInstance instance = zombie.getAttributeInstance(EntityAttributes.SPAWN_REINFORCEMENTS);
		if (instance == null) {
			return;
		}
		instance.clearModifiers();
		if (Double.compare(instance.getBaseValue(), 0.0) != 0) {
			instance.setBaseValue(0.0);
		}
	}

	private static void clearSpawnEquipment(ZombieEntity zombie) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!zombie.getEquippedStack(slot).isEmpty()) {
				zombie.equipStack(slot, ItemStack.EMPTY);
			}
		}
	}

}
