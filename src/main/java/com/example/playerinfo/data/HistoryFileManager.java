package com.example.playerinfo.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryFileManager {
    private static HistoryFileManager INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path historyDir;

    private HistoryFileManager(Path historyDir) {
        this.historyDir = historyDir;
        createDirectoryIfNotExists();
    }

    // ==================== 初始化 ====================

    public static void init(MinecraftServer server) {
        if (INSTANCE != null) return;
        Path historyDir = server.getWorldPath(LevelResource.ROOT).resolve("SpacetimeWarHistory");
        INSTANCE = new HistoryFileManager(historyDir);
    }

    public static HistoryFileManager getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("HistoryFileManager 尚未初始化或在客户端调用！文件操作仅在服务端可用。");
        }
        return INSTANCE;
    }

    // ==================== 文件操作 ====================

    /**
     * 保存对局信息
     * @param historyId 对局ID
     * @param historyData 对局数据（JSON格式的字符串或可序列化对象）
     * @return 保存的文件名
     */
    public String saveMatch(String historyId, HistoryData historyData) {
        if (INSTANCE == null) return null;

        try {
            String timestamp = LocalDateTime.now().format(FILE_NAME_FORMATTER);
            String fileName = timestamp + "_" + historyId + ".json";
            Path filePath = historyDir.resolve(fileName);

            // 将数据转换为JSON
            String jsonData = GSON.toJson(historyData);

            // 写入文件
            Files.writeString(filePath, jsonData, StandardCharsets.UTF_8);

            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("保存对局记录失败: " + historyId, e);
        }
    }

    /**
     * 读取指定ID的最新对局记录
     * @param historyId 对局ID
     * @return JSON字符串，如果未找到返回null
     */
    public String readMatch(String historyId) {
        if (INSTANCE == null) return null;

        List<Path> files = findFilesByMatchId(historyId);
        if (files.isEmpty()) {
            return null;
        }

        // 返回最新的记录
        Path latestFile = files.get(files.size() - 1);
        return readFileContent(latestFile);
    }

    /**
     * 获取所有历史记录的文件名列表
     * @return 文件名列表（按时间排序）
     */
    public List<String> getAllFileNames() {
        if (INSTANCE == null) return null;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDir, "*.json")) {
            List<String> fileNames = new ArrayList<>();
            for (Path path : stream) {
                fileNames.add(path.getFileName().toString());
            }
            Collections.sort(fileNames);
            return fileNames;
        } catch (IOException e) {
            throw new RuntimeException("获取历史记录列表失败", e);
        }
    }

    /**
     * 读取指定文件名的对局记录（返回HistoryData对象）
     * @param fileName 完整的文件名
     * @return HistoryData对象，如果文件不存在或解析失败返回null
     */
    public HistoryData readByFileNameAsObject(String fileName) {
        String jsonData = readByFileName(fileName);
        if (jsonData == null) {
            return null;
        }

        try {
            return GSON.fromJson(jsonData, HistoryData.class);
        } catch (Exception e) {
            System.err.println("解析历史记录失败: " + fileName);
            return null;
        }
    }

    /**
     * 读取指定文件名的对局记录（返回JSON字符串）
     * @param fileName 完整的文件名
     * @return JSON字符串，如果文件不存在返回null
     */
    public String readByFileName(String fileName) {
        if (INSTANCE == null) return null;

        Path filePath = historyDir.resolve(fileName);
        if (!Files.exists(filePath)) {
            return null;
        }
        return readFileContent(filePath);
    }

    /**
     * 读取最近的N条对局记录（不区分historyId）
     * @param count 要读取的记录数量
     * @return HistoryData对象列表（按时间倒序，最新的在前）
     */
    public List<HistoryData> readRecentMatchesAsObjects(int count) {
        if (INSTANCE == null) return null;

        if (count <= 0) {
            return new ArrayList<>();
        }

        List<String> fileNames = getAllFileNames();
        List<HistoryData> historyDataList = new ArrayList<>();

        // 从最新的文件开始读取
        int startIndex = Math.max(0, fileNames.size() - count);
        for (int i = fileNames.size() - 1; i >= startIndex; i--) {
            HistoryData historyData = readByFileNameAsObject(fileNames.get(i));
            if (historyData != null) {
                historyDataList.add(historyData);
            }
        }

        return historyDataList;
    }

    /**
     * 删除指定ID的所有记录
     * @param historyId 对局ID
     * @return 删除的文件数量
     */
    public int deleteMatches(String historyId) {
        if (INSTANCE == null) return 0;

        List<Path> files = findFilesByMatchId(historyId);
        int deletedCount = 0;

        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
                deletedCount++;
            } catch (IOException e) {
                System.err.println("删除文件失败: " + file.getFileName());
            }
        }

        return deletedCount;
    }

    // ==================== 私有辅助方法 ====================

    private void createDirectoryIfNotExists() {
        try {
            if (!Files.exists(historyDir)) {
                Files.createDirectories(historyDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("创建历史记录目录失败: " + historyDir, e);
        }
    }

    /**
     * 查找指定ID的所有文件
     */
    private List<Path> findFilesByMatchId(String matchId) {
        List<Path> matchingFiles = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDir, "*.json")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                // 文件名格式: timestamp_matchId.json
                if (fileName.endsWith("_" + matchId + ".json")) {
                    matchingFiles.add(path);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("查找对局记录失败: " + matchId, e);
        }

        // 按文件名排序（时间戳部分会按时间排序）
        Collections.sort(matchingFiles);
        return matchingFiles;
    }

    /**
     * 读取文件内容
     */
    private String readFileContent(Path filePath) {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("读取文件失败: " + filePath.getFileName());
            return null;
        }
    }

    /**
     * 获取历史记录目录路径
     */
    public Path getHistoryDir() {
        return historyDir;
    }
}
