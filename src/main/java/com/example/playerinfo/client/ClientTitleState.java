package com.example.playerinfo.client;

import com.example.playerinfo.enums.PlayerTitle;
import net.minecraft.network.chat.Component;

/**
 * Client-side copy of the title most recently confirmed by the server.
 * Screens read this value while rendering, so an open title screen reacts to
 * the confirmation packet without being recreated.
 */
public final class ClientTitleState {

    private static volatile String equippedTitleId = PlayerTitle.NONE_ID;

    private ClientTitleState() {
    }

    // 获取称号ID
    public static String getEquippedTitleId() {
        return equippedTitleId;
    }

    public static void setEquippedTitleId(String titleId) {
        equippedTitleId = titleId == null
                ? PlayerTitle.NONE_ID
                : titleId;
    }

    // 获取称号名称
    public static Component getEquippedTitleComponent() {
        String titleId = ClientTitleState.getEquippedTitleId();
        PlayerTitle title = PlayerTitle.byId(titleId);
        if (title == null) {
            return Component.empty();
        }
        return title.displayName();
    }
}
