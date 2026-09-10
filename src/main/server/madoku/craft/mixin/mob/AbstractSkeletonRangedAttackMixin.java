package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonRangedAttackMixin {
	@Shadow
	@Final
	private RangedBowAttackGoal<AbstractSkeleton> bowGoal;

	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyCustomRangedAttack(LivingEntity target, float pullProgress, CallbackInfo ci) {
		AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;
		if (MobEntityManager.applyCustomSkeletonRangedAttack(skeleton, target, pullProgress)) {
			ci.cancel();
		}
	}

	@Inject(method = "reassessWeaponGoal", at = @At("TAIL"))
	private void madokuCraft$applyCustomAttackInterval(CallbackInfo ci) {
		AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;
		int intervalTicks = MobEntityManager.resolveSkeletonRangedAttackIntervalTicks(skeleton);
		if (intervalTicks > 0 && bowGoal != null) {
			bowGoal.setMinAttackInterval(intervalTicks);
		}
	}
}


