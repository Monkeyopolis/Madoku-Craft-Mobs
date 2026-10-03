package madoku.craft.java.mob;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Synchronizes the active vulnerability indicator state for a managed mob. */
public record MobIndicatorPayloadManager(UUID entityUuid, float vulnerabilityPercent) implements CustomPacketPayload {
	public static final Type<MobIndicatorPayloadManager> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("madoku-craft", "mob_indicator"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MobIndicatorPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.LONG,
			payload -> payload.entityUuid().getMostSignificantBits(),
			ByteBufCodecs.LONG,
			payload -> payload.entityUuid().getLeastSignificantBits(),
			ByteBufCodecs.FLOAT,
			MobIndicatorPayloadManager::vulnerabilityPercent,
			(mostSignificantBits, leastSignificantBits, vulnerabilityPercent) ->
				new MobIndicatorPayloadManager(new UUID(mostSignificantBits, leastSignificantBits), vulnerabilityPercent)
		);

	@Override
	public Type<MobIndicatorPayloadManager> type() {
		return TYPE;
	}
}
