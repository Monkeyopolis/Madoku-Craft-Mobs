package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CaveSpider.class)
public abstract class CaveSpiderPoisonEffectMixin {
	@Redirect(
		method = "doHurtTarget",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"
		)
	)
	private boolean madokuCraft$applyConfiguredEffect(
		LivingEntity target,
		MobEffectInstance effect,
		Entity attacker
	) {
		return MobEntityManager.applyConfiguredMobAttackEffect(
			target,
			(CaveSpider) (Object) this,
			effect
		);
	}
}
