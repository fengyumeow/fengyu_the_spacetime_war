package com.example.playerinfo.command;

import com.example.playerinfo.data.HistoryData;
import com.example.playerinfo.data.HistoryFileManager;
import com.example.playerinfo.data.PlayerData;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.minecraft.commands.Commands.literal;

public class matchCommand {
    private static final String GAME_INFO = "game_info";
    private static final String JOB_OBJECTIVE_NAME = "history_job_id";
    private static final String TEAM_OBJECTIVE_NAME = "history_team";
    private static final String KILL_OBJECTIVE = "history_kill_count";
    private static final String DEATH_OBJECTIVE = "history_death_count";
    private static final String DAMAGE_OBJECTIVE = "history_damage_dealt";
    private static final String ABSORBED_OBJECTIVE = "history_damage_absorbed";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("match")
                .then(literal("history")
                        .then(literal("save")
                                .executes(context ->
                                        saveMatchHistory(context.getSource())))));
    }

    public static int saveMatchHistory(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Scoreboard scoreboard = server.getScoreboard();
        int matchId = readScore(scoreboard, "match_id", GAME_INFO);

        // 读取当前计分板数据并构建 HistoryData
        HistoryData historyData = readCurrentMatchData(server, matchId);

        if (historyData == null) {
            source.sendFailure(Component.literal("没有可保存的玩家数据"));
            return 0;
        }

        // 保存到文件
        HistoryFileManager fileManager = HistoryFileManager.getInstance();
        String fileName = fileManager.saveMatch(String.valueOf(matchId), historyData);
        source.sendSuccess(() -> Component.literal("已保存历史记录: " + fileName), true);

        return 1;
    }

    /**
     * 从当前计分板读取所有在线玩家的比赛数据，构造成 HistoryData
     */
    private static HistoryData readCurrentMatchData(MinecraftServer server, int matchId) {
        Scoreboard scoreboard = server.getScoreboard();
        List<PlayerData> playerDataList = new ArrayList<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String playerName = player.getScoreboardName();

            int jobId = readScore(scoreboard, playerName, JOB_OBJECTIVE_NAME);
            int team = readScore(scoreboard, playerName, TEAM_OBJECTIVE_NAME);
            int kills = readScore(scoreboard, playerName, KILL_OBJECTIVE);
            int deaths = readScore(scoreboard, playerName, DEATH_OBJECTIVE);
            int damage = readScore(scoreboard, playerName, DAMAGE_OBJECTIVE);
            int absorbed = readScore(scoreboard, playerName, ABSORBED_OBJECTIVE);

            List<Integer> scores = List.of(kills, deaths, damage, absorbed);
            Integer teamColor;
            switch (team) {
                case 1 -> teamColor = ChatFormatting.RED.getColor();
                case 2 -> teamColor = ChatFormatting.BLUE.getColor();
                default -> teamColor = ChatFormatting.WHITE.getColor();
            }
            int intTeamColor = Objects.requireNonNullElse(teamColor, 0xFFFFFFFF);
            playerDataList.add(new PlayerData(
                    playerName,
                    jobId,
                    String.valueOf(team),
                    intTeamColor,
                    scores
            ));
        }

        if (playerDataList.isEmpty()) {
            return null;
        }

        return new HistoryData(matchId, playerDataList);
    }

    private static int readScore(Scoreboard scoreboard, String playerName, String objectiveName) {
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null || !scoreboard.hasPlayerScore(playerName, objective)) {
            return 0;
        }
        return scoreboard.getOrCreatePlayerScore(playerName, objective).getScore();
    }

}
