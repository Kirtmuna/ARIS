package jp.apple.aris.ctc.tool;

import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.aris.ctc.config.LineManager;
import jp.apple.aris.ctc.network.ServerLineSyncHandler;
import jp.apple.aris.ctc.state.LineStateManager;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class RailRegisterLogic {
    /**
     * @return このクリックがレール登録処理として処理されたか(true=処理した/false=対象外なのでPASSすべき)
     */
    public static boolean handleRailClick(EntityPlayer player, World world, BlockPos clickedPos, ItemStack stack) {
        TileEntity te = world.getTileEntity(clickedPos);
        if (!(te instanceof TileEntityLargeRailBase)) {
            return false;
        }

        TileEntityLargeRailCore core = ((TileEntityLargeRailBase) te).getRailCore();
        if (core == null) {
            sendMsg(player, TextFormatting.RED, "このレールはまだ初期化されていません");
            return true;
        }
        BlockPos corePos = core.getPos();

        NBTTagCompound nbt = stack.getTagCompound();
        String lineId = nbt != null ? nbt.getString("TargetLine") : "";
        String pattern = nbt != null ? nbt.getString("NamePattern") : "";

        if (lineId.isEmpty() || pattern.isEmpty()) {
            sendMsg(player, TextFormatting.RED, "先にGuiで路線IDと名称パターンを設定してください");
            return true;
        }

        LineConfig config = LineManager.getLine(lineId);
        if (config == null) {
            sendMsg(player, TextFormatting.RED, "路線 '" + lineId + "' が見つかりません");
            return true;
        }
        if (config.rails == null) {
            config.rails = new HashMap<>();
        }
        
        String existingId = null;
        for (Map.Entry<String, LineConfig.RailConfig> entry : config.rails.entrySet()) {
            int[] p = entry.getValue().position;
            if (p != null && p.length >= 3
                    && p[0] == corePos.getX() && p[1] == corePos.getY() && p[2] == corePos.getZ()) {
                existingId = entry.getKey();
                break;
            }
        }

        String candidateName = resolveName(config.rails, pattern, existingId);

        if (existingId == null) {
            LineConfig.RailConfig rc = new LineConfig.RailConfig();
            rc.position = new int[]{corePos.getX(), corePos.getY(), corePos.getZ()};
            config.rails.put(candidateName, rc);
            sendMsg(player, TextFormatting.GREEN, "登録しました -> " + candidateName);
        } else if (candidateName.equals(existingId)) {
            config.rails.remove(existingId);
            sendMsg(player, TextFormatting.YELLOW, "解除しました -> " + existingId);
        } else {
            LineConfig.RailConfig rc = config.rails.remove(existingId);
            config.rails.put(candidateName, rc);
            sendMsg(player, TextFormatting.AQUA, "名称変更 " + existingId + " -> " + candidateName);
        }
        
        LineManager.saveLine(lineId);
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLineList(world.getMinecraftServer());
        return true;
    }
    /**
     * <> を含むパターンから、excludeId自身は"使用中"から除外して空き番号を探す。
     * <> を含まない場合はそのままリテラル名として扱う。
     */
    private static String resolveName(Map<String, LineConfig.RailConfig> rails, String pattern, String excludeId) {
        if (!pattern.contains("<>")) {
            return pattern;
        }
        int n = 1;
        while (true) {
            String candidate = pattern.replace("<>", String.valueOf(n));
            boolean taken = rails.containsKey(candidate) && !candidate.equals(excludeId);
            if (!taken) {
                return candidate;
            }
            n++;
        }
    }
    private static void sendMsg(EntityPlayer player, TextFormatting color, String msg) {
        player.sendMessage(new TextComponentString(color + "ARIS: " + msg));
    }
}