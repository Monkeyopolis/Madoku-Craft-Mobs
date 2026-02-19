package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.SkeletonMobSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.BowAttackGoal;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSkeletonEntity.class)
public abstract class AbstractSkeletonEntityRangedMixin {
	@Shadow
	@Final
	private BowAttackGoal<AbstractSkeletonEntity> bowAttackGoal;

	@Inject(method = "shootAt", at = @At("HEAD"), cancellable = true)
	private void madokuCraftMobs$applyCustomRangedShot(LivingEntity target, float pullProgress, CallbackInfo ci) {
		AbstractSkeletonEntity skeleton = (AbstractSkeletonEntity) (Object) this;
		if (SkeletonMobSystem.applyCustomRangedShot(skeleton, target, pullProgress)) {
			ci.cancel();
		}
	}

	@Inject(method = "updateAttackType", at = @At("TAIL"))
	private void madokuCraftMobs$applyCustomAttackInterval(CallbackInfo ci) {
		AbstractSkeletonEntity skeleton = (AbstractSkeletonEntity) (Object) this;
		int intervalTicks = SkeletonMobSystem.resolveRangedAttackIntervalTicks(skeleton);
		if (intervalTicks > 0 && bowAttackGoal != null) {
			bowAttackGoal.setAttackInterval(intervalTicks);
		}
	}
}
