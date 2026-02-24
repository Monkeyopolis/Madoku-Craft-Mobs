package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.SkeletonMobSystem;
import net.minecraft.entity.ai.goal.BowAttackGoal;
import net.minecraft.entity.mob.HostileEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(BowAttackGoal.class)
public abstract class BowAttackGoalChargeUpMixin {
	@Shadow
	@Final
	private HostileEntity actor;

	@ModifyConstant(method = "tick", constant = @Constant(intValue = 20))
	private int madokuCraftMobs$applySkeletonChargeUpTicks(int vanillaTicks) {
		int chargeUpTicks = SkeletonMobSystem.resolveChargeUpTicks(actor);
		return chargeUpTicks > 0 ? chargeUpTicks : vanillaTicks;
	}
}
