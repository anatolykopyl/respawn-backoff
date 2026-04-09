package net.respawnbackoff;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CooldownSyncPayload(boolean active, long cooldownEndEpochMs) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CooldownSyncPayload> TYPE = new CustomPacketPayload.Type<>(
		ResourceLocation.fromNamespaceAndPath(RespawnBackoffMod.MOD_ID, "cooldown_sync")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, CooldownSyncPayload> STREAM_CODEC = StreamCodec.of(
		(RegistryFriendlyByteBuf buf, CooldownSyncPayload payload) -> {
			buf.writeBoolean(payload.active());
			buf.writeLong(payload.cooldownEndEpochMs());
		},
		buf -> new CooldownSyncPayload(buf.readBoolean(), buf.readLong())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
