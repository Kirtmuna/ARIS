package jp.apple.aris.ctc.network;

import io.netty.buffer.ByteBuf;
import jp.apple.aris.ctc.client.ClientLineCache;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

public class PacketSyncLineList implements IMessage {
    private List<String> lineIds = new ArrayList<>();

    public PacketSyncLineList() {}

    public PacketSyncLineList(List<String> lineIds) {
        this.lineIds = lineIds;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();
        this.lineIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.lineIds.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.lineIds.size());
        for (String id : this.lineIds) {
            ByteBufUtils.writeUTF8String(buf, id);
        }
    }

    public static class Handler implements IMessageHandler<PacketSyncLineList, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketSyncLineList message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() ->
                    ClientLineCache.setLineIds(message.lineIds));
            return null;
        }
    }
}
