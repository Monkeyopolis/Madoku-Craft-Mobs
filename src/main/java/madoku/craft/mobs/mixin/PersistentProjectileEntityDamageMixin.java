package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.SkeletonMobSystem;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityDamageMixin {
	@ModifyArg(
		method = "onEntityHit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z"
		),
		index = 2,
		require = 0
	)
	private float madokuCraftMobs$applyFixedSkeletonArrowDamageNew(float originalDamage, EntityHitResult hitResult) {
		return resolveFixedDamageOverride(originalDamage);
	}

	@ModifyArg(
		method = "onEntityHit",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"),
		index = 1,
		require = 0
	)
	private float madokuCraftMobs$applyFixedSkeletonArrowDamageOld(float originalDamage, EntityHitResult hitResult) {
		return resolveFixedDamageOverride(originalDamage);
	}

	private float resolveFixedDamageOverride(float originalDamage) {
		PersistentProjectileEntity projectile = (PersistentProjectileEntity) (Object) this;
		Float fixedDamage = SkeletonMobSystem.consumeFixedSkeletonArrowDamage(projectile);
		return fixedDamage != null ? fixedDamage : originalDamage;
	}
}
