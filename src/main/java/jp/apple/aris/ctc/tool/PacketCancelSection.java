package jp.apple.aris.ctc.tool;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public class PacketCancelSection implements IMessage {
    public PacketCancelSection() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketCancelSection, IMessage> {
        @Override
        public IMessage onMessage(PacketCancelSection message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() ->
                    SectionRegisterLogic.cancelSession(ctx.getServerHandler().player));
            return null;
        }
    }
}
