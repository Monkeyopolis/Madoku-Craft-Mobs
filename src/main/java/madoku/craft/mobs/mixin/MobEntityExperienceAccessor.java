package madoku.craft.mobs.mixin;

import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEntity.class)
public interface MobEntityExperienceAccessor {
	@Accessor("experiencePoints")
	void madokuCraftMobs$setExperiencePoints(int value);
}
