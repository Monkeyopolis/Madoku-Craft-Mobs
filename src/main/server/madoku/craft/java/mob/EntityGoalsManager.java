package madoku.craft.java.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

/** Shared runtime policy for configured AI goals. */
public final class EntityGoalsManager {
	private static final JsonObject DISABLED_GOAL = createDisabledGoal();
	private static final Map<GoalCooldownKey, Long> GOAL_COOLDOWNS = new ConcurrentHashMap<>();
	private static final Map<Goal, WeakReference<Mob>> GOAL_OWNER_CACHE =
		Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Goal, CachedGoalSettings> GOAL_SETTINGS_CACHE =
		Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<LivingEntity, CachedPlayerTargetGoal> PLAYER_TARGET_GOAL_CACHE =
		Collections.synchronizedMap(new WeakHashMap<>());

	private EntityGoalsManager() {
	}

	static JsonObject resolveConfiguredGoalForRuntime(LivingEntity entity, String goalKey) {
		if (entity == null || goalKey == null || goalKey.isBlank() || !MobConfigManager.isEnabled()) {
			return null;
		}
		String fileKey = MobEntityManager.resolveRuntimeMobFileKey(entity);
		if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
			return null;
		}
		JsonObject fileRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
		if (!readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_GOALS, true)) {
			return null;
		}
		JsonObject variant = MobEntityManager.resolveConfiguredEntityVariantForRuntime(entity);
		JsonObject goal = readGoal(variant, goalKey);
		if (goal != null) {
			return goal;
		}
		// Goal overrides are variant profiles. An omitted ranged goal means the
		// variant is melee-only, while an omitted melee goal means the variant is
		// ranged-only when a ranged goal is explicitly enabled.
		if (isRangedGoalKey(goalKey)
			|| ("melee-attack".equals(goalKey) && hasEnabledRangedGoal(variant))) {
			return DISABLED_GOAL;
		}
		return null;
	}

	static boolean isGoalEnabledForRuntime(LivingEntity entity, String goalKey) {
		JsonObject goal = resolveConfiguredGoalForRuntime(entity, goalKey);
		return goal != null && readBoolean(goal, MobConfigManager.FIELD_ENABLED, false);
	}

	public static Boolean resolvePlayerTargetGoal(LivingEntity entity) {
		JsonObject targetPlayer = resolvePlayerTargetDefinitionForRuntime(entity);
		return targetPlayer == null ? null : readBoolean(targetPlayer, MobConfigManager.FIELD_ENABLED, false);
	}

	public static void applyProjectileFireSetting(LivingEntity attacker, Entity projectile) {
		if (projectile != null) {
			projectile.setRemainingFireTicks(isProjectileSetOnFireEnabled(attacker) ? 100 : 0);
		}
	}

	private static boolean isProjectileSetOnFireEnabled(LivingEntity entity) {
		JsonObject rangedAttack = resolveConfiguredGoalForRuntime(entity, MobConfigManager.FIELD_RANGED_ATTACK);
		if (rangedAttack == null) {
			return false;
		}
		JsonElement element = rangedAttack.get(MobConfigManager.FIELD_PROJECTILE_SET_ON_FIRE);
		return element != null
			&& element.isJsonObject()
			&& readBoolean(element.getAsJsonObject(), MobConfigManager.FIELD_ENABLED, false);
	}

	public static boolean arePlayerTargetConditionsSatisfied(LivingEntity entity) {
		return arePlayerTargetConditionsSatisfied(entity, null);
	}

	public static boolean arePlayerTargetConditionsSatisfied(LivingEntity entity, LivingEntity target) {
		JsonObject targetPlayer = resolvePlayerTargetDefinitionForRuntime(entity);
		return readBoolean(targetPlayer, MobConfigManager.FIELD_ENABLED, false)
			&& EntityConditionsManager.areSatisfied(entity, targetPlayer, target);
	}

	public static boolean shouldIgnorePlayerTarget(Mob attacker, LivingEntity target) {
		if (!(target instanceof Player)) {
			return false;
		}
		JsonObject targetPlayer = resolvePlayerTargetDefinitionForRuntime(attacker);
		return targetPlayer != null
			&& (!readBoolean(targetPlayer, MobConfigManager.FIELD_ENABLED, false)
				|| !EntityConditionsManager.areSatisfied(attacker, targetPlayer, target));
	}

	public static boolean shouldAllowCanUse(Mob mob, Goal goal) {
		GoalSettings settings = resolveSettings(mob, goal);
		if (settings == null) {
			return true;
		}
		if (!settings.enabled() || !EntityConditionsManager.areSatisfied(mob, settings.definition())) {
			return false;
		}
		if (isCoolingDown(mob, settings.key())) {
			return false;
		}
		return settings.weight() >= 100.0D
			|| (settings.weight() > 0.0D && mob.getRandom().nextDouble() * 100.0D < settings.weight());
	}

	public static boolean shouldAllowCanContinue(Mob mob, Goal goal) {
		GoalSettings settings = resolveSettings(mob, goal);
		return settings == null
			|| (settings.enabled() && EntityConditionsManager.areSatisfied(mob, settings.definition()));
	}

	public static int resolvePriority(Mob mob, Goal goal, int fallback) {
		GoalSettings settings = resolveSettings(mob, goal);
		return settings == null ? fallback : settings.priority();
	}

	public static void onGoalStopped(Mob mob, Goal goal) {
		GoalSettings settings = resolveSettings(mob, goal);
		if (settings == null || settings.cooldownTicks() <= 0) {
			return;
		}
		GOAL_COOLDOWNS.put(new GoalCooldownKey(mob.getUUID(), settings.key()),
			mob.level().getGameTime() + settings.cooldownTicks());
	}

	static void onEntityCleanup(LivingEntity entity) {
		if (entity != null) {
			GOAL_COOLDOWNS.keySet().removeIf(key -> key.entityId().equals(entity.getUUID()));
			PLAYER_TARGET_GOAL_CACHE.remove(entity);
		}
	}

	static void clearRuntimeCache() {
		GOAL_OWNER_CACHE.clear();
		GOAL_SETTINGS_CACHE.clear();
		PLAYER_TARGET_GOAL_CACHE.clear();
	}

	private static JsonObject resolvePlayerTargetDefinitionForRuntime(LivingEntity entity) {
		if (entity == null || !MobConfigManager.isEnabled()) {
			return null;
		}
		long gameTime = entity.level().getGameTime();
		CachedPlayerTargetGoal cached = PLAYER_TARGET_GOAL_CACHE.get(entity);
		if (cached != null && cached.gameTime() == gameTime) {
			return cached.definition();
		}

		JsonObject definition = resolveConfiguredGoalForRuntime(entity, MobConfigManager.FIELD_TARGET_PLAYER);
		PLAYER_TARGET_GOAL_CACHE.put(entity, new CachedPlayerTargetGoal(gameTime, definition));
		return definition;
	}

	public static Mob resolveOwningMob(Goal goal) {
		if (goal == null) {
			return null;
		}
		WeakReference<Mob> cachedReference = GOAL_OWNER_CACHE.get(goal);
		Mob cachedMob = cachedReference == null ? null : cachedReference.get();
		if (cachedMob != null) {
			return cachedMob;
		}

		Mob resolved = resolveOwningMobUncached(goal);
		if (resolved != null) {
			GOAL_OWNER_CACHE.put(goal, new WeakReference<>(resolved));
		}
		return resolved;
	}

	private static Mob resolveOwningMobUncached(Goal goal) {
			Class<?> type = goal.getClass();
			while (type != null && type != Object.class) {
				for (Field field : type.getDeclaredFields()) {
					if (Modifier.isStatic(field.getModifiers()) || !Mob.class.isAssignableFrom(field.getType())) continue;
					try {
						if (!field.trySetAccessible()) continue;
						Object value = field.get(goal);
						if (value instanceof Mob mob) return mob;
					} catch (IllegalAccessException ignored) {
					}
				}
				type = type.getSuperclass();
			}
			return null;
	}

	static String resolveGoalKey(Goal goal) {
		if (goal == null) return "";
		String name = goal.getClass().getSimpleName();
		if (name.contains("SpiderTarget") || name.contains("NearestAttackableTarget") || name.contains("EndermanLookForPlayer")) {
			return MobConfigManager.FIELD_TARGET_PLAYER;
		}
		if (name.contains("HurtByTarget")) return "hurt-by-target";
		if (name.contains("RangedBow") || name.contains("Crossbow")) return "ranged-attack";
		if (name.contains("Trident")) return MobConfigManager.FIELD_TRIDENT_ATTACK;
		if (name.contains("MeleeAttack") || name.endsWith("AttackGoal")) return "melee-attack";
		if (name.contains("FollowParent")) return MobConfigManager.FIELD_FOLLOW_PARENT;
		return camelCaseToKey(name.endsWith("Goal") ? name.substring(0, name.length() - 4) : name);
	}

	private static GoalSettings resolveSettings(Mob mob, Goal goal) {
		if (mob == null || goal == null || !MobConfigManager.isEnabled()) return null;
		RuntimeGoalState state = resolveRuntimeGoalState(mob);
		CachedGoalSettings cached = GOAL_SETTINGS_CACHE.get(goal);
		if (cached != null && cached.owner().get() == mob && cached.state().equals(state)) {
			return cached.settings();
		}

		String key = resolveGoalKey(goal);
		JsonObject definition = resolveConfiguredGoalForRuntime(mob, key);
		GoalSettings settings = definition == null ? null : new GoalSettings(
			key,
			readBoolean(definition, MobConfigManager.FIELD_ENABLED, false),
			readInt(definition, MobConfigManager.FIELD_PRIORITY, Integer.MAX_VALUE),
			Math.max(0.0D, Math.min(100.0D, readDouble(definition, MobConfigManager.FIELD_WEIGHT, 100.0D))),
			Math.max(0, readInt(definition, MobConfigManager.FIELD_COOLDOWN_TICKS, 0)),
			definition
		);
		GOAL_SETTINGS_CACHE.put(goal, new CachedGoalSettings(new WeakReference<>(mob), state, settings));
		return settings;
	}

	private static RuntimeGoalState resolveRuntimeGoalState(Mob mob) {
		return new RuntimeGoalState(
			mob.entityTags().hashCode(),
			MobEntityManager.isBabyVariantEntityForRuntime(mob),
			mob instanceof Creeper creeper && creeper.isPowered()
		);
	}

	private static boolean isCoolingDown(Mob mob, String goalKey) {
		GoalCooldownKey key = new GoalCooldownKey(mob.getUUID(), goalKey);
		Long until = GOAL_COOLDOWNS.get(key);
		if (until == null || until <= mob.level().getGameTime()) {
			if (until != null) GOAL_COOLDOWNS.remove(key, until);
			return false;
		}
		return true;
	}

	private static JsonObject readGoal(JsonObject variant, String goalKey) {
		if (variant == null || goalKey == null || goalKey.isBlank()) return null;
		JsonElement goalsElement = variant.get(MobConfigManager.FIELD_MOB_GOALS);
		if (goalsElement == null || !goalsElement.isJsonObject()) return null;
		JsonElement goalElement = goalsElement.getAsJsonObject().get(goalKey);
		return goalElement != null && goalElement.isJsonObject() ? goalElement.getAsJsonObject() : null;
	}

	private static boolean isRangedGoalKey(String goalKey) {
		return MobConfigManager.FIELD_RANGED_ATTACK.equals(goalKey)
			|| MobConfigManager.FIELD_TRIDENT_ATTACK.equals(goalKey);
	}

	private static boolean hasEnabledRangedGoal(JsonObject variant) {
		return isConfiguredGoalEnabled(variant, MobConfigManager.FIELD_RANGED_ATTACK)
			|| isConfiguredGoalEnabled(variant, MobConfigManager.FIELD_TRIDENT_ATTACK);
	}

	private static boolean isConfiguredGoalEnabled(JsonObject variant, String goalKey) {
		return readBoolean(readGoal(variant, goalKey), MobConfigManager.FIELD_ENABLED, false);
	}

	private static JsonObject createDisabledGoal() {
		JsonObject goal = new JsonObject();
		goal.addProperty(MobConfigManager.FIELD_ENABLED, false);
		return goal;
	}

	private static String camelCaseToKey(String value) {
		return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		JsonElement element = root == null ? null : root.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
			? element.getAsBoolean() : fallback;
	}

	private static int readInt(JsonObject root, String key, int fallback) {
		JsonElement element = root == null ? null : root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return fallback;
		try { return element.getAsInt(); } catch (RuntimeException ignored) { return fallback; }
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		JsonElement element = root == null ? null : root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return fallback;
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private record GoalSettings(String key, boolean enabled, int priority, double weight, int cooldownTicks, JsonObject definition) {
	}

	private record CachedGoalSettings(WeakReference<Mob> owner, RuntimeGoalState state, GoalSettings settings) {
	}

	private record CachedPlayerTargetGoal(long gameTime, JsonObject definition) {
	}

	private record RuntimeGoalState(int entityTagsHash, boolean baby, boolean powered) {
	}

	private record GoalCooldownKey(UUID entityId, String goalKey) {
	}
}
