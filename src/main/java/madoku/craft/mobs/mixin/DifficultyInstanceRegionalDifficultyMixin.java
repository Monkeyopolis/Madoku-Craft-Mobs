package madoku.craft.mobs.mixin;

import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DifficultyInstance.class)
public abstract class DifficultyInstanceRegionalDifficultyMixin {
	@Inject(method = "calculateDifficulty", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$removeRegionalDifficulty(
		Difficulty difficulty,
		long worldTime,
		long chunkInhabitedTime,
		float moonBrightness,
		CallbackInfoReturnable<Float> cir
	) {
		if (difficulty == null) {
			cir.setReturnValue(0.0F);
			return;
		}
		cir.setReturnValue(difficulty.getId() * 0.75F);
	}
}

