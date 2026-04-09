package net.respawnbackoff.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.respawnbackoff.CooldownSyncPayload;

public class RespawnBackoffClient implements ClientModInitializer {
	private static volatile boolean overlayActive;
	private static volatile long cooldownEndEpochMs;

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearForDisconnect());

		ClientPlayNetworking.registerGlobalReceiver(CooldownSyncPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				overlayActive = payload.active();
				cooldownEndEpochMs = payload.cooldownEndEpochMs();
			});
		});

		HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
			if (!overlayActive) {
				return;
			}
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) {
				return;
			}
			long remainingMs = Math.max(0L, cooldownEndEpochMs - System.currentTimeMillis());
			renderOverlay(guiGraphics, client, remainingMs);
		});
	}

	private static void renderOverlay(GuiGraphics graphics, Minecraft client, long remainingMs) {
		int w = client.getWindow().getGuiScaledWidth();
		int h = client.getWindow().getGuiScaledHeight();
		graphics.fill(0, 0, w, h, 0xFF000000);

		int totalSeconds = (int) (remainingMs / 1000L);
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		String time = String.format("%02d:%02d", minutes, seconds);
		Component line = Component.translatable("respawn_backoff.hud.countdown", time);

		int textWidth = client.font.width(line);
		graphics.drawString(
			client.font,
			line,
			(w - textWidth) / 2,
			h / 2,
			0xFFFFFF,
			false
		);
	}

	public static void clearForDisconnect() {
		overlayActive = false;
		cooldownEndEpochMs = 0L;
	}
}
