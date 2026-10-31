package ru.catk.minuteevents;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GameplaySettingsScreen extends Screen {
    private boolean enabled = true, shared = false, blocks = true, cages = true, traps = true, mobs = true;
    private int interval = 60, blocksChance = 17, cagesChance = 17, trapsChance = 8, mobsChance = 17;

    public GameplaySettingsScreen() { super(Component.literal("Настройки минутных событий")); }

    @Override protected void init() {
        int w = Math.min(330, this.width - 24), x = (this.width - w) / 2, y = 48;
        addRenderableWidget(Button.builder(label("События", enabled), b -> { enabled = !enabled; rebuildWidgets(); })
            .bounds(x, y, w, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Интервал: " + interval + " сек. (нажми, чтобы изменить)"),
            b -> { interval = interval == 30 ? 60 : interval == 60 ? 120 : interval == 120 ? 300 : 30; rebuildWidgets(); })
            .bounds(x, y, w, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(label("Одно событие для всех", shared), b -> { shared = !shared; rebuildWidgets(); })
            .bounds(x, y, w, 20).build());
        y += 30;
        addCategory(x, y, "Разрушение блоков", 0); y += 23;
        addCategory(x, y, "Клетки", 1); y += 23;
        addCategory(x, y, "Ловушки и взрывы", 2); y += 23;
        addCategory(x, y, "Мобы", 3); y += 30;
        addRenderableWidget(Button.builder(Component.literal("Сохранить настройки"),
            b -> save()).bounds(x, y, w, 22).build());
    }

    private Component label(String name, boolean value) {
        return Component.literal(name + ": " + (value ? "ВКЛ" : "ВЫКЛ"));
    }

    private void addCategory(int x, int y, String name, int id) {
        String state = switch (id) { case 0 -> blocks ? "ВКЛ" : "ВЫКЛ"; case 1 -> cages ? "ВКЛ" : "ВЫКЛ";
            case 2 -> traps ? "ВКЛ" : "ВЫКЛ"; default -> mobs ? "ВКЛ" : "ВЫКЛ"; };
        int chance = switch (id) { case 0 -> blocksChance; case 1 -> cagesChance; case 2 -> trapsChance; default -> mobsChance; };
        addRenderableWidget(Button.builder(Component.literal(name + ": " + state + " | шанс " + chance + "% (+5%)"),
            b -> {
                switch (id) {
                    case 0 -> { blocks = !blocks; blocksChance = (blocksChance + 5) % 105; }
                    case 1 -> { cages = !cages; cagesChance = (cagesChance + 5) % 105; }
                    case 2 -> { traps = !traps; trapsChance = (trapsChance + 5) % 105; }
                    default -> { mobs = !mobs; mobsChance = (mobsChance + 5) % 105; }
                }
                if (blocksChance + cagesChance + trapsChance + mobsChance > 100) {
                    switch (id) {
                        case 0 -> blocksChance = 0; case 1 -> cagesChance = 0;
                        case 2 -> trapsChance = 0; default -> mobsChance = 0;
                    }
                }
                rebuildWidgets();
            }).bounds(x, y, Math.min(330, this.width - 24), 20).build());
    }

    private void save() {
        ClientPlayNetworking.send(new GameplaySettingsPayload(enabled, interval, shared,
            blocks, blocksChance, cages, cagesChance, traps, trapsChance, mobs, mobsChance));
        Minecraft.getInstance().setScreen(null);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xCC101722);
        g.drawCenteredString(font, "НАСТРОЙКА ИГРОВОГО ПРОЦЕССА", width / 2, 20, 0xFFFFC66D);
        g.drawCenteredString(font, "Нажимай на категории: переключение и изменение шанса на 5%.", width / 2, 34, 0xFFCFD8E3);
        g.drawCenteredString(font, "Сумма шансов категорий не должна превышать 100%.", width / 2, height - 22, 0xFFB8C7D9);
        super.render(g, mouseX, mouseY, partialTick);
    }
}