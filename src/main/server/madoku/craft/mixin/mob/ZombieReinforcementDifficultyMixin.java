package madoku.craft.mixin.mob;

import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Zombie.class)
public abstract class ZombieReinforcementDifficultyMixin {
	@Redirect(
		method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;getDifficulty()Lnet/minecraft/world/Difficulty;"
		)
	)
	private Difficulty madokuCraft$allowConfiguredReinforcementsOnAllNonPeacefulDifficulties(ServerLevel level) {
		Difficulty difficulty = level.getDifficulty();
		if (MobEntityManager.shouldAllowConfiguredZombieReinforcementsOnDifficulty(
			(Zombie) (Object) this,
			difficulty
		)) {
			return Difficulty.HARD;
		}
		return difficulty;
	}
}
