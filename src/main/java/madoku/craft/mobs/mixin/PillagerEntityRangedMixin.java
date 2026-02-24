package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.PillagerMobSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PillagerEntity.class)
public abstract class PillagerEntityRangedMixin {
	@Inject(method = "shootAt", at = @At("HEAD"), cancellable = true)
	private void madokuCraftMobs$applyCustomRangedShot(LivingEntity target, float pullProgress, CallbackInfo ci) {
		PillagerEntity pillager = (PillagerEntity) (Object) this;
		if (PillagerMobSystem.applyCustomRangedShot(pillager, target, 1.6f)) {
			ci.cancel();
		}
	}
}
