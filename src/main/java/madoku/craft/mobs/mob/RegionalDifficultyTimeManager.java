package madoku.craft.mobs.mob;

import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;

import java.util.List;

/** Configuration group for time-based regional difficulty adjustments. */
public final class RegionalDifficultyTimeManager {
	public static final int UNBOUNDED_MAX_DAY = -1;

	private RegionalDifficultyTimeManager() {
	}

	public static JsonObject buildDefaults() {
		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.object(MobConfigManager.FIELD_DAY_LIST, days -> days
				.object("difficulty-zero", tier -> tier.put(MobConfigManager.FIELD_DAY_COUNT, 0).put(MobConfigManager.FIELD_ADJUSTMENT, 0))
				.object("difficulty-one", tier -> tier.put(MobConfigManager.FIELD_DAY_COUNT, 29).put(MobConfigManager.FIELD_ADJUSTMENT, 1))
				.object("difficulty-two", tier -> tier.put(MobConfigManager.FIELD_DAY_COUNT, 113).put(MobConfigManager.FIELD_ADJUSTMENT, 2))
				.object("difficulty-three", tier -> tier.put(MobConfigManager.FIELD_DAY_COUNT, 337).put(MobConfigManager.FIELD_ADJUSTMENT, 3)))
			.build();
	}

	public static List<TimeTierDefinition> defaultTimeTiers() {
		return List.of(
			new TimeTierDefinition(0, 28, 0),
			new TimeTierDefinition(29, 112, 1),
			new TimeTierDefinition(113, 336, 2),
			new TimeTierDefinition(337, UNBOUNDED_MAX_DAY, 3)
		);
	}

	public record TimeTierDefinition(int minDay, int maxDay, int adjustment) {
	}
}

