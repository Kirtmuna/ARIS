package jp.apple.aris.ctc.tool;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ctc.tool.gui.GuiRailRegisterTool;
import jp.apple.aris.ctc.tool.logic.SectionRegisterLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemSectionRegisterTool extends Item {

    public ItemSectionRegisterTool() {
        this.setMaxStackSize(1);
        this.setTranslationKey("aris_section_register_tool");
        this.setRegistryName(ArisCore.ID, "section_register_tool");
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
        Minecraft.getMinecraft().displayGuiScreen(new GuiRailRegisterTool(stack, hand)); // GUIはそのまま流用
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        boolean handled = SectionRegisterLogic.handleRightClick(player, world, pos, player.getHeldItem(hand));
        return handled ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
    }
}
