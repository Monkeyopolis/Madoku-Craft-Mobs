package madoku.craft.java.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

/** Renders a configured translucent frost overlay for skeleton variants. */
public final class SkeletonVariantArmorLayer extends RenderLayer<SkeletonRenderState, SkeletonModel<SkeletonRenderState>> {
	private final SkeletonModel<SkeletonRenderState> model;
	private final String mobId;

	public SkeletonVariantArmorLayer(
		RenderLayerParent<SkeletonRenderState, SkeletonModel<SkeletonRenderState>> parent,
		EntityModelSet modelSet
	) {
		this(parent, modelSet, ModelLayers.SKELETON, "minecraft:skeleton");
	}

	public SkeletonVariantArmorLayer(
		RenderLayerParent<SkeletonRenderState, SkeletonModel<SkeletonRenderState>> parent,
		EntityModelSet modelSet,
		ModelLayerLocation modelLayer,
		String mobId
	) {
		super(parent);
		model = new SkeletonModel<>(modelSet.bakeLayer(modelLayer));
		this.mobId = mobId;
	}

	@Override
	public void submit(
		PoseStack poseStack,
		SubmitNodeCollector collector,
		int packedLight,
		SkeletonRenderState state,
		float yRot,
		float xRot
	) {
		if (!(state instanceof MobVariantRenderStateAccess access)) {
			return;
		}
		Identifier armor = MobAppearanceRenderManager.armor(mobId, access.madokuCraft$getVariantKey());
		if (armor == null) {
			return;
		}
		collector.order(1).submitModel(
			model,
			state,
			poseStack,
			RenderTypes.entityTranslucentCull(armor),
			packedLight,
			OverlayTexture.NO_OVERLAY,
			-1,
			null,
			state.outlineColor
		);
	}
}
