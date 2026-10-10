package jp.apple.aris.cbi.tool.network;

import io.netty.buffer.ByteBuf;
import jp.apple.aris.cbi.Cbi;
import jp.apple.aris.ctc.state.LineStateManager;
import jp.apple.aris.ctc.state.RouteState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketRequestRoute implements IMessage {
    private String routeId;
    private EnumHand hand;

    public PacketRequestRoute() {}

    public PacketRequestRoute(String routeId, EnumHand hand) {
        this.routeId = routeId;
        this.hand = hand;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.routeId = ByteBufUtils.readUTF8String(buf);
        this.hand = buf.readBoolean() ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.routeId);
        buf.writeBoolean(this.hand == EnumHand.OFF_HAND);
    }

    public static class Handler implements IMessageHandler<PacketRequestRoute, IMessage> {
        @Override
        public IMessage onMessage(PacketRequestRoute message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                RouteState route = LineStateManager.getRoutes().get(message.routeId);
                if (route == null) {
                    player.sendMessage(new TextComponentString(
                            TextFormatting.RED + "ARIS: 進路 '" + message.routeId + "' が見つかりません"));
                    return;
                }
                boolean ok = Cbi.get().requestRoute(player.world, route);
                if (ok) {
                    route.setStatus(RouteState.Status.SET);
                    player.sendMessage(new TextComponentString(
                            TextFormatting.GREEN + "ARIS: 進路 '" + message.routeId + "' を予約しました"));
                } else {
                    player.sendMessage(new TextComponentString(
                            TextFormatting.RED + "ARIS: 進路 '" + message.routeId + "' は予約できませんでした(他の進路が使用中)"));
                }
            });
            return null;
        }
    }
}
