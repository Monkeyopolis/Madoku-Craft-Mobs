package madoku.craft.mixin.mob;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperAccessor {
	@Accessor("oldSwell")
	int madokuCraft$getOldSwell();

	@Accessor("oldSwell")
	void madokuCraft$setOldSwell(int value);

	@Accessor("swell")
	int madokuCraft$getSwell();

	@Accessor("swell")
	void madokuCraft$setSwell(int value);

	@Accessor("maxSwell")
	void madokuCraft$setMaxSwell(int value);

	@Accessor("explosionRadius")
	void madokuCraft$setExplosionRadius(int value);
}

