package madoku.craft.java.mob;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;

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
		JSONFormatAPIManager.ObjectBuilder list = JSONFormatAPIManager.object();
		for (String structure : DEFAULT_STRUCTURES) {
			list.object(structure, entry -> entry.put(RegionalDifficultyConfigManager.FIELD_ADJUSTMENT, defaultAdjustment(structure)));
		}
		return JSONFormatAPIManager.object()
			.put(RegionalDifficultyConfigManager.FIELD_ENABLED, true)
			.put(RegionalDifficultyConfigManager.FIELD_STRUCTURE_LIST, list.build())
			.build();
	}

	private static int defaultAdjustment(String structure) {
		return switch (structure) {
			case "trial-ruins", "shipwreck", "shipwreck-beached", "ruined-portal", "ruined-portal-desert",
				"ruined-portal-jungle", "ruined-portal-mountain", "ruined-portal-nether", "ruined-portal-ocean",
				"ruined-portal-swamp" -> 1;
			case "jungle-pyramid", "desert-pyramid", "pillager-outpost", "mineshaft", "mineshaft-mesa" -> 2;
			case "fortress", "mansion", "monument", "stronghold", "trial-chambers", "end-city", "ancient-city",
				"bastion-remnant" -> 3;
			default -> 0;
		};
	}
}

