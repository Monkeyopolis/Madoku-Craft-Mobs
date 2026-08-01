package madoku.craft.mobs.mixin;

import madoku.craft.mobs.mob.MobEntityManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ThrownTrident.class)
public abstract class ThrownTridentDamageMixin {
	@Redirect(
		method = "onHitEntity",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	@SuppressWarnings("deprecation")
	private boolean madokuCraft$applyFixedTridentDamage(Entity entity, DamageSource source, float originalDamage) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		float resolvedDamage = MobEntityManager.resolveProjectileDamageOverride(trident, originalDamage);
		boolean hit = entity.hurtOrSimulate(source, resolvedDamage);
		if (hit) {
			MobEntityManager.clearProjectileHoming(trident);
		}
		MobEntityManager.clearInvulnerabilityBypass(trident);
		if (hit && entity instanceof LivingEntity livingEntity) {
			MobEntityManager.applyWitherSkeletonArrowHitEffect(livingEntity, trident.getOwner());
		}
		return hit;
	}
}



