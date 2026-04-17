package net.respawnbackoff;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;

public class RespawnBackoffMod implements ModInitializer {
	public static final String MOD_ID = "respawn_backoff";

	/** Calendar day for resetting the death-chain exponent: midnight MSK (UTC+3). */
	private static final ZoneId DAILY_RESET_ZONE = ZoneId.of("Europe/Moscow");

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
			if (data.isCooldownFinished(now)) {
				clearCooldownAndRestore(player, data);
				return;
			}
			if (data.hasActiveCooldown(now)) {
				if (!player.isSpectator()) {
					player.setGameMode(GameType.SPECTATOR);
				}
				data.deathLock().ifPresent(lock -> teleportToDeathLock(player, lock));
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

	private static long currentResetEpochDay() {
		return LocalDate.now(DAILY_RESET_ZONE).toEpochDay();
	}

	static void onPlayerDeath(ServerPlayer player) {
		long today = currentResetEpochDay();
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

		RespawnBackoffData next = new RespawnBackoffData(
			nextExponent,
			today,
			pendingMs,
			0L,
			Optional.of(DeathLockSnapshot.from(player))
		);
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
			end,
			data.deathLock()
		);
		player.setAttached(RESPAWN_BACKOFF, next);
		player.setGameMode(GameType.SPECTATOR);
		data.deathLock().ifPresent(lock -> teleportToDeathLock(player, lock));
		RespawnBackoffNetworking.sendCooldown(player, true, end);
	}

	private static void tickPlayer(ServerPlayer player, long nowMs) {
		RespawnBackoffData data = player.getAttachedOrElse(RESPAWN_BACKOFF, RespawnBackoffData.DEFAULT);
		if (data.isCooldownFinished(nowMs)) {
			clearCooldownAndRestore(player, data);
			return;
		}
		if (data.hasActiveCooldown(nowMs)) {
			data.deathLock().ifPresent(lock -> enforceDeathLock(player, lock));
		}
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
			0L,
			Optional.empty()
		);
		player.setAttached(RESPAWN_BACKOFF, cleared);
		player.setCamera(player);
		teleportToRespawnPoint(player);
		if (player.isSpectator()) {
			player.setGameMode(GameType.SURVIVAL);
		}
		RespawnBackoffNetworking.sendCooldown(player, false, 0L);
	}

	/**
	 * Next death uses the minimum wait (1 minute): exponent 0 and today's calendar day (MSK) recorded.
	 * Does not end an active on-screen countdown; use {@link #skipCooldown} for that.
	 */
	public static void resetBackoffChain(ServerPlayer player) {
		long today = currentResetEpochDay();
		RespawnBackoffData data = player.getAttachedOrElse(RESPAWN_BACKOFF, RespawnBackoffData.DEFAULT);
		RespawnBackoffData next = new RespawnBackoffData(
			0,
			today,
			0L,
			data.cooldownEndEpochMs(),
			data.deathLock()
		);
		player.setAttached(RESPAWN_BACKOFF, next);
	}

	public static boolean isPenaltyActive(ServerPlayer player, long nowMs) {
		return player.getAttachedOrElse(RESPAWN_BACKOFF, RespawnBackoffData.DEFAULT).hasActiveCooldown(nowMs);
	}

	private static final double DEATH_LOCK_EPSILON_SQ = 1.0e-6;

	private static void teleportToDeathLock(ServerPlayer player, DeathLockSnapshot lock) {
		MinecraftServer server = player.server;
		ServerLevel level = server.getLevel(lock.dimension());
		if (level == null) {
			level = server.overworld();
			BlockPos spawn = level.getSharedSpawnPos();
			teleportAbsolute(player, level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, level.getSharedSpawnAngle(), 0.0f);
			return;
		}
		teleportAbsolute(player, level, lock.x(), lock.y(), lock.z(), lock.yaw(), lock.pitch());
	}

	private static void teleportAbsolute(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
		Set<RelativeMovement> relatives = Collections.emptySet();
		player.teleportTo(level, x, y, z, relatives, yaw, pitch);
	}

	private static void enforceDeathLock(ServerPlayer player, DeathLockSnapshot lock) {
		if (!player.level().dimension().equals(lock.dimension())) {
			teleportToDeathLock(player, lock);
			return;
		}
		double dx = player.getX() - lock.x();
		double dy = player.getY() - lock.y();
		double dz = player.getZ() - lock.z();
		if (dx * dx + dy * dy + dz * dz > DEATH_LOCK_EPSILON_SQ) {
			teleportToDeathLock(player, lock);
		}
	}

	private static void teleportToRespawnPoint(ServerPlayer player) {
		MinecraftServer server = player.server;
		ServerLevel respawnLevel = server.getLevel(player.getRespawnDimension());
		if (respawnLevel == null) {
			respawnLevel = server.overworld();
		}
		BlockPos bed = player.getRespawnPosition();
		float yaw = player.getRespawnAngle();
		if (bed != null) {
			teleportAbsolute(player, respawnLevel, bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, yaw, 0.0f);
			return;
		}
		BlockPos spawn = respawnLevel.getSharedSpawnPos();
		teleportAbsolute(
			player,
			respawnLevel,
			spawn.getX() + 0.5,
			spawn.getY(),
			spawn.getZ() + 0.5,
			respawnLevel.getSharedSpawnAngle(),
			0.0f
		);
	}
}
