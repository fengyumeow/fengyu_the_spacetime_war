package com.example.playerinfo.client;

// CLIENT_EVENTS_PHYSICAL_KEY_V5

import com.example.playerinfo.network.ModNetwork;
import com.example.playerinfo.network.RequestPlayerInfoC2SPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {

    private static final String MOD_ID =
            "fengyu_the_spacetime_war";

    private ClientEvents() {
    }

    /**
     * 直接读取玩家当前绑定按键的物理状态。
     * 不使用KeyMapping.isDown()，避免Screen打开后
     * 按键上下文变化导致状态抖动。
     */
    public static boolean isShowPlayerInfoKeyHeld() {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();

        InputConstants.Key boundKey =
                ModBusEvents.SHOW_PLAYER_INFO.getKey();

        if (boundKey.equals(InputConstants.UNKNOWN)) {
            return false;
        }

        if (boundKey.getType() ==
                InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(
                    window,
                    boundKey.getValue()
            ) == GLFW.GLFW_PRESS;
        }

        if (boundKey.getType() ==
                InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(
                    window,
                    boundKey.getValue()
            );
        }

        // 极少见的SCANCODE绑定使用原有状态作为兼容回退。
        return ModBusEvents.SHOW_PLAYER_INFO.isDown();
    }

    @Mod.EventBusSubscriber(
            modid = MOD_ID,
            value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD
    )
    public static final class ModBusEvents {

        public static final KeyMapping SHOW_PLAYER_INFO =
                new KeyMapping(
                        "key.playerinfo.show_player_info",
                        KeyConflictContext.UNIVERSAL,
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_I,
                        "key.categories.playerinfo"
                );

        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(
                RegisterKeyMappingsEvent event
        ) {
            event.register(SHOW_PLAYER_INFO);
        }
    }

    @Mod.EventBusSubscriber(
            modid = MOD_ID,
            value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE
    )
    public static final class ForgeBusEvents {

        /*
         * true表示当前这一次按住已经发送过请求。
         * 只有松开按键后才会恢复为false。
         */
        private static boolean requestSentForCurrentPress;

        private ForgeBusEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(
                TickEvent.ClientTickEvent event
        ) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();

            if (minecraft.player == null ||
                    minecraft.getConnection() == null) {
                requestSentForCurrentPress = false;
                return;
            }

            boolean keyIsDown =
                    ClientEvents.isShowPlayerInfoKeyHeld();

            if (keyIsDown) {
                if (!requestSentForCurrentPress
                        && minecraft.screen == null) {
                    ModNetwork.CHANNEL.sendToServer(
                            new RequestPlayerInfoC2SPacket()
                    );
                }

                /*
                 * 即使当前打开着聊天栏或其他界面，也消费这次按压。
                 * 这样关闭界面时如果按键仍未松开，不会补触发玩家信息UI。
                 */
                requestSentForCurrentPress = true;

                return;
            }

            requestSentForCurrentPress = false;

            if (minecraft.screen instanceof
                    PlayerInfoScreen screen
                    && !screen.shouldKeepOpen()) {
                minecraft.setScreen(null);
            }
        }
    }
}
