package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Pillager.class)
public abstract class PillagerCrossbowAttackMixin {
	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyCustomCrossbowAttack(LivingEntity target, float pullProgress, CallbackInfo callbackInfo) {
		Pillager pillager = (Pillager) (Object) this;
		if (EntityBehaviorsManager.PiglinBehavior.applyRangedCrossbowAttack(pillager, target)) {
			callbackInfo.cancel();
		}
	}
}
