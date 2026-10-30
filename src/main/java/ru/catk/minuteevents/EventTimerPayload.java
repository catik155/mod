package ru.catk.minuteevents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EventTimerPayload(int secondsRemaining) implements CustomPacketPayload {
    public static final Type<EventTimerPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MinuteEventsMod.MOD_ID, "event_timer")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, EventTimerPayload> CODEC =
        StreamCodec.of(
            (buffer, payload) -> buffer.writeVarInt(payload.secondsRemaining()),
            buffer -> new EventTimerPayload(buffer.readVarInt())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
