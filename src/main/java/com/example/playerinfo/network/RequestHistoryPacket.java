package com.example.playerinfo.network;

import com.example.playerinfo.data.HistoryData;
import com.example.playerinfo.data.HistoryFileManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端请求历史记录的数据包
 * 客户端 -> 服务端
 */
public class RequestHistoryPacket {
    private final int count; // 请求的记录数量

    // 请求最近的N条记录
    public static RequestHistoryPacket requestRecent(int count) {
        return new RequestHistoryPacket(count);
    }

    private RequestHistoryPacket(int count) {
        this.count = count;
    }

    public static void encode(RequestHistoryPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.count);
    }

    public static RequestHistoryPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        return new RequestHistoryPacket(count);
    }

    public static void handle(RequestHistoryPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 确保在服务端执行
            if (ctx.get().getDirection().getReceptionSide().isServer()) {
                var sender = ctx.get().getSender();
                if (sender != null) {
                    // 从文件读取数据
                    HistoryFileManager fileManager = HistoryFileManager.getInstance();

                    List<HistoryData> historyList = fileManager.readRecentMatchesAsObjects(packet.count);

                    // 发送回客户端
                    ResponseHistoryPacket.sendToClient(sender, historyList);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}