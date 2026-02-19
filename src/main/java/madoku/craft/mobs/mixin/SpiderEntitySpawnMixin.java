package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.SpiderMobSystem;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpiderEntity.class)
public abstract class SpiderEntitySpawnMixin {
	@Inject(method = "initialize", at = @At("TAIL"))
	private void madokuCraftMobs$applySpawnOverrides(
		ServerWorldAccess world,
		LocalDifficulty difficulty,
		SpawnReason spawnReason,
		EntityData entityData,
		CallbackInfoReturnable<EntityData> cir
	) {
		SpiderEntity spider = (SpiderEntity) (Object) this;
		SpiderMobSystem.applySpawnOverrides(spider, world, difficulty, spawnReason, entityData);
	}
}
