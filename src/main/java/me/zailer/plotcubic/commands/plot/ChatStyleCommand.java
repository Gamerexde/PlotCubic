package me.zailer.plotcubic.commands.plot;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.zailer.plotcubic.commands.CommandCategory;
import me.zailer.plotcubic.commands.SubcommandAbstract;
import me.zailer.plotcubic.gui.ChatStylesGui;
import me.zailer.plotcubic.plot.Plot;
import me.zailer.plotcubic.plot.PlotID;
import me.zailer.plotcubic.utils.MessageUtils;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class ChatStyleCommand extends SubcommandAbstract {
    @Override
    public String[] getAlias() {
        return new String[]{"chatstyle"};
    }

    @Override
    public void apply(LiteralArgumentBuilder<ServerCommandSource> command, String alias) {
        command.then(
                CommandManager.literal(alias)
                        .executes(this::execute)
        );
    }

    @Override
    public int execute(CommandContext<ServerCommandSource> serverCommandSource) {
        try {
            ServerPlayerEntity player = serverCommandSource.getSource().getPlayer();
            PlotID plotId = PlotID.ofBlockPos(player.getBlockX(), player.getBlockZ());

            if (plotId == null) {
                MessageUtils.sendChatMessage(player, MessageUtils.getError("You are not in a plot").get());
                return 1;
            }
            Plot plot = Plot.getPlot(plotId);

            if (plot == null || !plot.isOwner(player)) {
                MessageUtils.sendChatMessage(player, MessageUtils.getError("You are not the owner of this plot").get());
                return 1;
            }

            this.execute(player, plot);
        } catch (CommandSyntaxException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }

    public void execute(ServerPlayerEntity player, Plot plot) {
        new ChatStylesGui().open(player, plot);
    }

    @Override
    protected String getHelpDetails() {
        return "Change the style of messages in plot chat";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.COSMETIC;
    }
}
