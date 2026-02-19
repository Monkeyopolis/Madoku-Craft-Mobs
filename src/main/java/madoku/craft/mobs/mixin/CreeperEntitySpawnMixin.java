package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.CreeperMobSystem;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class CreeperEntitySpawnMixin {
	@Inject(method = "initialize", at = @At("TAIL"))
	private void madokuCraftMobs$applySpawnOverrides(
		ServerWorldAccess world,
		LocalDifficulty difficulty,
		SpawnReason spawnReason,
		EntityData entityData,
		CallbackInfoReturnable<EntityData> cir
	) {
		if (!((Object) this instanceof CreeperEntity creeper)) {
			return;
		}
		CreeperMobSystem.applySpawnOverrides(creeper, world, difficulty, spawnReason, entityData);
	}
}
