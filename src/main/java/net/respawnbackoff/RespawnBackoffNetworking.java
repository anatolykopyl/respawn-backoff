package net.respawnbackoff;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class RespawnBackoffNetworking {
	private RespawnBackoffNetworking() {
	}

	public static void sendCooldown(ServerPlayer player, boolean active, long cooldownEndEpochMs) {
		ServerPlayNetworking.send(player, new CooldownSyncPayload(active, cooldownEndEpochMs));
	}
}
