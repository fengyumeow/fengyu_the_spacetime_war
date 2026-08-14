package com.example.playerinfo.network;

import com.example.playerinfo.server.IronSpellDamageTracker;
import com.example.playerinfo.server.PlayerTitleData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

public final class RequestPlayerInfoC2SPacket {

    private static final String JOB_OBJECTIVE_NAME =
            "job_id";

    private static final String TOTAL_GAME_OBJECTIVE =
            "total_game";
    private static final String TOTAL_WIN_OBJECTIVE =
            "total_win";
    private static final String TOTAL_KILL_OBJECTIVE =
            "total_kill";
    private static final String TOTAL_DEATH_OBJECTIVE =
            "total_death";
    private static final String TOTAL_DAMAGE_OBJECTIVE =
            "total_damage";
    private static final String TOTAL_DAMAGE_ABSORBED_OBJECTIVE =
            "total_damage_absorbed";
    private static final String AMAZING_FENGYU_OBJECTIVE =
            "amazing_fengyu";

    /*
     * 以后需要显示更多计分板时，
     * 只需要在这个列表中增加名称。
     */
    private static final List<String> OBJECTIVE_NAMES =
            List.of(
                    "kill_count",
                    "death_count",
                    "damage_dealt",
                    "damage_absorbed"
            );

    private static final int HISTORY_PAGE_COUNT = 5;

    public static void encode(
            RequestPlayerInfoC2SPacket message,
            FriendlyByteBuf buffer
    ) {
        // 请求包不需要携带额外数据
    }

    public static RequestPlayerInfoC2SPacket decode(
            FriendlyByteBuf buffer
    ) {
        return new RequestPlayerInfoC2SPacket();
    }

