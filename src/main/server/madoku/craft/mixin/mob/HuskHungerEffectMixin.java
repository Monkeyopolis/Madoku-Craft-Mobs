package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Husk.class)
public abstract class HuskHungerEffectMixin {
	@Redirect(
		method = "doHurtTarget",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"
		)
	)
	private boolean madokuCraft$applyHuskHungerEffect(
		LivingEntity target,
		MobEffectInstance effect,
		Entity attacker
	) {
		return EntityBehaviorsManager.HuskBehavior.applyHungerAttackEffect((Husk) (Object) this, target, effect, attacker);
	}
}
