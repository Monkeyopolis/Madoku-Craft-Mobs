package madoku.craft.java.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime group for configured entity components and attributes. */
public final class EntityComponentsManager {
	private static final ConcurrentHashMap<UUID, MobBabySettings> MOB_BABY_SETTINGS_CACHE = new ConcurrentHashMap<>();

	public record MobBabySettings(boolean configured, boolean ageable, long durationTicks) {
		private static final MobBabySettings DISABLED = new MobBabySettings(false, false, 0L);
	}

	private EntityComponentsManager() {
	}

	public static boolean applyConfiguredComponents(LivingEntity entity, JsonObject variant) {
		if (entity == null || variant == null) return false;
		JsonElement element = variant.get(MobConfigManager.FIELD_MOB_COMPONENTS);
		if (element == null || !element.isJsonObject()) return false;
		JsonObject components = element.getAsJsonObject();
		boolean changed = MobEntityManager.applyUniversalBaseStatsForRuntime(entity, variant);
		changed |= set(entity, Attributes.WATER_MOVEMENT_EFFICIENCY, components, MobConfigManager.FIELD_SWIMMING_SPEED);
		return changed;
	}

	public static MobBabySettings resolveMobBabySettings(LivingEntity entity) {
		if (entity == null) {
			return MobBabySettings.DISABLED;
		}
		UUID entityId = entity.getUUID();
		MobBabySettings cached = MOB_BABY_SETTINGS_CACHE.get(entityId);
		if (cached != null) {
			return cached;
		}

		MobBabySettings resolved = resolveMobBabySettingsUncached(entity);
		MOB_BABY_SETTINGS_CACHE.put(entityId, resolved);
		return resolved;
	}

	private static MobBabySettings resolveMobBabySettingsUncached(LivingEntity entity) {
		JsonObject variant = MobEntityManager.resolveConfiguredEntityVariantForRuntime(entity);
		JsonElement componentsElement = variant.get(MobConfigManager.FIELD_MOB_COMPONENTS);
		if (componentsElement == null || !componentsElement.isJsonObject()) {
			return MobBabySettings.DISABLED;
		}
		JsonObject components = componentsElement.getAsJsonObject();
		JsonElement mobBabyElement = components.get(MobConfigManager.FIELD_MOB_BABY);
		if (mobBabyElement == null || !mobBabyElement.isJsonObject()) {
			return MobBabySettings.DISABLED;
		}
		JsonObject mobBaby = mobBabyElement.getAsJsonObject();
		JsonElement ageableElement = mobBaby.get(MobConfigManager.FIELD_AGEABLE);
		if (ageableElement == null || ageableElement.isJsonNull()) {
			return MobBabySettings.DISABLED;
		}
		boolean ageable = false;
		double durationSeconds = readDouble(mobBaby, MobConfigManager.FIELD_DURATION, 0.0D);
		if (ageableElement.isJsonObject()) {
			JsonObject ageableGroup = ageableElement.getAsJsonObject();
			ageable = readBoolean(ageableGroup, MobConfigManager.FIELD_ENABLED, false);
			durationSeconds = readDouble(ageableGroup, MobConfigManager.FIELD_DURATION, durationSeconds);
		} else if (ageableElement.isJsonPrimitive() && ageableElement.getAsJsonPrimitive().isBoolean()) {
			ageable = ageableElement.getAsBoolean();
		} else {
			return MobBabySettings.DISABLED;
		}
		return new MobBabySettings(true, ageable, resolveDurationTicks(durationSeconds));
	}

	public static void invalidateMobBabySettings(UUID entityId) {
		if (entityId != null) {
			MOB_BABY_SETTINGS_CACHE.remove(entityId);
		}
	}

	public static void clearMobBabySettingsCache() {
		MOB_BABY_SETTINGS_CACHE.clear();
	}

	public static boolean applyMobBabyComponent(LivingEntity entity) {
		if (!(entity instanceof AgeableMob ageableMob)) {
			return false;
		}
		MobBabySettings settings = resolveMobBabySettings(entity);
		if (!settings.configured() || !ageableMob.isBaby()) {
			return false;
		}
		int desiredAge = settings.ageable()
			? settings.durationTicks() <= 0L ? 0 : (int) -settings.durationTicks()
			: AgeableMob.BABY_START_AGE;
		if (settings.ageable() && ageableMob.getAge() != AgeableMob.BABY_START_AGE && settings.durationTicks() > 0L) {
			return false;
		}
		if (ageableMob.getAge() == desiredAge) {
			return false;
		}
		ageableMob.setAge(desiredAge);
		return true;
	}

