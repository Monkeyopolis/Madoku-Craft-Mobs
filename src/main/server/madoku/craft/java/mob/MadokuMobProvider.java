package madoku.craft.java.mob;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Built-in provider backed by the Madoku Mobs runtime orchestrator. */
public final class MadokuMobProvider implements MobProvider {
	@Override public void initialize() { MadokuMobManager.initialize(); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuMobManager.onServerStarted(server); }
	@Override public void onServerTick(MinecraftServer server) { MadokuMobManager.onServerTick(server); }
	@Override public void onServerStopped() { MadokuMobManager.onServerStopped(); }
	@Override public boolean isEnabled() { return MobEntityManager.isEnabled(); }
	@Override public boolean isBeeCustomMobDropsEnabled(LivingEntity entity) { return MobEntityManager.isBeeCustomMobDropsEnabled(entity); }
	@Override public String resolveBeeMobDropsConfigReference(LivingEntity entity) { return MobEntityManager.resolveBeeMobDropsConfigReference(entity); }
	@Override public boolean isZombieCustomMobDropsEnabled(LivingEntity entity) { return MobEntityManager.isZombieCustomMobDropsEnabled(entity); }
	@Override public String resolveZombieMobDropsConfigReference(LivingEntity entity) { return MobEntityManager.resolveZombieMobDropsConfigReference(entity); }
	@Override public boolean isPiglinCustomMobDropsEnabled(LivingEntity entity) { return MobEntityManager.isPiglinCustomMobDropsEnabled(entity); }
	@Override public String resolvePiglinMobDropsConfigReference(LivingEntity entity) { return MobEntityManager.resolvePiglinMobDropsConfigReference(entity); }
	@Override public boolean shouldSkeletonMeleeIgnoreArmor(DamageSource source) { return MobEntityManager.shouldSkeletonMeleeIgnoreArmor(source); }
	@Override public boolean shouldBypassArmorForMobDamage(DamageSource source) { return MobEntityManager.shouldBypassArmorForMobDamage(source); }
	@Override public void broadcastDifficultyIfChanged(MinecraftServer server) { MadokuMobManager.broadcastDifficultyIfChanged(server); }
}
