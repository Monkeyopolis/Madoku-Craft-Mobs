package madoku.craft.mixin.mob;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import madoku.craft.java.mob.MobIndicatorClient;
import madoku.craft.java.mob.MobIndicatorRenderStateAccess;
import madoku.craft.java.mob.LivingEntityRendererLayersAccess;
import madoku.craft.java.mob.MobVariantClient;
import madoku.craft.java.mob.MobVariantRenderStateAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin implements LivingEntityRendererLayersAccess {
	@Shadow @Final protected List<?> layers;

	@Override
	public List<?> madokuCraft$getLayers() {
		return layers;
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL")
	)
	private void madokuCraft$extractMobIndicatorState(
		LivingEntity entity,
		LivingEntityRenderState state,
		float partialTick,
		CallbackInfo callbackInfo
	) {
		MobIndicatorRenderStateAccess access = (MobIndicatorRenderStateAccess) state;
		boolean managed = MobIndicatorClient.isManagedMob(entity);
		access.madokuCraftMobIndicator$setManaged(managed);
		access.madokuCraftMobIndicator$setEntityUuid(managed ? entity.getUUID() : null);
		access.madokuCraftMobIndicator$setHealth(managed ? entity.getHealth() : 0.0F);
		access.madokuCraftMobIndicator$setMaxHealth(managed ? entity.getMaxHealth() : 0.0F);
		access.madokuCraftMobIndicator$setArmor(managed ? entity.getArmorValue() : 0.0F);
		if (state instanceof MobVariantRenderStateAccess variantAccess) {
			String variantKey = MobVariantClient.getVariantKey(entity.getUUID());
			variantAccess.madokuCraft$setVariantKey(variantKey);
		}
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("TAIL")
	)
	private void madokuCraft$submitMobIndicator(
		LivingEntityRenderState state,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		CameraRenderState cameraState,
		CallbackInfo callbackInfo
	) {
		MobIndicatorClient.render((MobIndicatorRenderStateAccess) state, state, poseStack, collector, cameraState);
	}
}
