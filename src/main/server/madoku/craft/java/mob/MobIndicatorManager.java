package madoku.craft.java.mob;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import madoku.craft.java.core.damage.DamageVulnerabilityFeatureAPIManager;
import madoku.craft.java.core.runtime.AdaptiveIntervalAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/** Tracks server-authoritative state displayed by the mob indicator above managed entities. */
final class MobIndicatorManager {
	private static final Map<UUID, LivingEntity> TRACKED_ENTITIES = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> LAST_DAMAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> LAST_VULNERABILITY = new ConcurrentHashMap<>();
	private static final String ADAPTIVE_INTERVAL_ID = "madoku-mob-indicators";
	private static final long MIN_SCAN_INTERVAL_TICKS = 1L;
	private static final long MAX_SCAN_INTERVAL_TICKS = 4L;
	private static final double INDICATOR_TRACK_DISTANCE = 32.0D;
	private static final long FULL_RECONCILIATION_INTERVAL_TICKS = 100L;
	private static final long DAMAGE_CACHE_TTL_TICKS = 4L;
	private static long nextScanTick = Long.MIN_VALUE;
	private static long nextFullReconciliationTick = Long.MIN_VALUE;
	private static boolean initialized;
	private static Boolean lastBroadcastSetting;
	private static final Map<UUID, CachedIndicatorDamage> DAMAGE_CACHE = new ConcurrentHashMap<>();
	private static final Set<UUID> DIRTY_ENTITIES = ConcurrentHashMap.newKeySet();

	private MobIndicatorManager() {
	}

