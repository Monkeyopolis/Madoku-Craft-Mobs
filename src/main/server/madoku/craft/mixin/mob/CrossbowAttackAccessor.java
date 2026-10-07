package madoku.craft.mixin.mob;

import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CrossbowAttack.class)
public interface CrossbowAttackAccessor {
	@Accessor("attackDelay")
	void madokuCraft$setAttackDelay(int value);
}
