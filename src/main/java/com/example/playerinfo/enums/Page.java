package com.example.playerinfo.enums;

public enum Page {
    COMBAT(
            0,
            "screen.playerinfo.title",
            "⚔"
    ),

    PERSONAL(
            1,
            "screen.playerinfo.personal_title",
            "☺"
    ),

    HISTORY(
            2,
            "screen.playerinfo.history_title",
            "🕑"
    );

    public final int index;
    public final String titleKey;
    public final String icon;

    Page(int index, String titleKey, String icon) {
        this.index = index;
        this.titleKey = titleKey;
        this.icon = icon;
    }
}