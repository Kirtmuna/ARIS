package jp.apple.aris.ctc;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ctc.logic.BlockSystem;
import jp.apple.aris.ctc.state.LineStateManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = ArisCore.ID)
public class CtcTickHandler {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        tickCounter++;
        if (tickCounter < 5) return;
        tickCounter = 0;
        
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        
        for (WorldServer world : server.worlds) {
            if (world == null) continue;
            
            BlockSystem.runBlockSystem(world);
        }
    }
}
