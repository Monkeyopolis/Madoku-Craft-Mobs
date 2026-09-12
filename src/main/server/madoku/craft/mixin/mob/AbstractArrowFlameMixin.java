package madoku.craft.mixin.mob;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowFlameMixin {
	@Redirect(
		method = "onHitEntity",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;igniteForSeconds(F)V"
		)
	)
	private void madokuCraft$applyConfiguredFlame(Entity target, float vanillaSeconds) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;
		if (!EnchantBooksAPIManager.applyConfiguredFlame(target, arrow.getWeaponItem(), vanillaSeconds)) {
			target.igniteForSeconds(vanillaSeconds);
		}
	}
}

