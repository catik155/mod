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
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biomes;
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
    private final Map<UUID, java.util.List<EventCandidate>> upcomingCandidates = new HashMap<>();
    private final Map<UUID, Integer> badEventStreaks = new HashMap<>();

    private record EventCandidate(MinuteEvent event, int weight) {
        double percent(int totalWeight) {
            return totalWeight == 0 ? 0.0 : weight * 100.0 / totalWeight;
        }
    }
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
                        ServerPlayNetworking.send(player, new EventTimerPayload(remaining, timerVisible, blindMode ? "" : timerDisplayFor(player)));
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
                .then(Commands.literal("chances")
                    .executes(context -> showEventChances(context.getSource())))
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
        upcomingCandidates.clear();
        String message = switch (mode) {
            case "preview" -> "Режим предпросмотра включён: таймер показывает следующее событие.";
            case "blind" -> "Слепой режим включён: видно только время до события.";
            default -> "Режим процентов включён: точные шансы каждого события смотри командой /minuteevent chances.";
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
            java.util.List<EventCandidate> candidates = candidatesFor(player);
            int totalWeight = candidates.stream().mapToInt(EventCandidate::weight).sum();
            StringBuilder display = new StringBuilder("10 возможных событий:");
            for (EventCandidate candidate : candidates) {
                display.append("\\n")
                    .append(candidate.event().displayName())
                    .append(" — ")
                    .append(String.format(java.util.Locale.forLanguageTag("ru-RU"), "%.1f%%", candidate.percent(totalWeight)));
            }
            return display.toString();
        }
        return "Точные шансы: /minuteevent chances";
    }

    private double eventChance(MinuteEvent event, int badStreak) {
        if (badStreak >= 3) {
            if (!event.isGood()) return 0.0;
            long goodCount = java.util.Arrays.stream(MinuteEvent.values()).filter(MinuteEvent::isGood).count();
            return 100.0 / Math.max(1, goodCount);
        }
        long categoryCount = java.util.Arrays.stream(MinuteEvent.values())
            .filter(candidate -> candidate.category() == event.category()).count();
        int categoryChance;
        if (event.category() == EventCategory.OTHER) {
            int configured = 0;
            for (EventCategory category : EventCategory.configurableValues()) {
                if (categoryEnabled.getOrDefault(category, true)) {
                    configured += categoryChances.getOrDefault(category, category.defaultChance());
                }
            }
            categoryChance = Math.max(0, 100 - configured);
        } else {
            categoryChance = categoryEnabled.getOrDefault(event.category(), true)
                ? categoryChances.getOrDefault(event.category(), event.category().defaultChance()) : 0;
        }
        return (double) categoryChance / Math.max(1, categoryCount);
    }

    private int showEventChances(CommandSourceStack source) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        int badStreak = player == null ? 0 : badEventStreaks.getOrDefault(player.getUUID(), 0);
        EnumMap<EventCategory, Integer> counts = new EnumMap<>(EventCategory.class);
        int goodCount = 0;
        for (MinuteEvent event : MinuteEvent.values()) {
            counts.merge(event.category(), 1, Integer::sum);
            if (event.isGood()) goodCount++;
        }
        source.sendSuccess(() -> Component.literal("Точные шансы следующего события:"), false);
        if (badStreak >= 3) {
            source.sendSuccess(() -> Component.literal("После 3 плохих событий гарантируется хорошее."), false);
        }
        for (MinuteEvent event : MinuteEvent.values()) {
            double chance;
            if (badStreak >= 3) {
                chance = event.isGood() ? 100.0 / Math.max(1, goodCount) : 0.0;
            } else {
                EventCategory category = event.category();
                int categoryChance;
                if (category == EventCategory.OTHER) {
                    int configured = 0;
                    for (EventCategory configuredCategory : EventCategory.configurableValues()) {
                        if (categoryEnabled.getOrDefault(configuredCategory, true)) {
                            configured += categoryChances.getOrDefault(configuredCategory, configuredCategory.defaultChance());
                        }
                    }
                    categoryChance = Math.max(0, 100 - configured);
                } else {
                    categoryChance = categoryEnabled.getOrDefault(category, true)
                        ? categoryChances.getOrDefault(category, category.defaultChance()) : 0;
                }
                chance = (double) categoryChance / Math.max(1, counts.getOrDefault(category, 1));
            }
            String line = String.format(java.util.Locale.forLanguageTag("ru-RU"), "%s — %.1f%%", event.displayName(), chance);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
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
            java.util.List<EventCandidate> candidates = candidatesFor(player);
            int totalWeight = candidates.stream().mapToInt(EventCandidate::weight).sum();
            int roll = ThreadLocalRandom.current().nextInt(Math.max(1, totalWeight));
            MinuteEvent event = candidates.get(0).event();
            for (EventCandidate candidate : candidates) {
                roll -= candidate.weight();
                if (roll < 0) {
                    event = candidate.event();
                    break;
                }
            }
            upcomingCandidates.remove(player.getUUID());
            int streak = badEventStreaks.getOrDefault(player.getUUID(), 0);
            badEventStreaks.put(player.getUUID(), event.isGood() ? 0 : streak + 1);
            String description = event.apply(player);
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


    private java.util.List<EventCandidate> candidatesFor(ServerPlayer player) {
        return upcomingCandidates.computeIfAbsent(player.getUUID(), ignored -> {
            boolean forceGood = badEventStreaks.getOrDefault(player.getUUID(), 0) >= 3;
            java.util.EnumSet<MinuteEvent> chosen = java.util.EnumSet.noneOf(MinuteEvent.class);
            int attempts = 0;
            while (chosen.size() < 10 && attempts++ < 500) {
                MinuteEvent candidate;
                if (forceGood) {
                    candidate = MinuteEvent.randomGoodEvent();
                } else if (ThreadLocalRandom.current().nextInt(100) < 30) {
                    if (player.serverLevel().dimension() == Level.NETHER) {
                        candidate = randomNetherEvent(player);
                    } else if (player.serverLevel().dimension() == Level.END) {
                        candidate = randomEndEvent(player);
                    } else {
                        candidate = randomOverworldEvent(player);
                    }
                } else {
                    candidate = selectEvent();
                }
                if (!forceGood || candidate.isGood()) chosen.add(candidate);
            }
            if (chosen.size() < 10) {
                for (MinuteEvent candidate : MinuteEvent.values()) {
                    if ((!forceGood || candidate.isGood()) && chosen.size() < 10) chosen.add(candidate);
                }
            }
            java.util.ArrayList<EventCandidate> weighted = new java.util.ArrayList<>();
            for (MinuteEvent candidate : chosen) {
                weighted.add(new EventCandidate(candidate, ThreadLocalRandom.current().nextInt(1, 101)));
            }
            return java.util.List.copyOf(weighted);
        });
    }

    private MinuteEvent[] netherEventsFor(ServerPlayer player) {
        var biome = player.serverLevel().getBiome(player.blockPosition());
        if (biome.is(Biomes.SOUL_SAND_VALLEY)) {
            return new MinuteEvent[] {MinuteEvent.NETHER_SOUL_VALLEY_SKELETONS, MinuteEvent.NETHER_FIRE_RESISTANCE, MinuteEvent.NETHER_GOLD_GIFT, MinuteEvent.NETHER_SOULFIRE_BLESSING};
        }
        if (biome.is(Biomes.CRIMSON_FOREST)) {
            return new MinuteEvent[] {MinuteEvent.NETHER_CRIMSON_ROOTS, MinuteEvent.NETHER_HOGLIN_SCOUT, MinuteEvent.NETHER_PIGLIN_VISIT, MinuteEvent.NETHER_GOLD_GIFT, MinuteEvent.NETHER_PIGLIN_BRUTE_TRIBUTE, MinuteEvent.NETHER_SOULFIRE_BLESSING, MinuteEvent.NETHER_WITHER_SKELETON_PATROL};
        }
        if (biome.is(Biomes.WARPED_FOREST)) {
            return new MinuteEvent[] {MinuteEvent.NETHER_WARPED_ENDERMEN, MinuteEvent.NETHER_FIRE_RESISTANCE, MinuteEvent.NETHER_STRIDER_RIDE, MinuteEvent.NETHER_GHAST_SHADOW, MinuteEvent.NETHER_WITHER_SKELETON_PATROL};
        }
        if (biome.is(Biomes.BASALT_DELTAS)) {
            return new MinuteEvent[] {MinuteEvent.NETHER_BASALT_MAGMA, MinuteEvent.NETHER_MAGMA_CUBES, MinuteEvent.NETHER_FIRE_RESISTANCE, MinuteEvent.NETHER_GHAST_SHADOW};
        }
        return new MinuteEvent[] {MinuteEvent.NETHER_PIGLIN_VISIT, MinuteEvent.NETHER_MAGMA_CUBES, MinuteEvent.NETHER_FIRE_RESISTANCE, MinuteEvent.NETHER_GOLD_GIFT, MinuteEvent.NETHER_HOGLIN_SCOUT, MinuteEvent.NETHER_STRIDER_RIDE};
    }

    private MinuteEvent[] endEventsFor(ServerPlayer player) {
        var biome = player.serverLevel().getBiome(player.blockPosition());
        if (biome.is(Biomes.END_HIGHLANDS)) {
            return new MinuteEvent[] {MinuteEvent.END_HIGH_ISLAND_SHULKER, MinuteEvent.END_PEARL_GIFT, MinuteEvent.END_SLOW_FALL, MinuteEvent.END_ENDERMITE_AMBUSH, MinuteEvent.END_SHULKER_CIRCLE, MinuteEvent.END_VOID_GIFT};
        }
        if (biome.is(Biomes.END_MIDLANDS)) {
            return new MinuteEvent[] {MinuteEvent.ENDERMAN_VISIT, MinuteEvent.END_CHORUS_BLOOM, MinuteEvent.END_ENDERMITE_AMBUSH, MinuteEvent.END_SLOW_FALL, MinuteEvent.END_ENDERMAN_CARAVAN, MinuteEvent.END_CHORUS_TELEPORT};
        }
        if (biome.is(Biomes.SMALL_END_ISLANDS) || biome.is(Biomes.END_BARRENS)) {
            return new MinuteEvent[] {MinuteEvent.END_CHORUS_GIFT, MinuteEvent.END_PEARL_GIFT, MinuteEvent.END_SLOW_FALL, MinuteEvent.ENDERMAN_VISIT, MinuteEvent.END_VOID_GIFT, MinuteEvent.END_PHANTOM_ECLIPSE};
        }
        return new MinuteEvent[] {MinuteEvent.ENDERMAN_VISIT, MinuteEvent.END_SHULKER_SURPRISE, MinuteEvent.END_CHORUS_GIFT, MinuteEvent.END_SLOW_FALL, MinuteEvent.END_PEARL_GIFT, MinuteEvent.END_SHULKER_CIRCLE, MinuteEvent.END_ENDERMAN_CARAVAN, MinuteEvent.END_CHORUS_TELEPORT, MinuteEvent.END_PHANTOM_ECLIPSE};
    }

    private boolean hasNearbyBlock(ServerPlayer player, net.minecraft.world.level.block.Block block) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            if (player.serverLevel().getBlockState(pos).is(block)) return true;
        }
        return false;
    }

    private MinuteEvent randomOverworldEvent(ServerPlayer player) {
        var biome = player.serverLevel().getBiome(player.blockPosition());
        MinuteEvent[] events;
        if (biome.is(Biomes.DESERT)) {
            events = new MinuteEvent[] {MinuteEvent.DESERT_HUSK_PATROL, MinuteEvent.BADLANDS_GOLD_GIFT, MinuteEvent.QUICK_FEET, MinuteEvent.DESERT_CAMEL_CARAVAN};
        } else if (biome.is(Biomes.BADLANDS) || biome.is(Biomes.ERODED_BADLANDS) || biome.is(Biomes.WOODED_BADLANDS)) {
            events = new MinuteEvent[] {MinuteEvent.BADLANDS_GOLD_GIFT, MinuteEvent.DESERT_HUSK_PATROL, MinuteEvent.SECOND_WIND};
        } else if (biome.is(Biomes.JUNGLE) || biome.is(Biomes.BAMBOO_JUNGLE) || biome.is(Biomes.SPARSE_JUNGLE)) {
            events = new MinuteEvent[] {MinuteEvent.JUNGLE_PARROT_VISIT, MinuteEvent.JUNGLE_OCELOT_VISIT, MinuteEvent.FRIENDLY_VISITOR, MinuteEvent.FOREST_BEE_COURIER};
        } else if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
            events = new MinuteEvent[] {MinuteEvent.SWAMP_SLIME_VISIT, MinuteEvent.SECOND_WIND, MinuteEvent.WATER_NEARBY_BREATHING};
        } else if (biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.SNOWY_TAIGA) || biome.is(Biomes.ICE_SPIKES)) {
            events = new MinuteEvent[] {MinuteEvent.SNOWY_STRAY_PATROL, MinuteEvent.QUICK_FEET, MinuteEvent.SECOND_WIND, MinuteEvent.SNOW_FOX_COURIER};
        } else if (biome.is(Biomes.FROZEN_PEAKS) || biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.STONY_PEAKS)) {
            events = new MinuteEvent[] {MinuteEvent.MOUNTAIN_GOAT_VISIT, MinuteEvent.SNOWY_STRAY_PATROL, MinuteEvent.SECOND_WIND};
        } else if (biome.is(Biomes.OCEAN) || biome.is(Biomes.DEEP_OCEAN) || biome.is(Biomes.COLD_OCEAN) || biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(Biomes.FROZEN_OCEAN)) {
            events = new MinuteEvent[] {MinuteEvent.OCEAN_DOLPHIN_VISIT, MinuteEvent.WATER_NEARBY_BREATHING, MinuteEvent.END_PEARL_GIFT, MinuteEvent.OCEAN_GUARDIAN_WARNING};
        } else if (biome.is(Biomes.MUSHROOM_FIELDS)) {
            events = new MinuteEvent[] {MinuteEvent.MUSHROOM_COW_VISIT, MinuteEvent.SECOND_WIND, MinuteEvent.FOOD_GIFT};
        } else if (biome.is(Biomes.CHERRY_GROVE)) {
            events = new MinuteEvent[] {MinuteEvent.CHERRY_BLOSSOM_GIFT, MinuteEvent.MOUNTAIN_GOAT_VISIT, MinuteEvent.FRIENDLY_VISITOR};
        } else if (player.blockPosition().getY() < 45) {
            events = new MinuteEvent[] {MinuteEvent.CAVE_AMETHYST_RESONANCE, MinuteEvent.CAVE_BAT_ORACLE, MinuteEvent.CAVE_SILVERFISH_BREAKOUT, MinuteEvent.SECOND_WIND};
        } else if (biome.is(Biomes.FOREST) || biome.is(Biomes.FLOWER_FOREST) || biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.DARK_FOREST)) {
            events = new MinuteEvent[] {MinuteEvent.FOREST_WOLF_CIRCLE, MinuteEvent.FOREST_BEE_COURIER, MinuteEvent.FRIENDLY_VISITOR};
        } else if (biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS)) {
            events = new MinuteEvent[] {MinuteEvent.PLAINS_HORSE_CARAVAN, MinuteEvent.ANIMAL_HERD, MinuteEvent.FRIENDLY_VISITOR};
        } else if (biome.is(Biomes.RIVER) || biome.is(Biomes.FROZEN_RIVER)) {
            events = new MinuteEvent[] {MinuteEvent.RIVER_FROG_CHORUS, MinuteEvent.WATER_NEARBY_BREATHING, MinuteEvent.OCEAN_DOLPHIN_VISIT};
        } else if (biome.is(Biomes.BEACH) || biome.is(Biomes.SNOWY_BEACH)) {
            events = new MinuteEvent[] {MinuteEvent.BEACH_TURTLE_ESCORT, MinuteEvent.WATER_NEARBY_BREATHING, MinuteEvent.FOOD_GIFT};
        } else {
            if (hasNearbyBlock(player, Blocks.LAVA)) events = new MinuteEvent[] {MinuteEvent.LAVA_NEARBY_FIRE_RESISTANCE, MinuteEvent.NETHER_FIRE_RESISTANCE, MinuteEvent.SECOND_WIND};
            else if (hasNearbyBlock(player, Blocks.WATER)) events = new MinuteEvent[] {MinuteEvent.WATER_NEARBY_BREATHING, MinuteEvent.OCEAN_DOLPHIN_VISIT, MinuteEvent.SECOND_WIND};
            else events = new MinuteEvent[] {MinuteEvent.ANIMAL_HERD, MinuteEvent.FRIENDLY_VISITOR, MinuteEvent.QUICK_FEET, MinuteEvent.SECOND_WIND, MinuteEvent.JUNGLE_PARROT_VISIT};
        }
        if (hasNearbyBlock(player, Blocks.LAVA) && ThreadLocalRandom.current().nextBoolean()) return MinuteEvent.LAVA_NEARBY_FIRE_RESISTANCE;
        if (hasNearbyBlock(player, Blocks.WATER) && ThreadLocalRandom.current().nextBoolean()) return MinuteEvent.WATER_NEARBY_BREATHING;
        return events[ThreadLocalRandom.current().nextInt(events.length)];
    }

    private MinuteEvent randomNetherEvent(ServerPlayer player) {
        MinuteEvent[] events = netherEventsFor(player);
        return events[ThreadLocalRandom.current().nextInt(events.length)];
    }

    private MinuteEvent randomNetherGoodEvent() {
        MinuteEvent[] events = {MinuteEvent.NETHER_FIRE_RESISTANCE, MinuteEvent.NETHER_GOLD_GIFT};
        return events[ThreadLocalRandom.current().nextInt(events.length)];
    }

    private MinuteEvent randomEndEvent(ServerPlayer player) {
        MinuteEvent[] events = endEventsFor(player);
        return events[ThreadLocalRandom.current().nextInt(events.length)];
    }

    private MinuteEvent randomEndGoodEvent() {
        MinuteEvent[] events = {MinuteEvent.END_CHORUS_GIFT, MinuteEvent.END_SLOW_FALL, MinuteEvent.END_PEARL_GIFT, MinuteEvent.END_CHORUS_BLOOM, MinuteEvent.END_VOID_GIFT, MinuteEvent.END_CHORUS_TELEPORT};
        return events[ThreadLocalRandom.current().nextInt(events.length)];
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
