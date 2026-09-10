package madoku.craft.mixin.mob;

import madoku.craft.java.mob.EntityBehaviorsManager;

import java.lang.reflect.Field;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownTrident.class)
public abstract class ThrownTridentGroundClearMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void madokuCraft$clearRangedDrownedTridents(CallbackInfo ci) {
		ThrownTrident entity = (ThrownTrident) (Object) this;
		int clearTicks = EntityBehaviorsManager.DrownedBehavior.resolveTridentGroundClearTicks(entity);
		if (clearTicks < 0) {
			return;
		}
		int inGroundTime = madokuCraft$getInGroundTime(entity);
		if (inGroundTime >= clearTicks) {
			entity.discard();
		}
	}

	private static int madokuCraft$getInGroundTime(ThrownTrident entity) {
		Class<?> type = entity.getClass();
		while (type != null) {
			try {
				Field field = type.getDeclaredField("inGroundTime");
				field.setAccessible(true);
				return field.getInt(entity);
			} catch (NoSuchFieldException exception) {
				type = type.getSuperclass();
			} catch (IllegalAccessException exception) {
				return -1;
			}
		}
		return -1;
	}
}
