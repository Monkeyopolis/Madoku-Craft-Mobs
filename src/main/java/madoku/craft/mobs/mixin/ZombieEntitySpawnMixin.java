package madoku.craft.mobs.mixin;

import madoku.craft.mobs.system.ZombieMobSystem;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntitySpawnMixin {
	@Inject(method = "initialize", at = @At("TAIL"))
	private void madokuCraftMobs$applyBabySpawnOverride(
		ServerWorldAccess world,
		LocalDifficulty difficulty,
		SpawnReason spawnReason,
		EntityData entityData,
		CallbackInfoReturnable<EntityData> cir
	) {
		ZombieEntity zombie = (ZombieEntity) (Object) this;
		Random random = world.getRandom();
		boolean hardcore = world.toServerWorld().getServer().isHardcore();
		ZombieMobSystem.applyCustomBabySpawnChance(zombie, difficulty.getGlobalDifficulty(), random, hardcore);
		ZombieMobSystem.sanitizeSpawnState(zombie);
		ZombieMobSystem.logSpawnDebug(zombie, difficulty.getGlobalDifficulty(), hardcore, spawnReason);
	}
}
