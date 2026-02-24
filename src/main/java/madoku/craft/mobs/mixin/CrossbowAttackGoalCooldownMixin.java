package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.PillagerMobSystem;
import net.minecraft.entity.ai.goal.CrossbowAttackGoal;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.mob.HostileEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrossbowAttackGoal.class)
public abstract class CrossbowAttackGoalCooldownMixin {
	@Shadow
	@Final
	private HostileEntity actor;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void madokuCraftMobs$applyPillagerPostShotCooldown(CallbackInfo ci) {
		if (PillagerMobSystem.tickAttackCooldown(actor)) {
			ci.cancel();
		}
	}

	@Redirect(
		method = "tick",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/entity/ai/goal/CrossbowAttackGoal;chargedTicksLeft:I",
			opcode = Opcodes.PUTFIELD,
			ordinal = 0
		)
	)
	private void madokuCraftMobs$reducePillagerPostChargeDelay(CrossbowAttackGoal<?> goal, int vanillaChargedTicks) {
		int chargeUpTicks = PillagerMobSystem.resolveChargeUpTicks(actor);
		int resolvedChargedDelay = chargeUpTicks > 0 ? 1 : vanillaChargedTicks;
		((CrossbowAttackGoalAccessor) goal).madokuCraftMobs$setChargedTicksLeft(resolvedChargedDelay);
	}

	@Redirect(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/ai/RangedAttackMob;shootAt(Lnet/minecraft/entity/LivingEntity;F)V"
		)
	)
	private void madokuCraftMobs$startPillagerCooldownAfterShot(
		RangedAttackMob rangedAttackMob,
		LivingEntity target,
		float pullProgress
	) {
		rangedAttackMob.shootAt(target, pullProgress);
		PillagerMobSystem.markAttackCooldownFromShot(actor);
	}
}
