package com.example.playerinfo.client;

// CLIENT_PACKET_HANDLER_PHYSICAL_KEY_V5

import com.example.playerinfo.network.PlayerInfoS2CPacket;
import net.minecraft.client.Minecraft;

public final class ClientPacketHandler {

    private ClientPacketHandler() {
    }

    public static void handle(
            PlayerInfoS2CPacket message
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        /*
         * 如果请求返回时玩家已经松开按键，
         * 直接丢弃这个迟到的数据包。
         */
        if (!ClientEvents
                .isShowPlayerInfoKeyHeld()) {
            return;
        }

        /*
         * 只允许从正常游戏画面打开。
         * 这也会丢弃聊天栏或其他界面打开期间抵达的迟到响应，
         * 防止玩家信息UI替换当前界面。
         */
        if (minecraft.screen != null) {
            return;
        }

        minecraft.setScreen(
                new PlayerInfoScreen(
                        message.personalStats(),
                        message.objectiveNames(),
                        message.players(),
                        message.historyPages(),
                        message.equippedTitleId()
                )
        );
    }
}
