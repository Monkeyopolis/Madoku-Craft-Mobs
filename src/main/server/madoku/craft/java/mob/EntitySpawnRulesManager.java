package madoku.craft.java.mob;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.level.ServerLevelAccessor;

/** Runtime group for configured entity spawn rules. */
public final class EntitySpawnRulesManager {
	private EntitySpawnRulesManager() {
	}

	public static void applyAfterVanilla(Mob mob, ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason spawnReason) {
		if (mob == null || world == null || difficulty == null || spawnReason == null || !MobEntityManager.isEnabled()) return;
		if (mob.getType() == MobEntityTypeAPIManager.BEE) {
			EntityBehaviorsManager.BeeBehavior.applySpawnOverrides(mob, world);
		} else if (MobEntityTypeAPIManager.isHag(mob.getType())) {
			EntityBehaviorsManager.HagBehavior.applySpawnOverrides(mob);
		} else if (mob instanceof Spider spider) {
			MobEntityManager.applySpiderSpawnOverrides(spider, world, difficulty, spawnReason);
		} else if (mob instanceof Creeper creeper) {
			EntityBehaviorsManager.CreeperBehavior.applySpawnOverrides(creeper, world, difficulty);
		} else if (mob instanceof ZombieVillager zombieVillager) {
			MobEntityManager.applyZombieSpawnOverrides(zombieVillager, world, difficulty, spawnReason);
		} else if (mob instanceof Drowned drowned) {
			MobEntityManager.applyDrownedSpawnOverrides(drowned, world, difficulty, spawnReason);
		} else if (mob instanceof Husk husk) {
			EntityBehaviorsManager.HuskBehavior.applySpawnOverrides(husk, world, difficulty, spawnReason);
		} else if (mob instanceof AbstractSkeleton skeleton) {
			MobEntityManager.applySkeletonSpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else if (mob instanceof Zombie zombie) {
			MobEntityManager.applyZombieSpawnOverrides(zombie, world, difficulty, spawnReason);
		}
		// Apply the selected configuration after the behavior hook has selected
		// and stored the variant, while preserving vanilla non-jockey initialization.
		if (MobEntityManager.shouldApplyConfiguredComponentsForRuntime(mob)) {
			EntityComponentsManager.applyConfiguredComponents(mob, MobEntityManager.resolveConfiguredEntityVariantForRuntime(mob));
		}
	}
}
