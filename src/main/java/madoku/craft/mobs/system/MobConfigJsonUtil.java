package madoku.craft.mobs.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Shared JSON parsing helpers for mob config classes.
 */
public final class MobConfigJsonUtil {
	public static final String KEY_HEALTH = "health";
	public static final String KEY_ARMOR = "armor";
	public static final String KEY_DAMAGE = "damage";
	public static final String KEY_MOVEMENT_SPEED = "movement_speed";
	public static final String KEY_KNOCKBACK_RESISTANCE = "knockback_resistance";
	public static final String KEY_SCALE = "scale";
	public static final String KEY_EXPERIENCE_DROP = "experience_drop";

	private MobConfigJsonUtil() {
	}

	public record UniversalMobStats(
		Double health,
		Double armor,
		Double damage,
		Double movementSpeed,
		Double knockbackResistance,
		Double scale,
		Integer experienceDrop
	) {
	}

	public record UniversalMobStatsLoadResult(UniversalMobStats stats, boolean changed) {
	}

	public static JsonObject getOrCreateObject(JsonObject root, String key) {
		JsonElement existing = root.get(key);
		if (existing instanceof JsonObject object) {
			return object;
		}
		JsonObject created = new JsonObject();
		root.add(key, created);
		return created;
	}

	public static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
			return primitive.getAsBoolean();
		}
		return fallback;
	}

	public static double readDouble(JsonObject root, String key, double fallback) {
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			double value = primitive.getAsDouble();
			if (Double.isFinite(value)) {
				return value;
			}
		}
		return fallback;
	}

	public static Double readOptionalDouble(JsonObject root, String key) {
		JsonElement element = root.get(key);
		if (element == null || element instanceof JsonNull) {
			return null;
		}
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			double value = primitive.getAsDouble();
			if (Double.isFinite(value)) {
				return value;
			}
		}
		return null;
	}

	public static Integer readOptionalInt(JsonObject root, String key) {
		JsonElement element = root.get(key);
		if (element == null || element instanceof JsonNull) {
			return null;
		}
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			double value = primitive.getAsDouble();
			if (Double.isFinite(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
				return (int) Math.round(value);
			}
		}
		return null;
	}

	public static double sanitizePositive(double value, double fallback) {
		if (!Double.isFinite(value) || value <= 0.0) {
			return fallback;
		}
		return value;
	}

	public static double sanitizeNonNegative(double value, double fallback) {
		if (!Double.isFinite(value) || value < 0.0) {
			return fallback;
		}
		return value;
	}

	public static Double sanitizeOptionalPositive(Double value) {
		if (value == null || !Double.isFinite(value) || value <= 0.0) {
			return null;
		}
		return value;
	}

	public static Double sanitizeOptionalNonNegative(Double value) {
		if (value == null || !Double.isFinite(value) || value < 0.0) {
			return null;
		}
		return value;
	}

	public static Integer sanitizeOptionalNonNegativeInt(Integer value) {
		if (value == null || value < 0) {
			return null;
		}
		return value;
	}

	public static Double clampOptional(Double value, double min, double max) {
		if (value == null || !Double.isFinite(value)) {
			return null;
		}
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	public static double clamp(double value, double min, double max, double fallback) {
		if (!Double.isFinite(value)) {
			return fallback;
		}
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	public static boolean setBoolean(JsonObject root, String key, boolean value) {
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean() == value) {
			return false;
		}
		root.addProperty(key, value);
		return true;
	}

	public static boolean setDouble(JsonObject root, String key, double value) {
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isNumber() && Double.compare(primitive.getAsDouble(), value) == 0) {
			return false;
		}
		root.addProperty(key, value);
		return true;
	}

	public static boolean setNullableDouble(JsonObject root, String key, Double value) {
		JsonElement element = root.get(key);
		if (value == null) {
			if (element == null || element instanceof JsonNull) {
				return false;
			}
			root.add(key, JsonNull.INSTANCE);
			return true;
		}
		if (element instanceof JsonPrimitive primitive && primitive.isNumber() && Double.compare(primitive.getAsDouble(), value) == 0) {
			return false;
		}
		root.addProperty(key, value);
		return true;
	}

	public static boolean setNullableInt(JsonObject root, String key, Integer value) {
		JsonElement element = root.get(key);
		if (value == null) {
			if (element == null || element instanceof JsonNull) {
				return false;
			}
			root.add(key, JsonNull.INSTANCE);
			return true;
		}
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			double rawValue = primitive.getAsDouble();
			if (Double.isFinite(rawValue) && rawValue == value.doubleValue()) {
				return false;
			}
		}
		root.addProperty(key, value);
		return true;
	}

	public static JsonObject buildUniversalStatDefaults(UniversalMobStats defaults) {
		JsonObject node = new JsonObject();
		writeNullableDouble(node, KEY_HEALTH, defaults.health());
		writeNullableDouble(node, KEY_ARMOR, defaults.armor());
		writeNullableDouble(node, KEY_DAMAGE, defaults.damage());
		writeNullableDouble(node, KEY_MOVEMENT_SPEED, defaults.movementSpeed());
		writeNullableDouble(node, KEY_KNOCKBACK_RESISTANCE, defaults.knockbackResistance());
		writeNullableDouble(node, KEY_SCALE, defaults.scale());
		writeNullableInt(node, KEY_EXPERIENCE_DROP, defaults.experienceDrop());
		return node;
	}

	public static UniversalMobStatsLoadResult readUniversalStatOverrides(JsonObject node) {
		boolean changed = false;

		Double health = sanitizeOptionalPositive(readOptionalDouble(node, KEY_HEALTH));
		changed |= setNullableDouble(node, KEY_HEALTH, health);

		Double armor = sanitizeOptionalNonNegative(readOptionalDouble(node, KEY_ARMOR));
		changed |= setNullableDouble(node, KEY_ARMOR, armor);

		Double damage = sanitizeOptionalNonNegative(readOptionalDouble(node, KEY_DAMAGE));
		changed |= setNullableDouble(node, KEY_DAMAGE, damage);

		Double movementSpeed = sanitizeOptionalPositive(readOptionalDouble(node, KEY_MOVEMENT_SPEED));
		changed |= setNullableDouble(node, KEY_MOVEMENT_SPEED, movementSpeed);

		Double knockbackResistance = clampOptional(readOptionalDouble(node, KEY_KNOCKBACK_RESISTANCE), 0.0, 1.0);
		changed |= setNullableDouble(node, KEY_KNOCKBACK_RESISTANCE, knockbackResistance);

		Double scale = sanitizeOptionalPositive(readOptionalDouble(node, KEY_SCALE));
		changed |= setNullableDouble(node, KEY_SCALE, scale);

		Integer experienceDrop = sanitizeOptionalNonNegativeInt(readOptionalInt(node, KEY_EXPERIENCE_DROP));
		changed |= setNullableInt(node, KEY_EXPERIENCE_DROP, experienceDrop);

		return new UniversalMobStatsLoadResult(
			new UniversalMobStats(health, armor, damage, movementSpeed, knockbackResistance, scale, experienceDrop),
			changed
		);
	}

	private static void writeNullableDouble(JsonObject root, String key, Double value) {
		if (value == null) {
			root.add(key, JsonNull.INSTANCE);
		} else {
			root.addProperty(key, value);
		}
	}

	private static void writeNullableInt(JsonObject root, String key, Integer value) {
		if (value == null) {
			root.add(key, JsonNull.INSTANCE);
		} else {
			root.addProperty(key, value);
		}
	}
}
