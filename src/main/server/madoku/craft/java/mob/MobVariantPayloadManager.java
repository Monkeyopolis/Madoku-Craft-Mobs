package madoku.craft.java.mob;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Synchronizes the configured appearance variant for a tracked mob. */
public record MobVariantPayloadManager(UUID entityUuid, String variantKey) implements CustomPacketPayload {
	public static final Type<MobVariantPayloadManager> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("madoku-craft", "mob_variant"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MobVariantPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.LONG,
			payload -> payload.entityUuid().getMostSignificantBits(),
			ByteBufCodecs.LONG,
			payload -> payload.entityUuid().getLeastSignificantBits(),
			ByteBufCodecs.stringUtf8(64),
			MobVariantPayloadManager::variantKey,
			(mostSignificantBits, leastSignificantBits, variantKey) ->
				new MobVariantPayloadManager(new UUID(mostSignificantBits, leastSignificantBits), variantKey)
		);

	@Override
	public Type<MobVariantPayloadManager> type() {
		return TYPE;
	}
}
