package madoku.craft.mobs.mixin;

import net.minecraft.entity.ai.goal.CrossbowAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CrossbowAttackGoal.class)
public interface CrossbowAttackGoalAccessor {
	@Accessor("chargedTicksLeft")
	void madokuCraftMobs$setChargedTicksLeft(int value);
}
