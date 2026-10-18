package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
	@Inject(method = "getChargeDuration", at = @At("HEAD"), cancellable = true)
	private static void madokuCraft$applyConfiguredChargeDuration(
		ItemStack crossbow,
		LivingEntity user,
		CallbackInfoReturnable<Integer> callbackInfo
	) {
		int configuredDuration = EntityBehaviorsManager.PiglinBehavior.resolveCrossbowChargeUpTicks(user, -1);
		if (configuredDuration > 0) {
			callbackInfo.setReturnValue(configuredDuration);
		}
	}
}
