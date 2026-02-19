package madoku.craft.mobs.mixin;

import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSkeletonEntity.class)
public interface AbstractSkeletonEntityArrowInvoker {
	@Invoker("createArrowProjectile")
	PersistentProjectileEntity madokuCraftMobs$createArrowProjectile(
		ItemStack arrow,
		float damageModifier,
		ItemStack shotFrom
	);
}
