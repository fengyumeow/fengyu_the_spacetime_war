package com.example.playerinfo.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record PlayerInfoEntry(
        String playerName,
        int jobId,
        String teamName,
        int teamColor,
        List<Integer> scores
) {
    public PlayerInfoEntry {
        scores = List.copyOf(scores);
    }

    public static void encode(
            PlayerInfoEntry entry,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUtf(entry.playerName());
        buffer.writeVarInt(entry.jobId());
        buffer.writeUtf(entry.teamName());

        /*
         * 队伍文字的ARGB颜色。
         */
        buffer.writeInt(entry.teamColor());

        buffer.writeVarInt(entry.scores().size());

        for (int score : entry.scores()) {
            buffer.writeVarInt(score);
        }
    }

    public static PlayerInfoEntry decode(
            FriendlyByteBuf buffer
    ) {
        String playerName = buffer.readUtf();
        int jobId = buffer.readVarInt();
        String teamName = buffer.readUtf();
        int teamColor = buffer.readInt();

        int scoreCount = buffer.readVarInt();

        List<Integer> scores =
                new ArrayList<>(scoreCount);

        for (int index = 0;
             index < scoreCount;
             index++) {

            scores.add(buffer.readVarInt());
        }

        return new PlayerInfoEntry(
                playerName,
                jobId,
                teamName,
                teamColor,
                scores
        );
    }
}
