package madoku.craft.mobs.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import madoku.craft.API.system.MadokuJSONSystem;

/**
 * Loads and sanitizes pillager stat/behavior settings from the Madoku JSON system.
 */
public final class PillagerMobConfig {
	private static final String JSON_FOLDER_ID = "Mobs";
	private static final String JSON_FILE_ID = "pillager";

	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_RANGED_DAMAGE = "ranged_damage";
	private static final String KEY_ATTACK_INTERVAL = "attack_interval";
	private static final String KEY_ATTACK_ACCURACY = "attack_accuracy";
	private static final String KEY_CHARGE_UP_TICKS = "charge_up_ticks";

	private final boolean enabled;
	private final double rangedDamage;
	private final double attackInterval;
	private final double attackAccuracy;
	private final double chargeUpTicks;
	private final MobConfigJsonUtil.UniversalMobStats stats;

	private PillagerMobConfig(
		boolean enabled,
		double rangedDamage,
		double attackInterval,
		double attackAccuracy,
		double chargeUpTicks,
		MobConfigJsonUtil.UniversalMobStats stats
	) {
		this.enabled = enabled;
		this.rangedDamage = rangedDamage;
		this.attackInterval = attackInterval;
		this.attackAccuracy = attackAccuracy;
		this.chargeUpTicks = chargeUpTicks;
		this.stats = stats;
	}

	public static PillagerMobConfig load() {
		JsonObject defaults = buildDefaults();
		MadokuJSONSystem.ManagedJSON managed = MadokuJSONSystem.load(JSON_FOLDER_ID, JSON_FILE_ID, defaults);
		JsonObject root = managed.getRoot();

		boolean changed = false;

		boolean enabled = MobConfigJsonUtil.readBoolean(root, KEY_ENABLED, true);
		changed |= MobConfigJsonUtil.setBoolean(root, KEY_ENABLED, enabled);

		double rangedDamage = MobConfigJsonUtil.sanitizeNonNegative(
			MobConfigJsonUtil.readDouble(root, KEY_RANGED_DAMAGE, 6.0),
			6.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_RANGED_DAMAGE, rangedDamage);

		double attackInterval = MobConfigJsonUtil.sanitizePositive(
			MobConfigJsonUtil.readDouble(root, KEY_ATTACK_INTERVAL, 20.0),
			20.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_ATTACK_INTERVAL, attackInterval);

		double attackAccuracy = MobConfigJsonUtil.clamp(
			MobConfigJsonUtil.readDouble(root, KEY_ATTACK_ACCURACY, 0.7),
			0.0,
			1.0,
			0.7
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_ATTACK_ACCURACY, attackAccuracy);

		double chargeUpTicks = MobConfigJsonUtil.sanitizePositive(
			MobConfigJsonUtil.readDouble(root, KEY_CHARGE_UP_TICKS, 10.0),
			10.0
		);
		changed |= MobConfigJsonUtil.setDouble(root, KEY_CHARGE_UP_TICKS, chargeUpTicks);

		MobConfigJsonUtil.UniversalMobStatsLoadResult statsResult = MobConfigJsonUtil.readUniversalStatOverrides(root);
		changed |= statsResult.changed();

		if (changed) {
			managed.save();
		}

		return new PillagerMobConfig(
			enabled,
			rangedDamage,
			attackInterval,
			attackAccuracy,
			chargeUpTicks,
			statsResult.stats()
		);
	}

	public boolean enabled() {
		return enabled;
	}

	public double rangedDamage() {
		return rangedDamage;
	}

	public double attackInterval() {
		return attackInterval;
	}

	public double attackAccuracy() {
		return attackAccuracy;
	}

	public double chargeUpTicks() {
		return chargeUpTicks;
	}

	public MobConfigJsonUtil.UniversalMobStats stats() {
		return stats;
	}

	private static JsonObject buildDefaults() {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(KEY_ENABLED, true);
		defaults.addProperty(KEY_RANGED_DAMAGE, 6.0);
		defaults.addProperty(KEY_ATTACK_INTERVAL, 20.0);
		defaults.addProperty(KEY_ATTACK_ACCURACY, 0.7);
		defaults.addProperty(KEY_CHARGE_UP_TICKS, 10.0);
		for (Map.Entry<String, JsonElement> entry : MobConfigJsonUtil.buildUniversalStatDefaults(defaultStats()).entrySet()) {
			defaults.add(entry.getKey(), entry.getValue());
		}
		return defaults;
	}

	private static MobConfigJsonUtil.UniversalMobStats defaultStats() {
		return new MobConfigJsonUtil.UniversalMobStats(20.0, 1.0, 5.0, 0.25, 0.1, 1.0, 7);
	}
}
