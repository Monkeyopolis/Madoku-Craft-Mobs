package madoku.craft.java.mob;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Public contract for the Madoku Mobs subsystem. */
public final class MobAPIManager {
	private static final MobProvider UNAVAILABLE_PROVIDER = new MobProvider() { };
	private static volatile MobProvider provider = UNAVAILABLE_PROVIDER;

	private MobAPIManager() { }

	public static void registerProvider(MobProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Mob provider must not be null.");
		provider = candidate;
	}

	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void onServerTick(MinecraftServer server) { provider.onServerTick(server); }
	public static void onServerStopped() { provider.onServerStopped(); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static boolean isBeeCustomMobDropsEnabled(LivingEntity entity) { return provider.isBeeCustomMobDropsEnabled(entity); }
	public static String resolveBeeMobDropsConfigReference(LivingEntity entity) { return provider.resolveBeeMobDropsConfigReference(entity); }
	public static boolean isZombieCustomMobDropsEnabled(LivingEntity entity) { return provider.isZombieCustomMobDropsEnabled(entity); }
	public static String resolveZombieMobDropsConfigReference(LivingEntity entity) { return provider.resolveZombieMobDropsConfigReference(entity); }
	public static boolean isPiglinCustomMobDropsEnabled(LivingEntity entity) { return provider.isPiglinCustomMobDropsEnabled(entity); }
	public static String resolvePiglinMobDropsConfigReference(LivingEntity entity) { return provider.resolvePiglinMobDropsConfigReference(entity); }
	public static boolean isPillagerCustomMobDropsEnabled(LivingEntity entity) { return provider.isPillagerCustomMobDropsEnabled(entity); }
	public static String resolvePillagerMobDropsConfigReference(LivingEntity entity) { return provider.resolvePillagerMobDropsConfigReference(entity); }
	public static boolean shouldSkeletonMeleeIgnoreArmor(DamageSource source) { return provider.shouldSkeletonMeleeIgnoreArmor(source); }
	public static boolean shouldBypassArmorForMobDamage(DamageSource source) { return provider.shouldBypassArmorForMobDamage(source); }
	public static void broadcastDifficultyIfChanged(MinecraftServer server) { provider.broadcastDifficultyIfChanged(server); }
}