	public static boolean applyWorldDifficultyScaling(LivingEntity entity) {
		if (!MobConfigManager.isEnabled() || !MobEntityManager.isDifficultyScalingEligible(entity) || !MobWorldDifficultyManager.isEnabled()
			|| !EntityConfigManager.isWorldDifficultyScalingEnabled(MobEntityManager.resolveMobFileConfigRootForRuntime(MobEntityManager.resolveRuntimeMobFileKey(entity)))) return false;
		if (entity instanceof MobEntityManager.DifficultyState state && state.madokuCraft$isWorldDifficultyScalingApplied()) {
			return false;
		}
		boolean hardcore = entity.level().getServer() != null && entity.level().getServer().isHardcore();
		boolean changed = false;
		changed |= add(entity, Attributes.MAX_HEALTH, MobConfigManager.FIELD_HEALTH, hardcore);
		changed |= add(entity, Attributes.ATTACK_DAMAGE, MobConfigManager.FIELD_DAMAGE, hardcore);
		changed |= add(entity, Attributes.MOVEMENT_SPEED, MobConfigManager.FIELD_MOVEMENT_SPEED, hardcore);
		changed |= add(entity, Attributes.FLYING_SPEED, MobConfigManager.FIELD_FLYING_SPEED, hardcore);
		changed |= add(entity, Attributes.WATER_MOVEMENT_EFFICIENCY, MobConfigManager.FIELD_SWIMMING_SPEED, hardcore);
		int baseExperienceDrop = MobEntityManager.resolveMobExperienceDropForRuntime(entity);
		int resolvedExperienceDrop = (int) Math.round(MobWorldDifficultyManager.resolveValue(
			MobConfigManager.FIELD_EXPERIENCE_DROP,
			baseExperienceDrop,
			entity.level().getDifficulty(),
			hardcore
		));
		if (resolvedExperienceDrop != baseExperienceDrop) {
			MobEntityManager.applyExperienceDropForRuntime(entity, resolvedExperienceDrop);
			changed = true;
		}
		if (entity instanceof Mob mob) {
			MobRegionalDifficultyManager.roundFinalScalingValues(mob);
		}
		if (entity instanceof MobEntityManager.DifficultyState state) {
			state.madokuCraft$setWorldDifficultyScalingApplied(true);
		}
		return changed;
	}

	private static boolean set(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, JsonObject root, String key) {
		JsonElement value = root.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) return false;
		double resolved = value.getAsDouble();
		if (!Double.isFinite(resolved)) return false;
		instance.setBaseValue(Math.max(0.0D, resolved));
		if (attribute == Attributes.MAX_HEALTH) entity.setHealth(Math.min(entity.getHealth(), entity.getMaxHealth()));
		return true;
	}

	private static boolean add(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String key, boolean hardcore) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) return false;
		double base = instance.getBaseValue();
		double oldMaxHealth = attribute == Attributes.MAX_HEALTH ? entity.getMaxHealth() : 0.0D;
		boolean wasAtMaxHealth = attribute == Attributes.MAX_HEALTH && entity.getHealth() >= oldMaxHealth;
		double addition = MobWorldDifficultyManager.resolveAddition(key, base, entity.level().getDifficulty(), hardcore);
		if (!Double.isFinite(addition) || addition == 0.0D) return false;
		instance.setBaseValue(Math.max(0.0D, base + addition));
		if (attribute == Attributes.MAX_HEALTH) {
			entity.setHealth(wasAtMaxHealth
				? (float) entity.getMaxHealth()
				: Math.min(entity.getHealth(), entity.getMaxHealth()));
		}
		return true;
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		JsonElement value = root == null ? null : root.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return value.getAsBoolean();
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		JsonElement value = root == null ? null : root.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			double resolved = value.getAsDouble();
			return Double.isFinite(resolved) ? resolved : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static long resolveDurationTicks(double durationSeconds) {
		if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0D) {
			return 0L;
		}
		double ticks = durationSeconds * 20.0D;
		return ticks >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1L, Math.round(ticks));
	}
}
