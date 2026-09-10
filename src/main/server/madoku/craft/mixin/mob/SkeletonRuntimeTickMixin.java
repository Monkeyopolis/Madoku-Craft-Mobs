package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;
import madoku.craft.java.mob.MobEntityTypeAPIManager;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SkeletonRuntimeTickMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void madokuCraft$tickRangedSkeletonRuntime(CallbackInfo ci) {
		if (!((Object) this instanceof AbstractSkeleton skeleton)) {
			return;
		}
		if (skeleton.getType() == MobEntityTypeAPIManager.STRAY) {
			EntityBehaviorsManager.StrayBehavior.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		if (skeleton.getType() == MobEntityTypeAPIManager.BOGGED) {
			EntityBehaviorsManager.BoggedBehavior.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		if (skeleton.getType() == MobEntityTypeAPIManager.PARCHED) {
			EntityBehaviorsManager.ParchedBehavior.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		if (skeleton.getType() == MobEntityTypeAPIManager.WITHER_SKELETON) {
			EntityBehaviorsManager.SkeletonBehavior.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		EntityBehaviorsManager.SkeletonBehavior.tickRangedSkeletonRuntime(skeleton);
	}
}
