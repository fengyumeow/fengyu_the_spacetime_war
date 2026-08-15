package com.example.playerinfo.client;

import com.example.playerinfo.data.HistoryData;
import com.example.playerinfo.network.ResponseHistoryPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 客户端历史数据管理器
 * 负责存储从服务端接收到的历史数据
 */
public class ClientHistoryManager {
    private static List<HistoryData> cachedHistory = new ArrayList<>();
    private static final List<Consumer<List<HistoryData>>> RESPONSE_LISTENERS = new ArrayList<>();

    /**
     * 处理服务端响应的历史数据
     */
    public static void handleHistoryResponse(ResponseHistoryPacket packet) {
        // 更新缓存
        cachedHistory = packet.historyDataList();

        // 通知监听器
        for (Consumer<List<HistoryData>> listener : new ArrayList<>(RESPONSE_LISTENERS)) {
            listener.accept(cachedHistory);
        }
    }

    /**
     * 获取缓存的历史数据
     */
    public static List<HistoryData> getCachedHistory() {
        return cachedHistory;
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        cachedHistory.clear();
    }

    /**
     * 添加响应监听器
     */
    public static void addResponseListener(Consumer<List<HistoryData>> listener) {
        if (!RESPONSE_LISTENERS.contains(listener)) {
            RESPONSE_LISTENERS.add(listener);
        }
    }

    /**
     * 移除响应监听器
     */
    public static void removeResponseListener(Consumer<List<HistoryData>> listener) {
        RESPONSE_LISTENERS.remove(listener);
    }
}