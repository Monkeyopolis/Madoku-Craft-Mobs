package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobAppearanceRenderManager;
import madoku.craft.java.mob.MobVariantRenderStateAccess;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkeletonRenderer.class)
public abstract class SkeletonRendererMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/SkeletonRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("RETURN"),
		cancellable = true
	)
	private void madokuCraft$useVariantTexture(
		SkeletonRenderState state,
		CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!(state instanceof MobVariantRenderStateAccess access)) {
			return;
		}
		Identifier texture = MobAppearanceRenderManager.texture("minecraft:skeleton", access.madokuCraft$getVariantKey());
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}
