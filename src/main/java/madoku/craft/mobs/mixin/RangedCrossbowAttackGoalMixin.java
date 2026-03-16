package madoku.craft.mobs.mixin;

import madoku.craft.mobs.mob.system.MadokuMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RangedCrossbowAttackGoal.class)
public abstract class RangedCrossbowAttackGoalMixin {
	@Shadow
	@Final
	private Monster mob;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyPillagerPostShotCooldown(CallbackInfo ci) {
		if (MadokuMob.tickPillagerAttackCooldown(mob)) {
			ci.cancel();
		}
	}

	@Redirect(
		method = "tick",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/entity/ai/goal/RangedCrossbowAttackGoal;attackDelay:I",
			opcode = Opcodes.PUTFIELD,
			ordinal = 0
		)
	)
	private void madokuCraft$reducePillagerPostChargeDelay(RangedCrossbowAttackGoal<?> goal, int vanillaDelay) {
		int resolvedDelay = MadokuMob.resolveCrossbowPostChargeDelay(mob, vanillaDelay);
		((RangedCrossbowAttackGoalAccessor) goal).madokuCraft$setAttackDelay(resolvedDelay);
	}

	@Redirect(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/RangedAttackMob;performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V"
		)
	)
	private void madokuCraft$startPillagerCooldownAfterShot(
		RangedAttackMob rangedAttackMob,
		LivingEntity target,
		float pullProgress
	) {
		rangedAttackMob.performRangedAttack(target, pullProgress);
		MadokuMob.markPillagerAttackCooldownFromShot(mob);
	}
}
