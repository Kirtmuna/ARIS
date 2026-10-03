package jp.apple.aris.cbi.tool.gui;

import jp.apple.aris.ctc.client.ClientLineCache;
import jp.apple.gui.GuiAppleListSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumHand;

import java.util.ArrayList;
import java.util.List;

public class GuiLineSelectMenu extends GuiScreen {
    private final EnumHand hand;
    private boolean opened = false;
    private String pendingLine = null;

    public GuiLineSelectMenu(EnumHand hand) {
        this.hand = hand;
    }

    @Override
    public void initGui() {
        super.initGui();
        if (this.opened) return;
        this.opened = true;

        List<String> lines = new ArrayList<>(ClientLineCache.getLineIds());
        if (lines.isEmpty()) {
            Minecraft.getMinecraft().displayGuiScreen(null);
            return;
        }

        Minecraft.getMinecraft().addScheduledTask(() -> {
            int listWidth = 160;
            int listHeight = Math.min(200, lines.size() * 12 + 10);
            int x = (this.width - listWidth) / 2;
            int y = (this.height - listHeight) / 2;

            Minecraft.getMinecraft().displayGuiScreen(new GuiAppleListSelector(
                    this, x, y, listWidth, listHeight,
                    () -> -1,
                    lines,
                    index -> onLineSelected(lines, index)
            ));
        });
    }

    private void onLineSelected(List<String> lines, int index) {
        if (index < 0 || index >= lines.size()) return;
        this.pendingLine = lines.get(index);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.pendingLine != null) {
            String line = this.pendingLine;
            this.pendingLine = null;
            Minecraft.getMinecraft().displayGuiScreen(new GuiRouteSelectMenu(line, this.hand));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
