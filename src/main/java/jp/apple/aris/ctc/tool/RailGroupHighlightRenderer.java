package jp.apple.aris.ctc.tool;

import jp.apple.aris.ArisCore;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.List;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = ArisCore.ID, value = Side.CLIENT)
public class RailGroupHighlightRenderer {

    private static final int SAMPLES = 24;

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        if (!(mc.player.getHeldItemMainhand().getItem() instanceof ItemRailRegisterTool)) return;

        RayTraceResult trace = mc.objectMouseOver;
        if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK) return;

        BlockPos pos = trace.getBlockPos();
        TileEntity te = mc.world.getTileEntity(pos);
        if (!(te instanceof TileEntityLargeRailBase)) return;

        TileEntityLargeRailCore core = ((TileEntityLargeRailBase) te).getRailCore();
        if (core == null) return;
        
        List<int[]> groupPositions = core.getRailGroupCorePositions();

        Entity view = mc.getRenderViewEntity();
        double partial = event.getPartialTicks();
        double px = view.lastTickPosX + (view.posX - view.lastTickPosX) * partial;
        double py = view.lastTickPosY + (view.posY - view.lastTickPosY) * partial;
        double pz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.glLineWidth(4.0F);
        GlStateManager.color(1.0F, 0.0F, 0.0F, 1.0F);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        for (int[] gp : groupPositions) {
            BlockPos gPos = new BlockPos(gp[0], gp[1], gp[2]);
            TileEntity gte = mc.world.getTileEntity(gPos);
            if (!(gte instanceof TileEntityLargeRailCore)) continue;

            TileEntityLargeRailCore gCore = (TileEntityLargeRailCore) gte;
            RailMap[] maps = gCore.getAllRailMaps();
            if (maps == null) continue;

            for (RailMap rm : maps) {
                if (rm == null) continue;
                buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
                for (int i = 0; i <= SAMPLES; i++) {
                    double[] point = rm.getRailPos(SAMPLES, i); // {z, x}
                    double y = rm.getRailHeight(SAMPLES, i) + 0.15D;
                    buf.pos(point[1], y, point[0]).endVertex();
                }
                tess.draw();
            }
        }

        GlStateManager.glLineWidth(1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
