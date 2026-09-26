package jp.apple.aris;

import jp.apple.aris.ctc.network.PacketSyncLineList;
import jp.apple.aris.ctc.tool.PacketRailToolConfig;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class ArisNetwork {
    public static SimpleNetworkWrapper CHANNEL;

    public static void init() {
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(ArisCore.ID);
        CHANNEL.registerMessage(PacketRailToolConfig.Handler.class, PacketRailToolConfig.class, 0, Side.SERVER);
        CHANNEL.registerMessage(PacketSyncLineList.Handler.class, PacketSyncLineList.class, 1, Side.CLIENT);
    }
}
