package jp.apple.aris.ctc.tool.network;

import io.netty.buffer.ByteBuf;
import jp.apple.aris.ctc.tool.logic.SectionRegisterLogic;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketCancelSection implements IMessage {
    public PacketCancelSection() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketCancelSection, IMessage> {
        @Override
        public IMessage onMessage(PacketCancelSection message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() ->
                    SectionRegisterLogic.cancelSession(ctx.getServerHandler().player));
            return null;
        }
    }
}
