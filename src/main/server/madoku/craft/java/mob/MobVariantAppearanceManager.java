package madoku.craft.java.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import madoku.craft.java.core.sync.SyncConfigAPIManager;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Resolves configured variant textures and synchronizes them to connected clients. */
public final class MobVariantAppearanceManager {
	public static final String SYNC_ID = "mobs.variant-appearance";

	private static volatile Map<String, VariantAppearance> clientAppearances = Map.of();
	private static boolean initialized;

	private MobVariantAppearanceManager() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}

		SyncConfigAPIManager.register(
			SYNC_ID,
			MobVariantAppearanceManager::createServerSnapshot,
			MobVariantAppearanceManager::applyClientSnapshot,
			MobVariantAppearanceManager::resetClientAppearances
		);
		clientAppearances = collectAppearances(MobConfigManager.getRuntimeMobFiles());
		initialized = true;
	}

	public static VariantAppearance resolve(String mobId, String variantKey) {
		if (mobId == null || variantKey == null || variantKey.isBlank()) {
			return VariantAppearance.EMPTY;
		}
		return clientAppearances.getOrDefault(key(mobId, variantKey), VariantAppearance.EMPTY);
	}

	private static String createServerSnapshot() {
		JsonObject snapshot = new JsonObject();
		for (Map.Entry<String, VariantAppearance> entry : collectAppearances(MobConfigManager.getRuntimeMobFiles()).entrySet()) {
			JsonObject appearance = new JsonObject();
			VariantAppearance value = entry.getValue();
			if (value.texture() != null) {
				appearance.addProperty(MobConfigManager.FIELD_APPEARANCE_TEXTURE, value.texture().toString());
			}
			if (value.eyes() != null) {
				appearance.addProperty(MobConfigManager.FIELD_APPEARANCE_EYES, value.eyes().toString());
			}
			if (value.particles() != null) {
				JsonElement particle = serializeParticle(value.particles());
				if (particle != null) {
					appearance.add(MobConfigManager.FIELD_APPEARANCE_PARTICLES, particle);
				}
			}
			snapshot.add(entry.getKey(), appearance);
		}
		return snapshot.toString();
	}

	public static void emitConfiguredParticles(Mob mob) {
		if (mob == null || mob.isRemoved() || mob.tickCount % 4 != 0 || !(mob.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		String fileKey = MobEntityManager.resolveRuntimeMobFileKey(mob);
		if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
			return;
		}
		String variantKey = MobEntityManager.resolveConfiguredVariantKeyForRuntime(mob);
		if (variantKey.isBlank()) {
			return;
		}
		Identifier mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		if (mobId == null) {
			return;
		}
		ParticleOptions particle = resolve(mobId.toString(), variantKey).particles();
		if (particle == null) {
			return;
		}
		serverLevel.sendParticles(
			particle,
			mob.getX(),
			mob.getY() + mob.getBbHeight() * 0.6D,
			mob.getZ(),
			1,
			0.28D,
			0.28D,
			0.28D,
			0.0D
		);
	}

	private static void applyClientSnapshot(String snapshot) {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(snapshot == null ? "{}" : snapshot);
		} catch (RuntimeException exception) {
			clientAppearances = Map.of();
			return;
		}
		if (parsed == null || !parsed.isJsonObject()) {
			clientAppearances = Map.of();
			return;
		}

		Map<String, VariantAppearance> synchronizedAppearances = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
			if (entry.getValue() != null && entry.getValue().isJsonObject()) {
				VariantAppearance appearance = parseAppearance(entry.getValue().getAsJsonObject());
				if (!appearance.isEmpty()) {
					synchronizedAppearances.put(entry.getKey().toLowerCase(Locale.ROOT), appearance);
				}
			}
		}
		clientAppearances = Map.copyOf(synchronizedAppearances);
	}

	private static void resetClientAppearances() {
		clientAppearances = collectAppearances(MobConfigManager.getRuntimeMobFiles());
	}

	private static Map<String, VariantAppearance> collectAppearances(Map<String, JsonObject> files) {
		Map<String, VariantAppearance> appearances = new LinkedHashMap<>();
		if (files == null) {
			return appearances;
		}

		for (JsonObject fileRoot : files.values()) {
			if (fileRoot == null) {
				continue;
			}
			String mobId = readString(fileRoot, MobConfigManager.FIELD_MOB_ID);
			JsonElement entityElement = fileRoot.get(MobConfigManager.FIELD_ENTITY);
			if (mobId.isBlank() || entityElement == null || !entityElement.isJsonObject()) {
				continue;
			}

			for (Map.Entry<String, JsonElement> variantEntry : entityElement.getAsJsonObject().entrySet()) {
				if (!EntityConfigManager.isVariantKey(variantEntry.getKey())
					|| variantEntry.getValue() == null
					|| !variantEntry.getValue().isJsonObject()) {
					continue;
				}

				JsonObject variant = variantEntry.getValue().getAsJsonObject();
				JsonElement spawnRulesElement = variant.get(MobConfigManager.FIELD_SPAWN_RULES);
				if (spawnRulesElement == null || !spawnRulesElement.isJsonObject()) {
					continue;
				}
				JsonElement appearanceElement = spawnRulesElement.getAsJsonObject().get(MobConfigManager.FIELD_VARIANT_APPEARANCE);
				if (appearanceElement == null || !appearanceElement.isJsonObject()) {
					continue;
				}

				VariantAppearance appearance = parseAppearance(appearanceElement.getAsJsonObject());
				if (!appearance.isEmpty()) {
					appearances.put(key(mobId, variantEntry.getKey()), appearance);
				}
			}
		}
		return appearances;
	}

	private static VariantAppearance parseAppearance(JsonObject root) {
		return new VariantAppearance(
			parseTexture(readString(root, MobConfigManager.FIELD_APPEARANCE_TEXTURE)),
			parseTexture(readString(root, MobConfigManager.FIELD_APPEARANCE_EYES)),
			parseParticle(root == null ? null : root.get(MobConfigManager.FIELD_APPEARANCE_PARTICLES))
		);
	}

	private static ParticleOptions parseParticle(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			return parseSimpleParticle(element.getAsString().trim());
		}
		if (!element.isJsonObject()) {
			return null;
		}

		JsonObject object = element.getAsJsonObject();
		Identifier parsed = Identifier.tryParse(readString(object, MobConfigManager.FIELD_APPEARANCE_PARTICLE_TYPE));
		if (parsed == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(parsed)) {
			return null;
		}
		ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.getValue(parsed);
		if (particleType == ParticleTypes.DUST) {
			int color = parseColor(object.get(MobConfigManager.FIELD_APPEARANCE_PARTICLE_COLOR), 0xFFFFFF);
			float scale = clampScale(readDouble(object, MobConfigManager.FIELD_APPEARANCE_PARTICLE_SCALE, 0.65D));
			return new DustParticleOptions(color, scale);
		}
		return particleType instanceof SimpleParticleType simpleParticleType ? simpleParticleType : null;
	}

	private static ParticleOptions parseSimpleParticle(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		Identifier parsed = Identifier.tryParse(raw);
		if (parsed == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(parsed)) {
			return null;
		}
		ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.getValue(parsed);
		return particleType instanceof SimpleParticleType simpleParticleType ? simpleParticleType : null;
	}

	private static JsonElement serializeParticle(ParticleOptions particle) {
		Identifier particleId = BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType());
		if (particleId == null) {
			return null;
		}
		if (particle instanceof DustParticleOptions dust) {
			JsonObject object = new JsonObject();
			object.addProperty(MobConfigManager.FIELD_APPEARANCE_PARTICLE_TYPE, particleId.toString());
			object.addProperty(MobConfigManager.FIELD_APPEARANCE_PARTICLE_COLOR, formatColor(dust.getColor().x(), dust.getColor().y(), dust.getColor().z()));
			object.addProperty(MobConfigManager.FIELD_APPEARANCE_PARTICLE_SCALE, dust.getScale());
			return object;
		}
		return new com.google.gson.JsonPrimitive(particleId.toString());
	}

	private static int parseColor(JsonElement element, int fallback) {
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			String raw = element.getAsString().trim();
			if (raw.startsWith("#")) raw = raw.substring(1);
			if (raw.startsWith("0x") || raw.startsWith("0X")) raw = raw.substring(2);
			if (raw.length() == 6) {
				try {
					return Integer.parseInt(raw, 16) & 0xFFFFFF;
				} catch (NumberFormatException ignored) {
					return fallback;
				}
			}
		}
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			try {
				return element.getAsInt() & 0xFFFFFF;
			} catch (RuntimeException ignored) {
				return fallback;
			}
		}
		if (element.isJsonArray() && element.getAsJsonArray().size() >= 3) {
			try {
				int red = colorChannel(element.getAsJsonArray().get(0).getAsDouble());
				int green = colorChannel(element.getAsJsonArray().get(1).getAsDouble());
				int blue = colorChannel(element.getAsJsonArray().get(2).getAsDouble());
				return (red << 16) | (green << 8) | blue;
			} catch (RuntimeException ignored) {
				return fallback;
			}
		}
		return fallback;
	}

	private static int colorChannel(double value) {
		double normalized = value <= 1.0D ? value * 255.0D : value;
		return (int) Math.round(Math.max(0.0D, Math.min(255.0D, normalized)));
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		if (root == null || key == null) return fallback;
		JsonElement value = root.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
		try {
			return value.getAsDouble();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static float clampScale(double value) {
		return (float) Math.max(0.01D, Math.min(4.0D, value));
	}

	private static String formatColor(float red, float green, float blue) {
		int color = (colorChannel(red) << 16) | (colorChannel(green) << 8) | colorChannel(blue);
		return String.format(Locale.ROOT, "#%06X", color);
	}

	private static Identifier parseTexture(String raw) {
		if (raw.isBlank()) {
			return null;
		}
		Identifier parsed = Identifier.tryParse(raw);
		if (parsed == null) {
			return null;
		}
		String path = parsed.getPath();
		if (!path.startsWith("textures/")) {
			path = "textures/" + path;
		}
		if (!path.endsWith(".png")) {
			path += ".png";
		}
		return Identifier.fromNamespaceAndPath(parsed.getNamespace(), path);
	}

	private static String readString(JsonObject root, String key) {
		if (root == null || key == null) {
			return "";
		}
		JsonElement value = root.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
			? value.getAsString().trim()
			: "";
	}

	private static String key(String mobId, String variantKey) {
		return mobId.trim().toLowerCase(Locale.ROOT) + "|" + variantKey.trim().toLowerCase(Locale.ROOT);
	}

	public record VariantAppearance(Identifier texture, Identifier eyes, ParticleOptions particles) {
		private static final VariantAppearance EMPTY = new VariantAppearance(null, null, null);

		public boolean isEmpty() {
			return texture == null && eyes == null && particles == null;
		}
	}
}
