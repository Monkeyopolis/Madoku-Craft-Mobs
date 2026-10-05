package madoku.craft.java.mob;

import net.minecraft.resources.Identifier;

/** Shared client adapter for configured mob appearance channels. */
public final class MobAppearanceRenderManager {
	private MobAppearanceRenderManager() {
	}

	public static Identifier texture(String mobId, String variantKey) {
		return MobVariantAppearanceManager.resolve(mobId, variantKey).texture();
	}

	public static Identifier eyes(String mobId, String variantKey) {
		return MobVariantAppearanceManager.resolve(mobId, variantKey).eyes();
	}

	public static Identifier armor(String mobId, String variantKey) {
		return MobVariantAppearanceManager.resolve(mobId, variantKey).armor();
	}
}
