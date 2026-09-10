package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Creeper.class)
public abstract class CreeperExplosionMixin {
	@Redirect(
		method = "explodeCreeper",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V"
		)
	)
	private void madokuCraft$applyExplosionOverride(
		ServerLevel level,
		Entity source,
		double x,
		double y,
		double z,
		float power,
		Level.ExplosionInteraction interaction
	) {
		EntityBehaviorsManager.CreeperBehavior.applyExplosionOverride((Creeper) (Object) this, level, source, x, y, z, power, interaction);
	}
}
