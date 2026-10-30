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
                ? "Рядом с вами исчезло блоков: " + removed + "."
                : "Рядом с вами ничего не удалось разрушить.";
        }
    },
    DISTANT_BLOCK_BURST(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) {
            int removed = removeBlockBurst(player, 5, 10, 9, 16);
            return removed > 0
                ? "В 5–10 блоках от вас исчезло блоков: " + removed + "."
                : "На выбранном участке блоки не удалось разрушить.";
        }
    },
    LARGE_NEARBY_CAVE_IN(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) {
            int removed = removeBlockBurst(player, 2, 4, 14, 22);
            return removed > 0
                ? "Обвал рядом с вами убрал блоков: " + removed + "!"
                : "Обвал не нашёл подходящих блоков.";
        }
    },
    WIDE_DISTANT_CAVE_IN(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) {
            int removed = removeBlockBurst(player, 5, 10, 18, 27);
            return removed > 0
                ? "Сильный обвал в 5–10 блоках снёс блоков: " + removed + "!"
                : "Обвал не нашёл подходящих блоков.";
        }
    },
    MINI_BLAST(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) {
            BlockPos center = randomSurfacePosition(player, 5, 10).below();
            int removed = createCrater(player, center, 2);
            return removed > 0
                ? "В 5–10 блоках прогремел взрыв: разрушено блоков " + removed + "!"
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
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE}, 10, 5, 12); return "АПОКАЛИПСИС! Орда из 10 зомби: " + n + "!";
        }
    },
    SKELETON_AMBUSH(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.SKELETON}, 8, 4, 10); return "Лучники окружают вас! Скелетов: " + n;
        }
    },
    SPIDER_NEST(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.SPIDER}, 8, 3, 8); return "Вы потревожили паучье гнездо! Пауков: " + n;
        }
    },
    MIXED_MONSTER_WAVE(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 8, 5, 12); return "Волна монстров! Врагов появилось: " + n;
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
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.CAVE_SPIDER}, 5, 4, 9); return "Ядовитые пауки выползли из темноты: " + n;
        }
    },
    STRAY_SQUAD(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.STRAY}, 5, 5, 10); return "Ледяные лучники атакуют: " + n;
        }
    },
    HUSK_SQUAD(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) {
            int n = spawnGroup(player, new EntityType<?>[] {EntityType.HUSK}, 6, 4, 9); return "Песчаная нежить вышла из укрытий: " + n;
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
    EXTRA_CATASTROPHE_001(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 3, 6, 9, 15); return removed > 0 ? "Катастрофа #1: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #1: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_002(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 8, 4, 11); return "Катастрофа #2: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_003(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBBLESTONE.defaultBlockState()); return placed > 0 ? "Катастрофа #3: ловушка захлопнулась вокруг вас!" : "Катастрофа #3: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_004(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.COBBLESTONE.defaultBlockState(), 2, 4); return placed > 0 ? "Катастрофа #4: опасная стена перекрыла путь!" : "Катастрофа #4: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_005(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 15, 1)); return "Катастрофа #5: вас сковало замедление!"; }
    },
    EXTRA_CATASTROPHE_006(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 5, 14, 20); return removed > 0 ? "Катастрофа #6: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #6: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_007(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 13, 4, 11); return "Катастрофа #7: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_008(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.IRON_BARS.defaultBlockState()); return placed > 0 ? "Катастрофа #8: ловушка захлопнулась вокруг вас!" : "Катастрофа #8: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_009(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.MAGMA_BLOCK.defaultBlockState(), 3, 3); return placed > 0 ? "Катастрофа #9: опасная стена перекрыла путь!" : "Катастрофа #9: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_010(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 8, 0)); return "Катастрофа #10: ядовитый воздух!"; }
    },
    EXTRA_CATASTROPHE_011(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 5, 10, 11, 25); return removed > 0 ? "Катастрофа #11: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #11: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_012(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 9, 4, 11); return "Катастрофа #12: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_013(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBWEB.defaultBlockState()); return placed > 0 ? "Катастрофа #13: ловушка захлопнулась вокруг вас!" : "Катастрофа #13: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_014(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.NETHERRACK.defaultBlockState(), 4, 5); return placed > 0 ? "Катастрофа #14: опасная стена перекрыла путь!" : "Катастрофа #14: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_015(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 18, 1)); return "Катастрофа #15: внезапная слабость!"; }
    },
    EXTRA_CATASTROPHE_016(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 2, 9, 8, 18); return removed > 0 ? "Катастрофа #16: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #16: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_017(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 14, 4, 11); return "Катастрофа #17: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_018(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()); return placed > 0 ? "Катастрофа #18: ловушка захлопнулась вокруг вас!" : "Катастрофа #18: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_019(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.STONE_BRICKS.defaultBlockState(), 5, 4); return placed > 0 ? "Катастрофа #19: опасная стена перекрыла путь!" : "Катастрофа #19: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_020(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 15, 1)); return "Катастрофа #20: земля будто тянет руки вниз!"; }
    },
    EXTRA_CATASTROPHE_021(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 3, 8, 13, 23); return removed > 0 ? "Катастрофа #21: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #21: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_022(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 10, 4, 11); return "Катастрофа #22: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_023(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBBLESTONE.defaultBlockState()); return placed > 0 ? "Катастрофа #23: ловушка захлопнулась вокруг вас!" : "Катастрофа #23: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_024(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.COBBLESTONE.defaultBlockState(), 2, 3); return placed > 0 ? "Катастрофа #24: опасная стена перекрыла путь!" : "Катастрофа #24: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_025(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 12, 0)); return "Катастрофа #25: внезапная тьма ослепляет вас!"; }
    },
    EXTRA_CATASTROPHE_026(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 7, 10, 16); return removed > 0 ? "Катастрофа #26: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #26: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_027(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 6, 4, 11); return "Катастрофа #27: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_028(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.IRON_BARS.defaultBlockState()); return placed > 0 ? "Катастрофа #28: ловушка захлопнулась вокруг вас!" : "Катастрофа #28: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_029(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.MAGMA_BLOCK.defaultBlockState(), 3, 5); return placed > 0 ? "Катастрофа #29: опасная стена перекрыла путь!" : "Катастрофа #29: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_030(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 15, 1)); return "Катастрофа #30: вас сковало замедление!"; }
    },
    EXTRA_CATASTROPHE_031(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 5, 6, 15, 21); return removed > 0 ? "Катастрофа #31: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #31: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_032(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 11, 4, 11); return "Катастрофа #32: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_033(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBWEB.defaultBlockState()); return placed > 0 ? "Катастрофа #33: ловушка захлопнулась вокруг вас!" : "Катастрофа #33: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_034(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.NETHERRACK.defaultBlockState(), 4, 4); return placed > 0 ? "Катастрофа #34: опасная стена перекрыла путь!" : "Катастрофа #34: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_035(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 8, 0)); return "Катастрофа #35: ядовитый воздух!"; }
    },
    EXTRA_CATASTROPHE_036(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 2, 5, 12, 14); return removed > 0 ? "Катастрофа #36: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #36: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_037(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 7, 4, 11); return "Катастрофа #37: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_038(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()); return placed > 0 ? "Катастрофа #38: ловушка захлопнулась вокруг вас!" : "Катастрофа #38: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_039(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.STONE_BRICKS.defaultBlockState(), 5, 3); return placed > 0 ? "Катастрофа #39: опасная стена перекрыла путь!" : "Катастрофа #39: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_040(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 18, 1)); return "Катастрофа #40: внезапная слабость!"; }
    },
    EXTRA_CATASTROPHE_041(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 3, 10, 9, 19); return removed > 0 ? "Катастрофа #41: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #41: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_042(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 12, 4, 11); return "Катастрофа #42: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_043(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBBLESTONE.defaultBlockState()); return placed > 0 ? "Катастрофа #43: ловушка захлопнулась вокруг вас!" : "Катастрофа #43: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_044(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.COBBLESTONE.defaultBlockState(), 2, 5); return placed > 0 ? "Катастрофа #44: опасная стена перекрыла путь!" : "Катастрофа #44: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_045(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 15, 1)); return "Катастрофа #45: земля будто тянет руки вниз!"; }
    },
    EXTRA_CATASTROPHE_046(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 9, 14, 24); return removed > 0 ? "Катастрофа #46: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #46: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_047(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 8, 4, 11); return "Катастрофа #47: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_048(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.IRON_BARS.defaultBlockState()); return placed > 0 ? "Катастрофа #48: ловушка захлопнулась вокруг вас!" : "Катастрофа #48: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_049(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.MAGMA_BLOCK.defaultBlockState(), 3, 4); return placed > 0 ? "Катастрофа #49: опасная стена перекрыла путь!" : "Катастрофа #49: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_050(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 12, 0)); return "Катастрофа #50: внезапная тьма ослепляет вас!"; }
    },
    EXTRA_CATASTROPHE_051(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 5, 8, 11, 17); return removed > 0 ? "Катастрофа #51: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #51: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_052(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 13, 4, 11); return "Катастрофа #52: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_053(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBWEB.defaultBlockState()); return placed > 0 ? "Катастрофа #53: ловушка захлопнулась вокруг вас!" : "Катастрофа #53: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_054(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.NETHERRACK.defaultBlockState(), 4, 3); return placed > 0 ? "Катастрофа #54: опасная стена перекрыла путь!" : "Катастрофа #54: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_055(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 15, 1)); return "Катастрофа #55: вас сковало замедление!"; }
    },
    EXTRA_CATASTROPHE_056(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 2, 7, 8, 22); return removed > 0 ? "Катастрофа #56: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #56: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_057(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 9, 4, 11); return "Катастрофа #57: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_058(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()); return placed > 0 ? "Катастрофа #58: ловушка захлопнулась вокруг вас!" : "Катастрофа #58: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_059(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.STONE_BRICKS.defaultBlockState(), 5, 5); return placed > 0 ? "Катастрофа #59: опасная стена перекрыла путь!" : "Катастрофа #59: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_060(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 8, 0)); return "Катастрофа #60: ядовитый воздух!"; }
    },
    EXTRA_CATASTROPHE_061(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 3, 6, 13, 15); return removed > 0 ? "Катастрофа #61: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #61: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_062(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 14, 4, 11); return "Катастрофа #62: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_063(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBBLESTONE.defaultBlockState()); return placed > 0 ? "Катастрофа #63: ловушка захлопнулась вокруг вас!" : "Катастрофа #63: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_064(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.COBBLESTONE.defaultBlockState(), 2, 4); return placed > 0 ? "Катастрофа #64: опасная стена перекрыла путь!" : "Катастрофа #64: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_065(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 18, 1)); return "Катастрофа #65: внезапная слабость!"; }
    },
    EXTRA_CATASTROPHE_066(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 5, 10, 20); return removed > 0 ? "Катастрофа #66: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #66: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_067(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 10, 4, 11); return "Катастрофа #67: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_068(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.IRON_BARS.defaultBlockState()); return placed > 0 ? "Катастрофа #68: ловушка захлопнулась вокруг вас!" : "Катастрофа #68: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_069(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.MAGMA_BLOCK.defaultBlockState(), 3, 3); return placed > 0 ? "Катастрофа #69: опасная стена перекрыла путь!" : "Катастрофа #69: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_070(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 15, 1)); return "Катастрофа #70: земля будто тянет руки вниз!"; }
    },
    EXTRA_CATASTROPHE_071(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 5, 10, 15, 25); return removed > 0 ? "Катастрофа #71: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #71: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_072(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 6, 4, 11); return "Катастрофа #72: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_073(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBWEB.defaultBlockState()); return placed > 0 ? "Катастрофа #73: ловушка захлопнулась вокруг вас!" : "Катастрофа #73: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_074(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.NETHERRACK.defaultBlockState(), 4, 5); return placed > 0 ? "Катастрофа #74: опасная стена перекрыла путь!" : "Катастрофа #74: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_075(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 12, 0)); return "Катастрофа #75: внезапная тьма ослепляет вас!"; }
    },
    EXTRA_CATASTROPHE_076(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 2, 9, 12, 18); return removed > 0 ? "Катастрофа #76: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #76: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_077(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 11, 4, 11); return "Катастрофа #77: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_078(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()); return placed > 0 ? "Катастрофа #78: ловушка захлопнулась вокруг вас!" : "Катастрофа #78: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_079(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.STONE_BRICKS.defaultBlockState(), 5, 4); return placed > 0 ? "Катастрофа #79: опасная стена перекрыла путь!" : "Катастрофа #79: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_080(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 15, 1)); return "Катастрофа #80: вас сковало замедление!"; }
    },
    EXTRA_CATASTROPHE_081(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 3, 8, 9, 23); return removed > 0 ? "Катастрофа #81: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #81: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_082(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 7, 4, 11); return "Катастрофа #82: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_083(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBBLESTONE.defaultBlockState()); return placed > 0 ? "Катастрофа #83: ловушка захлопнулась вокруг вас!" : "Катастрофа #83: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_084(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.COBBLESTONE.defaultBlockState(), 2, 3); return placed > 0 ? "Катастрофа #84: опасная стена перекрыла путь!" : "Катастрофа #84: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_085(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 8, 0)); return "Катастрофа #85: ядовитый воздух!"; }
    },
    EXTRA_CATASTROPHE_086(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 4, 7, 14, 16); return removed > 0 ? "Катастрофа #86: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #86: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_087(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 12, 4, 11); return "Катастрофа #87: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_088(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.IRON_BARS.defaultBlockState()); return placed > 0 ? "Катастрофа #88: ловушка захлопнулась вокруг вас!" : "Катастрофа #88: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_089(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.MAGMA_BLOCK.defaultBlockState(), 3, 5); return placed > 0 ? "Катастрофа #89: опасная стена перекрыла путь!" : "Катастрофа #89: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_090(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 18, 1)); return "Катастрофа #90: внезапная слабость!"; }
    },
    EXTRA_CATASTROPHE_091(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 5, 6, 11, 21); return removed > 0 ? "Катастрофа #91: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #91: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_092(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 8, 4, 11); return "Катастрофа #92: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_093(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.COBWEB.defaultBlockState()); return placed > 0 ? "Катастрофа #93: ловушка захлопнулась вокруг вас!" : "Катастрофа #93: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_094(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.NETHERRACK.defaultBlockState(), 4, 4); return placed > 0 ? "Катастрофа #94: опасная стена перекрыла путь!" : "Катастрофа #94: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_095(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 15, 1)); return "Катастрофа #95: земля будто тянет руки вниз!"; }
    },
    EXTRA_CATASTROPHE_096(EventCategory.BLOCKS) {
        @Override
        String apply(ServerPlayer player) { int removed = removeBlockBurst(player, 2, 5, 8, 14); return removed > 0 ? "Катастрофа #96: ударная волна уничтожила блоков: " + removed + "!" : "Катастрофа #96: ударная волна прошла без разрушений."; }
    },
    EXTRA_CATASTROPHE_097(EventCategory.MOBS) {
        @Override
        String apply(ServerPlayer player) { int spawned = spawnGroup(player, new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER}, 13, 4, 11); return "Катастрофа #97: в округе появилось враждебных мобов: " + spawned + "!"; }
    },
    EXTRA_CATASTROPHE_098(EventCategory.CAGES) {
        @Override
        String apply(ServerPlayer player) { int placed = buildCage(player.serverLevel(), player.blockPosition(), Blocks.OBSIDIAN.defaultBlockState()); return placed > 0 ? "Катастрофа #98: ловушка захлопнулась вокруг вас!" : "Катастрофа #98: ловушка не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_099(EventCategory.WALLS) {
        @Override
        String apply(ServerPlayer player) { int placed = buildWall(player, randomSurfacePosition(player, 3, 7), Blocks.STONE_BRICKS.defaultBlockState(), 5, 3); return placed > 0 ? "Катастрофа #99: опасная стена перекрыла путь!" : "Катастрофа #99: стена не смогла появиться."; }
    },
    EXTRA_CATASTROPHE_100(EventCategory.OTHER) {
        @Override
        String apply(ServerPlayer player) { player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 12, 0)); return "Катастрофа #100: внезапная тьма ослепляет вас!"; }
    };

    private final EventCategory category;

    MinuteEvent(EventCategory category) {
        this.category = category;
    }

    abstract String apply(ServerPlayer player);

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
