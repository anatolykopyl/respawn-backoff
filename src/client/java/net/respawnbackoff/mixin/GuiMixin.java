package net.respawnbackoff.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.respawnbackoff.client.RespawnBackoffClient;

/**
 * Draw the penalty screen after the in-game HUD (including chat). Vanilla then draws the pause
 * menu and toasts on top, so we no longer paint over {@link net.minecraft.client.gui.screens.PauseScreen} widgets.
 */
@Mixin(Gui.class)
public class GuiMixin {
	@Inject(method = "render", at = @At("RETURN"))
	private void respawn_backoff$afterInGameHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		RespawnBackoffClient.renderPenaltyOverlay(graphics);
	}
}
