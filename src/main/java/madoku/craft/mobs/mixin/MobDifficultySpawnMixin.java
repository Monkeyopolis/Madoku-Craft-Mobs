package madoku.craft.mobs.mixin;

import madoku.craft.mobs.difficulty.system.DifficultyScaledMob;
import madoku.craft.mobs.difficulty.system.MadokuDifficulty;
import madoku.craft.mobs.mob.system.MadokuMob;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobDifficultySpawnMixin implements DifficultyScaledMob {
	@Unique
	private static final String MADOKU_CRAFT_DIFFICULTY_ADJUSTMENT_KEY = "madoku_craft_spawn_difficulty_adjustment";

	@Unique
	private int madokuCraft$spawnDifficultyAdjustment;

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void madokuCraft$applySpawnDifficultyScaling(
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		SpawnGroupData spawnGroupData,
		CallbackInfoReturnable<SpawnGroupData> cir
	) {
		if (MadokuMob.isEnabled()) {
			return;
		}
		Mob mob = (Mob) (Object) this;
		MadokuDifficulty.applySpawnScalingIfUnscaled(mob, world);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void madokuCraft$writeSpawnDifficultyAdjustment(ValueOutput output, CallbackInfo ci) {
		output.putInt(MADOKU_CRAFT_DIFFICULTY_ADJUSTMENT_KEY, madokuCraft$spawnDifficultyAdjustment);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void madokuCraft$readSpawnDifficultyAdjustment(ValueInput input, CallbackInfo ci) {
		madokuCraft$spawnDifficultyAdjustment = input.getIntOr(MADOKU_CRAFT_DIFFICULTY_ADJUSTMENT_KEY, 0);
	}

	@Override
	public int madokuCraft$getSpawnDifficultyAdjustment() {
		return madokuCraft$spawnDifficultyAdjustment;
	}

	@Override
	public void madokuCraft$setSpawnDifficultyAdjustment(int adjustment) {
		madokuCraft$spawnDifficultyAdjustment = Math.max(0, adjustment);
	}
}

