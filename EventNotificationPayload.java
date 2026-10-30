package ru.catk.minuteevents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EventNotificationPayload(String message) implements CustomPacketPayload {
    public static final Type<EventNotificationPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MinuteEventsMod.MOD_ID, "event_notification")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, EventNotificationPayload> CODEC =
        StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.message(), 512),
            buffer -> new EventNotificationPayload(buffer.readUtf(512))
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
