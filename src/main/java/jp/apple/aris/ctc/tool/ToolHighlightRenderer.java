package jp.apple.aris.ctc.tool;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ctc.client.ClientLineCache;
import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.aris.ctc.client.ClientSectionSessionCache;
import jp.apple.aris.common.util.PositionUtil;
import jp.ngt.rtm.electric.TileEntitySignal;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = ArisCore.ID, value = Side.CLIENT)
public class ToolHighlightRenderer {

    private static final int SAMPLES = 24;
    private static final float[] GREEN = {0.2F, 1.0F, 0.2F};
    private static final float[] RED   = {1.0F, 0.2F, 0.2F};
    private static final float[] BLUE  = {0.3F, 0.5F, 1.0F};
    
    private static List<String> hudLines = new ArrayList<>();

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        hudLines = new ArrayList<>();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        ItemStack stack = mc.player.getHeldItemMainhand();
        Item held = stack.getItem();
        boolean isRailTool = held instanceof ItemRailRegisterTool;
        boolean isSectionTool = held instanceof ItemSectionRegisterTool;
        if (!isRailTool && !isSectionTool) return;

        NBTTagCompound nbt = stack.getTagCompound();
        String lineId = nbt != null ? nbt.getString("TargetLine") : "";
        LineConfig config = ClientLineCache.getConfig(lineId);

        prepareRender();
        
        if (isSectionTool && config != null && lineId.equals(ClientSectionSessionCache.getLineId())) {
            for (String selId : ClientSectionSessionCache.getSelectedRailIds()) {
                LineConfig.RailConfig rc = config.rails != null ? config.rails.get(selId) : null;
                if (rc == null) continue;
                BlockPos selPos = PositionUtil.toBlockPos(rc.position);
                if (!mc.world.isBlockLoaded(selPos)) continue;
                TileEntity selTe = mc.world.getTileEntity(selPos);
                if (!(selTe instanceof TileEntityLargeRailCore)) continue;
                List<int[]> selGroup = ((TileEntityLargeRailCore) selTe).getRailGroupCorePositions();
                drawGroupHighlight(mc, event.getPartialTicks(), selGroup, BLUE);
            }
        }
        RayTraceResult trace = mc.objectMouseOver;
        if (trace != null && trace.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos pos = trace.getBlockPos();
            TileEntity te = mc.world.getTileEntity(pos);
            
            if (te instanceof TileEntitySignal) {
                if (isSectionTool && config != null) {
                    String signalId = findSignalId(config, pos);
                    hudLines.add(signalId != null ? "信号ID: " + signalId : "信号ID: 未登録");
                }
            } else if (te instanceof TileEntityLargeRailBase) {
                TileEntityLargeRailCore core = ((TileEntityLargeRailBase) te).getRailCore();
                if (core != null && config != null) {
                    List<int[]> groupPositions = core.getRailGroupCorePositions();
                    String railId = findRegisteredRailId(config, groupPositions);

                    boolean isSelected = isSectionTool
                            && railId != null
                            && lineId.equals(ClientSectionSessionCache.getLineId())
                            && ClientSectionSessionCache.getSelectedRailIds().contains(railId);
                    
                    float[] color = isSelected ? BLUE : (railId != null ? GREEN : RED);
                    drawGroupHighlight(mc, event.getPartialTicks(), groupPositions, color);

                    if (railId != null) {
                        hudLines.add("レールID: " + railId);
                        if (isSectionTool) {
                            List<String> sections = findSectionsContaining(config, railId);
                            if (!sections.isEmpty()) {
                                hudLines.add("所属区間: " + String.join(", ", sections));
                            }
                        }
                    } else {
                        hudLines.add("レールID: 未登録");
                    }
                }
            }
        }

        finishRender();
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
        if (hudLines.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = event.getResolution();
        int x = sr.getScaledWidth() / 2 + 12;
        int y = sr.getScaledHeight() / 2 - 8;

        for (String line : hudLines) {
            mc.fontRenderer.drawStringWithShadow(line, x, y, 0xFFFFFF);
            y += 10;
        }
    }

    private static void prepareRender() {
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(4.0F);
    }

    private static void finishRender() {
        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
    }

    private static void drawGroupHighlight(Minecraft mc, float partialTicks, List<int[]> groupPositions, float[] color) {
        Entity view = mc.getRenderViewEntity();
        double px = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double py = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double pz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.color(color[0], color[1], color[2], 1.0F);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        for (int[] gp : groupPositions) {
            BlockPos gPos = new BlockPos(gp[0], gp[1], gp[2]);
            TileEntity gte = mc.world.getTileEntity(gPos);
            if (!(gte instanceof TileEntityLargeRailCore)) continue;

            RailMap[] maps = ((TileEntityLargeRailCore) gte).getAllRailMaps();
            if (maps == null) continue;

            for (RailMap rm : maps) {
                if (rm == null) continue;
                buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
                for (int i = 0; i <= SAMPLES; i++) {
                    double[] point = rm.getRailPos(SAMPLES, i);
                    double y = rm.getRailHeight(SAMPLES, i) + 0.15D;
                    buf.pos(point[1], y, point[0]).endVertex();
                }
                tess.draw();
            }
        }
        GlStateManager.popMatrix();
    }

    private static String findRegisteredRailId(LineConfig config, List<int[]> groupPositions) {
        if (config.rails == null) return null;
        for (int[] gp : groupPositions) {
            for (Map.Entry<String, LineConfig.RailConfig> e : config.rails.entrySet()) {
                int[] p = e.getValue().position;
                if (p != null && p.length >= 3 && p[0] == gp[0] && p[1] == gp[1] && p[2] == gp[2]) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    private static String findSignalId(LineConfig config, BlockPos pos) {
        if (config.signals == null) return null;
        for (Map.Entry<String, LineConfig.SignalConfig> e : config.signals.entrySet()) {
            for (BlockPos sp : PositionUtil.toBlockPosArray(e.getValue().positions)) {
                if (sp.equals(pos)) return e.getKey();
            }
        }
        return null;
    }

    private static List<String> findSectionsContaining(LineConfig config, String railId) {
        List<String> result = new ArrayList<>();
        if (config.sections == null) return result;
        for (Map.Entry<String, LineConfig.SectionConfig> e : config.sections.entrySet()) {
            if (e.getValue().sectionRails == null) continue;
            for (String r : e.getValue().sectionRails) {
                if (railId.equals(r)) { result.add(e.getKey()); break; }
            }
        }
        return result;
    }
}
