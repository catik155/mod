package ru.catk.minuteevents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.concurrent.ThreadLocalRandom;

enum EventCategory {
    BLOCKS("blocks", "разрушение блоков", 17),
    CAGES("cages", "клетки", 17),
    WALLS("walls", "стены", 8),
    MOBS("mobs", "мобы", 17),
    OTHER("other", "обычные события", 0);

    private final String configKey;
    private final String displayName;
    private final int defaultChance;

    EventCategory(String configKey, String displayName, int defaultChance) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.defaultChance = defaultChance;
    }

    String configKey() {
        return configKey;
    }

    String displayName() {
        return displayName;
    }

    int defaultChance() {
        return defaultChance;
    }

    boolean isConfigurable() {
        return this != OTHER;
    }

    static EventCategory fromConfigKey(String key) {
        for (EventCategory category : values()) {
            if (category.isConfigurable() && category.configKey.equals(key)) {
                return category;
            }
        }
        return null;
    }

    static EventCategory[] configurableValues() {
        return new EventCategory[] {BLOCKS, CAGES, WALLS, MOBS};
    }
}

enum MinuteEvent {
    NEARBY_BLOCK_BURST(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) {
            int removed = removeBlockBurst(player, 2, 4, 5, 9);
            return removed > 0
                ? "Рядом с вами что-то подозрительно исчезло. Кто-то явно покопался!"
                : "Рядом с вами ничего не удалось разрушить.";
        }
    },
    DISTANT_BLOCK_BURST(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) {
            int removed = removeBlockBurst(player, 5, 10, 9, 16);
            return removed > 0
                ? "В 5–10 блоках от вас прошёлся невидимый шахтёр. Лучше не спрашивать."
                : "На выбранном участке блоки не удалось разрушить.";
        }
    },
    LARGE_NEARBY_CAVE_IN(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) {
            int removed = removeBlockBurst(player, 2, 4, 14, 22);
            return removed > 0
                ? "Рядом с вами случился обвал. Земля решила обновить интерьер!"
                : "Обвал не нашёл подходящих блоков.";
        }
    },
    WIDE_DISTANT_CAVE_IN(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) {
            int removed = removeBlockBurst(player, 5, 10, 18, 27);
            return removed > 0
                ? "В 5–10 блоках прогремел мощный обвал. Геология сегодня без тормозов!"
                : "Обвал не нашёл подходящих блоков.";
        }
    },
    MINI_BLAST(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) {
            BlockPos center = randomSurfacePosition(player, 5, 10).below();
            int removed = createCrater(player, center, 2);
            return removed > 0
                ? "В 5–10 блоках бабахнуло! Крипер? Нет, просто Minecraft нервничает."
                : "Взрыв прогремел, но блоки устояли.";
        }
    },
    OBSIDIAN_CAGE(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) {
            int placed = buildCage(
                player.serverLevel(),
                player.blockPosition(),
                Blocks.OBSIDIAN.defaultBlockState()
            );
            return placed > 0
                ? "Вы заперты в обсидиановой клетке!"
                : "Обсидиановую клетку построить не удалось.";
        }
    },
    DISTANT_OBSIDIAN_CAGE(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) {
            BlockPos site = randomSurfacePosition(player, 5, 10);
            int placed = buildCage(
                player.serverLevel(),
                site,
                Blocks.OBSIDIAN.defaultBlockState()
            );
            return placed > 0
                ? "В 5–10 блоках выросла прочная обсидиановая клетка!"
                : "Обсидиановую клетку построить не удалось.";
        }
    },
    WEB_SNARE(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) {
            int placed = buildWebSnare(player);
            return placed > 0
                ? "Паутина опутала пространство вокруг вас!"
                : "Паутина не нашла места для ловушки.";
        }
    },
    STONE_SPIKES(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) {
            int placed = buildPillars(player, randomSurfacePosition(player, 2, 4));
            return placed > 0
                ? "Из земли рядом вырвались каменные столбы!"
                : "Каменным столбам не хватило свободного места.";
        }
    },
    SAND_WALL(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) {
            int placed = buildWall(
                player,
                randomSurfacePosition(player, 5, 10),
                Blocks.SAND.defaultBlockState(),
                2,
                3
            );
            return placed > 0
                ? "В 5–10 блоках выросла песчаная стена."
                : "Песчаную стену построить не удалось.";
        }
    },
    STONE_BRIDGE(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) {
            int placed = buildBridge(player, randomSurfacePosition(player, 5, 10));
            return placed > 0
                ? "В 5–10 блоках появился каменный мост."
                : "Мост построить не удалось: участок занят.";
        }
    },
    ANIMAL_HERD(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) {
            int spawned = spawnGroup(
                player,
                new EntityType<?>[] {
                    EntityType.SHEEP,
                    EntityType.COW,
                    EntityType.PIG,
                    EntityType.CHICKEN
                },
                4,
                2,
                5
            );
            return "Стадо животных появилось рядом: " + spawned + ".";
        }
    },
    BEE_SWARM(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) {
            int spawned = spawnGroup(
                player,
                new EntityType<?>[] {EntityType.BEE},
                3,
                2,
                5
            );
            return "Пчелиный рой появился рядом: " + spawned + ".";
        }
    },
    ZOMBIE_PACK(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) {
            int spawned = spawnGroup(
                player,
                new EntityType<?>[] {EntityType.ZOMBIE},
                3,
                5,
                10
            );
            return "Орда зомби появилась неподалёку: " + spawned + "!";
        }
    },
    SKELETON_PACK(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) {
            int spawned = spawnGroup(
                player,
                new EntityType<?>[] {EntityType.SKELETON},
                3,
                5,
                10
            );
            return "Отряд скелетов появился неподалёку: " + spawned + "!";
        }
    },
    SPIDER_PACK(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) {
            int spawned = spawnGroup(
                player,
                new EntityType<?>[] {EntityType.SPIDER},
                3,
                2,
                5
            );
            return "Пауки окружили вас: " + spawned + "!";
        }
    },
    WITCH_ATTACK(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) {
            BlockPos site = randomSurfacePosition(player, 5, 10);
            return EntityType.WITCH.spawn(player.serverLevel(), site, MobSpawnType.EVENT) == null
                ? "Ведьма не смогла появиться."
                : "В 5–10 блоках появилась ведьма!";
        }
    },
    BLINDNESS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 10, 0));
            return "Слепота на 10 секунд!";
        }
    },
    SLUGGISH(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 25, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 25, 0));
            return "Замедление и усталость шахтёра на 25 секунд!";
        }
    },
    POISON(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 10, 0));
            return "Отравление на 10 секунд!";
        }
    },
    WITHER(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 6, 0));
            return "Иссушение на 6 секунд — срочно лечитесь!";
        }
    },
    FIRE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.igniteForSeconds(5.0F);
            return "Вы загорелись на 5 секунд!";
        }
    },
    PLAYER_CAGE(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) {
            int placed = buildIronCage(player.serverLevel(), player.blockPosition());
            return placed > 0
                ? "Вокруг вас выросла железная клетка (" + placed + " блоков)."
                : "Клетку построить не удалось: рядом нет свободного места.";
        }
    },
    DISTANT_CAGE(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) {
            BlockPos site = randomSurfacePosition(player, 5, 10);
            int placed = buildIronCage(player.serverLevel(), site);
            return placed > 0
                ? "В 5–10 блоках от вас появилась железная клетка."
                : "На участке в 5–10 блоках клетку построить не удалось.";
        }
    },
    STONE_BARRICADE(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) {
            int placed = buildStoneWall(player, randomSurfacePosition(player, 5, 10));
            return placed > 0
                ? "В 5–10 блоках от вас выросла каменная стена."
                : "Каменную стену построить не удалось: участок занят.";
        }
    },
    HOSTILE_VISITOR(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) {
            BlockPos site = randomSurfacePosition(player, 5, 10);
            EntityType<?>[] mobs = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};
            EntityType<?> mob = mobs[ThreadLocalRandom.current().nextInt(mobs.length)];
            return mob.spawn(player.serverLevel(), site, MobSpawnType.EVENT) == null
                ? "Враждебного моба призвать не удалось."
                : "В 5–10 блоках от вас появился враждебный моб!";
        }
    },
    FRIENDLY_VISITOR(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) {
            BlockPos site = randomSurfacePosition(player, 2, 4);
            EntityType<?>[] animals = {
                EntityType.SHEEP,
                EntityType.COW,
                EntityType.PIG,
                EntityType.CHICKEN
            };
            EntityType<?> animal = animals[ThreadLocalRandom.current().nextInt(animals.length)];
            return animal.spawn(player.serverLevel(), site, MobSpawnType.EVENT) == null
                ? "Дружелюбное животное призвать не удалось."
                : "Рядом появилось дружелюбное животное.";
        }
    },
    SMALL_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            ItemStack[] gifts = {
                new ItemStack(Items.BREAD, 2),
                new ItemStack(Items.TORCH, 8),
                new ItemStack(Items.IRON_INGOT, 1),
                new ItemStack(Items.COOKED_BEEF, 2)
            };
            giveItem(player, gifts[ThreadLocalRandom.current().nextInt(gifts.length)]);
            sparkle(player);
            return "Вам доставили небольшой подарок!";
        }
    },
    QUICK_FEET(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 30, 0));
            sparkle(player);
            return "На 30 секунд вы стали быстрее!";
        }
    },
    SECOND_WIND(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.heal(4.0F);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 0));
            sparkle(player);
            return "Передышка: здоровье восстановлено, регенерация усилена!";
        }
    },
    STAR_BURST(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            sparkle(player);
            return "Над вами вспыхнули звёздные искры!";
        }
    },
    CREEPER_SQUAD(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.CREEPER}, 3, 5, 9); return "Криперы уже рядом: " + n + "!";
        }
    },
    ENDERMAN_HUNT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.ENDERMAN}, 2, 5, 9); return "Эндермены вышли на охоту: " + n + "!";
        }
    },
    SLIME_INVASION(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.SLIME}, 5, 3, 8); return "Слизневая атака! Слизней: " + n;
        }
    },
    PHANTOM_SWARM(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.PHANTOM}, 4, 4, 9); return "Фантомы кружат над вами: " + n + "!";
        }
    },
    DROWNED_RAID(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.DROWNED}, 4, 4, 9); return "Утопленники напали: " + n + "!";
        }
    },
    PILLAGER_PATROL(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.PILLAGER}, 4, 6, 12); return "Разбойничий патруль обнаружен: " + n + "!";
        }
    },
    VINDICATOR_HUNT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.VINDICATOR}, 3, 5, 9); return "Победители устроили охоту: " + n + "!";
        }
    },
    BLAZE_AMBUSH(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.BLAZE}, 3, 5, 9); return "Засада ифритов! Количество: " + n;
        }
    },
    WOLF_GUARDIANS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.WOLF}, 4, 2, 5); return "Стая волков появилась рядом: " + n + ". Возможно, они не рады вам!";
        }
    },
    IRON_GOLEM_SQUAD(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.IRON_GOLEM}, 2, 3, 7); return "Железная охрана прибыла: " + n + " голема!";
        }
    },
    RAVAGER_CHARGE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.RAVAGER}, 1, 7, 12); return "Разоритель несётся на вас!";
        }
    },
    GUARDIAN_PATROL(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.GUARDIAN}, 3, 5, 9); return "Стражи появились неподалёку: " + n;
        }
    },
    FOX_FRIENDS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.FOX}, 3, 2, 5); return "Лисья компания заглянула в гости: " + n;
        }
    },
    HORSE_DELIVERY(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.HORSE}, 2, 2, 5); return "Доставка лошадей! Прибыло: " + n;
        }
    },
    CAT_VISIT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.CAT}, 2, 2, 5); return "К вам пришли котики: " + n + ". Мяу!";
        }
    },
    VILLAGER_MEETING(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.VILLAGER}, 3, 3, 7); return "Жители устроили собрание: " + n;
        }
    },
    TNT_SURPRISE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            var pos = randomSurfacePosition(player, 5, 8); return EntityType.TNT.spawn(player.serverLevel(), pos, MobSpawnType.EVENT) == null ? "TNT не удалось доставить." : "Сюрприз! Рядом активирован динамит!";
        }
    },
    LIGHTNING_STRIKE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            var pos = randomSurfacePosition(player, 3, 8); return EntityType.LIGHTNING_BOLT.spawn(player.serverLevel(), pos, MobSpawnType.EVENT) == null ? "Молния промахнулась." : "НЕБЕСА ГНЕВАЮТСЯ! Молния ударила рядом!";
        }
    },
    LIGHTNING_TRIO(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = 0; for (int i = 0; i < 3; i++) { if (EntityType.LIGHTNING_BOLT.spawn(player.serverLevel(), randomSurfacePosition(player, 4, 10), MobSpawnType.EVENT) != null) n++; } return "Гроза бьёт рядом! Молний: " + n;
        }
    },
    METEOR_CRATER(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            var pos = randomSurfacePosition(player, 7, 12).below(); int n = createCrater(player, pos, 3); return "Метеоритный удар! Разрушено блоков: " + n;
        }
    },
    GROUND_COLLAPSE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = removeBlockBurst(player, 2, 5, 20, 30); return "Земля провалилась под ногами! Блоков исчезло: " + n;
        }
    },
    REMOTE_EXPLOSION(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            var pos = randomSurfacePosition(player, 8, 14).below(); int n = createCrater(player, pos, 2); return "Вдалеке прогремел взрыв! Уничтожено блоков: " + n;
        }
    },
    SPEED_CURSE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 35, 2)); return "Проклятие тяжёлых ног: сильное замедление на 35 секунд!";
        }
    },
    SUPER_SPEED(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 45, 2)); sparkle(player); return "СУПЕРСКОРОСТЬ! Вы молниеносны 45 секунд!";
        }
    },
    MEGA_JUMP(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * 40, 3)); sparkle(player); return "Пружинные ноги! Прыжки усилены на 40 секунд!";
        }
    },
    DARKNESS_CURSE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 18, 0)); return "Тьма поглотила ваш мир на 18 секунд!";
        }
    },
    DOUBLE_POISON(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 18, 1)); return "Сильный яд! Держитесь 18 секунд!";
        }
    },
    LONG_WITHER(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 10, 1)); return "Проклятие иссушения! 10 секунд опасности!";
        }
    },
    HUNGER_PANIC(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 45, 2)); return "Внезапный голод! Срочно ищите еду!";
        }
    },
    WEAKNESS_CURSE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 40, 1)); return "Слабость сковала ваши руки на 40 секунд!";
        }
    },
    LEVITATION_TRAP(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 * 6, 1)); return "Вас подбросило в воздух! Надеюсь, вы умеете приземляться.";
        }
    },
    SLOW_FALLING_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 45, 0)); sparkle(player); return "Мягкое падение! Можно прыгать с высоты 45 секунд.";
        }
    },
    REGENERATION_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 25, 1)); player.heal(6.0F); sparkle(player); return "Мощное исцеление!";
        }
    },
    ABSORPTION_SHIELD(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60, 2)); sparkle(player); return "Щит поглощения защищает вас целую минуту!";
        }
    },
    FIRE_RESISTANCE_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0)); sparkle(player); return "Огнеупорность на 60 секунд!";
        }
    },
    WATER_BREATHING_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 60, 0)); sparkle(player); return "Подводное дыхание на минуту!";
        }
    },
    INVISIBILITY_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 30, 0)); sparkle(player); return "Вы стали невидимы на 30 секунд!";
        }
    },
    NIGHT_VISION_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 90, 0)); sparkle(player); return "Ночное зрение на 90 секунд!";
        }
    },
    SATURATION_BLESSING(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 5, 2)); player.heal(2.0F); return "Пир богов! Голод утолён.";
        }
    },
    DIAMOND_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.DIAMOND, 3)); sparkle(player); return "Алмазный подарок: 3 алмаза!";
        }
    },
    EMERALD_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.EMERALD, 8)); sparkle(player); return "Торговец оставил 8 изумрудов!";
        }
    },
    GOLD_RAIN(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.GOLD_INGOT, 12)); sparkle(player); return "Золотой дождь! Вы получили 12 слитков золота.";
        }
    },
    IRON_CACHE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.IRON_INGOT, 16)); sparkle(player); return "Тайник с железом: 16 слитков!";
        }
    },
    ENDER_PEARLS_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.ENDER_PEARL, 4)); sparkle(player); return "Подарок странника: 4 жемчуга Края!";
        }
    },
    GOLDEN_APPLE_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.GOLDEN_APPLE, 2)); sparkle(player); return "Легендарная награда: 2 золотых яблока!";
        }
    },
    TOTEM_TEASE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.TOTEM_OF_UNDYING, 1)); sparkle(player); return "НЕВЕРОЯТНО! Вам достался тотем бессмертия!";
        }
    },
    TNT_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.TNT, 8)); sparkle(player); return "Вам подарили 8 блоков TNT. Используйте с умом!";
        }
    },
    ARROW_BUNDLE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.ARROW, 32)); return "Колчан пополнен: 32 стрелы!";
        }
    },
    FOOD_CRATE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.COOKED_BEEF, 16)); return "Большой запас еды: 16 стейков!";
        }
    },
    OBSIDIAN_CACHE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.OBSIDIAN, 12)); sparkle(player); return "Подарок строителя: 12 обсидиана!";
        }
    },
    EXPERIENCE_BOTTLES(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.EXPERIENCE_BOTTLE, 16)); sparkle(player); return "Бутылочки опыта! Забирайте 16 штук.";
        }
    },
    ENDER_CHEST_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.ENDER_CHEST, 1)); sparkle(player); return "Подарок путешественника: эндер-сундук!";
        }
    },
    DIAMOND_SWORD_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.DIAMOND_SWORD, 1)); sparkle(player); return "Вам подарили алмазный меч!";
        }
    },
    SHIELD_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.SHIELD, 1)); sparkle(player); return "Защитный подарок: щит!";
        }
    },
    CREEPER_NEARBY(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.CREEPER}, 1, 2, 4); return "Крипер появился совсем близко: " + n + "!";
        }
    },
    ZOMBIE_HORDE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE}, 30, 6, 14); return "АПОКАЛИПСИС! Орда из 10 зомби: " + n + "!";
        }
    },
    SKELETON_AMBUSH(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.SKELETON}, 24, 6, 14); return "Лучники окружают вас! Скелетов: " + n;
        }
    },
    SPIDER_NEST(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.SPIDER}, 24, 5, 12); return "Вы потревожили паучье гнездо! Пауков: " + n;
        }
    },
    MIXED_MONSTER_WAVE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 32, 7, 16); return "Волна монстров! Врагов появилось: " + n;
        }
    },
    RANDOM_ANIMAL_PARTY(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.SHEEP, EntityType.COW, EntityType.PIG, EntityType.CHICKEN, EntityType.RABBIT}, 10, 2, 7); return "Фермерский фестиваль! Животных: " + n;
        }
    },
    BEE_ARMY(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.BEE}, 10, 3, 8); return "Пчелиная армия защищает улей! Пчёл: " + n;
        }
    },
    CAVE_SPIDER_NEST(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.CAVE_SPIDER}, 18, 5, 12); return "Ядовитые пауки выползли из темноты: " + n;
        }
    },
    STRAY_SQUAD(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.STRAY}, 18, 6, 13); return "Ледяные лучники атакуют: " + n;
        }
    },
    HUSK_SQUAD(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.HUSK}, 20, 5, 12); return "Песчаная нежить вышла из укрытий: " + n;
        }
    },
    MAGMA_CUBE_PACK(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.MAGMA_CUBE}, 4, 5, 10); return "Магмовые кубы прыгают к вам: " + n;
        }
    },
    GIFT_OF_TORCHES(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.TORCH, 32)); return "Подарок шахтёра: 32 факела!";
        }
    },
    GIFT_OF_BREAD(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.BREAD, 12)); return "Каравай удачи: 12 хлеба!";
        }
    },
    GIFT_OF_BOW(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.BOW, 1)); giveItem(player, new ItemStack(Items.ARROW, 16)); return "Лучник оставил лук и 16 стрел!";
        }
    },
    GIFT_OF_COBBLESTONE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.COBBLESTONE, 64)); return "Строительный набор: стак булыжника!";
        }
    },
    GIFT_OF_LAPIS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.LAPIS_LAZULI, 16)); return "Синий клад: 16 лазурита!";
        }
    },
    GIFT_OF_REDSTONE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.REDSTONE, 32)); return "Редстоуновый запас: 32 пыли!";
        }
    },
    GIFT_OF_POTIONLESS_CHAOS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 20, 1)); player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * 20, 1)); return "Комбо-бафф! Скорость и прыжок на 20 секунд!";
        }
    },
    SMALL_HEALING_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.heal(8.0F);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 8, 0));
            sparkle(player);
            return "Добрый сюрприз: вы восстановили здоровье!";
        }
    },
    FOOD_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.COOKED_BEEF, 8));
            return "Подарок путешественника: 8 стейков!";
        }
    },
    EMERALD_BONUS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.EMERALD, 4));
            sparkle(player);
            return "Удачная находка: 4 изумруда!";
        }
    },
    TEMPORARY_RESISTANCE_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 30, 0));
            sparkle(player);
            return "Защитное благословение: сопротивление урону на 30 секунд!";
        }
    },
    EXPERIENCE_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.giveExperiencePoints(30);
            sparkle(player);
            return "Немного опыта в подарок: +30 очков!";
        }
    },
    HAPPY_ANIMALS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN}, 5, 3, 7);
            sparkle(player);
            return "Мирный зверинец! Рядом появилось животных: " + n + ".";
        }
    },
    EXTRA_CATASTROPHE_001(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 20, 33); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_002(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 16, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_003(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBBLESTONE.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_004(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.COBBLESTONE.defaultBlockState(), 2, 4); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_005(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 45, 1)); return "вас сковало замедление!"; }
    },
    EXTRA_CATASTROPHE_006(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 31, 44); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_007(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 24, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_008(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.IRON_BARS.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_009(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.MAGMA_BLOCK.defaultBlockState(), 3, 3); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_010(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 30, 1)); return "ядовитый воздух!"; }
    },
    EXTRA_CATASTROPHE_011(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 5, 10, 24, 55); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_012(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 18, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_013(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBWEB.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_014(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.NETHERRACK.defaultBlockState(), 4, 5); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_015(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 54, 1)); return "внезапная слабость!"; }
    },
    EXTRA_CATASTROPHE_016(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 18, 40); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_017(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 24, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_018(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_019(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.STONE_BRICKS.defaultBlockState(), 5, 4); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_020(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 45, 1)); return "земля будто тянет руки вниз!"; }
    },
    EXTRA_CATASTROPHE_021(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 29, 51); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_022(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 20, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_023(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBBLESTONE.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_024(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.COBBLESTONE.defaultBlockState(), 2, 3); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_025(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 36, 1)); return "внезапная тьма ослепляет вас!"; }
    },
    EXTRA_CATASTROPHE_026(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 22, 35); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_027(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 16, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_028(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.IRON_BARS.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_029(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.MAGMA_BLOCK.defaultBlockState(), 3, 5); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_030(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 45, 1)); return "вас сковало замедление!"; }
    },
    EXTRA_CATASTROPHE_031(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 5, 10, 33, 46); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_032(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 22, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_033(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBWEB.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_034(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.NETHERRACK.defaultBlockState(), 4, 4); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_035(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 30, 1)); return "ядовитый воздух!"; }
    },
    EXTRA_CATASTROPHE_036(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 26, 34); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_037(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 16, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_038(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_039(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.STONE_BRICKS.defaultBlockState(), 5, 3); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_040(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 54, 1)); return "внезапная слабость!"; }
    },
    EXTRA_CATASTROPHE_041(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 20, 42); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_042(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 24, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_043(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBBLESTONE.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_044(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.COBBLESTONE.defaultBlockState(), 2, 5); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_045(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 45, 1)); return "земля будто тянет руки вниз!"; }
    },
    EXTRA_CATASTROPHE_046(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 31, 53); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_047(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 16, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_048(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.IRON_BARS.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_049(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.MAGMA_BLOCK.defaultBlockState(), 3, 4); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_050(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 36, 1)); return "внезапная тьма ослепляет вас!"; }
    },
    EXTRA_CATASTROPHE_051(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 5, 10, 24, 37); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_052(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 24, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_053(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBWEB.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_054(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.NETHERRACK.defaultBlockState(), 4, 3); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_055(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 45, 1)); return "вас сковало замедление!"; }
    },
    EXTRA_CATASTROPHE_056(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 18, 48); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_057(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 18, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_058(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_059(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.STONE_BRICKS.defaultBlockState(), 5, 5); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_060(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 30, 1)); return "ядовитый воздух!"; }
    },
    EXTRA_CATASTROPHE_061(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 29, 37); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_062(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 24, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_063(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBBLESTONE.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_064(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.COBBLESTONE.defaultBlockState(), 2, 4); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_065(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 54, 1)); return "внезапная слабость!"; }
    },
    EXTRA_CATASTROPHE_066(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 22, 44); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_067(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 20, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_068(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.IRON_BARS.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_069(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.MAGMA_BLOCK.defaultBlockState(), 3, 3); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_070(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 45, 1)); return "земля будто тянет руки вниз!"; }
    },
    EXTRA_CATASTROPHE_071(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 5, 10, 33, 55); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_072(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 16, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_073(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBWEB.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_074(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.NETHERRACK.defaultBlockState(), 4, 5); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_075(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 36, 1)); return "внезапная тьма ослепляет вас!"; }
    },
    EXTRA_CATASTROPHE_076(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 26, 40); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_077(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 22, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_078(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_079(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.STONE_BRICKS.defaultBlockState(), 5, 4); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_080(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 45, 1)); return "вас сковало замедление!"; }
    },
    EXTRA_CATASTROPHE_081(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 20, 51); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_082(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 16, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_083(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBBLESTONE.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_084(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.COBBLESTONE.defaultBlockState(), 2, 3); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_085(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 30, 1)); return "ядовитый воздух!"; }
    },
    EXTRA_CATASTROPHE_086(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 31, 39); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_087(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 24, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_088(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.IRON_BARS.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_089(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.MAGMA_BLOCK.defaultBlockState(), 3, 5); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_090(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 54, 1)); return "внезапная слабость!"; }
    },
    EXTRA_CATASTROPHE_091(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 5, 10, 24, 46); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_092(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 16, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_093(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBWEB.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_094(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.NETHERRACK.defaultBlockState(), 4, 4); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_095(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 45, 1)); return "земля будто тянет руки вниз!"; }
    },
    EXTRA_CATASTROPHE_096(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 10, 18, 31); return removed > 0 ? "ударная волна уничтожила блоков: " + removed + "!" : "ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_097(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 24, 5, 12); return "в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_098(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()); return placed > 0 ? "ловушка захлопнулась вокруг вас!" : "ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_099(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.STONE_BRICKS.defaultBlockState(), 5, 3); return placed > 0 ? "опасная стена перекрыла путь!" : "стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_100(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 36, 1)); return "внезапная тьма ослепляет вас!"; }
    },
    EXTRA_CATASTROPHE_101(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int n = createCrater(player, randomSurfacePosition(player, 4, 9).below(), 4); return "Мега-взрыв! Разрушено блоков: " + n; }
    },
    EXTRA_CATASTROPHE_102(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int n = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.CREEPER, EntityType.ZOMBIE}, 22, 5, 13); return "Взрывная орда преследует вас: " + n + " мобов!"; }
    },
    EXTRA_CATASTROPHE_103(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = 0; for (int i = 0; i < 5; i++) { if (EntityType.LIGHTNING_BOLT.spawn(player.serverLevel(), randomSurfacePosition(player, 3, 12), MobSpawnType.EVENT) != null) n++; } return "Кара небес! Ударов молнии: " + n; }
    },
    EXTRA_CATASTROPHE_104(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int n = buildWall(player, randomSurfacePosition(player, 2, 6), Blocks.OBSIDIAN.defaultBlockState(), 5, 5); return n > 0 ? "Обсидиановая стена отрезала путь!" : "Стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_105(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int n = buildCage(player.serverLevel(), player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()); return n > 0 ? "Обсидиановая ловушка захлопнулась!" : "Ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_106(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int n = removeBlockBurst(player, 4, 12, 35, 55); return "Разлом земли! Исчезло блоков: " + n; }
    },
    EXTRA_CATASTROPHE_107(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.WITHER_SKELETON, EntityType.BLAZE}, 14, 5, 12); return "Адский отряд прибыл: " + n + " врагов!"; }
    },
    EXTRA_CATASTROPHE_108(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 18, 1)); player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 25, 2)); return "Проклятие иссушения и замедления!"; }
    },
    EXTRA_CATASTROPHE_109(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int n = createCrater(player, randomSurfacePosition(player, 8, 14).below(), 5); return "Метеорит расколол землю! Разрушено блоков: " + n; }
    },
    EXTRA_CATASTROPHE_110(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.RAVAGER, EntityType.VINDICATOR}, 12, 5, 12); return "Охотники настигают вас: " + n + " опасных мобов!"; }
    },
    EXTRA_CATASTROPHE_111(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.setRemainingFireTicks(12 * 20); player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 20, 1)); return "Огненный яд! Горение и отравление!"; }
    },
    EXTRA_CATASTROPHE_112(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int n = buildWall(player, randomSurfacePosition(player, 3, 8), Blocks.MAGMA_BLOCK.defaultBlockState(), 5, 6); return n > 0 ? "Магмовая баррикада перекрыла путь!" : "Баррикада не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_113(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.ENDERMAN}, 12, 5, 13); return "Стая эндерменов телепортировалась рядом: " + n + "!"; }
    },
    EXTRA_CATASTROPHE_114(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int n = removeBlockBurst(player, 6, 14, 28, 48); return "Сейсмическая волна прокатилась по земле! Уничтожено блоков: " + n; }
    },
    EXTRA_CATASTROPHE_115(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 25, 0)); player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 35, 2)); player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 25, 2)); return "Полное затмение: слепота, слабость и замедление!"; }
    },
    EXTRA_CATASTROPHE_116(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.GHAST}, 5, 8, 15); return "Гасты обрушились на округу: " + n + "!"; }
    },
    EXTRA_CATASTROPHE_117(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int n = createCrater(player, randomSurfacePosition(player, 2, 5).below(), 3); return "Взрыв прямо рядом! Разрушено блоков: " + n; }
    },
    EXTRA_CATASTROPHE_118(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.WARDEN}, 2, 6, 12); return "Древний ужас пробудился! Стражей: " + n; }
    },
    EXTRA_CATASTROPHE_119(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 35, 2)); player.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 12, 1)); return "Смертельный токсин проник в кровь!"; }
    },
    GOOD_COOKED_BEEF(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.COOKED_BEEF, 16)); return "Подарок: 16 стейков!"; }
    },
    GOOD_GOLDEN_CARROTS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.GOLDEN_CARROT, 12)); return "Подарок: золотая морковь!"; }
    },
    GOOD_GOLDEN_APPLES(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.GOLDEN_APPLE, 3)); return "Подарок: 3 золотых яблока!"; }
    },
    GOOD_DIAMONDS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.DIAMOND, 4)); return "Подарок: 4 алмаза!"; }
    },
    GOOD_EMERALDS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.EMERALD, 12)); return "Подарок: 12 изумрудов!"; }
    },
    GOOD_IRON_INGOTS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.IRON_INGOT, 24)); return "Подарок: 24 железных слитка!"; }
    },
    GOOD_GOLD_INGOTS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.GOLD_INGOT, 12)); return "Подарок: 12 золотых слитков!"; }
    },
    GOOD_OBSIDIAN(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.OBSIDIAN, 16)); return "Подарок: обсидиан!"; }
    },
    GOOD_ENDER_PEARLS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.ENDER_PEARL, 8)); return "Подарок: жемчуг Края!"; }
    },
    GOOD_EXPERIENCE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.EXPERIENCE_BOTTLE, 16)); return "Подарок: бутылочки опыта!"; }
    },
    GOOD_TOTEM(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.TOTEM_OF_UNDYING, 1)); return "Невероятно! Тотем бессмертия!"; }
    },
    GOOD_ENCHANTED_GOLDEN_APPLE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1)); return "Легендарный подарок: зачарованное золотое яблоко!"; }
    },
    GOOD_BOW(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.BOW, 1)); return "Подарок: лук!"; }
    },
    GOOD_ARROWS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.ARROW, 64)); return "Подарок: 64 стрелы!"; }
    },
    GOOD_SHIELD(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.SHIELD, 1)); return "Подарок: щит!"; }
    },
    GOOD_DIAMOND_PICKAXE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.DIAMOND_PICKAXE, 1)); return "Подарок: алмазная кирка!"; }
    },
    GOOD_BREAD(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.BREAD, 24)); return "Подарок: хлебный запас!"; }
    },
    GOOD_COBBLESTONE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.COBBLESTONE, 64)); return "Подарок: 64 булыжника!"; }
    },
    GOOD_TORCHES(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.TORCH, 48)); return "Подарок: 48 факелов!"; }
    },
    GOOD_ENDER_CHEST(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { giveItem(player, new ItemStack(Items.ENDER_CHEST, 1)); return "Подарок: эндер-сундук!"; }
    },
    GOOD_REGENERATION(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 45, 1)); return "Благословение регенерации!"; }
    },
    GOOD_RESISTANCE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 1)); return "Защитное благословение!"; }
    },
    GOOD_STRENGTH(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60, 1)); return "Благословение силы!"; }
    },
    GOOD_SPEED(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 1)); return "Благословение скорости!"; }
    },
    GOOD_ABSORPTION(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 90, 3)); return "Золотой щит поглощения!"; }
    },
    BAD_HUNGER(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 45, 2)); return "Внезапный голод!"; }
    },
    BAD_MINING_FATIGUE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 45, 2)); return "Усталость шахтёра!"; }
    },
    BAD_NAUSEA(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 25, 0)); return "Мир пошёл кругом!"; }
    },
    BAD_BLINDNESS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 20, 0)); return "Внезапная тьма!"; }
    },
    BAD_WEAKNESS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 45, 1)); return "Слабость накрывает вас!"; }
    },
    BAD_POISON(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 18, 1)); return "Отравление!"; }
    },
    BAD_WITHER(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 12, 0)); return "Проклятие иссушения!"; }
    },
    BAD_SLOWNESS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 35, 2)); return "Ноги стали свинцовыми!"; }
    },
    BAD_LEVITATION(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 * 8, 1)); return "Гравитация сломалась!"; }
    },
    BAD_DARKNESS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 18, 0)); return "Тьма поглотила всё вокруг!"; }
    },
    BAD_FIRE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.setRemainingFireTicks(10 * 20); return "Вы внезапно загорелись!"; }
    },
    BAD_CREEPERS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.CREEPER}, 3, 4, 8); return "Криперы рядом: " + n + "!"; }
    },
    BAD_ZOMBIES(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE}, 6, 4, 9); return "Зомби атакуют: " + n + "!"; }
    },
    BAD_SKELETONS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.SKELETON}, 5, 4, 9); return "Скелеты открыли огонь: " + n + "!"; }
    },
    BAD_SPIDERS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.SPIDER}, 5, 4, 8); return "Пауки окружили вас: " + n + "!"; }
    },
    BAD_PHANTOMS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.PHANTOM}, 3, 5, 10); return "Фантомы спустились с неба: " + n + "!"; }
    },
    BAD_WITCHES(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.WITCH}, 2, 5, 9); return "Ведьмы устроили засаду: " + n + "!"; }
    },
    BAD_HUSKS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.HUSK}, 5, 4, 9); return "Пустынная нежить прибыла: " + n + "!"; }
    },
    BAD_DROWNED(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.DROWNED}, 5, 4, 9); return "Утопленники пришли за вами: " + n + "!"; }
    },
    BAD_SLIMES(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.SLIME}, 4, 4, 8); return "Слизни заполонили округу: " + n + "!"; }
    },
    BAD_CAVE_SPIDERS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.CAVE_SPIDER}, 4, 4, 8); return "Пещерные пауки напали: " + n + "!"; }
    },
    BAD_PILLAGERS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.PILLAGER}, 4, 5, 10); return "Разбойничий патруль: " + n + "!"; }
    },
    BAD_VINDICATORS(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.VINDICATOR}, 3, 5, 9); return "Палачи преследуют вас: " + n + "!"; }
    },
    BAD_BLAZES(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.BLAZE}, 3, 5, 10); return "Вспыхнула огненная засада: " + n + "!"; }
    },
    BAD_MAGMA_CUBES(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { int n = spawnGroup(player, new EntityType<?>[] {EntityType.MAGMA_CUBE}, 4, 4, 8); return "Магмовые кубы прыгают вокруг: " + n + "!"; }
    },
    NETHER_PIGLIN_VISIT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.PIGLIN}, 2, 4, 7);
            return "Пиглины заглянули в гости: " + n + ". Не доставайте золото без спроса!";
        }
    },
    NETHER_MAGMA_CUBES(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.MAGMA_CUBE}, 2, 4, 7);
            return "Пара магмовых кубов решила размяться: " + n + ".";
        }
    },
    NETHER_FIRE_RESISTANCE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 20, 0));
            sparkle(player);
            return "Огнестойкость на 20 секунд!";
        }
    },
    NETHER_GOLD_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.GOLD_INGOT, 2));
            sparkle(player);
            return "Подарок из Нижнего мира: 2 золотых слитка!";
        }
    },
    ENDERMAN_VISIT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.ENDERMAN}, 1, 5, 8);
            return "Эндермен заглянул из пустоты: " + n + ". Не смотри ему в глаза!";
        }
    },
    END_SHULKER_SURPRISE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.SHULKER}, 1, 5, 8);
            return "Неподалёку появился шалкер: " + n + ".";
        }
    },
    END_CHORUS_GIFT(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.CHORUS_FRUIT, 3));
            sparkle(player);
            return "Из Края передали 3 плода хоруса!";
        }
    },
    END_SLOW_FALL(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 15, 0));
            sparkle(player);
            return "Замедленное падение на 15 секунд!";
        }
    },

    NETHER_HOGLIN_SCOUT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.HOGLIN}, 1, 5, 8);
            return "Хоглин вышел на разведку: " + n + "!";
        }
    },
    NETHER_STRIDER_RIDE(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.STRIDER}, 1, 3, 6);
            return "По лавовому морю проплывает страйдер: " + n + ".";
        }
    },
    NETHER_SOUL_VALLEY_SKELETONS(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.SKELETON}, 2, 5, 8);
            return "Души долины призвали скелетов: " + n + "!";
        }
    },
    NETHER_CRIMSON_ROOTS(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.HOGLIN, EntityType.PIGLIN}, 2, 4, 7);
            return "Багровый лес ожил. Из зарослей вышли: " + n + ".";
        }
    },
    NETHER_WARPED_ENDERMEN(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.ENDERMAN}, 1, 5, 8);
            return "Искажённый лес привлёк эндермена: " + n + ".";
        }
    },
    NETHER_BASALT_MAGMA(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.MAGMA_CUBE}, 2, 4, 7);
            return "Базальтовая дельта выплюнула магмовых кубов: " + n + ".";
        }
    },
    END_CHORUS_BLOOM(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.CHORUS_FRUIT, 5));
            sparkle(player);
            return "Острова Края поделились урожаем: 5 плодов хоруса!";
        }
    },
    END_PEARL_GIFT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.ENDER_PEARL, 2));
            sparkle(player);
            return "Подарок из пустоты: 2 жемчуга Края!";
        }
    },
    END_ENDERMITE_AMBUSH(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.ENDERMITE}, 2, 4, 7);
            return "Из разлома выскочили эндермиты: " + n + "!";
        }
    },
    END_HIGH_ISLAND_SHULKER(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.SHULKER}, 1, 6, 9);
            return "На острове проснулся шалкер: " + n + ".";
        }
    },
    DESERT_HUSK_PATROL(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.HUSK}, 2, 5, 8);
            return "Пески подняли патруль кадавров: " + n + "!";
        }
    },
    JUNGLE_PARROT_VISIT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.PARROT}, 2, 2, 5);
            return "Из джунглей прилетели попугаи: " + n + ".";
        }
    },
    JUNGLE_OCELOT_VISIT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.OCELOT}, 1, 3, 6);
            return "В кустах джунглей притаился оцелот: " + n + ".";
        }
    },
    SWAMP_SLIME_VISIT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.SLIME}, 2, 3, 6);
            return "Болотная жижа собралась в слизней: " + n + ".";
        }
    },
    SNOWY_STRAY_PATROL(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.STRAY}, 2, 5, 8);
            return "Из метели вышли зимогоры: " + n + "!";
        }
    },
    BADLANDS_GOLD_GIFT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.GOLD_NUGGET, 12));
            sparkle(player);
            return "В красных каньонах нашлась золотая россыпь: 12 самородков!";
        }
    },
    OCEAN_DOLPHIN_VISIT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.DOLPHIN}, 1, 3, 6);
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 12, 0));
            return "Дельфин вывел вас к безопасному месту: " + n + ". Подводное дыхание на 12 секунд!";
        }
    },
    MOUNTAIN_GOAT_VISIT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.GOAT}, 2, 3, 6);
            return "На склоне появились горные козлы: " + n + ".";
        }
    },
    MUSHROOM_COW_VISIT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.MOOSHROOM}, 1, 3, 6);
            return "Грибная поляна прислала муухомора: " + n + ".";
        }
    },
    CHERRY_BLOSSOM_GIFT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.PINK_PETALS, 8));
            sparkle(player);
            return "Вишнёвая роща осыпала вас лепестками: 8 штук!";
        }
    },
    LAVA_NEARBY_FIRE_RESISTANCE(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 12, 0));
            sparkle(player);
            return "Рядом плещется лава — огнестойкость на 12 секунд!";
        }
    },
    WATER_NEARBY_BREATHING(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 15, 0));
            sparkle(player);
            return "Поблизости вода! Подводное дыхание на 15 секунд.";
        }
    },

    CAVE_AMETHYST_RESONANCE(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.AMETHYST_SHARD, 4));
            giveItem(player, new ItemStack(Items.GLOW_BERRIES, 3));
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.8, 0.7, 0.8, 0.03);
            return "Кристаллы в пещере отозвались на ваш голос: 4 осколка аметиста и светящиеся ягоды!";
        }
    },
    CAVE_BAT_ORACLE(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.BAT}, 4, 3, 7);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 18, 0));
            return "Летучие мыши вывели вас из темноты: " + n + " проводников и ночное зрение на 18 секунд.";
        }
    },
    CAVE_SILVERFISH_BREAKOUT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.SILVERFISH}, 3, 4, 7);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SILVERFISH_HURT, SoundSource.HOSTILE, 1.0F, 0.7F);
            return "Из трещин вырвался выводок чешуйниц: " + n + "! Похоже, камень был не совсем камнем.";
        }
    },
    FOREST_WOLF_CIRCLE(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.WOLF}, 3, 3, 6);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 12, 0));
            return "Лесная стая окружила вас защитным кругом: " + n + " волков и сопротивление урону на 12 секунд.";
        }
    },
    FOREST_BEE_COURIER(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.BEE}, 3, 2, 5);
            giveItem(player, new ItemStack(Items.HONEY_BOTTLE, 2));
            return "Пчёлы принесли лесной гостинец: " + n + " пчёл и 2 бутылочки мёда.";
        }
    },
    PLAINS_HORSE_CARAVAN(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.HORSE}, 2, 4, 7);
            giveItem(player, new ItemStack(Items.SADDLE, 1));
            return "Через равнину прошёл караван: " + n + " лошадей. Одна оставила вам седло!";
        }
    },
    RIVER_FROG_CHORUS(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.FROG}, 3, 2, 5);
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 20, 0));
            return "Береговой хор квакнул в вашу честь: " + n + " лягушек и дыхание под водой на 20 секунд.";
        }
    },
    BEACH_TURTLE_ESCORT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.TURTLE}, 2, 3, 6);
            giveItem(player, new ItemStack(Items.SEAGRASS, 1));
            return "Морские черепахи проводят вас вдоль берега: " + n + " черепах и один щиток.";
        }
    },
    OCEAN_GUARDIAN_WARNING(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.GUARDIAN}, 2, 5, 8);
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 15, 0));
            return "Стражи заметили вас в глубине: " + n + " стражей. Вода будто предупреждает — дыхание на 15 секунд.";
        }
    },
    SNOW_FOX_COURIER(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.FOX}, 2, 3, 6);
            giveItem(player, new ItemStack(Items.SWEET_BERRIES, 5));
            return "Снежные лисы оставили след к тайнику: " + n + " лис и 5 сладких ягод.";
        }
    },
    DESERT_CAMEL_CARAVAN(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.CAMEL}, 1, 5, 8);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 16, 0));
            return "Из марева вышел караван верблюдов: " + n + ". Попутный ветер ускоряет вас на 16 секунд.";
        }
    },
    NETHER_GHAST_SHADOW(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.GHAST}, 1, 8, 12);
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 10, 0));
            return "Над лавовым морем проплыла тень гастa: " + n + ". Адская жара временно не страшна — огнестойкость 10 секунд.";
        }
    },
    NETHER_WITHER_SKELETON_PATROL(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.WITHER_SKELETON}, 2, 5, 8);
            return "Из крепости вышел патруль wither-скелетов: " + n + "! Не дайте им окружить себя.";
        }
    },
    NETHER_PIGLIN_BRUTE_TRIBUTE(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.PIGLIN_BRUTE}, 1, 6, 9);
            return "Пиглин-брут требует дань за проход: " + n + " охранник уже направляется к вам!";
        }
    },
    NETHER_SOULFIRE_BLESSING(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 14, 0));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 14, 0));
            player.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.7, 0.8, 0.7, 0.02);
            return "Синее пламя долины душ признало вас: огнестойкость и сопротивление урону на 14 секунд.";
        }
    },
    END_SHULKER_CIRCLE(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.SHULKER}, 2, 6, 9);
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 12, 0));
            return "Шалкеры заняли позиции вокруг острова: " + n + ". На всякий случай — замедленное падение на 12 секунд.";
        }
    },
    END_VOID_GIFT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.ENDER_PEARL, 3));
            giveItem(player, new ItemStack(Items.PHANTOM_MEMBRANE, 1));
            player.serverLevel().sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 28, 0.8, 0.8, 0.8, 0.12);
            return "Пустота оставила подношение: 3 жемчуга Края и мембрана фантома.";
        }
    },
    END_ENDERMAN_CARAVAN(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.ENDERMAN}, 3, 7, 10);
            return "По острову проходит караван эндерменов: " + n + ". Смотрите под ноги, не им в глаза.";
        }
    },
    END_CHORUS_TELEPORT(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            giveItem(player, new ItemStack(Items.CHORUS_FRUIT, 4));
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 20, 0));
            player.serverLevel().sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 32, 0.6, 0.8, 0.6, 0.15);
            return "Хорусный всплеск исказил пространство: 4 плода хоруса и замедленное падение на 20 секунд.";
        }
    },
    END_PHANTOM_ECLIPSE(EventCategory.OTHER) {
        @Override String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.PHANTOM}, 3, 6, 9);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 10, 0));
            return "Над островом случилось затмение: " + n + " фантомов и ночное зрение на 10 секунд.";
        }
    },


    CHUNK_QUAKE(EventCategory.BLOCKS) {
        @Override String apply(ServerPlayer player) {
            BlockPos center = randomChunkSurfacePosition(player);
            int changed = removeChunkSurface(player, center, 3, 1, 34);
            return "ЧАНКОВОЕ ЗЕМЛЕТРЯСЕНИЕ! В выбранном чанке обрушилось " + changed + " блоков поверхности.";
        }
    },
    CHUNK_FLASH_FLOOD(EventCategory.BLOCKS) {
        @Override String apply(ServerPlayer player) {
            BlockPos center = randomChunkSurfacePosition(player);
            int changed = floodChunkPatch(player, center, 3);
            return "ПРОРЫВ ВОДЫ! Часть чанка затоплена. Размещено водяных блоков: " + changed + ".";
        }
    },
    CHUNK_MAGMA_FAULT(EventCategory.BLOCKS) {
        @Override String apply(ServerPlayer player) {
            BlockPos center = randomChunkSurfacePosition(player);
            int changed = replaceChunkSurface(player, center, 2, Blocks.MAGMA_BLOCK.defaultBlockState(), 0);
            return "МАГМАТИЧЕСКИЙ РАЗЛОМ! Поверхность чанка покрылась магмой: " + changed + " блоков.";
        }
    },
    CHUNK_GLASSIFICATION(EventCategory.BLOCKS) {
        @Override String apply(ServerPlayer player) {
            BlockPos center = randomChunkSurfacePosition(player);
            int changed = replaceChunkSurface(player, center, 3, Blocks.GLASS.defaultBlockState(), 1);
            return "СТЕКЛЯНИЕ ЧАНКА! Земля на участке превратилась в стекло: " + changed + " блоков.";
        }
    },
    CHUNK_GRAVEL_COLLAPSE(EventCategory.BLOCKS) {
        @Override String apply(ServerPlayer player) {
            BlockPos center = randomChunkSurfacePosition(player);
            int changed = replaceChunkSurface(player, center, 3, Blocks.GRAVEL.defaultBlockState(), 0);
            return "ГРАВИЙНЫЙ ОБВАЛ! Поверхность чанка осыпалась и стала гравием: " + changed + " блоков.";
        }
    },
    CHUNK_OBSIDIAN_SEAL(EventCategory.BLOCKS) {
        @Override String apply(ServerPlayer player) {
            BlockPos center = randomChunkSurfacePosition(player);
            int changed = replaceChunkSurface(player, center, 2, Blocks.OBSIDIAN.defaultBlockState(), 0);
            return "ОБСИДИАНОВАЯ ПЕЧАТЬ! Участок чанка запечатан обсидианом: " + changed + " блоков.";
        }
    },
    CHUNK_ICE_AGE(EventCategory.BLOCKS) {
        @Override String apply(ServerPlayer player) {
            BlockPos center = randomChunkSurfacePosition(player);
            int changed = replaceChunkSurface(player, center, 3, Blocks.ICE.defaultBlockState(), 1);
            return "ЛОКАЛЬНЫЙ ЛЕДНИК! Поверхность чанка промёрзла: " + changed + " блоков заменено льдом.";
        }
    },
    CHUNK_SCULK_INFECTION(EventCategory.BLOCKS) {
        @Override String apply(ServerPlayer player) {
            BlockPos center = randomChunkSurfacePosition(player);
            int changed = replaceChunkSurface(player, center, 3, Blocks.SCULK.defaultBlockState(), 0);
            return "СКАЛКОВАЯ ИНФЕКЦИЯ! Заражение поглотило поверхность чанка: " + changed + " блоков.";
        }
    },
    CHUNK_VOID_BITE(EventCategory.BLOCKS) {
        @Override String apply(ServerPlayer player) {
            BlockPos center = randomChunkSurfacePosition(player);
            int changed = removeChunkSurface(player, center, 2, 2, 48);
            return "УКУС ПУСТОТЫ! Кусок поверхности и породы исчез из чанка. Удалено блоков: " + changed + ".";
        }
    },
    CHUNK_DEEPSLATE_RUIN(EventCategory.BLOCKS) {
        @Override String apply(ServerPlayer player) {
            BlockPos center = randomChunkSurfacePosition(player);
            int changed = replaceChunkSurface(player, center, 3, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 2);
            return "ДРЕВНЕЕ ИСКАЖЕНИЕ! Поверхность чанка стала древним сланцем: " + changed + " блоков.";
        }
    },

    EXTRA_CATASTROPHE_120(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int n = spawnHordeIfAnotherPlayerNearby(player, new EntityType<?>[] {EntityType.CREEPER, EntityType.BLAZE, EntityType.WITCH, EntityType.SKELETON}, 24, 5, 14); return "Апокалиптическая волна! Врагов появилось: " + n; }
    };

    private final EventCategory category;

    MinuteEvent(EventCategory category) {
        this.category = category;
    }

    abstract String apply(ServerPlayer player);

    String displayName() {
        return switch (this) {
            case NEARBY_BLOCK_BURST -> "Соседний блок испарился";
            case DISTANT_BLOCK_BURST -> "Дистанционная уборка";
            case LARGE_NEARBY_CAVE_IN -> "Локальный конец света";
            case WIDE_DISTANT_CAVE_IN -> "Обвал по расписанию";
            case MINI_BLAST -> "Небольшое БА-БАХ";
            case OBSIDIAN_CAGE -> "Обсидиум: вы арестованы";
            case DISTANT_OBSIDIAN_CAGE -> "Клетка для соседа";
            case WEB_SNARE -> "Паучий капкан";
            case STONE_SPIKES -> "Земля сказала: стой";
            case SAND_WALL -> "Песчаная пробка";
            case STONE_BRIDGE -> "Строители Minecraft одобряют";
            case ANIMAL_HERD -> "Ферма сама приехала";
            case BEE_SWARM -> "Пчелиный отдел безопасности";
            case ZOMBIE_PACK -> "Зомби идут на собеседование";
            case SKELETON_PACK -> "Скелеты с плохими новостями";
            case SPIDER_PACK -> "Паучья вечеринка";
            case WITCH_ATTACK -> "Ведьма заглянула в гости";
            case BLINDNESS, BAD_BLINDNESS -> "Режим: экран выключен";
            case SLUGGISH -> "Черепаший режим";
            case POISON, BAD_POISON -> "Не пробуйте это на вкус";
            case WITHER, BAD_WITHER -> "Проклятие без подписки";
            case FIRE, BAD_FIRE -> "Вы теперь факел";
            case PLAYER_CAGE -> "Персональная парковка";
            case DISTANT_CAGE -> "Клетка неподалёку";
            case STONE_BARRICADE -> "Каменная пробка";
            case HOSTILE_VISITOR -> "Нежданные гости";
            case FRIENDLY_VISITOR -> "Милый незваный гость";
            case SMALL_GIFT -> "Подарочек от судьбы";
            case QUICK_FEET -> "Ноги на турбо";
            case SECOND_WIND -> "Второе дыхание";
            case STAR_BURST -> "Звёздный конфетти";
            case CREEPER_SQUAD, BAD_CREEPERS -> "Криперы: дружба отменяется";
            case ENDERMAN_HUNT -> "Эндермены ищут вас";
            case SLIME_INVASION, BAD_SLIMES -> "Слизневый корпоратив";
            case PHANTOM_SWARM, BAD_PHANTOMS -> "Фантомы без приглашения";
            case DROWNED_RAID, BAD_DROWNED -> "Утопленники на районе";
            case PILLAGER_PATROL, BAD_PILLAGERS -> "Разбойничий патруль";
            case VINDICATOR_HUNT, BAD_VINDICATORS -> "Палачи на подработке";
            case BLAZE_AMBUSH, BAD_BLAZES -> "Ифриты поджидают";
            case WOLF_GUARDIANS -> "Волки: охрана или угроза?";
            case IRON_GOLEM_SQUAD -> "Железная охрана";
            case RAVAGER_CHARGE -> "Разоритель уже бежит";
            case GUARDIAN_PATROL -> "Стражи на посту";
            case FOX_FRIENDS -> "Лисья тусовка";
            case HORSE_DELIVERY -> "Доставка лошадей";
            case CAT_VISIT -> "Котики пришли";
            case VILLAGER_MEETING -> "Собрание жителей";
            default -> localizedFallbackName();
        };
    }

    private String localizedFallbackName() {
        String key = name();
        if (key.startsWith("EXTRA_CATASTROPHE_")) {
            return "Сюрприз от Minecraft №" + key.substring("EXTRA_CATASTROPHE_".length()) + " 💀";
        }

        boolean good = key.startsWith("GOOD_") || key.endsWith("_GIFT");
        boolean bad = key.startsWith("BAD_");
        String words = key.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        String[][] translations = {
            {"cooked beef", "жареная говядина"}, {"golden carrots", "золотая морковь"},
            {"golden apples", "золотые яблоки"}, {"diamonds", "алмазы"},
            {"emeralds", "изумруды"}, {"iron ingots", "железные слитки"},
            {"gold ingots", "золотые слитки"}, {"obsidian", "обсидиан"},
            {"ender pearls", "жемчуг Края"}, {"experience", "опыт"},
            {"totem", "тотем"}, {"enchanted golden apple", "зачарованное золотое яблоко"},
            {"diamond pickaxe", "алмазная кирка"}, {"bread", "хлеб"},
            {"cobblestone", "булыжник"}, {"torches", "факелы"},
            {"ender chest", "эндер-сундук"}, {"regeneration", "регенерация"},
            {"resistance", "сопротивление"}, {"strength", "сила"},
            {"speed", "скорость"}, {"absorption", "поглощение"},
            {"hunger", "голод"}, {"mining fatigue", "усталость шахтёра"},
            {"nausea", "тошнота"}, {"blindness", "слепота"},
            {"weakness", "слабость"}, {"poison", "отравление"},
            {"wither", "иссушение"}, {"slowness", "замедление"},
            {"levitation", "левитация"}, {"darkness", "тьма"},
            {"creepers", "криперы"}, {"zombies", "зомби"},
            {"skeletons", "скелеты"}, {"spiders", "пауки"},
            {"phantoms", "фантомы"}, {"witches", "ведьмы"},
            {"husks", "кадавры"}, {"drowned", "утопленники"},
            {"slimes", "слизни"}, {"cave spiders", "пещерные пауки"},
            {"pillagers", "разбойники"}, {"vindicators", "поборники"},
            {"blazes", "ифриты"}, {"magma cubes", "магмовые кубы"},
            {"gift", "подарок"}, {"good", "хорошее событие"},
            {"bad", "неприятность"}, {"extra", "дополнительное"},
            {"temporary", "временное"}, {"small", "маленький"},
            {"large", "большой"}, {"random", "случайный"},
            {"animals", "животные"}, {"animal", "животное"},
            {"resistance", "сопротивление"}, {"fire", "огонь"},
            {"water", "вода"}, {"breathing", "дыхание"},
            {"invisibility", "невидимость"}, {"night vision", "ночное зрение"},
            {"saturation", "сытость"}, {"gold", "золото"},
            {"iron", "железо"}, {"arrow", "стрела"}, {"arrows", "стрелы"},
            {"bow", "лук"}, {"shield", "щит"}, {"sword", "меч"},
            {"experience bottles", "бутылочки опыта"}, {"cache", "тайник"},
            {"bonus", "бонус"}, {"happy", "счастливые"}
        };
        for (String[] pair : translations) {
            words = words.replace(pair[0], pair[1]);
        }
        if (good) return "Халява подъехала: " + words + " 😎";
        if (bad) return "Ну всё, приехали: " + words + " 💀";
        return words.substring(0, 1).toUpperCase(java.util.Locale.forLanguageTag("ru-RU")) + words.substring(1);
    }

    EventCategory category() { return category; }

    boolean isGood() {
        return switch (this) {
            case GOOD_COOKED_BEEF, GOOD_GOLDEN_CARROTS, GOOD_GOLDEN_APPLES, GOOD_DIAMONDS, GOOD_EMERALDS, GOOD_IRON_INGOTS, GOOD_GOLD_INGOTS, GOOD_OBSIDIAN, GOOD_ENDER_PEARLS, GOOD_EXPERIENCE, GOOD_TOTEM, GOOD_ENCHANTED_GOLDEN_APPLE, GOOD_BOW, GOOD_ARROWS, GOOD_SHIELD, GOOD_DIAMOND_PICKAXE, GOOD_BREAD, GOOD_COBBLESTONE, GOOD_TORCHES, GOOD_ENDER_CHEST, GOOD_REGENERATION, GOOD_RESISTANCE, GOOD_STRENGTH, GOOD_SPEED, GOOD_ABSORPTION,
                 SMALL_GIFT, QUICK_FEET, SECOND_WIND, STAR_BURST, FRIENDLY_VISITOR,
                 SLOW_FALLING_GIFT, REGENERATION_GIFT, ABSORPTION_SHIELD,
                 FIRE_RESISTANCE_GIFT, WATER_BREATHING_GIFT, INVISIBILITY_GIFT,
                 NIGHT_VISION_GIFT, SATURATION_BLESSING, DIAMOND_GIFT, EMERALD_GIFT,
                 GOLD_RAIN, IRON_CACHE, ENDER_PEARLS_GIFT, GOLDEN_APPLE_GIFT,
                 TNT_GIFT, ARROW_BUNDLE, FOOD_CRATE, OBSIDIAN_CACHE, EXPERIENCE_BOTTLES,
                 ENDER_CHEST_GIFT, DIAMOND_SWORD_GIFT, SHIELD_GIFT, RANDOM_ANIMAL_PARTY,
                 GIFT_OF_TORCHES, GIFT_OF_BREAD, GIFT_OF_BOW, GIFT_OF_COBBLESTONE,
                 GIFT_OF_LAPIS, GIFT_OF_REDSTONE, SMALL_HEALING_GIFT, FOOD_GIFT,
                 EMERALD_BONUS, TEMPORARY_RESISTANCE_GIFT, EXPERIENCE_GIFT, HAPPY_ANIMALS,
                 NETHER_FIRE_RESISTANCE, NETHER_GOLD_GIFT, NETHER_STRIDER_RIDE,
                 END_CHORUS_GIFT, END_SLOW_FALL, END_CHORUS_BLOOM, END_PEARL_GIFT,
                 BADLANDS_GOLD_GIFT, JUNGLE_PARROT_VISIT, JUNGLE_OCELOT_VISIT,
                 OCEAN_DOLPHIN_VISIT, MOUNTAIN_GOAT_VISIT, MUSHROOM_COW_VISIT,
                 CHERRY_BLOSSOM_GIFT, LAVA_NEARBY_FIRE_RESISTANCE, WATER_NEARBY_BREATHING,
                 CAVE_AMETHYST_RESONANCE, CAVE_BAT_ORACLE, FOREST_BEE_COURIER,
                 PLAINS_HORSE_CARAVAN, RIVER_FROG_CHORUS, BEACH_TURTLE_ESCORT,
                 SNOW_FOX_COURIER, DESERT_CAMEL_CARAVAN, NETHER_SOULFIRE_BLESSING,
                 END_VOID_GIFT, END_CHORUS_TELEPORT -> true;
            default -> false;
        };
    }

    static MinuteEvent randomGoodEvent() {
        MinuteEvent[] goodEvents = java.util.Arrays.stream(values())
            .filter(MinuteEvent::isGood)
            .toArray(MinuteEvent[]::new);
        return goodEvents[ThreadLocalRandom.current().nextInt(goodEvents.length)];
    }

    static MinuteEvent randomForCategory(EventCategory category) {
        int matchingEvents = 0;
        for (MinuteEvent event : values()) {
            if (event.category == category) {
                matchingEvents++;
            }
        }

        if (matchingEvents == 0) {
            throw new IllegalArgumentException("Нет событий категории " + category);
        }

        int selected = ThreadLocalRandom.current().nextInt(matchingEvents);
        for (MinuteEvent event : values()) {
            if (event.category == category && selected-- == 0) {
                return event;
            }
        }

        throw new IllegalStateException("Не удалось выбрать событие категории " + category);
    }

    private static BlockPos randomSurfacePosition(ServerPlayer player, int minDistance, int maxDistance) {
        double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
        int distance = ThreadLocalRandom.current().nextInt(minDistance, maxDistance + 1);
        int offsetX = (int) Math.round(Math.cos(angle) * distance);
        int offsetZ = (int) Math.round(Math.sin(angle) * distance);
        BlockPos target = player.blockPosition().offset(offsetX, 0, offsetZ);
        return player.serverLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target);
    }

    private static int removeBlockBurst(
        ServerPlayer player,
        int minDistance,
        int maxDistance,
        int minBlocks,
        int maxBlocks
    ) {
        ServerLevel level = player.serverLevel();
        BlockPos surface = randomSurfacePosition(player, minDistance, maxDistance);
        BlockPos center = surface.below();
        int desired = ThreadLocalRandom.current().nextInt(minBlocks, maxBlocks + 1);
        int removed = 0;

        for (int attempt = 0; attempt < desired * 12 && removed < desired; attempt++) {
            BlockPos candidate = center.offset(
                ThreadLocalRandom.current().nextInt(-1, 2),
                ThreadLocalRandom.current().nextInt(-1, 2),
                ThreadLocalRandom.current().nextInt(-1, 2)
            );
            BlockState state = level.getBlockState(candidate);
            if (isRemovable(state) && level.destroyBlock(candidate, false, player)) {
                removed++;
            }
        }

        return removed;
    }

    private static int createCrater(ServerPlayer player, BlockPos center, int radius) {
        ServerLevel level = player.serverLevel();
        int removed = 0;
        int radiusSquared = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) {
                        continue;
                    }

                    BlockPos pos = center.offset(dx, dy, dz);
                    if (isRemovable(level.getBlockState(pos)) && level.destroyBlock(pos, false, player)) {
                        removed++;
                    }
                }
            }
        }

        level.sendParticles(
            ParticleTypes.EXPLOSION_EMITTER,
            center.getX() + 0.5,
            center.getY() + 0.5,
            center.getZ() + 0.5,
            1,
            0.0,
            0.0,
            0.0,
            0.0
        );
        level.playSound(
            null,
            center,
            SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.BLOCKS,
            1.2F,
            0.9F
        );
        return removed;
    }

    private static boolean isRemovable(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }

        return !state.is(Blocks.BEDROCK)
            && !state.is(Blocks.BARRIER)
            && !state.is(Blocks.END_PORTAL)
            && !state.is(Blocks.END_PORTAL_FRAME)
            && !state.is(Blocks.END_GATEWAY)
            && !state.is(Blocks.NETHER_PORTAL)
            && !state.is(Blocks.STRUCTURE_BLOCK)
            && !state.is(Blocks.STRUCTURE_VOID)
            && !state.is(Blocks.JIGSAW)
            && !state.is(Blocks.COMMAND_BLOCK)
            && !state.is(Blocks.REPEATING_COMMAND_BLOCK)
            && !state.is(Blocks.CHAIN_COMMAND_BLOCK);
    }


    private static BlockPos randomChunkSurfacePosition(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int playerChunkX = player.blockPosition().getX() >> 4;
        int playerChunkZ = player.blockPosition().getZ() >> 4;
        int chunkX = playerChunkX + ThreadLocalRandom.current().nextInt(-1, 2);
        int chunkZ = playerChunkZ + ThreadLocalRandom.current().nextInt(-1, 2);
        int x = (chunkX << 4) + ThreadLocalRandom.current().nextInt(3, 13);
        int z = (chunkZ << 4) + ThreadLocalRandom.current().nextInt(3, 13);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, player.getBlockY(), z));
        if (surface.closerToCenterThan(player.position(), 7.0)) {
            x = (chunkX << 4) + (x - (chunkX << 4) < 8 ? 12 : 3);
            z = (chunkZ << 4) + (z - (chunkZ << 4) < 8 ? 12 : 3);
            surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, player.getBlockY(), z));
        }
        return surface;
    }

    private static boolean isNaturalChunkTerrain(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) return false;
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
            || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.STONE)
            || state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE)
            || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
            || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.NETHERRACK)
            || state.is(Blocks.END_STONE) || state.is(Blocks.TUFF) || state.is(Blocks.CALCITE)
            || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.SNOW_BLOCK);
    }

    private static int replaceChunkSurface(ServerPlayer player, BlockPos center, int radius, BlockState replacement, int pattern) {
        ServerLevel level = player.serverLevel();
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        int changed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (pattern == 1 && ThreadLocalRandom.current().nextInt(100) < 25) continue;
                if (pattern == 2 && ((dx + dz) & 1) != 0) continue;
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if ((x >> 4) != chunkX || (z >> 4) != chunkZ) continue;
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, center.getY(), z)).below();
                BlockState old = level.getBlockState(ground);
                if (!isNaturalChunkTerrain(old) || old.is(replacement.getBlock())) continue;
                if (level.setBlock(ground, replacement, 3)) changed++;
            }
        }
        return changed;
    }

    private static int removeChunkSurface(ServerPlayer player, BlockPos center, int radius, int depth, int limit) {
        ServerLevel level = player.serverLevel();
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        int removed = 0;
        for (int dx = -radius; dx <= radius && removed < limit; dx++) {
            for (int dz = -radius; dz <= radius && removed < limit; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if ((x >> 4) != chunkX || (z >> 4) != chunkZ) continue;
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, center.getY(), z)).below();
                for (int dy = 0; dy < depth && removed < limit; dy++) {
                    BlockPos target = ground.below(dy);
                    if (!isNaturalChunkTerrain(level.getBlockState(target))) break;
                    if (level.destroyBlock(target, false, player)) removed++;
                }
            }
        }
        return removed;
    }

    private static int floodChunkPatch(ServerPlayer player, BlockPos center, int radius) {
        ServerLevel level = player.serverLevel();
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        int placed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if ((x >> 4) != chunkX || (z >> 4) != chunkZ) continue;
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, center.getY(), z)).below();
                BlockPos waterPos = ground.above();
                if (level.getBlockState(waterPos).isAir() && level.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3)) placed++;
            }
        }
        return placed;
    }

    private static int buildIronCage(ServerLevel level, BlockPos base) {
        return buildCage(level, base, Blocks.IRON_BARS.defaultBlockState());
    }

    private static int buildCage(ServerLevel level, BlockPos base, BlockState material) {
        int placed = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean wall = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                if (wall) {
                    for (int dy = 0; dy <= 1; dy++) {
                        if (placeIfOpen(level, base.offset(dx, dy, dz), material)) {
                            placed++;
                        }
                    }
                }
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (placeIfOpen(level, base.offset(dx, 2, dz), material)) {
                    placed++;
                }
            }
        }

        return placed;
    }

    private static int buildStoneWall(ServerPlayer player, BlockPos center) {
        return buildWall(player, center, Blocks.COBBLESTONE.defaultBlockState(), 2, 3);
    }

    private static int buildWall(
        ServerPlayer player,
        BlockPos center,
        BlockState material,
        int halfWidth,
        int height
    ) {
        ServerLevel level = player.serverLevel();
        int placed = 0;

        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            BlockPos column = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                center.offset(dx, 0, 0)
            );
            for (int dy = 0; dy < height; dy++) {
                if (placeIfOpen(level, column.above(dy), material)) {
                    placed++;
                }
            }
        }

        return placed;
    }

    private static int buildWebSnare(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos base = player.blockPosition();
        int placed = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) != 1 && Math.abs(dz) != 1) {
                    continue;
                }
                for (int dy = 0; dy <= 1; dy++) {
                    if (placeIfOpen(level, base.offset(dx, dy, dz), Blocks.COBWEB.defaultBlockState())) {
                        placed++;
                    }
                }
            }
        }

        return placed;
    }

    private static int buildPillars(ServerPlayer player, BlockPos center) {
        ServerLevel level = player.serverLevel();
        int[][] offsets = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        int placed = 0;

        for (int[] offset : offsets) {
            BlockPos column = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                center.offset(offset[0], 0, offset[1])
            );
            int height = ThreadLocalRandom.current().nextInt(2, 5);
            for (int dy = 0; dy < height; dy++) {
                if (placeIfOpen(level, column.above(dy), Blocks.STONE_BRICKS.defaultBlockState())) {
                    placed++;
                }
            }
        }

        return placed;
    }

    private static int buildBridge(ServerPlayer player, BlockPos center) {
        ServerLevel level = player.serverLevel();
        int placed = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.offset(dx, 0, dz)
                );
                if (placeIfOpen(level, surface, Blocks.STONE_BRICKS.defaultBlockState())) {
                    placed++;
                }
            }
        }

        return placed;
    }

    private static int spawnHordeIfAnotherPlayerNearby(
        ServerPlayer player,
        EntityType<?>[] types,
        int amount,
        int minDistance,
        int maxDistance
    ) {
        long nearbyPlayers = player.serverLevel().players().stream()
            .filter(other -> other.distanceToSqr(player) <= 32.0 * 32.0)
            .count();

        // Один игрок получает базовую орду, каждый следующий рядом добавляет 0,5x.
        double multiplier = 1.0 + Math.max(0L, nearbyPlayers - 1L) * 0.5;
        int scaledAmount = Math.max(1, (int) Math.round(amount * multiplier * 0.6));
        return spawnGroup(player, types, scaledAmount, minDistance, maxDistance);
    }

    private static int spawnGroup(
        ServerPlayer player,
        EntityType<?>[] types,
        int amount,
        int minDistance,
        int maxDistance
    ) {
        ServerLevel level = player.serverLevel();
        int spawned = 0;

        for (int i = 0; i < amount; i++) {
            BlockPos site = randomSurfacePosition(player, minDistance, maxDistance);
            EntityType<?> type = types[ThreadLocalRandom.current().nextInt(types.length)];
            if (type.spawn(level, site, MobSpawnType.EVENT) != null) {
                spawned++;
            }
        }

        return spawned;
    }

    private static boolean placeIfOpen(ServerLevel level, BlockPos pos, BlockState newState) {
        BlockState existing = level.getBlockState(pos);
        if (!existing.isAir() && !existing.canBeReplaced()) {
            return false;
        }
        return level.setBlock(pos, newState, 3);
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void sparkle(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(
            ParticleTypes.HAPPY_VILLAGER,
            player.getX(),
            player.getY() + 1.0,
            player.getZ(),
            14,
            0.6,
            0.5,
            0.6,
            0.03
        );
        level.playSound(
            null,
            player.blockPosition(),
            SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.PLAYERS,
            0.7F,
            1.2F
        );
    }
}
