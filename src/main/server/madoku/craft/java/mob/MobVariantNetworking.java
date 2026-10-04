package madoku.craft.java.mob;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.spider.Spider;

final class MobVariantNetworking {
	private MobVariantNetworking() {
	}

	static boolean send(ServerPlayer player, MobVariantPayloadManager payload) {
		if (player == null || payload == null || !ServerPlayNetworking.canSend(player, payload.type())) {
			return false;
		}
		ServerPlayNetworking.send(player, payload);
		return true;
	}

	static void broadcast(MinecraftServer server, Spider spider) {
		if (server == null || spider == null) {
			return;
		}
		String variantKey = MobEntityManager.resolveConfiguredVariantKeyForRuntime(spider);
		broadcast(server, spider.getUUID(), variantKey);
	}

	static void broadcast(MinecraftServer server, Entity entity, String variantKey) {
		if (entity == null) {
			return;
		}
		broadcast(server, entity.getUUID(), variantKey);
	}

	static void broadcast(MinecraftServer server, java.util.UUID entityUuid, String variantKey) {
		if (server == null || entityUuid == null) {
			return;
		}
		MobVariantPayloadManager payload = new MobVariantPayloadManager(
			entityUuid,
			variantKey == null ? "" : variantKey
		);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player, payload);
		}
	}

	static void sendActiveStates(ServerPlayer player, MinecraftServer server) {
		if (player == null || server == null) {
			return;
		}
		for (var level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof Spider spider) {
					String variantKey = MobEntityManager.resolveConfiguredVariantKeyForRuntime(spider);
					if (!variantKey.isBlank()) {
						send(player, new MobVariantPayloadManager(spider.getUUID(), variantKey));
					}
				}
			}
		}
	}
}
