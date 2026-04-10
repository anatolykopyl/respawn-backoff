package net.respawnbackoff;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record RespawnBackoffData(
	int exponent,
	long lastEpochDay,
	long pendingDurationMs,
	long cooldownEndEpochMs,
	Optional<DeathLockSnapshot> deathLock
) {
	public static final RespawnBackoffData DEFAULT = new RespawnBackoffData(0, 0L, 0L, 0L, Optional.empty());

	public static final Codec<RespawnBackoffData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.fieldOf("exponent").forGetter(RespawnBackoffData::exponent),
		Codec.LONG.fieldOf("last_epoch_day").forGetter(RespawnBackoffData::lastEpochDay),
		Codec.LONG.fieldOf("pending_duration_ms").forGetter(RespawnBackoffData::pendingDurationMs),
		Codec.LONG.fieldOf("cooldown_end_ms").forGetter(RespawnBackoffData::cooldownEndEpochMs),
		DeathLockSnapshot.CODEC.optionalFieldOf("death_lock").forGetter(RespawnBackoffData::deathLock)
	).apply(instance, RespawnBackoffData::new));

	public boolean hasActiveCooldown(long nowMs) {
		return cooldownEndEpochMs > nowMs;
	}

	/** True once wall-clock time has reached the scheduled end (timer at zero or past). */
	public boolean isCooldownFinished(long nowMs) {
		return cooldownEndEpochMs > 0L && nowMs >= cooldownEndEpochMs;
	}

	/**
	 * Spectator wait length in whole minutes if the player dies again now (same formula as the next death).
	 */
	public long nextDeathWaitMinutes() {
		int cappedExp = Math.min(exponent, 6);
		return Math.min(1L << cappedExp, 64L);
	}
}
