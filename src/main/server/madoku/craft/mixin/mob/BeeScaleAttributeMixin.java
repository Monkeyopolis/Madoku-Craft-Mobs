package madoku.craft.mixin.mob;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.bee.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Bee.class)
public abstract class BeeScaleAttributeMixin {
	@Inject(method = "createAttributes", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$addBeeScaleAttribute(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
		AttributeSupplier.Builder builder = cir.getReturnValue();
		if (builder != null) {
			cir.setReturnValue(builder.add(Attributes.SCALE, 1.0D));
		}
	}
}
