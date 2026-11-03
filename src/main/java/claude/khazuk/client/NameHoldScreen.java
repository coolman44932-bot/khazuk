package claude.khazuk.client;

import claude.khazuk.god.Waking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** The first Dawi ask their god the hold's name: a line to write it in, and "Carve it" (or Escape to keep theirs). */
public final class NameHoldScreen extends Screen {

    private final String suggested;
    private EditBox name;

    private NameHoldScreen(String suggested) {
        super(Component.literal("Name the hold"));
        this.suggested = suggested;
    }

    public static void open(String suggested) {
        Minecraft.getInstance().setScreen(new NameHoldScreen(suggested));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        name = new EditBox(font, cx - 100, height / 2 - 10, 200, 20, Component.literal("The hold's name"));
        name.setMaxLength(Waking.NAME_MAX);
        name.setValue(suggested);
        addRenderableWidget(name);
        setInitialFocus(name);
        addRenderableWidget(Button.builder(Component.literal("Carve it"), b -> carve()).bounds(cx - 50, height / 2 + 18, 100, 20).build());
    }

    private void carve() {
        ClientPacketDistributor.sendToServer(new Waking.NameHold(name.getValue()));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, Component.literal("The first Dawi kneel before you. What is their hold called?"), width / 2, height / 2 - 34, 0xFFE0C060);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
