package madoku.craft.java.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Configuration group for entity definitions. */
public final class EntityConfigManager {
	private EntityConfigManager() {
	}

	public static JsonObject resolveVariant(JsonObject fileRoot, String variantKey) {
		if (fileRoot == null) return new JsonObject();
		JsonElement entityElement = fileRoot.get(MobConfigManager.FIELD_ENTITY);
		if (entityElement == null || !entityElement.isJsonObject()) return new JsonObject();
		JsonObject entity = entityElement.getAsJsonObject();
		String resolvedKey = variantKey == null || variantKey.isBlank()
			? resolvePrimaryVariantKey(fileRoot, entity)
			: variantKey;
		JsonObject selected = resolveVariantPath(entity, resolvedKey);
		return merge(new JsonObject(), selected);
	}

	public static JsonObject resolvePrimaryVariant(JsonObject fileRoot) {
		if (fileRoot == null) return new JsonObject();
		JsonElement entityElement = fileRoot.get(MobConfigManager.FIELD_ENTITY);
		if (entityElement == null || !entityElement.isJsonObject()) return new JsonObject();
		JsonObject entity = entityElement.getAsJsonObject();
		String defaultKey = resolvePrimaryVariantKey(fileRoot, entity);
		JsonObject resolved = resolveVariantPath(entity, defaultKey);
		for (Map.Entry<String, JsonElement> entry : entity.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(defaultKey)
				|| !isVariantKey(entry.getKey())
				|| entry.getValue() == null
				|| !entry.getValue().isJsonObject()) {
				continue;
			}
			resolved.add(entry.getKey(), entry.getValue().deepCopy());
		}
		return resolved;
	}

	public static JsonObject resolvePrimaryVariantOnly(JsonObject fileRoot) {
		if (fileRoot == null) return new JsonObject();
		JsonElement entityElement = fileRoot.get(MobConfigManager.FIELD_ENTITY);
		if (entityElement == null || !entityElement.isJsonObject()) return new JsonObject();
		JsonObject entity = entityElement.getAsJsonObject();
		return resolveVariantPath(entity, resolvePrimaryVariantKey(fileRoot, entity));
	}

	static Map<String, JsonObject> collectTopLevelVariantRoots(JsonObject fileRoot) {
		Map<String, JsonObject> variants = new LinkedHashMap<>();
		if (fileRoot == null) {
			return variants;
		}
		JsonElement entityElement = fileRoot.get(MobConfigManager.FIELD_ENTITY);
		if (entityElement == null || !entityElement.isJsonObject()) {
			return variants;
		}
		for (Map.Entry<String, JsonElement> entry : entityElement.getAsJsonObject().entrySet()) {
			if (entry.getValue() != null && entry.getValue().isJsonObject() && isVariantKey(entry.getKey())) {
				variants.putIfAbsent(entry.getKey().trim().toLowerCase(), entry.getValue().getAsJsonObject());
			}
		}
		return variants;
	}

	static String resolvePrimaryVariantKeyForRuntime(JsonObject fileRoot) {
		if (fileRoot == null) {
			return "";
		}
		JsonElement entityElement = fileRoot.get(MobConfigManager.FIELD_ENTITY);
		if (entityElement == null || !entityElement.isJsonObject()) {
			return "";
		}
		return resolvePrimaryVariantKey(fileRoot, entityElement.getAsJsonObject());
	}

	static JsonObject resolveTopLevelVariant(JsonObject fileRoot, String variantKey) {
		if (fileRoot == null || variantKey == null || variantKey.isBlank()) {
			return new JsonObject();
		}
		JsonObject variant = collectTopLevelVariantRoots(fileRoot).get(variantKey.trim().toLowerCase());
		return variant == null ? new JsonObject() : variant;
	}

	public static boolean isWorldDifficultyScalingEnabled(JsonObject fileRoot) {
		JsonElement entityElement = fileRoot == null ? null : fileRoot.get(MobConfigManager.FIELD_ENTITY);
		if (entityElement == null || !entityElement.isJsonObject()) return true;
		JsonElement enabled = entityElement.getAsJsonObject().get(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
		try {
			return enabled == null || enabled.getAsBoolean();
		} catch (RuntimeException exception) {
			return true;
		}
	}

	public static boolean isRegionalDifficultyScalingEnabled(JsonObject fileRoot) {
		JsonElement entityElement = fileRoot == null ? null : fileRoot.get(MobConfigManager.FIELD_ENTITY);
		if (entityElement == null || !entityElement.isJsonObject()) return true;
		JsonElement enabled = entityElement.getAsJsonObject().get(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW);
		try {
			return enabled == null || enabled.getAsBoolean();
		} catch (RuntimeException exception) {
			return true;
		}
	}

	static JsonElement resolveConfiguredElement(JsonObject root, String key) {
		if (root == null || key == null) return null;
		return root.get(key);
	}

	private static JsonObject resolveVariantPath(JsonObject entity, String variantKey) {
		if (entity == null || entity.entrySet().isEmpty()) return new JsonObject();
		List<String> path = splitPath(variantKey);
		JsonObject current = path.isEmpty() ? firstVariant(entity) : readObject(entity, path.get(0));
		if (current.entrySet().isEmpty()) return new JsonObject();
		JsonObject resolved = merge(new JsonObject(), current);
		for (int index = 1; index < path.size(); index++) {
			JsonObject nested = readObject(current, path.get(index));
			if (nested.entrySet().isEmpty()) break;
			resolved = merge(removeNestedVariantEntries(resolved), nested);
			current = nested;
		}
		return resolved;
	}

	private static String resolvePrimaryVariantKey(JsonObject fileRoot, JsonObject entity) {
		if (fileRoot != null && entity != null) {
			JsonElement mobIdElement = fileRoot.get(MobConfigManager.FIELD_MOB_ID);
			if (mobIdElement != null && mobIdElement.isJsonPrimitive() && mobIdElement.getAsJsonPrimitive().isString()) {
				String mobId = mobIdElement.getAsString().trim().toLowerCase(Locale.ROOT);
				int separator = mobId.indexOf(':');
				String path = separator >= 0 ? mobId.substring(separator + 1) : mobId;
				String canonicalKey = path.replace('_', '-');
				if (isVariantKey(canonicalKey) && entity.has(canonicalKey) && entity.get(canonicalKey).isJsonObject()) {
					return canonicalKey;
				}
			}
		}
		return firstVariantKey(entity);
	}

	private static JsonObject removeNestedVariantEntries(JsonObject root) {
		JsonObject sharedRoot = root == null ? new JsonObject() : root.deepCopy();
		for (String key : new ArrayList<>(sharedRoot.keySet())) {
			JsonElement value = sharedRoot.get(key);
			if (value != null && value.isJsonObject() && isVariantKey(key)) {
				sharedRoot.remove(key);
			}
		}
		return sharedRoot;
	}

	private static List<String> splitPath(String value) {
		List<String> segments = new ArrayList<>();
		if (value == null) return segments;
		for (String segment : value.split("[./]")) {
			if (!segment.isBlank()) segments.add(segment.trim().toLowerCase());
		}
		return segments;
	}

	private static JsonObject firstVariant(JsonObject entity) {
		for (Map.Entry<String, JsonElement> entry : entity.entrySet()) {
			if (isVariantKey(entry.getKey()) && entry.getValue().isJsonObject()) return entry.getValue().getAsJsonObject();
		}
		return new JsonObject();
	}

	private static String firstVariantKey(JsonObject entity) {
		for (Map.Entry<String, JsonElement> entry : entity.entrySet()) {
			if (isVariantKey(entry.getKey()) && entry.getValue().isJsonObject()) return entry.getKey();
		}
		return "";
	}

	static boolean isVariantKey(String key) {
		return !MobConfigManager.FIELD_ENABLED.equals(key)
			&& !MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES.equals(key)
			&& !MobConfigManager.FIELD_OVERRIDE_COMPONENTS.equals(key)
			&& !MobConfigManager.FIELD_OVERRIDE_BEHAVIORS.equals(key)
			&& !MobConfigManager.FIELD_OVERRIDE_GOALS.equals(key)
			&& !MobConfigManager.FIELD_MOB_ID.equals(key)
			&& !MobConfigManager.FIELD_ENTITY.equals(key)
			&& !MobConfigManager.FIELD_CUSTOM_MOB_DROPS.equals(key)
			&& !MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING.equals(key)
			&& !MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW.equals(key)
			&& !MobConfigManager.FIELD_SPAWN_RULES.equals(key)
			&& !MobConfigManager.FIELD_MOB_COMPONENTS.equals(key)
			&& !MobConfigManager.FIELD_MOB_BEHAVIORS.equals(key)
			&& !MobConfigManager.FIELD_MOB_GOALS.equals(key);
	}

	private static JsonObject readObject(JsonObject root, String key) {
		JsonElement value = root.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
	}

	private static JsonObject merge(JsonObject target, JsonObject source) {
		if (source == null) return target;
		for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
			JsonElement current = target.get(entry.getKey());
			if (current != null && current.isJsonObject() && entry.getValue().isJsonObject()) {
				merge(current.getAsJsonObject(), entry.getValue().getAsJsonObject());
			} else {
				target.add(entry.getKey(), entry.getValue().deepCopy());
			}
		}
		return target;
	}
}
