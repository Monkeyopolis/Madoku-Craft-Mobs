package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;

import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RangedBowAttackGoal.class)
public abstract class RangedBowAttackGoalChargeUpMixin {
	@Shadow
	@Final
	private Monster mob;

	@Redirect(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/Monster;getTicksUsingItem()I"
		)
	)
	private int madokuCraft$applySkeletonChargeUpTicks(Monster attacker) {
		int vanillaTicks = attacker.getTicksUsingItem();
		int chargeUpTicks = EntityBehaviorsManager.SkeletonBehavior.resolveBowChargeUpTicks(attacker);
		if (chargeUpTicks <= 0 || chargeUpTicks == 20) {
			return vanillaTicks;
		}
		return (int) Math.floor((vanillaTicks * 20.0D) / chargeUpTicks);
	}
}
