package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.PillagerMobSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CrossbowItem.class)
public abstract class CrossbowItemShootVelocityMixin {
	@Redirect(
		method = "shoot",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/projectile/ProjectileEntity;setVelocity(DDDFF)V"
		)
	)
	private void madokuCraftMobs$applyPillagerBinaryAccuracy(
		ProjectileEntity projectile,
		double velocityX,
		double velocityY,
		double velocityZ,
		float speed,
		float divergence,
		LivingEntity shooter,
		ProjectileEntity projectileArg,
		int shotIndex,
		float speedArg,
		float divergenceArg,
		float simulated,
		LivingEntity target
	) {
		if (!PillagerMobSystem.applyProjectileAccuracyOverride(
			projectile,
			shooter,
			target,
			velocityX,
			velocityY,
			velocityZ,
			speed
		)) {
			projectile.setVelocity(velocityX, velocityY, velocityZ, speed, divergence);
		}
	}
}
