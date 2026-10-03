package madoku.craft.java.mob;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

final class MobIndicatorNetworking {
	private MobIndicatorNetworking() {
	}

	static boolean send(ServerPlayer player, MobIndicatorPayloadManager payload) {
		if (player == null || payload == null || !ServerPlayNetworking.canSend(player, payload.type())) return false;
		ServerPlayNetworking.send(player, payload);
		return true;
	}
}
