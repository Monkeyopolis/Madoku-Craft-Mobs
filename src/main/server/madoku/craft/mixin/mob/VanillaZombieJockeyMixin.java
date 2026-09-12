package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Zombie.class)
public abstract class VanillaZombieJockeyMixin {
	@Redirect(
		method = "finalizeSpawn",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/zombie/Zombie;startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z"
		)
	)
	private boolean madokuCraft$suppressVanillaZombieJockey(
		Zombie zombie,
		Entity vehicle,
		boolean force,
		boolean createTicket
	) {
		if (MobEntityManager.shouldSuppressVanillaJockey((Mob) zombie)) {
			return false;
		}
		return zombie.startRiding(vehicle, force, createTicket);
	}

	@Redirect(
		method = "finalizeSpawn",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/ServerLevelAccessor;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
		)
	)
	private boolean madokuCraft$suppressVanillaZombieJockeyEntity(
		ServerLevelAccessor world,
		Entity entity
	) {
		if (MobEntityManager.shouldSuppressVanillaJockey((Mob) (Object) this)) {
			return false;
		}
		return world.addFreshEntity(entity);
	}
}
