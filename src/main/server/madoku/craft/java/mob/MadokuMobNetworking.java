package madoku.craft.java.mob;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Registers the Mobs module's own network payloads. */
final class MadokuMobNetworking {
	private static boolean initialized;

	private MadokuMobNetworking() {
	}

	static void initialize() {
		if (initialized) return;
		PayloadTypeRegistry.clientboundPlay().register(MobPayloadManager.TYPE, MobPayloadManager.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MobIndicatorPayloadManager.TYPE, MobIndicatorPayloadManager.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MobVariantPayloadManager.TYPE, MobVariantPayloadManager.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			MobVariantNetworking.sendActiveStates(handler.player, server));
		initialized = true;
	}
}
