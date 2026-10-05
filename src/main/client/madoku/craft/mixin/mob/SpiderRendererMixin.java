package madoku.craft.mixin.mob;

import java.util.List;

import madoku.craft.java.mob.LivingEntityRendererLayersAccess;
import madoku.craft.java.mob.MobAppearanceRenderManager;
import madoku.craft.java.mob.SpiderVariantEyesLayer;
import madoku.craft.java.mob.MobVariantRenderStateAccess;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SpiderEyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpiderRenderer.class)
public abstract class SpiderRendererMixin {
	@Inject(
		method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/client/model/geom/ModelLayerLocation;)V",
		at = @At("TAIL")
	)
	private void madokuCraft$replaceSpiderEyesLayer(
		EntityRendererProvider.Context context,
		ModelLayerLocation modelLayerLocation,
		CallbackInfo callbackInfo
	) {
		@SuppressWarnings("unchecked")
		RenderLayerParent<LivingEntityRenderState, SpiderModel> parent =
			(RenderLayerParent<LivingEntityRenderState, SpiderModel>) (RenderLayerParent<?, ?>) this;
		@SuppressWarnings("unchecked")
		List<RenderLayer<LivingEntityRenderState, SpiderModel>> layers =
			(List<RenderLayer<LivingEntityRenderState, SpiderModel>>) (List<?>)
				((LivingEntityRendererLayersAccess) (Object) this).madokuCraft$getLayers();
		for (int index = 0; index < layers.size(); index++) {
			if (layers.get(index) instanceof SpiderEyesLayer<?>) {
				layers.set(index, new SpiderVariantEyesLayer(parent));
			}
		}
	}

	@Inject(method = "getTextureLocation", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$useVariantTexture(
		LivingEntityRenderState state,
		CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!(state instanceof MobVariantRenderStateAccess access)) {
			return;
		}
		Identifier texture = MobAppearanceRenderManager.texture("minecraft:spider", access.madokuCraft$getVariantKey());
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}
