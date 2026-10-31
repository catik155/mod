package ru.catk.minuteevents;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class MinuteEventsClient implements ClientModInitializer {
    private static final long DISPLAY_DURATION_NANOS = 7_000_000_000L;
    private static final int PANEL_WIDTH = 360;
    private static final int DETAIL_WIDTH = 270;

    private static String currentMessage;
    private static long shownAtNanos;
    private static int secondsUntilEvent = 60;
    private static String timerDetail = "Шансы категорий";
    private static boolean timerVisible = true;
    private static boolean promptedForLan = false;

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
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                promptedForLan = false;
                return;
            }
            boolean isHost = client.getSingleplayerServer() != null || client.player.hasPermissions(2);
            if (isHost && !promptedForLan) {
                promptedForLan = true;
                client.setScreen(new GameplaySettingsScreen());
            }
        });
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
            int timerWidth = Math.max(112, client.font.width(timerText) + 48);
            graphics.fill(7, 7, 7 + timerWidth, 31, 0xE8101724);
            graphics.fill(7, 7, 9, 31, 0xFFFFB74A);
            graphics.drawString(client.font, "ДО СОБЫТИЯ", 15, 11, 0xFFB8C7D9, false);
            graphics.drawString(client.font, timerText, 15, 20, 0xFFFFD27A, false);
        }
        if (!timerDetail.isBlank()) {
            String[] detailParts = timerDetail.split("\\n", 2);
            String lastEvent = detailParts.length > 1 && detailParts[0].startsWith("Последнее событие:")
                ? detailParts[0]
                : "";
            String detailText = lastEvent.isEmpty() ? timerDetail : detailParts[1];

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int timerWidth = timerVisible
                ? Math.max(112, client.font.width("00:00") + 48)
                : 0;

            if (!lastEvent.isBlank()) {
                int panelX = timerVisible ? 7 + timerWidth + 8 : 7;
                int availableWidth = Math.max(90, screenWidth - panelX - 7);
                List<FormattedCharSequence> lastLines = client.font.split(
                    Component.literal(lastEvent), Math.max(70, availableWidth - 20)
                );
                int panelWidth = Math.min(availableWidth,
                    Math.max(130, lastLines.stream().mapToInt(client.font::width).max().orElse(100) + 20));
                int panelHeight = Math.max(24, lastLines.size() * 10 + 14);
                int panelTop = timerVisible ? 7 : 8;
                graphics.fill(panelX, panelTop, panelX + panelWidth, panelTop + panelHeight, 0xE8101724);
                graphics.fill(panelX, panelTop, panelX + 2, panelTop + panelHeight, 0xFF54D6A0);
                for (int i = 0; i < lastLines.size(); i++) {
                    graphics.drawString(client.font, lastLines.get(i), panelX + 10, panelTop + 7 + i * 10, 0xFFF1F4F8, false);
                }
            }

            if (!detailText.isBlank()) {
                List<FormattedCharSequence> detailLines = client.font.split(
                    Component.literal(detailText), DETAIL_WIDTH - 18
                );
                int detailHeight = Math.max(24, detailLines.size() * 9 + 18);
                int detailWidth = Math.min(DETAIL_WIDTH,
                    Math.max(130, detailLines.stream().mapToInt(client.font::width).max().orElse(110) + 18));
                int top = timerVisible || !lastEvent.isBlank() ? 36 : 8;
                graphics.fill(7, top, 7 + detailWidth, top + detailHeight, 0xE8101724);
                graphics.fill(7, top, 7 + detailWidth, top + 2, 0xFF54D6A0);
                graphics.drawString(client.font, "ХРОНИКА СОБЫТИЙ", 15, top + 5, 0xFF70DDB5, false);
                for (int i = 0; i < detailLines.size(); i++) {
                    graphics.drawString(client.font, detailLines.get(i), 15, top + 15 + i * 9, 0xFFF1F4F8, false);
                }
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
