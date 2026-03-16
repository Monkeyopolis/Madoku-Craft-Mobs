package madoku.craft.mobs.mixin;

import madoku.craft.mobs.mob.system.MadokuMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CrossbowItem.class)
public abstract class CrossbowProjectileAccuracyMixin {
	@Redirect(
		method = "shootProjectile",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/Projectile;shoot(DDDFF)V"
		)
	)
	private void madokuCraft$applyPillagerProjectileAccuracy(
		Projectile projectile,
		double velocityX,
		double velocityY,
		double velocityZ,
		float speed,
		float divergence,
		LivingEntity shooter,
		Projectile projectileArg,
		int shotIndex,
		float speedArg,
		float divergenceArg,
		float simulated,
		LivingEntity target
	) {
		if (!MadokuMob.applyPillagerProjectileAccuracyOverride(
			projectile,
			shooter,
			target,
			velocityX,
			velocityY,
			velocityZ,
			speed,
			divergence
		)) {
			projectile.shoot(velocityX, velocityY, velocityZ, speed, divergence);
		}
	}
}
