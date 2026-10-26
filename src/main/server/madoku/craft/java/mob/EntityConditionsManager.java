package madoku.craft.java.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.List;

/** Evaluates the shared condition format used by every configured goal. */
public final class EntityConditionsManager {
	private static final Map<LivingEntity, EyesLookCache> EYES_LOOK_CACHE =
		Collections.synchronizedMap(new WeakHashMap<>());

	private EntityConditionsManager() {
	}

	static boolean areSatisfied(LivingEntity entity, JsonObject goal) {
		return areSatisfied(entity, goal, null);
	}

	static boolean areSatisfied(LivingEntity entity, JsonObject goal, LivingEntity target) {
		if (entity == null || goal == null) {
			return false;
		}
		JsonElement conditionsElement = goal.get(MobConfigManager.FIELD_CONDITIONS);
		if (conditionsElement == null) {
			return true;
		}
		if (!conditionsElement.isJsonArray()) {
			return false;
		}
		for (JsonElement conditionElement : conditionsElement.getAsJsonArray()) {
			if (conditionElement == null || !conditionElement.isJsonObject()
				|| !evaluate(entity, target, conditionElement.getAsJsonObject())) {
				return false;
			}
		}
		return true;
	}

	private static boolean evaluate(LivingEntity entity, LivingEntity target, JsonObject condition) {
		String type = normalize(readString(condition, MobConfigManager.FIELD_CONDITION, ""));
		return switch (type) {
			case MobConfigManager.CONDITION_BABY_NEARBY -> {
				double distance = Math.max(0.0D, readDouble(condition, MobConfigManager.FIELD_DISTANCE, 0.0D));
				yield MobEntityManager.hasConfiguredBabyNearby(entity, distance);
			}
			case MobConfigManager.CONDITION_LIGHT_LEVEL -> {
				double maximumLight = Math.max(0.0D, Math.min(15.0D,
					readDouble(condition, MobConfigManager.FIELD_VALUE, 15.0D)));
				yield entity.level().getMaxLocalRawBrightness(entity.blockPosition()) <= maximumLight;
			}
			case "has-target" -> {
				boolean expected = readBoolean(condition, MobConfigManager.FIELD_VALUE, true);
				yield entity instanceof Mob mob && (mob.getTarget() != null) == expected;
			}
			case "on-ground" -> entity.onGround() == readBoolean(condition, MobConfigManager.FIELD_VALUE, true);
			case "underwater" -> entity.isUnderWater() == readBoolean(condition, MobConfigManager.FIELD_VALUE, true);
			case MobConfigManager.CONDITION_PLAYER_NEARBY -> {
				double distance = Math.max(0.0D, readDouble(condition, MobConfigManager.FIELD_DISTANCE, 0.0D));
				boolean lineOfSight = readBoolean(condition, MobConfigManager.FIELD_LINE_OF_SIGHT, false);
				yield hasNearbyPlayer(entity, target, distance, lineOfSight);
			}
			case MobConfigManager.CONDITION_EYES_LOOKED_AT -> {
				yield hasEyesLookedAtPlayer(entity, target);
			}
			case MobConfigManager.CONDITION_WEARING_ARMOR -> {
				yield hasPlayerWearingArmorMaterial(
					entity,
					target,
					readString(condition, MobConfigManager.FIELD_MATERIAL, "")
				);
			}
			case MobConfigManager.CONDITION_WEARING_IRON -> {
				yield hasPlayerWearingArmorMaterial(entity, target, "iron");
			}
			case MobConfigManager.CONDITION_WEARING_GOLD -> {
				yield hasPlayerWearingArmorMaterial(entity, target, "gold");
			}
			case MobConfigManager.CONDITION_NOT_WEARING_ARMOR -> {
				yield isPlayerNotWearingArmorMaterial(
					entity,
					target,
					readString(condition, MobConfigManager.FIELD_MATERIAL, "")
				);
			}
			case MobConfigManager.CONDITION_NOT_WEARING_IRON -> {
				yield isPlayerNotWearingArmorMaterial(entity, target, "iron");
			}
			case MobConfigManager.CONDITION_NOT_WEARING_GOLD -> {
				yield isPlayerNotWearingArmorMaterial(entity, target, "gold");
			}
			case MobConfigManager.CONDITION_NOT_WEARING_NETHERITE -> {
				yield isPlayerNotWearingArmorMaterial(entity, target, "netherite");
			}
			default -> false;
		};
	}

