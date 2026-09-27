package jp.apple.aris.ctc.network;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import jp.apple.aris.ctc.client.ClientLineCache;
import jp.apple.aris.ctc.config.LineConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class PacketSyncLineList implements IMessage {
    private static final Gson GSON = new Gson();
    private Map<String, String> serializedLines = new HashMap<>(); // lineId -> json

    public PacketSyncLineList() {}

    public PacketSyncLineList(Map<String, LineConfig> lines) {
        for (Map.Entry<String, LineConfig> e : lines.entrySet()) {
            this.serializedLines.put(e.getKey(), GSON.toJson(e.getValue()));
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();
        this.serializedLines = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String id = ByteBufUtils.readUTF8String(buf);
            String json = ByteBufUtils.readUTF8String(buf);
            this.serializedLines.put(id, json);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.serializedLines.size());
        for (Map.Entry<String, String> e : this.serializedLines.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, e.getKey());
            ByteBufUtils.writeUTF8String(buf, e.getValue());
        }
    }

    public static class Handler implements IMessageHandler<PacketSyncLineList, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketSyncLineList message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                Map<String, LineConfig> parsed = new HashMap<>();
                for (Map.Entry<String, String> e : message.serializedLines.entrySet()) {
                    parsed.put(e.getKey(), GSON.fromJson(e.getValue(), LineConfig.class));
                }
                ClientLineCache.setLines(parsed);
            });
            return null;
        }
    }
}
