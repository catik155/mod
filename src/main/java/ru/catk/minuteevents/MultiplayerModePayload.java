package ru.catk.minuteevents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MultiplayerModePayload(boolean sharedEvents) implements CustomPacketPayload {
    public static final Type<MultiplayerModePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MinuteEventsMod.MOD_ID, "multiplayer_mode")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MultiplayerModePayload> CODEC =
        StreamCodec.of(
            (buffer, value) -> buffer.writeBoolean(value.sharedEvents()),
            buffer -> new MultiplayerModePayload(buffer.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
