package ru.catk.minuteevents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GameplaySettingsPayload(boolean enabled, int intervalSeconds, boolean sharedEvents,
        boolean blocks, int blocksChance, boolean cages, int cagesChance,
        boolean traps, int trapsChance, boolean mobs, int mobsChance) implements CustomPacketPayload {
    public static final Type<GameplaySettingsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MinuteEventsMod.MOD_ID, "gameplay_settings")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, GameplaySettingsPayload> CODEC = StreamCodec.of(
        (b, v) -> {
            b.writeBoolean(v.enabled()); b.writeVarInt(v.intervalSeconds()); b.writeBoolean(v.sharedEvents());
            b.writeBoolean(v.blocks()); b.writeVarInt(v.blocksChance());
            b.writeBoolean(v.cages()); b.writeVarInt(v.cagesChance());
            b.writeBoolean(v.traps()); b.writeVarInt(v.trapsChance());
            b.writeBoolean(v.mobs()); b.writeVarInt(v.mobsChance());
        },
        b -> new GameplaySettingsPayload(b.readBoolean(), b.readVarInt(), b.readBoolean(),
            b.readBoolean(), b.readVarInt(), b.readBoolean(), b.readVarInt(),
            b.readBoolean(), b.readVarInt(), b.readBoolean(), b.readVarInt())
    );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}