package jp.apple.aris.ctc.command;

import jp.apple.aris.ctc.config.LineManager;
import jp.apple.aris.ctc.network.ServerLineSyncHandler;
import jp.apple.aris.ctc.state.LineStateManager;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class CommandCtc extends CommandBase {

    @Override
    public String getName() {
        return "aris";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/aris reload";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "使い方: " + getUsage(sender)));
            return;
        }

        LineManager.loadAllLines();
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLineList(server);

        sender.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "ARIS: 路線データをリロードしました (" + LineManager.getAllLines().size() + "路線)"));
    }
}
