package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.CreeperMobSystem;
import net.minecraft.entity.Entity;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ExplosionImpl.class)
public abstract class ExplosionImplBlockPowerMixin {
	@Shadow
	@Final
	private Entity entity;

	@Shadow
	@Final
	private float power;

	@Redirect(
		method = "getBlocksToDestroy",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/explosion/ExplosionImpl;power:F"
		)
	)
	private float madokuCraftMobs$useGriefOnlyPowerForBlocks(ExplosionImpl instance) {
		return CreeperMobSystem.resolveGriefOnlyExplosionPower(this.entity, this.power);
	}
}

