package net.respawnbackoff;

import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RespawnBackoffCommands {
	private RespawnBackoffCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register(RespawnBackoffCommands::registerLiteral);
	}

	private static void registerLiteral(
		CommandDispatcher<CommandSourceStack> dispatcher,
		CommandBuildContext registryAccess,
		Commands.CommandSelection environment
	) {
		dispatcher.register(
			Commands.literal("respawnbackoff")
				.requires(source -> source.hasPermission(2))
				.then(
					Commands.literal("reset")
						.executes(ctx -> resetBackoff(ctx.getSource().getPlayerOrException(), ctx.getSource()))
						.then(
							Commands.argument("targets", EntityArgument.players())
								.executes(ctx -> resetBackoff(EntityArgument.getPlayers(ctx, "targets"), ctx.getSource()))
						)
				)
				.then(
					Commands.literal("skip")
						.executes(ctx -> skipWait(ctx.getSource().getPlayerOrException(), ctx.getSource()))
						.then(
							Commands.argument("targets", EntityArgument.players())
								.executes(ctx -> skipWait(EntityArgument.getPlayers(ctx, "targets"), ctx.getSource()))
						)
				)
		);
	}

	private static int resetBackoff(ServerPlayer target, CommandSourceStack source) throws CommandSyntaxException {
		RespawnBackoffMod.resetBackoffChain(target);
		source.sendSuccess(
			() -> Component.translatable("respawn_backoff.command.reset.single", target.getDisplayName()),
			true
		);
		return 1;
	}

	private static int resetBackoff(Collection<ServerPlayer> targets, CommandSourceStack source) {
		int n = 0;
		for (ServerPlayer target : targets) {
			RespawnBackoffMod.resetBackoffChain(target);
			n++;
		}
		if (n == 1) {
			source.sendSuccess(
				() -> Component.translatable("respawn_backoff.command.reset.single", targets.iterator().next().getDisplayName()),
				true
			);
		} else {
			int count = n;
			source.sendSuccess(
				() -> Component.translatable("respawn_backoff.command.reset.multiple", count),
				true
			);
		}
		return n;
	}

	private static int skipWait(ServerPlayer target, CommandSourceStack source) throws CommandSyntaxException {
		RespawnBackoffMod.skipCooldown(target);
		source.sendSuccess(
			() -> Component.translatable("respawn_backoff.command.skip.single", target.getDisplayName()),
			true
		);
		return 1;
	}

	private static int skipWait(Collection<ServerPlayer> targets, CommandSourceStack source) {
		int n = 0;
		for (ServerPlayer target : targets) {
			RespawnBackoffMod.skipCooldown(target);
			n++;
		}
		if (n == 1) {
			source.sendSuccess(
				() -> Component.translatable("respawn_backoff.command.skip.single", targets.iterator().next().getDisplayName()),
				true
			);
		} else {
			int count = n;
			source.sendSuccess(
				() -> Component.translatable("respawn_backoff.command.skip.multiple", count),
				true
			);
		}
		return n;
	}
}
