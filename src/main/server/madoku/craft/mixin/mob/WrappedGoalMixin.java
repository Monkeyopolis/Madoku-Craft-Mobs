package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityGoalsManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WrappedGoal.class)
public abstract class WrappedGoalMixin {
	@Shadow @Final private Goal goal;
	@Shadow private boolean isRunning;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyConfiguredCanUse(CallbackInfoReturnable<Boolean> callbackInfo) {
		Mob mob = EntityGoalsManager.resolveOwningMob(goal);
		if (mob != null && !EntityGoalsManager.shouldAllowCanUse(mob, goal)) {
			callbackInfo.setReturnValue(false);
		}
	}

	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyConfiguredCanContinue(CallbackInfoReturnable<Boolean> callbackInfo) {
		Mob mob = EntityGoalsManager.resolveOwningMob(goal);
		if (mob != null && !EntityGoalsManager.shouldAllowCanContinue(mob, goal)) {
			callbackInfo.setReturnValue(false);
		}
	}

	@Inject(method = "getPriority", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$applyConfiguredPriority(CallbackInfoReturnable<Integer> callbackInfo) {
		Mob mob = EntityGoalsManager.resolveOwningMob(goal);
		if (mob != null) {
			callbackInfo.setReturnValue(EntityGoalsManager.resolvePriority(mob, goal, callbackInfo.getReturnValue()));
		}
	}

	@Inject(method = "stop", at = @At("HEAD"))
	private void madokuCraft$applyConfiguredCooldown(CallbackInfo callbackInfo) {
		if (!isRunning) return;
		Mob mob = EntityGoalsManager.resolveOwningMob(goal);
		if (mob != null) EntityGoalsManager.onGoalStopped(mob, goal);
	}
}
