package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobAppearanceRenderManager;
import madoku.craft.java.mob.MobVariantRenderStateAccess;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractZombieRenderer.class)
public abstract class ZombieRendererMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("RETURN"),
		cancellable = true
	)
	private void madokuCraft$useVariantTexture(
		ZombieRenderState state,
		CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!(state instanceof MobVariantRenderStateAccess access)) {
			return;
		}
		Identifier texture = MobAppearanceRenderManager.texture("minecraft:zombie", access.madokuCraft$getVariantKey());
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}
