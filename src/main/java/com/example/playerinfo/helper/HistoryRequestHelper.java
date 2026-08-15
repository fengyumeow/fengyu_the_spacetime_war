package com.example.playerinfo.helper;

import com.example.playerinfo.client.ClientHistoryManager;
import com.example.playerinfo.data.HistoryData;
import com.example.playerinfo.network.ModNetwork;
import com.example.playerinfo.network.RequestHistoryPacket;

import java.util.List;
import java.util.function.Consumer;

/**
 * 客户端请求历史数据的工具类
 */
public class HistoryRequestHelper {

    /**
     * 请求最近的N条历史记录
     * @param count 记录数量
     */
    public static void requestRecentHistory(int count) {
        RequestHistoryPacket packet = RequestHistoryPacket.requestRecent(count);
        ModNetwork.CHANNEL.sendToServer(packet);
    }

    /**
     * 请求最近的N条历史记录（使用回调）
     * @param count 记录数量
     * @param callback 响应回调
     */
    public static void requestRecentHistory(int count, Consumer<List<HistoryData>> callback) {
        // 添加临时监听器
        ClientHistoryManager.addResponseListener(new Consumer<List<HistoryData>>() {
            @Override
            public void accept(List<HistoryData> historyDataList) {
                callback.accept(historyDataList);
                // 移除自己
                ClientHistoryManager.removeResponseListener(this);
            }
        });

        // 发送请求
        requestRecentHistory(count);
    }

}