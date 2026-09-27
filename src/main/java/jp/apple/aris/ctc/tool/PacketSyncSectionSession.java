package jp.apple.aris.ctc.tool;

import io.netty.buffer.ByteBuf;
import jp.apple.aris.ctc.client.ClientSectionSessionCache;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

public class PacketSyncSectionSession implements IMessage {
    private String lineId = "";
    private List<String> selectedRailIds = new ArrayList<>();

    public PacketSyncSectionSession() {}

    public PacketSyncSectionSession(String lineId, List<String> selectedRailIds) {
        this.lineId = lineId;
        this.selectedRailIds = selectedRailIds;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.lineId = ByteBufUtils.readUTF8String(buf);
        int size = buf.readInt();
        this.selectedRailIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.selectedRailIds.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.lineId);
        buf.writeInt(this.selectedRailIds.size());
        for (String id : this.selectedRailIds) {
            ByteBufUtils.writeUTF8String(buf, id);
        }
    }

    public static class Handler implements IMessageHandler<PacketSyncSectionSession, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketSyncSectionSession message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() ->
                    ClientSectionSessionCache.set(message.lineId, message.selectedRailIds));
            return null;
        }
    }
}
