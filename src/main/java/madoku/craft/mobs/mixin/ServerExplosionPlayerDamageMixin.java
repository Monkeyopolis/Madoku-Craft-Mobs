package madoku.craft.mobs.mixin;

import madoku.craft.mobs.mob.system.MadokuMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Explosion.class)
public abstract class ServerExplosionPlayerDamageMixin {
	@Shadow
	@Final
	private Entity source;

	@Shadow
	@Final
	private float radius;

	@Redirect(
		method = "explode",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/ExplosionDamageCalculator;getEntityDamageAmount(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/entity/Entity;)F"
		)
	)
	private float madokuCraft$applyFixedCreeperPlayerDamage(
		ExplosionDamageCalculator calculator,
		Explosion explosion,
		Entity damagedEntity
	) {
		if (source instanceof Creeper creeper && damagedEntity instanceof Player) {
			return MadokuMob.resolveFixedPlayerExplosionDamage(creeper, radius);
		}
		return calculator.getEntityDamageAmount(explosion, damagedEntity);
	}
}

