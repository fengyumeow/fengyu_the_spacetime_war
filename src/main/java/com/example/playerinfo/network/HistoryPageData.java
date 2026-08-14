package com.example.playerinfo.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record HistoryPageData(
        List<PlayerInfoEntry> players
) {
    public HistoryPageData {
        players = List.copyOf(players);
    }

    public static void encode(
            HistoryPageData page,
            FriendlyByteBuf buffer
    ) {
        buffer.writeVarInt(page.players().size());

        for (PlayerInfoEntry player : page.players()) {
            PlayerInfoEntry.encode(player, buffer);
        }
    }

    public static HistoryPageData decode(
            FriendlyByteBuf buffer
    ) {
        int playerCount = buffer.readVarInt();
        List<PlayerInfoEntry> players =
                new ArrayList<>(playerCount);

        for (int index = 0;
             index < playerCount;
             index++) {
            players.add(
                    PlayerInfoEntry.decode(buffer)
            );
        }

        return new HistoryPageData(players);
    }
}
