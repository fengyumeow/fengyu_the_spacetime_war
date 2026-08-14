package com.example.playerinfo.network;

import com.example.playerinfo.server.PlayerTitleData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record SetEquippedTitleC2SPacket(String titleId) {
    public SetEquippedTitleC2SPacket {
        titleId = titleId == null ? "" : titleId;
    }

    public static void encode(SetEquippedTitleC2SPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.titleId());
    }

    public static SetEquippedTitleC2SPacket decode(FriendlyByteBuf buffer) {
        return new SetEquippedTitleC2SPacket(buffer.readUtf());
    }

    public static void handle(
            SetEquippedTitleC2SPacket message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            String authoritativeId = PlayerTitleData.applyRequestedTitle(
                    player,
                    message.titleId()
            );
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new TitleStateS2CPacket(authoritativeId)
            );
        });
        context.setPacketHandled(true);
    }
}
