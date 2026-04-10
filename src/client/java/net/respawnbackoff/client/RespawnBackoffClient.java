package net.respawnbackoff.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.respawnbackoff.CooldownSyncPayload;

public class RespawnBackoffClient implements ClientModInitializer {
	private static volatile boolean overlayActive;
	private static volatile long cooldownEndEpochMs;
	private static volatile long nextDeathWaitMinutes;

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearForDisconnect());

		ClientPlayNetworking.registerGlobalReceiver(CooldownSyncPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				overlayActive = payload.active();
				cooldownEndEpochMs = payload.cooldownEndEpochMs();
				nextDeathWaitMinutes = payload.nextDeathWaitMinutes();
			});
		});

	}

	/** Invoked from a mixin at the end of {@link net.minecraft.client.gui.Gui#render} (HUD + chat). Pause UI and toasts draw later. */
	public static void renderPenaltyOverlay(GuiGraphics graphics) {
		if (!overlayActive) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		long remainingMs = Math.max(0L, cooldownEndEpochMs - System.currentTimeMillis());
		int w = client.getWindow().getGuiScaledWidth();
		int h = client.getWindow().getGuiScaledHeight();
		graphics.fill(0, 0, w, h, 0xFF000000);

		int totalSeconds = (int) (remainingMs / 1000L);
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		String time = String.format("%02d:%02d", minutes, seconds);
		Component line = Component.translatable("respawn_backoff.hud.countdown", time);
		int lineHeight = client.font.lineHeight;
		int centerY = h / 2;
		int mainY = centerY - lineHeight - 2;
		int textWidth = client.font.width(line);
		graphics.drawString(client.font, line, (w - textWidth) / 2, mainY, 0xFFFFFF, false);

		long nextMin = nextDeathWaitMinutes;
		if (nextMin > 0L) {
			Component sub = Component.translatable("respawn_backoff.hud.next_death", nextMin);
			int subW = client.font.width(sub);
			graphics.drawString(client.font, sub, (w - subW) / 2, centerY + 4, 0xA0A0A0, false);
		}
	}

	public static void clearForDisconnect() {
		overlayActive = false;
		cooldownEndEpochMs = 0L;
		nextDeathWaitMinutes = 0L;
	}
}
