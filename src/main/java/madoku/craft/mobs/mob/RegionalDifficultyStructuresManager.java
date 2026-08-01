package madoku.craft.mobs.mob;

import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;

import java.util.List;

/** Configuration group for structure difficulty adjustments. */
public final class RegionalDifficultyStructuresManager {
	private static final List<String> DEFAULT_STRUCTURES = List.of(
		"igloo", "swamp-hut", "buried-treasure", "nether-fossil", "ocean-ruin-cold", "ocean-ruin-warm",
		"village-plains", "village-desert", "village-savanna", "village-snowy", "village-taiga", "trial-ruins",
		"shipwreck", "shipwreck-beached", "ruined-portal", "ruined-portal-desert", "ruined-portal-jungle",
		"ruined-portal-mountain", "ruined-portal-nether", "ruined-portal-ocean", "ruined-portal-swamp", "jungle-pyramid",
		"desert-pyramid", "pillager-outpost", "mineshaft", "mineshaft-mesa", "fortress", "mansion", "monument",
		"stronghold", "trial-chambers", "end-city", "ancient-city", "bastion-remnant"
	);

	private RegionalDifficultyStructuresManager() {
	}

	public static JsonObject buildDefaults() {
		JSONFormatManager.ObjectBuilder list = JSONFormatManager.object();
		for (String structure : DEFAULT_STRUCTURES) {
			list.object(structure, entry -> entry.put(MobConfigManager.FIELD_ADJUSTMENT, 0));
		}
		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_STRUCTURE_LIST, list.build())
			.build();
	}
}

