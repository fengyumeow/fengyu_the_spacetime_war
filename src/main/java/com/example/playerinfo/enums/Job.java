package com.example.playerinfo.enums;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum Job {
    UNKNOWN(
            0,
            "",
            ChatFormatting.WHITE
    ),

    WARRIOR(
            1,
            "screen.playerinfo.job.warrior",
            ChatFormatting.DARK_BLUE
    ),

    RANGER(
            2,
            "screen.playerinfo.job.ranger",
            ChatFormatting.DARK_GREEN
    ),

    CRUSADER(
            3,
            "screen.playerinfo.job.crusader",
            ChatFormatting.LIGHT_PURPLE
    ),

    IRON_TOWER_GUARD(
            5,
            "screen.playerinfo.job.iron_tower_guard",
            ChatFormatting.GRAY
    ),

    FLAME_DEMON(
            4,
            "screen.playerinfo.job.flame_demon",
            ChatFormatting.RED
    ),

    BERSERKER(
            6,
            "screen.playerinfo.job.berserker",
            ChatFormatting.DARK_RED,
            true
    ),

    DRUID(
            456,
            "screen.playerinfo.job.druid",
            ChatFormatting.GREEN
    );

    private final int id;
    private final String translationKey;
    private final ChatFormatting textColor;
    private final boolean bold;

    Job(
            int id,
            String translationKey,
            ChatFormatting textColor
    ) {
        this(id, translationKey, textColor, false);
    }

    Job(
            int id,
            String translationKey,
            ChatFormatting textColor,
            boolean bold
    ) {
        this.id = id;
        this.translationKey = translationKey;
        this.textColor = textColor;
        this.bold = bold;
    }

    public Component getDisplayName() {
        if (translationKey.isEmpty()) {
            return Component.empty();
        }

        if (bold) {
            return Component.translatable(translationKey).withStyle(
                    textColor,
                    ChatFormatting.BOLD
            );
        }

        return Component.translatable(translationKey).withStyle(textColor);
    }

    public static Job fromId(int id) {
        for (Job job : values()) {
            if (job.id == id) {
                return job;
            }
        }

        return UNKNOWN;
    }
}