package net.respawnbackoff.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.respawnbackoff.RespawnBackoffMod;

@Mixin(ServerPlayer.class)
public class ServerPlayerGameModeMixin {
	@Inject(method = "setGameMode", at = @At("HEAD"), cancellable = true)
	private void respawn_backoff$enforceSpectatorDuringPenalty(GameType gameType, CallbackInfoReturnable<Boolean> cir) {
		if (gameType == GameType.SPECTATOR) {
			return;
		}
		ServerPlayer self = (ServerPlayer) (Object) this;
		long now = System.currentTimeMillis();
		if (RespawnBackoffMod.isPenaltyActive(self, now)) {
			cir.setReturnValue(false);
		}
	}
}
