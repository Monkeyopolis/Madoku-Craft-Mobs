package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.PillagerMobSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
public abstract class CrossbowItemPullTimeMixin {
	@Inject(method = "getPullTime", at = @At("RETURN"), cancellable = true)
	private static void madokuCraftMobs$applyPillagerChargeUpOverride(
		ItemStack stack,
		LivingEntity user,
		CallbackInfoReturnable<Integer> cir
	) {
		if (!(user instanceof HostileEntity hostileEntity)) {
			return;
		}
		int chargeUpTicks = PillagerMobSystem.resolveChargeUpTicks(hostileEntity);
		if (chargeUpTicks > 0) {
			cir.setReturnValue(chargeUpTicks);
		}
	}
}
