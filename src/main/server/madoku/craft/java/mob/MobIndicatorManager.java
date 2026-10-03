package madoku.craft.java.mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import madoku.craft.java.core.damage.DamageVulnerabilityFeatureAPIManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Tracks server-authoritative state displayed by the mob indicator above managed entities. */
final class MobIndicatorManager {
	private static final Map<UUID, LivingEntity> TRACKED_ENTITIES = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> LAST_VULNERABILITY = new ConcurrentHashMap<>();
	private static boolean initialized;

	private MobIndicatorManager() {
	}

	static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (isManagedMob(entity)) {
				TRACKED_ENTITIES.put(entity.getUUID(), (LivingEntity) entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (!isManagedMob(entity)) return;
			TRACKED_ENTITIES.remove(entity.getUUID());
			if (LAST_VULNERABILITY.remove(entity.getUUID()) != null && world != null) {
				broadcast(world.getServer(), entity.getUUID(), 0.0F);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendActiveStates(handler.player));
	}

	static void onServerStarted(MinecraftServer server) {
		TRACKED_ENTITIES.clear();
		LAST_VULNERABILITY.clear();
	}

	static void onServerTick(MinecraftServer server) {
		if (server == null) return;
		if (!MobConfigManager.isEnabled()) {
			for (UUID entityUuid : LAST_VULNERABILITY.keySet()) clear(server, entityUuid);
			return;
		}

		for (Map.Entry<UUID, LivingEntity> entry : TRACKED_ENTITIES.entrySet()) {
			LivingEntity entity = entry.getValue();
			if (!isManagedMob(entity) || !entity.isAlive()) {
				TRACKED_ENTITIES.remove(entry.getKey(), entity);
				clear(server, entry.getKey());
				continue;
			}

			float vulnerabilityPercent = EnchantBooksAPIManager.getConfiguredSmiteVulnerabilityPercent(entity)
				+ DamageVulnerabilityFeatureAPIManager.getDamageVulnerabilityPercent(entity);
			Float previous = LAST_VULNERABILITY.get(entry.getKey());
			if (vulnerabilityPercent <= 0.0F) {
				if (previous != null) clear(server, entry.getKey());
				continue;
			}
			if (previous == null || Math.abs(previous - vulnerabilityPercent) > 0.01F) {
				LAST_VULNERABILITY.put(entry.getKey(), vulnerabilityPercent);
				broadcast(server, entry.getKey(), vulnerabilityPercent);
			}
		}
	}

	static void onServerStopped() {
		TRACKED_ENTITIES.clear();
		LAST_VULNERABILITY.clear();
	}

	private static boolean isManagedMob(Entity entity) {
		return entity instanceof Mob && !(entity instanceof ServerPlayer)
			&& !MobFeatureAPIManager.isManagedPet(entity);
	}

	private static void clear(MinecraftServer server, UUID entityUuid) {
		if (LAST_VULNERABILITY.remove(entityUuid) != null) {
			broadcast(server, entityUuid, 0.0F);
		}
	}

	private static void broadcast(MinecraftServer server, UUID entityUuid, float vulnerabilityPercent) {
		if (server == null || entityUuid == null) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			MobIndicatorNetworking.send(player, new MobIndicatorPayloadManager(entityUuid, vulnerabilityPercent));
		}
	}

	private static void sendActiveStates(ServerPlayer player) {
		if (player == null) return;
		for (Map.Entry<UUID, Float> entry : LAST_VULNERABILITY.entrySet()) {
			MobIndicatorNetworking.send(player, new MobIndicatorPayloadManager(entry.getKey(), entry.getValue()));
		}
	}
}
