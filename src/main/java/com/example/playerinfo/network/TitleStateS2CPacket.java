package com.example.playerinfo.network;

import com.example.playerinfo.client.ClientTitleState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record TitleStateS2CPacket(String equippedTitleId) {
    public TitleStateS2CPacket {
        equippedTitleId = equippedTitleId == null ? "" : equippedTitleId;
    }

    public static void encode(TitleStateS2CPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.equippedTitleId());
    }

    public static TitleStateS2CPacket decode(FriendlyByteBuf buffer) {
        return new TitleStateS2CPacket(buffer.readUtf());
    }

    public static void handle(
            TitleStateS2CPacket message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientTitleState.setEquippedTitleId(message.equippedTitleId())
        ));
        context.setPacketHandled(true);
    }
}
