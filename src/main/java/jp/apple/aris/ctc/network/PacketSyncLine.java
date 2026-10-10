package jp.apple.aris.ctc.network;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import jp.apple.aris.ctc.client.ClientLineCache;
import jp.apple.aris.ctc.config.LineConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PacketSyncLine implements IMessage {
    private static final Gson GSON = new Gson();
    private String json = "{}";

    public PacketSyncLine() {}

    public PacketSyncLine(LineConfig config) {
        this.json = GSON.toJson(config != null ? config : new LineConfig());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.json = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.json);
    }

    public static class Handler implements IMessageHandler<PacketSyncLine, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketSyncLine message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                LineConfig cfg = GSON.fromJson(message.json, LineConfig.class);
                ClientLineCache.setConfig(cfg);
            });
            return null;
        }
    }
}
