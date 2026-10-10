package jp.apple.aris.ctc.network;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ArisNetwork;
import jp.apple.aris.ctc.config.LineManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

@Mod.EventBusSubscriber(modid = ArisCore.ID)
public class ServerLineSyncHandler {
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            sendLineTo((EntityPlayerMP) event.player);
        }
    }

    private static void sendLineTo(EntityPlayerMP player) {
        ArisNetwork.CHANNEL.sendTo(new PacketSyncLine(LineManager.getConfig()), player);
    }

    public static void broadcastLine(MinecraftServer server) {
        if (server == null) return;
        PacketSyncLine packet = new PacketSyncLine(LineManager.getConfig());
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            ArisNetwork.CHANNEL.sendTo(packet, player);
        }
    }
}
