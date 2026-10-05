package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobVariantAccess;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Mob.class)
public abstract class MobVariantMixin implements MobVariantAccess {
	@Unique private String madokuCraft$variantKey = "";

	@Override
	public void madokuCraft$setVariantKey(String variantKey) {
		madokuCraft$variantKey = variantKey == null ? "" : variantKey;
	}

	@Override
	public String madokuCraft$getVariantKey() {
		return madokuCraft$variantKey;
	}
}
