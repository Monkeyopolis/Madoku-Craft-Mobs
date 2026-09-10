package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {
	Mob.class,
	Zombie.class,
	ZombieVillager.class,
	Drowned.class,
	Husk.class,
	AbstractSkeleton.class,
	Spider.class
}, priority = 1100)
public abstract class MobSpawnOverridesMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void madokuCraft$selectTopLevelVariantBeforeVanilla(
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		SpawnGroupData spawnGroupData,
		CallbackInfoReturnable<SpawnGroupData> cir
	) {
		MobEntityManager.selectConfiguredTopLevelVariantForRuntime(
			(Mob) (Object) this,
			world == null ? null : world.getRandom(),
			spawnReason
		);
	}

	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void madokuCraft$applySpawnOverridesAfterVanilla(
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		SpawnGroupData spawnGroupData,
		CallbackInfoReturnable<SpawnGroupData> cir
	) {
		MobEntityManager.applyMobSpawnOverridesAfterVanilla((Mob) (Object) this, world, difficulty, spawnReason);
	}
}


