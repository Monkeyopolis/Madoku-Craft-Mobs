package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityRelationshipManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobTargetMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyConfiguredRelationship(LivingEntity target, CallbackInfo callbackInfo) {
		if (target != null && !EntityRelationshipManager.canSetTarget((Mob) (Object) this, target)) {
			callbackInfo.cancel();
		}
	}
}
