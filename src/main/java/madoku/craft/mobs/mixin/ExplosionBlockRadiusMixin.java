package madoku.craft.mobs.mixin;

import madoku.craft.mobs.mob.system.MadokuMob;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Explosion.class)
public abstract class ExplosionBlockRadiusMixin {
	@Shadow
	@Final
	private float radius;

	@Redirect(
		method = "explode",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/level/Explosion;radius:F",
			ordinal = 0
		)
	)
	private float madokuCraft$useGriefOnlyRadius(Explosion explosion) {
		return MadokuMob.resolveCreeperGriefExplosionRadius((Explosion) (Object) this, radius);
	}
}
