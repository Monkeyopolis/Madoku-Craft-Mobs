package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;

import net.minecraft.world.entity.monster.zombie.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Drowned.class)
public abstract class DrownedTravelInWaterSpeedMixin {
	@ModifyArg(
		method = "travelInWater",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/zombie/Drowned;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"
		),
		index = 0
	)
	private float madokuCraft$applySwimmingSpeed(float vanillaSpeed) {
		return (float) EntityBehaviorsManager.DrownedBehavior.resolveSwimmingSpeedForRuntime((Drowned) (Object) this, vanillaSpeed);
	}
}
