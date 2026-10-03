package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public abstract class ZombieSpawnAttributesMixin {
	@Inject(method = "handleAttributes", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$overrideVanillaSpawnAttributes(
		float specialMultiplier,
		EntitySpawnReason spawnReason,
		CallbackInfo ci
	) {
		Zombie zombie = (Zombie) (Object) this;
		if (!MobEntityManager.shouldOverrideVanillaZombieSpawnAttributes(zombie)) {
			return;
		}

		// The configured mob pipeline owns these values. Preserve the vanilla door-breaking behavior,
		// but skip the leader max-health and reinforcement modifiers entirely.
		zombie.setCanBreakDoors(true);
		ci.cancel();
	}
}
