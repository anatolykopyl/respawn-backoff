package net.respawnbackoff;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record RespawnBackoffData(
	int exponent,
	long lastEpochDay,
	long pendingDurationMs,
	long cooldownEndEpochMs
) {
	public static final RespawnBackoffData DEFAULT = new RespawnBackoffData(0, 0L, 0L, 0L);

	public static final Codec<RespawnBackoffData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.fieldOf("exponent").forGetter(RespawnBackoffData::exponent),
		Codec.LONG.fieldOf("last_epoch_day").forGetter(RespawnBackoffData::lastEpochDay),
		Codec.LONG.fieldOf("pending_duration_ms").forGetter(RespawnBackoffData::pendingDurationMs),
		Codec.LONG.fieldOf("cooldown_end_ms").forGetter(RespawnBackoffData::cooldownEndEpochMs)
	).apply(instance, RespawnBackoffData::new));

	public boolean hasActiveCooldown(long nowMs) {
		return cooldownEndEpochMs > nowMs;
	}
}
