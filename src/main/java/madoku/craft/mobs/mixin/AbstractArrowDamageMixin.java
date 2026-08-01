package madoku.craft.mobs.mixin;

import madoku.craft.mobs.mob.MobEntityManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowDamageMixin {
	@Shadow
	protected abstract void doKnockback(LivingEntity target, DamageSource source);

	@Redirect(
		method = "onHitEntity",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	@SuppressWarnings("deprecation")
	private boolean madokuCraft$applyFixedArrowDamage(Entity entity, DamageSource source, float originalDamage) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;
		if (MobEntityManager.shouldBypassInvulnerability(arrow) && entity instanceof LivingEntity livingEntity) {
			livingEntity.invulnerableTime = 0;
			livingEntity.hurtTime = 0;
		}
		float resolvedDamage = MobEntityManager.resolveProjectileDamageOverride(arrow, originalDamage);
		boolean hit = entity.hurtOrSimulate(source, resolvedDamage);
		if (hit && MobEntityManager.isManagedHomingArrow(arrow)) {
			MobEntityManager.clearProjectileHoming(arrow);
		}
		MobEntityManager.clearInvulnerabilityBypass(arrow);
		if (hit && entity instanceof LivingEntity livingEntity) {
			MobEntityManager.applySkeletonArrowHitEffect(livingEntity, arrow.getOwner());
		}
		return hit;
	}

	@Redirect(
		method = "onHitEntity",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;doKnockback(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)V"
		)
	)
	private void madokuCraft$skipHomingArrowKnockback(AbstractArrow arrow, LivingEntity target, DamageSource source) {
		if (!MobEntityManager.isManagedHomingArrow(arrow)) {
			this.doKnockback(target, source);
		}
	}
}

