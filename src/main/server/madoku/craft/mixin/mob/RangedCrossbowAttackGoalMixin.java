package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;
import madoku.craft.java.mob.EntityGoalsManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RangedCrossbowAttackGoal.class)
public abstract class RangedCrossbowAttackGoalMixin {
	@Inject(
		method = "tick",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/entity/ai/goal/RangedCrossbowAttackGoal;attackDelay:I",
			opcode = Opcodes.PUTFIELD,
			ordinal = 0,
			shift = At.Shift.AFTER
		)
	)
	private void madokuCraft$applyConfiguredAttackInterval(CallbackInfo callbackInfo) {
		Mob mob = EntityGoalsManager.resolveOwningMob((Goal) (Object) this);
		int interval = EntityBehaviorsManager.PiglinBehavior.resolveCrossbowAttackIntervalTicks(mob);
		if (interval > 0) {
			((RangedCrossbowAttackGoalAccessor) (Object) this).madokuCraft$setAttackDelay(interval);
		}
	}
}
