package com.example.playerinfo.data;

import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    private String name;
    private int jobId;
    private String teamName;
    private int teamColor;
    private List<Integer> scores;

    public PlayerData() {
        this.name = "";
        this.jobId = 0;
        this.teamName = "";
        this.teamColor = 0;
        this.scores = new ArrayList<>();
    }

    public PlayerData(String name, int jobId, String teamName, int teamColor, List<Integer> scores) {
        this.name = name;
        this.jobId = jobId;
        this.teamName = teamName;
        this.teamColor = teamColor;
        this.scores = scores;
    }

    public String getName() {
        return name;
    }

    public int getJobId() {
        return jobId;
    }

    public String getTeamName() {
        return teamName;
    }

    public int getTeamColor() {
        return teamColor;
    }

    public List<Integer> getScores() {
        return scores;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public void setTeamColor(int teamColor) {
        this.teamColor = teamColor;
    }

    public void setScores(List<Integer> scores) {
        this.scores = scores;
    }

}
