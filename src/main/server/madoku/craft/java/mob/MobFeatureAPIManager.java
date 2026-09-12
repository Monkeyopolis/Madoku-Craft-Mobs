package madoku.craft.java.mob;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Registry for optional integrations used by the Mobs runtime. */
public final class MobFeatureAPIManager {
	private static final List<MobFeatureAdapter> ADAPTERS = new CopyOnWriteArrayList<>();

	private MobFeatureAPIManager() {
	}

	public static void registerAdapter(MobFeatureAdapter adapter) {
		if (adapter == null) {
			throw new IllegalArgumentException("Mob feature adapter must not be null.");
		}
		ADAPTERS.add(adapter);
	}

	public static boolean isManagedPet(Entity entity) {
		for (MobFeatureAdapter adapter : ADAPTERS) {
			if (adapter.isManagedPet(entity)) {
				return true;
			}
		}
		return false;
	}

	public static double reduceCreeperGriefChance(LivingEntity target, double chance) {
		double resolved = chance;
		for (MobFeatureAdapter adapter : ADAPTERS) {
			resolved = adapter.reduceCreeperGriefChance(target, resolved);
		}
		return resolved;
	}

	public static double reduceHostileRangedAccuracy(LivingEntity target, double accuracy) {
		double resolved = accuracy;
		for (MobFeatureAdapter adapter : ADAPTERS) {
			resolved = adapter.reduceHostileRangedAccuracy(target, resolved);
		}
		return resolved;
	}

	public static boolean applyBeeCropGrowth(ServerLevel world, BlockPos cropPos, double growthPercent, String source) {
		for (MobFeatureAdapter adapter : ADAPTERS) {
			if (adapter.applyBeeCropGrowth(world, cropPos, growthPercent, source)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isBeeCropGrowthEnabled() {
		for (MobFeatureAdapter adapter : ADAPTERS) {
			if (adapter.isBeeCropGrowthEnabled()) {
				return true;
			}
		}
		return false;
	}

	public static boolean isEquipmentOverrideEnabled() {
		for (MobFeatureAdapter adapter : ADAPTERS) {
			if (adapter.isEquipmentOverrideEnabled()) {
				return true;
			}
		}
		return false;
	}

	public static Map<EquipmentSlot, ItemStack> resolveEquipment(
		String equipmentReference,
		EntityType<?> mobType,
		RandomSource random
	) {
		for (MobFeatureAdapter adapter : ADAPTERS) {
			if (adapter.isEquipmentOverrideEnabled()) {
				return adapter.resolveEquipment(equipmentReference, mobType, random);
			}
		}
		return null;
	}
}
