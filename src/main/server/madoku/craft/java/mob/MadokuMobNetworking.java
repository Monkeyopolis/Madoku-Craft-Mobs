package madoku.craft.java.mob;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the Mobs module's own network payloads. */
final class MadokuMobNetworking {
	private static boolean initialized;

	private MadokuMobNetworking() {
	}

	static void initialize() {
		if (initialized) return;
		PayloadTypeRegistry.playS2C().register(MobPayloadManager.TYPE, MobPayloadManager.CODEC);
		initialized = true;
	}
}
