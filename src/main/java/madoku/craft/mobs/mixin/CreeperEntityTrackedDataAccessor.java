package madoku.craft.mobs.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreeperEntity.class)
public interface CreeperEntityTrackedDataAccessor {
	@Accessor("CHARGED")
	static TrackedData<Boolean> madokuCraftMobs$getChargedTrackedData() {
		throw new AssertionError("Mixin did not apply.");
	}
}
