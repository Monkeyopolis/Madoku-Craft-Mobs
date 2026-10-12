package madoku.craft.java.mob;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Synchronizes whether the server has enabled mob indicators for the client. */
public record MobIndicatorSettingsPayloadManager(boolean enabled) implements CustomPacketPayload {
	public static final Type<MobIndicatorSettingsPayloadManager> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("madoku-craft", "mob_indicator_settings"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MobIndicatorSettingsPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.BOOL,
			MobIndicatorSettingsPayloadManager::enabled,
			MobIndicatorSettingsPayloadManager::new
		);

	@Override
	public Type<MobIndicatorSettingsPayloadManager> type() {
		return TYPE;
	}
}
