package net.respawnbackoff;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public record DeathLockSnapshot(
	ResourceKey<Level> dimension,
	double x,
	double y,
	double z,
	float yaw,
	float pitch
) {
	public static final Codec<DeathLockSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(DeathLockSnapshot::dimension),
		Codec.DOUBLE.fieldOf("x").forGetter(DeathLockSnapshot::x),
		Codec.DOUBLE.fieldOf("y").forGetter(DeathLockSnapshot::y),
		Codec.DOUBLE.fieldOf("z").forGetter(DeathLockSnapshot::z),
		Codec.FLOAT.fieldOf("yaw").forGetter(DeathLockSnapshot::yaw),
		Codec.FLOAT.fieldOf("pitch").forGetter(DeathLockSnapshot::pitch)
	).apply(instance, DeathLockSnapshot::new));

	public static DeathLockSnapshot from(ServerPlayer player) {
		return new DeathLockSnapshot(
			player.level().dimension(),
			player.getX(),
			player.getY(),
			player.getZ(),
			player.getYRot(),
			player.getXRot()
		);
	}
}
