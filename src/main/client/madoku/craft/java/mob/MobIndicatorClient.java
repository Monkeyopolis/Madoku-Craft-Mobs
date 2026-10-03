package madoku.craft.java.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Renders the server-authoritative health, armor, and vulnerability rows. */
public final class MobIndicatorClient {
	private static final Identifier MADOKU_PET_ENTITY_ID =
		Identifier.fromNamespaceAndPath("madoku-craft", "pet");
	private static final Map<UUID, Float> VULNERABILITY_BY_ENTITY = new ConcurrentHashMap<>();
	private static final double MAX_INDICATOR_DISTANCE = 24.0D;
	private static final Identifier INDICATOR_FONT_ID = Identifier.fromNamespaceAndPath("madoku-craft", "mob_indicator");
	private static final FontDescription INDICATOR_FONT = new FontDescription.Resource(INDICATOR_FONT_ID);
	private static final Identifier INDICATOR_SPACING_FONT_ID = Identifier.fromNamespaceAndPath("madoku-craft", "mob_indicator_spacing");
	private static final FontDescription INDICATOR_SPACING_FONT = new FontDescription.Resource(INDICATOR_SPACING_FONT_ID);
	private static final Identifier HEALTH_ICON = Identifier.fromNamespaceAndPath("madoku-craft", "icons/health.png");
	private static final Identifier ARMOR_ICON = Identifier.fromNamespaceAndPath("madoku-craft", "icons/armor.png");
	private static final Identifier VULNERABILITY_ICON = Identifier.fromNamespaceAndPath("madoku-craft", "icons/vulnerability.png");
	private static final char HEALTH_ICON_CHARACTER = '\uE000';
	private static final char ARMOR_ICON_CHARACTER = '\uE001';
	private static final char VULNERABILITY_ICON_CHARACTER = '\uE002';
	private static final char INDICATOR_SPACING_CHARACTER = '\uE003';
	private static boolean initialized;

	private MobIndicatorClient() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ClientPlayNetworking.registerGlobalReceiver(MobIndicatorPayloadManager.TYPE, (payload, context) -> {
			if (payload.entityUuid() == null) {
				return;
			}
			if (payload.vulnerabilityPercent() <= 0.0F) {
				VULNERABILITY_BY_ENTITY.remove(payload.entityUuid());
			} else {
				VULNERABILITY_BY_ENTITY.put(payload.entityUuid(), payload.vulnerabilityPercent());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			VULNERABILITY_BY_ENTITY.clear();
		});
	}

	public static boolean isManagedMob(LivingEntity entity) {
		return entity instanceof Mob
			&& !(entity instanceof Player)
			&& !MADOKU_PET_ENTITY_ID.equals(EntityType.getKey(entity.getType()));
	}

	public static void render(
		MobIndicatorRenderStateAccess state,
		LivingEntityRenderState renderState,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		CameraRenderState cameraState
	) {
		if (state == null || renderState == null || poseStack == null || collector == null || cameraState == null) {
			return;
		}

		boolean managed = state.madokuCraftMobIndicator$isManaged();
		UUID entityUuid = state.madokuCraftMobIndicator$getEntityUuid();
		boolean inRange = renderState.distanceToCameraSq <= MAX_INDICATOR_DISTANCE * MAX_INDICATOR_DISTANCE;
		boolean inPlayerPov = inRange && isInPlayerPov(renderState, cameraState);
		boolean hasLineOfSight = inPlayerPov && hasLineOfSight(renderState, cameraState);
		if (!managed || entityUuid == null || renderState.isInvisibleToPlayer || !inRange || !inPlayerPov || !hasLineOfSight) {
			return;
		}

		float health = Math.max(0.0F, state.madokuCraftMobIndicator$getHealth());
		float maxHealth = Math.max(0.0F, state.madokuCraftMobIndicator$getMaxHealth());
		if (maxHealth <= 0.0F) {
			return;
		}

		float armor = Math.max(0.0F, state.madokuCraftMobIndicator$getArmor());
		float vulnerability = Math.max(0.0F,
			VULNERABILITY_BY_ENTITY.getOrDefault(state.madokuCraftMobIndicator$getEntityUuid(), 0.0F));
		int indicatorLight = LightCoordsUtil.lightCoordsWithEmission(renderState.lightCoords, 2);
		List<Row> rows = new ArrayList<>();
		rows.add(new Row(HEALTH_ICON_CHARACTER, HEALTH_ICON, formatHealth(health), 0xFFFF5555));
		if (armor > 0.01F) rows.add(new Row(ARMOR_ICON_CHARACTER, ARMOR_ICON, formatNumber(armor), 0xFFAAAAAA));
		if (vulnerability > 0.01F) rows.add(new Row(VULNERABILITY_ICON_CHARACTER, VULNERABILITY_ICON, formatPercent(vulnerability), 0xFFFFAA00));

		for (int index = 0; index < rows.size(); index++) {
			Row row = rows.get(index);
			int displayIndex = rows.size() - 1 - index;
			collector.submitNameTag(
				poseStack,
				new Vec3(0.0D, renderState.boundingBoxHeight + (displayIndex * 0.25D), 0.0D),
				0,
				row.component(),
				true,
				indicatorLight,
				cameraState
			);
		}
	}

	private static String formatHealth(float value) {
		return formatNumber(value);
	}

	private static String formatPercent(float value) {
		return formatNumber(value) + "%";
	}

	private static String formatNumber(float value) {
		String formatted = String.format(Locale.ROOT, "%.1f", value);
		if (formatted.endsWith(".0")) return formatted.substring(0, formatted.length() - 2);
		return formatted;
	}

	private static boolean isInPlayerPov(LivingEntityRenderState renderState, CameraRenderState cameraState) {
		if (cameraState.cullFrustum == null) return true;

		double halfWidth = Math.max(renderState.boundingBoxWidth * 0.5D, 0.25D);
		AABB entityBounds = new AABB(
			renderState.x - halfWidth,
			renderState.y,
			renderState.z - halfWidth,
			renderState.x + halfWidth,
			renderState.y + Math.max(renderState.boundingBoxHeight, 0.5F),
			renderState.z + halfWidth
		);
		return cameraState.cullFrustum.isVisible(entityBounds);
	}

	private static boolean hasLineOfSight(LivingEntityRenderState renderState, CameraRenderState cameraState) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || cameraState.pos == null) return true;

		Vec3 target = new Vec3(
			renderState.x,
			renderState.y + Math.max(renderState.boundingBoxHeight * 0.5D, 0.5D),
			renderState.z
		);
		BlockHitResult hitResult = minecraft.level.clip(new ClipContext(
			cameraState.pos,
			target,
			ClipContext.Block.VISUAL,
			ClipContext.Fluid.NONE,
			CollisionContext.empty()
		));
		return hitResult.getType() == HitResult.Type.MISS;
	}

	private record Row(char iconCharacter, Identifier icon, String text, int color) {
		private Component component() {
			return Component.literal(String.valueOf(iconCharacter))
				.withStyle(style -> style.withFont(INDICATOR_FONT))
				.append(Component.literal(String.valueOf(INDICATOR_SPACING_CHARACTER))
					.withStyle(style -> style.withFont(INDICATOR_SPACING_FONT)))
				.append(Component.literal(text).withStyle(style -> style
					.withFont(FontDescription.DEFAULT)
					.withColor(color & 0xFFFFFF)));
		}
	}
}
