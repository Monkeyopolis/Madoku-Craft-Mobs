package madoku.craft.mobs.mixin;

import madoku.craft.mobs.mob.EntityBehaviorsManager;

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
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY) {
			EntityBehaviorsManager.StrayBehavior.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED) {
			EntityBehaviorsManager.BoggedBehavior.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED) {
			EntityBehaviorsManager.ParchedBehavior.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON) {
			EntityBehaviorsManager.SkeletonBehavior.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		EntityBehaviorsManager.SkeletonBehavior.tickRangedSkeletonRuntime(skeleton);
	}
}


