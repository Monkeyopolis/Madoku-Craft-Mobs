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
import java.util.Map;

/** Runtime group for configured entity spawn rules. */
public final class EntitySpawnRulesManager {
	private static final Map<String, SpawnAdapter> ADAPTERS = Map.ofEntries(
		Map.entry(MobConfigManager.FILE_BEE, (mob, world, difficulty, reason) -> {
			if (mob.getType() == MobEntityTypeAPIManager.BEE) EntityBehaviorsManager.BeeBehavior.applySpawnOverrides(mob, world);
		}),
		Map.entry(MobConfigManager.FILE_HAG, (mob, world, difficulty, reason) -> {
			if (MobEntityTypeAPIManager.isHag(mob.getType())) EntityBehaviorsManager.HagBehavior.applySpawnOverrides(mob);
		}),
		Map.entry(MobConfigManager.FILE_SPIDER, (mob, world, difficulty, reason) -> {
			if (mob instanceof Spider spider) EntityBehaviorsManager.SpiderBehavior.applySpawnOverrides(spider, world, difficulty, reason);
		}),
		Map.entry(MobConfigManager.FILE_CREEPER, (mob, world, difficulty, reason) -> {
			if (mob instanceof Creeper creeper) EntityBehaviorsManager.CreeperBehavior.applySpawnOverrides(creeper, world, difficulty);
		}),
		Map.entry(MobConfigManager.FILE_ZOMBIE_VILLAGER, (mob, world, difficulty, reason) -> {
			if (mob instanceof ZombieVillager zombieVillager) EntityBehaviorsManager.ZombieVillagerBehavior.applySpawnOverrides(zombieVillager, world, difficulty, reason);
		}),
		Map.entry(MobConfigManager.FILE_DROWNED, (mob, world, difficulty, reason) -> {
			if (mob instanceof Drowned drowned) EntityBehaviorsManager.DrownedBehavior.applySpawnOverrides(drowned, world, difficulty, reason);
		}),
		Map.entry(MobConfigManager.FILE_HUSK, (mob, world, difficulty, reason) -> {
			if (mob instanceof Husk husk) EntityBehaviorsManager.HuskBehavior.applySpawnOverrides(husk, world, difficulty, reason);
		}),
		Map.entry(MobConfigManager.FILE_ZOMBIE, (mob, world, difficulty, reason) -> {
			if (mob instanceof Zombie zombie) EntityBehaviorsManager.ZombieBehavior.applySpawnOverrides(zombie, world, difficulty, reason);
		}),
		Map.entry(MobConfigManager.FILE_SKELETON, EntitySpawnRulesManager::applySkeletonAdapter),
		Map.entry(MobConfigManager.FILE_STRAY, EntitySpawnRulesManager::applySkeletonAdapter),
		Map.entry(MobConfigManager.FILE_BOGGED, EntitySpawnRulesManager::applySkeletonAdapter),
		Map.entry(MobConfigManager.FILE_PARCHED, EntitySpawnRulesManager::applySkeletonAdapter),
		Map.entry(MobConfigManager.FILE_WITHER_SKELETON, EntitySpawnRulesManager::applySkeletonAdapter)
	);

	private EntitySpawnRulesManager() {
	}

	public static void applyAfterVanilla(Mob mob, ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason spawnReason) {
		if (mob == null || world == null || difficulty == null || spawnReason == null || !MobEntityManager.isEnabled()) return;
		SpawnAdapter adapter = ADAPTERS.get(MobEntityManager.resolveRuntimeMobFileKey(mob));
		if (adapter != null) adapter.apply(mob, world, difficulty, spawnReason);
		EntityBehaviorsManager.FamilyBehavior.applySpawnOverrides(mob, world, spawnReason);
		// Apply the selected configuration after the behavior hook has selected
		// and stored the variant, while preserving vanilla non-jockey initialization.
		if (MobEntityManager.shouldApplyConfiguredComponentsForRuntime(mob)) {
			EntityComponentsManager.applyConfiguredComponents(mob, MobEntityManager.resolveConfiguredEntityVariantForRuntime(mob));
		}
	}

	private static void applySkeletonAdapter(Mob mob, ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason spawnReason) {
		if (!(mob instanceof AbstractSkeleton skeleton)) return;
		if (skeleton.getType() == MobEntityTypeAPIManager.WITHER_SKELETON) {
			EntityBehaviorsManager.WitherSkeletonBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else if (skeleton.getType() == MobEntityTypeAPIManager.STRAY) {
			EntityBehaviorsManager.StrayBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else if (skeleton.getType() == MobEntityTypeAPIManager.BOGGED) {
			EntityBehaviorsManager.BoggedBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else if (skeleton.getType() == MobEntityTypeAPIManager.PARCHED) {
			EntityBehaviorsManager.ParchedBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else {
			EntityBehaviorsManager.SkeletonBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		}
	}

	@FunctionalInterface
	private interface SpawnAdapter {
		void apply(Mob mob, ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason spawnReason);
	}
}
