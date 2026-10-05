package jp.apple.aris;

import jp.apple.aris.cbi.tool.ItemRouteRequestTool;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod.EventBusSubscriber(modid = ArisCore.ID)
public class ArisItem {

    public static final ItemRouteRequestTool ROUTE_REQUEST_TOOL = new ItemRouteRequestTool();

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(ROUTE_REQUEST_TOOL);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onRegisterModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(ROUTE_REQUEST_TOOL, 0,
                new ModelResourceLocation(ROUTE_REQUEST_TOOL.getRegistryName(), "inventory"));
    }
}
