package com.example.playerinfo.data;

import java.util.ArrayList;
import java.util.List;

public class HistoryData {
    private int historyId;
    private List<PlayerData> playerDataList;

    public HistoryData() {
        this.historyId = 0;
        this.playerDataList = new ArrayList<>();
    }

    public HistoryData(int historyId, List<PlayerData> playerDataList) {
        this.historyId = historyId;
        this.playerDataList = playerDataList;
    }

    public int getHistoryId() {
        return historyId;
    }

    public void setHistoryId(int historyId) {
        this.historyId = historyId;
    }

    public List<PlayerData> getPlayerDataList() {
        return playerDataList;
    }

    public void setPlayerDataList(List<PlayerData> playerDataList) {
        this.playerDataList = playerDataList;
    }
}
