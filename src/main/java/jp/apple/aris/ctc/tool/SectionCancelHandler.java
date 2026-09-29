package jp.apple.aris.ctc.tool;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ArisNetwork;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = ArisCore.ID)
public class SectionCancelHandler {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getWorld().isRemote) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !(held.getItem() instanceof ItemSectionRegisterTool)) return;

        if (SectionRegisterLogic.cancelSession(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        ItemStack held = event.getEntityPlayer().getHeldItemMainhand();
        if (held.isEmpty() || !(held.getItem() instanceof ItemSectionRegisterTool)) return;

        ArisNetwork.CHANNEL.sendToServer(new PacketCancelSection());
    }
}
