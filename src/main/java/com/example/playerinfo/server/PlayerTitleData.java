package com.example.playerinfo.server;

import com.example.playerinfo.PlayerInfoMod;
import com.example.playerinfo.network.PersonalStats;
import com.example.playerinfo.title.PlayerTitle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import javax.annotation.Nullable;

/** Server-authoritative storage and validation for a player's equipped title. */
public final class PlayerTitleData {
    private static final String EQUIPPED_TITLE_KEY = "EquippedTitle";

    private PlayerTitleData() {
    }

    public static String getEquippedTitleId(Player player) {
        CompoundTag persistedData = getPersistedData(player);
        if (!persistedData.contains(PlayerInfoMod.MOD_ID)) {
            return PlayerTitle.NONE_ID;
        }
        return persistedData.getCompound(PlayerInfoMod.MOD_ID)
                .getString(EQUIPPED_TITLE_KEY);
    }

    @Nullable
    public static PlayerTitle getEquippedTitle(Player player) {
        return PlayerTitle.byId(getEquippedTitleId(player));
    }

    /**
     * Returns the stored title only if it still exists and is unlocked. Invalid
     * persisted state is removed so every caller observes the same authority.
     */
    public static String getValidEquippedTitleId(ServerPlayer player) {
        return getValidEquippedTitleId(player, readPersonalStats(player));
    }

    public static String getValidEquippedTitleId(
            ServerPlayer player,
            PersonalStats stats
    ) {
        String storedId = getEquippedTitleId(player);
        if (storedId.isEmpty()) {
            return PlayerTitle.NONE_ID;
        }

        PlayerTitle title = PlayerTitle.byId(storedId);
        if (title != null && title.isUnlocked(stats)) {
            return storedId;
        }

        setEquippedTitleId(player, PlayerTitle.NONE_ID);
        refreshPlayerNames(player);
        return PlayerTitle.NONE_ID;
    }

    /**
     * Applies a client request after reading the current scoreboard. An empty
     * ID unequips; an unknown or locked ID leaves the current valid title alone.
     */
    public static String applyRequestedTitle(
            ServerPlayer player,
            String requestedId
    ) {
        PersonalStats stats = readPersonalStats(player);
        String currentId = getValidEquippedTitleId(player, stats);
        String normalizedId = requestedId == null
                ? PlayerTitle.NONE_ID
                : requestedId;

        if (normalizedId.isEmpty()) {
            if (!currentId.isEmpty()) {
                setEquippedTitleId(player, PlayerTitle.NONE_ID);
                refreshPlayerNames(player);
            }
            return PlayerTitle.NONE_ID;
        }

        PlayerTitle requestedTitle = PlayerTitle.byId(normalizedId);
        if (requestedTitle == null || !requestedTitle.isUnlocked(stats)) {
            return currentId;
        }

        if (!normalizedId.equals(currentId)) {
            setEquippedTitleId(player, normalizedId);
            refreshPlayerNames(player);
        }
        return normalizedId;
    }

    public static PersonalStats readPersonalStats(ServerPlayer player) {
        Scoreboard scoreboard = player.getServer().getScoreboard();
        String scoreOwner = player.getScoreboardName();
        return new PersonalStats(
                readScore(scoreboard, scoreOwner, "total_game"),
                readScore(scoreboard, scoreOwner, "total_win"),
                readScore(scoreboard, scoreOwner, "total_kill"),
                readScore(scoreboard, scoreOwner, "total_death"),
                readScore(scoreboard, scoreOwner, "total_damage"),
                readScore(scoreboard, scoreOwner, "total_damage_absorbed"),
                readScore(scoreboard, scoreOwner, "amazing_fengyu"),
                readScore(scoreboard, scoreOwner, "balrog_huge_energy"),
                readScore(scoreboard, scoreOwner, "weapon_VinesHitCount"),
                readScore(scoreboard, scoreOwner, "iron_chenghao1"),
                readScore(scoreboard, scoreOwner, "ranger_50kill"),
                readScore(scoreboard, scoreOwner, "vanilla_nether_enchanting"),
                readScore(scoreboard, scoreOwner, "admin_server")
        );
    }

    public static void refreshPlayerNames(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    private static void setEquippedTitleId(Player player, String titleId) {
        CompoundTag persistedData = getPersistedData(player);
        CompoundTag modData = persistedData.getCompound(PlayerInfoMod.MOD_ID);
        if (titleId.isEmpty()) {
            modData.remove(EQUIPPED_TITLE_KEY);
        } else {
            modData.putString(EQUIPPED_TITLE_KEY, titleId);
        }
        persistedData.put(PlayerInfoMod.MOD_ID, modData);
    }

    private static CompoundTag getPersistedData(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG)) {
            persistentData.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return persistentData.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static int readScore(
            Scoreboard scoreboard,
            String scoreOwner,
            String objectiveName
    ) {
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null || !scoreboard.hasPlayerScore(scoreOwner, objective)) {
            return 0;
        }
        return scoreboard.getOrCreatePlayerScore(scoreOwner, objective).getScore();
    }
}
