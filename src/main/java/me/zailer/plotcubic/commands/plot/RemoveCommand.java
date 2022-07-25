package me.zailer.plotcubic.commands.plot;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.lucko.fabric.api.permissions.v0.Permissions;
import me.zailer.plotcubic.commands.CommandCategory;
import me.zailer.plotcubic.commands.CommandSuggestions;
import me.zailer.plotcubic.commands.PlotCommand;
import me.zailer.plotcubic.commands.SubcommandAbstract;
import me.zailer.plotcubic.database.UnitOfWork;
import me.zailer.plotcubic.plot.Plot;
import me.zailer.plotcubic.plot.PlotID;
import me.zailer.plotcubic.utils.MessageUtils;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;

import java.sql.SQLException;

public class RemoveCommand extends SubcommandAbstract {
    @Override
    public String[] getAlias() {
        return new String[]{"remove", "r"};
    }

    @Override
    public void apply(LiteralArgumentBuilder<ServerCommandSource> command, String alias) {
        command.then(
                CommandManager.literal(alias)
                        .requires(Permissions.require(this.getCommandPermission()))
                        .executes(this::executeValidUsages)
                        .then(CommandManager.argument("PLAYER", StringArgumentType.word())
                                .suggests(CommandSuggestions.REMOVE_PLAYER_SUGGESTION)
                                .executes(this::execute)
                        )
        );
    }

    @Override
    public int execute(CommandContext<ServerCommandSource> serverCommandSource) {
        try {
            ServerPlayerEntity player = serverCommandSource.getSource().getPlayer();
            String removedPlayer = serverCommandSource.getArgument("PLAYER", String.class);
            PlotID plotId = PlotID.ofBlockPos(player.getBlockX(), player.getBlockZ());

            if (plotId == null) {
                MessageUtils.sendChatMessage(player, "error.plotcubic.requires.plot");
                return 1;
            }

            if (!Plot.isOwner(player, plotId)) {
                MessageUtils.sendChatMessage(player, "error.plotcubic.requires.plot_owner");
                return 1;
            }

            Plot plot = Plot.getLoadedPlot(plotId);
            if (plot == null) {
                MessageUtils.sendChatMessage(player, "error.plotcubic.plot.not_loaded");
                return 1;
            }

            boolean denyRemoved = plot.removeDeny(removedPlayer);
            boolean trustRemoved = plot.removeTrust(removedPlayer);

            try (var uow = new UnitOfWork()) {
                try {
                    uow.beginTransaction();
                    if (uow.deniedRepository.exists(plotId, removedPlayer))
                        uow.deniedRepository.delete(plotId, removedPlayer);

                    uow.trustedRepository.delete(plotId, removedPlayer);
                    uow.commit();
                } catch (SQLException e) {
                    uow.rollback();
                    MessageUtils.sendChatMessage(player, "error.plotcubic.database.remove");
                    return 1;
                }
            } catch (Exception ignored) {
                MessageUtils.sendDatabaseConnectionError(player);
                return 1;
            }

            String translationKey = "text.plotcubic.plot.remove.";
            if (denyRemoved && trustRemoved)
                translationKey += "deny_and_trust";
            else if (denyRemoved)
                translationKey += "deny";
            else if (trustRemoved)
                translationKey += "trust";
            else
                translationKey += "nothing";

            MessageUtils.sendChatMessage(player, translationKey, removedPlayer);

        } catch (CommandSyntaxException ignored) {
        }
        return 1;
    }

    @Override
    protected String getHelpTranslationKey() {
        return "text.plotcubic.help.remove";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MODERATION;
    }

    @Override
    public MutableText getValidUsage() {
        //Command usage: /plot remove <player>

        String command = String.format("/%s %s <%s>", PlotCommand.COMMAND_ALIAS[0], this.getAlias()[0], "player");

        return MessageUtils.formatArgs("text.plotcubic.help.command_usage.generic", command);
    }
}
