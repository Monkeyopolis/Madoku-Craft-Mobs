package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityRelationshipManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the shared target policy to vanilla brain-based target acquisition. */
@Mixin(StartAttacking.class)
public abstract class StartAttackingTargetMixin {
	@Redirect(
		method = "lambda$create$3",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Mob;canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z"
		)
	)
	private static boolean madokuCraft$applyConfiguredTargetPolicy(Mob mob, LivingEntity target) {
		return EntityRelationshipManager.canAcquireTarget(mob, target) && mob.canAttack(target);
	}
}
