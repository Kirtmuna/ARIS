package jp.apple.aris.ctc.tool;

import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.aris.ctc.config.LineManager;
import jp.apple.aris.ctc.network.ServerLineSyncHandler;
import jp.apple.aris.ctc.state.LineStateManager;
import jp.ngt.rtm.electric.TileEntitySignal;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SectionRegisterLogic {
    private static final Map<UUID, SectionRegisterSession> SESSIONS = new HashMap<>();

    public static boolean handleRightClick(EntityPlayer player, World world, BlockPos clickedPos, ItemStack stack) {
        TileEntity te = world.getTileEntity(clickedPos);
        UUID uuid = player.getUniqueID();
        SectionRegisterSession session = SESSIONS.get(uuid);

        if (te instanceof TileEntitySignal) {
            if (session == null) {
                return startSession(player, world, clickedPos, stack);
            } else {
                return finishSession(player, world, clickedPos, session);
            }
        }

        if (te instanceof TileEntityLargeRailBase) {
            if (session == null) {
                sendMsg(player, TextFormatting.RED, "先に開始信号を右クリックしてください");
                return true;
            }
            return addRailToSession(player, world, clickedPos, session);
        }

        return false;
    }

    private static boolean startSession(EntityPlayer player, World world, BlockPos signalPos, ItemStack stack) {
        NBTTagCompound nbt = stack.getTagCompound();
        String lineId = nbt != null ? nbt.getString("TargetLine") : "";
        String pattern = nbt != null ? nbt.getString("NamePattern") : "";

        if (lineId.isEmpty() || pattern.isEmpty()) {
            sendMsg(player, TextFormatting.RED, "先にGuiで路線IDと名称パターンを設定してください");
            return true;
        }

        LineManager.reloadLine(lineId);
        LineConfig config = LineManager.getLine(lineId);
        if (config == null) {
            sendMsg(player, TextFormatting.RED, "路線 '" + lineId + "' が見つかりません");
            return true;
        }

        String signalId = LineLookup.findSignalId(config, signalPos);
        if (signalId == null) {
            sendMsg(player, TextFormatting.RED, "この信号機は路線に登録されていません");
            return true;
        }

        SESSIONS.put(player.getUniqueID(), new SectionRegisterSession(lineId, pattern, signalId));
        syncSession(player, SESSIONS.get(player.getUniqueID()));
        sendMsg(player, TextFormatting.GREEN, "区間登録開始 (開始信号: " + signalId + ")");
        return true;
    }

    private static boolean addRailToSession(EntityPlayer player, World world, BlockPos clickedPos, SectionRegisterSession session) {
        LineConfig config = LineManager.getLine(session.lineId);
        if (config == null) {
            sendMsg(player, TextFormatting.RED, "路線が見つかりません");
            SESSIONS.remove(player.getUniqueID());
            return true;
        }

        TileEntity te = world.getTileEntity(clickedPos);
        TileEntityLargeRailCore core = ((TileEntityLargeRailBase) te).getRailCore();
        if (core == null) {
            sendMsg(player, TextFormatting.RED, "このレールはまだ初期化されていません");
            return true;
        }

        String railId = LineLookup.findRailId(config, core);
        if (railId == null) {
            sendMsg(player, TextFormatting.RED, "このレールは路線に登録されていません。先にレール登録ツールで登録してください");
            return true;
        }

        if (session.railIds.contains(railId)) {
            session.railIds.remove(railId);
            syncSession(player, session);
            sendMsg(player, TextFormatting.YELLOW, "区間から除外: " + railId + " (現在 " + session.railIds.size() + "本)");
        } else {
            session.railIds.add(railId);
            syncSession(player, session);
            sendMsg(player, TextFormatting.AQUA, "区間に追加: " + railId + " (現在 " + session.railIds.size() + "本)");
        }
        return true;
    }

    private static boolean finishSession(EntityPlayer player, World world, BlockPos signalPos, SectionRegisterSession session) {
        LineManager.reloadLine(session.lineId);
        LineConfig config = LineManager.getLine(session.lineId);
        if (config == null) {
            sendMsg(player, TextFormatting.RED, "路線が見つかりません");
            SESSIONS.remove(player.getUniqueID());
            syncSession(player, null);
            return true;
        }

        String endSignalId = LineLookup.findSignalId(config, signalPos);
        if (endSignalId == null) {
            sendMsg(player, TextFormatting.RED, "この信号機は路線に登録されていません。別の信号機を選んでください");
            return true;
        }

        if (session.railIds.isEmpty()) {
            sendMsg(player, TextFormatting.RED, "レールが1本も追加されていません。先にレールを右クリックしてください");
            return true;
        }

        if (config.sections == null) {
            config.sections = new HashMap<>();
        }

        String candidateName = resolveSectionName(config.sections, session.namePattern);

        LineConfig.SectionConfig sc = new LineConfig.SectionConfig();
        sc.sectionRails = session.railIds.toArray(new String[0]);
        sc.startSignal = session.startSignalId;
        sc.endSignal = endSignalId;
        config.sections.put(candidateName, sc);

        LineManager.saveLine(session.lineId);
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLineList(world.getMinecraftServer());

        SESSIONS.remove(player.getUniqueID());
        sendMsg(player, TextFormatting.GREEN, "区間登録完了 -> " + candidateName
                + " (レール" + sc.sectionRails.length + "本, " + session.startSignalId + " -> " + endSignalId + ")");
        return true;
    }

    /**
     * 左クリックでのキャンセル。セッションがあれば破棄してtrueを返す
     */
    public static boolean cancelSession(EntityPlayer player) {
        if (SESSIONS.remove(player.getUniqueID()) != null) {
            syncSession(player, null);
            sendMsg(player, TextFormatting.YELLOW, "区間登録をキャンセルしました");
            return true;
        }
        return false;
    }

    private static String resolveSectionName(Map<String, LineConfig.SectionConfig> sections, String pattern) {
        if (!pattern.contains("<>")) {
            return pattern;
        }
        int n = 1;
        while (sections.containsKey(pattern.replace("<>", String.valueOf(n)))) {
            n++;
        }
        return pattern.replace("<>", String.valueOf(n));
    }

    private static void sendMsg(EntityPlayer player, TextFormatting color, String msg) {
        player.sendMessage(new TextComponentString(color + "ARIS: " + msg));
    }

    private static void syncSession(EntityPlayer player, SectionRegisterSession session) {
        String lineId = session != null ? session.lineId : "";
        java.util.List<String> ids = session != null ? session.railIds : java.util.Collections.emptyList();
        jp.apple.aris.ArisNetwork.CHANNEL.sendTo(
                new PacketSyncSectionSession(lineId, ids),
                (net.minecraft.entity.player.EntityPlayerMP) player);
    }
}