	static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (isManagedMob(entity)) {
				DIRTY_ENTITIES.add(entity.getUUID());
				DAMAGE_CACHE.remove(entity.getUUID());
				nextScanTick = Long.MIN_VALUE;
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (!isManagedMob(entity)) return;
			TRACKED_ENTITIES.remove(entity.getUUID());
			DIRTY_ENTITIES.remove(entity.getUUID());
			DAMAGE_CACHE.remove(entity.getUUID());
			boolean hadState = LAST_DAMAGE.remove(entity.getUUID()) != null;
			hadState |= LAST_VULNERABILITY.remove(entity.getUUID()) != null;
			if (hadState && world != null) {
				broadcast(world.getServer(), entity.getUUID(), 0.0F, 0.0F);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			boolean enabled = MobConfigManager.isMobIndicatorEnabled();
			MobIndicatorNetworking.sendSettings(handler.player, enabled);
			if (enabled) {
				sendActiveStates(handler.player);
			}
		});
	}

	static void onServerStarted(MinecraftServer server) {
		TRACKED_ENTITIES.clear();
		LAST_DAMAGE.clear();
		LAST_VULNERABILITY.clear();
		DAMAGE_CACHE.clear();
		DIRTY_ENTITIES.clear();
		AdaptiveIntervalAPIManager.clearSystem(ADAPTIVE_INTERVAL_ID);
		nextScanTick = Long.MIN_VALUE;
		nextFullReconciliationTick = Long.MIN_VALUE;
		lastBroadcastSetting = null;
	}

	static void onServerTick(MinecraftServer server) {
		if (server == null) return;
		boolean enabled = MobConfigManager.isMobIndicatorEnabled();
		if (lastBroadcastSetting == null || lastBroadcastSetting.booleanValue() != enabled) {
			lastBroadcastSetting = enabled;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				MobIndicatorNetworking.sendSettings(player, enabled);
			}
		}
		if (!enabled) {
			for (UUID entityUuid : activeIndicatorEntities()) clear(server, entityUuid);
			nextScanTick = Long.MIN_VALUE;
			DIRTY_ENTITIES.clear();
			DAMAGE_CACHE.clear();
			return;
		}
		if (server.getPlayerList().getPlayers().isEmpty()) {
			clearNearbyEntities(server);
			nextScanTick = Long.MIN_VALUE;
			return;
		}

		long now = Math.max(0L, TimeAPIManager.getGameplayTicks());
		if (nextScanTick != Long.MIN_VALUE && now < nextScanTick) return;
		long interval = AdaptiveIntervalAPIManager.resolve(
			ADAPTIVE_INTERVAL_ID,
			server,
			MIN_SCAN_INTERVAL_TICKS,
			MAX_SCAN_INTERVAL_TICKS
		);
		nextScanTick = now + Math.max(1L, interval);
		scanEntities(server, now);
	}

	private static void scanEntities(MinecraftServer server, long now) {
		refreshNearbyEntities(server, now);
		Set<UUID> dirtyEntities = new HashSet<>(DIRTY_ENTITIES);
		for (UUID entityUuid : dirtyEntities) {
			DIRTY_ENTITIES.remove(entityUuid);
			LivingEntity entity = TRACKED_ENTITIES.get(entityUuid);
			if (entity != null) {
				tickEntity(server, entityUuid, entity, now);
			}
		}
	}

	private static void tickEntity(
		MinecraftServer server,
		UUID entityUuid,
		LivingEntity entity,
		long now
	) {
		if (!isManagedMob(entity) || !entity.isAlive()) {
			TRACKED_ENTITIES.remove(entityUuid, entity);
			clear(server, entityUuid);
			return;
		}

		float damage = resolveCachedIndicatorDamage((Mob) entity, now);

		float vulnerabilityPercent = EnchantBooksAPIManager.getConfiguredSmiteVulnerabilityPercent(entity)
			+ DamageVulnerabilityFeatureAPIManager.getDamageVulnerabilityPercent(entity);

		Float previousDamage = LAST_DAMAGE.get(entityUuid);
		Float previousVulnerability = LAST_VULNERABILITY.get(entityUuid);
		if (damage <= 0.0F && vulnerabilityPercent <= 0.0F) {
			if (previousDamage != null || previousVulnerability != null) clear(server, entityUuid);
			return;
		}

		boolean damageChanged = previousDamage == null || Math.abs(previousDamage - damage) > 0.01F;
		boolean vulnerabilityChanged = previousVulnerability == null
			|| Math.abs(previousVulnerability - vulnerabilityPercent) > 0.01F;
		if (damageChanged || vulnerabilityChanged) {
			if (damage <= 0.0F) LAST_DAMAGE.remove(entityUuid);
			else LAST_DAMAGE.put(entityUuid, damage);
			if (vulnerabilityPercent <= 0.0F) LAST_VULNERABILITY.remove(entityUuid);
			else LAST_VULNERABILITY.put(entityUuid, vulnerabilityPercent);
			broadcast(server, entityUuid, damage, vulnerabilityPercent);
		}
	}

	private static void refreshNearbyEntities(MinecraftServer server, long now) {
		Map<UUID, LivingEntity> nearbyEntities = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			AABB trackingArea = player.getBoundingBox().inflate(INDICATOR_TRACK_DISTANCE);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, trackingArea, MobIndicatorManager::isManagedMob)) {
				nearbyEntities.put(mob.getUUID(), mob);
			}
		}

		for (Map.Entry<UUID, LivingEntity> entry : new HashMap<>(TRACKED_ENTITIES).entrySet()) {
			if (nearbyEntities.containsKey(entry.getKey())) {
				continue;
			}
			if (TRACKED_ENTITIES.remove(entry.getKey(), entry.getValue())) {
				DIRTY_ENTITIES.remove(entry.getKey());
				DAMAGE_CACHE.remove(entry.getKey());
				clear(server, entry.getKey());
			}
		}

		for (Map.Entry<UUID, LivingEntity> entry : nearbyEntities.entrySet()) {
			LivingEntity previous = TRACKED_ENTITIES.put(entry.getKey(), entry.getValue());
			if (previous == null || previous != entry.getValue()) {
				DIRTY_ENTITIES.add(entry.getKey());
				DAMAGE_CACHE.remove(entry.getKey());
			}
		}

		if (nextFullReconciliationTick == Long.MIN_VALUE || now >= nextFullReconciliationTick) {
			DIRTY_ENTITIES.addAll(nearbyEntities.keySet());
			nextFullReconciliationTick = now + FULL_RECONCILIATION_INTERVAL_TICKS;
		}
	}

	private static void clearNearbyEntities(MinecraftServer server) {
		for (UUID entityUuid : new HashSet<>(TRACKED_ENTITIES.keySet())) {
			TRACKED_ENTITIES.remove(entityUuid);
			DIRTY_ENTITIES.remove(entityUuid);
			DAMAGE_CACHE.remove(entityUuid);
			clear(server, entityUuid);
		}
		DAMAGE_CACHE.clear();
	}

	static void markDirty(LivingEntity entity) {
		if (!MobConfigManager.isMobIndicatorEnabled() || !isManagedMob(entity)) {
			return;
		}
		UUID entityUuid = entity.getUUID();
		if (TRACKED_ENTITIES.containsKey(entityUuid)) {
			DAMAGE_CACHE.remove(entityUuid);
			DIRTY_ENTITIES.add(entityUuid);
		}
	}

	private static float resolveCachedIndicatorDamage(Mob mob, long now) {
		UUID entityUuid = mob.getUUID();
		CachedIndicatorDamage cached = DAMAGE_CACHE.get(entityUuid);
		if (cached != null && now < cached.expiresAtTick()) {
			return cached.damage();
		}

		float damage = MobEntityManager.resolveMobIndicatorDamage(mob);
		DAMAGE_CACHE.put(
			entityUuid,
			new CachedIndicatorDamage(damage, now + DAMAGE_CACHE_TTL_TICKS)
		);
		return damage;
	}

	private record CachedIndicatorDamage(float damage, long expiresAtTick) {
	}

	static void onServerStopped() {
		TRACKED_ENTITIES.clear();
		LAST_DAMAGE.clear();
		LAST_VULNERABILITY.clear();
		DAMAGE_CACHE.clear();
		DIRTY_ENTITIES.clear();
		AdaptiveIntervalAPIManager.clearSystem(ADAPTIVE_INTERVAL_ID);
		nextScanTick = Long.MIN_VALUE;
		nextFullReconciliationTick = Long.MIN_VALUE;
		lastBroadcastSetting = null;
	}

	private static boolean isManagedMob(Entity entity) {
		return entity instanceof Mob && !(entity instanceof ServerPlayer)
			&& !MobFeatureAPIManager.isManagedPet(entity);
	}

	private static void clear(MinecraftServer server, UUID entityUuid) {
		boolean hadState = LAST_DAMAGE.remove(entityUuid) != null;
		hadState |= LAST_VULNERABILITY.remove(entityUuid) != null;
		if (hadState) {
			broadcast(server, entityUuid, 0.0F, 0.0F);
		}
	}

	private static Set<UUID> activeIndicatorEntities() {
		Set<UUID> entityUuids = new HashSet<>(LAST_DAMAGE.keySet());
		entityUuids.addAll(LAST_VULNERABILITY.keySet());
		return entityUuids;
	}

	private static void broadcast(MinecraftServer server, UUID entityUuid, float damage, float vulnerabilityPercent) {
		if (server == null || entityUuid == null) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			MobIndicatorNetworking.send(player, new MobIndicatorPayloadManager(entityUuid, damage, vulnerabilityPercent));
		}
	}

	private static void sendActiveStates(ServerPlayer player) {
		if (player == null || !MobConfigManager.isMobIndicatorEnabled()) return;
		for (Map.Entry<UUID, LivingEntity> entry : TRACKED_ENTITIES.entrySet()) {
			if (!(entry.getValue() instanceof Mob mob) || !isManagedMob(mob)) continue;
			Float cachedDamage = LAST_DAMAGE.get(entry.getKey());
			float damage = cachedDamage != null
				? cachedDamage
				: resolveCachedIndicatorDamage(mob, Math.max(0L, TimeAPIManager.getGameplayTicks()));
			float vulnerability = LAST_VULNERABILITY.getOrDefault(entry.getKey(), 0.0F);
			if (damage <= 0.0F && vulnerability <= 0.0F) continue;
			MobIndicatorNetworking.send(player, new MobIndicatorPayloadManager(entry.getKey(), damage, vulnerability));
		}
	}
}
