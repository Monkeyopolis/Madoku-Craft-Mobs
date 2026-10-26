package madoku.craft.mixin.mob;

import java.util.ArrayList;
import java.util.List;

import madoku.craft.java.mob.EntityGoalsManager;
import madoku.craft.java.mob.EntityRelationshipManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NearestAttackableTargetGoal.class)
public abstract class NearestAttackableTargetMixin {
	@Redirect(
		method = "findTarget",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;getNearestPlayer(Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;DDD)Lnet/minecraft/world/entity/player/Player;"
		)
	)
	private Player madokuCraft$filterConfiguredPlayerTarget(
		ServerLevel serverLevel,
		TargetingConditions conditions,
		LivingEntity except,
		double x,
		double y,
		double z
	) {
		Mob mob = ((TargetGoalAccessor) (Object) this).madokuCraft$getMob();
		if (EntityGoalsManager.resolvePlayerTargetGoal(mob) == null) {
			return serverLevel.getNearestPlayer(conditions, except, x, y, z);
		}

		TargetingConditions configuredConditions = conditions.copy().selector((candidate, level) ->
			EntityRelationshipManager.canAcquireTarget(mob, candidate)
		);
		return serverLevel.getNearestPlayer(configuredConditions, except, x, y, z);
	}

	@Redirect(
		method = "findTarget",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;getNearestEntity(Ljava/util/List;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;DDD)Lnet/minecraft/world/entity/LivingEntity;"
		)
	)
	private LivingEntity madokuCraft$filterConfiguredTargets(
		ServerLevel serverLevel,
		List<? extends LivingEntity> candidates,
		TargetingConditions conditions,
		LivingEntity except,
		double x,
		double y,
		double z
	) {
		Mob mob = ((TargetGoalAccessor) (Object) this).madokuCraft$getMob();
		List<LivingEntity> filteredCandidates = new ArrayList<>();
		for (LivingEntity candidate : candidates) {
			if (EntityRelationshipManager.canAcquireTarget(mob, candidate)) {
				filteredCandidates.add(candidate);
			}
		}
		return serverLevel.getNearestEntity(filteredCandidates, conditions, except, x, y, z);
	}
}
