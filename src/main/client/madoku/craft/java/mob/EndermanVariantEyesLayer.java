package madoku.craft.java.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Enderman eyes layer that can select a configured texture per render state. */
public final class EndermanVariantEyesLayer extends EyesLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
	private static final Identifier DEFAULT_EYES = Identifier.withDefaultNamespace("textures/entity/enderman/enderman_eyes.png");
	private static final RenderType DEFAULT_RENDER_TYPE = RenderTypes.eyes(DEFAULT_EYES);

	public EndermanVariantEyesLayer(
		RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> parent
	) {
		super(parent);
	}

	@Override
	public void submit(
		PoseStack poseStack,
		SubmitNodeCollector collector,
		int packedLight,
		EndermanRenderState state,
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

	private static Identifier resolveEyes(EndermanRenderState state) {
		if (!(state instanceof MobVariantRenderStateAccess access)) {
			return null;
		}
		return MobAppearanceRenderManager.eyes("minecraft:enderman", access.madokuCraft$getVariantKey());
	}
}
