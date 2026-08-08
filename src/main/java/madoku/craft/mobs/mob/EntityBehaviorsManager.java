package madoku.craft.mobs.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import madoku.craft.mobs.mixin.AbstractSkeletonArrowInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

public final class EntityBehaviorsManager {
	private EntityBehaviorsManager() { }

	public static final class BeeBehavior {
		private static final int DEFAULT_NECTAR_TOTAL_CHARGES = 10;
		private static final int DEFAULT_SEARCH_DURATION_TICKS = 1200;
		private static final int DEFAULT_SEARCH_RADIUS_HORIZONTAL = 12;
		private static final int DEFAULT_SEARCH_RADIUS_VERTICAL = 3;
		private static final double DEFAULT_CROP_REACH_DISTANCE_SQR = 4.0D;
		private static final int DEFAULT_CROP_RESERVATION_TTL_TICKS = 40;
		private static final double DEFAULT_MOVE_SPEED_MODIFIER = 1.1D;
		private static final double DEFAULT_ARRIVAL_THRESHOLD = 0.1D;
		private static final int DEFAULT_POSITION_CHANGE_CHANCE = 25;
		private static final double DEFAULT_HOVER_HEIGHT_WITHIN_FLOWER = 0.6D;
		private static final double DEFAULT_HOVER_POS_OFFSET = 0.33333334D;
		private static final int DEFAULT_CHARGE_INTERVAL_TICKS = 20;
		private static final int DEFAULT_CHARGES_SPEND_DIVISOR = 2;

		private static final Map<UUID, BeeNectarState> BEE_NECTAR_STATES = new ConcurrentHashMap<>();
		private static final Map<String, BeeCropReservation> BEE_CROP_RESERVATIONS = new ConcurrentHashMap<>();
		private static final java.util.Set<UUID> BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE = ConcurrentHashMap.newKeySet();

		private BeeBehavior() {
		}

		public static void applySpawnOverrides(Mob mob, ServerLevelAccessor world) {
			MobEntityManager.applyBeeSpawnOverrides(mob, world);
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			return MobEntityManager.applyBeeLoadedEntityOverrides(entity);
		}

		public static boolean applyStingingAttackEffect(Bee bee, LivingEntity target, MobEffectInstance effect, Entity attacker) {
			if (bee == null || target == null || effect == null || attacker == null) {
				return false;
			}
			if (!MobEntityManager.isEnabled() || !MobEntityManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_BEE)) {
				return target.addEffect(effect, attacker);
			}
			MobEffectInstance configuredEffect = MobEntityManager.resolveBeeAttackEffect(bee, effect);
			return target.addEffect(configuredEffect, attacker);
		}

		public static boolean isPollinateCropsEnabled(Bee bee) {
			if (bee == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			if (!MobEntityManager.isBeeBehaviorOverrideEnabled() || !MobEntityManager.isBeeGoalsOverrideEnabled()) {
				return false;
			}
			JsonObject behaviorRoot = MobEntityManager.resolveBeeBehaviorRoot(bee);
			JsonObject pollinateCropsRoot = getObject(behaviorRoot, "pollinate-crops");
			return getBoolean(pollinateCropsRoot, MobConfigManager.FIELD_ENABLED, false);
		}

		public static void resetRuntimeState() {
			BEE_NECTAR_STATES.clear();
			BEE_CROP_RESERVATIONS.clear();
			BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE.clear();
		}

		public static void onEntityCleanup(Entity entity) {
			if (entity == null) {
				return;
			}
			resetBeeNectarCycle(entity.getUUID());
		}

		public static boolean tickRuntime(
			MinecraftServer server,
			Iterable<Entity> trackedEntities,
			boolean mobSystemEnabled,
			boolean beeFileEnabled
		) {
			if (server == null || !mobSystemEnabled || !beeFileEnabled) {
				resetRuntimeState();
				return false;
			}
			long nowTick = server.overworld() == null ? 0L : server.overworld().getGameTime();
			pruneBeeCropReservations(nowTick);
			if (trackedEntities == null) {
				return !BEE_NECTAR_STATES.isEmpty() || !BEE_CROP_RESERVATIONS.isEmpty();
			}
			for (Entity entity : trackedEntities) {
				if (!(entity instanceof Bee bee) || !bee.isAlive() || !(bee.level() instanceof ServerLevel level)) {
					continue;
				}
				if (!MobEntityManager.isBeeBehaviorOverrideEnabled()) {
					allowBeeHiveReturn(bee);
					resetBeeNectarCycle(bee.getUUID());
					continue;
				}
				if (!MobEntityManager.isBeeGoalsOverrideEnabled()) {
					allowBeeHiveReturn(bee);
					resetBeeNectarCycle(bee.getUUID());
					continue;
				}
				JsonObject behaviorRoot = MobEntityManager.resolveBeeBehaviorRoot(bee);
				JsonObject pollinateCropsRoot = getObject(behaviorRoot, "pollinate-crops");
				boolean pollinateCropsEnabled = getBoolean(pollinateCropsRoot, MobConfigManager.FIELD_ENABLED, false);
				if (!pollinateCropsEnabled) {
					allowBeeHiveReturn(bee);
					resetBeeNectarCycle(bee.getUUID());
					continue;
				}
				// pollinate-crops is the override mode and takes precedence over pollination behavior config.
				JsonObject pollinationRoot = getObject(behaviorRoot, "pollination");
				int nectarTotalCharges = Math.max(1, getInt(pollinateCropsRoot, "nectar_total_charges", DEFAULT_NECTAR_TOTAL_CHARGES));
				int searchDurationTicks = Math.max(1, getInt(pollinateCropsRoot, "search_duration_ticks", DEFAULT_SEARCH_DURATION_TICKS));
				int searchRadiusHorizontal = Math.max(1, getInt(pollinateCropsRoot, "search_radius_horizontal", DEFAULT_SEARCH_RADIUS_HORIZONTAL));
				int searchRadiusVertical = Math.max(0, getInt(pollinateCropsRoot, "search_radius_vertical", DEFAULT_SEARCH_RADIUS_VERTICAL));
				double cropReachDistanceSqr = Math.max(0.25D, getDouble(pollinateCropsRoot, "crop_reach_distance_sqr", DEFAULT_CROP_REACH_DISTANCE_SQR));
				int cropReservationTtlTicks = Math.max(1, getInt(pollinateCropsRoot, "crop_reservation_ttl_ticks", DEFAULT_CROP_RESERVATION_TTL_TICKS));
				double moveSpeedModifier = Math.max(0.05D, getDouble(pollinateCropsRoot, "move_speed_modifier", DEFAULT_MOVE_SPEED_MODIFIER));
				double arrivalThreshold = Math.max(
					0.01D,
					getDouble(
						pollinateCropsRoot,
						"arrival_threshold",
						getDouble(pollinationRoot, "arrival_threshold", DEFAULT_ARRIVAL_THRESHOLD)
					)
				);
				int positionChangeChance = Math.max(
					0,
					Math.min(
						100,
						getInt(
							pollinateCropsRoot,
							"position_change_chance",
							getInt(pollinationRoot, "position_change_chance", DEFAULT_POSITION_CHANGE_CHANCE)
						)
					)
				);
				double hoverHeightWithinFlower = Math.max(
					0.0D,
					getDouble(
						pollinateCropsRoot,
						"hover_height_within_crop",
						getDouble(pollinationRoot, "hover_height_within_flower", DEFAULT_HOVER_HEIGHT_WITHIN_FLOWER)
					)
				);
				double hoverPosOffset = Math.max(
					0.0D,
					getDouble(
						pollinateCropsRoot,
						"hover_pos_offset",
						getDouble(pollinationRoot, "hover_pos_offset", DEFAULT_HOVER_POS_OFFSET)
					)
				);
				int chargeIntervalTicks = Math.max(1, getInt(pollinateCropsRoot, "charge_interval_ticks", DEFAULT_CHARGE_INTERVAL_TICKS));
				int chargesSpendDivisor = Math.max(1, getInt(pollinateCropsRoot, "charges_spend_divisor", DEFAULT_CHARGES_SPEND_DIVISOR));
				double arrivalThresholdSqr = arrivalThreshold * arrivalThreshold;
				UUID beeId = bee.getUUID();
				if (!bee.hasNectar()) {
					allowBeeHiveReturn(bee);
					resetBeeNectarCycle(beeId);
					continue;
				}
				if (BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE.contains(beeId)) {
					allowBeeHiveReturn(bee);
					clearBeeNectarState(beeId);
					continue;
				}

				BeeNectarState state = BEE_NECTAR_STATES.computeIfAbsent(
					beeId,
					id -> BeeNectarState.create(nectarTotalCharges, chargesSpendDivisor, nowTick + searchDurationTicks)
				);
				if (state.chargesRemaining <= 0 || state.searchExpiresAtTick <= nowTick) {
					allowBeeHiveReturn(bee);
					clearBeeNectarState(beeId);
					continue;
				}
				if (state.chargesRemaining <= state.minimumChargesRemaining) {
					BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE.add(beeId);
					allowBeeHiveReturn(bee);
					clearBeeNectarState(beeId);
					continue;
				}

				int stayOutTicks = (int) Math.max(0L, state.searchExpiresAtTick - nowTick);
				bee.setStayOutOfHiveCountdown(stayOutTicks);
				if (state.hasTarget() && !isBeeCropTargetStillValid(level, state, beeId, nowTick, cropReservationTtlTicks)) {
					releaseBeeCropReservation(beeId, state.reservedCropKey);
					state.clearTarget();
				}

				if (!state.hasTarget()) {
					BeeCropTarget target = findBeeCropTarget(
						bee,
						level,
						beeId,
						nowTick,
						searchRadiusHorizontal,
						searchRadiusVertical,
						cropReservationTtlTicks
					);
					if (target == null) {
						continue;
					}
					state.assignTarget(target.cropKey(), target.pos(), target.levelId());
				}

				BlockPos targetPos = BlockPos.of(state.reservedCropPos);
				state.updateHoverTarget(
					bee,
					targetPos,
					hoverPosOffset,
					hoverHeightWithinFlower,
					arrivalThresholdSqr,
					positionChangeChance
				);
				bee.getNavigation().moveTo(
					state.hoverTargetX,
					state.hoverTargetY,
					state.hoverTargetZ,
					moveSpeedModifier
				);

				if (bee.distanceToSqr(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D) > cropReachDistanceSqr) {
					continue;
				}

				BlockState targetState = level.getBlockState(targetPos);
				if (!isBeeSupportedCrop(targetState) || isBeeCropFullyGrown(targetState) || !isFarmlandBelow(level, targetPos)) {
					releaseBeeCropReservation(beeId, state.reservedCropKey);
					state.clearTarget();
					continue;
				}
				if (!state.canSpendCharge(nowTick)) {
					continue;
				}

				int chargesToSpend = 1;
				boolean appliedGrowth = growCropBySingleStage(level, targetPos, targetState);
				if (!appliedGrowth) {
					releaseBeeCropReservation(beeId, state.reservedCropKey);
					state.clearTarget();
					continue;
				}

				state.chargesRemaining = Math.max(0, state.chargesRemaining - chargesToSpend);
				state.markChargeSpent(nowTick, chargeIntervalTicks);
				releaseBeeCropReservation(beeId, state.reservedCropKey);
				state.clearTarget();
				if (state.chargesRemaining <= state.minimumChargesRemaining) {
					BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE.add(beeId);
					allowBeeHiveReturn(bee);
					clearBeeNectarState(beeId);
				}
			}
			pruneBeeCropReservations(nowTick);
			return !BEE_NECTAR_STATES.isEmpty() || !BEE_CROP_RESERVATIONS.isEmpty();
		}

		private static BeeCropTarget findBeeCropTarget(
			Bee bee,
			ServerLevel level,
			UUID beeId,
			long nowTick,
			int searchRadiusHorizontal,
			int searchRadiusVertical,
			int cropReservationTtlTicks
		) {
			if (bee == null || level == null || beeId == null) {
				return null;
			}
			BlockPos center = bee.blockPosition();
			BlockPos best = null;
			String bestKey = "";
			double bestProgress = Double.MAX_VALUE;
			int tieCount = 0;
			String levelId = MadokuSchedulerManager.normalizeLevelIdentifier(level.dimension().toString());
			for (BlockPos candidate : BlockPos.betweenClosed(
				center.offset(-searchRadiusHorizontal, -searchRadiusVertical, -searchRadiusHorizontal),
				center.offset(searchRadiusHorizontal, searchRadiusVertical, searchRadiusHorizontal)
			)) {
				BlockState state = level.getBlockState(candidate);
				if (!isBeeSupportedCrop(state) || isBeeCropFullyGrown(state) || !isFarmlandBelow(level, candidate)) {
					continue;
				}
				String cropKey = beeCropKey(levelId, candidate.asLong());
				BeeCropReservation reservation = BEE_CROP_RESERVATIONS.get(cropKey);
				if (reservation != null && (reservation.expiresAtTick <= nowTick)) {
					BEE_CROP_RESERVATIONS.remove(cropKey, reservation);
					reservation = null;
				}
				if (reservation != null && !beeId.equals(reservation.beeId)) {
					continue;
				}
				double progress = getCropGrowthProgress(state);
				if (progress < bestProgress - 1.0E-6D) {
					best = candidate.immutable();
					bestKey = cropKey;
					bestProgress = progress;
					tieCount = 1;
					continue;
				}
				if (Math.abs(progress - bestProgress) <= 1.0E-6D) {
					tieCount++;
					// Reservoir sampling: uniform random among equal-growth candidates.
					if (bee.getRandom().nextInt(tieCount) == 0) {
						best = candidate.immutable();
						bestKey = cropKey;
					}
				}
			}
			if (best == null || bestKey.isBlank()) {
				return null;
			}
			BEE_CROP_RESERVATIONS.put(bestKey, new BeeCropReservation(beeId, nowTick + cropReservationTtlTicks));
			return new BeeCropTarget(levelId, best.asLong(), bestKey);
		}

		private static boolean isBeeCropTargetStillValid(
			ServerLevel level,
			BeeNectarState state,
			UUID beeId,
			long nowTick,
			int cropReservationTtlTicks
		) {
			if (level == null || state == null || !state.hasTarget() || beeId == null) {
				return false;
			}
			String currentLevelId = MadokuSchedulerManager.normalizeLevelIdentifier(level.dimension().toString());
			if (!currentLevelId.equals(state.reservedLevelId)) {
				return false;
			}
			BlockPos targetPos = BlockPos.of(state.reservedCropPos);
			BlockState stateAtTarget = level.getBlockState(targetPos);
			if (!isBeeSupportedCrop(stateAtTarget) || isBeeCropFullyGrown(stateAtTarget) || !isFarmlandBelow(level, targetPos)) {
				return false;
			}
			BeeCropReservation reservation = BEE_CROP_RESERVATIONS.get(state.reservedCropKey);
			if (reservation == null) {
				BEE_CROP_RESERVATIONS.put(state.reservedCropKey, new BeeCropReservation(beeId, nowTick + cropReservationTtlTicks));
				return true;
			}
			if (!beeId.equals(reservation.beeId)) {
				return false;
			}
			if (reservation.expiresAtTick <= nowTick) {
				BEE_CROP_RESERVATIONS.put(state.reservedCropKey, new BeeCropReservation(beeId, nowTick + cropReservationTtlTicks));
				return true;
			}
			return true;
		}

		private static void clearBeeNectarState(UUID beeId) {
			if (beeId == null) {
				return;
			}
			BeeNectarState removed = BEE_NECTAR_STATES.remove(beeId);
			if (removed == null || !removed.hasTarget()) {
				return;
			}
			releaseBeeCropReservation(beeId, removed.reservedCropKey);
		}

		private static void resetBeeNectarCycle(UUID beeId) {
			if (beeId == null) {
				return;
			}
			BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE.remove(beeId);
			clearBeeNectarState(beeId);
		}

		private static void releaseBeeCropReservation(UUID beeId, String cropKey) {
			if (beeId == null || cropKey == null || cropKey.isBlank()) {
				return;
			}
			BeeCropReservation reservation = BEE_CROP_RESERVATIONS.get(cropKey);
			if (reservation != null && beeId.equals(reservation.beeId)) {
				BEE_CROP_RESERVATIONS.remove(cropKey, reservation);
			}
		}

		private static void pruneBeeCropReservations(long nowTick) {
			for (Map.Entry<String, BeeCropReservation> entry : BEE_CROP_RESERVATIONS.entrySet()) {
				BeeCropReservation reservation = entry.getValue();
				if (reservation == null || reservation.expiresAtTick <= nowTick) {
					BEE_CROP_RESERVATIONS.remove(entry.getKey(), reservation);
				}
			}
		}

		private static String beeCropKey(String levelId, long pos) {
			return (levelId == null ? "" : levelId) + "|" + pos;
		}

		private static void allowBeeHiveReturn(Bee bee) {
			if (bee == null) {
				return;
			}
			bee.setStayOutOfHiveCountdown(0);
		}





		private static boolean isFarmlandBelow(ServerLevel level, BlockPos cropPos) {
			if (level == null || cropPos == null) {
				return false;
			}
			return level.getBlockState(cropPos.below()).is(Blocks.FARMLAND);
		}

		private static boolean isBeeSupportedCrop(BlockState state) {
			if (state == null) {
				return false;
			}
			Block block = state.getBlock();
			return block == Blocks.WHEAT
				|| block == Blocks.CARROTS
				|| block == Blocks.POTATOES
				|| block == Blocks.BEETROOTS
				|| block == Blocks.MELON_STEM
				|| block == Blocks.PUMPKIN_STEM;
		}

		private static boolean isBeeCropFullyGrown(BlockState state) {
			IntegerProperty ageProperty = findAgeProperty(state);
			if (ageProperty == null) {
				return true;
			}
			Integer age = state.getValue(ageProperty);
			int max = ageProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
			return age != null && age >= max;
		}

		private static double getCropGrowthProgress(BlockState state) {
			IntegerProperty ageProperty = findAgeProperty(state);
			if (ageProperty == null) {
				return 1.0D;
			}
			Integer age = state.getValue(ageProperty);
			int max = ageProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
			if (age == null || max <= 0) {
				return 1.0D;
			}
			return Math.max(0.0D, Math.min(1.0D, age / (double) max));
		}

		private static boolean growCropBySingleStage(ServerLevel level, BlockPos cropPos, BlockState state) {
			if (level == null || cropPos == null || state == null) {
				return false;
			}
			IntegerProperty ageProperty = findAgeProperty(state);
			if (ageProperty == null) {
				return false;
			}
			Integer age = state.getValue(ageProperty);
			int max = ageProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
			if (age == null || age >= max) {
				return false;
			}
			BlockState updated = state.setValue(ageProperty, age + 1);
			return level.setBlock(cropPos, updated, 2);
		}

		private static IntegerProperty findAgeProperty(BlockState state) {
			if (state == null) {
				return null;
			}
			for (Property<?> property : state.getProperties()) {
				if (property instanceof IntegerProperty integerProperty && "age".equals(property.getName())) {
					return integerProperty;
				}
			}
			return null;
		}

		private static JsonObject getObject(JsonObject root, String key) {
			if (root == null || key == null) {
				return new JsonObject();
			}
			JsonElement element = EntityConfigManager.resolveConfiguredElement(root, key);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		}

		private static boolean getBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			return element.getAsBoolean();
		}

