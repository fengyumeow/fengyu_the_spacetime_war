package com.example.playerinfo.title;

import com.example.playerinfo.network.PersonalStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The titles shown by the player-info UI.  IDs are deliberately stable and
 * independent of enum order so saved player data remains compatible when the
 * list is rearranged later.
 */
public enum PlayerTitle {
    HUMBLE(
            "humble",
            "total_death",
            1_000,
            Component.literal("！？区区？！")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD),
            "总死亡数达到1000"
    ),
    COSMIC_WORM(
            "cosmic_worm",
            "total_death",
            10_000,
            cosmicWormName(),
            "总死亡数达到10000"
    ),
    THOUSAND_SLAYER(
            "thousand_slayer",
            "total_kill",
            1_000,
            Component.literal("千人斩⚔️")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
            "总击杀数达到1000"
    ),
    UNBREAKABLE(
            "unbreakable",
            "total_kill",
            10_000,
            Component.literal("万夫莫开🎖️")
                    .withStyle(Style.EMPTY.withColor(0x8B0000).withBold(true)),
            "总击杀数达到10000"
    ),
    VICTORIOUS_GENERAL(
            "victorious_general",
            "total_win",
            100,
            Component.literal("常胜将军🏆")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
            "总胜场达到100"
    ),
    INVINCIBLE_MONARCH(
            "invincible_monarch",
            "total_win",
            1_000,
            Component.literal("无敌君主👑")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            "总胜场达到1000"
    ),
    STAR_ONE(
            "star_one",
            "total_game",
            50,
            yellowStars(1),
            "总场数达到50"
    ),
    STAR_TWO(
            "star_two",
            "total_game",
            100,
            yellowStars(2),
            "总场数达到100"
    ),
    STAR_THREE(
            "star_three",
            "total_game",
            150,
            yellowStars(3),
            "总场数达到150"
    ),
    STAR_FOUR(
            "star_four",
            "total_game",
            200,
            yellowStars(4),
            "总场数达到200"
    ),
    STAR_FIVE(
            "star_five",
            "total_game",
            250,
            yellowStars(5),
            "总场数达到250"
    ),
    RED_STAR(
            "red_star",
            "total_game",
            300,
            Component.literal("★")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
            "总场数达到300",
            false
    ),
    AMAZING_FENGYU(
            "amazing_fengyu",
            "amazing_fengyu",
            100,
            Component.literal("神奇风鱼喵🐋")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
            "amazing_fengyu达到100",
            true
    );

    public static final String NONE_ID = "";

    private static final Map<String, PlayerTitle> BY_ID;

    static {
        Map<String, PlayerTitle> byId = new HashMap<>();
        for (PlayerTitle title : values()) {
            if (byId.put(title.id, title) != null) {
                throw new IllegalStateException("Duplicate player title id: " + title.id);
            }
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private final String id;
    private final String objectiveName;
    private final int threshold;
    private final Component displayName;
    private final Component unlockCondition;
    private final boolean hiddenUntilUnlocked;

    PlayerTitle(
            String id,
            String objectiveName,
            int threshold,
            Component displayName,
            String unlockCondition
    ) {
        this(
                id,
                objectiveName,
                threshold,
                displayName,
                unlockCondition,
                false
        );
    }

    PlayerTitle(
            String id,
            String objectiveName,
            int threshold,
            Component displayName,
            String unlockCondition,
            boolean hiddenUntilUnlocked
    ) {
        this.id = id;
        this.objectiveName = objectiveName;
        this.threshold = threshold;
        this.displayName = displayName;
        this.unlockCondition = Component.literal(unlockCondition);
        this.hiddenUntilUnlocked = hiddenUntilUnlocked;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return displayName.copy();
    }

    public String objectiveName() {
        return objectiveName;
    }

    public int threshold() {
        return threshold;
    }

    public Component unlockCondition() {
        return unlockCondition.copy();
    }

    public boolean hiddenUntilUnlocked() {
        return hiddenUntilUnlocked;
    }

    public boolean isUnlocked(PersonalStats stats) {
        return switch (objectiveName) {
            case "total_game" -> stats.totalGames() >= threshold;
            case "total_win" -> stats.totalWins() >= threshold;
            case "total_kill" -> stats.totalKills() >= threshold;
            case "total_death" -> stats.totalDeaths() >= threshold;
            case "amazing_fengyu" -> stats.amazingFengyu() >= threshold;
            default -> false;
        };
    }

    @Nullable
    public static PlayerTitle byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        return BY_ID.get(id);
    }

    private static Component yellowStars(int count) {
        return Component.literal("★".repeat(count))
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
    }

    private static Component cosmicWormName() {
        MutableComponent name = Component.empty();
        ChatFormatting[] colors = {
                ChatFormatting.RED,
                ChatFormatting.YELLOW,
                ChatFormatting.GREEN,
                ChatFormatting.BLUE
        };
        String text = "寰宇星区";

        for (int index = 0; index < text.length(); index++) {
            name.append(
                    Component.literal(String.valueOf(text.charAt(index)))
                            .withStyle(colors[index], ChatFormatting.BOLD)
            );
        }

        return name.append(
                Component.literal("🐛")
                        .withStyle(ChatFormatting.BOLD)
        );
    }
}
