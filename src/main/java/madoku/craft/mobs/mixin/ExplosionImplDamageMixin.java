package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.CreeperMobSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ExplosionImpl.class)
public abstract class ExplosionImplDamageMixin {
	@Shadow
	@Final
	private Entity entity;

	@Shadow
	@Final
	private float power;

	@Redirect(
		method = "damageEntities",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/explosion/ExplosionBehavior;calculateDamage(Lnet/minecraft/world/explosion/Explosion;Lnet/minecraft/entity/Entity;F)F"
		)
	)
	private float madokuCraftMobs$applyFixedCreeperDamage(
		ExplosionBehavior behavior,
		Explosion explosion,
		Entity damagedEntity,
		float receivedDamage
	) {
		if (this.entity instanceof CreeperEntity creeper && damagedEntity instanceof PlayerEntity) {
			return CreeperMobSystem.resolveFixedPlayerExplosionDamage(creeper, this.power);
		}
		return behavior.calculateDamage(explosion, damagedEntity, receivedDamage);
	}
}

