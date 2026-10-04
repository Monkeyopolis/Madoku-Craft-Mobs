package madoku.craft.java.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Spider eyes layer that can select a configured texture per render state. */
public final class SpiderVariantEyesLayer extends EyesLayer<LivingEntityRenderState, SpiderModel> {
	private static final Identifier DEFAULT_EYES = Identifier.withDefaultNamespace("textures/entity/spider/spider_eyes.png");
	private static final RenderType DEFAULT_RENDER_TYPE = RenderTypes.eyes(DEFAULT_EYES);

	public SpiderVariantEyesLayer(RenderLayerParent<LivingEntityRenderState, SpiderModel> parent) {
		super(parent);
	}

	@Override
	public void submit(
		PoseStack poseStack,
		SubmitNodeCollector collector,
		int packedLight,
		LivingEntityRenderState state,
		float yRot,
		float xRot
	) {
		Identifier eyes = resolveEyes(state);
		RenderType renderType = eyes == null ? DEFAULT_RENDER_TYPE : RenderTypes.eyes(eyes);
		collector.order(1).submitModel(
			getParentModel(),
			state,
			poseStack,
			renderType,
			packedLight,
			OverlayTexture.NO_OVERLAY,
			state.outlineColor
		);
	}

	@Override
	public RenderType renderType() {
		return DEFAULT_RENDER_TYPE;
	}

	private static Identifier resolveEyes(LivingEntityRenderState state) {
		if (!(state instanceof SpiderVariantRenderStateAccess access)) {
			return null;
		}
		return MobVariantAppearanceManager.resolve("minecraft:spider", access.madokuCraft$getVariantKey()).eyes();
	}
}
