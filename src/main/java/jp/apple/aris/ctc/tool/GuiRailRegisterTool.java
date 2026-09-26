package jp.apple.aris.ctc.tool;

import jp.apple.aris.ArisNetwork;
import jp.apple.aris.ctc.client.ClientLineCache;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

public class GuiRailRegisterTool extends GuiScreen {
    private final EnumHand hand;
    private String selectedLine;
    private GuiTextField patternField;
    private GuiButton lineButton;
    private GuiButton pasteButton;

    public GuiRailRegisterTool(ItemStack stack, EnumHand hand) {
        this.hand = hand;
        NBTTagCompound nbt = stack.getTagCompound();
        this.selectedLine = nbt != null ? nbt.getString("TargetLine") : "";
        this.initialPattern = nbt != null ? nbt.getString("NamePattern") : "";
    }
    private String initialPattern = "";

    @Override
    public void initGui() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        List<String> lines = ClientLineCache.getLineIds();
        if (this.selectedLine.isEmpty() && !lines.isEmpty()) {
            this.selectedLine = lines.get(0);
        }

        this.lineButton = new GuiButton(0, cx - 100, cy - 50, 200, 20, lineLabel());
        this.buttonList.add(this.lineButton);

        this.patternField = new GuiTextField(1, this.fontRenderer, cx - 100, cy, 176, 20);
        this.patternField.setMaxStringLength(64);
        this.patternField.setText(this.initialPattern);
        this.patternField.setFocused(true);

        this.pasteButton = new GuiButton(3, cx + 78, cy, 22, 20, "V");
        this.buttonList.add(this.pasteButton);

        this.buttonList.add(new GuiButton(2, cx - 50, cy + 30, 100, 20, "保存して閉じる"));
    }

    private String lineLabel() {
        List<String> lines = ClientLineCache.getLineIds();
        if (lines.isEmpty()) return "路線がありません";
        return "路線: " + (this.selectedLine.isEmpty() ? "未選択" : this.selectedLine)
                + "  (" + (lines.indexOf(this.selectedLine) + 1) + "/" + lines.size() + ")";
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        List<String> lines = ClientLineCache.getLineIds();
        if (button.id == 0 && !lines.isEmpty()) {
            int idx = lines.indexOf(this.selectedLine);
            idx = (idx + 1) % lines.size(); // クリックで次の路線へ循環
            this.selectedLine = lines.get(idx);
            this.lineButton.displayString = lineLabel();
        } else if (button.id == 2) {
            this.save();
        } else if (button.id == 3) {
            this.pasteFromClipboard();
        }
    }

    private void pasteFromClipboard() {
        String clipboard = getClipboardString();
        if (clipboard != null && !clipboard.isEmpty()) {
            String firstLine = clipboard.split("\\r?\\n", 2)[0];
            this.patternField.setText(firstLine);
        }
    }

    private void save() {
        if (this.selectedLine == null || this.selectedLine.isEmpty()) return; // 未選択なら保存しない
        ArisNetwork.CHANNEL.sendToServer(
                new PacketRailToolConfig(this.selectedLine, this.patternField.getText(), this.hand));
        this.mc.displayGuiScreen(null);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { this.mc.displayGuiScreen(null); return; }
        if (keyCode == Keyboard.KEY_RETURN) { this.save(); return; }
        this.patternField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.patternField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer, "登録名称", this.width / 2, this.height / 2 - 14, 0xFFFFFF);
        this.patternField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}