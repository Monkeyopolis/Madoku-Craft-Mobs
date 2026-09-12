package madoku.craft.java.mob;

import net.minecraft.world.Difficulty;

/** Runtime owner for world-difficulty attribute scaling. */
public final class MobWorldDifficultyManager {
	private MobWorldDifficultyManager() {
	}

	public static void initialize() {
		WorldDifficultyConfigManager.initialize();
	}

	public static void onServerStopped() {
		WorldDifficultyConfigManager.reset();
	}

	public static boolean isEnabled() {
		return MobConfigManager.isEnabled() && WorldDifficultyConfigManager.isEnabled();
	}

	public static double resolveAddition(String attribute, double baseValue, Difficulty difficulty, boolean hardcore) {
		// World scaling is centered on Normal: Normal is the configured base value.
		int level = switch (difficulty == null ? Difficulty.NORMAL : difficulty) {
			case PEACEFUL -> -2;
			case EASY -> -1;
			case NORMAL -> 0;
			case HARD -> hardcore ? 2 : 1;
		};
		return WorldDifficultyConfigManager.resolveAddition(attribute, baseValue, level);
	}

	public static double resolveValue(String attribute, double baseValue, Difficulty difficulty, boolean hardcore) {
		double sanitizedBase = Math.max(0.0D, baseValue);
		double resolved = Math.max(0.0D, sanitizedBase + resolveAddition(attribute, sanitizedBase, difficulty, hardcore));
		return MobConfigManager.roundDifficultyScaleValue(resolved);
	}
}
