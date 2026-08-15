package com.example.playerinfo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.HashMap;
import java.util.Map;

public class ClientPingCache {
    private static final Map<String, Integer> pingCache = new HashMap<>();
    private static long lastUpdateTime = 0;
    private static final long CACHE_DURATION = 1000; // 1秒缓存

    public static int getPing(String playerName) {
        long currentTime = System.currentTimeMillis();

        // 如果缓存过期，重新获取
        if (currentTime - lastUpdateTime > CACHE_DURATION) {
            updateCache();
            lastUpdateTime = currentTime;
        }

        return pingCache.getOrDefault(playerName, -1);
    }

    private static void updateCache() {
        pingCache.clear();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }

        for (PlayerInfo playerInfo : minecraft.getConnection().getOnlinePlayers()) {
            String name = playerInfo.getProfile().getName();
            int ping = playerInfo.getLatency();
            pingCache.put(name, ping);
        }
    }
}