package net.respawnbackoff;

import java.time.LocalDate;
import java.time.ZoneOffset;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class RespawnBackoffMod implements ModInitializer {
	public static final String MOD_ID = "respawn_backoff";

	public static final AttachmentType<RespawnBackoffData> RESPAWN_BACKOFF = AttachmentRegistry.create(
		ResourceLocation.fromNamespaceAndPath(MOD_ID, "state"),
		builder -> builder
			.persistent(RespawnBackoffData.CODEC)
			.copyOnDeath()
			.initializer(() -> RespawnBackoffData.DEFAULT)
	);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(CooldownSyncPayload.TYPE, CooldownSyncPayload.STREAM_CODEC);
		RespawnBackoffCommands.register();

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			onPlayerDeath(player);
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				onAfterDeathRespawn(newPlayer);
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			long now = System.currentTimeMillis();
			RespawnBackoffData data = player.getAttachedOrElse(RESPAWN_BACKOFF, RespawnBackoffData.DEFAULT);
			if (data.hasActiveCooldown(now)) {
				if (!player.isSpectator()) {
					player.setGameMode(GameType.SPECTATOR);
				}
				RespawnBackoffNetworking.sendCooldown(player, true, data.cooldownEndEpochMs());
			} else {
				RespawnBackoffNetworking.sendCooldown(player, false, 0L);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = System.currentTimeMillis();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickPlayer(player, now);
			}
		});
	}

	private static long currentUtcEpochDay() {
		return LocalDate.now(ZoneOffset.UTC).toEpochDay();
	}

	static void onPlayerDeath(ServerPlayer player) {
		long today = currentUtcEpochDay();
		RespawnBackoffData data = player.getAttachedOrElse(RESPAWN_BACKOFF, RespawnBackoffData.DEFAULT);

		int exponent = data.exponent();
		long lastDay = data.lastEpochDay();
		if (lastDay != today) {
			exponent = 0;
		}

		int cappedExp = Math.min(exponent, 6);
		long waitMinutes = Math.min(1L << cappedExp, 64L);
		long pendingMs = waitMinutes * 60_000L;
		int nextExponent = Math.min(exponent + 1, 6);

		RespawnBackoffData next = new RespawnBackoffData(nextExponent, today, pendingMs, 0L);
		player.setAttached(RESPAWN_BACKOFF, next);
	}

	private static void onAfterDeathRespawn(ServerPlayer player) {
		RespawnBackoffData data = player.getAttachedOrElse(RESPAWN_BACKOFF, RespawnBackoffData.DEFAULT);
		long pending = data.pendingDurationMs();
		if (pending <= 0L) {
			return;
		}

		long end = System.currentTimeMillis() + pending;
		RespawnBackoffData next = new RespawnBackoffData(
			data.exponent(),
			data.lastEpochDay(),
			0L,
			end
		);
		player.setAttached(RESPAWN_BACKOFF, next);
		player.setGameMode(GameType.SPECTATOR);
		RespawnBackoffNetworking.sendCooldown(player, true, end);
	}

	private static void tickPlayer(ServerPlayer player, long nowMs) {
		RespawnBackoffData data = player.getAttachedOrElse(RESPAWN_BACKOFF, RespawnBackoffData.DEFAULT);
		if (!data.hasActiveCooldown(nowMs)) {
			return;
		}

		if (nowMs < data.cooldownEndEpochMs()) {
			return;
		}

		clearCooldownAndRestore(player, data);
	}

	/**
	 * Clears active respawn wait and pending pre-respawn duration; restores survival if needed.
	 * Does not change the death-count chain (exponent / last day).
	 */
	public static void skipCooldown(ServerPlayer player) {
		RespawnBackoffData data = player.getAttachedOrElse(RESPAWN_BACKOFF, RespawnBackoffData.DEFAULT);
		clearCooldownAndRestore(player, data);
	}

	private static void clearCooldownAndRestore(ServerPlayer player, RespawnBackoffData data) {
		RespawnBackoffData cleared = new RespawnBackoffData(
			data.exponent(),
			data.lastEpochDay(),
			0L,
			0L
		);
		player.setAttached(RESPAWN_BACKOFF, cleared);
		if (player.isSpectator()) {
			player.setGameMode(GameType.SURVIVAL);
		}
		RespawnBackoffNetworking.sendCooldown(player, false, 0L);
	}

	/**
	 * Next death uses the minimum wait (1 minute): exponent 0 and today's UTC day recorded.
	 * Does not end an active on-screen countdown; use {@link #skipCooldown} for that.
	 */
	public static void resetBackoffChain(ServerPlayer player) {
		long today = currentUtcEpochDay();
		RespawnBackoffData data = player.getAttachedOrElse(RESPAWN_BACKOFF, RespawnBackoffData.DEFAULT);
		RespawnBackoffData next = new RespawnBackoffData(
			0,
			today,
			0L,
			data.cooldownEndEpochMs()
		);
		player.setAttached(RESPAWN_BACKOFF, next);
	}

	public static boolean isPenaltyActive(ServerPlayer player, long nowMs) {
		return player.getAttachedOrElse(RESPAWN_BACKOFF, RespawnBackoffData.DEFAULT).hasActiveCooldown(nowMs);
	}
}
