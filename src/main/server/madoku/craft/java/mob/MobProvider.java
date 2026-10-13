package madoku.craft.java.mob;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Public provider contract for the Madoku Mobs subsystem. */
public interface MobProvider {
	default void initialize() { }
	default void onServerStarted(MinecraftServer server) { }
	default void onServerTick(MinecraftServer server) { }
	default void onServerStopped() { }
	default boolean isEnabled() { return false; }
	default boolean isBeeCustomMobDropsEnabled(LivingEntity entity) { return false; }
	default String resolveBeeMobDropsConfigReference(LivingEntity entity) { return ""; }
	default boolean isZombieCustomMobDropsEnabled(LivingEntity entity) { return false; }
	default String resolveZombieMobDropsConfigReference(LivingEntity entity) { return ""; }
	default boolean isPiglinCustomMobDropsEnabled(LivingEntity entity) { return false; }
	default String resolvePiglinMobDropsConfigReference(LivingEntity entity) { return ""; }
	default boolean shouldSkeletonMeleeIgnoreArmor(DamageSource source) { return false; }
	default boolean shouldBypassArmorForMobDamage(DamageSource source) { return false; }
	default void broadcastDifficultyIfChanged(MinecraftServer server) { }
}
