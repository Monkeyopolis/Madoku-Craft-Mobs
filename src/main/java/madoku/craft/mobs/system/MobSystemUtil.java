package madoku.craft.mobs.system;

import madoku.craft.mobs.MadokuCraftMobs;
import madoku.craft.mobs.mixin.MobEntityExperienceAccessor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

/**
 * Shared helpers for mob systems to keep behavior consistent and reduce duplication.
 */
public final class MobSystemUtil {
	public static final double HEALTH_DIFFICULTY_STEP = 4.0;
	public static final double MOVEMENT_SPEED_DIFFICULTY_STEP = 0.01;
	public static final double DAMAGE_DIFFICULTY_STEP = 1.0;
	public static final double ARMOR_DIFFICULTY_STEP = 0.5;
	public static final double KNOCKBACK_RESISTANCE_DIFFICULTY_STEP = 0.1;
	public static final double SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP = 2.0;

	private MobSystemUtil() {
	}

	public static void setBaseValue(LivingEntity entity, RegistryEntry<EntityAttribute> attribute, double value) {
		EntityAttributeInstance instance = entity.getAttributeInstance(attribute);
		if (instance != null && Double.compare(instance.getBaseValue(), value) != 0) {
			instance.setBaseValue(value);
		}
	}

	public static void setBaseValueIfPresent(LivingEntity entity, RegistryEntry<EntityAttribute> attribute, Double value) {
		if (value != null) {
			setBaseValue(entity, attribute, value);
		}
	}

	public static void applyUniversalStats(
		LivingEntity entity,
		MobConfigJsonUtil.UniversalMobStats stats,
		Difficulty difficulty
	) {
		if (entity == null || stats == null) {
			return;
		}
		boolean hardcore = isHardcoreWorld(entity.getEntityWorld());

		setBaseValueIfPresent(
			entity,
			EntityAttributes.MAX_HEALTH,
			resolveDifficultyAdjustedOptionalValue(stats.health(), difficulty, hardcore, HEALTH_DIFFICULTY_STEP, 0.0)
		);
		setBaseValueIfPresent(
			entity,
			EntityAttributes.ARMOR,
			resolveDifficultyAdjustedOptionalValue(stats.armor(), difficulty, hardcore, ARMOR_DIFFICULTY_STEP, 0.0)
		);
		setBaseValueIfPresent(
			entity,
			EntityAttributes.ATTACK_DAMAGE,
			resolveDifficultyAdjustedOptionalValue(stats.damage(), difficulty, hardcore, DAMAGE_DIFFICULTY_STEP, 0.0)
		);
		setBaseValueIfPresent(
			entity,
			EntityAttributes.MOVEMENT_SPEED,
			resolveDifficultyAdjustedOptionalValue(stats.movementSpeed(), difficulty, hardcore, MOVEMENT_SPEED_DIFFICULTY_STEP, 0.0)
		);
		setBaseValueIfPresent(
			entity,
			EntityAttributes.KNOCKBACK_RESISTANCE,
			resolveDifficultyAdjustedOptionalValue(stats.knockbackResistance(), difficulty, hardcore, KNOCKBACK_RESISTANCE_DIFFICULTY_STEP, 0.0)
		);
		setBaseValueIfPresent(entity, EntityAttributes.SCALE, stats.scale());
		applyExperienceDrop(entity, stats.experienceDrop());
	}

	public static void applyExperienceDrop(LivingEntity entity, Integer experienceDrop) {
		if (!(entity instanceof MobEntity mobEntity) || experienceDrop == null) {
			return;
		}
		((MobEntityExperienceAccessor) mobEntity).madokuCraftMobs$setExperiencePoints(Math.max(0, experienceDrop));
	}

	public static double resolveDifficultyAdjustedValue(
		Difficulty difficulty,
		boolean hardcore,
		double baseValue,
		double step,
		double minimum
	) {
		int tier = resolveDifficultyTier(difficulty, hardcore);
		return Math.max(minimum, baseValue + (step * tier));
	}

	public static double resolveDifficultyAdjustedInverseValue(
		Difficulty difficulty,
		boolean hardcore,
		double baseValue,
		double step,
		double minimum
	) {
		int tier = resolveDifficultyTier(difficulty, hardcore);
		return Math.max(minimum, baseValue - (step * tier));
	}

	public static Double resolveDifficultyAdjustedOptionalValue(
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

	public static void rescaleCurrentHealth(LivingEntity entity, double oldMaxHealth) {
		double newMaxHealth = entity.getMaxHealth();
		if (newMaxHealth <= 0.0) {
			return;
		}

		float currentHealth = entity.getHealth();
		float scaledHealth = oldMaxHealth > 0.0
			? (float) (newMaxHealth * (currentHealth / oldMaxHealth))
			: (float) newMaxHealth;
		entity.setHealth(Math.min((float) newMaxHealth, Math.max(0.0f, scaledHealth)));
	}

	public static boolean canApplyBabySpawnRoll(
		ZombieEntity zombie,
		EntityType<?> expectedType,
		Difficulty difficulty,
		Random random,
		boolean enabled,
		boolean useCustomBabySpawnChance
	) {
		return zombie != null
			&& zombie.getType() == expectedType
			&& difficulty != null
			&& random != null
			&& enabled
			&& useCustomBabySpawnChance;
	}

	public static boolean applyBabySpawnRoll(
		ZombieEntity zombie,
		Difficulty difficulty,
		Random random,
		double chance,
		String logSource
	) {
		boolean shouldBeBaby = random.nextFloat() < chance;
		zombie.setBaby(shouldBeBaby);
		MadokuCraftMobs.infoDebug(
			logSource,
			"Spawn roll difficulty={}, babyChance={}%, result={}",
			difficulty.name(),
			Math.round(chance * 10000.0) / 100.0,
			shouldBeBaby ? "BABY" : "ADULT"
		);
		return shouldBeBaby;
	}

	public static SpawnWeightPair resolveDifficultyShiftedSpawnWeights(
		double regularWeight,
		double specialWeight,
		Difficulty difficulty,
		boolean hardcore,
		double difficultyStep
	) {
		double regular = Math.max(0.0, regularWeight);
		double special = Math.max(0.0, specialWeight);
		if (difficulty == null) {
			return new SpawnWeightPair(regular, special);
		}

		double shiftPerTier = Math.max(0.0, difficultyStep);
		if (shiftPerTier <= 0.0) {
			return new SpawnWeightPair(regular, special);
		}

		double shift = shiftPerTier * resolveDifficultyTier(difficulty, hardcore);
		if (shift > 0.0) {
			double transferred = Math.min(shift, regular);
			regular -= transferred;
			special += transferred;
		} else if (shift < 0.0) {
			double transferred = Math.min(-shift, special);
			special -= transferred;
			regular += transferred;
		}
		return new SpawnWeightPair(regular, special);
	}

	public record SpawnWeightPair(double regularWeight, double specialWeight) {
	}

	public static double roundToTwoDecimals(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	public static int resolveDifficultyTier(Difficulty difficulty, boolean hardcore) {
		Difficulty resolved = difficulty == null ? Difficulty.NORMAL : difficulty;
		return switch (resolved) {
			case PEACEFUL -> -2;
			case EASY -> -1;
			case NORMAL -> 0;
			case HARD -> hardcore ? 2 : 1;
		};
	}

	public static boolean isHardcoreWorld(World world) {
		if (world == null) {
			return false;
		}
		MinecraftServer server = world.getServer();
		return server != null && server.isHardcore();
	}
}
