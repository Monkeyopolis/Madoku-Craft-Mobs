package madoku.craft.mobs.mixin;

import madoku.craft.mobs.mob.system.MadokuMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Pillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Pillager.class)
public abstract class PillagerRangedAttackMixin {
	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyCustomRangedAttack(LivingEntity target, float pullProgress, CallbackInfo ci) {
		Pillager pillager = (Pillager) (Object) this;
		if (MadokuMob.applyCustomPillagerRangedShot(pillager, target, 1.6F)) {
			ci.cancel();
		}
	}
}
