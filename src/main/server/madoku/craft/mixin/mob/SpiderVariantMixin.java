package madoku.craft.mixin.mob;

import madoku.craft.java.mob.SpiderVariantAccess;
import net.minecraft.world.entity.monster.spider.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Spider.class)
public abstract class SpiderVariantMixin implements SpiderVariantAccess {
	@Unique private String madokuCraft$variantKey = "";

	@Override
	public void madokuCraft$setVariantKey(String variantKey) {
		madokuCraft$variantKey = variantKey == null ? "" : variantKey;
	}

	@Override
	public String madokuCraft$getVariantKey() {
		return madokuCraft$variantKey;
	}
}
