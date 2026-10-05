package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityGoalsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.monster.spider.Spider$SpiderTargetGoal")
public abstract class SpiderTargetGoalMixin {
	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableConfiguredPlayerTargeting(CallbackInfoReturnable<Boolean> callbackInfo) {
		Mob mob = ((TargetGoalAccessor) (Object) this).madokuCraft$getMob();
		Boolean configured = EntityGoalsManager.resolvePlayerTargetGoal(mob);
		if (configured != null && (!configured || !EntityGoalsManager.arePlayerTargetConditionsSatisfied(mob))) {
			callbackInfo.setReturnValue(false);
		}
	}

	@Redirect(
		method = "canUse",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Mob;getLightLevelDependentMagicValue()F"
		)
	)
	private float madokuCraft$allowConfiguredPlayerTargeting(Mob mob) {
		if (Boolean.TRUE.equals(EntityGoalsManager.resolvePlayerTargetGoal(mob))
			&& EntityGoalsManager.arePlayerTargetConditionsSatisfied(mob)) {
			return 0.0F;
		}
		return madokuCraft$resolveVanillaLightValue(mob);
	}

	private static float madokuCraft$resolveVanillaLightValue(Mob mob) {
		BlockPos lightPosition = BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ());
		float brightness = mob.level().getMaxLocalRawBrightness(lightPosition) / 15.0F;
		if (brightness <= 0.0F) {
			return 0.0F;
		}
		float adjustedBrightness = brightness / (4.0F - 3.0F * brightness);
		return Mth.lerp(adjustedBrightness, mob.level().dimensionType().ambientLight(), 1.0F);
	}
}
