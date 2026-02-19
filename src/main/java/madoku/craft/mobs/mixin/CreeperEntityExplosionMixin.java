package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.CreeperMobSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CreeperEntity.class)
public abstract class CreeperEntityExplosionMixin {
	@Redirect(
		method = "explode",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/world/ServerWorld;createExplosion(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/world/World$ExplosionSourceType;)V"
		)
	)
	private void madokuCraftMobs$applyExplosionOverride(
		ServerWorld world,
		Entity entity,
		double x,
		double y,
		double z,
		float power,
		World.ExplosionSourceType explosionSourceType
	) {
		CreeperMobSystem.applyExplosionOverride(
			(CreeperEntity) (Object) this,
			world,
			x,
			y,
			z,
			power,
			explosionSourceType
		);
	}
}
