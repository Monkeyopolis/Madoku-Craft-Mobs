package madoku.craft.java.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONTypeAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MobConfigManager {
	private static final String INTERNAL_NESTED_VARIANT_MARKER = "__madoku_nested_variant";
	public static final String CONFIG_ROOT = "madoku-craft";
	public static final String MOBS_SYSTEM_FOLDER = "madoku-craft-mobs";
	public static final String ENTITIES_SYSTEM_FOLDER = "madoku-entities";
	public static final String REGIONAL_DIFFICULTY_SYSTEM_FOLDER = "madoku-regional-difficulty";
	public static final String MOBS_SETTINGS_FILE = "madoku-mobs";
	public static final String ENTITIES_SETTINGS_FILE = "madoku-entities";
	public static final String WORLD_DIFFICULTY_SETTINGS_FILE = "madoku-world-difficulty";
	public static final String REGIONAL_DIFFICULTY_SETTINGS_FILE = "madoku-regional-difficulty";
	public static final String REGIONAL_SCALING_FOLDER = "scaling";
	public static final String REGIONAL_STRUCTURES_FILE = "structures";
	public static final String REGIONAL_TIME_FILE = "time";
	public static final String REGIONAL_BIOMES_FILE = "biomes";

	public static final String FIELD_OVERRIDE_COMPONENTS = "override-components";
	public static final String FIELD_OVERRIDE_BEHAVIORS = "override-behaviors";
	public static final String FIELD_ENTITY = "entity";
	public static final String FIELD_MOB_ID = "mob-id";
	public static final String FIELD_WORLD_DIFFICULTY_SCALING = "world-difficulty-scaling";
	public static final String FIELD_REGIONAL_DIFFICULTY_SCALING_NEW = "regional-difficulty-scaling";
	public static final String FIELD_BIOME_LIST = "biome-list";
	public static final String FIELD_STRUCTURE_LIST = "structure-list";
	public static final String FIELD_DAY_LIST = "day-list";
	public static final String FIELD_DAY_COUNT = "day-count";
	public static final String FIELD_ADJUSTMENT = "adjustment";
	public static final String FIELD_TYPE = "type";
	public static final String FIELD_VALUE = "value";
	public static final String TYPE_PERCENTAGE = "percentage";
	public static final String TYPE_FLAT = "flat";
	private static volatile boolean initialized;
	private static volatile RuntimeConfig runtimeConfig = RuntimeConfig.disabled();

	public static final String FIELD_ENABLED = "enabled";

	public static final String FIELD_HEALTH = "health";
	public static final String FIELD_ARMOR = "armor";
	public static final String FIELD_DAMAGE = "damage";
	public static final String FIELD_TRUE_DAMAGE = "true-damage";
	public static final String FIELD_MOVEMENT_SPEED = "movement-speed";
	public static final String FIELD_SWIMMING_SPEED = "swimming-speed";
	public static final String FIELD_FLYING_SPEED = "flying-speed";
	public static final String FIELD_KNOCKBACK_RESISTANCE = "knockback-resistance";
	public static final String FIELD_SCALE = "scale";
	public static final String FIELD_EXPERIENCE_DROP = "experience-drop";

	public static final String FILE_CREEPER = "creeper";
	public static final String FILE_SKELETON = "skeleton";
	public static final String FILE_STRAY = "stray";
	public static final String FILE_BOGGED = "bogged";
	public static final String FILE_PARCHED = "parched";
	public static final String FILE_SPIDER = "spider";
	public static final String FILE_CAVE_SPIDER = "cave-spider";
	public static final String FILE_ZOMBIE = "zombie";
	public static final String FILE_HUSK = "husk";
	public static final String FILE_DROWNED = "drowned";
	public static final String FILE_ZOMBIE_VILLAGER = "zombie-villager";
	public static final String FILE_WITHER_SKELETON = "wither-skeleton";
	public static final String FILE_HAG = "hag";
	public static final String FILE_BEE = "bee";

	public static final String FIELD_CAN_PICK_UP_LOOT = "can-pick-up-loot";
	public static final String FIELD_TRIDENT_ATTACK = "trident-attack";
	public static final String FIELD_BOW_ATTACK = "bow-attack";
	public static final String FIELD_SPAWN_WEIGHT = "spawn-weight";
	public static final String FIELD_RANGED_DAMAGE = "ranged-damage";
	public static final String FIELD_EXPLOSION_POWER = "explosion-power";
	public static final String FIELD_ATTACK_INTERVAL = "attack-interval";
	public static final String FIELD_ATTACK_ACCURACY = "attack-accuracy";
	public static final String FIELD_CHARGE_INTERVAL = "charge-interval";
	public static final String FIELD_SPAWN_RULES = "mob-spawn-rules";
	public static final String FIELD_MOB_COMPONENTS = "mob-components";
	public static final String FIELD_MOB_BABY = "mob-baby";
	public static final String FIELD_AGEABLE = "ageable";
	public static final String FIELD_MOB_WEAPON = "mob-weapon";
	public static final String FIELD_MOB_EFFECT = "mob-effect";
	public static final String FIELD_MOB_DROPS = "mob-drops";
	public static final String FIELD_ITEM = "item";
	public static final String FIELD_EFFECT = "effect";
	public static final String FIELD_DURATION = "duration";
	public static final String FIELD_WEAPON_DAMAGE = "weapon-damage";
	public static final String FIELD_MOB_BEHAVIORS = "mob-behaviors";
	public static final String FIELD_MOB_GOALS = "mob-goals";
	public static final String FIELD_RETALIATE_WHEN_HURT = "retaliate-when-hurt";
	public static final String FIELD_CALLS_REINFORCEMENTS_WHEN_HURT = "calls-reinforcements-when-hurt";
	public static final String FIELD_POLLINATE_CROPS = "pollinate-crops";
	public static final String FIELD_CUSTOM_MOB_DROPS = "custom-mob-drops";
	public static final String FIELD_EQUIPMENT_SET = "equipment-set";
	public static final String FIELD_MOB_EQUIPMENT = "mob-equipment";
	public static final String FIELD_MOB_JOCKEY = "mob-jockey";
	public static final String FIELD_JOCKEY_PASSENGERS = "passengers";
	public static final String FIELD_JOCKEY_MOBS = "mobs";
	public static final String FIELD_JOCKEY_MOUNT = "mount";
	public static final String FIELD_SPAWN_ALTERNATIVE_MOB = "spawn-alternative-mob";
	public static final String FIELD_MOB = "mob";
	public static final String FIELD_OVERRIDE_SPAWN_RULES = "override-spawn-rules";
	public static final String FIELD_OVERRIDE_GOALS = "override-goals";
	public static final String FIELD_BABY_GROUP = "baby";
	public static final String FIELD_ADULT_GROUP = "adult";
	public static final String FIELD_PARTIAL_SET = "partial-set";
	public static final String FIELD_HALF_SET = "half-set";
	public static final String FIELD_FULL_SET = "full-set";
	public static final String FIELD_HELMET = "helmet";
	public static final String FIELD_CHESTPLATE = "chestplate";
	public static final String FIELD_LEGGINGS = "leggings";
	public static final String FIELD_BOOTS = "boots";
	public static final double DEFAULT_MOB_BABY_DURATION_SECONDS = 1200.0D;

	public static final String FIELD_CREEPER = "creeper";
	public static final String FIELD_CHARGED_CREEPER = "charged-creeper";
	public static final String FIELD_MOB_EXPLODE = "mob-explode";
	public static final String FIELD_DESTRUCTION_CHANCE = "destruction-chance";
	public static final String FIELD_GREIF_POWER = "greif-power";
	public static final String FIELD_FUSE_LENGTH = "fuse-length";

	private MobConfigManager() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}
		try {
			Path root = JSONAPIManager.getOrCreateGlobalRootDirectory();
			Path mobsDirectory = ensureDirectory(root.resolve(MOBS_SYSTEM_FOLDER));
			JSONFormatAPIManager.ensureManagedFile(
				mobsDirectory.resolve(MOBS_SETTINGS_FILE + ".json"),
				buildMobSystemDefaults(),
				JSONTypeAPIManager.STATIC_CONFIG,
				null
			);
			JSONFormatAPIManager.ensureManagedFile(
				mobsDirectory.resolve(ENTITIES_SETTINGS_FILE + ".json"),
				buildEntitySystemDefaults(),
				JSONTypeAPIManager.STATIC_CONFIG,
				null
			);
			JSONFormatAPIManager.ensureManagedFile(
				mobsDirectory.resolve(WORLD_DIFFICULTY_SETTINGS_FILE + ".json"),
				buildWorldDifficultyDefaults(),
				JSONTypeAPIManager.STATIC_CONFIG,
				null
			);
			Path regionalDirectory = ensureDirectory(mobsDirectory.resolve(REGIONAL_DIFFICULTY_SYSTEM_FOLDER));
			JSONFormatAPIManager.ensureManagedFile(
				mobsDirectory.resolve(REGIONAL_DIFFICULTY_SETTINGS_FILE + ".json"),
				buildRegionalDifficultyDefaults(),
				JSONTypeAPIManager.STATIC_CONFIG,
				null
			);
			JSONFormatAPIManager.ensureManagedFile(
				regionalDirectory.resolve(REGIONAL_STRUCTURES_FILE + ".json"),
				buildStructuresDefaults(),
				JSONTypeAPIManager.DYNAMIC_CONFIG,
				null
			);
			JSONFormatAPIManager.ensureManagedFile(
				regionalDirectory.resolve(REGIONAL_TIME_FILE + ".json"),
				buildTimeDefaults(),
				JSONTypeAPIManager.DYNAMIC_CONFIG,
				null
			);
			JSONFormatAPIManager.ensureManagedFile(
				regionalDirectory.resolve(REGIONAL_BIOMES_FILE + ".json"),
				buildBiomesDefaults(),
				JSONTypeAPIManager.DYNAMIC_CONFIG,
				null
			);
			JSONFormatAPIManager.ensureManagedFolder(
				getOrCreateEntityDirectory(),
				buildNewEntityDefaults(),
				MobConfigManager::buildNewDynamicEntityDefaults,
				(fileKey, ignored) -> true,
				MobConfigManager::normalizeDynamicEntityEntry
			);
			JSONFormatAPIManager.ensureManagedFolder(
				ensureDirectory(regionalDirectory.resolve(REGIONAL_SCALING_FOLDER)),
				Map.of(),
				MobConfigManager::buildNewDynamicScalingDefaults,
				(fileKey, ignored) -> true,
				MobConfigManager::normalizeDynamicScalingEntry
			);
			loadRuntimeConfig();
			initialized = true;
		} catch (IOException | RuntimeException exception) {
			runtimeConfig = RuntimeConfig.disabled();
			initialized = false;
		}
	}

	static Path getOrCreateMobSystemDirectory(String systemFolder) throws IOException {
		Path mobsDirectory = ensureDirectory(
			JSONAPIManager.getOrCreateGlobalRootDirectory().resolve(MOBS_SYSTEM_FOLDER)
		);
		return ensureDirectory(mobsDirectory.resolve(systemFolder));
	}

	static Path getOrCreateMobRootDirectory() throws IOException {
		return ensureDirectory(
			JSONAPIManager.getOrCreateGlobalRootDirectory().resolve(MOBS_SYSTEM_FOLDER)
		);
	}

	private static Path getOrCreateEntityDirectory() throws IOException {
		return ensureDirectory(getOrCreateMobRootDirectory().resolve(ENTITIES_SYSTEM_FOLDER));
	}

	public static synchronized void reset() {
		initialized = false;
		runtimeConfig = RuntimeConfig.disabled();
	}

	public static boolean isEnabled() {
		return runtimeConfig.enabled();
	}

	static Map<String, JsonObject> getRuntimeMobFiles() {
		return runtimeConfig.files();
	}

	private static void loadRuntimeConfig() throws IOException {
		Path mobsRootDirectory = getOrCreateMobRootDirectory();
		Path systemSettingsFile = mobsRootDirectory.resolve(MOBS_SETTINGS_FILE + ".json");
		JsonObject systemSettings = JSONFormatAPIManager.ensureManagedFile(systemSettingsFile, buildMobSystemDefaults());
		boolean systemEnabled = readBoolean(systemSettings, FIELD_ENABLED, true);
		Path settingsFile = mobsRootDirectory.resolve(ENTITIES_SETTINGS_FILE + ".json");
		JsonObject settingsRoot = JSONFormatAPIManager.ensureManagedFile(settingsFile, buildEntitySystemDefaults());
		boolean entitiesEnabled = readBoolean(settingsRoot, FIELD_ENABLED, true);
		Path entityDirectory = getOrCreateEntityDirectory();
		Map<String, JsonObject> files = new LinkedHashMap<>();
		if (Files.isDirectory(entityDirectory)) {
			try (var stream = Files.list(entityDirectory)) {
				for (Path file : stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
					String fileKey = file.getFileName().toString().substring(0, file.getFileName().toString().length() - 5).toLowerCase();
					files.put(fileKey, JSONFormatAPIManager.readManagedDocument(file).data());
				}
			}
		}
		runtimeConfig = systemEnabled && entitiesEnabled
			? new RuntimeConfig(true, Map.copyOf(files))
			: RuntimeConfig.disabled();
	}

	private static Path ensureDirectory(Path path) throws IOException {
		Files.createDirectories(path);
		return path;
	}

	public static JsonObject buildEntitySystemDefaults() {
		return JSONFormatAPIManager.object().put(FIELD_ENABLED, true).build();
	}

	public static JsonObject buildMobSystemDefaults() {
		return JSONFormatAPIManager.object().put(FIELD_ENABLED, true).build();
	}

	public static JsonObject buildWorldDifficultyDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, true)
			.object(FIELD_WORLD_DIFFICULTY_SCALING, root -> root
				.put(FIELD_HEALTH, buildScalingRule(TYPE_PERCENTAGE, 0.25D))
				.put(FIELD_DAMAGE, buildScalingRule(TYPE_PERCENTAGE, 0.10D))
				.put(FIELD_MOVEMENT_SPEED, buildScalingRule(TYPE_PERCENTAGE, 0.10D))
				.put(FIELD_EXPERIENCE_DROP, buildScalingRule(TYPE_PERCENTAGE, 0.25D))
				.put(FIELD_FLYING_SPEED, buildScalingRule(TYPE_PERCENTAGE, 0.10D))
				.put(FIELD_EXPLOSION_POWER, buildScalingRule(TYPE_PERCENTAGE, 0.10D))
				.put(FIELD_RANGED_DAMAGE, buildScalingRule(TYPE_PERCENTAGE, 0.10D))
				.put(FIELD_SWIMMING_SPEED, buildScalingRule(TYPE_PERCENTAGE, 0.10D)))
			.build();
	}

	public static JsonObject buildRegionalDifficultyDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, true)
			.object(FIELD_REGIONAL_DIFFICULTY_SCALING_NEW, root -> root
				.put(FIELD_HEALTH, buildScalingRule(TYPE_PERCENTAGE, 0.20D))
				.put(FIELD_MOVEMENT_SPEED, buildScalingRule(TYPE_PERCENTAGE, 0.02D))
				.put(FIELD_ARMOR, buildScalingRule(TYPE_PERCENTAGE, 0.02D))
				.put(FIELD_KNOCKBACK_RESISTANCE, buildScalingRule(TYPE_FLAT, 0.02D))
				.put(FIELD_EXPERIENCE_DROP, buildScalingRule(TYPE_PERCENTAGE, 0.10D)))
			.build();
	}

	private static JsonObject buildScalingRule(String type, double value) {
		return JSONFormatAPIManager.object().put(FIELD_TYPE, type).put(FIELD_VALUE, value).build();
	}

	private static Map<String, JsonObject> buildNewEntityDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		for (String key : List.of(FILE_BEE, FILE_BOGGED, FILE_CAVE_SPIDER, FILE_CREEPER, FILE_DROWNED,
			FILE_HAG, FILE_HUSK, FILE_PARCHED, FILE_SKELETON, FILE_SPIDER, FILE_STRAY,
			FILE_WITHER_SKELETON, FILE_ZOMBIE, FILE_ZOMBIE_VILLAGER)) {
			defaults.put(key, buildNewDynamicEntityDefaults(key));
		}
		return defaults;
	}

	public static JsonObject buildNewDynamicEntityDefaults(String fileKey) {
		String key = normalizeFileKey(fileKey);
		String mobId = key.contains(":")
			? key
			: "hag".equals(key) ? "madoku-craft:hag" : "minecraft:" + key;
		mobId = JSONAPIManager.normalizeRegistryIdentifierForJson(mobId);
		JsonObject entityVariant = buildEntityVariantDefaults(key);
		if (List.of(FILE_BOGGED, FILE_DROWNED, FILE_HUSK, FILE_PARCHED, FILE_SKELETON, FILE_STRAY,
			FILE_WITHER_SKELETON, FILE_ZOMBIE, FILE_ZOMBIE_VILLAGER).contains(key)) {
			entityVariant.getAsJsonObject(FIELD_MOB_COMPONENTS).addProperty(FIELD_WEAPON_DAMAGE, false);
		}
		JsonObject entityRoot = new JsonObject();
		entityRoot.addProperty(FIELD_CUSTOM_MOB_DROPS, true);
		entityRoot.addProperty(FIELD_WORLD_DIFFICULTY_SCALING, true);
		entityRoot.addProperty(FIELD_REGIONAL_DIFFICULTY_SCALING_NEW, true);
		addSiblingVariants(entityRoot, key, entityVariant);
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, true)
			.put(FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(FIELD_OVERRIDE_COMPONENTS, true)
			.put(FIELD_OVERRIDE_BEHAVIORS, true)
			.put(FIELD_OVERRIDE_GOALS, true)
			.put(FIELD_MOB_ID, mobId)
			.put(FIELD_ENTITY, entityRoot)
			.build();
	}

	private static void addSiblingVariants(JsonObject entityRoot, String primaryKey, JsonObject primaryVariant) {
		if (entityRoot == null || primaryKey == null || primaryVariant == null) {
			return;
		}
		JsonObject primary = primaryVariant.deepCopy();
		JsonObject shared = primaryVariant.deepCopy();
		for (String key : List.copyOf(primary.keySet())) {
			JsonElement value = primary.get(key);
			if (value != null && value.isJsonObject() && EntityConfigManager.isVariantKey(key)) {
				if (!isNestedVariantEntry(value)) {
					primary.remove(key);
				}
				shared.remove(key);
			}
		}
		entityRoot.add(primaryKey, primary);
		for (Map.Entry<String, JsonElement> entry : primaryVariant.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			if (value == null || !value.isJsonObject() || !EntityConfigManager.isVariantKey(key)
				|| isNestedVariantEntry(value)) {
				continue;
			}
			JsonObject sibling = value.getAsJsonObject();
			JsonObject resolvedSibling = isSpawnAlternativeVariant(sibling)
				? sibling.deepCopy()
				: mergeVariantEntries(shared, sibling);
			stripInternalNestedVariantMarkers(resolvedSibling);
			entityRoot.add(key, resolvedSibling);
		}
		stripInternalNestedVariantMarkers(primary);
		entityRoot.remove(primaryKey);
		entityRoot.add(primaryKey, primary);
	}

	private static boolean isSpawnAlternativeVariant(JsonObject variant) {
		if (variant == null) {
			return false;
		}
		JsonElement spawnRulesElement = variant.get(FIELD_SPAWN_RULES);
		if (spawnRulesElement == null || !spawnRulesElement.isJsonObject()) {
			return false;
		}
		JsonElement alternativeElement = spawnRulesElement.getAsJsonObject().get(FIELD_SPAWN_ALTERNATIVE_MOB);
		if (alternativeElement == null || !alternativeElement.isJsonObject()) {
			return false;
		}
		return true;
	}

	private static JsonObject buildSpawnAlternativeVariant(JsonObject spawnRules) {
		JsonObject variant = new JsonObject();
		variant.add(FIELD_SPAWN_RULES, spawnRules == null ? new JsonObject() : spawnRules);
		return variant;
	}

	private static boolean isNestedVariantEntry(JsonElement value) {
		return value != null && value.isJsonObject()
			&& value.getAsJsonObject().has(INTERNAL_NESTED_VARIANT_MARKER);
	}

	private static void addNestedVariant(JsonObject parent, String key, JsonObject variant) {
		if (parent == null || key == null || variant == null) {
			return;
		}
		variant.addProperty(INTERNAL_NESTED_VARIANT_MARKER, true);
		parent.add(key, variant);
	}

	private static void stripInternalNestedVariantMarkers(JsonObject root) {
		if (root == null) {
			return;
		}
		root.remove(INTERNAL_NESTED_VARIANT_MARKER);
		for (JsonElement value : root.asMap().values()) {
			if (value != null && value.isJsonObject()) {
				stripInternalNestedVariantMarkers(value.getAsJsonObject());
			}
		}
	}

	private static JsonObject mergeVariantEntries(JsonObject shared, JsonObject variant) {
		JsonObject merged = shared == null ? new JsonObject() : shared.deepCopy();
		if (variant == null) {
			return merged;
		}
		for (Map.Entry<String, JsonElement> entry : variant.entrySet()) {
			JsonElement current = merged.get(entry.getKey());
			if (current != null && current.isJsonObject() && entry.getValue().isJsonObject()) {
				mergeVariantEntriesInto(current.getAsJsonObject(), entry.getValue().getAsJsonObject());
			} else {
				merged.add(entry.getKey(), entry.getValue().deepCopy());
			}
		}
		return merged;
	}

	private static void mergeVariantEntriesInto(JsonObject target, JsonObject source) {
		for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
			JsonElement current = target.get(entry.getKey());
			if (current != null && current.isJsonObject() && entry.getValue().isJsonObject()) {
				mergeVariantEntriesInto(current.getAsJsonObject(), entry.getValue().getAsJsonObject());
			} else {
				target.add(entry.getKey(), entry.getValue().deepCopy());
			}
		}
	}

	private static void addMobBabyComponent(JsonObject variant, boolean ageable) {
		if (variant == null) {
			return;
		}
		JsonObject components = variant.getAsJsonObject(FIELD_MOB_COMPONENTS);
		if (components == null) {
			return;
		}
		JsonObject mobBaby = new JsonObject();
		JsonObject ageableGroup = new JsonObject();
		ageableGroup.addProperty(FIELD_ENABLED, ageable);
		ageableGroup.addProperty(FIELD_DURATION, ageable ? DEFAULT_MOB_BABY_DURATION_SECONDS : 0.0D);
		mobBaby.add(FIELD_AGEABLE, ageableGroup);
		components.add(FIELD_MOB_BABY, mobBaby);
	}

	private static JsonObject buildEntityVariantDefaults(String key) {
		return switch (key) {
			case FILE_BEE -> buildBeeDefaults();
			case FILE_BOGGED -> buildBowSkeletonVariantDefaults(12.0D, 2.0D, 0.27D, 1.0D, 7, 4.0D, "minecraft:poison", "minecraft-equipment-bogged.json", "minecraft-entities-bogged.json", "bogged-jockey");
			case FILE_CAVE_SPIDER -> buildCaveSpiderVariantDefaults();
			case FILE_CREEPER -> buildCreeperVariantDefaults();
			case FILE_DROWNED -> buildDrownedVariantDefaults();
			case FILE_HAG -> buildVariant(
				buildComponents(40.0D, 1.0D, null, 0.25D, null, null, 0.2D, null, 11, null, null, null, null, null, null, null, 0, null, null, null),
				new JsonObject(), new JsonObject(), new JsonObject());
			case FILE_HUSK -> buildHuskVariantDefaults();
			case FILE_PARCHED -> buildBowSkeletonVariantDefaults(12.0D, 2.0D, 0.27D, 1.0D, 7, 4.0D, "minecraft:slowness", "minecraft-equipment-parched.json", "minecraft-entities-parched.json", "parched-jockey");
			case FILE_SKELETON -> buildSkeletonVariantDefaults();
			case FILE_SPIDER -> buildSpiderVariantDefaults();
			case FILE_STRAY -> buildBowSkeletonVariantDefaults(12.0D, 2.0D, 0.27D, 1.0D, 7, 4.0D, "minecraft:weakness", "minecraft-equipment-stray.json", "minecraft-entities-stray.json", "stray-jockey");
			case FILE_WITHER_SKELETON -> buildWitherSkeletonVariantDefaults();
			case FILE_ZOMBIE -> buildZombieVariantDefaults();
			case FILE_ZOMBIE_VILLAGER -> buildZombieVillagerVariantDefaults();
			default -> buildVariant(new JsonObject(), new JsonObject(), new JsonObject(), new JsonObject());
		};
	}

	private static JsonObject buildVariant(JsonObject components, JsonObject spawnRules, JsonObject behaviors, JsonObject goals) {
		JsonObject variant = new JsonObject();
		variant.add(FIELD_SPAWN_RULES, spawnRules == null ? new JsonObject() : spawnRules);
		variant.add(FIELD_MOB_COMPONENTS, components == null ? new JsonObject() : components);
		variant.add(FIELD_MOB_BEHAVIORS, behaviors == null ? new JsonObject() : behaviors);
		variant.add(FIELD_MOB_GOALS, goals == null ? new JsonObject() : goals);
		return variant;
	}

	private static JsonObject buildComponents(
		Double health, Double armor, Double damage, Double movementSpeed, Double swimmingSpeed,
		Double flyingSpeed, Double knockbackResistance, Double scale, Integer experienceDrop,
		Double rangedDamage, Double attackAccuracy, Double attackInterval, Double chargeInterval,
		String mobDrops, String weapon, String effect, int effectDuration, Double trueDamage,
		Double explosionPower, Double fuseLength
	) {
		JsonObject components = buildMobComponentsDefaults(
			health, armor, damage, movementSpeed, swimmingSpeed, flyingSpeed, knockbackResistance,
			scale, experienceDrop, rangedDamage, attackAccuracy, attackInterval, chargeInterval, mobDrops
		);
		if (weapon != null) components.add(FIELD_MOB_WEAPON, buildMobWeaponDefaults(weapon));
		if (effect != null) components.add(FIELD_MOB_EFFECT, buildMobEffectDefaults(effect, effectDuration));
		if (trueDamage != null) components.addProperty(FIELD_TRUE_DAMAGE, trueDamage);
		if (explosionPower != null) components.addProperty(FIELD_EXPLOSION_POWER, explosionPower);
		if (fuseLength != null) components.addProperty(FIELD_FUSE_LENGTH, fuseLength);
		return components;
	}

	private static JsonObject buildSpawnRules(double weight) {
		JsonObject rules = new JsonObject();
		rules.addProperty(FIELD_SPAWN_WEIGHT, weight);
		return rules;
	}

	private static JsonObject buildSpawnRules(double weight, String equipmentReference) {
		JsonObject rules = buildSpawnRules(weight);
		if (equipmentReference != null) {
			JsonObject equipment = new JsonObject();
			equipment.addProperty(FIELD_ENABLED, true);
			equipment.addProperty(FIELD_MOB_EQUIPMENT, equipmentReference);
			rules.add(FIELD_EQUIPMENT_SET, equipment);
		}
		return rules;
	}

	private static JsonObject buildGoals(String... keys) {
		JsonObject goals = new JsonObject();
		if (keys == null) return goals;
		for (String key : keys) {
			int priority = "hurt-by-target".equals(key) ? 1 : "target-player".equals(key) ? 2 : "trident-attack".equals(key) ? 3 : 4;
			int cooldown = "melee-attack".equals(key) || "ranged-attack".equals(key) || "trident-attack".equals(key) ? 20 : 0;
			addMobGoal(goals, key, true, priority, 100.0D, cooldown);
		}
		return goals;
	}

	private static JsonObject buildBehavior(boolean canPickUpLoot, boolean bowAttack, boolean tridentAttack, boolean retaliate) {
		JsonObject behavior = new JsonObject();
		if (canPickUpLoot) behavior.addProperty(FIELD_CAN_PICK_UP_LOOT, true);
		if (bowAttack) behavior.addProperty(FIELD_BOW_ATTACK, true);
		if (tridentAttack) behavior.addProperty(FIELD_TRIDENT_ATTACK, true);
		if (retaliate) behavior.addProperty(FIELD_RETALIATE_WHEN_HURT, true);
		behavior.addProperty(FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, false);
		return behavior;
	}

	private static JsonObject buildBeeDefaults() {
		JsonObject pollination = new JsonObject();
		pollination.addProperty(FIELD_ENABLED, true);
		pollination.addProperty("nectar-total-charges", 10);
		pollination.addProperty("search-duration-ticks", 1200);
		pollination.addProperty("search-radius-horizontal", 12);
		pollination.addProperty("search-radius-vertical", 4);
		pollination.addProperty("crop-reach-distance-sqr", 0.5D);
		pollination.addProperty("crop-reservation-ttl-ticks", 50);
		pollination.addProperty("move-speed-modifier", 1.0D);
		pollination.addProperty("arrival-threshold", 0.1D);
		pollination.addProperty("position-change-chance", 10);
		pollination.addProperty("hover-height-within-crop", 0.5D);
		pollination.addProperty("hover-pos-offset", 0.33333334D);
		pollination.addProperty("charge-interval-ticks", 20);
		pollination.addProperty("charges-spend-divisor", 2);
		pollination.addProperty("growth-percent-per-charge", 2.0D);
		JsonObject behavior = new JsonObject();
		behavior.add(FIELD_POLLINATE_CROPS, pollination);
		JsonObject variant = buildVariant(
			buildComponents(10.0D, null, 2.0D, 0.30D, null, 0.60D, null, 0.5D, null, null, null, null, null, "minecraft-entities-bee.json", null, "minecraft:poison", 60, null, null, null),
			buildSpawnRules(100.0D), behavior, buildGoals("breed", "hurt-by-target", "become-angry-target")
		);
		addNestedVariant(variant, FIELD_ADULT_GROUP, buildVariant(buildComponents(null, null, null, null, null, null, null, null, 3, null, null, null, null, null, null, null, 0, null, null, null), buildSpawnRules(80.0D), new JsonObject(), new JsonObject()));
		JsonObject baby = buildVariant(
			buildComponents(5.0D, null, null, 0.25D, null, 0.0D, null, 0.45D, 1, null, null, null, null, null, null, null, 0, null, null, null),
			buildSpawnRules(20.0D), new JsonObject(), new JsonObject());
		addMobBabyComponent(baby, true);
		addNestedVariant(variant, FIELD_BABY_GROUP, baby);
		return variant;
	}

	private static JsonObject buildBowSkeletonVariantDefaults(
		double health, double damage, double movementSpeed, double scale, int experienceDrop,
		double rangedDamage, String effect, String equipmentReference, String mobDrops, String jockeyKey
	) {
		JsonObject variant = buildVariant(
			buildComponents(health, null, damage, movementSpeed, null, null, null, scale, experienceDrop,
				rangedDamage, 0.7D, 20.0D, 10.0D, mobDrops, "minecraft:bow", effect, 15, null, null, null),
			buildSpawnRules(90.0D, equipmentReference), buildBehavior(false, true, false, false),
			buildGoals("hurt-by-target", "target-player", "ranged-attack")
		);
		String passengerType = JSONAPIManager.normalizeRegistryIdentifierForJson(
			"minecraft:" + jockeyKey.replace("-jockey", "")
		);
		variant.add(jockeyKey, buildVariant(new JsonObject(), buildJockeySpawnRules(10.0D, passengerType, "minecraft:bow", "minecraft:spider"), new JsonObject(), new JsonObject()));
		return variant;
	}

	private static JsonObject buildJockeySpawnRules(double weight, String passengerType, String passengerWeapon, String mountType) {
		return buildJockeySpawnRules(
			weight,
			mountType,
			buildJockeyMobDefinition(passengerType, passengerWeapon)
		);
	}

	private static JsonObject buildJockeySpawnRules(double weight, String mountType, JsonObject... passengerDefinitions) {
		JsonObject jockey = new JsonObject();
		jockey.addProperty(FIELD_ENABLED, true);
		JsonArray passengerMobs = new JsonArray();
		if (passengerDefinitions != null) {
			for (JsonObject passengerDefinition : passengerDefinitions) {
				if (passengerDefinition != null && !passengerDefinition.entrySet().isEmpty()) {
					passengerMobs.add(passengerDefinition);
				}
			}
		}
		JsonObject passengers = new JsonObject();
		passengers.add(FIELD_JOCKEY_MOBS, passengerMobs);
		jockey.add(FIELD_JOCKEY_PASSENGERS, passengers);
		JsonObject mount = new JsonObject();
		mount.addProperty(FIELD_MOB_ID, JSONAPIManager.normalizeRegistryIdentifierForJson(mountType));
		jockey.add(FIELD_JOCKEY_MOUNT, mount);
		JsonObject rules = buildSpawnRules(weight);
		rules.add(FIELD_MOB_JOCKEY, jockey);
		return rules;
	}

	private static JsonObject buildJockeyMobDefinition(String mobId, String weaponId) {
		JsonObject definition = new JsonObject();
		definition.addProperty(FIELD_MOB_ID, JSONAPIManager.normalizeRegistryIdentifierForJson(mobId));
		if (weaponId != null && !weaponId.isBlank()) {
			JsonObject weapon = new JsonObject();
			weapon.addProperty(FIELD_ITEM, JSONAPIManager.normalizeRegistryIdentifierForJson(weaponId));
			definition.add(FIELD_MOB_WEAPON, weapon);
		}
		return definition;
	}

	private static JsonObject buildCaveSpiderVariantDefaults() {
		return buildVariant(
			buildComponents(12.0D, null, 5.0D, 0.27D, null, null, null, 0.5D, 7, null, null, null, null,
				"minecraft-entities-cave-spider.json", null, "minecraft:poison", 15, null, null, null),
			buildSpawnRules(100.0D), buildBehavior(false, false, false, true),
			buildGoals("hurt-by-target", "target-player", "melee-attack")
		);
	}

	private static JsonObject buildCreeperVariantDefaults() {
		JsonObject variant = buildVariant(
			buildComponents(12.0D, 1.0D, null, 0.27D, null, null, 0.10D, 1.0D, 7, null, null, null, null, null, null, null, 0, null, 3.0D, 30.0D),
			buildSpawnRules(90.0D), buildCreeperBehavior(0.4D, 0.4D),
			buildGoals("ranged-attack", "target-player", "hurt-by-target")
		);
		variant.add("charged-creeper", buildVariant(
			buildComponents(12.0D, 1.0D, null, 0.30D, null, null, 0.20D, 1.0D, 11, null, null, null, null, null, null, null, 0, null, 5.0D, 25.0D),
			buildSpawnRules(10.0D), buildCreeperBehavior(0.6D, 0.6D),
			buildGoals("ranged-attack", "target-player", "hurt-by-target")
		));
		return variant;
	}

	private static JsonObject buildCreeperBehavior(double destructionChance, double griefPower) {
		JsonObject behavior = new JsonObject();
		JsonObject explode = new JsonObject();
		explode.addProperty(FIELD_ENABLED, true);
		explode.addProperty(FIELD_DESTRUCTION_CHANCE, destructionChance);
		explode.addProperty(FIELD_GREIF_POWER, griefPower);
		behavior.add(FIELD_MOB_EXPLODE, explode);
		return behavior;
	}

	private static JsonObject buildDrownedVariantDefaults() {
		JsonObject melee = buildVariant(
			buildComponents(20.0D, null, 5.0D, 0.24D, 0.012D, null, null, 1.0D, 7, null, null, null, null,
				"minecraft-entities-drowned.json", null, null, 0, null, null, null),
			buildSpawnRules(90.0D, "minecraft-equipment-drowned.json"), buildBehavior(true, false, false, false),
			buildGoals("hurt-by-target", "target-player")
		);
		addNestedVariant(melee, FIELD_ADULT_GROUP, buildVariant(new JsonObject(), buildSpawnRules(90.0D), new JsonObject(), new JsonObject()));
		JsonObject meleeBaby = buildVariant(
			buildComponents(10.0D, null, 2.5D, null, null, null, null, null, 3, null, null, null, null, "minecraft-entities-drowned.json", null, null, 0, null, null, null),
			buildSpawnRules(10.0D), new JsonObject(), new JsonObject());
		addMobBabyComponent(meleeBaby, true);
		addNestedVariant(melee, FIELD_BABY_GROUP, meleeBaby);
		JsonObject ranged = buildVariant(
			buildComponents(null, null, null, 0.24D, 0.012D, null, null, null, null, 9.0D, 0.8D, 30.0D, 15.0D,
				"minecraft-entities-drowned.json", "minecraft:trident", null, 0, null, null, null),
			buildSpawnRules(10.0D, "minecraft-equipment-drowned.json"), buildBehavior(true, false, true, false),
			buildGoals("trident-attack", "hurt-by-target", "target-player")
		);
		addNestedVariant(ranged, FIELD_ADULT_GROUP, buildVariant(
			buildComponents(20.0D, null, 5.0D, null, null, null, null, 1.0D, 7, null, null, null, null, null, null, null, 0, null, null, null),
			buildSpawnRules(90.0D), new JsonObject(), new JsonObject()));
		JsonObject rangedBaby = buildVariant(
			buildComponents(10.0D, null, 2.5D, null, null, null, null, null, 3, 4.5D, null, null, null, null, null, null, 0, null, null, null),
			buildSpawnRules(10.0D), new JsonObject(), new JsonObject());
		addMobBabyComponent(rangedBaby, true);
		addNestedVariant(ranged, FIELD_BABY_GROUP, rangedBaby);
		melee.add("ranged-drowned", ranged);
		return melee;
	}

	private static JsonObject buildHuskVariantDefaults() {
		JsonObject variant = buildVariant(
			buildComponents(28.0D, 2.0D, 7.0D, 0.18D, null, null, 0.4D, 1.0D, 7, null, null, null, null,
				"minecraft-entities-husk.json", null, "minecraft:slowness", 15, null, null, null),
			buildSpawnRules(90.0D, "minecraft-equipment-husk.json"), buildBehavior(true, false, false, false),
			buildGoals("hurt-by-target", "target-player", "melee-attack")
		);
		addNestedVariant(variant, FIELD_ADULT_GROUP, buildVariant(new JsonObject(), buildSpawnRules(90.0D), new JsonObject(), new JsonObject()));
		JsonObject baby = buildVariant(
			buildComponents(14.0D, 1.0D, 3.5D, null, null, null, 0.2D, null, 3, null, null, null, null, null, null, null, 0, null, null, null),
			buildSpawnRules(10.0D), new JsonObject(), new JsonObject());
		addMobBabyComponent(baby, true);
		addNestedVariant(variant, FIELD_BABY_GROUP, baby);
		JsonObject huskJockey = buildVariant(
			new JsonObject(), buildSpawnRules(10.0D, "minecraft-equipment-husk.json"), new JsonObject(), new JsonObject());
		addNestedVariant(huskJockey, FIELD_ADULT_GROUP, buildVariant(
			new JsonObject(),
			buildJockeySpawnRules(
				90.0D,
				"minecraft:camel_husk",
				buildJockeyMobDefinition("minecraft:husk", "minecraft:golden_spear"),
				buildJockeyMobDefinition("minecraft:parched", "minecraft:bow")
			),
			new JsonObject(), new JsonObject()));
		JsonObject huskJockeyBaby = buildVariant(
			new JsonObject(),
			buildJockeySpawnRules(
				10.0D,
				"minecraft:chicken",
				buildJockeyMobDefinition("minecraft:husk", null)
			),
			new JsonObject(), new JsonObject());
		addMobBabyComponent(huskJockeyBaby, true);
		addNestedVariant(huskJockey, FIELD_BABY_GROUP, huskJockeyBaby);
		variant.add("husk-jockey", huskJockey);
		return variant;
	}

	private static JsonObject buildSkeletonVariantDefaults() {
		JsonObject variant = buildVariant(
			buildComponents(16.0D, null, null, 0.24D, null, null, null, 1.0D, 7, 5.0D, 0.7D, 20.0D, 10.0D,
				"minecraft-entities-skeleton.json", "minecraft:bow", null, 0, null, null, null),
			buildSpawnRules(80.0D, "minecraft-equipment-skeleton.json"), buildBehavior(false, true, false, false),
			buildGoals("hurt-by-target", "target-player", "ranged-attack")
		);
		JsonObject meleeComponents = buildComponents(20.0D, null, 5.0D, 0.24D, null, null, null, 1.0D, 7, null, null, null, null,
			"minecraft-entities-skeleton.json", "empty", null, 0, 1.0D, null, null);
		variant.add("melee-skeleton", buildVariant(meleeComponents, buildSpawnRules(10.0D, "minecraft-equipment-skeleton.json"), new JsonObject(), buildGoals("hurt-by-target", "target-player", "melee-attack")));
		variant.add("skeleton-jockey", buildVariant(new JsonObject(), buildJockeySpawnRules(10.0D, "minecraft:skeleton", "minecraft:bow", "minecraft:spider"), new JsonObject(), new JsonObject()));
		return variant;
	}

	private static JsonObject buildSpiderVariantDefaults() {
		JsonObject variant = buildVariant(
			buildComponents(16.0D, null, 4.0D, 0.30D, null, null, null, 0.5D, 7, null, null, null, null,
				"minecraft-entities-spider.json", null, null, 0, null, null, null),
			buildSpawnRules(80.0D), buildBehavior(false, false, false, true),
			buildGoals("hurt-by-target", "target-player", "melee-attack")
		);
		JsonObject alternative = new JsonObject();
		alternative.addProperty(FIELD_ENABLED, true);
		alternative.addProperty(FIELD_MOB, JSONAPIManager.normalizeRegistryIdentifierForJson("minecraft:cave_spider"));
		JsonObject caveSpiderRules = buildSpawnRules(10.0D);
		caveSpiderRules.add(FIELD_SPAWN_ALTERNATIVE_MOB, alternative);
		variant.add("cave-spider", buildSpawnAlternativeVariant(caveSpiderRules));
		variant.add("spider-jockey", buildVariant(new JsonObject(), buildJockeySpawnRules(10.0D, "minecraft:skeleton", "minecraft:bow", "minecraft:spider"), new JsonObject(), new JsonObject()));
		return variant;
	}

	private static JsonObject buildWitherSkeletonVariantDefaults() {
		JsonObject variant = buildVariant(
			buildComponents(20.0D, null, 7.0D, 0.25D, null, null, null, 1.0D, 11, null, null, null, null,
				"minecraft-entities-wither-skeleton.json", "minecraft:netherite_sword", "minecraft:wither", 15, null, null, null),
			buildSpawnRules(90.0D, "minecraft-equipment-wither-skeleton.json"), new JsonObject(),
			buildGoals("hurt-by-target", "target-player", "melee-attack")
		);
		variant.add("ranged-wither-skeleton", buildVariant(
			buildComponents(20.0D, null, 7.0D, 0.25D, null, null, null, 1.0D, 11, 6.0D, 0.7D, 20.0D, 10.0D,
				"minecraft-entities-wither-skeleton.json", "minecraft:bow", "minecraft:wither", 15, null, null, null),
			buildSpawnRules(10.0D, "minecraft-equipment-wither-skeleton.json"), buildBehavior(false, true, false, false),
			buildGoals("hurt-by-target", "target-player", "ranged-attack")));
		return variant;
	}

	private static JsonObject buildZombieVariantDefaults() {
		JsonObject variant = buildVariant(
			buildComponents(24.0D, 1.0D, 6.0D, 0.21D, null, null, 0.2D, 1.0D, 7, null, null, null, null,
				"minecraft-entities-zombie.json", null, null, 0, null, null, null),
			buildSpawnRules(80.0D, "minecraft-equipment-zombie.json"), buildBehavior(false, false, false, false),
			buildGoals("hurt-by-target", "target-player", "melee-attack")
		);
		addZombieAgeVariants(variant);
		JsonObject zombieJockey = buildVariant(
			new JsonObject(), buildSpawnRules(10.0D, "minecraft-equipment-zombie.json"), new JsonObject(), new JsonObject());
		addNestedVariant(zombieJockey, FIELD_ADULT_GROUP, buildVariant(
			new JsonObject(), buildJockeySpawnRules(90.0D, "minecraft:zombie", "minecraft:stone_spear", "minecraft:zombie_horse"), new JsonObject(), new JsonObject()));
		JsonObject zombieJockeyBaby = buildVariant(
			new JsonObject(), buildJockeySpawnRules(10.0D, "minecraft:zombie", "minecraft:stone_spear", "minecraft:chicken"), new JsonObject(), new JsonObject());
		addMobBabyComponent(zombieJockeyBaby, true);
		addNestedVariant(zombieJockey, FIELD_BABY_GROUP, zombieJockeyBaby);
		variant.add("zombie-jockey", zombieJockey);
		JsonObject alternative = new JsonObject();
		alternative.addProperty(FIELD_ENABLED, true);
		alternative.addProperty(FIELD_MOB, JSONAPIManager.normalizeRegistryIdentifierForJson("minecraft:zombie_villager"));
		JsonObject villagerRules = buildSpawnRules(10.0D);
		villagerRules.add(FIELD_SPAWN_ALTERNATIVE_MOB, alternative);
		JsonObject zombieVillager = buildSpawnAlternativeVariant(villagerRules);
		variant.add("zombie-villager", zombieVillager);
		return variant;
	}

	private static void addZombieAgeVariants(JsonObject variant) {
		if (variant == null) {
			return;
		}
		addNestedVariant(variant, FIELD_ADULT_GROUP, buildVariant(new JsonObject(), buildSpawnRules(90.0D), new JsonObject(), new JsonObject()));
		JsonObject baby = buildVariant(
			buildComponents(12.0D, 0.0D, 3.0D, 0.21D, null, null, 0.0D, null, 3, null, null, null, null, null, null, null, 0, null, null, null),
			buildSpawnRules(10.0D), buildBehavior(false, false, false, false), new JsonObject());
		addMobBabyComponent(baby, true);
		addNestedVariant(variant, FIELD_BABY_GROUP, baby);
	}

	private static JsonObject buildZombieVillagerVariantDefaults() {
		JsonObject variant = buildVariant(
			buildComponents(20.0D, null, 5.0D, 0.24D, null, null, null, 1.0D, 7, null, null, null, null,
				"minecraft-entities-zombie-villager.json", null, null, 0, null, null, null),
			buildSpawnRules(100.0D, "minecraft-equipment-zombie-villager.json"), buildBehavior(false, false, false, false),
			buildGoals("hurt-by-target", "target-player", "melee-attack")
		);
		variant.add("adult", buildVariant(new JsonObject(), buildSpawnRules(90.0D), new JsonObject(), new JsonObject()));
		JsonObject baby = buildVariant(
			buildComponents(10.0D, null, 2.5D, 0.24D, null, null, null, null, 3, null, null, null, null, null, null, null, 0, null, null, null),
			buildSpawnRules(10.0D), new JsonObject(), new JsonObject());
		addMobBabyComponent(baby, true);
		variant.add("baby", baby);
		return variant;
	}

	private static JsonObject buildNewDynamicScalingDefaults(String fileKey) {
		return RegionalDifficultyConfigManager.buildDynamicMobScalingDefaults(fileKey);
	}

	public static JsonObject buildStructuresDefaults() {
		return RegionalDifficultyStructuresManager.buildDefaults();
	}

	public static JsonObject buildBiomesDefaults() {
		return RegionalDifficultyBiomesManager.buildDefaults();
	}

	public static JsonObject buildTimeDefaults() {
		return RegionalDifficultyTimeManager.buildDefaults();
	}

	private static String normalizeFileKey(String fileKey) {
		return fileKey == null ? "" : fileKey.trim().toLowerCase();
	}

	private static com.google.gson.JsonElement normalizeDynamicEntityEntry(String key, com.google.gson.JsonElement value) {
		if (key == null) return null;
		return switch (key.trim().toLowerCase()) {
			case FIELD_ENABLED, FIELD_OVERRIDE_SPAWN_RULES, FIELD_OVERRIDE_COMPONENTS, FIELD_OVERRIDE_BEHAVIORS,
				FIELD_OVERRIDE_GOALS, FIELD_MOB_ID, FIELD_ENTITY -> value;
			default -> null;
		};
	}

	private static com.google.gson.JsonElement normalizeDynamicScalingEntry(String key, com.google.gson.JsonElement value) {
		if (key == null) return null;
		return switch (key.trim().toLowerCase()) {
			case FIELD_ENABLED, FIELD_MOB_ID, FIELD_HEALTH, FIELD_MOVEMENT_SPEED, FIELD_SWIMMING_SPEED,
				FIELD_FLYING_SPEED, FIELD_SCALE, FIELD_ARMOR, FIELD_DAMAGE, FIELD_KNOCKBACK_RESISTANCE,
				FIELD_EXPERIENCE_DROP, FIELD_RANGED_DAMAGE, FIELD_EXPLOSION_POWER, "ranged-accuracy" -> value;
			default -> null;
		};
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		try {
			return root != null && root.has(key) ? root.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	static JsonObject buildMobComponentsDefaults(
		Double health,
		Double armor,
		Double damage,
		Double movementSpeed,
		Double swimmingSpeed,
		Double flyingSpeed,
		Double knockbackResistance,
		Double scale,
		Integer experienceDrop,
		Double rangedDamage,
		Double attackAccuracy,
		Double attackInterval,
		Double chargeUpTicks,
		String mobDropsReference
	) {
		JSONFormatAPIManager.ObjectBuilder root = JSONFormatAPIManager.object();
		if (hasConfiguredDoubleValue(health)) {
			root.put(FIELD_HEALTH, health);
		}
		if (hasConfiguredDoubleValue(armor)) {
			root.put(FIELD_ARMOR, armor);
		}
		if (hasConfiguredDoubleValue(damage)) {
			root.put(FIELD_DAMAGE, damage);
		}
		if (hasConfiguredDoubleValue(movementSpeed)) {
			root.put(FIELD_MOVEMENT_SPEED, movementSpeed);
		}
		if (hasConfiguredDoubleValue(swimmingSpeed)) {
			root.put(FIELD_SWIMMING_SPEED, swimmingSpeed);
		}
		if (hasConfiguredDoubleValue(flyingSpeed)) {
			root.put(FIELD_FLYING_SPEED, flyingSpeed);
		}
		if (hasConfiguredDoubleValue(knockbackResistance)) {
			root.put(FIELD_KNOCKBACK_RESISTANCE, knockbackResistance);
		}
		if (hasConfiguredDoubleValue(scale)) {
			root.put(FIELD_SCALE, scale);
		}
		if (experienceDrop != null) {
			root.put(FIELD_EXPERIENCE_DROP, experienceDrop);
		}
		if (hasConfiguredDoubleValue(rangedDamage)) {
			root.put(FIELD_RANGED_DAMAGE, rangedDamage);
		}
		if (hasConfiguredDoubleValue(attackAccuracy)) {
			root.put(FIELD_ATTACK_ACCURACY, attackAccuracy);
		}
		if (hasConfiguredDoubleValue(attackInterval)) {
			root.put(FIELD_ATTACK_INTERVAL, attackInterval);
		}
		if (hasConfiguredDoubleValue(chargeUpTicks)) {
			root.put(FIELD_CHARGE_INTERVAL, chargeUpTicks);
		}
		if (mobDropsReference != null && !mobDropsReference.isBlank()) {
			root.put(FIELD_MOB_DROPS, mobDropsReference);
		}
		return root.build();
	}

	static JsonObject buildMobWeaponDefaults(String itemId) {
		JSONFormatAPIManager.ObjectBuilder weapon = JSONFormatAPIManager.object();
		if (itemId != null && !itemId.isBlank()) {
			weapon.put(FIELD_ITEM, JSONAPIManager.normalizeRegistryIdentifierForJson(itemId));
		}
		return weapon.build();
	}

	static JsonObject buildMobEffectDefaults(String effectId, int durationSeconds) {
		if (effectId == null || effectId.isBlank() || durationSeconds <= 0) {
			return new JsonObject();
		}
		return JSONFormatAPIManager.object()
			.put(FIELD_EFFECT, effectId)
			.put(FIELD_DURATION, durationSeconds)
			.build();
	}

	private static boolean hasConfiguredDoubleValue(Double value) {
		return value != null && Double.isFinite(value);
	}

	static double roundDifficultyScaleValue(double value) {
		if (!Double.isFinite(value)) {
			return value;
		}
		double step = isWholeNumber(value) ? 0.05d : 0.005d;
		return Math.round(value / step) * step;
	}

	private static boolean isWholeNumber(double value) {
		return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
	}

	static JsonObject buildMobExplodeDefaults(Double destructionChance, Double griefPower) {
		JsonObject mobExplode = new JsonObject();
		mobExplode.addProperty(FIELD_ENABLED, true);
		if (hasConfiguredDoubleValue(destructionChance)) {
			mobExplode.addProperty(FIELD_DESTRUCTION_CHANCE, destructionChance);
		}
		if (hasConfiguredDoubleValue(griefPower)) {
			mobExplode.addProperty(FIELD_GREIF_POWER, griefPower);
		}
		return mobExplode;
	}

	static void addMobGoal(JsonObject goalsRoot, String goalKey, boolean enabled, int priority, double weight, int cooldownTicks) {
		if (goalsRoot == null || goalKey == null || goalKey.isBlank()) {
			return;
		}
		JsonObject goal = new JsonObject();
		goal.addProperty(FIELD_ENABLED, enabled);
		goal.addProperty("priority", priority);
		goal.addProperty("weight", weight);
		goal.addProperty("cooldown-ticks", cooldownTicks);
		goalsRoot.add(goalKey, goal);
	}

	private record RuntimeConfig(boolean enabled, Map<String, JsonObject> files) {
		private static RuntimeConfig disabled() {
			return new RuntimeConfig(false, Map.of());
		}
	}
}
