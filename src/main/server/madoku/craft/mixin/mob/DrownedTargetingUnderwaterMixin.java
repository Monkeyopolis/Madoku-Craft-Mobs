package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Drowned.class)
public abstract class DrownedTargetingUnderwaterMixin {
	@Inject(method = "okTarget", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyTargetingUnderwaterToggle(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		Drowned drowned = (Drowned) (Object) this;
		if (!EntityBehaviorsManager.DrownedBehavior.shouldAllowUnderwaterTargeting(drowned, target)) {
			cir.setReturnValue(false);
		}
	}
}
