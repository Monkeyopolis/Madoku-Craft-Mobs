package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enderman.class)
public abstract class EndermanRandomTeleportMixin {
	@Inject(method = "teleport()Z", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyConfiguredRandomTeleport(CallbackInfoReturnable<Boolean> callbackInfo) {
		if (!EntityBehaviorsManager.EndermanBehavior.isRandomTeleportEnabled((Enderman) (Object) this)) {
			callbackInfo.setReturnValue(false);
		}
	}
}
