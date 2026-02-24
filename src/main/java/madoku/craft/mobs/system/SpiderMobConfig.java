package madoku.craft.mobs.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import madoku.craft.API.system.MadokuJSONSystem;

/**
 * Loads spider and cave-spider settings from separate JSON files.
 */
public final class SpiderMobConfig {
	private static final String JSON_FOLDER_ID = "Mobs";
	private static final String JSON_FILE_SPIDER = "spider";
	private static final String JSON_FILE_CAVE_SPIDER = "cave-spider";

	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_SCALE_DIFFICULTY_STEP = "scale_difficulty_step";
	private static final String KEY_SPIDER_SPAWN_WEIGHT = "spider_spawn_weight";
	private static final String KEY_CAVE_SPIDER_SPAWN_WEIGHT = "cave_spider_spawn_weight";
	private static final String KEY_SPIDER_JOCKEY_SPAWN_WEIGHT = "spider_jockey_spawn_weight";

	private final SpiderTypeConfig spider;
	private final SpiderTypeConfig caveSpider;
	private final SpiderSpawnWeights spawnWeights;

	private SpiderMobConfig(SpiderTypeConfig spider, SpiderTypeConfig caveSpider, SpiderSpawnWeights spawnWeights) {
		this.spider = spider;
		this.caveSpider = caveSpider;
		this.spawnWeights = spawnWeights;
	}

	public static SpiderMobConfig load() {
		SpiderLoadResult spiderResult = loadSpiderType();
		SpiderTypeLoadResult caveResult = loadCaveSpiderType();
		return new SpiderMobConfig(spiderResult.typeConfig(), caveResult.typeConfig(), spiderResult.spawnWeights());
	}

	public boolean enabled() {
		return spider.enabled() || caveSpider.enabled();
	}

	public boolean spiderEnabled() {
		return spider.enabled();
	}

	public boolean caveSpiderEnabled() {
		return caveSpider.enabled();
	}

	public double spiderScaleDifficultyStep() {
		return spider.scaleDifficultyStep();
	}

	public double caveSpiderScaleDifficultyStep() {
		return caveSpider.scaleDifficultyStep();
	}

	public double spiderSpawnWeight() {
		return spawnWeights.spiderSpawnWeight();
	}

	public double caveSpiderSpawnWeight() {
		return spawnWeights.caveSpiderSpawnWeight();
	}

	public double spiderJockeySpawnWeight() {
		return spawnWeights.spiderJockeySpawnWeight();
	}

	public SpiderVariantConfig spider() {
		return new SpiderVariantConfig(spider.stats());
	}

	public SpiderVariantConfig caveSpider() {
		return new SpiderVariantConfig(caveSpider.stats());
	}

	private static SpiderLoadResult loadSpiderType() {
		JsonObject defaults = buildSpiderDefaults();
		MadokuJSONSystem.ManagedJSON managed = MadokuJSONSystem.load(JSON_FOLDER_ID, JSON_FILE_SPIDER, defaults);
		JsonObject root = managed.getRoot();

		boolean changed = false;

		boolean enabled = MobConfigJsonUtil.readBoolean(root, KEY_ENABLED, true);
		changed |= MobConfigJsonUtil.setBoolean(root, KEY_ENABLED, enabled);

		double scaleDifficultyStep = MobConfigJsonUtil.clamp(
			MobConfigJsonUtil.readDouble(root, KEY_SCALE_DIFFICULTY_STEP, 0.05),
			0.0,
			2.0,
			0.05
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_SCALE_DIFFICULTY_STEP, scaleDifficultyStep);

		double spiderSpawnWeight = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(root, KEY_SPIDER_SPAWN_WEIGHT, 90.0),
			90.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_SPIDER_SPAWN_WEIGHT, spiderSpawnWeight);

