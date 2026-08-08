package madoku.craft.mobs.mob;

import madoku.craft.api.loot.EquipmentsConfigManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Mobs-scoped compatibility facade for the API equipment configuration.
 *
 * <p>The API owns loading, parsing, and weighted equipment rolls. This bridge
 * keeps the mobs module's equipment call sites scoped to mob spawning.</p>
 */
final class EquipmentConfigManager {
	private static final double DEFAULT_CHANCE_WHEN_MOB_SYSTEM_DISABLED = 10.0D;

	private EquipmentConfigManager() {
	}

	static boolean isCustomEntityEquipmentEnabled() {
		return EquipmentsConfigManager.isEntityEquipmentOverrideEnabled();
	}

	static EquipmentProfile resolveProfile(String rawReference, EntityType<?> mobType) {
		EquipmentsConfigManager.EquipmentProfile profile =
			EquipmentsConfigManager.resolveProfile(rawReference, mobType);
		if (profile == null) {
			return null;
		}
		return new EquipmentProfile(
			profile.enabled(),
			profile);
	}

	static Map<EquipmentSlot, ItemStack> rollEquipment(EquipmentProfile profile, RandomSource random) {
		return profile == null
			? Map.of()
			: EquipmentsConfigManager.rollEquipment(profile.apiProfile(), random);
	}

	static double customEntityEquipmentChanceWhenMobSystemDisabled() {
		return DEFAULT_CHANCE_WHEN_MOB_SYSTEM_DISABLED;
	}

	record EquipmentProfile(
		boolean enabled,
		EquipmentsConfigManager.EquipmentProfile apiProfile
	) {
	}
}
