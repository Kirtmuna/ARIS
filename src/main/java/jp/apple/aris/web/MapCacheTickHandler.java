package jp.apple.aris.web;

import jp.apple.aris.ArisCore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = ArisCore.ID)
public class MapCacheTickHandler {
    private static int counter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        counter++;
        if (counter < 40) return;  // 2秒毎
        counter = 0;

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        WorldServer world = server.getWorld(0);
        if (world == null) return;

        try {
            ArisMapCache.get().updateFromWorld(world);
        } catch (Exception e) {
            ArisCore.LOGGER.error("ARIS: map cache update failed", e);
        }
    }
}
