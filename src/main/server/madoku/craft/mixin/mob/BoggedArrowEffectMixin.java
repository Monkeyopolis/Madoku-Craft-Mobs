package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Bogged.class)
public abstract class BoggedArrowEffectMixin {
	@Redirect(
		method = "getArrow",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/arrow/Arrow;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)V"
		)
	)
	private void madokuCraft$skipVanillaEffect(Arrow arrow, MobEffectInstance effect) {
		Bogged bogged = (Bogged) (Object) this;
		if (!MobEntityManager.shouldSuppressVanillaMobAttackEffect(bogged)) {
			arrow.addEffect(effect);
		}
	}
}
