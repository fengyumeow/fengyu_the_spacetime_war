package com.example.playerinfo.network;

import com.example.playerinfo.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record PlayerInfoS2CPacket(
        PersonalStats personalStats,
        String equippedTitleId,
        List<String> objectiveNames,
        List<PlayerInfoEntry> players,
        List<HistoryPageData> historyPages
) {
    public PlayerInfoS2CPacket {
        objectiveNames =
                List.copyOf(objectiveNames);

        players =
                List.copyOf(players);

        historyPages =
                List.copyOf(historyPages);
    }

    public static void encode(
            PlayerInfoS2CPacket message,
            FriendlyByteBuf buffer
    ) {
        PersonalStats.encode(
                message.personalStats(),
                buffer
        );
        buffer.writeUtf(message.equippedTitleId());

        buffer.writeVarInt(
                message.objectiveNames().size()
        );

        for (String objectiveName :
                message.objectiveNames()) {

            buffer.writeUtf(objectiveName);
        }

        buffer.writeVarInt(
                message.players().size()
        );

        for (PlayerInfoEntry player :
                message.players()) {

            /*
             * 队伍颜色也会在这里通过
             * PlayerInfoEntry自动写入。
             */
            PlayerInfoEntry.encode(
                    player,
                    buffer
            );
        }

        buffer.writeVarInt(
                message.historyPages().size()
        );

        for (HistoryPageData historyPage :
                message.historyPages()) {
            HistoryPageData.encode(
                    historyPage,
                    buffer
            );
        }
    }

    public static PlayerInfoS2CPacket decode(
            FriendlyByteBuf buffer
    ) {
        PersonalStats personalStats =
                PersonalStats.decode(buffer);
        String equippedTitleId = buffer.readUtf();

        int objectiveCount =
                buffer.readVarInt();

        List<String> objectiveNames =
                new ArrayList<>(
                        objectiveCount
                );

        for (int index = 0;
             index < objectiveCount;
             index++) {

            objectiveNames.add(
                    buffer.readUtf()
            );
        }

        int playerCount =
                buffer.readVarInt();

        List<PlayerInfoEntry> players =
                new ArrayList<>(
                        playerCount
                );

        for (int index = 0;
             index < playerCount;
             index++) {

            players.add(
                    PlayerInfoEntry.decode(buffer)
            );
        }

        int historyPageCount =
                buffer.readVarInt();

        List<HistoryPageData> historyPages =
                new ArrayList<>(historyPageCount);

        for (int index = 0;
             index < historyPageCount;
             index++) {
            historyPages.add(
                    HistoryPageData.decode(buffer)
            );
        }

        return new PlayerInfoS2CPacket(
                personalStats,
                equippedTitleId,
                objectiveNames,
                players,
                historyPages
        );
    }

    public static void handle(
            PlayerInfoS2CPacket message,
            Supplier<NetworkEvent.Context>
                    contextSupplier
    ) {
        NetworkEvent.Context context =
                contextSupplier.get();

        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT,
                        () -> () ->
                                ClientPacketHandler.handle(
                                        message
                                )
                )
        );

        context.setPacketHandled(true);
    }
}
