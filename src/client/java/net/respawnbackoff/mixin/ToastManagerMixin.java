package net.respawnbackoff.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.respawnbackoff.client.RespawnBackoffClient;

/**
 * Intermediary name for {@code net.minecraft.client.toast.ToastManager} (Mojang mappings hide this on compile classpath).
 */
@Mixin(targets = "net.minecraft.class_374")
public class ToastManagerMixin {
	@Inject(method = "method_1996", at = @At("RETURN"))
	private void respawn_backoff$afterToasts(GuiGraphics graphics, CallbackInfo ci) {
		RespawnBackoffClient.renderPenaltyOverlay(graphics);
	}
}
