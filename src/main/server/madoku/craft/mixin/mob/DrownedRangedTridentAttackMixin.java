package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Drowned.class)
public abstract class DrownedRangedTridentAttackMixin {
	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyCustomTridentAttack(LivingEntity target, float pullProgress, CallbackInfo ci) {
		Drowned drowned = (Drowned) (Object) this;
		if (EntityBehaviorsManager.DrownedBehavior.applyRangedDrownedTridentAttack(drowned, target, pullProgress)) {
			ci.cancel();
		}
	}

	@ModifyArg(
		method = "addBehaviourGoals",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/zombie/Drowned$DrownedTridentAttackGoal;<init>(Lnet/minecraft/world/entity/monster/RangedAttackMob;DIF)V"
		),
		index = 2
	)
	private int madokuCraft$applyTridentAttackInterval(int vanillaInterval) {
		return EntityBehaviorsManager.DrownedBehavior.resolveTridentAttackIntervalTicks((Drowned) (Object) this);
	}
}
