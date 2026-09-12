package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;

import net.minecraft.world.entity.animal.bee.Bee;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.animal.bee.Bee$BeeGrowCropGoal")
public abstract class BeeGrowCropGoalDisableMixin {
	@Shadow
	@Final
	private Bee field_20373;

	@Inject(method = "canBeeUse", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableVanillaGrowCropWhenPollinateCropsEnabled(CallbackInfoReturnable<Boolean> cir) {
		if (EntityBehaviorsManager.BeeBehavior.isPollinateCropsEnabled(this.field_20373)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canBeeContinueToUse", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableVanillaGrowCropContinuationWhenPollinateCropsEnabled(CallbackInfoReturnable<Boolean> cir) {
		if (EntityBehaviorsManager.BeeBehavior.isPollinateCropsEnabled(this.field_20373)) {
			cir.setReturnValue(false);
		}
	}
}
