package madoku.craft.mixin.mob;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import madoku.craft.java.mob.EntityBehaviorsManager;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionPlayerDamageMixin {
	@Shadow
	@Final
	private Entity source;

	@Shadow
	@Final
	private float radius;

	@Redirect(
		method = "hurtEntities",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/ExplosionDamageCalculator;getEntityDamageAmount(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/entity/Entity;F)F"
		)
	)
	private float madokuCraft$applyFixedCreeperPlayerDamage(
		ExplosionDamageCalculator calculator,
		Explosion explosion,
		Entity damagedEntity,
		float seenPercent
	) {
		if (source instanceof Creeper creeper
			&& damagedEntity instanceof Player
			&& EntityBehaviorsManager.CreeperBehavior.shouldUseMobExplodeBehavior(creeper)) {
			return EntityBehaviorsManager.CreeperBehavior.resolveFixedPlayerExplosionDamage(creeper, radius);
		}
		return calculator.getEntityDamageAmount(explosion, damagedEntity, seenPercent);
	}

	@Redirect(
		method = "hurtEntities",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"
		)
	)
	private double madokuCraft$applyBlastProtectionKnockbackResistance(
		LivingEntity entity,
		Holder<Attribute> attribute
	) {
		double currentResistance = entity.getAttributeValue(attribute);
		if (attribute == Attributes.EXPLOSION_KNOCKBACK_RESISTANCE) {
			return EnchantBooksAPIManager.resolveExplosionKnockbackResistance(entity, currentResistance);
		}
		return currentResistance;
	}
}