	private static boolean hasPlayerWearingArmorMaterial(
		LivingEntity entity,
		LivingEntity target,
		String material
	) {
		String normalizedMaterial = normalize(material);
		if (normalizedMaterial.isBlank()) {
			return false;
		}
		if (target != null) {
			return target instanceof Player player && isWearingArmorMaterial(player, normalizedMaterial);
		}

		// The vanilla target goal performs the nearby-player search. Candidate
		// filtering and Mob#setTarget enforce this condition once a player exists,
		// so do not duplicate that search during goal startup.
		return true;
	}

	private static boolean isPlayerNotWearingArmorMaterial(
		LivingEntity entity,
		LivingEntity target,
		String material
	) {
		if (target == null) {
			// Let the vanilla target goal perform its nearby-player search. The
			// candidate-level check below applies the negated condition.
			return true;
		}
		return !hasPlayerWearingArmorMaterial(entity, target, material);
	}

	private static boolean isWearingArmorMaterial(Player player, String material) {
		if (player == null) {
			return false;
		}
		return switch (material) {
			case "iron" -> hasArmorPiece(player, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
			case "gold", "golden" -> hasArmorPiece(player, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
			case "netherite" -> hasArmorPiece(player, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
			default -> false;
		};
	}

	private static boolean hasArmorPiece(Player player, net.minecraft.world.item.Item... armorPieces) {
		return player.getItemBySlot(EquipmentSlot.HEAD).is(armorPieces[0])
			|| player.getItemBySlot(EquipmentSlot.CHEST).is(armorPieces[1])
			|| player.getItemBySlot(EquipmentSlot.LEGS).is(armorPieces[2])
			|| player.getItemBySlot(EquipmentSlot.FEET).is(armorPieces[3]);
	}

	private static boolean hasNearbyPlayer(
		LivingEntity entity,
		LivingEntity target,
		double distance,
		boolean lineOfSight
	) {
		if (target != null) {
			return target instanceof Player player
				&& (distance <= 0.0D || entity.distanceToSqr(player) <= distance * distance)
				&& (!lineOfSight || entity.hasLineOfSight(player));
		}
		if (distance <= 0.0D) {
			return false;
		}
		List<Player> players = entity.level().getEntitiesOfClass(
			Player.class,
			entity.getBoundingBox().inflate(distance),
			Player::isAlive
		);
		for (Player player : players) {
			if (entity.distanceToSqr(player) <= distance * distance
				&& (!lineOfSight || entity.hasLineOfSight(player))) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasEyesLookedAtPlayer(LivingEntity entity, LivingEntity target) {
		if (!(entity instanceof Enderman enderman)) {
			return false;
		}
		if (target instanceof Player player) {
			return isPlayerLookingAtEnderman(enderman, player);
		}
		if (target != null) {
			return false;
		}
		long gameTime = entity.level().getGameTime();
		EyesLookCache cached = EYES_LOOK_CACHE.get(entity);
		if (cached != null && cached.gameTime() == gameTime) {
			return cached.lookedAt();
		}
		List<Player> players = entity.level().getEntitiesOfClass(
			Player.class,
			entity.getBoundingBox().inflate(64.0D),
			Player::isAlive
		);
		for (Player player : players) {
			if (isPlayerLookingAtEnderman(enderman, player)) {
				EYES_LOOK_CACHE.put(entity, new EyesLookCache(gameTime, true));
				return true;
			}
		}
		EYES_LOOK_CACHE.put(entity, new EyesLookCache(gameTime, false));
		return false;
	}

	private static boolean isPlayerLookingAtEnderman(Enderman enderman, Player player) {
		return enderman.hasLineOfSight(player)
			&& enderman.isLookingAtMe(player, 0.025D, true, false, enderman.getEyeY());
	}

	private record EyesLookCache(long gameTime, boolean lookedAt) {
	}

	private static String readString(JsonObject root, String key, String fallback) {
		JsonElement element = root == null ? null : root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		return element.getAsString();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		JsonElement element = root == null ? null : root.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
			? element.getAsBoolean() : fallback;
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		JsonElement element = root == null ? null : root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
	}
}
