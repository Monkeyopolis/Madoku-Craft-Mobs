package madoku.craft.mixin.mob;

import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSkeleton.class)
public interface AbstractSkeletonArrowInvoker {
	@Invoker("getArrow")
	AbstractArrow madokuCraft$invokeGetArrow(ItemStack ammo, float velocity, ItemStack shotFrom);
}

