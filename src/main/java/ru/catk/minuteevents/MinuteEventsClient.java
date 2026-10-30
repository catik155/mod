package ru.catk.minuteevents;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class MinuteEventsClient implements ClientModInitializer {
    private static final long DISPLAY_DURATION_NANOS = 7_000_000_000L;
    private static final int PANEL_WIDTH = 340;

    private static String currentMessage;
    private static long shownAtNanos;
    private static int secondsUntilEvent = 60;
    private static String timerDetail = "Шансы категорий";
    private static boolean timerVisible = true;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
            EventNotificationPayload.TYPE,
            (payload, context) -> show(payload.message())
        );
        ClientPlayNetworking.registerGlobalReceiver(
            EventTimerPayload.TYPE,
            (payload, context) -> {
                secondsUntilEvent = payload.secondsRemaining();
                timerVisible = payload.timerVisible();
                timerDetail = payload.detail();
            }
        );
        HudRenderCallback.EVENT.register(MinuteEventsClient::renderNotification);
    }

    private static void show(String message) {
        currentMessage = message;
        shownAtNanos = System.nanoTime();
    }

    private static void renderNotification(GuiGraphics graphics, Object tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (timerVisible) {
            String timerText = String.format(java.util.Locale.ROOT, "%02d:%02d", secondsUntilEvent / 60, secondsUntilEvent % 60);
            int timerWidth = client.font.width(timerText);
            graphics.fill(8, 8, 8 + timerWidth + 12, 25, 0xB9161920);
            graphics.drawString(client.font, timerText, 14, 13, 0xFFFFC66D, false);
        }
        if (!timerDetail.isBlank()) {
            List<FormattedCharSequence> detailLines = client.font.split(
                Component.literal(timerDetail),
                320
            );
            int detailHeight = Math.max(17, detailLines.size() * 10 + 8);
            int detailWidth = Math.min(340, Math.max(120, detailLines.stream()
                .mapToInt(client.font::width).max().orElse(120))) + 12;
            graphics.fill(8, 29, 8 + detailWidth, 29 + detailHeight, 0xB9161920);
            for (int i = 0; i < detailLines.size(); i++) {
                graphics.drawString(client.font, detailLines.get(i), 14, 33 + i * 10, 0xFFFFFFFF, false);
            }
        }
        if (currentMessage == null) {
            return;
        }

        long elapsed = System.nanoTime() - shownAtNanos;
        if (elapsed >= DISPLAY_DURATION_NANOS) {
            currentMessage = null;
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int width = Math.min(PANEL_WIDTH, screenWidth - 16);
        if (width < 180) {
            return;
        }

        List<FormattedCharSequence> lines = client.font.split(
            Component.literal(currentMessage),
            width - 28
        );
        int contentLines = Math.max(1, lines.size());
        int height = 36 + contentLines * 11;
        double enterProgress = Math.min(1.0, elapsed / 250_000_000.0);
        int y = 18 - (int) Math.round((1.0 - enterProgress) * 10.0);
        int x = (screenWidth - width) / 2;
        int centerX = x + width / 2;
        int remainingBarWidth = (int) Math.round(
            (width - 2) * (1.0 - (double) elapsed / DISPLAY_DURATION_NANOS)
        );

        graphics.fill(x, y, x + width, y + height, 0xE9161920);
        graphics.fill(x, y, x + width, y + 2, 0xFFFFB74A);
        graphics.fill(x, y + height - 2, x + 1 + remainingBarWidth, y + height, 0xFF54D6A0);
        graphics.drawCenteredString(
            client.font,
            Component.literal("МИНУТНОЕ СОБЫТИЕ"),
            centerX,
            y + 7,
            0xFFFFC66D
        );

        for (int i = 0; i < lines.size(); i++) {
            graphics.drawCenteredString(
                client.font,
                lines.get(i),
                centerX,
                y + 21 + i * 11,
                0xFFF4F1E9
            );
        }
    }
}
