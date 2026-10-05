package madoku.craft.mixin.mob;

import java.util.List;

import madoku.craft.java.mob.CreeperVariantArmorLayer;
import madoku.craft.java.mob.LivingEntityRendererLayersAccess;
import madoku.craft.java.mob.MobAppearanceRenderManager;
import madoku.craft.java.mob.MobVariantRenderStateAccess;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreeperRenderer.class)
public abstract class CreeperRendererMixin {
	@Inject(
		method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V",
		at = @At("TAIL")
	)
	private void madokuCraft$addVariantArmorLayer(
		EntityRendererProvider.Context context,
		CallbackInfo callbackInfo
	) {
		@SuppressWarnings("unchecked")
		RenderLayerParent<CreeperRenderState, CreeperModel> parent =
			(RenderLayerParent<CreeperRenderState, CreeperModel>) (RenderLayerParent<?, ?>) this;
		@SuppressWarnings("unchecked")
		List<RenderLayer<CreeperRenderState, CreeperModel>> layers =
			(List<RenderLayer<CreeperRenderState, CreeperModel>>) (List<?>)
				((LivingEntityRendererLayersAccess) (Object) this).madokuCraft$getLayers();
		layers.add(new CreeperVariantArmorLayer(parent, context.getModelSet()));
	}

	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("RETURN"),
		cancellable = true
	)
	private void madokuCraft$useVariantTexture(
		CreeperRenderState state,
		CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!(state instanceof MobVariantRenderStateAccess access)) {
			return;
		}
		Identifier texture = MobAppearanceRenderManager.texture("minecraft:creeper", access.madokuCraft$getVariantKey());
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}
