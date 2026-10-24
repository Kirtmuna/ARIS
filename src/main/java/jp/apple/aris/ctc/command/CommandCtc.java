package jp.apple.aris.ctc.command;

import jp.apple.aris.ctc.config.LineManager;
import jp.apple.aris.ctc.state.LineStateManager;
import jp.apple.aris.web.ArisAuth;
import jp.apple.aris.web.ArisWebServer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
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
        return "/aris <reload|web>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "使い方: " + getUsage(sender)));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            LineManager.load();
            LineStateManager.initializeStates();
            sender.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + "ARIS: 路線データをリロードしました"));
            return;
        }

        if (args[0].equalsIgnoreCase("web")) {
            if (!(sender instanceof EntityPlayerMP)) {
                sender.sendMessage(new TextComponentString(
                        TextFormatting.RED + "このコマンドはプレイヤーのみ実行できます"));
                return;
            }
            String mcid = sender.getName();
            String code = ArisAuth.issueCode(mcid);
            sender.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + "ARIS Web ログインコード: " +
                            TextFormatting.AQUA + code +
                            TextFormatting.GRAY + " (5分間有効)"));
            sender.sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "ブラウザで http://<server-ip>:" +
                            ArisWebServer.getPort() + "/ を開き、MCID とコードを入力"));
            return;
        }

        sender.sendMessage(new TextComponentString(TextFormatting.RED + "使い方: " + getUsage(sender)));
    }
}
