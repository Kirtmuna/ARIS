package jp.apple.aris.cbi.tool.gui;

import jp.apple.aris.ArisNetwork;
import jp.apple.aris.cbi.tool.network.PacketRequestRoute;
import jp.apple.aris.ctc.client.ClientLineCache;
import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.gui.GuiAppleListSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumHand;

import java.util.ArrayList;
import java.util.List;

public class GuiRouteSelectMenu extends GuiScreen {
    private final EnumHand hand;
    private List<String> routeIds = new ArrayList<>();
    private boolean opened = false;
    private String pendingRoute = null;

    public GuiRouteSelectMenu(EnumHand hand) {
        this.hand = hand;
    }

    @Override
    public void initGui() {
        super.initGui();
        if (this.opened) return;
        this.opened = true;

        this.routeIds = new ArrayList<>();
        LineConfig config = ClientLineCache.getConfig();
        if (config != null && config.routes != null) {
            this.routeIds.addAll(config.routes.keySet());
        }

        if (this.routeIds.isEmpty()) {
            Minecraft.getMinecraft().displayGuiScreen(null);
            return;
        }

        Minecraft.getMinecraft().addScheduledTask(() -> {
            int listWidth = 160;
            int listHeight = Math.min(200, this.routeIds.size() * 12 + 10);
            int x = (this.width - listWidth) / 2;
            int y = (this.height - listHeight) / 2;

            Minecraft.getMinecraft().displayGuiScreen(new GuiAppleListSelector(
                    this, x, y, listWidth, listHeight,
                    () -> -1,
                    this.routeIds,
                    this::onRouteSelected
            ));
        });
    }

    private void onRouteSelected(int index) {
        if (index < 0 || index >= this.routeIds.size()) return;
        this.pendingRoute = this.routeIds.get(index);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.pendingRoute != null) {
            String routeId = this.pendingRoute;
            this.pendingRoute = null;
            ArisNetwork.CHANNEL.sendToServer(new PacketRequestRoute(routeId, this.hand));
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
