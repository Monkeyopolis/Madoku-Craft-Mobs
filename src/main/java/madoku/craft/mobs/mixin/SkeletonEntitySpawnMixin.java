package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.SkeletonMobSystem;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSkeletonEntity.class)
public abstract class SkeletonEntitySpawnMixin {
	@Inject(method = "initialize", at = @At("TAIL"))
	private void madokuCraftMobs$applyBowSpawnOverride(
		ServerWorldAccess world,
		LocalDifficulty difficulty,
		SpawnReason spawnReason,
		EntityData entityData,
		CallbackInfoReturnable<EntityData> cir
	) {
		AbstractSkeletonEntity skeleton = (AbstractSkeletonEntity) (Object) this;
		Random random = world.getRandom();
		SkeletonMobSystem.applySpawnBowOverride(skeleton, random);
		SkeletonMobSystem.applySpawnSpiderJockeyOverride(skeleton, world, difficulty, spawnReason, random);
		SkeletonMobSystem.logSpawnDebug(skeleton, world, difficulty, spawnReason);
	}
}
