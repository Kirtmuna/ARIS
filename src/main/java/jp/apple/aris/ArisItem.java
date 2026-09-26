package jp.apple.aris;

import jp.apple.aris.ctc.tool.ItemRailRegisterTool;
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

    public static final ItemRailRegisterTool RAIL_REGISTER_TOOL = new ItemRailRegisterTool();

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(RAIL_REGISTER_TOOL);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onRegisterModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(RAIL_REGISTER_TOOL, 0,
                new ModelResourceLocation(RAIL_REGISTER_TOOL.getRegistryName(), "inventory"));
    }
}
