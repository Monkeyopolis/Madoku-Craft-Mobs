package madoku.craft.mobs.mixin;

import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreeperEntity.class)
public interface CreeperEntityAccessor {
	@Accessor("lastFuseTime")
	int madokuCraftMobs$getLastFuseTime();

	@Accessor("lastFuseTime")
	void madokuCraftMobs$setLastFuseTime(int value);

	@Accessor("currentFuseTime")
	int madokuCraftMobs$getCurrentFuseTime();

	@Accessor("currentFuseTime")
	void madokuCraftMobs$setCurrentFuseTime(int value);

	@Accessor("fuseTime")
	void madokuCraftMobs$setFuseTime(int value);

	@Accessor("explosionRadius")
	void madokuCraftMobs$setExplosionRadius(int value);
}
