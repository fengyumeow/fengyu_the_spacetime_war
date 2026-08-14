package com.example.playerinfo.network;

import net.minecraft.network.FriendlyByteBuf;

public record PersonalStats(
        int totalGames,
        int totalWins,
        int totalKills,
        int totalDeaths,
        int totalDamage,
        int totalDamageAbsorbed,
        int amazingFengyu
) {

    public static void encode(
            PersonalStats stats,
            FriendlyByteBuf buffer
    ) {
        buffer.writeVarInt(stats.totalGames());
        buffer.writeVarInt(stats.totalWins());
        buffer.writeVarInt(stats.totalKills());
        buffer.writeVarInt(stats.totalDeaths());
        buffer.writeVarInt(stats.totalDamage());
        buffer.writeVarInt(stats.totalDamageAbsorbed());
        buffer.writeVarInt(stats.amazingFengyu());
    }

    public static PersonalStats decode(
            FriendlyByteBuf buffer
    ) {
        return new PersonalStats(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }
}
