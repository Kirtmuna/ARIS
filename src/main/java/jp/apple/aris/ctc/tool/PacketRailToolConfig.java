package jp.apple.aris.ctc.tool;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public class PacketRailToolConfig implements IMessage {
    private String lineId;
    private String pattern;
    private EnumHand hand;

    public PacketRailToolConfig() {}

    public PacketRailToolConfig(String lineId, String pattern, EnumHand hand) {
        this.lineId = lineId;
        this.pattern = pattern;
        this.hand = hand;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.lineId = ByteBufUtils.readUTF8String(buf);
        this.pattern = ByteBufUtils.readUTF8String(buf);
        this.hand = buf.readBoolean() ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.lineId);
        ByteBufUtils.writeUTF8String(buf, this.pattern);
        buf.writeBoolean(this.hand == EnumHand.OFF_HAND);
    }

    public static class Handler implements IMessageHandler<PacketRailToolConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketRailToolConfig message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                ItemStack stack = ctx.getServerHandler().player.getHeldItem(message.hand);
                if (stack.isEmpty()) return;
                NBTTagCompound nbt = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
                nbt.setString("TargetLine", message.lineId);
                nbt.setString("NamePattern", message.pattern);
                stack.setTagCompound(nbt);
            });
            return null;
        }
    }
}