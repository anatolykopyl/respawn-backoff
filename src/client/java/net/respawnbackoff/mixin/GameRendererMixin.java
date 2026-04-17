package net.respawnbackoff.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.respawnbackoff.client.RespawnBackoffClient;

/**
 * Draw the penalty blackout after the full frame (vanilla HUD, chat, and typical mod overlays like
 * Jade). Skip while a {@link net.minecraft.client.gui.screens.Screen} is open so pause menus stay unobstructed.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "render", at = @At("RETURN"))
	private void respawn_backoff$penaltyOverlayLastInFrame(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
		RespawnBackoffClient.renderPenaltyOverlayEndOfFrame();
	}
}
