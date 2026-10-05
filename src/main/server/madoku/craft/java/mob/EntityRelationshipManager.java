package madoku.craft.java.mob;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;

/** Central relationship policy for configured neutral and hostile entities. */
public final class EntityRelationshipManager {
	private EntityRelationshipManager() {
	}

	public static boolean canSetTarget(Mob attacker, LivingEntity target) {
		if (attacker == null || target == null || attacker == target || !target.isAlive()) {
			return target == null;
		}
		if (MobEntityManager.isConfiguredEntityType(target, MobConfigManager.ENTITY_TYPE_NEUTRAL)
			&& isHostile(attacker)
			&& target != attacker.getLastHurtByMob()
			&& target != attacker.getLastHurtByPlayer()) {
			return false;
		}
		if (!MobEntityManager.isConfiguredEntityType(attacker, MobConfigManager.ENTITY_TYPE_NEUTRAL)) {
			return true;
		}
		if (!(target instanceof net.minecraft.world.entity.player.Player)) {
			return true;
		}
		Boolean configuredPlayerTarget = EntityGoalsManager.resolvePlayerTargetGoal(attacker);
		if (Boolean.TRUE.equals(configuredPlayerTarget)
			&& EntityGoalsManager.arePlayerTargetConditionsSatisfied(attacker)) {
			return true;
		}
		return EntityGoalsManager.isGoalEnabledForRuntime(attacker, "hurt-by-target")
			&& (target == attacker.getLastHurtByMob() || target == attacker.getLastHurtByPlayer());
	}

	public static void handleDamage(LivingEntity victim, LivingEntity attacker) {
		if (!(victim instanceof Mob mob) || attacker == null || !attacker.isAlive()) {
			return;
		}
		if (!EntityGoalsManager.isGoalEnabledForRuntime(victim, "hurt-by-target")) {
			return;
		}
		if (EntityRelationshipManager.canSetTarget(mob, attacker)) {
			mob.setTarget(attacker);
		}
	}

	static boolean isHostile(LivingEntity entity) {
		if (entity == null || MobEntityManager.isConfiguredEntityType(entity, MobConfigManager.ENTITY_TYPE_NEUTRAL)) {
			return false;
		}
		return MobEntityManager.isConfiguredEntityType(entity, MobConfigManager.ENTITY_TYPE_HOSTILE)
			|| entity instanceof Monster
			|| entity instanceof IronGolem;
	}
}
