package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Piglin.class)
public abstract class PiglinCrossbowAttackMixin {
	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyCustomCrossbowAttack(LivingEntity target, float pullProgress, CallbackInfo callbackInfo) {
		Piglin piglin = (Piglin) (Object) this;
		if (EntityBehaviorsManager.PiglinBehavior.applyRangedPiglinCrossbowAttack(piglin, target)) {
			callbackInfo.cancel();
		}
	}
}
