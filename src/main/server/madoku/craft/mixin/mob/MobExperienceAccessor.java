package madoku.craft.mixin.mob;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobExperienceAccessor {
	@Accessor("xpReward")
	int madokuCraft$getXpReward();

	@Accessor("xpReward")
	void madokuCraft$setXpReward(int value);
}

