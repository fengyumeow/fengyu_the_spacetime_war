package com.example.playerinfo.network;

import com.example.playerinfo.PlayerInfoMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "8";
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    ResourceLocation.fromNamespaceAndPath(
                            PlayerInfoMod.MOD_ID,
                            "main"
                    ),
                    () -> PROTOCOL_VERSION,
                    PROTOCOL_VERSION::equals,
                    PROTOCOL_VERSION::equals
            );

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                RequestPlayerInfoC2SPacket.class,
                RequestPlayerInfoC2SPacket::encode,
                RequestPlayerInfoC2SPacket::decode,
                RequestPlayerInfoC2SPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                PlayerInfoS2CPacket.class,
                PlayerInfoS2CPacket::encode,
                PlayerInfoS2CPacket::decode,
                PlayerInfoS2CPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                SetEquippedTitleC2SPacket.class,
                SetEquippedTitleC2SPacket::encode,
                SetEquippedTitleC2SPacket::decode,
                SetEquippedTitleC2SPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                TitleStateS2CPacket.class,
                TitleStateS2CPacket::encode,
                TitleStateS2CPacket::decode,
                TitleStateS2CPacket::handle
        );

        // 客户端请求历史记录
        CHANNEL.registerMessage(
                packetId++,
                RequestHistoryPacket.class,
                RequestHistoryPacket::encode,
                RequestHistoryPacket::decode,
                RequestHistoryPacket::handle
        );

        // 服务端响应历史记录
        CHANNEL.registerMessage(
                packetId++,
                ResponseHistoryPacket.class,
                ResponseHistoryPacket::encode,
                ResponseHistoryPacket::decode,
                ResponseHistoryPacket::handle
        );
    }

    private ModNetwork() {
    }
}
