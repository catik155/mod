package ru.catk.minuteevents;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MultiplayerModeScreen extends Screen {
    private final Screen parent;

    public MultiplayerModeScreen(Screen parent) {
        super(Component.literal("Режим минутных событий"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(260, this.width - 40);
        int left = (this.width - buttonWidth) / 2;
        int top = this.height / 2 - 8;

        addRenderableWidget(Button.builder(
            Component.literal("Одно событие для всех"),
            button -> chooseMode(true)
        ).bounds(left, top, buttonWidth, 22).build());

        addRenderableWidget(Button.builder(
            Component.literal("Каждому своё событие"),
            button -> chooseMode(false)
        ).bounds(left, top + 28, buttonWidth, 22).build());
    }

    private void chooseMode(boolean shared) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new MultiplayerModePayload(shared));
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        chooseMode(false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF101722);
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = this.width / 2;
        graphics.drawCenteredString(font, "МИНУТНЫЕ СОБЫТИЯ: LAN", center, this.height / 2 - 65, 0xFFFFC66D);
        graphics.drawCenteredString(font, "Как должны происходить события?", center, this.height / 2 - 43, 0xFFFFFFFF);
        graphics.drawCenteredString(font, "Общий режим: все игроки получают один и тот же тип события.", center, this.height / 2 + 58, 0xFFB8C7D9);
        graphics.drawCenteredString(font, "Личный режим: у каждого игрока свой выбор и последствия.", center, this.height / 2 + 72, 0xFFB8C7D9);
    }
}
