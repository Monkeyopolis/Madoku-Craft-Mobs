package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobVariantAppearanceManager;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobVariantParticlesMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void madokuCraft$emitVariantParticles(CallbackInfo callbackInfo) {
		MobVariantAppearanceManager.emitConfiguredParticles((Mob) (Object) this);
	}
}
