package com.example.playerinfo.network;

import com.example.playerinfo.client.ClientHistoryManager;
import com.example.playerinfo.data.HistoryData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端响应的历史记录数据包
 * 服务端 -> 客户端
 */
public record ResponseHistoryPacket(List<HistoryData> historyDataList) {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type HISTORY_LIST_TYPE = new TypeToken<List<HistoryData>>() {}.getType();

    public static void encode(ResponseHistoryPacket packet, FriendlyByteBuf buf) {
        // 序列化HistoryData列表
        String jsonData = GSON.toJson(packet.historyDataList);
        buf.writeUtf(jsonData);
    }

    public static ResponseHistoryPacket decode(FriendlyByteBuf buf) {
        // 反序列化HistoryData列表
        String jsonData = buf.readUtf();
        List<HistoryData> historyDataList = GSON.fromJson(jsonData, HISTORY_LIST_TYPE);

        return new ResponseHistoryPacket(historyDataList);
    }

    public static void handle(ResponseHistoryPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 确保在客户端执行
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                // 客户端处理接收到的数据
                ClientHistoryManager.handleHistoryResponse(packet);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 发送历史数据给指定玩家
     */
    public static void sendToClient(ServerPlayer player, List<HistoryData> historyDataList) {
        ResponseHistoryPacket packet = new ResponseHistoryPacket(historyDataList);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

}