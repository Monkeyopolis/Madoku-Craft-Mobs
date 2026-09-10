package madoku.craft.mixin.mob;

import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RangedCrossbowAttackGoal.class)
public interface RangedCrossbowAttackGoalAccessor {
	@Accessor("attackDelay")
	void madokuCraft$setAttackDelay(int value);
}

