package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Spider.class)
public abstract class VanillaSpiderJockeyMixin {
	@Redirect(
		method = "finalizeSpawn",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/skeleton/Skeleton;startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z"
		)
	)
	private boolean madokuCraft$suppressVanillaSpiderJockey(
		Skeleton passenger,
		Entity vehicle,
		boolean force,
		boolean createTicket
	) {
		if (MobEntityManager.shouldSuppressVanillaJockey((Mob) (Object) this)) {
			return false;
		}
		return passenger.startRiding(vehicle, force, createTicket);
	}
}
