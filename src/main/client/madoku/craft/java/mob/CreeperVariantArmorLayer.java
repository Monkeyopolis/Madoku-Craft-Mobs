package madoku.craft.java.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Renders a configured energy-swirl armor texture for creeper variants. */
public final class CreeperVariantArmorLayer extends RenderLayer<CreeperRenderState, CreeperModel> {
	private final CreeperModel model;

	public CreeperVariantArmorLayer(
		RenderLayerParent<CreeperRenderState, CreeperModel> parent,
		EntityModelSet modelSet
	) {
		super(parent);
		model = new CreeperModel(modelSet.bakeLayer(ModelLayers.CREEPER_ARMOR));
	}

	@Override
	public void submit(
		PoseStack poseStack,
		SubmitNodeCollector collector,
		int packedLight,
		CreeperRenderState state,
		float yRot,
		float xRot
	) {
		if (!(state instanceof MobVariantRenderStateAccess access)) {
			return;
		}
		Identifier armor = MobAppearanceRenderManager.armor("minecraft:creeper", access.madokuCraft$getVariantKey());
		if (armor == null) {
			return;
		}
		float offset = state.ageInTicks * 0.01F;
		collector.order(1).submitModel(
			model,
			state,
			poseStack,
			RenderTypes.energySwirl(armor, offset % 1.0F, offset % 1.0F),
			packedLight,
			OverlayTexture.NO_OVERLAY,
			-8355712,
			null,
			state.outlineColor
		);
	}
}
