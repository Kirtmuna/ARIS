package jp.apple.aris.ctc.network;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ArisNetwork;
import jp.apple.aris.ctc.config.LineManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ArisCore.ID)
public class ServerLineSyncHandler {
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            sendLineListTo((EntityPlayerMP) event.player);
        }
    }

    private static void sendLineListTo(EntityPlayerMP player) {
        ArisNetwork.CHANNEL.sendTo(new PacketSyncLineList(LineManager.getAllLines()), player);
    }
    /**
     * 現在ログイン中の全プレイヤーに、最新の路線ID一覧を再送する
     */
    public static void broadcastLineList(MinecraftServer server) {
        if (server == null) return;
        PacketSyncLineList packet = new PacketSyncLineList(LineManager.getAllLines());
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            ArisNetwork.CHANNEL.sendTo(packet, player);
        }
    }
}
