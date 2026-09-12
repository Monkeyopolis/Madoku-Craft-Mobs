package madoku.craft.java.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONTypeAPIManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Configuration group for world-difficulty scaling. */
public final class WorldDifficultyConfigManager {
	private static final Map<String, ScalingRule> RULES = new ConcurrentHashMap<>();
	private static volatile boolean enabled;

	private WorldDifficultyConfigManager() {
	}

	public static synchronized void initialize() {
		RULES.clear();
		enabled = false;
		try {
			Path directory = MobConfigManager.getOrCreateMobRootDirectory();
			JsonObject root = JSONFormatAPIManager.ensureManagedFile(
				directory.resolve(MobConfigManager.WORLD_DIFFICULTY_SETTINGS_FILE + ".json"),
				MobConfigManager.buildWorldDifficultyDefaults(),
				JSONTypeAPIManager.STATIC_CONFIG,
				null
			);
			enabled = MobConfigManager.isEnabled()
				&& readBoolean(root, MobConfigManager.FIELD_ENABLED, true);
			JsonObject scaling = readObject(root, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
			for (Map.Entry<String, JsonElement> entry : scaling.entrySet()) {
				if (!entry.getValue().isJsonObject()) continue;
				JsonObject value = entry.getValue().getAsJsonObject();
				String type = readString(value, MobConfigManager.FIELD_TYPE, MobConfigManager.TYPE_PERCENTAGE);
				double amount = readDouble(value, MobConfigManager.FIELD_VALUE, 0.0D);
				RULES.put(entry.getKey(), new ScalingRule(type, Math.max(0.0D, amount)));
			}
		} catch (IOException | RuntimeException ignored) {
			RULES.clear();
			enabled = false;
		}
	}

	public static void reset() {
		RULES.clear();
		enabled = false;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static double resolveAddition(String attribute, double baseValue, int difficultyLevel) {
		if (!enabled || attribute == null || difficultyLevel == 0) return 0.0D;
		ScalingRule rule = RULES.get(attribute);
		if (rule == null) return 0.0D;
		double safeBase = Math.max(0.0D, baseValue);
		double addition = switch (rule.type()) {
			case MobConfigManager.TYPE_FLAT -> rule.value() * difficultyLevel;
			case MobConfigManager.TYPE_PERCENTAGE -> safeBase * (rule.value() * difficultyLevel);
			default -> 0.0D;
		};
		return addition;
	}

	private static JsonObject readObject(JsonObject root, String key) {
		JsonElement value = root == null ? null : root.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		try { return root != null && root.has(key) ? root.get(key).getAsBoolean() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static String readString(JsonObject root, String key, String fallback) {
		try { return root != null && root.has(key) ? root.get(key).getAsString() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		try { return root != null && root.has(key) ? root.get(key).getAsDouble() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private record ScalingRule(String type, double value) { }
}

