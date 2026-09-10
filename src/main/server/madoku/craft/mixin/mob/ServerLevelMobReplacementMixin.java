package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMobReplacementMixin {
	@Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$replaceConfiguredAlternativeMob(
		Entity entity,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (MobEntityManager.replacePendingEntityBeforeVanillaAdd((ServerLevel) (Object) this, entity)) {
			cir.setReturnValue(true);
		}
	}
}
