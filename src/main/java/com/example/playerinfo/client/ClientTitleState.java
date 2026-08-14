package com.example.playerinfo.client;

import com.example.playerinfo.title.PlayerTitle;

/**
 * Client-side copy of the title most recently confirmed by the server.
 * Screens read this value while rendering, so an open title screen reacts to
 * the confirmation packet without being recreated.
 */
public final class ClientTitleState {

    private static volatile String equippedTitleId =
            PlayerTitle.NONE_ID;

    private ClientTitleState() {
    }

    public static String getEquippedTitleId() {
        return equippedTitleId;
    }

    public static void setEquippedTitleId(String titleId) {
        equippedTitleId = titleId == null
                ? PlayerTitle.NONE_ID
                : titleId;
    }
}
