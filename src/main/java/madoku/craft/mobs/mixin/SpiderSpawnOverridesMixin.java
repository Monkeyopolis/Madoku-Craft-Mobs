package madoku.craft.mobs.mixin;

import madoku.craft.mobs.mob.system.MadokuMob;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Spider.class)
public abstract class SpiderSpawnOverridesMixin {
	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void madokuCraft$applySpiderSpawnOverrides(
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		SpawnGroupData spawnGroupData,
		CallbackInfoReturnable<SpawnGroupData> cir
	) {
		MadokuMob.applySpiderSpawnOverrides((Spider) (Object) this, world, difficulty, spawnReason);
	}
}

