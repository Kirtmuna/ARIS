package jp.apple.aris.cbi.tool;

import jp.apple.aris.ArisCore;
import jp.apple.aris.cbi.tool.gui.GuiLineSelectMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemRouteRequestTool extends Item {

    public ItemRouteRequestTool() {
        this.setMaxStackSize(1);
        this.setTranslationKey("aris_route_request_tool");
        this.setRegistryName(ArisCore.ID, "route_request_tool");
        this.setCreativeTab(jp.apple.AppleLib.tabAppleLib);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            openGuiClient(stack, hand);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @SideOnly(Side.CLIENT)
    private void openGuiClient(ItemStack stack, EnumHand hand) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiLineSelectMenu(hand));
    }
}
