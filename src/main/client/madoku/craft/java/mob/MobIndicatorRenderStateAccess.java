package madoku.craft.java.mob;

import java.util.UUID;

/** Extra render-state values captured for the managed-mob indicator. */
public interface MobIndicatorRenderStateAccess {
	void madokuCraftMobIndicator$setManaged(boolean managed);
	boolean madokuCraftMobIndicator$isManaged();
	void madokuCraftMobIndicator$setEntityUuid(UUID entityUuid);
	UUID madokuCraftMobIndicator$getEntityUuid();
	void madokuCraftMobIndicator$setHealth(float health);
	float madokuCraftMobIndicator$getHealth();
	void madokuCraftMobIndicator$setMaxHealth(float maxHealth);
	float madokuCraftMobIndicator$getMaxHealth();
	void madokuCraftMobIndicator$setArmor(float armor);
	float madokuCraftMobIndicator$getArmor();
}
