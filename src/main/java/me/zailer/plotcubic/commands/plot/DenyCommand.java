package me.zailer.plotcubic.commands.plot;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.zailer.plotcubic.PlotCubic;
import me.zailer.plotcubic.commands.CommandCategory;
import me.zailer.plotcubic.commands.CommandSuggestions;
import me.zailer.plotcubic.commands.PlotCommand;
import me.zailer.plotcubic.commands.SubcommandAbstract;
import me.zailer.plotcubic.database.DatabaseManager;
import me.zailer.plotcubic.plot.DeniedPlayer;
import me.zailer.plotcubic.plot.Plot;
import me.zailer.plotcubic.plot.PlotID;
import me.zailer.plotcubic.plot.TrustedPlayer;
import me.zailer.plotcubic.utils.CommandColors;
import me.zailer.plotcubic.utils.MessageUtils;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Set;

public class DenyCommand extends SubcommandAbstract {
    @Override
    public String[] getAlias() {
        return new String[]{"deny"};
    }

    @Override
    public void apply(LiteralArgumentBuilder<ServerCommandSource> command, String alias) {
        command.then(
                CommandManager.literal(alias)
                        .executes(this::executeValidUsages)
                        .then(CommandManager.argument("PLAYER", StringArgumentType.word())
                                .suggests(CommandSuggestions.ONLINE_PLAYER_SUGGESTION)
                                .executes(this::execute)
                                .then(CommandManager.argument("REASON", StringArgumentType.greedyString())
                                        .executes(this::execute)
                                )
                        )
        );
    }

    @Override
    public int execute(CommandContext<ServerCommandSource> serverCommandSource) {
        try {
            ServerPlayerEntity player = serverCommandSource.getSource().getPlayer();
            String deniedUsername = serverCommandSource.getArgument("PLAYER", String.class);
            String reason = null;

            try {
                reason = serverCommandSource.getArgument("REASON", String.class);
            } catch (IllegalArgumentException ignored) {
            }

            if (deniedUsername.equalsIgnoreCase(player.getName().getString())) {
                MessageUtils.sendChatMessage(player, MessageUtils.getError("I don't think you should deny yourself").get());
                return 1;
            }

            PlotID plotId = PlotID.ofBlockPos(player.getBlockX(), player.getBlockZ());
            if (plotId == null) {
                MessageUtils.sendChatMessage(player, MessageUtils.getError("You are not in a plot").get());
                return 1;
            }

            if (!Plot.isOwner(player, plotId)) {
                MessageUtils.sendChatMessage(player, MessageUtils.getError("You are not the owner of this plot").get());
                return 1;
            }

            if (reason != null && reason.length() > 64) {
                MessageUtils.sendChatMessage(player, MessageUtils.getError("The deny reason must be a maximum of 64 characters").get());
                return 1;
            }

            if (!PlotCubic.getDatabaseManager().existPlayer(deniedUsername)) {
                MessageUtils.sendChatMessage(player, MessageUtils.getError("The player ")
                        .append(deniedUsername, CommandColors.HIGHLIGHT)
                        .append(" does not exist", CommandColors.ERROR).get());
                return 1;
            }
            DatabaseManager databaseManager = PlotCubic.getDatabaseManager();

            if (databaseManager.isDenied(plotId, deniedUsername)) {
                String removeCommand = String.format("/%s %s <%s>", PlotCommand.COMMAND_ALIAS[0], new RemoveCommand().getAlias()[0], deniedUsername);
                MessageUtils.sendChatMessage(player, MessageUtils.getError("The player already has deny, if you want to remove it you should use ")
                        .append(removeCommand, CommandColors.HIGHLIGHT).get());
                return 1;
            }

            boolean removeTrustedSuccessful = databaseManager.updateTrusted(new TrustedPlayer(deniedUsername, Set.of(), plotId));
            boolean deniedSuccessful = databaseManager.addDenied(plotId, deniedUsername, reason);
            DeniedPlayer deniedPlayer = new DeniedPlayer(deniedUsername, reason);

            if (removeTrustedSuccessful && deniedSuccessful) {
                MessageUtils.sendChatMessage(player, new MessageUtils(deniedUsername, CommandColors.HIGHLIGHT)
                        .append(" was successfully denied by ")
                        .append(deniedPlayer.reason(), CommandColors.HIGHLIGHT)
                        .get());
            } else {
                MessageUtils.sendChatMessage(player, MessageUtils.getError("An error occurred denying the player").get());
            }
            Plot plot = Plot.getLoadedPlot(plotId);

            if (plot != null)
                plot.addDenied(deniedPlayer);

        } catch (CommandSyntaxException ignored) {
        }
        return 1;
    }

    @Override
    public Text getValidUsage() {
        //Command usage: /plot deny <player>
        //Command usage: /plot deny <player> <reason>

        MessageUtils messageUtils = new MessageUtils().appendInfo("Command usage: ")
                .append(String.format("/%s %s <%s>\n", PlotCommand.COMMAND_ALIAS[0], this.getAlias()[0], "player"))
                .appendInfo("Command usage: ")
                .append(String.format("/%s %s <%s> <%s>", PlotCommand.COMMAND_ALIAS[0], this.getAlias()[0], "player", "reason"));
        return messageUtils.get();
    }

    @Override
    protected String getHelpDetails() {
        return "Ban a player from entering your plot";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MODERATION;
    }
}
