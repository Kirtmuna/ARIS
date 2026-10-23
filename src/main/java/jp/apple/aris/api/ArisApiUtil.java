package jp.apple.aris.api;

import jp.apple.aris.cbi.Cbi;
import jp.apple.aris.ctc.state.*;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationEntry;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Map;
import java.util.WeakHashMap;

public class ArisApiUtil {
    private static final Map<EntityTrainBase, SectionState> CURRENT_SECTION = new WeakHashMap<>();
    /**
     * 車両が今乗っているレール（currentRailObj）から、
     * それが属する区間を逆引きする。
     */
    public static SectionState getSection(EntityTrainBase train) {
        if (train == null) return null;

        EntityTrainBase head = getFormationHead(train);
        if (head == null) head = train;
        
        EntityBogie bogie = head.getBogie(head.getTrainDirection());
        if (bogie == null) return CURRENT_SECTION.get(train);

        TileEntityLargeRailCore core = bogie.getCurrentRailObj();
        if (core == null) return CURRENT_SECTION.get(train);

        SectionState found = LineStateManager.findSectionByRailPos(core.getPos());
        if (found != null) {
            CURRENT_SECTION.put(train, found);
        }
        return CURRENT_SECTION.get(train);
    }

    private static SectionState findSectionByRailKey(String railKey) {
        for (SectionState sec : LineStateManager.getSections().values()) {
            for (RailState rail : sec.getSectionRails()) {
                if (rail == null) continue;
                BlockPos p = rail.getRailPosition();
                String k = p.getX() + "," + p.getY() + "," + p.getZ();
                if (k.equals(railKey)) {
                    return sec;
                }
            }
        }
        return null;
    }

    private static EntityTrainBase getFormationHead(EntityTrainBase train) {
        Formation formation = train.getFormation();
        if (formation == null) return train;
        for (FormationEntry entry : formation.entries) {
            if (entry == null || entry.train == null) continue;
            if (formation.isFrontCar(entry.train)) return entry.train;
        }
        return train;
    }

    public static SignalState getNextSignal(EntityTrainBase train) {
        SectionState sec = getSection(train);
        if (sec == null) return null;
        return sec.resolveNextSignal();
    }

    public static int getNextSignalAspect(EntityTrainBase train) {
        SignalState sig = getNextSignal(train);
        return sig != null ? sig.getCurrentAspect() : -1;
    }

    /**
     * 進路IDを指定して進路を鎖錠する。
     * World の取得・RouteState の解決・状態遷移・予約までを1メソッドで行う。
     *
     * @param routeId 進路ID
     * @return 鎖錠できたら true。進路が無い/他進路と競合/サーバ未起動 なら false
     */
    public static boolean lockRoute(String routeId) {
        if (routeId == null) return false;

        RouteState route = LineStateManager.getRoutes().get(routeId);
        if (route == null) return false;

        // 既に SET/OCCUPIED なら成功扱い（冪等）
        if (route.getStatus() != RouteState.Status.IDLE) return true;

        WorldServer world = getOverworld();
        if (world == null) return false;

        boolean ok = Cbi.get().requestRoute(world, route);
        if (ok) {
            route.setStatus(RouteState.Status.SET);
        }
        return ok;
    }

    /**
     * 進路IDを指定して鎖錠を解放する。
     *
     * @return 解放したら true。進路が無い/サーバ未起動 なら false
     */
    public static boolean unlockRoute(String routeId) {
        if (routeId == null) return false;

        RouteState route = LineStateManager.getRoutes().get(routeId);
        if (route == null) return false;

        if (route.getStatus() == RouteState.Status.IDLE) return true;

        WorldServer world = getOverworld();
        if (world == null) return false;

        Cbi.get().releaseRoute(world, route);
        route.setStatus(RouteState.Status.IDLE);
        return true;
    }

    private static WorldServer getOverworld() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return null;
        return server.getWorld(0);
    }
}
