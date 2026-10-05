package madoku.craft.java.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/** Resolves configured mob definitions by their declared {@code mob-id}. */
final class MobDefinitionRegistry {
	private static volatile Map<String, String> fileKeysByMobId = Map.of();

	private MobDefinitionRegistry() {
	}

	static void rebuild(Map<String, JsonObject> files) {
		Map<String, String> resolved = new LinkedHashMap<>();
		if (files != null) {
			for (Map.Entry<String, JsonObject> entry : files.entrySet()) {
				String fileKey = normalize(entry.getKey());
				String mobId = readMobId(entry.getValue());
				if (!fileKey.isBlank() && !mobId.isBlank()) {
					resolved.putIfAbsent(mobId, fileKey);
				}
			}
		}
		fileKeysByMobId = Map.copyOf(resolved);
	}

	static String resolveFileKey(EntityType<?> entityType) {
		if (entityType == null) {
			return "";
		}
		Identifier identifier = EntityType.getKey(entityType);
		return identifier == null ? "" : fileKeysByMobId.getOrDefault(normalize(identifier.toString()), "");
	}

	private static String readMobId(JsonObject fileRoot) {
		if (fileRoot == null) {
			return "";
		}
		JsonElement element = fileRoot.get(MobConfigManager.FIELD_MOB_ID);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return "";
		}
		return normalize(element.getAsString());
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}