    public static void handle(
            RequestPlayerInfoC2SPacket message,
            Supplier<NetworkEvent.Context>
                    contextSupplier
    ) {
        NetworkEvent.Context context =
                contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer requester =
                    context.getSender();

            if (requester == null) {
                return;
            }

            MinecraftServer server =
                    requester.getServer();

            if (server == null) {
                return;
            }

            Scoreboard scoreboard =
                    server.getScoreboard();

            Objective jobObjective =
                    scoreboard.getObjective(
                            JOB_OBJECTIVE_NAME
                    );

            PersonalStats personalStats =
                    readPersonalStats(
                            scoreboard,
                            requester.getScoreboardName()
                    );

            String equippedTitleId =
                    PlayerTitleData
                            .getValidEquippedTitleId(
                                    requester,
                                    personalStats
                            );

            List<HistoryPageData> historyPages =
                    readHistoryPages(scoreboard);

            List<PlayerInfoEntry> playerEntries =
                    new ArrayList<>();

            for (ServerPlayer target :
                    server.getPlayerList().getPlayers()) {

                String playerName =
                        target.getScoreboardName();

                PlayerTeam team =
                        scoreboard.getPlayersTeam(
                                playerName
                        );

                /*
                 * 无队伍时发送空字符串，
                 * 客户端会翻译成“无队伍”。
                 */
                String teamName = team == null
                        ? ""
                        : team.getDisplayName()
                        .getString();

                int teamColor =
                        readTeamColor(team);

                int jobId = readScore(
                        scoreboard,
                        playerName,
                        jobObjective
                );

                List<Integer> scores =
                        new ArrayList<>(
                                OBJECTIVE_NAMES.size()
                        );

                for (String objectiveName :
                        OBJECTIVE_NAMES) {

                    Objective objective =
                            scoreboard.getObjective(
                                    objectiveName
                            );

                    int score = readScore(
                            scoreboard,
                            playerName,
                            objective
                    );

                    /*
                     * UI读取damage_dealt时，
                     * 将Iron魔法伤害一起加入。
                     */
                    if ("damage_dealt".equals(
                            objectiveName
                    )) {
                        Objective ironDamageObjective =
                                scoreboard.getObjective(
                                        IronSpellDamageTracker
                                                .OBJECTIVE_NAME
                                );

                        int ironDamage =
                                readScore(
                                        scoreboard,
                                        playerName,
                                        ironDamageObjective
                                );

                        score += ironDamage;
                    }

                    scores.add(score);
                }

                playerEntries.add(
                        new PlayerInfoEntry(
                                playerName,
                                jobId,
                                teamName,
                                teamColor,
                                scores
                        )
                );
            }

            PlayerInfoS2CPacket response =
                    new PlayerInfoS2CPacket(
                            personalStats,
                            equippedTitleId,
                            OBJECTIVE_NAMES,
                            playerEntries,
                            historyPages
                    );

            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(
                            () -> requester
                    ),
                    response
            );
        });

        context.setPacketHandled(true);
    }

    /*
     * 将Minecraft队伍颜色转换为
     * GuiGraphics能够使用的ARGB颜色。
     */
    private static int readTeamColor(
            PlayerTeam team
    ) {
        if (team == null) {
            return 0xFFFFFFFF;
        }

        Integer rgbColor =
                team.getColor().getColor();

        if (rgbColor == null) {
            return 0xFFFFFFFF;
        }

        return 0xFF000000 | rgbColor;
    }

    private static int readScore(
            Scoreboard scoreboard,
            String playerName,
            Objective objective
    ) {
        if (objective == null ||
                !scoreboard.hasPlayerScore(
                        playerName,
                        objective
                )) {
            return 0;
        }

        return scoreboard
                .getOrCreatePlayerScore(
                        playerName,
                        objective
                )
                .getScore();
    }

    private static PersonalStats readPersonalStats(
            Scoreboard scoreboard,
            String playerName
    ) {
        return new PersonalStats(
                readScore(
                        scoreboard,
                        playerName,
                        scoreboard.getObjective(
                                TOTAL_GAME_OBJECTIVE
                        )
                ),
                readScore(
                        scoreboard,
                        playerName,
                        scoreboard.getObjective(
                                TOTAL_WIN_OBJECTIVE
                        )
                ),
                readScore(
                        scoreboard,
                        playerName,
                        scoreboard.getObjective(
                                TOTAL_KILL_OBJECTIVE
                        )
                ),
                readScore(
                        scoreboard,
                        playerName,
                        scoreboard.getObjective(
                                TOTAL_DEATH_OBJECTIVE
                        )
                ),
                readScore(
                        scoreboard,
                        playerName,
                        scoreboard.getObjective(
                                TOTAL_DAMAGE_OBJECTIVE
                        )
                ),
                readScore(
                        scoreboard,
                        playerName,
                        scoreboard.getObjective(
                                TOTAL_DAMAGE_ABSORBED_OBJECTIVE
                        )
                ),
                readScore(
                        scoreboard,
                        playerName,
                        scoreboard.getObjective(
                                AMAZING_FENGYU_OBJECTIVE
                        )
                )
        );
    }

    private static List<HistoryPageData> readHistoryPages(
            Scoreboard scoreboard
    ) {
        List<HistoryPageData> pages =
                new ArrayList<>(HISTORY_PAGE_COUNT);

        for (int pageNumber = 1;
             pageNumber <= HISTORY_PAGE_COUNT;
             pageNumber++) {
            pages.add(
                    readHistoryPage(
                            scoreboard,
                            pageNumber
                    )
            );
        }

        return pages;
    }

    private static HistoryPageData readHistoryPage(
            Scoreboard scoreboard,
            int pageNumber
    ) {
        Objective jobObjective =
                scoreboard.getObjective(
                        "history_job_id_"
                                + pageNumber
                );
        Objective teamObjective =
                scoreboard.getObjective(
                        "history_team_"
                                + pageNumber
                );
        Objective killObjective =
                scoreboard.getObjective(
                        "history_kill_count_"
                                + pageNumber
                );
        Objective deathObjective =
                scoreboard.getObjective(
                        "history_death_count_"
                                + pageNumber
                );
        Objective damageObjective =
                scoreboard.getObjective(
                        "history_damage_dealt_"
                                + pageNumber
                );
        Objective absorbedObjective =
                scoreboard.getObjective(
                        "history_damage_absorbed_"
                                + pageNumber
                );

        Set<String> playerNames =
                new TreeSet<>();

        addScoreOwners(
                scoreboard,
                jobObjective,
                playerNames
        );
        addScoreOwners(
                scoreboard,
                teamObjective,
                playerNames
        );
        addScoreOwners(
                scoreboard,
                killObjective,
                playerNames
        );
        addScoreOwners(
                scoreboard,
                deathObjective,
                playerNames
        );
        addScoreOwners(
                scoreboard,
                damageObjective,
                playerNames
        );
        addScoreOwners(
                scoreboard,
                absorbedObjective,
                playerNames
        );

        List<PlayerInfoEntry> entries =
                new ArrayList<>(playerNames.size());

        for (String playerName : playerNames) {
            String teamName = readHistoryTeamName(
                    scoreboard,
                    playerName,
                    teamObjective
            );

            entries.add(
                    new PlayerInfoEntry(
                            playerName,
                            readScore(
                                    scoreboard,
                                    playerName,
                                    jobObjective
                            ),
                            teamName,
                            0xFFFFFFFF,
                            List.of(
                                    readScore(
                                            scoreboard,
                                            playerName,
                                            killObjective
                                    ),
                                    readScore(
                                            scoreboard,
                                            playerName,
                                            deathObjective
                                    ),
                                    readScore(
                                            scoreboard,
                                            playerName,
                                            damageObjective
                                    ),
                                    readScore(
                                            scoreboard,
                                            playerName,
                                            absorbedObjective
                                    )
                            )
                    )
            );
        }

        return new HistoryPageData(entries);
    }

    private static String readHistoryTeamName(
            Scoreboard scoreboard,
            String playerName,
            Objective teamObjective
    ) {
        if (teamObjective == null
                || !scoreboard.hasPlayerScore(
                playerName,
                teamObjective
        )) {
            return "";
        }

        return Integer.toString(
                scoreboard.getOrCreatePlayerScore(
                        playerName,
                        teamObjective
                ).getScore()
        );
    }

    private static void addScoreOwners(
            Scoreboard scoreboard,
            Objective objective,
            Set<String> playerNames
    ) {
        if (objective == null) {
            return;
        }

        for (Score score :
                scoreboard.getPlayerScores(objective)) {
            playerNames.add(score.getOwner());
        }
    }
}
