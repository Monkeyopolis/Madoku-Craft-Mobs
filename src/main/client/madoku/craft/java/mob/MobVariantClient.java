package madoku.craft.java.mob;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Stores server-authoritative mob appearance variants for client rendering. */
public final class MobVariantClient {
	private static final Map<UUID, String> VARIANT_KEYS = new ConcurrentHashMap<>();
	private static boolean initialized;

	private MobVariantClient() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ClientPlayNetworking.registerGlobalReceiver(MobVariantPayloadManager.TYPE, (payload, context) -> {
			if (payload.entityUuid() == null) {
				return;
			}
			String variantKey = payload.variantKey() == null ? "" : payload.variantKey().trim().toLowerCase(Locale.ROOT);
			if (variantKey.isBlank()) {
				VARIANT_KEYS.remove(payload.entityUuid());
			} else {
				VARIANT_KEYS.put(payload.entityUuid(), variantKey);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> VARIANT_KEYS.clear());
	}

	public static String getVariantKey(UUID entityUuid) {
		return entityUuid == null ? "" : VARIANT_KEYS.getOrDefault(entityUuid, "");
	}
}
