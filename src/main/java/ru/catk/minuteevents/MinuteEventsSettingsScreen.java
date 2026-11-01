package ru.catk.minuteevents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MinuteEventsSettingsScreen extends Screen {
    private final Screen parent;
    private boolean enabled = true;
    private boolean timer = true;
    private boolean blocks = true;
    private boolean cages = true;
    private boolean traps = true;
    private boolean mobs = true;
    private boolean other = true;

    public MinuteEventsSettingsScreen(Screen parent) {
        super(Component.literal("Настройки Minute Events"));
        this.parent = parent;
    }

    private void command(String value) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.connection.sendCommand(value);
        }
    }

    @Override
    protected void init() {
        int w = 142;
        int gap = 8;
        int left = this.width / 2 - (w * 2 + gap) / 2;
        int top = 48;
        addRenderableWidget(Button.builder(Component.literal("Мод: ВКЛ"), b -> {
            enabled = !enabled;
            command(enabled ? "minuteevent on" : "minuteevent off");
            b.setMessage(Component.literal("Мод: " + (enabled ? "ВКЛ" : "ВЫКЛ")));
        }).bounds(left, top, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Таймер: ВКЛ"), b -> {
            timer = !timer;
            command(timer ? "minuteevent timer on" : "minuteevent timer off");
            b.setMessage(Component.literal("Таймер: " + (timer ? "ВКЛ" : "ВЫКЛ")));
        }).bounds(left + w + gap, top, w, 20).build());

        addRenderableWidget(action("Предпросмотр события", "minuteevent mode preview", left, top + 27, w));
        addRenderableWidget(action("Показать проценты", "minuteevent mode chance", left + w + gap, top + 27, w));
        addRenderableWidget(action("Слепой режим", "minuteevent mode blind", left, top + 54, w));
        addRenderableWidget(action("Событие сейчас", "minuteevent now", left + w + gap, top + 54, w));

        addRenderableWidget(action("Интервал: 30 сек.", "minuteevent interval 30", left, top + 81, w));
        addRenderableWidget(action("Интервал: 60 сек.", "minuteevent interval 60", left + w + gap, top + 81, w));
        addRenderableWidget(action("Интервал: 120 сек.", "minuteevent interval 120", left, top + 108, w));
        addRenderableWidget(action("Показать шансы", "minuteevent chances", left + w + gap, top + 108, w));

        addRenderableWidget(toggleCategory("Блоки", "blocks", left, top + 145, () -> blocks, v -> blocks = v));
        addRenderableWidget(toggleCategory("Клетки", "cages", left + w + gap, top + 145, () -> cages, v -> cages = v));
        addRenderableWidget(toggleCategory("Ловушки", "traps", left, top + 172, () -> traps, v -> traps = v));
        addRenderableWidget(toggleCategory("Мобы", "mobs", left + w + gap, top + 172, () -> mobs, v -> mobs = v));
        addRenderableWidget(toggleCategory("Прочее", "other", left, top + 199, () -> other, v -> other = v));

        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose())
            .bounds(this.width / 2 - 70, this.height - 30, 140, 20).build());
    }

    private Button action(String label, String command, int x, int y, int width) {
        return Button.builder(Component.literal(label), b -> command(command))
            .bounds(x, y, width, 20).build();
    }

    private Button toggleCategory(String label, String key, int x, int y,
                                  java.util.function.BooleanSupplier getter,
                                  java.util.function.Consumer<Boolean> setter) {
        return Button.builder(Component.literal(label + ": " + (getter.getAsBoolean() ? "ВКЛ" : "ВЫКЛ")), b -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            command("minuteevent category " + key + (value ? " on" : " off"));
            b.setMessage(Component.literal(label + ": " + (value ? "ВКЛ" : "ВЫКЛ")));
        }).bounds(x, y, 142, 20).build();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF101722);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "НАСТРОЙКИ MINUTE EVENTS", this.width / 2, 20, 0xFFFFC66D);
        graphics.drawCenteredString(font, "Настройки доступны хосту мира", this.width / 2, 34, 0xFFB8C7D9);
    }
}
