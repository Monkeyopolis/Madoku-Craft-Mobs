package madoku.craft.mobs.mixin;

import madoku.craft.mobs.mob.system.MadokuMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
public abstract class CrossbowChargeDurationMixin {
	@Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$applyPillagerChargeUpOverride(
		ItemStack stack,
		LivingEntity user,
		CallbackInfoReturnable<Integer> cir
	) {
		int chargeUpTicks = MadokuMob.resolveCrossbowChargeDurationOverride(user);
		if (chargeUpTicks > 0) {
			cir.setReturnValue(chargeUpTicks);
		}
	}
}

