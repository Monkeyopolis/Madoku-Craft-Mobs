package madoku.craft.mixin.mob;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMobAggroMixin {
	@Inject(
		method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
		at = @At("RETURN")
	)
	private void madokuCraft$handleMobAggroOnDamage(
		ServerLevel level,
		DamageSource source,
		float amount,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (cir.getReturnValueZ()) {
			MobEntityManager.handleMobDamaged((LivingEntity) (Object) this, source);
			EnchantBooksAPIManager.applyOnHit((LivingEntity) (Object) this, source);
		}
	}
}



