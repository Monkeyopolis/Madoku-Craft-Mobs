package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowAttack.class)
public abstract class CrossbowAttackMixin {
	@Inject(
		method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$applyConfiguredCrossbowGoal(
		ServerLevel level,
		Mob mob,
		CallbackInfoReturnable<Boolean> callbackInfo
	) {
		if (!EntityBehaviorsManager.PiglinBehavior.shouldAllowCrossbowAttack(mob)) {
			callbackInfo.setReturnValue(false);
		}
	}

	@Redirect(
		method = "crossbowAttack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/CrossbowItem;getChargeDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I"
		)
	)
	private int madokuCraft$applyConfiguredChargeInterval(ItemStack crossbow, LivingEntity attacker) {
		int vanillaCharge = CrossbowItem.getChargeDuration(crossbow, attacker);
		return EntityBehaviorsManager.PiglinBehavior.resolveCrossbowChargeUpTicks(attacker, vanillaCharge);
	}

	@Inject(
		method = "crossbowAttack",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/entity/ai/behavior/CrossbowAttack;attackDelay:I",
			opcode = Opcodes.PUTFIELD,
			ordinal = 0,
			shift = At.Shift.AFTER
		)
	)
	private void madokuCraft$applyConfiguredAttackInterval(Mob mob, LivingEntity target, CallbackInfo callbackInfo) {
		if (mob != null) {
			int interval = mob instanceof Piglin piglin
				? EntityBehaviorsManager.PiglinBehavior.resolveCrossbowAttackIntervalTicks(piglin)
				: -1;
			if (interval > 0) {
				((CrossbowAttackAccessor) (Object) this).madokuCraft$setAttackDelay(interval);
			}
		}
	}
}
