package madoku.craft.mixin.mob;

import java.util.List;

import madoku.craft.java.mob.LivingEntityRendererLayersAccess;
import madoku.craft.java.mob.MobAppearanceRenderManager;
import madoku.craft.java.mob.MobRendererLayerManager;
import madoku.craft.java.mob.MobVariantClothingLayer;
import madoku.craft.java.mob.MobVariantRenderStateAccess;
import madoku.craft.java.mob.SkeletonVariantArmorLayer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.StrayRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SkeletonClothingLayer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StrayRenderer.class)
public abstract class StrayRendererMixin {
	@Inject(
		method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V",
		at = @At("TAIL")
	)
	private void madokuCraft$addVariantArmorLayer(
		EntityRendererProvider.Context context,
		CallbackInfo callbackInfo
	) {
		@SuppressWarnings("unchecked")
		RenderLayerParent<SkeletonRenderState, SkeletonModel<SkeletonRenderState>> parent =
			(RenderLayerParent<SkeletonRenderState, SkeletonModel<SkeletonRenderState>>) (RenderLayerParent<?, ?>) this;
		@SuppressWarnings("unchecked")
		List<RenderLayer<SkeletonRenderState, SkeletonModel<SkeletonRenderState>>> layers =
			(List<RenderLayer<SkeletonRenderState, SkeletonModel<SkeletonRenderState>>>) (List<?>)
				((LivingEntityRendererLayersAccess) (Object) this).madokuCraft$getLayers();
		MobRendererLayerManager.wrapFirst(
			layers,
			SkeletonClothingLayer.class,
			vanillaLayer -> new MobVariantClothingLayer<>(parent, vanillaLayer, "minecraft:stray")
		);
		MobRendererLayerManager.addIfAbsent(
			layers,
			SkeletonVariantArmorLayer.class,
			new SkeletonVariantArmorLayer(parent, context.getModelSet(), ModelLayers.STRAY, "minecraft:stray")
		);
	}

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
		String variantKey = access.madokuCraft$getVariantKey();
		Identifier texture = MobAppearanceRenderManager.texture("minecraft:stray", variantKey);
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}