		double caveSpiderSpawnWeight = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(root, KEY_CAVE_SPIDER_SPAWN_WEIGHT, 5.0),
			5.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_CAVE_SPIDER_SPAWN_WEIGHT, caveSpiderSpawnWeight);

		double spiderJockeySpawnWeight = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(root, KEY_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0),
			5.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_SPIDER_JOCKEY_SPAWN_WEIGHT, spiderJockeySpawnWeight);

		MobConfigJsonUtil.UniversalMobStatsLoadResult statsResult = MobConfigJsonUtil.readUniversalStatOverrides(root);
		changed |= statsResult.changed();

		if (changed) {
			managed.save();
		}

		return new SpiderLoadResult(
			new SpiderTypeConfig(enabled, scaleDifficultyStep, statsResult.stats()),
			new SpiderSpawnWeights(spiderSpawnWeight, caveSpiderSpawnWeight, spiderJockeySpawnWeight)
		);
	}

	private static SpiderTypeLoadResult loadCaveSpiderType() {
		JsonObject defaults = buildCaveSpiderDefaults();
		MadokuJSONSystem.ManagedJSON managed = MadokuJSONSystem.load(JSON_FOLDER_ID, JSON_FILE_CAVE_SPIDER, defaults);
		JsonObject root = managed.getRoot();

		boolean changed = false;

		boolean enabled = MobConfigJsonUtil.readBoolean(root, KEY_ENABLED, true);
		changed |= MobConfigJsonUtil.setBoolean(root, KEY_ENABLED, enabled);

		double scaleDifficultyStep = MobConfigJsonUtil.clamp(
			MobConfigJsonUtil.readDouble(root, KEY_SCALE_DIFFICULTY_STEP, 0.05),
			0.0,
			2.0,
			0.05
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_SCALE_DIFFICULTY_STEP, scaleDifficultyStep);

		MobConfigJsonUtil.UniversalMobStatsLoadResult statsResult = MobConfigJsonUtil.readUniversalStatOverrides(root);
		changed |= statsResult.changed();

		if (changed) {
			managed.save();
		}

		return new SpiderTypeLoadResult(new SpiderTypeConfig(enabled, scaleDifficultyStep, statsResult.stats()));
	}

	private static JsonObject buildSpiderDefaults() {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(KEY_ENABLED, true);
		defaults.addProperty(KEY_SCALE_DIFFICULTY_STEP, 0.05);
		defaults.addProperty(KEY_SPIDER_SPAWN_WEIGHT, 90.0);
		defaults.addProperty(KEY_CAVE_SPIDER_SPAWN_WEIGHT, 5.0);
		defaults.addProperty(KEY_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0);
		for (Map.Entry<String, JsonElement> entry : MobConfigJsonUtil.buildUniversalStatDefaults(defaultSpiderStats()).entrySet()) {
			defaults.add(entry.getKey(), entry.getValue());
		}
		return defaults;
	}

	private static JsonObject buildCaveSpiderDefaults() {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(KEY_ENABLED, true);
		defaults.addProperty(KEY_SCALE_DIFFICULTY_STEP, 0.05);
		for (Map.Entry<String, JsonElement> entry : MobConfigJsonUtil.buildUniversalStatDefaults(defaultCaveStats()).entrySet()) {
			defaults.add(entry.getKey(), entry.getValue());
		}
		return defaults;
	}

	private static MobConfigJsonUtil.UniversalMobStats defaultSpiderStats() {
		return new MobConfigJsonUtil.UniversalMobStats(16.0, 0.0, 4.0, 0.3, 0.0, 0.7, 7);
	}

	private static MobConfigJsonUtil.UniversalMobStats defaultCaveStats() {
		return new MobConfigJsonUtil.UniversalMobStats(12.0, 0.0, 3.0, 0.3, 0.0, 0.7, 7);
	}

	private record SpiderLoadResult(SpiderTypeConfig typeConfig, SpiderSpawnWeights spawnWeights) {
	}

	private record SpiderTypeLoadResult(SpiderTypeConfig typeConfig) {
	}

	private record SpiderTypeConfig(
		boolean enabled,
		double scaleDifficultyStep,
		MobConfigJsonUtil.UniversalMobStats stats
	) {
	}

	private record SpiderSpawnWeights(
		double spiderSpawnWeight,
		double caveSpiderSpawnWeight,
		double spiderJockeySpawnWeight
	) {
	}

	public record SpiderVariantConfig(MobConfigJsonUtil.UniversalMobStats stats) {
	}
}
