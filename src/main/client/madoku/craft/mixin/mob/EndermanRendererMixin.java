package madoku.craft.mixin.mob;

import java.util.List;

import madoku.craft.java.mob.EndermanVariantEyesLayer;
import madoku.craft.java.mob.LivingEntityRendererLayersAccess;
import madoku.craft.java.mob.MobAppearanceRenderManager;
import madoku.craft.java.mob.MobVariantRenderStateAccess;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EnderEyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndermanRenderer.class)
public abstract class EndermanRendererMixin {
	@Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V", at = @At("TAIL"))
	private void madokuCraft$replaceEndermanEyesLayer(
		EntityRendererProvider.Context context,
		CallbackInfo callbackInfo
	) {
		@SuppressWarnings("unchecked")
		RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> parent =
			(RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>>) (RenderLayerParent<?, ?>) this;
		@SuppressWarnings("unchecked")
		List<RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>>> layers =
			(List<RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>>>) (List<?>)
				((LivingEntityRendererLayersAccess) (Object) this).madokuCraft$getLayers();
		for (int index = 0; index < layers.size(); index++) {
			if (layers.get(index) instanceof EnderEyesLayer) {
				layers.set(index, new EndermanVariantEyesLayer(parent));
			}
		}
	}

	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/EndermanRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("RETURN"),
		cancellable = true
	)
	private void madokuCraft$useVariantTexture(
		EndermanRenderState state,
		CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!(state instanceof MobVariantRenderStateAccess access)) {
			return;
		}
		Identifier texture = MobAppearanceRenderManager.texture("minecraft:enderman", access.madokuCraft$getVariantKey());
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}