		private static int getInt(JsonObject root, String key, int fallback) {
			if (root == null || key == null) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				return fallback;
			}
			try {
				return element.getAsInt();
			} catch (RuntimeException ignored) {
				return fallback;
			}
		}

		private static double getDouble(JsonObject root, String key, double fallback) {
			if (root == null || key == null) {
				return fallback;
			}
			JsonElement element = root.get(key);
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

		private record BeeCropReservation(UUID beeId, long expiresAtTick) {}
		private record BeeCropTarget(String levelId, long pos, String cropKey) {}
		private static final class BeeNectarState {
			private int chargesRemaining;
			private final int minimumChargesRemaining;
			private long searchExpiresAtTick;
			private long nextChargeAllowedAtTick;
			private String reservedCropKey;
			private long reservedCropPos;
			private String reservedLevelId;
			private double hoverTargetX;
			private double hoverTargetY;
			private double hoverTargetZ;
			private boolean hasHoverTarget;

			private BeeNectarState(int chargesRemaining, int minimumChargesRemaining, long searchExpiresAtTick) {
				this.chargesRemaining = Math.max(0, chargesRemaining);
				this.minimumChargesRemaining = Math.max(0, minimumChargesRemaining);
				this.searchExpiresAtTick = Math.max(0L, searchExpiresAtTick);
				this.nextChargeAllowedAtTick = 0L;
				this.reservedCropKey = "";
				this.reservedCropPos = Long.MIN_VALUE;
				this.reservedLevelId = "";
				this.hoverTargetX = 0.0D;
				this.hoverTargetY = 0.0D;
				this.hoverTargetZ = 0.0D;
				this.hasHoverTarget = false;
			}

			private static BeeNectarState create(int chargesRemaining, int chargesSpendDivisor, long searchExpiresAtTick) {
				int sanitizedCharges = Math.max(1, chargesRemaining);
				int minimumChargesRemaining = chargesSpendDivisor <= 1
					? 0
					: (int) Math.ceil(sanitizedCharges / (double) chargesSpendDivisor);
				return new BeeNectarState(sanitizedCharges, minimumChargesRemaining, searchExpiresAtTick);
			}

			private void assignTarget(String cropKey, long cropPos, String levelId) {
				this.reservedCropKey = cropKey == null ? "" : cropKey;
				this.reservedCropPos = cropPos;
				this.reservedLevelId = levelId == null ? "" : levelId;
				clearHoverTarget();
			}

			private void clearTarget() {
				this.reservedCropKey = "";
				this.reservedCropPos = Long.MIN_VALUE;
				this.reservedLevelId = "";
				clearHoverTarget();
			}

			private boolean hasTarget() {
				return reservedCropPos != Long.MIN_VALUE && !reservedCropKey.isBlank();
			}

			private boolean canSpendCharge(long nowTick) {
				return nowTick >= nextChargeAllowedAtTick;
			}

			private void markChargeSpent(long nowTick, int chargeIntervalTicks) {
				long sanitizedNow = Math.max(0L, nowTick);
				int sanitizedInterval = Math.max(1, chargeIntervalTicks);
				this.nextChargeAllowedAtTick = sanitizedNow + sanitizedInterval;
			}

			private void updateHoverTarget(
				Bee bee,
				BlockPos cropPos,
				double hoverPosOffset,
				double hoverHeightWithinFlower,
				double arrivalThresholdSqr,
				int positionChangeChance
			) {
				if (bee == null || cropPos == null) {
					clearHoverTarget();
					return;
				}
				double centerX = cropPos.getX() + 0.5D;
				double centerY = cropPos.getY() + hoverHeightWithinFlower;
				double centerZ = cropPos.getZ() + 0.5D;
				if (!hasHoverTarget) {
					assignRandomHoverTarget(bee, centerX, centerY, centerZ, hoverPosOffset);
					return;
				}
				if (bee.distanceToSqr(hoverTargetX, hoverTargetY, hoverTargetZ) > Math.max(0.0001D, arrivalThresholdSqr)) {
					return;
				}
				if (positionChangeChance > 0 && bee.getRandom().nextInt(100) < positionChangeChance) {
					assignRandomHoverTarget(bee, centerX, centerY, centerZ, hoverPosOffset);
				}
			}

			private void assignRandomHoverTarget(Bee bee, double centerX, double centerY, double centerZ, double hoverPosOffset) {
				double offset = Math.max(0.0D, hoverPosOffset);
				double deltaX = (bee.getRandom().nextDouble() * 2.0D - 1.0D) * offset;
				double deltaZ = (bee.getRandom().nextDouble() * 2.0D - 1.0D) * offset;
				this.hoverTargetX = centerX + deltaX;
				this.hoverTargetY = centerY;
				this.hoverTargetZ = centerZ + deltaZ;
				this.hasHoverTarget = true;
			}

			private void clearHoverTarget() {
				this.hoverTargetX = 0.0D;
				this.hoverTargetY = 0.0D;
				this.hoverTargetZ = 0.0D;
				this.hasHoverTarget = false;
			}
		}
	}

	public static final class BoggedBehavior {
		private static final int DEFAULT_ATTACK_INTERVAL_TICKS = 20;
		private static final int DEFAULT_CHARGE_UP_TICKS = 10;
		private static final String BOGGED_VARIANT_TAG_PREFIX = "madoku-craft.bogged.variant:";

		private static final Map<UUID, PendingRangedBowCharge> PENDING_RANGED_BOW_CHARGES = new ConcurrentHashMap<>();
		private static final Map<UUID, Integer> RANGED_BOW_COOLDOWNS = new ConcurrentHashMap<>();

		private BoggedBehavior() {
		}

		public static void applySpawnOverrides(
			AbstractSkeleton skeleton,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason
		) {
			if (skeleton == null || world == null || difficulty == null || !MobEntityManager.isEnabled()) {
				return;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveMobFileSectionForRuntime(fileKey);
			JsonObject variantGroup = resolveVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, world, true);
			if (variantGroup.entrySet().isEmpty()) {
				return;
			}
			JsonObject resolvedRoot = mergeFileSettings(fileConfigRoot, variantGroup);

			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			applyWeaponDamagePolicy(skeleton, resolvedRoot);
			applyBehaviorToggles(skeleton, fileConfigRoot, resolvedRoot);
			if (overrideStats) {
				MobEntityManager.applyUniversalBaseStatsForRuntime(skeleton, resolvedRoot);
			}
			if (isBowAttackEnabled(skeleton)) {
				ensureBowEquipped(skeleton);
			}
		}

		public static boolean shouldOverrideSpawnRules(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			return readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			if (!(entity instanceof AbstractSkeleton skeleton) || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject resolvedRoot = resolveRuntimeRoot(skeleton);
			if (resolvedRoot.entrySet().isEmpty()) {
				return false;
			}

			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			boolean modified = overrideStats && MobEntityManager.applyUniversalBaseStatsForRuntime(skeleton, resolvedRoot);
			applyWeaponDamagePolicy(skeleton, resolvedRoot);
			applyBehaviorToggles(skeleton, fileConfigRoot, resolvedRoot);
			if (isBowAttackEnabled(skeleton)) {
				ensureBowEquipped(skeleton);
			}
			return modified;
		}

		public static JsonObject resolveRuntimeRoot(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return new JsonObject();
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return new JsonObject();
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveMobFileSectionForRuntime(fileKey);
			JsonObject variantGroup = resolveVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, null, false);
			if (variantGroup.entrySet().isEmpty()) {
				return new JsonObject();
			}
			return mergeFileSettings(fileConfigRoot, variantGroup);
		}

		public static boolean isBowAttackEnabled(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			return !root.entrySet().isEmpty() && MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false);
		}

		public static void ensureBowEquipped(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			ItemStack main = skeleton.getItemBySlot(EquipmentSlot.MAINHAND);
			if (main.isEmpty() || !main.is(Items.BOW)) {
				skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			}
		}

		public static boolean applyRangedSkeletonBowAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
			if (skeleton == null || target == null || !target.isAlive() || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			if (!isBowAttackEnabled(skeleton)) {
				return false;
			}

			UUID skeletonId = skeleton.getUUID();
			int cooldown = RANGED_BOW_COOLDOWNS.getOrDefault(skeletonId, 0);
			if (cooldown > 0) {
				return true;
			}
			PendingRangedBowCharge pending = PENDING_RANGED_BOW_CHARGES.get(skeletonId);
			if (pending != null) {
				return true;
			}

			int chargeUpTicks = resolveBowChargeUpTicks(skeleton);
			if (chargeUpTicks <= 0) {
				if (fireRangedBowArrow(skeleton, target)) {
					RANGED_BOW_COOLDOWNS.put(skeletonId, resolveBowAttackIntervalTicks(skeleton));
				}
				return true;
			}

			PENDING_RANGED_BOW_CHARGES.put(skeletonId, new PendingRangedBowCharge(target.getUUID(), chargeUpTicks));
			return true;
		}

		public static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton) {
			if (!isBowAttackEnabled(skeleton)) {
				return -1;
			}
			return resolveBowAttackIntervalTicks(skeleton, DEFAULT_ATTACK_INTERVAL_TICKS);
		}

		public static int resolveBowChargeUpTicks(Monster attacker) {
			if (!(attacker instanceof AbstractSkeleton skeleton) || !isBowAttackEnabled(skeleton)) {
				return -1;
			}
			return resolveBowChargeUpTicks(skeleton, DEFAULT_CHARGE_UP_TICKS);
		}

		public static void tickRangedSkeletonRuntime(AbstractSkeleton skeleton) {
			if (skeleton == null || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return;
			}
			if (!isBowAttackEnabled(skeleton)) {
				clearRangedSkeletonRuntimeState(skeleton);
				return;
			}

			UUID skeletonId = skeleton.getUUID();
			Integer cooldown = RANGED_BOW_COOLDOWNS.get(skeletonId);
			if (cooldown != null) {
				if (cooldown <= 1) {
					RANGED_BOW_COOLDOWNS.remove(skeletonId);
				} else {
					RANGED_BOW_COOLDOWNS.put(skeletonId, cooldown - 1);
				}
			}

			PendingRangedBowCharge pending = PENDING_RANGED_BOW_CHARGES.get(skeletonId);
			if (pending == null) {
				return;
			}
			if (pending.remainingTicks() > 1) {
				PENDING_RANGED_BOW_CHARGES.put(skeletonId, pending.withRemainingTicks(pending.remainingTicks() - 1));
				return;
			}

			PENDING_RANGED_BOW_CHARGES.remove(skeletonId);
			if (RANGED_BOW_COOLDOWNS.containsKey(skeletonId)) {
				return;
			}
			if (!(skeleton.level() instanceof ServerLevel level)) {
				return;
			}
			Entity targetEntity = level.getEntity(pending.targetUuid());
			if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
				return;
			}
			if (fireRangedBowArrow(skeleton, target)) {
				RANGED_BOW_COOLDOWNS.put(skeletonId, resolveBowAttackIntervalTicks(skeleton));
			}
		}

		public static void onEntityCleanup(Entity entity) {
			if (entity instanceof AbstractSkeleton skeleton) {
				clearRangedSkeletonRuntimeState(skeleton);
			}
		}

		public static void resetRuntimeState() {
			PENDING_RANGED_BOW_CHARGES.clear();
			RANGED_BOW_COOLDOWNS.clear();
		}

		private static JsonObject resolveVariantGroupRoot(
			AbstractSkeleton skeleton,
			JsonObject fileConfigRoot,
			JsonObject fileRoot,
			ServerLevelAccessor world,
			boolean spawnContext
		) {
			JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(fileConfigRoot);
			if (defaultGroup.entrySet().isEmpty()) {
				clearVariantTag(skeleton);
				return new JsonObject();
			}

			String storedVariant = readStoredVariantKey(skeleton);
			if (!storedVariant.isBlank()) {
				JsonObject known = resolveVariantRootByKey(fileConfigRoot, storedVariant);
				if (!known.entrySet().isEmpty()) {
					return MobEntityManager.resolveVariantGroupRoot(defaultGroup, known);
				}
			}

			boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
			if (!spawnContext || !overrideSpawnRules || world == null) {
				return defaultGroup;
			}

			String selectedVariant = selectVariantKey(fileConfigRoot, world);
			if (selectedVariant.isBlank()) {
				return defaultGroup;
			}
			writeVariantTag(skeleton, selectedVariant);
			JsonObject selected = resolveVariantRootByKey(fileConfigRoot, selectedVariant);
			return selected.entrySet().isEmpty() ? defaultGroup : MobEntityManager.resolveVariantGroupRoot(defaultGroup, selected);
		}

		private static String selectVariantKey(JsonObject fileRoot, ServerLevelAccessor world) {
			return MobEntityManager.selectWeightedVariantKey(
				fileRoot,
				world == null ? null : world.getRandom(),
				BoggedBehavior::isReservedBoggedGroupKey,
				variantRoot -> MobEntityManager.resolveVariantSpawnWeight(variantRoot, 0.0D)
			);
		}

		private static JsonObject resolveVariantRootByKey(JsonObject fileRoot, String variantKey) {
			return MobEntityManager.resolveVariantRootByKey(
				fileRoot,
				variantKey,
				BoggedBehavior::isReservedBoggedGroupKey
			);
		}

		private static boolean isReservedBoggedGroupKey(String normalizedKey) {
			if (normalizedKey == null || normalizedKey.isBlank()) {
				return true;
			}
			return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_ENABLED))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_COMPONENTS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_BEHAVIORS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_GOALS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WEAPON_DAMAGE))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW));
		}

		private static String readStoredVariantKey(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return "";
			}
			for (String tag : skeleton.entityTags()) {
				if (tag == null || !tag.startsWith(BOGGED_VARIANT_TAG_PREFIX)) {
					continue;
				}
				String normalized = normalizeKey(tag.substring(BOGGED_VARIANT_TAG_PREFIX.length()));
				if (!normalized.isBlank()) {
					return normalized;
				}
			}
			return "";
		}

		private static void writeVariantTag(AbstractSkeleton skeleton, String variantKey) {
			if (skeleton == null || variantKey == null || variantKey.isBlank()) {
				return;
			}
			clearVariantTag(skeleton);
			skeleton.addTag(BOGGED_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
		}

		private static void clearVariantTag(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			String existing = null;
			for (String tag : skeleton.entityTags()) {
				if (tag != null && tag.startsWith(BOGGED_VARIANT_TAG_PREFIX)) {
					existing = tag;
					break;
				}
			}
			if (existing != null) {
				skeleton.removeTag(existing);
			}
		}

		private static void applyBehaviorToggles(AbstractSkeleton skeleton, JsonObject fileRoot, JsonObject variantRoot) {
			if (skeleton == null) {
				return;
			}
			boolean overrideBehavior = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIORS, true);
			if (overrideBehavior) {
				skeleton.setCanPickUpLoot(MobEntityManager.readMobBehaviorBooleanForRuntime(variantRoot, MobConfigManager.FIELD_CAN_PICK_UP_LOOT, true));
			}
		}

		private static void applyWeaponDamagePolicy(AbstractSkeleton skeleton, JsonObject resolvedRoot) {
			if (skeleton == null || resolvedRoot == null) {
				return;
			}
			boolean weaponDamageEnabled = readBoolean(resolvedRoot, MobConfigManager.FIELD_WEAPON_DAMAGE, true);
			if (weaponDamageEnabled) {
				return;
			}
			stripHeldAttackDamageModifiers(skeleton, EquipmentSlot.MAINHAND);
			stripHeldAttackDamageModifiers(skeleton, EquipmentSlot.OFFHAND);
		}

		private static boolean fireRangedBowArrow(AbstractSkeleton skeleton, LivingEntity target) {
			if (skeleton == null || target == null || !target.isAlive() || !(skeleton.level() instanceof ServerLevel level)) {
				return false;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty()) {
				return false;
			}

			ensureBowEquipped(skeleton);
			InteractionHand bowHand = resolveBowHand(skeleton);
			if (bowHand == null) {
				return false;
			}
			ItemStack bowStack = skeleton.getItemInHand(bowHand);
			ItemStack projectileStack = skeleton.getProjectile(bowStack);
			if (projectileStack.isEmpty()) {
				projectileStack = new ItemStack(Items.ARROW);
			}
			AbstractArrow arrow = ((AbstractSkeletonArrowInvoker) skeleton).madokuCraft$invokeGetArrow(projectileStack, 1.0F, bowStack);
			if (arrow == null) {
				return false;
			}

			double accuracy = resolveScaledAttackAccuracy(
				readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_ATTACK_ACCURACY, 0.7D),
				skeleton.level().getDifficulty(),
				isHardcoreWorld(skeleton.level())
			);
			double rangedDamage = resolveSkeletonRangedDamage(skeleton, root);
			accuracy = MobRegionalDifficultyManager.resolveMobAttackAccuracyScaling(skeleton, accuracy);
			ShotVector shot = resolveShotVector(skeleton, arrow, target, accuracy);
			arrow.shoot(shot.vector.x, shot.vector.y, shot.vector.z, 1.6F, 0.0F);
			arrow.setCritArrow(false);
			MobEntityManager.setProjectileDamageOverride(arrow, (float) Math.max(0.0D, rangedDamage));
			MobEntityManager.trackManagedMobArrowForRuntime(arrow, level.getServer());
			if (shot.guaranteedHit) {
				MobEntityManager.startProjectileHoming(arrow, target, level.getServer());
			}
			skeleton.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
			level.addFreshEntity(arrow);
			return true;
		}

		private static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton, int fallback) {
			if (skeleton == null || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return fallback;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty() || !MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false)) {
				return fallback;
			}
			double interval = readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_ATTACK_INTERVAL, fallback);
			return Math.max(1, (int) Math.round(interval));
		}

		private static int resolveBowChargeUpTicks(AbstractSkeleton skeleton, int fallback) {
			if (skeleton == null || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return fallback;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty() || !MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false)) {
				return fallback;
			}
			double charge = readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_CHARGE_INTERVAL, fallback);
			return Math.max(0, (int) Math.round(charge));
		}

		private static void clearRangedSkeletonRuntimeState(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			UUID skeletonId = skeleton.getUUID();
			PENDING_RANGED_BOW_CHARGES.remove(skeletonId);
			RANGED_BOW_COOLDOWNS.remove(skeletonId);
		}

		private static InteractionHand resolveBowHand(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return null;
			}
			if (skeleton.getMainHandItem().is(Items.BOW)) {
				return InteractionHand.MAIN_HAND;
			}
			if (skeleton.getOffhandItem().is(Items.BOW)) {
				return InteractionHand.OFF_HAND;
			}
			return null;
		}

		private static JsonObject mergeFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
			JsonObject merged = variantRoot == null ? new JsonObject() : variantRoot.deepCopy();
			if (fileRoot == null || fileRoot.entrySet().isEmpty()) {
				return merged;
			}
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_CUSTOM_MOB_DROPS);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WEAPON_DAMAGE);
			return merged;
		}

		private static String fileKeyForType(AbstractSkeleton skeleton) {
			if (skeleton == null || skeleton.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED) {
				return "";
			}
			return MobConfigManager.FILE_BOGGED;
		}
		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			return element.getAsBoolean();
		}

		private static double readDouble(JsonObject root, String key, double fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
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

		private static JsonObject readObject(JsonObject root, String key) {
			if (root == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			JsonElement element = EntityConfigManager.resolveConfiguredElement(root, key);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		}

		private static void copyIfMissing(JsonObject target, JsonObject source, String key) {
			if (target == null || source == null || key == null || key.isBlank()) {
				return;
			}
			if (!target.has(key) && source.has(key)) {
				target.add(key, source.get(key).deepCopy());
			}
		}

		private static String normalizeKey(String value) {
			return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		}

		private static boolean isHardcoreWorld(Level level) {
			return level != null && level.getServer() != null && level.getServer().isHardcore();
		}

		private static double resolveSkeletonRangedDamage(AbstractSkeleton skeleton, JsonObject root) {
			if (skeleton == null) {
				return 0.0D;
			}
			double rangedDamage = resolveScaledRangedDamage(skeleton,
				readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_RANGED_DAMAGE, 4.0D),
				skeleton.level().getDifficulty(),
				isHardcoreWorld(skeleton.level())
			);
			return rangedDamage;
		}

		private static double resolveScaledRangedDamage(AbstractSkeleton skeleton, double base, Difficulty difficulty, boolean hardcore) {
			double regional = MobRegionalDifficultyManager.resolveMobRangedDamageScaling(skeleton, base);
			return MobEntityManager.resolveWorldDifficultyValueForRuntime(skeleton, MobConfigManager.FIELD_RANGED_DAMAGE, regional);
		}

		private static double resolveScaledAttackAccuracy(double base, Difficulty difficulty, boolean hardcore) {
			return Mth.clamp(resolveDifficultyAdjustedValue(difficulty, hardcore, Mth.clamp(base, 0.0D, 1.0D), 0.05D, 0.0D), 0.0D, 1.0D);
		}

		private static double resolveDifficultyAdjustedValue(Difficulty difficulty, boolean hardcore, double baseValue, double step, double minimum) {
			return roundDifficultyScaleValue(Math.max(minimum, baseValue + (step * resolveDifficultyTier(difficulty, hardcore))));
		}

		private static double roundDifficultyScaleValue(double value) {
			if (!Double.isFinite(value)) {
				return value;
			}
			double step = isWholeNumber(value) ? 0.05D : 0.005D;
			return Math.round(value / step) * step;
		}

		private static boolean isWholeNumber(double value) {
			return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
		}

		private static int resolveDifficultyTier(Difficulty difficulty, boolean hardcore) {
			Difficulty resolved = difficulty == null ? Difficulty.NORMAL : difficulty;
			return switch (resolved) {
				case PEACEFUL -> -2;
				case EASY -> -1;
				case NORMAL -> 0;
				case HARD -> hardcore ? 2 : 1;
			};
		}

		private static ShotVector resolveShotVector(AbstractSkeleton skeleton, AbstractArrow arrow, LivingEntity target, double accuracy) {
			double dx = target.getX() - skeleton.getX();
			double dz = target.getZ() - skeleton.getZ();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			double dy = target.getY(1.0D / 3.0D) - arrow.getY() + (horizontal * 0.2D);
			Vec3 desired = new Vec3(dx, dy, dz);
			if (desired.lengthSqr() <= 1.0E-6D) {
				return new ShotVector(desired, true);
			}
			double clampedAccuracy = Mth.clamp(accuracy, 0.0D, 1.0D);
			if (skeleton.getRandom().nextDouble() <= clampedAccuracy) {
				return new ShotVector(desired, true);
			}
			return new ShotVector(resolveMissVector(desired.x, desired.y, desired.z, clampedAccuracy, skeleton), false);
		}

		private static Vec3 resolveMissVector(double velocityX, double velocityY, double velocityZ, double attackAccuracy, LivingEntity shooter) {
			Vec3 desired = new Vec3(velocityX, velocityY, velocityZ);
			if (desired.lengthSqr() <= 1.0E-6D) {
				return desired;
			}
			Vec3 normalized = desired.normalize();
			Vec3 lateral = normalized.cross(new Vec3(0.0D, 1.0D, 0.0D));
			if (lateral.lengthSqr() <= 1.0E-6D) {
				lateral = normalized.cross(new Vec3(1.0D, 0.0D, 0.0D));
			}
			if (lateral.lengthSqr() > 1.0E-6D) {
				lateral = lateral.normalize();
			}
			double missFactor = 1.0D - Mth.clamp(attackAccuracy, 0.0D, 1.0D);
			double sideSign = shooter.getRandom().nextBoolean() ? -1.0D : 1.0D;
			double lateralStrength = Mth.lerp(missFactor, 1.4D, 2.4D);
			double verticalStrength = Mth.lerp(missFactor, 0.25D, 0.9D) * (shooter.getRandom().nextBoolean() ? -1.0D : 1.0D);
			Vec3 backwardBias = normalized.scale(-0.35D);
			Vec3 miss = lateral.scale(sideSign * lateralStrength).add(0.0D, verticalStrength, 0.0D).add(backwardBias);
			return miss.lengthSqr() <= 1.0E-6D ? lateral : miss.normalize();
		}

		private static void stripHeldAttackDamageModifiers(AbstractSkeleton skeleton, EquipmentSlot slot) {
			if (skeleton == null || slot == null) {
				return;
			}
			ItemStack stack = skeleton.getItemBySlot(slot);
			if (stack == null || stack.isEmpty()) {
				return;
			}
			ItemStack normalized = stack.copy();
			normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
			skeleton.setItemSlot(slot, normalized);
		}

		private static JsonObject readMobComponentsRoot(JsonObject root) {
			return readObject(root, MobConfigManager.FIELD_MOB_COMPONENTS);
		}

		private record PendingRangedBowCharge(UUID targetUuid, int remainingTicks) {
			private PendingRangedBowCharge withRemainingTicks(int remainingTicks) {
				return new PendingRangedBowCharge(targetUuid, remainingTicks);
			}
		}

		private record ShotVector(Vec3 vector, boolean guaranteedHit) {
		}

	}

	public static final class CaveSpiderBehavior {
		private CaveSpiderBehavior() {
		}

		public static void applySpawnOverrides(
			Spider spider,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason
		) {
			if (spider == null || world == null || difficulty == null || !MobEntityManager.isEnabled()) {
				return;
			}
			if (spider.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.CAVE_SPIDER) {
				return;
			}
			String fileKey = MobConfigManager.FILE_CAVE_SPIDER;
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return;
			}
			JsonObject fileRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject caveSpiderRoot = readMobRoot(fileRoot, fileKey);
			if (caveSpiderRoot.entrySet().isEmpty()) {
				return;
			}
			boolean overrideStats = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			if (overrideStats) {
				MobEntityManager.applyUniversalBaseStatsForRuntime(spider, caveSpiderRoot);
			}
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			if (!(entity instanceof Spider spider) || spider.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = MobConfigManager.FILE_CAVE_SPIDER;
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject fileRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject caveSpiderRoot = readMobRoot(fileRoot, fileKey);
			boolean overrideStats = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			return overrideStats && MobEntityManager.applyUniversalBaseStatsForRuntime(spider, caveSpiderRoot);
		}

		static boolean isCustomMobDropsEnabled(LivingEntity entity) {
			if (!(entity instanceof Spider spider) || spider.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = MobConfigManager.FILE_CAVE_SPIDER;
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject resolved = resolveActiveCaveSpiderRoot(spider);
			return readBoolean(resolved, MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		}

		static String resolveMobDropsConfigReference(LivingEntity entity) {
			if (!(entity instanceof Spider spider) || !MobEntityManager.isEnabled()) {
				return "";
			}
			String fileKey = MobConfigManager.FILE_CAVE_SPIDER;
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return "";
			}
			JsonObject resolved = resolveActiveCaveSpiderRoot(spider);
			JsonObject componentsRoot = readObject(resolved, MobConfigManager.FIELD_MOB_COMPONENTS);
			return readString(componentsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
		}

		private static JsonObject resolveActiveCaveSpiderRoot(Spider spider) {
			if (spider == null) {
				return new JsonObject();
			}
			JsonObject fileRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_CAVE_SPIDER);
			return readMobRoot(fileRoot, MobConfigManager.FILE_CAVE_SPIDER);
		}

		private static JsonObject readMobRoot(JsonObject fileRoot, String fileKey) {
			if (fileRoot == null || fileKey == null || fileKey.isBlank()) {
				return new JsonObject();
			}
			return EntityConfigManager.resolvePrimaryVariant(fileRoot);
		}

		private static JsonObject readObject(JsonObject parent, String key) {
			if (parent == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			JsonElement element = EntityConfigManager.resolveConfiguredElement(parent, key);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		}

		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() ? element.getAsBoolean() : fallback;
		}

		private static String readString(JsonObject root, String key, String fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				return fallback;
			}
			return element.getAsString();
		}
	}

	public static final class CreeperBehavior {
		private CreeperBehavior() {
		}

		public static void applySpawnOverrides(Creeper creeper, ServerLevelAccessor world, DifficultyInstance difficulty) {
			MobEntityManager.applyCreeperSpawnOverrides(creeper, world, difficulty);
		}

		public static boolean shouldOverrideSpawnRules(Creeper creeper) {
			if (creeper == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			if (!MobEntityManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_CREEPER)) {
				return false;
			}
			JsonObject root = MobEntityManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_CREEPER);
			return readBoolean(root, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			if (!(entity instanceof Creeper creeper) || creeper.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			if (!MobEntityManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_CREEPER)) {
				return false;
			}
			JsonObject root = MobEntityManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_CREEPER);
			return MobEntityManager.applyCreeperRuntimeStats(creeper, root);
		}

		public static boolean applyLoadedExplosionDifficultyScaling(LivingEntity entity) {
			if (!(entity instanceof Creeper creeper) || creeper.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			return MobEntityManager.applyCreeperLoadedEntityDifficultyOverrides(creeper);
		}

		public static void applyExplosionOverride(
			Creeper creeper,
			ServerLevel level,
			Entity source,
			double x,
			double y,
			double z,
			float vanillaPower,
			Level.ExplosionInteraction vanillaInteraction
		) {
			MobEntityManager.applyCreeperExplosionOverride(creeper, level, source, x, y, z, vanillaPower, vanillaInteraction);
		}

		public static float resolveGriefExplosionRadius(ServerExplosion explosion, float fallbackRadius) {
			return MobEntityManager.resolveCreeperGriefExplosionRadius(explosion, fallbackRadius);
		}

		public static float resolveFixedPlayerExplosionDamage(Creeper creeper, float fallbackExplosionRadius) {
			return MobEntityManager.resolveFixedPlayerExplosionDamage(creeper, fallbackExplosionRadius);
		}

		public static boolean shouldUseMobExplodeBehavior(Creeper creeper) {
			return MobEntityManager.shouldUseCreeperMobExplodeBehavior(creeper);
		}

		public static boolean shouldUseMobExplodeBehavior(LivingEntity entity) {
			return entity instanceof Creeper creeper && shouldUseMobExplodeBehavior(creeper);
		}

		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			return element.getAsBoolean();
		}
	}

	public static final class DrownedBehavior {
		private static final double MIN_DROWNED_TRIDENT_HOMING_DISTANCE_SQR = 4.0D;
		private static final String DROWNED_VARIANT_TAG_PREFIX = "madoku-craft.drowned.variant:";
		private static final String DROWNED_VARIANT_RANGED_KEY = "ranged-drowned";
		private static final String RANGED_TRIDENT_TAG = "madoku-craft.drowned.ranged_trident";

		private static String normalizeKey(String value) {
			return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		}

		private static boolean invokeTridentMethod(Entity trident, String methodName, Class<?>[] parameterTypes, Object... arguments) {
			if (trident == null || methodName == null || methodName.isBlank()) {
				return false;
			}
			Class<?> type = trident.getClass();
			while (type != null) {
				try {
					java.lang.reflect.Method method = type.getDeclaredMethod(methodName, parameterTypes);
					method.setAccessible(true);
					method.invoke(trident, arguments);
					return true;
				} catch (NoSuchMethodException exception) {
					type = type.getSuperclass();
				} catch (ReflectiveOperationException exception) {
					return false;
				}
			}
			return false;
		}

		private static final Map<UUID, PendingRangedTridentCharge> PENDING_RANGED_TRIDENT_CHARGES = new java.util.HashMap<>();
		private static final Map<UUID, Integer> RANGED_TRIDENT_COOLDOWNS = new java.util.HashMap<>();

		private DrownedBehavior() {
		}

		public static void applySpawnOverrides(
			Drowned drowned,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason
		) {
			if (drowned == null || world == null || difficulty == null) {
				return;
			}
			String fileKey = fileKeyForType(drowned.getType());
			if (fileKey.isBlank()) {
				return;
			}
			if (!MobEntityManager.isEnabled()) {
				applySpawnEquipmentLoadoutWhenMobSystemDisabled(drowned, world.getRandom());
				return;
			}
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveDrownedRootForRuntime(drowned.getType());
			JsonObject variantGroup = resolveDrownedVariantGroupRoot(drowned, fileConfigRoot, fileRoot, world, true);
			JsonObject defaultGroup = variantGroup;
			if (defaultGroup.entrySet().isEmpty()) {
				return;
			}

			boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);

			JsonObject variant = MobEntityManager.resolveNestedVariantForRuntime(
				variantGroup,
				drowned,
				overrideSpawnRules ? world.getRandom() : null,
				overrideSpawnRules
			);
			variant = mergeDrownedFileSettings(fileRoot, variant);
			if (overrideSpawnRules) {
			}
			ensureRangedDrownedTridentEquipped(drowned);
			applyWeaponDamagePolicy(drowned, variant);
			applyDrownedBehaviorToggles(drowned, fileConfigRoot, variant);
			if (overrideStats) {
				MobEntityManager.applyUniversalBaseStatsForRuntime(drowned, variant);
			}
		}

		public static boolean shouldOverrideSpawnRules(Drowned drowned) {
			if (drowned == null || drowned.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.DROWNED) {
				return false;
			}
			if (!MobEntityManager.isEnabled() || !MobEntityManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_DROWNED)) {
				return false;
			}
			JsonObject drownedFileRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_DROWNED);
			return readBoolean(drownedFileRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			if (!(entity instanceof Drowned drowned) || entity.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(drowned.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveDrownedRootForRuntime(drowned.getType());
			JsonObject variantGroup = resolveDrownedVariantGroupRoot(drowned, fileConfigRoot, fileRoot, null, false);
			JsonObject variant = MobEntityManager.resolveNestedVariantForRuntime(variantGroup, drowned, null, false);
			variant = mergeDrownedFileSettings(fileRoot, variant);

			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			boolean modified = overrideStats && MobEntityManager.applyUniversalBaseStatsForRuntime(drowned, variant);
			ensureRangedDrownedTridentEquipped(drowned);
			applyWeaponDamagePolicy(drowned, variant);
			applyDrownedBehaviorToggles(drowned, fileConfigRoot, variant);
			return modified;
		}

		public static boolean shouldAllowUnderwaterTargeting(Drowned drowned, LivingEntity target) {
			if (drowned == null || target == null) {
				return true;
			}
			if (drowned.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return true;
			}
			String fileKey = fileKeyForType(drowned.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return true;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveDrownedRootForRuntime(drowned.getType());
			JsonObject variantGroup = resolveDrownedVariantGroupRoot(drowned, fileConfigRoot, fileRoot, null, false);
			JsonObject variant = MobEntityManager.resolveNestedVariantForRuntime(variantGroup, drowned, null, false);
			variant = mergeDrownedFileSettings(fileRoot, variant);
			return true;
		}

		public static double resolveSwimmingSpeedForRuntime(Drowned drowned, double fallback) {
			if (drowned == null) {
				return fallback;
			}
			String fileKey = fileKeyForType(drowned.getType());
			if (fileKey.isBlank() || !MobEntityManager.isEnabled() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return fallback;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveDrownedRootForRuntime(drowned.getType());
			JsonObject variantGroup = resolveDrownedVariantGroupRoot(drowned, fileConfigRoot, fileRoot, null, false);
			JsonObject variant = MobEntityManager.resolveNestedVariantForRuntime(variantGroup, drowned, null, false);
			variant = mergeDrownedFileSettings(fileRoot, variant);
			JsonObject componentsRoot = readObject(variant, MobConfigManager.FIELD_MOB_COMPONENTS);
			return readDouble(componentsRoot, MobConfigManager.FIELD_SWIMMING_SPEED, fallback);
		}

		static boolean isCustomMobDropsEnabled(LivingEntity entity) {
			if (!(entity instanceof Drowned drowned) || entity.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(drowned.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject resolved = resolveActiveDrownedRoot(drowned);
			boolean enabled = readBoolean(
				resolved,
				MobConfigManager.FIELD_CUSTOM_MOB_DROPS,
				true
			);
			return enabled;
		}

		static String resolveMobDropsConfigReference(LivingEntity entity) {
			if (!(entity instanceof Drowned drowned) || !MobEntityManager.isEnabled()) {
				return "";
			}
			String fileKey = fileKeyForType(drowned.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return "";
			}
			JsonObject resolved = resolveActiveDrownedRoot(drowned);
			JsonObject componentsRoot = readObject(resolved, MobConfigManager.FIELD_MOB_COMPONENTS);
			String reference = readString(componentsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
			return reference;
		}

		private static JsonObject resolveActiveDrownedRoot(Drowned drowned) {
			if (drowned == null) {
				return new JsonObject();
			}
			JsonObject fileKeyRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_DROWNED);
			JsonObject fileRoot = MobEntityManager.resolveDrownedRootForRuntime(drowned.getType());
			JsonObject defaultGroup = resolveDrownedVariantGroupRoot(drowned, fileKeyRoot, fileRoot, null, false);
			JsonObject variant = MobEntityManager.resolveNestedVariantForRuntime(defaultGroup, drowned, null, false);
			return mergeDrownedFileSettings(fileRoot, variant);
		}

		private static JsonObject resolveDrownedVariantGroupRoot(
			Drowned drowned,
			JsonObject fileConfigRoot,
			JsonObject fileRoot,
			ServerLevelAccessor world,
			boolean spawnContext
		) {
			JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(fileConfigRoot);
			if (defaultGroup.entrySet().isEmpty()) {
				clearDrownedVariantTag(drowned);
				return new JsonObject();
			}
			String storedVariant = readStoredDrownedVariantKey(drowned);
			if (!storedVariant.isBlank()) {
				JsonObject known = resolveDrownedVariantRootByKey(fileConfigRoot, storedVariant);
				if (!known.entrySet().isEmpty()) {
					return MobEntityManager.resolveVariantGroupRoot(defaultGroup, known);
				}
			}

			boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
			if (!spawnContext || !overrideSpawnRules || world == null) {
				return defaultGroup;
			}

			String selectedVariant = selectDrownedVariantKey(fileConfigRoot, world);
			if (selectedVariant.isBlank()) {
				return defaultGroup;
			}
			writeDrownedVariantTag(drowned, selectedVariant);
			JsonObject selected = resolveDrownedVariantRootByKey(fileConfigRoot, selectedVariant);
			return selected.entrySet().isEmpty() ? defaultGroup : MobEntityManager.resolveVariantGroupRoot(defaultGroup, selected);
		}

		private static String selectDrownedVariantKey(JsonObject fileRoot, ServerLevelAccessor world) {
			return MobEntityManager.selectWeightedVariantKey(
				fileRoot,
				world == null ? null : world.getRandom(),
				DrownedBehavior::isReservedDrownedGroupKey,
				variantRoot -> resolveDrownedVariantSpawnWeight(variantRoot, 0.0D)
			);
		}

		private static double resolveDrownedVariantSpawnWeight(JsonObject variantRoot, double fallback) {
			return MobEntityManager.resolveVariantSpawnWeight(variantRoot, fallback);
		}

		private static boolean isReservedDrownedGroupKey(String normalizedKey) {
			if (normalizedKey == null || normalizedKey.isBlank()) {
				return true;
			}
			return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WEAPON_DAMAGE));
		}

		private static JsonObject resolveDrownedVariantRootByKey(JsonObject fileRoot, String variantKey) {
			return MobEntityManager.resolveVariantRootByKey(
				fileRoot,
				variantKey,
				DrownedBehavior::isReservedDrownedGroupKey
			);
		}

		private static String readStoredDrownedVariantKey(Drowned drowned) {
			if (drowned == null) {
				return "";
			}
			for (String tag : drowned.entityTags()) {
				if (tag == null || !tag.startsWith(DROWNED_VARIANT_TAG_PREFIX)) {
					continue;
				}
				String normalized = normalizeKey(tag.substring(DROWNED_VARIANT_TAG_PREFIX.length()));
				if (!normalized.isBlank()) {
					return normalized;
				}
			}
			return "";
		}

		private static void writeDrownedVariantTag(Drowned drowned, String variantKey) {
			if (drowned == null || variantKey == null || variantKey.isBlank()) {
				return;
			}
			clearDrownedVariantTag(drowned);
			drowned.addTag(DROWNED_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
		}

		private static void clearDrownedVariantTag(Drowned drowned) {
			if (drowned == null) {
				return;
			}
			String existing = null;
			for (String tag : drowned.entityTags()) {
				if (tag != null && tag.startsWith(DROWNED_VARIANT_TAG_PREFIX)) {
					existing = tag;
					break;
				}
			}
			if (existing != null) {
				drowned.removeTag(existing);
			}
		}

		private static JsonObject mergeDrownedFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
			JsonObject merged = variantRoot == null ? new JsonObject() : variantRoot.deepCopy();
			if (fileRoot == null || fileRoot.entrySet().isEmpty()) {
				return merged;
			}
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_CUSTOM_MOB_DROPS);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WEAPON_DAMAGE);
			return merged;
		}

		private static EquipmentLoadoutResult applySpawnEquipmentLoadoutWhenMobSystemDisabled(Drowned drowned, RandomSource random) {
			if (drowned == null || random == null) {
				return new EquipmentLoadoutResult(false, "invalid_inputs", "", 0.0D, "none", 0, 0);
			}
			if (!EquipmentConfigManager.isCustomEntityEquipmentEnabled()) {
				return new EquipmentLoadoutResult(false, "custom_entity_equipment_disabled", "", 0.0D, "none", 0, 0);
			}
			String equipmentReference = resolveDefaultMobEquipmentReference(drowned.getType());
			if (equipmentReference.isBlank()) {
				return new EquipmentLoadoutResult(false, "default_reference_blank", equipmentReference, 0.0D, "none", 0, 0);
			}
			return applyEquipmentSetLoadout(
				drowned,
				equipmentReference,
				EquipmentConfigManager.customEntityEquipmentChanceWhenMobSystemDisabled(),
				random
			);
		}

		private static EquipmentLoadoutResult applyEquipmentSetLoadout(
			Drowned drowned,
			String equipmentReference,
			double chancePercent,
			RandomSource random
		) {
			if (drowned == null || random == null) {
				return new EquipmentLoadoutResult(false, "invalid_inputs", equipmentReference, chancePercent, "none", 0, 0);
			}
			if (chancePercent <= 0.0D || random.nextDouble() * 100.0D >= chancePercent) {
				return new EquipmentLoadoutResult(false, "chance_failed", equipmentReference, chancePercent, "none", 0, 0);
			}
			EquipmentConfigManager.EquipmentProfile profile = EquipmentConfigManager.resolveProfile(equipmentReference, drowned.getType());
			if (profile == null || !profile.enabled()) {
				return new EquipmentLoadoutResult(false, "profile_missing_or_disabled", equipmentReference, chancePercent, "none", 0, 0);
			}

			Map<EquipmentSlot, ItemStack> rolledBySlot = EquipmentConfigManager.rollEquipment(profile, random);
			if (rolledBySlot.isEmpty()) {
				return new EquipmentLoadoutResult(
					false,
					"slot_pool_empty",
					equipmentReference,
					chancePercent,
					"api",
					0,
					0
				);
			}
			MobEntityManager.clearArmorSlotsForRuntime(drowned);
			for (Map.Entry<EquipmentSlot, ItemStack> entry : rolledBySlot.entrySet()) {
				drowned.setItemSlot(entry.getKey(), entry.getValue());
			}
			return new EquipmentLoadoutResult(
				true,
				"applied",
				equipmentReference,
				chancePercent,
				"api",
				rolledBySlot.size(),
				rolledBySlot.size()
			);
		}

		private static void applyDrownedBehaviorToggles(Drowned drowned, JsonObject fileRoot, JsonObject variantRoot) {
			if (drowned == null) {
				return;
			}
			JsonObject behaviorRoot = MobEntityManager.readMobBehaviorRootForRuntime(variantRoot);

			boolean overrideBehavior = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIORS, true);

			if (overrideBehavior) {
				drowned.setCanPickUpLoot(MobEntityManager.readMobBehaviorBooleanForRuntime(variantRoot, MobConfigManager.FIELD_CAN_PICK_UP_LOOT, true));
			}
			if (overrideBehavior) {
				boolean callsReinforcements = readBoolean(behaviorRoot, MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, false);
				if (!callsReinforcements) {
					MobEntityManager.disableZombieReinforcementsForRuntime(drowned);
				}
				drowned.setSearchingForLand(true);
			}
		}

		private static void applyWeaponDamagePolicy(Drowned drowned, JsonObject resolvedRoot) {
			if (drowned == null || resolvedRoot == null) {
				return;
			}
			boolean weaponDamageEnabled = readBoolean(resolvedRoot, MobConfigManager.FIELD_WEAPON_DAMAGE, true);
			if (weaponDamageEnabled) {
				return;
			}
			stripHeldAttackDamageModifiers(drowned, EquipmentSlot.MAINHAND);
			stripHeldAttackDamageModifiers(drowned, EquipmentSlot.OFFHAND);
		}

		public static void tickRangedDrownedRuntime(Drowned drowned) {
			if (drowned == null || drowned.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return;
			}
			String fileKey = fileKeyForType(drowned.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey) || !isRangedDrownedVariant(drowned)) {
				clearRangedDrownedRuntimeState(drowned);
				return;
			}

			UUID drownedId = drowned.getUUID();
			Integer cooldown = RANGED_TRIDENT_COOLDOWNS.get(drownedId);
			if (cooldown != null) {
				if (cooldown <= 1) {
					RANGED_TRIDENT_COOLDOWNS.remove(drownedId);
				} else {
					RANGED_TRIDENT_COOLDOWNS.put(drownedId, cooldown - 1);
				}
			}

			PendingRangedTridentCharge pending = PENDING_RANGED_TRIDENT_CHARGES.get(drownedId);
			if (pending == null) {
				return;
			}
			if (pending.remainingTicks() > 1) {
				PENDING_RANGED_TRIDENT_CHARGES.put(drownedId, pending.withRemainingTicks(pending.remainingTicks() - 1));
				return;
			}

			PENDING_RANGED_TRIDENT_CHARGES.remove(drownedId);
			if (RANGED_TRIDENT_COOLDOWNS.containsKey(drownedId)) {
				return;
			}
			if (!(drowned.level() instanceof ServerLevel level)) {
				return;
			}
			Entity targetEntity = level.getEntity(pending.targetUuid());
			if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
				return;
			}
			if (fireRangedDrownedTrident(drowned, target)) {
				RANGED_TRIDENT_COOLDOWNS.put(drownedId, resolveTridentAttackIntervalTicks(drowned));
			}
		}

		public static boolean applyRangedDrownedTridentAttack(Drowned drowned, LivingEntity target, float pullProgress) {
			if (drowned == null || target == null || !target.isAlive() || drowned.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(drowned.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey) || !isRangedDrownedVariant(drowned)) {
				return false;
			}
			UUID drownedId = drowned.getUUID();
			int cooldown = RANGED_TRIDENT_COOLDOWNS.getOrDefault(drownedId, 0);
			if (cooldown > 0) {
				return true;
			}
			PendingRangedTridentCharge pending = PENDING_RANGED_TRIDENT_CHARGES.get(drownedId);
			if (pending != null) {
				return true;
			}
			int chargeUpTicks = resolveTridentChargeUpTicks(drowned);
			if (chargeUpTicks <= 0) {
				if (fireRangedDrownedTrident(drowned, target)) {
					RANGED_TRIDENT_COOLDOWNS.put(drownedId, resolveTridentAttackIntervalTicks(drowned));
				}
				return true;
			}
			PENDING_RANGED_TRIDENT_CHARGES.put(drownedId, new PendingRangedTridentCharge(target.getUUID(), chargeUpTicks));
			return true;
		}

		public static int resolveTridentAttackIntervalTicks(Drowned drowned) {
			return resolveTridentAttackIntervalTicks(drowned, 20);
		}

		public static int resolveTridentChargeUpTicks(Drowned drowned) {
			return resolveTridentChargeUpTicks(drowned, 10);
		}

		public static boolean isRangedDrownedVariant(Drowned drowned) {
			return drowned != null && DROWNED_VARIANT_RANGED_KEY.equals(readStoredDrownedVariantKey(drowned));
		}

		public static int resolveTridentGroundClearTicks(net.minecraft.world.entity.projectile.arrow.ThrownTrident trident) {
			if (trident == null) {
				return -1;
			}
			Entity owner = trident.getOwner();
			if (owner instanceof Drowned drowned && isRangedDrownedVariant(drowned)) {
				return resolveTridentGroundClearTicks(drowned);
			}
			return trident.entityTags().contains(RANGED_TRIDENT_TAG) ? 300 : -1;
		}

		public static boolean isRangedDrownedTrident(Entity entity) {
			return entity instanceof net.minecraft.world.entity.projectile.arrow.ThrownTrident trident && trident.entityTags().contains(RANGED_TRIDENT_TAG);
		}

		private static boolean fireRangedDrownedTrident(Drowned drowned, LivingEntity target) {
			if (drowned == null || target == null || !target.isAlive() || !(drowned.level() instanceof ServerLevel level)) {
				return false;
			}
			JsonObject activeRoot = resolveActiveDrownedRoot(drowned);
			if (activeRoot.entrySet().isEmpty()) {
				return false;
			}
			JsonObject behaviorRoot = MobEntityManager.readMobBehaviorRootForRuntime(activeRoot);
			if (!readBoolean(behaviorRoot, MobConfigManager.FIELD_TRIDENT_ATTACK, true)) {
				return false;
			}
			JsonObject componentsRoot = readObject(activeRoot, MobConfigManager.FIELD_MOB_COMPONENTS);
			double accuracy = resolveTridentAttackAccuracy(drowned, target, readDouble(componentsRoot, MobConfigManager.FIELD_ATTACK_ACCURACY, drowned.isBaby() ? 0.5D : 0.7D));
			double baseDamage = readDouble(componentsRoot, MobConfigManager.FIELD_RANGED_DAMAGE, drowned.isBaby() ? 3.0D : 6.0D);
			double rangedDamage = resolveTridentRangedDamage(drowned, baseDamage);
			ItemStack tridentStack = drowned.getMainHandItem();
			if (!tridentStack.is(Items.TRIDENT)) {
				tridentStack = new ItemStack(Items.TRIDENT);
			}
			net.minecraft.world.entity.projectile.arrow.ThrownTrident trident = new net.minecraft.world.entity.projectile.arrow.ThrownTrident(level, drowned, tridentStack.copy());
			invokeTridentMethod(trident, "setOwner", new Class<?>[] { Entity.class }, drowned);
			trident.setBaseDamage(Math.max(0.0D, rangedDamage));
			MobEntityManager.setProjectileDamageOverride(trident, (float) rangedDamage);
			trident.addTag(RANGED_TRIDENT_TAG);

			double dx = target.getX() - drowned.getX();
			double dz = target.getZ() - drowned.getZ();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			double dy = target.getY(1.0D / 3.0D) - trident.getY() + (horizontal * 0.2D);
			Vec3 desired = new Vec3(dx, dy, dz);
			Vec3 shot = desired;
			boolean homing = false;
			boolean homingEligible = drowned.distanceToSqr(target) > MIN_DROWNED_TRIDENT_HOMING_DISTANCE_SQR;
			if (desired.lengthSqr() > 1.0E-6D) {
				double clampedAccuracy = Mth.clamp(accuracy, 0.0D, 1.0D);
				if (drowned.getRandom().nextDouble() > clampedAccuracy) {
					shot = resolveMissVector(desired.x, desired.y, desired.z, clampedAccuracy, drowned);
				} else if (homingEligible) {
					homing = true;
				}
			}
			trident.shoot(shot.x, shot.y, shot.z, 1.6F, 0.0F);
			trident.setCritArrow(false);
			trident.setNoPhysics(false);
			drowned.playSound(SoundEvents.TRIDENT_THROW.value(), 1.0F, 1.0F / (drowned.getRandom().nextFloat() * 0.4F + 0.8F));
			if (homing) {
				MobEntityManager.startProjectileHoming(trident, target, level.getServer());
			}
			level.addFreshEntity(trident);
			return true;
		}

		private static double resolveTridentAttackAccuracy(Drowned drowned, LivingEntity target, double baseAccuracy) {
			if (drowned == null) {
				return baseAccuracy;
			}
			double accuracy = resolveScaledAttackAccuracy(baseAccuracy, drowned.level().getDifficulty(), isHardcoreWorld(drowned.level()));
			accuracy = MobRegionalDifficultyManager.resolveMobAttackAccuracyScaling(drowned, accuracy);
			return Mth.clamp(accuracy, 0.0D, 1.0D);
		}

		private static double resolveTridentRangedDamage(Drowned drowned, double baseDamage) {
			if (drowned == null) {
				return baseDamage;
			}
			double damage = resolveScaledRangedDamage(drowned, baseDamage, drowned.level().getDifficulty(), isHardcoreWorld(drowned.level()));
			return Math.max(0.0D, damage);
		}

		private static int resolveTridentAttackIntervalTicks(Drowned drowned, int fallback) {
			if (drowned == null || drowned.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return fallback;
			}
			JsonObject activeRoot = resolveActiveDrownedRoot(drowned);
			JsonObject componentsRoot = readObject(activeRoot, MobConfigManager.FIELD_MOB_COMPONENTS);
			double interval = readDouble(componentsRoot, MobConfigManager.FIELD_ATTACK_INTERVAL, fallback);
			return Math.max(1, (int) Math.round(interval));
		}

		private static int resolveTridentChargeUpTicks(Drowned drowned, int fallback) {
			if (drowned == null || drowned.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return fallback;
			}
			JsonObject activeRoot = resolveActiveDrownedRoot(drowned);
			JsonObject componentsRoot = readObject(activeRoot, MobConfigManager.FIELD_MOB_COMPONENTS);
			double charge = readDouble(componentsRoot, MobConfigManager.FIELD_CHARGE_INTERVAL, fallback);
			return Math.max(0, (int) Math.round(charge));
		}

		private static int resolveTridentGroundClearTicks(Drowned drowned) {
			if (drowned == null || drowned.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return 300;
			}
			return 300;
		}

		private static void clearRangedDrownedRuntimeState(Drowned drowned) {
			if (drowned == null) {
				return;
			}
			UUID drownedId = drowned.getUUID();
			PENDING_RANGED_TRIDENT_CHARGES.remove(drownedId);
			RANGED_TRIDENT_COOLDOWNS.remove(drownedId);
		}

		private static void ensureRangedDrownedTridentEquipped(Drowned drowned) {
			if (drowned == null || !isRangedDrownedVariant(drowned)) {
				return;
			}
			if (!drowned.getMainHandItem().is(Items.TRIDENT)) {
				drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
			}
		}

		private static Vec3 resolveMissVector(double velocityX, double velocityY, double velocityZ, double attackAccuracy, LivingEntity shooter) {
			Vec3 desired = new Vec3(velocityX, velocityY, velocityZ);
			if (desired.lengthSqr() <= 1.0E-6D) {
				return desired;
			}
			Vec3 normalized = desired.normalize();
			Vec3 lateral = normalized.cross(new Vec3(0.0D, 1.0D, 0.0D));
			if (lateral.lengthSqr() <= 1.0E-6D) {
				lateral = normalized.cross(new Vec3(1.0D, 0.0D, 0.0D));
			}
			lateral = lateral.normalize();
			double missFactor = 1.0D - Mth.clamp(attackAccuracy, 0.0D, 1.0D);
			double sideSign = shooter.getRandom().nextBoolean() ? -1.0D : 1.0D;
			double lateralStrength = Mth.lerp(missFactor, 1.4D, 2.4D);
			double verticalStrength = Mth.lerp(missFactor, 0.25D, 0.9D) * (shooter.getRandom().nextBoolean() ? -1.0D : 1.0D);
			Vec3 backwardBias = normalized.scale(-0.35D);
			Vec3 miss = lateral.scale(sideSign * lateralStrength).add(0.0D, verticalStrength, 0.0D).add(backwardBias);
			return miss.lengthSqr() <= 1.0E-6D ? lateral : miss.normalize();
		}

		private static double resolveScaledRangedDamage(Drowned drowned, double base, Difficulty difficulty, boolean hardcore) {
			double regional = MobRegionalDifficultyManager.resolveMobRangedDamageScaling(drowned, base);
			return MobEntityManager.resolveWorldDifficultyValueForRuntime(drowned, MobConfigManager.FIELD_RANGED_DAMAGE, regional);
		}

		private static double resolveScaledAttackAccuracy(double base, Difficulty difficulty, boolean hardcore) {
			return Mth.clamp(resolveDifficultyAdjustedValue(difficulty, hardcore, Mth.clamp(base, 0.0D, 1.0D), 0.05D, 0.0D), 0.0D, 1.0D);
		}

		private static double resolveDifficultyAdjustedValue(Difficulty difficulty, boolean hardcore, double baseValue, double step, double minimum) {
			return roundDifficultyScaleValue(Math.max(minimum, baseValue + (step * resolveDifficultyTier(difficulty, hardcore))));
		}

		private static double roundDifficultyScaleValue(double value) {
			if (!Double.isFinite(value)) {
				return value;
			}
			double step = isWholeNumber(value) ? 0.05D : 0.005D;
			return Math.round(value / step) * step;
		}

		private static boolean isWholeNumber(double value) {
			return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
		}

		private static int resolveDifficultyTier(Difficulty difficulty, boolean hardcore) {
			Difficulty resolved = difficulty == null ? Difficulty.NORMAL : difficulty;
			return switch (resolved) {
				case PEACEFUL -> -2;
				case EASY -> -1;
				case NORMAL -> 0;
				case HARD -> hardcore ? 2 : 1;
			};
		}

		private static boolean isHardcoreWorld(net.minecraft.world.level.Level level) {
			return level != null && level.getServer() != null && level.getServer().isHardcore();
		}

		private static void stripHeldAttackDamageModifiers(Drowned drowned, EquipmentSlot slot) {
			if (drowned == null || slot == null) {
				return;
			}
			ItemStack stack = drowned.getItemBySlot(slot);
			if (stack == null || stack.isEmpty()) {
				return;
			}
			ItemStack normalized = stack.copy();
			normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
			drowned.setItemSlot(slot, normalized);
		}

		private static void copyIfMissing(JsonObject target, JsonObject source, String key) {
			if (target == null || source == null || key == null || key.isBlank()) {
				return;
			}
			if (!target.has(key) && source.has(key)) {
				target.add(key, source.get(key).deepCopy());
			}
		}
		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			return element.getAsBoolean();
		}

		private static double readDouble(JsonObject root, String key, double fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
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

		private static JsonObject readObject(JsonObject root, String key) {
			if (root == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			JsonElement element = EntityConfigManager.resolveConfiguredElement(root, key);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		}

		private static String readString(JsonObject root, String key, String fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				return fallback;
			}
			return element.getAsString();
		}

		private static String fileKeyForType(net.minecraft.world.entity.EntityType<?> type) {
			return type == madoku.craft.mobs.mob.MadokuMobEntityTypes.DROWNED ? MobConfigManager.FILE_DROWNED : "";
		}

		private static String resolveDefaultMobEquipmentReference(net.minecraft.world.entity.EntityType<?> type) {
			return type == madoku.craft.mobs.mob.MadokuMobEntityTypes.DROWNED ? "minecraft-equipment-drowned.json" : "";
		}

		private record PendingRangedTridentCharge(UUID targetUuid, int remainingTicks) {
			private PendingRangedTridentCharge withRemainingTicks(int remainingTicks) {
				return new PendingRangedTridentCharge(targetUuid, remainingTicks);
			}
		}

		private record EquipmentLoadoutResult(
			boolean applied,
			String reason,
			String equipmentReference,
			double chancePercent,
			String armorSet,
			int equippedPieces,
			int requiredPieces
		) {
		}

	}

	public static final class HuskBehavior {

		private HuskBehavior() {
		}

		public static void applySpawnOverrides(
			Husk husk,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason
		) {
			if (husk == null || world == null || difficulty == null) {
				return;
			}
			String fileKey = fileKeyForType(husk.getType());
			if (fileKey.isBlank()) {
				return;
			}
			if (!MobEntityManager.isEnabled()) {
				applySpawnEquipmentLoadoutWhenMobSystemDisabled(husk, world.getRandom());
				return;
			}
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);

			JsonObject variant = MobEntityManager.resolveConfiguredEntityVariantForRuntime(husk);
			if (variant.entrySet().isEmpty()) {
				return;
			}
			applyWeaponDamagePolicy(husk, variant);
			applyHuskBehaviorToggles(husk, fileConfigRoot, variant);
			if (overrideStats) {
				MobEntityManager.applyUniversalBaseStatsForRuntime(husk, variant);
			}
		}

		public static boolean shouldOverrideSpawnRules(Husk husk) {
			if (husk == null || husk.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.HUSK) {
				return false;
			}
			if (!MobEntityManager.isEnabled() || !MobEntityManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_HUSK)) {
				return false;
			}
			JsonObject huskFileRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_HUSK);
			return readBoolean(huskFileRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			if (!(entity instanceof Husk husk) || entity.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(husk.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject variant = MobEntityManager.resolveConfiguredEntityVariantForRuntime(husk);

			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			boolean modified = overrideStats && MobEntityManager.applyUniversalBaseStatsForRuntime(husk, variant);
			applyWeaponDamagePolicy(husk, variant);
			applyHuskBehaviorToggles(husk, fileConfigRoot, variant);
			return modified;
		}

		static boolean isCustomMobDropsEnabled(LivingEntity entity) {
			if (!(entity instanceof Husk husk) || entity.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(husk.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject resolved = resolveActiveHuskRoot(husk);
			boolean enabled = readBoolean(
				resolved,
				MobConfigManager.FIELD_CUSTOM_MOB_DROPS,
				true
			);
			return enabled;
		}

		static String resolveMobDropsConfigReference(LivingEntity entity) {
			if (!(entity instanceof Husk husk) || !MobEntityManager.isEnabled()) {
				return "";
			}
			String fileKey = fileKeyForType(husk.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return "";
			}
			JsonObject resolved = resolveActiveHuskRoot(husk);
			JsonObject componentsRoot = readObject(resolved, MobConfigManager.FIELD_MOB_COMPONENTS);
			String reference = readString(componentsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
			return reference;
		}

		private static String readString(JsonObject root, String key, String fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				return fallback;
			}
			return element.getAsString();
		}

		private static JsonObject resolveActiveHuskRoot(Husk husk) {
			if (husk == null) {
				return new JsonObject();
			}
			return MobEntityManager.resolveConfiguredEntityVariantForRuntime(husk);
		}

		private static EquipmentLoadoutResult applySpawnEquipmentLoadoutWhenMobSystemDisabled(Husk husk, RandomSource random) {
			if (husk == null || random == null) {
				return new EquipmentLoadoutResult(false, "invalid_inputs", "", 0.0D, "none", 0, 0);
			}
			if (!EquipmentConfigManager.isCustomEntityEquipmentEnabled()) {
				return new EquipmentLoadoutResult(false, "custom_entity_equipment_disabled", "", 0.0D, "none", 0, 0);
			}
			String equipmentReference = resolveDefaultMobEquipmentReference();
			if (equipmentReference.isBlank()) {
				return new EquipmentLoadoutResult(false, "default_reference_blank", equipmentReference, 0.0D, "none", 0, 0);
			}
			return applyEquipmentSetLoadout(
				husk,
				equipmentReference,
				EquipmentConfigManager.customEntityEquipmentChanceWhenMobSystemDisabled(),
				random
			);
		}

		private static EquipmentLoadoutResult applyEquipmentSetLoadout(Husk husk, String equipmentReference, double chancePercent, RandomSource random) {
			if (husk == null || random == null) {
				return new EquipmentLoadoutResult(false, "invalid_inputs", equipmentReference, chancePercent, "none", 0, 0);
			}
			if (chancePercent <= 0.0D || random.nextDouble() * 100.0D >= chancePercent) {
				return new EquipmentLoadoutResult(false, "chance_failed", equipmentReference, chancePercent, "none", 0, 0);
			}
			EquipmentConfigManager.EquipmentProfile profile = EquipmentConfigManager.resolveProfile(equipmentReference, husk.getType());
			if (profile == null || !profile.enabled()) {
				return new EquipmentLoadoutResult(false, "profile_missing_or_disabled", equipmentReference, chancePercent, "none", 0, 0);
			}

			Map<EquipmentSlot, ItemStack> rolledBySlot = EquipmentConfigManager.rollEquipment(profile, random);
			if (rolledBySlot.isEmpty()) {
				return new EquipmentLoadoutResult(
					false,
					"slot_pool_empty",
					equipmentReference,
					chancePercent,
					"api",
					0,
					0
				);
			}
			MobEntityManager.clearArmorSlotsForRuntime(husk);
			for (Map.Entry<EquipmentSlot, ItemStack> entry : rolledBySlot.entrySet()) {
				husk.setItemSlot(entry.getKey(), entry.getValue());
			}
			return new EquipmentLoadoutResult(
				true,
				"applied",
				equipmentReference,
				chancePercent,
				"api",
				rolledBySlot.size(),
				rolledBySlot.size()
			);
		}

		private static void applyHuskBehaviorToggles(Husk husk, JsonObject fileRoot, JsonObject variantRoot) {
			if (husk == null) {
				return;
			}
			JsonObject behaviorRoot = MobEntityManager.readMobBehaviorRootForRuntime(variantRoot);

			boolean overrideBehavior = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIORS, true);

			if (overrideBehavior) {
				husk.setCanPickUpLoot(MobEntityManager.readMobBehaviorBooleanForRuntime(variantRoot, MobConfigManager.FIELD_CAN_PICK_UP_LOOT, true));
			}
			if (overrideBehavior) {
				boolean callsReinforcements = readBoolean(behaviorRoot, MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, true);
				if (!callsReinforcements) {
					disableHuskReinforcementsForRuntime(husk);
				}
			}
		}

		private static void disableHuskReinforcementsForRuntime(Husk husk) {
			MobEntityManager.disableZombieReinforcementsForRuntime(husk);
		}

		private static void applyWeaponDamagePolicy(Husk husk, JsonObject resolvedRoot) {
			if (husk == null || resolvedRoot == null) {
				return;
			}
			boolean weaponDamageEnabled = readBoolean(resolvedRoot, MobConfigManager.FIELD_WEAPON_DAMAGE, true);
			if (weaponDamageEnabled) {
				return;
			}
			stripHeldAttackDamageModifiers(husk, EquipmentSlot.MAINHAND);
			stripHeldAttackDamageModifiers(husk, EquipmentSlot.OFFHAND);
		}

		public static boolean applyHungerAttackEffect(Husk husk, LivingEntity target, MobEffectInstance effect, Entity attacker) {
			if (husk == null || target == null || effect == null || attacker == null) {
				return false;
			}
			if (!MobEntityManager.isEnabled() || !MobEntityManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_HUSK)) {
				return target.addEffect(effect, attacker);
			}
			MobEffectInstance configuredEffect = MobEntityManager.resolveHuskAttackEffect(husk, effect);
			return target.addEffect(configuredEffect, attacker);
		}

		private static void stripHeldAttackDamageModifiers(Husk husk, EquipmentSlot slot) {
			if (husk == null || slot == null) {
				return;
			}
			ItemStack stack = husk.getItemBySlot(slot);
			if (stack == null || stack.isEmpty()) {
				return;
			}
			ItemStack normalized = stack.copy();
			normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
			husk.setItemSlot(slot, normalized);
		}

		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			return element.getAsBoolean();
		}

		private static JsonObject readObject(JsonObject root, String key) {
			if (root == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			JsonElement element = EntityConfigManager.resolveConfiguredElement(root, key);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		}

		private static String fileKeyForType(EntityType<?> type) {
			return type == madoku.craft.mobs.mob.MadokuMobEntityTypes.HUSK ? MobConfigManager.FILE_HUSK : "";
		}

		private static String resolveDefaultMobEquipmentReference() {
			return "minecraft-equipment-husk.json";
		}

		private record EquipmentLoadoutResult(
			boolean applied,
			String reason,
			String equipmentReference,
			double chancePercent,
			String armorSet,
			int equippedPieces,
			int requiredPieces
		) {}

	}

	public static final class ParchedBehavior {
		private static final int DEFAULT_ATTACK_INTERVAL_TICKS = 20;
		private static final int DEFAULT_CHARGE_UP_TICKS = 10;
		private static final String PARCHED_VARIANT_TAG_PREFIX = "madoku-craft.parched.variant:";

		private static final Map<UUID, PendingRangedBowCharge> PENDING_RANGED_BOW_CHARGES = new ConcurrentHashMap<>();
		private static final Map<UUID, Integer> RANGED_BOW_COOLDOWNS = new ConcurrentHashMap<>();

		private ParchedBehavior() {
		}

		public static void applySpawnOverrides(
			AbstractSkeleton skeleton,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason
		) {
			if (skeleton == null || world == null || difficulty == null || !MobEntityManager.isEnabled()) {
				return;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveMobFileSectionForRuntime(fileKey);
			JsonObject variantGroup = resolveVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, world, true);
			if (variantGroup.entrySet().isEmpty()) {
				return;
			}
			JsonObject resolvedRoot = mergeFileSettings(fileConfigRoot, variantGroup);

			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			applyWeaponDamagePolicy(skeleton, resolvedRoot);
			applyBehaviorToggles(skeleton, fileConfigRoot, resolvedRoot);
			if (overrideStats) {
				MobEntityManager.applyUniversalBaseStatsForRuntime(skeleton, resolvedRoot);
			}
			if (isBowAttackEnabled(skeleton)) {
				ensureBowEquipped(skeleton);
			}
		}

		public static boolean shouldOverrideSpawnRules(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			return readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			if (!(entity instanceof AbstractSkeleton skeleton) || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject resolvedRoot = resolveRuntimeRoot(skeleton);
			if (resolvedRoot.entrySet().isEmpty()) {
				return false;
			}

			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			boolean modified = overrideStats && MobEntityManager.applyUniversalBaseStatsForRuntime(skeleton, resolvedRoot);
			applyWeaponDamagePolicy(skeleton, resolvedRoot);
			applyBehaviorToggles(skeleton, fileConfigRoot, resolvedRoot);
			if (isBowAttackEnabled(skeleton)) {
				ensureBowEquipped(skeleton);
			}
			return modified;
		}

		public static JsonObject resolveRuntimeRoot(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return new JsonObject();
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return new JsonObject();
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveMobFileSectionForRuntime(fileKey);
			JsonObject variantGroup = resolveVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, null, false);
			if (variantGroup.entrySet().isEmpty()) {
				return new JsonObject();
			}
			return mergeFileSettings(fileConfigRoot, variantGroup);
		}

		public static boolean isBowAttackEnabled(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			return !root.entrySet().isEmpty() && MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false);
		}

		public static void ensureBowEquipped(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			ItemStack main = skeleton.getItemBySlot(EquipmentSlot.MAINHAND);
			if (main.isEmpty() || !main.is(Items.BOW)) {
				skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			}
		}

		public static boolean applyRangedSkeletonBowAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
			if (skeleton == null || target == null || !target.isAlive() || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			if (!isBowAttackEnabled(skeleton)) {
				return false;
			}

			UUID skeletonId = skeleton.getUUID();
			int cooldown = RANGED_BOW_COOLDOWNS.getOrDefault(skeletonId, 0);
			if (cooldown > 0) {
				return true;
			}
			PendingRangedBowCharge pending = PENDING_RANGED_BOW_CHARGES.get(skeletonId);
			if (pending != null) {
				return true;
			}

			int chargeUpTicks = resolveBowChargeUpTicks(skeleton);
			if (chargeUpTicks <= 0) {
				if (fireRangedBowArrow(skeleton, target)) {
					RANGED_BOW_COOLDOWNS.put(skeletonId, resolveBowAttackIntervalTicks(skeleton));
				}
				return true;
			}

			PENDING_RANGED_BOW_CHARGES.put(skeletonId, new PendingRangedBowCharge(target.getUUID(), chargeUpTicks));
			return true;
		}

		public static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton) {
			if (!isBowAttackEnabled(skeleton)) {
				return -1;
			}
			return resolveBowAttackIntervalTicks(skeleton, DEFAULT_ATTACK_INTERVAL_TICKS);
		}

		public static int resolveBowChargeUpTicks(Monster attacker) {
			if (!(attacker instanceof AbstractSkeleton skeleton) || !isBowAttackEnabled(skeleton)) {
				return -1;
			}
			return resolveBowChargeUpTicks(skeleton, DEFAULT_CHARGE_UP_TICKS);
		}

		public static void tickRangedSkeletonRuntime(AbstractSkeleton skeleton) {
			if (skeleton == null || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return;
			}
			if (!isBowAttackEnabled(skeleton)) {
				clearRangedSkeletonRuntimeState(skeleton);
				return;
			}

			UUID skeletonId = skeleton.getUUID();
			Integer cooldown = RANGED_BOW_COOLDOWNS.get(skeletonId);
			if (cooldown != null) {
				if (cooldown <= 1) {
					RANGED_BOW_COOLDOWNS.remove(skeletonId);
				} else {
					RANGED_BOW_COOLDOWNS.put(skeletonId, cooldown - 1);
				}
			}

			PendingRangedBowCharge pending = PENDING_RANGED_BOW_CHARGES.get(skeletonId);
			if (pending == null) {
				return;
			}
			if (pending.remainingTicks() > 1) {
				PENDING_RANGED_BOW_CHARGES.put(skeletonId, pending.withRemainingTicks(pending.remainingTicks() - 1));
				return;
			}

			PENDING_RANGED_BOW_CHARGES.remove(skeletonId);
			if (RANGED_BOW_COOLDOWNS.containsKey(skeletonId)) {
				return;
			}
			if (!(skeleton.level() instanceof ServerLevel level)) {
				return;
			}
			Entity targetEntity = level.getEntity(pending.targetUuid());
			if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
				return;
			}
			if (fireRangedBowArrow(skeleton, target)) {
				RANGED_BOW_COOLDOWNS.put(skeletonId, resolveBowAttackIntervalTicks(skeleton));
			}
		}

		public static void onEntityCleanup(Entity entity) {
			if (entity instanceof AbstractSkeleton skeleton) {
				clearRangedSkeletonRuntimeState(skeleton);
			}
		}

		public static void resetRuntimeState() {
			PENDING_RANGED_BOW_CHARGES.clear();
			RANGED_BOW_COOLDOWNS.clear();
		}

		private static JsonObject resolveVariantGroupRoot(
			AbstractSkeleton skeleton,
			JsonObject fileConfigRoot,
			JsonObject fileRoot,
			ServerLevelAccessor world,
			boolean spawnContext
		) {
			JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(fileConfigRoot);
			if (defaultGroup.entrySet().isEmpty()) {
				clearVariantTag(skeleton);
				return new JsonObject();
			}

			String storedVariant = readStoredVariantKey(skeleton);
			if (!storedVariant.isBlank()) {
				JsonObject known = resolveVariantRootByKey(fileConfigRoot, storedVariant);
				if (!known.entrySet().isEmpty()) {
					return MobEntityManager.resolveVariantGroupRoot(defaultGroup, known);
				}
			}

			boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
			if (!spawnContext || !overrideSpawnRules || world == null) {
				return defaultGroup;
			}

			String selectedVariant = selectVariantKey(fileConfigRoot, world);
			if (selectedVariant.isBlank()) {
				return defaultGroup;
			}
			writeVariantTag(skeleton, selectedVariant);
			JsonObject selected = resolveVariantRootByKey(fileConfigRoot, selectedVariant);
			return selected.entrySet().isEmpty() ? defaultGroup : MobEntityManager.resolveVariantGroupRoot(defaultGroup, selected);
		}

		private static String selectVariantKey(JsonObject fileRoot, ServerLevelAccessor world) {
			return MobEntityManager.selectWeightedVariantKey(
				fileRoot,
				world == null ? null : world.getRandom(),
				ParchedBehavior::isReservedParchedGroupKey,
				variantRoot -> MobEntityManager.resolveVariantSpawnWeight(variantRoot, 0.0D)
			);
		}

		private static JsonObject resolveVariantRootByKey(JsonObject fileRoot, String variantKey) {
			return MobEntityManager.resolveVariantRootByKey(
				fileRoot,
				variantKey,
				ParchedBehavior::isReservedParchedGroupKey
			);
		}

		private static boolean isReservedParchedGroupKey(String normalizedKey) {
			if (normalizedKey == null || normalizedKey.isBlank()) {
				return true;
			}
			return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_ENABLED))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_COMPONENTS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_BEHAVIORS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_GOALS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WEAPON_DAMAGE))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW));
		}

		private static String readStoredVariantKey(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return "";
			}
			for (String tag : skeleton.entityTags()) {
				if (tag == null || !tag.startsWith(PARCHED_VARIANT_TAG_PREFIX)) {
					continue;
				}
				String normalized = normalizeKey(tag.substring(PARCHED_VARIANT_TAG_PREFIX.length()));
				if (!normalized.isBlank()) {
					return normalized;
				}
			}
			return "";
		}

		private static void writeVariantTag(AbstractSkeleton skeleton, String variantKey) {
			if (skeleton == null || variantKey == null || variantKey.isBlank()) {
				return;
			}
			clearVariantTag(skeleton);
			skeleton.addTag(PARCHED_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
		}

		private static void clearVariantTag(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			String existing = null;
			for (String tag : skeleton.entityTags()) {
				if (tag != null && tag.startsWith(PARCHED_VARIANT_TAG_PREFIX)) {
					existing = tag;
					break;
				}
			}
			if (existing != null) {
				skeleton.removeTag(existing);
			}
		}

		private static void applyBehaviorToggles(AbstractSkeleton skeleton, JsonObject fileRoot, JsonObject variantRoot) {
			if (skeleton == null) {
				return;
			}
			boolean overrideBehavior = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIORS, true);
			if (overrideBehavior) {
				skeleton.setCanPickUpLoot(MobEntityManager.readMobBehaviorBooleanForRuntime(variantRoot, MobConfigManager.FIELD_CAN_PICK_UP_LOOT, true));
			}
		}

		private static void applyWeaponDamagePolicy(AbstractSkeleton skeleton, JsonObject resolvedRoot) {
			if (skeleton == null || resolvedRoot == null) {
				return;
			}
			boolean weaponDamageEnabled = readBoolean(resolvedRoot, MobConfigManager.FIELD_WEAPON_DAMAGE, true);
			if (weaponDamageEnabled) {
				return;
			}
			stripHeldAttackDamageModifiers(skeleton, EquipmentSlot.MAINHAND);
			stripHeldAttackDamageModifiers(skeleton, EquipmentSlot.OFFHAND);
		}

		private static boolean fireRangedBowArrow(AbstractSkeleton skeleton, LivingEntity target) {
			if (skeleton == null || target == null || !target.isAlive() || !(skeleton.level() instanceof ServerLevel level)) {
				return false;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty()) {
				return false;
			}

			ensureBowEquipped(skeleton);
			InteractionHand bowHand = resolveBowHand(skeleton);
			if (bowHand == null) {
				return false;
			}
			ItemStack bowStack = skeleton.getItemInHand(bowHand);
			ItemStack projectileStack = skeleton.getProjectile(bowStack);
			if (projectileStack.isEmpty()) {
				projectileStack = new ItemStack(Items.ARROW);
			}
			AbstractArrow arrow = ((AbstractSkeletonArrowInvoker) skeleton).madokuCraft$invokeGetArrow(projectileStack, 1.0F, bowStack);
			if (arrow == null) {
				return false;
			}

			double accuracy = resolveScaledAttackAccuracy(
				readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_ATTACK_ACCURACY, 0.7D),
				skeleton.level().getDifficulty(),
				isHardcoreWorld(skeleton.level())
			);
			double rangedDamage = resolveSkeletonRangedDamage(skeleton, root);
			accuracy = MobRegionalDifficultyManager.resolveMobAttackAccuracyScaling(skeleton, accuracy);
			ShotVector shot = resolveShotVector(skeleton, arrow, target, accuracy);
			arrow.shoot(shot.vector.x, shot.vector.y, shot.vector.z, 1.6F, 0.0F);
			arrow.setCritArrow(false);
			MobEntityManager.setProjectileDamageOverride(arrow, (float) Math.max(0.0D, rangedDamage));
			MobEntityManager.trackManagedMobArrowForRuntime(arrow, level.getServer());
			if (shot.guaranteedHit) {
				MobEntityManager.startProjectileHoming(arrow, target, level.getServer());
			}
			skeleton.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
			level.addFreshEntity(arrow);
			return true;
		}

		private static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton, int fallback) {
			if (skeleton == null || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return fallback;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty() || !MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false)) {
				return fallback;
			}
			double interval = readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_ATTACK_INTERVAL, fallback);
			return Math.max(1, (int) Math.round(interval));
		}

		private static int resolveBowChargeUpTicks(AbstractSkeleton skeleton, int fallback) {
			if (skeleton == null || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return fallback;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty() || !MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false)) {
				return fallback;
			}
			double charge = readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_CHARGE_INTERVAL, fallback);
			return Math.max(0, (int) Math.round(charge));
		}

		private static void clearRangedSkeletonRuntimeState(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			UUID skeletonId = skeleton.getUUID();
			PENDING_RANGED_BOW_CHARGES.remove(skeletonId);
			RANGED_BOW_COOLDOWNS.remove(skeletonId);
		}

		private static InteractionHand resolveBowHand(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return null;
			}
			if (skeleton.getMainHandItem().is(Items.BOW)) {
				return InteractionHand.MAIN_HAND;
			}
			if (skeleton.getOffhandItem().is(Items.BOW)) {
				return InteractionHand.OFF_HAND;
			}
			return null;
		}

		private static JsonObject mergeFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
			JsonObject merged = variantRoot == null ? new JsonObject() : variantRoot.deepCopy();
			if (fileRoot == null || fileRoot.entrySet().isEmpty()) {
				return merged;
			}
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_CUSTOM_MOB_DROPS);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WEAPON_DAMAGE);
			return merged;
		}

		private static String fileKeyForType(AbstractSkeleton skeleton) {
			if (skeleton == null || skeleton.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED) {
				return "";
			}
			return MobConfigManager.FILE_PARCHED;
		}

		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			return element.getAsBoolean();
		}

		private static double readDouble(JsonObject root, String key, double fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
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

		private static JsonObject readObject(JsonObject root, String key) {
			if (root == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			JsonElement element = EntityConfigManager.resolveConfiguredElement(root, key);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		}

		private static void copyIfMissing(JsonObject target, JsonObject source, String key) {
			if (target == null || source == null || key == null || key.isBlank()) {
				return;
			}
			if (!target.has(key) && source.has(key)) {
				target.add(key, source.get(key).deepCopy());
			}
		}

		private static String normalizeKey(String value) {
			return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		}

		private static boolean isHardcoreWorld(Level level) {
			return level != null && level.getServer() != null && level.getServer().isHardcore();
		}

		private static double resolveSkeletonRangedDamage(AbstractSkeleton skeleton, JsonObject root) {
			if (skeleton == null) {
				return 0.0D;
			}
			double rangedDamage = resolveScaledRangedDamage(skeleton,
				readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_RANGED_DAMAGE, 4.0D),
				skeleton.level().getDifficulty(),
				isHardcoreWorld(skeleton.level())
			);
			return rangedDamage;
		}

		private static double resolveScaledRangedDamage(AbstractSkeleton skeleton, double base, Difficulty difficulty, boolean hardcore) {
			double regional = MobRegionalDifficultyManager.resolveMobRangedDamageScaling(skeleton, base);
			return MobEntityManager.resolveWorldDifficultyValueForRuntime(skeleton, MobConfigManager.FIELD_RANGED_DAMAGE, regional);
		}

		private static double resolveScaledAttackAccuracy(double base, Difficulty difficulty, boolean hardcore) {
			return Mth.clamp(resolveDifficultyAdjustedValue(difficulty, hardcore, Mth.clamp(base, 0.0D, 1.0D), 0.05D, 0.0D), 0.0D, 1.0D);
		}

		private static double resolveDifficultyAdjustedValue(Difficulty difficulty, boolean hardcore, double baseValue, double step, double minimum) {
			return roundDifficultyScaleValue(Math.max(minimum, baseValue + (step * resolveDifficultyTier(difficulty, hardcore))));
		}

		private static double roundDifficultyScaleValue(double value) {
			if (!Double.isFinite(value)) {
				return value;
			}
			double step = isWholeNumber(value) ? 0.05D : 0.005D;
			return Math.round(value / step) * step;
		}

		private static boolean isWholeNumber(double value) {
			return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
		}

		private static int resolveDifficultyTier(Difficulty difficulty, boolean hardcore) {
			Difficulty resolved = difficulty == null ? Difficulty.NORMAL : difficulty;
			return switch (resolved) {
				case PEACEFUL -> -2;
				case EASY -> -1;
				case NORMAL -> 0;
				case HARD -> hardcore ? 2 : 1;
			};
		}

		private static ShotVector resolveShotVector(AbstractSkeleton skeleton, AbstractArrow arrow, LivingEntity target, double accuracy) {
			double dx = target.getX() - skeleton.getX();
			double dz = target.getZ() - skeleton.getZ();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			double dy = target.getY(1.0D / 3.0D) - arrow.getY() + (horizontal * 0.2D);
			Vec3 desired = new Vec3(dx, dy, dz);
			if (desired.lengthSqr() <= 1.0E-6D) {
				return new ShotVector(desired, true);
			}
			double clampedAccuracy = Mth.clamp(accuracy, 0.0D, 1.0D);
			if (skeleton.getRandom().nextDouble() <= clampedAccuracy) {
				return new ShotVector(desired, true);
			}
			return new ShotVector(resolveMissVector(desired.x, desired.y, desired.z, clampedAccuracy, skeleton), false);
		}

		private static Vec3 resolveMissVector(double velocityX, double velocityY, double velocityZ, double attackAccuracy, LivingEntity shooter) {
			Vec3 desired = new Vec3(velocityX, velocityY, velocityZ);
			if (desired.lengthSqr() <= 1.0E-6D) {
				return desired;
			}
			Vec3 normalized = desired.normalize();
			Vec3 lateral = normalized.cross(new Vec3(0.0D, 1.0D, 0.0D));
			if (lateral.lengthSqr() <= 1.0E-6D) {
				lateral = normalized.cross(new Vec3(1.0D, 0.0D, 0.0D));
			}
			if (lateral.lengthSqr() > 1.0E-6D) {
				lateral = lateral.normalize();
			}
			double missFactor = 1.0D - Mth.clamp(attackAccuracy, 0.0D, 1.0D);
			double sideSign = shooter.getRandom().nextBoolean() ? -1.0D : 1.0D;
			double lateralStrength = Mth.lerp(missFactor, 1.4D, 2.4D);
			double verticalStrength = Mth.lerp(missFactor, 0.25D, 0.9D) * (shooter.getRandom().nextBoolean() ? -1.0D : 1.0D);
			Vec3 backwardBias = normalized.scale(-0.35D);
			Vec3 miss = lateral.scale(sideSign * lateralStrength).add(0.0D, verticalStrength, 0.0D).add(backwardBias);
			return miss.lengthSqr() <= 1.0E-6D ? lateral : miss.normalize();
		}

		private static void stripHeldAttackDamageModifiers(AbstractSkeleton skeleton, EquipmentSlot slot) {
			if (skeleton == null || slot == null) {
				return;
			}
			ItemStack stack = skeleton.getItemBySlot(slot);
			if (stack == null || stack.isEmpty()) {
				return;
			}
			ItemStack normalized = stack.copy();
			normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
			skeleton.setItemSlot(slot, normalized);
		}

		private static JsonObject readMobComponentsRoot(JsonObject root) {
			return readObject(root, MobConfigManager.FIELD_MOB_COMPONENTS);
		}

		private record PendingRangedBowCharge(UUID targetUuid, int remainingTicks) {
			private PendingRangedBowCharge withRemainingTicks(int remainingTicks) {
				return new PendingRangedBowCharge(targetUuid, remainingTicks);
			}
		}

		private record ShotVector(Vec3 vector, boolean guaranteedHit) {
		}

	}

	public static final class SkeletonBehavior {
		private static final int DEFAULT_ATTACK_INTERVAL_TICKS = 20;
		private static final int DEFAULT_CHARGE_UP_TICKS = 10;
		private static final String SKELETON_VARIANT_TAG_PREFIX = "madoku-craft.skeleton.variant:";

		private static final Map<UUID, PendingRangedBowCharge> PENDING_RANGED_BOW_CHARGES = new ConcurrentHashMap<>();
		private static final Map<UUID, Integer> RANGED_BOW_COOLDOWNS = new ConcurrentHashMap<>();

		private SkeletonBehavior() {
		}

		public static void applySpawnOverrides(
			AbstractSkeleton skeleton,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason
		) {
			if (skeleton == null || world == null || difficulty == null || !MobEntityManager.isEnabled()) {
				return;
			}
			String fileKey = skeletonFileKey(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = readSkeletonMobRoot(fileConfigRoot, fileKey);
			JsonObject variantGroup = resolveSkeletonVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, world, true);
			if (variantGroup.entrySet().isEmpty()) {
				return;
			}
			JsonObject resolvedRoot = mergeSkeletonFileSettings(fileConfigRoot, variantGroup);

			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			if (overrideStats) {
				MobEntityManager.applyUniversalBaseStatsForRuntime(skeleton, resolvedRoot);
			}
			boolean bowAttackEnabled = isBowAttackEnabled(skeleton);
			if (bowAttackEnabled) {
				MobEntityManager.ensureBowEquipped(skeleton);
			}
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			if (!(entity instanceof AbstractSkeleton skeleton) || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = skeletonFileKey(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}

			JsonObject resolvedRoot = resolveRuntimeRoot(skeleton);
			if (resolvedRoot.entrySet().isEmpty()) {
				return false;
			}
			return MobEntityManager.applyUniversalBaseStatsForRuntime(skeleton, resolvedRoot);
		}

		public static boolean shouldOverrideSpawnRules(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = skeletonFileKey(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			return readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		}

		public static JsonObject resolveRuntimeRoot(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return new JsonObject();
			}
			String fileKey = skeletonFileKey(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return new JsonObject();
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = readSkeletonMobRoot(fileConfigRoot, fileKey);
			JsonObject variantGroup = resolveSkeletonVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, null, false);
			if (variantGroup.entrySet().isEmpty()) {
				return new JsonObject();
			}
			return mergeSkeletonFileSettings(fileConfigRoot, variantGroup);
		}

		public static void resetRuntimeState() {
			PENDING_RANGED_BOW_CHARGES.clear();
			RANGED_BOW_COOLDOWNS.clear();
		}

		public static void onEntityCleanup(Entity entity) {
			if (entity instanceof AbstractSkeleton skeleton) {
				clearRangedSkeletonRuntimeState(skeleton);
			}
		}

		public static void tickRangedSkeletonRuntime(AbstractSkeleton skeleton) {
			if (skeleton == null || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return;
			}
			if (!isBowAttackEnabled(skeleton)) {
				clearRangedSkeletonRuntimeState(skeleton);
				return;
			}

			UUID skeletonId = skeleton.getUUID();
			Integer cooldown = RANGED_BOW_COOLDOWNS.get(skeletonId);
			if (cooldown != null) {
				if (cooldown <= 1) {
					RANGED_BOW_COOLDOWNS.remove(skeletonId);
				} else {
					RANGED_BOW_COOLDOWNS.put(skeletonId, cooldown - 1);
				}
			}

			PendingRangedBowCharge pending = PENDING_RANGED_BOW_CHARGES.get(skeletonId);
			if (pending == null) {
				return;
			}
			if (pending.remainingTicks() > 1) {
				PENDING_RANGED_BOW_CHARGES.put(skeletonId, pending.withRemainingTicks(pending.remainingTicks() - 1));
				return;
			}

			PENDING_RANGED_BOW_CHARGES.remove(skeletonId);
			if (RANGED_BOW_COOLDOWNS.containsKey(skeletonId)) {
				return;
			}
			if (!(skeleton.level() instanceof ServerLevel level)) {
				return;
			}
			Entity targetEntity = level.getEntity(pending.targetUuid());
			if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
				return;
			}
			if (fireRangedBowArrow(skeleton, target)) {
				RANGED_BOW_COOLDOWNS.put(skeletonId, resolveBowAttackIntervalTicks(skeleton));
			}
		}

		public static boolean applyRangedSkeletonBowAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
			if (skeleton == null || target == null || !target.isAlive() || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			if (!isBowAttackEnabled(skeleton)) {
				return false;
			}

			UUID skeletonId = skeleton.getUUID();
			int cooldown = RANGED_BOW_COOLDOWNS.getOrDefault(skeletonId, 0);
			if (cooldown > 0) {
				return true;
			}
			PendingRangedBowCharge pending = PENDING_RANGED_BOW_CHARGES.get(skeletonId);
			if (pending != null) {
				return true;
			}

			int chargeUpTicks = resolveBowChargeUpTicks(skeleton);
			if (chargeUpTicks <= 0) {
				if (fireRangedBowArrow(skeleton, target)) {
					RANGED_BOW_COOLDOWNS.put(skeletonId, resolveBowAttackIntervalTicks(skeleton));
				}
				return true;
			}

			PENDING_RANGED_BOW_CHARGES.put(skeletonId, new PendingRangedBowCharge(target.getUUID(), chargeUpTicks));
			return true;
		}

		public static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton) {
			if (!isBowAttackEnabled(skeleton)) {
				return -1;
			}
			return resolveBowAttackIntervalTicks(skeleton, DEFAULT_ATTACK_INTERVAL_TICKS);
		}

		public static int resolveBowChargeUpTicks(Monster attacker) {
			if (!(attacker instanceof AbstractSkeleton skeleton) || !isBowAttackEnabled(skeleton)) {
				return -1;
			}
			return resolveBowChargeUpTicks(attacker, DEFAULT_CHARGE_UP_TICKS);
		}

		public static boolean isBowAttackEnabled(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			JsonObject root = resolveBowRuntimeRoot(skeleton);
			return !root.entrySet().isEmpty() && MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false);
		}

		public static void ensureBowEquipped(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			ItemStack main = skeleton.getItemBySlot(EquipmentSlot.MAINHAND);
			if (main.isEmpty() || !main.is(Items.BOW)) {
				skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			}
		}

		public static boolean applyBowAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
			return applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
		}

		private static InteractionHand resolveBowHand(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return null;
			}
			if (skeleton.getMainHandItem().is(Items.BOW)) {
				return InteractionHand.MAIN_HAND;
			}
			if (skeleton.getOffhandItem().is(Items.BOW)) {
				return InteractionHand.OFF_HAND;
			}
			return null;
		}

		private static boolean fireRangedBowArrow(AbstractSkeleton skeleton, LivingEntity target) {
			if (skeleton == null || target == null || !target.isAlive() || !(skeleton.level() instanceof ServerLevel level)) {
				return false;
			}
			JsonObject root = resolveBowRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty()) {
				return false;
			}

			ensureBowEquipped(skeleton);
			InteractionHand bowHand = resolveBowHand(skeleton);
			if (bowHand == null) {
				return false;
			}
			ItemStack bowStack = skeleton.getItemInHand(bowHand);
			ItemStack projectileStack = skeleton.getProjectile(bowStack);
			if (projectileStack.isEmpty()) {
				projectileStack = new ItemStack(Items.ARROW);
			}
			AbstractArrow arrow = ((AbstractSkeletonArrowInvoker) skeleton).madokuCraft$invokeGetArrow(projectileStack, 1.0F, bowStack);
			if (arrow == null) {
				return false;
			}

			double accuracy = resolveScaledAttackAccuracy(
				readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_ATTACK_ACCURACY, 0.7D),
				skeleton.level().getDifficulty(),
				isHardcoreWorld(skeleton.level())
			);
			double rangedDamage = resolveSkeletonRangedDamage(skeleton, root);
			accuracy = MobRegionalDifficultyManager.resolveMobAttackAccuracyScaling(skeleton, accuracy);
			ShotVector shot = resolveShotVector(skeleton, arrow, target, accuracy);
			arrow.shoot(shot.vector.x, shot.vector.y, shot.vector.z, 1.6F, 0.0F);
			arrow.setCritArrow(false);
			if (skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON) {
				arrow.setRemainingFireTicks(0);
			}
			MobEntityManager.setProjectileDamageOverride(arrow, (float) Math.max(0.0D, rangedDamage));
			MobEntityManager.trackManagedMobArrowForRuntime(arrow, level.getServer());
			if (shot.guaranteedHit) {
				MobEntityManager.startProjectileHoming(arrow, target, level.getServer());
			}
			skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
			level.addFreshEntity(arrow);
			return true;
		}

		private static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton, int fallback) {
			if (skeleton == null || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return fallback;
			}
			JsonObject root = resolveBowRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty() || !MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false)) {
				return fallback;
			}
			double interval = readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_ATTACK_INTERVAL, fallback);
			return Math.max(1, (int) Math.round(interval));
		}

		private static int resolveBowChargeUpTicks(Monster attacker, int fallback) {
			if (!(attacker instanceof AbstractSkeleton skeleton) || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return fallback;
			}
			JsonObject root = resolveBowRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty() || !MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false)) {
				return fallback;
			}
			double charge = readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_CHARGE_INTERVAL, fallback);
			return Math.max(0, (int) Math.round(charge));
		}

		private static void clearRangedSkeletonRuntimeState(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			UUID skeletonId = skeleton.getUUID();
			PENDING_RANGED_BOW_CHARGES.remove(skeletonId);
			RANGED_BOW_COOLDOWNS.remove(skeletonId);
		}

		private static String skeletonFileKey(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return "";
			}
			return skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.SKELETON ? MobConfigManager.FILE_SKELETON
				: skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY ? MobConfigManager.FILE_STRAY
				: skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.BOGGED ? MobConfigManager.FILE_BOGGED
				: skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.PARCHED ? MobConfigManager.FILE_PARCHED
				: skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON ? MobConfigManager.FILE_WITHER_SKELETON
				: "";
		}

		private static JsonObject resolveBowRuntimeRoot(AbstractSkeleton skeleton) {
			return resolveRuntimeRoot(skeleton);
		}

		private static JsonObject readSkeletonMobRoot(JsonObject fileConfigRoot, String fileKey) {
			if (fileConfigRoot == null || fileKey == null || fileKey.isBlank()) {
				return new JsonObject();
			}
			return readObject(fileConfigRoot, fileKey);
		}

		private static JsonObject resolveSkeletonVariantGroupRoot(
			AbstractSkeleton skeleton,
			JsonObject fileConfigRoot,
			JsonObject fileRoot,
			ServerLevelAccessor world,
			boolean spawnContext
		) {
			JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(fileConfigRoot);
			if (defaultGroup.entrySet().isEmpty()) {
				clearSkeletonVariantTag(skeleton);
				return new JsonObject();
			}

			String storedVariant = readStoredSkeletonVariantKey(skeleton);
			if (!storedVariant.isBlank()) {
				JsonObject known = resolveSkeletonVariantRootByKey(fileConfigRoot, storedVariant);
				if (!known.entrySet().isEmpty()) {
					return MobEntityManager.resolveVariantGroupRoot(defaultGroup, known);
				}
			}

			boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
			if (!spawnContext || !overrideSpawnRules || world == null) {
				return defaultGroup;
			}

			String selectedVariant = selectSkeletonVariantKey(fileConfigRoot, world);
			if (selectedVariant.isBlank()) {
				return defaultGroup;
			}
			writeSkeletonVariantTag(skeleton, selectedVariant);
			JsonObject selected = resolveSkeletonVariantRootByKey(fileConfigRoot, selectedVariant);
			return selected.entrySet().isEmpty() ? defaultGroup : MobEntityManager.resolveVariantGroupRoot(defaultGroup, selected);
		}

		private static JsonObject resolveSkeletonVariantRootByKey(JsonObject fileRoot, String variantKey) {
			return MobEntityManager.resolveVariantRootByKey(
				fileRoot,
				variantKey,
				SkeletonBehavior::isReservedSkeletonGroupKey
			);
		}

		private static String selectSkeletonVariantKey(JsonObject fileRoot, ServerLevelAccessor world) {
			return MobEntityManager.selectWeightedVariantKey(
				fileRoot,
				world == null ? null : world.getRandom(),
				SkeletonBehavior::isReservedSkeletonGroupKey,
				variantRoot -> MobEntityManager.resolveVariantSpawnWeight(variantRoot, 0.0D)
			);
		}

		private static boolean isReservedSkeletonGroupKey(String normalizedKey) {
			if (normalizedKey == null || normalizedKey.isBlank()) {
				return true;
			}
			return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_ENABLED))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_COMPONENTS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_BEHAVIORS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_GOALS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WEAPON_DAMAGE))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW));
		}

		private static JsonObject mergeSkeletonFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
			JsonObject merged = variantRoot == null ? new JsonObject() : variantRoot.deepCopy();
			if (fileRoot == null || fileRoot.entrySet().isEmpty()) {
				return merged;
			}
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_CUSTOM_MOB_DROPS);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WEAPON_DAMAGE);
			return merged;
		}


		private static void copyIfMissing(JsonObject target, JsonObject source, String key) {
			if (target == null || source == null || key == null || key.isBlank()) {
				return;
			}
			if (!target.has(key) && source.has(key)) {
				target.add(key, source.get(key).deepCopy());
			}
		}

		private static String readStoredSkeletonVariantKey(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return "";
			}
			for (String tag : skeleton.entityTags()) {
				if (tag == null || !tag.startsWith(SKELETON_VARIANT_TAG_PREFIX)) {
					continue;
				}
				String normalized = normalizeKey(tag.substring(SKELETON_VARIANT_TAG_PREFIX.length()));
				if (!normalized.isBlank()) {
					return normalized;
				}
			}
			return "";
		}

		private static void writeSkeletonVariantTag(AbstractSkeleton skeleton, String variantKey) {
			if (skeleton == null || variantKey == null || variantKey.isBlank()) {
				return;
			}
			clearSkeletonVariantTag(skeleton);
			skeleton.addTag(SKELETON_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
		}

		private static void clearSkeletonVariantTag(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			String existing = null;
			for (String tag : skeleton.entityTags()) {
				if (tag != null && tag.startsWith(SKELETON_VARIANT_TAG_PREFIX)) {
					existing = tag;
					break;
				}
			}
			if (existing != null) {
				skeleton.removeTag(existing);
			}
		}

		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			if (!root.has(key) || !root.get(key).isJsonPrimitive() || !root.get(key).getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			return root.get(key).getAsBoolean();
		}

		private static String normalizeKey(String value) {
			return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		}

		private static JsonObject readMobComponentsRoot(JsonObject root) {
			return readObject(root, MobConfigManager.FIELD_MOB_COMPONENTS);
		}

		private static JsonObject readObject(JsonObject parent, String key) {
			if (parent == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
		}

		private static double readDouble(JsonObject root, String key, double fallback) {
			if (root == null) {
				return fallback;
			}
			if (!root.has(key) || !root.get(key).isJsonPrimitive() || !root.get(key).getAsJsonPrimitive().isNumber()) {
				return fallback;
			}
			return root.get(key).getAsDouble();
		}

		private static boolean isHardcoreWorld(Level level) {
			return level != null && level.getServer() != null && level.getServer().isHardcore();
		}

		private static double resolveSkeletonRangedDamage(AbstractSkeleton skeleton, JsonObject root) {
			if (skeleton == null) {
				return 0.0D;
			}
			double rangedDamage = resolveScaledRangedDamage(skeleton,
				readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_RANGED_DAMAGE, 4.0D),
				skeleton.level().getDifficulty(),
				isHardcoreWorld(skeleton.level())
			);
			return rangedDamage;
		}

		private static double resolveScaledRangedDamage(AbstractSkeleton skeleton, double base, Difficulty difficulty, boolean hardcore) {
			double regional = MobRegionalDifficultyManager.resolveMobRangedDamageScaling(skeleton, base);
			return MobEntityManager.resolveWorldDifficultyValueForRuntime(skeleton, MobConfigManager.FIELD_RANGED_DAMAGE, regional);
		}

		private static double resolveScaledAttackAccuracy(double base, Difficulty difficulty, boolean hardcore) {
			return Mth.clamp(resolveDifficultyAdjustedValue(difficulty, hardcore, Mth.clamp(base, 0.0D, 1.0D), 0.05D, 0.0D), 0.0D, 1.0D);
		}

		private static double resolveDifficultyAdjustedValue(Difficulty difficulty, boolean hardcore, double baseValue, double step, double minimum) {
			return roundDifficultyScaleValue(Math.max(minimum, baseValue + (step * resolveDifficultyTier(difficulty, hardcore))));
		}

		private static double roundDifficultyScaleValue(double value) {
			if (!Double.isFinite(value)) {
				return value;
			}
			double step = isWholeNumber(value) ? 0.05D : 0.005D;
			return Math.round(value / step) * step;
		}

		private static boolean isWholeNumber(double value) {
			return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
		}

		private static int resolveDifficultyTier(Difficulty difficulty, boolean hardcore) {
			Difficulty resolved = difficulty == null ? Difficulty.NORMAL : difficulty;
			return switch (resolved) {
				case PEACEFUL -> -2;
				case EASY -> -1;
				case NORMAL -> 0;
				case HARD -> hardcore ? 2 : 1;
			};
		}

		private static ShotVector resolveShotVector(AbstractSkeleton skeleton, AbstractArrow arrow, LivingEntity target, double accuracy) {
			double dx = target.getX() - skeleton.getX();
			double dz = target.getZ() - skeleton.getZ();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			double dy = target.getY(1.0D / 3.0D) - arrow.getY() + (horizontal * 0.2D);
			Vec3 desired = new Vec3(dx, dy, dz);
			if (desired.lengthSqr() <= 1.0E-6D) {
				return new ShotVector(desired, true);
			}
			double clampedAccuracy = Mth.clamp(accuracy, 0.0D, 1.0D);
			if (skeleton.getRandom().nextDouble() <= clampedAccuracy) {
				return new ShotVector(desired, true);
			}
			return new ShotVector(resolveMissVector(desired.x, desired.y, desired.z, clampedAccuracy, skeleton), false);
		}

		private static Vec3 resolveMissVector(double velocityX, double velocityY, double velocityZ, double attackAccuracy, LivingEntity shooter) {
			Vec3 desired = new Vec3(velocityX, velocityY, velocityZ);
			if (desired.lengthSqr() <= 1.0E-6D) {
				return desired;
			}
			Vec3 normalized = desired.normalize();
			Vec3 lateral = normalized.cross(new Vec3(0.0D, 1.0D, 0.0D));
			if (lateral.lengthSqr() <= 1.0E-6D) {
				lateral = normalized.cross(new Vec3(1.0D, 0.0D, 0.0D));
			}
			if (lateral.lengthSqr() > 1.0E-6D) {
				lateral = lateral.normalize();
			}
			double missFactor = 1.0D - Mth.clamp(attackAccuracy, 0.0D, 1.0D);
			double sideSign = shooter.getRandom().nextBoolean() ? -1.0D : 1.0D;
			double lateralStrength = Mth.lerp(missFactor, 1.4D, 2.4D);
			double verticalStrength = Mth.lerp(missFactor, 0.25D, 0.9D) * (shooter.getRandom().nextBoolean() ? -1.0D : 1.0D);
			Vec3 backwardBias = normalized.scale(-0.35D);
			Vec3 miss = lateral.scale(sideSign * lateralStrength).add(0.0D, verticalStrength, 0.0D).add(backwardBias);
			return miss.lengthSqr() <= 1.0E-6D ? lateral : miss.normalize();
		}

		private record PendingRangedBowCharge(UUID targetUuid, int remainingTicks) {
			private PendingRangedBowCharge withRemainingTicks(int remainingTicks) {
				return new PendingRangedBowCharge(targetUuid, remainingTicks);
			}
		}

		private record ShotVector(Vec3 vector, boolean guaranteedHit) {
		}
	}

	public static final class SpiderBehavior {
		private static final String SPIDER_VARIANT_TAG_PREFIX = "madoku-craft.spider.variant:";

		private SpiderBehavior() {
		}

		public static boolean applySpawnOverrides(
			Spider spider,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason
		) {
			if (spider == null || world == null || difficulty == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			if (spider.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.SPIDER || spawnReason == EntitySpawnReason.JOCKEY) {
				return false;
			}

			String fileKey = MobConfigManager.FILE_SPIDER;
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}

			JsonObject fileRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject spiderRoot = readMobRoot(fileRoot, fileKey);
			boolean overrideSpawnRules = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
			if (!overrideSpawnRules) {
				clearSpiderVariantTag(spider);
				return false;
			}
			if (spiderRoot.entrySet().isEmpty()) {
				clearSpiderVariantTag(spider);
				return false;
			}

			JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(fileRoot);
			if (defaultGroup.entrySet().isEmpty()) {
				clearSpiderVariantTag(spider);
				return false;
			}

			String storedVariant = readStoredSpiderVariantKey(spider);
			if (!storedVariant.isBlank()) {
				JsonObject storedVariantRoot = resolveSpiderVariantRootByKey(fileRoot, storedVariant);
				if (storedVariantRoot.entrySet().isEmpty()) {
					return false;
				}
				JsonObject effectiveStoredVariantRoot = MobEntityManager.resolveVariantGroupRoot(defaultGroup, storedVariantRoot);
				clearExistingSkeletonPassengers(spider);
				return applyConfiguredSpiderVariantOutcome(spider, world, difficulty, spawnReason, effectiveStoredVariantRoot);
			}

			// The common finalizeSpawn HEAD hook has already selected and stored the
			// top-level variant. An empty key means the primary/default variant.
			clearExistingSkeletonPassengers(spider);
			return applyConfiguredSpiderVariantOutcome(spider, world, difficulty, spawnReason, defaultGroup);
		}

		private static boolean applyConfiguredSpiderVariantOutcome(
			Spider spider,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			JsonObject variantRoot
		) {
			if (variantRoot == null) {
				return false;
			}

			JsonObject spawnRules = readObject(variantRoot, MobConfigManager.FIELD_SPAWN_RULES);
			JsonObject alternativeMobRoot = readObject(spawnRules, MobConfigManager.FIELD_SPAWN_ALTERNATIVE_MOB);
			if (!alternativeMobRoot.entrySet().isEmpty() && readBoolean(alternativeMobRoot, MobConfigManager.FIELD_ENABLED, false)) {
				EntityType<?> replacementType = MobEntityManager.resolveConfiguredMobEntityType(alternativeMobRoot);
				if (replacementType != null && replacementType != madoku.craft.mobs.mob.MadokuMobEntityTypes.SPIDER) {
					if (replacementType == madoku.craft.mobs.mob.MadokuMobEntityTypes.CAVE_SPIDER) {
						MobEntityManager.queueCaveSpiderReplacement(spider, spawnReason);
					}
					return true;
				}
			}

			return false;
		}

		public static boolean shouldOverrideSpawnRules(Spider spider) {
			if (spider == null || spider.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.SPIDER || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = MobConfigManager.FILE_SPIDER;
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			return readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		}

		private static String readStoredSpiderVariantKey(Spider spider) {
			if (spider == null) {
				return "";
			}
			for (String tag : spider.entityTags()) {
				if (tag == null || !tag.startsWith(SPIDER_VARIANT_TAG_PREFIX)) {
					continue;
				}
				String normalized = normalizeKey(tag.substring(SPIDER_VARIANT_TAG_PREFIX.length()));
				if (!normalized.isBlank()) {
					return normalized;
				}
			}
			return "";
		}

		private static void clearSpiderVariantTag(Spider spider) {
			if (spider == null) {
				return;
			}
			String existing = null;
			for (String tag : spider.entityTags()) {
				if (tag != null && tag.startsWith(SPIDER_VARIANT_TAG_PREFIX)) {
					existing = tag;
					break;
				}
			}
			if (existing != null) {
				spider.removeTag(existing);
			}
		}

		private static boolean isReservedSpiderGroupKey(String normalizedKey) {
			if (normalizedKey == null || normalizedKey.isBlank()) {
				return true;
			}
			return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_ENABLED))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_COMPONENTS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_BEHAVIORS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_GOALS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_MOB_COMPONENTS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_SPAWN_RULES))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_MOB_BEHAVIORS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_MOB_GOALS))
				|| normalizedKey.equals(normalizeKey("spider-spawn-weight"))
				|| normalizedKey.equals(normalizeKey("cave-spider-spawn-weight"))
				|| normalizedKey.equals(normalizeKey("spider-jockey-spawn-weight"));
		}

		private static JsonObject resolveSpiderVariantRootByKey(JsonObject spiderRoot, String variantKey) {
			return MobEntityManager.resolveVariantRootByKey(
				spiderRoot,
				variantKey,
				SpiderBehavior::isReservedSpiderGroupKey
			);
		}

		private static void clearExistingSkeletonPassengers(Spider spider) {
			for (Entity passenger : new ArrayList<>(spider.getPassengers())) {
				if (passenger.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.SKELETON) {
					passenger.stopRiding();
					passenger.discard();
				}
			}
		}

		private static JsonObject readMobRoot(JsonObject fileRoot, String fileKey) {
			if (fileRoot == null || fileKey == null || fileKey.isBlank()) {
				return new JsonObject();
			}
			return EntityConfigManager.resolvePrimaryVariant(fileRoot);
		}

		private static JsonObject readObject(JsonObject parent, String key) {
			if (parent == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			JsonElement element = EntityConfigManager.resolveConfiguredElement(parent, key);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		}

		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() ? element.getAsBoolean() : fallback;
		}

		private static String normalizeKey(String value) {
			return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		}

	}

	public static final class StrayBehavior {
		private static final int DEFAULT_ATTACK_INTERVAL_TICKS = 20;
		private static final int DEFAULT_CHARGE_UP_TICKS = 10;
		private static final String STRAY_VARIANT_TAG_PREFIX = "madoku-craft.stray.variant:";

		private static final Map<UUID, PendingRangedBowCharge> PENDING_RANGED_BOW_CHARGES = new ConcurrentHashMap<>();
		private static final Map<UUID, Integer> RANGED_BOW_COOLDOWNS = new ConcurrentHashMap<>();

		private StrayBehavior() {
		}

		public static void applySpawnOverrides(
			AbstractSkeleton skeleton,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason
		) {
			if (skeleton == null || world == null || difficulty == null || !MobEntityManager.isEnabled()) {
				return;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveMobFileSectionForRuntime(fileKey);
			JsonObject variantGroup = resolveVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, world, true);
			if (variantGroup.entrySet().isEmpty()) {
				return;
			}
			JsonObject resolvedRoot = mergeFileSettings(fileConfigRoot, variantGroup);

			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			applyWeaponDamagePolicy(skeleton, resolvedRoot);
			applyBehaviorToggles(skeleton, fileConfigRoot, resolvedRoot);
			if (overrideStats) {
				MobEntityManager.applyUniversalBaseStatsForRuntime(skeleton, resolvedRoot);
			}
			if (isBowAttackEnabled(skeleton)) {
				ensureBowEquipped(skeleton);
			}
		}

		public static boolean shouldOverrideSpawnRules(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			return readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			if (!(entity instanceof AbstractSkeleton skeleton) || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject resolvedRoot = resolveRuntimeRoot(skeleton);
			if (resolvedRoot.entrySet().isEmpty()) {
				return false;
			}

			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			boolean modified = overrideStats && MobEntityManager.applyUniversalBaseStatsForRuntime(skeleton, resolvedRoot);
			applyWeaponDamagePolicy(skeleton, resolvedRoot);
			applyBehaviorToggles(skeleton, fileConfigRoot, resolvedRoot);
			if (isBowAttackEnabled(skeleton)) {
				ensureBowEquipped(skeleton);
			}
			return modified;
		}

		public static JsonObject resolveRuntimeRoot(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return new JsonObject();
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return new JsonObject();
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveMobFileSectionForRuntime(fileKey);
			JsonObject variantGroup = resolveVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, null, false);
			if (variantGroup.entrySet().isEmpty()) {
				return new JsonObject();
			}
			return mergeFileSettings(fileConfigRoot, variantGroup);
		}

		public static boolean isBowAttackEnabled(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			return !root.entrySet().isEmpty() && MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false);
		}

		public static void ensureBowEquipped(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			ItemStack main = skeleton.getItemBySlot(EquipmentSlot.MAINHAND);
			if (main.isEmpty() || !main.is(Items.BOW)) {
				skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			}
		}

		public static boolean applyRangedSkeletonBowAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
			if (skeleton == null || target == null || !target.isAlive() || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			if (!isBowAttackEnabled(skeleton)) {
				return false;
			}

			UUID skeletonId = skeleton.getUUID();
			int cooldown = RANGED_BOW_COOLDOWNS.getOrDefault(skeletonId, 0);
			if (cooldown > 0) {
				return true;
			}
			PendingRangedBowCharge pending = PENDING_RANGED_BOW_CHARGES.get(skeletonId);
			if (pending != null) {
				return true;
			}

			int chargeUpTicks = resolveBowChargeUpTicks(skeleton);
			if (chargeUpTicks <= 0) {
				if (fireRangedBowArrow(skeleton, target)) {
					RANGED_BOW_COOLDOWNS.put(skeletonId, resolveBowAttackIntervalTicks(skeleton));
				}
				return true;
			}

			PENDING_RANGED_BOW_CHARGES.put(skeletonId, new PendingRangedBowCharge(target.getUUID(), chargeUpTicks));
			return true;
		}

		public static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton) {
			if (!isBowAttackEnabled(skeleton)) {
				return -1;
			}
			return resolveBowAttackIntervalTicks(skeleton, DEFAULT_ATTACK_INTERVAL_TICKS);
		}

		public static int resolveBowChargeUpTicks(Monster attacker) {
			if (!(attacker instanceof AbstractSkeleton skeleton) || !isBowAttackEnabled(skeleton)) {
				return -1;
			}
			return resolveBowChargeUpTicks(skeleton, DEFAULT_CHARGE_UP_TICKS);
		}

		public static void tickRangedSkeletonRuntime(AbstractSkeleton skeleton) {
			if (skeleton == null || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return;
			}
			if (!isBowAttackEnabled(skeleton)) {
				clearRangedSkeletonRuntimeState(skeleton);
				return;
			}

			UUID skeletonId = skeleton.getUUID();
			Integer cooldown = RANGED_BOW_COOLDOWNS.get(skeletonId);
			if (cooldown != null) {
				if (cooldown <= 1) {
					RANGED_BOW_COOLDOWNS.remove(skeletonId);
				} else {
					RANGED_BOW_COOLDOWNS.put(skeletonId, cooldown - 1);
				}
			}

			PendingRangedBowCharge pending = PENDING_RANGED_BOW_CHARGES.get(skeletonId);
			if (pending == null) {
				return;
			}
			if (pending.remainingTicks() > 1) {
				PENDING_RANGED_BOW_CHARGES.put(skeletonId, pending.withRemainingTicks(pending.remainingTicks() - 1));
				return;
			}

			PENDING_RANGED_BOW_CHARGES.remove(skeletonId);
			if (RANGED_BOW_COOLDOWNS.containsKey(skeletonId)) {
				return;
			}
			if (!(skeleton.level() instanceof ServerLevel level)) {
				return;
			}
			Entity targetEntity = level.getEntity(pending.targetUuid());
			if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
				return;
			}
			if (fireRangedBowArrow(skeleton, target)) {
				RANGED_BOW_COOLDOWNS.put(skeletonId, resolveBowAttackIntervalTicks(skeleton));
			}
		}

		public static void onEntityCleanup(Entity entity) {
			if (entity instanceof AbstractSkeleton skeleton) {
				clearRangedSkeletonRuntimeState(skeleton);
			}
		}

		public static void resetRuntimeState() {
			PENDING_RANGED_BOW_CHARGES.clear();
			RANGED_BOW_COOLDOWNS.clear();
		}

		private static JsonObject resolveVariantGroupRoot(
			AbstractSkeleton skeleton,
			JsonObject fileConfigRoot,
			JsonObject fileRoot,
			ServerLevelAccessor world,
			boolean spawnContext
		) {
			JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(fileConfigRoot);
			if (defaultGroup.entrySet().isEmpty()) {
				clearVariantTag(skeleton);
				return new JsonObject();
			}

			String storedVariant = readStoredVariantKey(skeleton);
			if (!storedVariant.isBlank()) {
				JsonObject known = resolveVariantRootByKey(fileConfigRoot, storedVariant);
				if (!known.entrySet().isEmpty()) {
					return MobEntityManager.resolveVariantGroupRoot(defaultGroup, known);
				}
			}

			boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
			if (!spawnContext || !overrideSpawnRules || world == null) {
				return defaultGroup;
			}

			String selectedVariant = selectVariantKey(fileConfigRoot, world);
			if (selectedVariant.isBlank()) {
				return defaultGroup;
			}
			writeVariantTag(skeleton, selectedVariant);
			JsonObject selected = resolveVariantRootByKey(fileConfigRoot, selectedVariant);
			return selected.entrySet().isEmpty() ? defaultGroup : MobEntityManager.resolveVariantGroupRoot(defaultGroup, selected);
		}

		private static String selectVariantKey(JsonObject fileRoot, ServerLevelAccessor world) {
			return MobEntityManager.selectWeightedVariantKey(
				fileRoot,
				world == null ? null : world.getRandom(),
				StrayBehavior::isReservedStrayGroupKey,
				variantRoot -> MobEntityManager.resolveVariantSpawnWeight(variantRoot, 0.0D)
			);
		}

		private static JsonObject resolveVariantRootByKey(JsonObject fileRoot, String variantKey) {
			return MobEntityManager.resolveVariantRootByKey(
				fileRoot,
				variantKey,
				StrayBehavior::isReservedStrayGroupKey
			);
		}

		private static boolean isReservedStrayGroupKey(String normalizedKey) {
			if (normalizedKey == null || normalizedKey.isBlank()) {
				return true;
			}
			return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_ENABLED))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_COMPONENTS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_BEHAVIORS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_GOALS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WEAPON_DAMAGE))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW));
		}

		private static String readStoredVariantKey(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return "";
			}
			for (String tag : skeleton.entityTags()) {
				if (tag == null || !tag.startsWith(STRAY_VARIANT_TAG_PREFIX)) {
					continue;
				}
				String normalized = normalizeKey(tag.substring(STRAY_VARIANT_TAG_PREFIX.length()));
				if (!normalized.isBlank()) {
					return normalized;
				}
			}
			return "";
		}

		private static void writeVariantTag(AbstractSkeleton skeleton, String variantKey) {
			if (skeleton == null || variantKey == null || variantKey.isBlank()) {
				return;
			}
			clearVariantTag(skeleton);
			skeleton.addTag(STRAY_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
		}

		private static void clearVariantTag(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			String existing = null;
			for (String tag : skeleton.entityTags()) {
				if (tag != null && tag.startsWith(STRAY_VARIANT_TAG_PREFIX)) {
					existing = tag;
					break;
				}
			}
			if (existing != null) {
				skeleton.removeTag(existing);
			}
		}

		private static void applyBehaviorToggles(AbstractSkeleton skeleton, JsonObject fileRoot, JsonObject variantRoot) {
			if (skeleton == null) {
				return;
			}
			boolean overrideBehavior = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIORS, true);
			if (overrideBehavior) {
				skeleton.setCanPickUpLoot(MobEntityManager.readMobBehaviorBooleanForRuntime(variantRoot, MobConfigManager.FIELD_CAN_PICK_UP_LOOT, true));
			}
		}

		private static void applyWeaponDamagePolicy(AbstractSkeleton skeleton, JsonObject resolvedRoot) {
			if (skeleton == null || resolvedRoot == null) {
				return;
			}
			boolean weaponDamageEnabled = readBoolean(resolvedRoot, MobConfigManager.FIELD_WEAPON_DAMAGE, true);
			if (weaponDamageEnabled) {
				return;
			}
			stripHeldAttackDamageModifiers(skeleton, EquipmentSlot.MAINHAND);
			stripHeldAttackDamageModifiers(skeleton, EquipmentSlot.OFFHAND);
		}

		private static boolean fireRangedBowArrow(AbstractSkeleton skeleton, LivingEntity target) {
			if (skeleton == null || target == null || !target.isAlive() || !(skeleton.level() instanceof ServerLevel level)) {
				return false;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty()) {
				return false;
			}

			ensureBowEquipped(skeleton);
			InteractionHand bowHand = resolveBowHand(skeleton);
			if (bowHand == null) {
				return false;
			}
			ItemStack bowStack = skeleton.getItemInHand(bowHand);
			ItemStack projectileStack = skeleton.getProjectile(bowStack);
			if (projectileStack.isEmpty()) {
				projectileStack = new ItemStack(Items.ARROW);
			}
			AbstractArrow arrow = ((AbstractSkeletonArrowInvoker) skeleton).madokuCraft$invokeGetArrow(projectileStack, 1.0F, bowStack);
			if (arrow == null) {
				return false;
			}

			double accuracy = resolveScaledAttackAccuracy(
				readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_ATTACK_ACCURACY, 0.7D),
				skeleton.level().getDifficulty(),
				isHardcoreWorld(skeleton.level())
			);
			double rangedDamage = resolveSkeletonRangedDamage(skeleton, root);
			accuracy = MobRegionalDifficultyManager.resolveMobAttackAccuracyScaling(skeleton, accuracy);
			ShotVector shot = resolveShotVector(skeleton, arrow, target, accuracy);
			arrow.shoot(shot.vector.x, shot.vector.y, shot.vector.z, 1.6F, 0.0F);
			arrow.setCritArrow(false);
			MobEntityManager.setProjectileDamageOverride(arrow, (float) Math.max(0.0D, rangedDamage));
			MobEntityManager.trackManagedMobArrowForRuntime(arrow, level.getServer());
			if (shot.guaranteedHit) {
				MobEntityManager.startProjectileHoming(arrow, target, level.getServer());
			}
			skeleton.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
			level.addFreshEntity(arrow);
			return true;
		}

		private static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton, int fallback) {
			if (skeleton == null || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return fallback;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty() || !MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false)) {
				return fallback;
			}
			double interval = readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_ATTACK_INTERVAL, fallback);
			return Math.max(1, (int) Math.round(interval));
		}

		private static int resolveBowChargeUpTicks(AbstractSkeleton skeleton, int fallback) {
			if (skeleton == null || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return fallback;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty() || !MobEntityManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false)) {
				return fallback;
			}
			double charge = readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_CHARGE_INTERVAL, fallback);
			return Math.max(0, (int) Math.round(charge));
		}

		private static void clearRangedSkeletonRuntimeState(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			UUID skeletonId = skeleton.getUUID();
			PENDING_RANGED_BOW_CHARGES.remove(skeletonId);
			RANGED_BOW_COOLDOWNS.remove(skeletonId);
		}

		private static InteractionHand resolveBowHand(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return null;
			}
			if (skeleton.getMainHandItem().is(Items.BOW)) {
				return InteractionHand.MAIN_HAND;
			}
			if (skeleton.getOffhandItem().is(Items.BOW)) {
				return InteractionHand.OFF_HAND;
			}
			return null;
		}

		private static JsonObject mergeFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
			JsonObject merged = variantRoot == null ? new JsonObject() : variantRoot.deepCopy();
			if (fileRoot == null || fileRoot.entrySet().isEmpty()) {
				return merged;
			}
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_CUSTOM_MOB_DROPS);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WEAPON_DAMAGE);
			return merged;
		}

		private static String fileKeyForType(AbstractSkeleton skeleton) {
			if (skeleton == null || skeleton.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.STRAY) {
				return "";
			}
			return MobConfigManager.FILE_STRAY;
		}

		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			return element.getAsBoolean();
		}

		private static double readDouble(JsonObject root, String key, double fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
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

		private static JsonObject readObject(JsonObject root, String key) {
			if (root == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			JsonElement element = EntityConfigManager.resolveConfiguredElement(root, key);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		}

		private static void copyIfMissing(JsonObject target, JsonObject source, String key) {
			if (target == null || source == null || key == null || key.isBlank()) {
				return;
			}
			if (!target.has(key) && source.has(key)) {
				target.add(key, source.get(key).deepCopy());
			}
		}

		private static String normalizeKey(String value) {
			return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		}

		private static boolean isHardcoreWorld(Level level) {
			return level != null && level.getServer() != null && level.getServer().isHardcore();
		}

		private static double resolveSkeletonRangedDamage(AbstractSkeleton skeleton, JsonObject root) {
			if (skeleton == null) {
				return 0.0D;
			}
			double rangedDamage = resolveScaledRangedDamage(skeleton,
				readDouble(readMobComponentsRoot(root), MobConfigManager.FIELD_RANGED_DAMAGE, 4.0D),
				skeleton.level().getDifficulty(),
				isHardcoreWorld(skeleton.level())
			);
			return rangedDamage;
		}

		private static double resolveScaledRangedDamage(AbstractSkeleton skeleton, double base, Difficulty difficulty, boolean hardcore) {
			double regional = MobRegionalDifficultyManager.resolveMobRangedDamageScaling(skeleton, base);
			return MobEntityManager.resolveWorldDifficultyValueForRuntime(skeleton, MobConfigManager.FIELD_RANGED_DAMAGE, regional);
		}

		private static double resolveScaledAttackAccuracy(double base, Difficulty difficulty, boolean hardcore) {
			return Mth.clamp(resolveDifficultyAdjustedValue(difficulty, hardcore, Mth.clamp(base, 0.0D, 1.0D), 0.05D, 0.0D), 0.0D, 1.0D);
		}

		private static double resolveDifficultyAdjustedValue(Difficulty difficulty, boolean hardcore, double baseValue, double step, double minimum) {
			return roundDifficultyScaleValue(Math.max(minimum, baseValue + (step * resolveDifficultyTier(difficulty, hardcore))));
		}

		private static double roundDifficultyScaleValue(double value) {
			if (!Double.isFinite(value)) {
				return value;
			}
			double step = isWholeNumber(value) ? 0.05D : 0.005D;
			return Math.round(value / step) * step;
		}

		private static boolean isWholeNumber(double value) {
			return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
		}

		private static int resolveDifficultyTier(Difficulty difficulty, boolean hardcore) {
			Difficulty resolved = difficulty == null ? Difficulty.NORMAL : difficulty;
			return switch (resolved) {
				case PEACEFUL -> -2;
				case EASY -> -1;
				case NORMAL -> 0;
				case HARD -> hardcore ? 2 : 1;
			};
		}

		private static ShotVector resolveShotVector(AbstractSkeleton skeleton, AbstractArrow arrow, LivingEntity target, double accuracy) {
			double dx = target.getX() - skeleton.getX();
			double dz = target.getZ() - skeleton.getZ();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			double dy = target.getY(1.0D / 3.0D) - arrow.getY() + (horizontal * 0.2D);
			Vec3 desired = new Vec3(dx, dy, dz);
			if (desired.lengthSqr() <= 1.0E-6D) {
				return new ShotVector(desired, true);
			}
			double clampedAccuracy = Mth.clamp(accuracy, 0.0D, 1.0D);
			if (skeleton.getRandom().nextDouble() <= clampedAccuracy) {
				return new ShotVector(desired, true);
			}
			return new ShotVector(resolveMissVector(desired.x, desired.y, desired.z, clampedAccuracy, skeleton), false);
		}

		private static Vec3 resolveMissVector(double velocityX, double velocityY, double velocityZ, double attackAccuracy, LivingEntity shooter) {
			Vec3 desired = new Vec3(velocityX, velocityY, velocityZ);
			if (desired.lengthSqr() <= 1.0E-6D) {
				return desired;
			}
			Vec3 normalized = desired.normalize();
			Vec3 lateral = normalized.cross(new Vec3(0.0D, 1.0D, 0.0D));
			if (lateral.lengthSqr() <= 1.0E-6D) {
				lateral = normalized.cross(new Vec3(1.0D, 0.0D, 0.0D));
			}
			if (lateral.lengthSqr() > 1.0E-6D) {
				lateral = lateral.normalize();
			}
			double missFactor = 1.0D - Mth.clamp(attackAccuracy, 0.0D, 1.0D);
			double sideSign = shooter.getRandom().nextBoolean() ? -1.0D : 1.0D;
			double lateralStrength = Mth.lerp(missFactor, 1.4D, 2.4D);
			double verticalStrength = Mth.lerp(missFactor, 0.25D, 0.9D) * (shooter.getRandom().nextBoolean() ? -1.0D : 1.0D);
			Vec3 backwardBias = normalized.scale(-0.35D);
			Vec3 miss = lateral.scale(sideSign * lateralStrength).add(0.0D, verticalStrength, 0.0D).add(backwardBias);
			return miss.lengthSqr() <= 1.0E-6D ? lateral : miss.normalize();
		}

		private static void stripHeldAttackDamageModifiers(AbstractSkeleton skeleton, EquipmentSlot slot) {
			if (skeleton == null || slot == null) {
				return;
			}
			ItemStack stack = skeleton.getItemBySlot(slot);
			if (stack == null || stack.isEmpty()) {
				return;
			}
			ItemStack normalized = stack.copy();
			normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
			skeleton.setItemSlot(slot, normalized);
		}

		private static JsonObject readMobComponentsRoot(JsonObject root) {
			return readObject(root, MobConfigManager.FIELD_MOB_COMPONENTS);
		}

		private record PendingRangedBowCharge(UUID targetUuid, int remainingTicks) {
			private PendingRangedBowCharge withRemainingTicks(int remainingTicks) {
				return new PendingRangedBowCharge(targetUuid, remainingTicks);
			}
		}

		private record ShotVector(Vec3 vector, boolean guaranteedHit) {
		}
	}

	public static final class WitherSkeletonBehavior {
		private static final int DEFAULT_WITHER_EFFECT_DURATION_TICKS = 5 * 20;
		private static final String WITHER_SKELETON_VARIANT_TAG_PREFIX = "madoku-craft.wither-skeleton.variant:";

		private WitherSkeletonBehavior() {
		}

		public static void applySpawnOverrides(
			AbstractSkeleton skeleton,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason
		) {
			if (skeleton == null || world == null || difficulty == null || !MobEntityManager.isEnabled()) {
				return;
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_WITHER_SKELETON);
			if (!readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)) {
				return;
			}
			JsonObject fileRoot = MobEntityManager.resolveMobFileSectionForRuntime(MobConfigManager.FILE_WITHER_SKELETON);
			JsonObject variantGroup = resolveWitherSkeletonVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, world, true);
			if (variantGroup.entrySet().isEmpty()) {
				return;
			}
			JsonObject root = mergeWitherSkeletonFileSettings(fileConfigRoot, variantGroup);
			if (readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true)) {
				MobEntityManager.applyUniversalBaseStatsForRuntime(skeleton, root);
			}
			if (isBowAttackEnabled(skeleton)) {
				ensureBowEquipped(skeleton);
			}
		}

		public static boolean shouldOverrideSpawnRules(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return false;
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_WITHER_SKELETON);
			return readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			if (!(entity instanceof AbstractSkeleton skeleton) || skeleton.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_WITHER_SKELETON);
			JsonObject root = resolveRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty()) {
				return false;
			}
			boolean modified = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true)
				&& MobEntityManager.applyUniversalBaseStatsForRuntime(skeleton, root);
			if (isBowAttackEnabled(skeleton)) {
				ensureBowEquipped(skeleton);
			}
			return modified;
		}

		public static JsonObject resolveRuntimeRoot(AbstractSkeleton skeleton) {
			if (skeleton == null || !MobEntityManager.isEnabled()) {
				return new JsonObject();
			}
			String fileKey = skeletonFileKey(skeleton);
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return new JsonObject();
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveMobFileSectionForRuntime(fileKey);
			JsonObject variantGroup = resolveWitherSkeletonVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, null, false);
			if (variantGroup.entrySet().isEmpty()) {
				return new JsonObject();
			}
			return mergeWitherSkeletonFileSettings(fileConfigRoot, variantGroup);
		}

		public static boolean isBowAttackEnabled(AbstractSkeleton skeleton) {
			return EntityBehaviorsManager.SkeletonBehavior.isBowAttackEnabled(skeleton);
		}

		public static void ensureBowEquipped(AbstractSkeleton skeleton) {
			EntityBehaviorsManager.SkeletonBehavior.ensureBowEquipped(skeleton);
		}

		public static boolean applyRangedSkeletonBowAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
			return EntityBehaviorsManager.SkeletonBehavior.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
		}

		public static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton) {
			return EntityBehaviorsManager.SkeletonBehavior.resolveBowAttackIntervalTicks(skeleton);
		}

		public static int resolveBowChargeUpTicks(Monster attacker) {
			return EntityBehaviorsManager.SkeletonBehavior.resolveBowChargeUpTicks(attacker);
		}

		public static void tickRangedSkeletonRuntime(AbstractSkeleton skeleton) {
			EntityBehaviorsManager.SkeletonBehavior.tickRangedSkeletonRuntime(skeleton);
		}

		public static void onEntityCleanup(Entity entity) {
			EntityBehaviorsManager.SkeletonBehavior.onEntityCleanup(entity);
		}

		public static void resetRuntimeState() {
			EntityBehaviorsManager.SkeletonBehavior.resetRuntimeState();
		}

		private static JsonObject resolveWitherSkeletonVariantGroupRoot(
			AbstractSkeleton skeleton,
			JsonObject fileConfigRoot,
			JsonObject fileRoot,
			ServerLevelAccessor world,
			boolean spawnContext
		) {
			JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(fileConfigRoot);
			if (defaultGroup.entrySet().isEmpty()) {
				clearWitherSkeletonVariantTag(skeleton);
				return new JsonObject();
			}

			String storedVariant = readStoredWitherSkeletonVariantKey(skeleton);
			if (!storedVariant.isBlank()) {
				JsonObject known = resolveWitherSkeletonVariantRootByKey(fileConfigRoot, storedVariant);
				if (!known.entrySet().isEmpty()) {
					return MobEntityManager.resolveVariantGroupRoot(defaultGroup, known);
				}
			}

			boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
			if (!spawnContext || !overrideSpawnRules || world == null) {
				return defaultGroup;
			}

			String selectedVariant = selectWitherSkeletonVariantKey(fileConfigRoot, world);
			if (selectedVariant.isBlank()) {
				return defaultGroup;
			}
			writeWitherSkeletonVariantTag(skeleton, selectedVariant);
			JsonObject selected = resolveWitherSkeletonVariantRootByKey(fileConfigRoot, selectedVariant);
			return selected.entrySet().isEmpty() ? defaultGroup : MobEntityManager.resolveVariantGroupRoot(defaultGroup, selected);
		}

		private static JsonObject resolveWitherSkeletonVariantRootByKey(JsonObject fileRoot, String variantKey) {
			return MobEntityManager.resolveVariantRootByKey(
				fileRoot,
				variantKey,
				WitherSkeletonBehavior::isReservedWitherSkeletonGroupKey
			);
		}

		private static String selectWitherSkeletonVariantKey(JsonObject fileRoot, ServerLevelAccessor world) {
			return MobEntityManager.selectWeightedVariantKey(
				fileRoot,
				world == null ? null : world.getRandom(),
				WitherSkeletonBehavior::isReservedWitherSkeletonGroupKey,
				variantRoot -> MobEntityManager.resolveVariantSpawnWeight(variantRoot, 0.0D)
			);
		}

		private static boolean isReservedWitherSkeletonGroupKey(String normalizedKey) {
			if (normalizedKey == null || normalizedKey.isBlank()) {
				return true;
			}
			return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_ENABLED))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_COMPONENTS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_BEHAVIORS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_GOALS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WEAPON_DAMAGE))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW));
		}

		public static boolean applyWitherSkeletonHitEffect(LivingEntity target, Entity attacker) {
			if (target == null || attacker == null || target.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			if (!(attacker instanceof AbstractSkeleton skeleton) || skeleton.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON) {
				return false;
			}
			JsonObject root = resolveRuntimeRoot(skeleton);
			if (root.entrySet().isEmpty()) {
				return target.addEffect(new MobEffectInstance(MobEffects.WITHER, DEFAULT_WITHER_EFFECT_DURATION_TICKS), attacker);
			}
			MobEffectInstance effect = resolveConfiguredMobEffectInstance(readMobComponentsRoot(root), new MobEffectInstance(MobEffects.WITHER, DEFAULT_WITHER_EFFECT_DURATION_TICKS));
			return effect != null && target.addEffect(effect, attacker);
		}
		private static JsonObject readMobComponentsRoot(JsonObject root) {
			return readObject(root, MobConfigManager.FIELD_MOB_COMPONENTS);
		}

		private static JsonObject readObject(JsonObject parent, String key) {
			if (parent == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
		}

		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			if (!root.has(key) || !root.get(key).isJsonPrimitive() || !root.get(key).getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			return root.get(key).getAsBoolean();
		}

		private static String readString(JsonObject root, String key, String fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			if (!root.has(key) || !root.get(key).isJsonPrimitive() || !root.get(key).getAsJsonPrimitive().isString()) {
				return fallback;
			}
			try {
				String value = root.get(key).getAsString();
				return value == null ? fallback : value;
			} catch (RuntimeException ignored) {
				return fallback;
			}
		}

		private static double readDouble(JsonObject root, String key, double fallback) {
			if (root == null) {
				return fallback;
			}
			if (!root.has(key) || !root.get(key).isJsonPrimitive() || !root.get(key).getAsJsonPrimitive().isNumber()) {
				return fallback;
			}
			try {
				double value = root.get(key).getAsDouble();
				return Double.isFinite(value) ? value : fallback;
			} catch (RuntimeException ignored) {
				return fallback;
			}
		}

		private static MobEffectInstance resolveConfiguredMobEffectInstance(JsonObject componentsRoot, MobEffectInstance fallbackEffect) {
			if (componentsRoot == null || componentsRoot.entrySet().isEmpty() || fallbackEffect == null) {
				return fallbackEffect;
			}
			JsonObject mobEffectRoot = readObject(componentsRoot, MobConfigManager.FIELD_MOB_EFFECT);
			if (mobEffectRoot.entrySet().isEmpty()) {
				return fallbackEffect;
			}
			String effectId = normalizeKey(readString(mobEffectRoot, MobConfigManager.FIELD_EFFECT, ""));
			if (effectId.isBlank()) {
				return fallbackEffect;
			}
			Identifier effectIdentifier = Identifier.tryParse(effectId);
			if (effectIdentifier == null || !BuiltInRegistries.MOB_EFFECT.containsKey(effectIdentifier)) {
				return fallbackEffect;
			}
			MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.getValue(effectIdentifier);
			if (mobEffect == null) {
				return fallbackEffect;
			}
			int durationSeconds = Math.max(1, (int) Math.round(readDouble(mobEffectRoot, MobConfigManager.FIELD_DURATION, 0.0D)));
			return new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(mobEffect), durationSeconds * 20);
		}

		private static JsonObject mergeWitherSkeletonFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
			JsonObject merged = variantRoot == null ? new JsonObject() : variantRoot.deepCopy();
			if (fileRoot == null || fileRoot.entrySet().isEmpty()) {
				return merged;
			}
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_CUSTOM_MOB_DROPS);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WEAPON_DAMAGE);
			return merged;
		}

		private static void copyIfMissing(JsonObject target, JsonObject source, String key) {
			if (target == null || source == null || key == null || key.isBlank()) {
				return;
			}
			if (!target.has(key) && source.has(key)) {
				target.add(key, source.get(key).deepCopy());
			}
		}

		private static String readStoredWitherSkeletonVariantKey(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return "";
			}
			for (String tag : skeleton.entityTags()) {
				if (tag == null || !tag.startsWith(WITHER_SKELETON_VARIANT_TAG_PREFIX)) {
					continue;
				}
				String normalized = normalizeKey(tag.substring(WITHER_SKELETON_VARIANT_TAG_PREFIX.length()));
				if (!normalized.isBlank()) {
					return normalized;
				}
			}
			return "";
		}

		private static void writeWitherSkeletonVariantTag(AbstractSkeleton skeleton, String variantKey) {
			if (skeleton == null || variantKey == null || variantKey.isBlank()) {
				return;
			}
			clearWitherSkeletonVariantTag(skeleton);
			skeleton.addTag(WITHER_SKELETON_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
		}

		private static void clearWitherSkeletonVariantTag(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return;
			}
			String existing = null;
			for (String tag : skeleton.entityTags()) {
				if (tag != null && tag.startsWith(WITHER_SKELETON_VARIANT_TAG_PREFIX)) {
					existing = tag;
					break;
				}
			}
			if (existing != null) {
				skeleton.removeTag(existing);
			}
		}

		private static String normalizeKey(String value) {
			return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		}

		private static String skeletonFileKey(AbstractSkeleton skeleton) {
			if (skeleton == null) {
				return "";
			}
			return skeleton.getType() == madoku.craft.mobs.mob.MadokuMobEntityTypes.WITHER_SKELETON ? MobConfigManager.FILE_WITHER_SKELETON : "";
		}
	}

	public static final class ZombieBehavior {
		private static final String ZOMBIE_VARIANT_TAG_PREFIX = "madoku-craft.zombie.variant:";

		private ZombieBehavior() {
		}

		public static void applySpawnOverrides(
			Zombie zombie,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason
		) {
			if (zombie == null || world == null || difficulty == null) {
				return;
			}
			boolean mobSystemEnabled = MobEntityManager.isEnabled();
			String fileKey = fileKeyForType(zombie.getType());
			if (fileKey.isBlank()) {
				return;
			}
			if (!mobSystemEnabled) {
				applySpawnEquipmentLoadoutWhenMobSystemDisabled(zombie, world.getRandom());
				return;
			}
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return;
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveZombieRootForRuntime(zombie.getType());
			JsonObject variantGroup = resolveZombieVariantGroupRoot(zombie, fileConfigRoot, fileRoot, world, true);
			boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			JsonObject variant = MobEntityManager.resolveNestedVariantForRuntime(
				variantGroup,
				zombie,
				overrideSpawnRules ? world.getRandom() : null,
				overrideSpawnRules
			);
			variant = mergeZombieFileSettings(fileRoot, variant);
			if (overrideSpawnRules && applyConfiguredZombieAlternativeMobReplacement(zombie, variant, spawnReason)) {
				return;
			}
			applyWeaponDamagePolicy(zombie, variant);
			applyZombieBehaviorToggles(zombie, fileConfigRoot, variant);
			if (overrideStats) {
				MobEntityManager.applyUniversalBaseStatsForRuntime(zombie, variant);
			}
		}

		public static boolean shouldOverrideSpawnRules(Zombie zombie) {
			if (zombie == null || zombie.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.ZOMBIE) {
				return false;
			}
			if (!MobEntityManager.isEnabled() || !MobEntityManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_ZOMBIE)) {
				return false;
			}
			JsonObject zombieFileRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_ZOMBIE);
			return readBoolean(zombieFileRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			if (!(entity instanceof Zombie zombie) || entity.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(zombie.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveZombieRootForRuntime(zombie.getType());
			JsonObject variantGroup = resolveZombieVariantGroupRoot(zombie, fileConfigRoot, fileRoot, null, false);
			JsonObject variant = MobEntityManager.resolveNestedVariantForRuntime(variantGroup, zombie, null, false);
			variant = mergeZombieFileSettings(fileRoot, variant);
			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			boolean modified = overrideStats && MobEntityManager.applyUniversalBaseStatsForRuntime(zombie, variant);
			applyWeaponDamagePolicy(zombie, variant);
			applyZombieBehaviorToggles(zombie, fileConfigRoot, variant);
			return modified;
		}

		static boolean isCustomMobDropsEnabled(LivingEntity entity) {
			if (!(entity instanceof Zombie zombie) || entity.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = fileKeyForType(zombie.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject resolved = resolveActiveZombieRoot(zombie, fileConfigRoot);
			boolean enabled = readBoolean(
				resolved,
				MobConfigManager.FIELD_CUSTOM_MOB_DROPS,
				true
			);
			return enabled;
		}

		static String resolveMobDropsConfigReference(LivingEntity entity) {
			if (!(entity instanceof Zombie zombie) || !MobEntityManager.isEnabled()) {
				return "";
			}
			String fileKey = fileKeyForType(zombie.getType());
			if (fileKey.isBlank() || !MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return "";
			}
			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject resolved = resolveActiveZombieRoot(zombie, fileConfigRoot);
			JsonObject componentsRoot = readObject(resolved, MobConfigManager.FIELD_MOB_COMPONENTS);
			String reference = readString(componentsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
			return reference;
		}

		private static JsonObject resolveActiveZombieRoot(Zombie zombie, JsonObject fileConfigRoot) {
			if (zombie == null) {
				return new JsonObject();
			}
			JsonObject fileRoot = MobEntityManager.resolveZombieRootForRuntime(zombie.getType());
			JsonObject variantGroup = resolveZombieVariantGroupRoot(zombie, fileConfigRoot, fileRoot, null, false);
			JsonObject variant = MobEntityManager.resolveNestedVariantForRuntime(variantGroup, zombie, null, false);
			return mergeZombieFileSettings(fileRoot, variant);
		}

		private static EquipmentLoadoutResult applySpawnEquipmentLoadoutWhenMobSystemDisabled(Zombie zombie, RandomSource random) {
			if (zombie == null || random == null) {
				return new EquipmentLoadoutResult(false, "invalid_inputs", "", 0.0D, "none", 0, 0);
			}
			if (!EquipmentConfigManager.isCustomEntityEquipmentEnabled()) {
				return new EquipmentLoadoutResult(false, "custom_entity_equipment_disabled", "", 0.0D, "none", 0, 0);
			}
			String equipmentReference = resolveDefaultMobEquipmentReference(zombie.getType());
			if (equipmentReference.isBlank()) {
				return new EquipmentLoadoutResult(false, "default_reference_blank", equipmentReference, 0.0D, "none", 0, 0);
			}
			return applyEquipmentSetLoadout(
				zombie,
				equipmentReference,
				EquipmentConfigManager.customEntityEquipmentChanceWhenMobSystemDisabled(),
				random
			);
		}

		private static boolean applyConfiguredZombieAlternativeMobReplacement(
			Zombie zombie,
			JsonObject variantRoot,
			EntitySpawnReason spawnReason
		) {
			if (zombie == null || variantRoot == null || zombie.getType() != madoku.craft.mobs.mob.MadokuMobEntityTypes.ZOMBIE) {
				return false;
			}
			JsonObject spawnRules = readObject(variantRoot, MobConfigManager.FIELD_SPAWN_RULES);
			JsonObject alternativeMobRoot = readObject(spawnRules, MobConfigManager.FIELD_SPAWN_ALTERNATIVE_MOB);
			if (alternativeMobRoot.entrySet().isEmpty() || !readBoolean(alternativeMobRoot, MobConfigManager.FIELD_ENABLED, false)) {
				return false;
			}
			EntityType<?> replacementType = MobEntityManager.resolveConfiguredMobEntityType(alternativeMobRoot, zombie.isBaby());
			if (replacementType == null || replacementType == madoku.craft.mobs.mob.MadokuMobEntityTypes.ZOMBIE) {
				return false;
			}
			MobEntityManager.queueZombieReplacement(zombie, replacementType, spawnReason);
			return true;
		}

		private static EquipmentLoadoutResult applyEquipmentSetLoadout(Zombie zombie, String equipmentReference, double chancePercent, RandomSource random) {
			if (zombie == null || random == null) {
				return new EquipmentLoadoutResult(false, "invalid_inputs", equipmentReference, chancePercent, "none", 0, 0);
			}
			if (chancePercent <= 0.0D || random.nextDouble() * 100.0D >= chancePercent) {
				return new EquipmentLoadoutResult(false, "chance_failed", equipmentReference, chancePercent, "none", 0, 0);
			}
			EquipmentConfigManager.EquipmentProfile profile = EquipmentConfigManager.resolveProfile(equipmentReference, zombie.getType());
			if (profile == null || !profile.enabled()) {
				return new EquipmentLoadoutResult(false, "profile_missing_or_disabled", equipmentReference, chancePercent, "none", 0, 0);
			}

			Map<EquipmentSlot, ItemStack> rolledBySlot = EquipmentConfigManager.rollEquipment(profile, random);
			if (rolledBySlot.isEmpty()) {
				return new EquipmentLoadoutResult(
					false,
					"slot_pool_empty",
					equipmentReference,
					chancePercent,
					"api",
					0,
					0
				);
			}
			MobEntityManager.clearArmorSlotsForRuntime(zombie);
			for (Map.Entry<EquipmentSlot, ItemStack> entry : rolledBySlot.entrySet()) {
				zombie.setItemSlot(entry.getKey(), entry.getValue());
			}
			return new EquipmentLoadoutResult(
				true,
				"applied",
				equipmentReference,
				chancePercent,
				"api",
				rolledBySlot.size(),
				rolledBySlot.size()
			);
		}



		private static JsonObject resolveZombieVariantGroupRoot(
			Zombie zombie,
			JsonObject fileConfigRoot,
			JsonObject fileRoot,
			ServerLevelAccessor world,
			boolean spawnContext
		) {
			JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(fileConfigRoot);
			if (defaultGroup.entrySet().isEmpty()) {
				clearZombieVariantTag(zombie);
				return new JsonObject();
			}
			String storedVariant = readStoredZombieVariantKey(zombie);
			if (!storedVariant.isBlank()) {
				JsonObject known = resolveZombieVariantRootByKey(fileConfigRoot, storedVariant);
				if (!known.entrySet().isEmpty()) {
					return MobEntityManager.resolveVariantGroupRoot(defaultGroup, known);
				}
			}

			boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
			if (!spawnContext || !overrideSpawnRules || world == null) {
				return defaultGroup;
			}

			String selectedVariant = selectZombieVariantKey(fileConfigRoot, world);
			if (selectedVariant.isBlank()) {
				return defaultGroup;
			}
			writeZombieVariantTag(zombie, selectedVariant);
			JsonObject selected = resolveZombieVariantRootByKey(fileConfigRoot, selectedVariant);
			return selected.entrySet().isEmpty() ? defaultGroup : MobEntityManager.resolveVariantGroupRoot(defaultGroup, selected);
		}

		private static JsonObject mergeZombieFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
			JsonObject merged = variantRoot == null ? new JsonObject() : variantRoot.deepCopy();
			if (fileRoot == null || fileRoot.entrySet().isEmpty()) {
				return merged;
			}
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_CUSTOM_MOB_DROPS);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WEAPON_DAMAGE);
			return merged;
		}

		private static void copyIfMissing(JsonObject target, JsonObject source, String key) {
			if (target == null || source == null || key == null || key.isBlank()) {
				return;
			}
			if (!target.has(key) && source.has(key)) {
				target.add(key, source.get(key).deepCopy());
			}
		}

		private static String selectZombieVariantKey(JsonObject fileRoot, ServerLevelAccessor world) {
			return MobEntityManager.selectWeightedVariantKey(
				fileRoot,
				world == null ? null : world.getRandom(),
				ZombieBehavior::isReservedZombieGroupKey,
				variantRoot -> resolveZombieVariantSpawnWeight(variantRoot, 0.0D)
			);
		}

		private static double resolveZombieVariantSpawnWeight(JsonObject variantRoot, double fallback) {
			return MobEntityManager.resolveVariantSpawnWeight(variantRoot, fallback);
		}

		private static boolean isReservedZombieGroupKey(String normalizedKey) {
			if (normalizedKey == null || normalizedKey.isBlank()) {
				return true;
			}
			return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW))
				|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WEAPON_DAMAGE));
		}

		private static JsonObject resolveZombieVariantRootByKey(JsonObject fileRoot, String variantKey) {
			return MobEntityManager.resolveVariantRootByKey(
				fileRoot,
				variantKey,
				ZombieBehavior::isReservedZombieGroupKey
			);
		}

		private static String readStoredZombieVariantKey(Zombie zombie) {
			if (zombie == null) {
				return "";
			}
			for (String tag : zombie.entityTags()) {
				if (tag == null || !tag.startsWith(ZOMBIE_VARIANT_TAG_PREFIX)) {
					continue;
				}
				String normalized = normalizeKey(tag.substring(ZOMBIE_VARIANT_TAG_PREFIX.length()));
				if (!normalized.isBlank()) {
					return normalized;
				}
			}
			return "";
		}

		private static void writeZombieVariantTag(Zombie zombie, String variantKey) {
			if (zombie == null || variantKey == null || variantKey.isBlank()) {
				return;
			}
			clearZombieVariantTag(zombie);
			zombie.addTag(ZOMBIE_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
		}

		private static void clearZombieVariantTag(Zombie zombie) {
			if (zombie == null) {
				return;
			}
			String existing = null;
			for (String tag : zombie.entityTags()) {
				if (tag != null && tag.startsWith(ZOMBIE_VARIANT_TAG_PREFIX)) {
					existing = tag;
					break;
				}
			}
			if (existing != null) {
				zombie.removeTag(existing);
			}
		}

		private static void applyZombieBehaviorToggles(Zombie zombie, JsonObject fileRoot, JsonObject variantRoot) {
			if (zombie == null) {
				return;
			}
			JsonObject behaviorRoot = MobEntityManager.readMobBehaviorRootForRuntime(variantRoot);
			boolean overrideBehavior = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIORS, true);

			if (overrideBehavior) {
				zombie.setCanPickUpLoot(MobEntityManager.readMobBehaviorBooleanForRuntime(variantRoot, MobConfigManager.FIELD_CAN_PICK_UP_LOOT, false));
			}
			if (overrideBehavior) {
				boolean callsReinforcements = readBoolean(behaviorRoot, MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, !zombie.isBaby());
				if (!callsReinforcements) {
					MobEntityManager.disableZombieReinforcementsForRuntime(zombie);
				}
			}
		}

		private static void applyWeaponDamagePolicy(Zombie zombie, JsonObject resolvedZombieRoot) {
			if (zombie == null || resolvedZombieRoot == null) {
				return;
			}
			boolean weaponDamageEnabled = readBoolean(resolvedZombieRoot, MobConfigManager.FIELD_WEAPON_DAMAGE, true);
			if (weaponDamageEnabled) {
				return;
			}
			stripHeldAttackDamageModifiers(zombie, EquipmentSlot.MAINHAND);
			stripHeldAttackDamageModifiers(zombie, EquipmentSlot.OFFHAND);
		}

		private static void stripHeldAttackDamageModifiers(Zombie zombie, EquipmentSlot slot) {
			if (zombie == null || slot == null) {
				return;
			}
			ItemStack stack = zombie.getItemBySlot(slot);
			if (stack == null || stack.isEmpty()) {
				return;
			}
			ItemStack normalized = stack.copy();
			normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
			zombie.setItemSlot(slot, normalized);
		}

		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			return element.getAsBoolean();
		}

		private static String readString(JsonObject root, String key, String fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				return fallback;
			}
			return element.getAsString();
		}

		private static JsonObject readObject(JsonObject root, String key) {
			if (root == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			JsonElement element = EntityConfigManager.resolveConfiguredElement(root, key);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		}

		private static String normalizeKey(String value) {
			return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		}

		private static String fileKeyForType(EntityType<?> type) {
			if (type == madoku.craft.mobs.mob.MadokuMobEntityTypes.ZOMBIE) {
				return MobConfigManager.FILE_ZOMBIE;
			}
			return "";
		}

		private static String resolveDefaultMobEquipmentReference(EntityType<?> type) {
			if (type == madoku.craft.mobs.mob.MadokuMobEntityTypes.ZOMBIE) {
				return "minecraft-equipment-zombie.json";
			}
			return "";
		}

		private record EquipmentLoadoutResult(
			boolean applied,
			String reason,
			String equipmentReference,
			double chancePercent,
			String armorSet,
			int equippedPieces,
			int requiredPieces
		) {}

	}

	public static final class ZombieVillagerBehavior {
		private ZombieVillagerBehavior() {
		}

		public static void applySpawnOverrides(
			ZombieVillager zombieVillager,
			ServerLevelAccessor world,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason
		) {
			if (zombieVillager == null || world == null || difficulty == null) {
				return;
			}
			String fileKey = MobConfigManager.FILE_ZOMBIE_VILLAGER;
			if (!MobEntityManager.isEnabled()) {
				applySpawnEquipmentLoadoutWhenMobSystemDisabled(zombieVillager, world.getRandom());
				return;
			}
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveZombieVillagerRootForRuntime(zombieVillager.getType());
			JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(fileConfigRoot);
			if (defaultGroup.entrySet().isEmpty()) {
				return;
			}

			boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			JsonObject variant = MobEntityManager.resolveNestedVariantForRuntime(
				defaultGroup,
				zombieVillager,
				overrideSpawnRules ? world.getRandom() : null,
				overrideSpawnRules
			);
			variant = mergeZombieVillagerFileSettings(fileRoot, variant);
			if (overrideSpawnRules) {
			}
			applyWeaponDamagePolicy(zombieVillager, variant);
			applyZombieVillagerBehaviorToggles(zombieVillager, fileConfigRoot, variant);
			if (overrideStats) {
				MobEntityManager.applyUniversalBaseStatsForRuntime(zombieVillager, variant);
			}
		}

		public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
			if (!(entity instanceof ZombieVillager zombieVillager) || entity.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = MobConfigManager.FILE_ZOMBIE_VILLAGER;
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}

			JsonObject fileConfigRoot = MobEntityManager.resolveMobFileConfigRootForRuntime(fileKey);
			JsonObject fileRoot = MobEntityManager.resolveZombieVillagerRootForRuntime(zombieVillager.getType());
			JsonObject defaultGroup = EntityConfigManager.resolvePrimaryVariantOnly(fileConfigRoot);
			JsonObject variant = MobEntityManager.resolveNestedVariantForRuntime(defaultGroup, zombieVillager, null, false);
			variant = mergeZombieVillagerFileSettings(fileRoot, variant);

			boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
			boolean modified = overrideStats && MobEntityManager.applyUniversalBaseStatsForRuntime(zombieVillager, variant);
			applyWeaponDamagePolicy(zombieVillager, variant);
			applyZombieVillagerBehaviorToggles(zombieVillager, fileConfigRoot, variant);
			return modified;
		}

		static boolean isCustomMobDropsEnabled(LivingEntity entity) {
			if (!(entity instanceof ZombieVillager zombieVillager) || entity.level().isClientSide() || !MobEntityManager.isEnabled()) {
				return false;
			}
			String fileKey = MobConfigManager.FILE_ZOMBIE_VILLAGER;
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return false;
			}
			JsonObject resolved = resolveActiveZombieVillagerRoot(zombieVillager);
			boolean enabled = readBoolean(resolved, MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
			return enabled;
		}

		static String resolveMobDropsConfigReference(LivingEntity entity) {
			if (!(entity instanceof ZombieVillager zombieVillager) || !MobEntityManager.isEnabled()) {
				return "";
			}
			String fileKey = MobConfigManager.FILE_ZOMBIE_VILLAGER;
			if (!MobEntityManager.isMobFileEnabledForRuntime(fileKey)) {
				return "";
			}
			JsonObject resolved = resolveActiveZombieVillagerRoot(zombieVillager);
			JsonObject componentsRoot = readObject(resolved, MobConfigManager.FIELD_MOB_COMPONENTS);
			String reference = readString(componentsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
			return reference;
		}

		private static String readString(JsonObject root, String key, String fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				return fallback;
			}
			return element.getAsString();
		}

		private static JsonObject resolveActiveZombieVillagerRoot(ZombieVillager zombieVillager) {
			if (zombieVillager == null) {
				return new JsonObject();
			}
			JsonObject fileRoot = MobEntityManager.resolveZombieVillagerRootForRuntime(zombieVillager.getType());
			JsonObject variant = MobEntityManager.resolveNestedVariantForRuntime(fileRoot, zombieVillager, null, false);
			return mergeZombieVillagerFileSettings(fileRoot, variant);
		}

		private static JsonObject mergeZombieVillagerFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
			JsonObject merged = variantRoot == null ? new JsonObject() : variantRoot.deepCopy();
			if (fileRoot == null || fileRoot.entrySet().isEmpty()) {
				return merged;
			}
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_CUSTOM_MOB_DROPS);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW);
			copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WEAPON_DAMAGE);
			return merged;
		}

		private static EquipmentLoadoutResult applySpawnEquipmentLoadoutWhenMobSystemDisabled(ZombieVillager zombieVillager, RandomSource random) {
			if (zombieVillager == null || random == null) {
				return new EquipmentLoadoutResult(false, "invalid_inputs", "", 0.0D, "none", 0, 0);
			}
			if (!EquipmentConfigManager.isCustomEntityEquipmentEnabled()) {
				return new EquipmentLoadoutResult(false, "custom_entity_equipment_disabled", "", 0.0D, "none", 0, 0);
			}
			String equipmentReference = resolveDefaultMobEquipmentReference();
			if (equipmentReference.isBlank()) {
				return new EquipmentLoadoutResult(false, "default_reference_blank", equipmentReference, 0.0D, "none", 0, 0);
			}
			return applyEquipmentSetLoadout(
				zombieVillager,
				equipmentReference,
				EquipmentConfigManager.customEntityEquipmentChanceWhenMobSystemDisabled(),
				random
			);
		}

		private static EquipmentLoadoutResult applyEquipmentSetLoadout(
			ZombieVillager zombieVillager,
			String equipmentReference,
			double chancePercent,
			RandomSource random
		) {
			if (zombieVillager == null || random == null) {
				return new EquipmentLoadoutResult(false, "invalid_inputs", equipmentReference, chancePercent, "none", 0, 0);
			}
			if (chancePercent <= 0.0D || random.nextDouble() * 100.0D >= chancePercent) {
				return new EquipmentLoadoutResult(false, "chance_failed", equipmentReference, chancePercent, "none", 0, 0);
			}
			EquipmentConfigManager.EquipmentProfile profile = EquipmentConfigManager.resolveProfile(equipmentReference, zombieVillager.getType());
			if (profile == null || !profile.enabled()) {
				return new EquipmentLoadoutResult(false, "profile_missing_or_disabled", equipmentReference, chancePercent, "none", 0, 0);
			}

			Map<EquipmentSlot, ItemStack> rolledBySlot = EquipmentConfigManager.rollEquipment(profile, random);
			if (rolledBySlot.isEmpty()) {
				return new EquipmentLoadoutResult(
					false,
					"slot_pool_empty",
					equipmentReference,
					chancePercent,
					"api",
					0,
					0
				);
			}
			MobEntityManager.clearArmorSlotsForRuntime(zombieVillager);
			for (Map.Entry<EquipmentSlot, ItemStack> entry : rolledBySlot.entrySet()) {
				zombieVillager.setItemSlot(entry.getKey(), entry.getValue());
			}
			return new EquipmentLoadoutResult(
				true,
				"applied",
				equipmentReference,
				chancePercent,
				"api",
				rolledBySlot.size(),
				rolledBySlot.size()
			);
		}

		private static void applyZombieVillagerBehaviorToggles(ZombieVillager zombieVillager, JsonObject fileRoot, JsonObject variantRoot) {
			if (zombieVillager == null) {
				return;
			}
			JsonObject behaviorRoot = MobEntityManager.readMobBehaviorRootForRuntime(variantRoot);

			boolean overrideBehavior = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIORS, true);

			if (overrideBehavior) {
				zombieVillager.setCanPickUpLoot(MobEntityManager.readMobBehaviorBooleanForRuntime(variantRoot, MobConfigManager.FIELD_CAN_PICK_UP_LOOT, false));
			}
			if (overrideBehavior) {
				boolean callsReinforcements = readBoolean(behaviorRoot, MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, !zombieVillager.isBaby());
				if (!callsReinforcements) {
					MobEntityManager.disableZombieReinforcementsForRuntime(zombieVillager);
				}
			}
		}

		private static void applyWeaponDamagePolicy(ZombieVillager zombieVillager, JsonObject resolvedRoot) {
			if (zombieVillager == null || resolvedRoot == null) {
				return;
			}
			boolean weaponDamageEnabled = readBoolean(resolvedRoot, MobConfigManager.FIELD_WEAPON_DAMAGE, true);
			if (weaponDamageEnabled) {
				return;
			}
			stripHeldAttackDamageModifiers(zombieVillager, EquipmentSlot.MAINHAND);
			stripHeldAttackDamageModifiers(zombieVillager, EquipmentSlot.OFFHAND);
		}

		private static void stripHeldAttackDamageModifiers(ZombieVillager zombieVillager, EquipmentSlot slot) {
			if (zombieVillager == null || slot == null) {
				return;
			}
			ItemStack stack = zombieVillager.getItemBySlot(slot);
			if (stack == null || stack.isEmpty()) {
				return;
			}
			ItemStack normalized = stack.copy();
			normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
			zombieVillager.setItemSlot(slot, normalized);
		}

		private static void copyIfMissing(JsonObject target, JsonObject source, String key) {
			if (target == null || source == null || key == null || key.isBlank()) {
				return;
			}
			if (!target.has(key) && source.has(key)) {
				target.add(key, source.get(key).deepCopy());
			}
		}

		private static JsonObject readObject(JsonObject root, String key) {
			if (root == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			JsonElement element = EntityConfigManager.resolveConfiguredElement(root, key);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		}

		private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
			if (root == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = root.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			return element.getAsBoolean();
		}

		private static String resolveDefaultMobEquipmentReference() {
			return "minecraft-equipment-zombie-villager.json";
		}

		private record EquipmentLoadoutResult(
			boolean applied,
			String reason,
			String equipmentReference,
			double chancePercent,
			String armorSet,
			int equippedPieces,
			int requiredPieces
		) {
		}

	}
}
