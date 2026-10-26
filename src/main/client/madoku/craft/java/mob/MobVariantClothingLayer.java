package madoku.craft.java.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;

/** Delegates a vanilla clothing layer unless the active variant supplies custom armor. */
public final class MobVariantClothingLayer<S extends SkeletonRenderState, M extends EntityModel<S>>
		extends RenderLayer<S, M> {
	private final RenderLayer<S, M> vanillaLayer;
	private final String mobId;

	public MobVariantClothingLayer(
		RenderLayerParent<S, M> parent,
		RenderLayer<S, M> vanillaLayer,
		String mobId
	) {
		super(parent);
		this.vanillaLayer = vanillaLayer;
		this.mobId = mobId;
	}

	@Override
	public void submit(
		PoseStack poseStack,
		SubmitNodeCollector collector,
		int packedLight,
		S state,
		float yRot,
		float xRot
	) {
		if (!(state instanceof MobVariantRenderStateAccess access)
			|| MobAppearanceRenderManager.armor(mobId, access.madokuCraft$getVariantKey()) == null) {
			vanillaLayer.submit(poseStack, collector, packedLight, state, yRot, xRot);
		}
	}
}
