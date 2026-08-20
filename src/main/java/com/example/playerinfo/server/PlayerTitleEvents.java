package com.example.playerinfo.server;

import com.example.playerinfo.PlayerInfoMod;
import com.example.playerinfo.enums.PlayerTitle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = PlayerInfoMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class PlayerTitleEvents {
    private PlayerTitleEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerTitle title = validTitle(player);
        if (title != null) {
            event.setDisplayname(prefixed(title, event.getDisplayname()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerTitle title = validTitle(player);
        if (title == null) {
            return;
        }

        Component existingName = event.getDisplayName();
        if (existingName == null) {
            event.setDisplayName(player.getDisplayName());
            return;
        }
        event.setDisplayName(prefixed(title, existingName));
    }

    private static PlayerTitle validTitle(ServerPlayer player) {
        return PlayerTitle.byId(PlayerTitleData.getValidEquippedTitleId(player));
    }

    private static Component prefixed(PlayerTitle title, Component playerName) {
        return Component.empty()
                .append(title.displayName())
                .append(Component.literal(" "))
                .append(playerName);
    }
}
