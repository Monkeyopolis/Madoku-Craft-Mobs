package madoku.craft.java.mob;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Optional integrations supplied by other Madoku modules or the bundle. */
public interface MobFeatureAdapter {
	default boolean isManagedPet(Entity entity) {
		return false;
	}

	default double reduceCreeperGriefChance(LivingEntity target, double chance) {
		return chance;
	}

	default double reduceHostileRangedAccuracy(LivingEntity target, double accuracy) {
		return accuracy;
	}

	default boolean applyBeeCropGrowth(ServerLevel world, BlockPos cropPos, double growthPercent, String source) {
		return false;
	}

	default boolean isBeeCropGrowthEnabled() {
		return false;
	}

	default boolean isEquipmentOverrideEnabled() {
		return false;
	}

	default Map<EquipmentSlot, ItemStack> resolveEquipment(
		String equipmentReference,
		EntityType<?> mobType,
		RandomSource random
	) {
		return null;
	}
}
