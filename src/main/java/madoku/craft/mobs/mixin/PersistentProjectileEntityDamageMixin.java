package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.PillagerMobSystem;
import madoku.craft.mobs.system.SkeletonMobSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PersistentProjectileEntity.class)
@SuppressWarnings("deprecation")
public abstract class PersistentProjectileEntityDamageMixin {
	@Redirect(
		method = "onEntityHit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;sidedDamage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
		)
	)
	private boolean madokuCraftMobs$applyFixedSkeletonArrowDamage(Entity entity, DamageSource source, float originalDamage) {
		float resolvedDamage = resolveFixedDamageOverride(originalDamage);
		return entity.sidedDamage(source, resolvedDamage);
	}

	private float resolveFixedDamageOverride(float originalDamage) {
		PersistentProjectileEntity projectile = (PersistentProjectileEntity) (Object) this;
		Float fixedDamage = SkeletonMobSystem.consumeFixedSkeletonArrowDamage(projectile);
		if (fixedDamage != null) {
			return fixedDamage;
		}

		Float fixedPillagerDamage = PillagerMobSystem.resolveFixedProjectileDamage(projectile);
		return fixedPillagerDamage != null ? fixedPillagerDamage : originalDamage;
	}
}
