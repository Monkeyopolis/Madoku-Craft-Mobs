package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityRelationshipManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PiglinAi.class)
public abstract class PiglinAiTargetMixin {
	@Inject(method = "setAngerTarget", at = @At("HEAD"), cancellable = true)
	private static void madokuCraft$applyConfiguredTargetCondition(
		ServerLevel level,
		AbstractPiglin piglin,
		LivingEntity target,
		CallbackInfo callbackInfo
	) {
		if (!EntityRelationshipManager.canSetTarget(piglin, target)) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "findNearestValidAttackTarget", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$filterConfiguredTarget(
		ServerLevel level,
		Piglin piglin,
		CallbackInfoReturnable<Optional<? extends LivingEntity>> callbackInfo
	) {
		Optional<? extends LivingEntity> target = callbackInfo.getReturnValue();
		if (target.isPresent() && !EntityRelationshipManager.canAcquireTarget(piglin, target.get())) {
			callbackInfo.setReturnValue(Optional.empty());
		}
	}
}
