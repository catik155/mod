package ru.catk.minuteevents;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

public final class MinuteEventsMod implements ModInitializer {
    public static final String MOD_ID = "minute_events";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int TICKS_PER_SECOND = 20;
    private static final int DEFAULT_INTERVAL_SECONDS = 60;
    private static final int MIN_INTERVAL_SECONDS = 10;
    private static final int MAX_INTERVAL_SECONDS = 3600;

    private boolean enabled = true;
    private int intervalSeconds = DEFAULT_INTERVAL_SECONDS;
    private int elapsedTicks;
    private int timerSyncTicks;
    private boolean previewMode = false;
    private boolean blindMode = false;
    private boolean timerVisible = true;
    private final Map<UUID, MinuteEvent> upcomingEvents = new HashMap<>();
    private final Map<UUID, Integer> badEventStreaks = new HashMap<>();
    private Path configPath;
    private final EnumMap<EventCategory, Boolean> categoryEnabled = defaultCategoryEnabled();
    private final EnumMap<EventCategory, Integer> categoryChances = defaultCategoryChances();

    @Override
    public void onInitialize() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("minute-events.properties");
        loadConfig();
        PayloadTypeRegistry.playS2C().register(
            EventNotificationPayload.TYPE,
            EventNotificationPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
            EventTimerPayload.TYPE,
            EventTimerPayload.CODEC
        );
        registerCommands();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!enabled || server.getPlayerList().getPlayers().isEmpty()) {
                return;
            }

            elapsedTicks++;
            timerSyncTicks++;
            if (timerSyncTicks >= TICKS_PER_SECOND) {
                timerSyncTicks = 0;
                int remaining = Math.max(0, intervalSeconds - elapsedTicks / TICKS_PER_SECOND);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (ServerPlayNetworking.canSend(player, EventTimerPayload.TYPE)) {
                        ServerPlayNetworking.send(player, new EventTimerPayload(remaining, timerVisible ? timerDisplayFor(player) : "__HIDE_TIMER__"));
                    }
                }
            }
            if (elapsedTicks >= intervalSeconds * TICKS_PER_SECOND) {
                elapsedTicks = 0;
                triggerEvents(server);
            }
        });

        LOGGER.info("Минутные события загружены. Интервал: {} сек.", intervalSeconds);
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("minuteevent")
                .executes(context -> showStatus(context.getSource()))
                .then(Commands.literal("mode")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("preview")
                        .executes(context -> setPreviewMode(context.getSource(), true)))
                    .then(Commands.literal("chance")
                        .executes(context -> setDisplayMode(context.getSource(), "chance")))
                    .then(Commands.literal("blind")
                        .executes(context -> setDisplayMode(context.getSource(), "blind"))))
                .then(Commands.literal("timer")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("on")
                        .executes(context -> setTimerVisible(context.getSource(), true)))
                    .then(Commands.literal("off")
                        .executes(context -> setTimerVisible(context.getSource(), false))))
                .then(Commands.literal("status")
                    .executes(context -> showStatus(context.getSource())))
                .then(Commands.literal("on")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> setEnabled(context.getSource(), true)))
                .then(Commands.literal("off")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> setEnabled(context.getSource(), false)))
                .then(Commands.literal("now")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> triggerNow(context.getSource())))
                .then(Commands.literal("interval")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("секунды",
                            IntegerArgumentType.integer(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS))
                        .executes(context -> setInterval(
                            context.getSource(),
                            IntegerArgumentType.getInteger(context, "секунды")
                        ))))
                .then(Commands.literal("category")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("category", StringArgumentType.word())
                        .then(Commands.literal("on")
                            .executes(context -> setCategoryEnabled(
                                context.getSource(),
                                StringArgumentType.getString(context, "category"),
                                true
                            )))
                        .then(Commands.literal("off")
                            .executes(context -> setCategoryEnabled(
                                context.getSource(),
                                StringArgumentType.getString(context, "category"),
                                false
                            )))))
                .then(Commands.literal("chance")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("category", StringArgumentType.word())
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                            .executes(context -> setCategoryChance(
                                context.getSource(),
                                StringArgumentType.getString(context, "category"),
                                IntegerArgumentType.getInteger(context, "percent")
                            )))))
            )
        );
    }

    private int setPreviewMode(CommandSourceStack source, boolean value) {
        return setDisplayMode(source, value ? "preview" : "chance");
    }

    private int setDisplayMode(CommandSourceStack source, String mode) {
        previewMode = mode.equals("preview");
        blindMode = mode.equals("blind");
        upcomingEvents.clear();
        String message = switch (mode) {
            case "preview" -> "Режим предпросмотра включён: таймер показывает следующее событие.";
            case "blind" -> "Слепой режим включён: видно только время до события.";
            default -> "Обычный режим включён: отображаются шансы категорий и названия событий.";
        };
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private int setTimerVisible(CommandSourceStack source, boolean value) {
        timerVisible = value;
        source.sendSuccess(() -> Component.literal(value
            ? "Таймер HUD включён."
            : "Таймер HUD выключен. Включить снова: /minuteevent timer on"), true);
        return 1;
    }

    private String timerDisplayFor(ServerPlayer player) {
        if (blindMode) {
            return "Событие скрыто";
        }
        if (previewMode) {
            MinuteEvent event = upcomingEvents.computeIfAbsent(player.getUUID(), ignored -> {
                int streak = badEventStreaks.getOrDefault(player.getUUID(), 0);
                return streak >= 3 ? MinuteEvent.randomGoodEvent() : selectEvent();
            });
            return "Будет: " + event.displayName();
        }
        return "Шансы категорий: " + categoryChanceSummary();
    }

    private String categoryChanceSummary() {
        int cumulative = 0;
        StringBuilder text = new StringBuilder();
        for (EventCategory category : EventCategory.configurableValues()) {
            int chance = categoryEnabled.getOrDefault(category, true)
                ? categoryChances.getOrDefault(category, category.defaultChance()) : 0;
            int before = Math.min(100, cumulative);
            cumulative += chance;
            int after = Math.min(100, cumulative);
            int effectiveChance = Math.max(0, after - before);
            if (text.length() > 0) text.append(" | ");
            text.append(category.displayName()).append(": ").append(effectiveChance).append("%");
        }
        int otherChance = Math.max(0, 100 - Math.min(100, cumulative));
        if (text.length() > 0) text.append(" | ");
        text.append("Другое: ").append(otherChance).append("%");
        return text.toString();
    }

    private int showStatus(CommandSourceStack source) {
        String status = enabled ? "включены" : "выключены";
        int remaining = Math.max(0, intervalSeconds * TICKS_PER_SECOND - elapsedTicks)
            / TICKS_PER_SECOND;
        source.sendSuccess(
            () -> Component.literal("Минутные события " + status
                + ". Интервал: " + intervalSeconds + " сек. До следующего: " + remaining + " сек."
                + " Режим: " + (previewMode ? "предпросмотр" : blindMode ? "слепой" : "проценты")
                + " Категории: " + categoryStatus()),
            false
        );
        return 1;
    }

    private int setEnabled(CommandSourceStack source, boolean value) {
        enabled = value;
        elapsedTicks = 0;
        boolean saved = saveConfig();
        String message = value ? "События включены." : "События выключены.";
        source.sendSuccess(() -> Component.literal(message + persistenceNote(saved)), true);
        return 1;
    }

    private int setInterval(CommandSourceStack source, int seconds) {
        intervalSeconds = seconds;
        elapsedTicks = 0;
        boolean saved = saveConfig();
        source.sendSuccess(
            () -> Component.literal("Интервал событий установлен: " + seconds + " сек."
                + persistenceNote(saved)),
            true
        );
        return 1;
    }

    private int setCategoryEnabled(CommandSourceStack source, String categoryKey, boolean value) {
        EventCategory category = EventCategory.fromConfigKey(categoryKey);
        if (category == null) {
            source.sendFailure(Component.literal("Неизвестная категория. Используйте: blocks, cages, walls или mobs."));
            return 0;
        }

        categoryEnabled.put(category, value);
        boolean saved = saveConfig();
        source.sendSuccess(
            () -> Component.literal("Категория «" + category.displayName() + "» "
                + (value ? "включена." : "выключена.") + persistenceNote(saved)),
            true
        );
        return 1;
    }

    private int setCategoryChance(CommandSourceStack source, String categoryKey, int percent) {
        EventCategory category = EventCategory.fromConfigKey(categoryKey);
        if (category == null) {
            source.sendFailure(Component.literal("Неизвестная категория. Используйте: blocks, cages, walls или mobs."));
            return 0;
        }

        int totalChance = categoryChances.values().stream().mapToInt(Integer::intValue).sum();
        int updatedTotal = totalChance - categoryChances.get(category) + percent;
        if (updatedTotal > 100) {
            source.sendFailure(Component.literal(
                "Сумма вероятностей категорий не может превышать 100% (сейчас получилось "
                    + updatedTotal + "%)."
            ));
            return 0;
        }

        categoryChances.put(category, percent);
        boolean saved = saveConfig();
        source.sendSuccess(
            () -> Component.literal("Вероятность категории «" + category.displayName()
                + "» установлена: " + percent + "%." + persistenceNote(saved)),
            true
        );
        return 1;
    }

    private String categoryStatus() {
        StringBuilder status = new StringBuilder();
        for (EventCategory category : EventCategory.configurableValues()) {
            if (status.length() > 0) {
                status.append("; ");
            }
            status.append(category.displayName())
                .append(": ")
                .append(categoryEnabled.get(category) ? "вкл." : "выкл.")
                .append(", ")
                .append(categoryChances.get(category))
                .append("%");
        }
        return status.toString();
    }

    private int triggerNow(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) {
            source.sendFailure(Component.literal("Сейчас на сервере нет игроков."));
            return 0;
        }

        elapsedTicks = 0;
        triggerEvents(server);
        return 1;
    }

    private void triggerEvents(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MinuteEvent event = previewMode ? upcomingEvents.remove(player.getUUID()) : null;
            int badStreak = badEventStreaks.getOrDefault(player.getUUID(), 0);
            if (badStreak >= 3) {
                if (event == null || !event.isGood()) event = MinuteEvent.randomGoodEvent();
            } else if (event == null) {
                event = selectEvent();
            }
            String description = event.apply(player);
            if (event.isGood()) {
                badEventStreaks.put(player.getUUID(), 0);
            } else {
                badEventStreaks.put(player.getUUID(), badStreak + 1);
            }
            if (ServerPlayNetworking.canSend(player, EventNotificationPayload.TYPE)) {
                ServerPlayNetworking.send(player, new EventNotificationPayload(description));
            } else {
                player.displayClientMessage(
                    Component.literal("[Минутное событие] " + description),
                    true
                );
            }
        }
    }

    private MinuteEvent selectEvent() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        int cumulativeChance = 0;
        for (EventCategory category : EventCategory.configurableValues()) {
            if (!categoryEnabled.get(category)) {
                continue;
            }

            cumulativeChance += categoryChances.get(category);
            if (roll < cumulativeChance) {
                return MinuteEvent.randomForCategory(category);
            }
        }

        return MinuteEvent.randomForCategory(EventCategory.OTHER);
    }

    private void loadConfig() {
        if (!Files.exists(configPath)) {
            saveConfig();
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
            boolean loadedEnabled = parseBoolean(properties, "enabled", true);

            String interval = properties.getProperty(
                "intervalSeconds",
                Integer.toString(DEFAULT_INTERVAL_SECONDS)
            );
            int parsedInterval = Integer.parseInt(interval);
            if (parsedInterval < MIN_INTERVAL_SECONDS || parsedInterval > MAX_INTERVAL_SECONDS) {
                throw new IllegalArgumentException("Интервал вне допустимого диапазона.");
            }

            EnumMap<EventCategory, Boolean> loadedCategoryEnabled = new EnumMap<>(EventCategory.class);
            EnumMap<EventCategory, Integer> loadedCategoryChances = new EnumMap<>(EventCategory.class);
            int totalChance = 0;
            for (EventCategory category : EventCategory.configurableValues()) {
                loadedCategoryEnabled.put(
                    category,
                    parseBoolean(properties, category.configKey() + ".enabled", true)
                );
                int chance = Integer.parseInt(properties.getProperty(
                    category.configKey() + ".chance",
                    Integer.toString(category.defaultChance())
                ));
                if (chance < 0 || chance > 100) {
                    throw new IllegalArgumentException(
                        "Вероятность категории " + category.configKey() + " вне диапазона 0–100."
                    );
                }
                totalChance += chance;
                loadedCategoryChances.put(category, chance);
            }
            if (totalChance > 100) {
                throw new IllegalArgumentException("Сумма вероятностей категорий превышает 100%.");
            }

            enabled = loadedEnabled;
            intervalSeconds = parsedInterval;
            categoryEnabled.clear();
            categoryEnabled.putAll(loadedCategoryEnabled);
            categoryChances.clear();
            categoryChances.putAll(loadedCategoryChances);

            // Rewrite old configuration files with the newly introduced category defaults.
            saveConfig();
        } catch (IOException | IllegalArgumentException exception) {
            enabled = true;
            intervalSeconds = DEFAULT_INTERVAL_SECONDS;
            resetCategorySettings();
            LOGGER.warn("Не удалось прочитать конфигурацию минутных событий. Используются значения по умолчанию.", exception);
            saveConfig();
        }
    }

    private boolean parseBoolean(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        throw new IllegalArgumentException("Некорректное логическое значение параметра " + key + ".");
    }

    private boolean saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("intervalSeconds", Integer.toString(intervalSeconds));
        for (EventCategory category : EventCategory.configurableValues()) {
            properties.setProperty(
                category.configKey() + ".enabled",
                Boolean.toString(categoryEnabled.get(category))
            );
            properties.setProperty(
                category.configKey() + ".chance",
                Integer.toString(categoryChances.get(category))
            );
        }

        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                properties.store(writer, "Minute Events settings");
            }
            return true;
        } catch (IOException exception) {
            LOGGER.error("Не удалось сохранить конфигурацию минутных событий.", exception);
            return false;
        }
    }

    private void resetCategorySettings() {
        categoryEnabled.clear();
        categoryEnabled.putAll(defaultCategoryEnabled());
        categoryChances.clear();
        categoryChances.putAll(defaultCategoryChances());
    }

    private static EnumMap<EventCategory, Boolean> defaultCategoryEnabled() {
        EnumMap<EventCategory, Boolean> defaults = new EnumMap<>(EventCategory.class);
        for (EventCategory category : EventCategory.configurableValues()) {
            defaults.put(category, true);
        }
        return defaults;
    }

    private static EnumMap<EventCategory, Integer> defaultCategoryChances() {
        EnumMap<EventCategory, Integer> defaults = new EnumMap<>(EventCategory.class);
        for (EventCategory category : EventCategory.configurableValues()) {
            defaults.put(category, category.defaultChance());
        }
        return defaults;
    }

    private String persistenceNote(boolean saved) {
        return saved ? "" : " Настройка действует до перезапуска, но не сохранилась в файл.";
    }
}
