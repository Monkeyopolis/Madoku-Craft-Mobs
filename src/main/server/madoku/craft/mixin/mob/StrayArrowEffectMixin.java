package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Stray.class)
public abstract class StrayArrowEffectMixin {
	@Redirect(
		method = "getArrow",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/arrow/Arrow;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)V"
		)
	)
	private void madokuCraft$skipVanillaEffect(Arrow arrow, MobEffectInstance effect) {
		Stray stray = (Stray) (Object) this;
		boolean suppressVanillaEffect = MobEntityManager.shouldSuppressVanillaMobAttackEffect(stray);
		if (!suppressVanillaEffect) {
			arrow.addEffect(effect);
		}
	}
}
