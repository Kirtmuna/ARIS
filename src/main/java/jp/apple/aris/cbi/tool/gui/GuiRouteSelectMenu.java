package jp.apple.aris.cbi.tool.gui;

import jp.apple.aris.ArisNetwork;
import jp.apple.aris.cbi.tool.network.PacketRequestRoute;
import jp.apple.aris.ctc.client.ClientLineCache;
import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.gui.GuiAppleListSelector;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumHand;

import java.util.ArrayList;
import java.util.List;

public class GuiRouteSelectMenu extends GuiScreen {
    private final String lineId;
    private final EnumHand hand;
    private List<String> routeIds = new ArrayList<>();

    public GuiRouteSelectMenu(String lineId, EnumHand hand) {
        this.lineId = lineId;
        this.hand = hand;
    }

    @Override
    public void initGui() {
        this.routeIds = new ArrayList<>();
        LineConfig config = ClientLineCache.getConfig(this.lineId);
        if (config != null && config.routes != null) {
            this.routeIds.addAll(config.routes.keySet());
        }

        if (this.routeIds.isEmpty()) {
            this.mc.displayGuiScreen(null);
            return;
        }

        int listWidth = 160;
        int listHeight = Math.min(200, this.routeIds.size() * 12 + 10);
        int x = (this.width - listWidth) / 2;
        int y = (this.height - listHeight) / 2;

        this.mc.displayGuiScreen(new GuiAppleListSelector(
                this, x, y, listWidth, listHeight,
                () -> -1,
                this.routeIds,
                this::onRouteSelected
        ));
    }

    private void onRouteSelected(int index) {
        String routeId = this.routeIds.get(index);
        ArisNetwork.CHANNEL.sendToServer(new PacketRequestRoute(this.lineId, routeId, this.hand));
        this.mc.displayGuiScreen(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
