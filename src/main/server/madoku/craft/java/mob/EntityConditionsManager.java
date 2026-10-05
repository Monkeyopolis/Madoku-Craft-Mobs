package madoku.craft.java.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Evaluates the shared condition format used by every configured goal. */
public final class EntityConditionsManager {
	private EntityConditionsManager() {
	}

	static boolean areSatisfied(LivingEntity entity, JsonObject goal) {
		if (entity == null || goal == null) {
			return false;
		}
		JsonElement conditionsElement = goal.get(MobConfigManager.FIELD_CONDITIONS);
		if (conditionsElement == null) {
			return true;
		}
		if (!conditionsElement.isJsonArray()) {
			return false;
		}
		for (JsonElement conditionElement : conditionsElement.getAsJsonArray()) {
			if (conditionElement == null || !conditionElement.isJsonObject()
				|| !evaluate(entity, conditionElement.getAsJsonObject())) {
				return false;
			}
		}
		return true;
	}

	private static boolean evaluate(LivingEntity entity, JsonObject condition) {
		String type = normalize(readString(condition, MobConfigManager.FIELD_CONDITION, ""));
		return switch (type) {
			case MobConfigManager.CONDITION_BABY_NEARBY -> {
				double distance = Math.max(0.0D, readDouble(condition, MobConfigManager.FIELD_DISTANCE, 0.0D));
				yield MobEntityManager.hasConfiguredBabyNearby(entity, distance);
			}
			case MobConfigManager.CONDITION_LIGHT_LEVEL -> {
				double maximumLight = Math.max(0.0D, Math.min(15.0D,
					readDouble(condition, MobConfigManager.FIELD_VALUE, 15.0D)));
				yield entity.level().getMaxLocalRawBrightness(entity.blockPosition()) <= maximumLight;
			}
			case "has-target" -> {
				boolean expected = readBoolean(condition, MobConfigManager.FIELD_VALUE, true);
				yield entity instanceof Mob mob && (mob.getTarget() != null) == expected;
			}
			case "on-ground" -> entity.onGround() == readBoolean(condition, MobConfigManager.FIELD_VALUE, true);
			case "underwater" -> entity.isUnderWater() == readBoolean(condition, MobConfigManager.FIELD_VALUE, true);
			default -> false;
		};
	}

	private static String readString(JsonObject root, String key, String fallback) {
		JsonElement element = root == null ? null : root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		return element.getAsString();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		JsonElement element = root == null ? null : root.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
			? element.getAsBoolean() : fallback;
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		JsonElement element = root == null ? null : root.get(key);
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

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
	}
}
