package madoku.craft.java.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Runtime group for configured entity goals. */
public final class EntityGoalsManager {
	private EntityGoalsManager() {
	}

	public static boolean isGoalEnabled(JsonObject variant, String goalKey) {
		if (variant == null || goalKey == null) return false;
		JsonElement goalsElement = variant.get(MobConfigManager.FIELD_MOB_GOALS);
		if (goalsElement == null || !goalsElement.isJsonObject()) return false;
		JsonElement goal = goalsElement.getAsJsonObject().get(goalKey);
		if (goal == null || !goal.isJsonObject()) return false;
		JsonElement enabled = goal.getAsJsonObject().get(MobConfigManager.FIELD_ENABLED);
		return enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean() && enabled.getAsBoolean();
	}
}
