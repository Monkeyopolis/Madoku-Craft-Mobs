package madoku.craft.mixin.mob;

import java.util.UUID;

import madoku.craft.java.mob.MobIndicatorRenderStateAccess;
import madoku.craft.java.mob.MobVariantRenderStateAccess;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements MobIndicatorRenderStateAccess, MobVariantRenderStateAccess {
	@Unique private boolean madokuCraft$mobIndicatorManaged;
	@Unique private UUID madokuCraft$mobIndicatorEntityUuid;
	@Unique private float madokuCraft$mobIndicatorHealth;
	@Unique private float madokuCraft$mobIndicatorMaxHealth;
	@Unique private float madokuCraft$mobIndicatorArmor;
	@Unique private String madokuCraft$variantKey = "";

	@Override
	public void madokuCraftMobIndicator$setManaged(boolean managed) {
		madokuCraft$mobIndicatorManaged = managed;
	}

	@Override
	public boolean madokuCraftMobIndicator$isManaged() {
		return madokuCraft$mobIndicatorManaged;
	}

	@Override
	public void madokuCraftMobIndicator$setEntityUuid(UUID entityUuid) {
		madokuCraft$mobIndicatorEntityUuid = entityUuid;
	}

	@Override
	public UUID madokuCraftMobIndicator$getEntityUuid() {
		return madokuCraft$mobIndicatorEntityUuid;
	}

	@Override
	public void madokuCraftMobIndicator$setHealth(float health) {
		madokuCraft$mobIndicatorHealth = health;
	}

	@Override
	public float madokuCraftMobIndicator$getHealth() {
		return madokuCraft$mobIndicatorHealth;
	}

	@Override
	public void madokuCraftMobIndicator$setMaxHealth(float maxHealth) {
		madokuCraft$mobIndicatorMaxHealth = maxHealth;
	}

	@Override
	public float madokuCraftMobIndicator$getMaxHealth() {
		return madokuCraft$mobIndicatorMaxHealth;
	}

	@Override
	public void madokuCraftMobIndicator$setArmor(float armor) {
		madokuCraft$mobIndicatorArmor = armor;
	}

	@Override
	public float madokuCraftMobIndicator$getArmor() {
		return madokuCraft$mobIndicatorArmor;
	}

	@Override
	public void madokuCraft$setVariantKey(String variantKey) {
		madokuCraft$variantKey = variantKey == null ? "" : variantKey;
	}

	@Override
	public String madokuCraft$getVariantKey() {
		return madokuCraft$variantKey;
	}
}
