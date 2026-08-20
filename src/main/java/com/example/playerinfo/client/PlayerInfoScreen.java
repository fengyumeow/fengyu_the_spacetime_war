package com.example.playerinfo.client;

// PLAYER_INFO_SCREEN_PHYSICAL_KEY_V5
// Forge 1.20.1：读取当前绑定键的物理按下状态，防止界面频闪。

import com.example.playerinfo.data.HistoryData;
import com.example.playerinfo.data.PlayerData;
import com.example.playerinfo.enums.Job;
import com.example.playerinfo.enums.Page;
import com.example.playerinfo.enums.SortKey;
import com.example.playerinfo.network.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

import java.util.*;

public final class PlayerInfoScreen extends Screen {

    private static final int PADDING = 10;
    private static final int TITLE_HEIGHT = 26;
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 20;

    private static final int BUTTON_WIDTH = 24;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    private static final int PERSONAL_PANEL_MIN_HEIGHT = 300;
    private static final long PAGE_TRANSITION_DURATION_MS = 240L;
    private static final int HISTORY_PAGE_COUNT = 5;

    private static final List<String> HISTORY_OBJECTIVE_NAMES =
            List.of(
                    "kill_count",
                    "death_count",
                    "damage_dealt",
                    "damage_absorbed"
            );

    private static final int PANEL_COLOR = 0xC0101010;
    private static final int PERSONAL_PANEL_COLOR = 0xFF101010;
    private static final int HEADER_COLOR = 0xB0303030;
    private static final int ROW_COLOR_1 = 0x50303030;
    private static final int ROW_COLOR_2 = 0x30202020;
    private static final int BORDER_COLOR = 0x90FFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HEADER_TEXT_COLOR = 0xFFFFD700;
    private static final int LOCAL_PLAYER_ROW_COLOR = 0x5030A0A0; // 半透明蓝绿色，用于高亮自己
    private static final int LOCAL_PLAYER_NAME_COLOR = 0xFFFFFF00; // 文字颜色（黄色）

    private static final int AVATAR_SIZE = 16;          // 头像大小（可根据行高调整）
    private static final int AVATAR_TEXT_GAP = 4;       // 头像与文本间距
    private final Map<String, ResourceLocation> skinCache = new HashMap<>();

    /*
     * 使用静态字段保存设置，使界面关闭后再次打开时
     * 仍然保持上一次选择的排序方式。
     */
    private static SortKey combatSortKey = SortKey.KILLS;
    private static boolean combatSortDescending = true;
    private static SortKey historySortKey = SortKey.KILLS;
    private static boolean historySortDescending = true;

    private static Page selectedPage = Page.COMBAT;

    private final PersonalStats personalStats;
    private final List<String> objectiveNames;
    private final List<PlayerInfoEntry> players;
    private List<HistoryPageData> historyPages;
    private final List<PlayerInfoEntry> sortedPlayers =
            new ArrayList<>();
    private final List<PlayerInfoEntry> sortedHistoryPlayers =
            new ArrayList<>();
    private String localPlayerName = "";

    private int combatPageButtonX;
    private int personalPageButtonX;
    private int historyPageButtonX;
    private int pageButtonY;
    private int historyPageButtonsX;
    private int historyPageButtonsY;

    private int scrollOffset;
    private int visibleRows;
    private int maxScroll;
    private int selectedHistoryPageIndex;
    private int historyScrollOffset;
    private int historyVisibleRows;
    private int historyMaxScroll;
    private int personalTitleButtonX;
    private int personalTitleButtonY;
    private int personalTitleButtonWidth;
    private int personalTitleButtonHeight;
    private boolean keepOpen;

    private Page currentPage;
    private Page transitionTarget;
    private boolean pageTransitionActive;
    private long pageTransitionStartMillis;
    private boolean historyLoading;

    public PlayerInfoScreen(
            PersonalStats personalStats,
            List<String> objectiveNames,
            List<PlayerInfoEntry> players,
            List<HistoryPageData> historyPages,
            String equippedTitleId
    ) {
        super(Component.translatable(
                "screen.playerinfo.title"
        ));

        this.personalStats = personalStats;
        ClientTitleState.setEquippedTitleId(
                equippedTitleId
        );

        this.objectiveNames =
                List.copyOf(objectiveNames);

        this.players =
                List.copyOf(players);

        this.historyPages = new ArrayList<>();  // 初始使用空数据，之后动态更新
        this.historyLoading = false;

        // 如果缓存有数据，立即填充
        List<HistoryData> cached = ClientHistoryManager.getCachedHistory();
        if (!cached.isEmpty()) {
            updateHistoryData(cached);
        }

        this.sortedPlayers.addAll(this.players);

        this.currentPage = selectedPage;
        this.transitionTarget = selectedPage;

        refreshSortedHistoryPlayers();
    }

    @Override
    protected void init() {
        super.init();
        // 缓存本地玩家名
        if (minecraft != null && minecraft.player != null) {
            localPlayerName = minecraft.player.getScoreboardName();
        }

        cachePlayerSkins();
        refreshSortedPlayers();
        refreshSortedHistoryPlayers();

        updateButtonPositions();

        // 注册历史数据监听器
        ClientHistoryManager.addResponseListener(this::onHistoryResponse);
        // 如果当前就在历史页且数据未加载，立即请求
        if (currentPage == Page.HISTORY) {
            requestHistoryData();
        }
    }

    private void updateButtonPositions() {
        // 更新按钮位置
        int panelWidth = calculatePanelWidth();
        int panelHeight = calculatePanelHeight();
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        // 界面切换按钮位置
        int startX = panelX + PADDING;
        combatPageButtonX = startX;
        personalPageButtonX = startX + BUTTON_WIDTH + BUTTON_GAP;
        historyPageButtonX = startX + (BUTTON_WIDTH + BUTTON_GAP) * 2;
        pageButtonY = panelY + PADDING + 3;
        // 历史记录页码切换按钮
        historyPageButtonsX = panelX + panelWidth - PADDING - BUTTON_WIDTH;
        historyPageButtonsY = panelY + PADDING + TITLE_HEIGHT;
    }

    @Override
    public void tick() {
        super.tick();

        /*
         * 通过ClientEvents读取玩家当前绑定键的
         * 物理按下状态，不依赖Screen中的按键上下文。
         */
        if (!keepOpen && !ClientEvents
                .isShowPlayerInfoKeyHeld()) {
            this.onClose();
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        RenderSystem.enableBlend();

        int scoreColumnCount =
                Math.max(1, objectiveNames.size());

        int panelWidth = calculatePanelWidth();
        int panelHeight = calculatePanelHeight();

        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        int tableX = panelX + PADDING;
        int tableWidth = panelWidth - PADDING * 2;

        int headerY =
                panelY + PADDING + TITLE_HEIGHT;

        int rowsY = headerY + HEADER_HEIGHT;
        int rowsBottom = panelY + panelHeight - PADDING;
        int rowsAreaHeight = rowsBottom - rowsY;

        visibleRows = Math.max(
                1,
                rowsAreaHeight / ROW_HEIGHT
        );

        maxScroll = Math.max(
                0,
                sortedPlayers.size() - visibleRows
        );

        scrollOffset = Mth.clamp(
                scrollOffset,
                0,
                maxScroll
        );

        float pagePosition = updatePagePosition();
        int combatOffsetY = -Math.round(
                pagePosition * panelHeight
        );

        /*
         * 三个页面组成连续的纵向页面栈。固定裁剪区保证动画期间
         * 只显示面板视口内的内容。
         */
        graphics.enableScissor(
                panelX,
                panelY,
                panelX + panelWidth,
                panelY + panelHeight
        );

        graphics.pose().pushPose();
        graphics.pose().translate(
                0.0F,
                combatOffsetY,
                0.0F
        );

        // 半透明主面板
        graphics.fill(
                panelX,
                panelY,
                panelX + panelWidth,
                panelY + panelHeight,
                PANEL_COLOR
        );

        // 面板边框
        graphics.fill(
                panelX,
                panelY,
                panelX + panelWidth,
                panelY + 1,
                BORDER_COLOR
        );

        graphics.fill(
                panelX,
                panelY + panelHeight - 1,
                panelX + panelWidth,
                panelY + panelHeight,
                BORDER_COLOR
        );

        graphics.fill(
                panelX,
                panelY,
                panelX + 1,
                panelY + panelHeight,
                BORDER_COLOR
        );

        graphics.fill(
                panelX + panelWidth - 1,
                panelY,
                panelX + panelWidth,
                panelY + panelHeight,
                BORDER_COLOR
        );

        // 标题
        graphics.drawCenteredString(
                font,
                title,
                width / 2,
                panelY + PADDING + 7,
                HEADER_TEXT_COLOR
        );

        // 表头背景
        graphics.fill(
                tableX,
                headerY,
                tableX + tableWidth,
                headerY + HEADER_HEIGHT,
                HEADER_COLOR
        );

        int nameWidth = Math.max(1, tableWidth * 22 / 100);
        int jobWidth = Math.max(1, tableWidth * 14 / 100);
        int teamWidth = Math.max(1, tableWidth * 14 / 100);

        int remainingWidth = tableWidth - nameWidth - jobWidth - teamWidth;
        int scoreWidth = remainingWidth / scoreColumnCount;

        int currentX = tableX;

        // 名字
        drawHeader(
                graphics,
                Component.translatable(
                        "screen.playerinfo.player_name"
                ),
                currentX,
                headerY,
                nameWidth
        );
        currentX += nameWidth;

        // 职业
        drawHeader(
                graphics,
                Component.translatable(
                        "screen.playerinfo.job"
                ),
                currentX,
                headerY,
                jobWidth
        );
        currentX += jobWidth;

        // 队伍
        drawSortableHeader(
                graphics,
                Component.translatable("screen.playerinfo.team"),
                currentX,
                headerY,
                teamWidth,
                SortKey.TEAM,
                false,
                mouseX,
                mouseY);
        currentX += teamWidth;

        // 得分
        for (String objectiveName : objectiveNames) {
            SortKey key = switch (objectiveName) {
//                case "kill_count" -> SortKey.KILLS;   // 默认为 KILLS
                case "death_count" -> SortKey.DEATHS;
                case "damage_dealt" -> SortKey.DAMAGE;
                case "damage_absorbed" -> SortKey.ABSORBED;
                default -> SortKey.KILLS;
            };
            drawSortableHeader(
                    graphics,
                    getObjectiveDisplayName(objectiveName),
                    currentX,
                    headerY,
                    scoreWidth,
                    key,
                    false,
                    mouseX,
                    mouseY);
            currentX += scoreWidth;
        }

        // 表格内容裁剪区域
        graphics.enableScissor(
                tableX,
                rowsY + combatOffsetY,
                tableX + tableWidth,
                rowsBottom + combatOffsetY
        );

        // 遍历并绘制每一行玩家数据
        int endIndex = Math.min(sortedPlayers.size(), scrollOffset + visibleRows);

        for (int index = scrollOffset; index < endIndex; index++) {

            PlayerInfoEntry entry = sortedPlayers.get(index);

            int visibleIndex = index - scrollOffset;

            int rowY = rowsY + visibleIndex * ROW_HEIGHT;

            // 渲染行背景
            boolean isLocal = isLocalPlayer(entry.playerName());
            int rowColor;
            if (isLocal) {
                rowColor = LOCAL_PLAYER_ROW_COLOR;
            } else {
                rowColor = visibleIndex % 2 == 0 ? ROW_COLOR_1 : ROW_COLOR_2;
            }

            graphics.fill(tableX,
                    rowY,
                    tableX + tableWidth,
                    rowY + ROW_HEIGHT,
                    rowColor
            );
            currentX = tableX;

            // 玩家名称
            drawPlayerNameCell(graphics, entry.playerName(), currentX, rowY, nameWidth);
            // Ping
            int ping = ClientPingCache.getPing(entry.playerName());
            int pingColor = PingColors.getColor(ping);
            drawRightCell(
                    graphics,
                    ping + "ms",
                    currentX,
                    rowY,
                    nameWidth,
                    pingColor
            );
            currentX += nameWidth;

            // 职业
            Job job = Job.fromId(entry.jobId());
            drawCenterCell(
                    graphics,
                    job.getDisplayName(),
                    currentX,
                    rowY,
                    jobWidth
            );
            currentX += jobWidth;

            // 队伍
            boolean hasTeam = !entry.teamName().isBlank();

            String teamName = Component.translatable("screen.playerinfo.no_team").getString();
            if (hasTeam) {
                if (entry.teamName().equals("red")) {
                    teamName = Component.translatable("screen.playerinfo.team.red").getString();
                } else if (entry.teamName().equals("blue")) {
                    teamName = Component.translatable("screen.playerinfo.team.blue").getString();
                } else {
                    teamName = entry.teamName();
                }
            }

            int teamTextColor = hasTeam
                    ? entry.teamColor()
                    : TEXT_COLOR;

            drawCenterCell(
                    graphics,
                    teamName,
                    currentX,
                    rowY,
                    teamWidth,
                    teamTextColor
            );

            currentX += teamWidth;

            for (int scoreIndex = 0;
                 scoreIndex < objectiveNames.size();
                 scoreIndex++) {

                int score =
                        scoreIndex < entry.scores().size()
                                ? entry.scores().get(scoreIndex)
                                : 0;

                drawCenterCell(
                        graphics,
                        Integer.toString(score),
                        currentX,
                        rowY,
                        scoreWidth
                );

                currentX += scoreWidth;
            }
        }

        graphics.disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            int scrollBarX =
                    panelX + panelWidth - 5;

            int scrollBarHeight =
                    rowsBottom - rowsY;

            graphics.fill(
                    scrollBarX,
                    rowsY,
                    scrollBarX + 2,
                    rowsBottom,
                    0x50505050
            );

            int thumbHeight = Math.max(
                    12,
                    scrollBarHeight * visibleRows /
                            sortedPlayers.size()
            );

            int thumbTravel =
                    scrollBarHeight - thumbHeight;

            int thumbY = rowsY +
                    thumbTravel * scrollOffset /
                            maxScroll;

            graphics.fill(
                    scrollBarX,
                    thumbY,
                    scrollBarX + 2,
                    thumbY + thumbHeight,
                    0xFFFFFFFF
            );
        }

        graphics.pose().popPose();

        int personalPanelY = panelY
                + combatOffsetY + panelHeight;
        renderPersonalPage(
                graphics,
                panelX,
                personalPanelY,
                panelWidth,
                panelHeight
        );

        int historyPanelY = panelY
                + combatOffsetY + panelHeight * 2;
        renderHistoryPage(
                graphics,
                panelX,
                historyPanelY,
                panelWidth,
                panelHeight,
                mouseX,
                mouseY
        );

        graphics.disableScissor();
        RenderSystem.enableBlend();

        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        /*
         * 直接绘制按钮，不依赖原生Button控件的渲染层。
         */
        drawPageControls(graphics, mouseX, mouseY);

        if (!pageTransitionActive
                && currentPage == Page.PERSONAL
                && isInsideRectangle(
                mouseX,
                mouseY,
                personalTitleButtonX,
                personalTitleButtonY,
                personalTitleButtonWidth,
                personalTitleButtonHeight
        )) {
            drawTitleButton(graphics, true);
            graphics.renderTooltip(
                    font,
                    Component.translatable(
                            "screen.playerinfo.titles"
                    ),
                    mouseX,
                    mouseY
            );
        }
    }

    boolean shouldKeepOpen() {
        return keepOpen;
    }

    void prepareForReturnFromTitles() {
        keepOpen = true;
    }

    private int calculatePanelWidth() {
        int scoreColumnCount =
                Math.max(1, objectiveNames.size());

        int preferredWidth =
                352 + scoreColumnCount * 90;

        return Math.min(
                preferredWidth,
                width - 20
        );
    }

    private int calculatePanelHeight() {
        int largestPlayerCount = players.size();

        for (HistoryPageData historyPage :
                historyPages) {
            largestPlayerCount = Math.max(
                    largestPlayerCount,
                    historyPage.players().size()
            );
        }

        int battleDesiredHeight =
                PADDING * 2 +
                        TITLE_HEIGHT +
                        HEADER_HEIGHT +
                        Math.max(1, largestPlayerCount) *
                                ROW_HEIGHT;

        int desiredHeight = Math.max(
                PERSONAL_PANEL_MIN_HEIGHT,
                battleDesiredHeight
        );

        return Math.min(
                Math.max(100, desiredHeight),
                height - 40
        );
    }

    private float updatePagePosition() {
        if (!pageTransitionActive) {
            return currentPage.index;
        }

        int pageDistance = Math.max(
                1,
                Math.abs(
                        transitionTarget.index
                                - currentPage.index
                )
        );

        float progress = Mth.clamp(
                (Util.getMillis()
                        - pageTransitionStartMillis)
                        / (float) (PAGE_TRANSITION_DURATION_MS
                        * pageDistance),
                0.0F,
                1.0F
        );

        if (progress >= 1.0F) {
            currentPage = transitionTarget;
            pageTransitionActive = false;

            return currentPage.index;
        }

        float easedProgress =
                progress * progress
                        * (3.0F - 2.0F * progress);

        return Mth.lerp(
                easedProgress,
                currentPage.index,
                transitionTarget.index
        );
    }

    private void startPageTransitionTo(Page target) {
        if (pageTransitionActive || target == currentPage) {
            return;
        }

        playButtonClickSound();

        transitionTarget = target;
        selectedPage = target; // 更新静态选中页面
        pageTransitionStartMillis = Util.getMillis();
        pageTransitionActive = true;

        // 如果目标页面是历史记录，且数据未加载，则请求数据
        if (transitionTarget == Page.HISTORY) {
            requestHistoryData();
        }
    }

    private void playButtonClickSound() {
        if (minecraft == null) {
            return;
        }

        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(
                        SoundEvents.UI_BUTTON_CLICK,
                        1.0F
                )
        );
    }

    private void renderPersonalPage(
            GuiGraphics graphics,
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight
    ) {
        graphics.fill(
                panelX,
                panelY,
                panelX + panelWidth,
                panelY + panelHeight,
                PERSONAL_PANEL_COLOR
        );

        graphics.fill(
                panelX,
                panelY,
                panelX + panelWidth,
                panelY + 1,
                BORDER_COLOR
        );
        graphics.fill(
                panelX,
                panelY + panelHeight - 1,
                panelX + panelWidth,
                panelY + panelHeight,
                BORDER_COLOR
        );
        graphics.fill(
                panelX,
                panelY,
                panelX + 1,
                panelY + panelHeight,
                BORDER_COLOR
        );
        graphics.fill(
                panelX + panelWidth - 1,
                panelY,
                panelX + panelWidth,
                panelY + panelHeight,
                BORDER_COLOR
        );

        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "screen.playerinfo.personal_title"
                ),
                panelX + panelWidth / 2,
                panelY + PADDING + 7,
                HEADER_TEXT_COLOR
        );

        int contentTop =
                panelY + PADDING + TITLE_HEIGHT;
        int contentBottom =
                panelY + panelHeight - PADDING;
        int contentHeight =
                Math.max(1, contentBottom - contentTop);

        int dividerX =
                panelX + panelWidth * 36 / 100;

        graphics.fill(
                dividerX,
                contentTop,
                dividerX + 1,
                contentBottom,
                BORDER_COLOR
        );

        int leftX = panelX + PADDING;
        int leftWidth = Math.max(
                1,
                dividerX - PADDING - leftX
        );

        int avatarSize = Math.max(
                8,
                Math.min(
                        panelWidth / 4,
                        Math.min(
                                leftWidth - 12,
                                contentHeight
                                        - font.lineHeight
                                        - 30
                        )
                )
        );

        int avatarX =
                leftX + (leftWidth - avatarSize) / 2;
        int avatarY = contentTop + 8;

        graphics.fill(
                avatarX - 2,
                avatarY - 2,
                avatarX + avatarSize + 2,
                avatarY + avatarSize + 2,
                BORDER_COLOR
        );
        graphics.fill(
                avatarX,
                avatarY,
                avatarX + avatarSize,
                avatarY + avatarSize,
                0xFF202020
        );

        String playerName = "";

        if (minecraft != null && minecraft.player != null) {
            PlayerFaceRenderer.draw(
                    graphics,
                    minecraft.player
                            .getSkinTextureLocation(),
                    avatarX,
                    avatarY,
                    avatarSize
            );

            RenderSystem.enableBlend();

            playerName = minecraft.player
                    .getScoreboardName();
        }

        String shortenedPlayerName =
                font.plainSubstrByWidth(
                        playerName,
                        Math.max(0, leftWidth - 8)
                );

        graphics.drawCenteredString(
                font,
                shortenedPlayerName,
                leftX + leftWidth / 2,
                avatarY + avatarSize + 8,
                TEXT_COLOR
        );

        personalTitleButtonWidth = Math.max(
                BUTTON_WIDTH,
                Math.min(76, leftWidth - 12)
        );
        personalTitleButtonHeight = BUTTON_HEIGHT;
        personalTitleButtonX = leftX
                + (leftWidth
                - personalTitleButtonWidth) / 2;
        personalTitleButtonY = Math.min(
                contentBottom
                        - personalTitleButtonHeight,
                avatarY + avatarSize
                        + font.lineHeight + 12
        );

        drawTitleButton(graphics, false);

        int rightX = dividerX + PADDING;
        int rightWidth = Math.max(
                1,
                panelX + panelWidth - PADDING - rightX
        );

        drawPersonalStatRow(
                graphics,
                "screen.playerinfo.total_games",
                personalStats.totalGames(),
                rightX,
                rightWidth,
                contentTop,
                contentHeight,
                0
        );
        drawPersonalStatRow(
                graphics,
                "screen.playerinfo.total_wins",
                personalStats.totalWins(),
                rightX,
                rightWidth,
                contentTop,
                contentHeight,
                1
        );
        drawPersonalStatRow(
                graphics,
                "screen.playerinfo.total_kills",
                personalStats.totalKills(),
                rightX,
                rightWidth,
                contentTop,
                contentHeight,
                2
        );
        drawPersonalStatRow(
                graphics,
                "screen.playerinfo.total_deaths",
                personalStats.totalDeaths(),
                rightX,
                rightWidth,
                contentTop,
                contentHeight,
                3
        );
        drawPersonalStatRow(
                graphics,
                "screen.playerinfo.total_damage",
                personalStats.totalDamage(),
                rightX,
                rightWidth,
                contentTop,
                contentHeight,
                4
        );
        drawPersonalStatRow(
                graphics,
                "screen.playerinfo.total_damage_absorbed",
                personalStats.totalDamageAbsorbed(),
                rightX,
                rightWidth,
                contentTop,
                contentHeight,
                5
        );
    }

    private void drawTitleButton(
            GuiGraphics graphics,
            boolean hovered
    ) {
        int borderColor = hovered
                ? HEADER_TEXT_COLOR
                : 0xFFA0A0A0;
        int backgroundColor = hovered
                ? 0xE0606060
                : 0xD0303030;

        graphics.fill(
                personalTitleButtonX,
                personalTitleButtonY,
                personalTitleButtonX
                        + personalTitleButtonWidth,
                personalTitleButtonY
                        + personalTitleButtonHeight,
                borderColor
        );
        graphics.fill(
                personalTitleButtonX + 1,
                personalTitleButtonY + 1,
                personalTitleButtonX
                        + personalTitleButtonWidth - 1,
                personalTitleButtonY
                        + personalTitleButtonHeight - 1,
                backgroundColor
        );

        graphics.renderItem(
                Items.NAME_TAG.getDefaultInstance(),
                personalTitleButtonX
                        + (personalTitleButtonWidth - 16) / 2,
                personalTitleButtonY
                        + (personalTitleButtonHeight - 16) / 2
        );
    }

    private void renderHistoryPage(
            GuiGraphics graphics,
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int mouseX,
            int mouseY
    ) {
        graphics.fill(
                panelX,
                panelY,
                panelX + panelWidth,
                panelY + panelHeight,
                PERSONAL_PANEL_COLOR
        );

        graphics.fill(
                panelX,
                panelY,
                panelX + panelWidth,
                panelY + 1,
                BORDER_COLOR
        );
        graphics.fill(
                panelX,
                panelY + panelHeight - 1,
                panelX + panelWidth,
                panelY + panelHeight,
                BORDER_COLOR
        );
        graphics.fill(
                panelX,
                panelY,
                panelX + 1,
                panelY + panelHeight,
                BORDER_COLOR
        );
        graphics.fill(
                panelX + panelWidth - 1,
                panelY,
                panelX + panelWidth,
                panelY + panelHeight,
                BORDER_COLOR
        );

        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "screen.playerinfo.history_title"
                ),
                panelX + panelWidth / 2,
                panelY + PADDING + 7,
                HEADER_TEXT_COLOR
        );

        if (historyLoading) {
            // 显示“加载中...”
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.playerinfo.loading"),
                    panelX + panelWidth / 2,
                    panelY + panelHeight / 2,
                    TEXT_COLOR
            );
            return;
        }

        if (historyPages.isEmpty()) {
            // 显示“无记录”
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.playerinfo.no_history"),
                    panelX + panelWidth / 2,
                    panelY + panelHeight / 2,
                    TEXT_COLOR
            );
            return;
        }

        int tableX = panelX + PADDING;
        int tableWidth = Math.max(
                1,
                panelWidth - PADDING * 2
                        - BUTTON_WIDTH - BUTTON_GAP
        );
        int headerY = panelY + PADDING + TITLE_HEIGHT;
        int rowsY = headerY + HEADER_HEIGHT;
        int rowsBottom = panelY + panelHeight - PADDING;

        historyVisibleRows = Math.max(
                1,
                (rowsBottom - rowsY) / ROW_HEIGHT
        );
        historyMaxScroll = Math.max(
                0,
                sortedHistoryPlayers.size()
                        - historyVisibleRows
        );
        historyScrollOffset = Mth.clamp(
                historyScrollOffset,
                0,
                historyMaxScroll
        );

        graphics.fill(
                tableX,
                headerY,
                tableX + tableWidth,
                headerY + HEADER_HEIGHT,
                HEADER_COLOR
        );

        int scoreColumnCount =
                HISTORY_OBJECTIVE_NAMES.size();
        int nameWidth = Math.max(
                1,
                tableWidth * 22 / 100
        );
        int jobWidth = Math.max(
                1,
                tableWidth * 14 / 100
        );
        int teamWidth = Math.max(
                1,
                tableWidth * 14 / 100
        );
        int scoreWidth = Math.max(
                1,
                (tableWidth - nameWidth
                        - jobWidth - teamWidth)
                        / scoreColumnCount
        );

        int currentX = tableX;
        drawHeader(
                graphics,
                Component.translatable(
                        "screen.playerinfo.player_name"
                ),
                currentX,
                headerY,
                nameWidth
        );
        currentX += nameWidth;

        drawHeader(
                graphics,
                Component.translatable(
                        "screen.playerinfo.job"
                ),
                currentX,
                headerY,
                jobWidth
        );
        currentX += jobWidth;

        // 表头按队伍排序按钮
        drawSortableHeader(
                graphics,
                Component.translatable("screen.playerinfo.team"),
                currentX,
                headerY,
                teamWidth,
                SortKey.TEAM,
                true,
                mouseX,
                mouseY);
        currentX += teamWidth;

        // 表头按分数排序按钮
        for (String objectiveName : HISTORY_OBJECTIVE_NAMES) {
            SortKey key = switch (objectiveName) {
//                case "kill_count" -> SortKey.KILLS;   // 默认为 KILLS
                case "death_count" -> SortKey.DEATHS;
                case "damage_dealt" -> SortKey.DAMAGE;
                case "damage_absorbed" -> SortKey.ABSORBED;
                default -> SortKey.KILLS;
            };
            drawSortableHeader(
                    graphics,
                    getObjectiveDisplayName(objectiveName),
                    currentX,
                    headerY,
                    scoreWidth,
                    key,
                    true,
                    mouseX,
                    mouseY);
            currentX += scoreWidth;
        }

        graphics.enableScissor(
                tableX,
                rowsY,
                tableX + tableWidth,
                rowsBottom
        );

        int endIndex = Math.min(
                sortedHistoryPlayers.size(),
                historyScrollOffset
                        + historyVisibleRows
        );

        for (int index = historyScrollOffset;
             index < endIndex;
             index++) {
            PlayerInfoEntry entry =
                    sortedHistoryPlayers.get(index);
            int visibleIndex =
                    index - historyScrollOffset;
            int rowY = rowsY
                    + visibleIndex * ROW_HEIGHT;

            // 渲染行背景
            boolean isLocal = isLocalPlayer(entry.playerName());
            int rowColor = isLocal ? LOCAL_PLAYER_ROW_COLOR : (visibleIndex % 2 == 0 ? ROW_COLOR_1 : ROW_COLOR_2);
            graphics.fill(tableX, rowY, tableX + tableWidth, rowY + ROW_HEIGHT, rowColor);

            currentX = tableX;
            drawLeftCell(
                    graphics,
                    entry.playerName(),
                    currentX,
                    rowY,
                    nameWidth
            );
            currentX += nameWidth;

            Job job = Job.fromId(entry.jobId());
            drawCenterCell(
                    graphics,
                    job.getDisplayName(),
                    currentX,
                    rowY,
                    jobWidth
            );
            currentX += jobWidth;

            drawHistoryTeamCell(
                    graphics,
                    entry.teamName(),
                    currentX,
                    rowY,
                    teamWidth
            );
            currentX += teamWidth;

            for (int scoreIndex = 0;
                 scoreIndex < scoreColumnCount;
                 scoreIndex++) {
                int score = scoreIndex
                        < entry.scores().size()
                        ? entry.scores().get(scoreIndex)
                        : 0;
                drawCenterCell(
                        graphics,
                        Integer.toString(score),
                        currentX,
                        rowY,
                        scoreWidth
                );
                currentX += scoreWidth;
            }
        }

        graphics.disableScissor();

        if (historyMaxScroll > 0) {
            int scrollBarX =
                    tableX + tableWidth + 1;
            int scrollBarHeight = rowsBottom - rowsY;

            graphics.fill(
                    scrollBarX,
                    rowsY,
                    scrollBarX + 2,
                    rowsBottom,
                    0x50505050
            );

            int thumbHeight = Math.max(
                    12,
                    scrollBarHeight
                            * historyVisibleRows
                            / sortedHistoryPlayers.size()
            );
            int thumbTravel =
                    scrollBarHeight - thumbHeight;
            int thumbY = rowsY
                    + thumbTravel
                    * historyScrollOffset
                    / historyMaxScroll;

            graphics.fill(
                    scrollBarX,
                    thumbY,
                    scrollBarX + 2,
                    thumbY + thumbHeight,
                    0xFFFFFFFF
            );
        }
    }

    private void drawPersonalStatRow(
            GuiGraphics graphics,
            String translationKey,
            int value,
            int x,
            int width,
            int contentTop,
            int contentHeight,
            int rowIndex
    ) {
        int rowTop = contentTop
                + contentHeight * rowIndex / 6;
        int rowBottom = contentTop
                + contentHeight * (rowIndex + 1) / 6;

        if (rowIndex % 2 == 0) {
            graphics.fill(
                    x,
                    rowTop,
                    x + width,
                    rowBottom,
                    ROW_COLOR_1
            );
        }

        if (rowIndex > 0) {
            graphics.fill(
                    x,
                    rowTop,
                    x + width,
                    rowTop + 1,
                    0x40606060
            );
        }

        int textY = rowTop
                + (rowBottom - rowTop
                - font.lineHeight) / 2;

        String label = font.plainSubstrByWidth(
                Component.translatable(
                        translationKey
                ).getString(),
                Math.max(0, width * 2 / 3 - 16)
        );

        graphics.drawString(
                font,
                label,
                x + 8,
                textY,
                0xFFD0D0D0,
                false
        );

        String valueText = Integer.toString(value);

        graphics.drawString(
                font,
                valueText,
                x + width - 8 - font.width(valueText),
                textY,
                HEADER_TEXT_COLOR,
                false
        );
    }

    private void refreshSortedPlayers() {
        sortedPlayers.clear();
        sortedPlayers.addAll(players);
        sortedPlayers.sort(getComparator(combatSortKey, combatSortDescending));
        scrollOffset = 0;
    }

    private void refreshSortedHistoryPlayers() {
        sortedHistoryPlayers.clear();
        if (selectedHistoryPageIndex >= 0 && selectedHistoryPageIndex < historyPages.size()) {
            sortedHistoryPlayers.addAll(historyPages.get(selectedHistoryPageIndex).players());
        }
        sortedHistoryPlayers.sort(getComparator(historySortKey, historySortDescending));
        historyScrollOffset = 0;
    }

    private Comparator<PlayerInfoEntry> getComparator(SortKey key, boolean descending) {
        Comparator<PlayerInfoEntry> comparator;
        switch (key) {
            case TEAM -> comparator = Comparator.comparing(
                    PlayerInfoEntry::teamName,
                    String.CASE_INSENSITIVE_ORDER
            );
            case KILLS -> comparator = Comparator.comparingInt(entry -> getScore(entry, 0));
            case DEATHS -> comparator = Comparator.comparingInt(entry -> getScore(entry, 1));
            case DAMAGE -> comparator = Comparator.comparingInt(entry -> getScore(entry, 2));
            case ABSORBED -> comparator = Comparator.comparingInt(entry -> getScore(entry, 3));
            default -> comparator = Comparator.comparing(PlayerInfoEntry::playerName);
        }
        if (descending) comparator = comparator.reversed();
        // 次级排序：名字
        comparator = comparator.thenComparing(PlayerInfoEntry::playerName, String.CASE_INSENSITIVE_ORDER);
        return comparator;
    }

    private int getScore(PlayerInfoEntry entry, int index) {
        return index < entry.scores().size() ? entry.scores().get(index) : 0;
    }

    private void drawSortableHeader(GuiGraphics graphics, Component text, int x, int y, int cellWidth,
                                    SortKey key, boolean isHistory, int mouseX, int mouseY) {
        SortKey activeKey = isHistory ? historySortKey : combatSortKey;
        boolean descending = isHistory ? historySortDescending : combatSortDescending;
        boolean active = key == activeKey;

        // 悬停检测
        boolean hovered = mouseX >= x && mouseX < x + cellWidth
                && mouseY >= y && mouseY < y + HEADER_HEIGHT;

        // 背景：悬停时稍微变亮，激活时使用更明显的背景
        int bgColor;
        if (hovered) {
            bgColor = 0xFF505050;
        } else if (active) {
            bgColor = 0xFF404040;
        } else {
            bgColor = 0xFF303030; // 与表头背景 HEADER_COLOR 类似
        }
        graphics.fill(x, y, x + cellWidth, y + HEADER_HEIGHT, bgColor);
        // 边框：激活列金色，非激活列灰色，悬停时更亮
        int borderColor = active ? HEADER_TEXT_COLOR : (hovered ? 0xFFC0C0C0 : 0xFF808080);
        // 绘制四周边框（1像素）
        graphics.fill(x, y, x + cellWidth, y + 1, borderColor);
        graphics.fill(x, y + HEADER_HEIGHT - 1, x + cellWidth, y + HEADER_HEIGHT, borderColor);
        graphics.fill(x, y, x + 1, y + HEADER_HEIGHT, borderColor);
        graphics.fill(x + cellWidth - 1, y, x + cellWidth, y + HEADER_HEIGHT, borderColor);

        // 文本：激活列显示箭头
        String display = text.getString();
        if (key == activeKey) {
            display += descending ? " ↓" : " ↑";
        }

        int textColor = active ? HEADER_TEXT_COLOR : TEXT_COLOR;
        String shortened = font.plainSubstrByWidth(display, Math.max(0, cellWidth - 6));

        graphics.drawCenteredString(
                font,
                shortened,
                x + cellWidth / 2,
                y + (HEADER_HEIGHT - font.lineHeight) / 2,
                textColor
        );
    }

    private void drawPageControls(GuiGraphics graphics, int mouseX, int mouseY) {
        // 战斗页按钮
        boolean combatHovered = isInsideButton(mouseX, mouseY, combatPageButtonX, pageButtonY);
        drawControlButton(
                graphics,
                combatPageButtonX,
                pageButtonY,
                Page.COMBAT.icon,
                combatHovered,
                currentPage == Page.COMBAT
        );

        // 个人页按钮
        boolean personalHovered = isInsideButton(mouseX, mouseY, personalPageButtonX, pageButtonY);
        drawControlButton(
                graphics,
                personalPageButtonX,
                pageButtonY,
                Page.PERSONAL.icon,
                personalHovered,
                currentPage == Page.PERSONAL
        );

        // 历史页按钮
        boolean historyHovered = isInsideButton(mouseX, mouseY, historyPageButtonX, pageButtonY);
        drawControlButton(
                graphics,
                historyPageButtonX,
                pageButtonY,
                Page.HISTORY.icon,
                historyHovered,
                currentPage == Page.HISTORY
        );

        // 悬停提示
        if (combatHovered) {
            graphics.renderTooltip(font, Component.translatable(Page.COMBAT.titleKey), mouseX, mouseY);
        } else if (personalHovered) {
            graphics.renderTooltip(font, Component.translatable(Page.PERSONAL.titleKey), mouseX, mouseY);
        } else if (historyHovered) {
            graphics.renderTooltip(font, Component.translatable(Page.HISTORY.titleKey), mouseX, mouseY);
        }

        // 历史分页按钮（仅在历史页面且无动画时显示）
        if (!pageTransitionActive && currentPage == Page.HISTORY) {
            drawHistoryPageButtons(graphics, mouseX, mouseY);
        }
    }

    private void drawHistoryPageButtons(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        for (int index = 0;
             index < HISTORY_PAGE_COUNT;
             index++) {
            int buttonY = historyPageButtonsY
                    + index
                    * (BUTTON_HEIGHT + BUTTON_GAP);
            boolean hovered = isInsideButton(
                    mouseX,
                    mouseY,
                    historyPageButtonsX,
                    buttonY
            );

            drawControlButton(
                    graphics,
                    historyPageButtonsX,
                    buttonY,
                    Integer.toString(index + 1),
                    hovered,
                    index == selectedHistoryPageIndex
            );

            if (hovered) {
                graphics.renderTooltip(
                        font,
                        Component.translatable(
                                "screen.playerinfo.history_page_tooltip",
                                index + 1
                        ),
                        mouseX,
                        mouseY
                );
            }
        }
    }

    private void drawControlButton(
            GuiGraphics graphics,
            int x,
            int y,
            String symbol,
            boolean hovered,
            boolean selected
    ) {
        int borderColor = hovered || selected
                ? 0xFFFFD700
                : 0xFFA0A0A0;

        int backgroundColor = hovered || selected
                ? 0xE0606060
                : 0xD0303030;

        graphics.fill(
                x,
                y,
                x + BUTTON_WIDTH,
                y + BUTTON_HEIGHT,
                borderColor
        );

        graphics.fill(
                x + 1,
                y + 1,
                x + BUTTON_WIDTH - 1,
                y + BUTTON_HEIGHT - 1,
                backgroundColor
        );

        graphics.drawCenteredString(
                font,
                symbol,
                x + BUTTON_WIDTH / 2,
                y + (BUTTON_HEIGHT - font.lineHeight) / 2,
                TEXT_COLOR
        );
    }

    private boolean isInsideRectangle(
            double mouseX,
            double mouseY,
            int rectangleX,
            int rectangleY,
            int rectangleWidth,
            int rectangleHeight
    ) {
        return mouseX >= rectangleX
                && mouseX < rectangleX + rectangleWidth
                && mouseY >= rectangleY
                && mouseY < rectangleY + rectangleHeight;
    }

    private boolean isInsideButton(
            double mouseX,
            double mouseY,
            int buttonX,
            int buttonY
    ) {
        return mouseX >= buttonX &&
                mouseX < buttonX + BUTTON_WIDTH &&
                mouseY >= buttonY &&
                mouseY < buttonY + BUTTON_HEIGHT;
    }

    private void drawHeader(
            GuiGraphics graphics,
            Component text,
            int x,
            int y,
            int cellWidth
    ) {
        String shortened = font.plainSubstrByWidth(
                text.getString(),
                cellWidth - 6
        );

        graphics.drawCenteredString(
                font,
                shortened,
                x + cellWidth / 2,
                y + (HEADER_HEIGHT - font.lineHeight) / 2,
                HEADER_TEXT_COLOR
        );
    }

    private void drawLeftCell(GuiGraphics graphics, String text, int x, int y, int cellWidth) {
        drawLeftCell(graphics, text, x, y, cellWidth, TEXT_COLOR);
    }

    private void drawLeftCell(GuiGraphics graphics, String text, int x, int y, int cellWidth, int color) {
        String shortened = font.plainSubstrByWidth(text, cellWidth - 8);

        graphics.drawString(
                font,
                shortened,
                x + 4,
                y + (ROW_HEIGHT - font.lineHeight) / 2,
                color,
                false
        );
    }

    private void drawRightCell(GuiGraphics graphics, String text, int x, int y, int cellWidth, int color) {
        String shortened = font.plainSubstrByWidth(text, cellWidth - 8);

        graphics.drawString(
                font,
                shortened,
                x + cellWidth - 4 - font.width(shortened),
                y + (ROW_HEIGHT - font.lineHeight) / 2,
                color,
                false
        );
    }

    private void drawCenterCell(GuiGraphics graphics, String text, int x, int y, int cellWidth) {
        drawCenterCell(graphics, text, x, y, cellWidth, TEXT_COLOR);
    }

    private void drawCenterCell(GuiGraphics graphics, String text, int x, int y, int cellWidth, int color) {
        String shortened = font.plainSubstrByWidth(text, Math.max(0, cellWidth - 6));

        graphics.drawCenteredString(
                font,
                shortened,
                x + cellWidth / 2,
                y + (ROW_HEIGHT - font.lineHeight) / 2,
                color
        );
    }

    private void drawCenterCell(
            GuiGraphics graphics,
            Component text,
            int x,
            int y,
            int cellWidth
    ) {
        String shortened = font.plainSubstrByWidth(
                text.getString(),
                Math.max(0, cellWidth - 6)
        );

        graphics.drawCenteredString(
                font,
                Component.literal(shortened).setStyle(text.getStyle()),
                x + cellWidth / 2,
                y + (ROW_HEIGHT - font.lineHeight) / 2,
                TEXT_COLOR
        );
    }

    private void drawHistoryTeamCell(
            GuiGraphics graphics,
            String teamScore,
            int x,
            int y,
            int cellWidth
    ) {
        Component displayName = switch (teamScore) {
            case "1" -> Component.translatable(
                    "screen.playerinfo.team.red"
            ).withStyle(
                    ChatFormatting.RED,
                    ChatFormatting.BOLD
            );
            case "2" -> Component.translatable(
                    "screen.playerinfo.team.blue"
            ).withStyle(
                    ChatFormatting.BLUE,
                    ChatFormatting.BOLD
            );
            case "" -> Component.translatable(
                    "screen.playerinfo.no_team"
            );
            default -> Component.literal(teamScore);
        };

        graphics.drawCenteredString(
                font,
                displayName,
                x + cellWidth / 2,
                y + (ROW_HEIGHT - font.lineHeight) / 2,
                TEXT_COLOR
        );
    }

    private Component getObjectiveDisplayName(
            String objectiveName
    ) {
        return switch (objectiveName) {
            case "kill_count" ->
                    Component.translatable(
                            "screen.playerinfo.kill_count"
                    );

            case "death_count" ->
                    Component.translatable(
                            "screen.playerinfo.death_count"
                    );

            case "damage_dealt" ->
                    Component.translatable(
                            "screen.playerinfo.damage_dealt"
                    );

            case "damage_absorbed" ->
                    Component.translatable(
                            "screen.playerinfo.damage_absorbed"
                    );

            default -> Component.literal(objectiveName);
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton == 0) {
            // 战斗页按钮
            if (isInsideButton(mouseX, mouseY, combatPageButtonX, pageButtonY)) {
                if (currentPage != Page.COMBAT) {
                    startPageTransitionTo(Page.COMBAT);
                }
                return true;
            }
            // 个人页按钮
            if (isInsideButton(mouseX, mouseY, personalPageButtonX, pageButtonY)) {
                if (currentPage != Page.PERSONAL) {
                    startPageTransitionTo(Page.PERSONAL);
                }
                return true;
            }
            // 历史页按钮
            if (isInsideButton(mouseX, mouseY, historyPageButtonX, pageButtonY)) {
                if (currentPage != Page.HISTORY) {
                    startPageTransitionTo(Page.HISTORY);
                }
                return true;
            }

            if (pageTransitionActive) {
                return true;
            }

            // 个人界面
            if (currentPage == Page.PERSONAL) {
                if (isInsideRectangle(
                        mouseX,
                        mouseY,
                        personalTitleButtonX,
                        personalTitleButtonY,
                        personalTitleButtonWidth,
                        personalTitleButtonHeight
                )) {
                    playButtonClickSound();
                    keepOpen = true;

                    if (minecraft != null) {
                        minecraft.setScreen(
                                new TitleSelectionScreen(
                                        this,
                                        personalStats
                                )
                        );
                    }
                    return true;
                }

                return super.mouseClicked(
                        mouseX,
                        mouseY,
                        mouseButton
                );
            }

            // 历史记录界面
            if (currentPage == Page.HISTORY) {
                for (int index = 0;
                     index < HISTORY_PAGE_COUNT;
                     index++) {
                    int buttonY = historyPageButtonsY
                            + index
                            * (BUTTON_HEIGHT + BUTTON_GAP);

                    if (isInsideButton(
                            mouseX,
                            mouseY,
                            historyPageButtonsX,
                            buttonY
                    )) {
                        playButtonClickSound();
                        selectedHistoryPageIndex = index;
                        refreshSortedHistoryPlayers();
                        return true;
                    }
                }
            }

            if (currentPage == Page.HISTORY && historyPages.isEmpty()) {
                return super.mouseClicked(mouseX, mouseY, mouseButton);
            }
            // 排序按钮
            if (currentPage == Page.COMBAT || currentPage == Page.HISTORY) {
                boolean isHistory = currentPage == Page.HISTORY;
                // 获取表头区域参数（需与渲染时一致）
                int panelWidth = calculatePanelWidth();
                int panelHeight = calculatePanelHeight();
                int panelX = (width - panelWidth) / 2;
                int panelY = (height - panelHeight) / 2;
                int tableX = panelX + PADDING;
                int tableWidth = panelWidth - PADDING * 2 - (isHistory ? BUTTON_WIDTH + BUTTON_GAP : 0);
                int headerY = panelY + PADDING + TITLE_HEIGHT;

                if (mouseY >= headerY && mouseY < headerY + HEADER_HEIGHT) {
                    int nameWidth = Math.max(1, tableWidth * 22 / 100);
                    int jobWidth = Math.max(1, tableWidth * 14 / 100);
                    int teamWidth = Math.max(1, tableWidth * 14 / 100);
                    int scoreColumnCount = isHistory ? HISTORY_OBJECTIVE_NAMES.size() : Math.max(1, objectiveNames.size());
                    int scoreWidth = Math.max(1, (tableWidth - nameWidth - jobWidth - teamWidth) / scoreColumnCount);

                    int currentX = tableX + nameWidth + jobWidth; // 跳过名字和职业
                    // 检查队伍列
                    if (mouseX >= currentX && mouseX < currentX + teamWidth) {
                        handleSortClick(SortKey.TEAM, isHistory);
                        return true;
                    }
                    currentX += teamWidth;
                    // 检查分数列
                    List<String> objectives = isHistory ? HISTORY_OBJECTIVE_NAMES : objectiveNames;
                    for (String objective : objectives) {
                        if (mouseX >= currentX && mouseX < currentX + scoreWidth) {
                            SortKey key = switch (objective) {
//                                case "kill_count" -> SortKey.KILLS;   // 默认为 KILLS
                                case "death_count" -> SortKey.DEATHS;
                                case "damage_dealt" -> SortKey.DAMAGE;
                                case "damage_absorbed" -> SortKey.ABSORBED;
                                default -> SortKey.KILLS;
                            };
                            handleSortClick(key, isHistory);
                            return true;
                        }
                        currentX += scoreWidth;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void handleSortClick(SortKey key, boolean isHistory) {
        SortKey activeKey = isHistory ? historySortKey : combatSortKey;
        if (key == activeKey) {
            // 切换方向
            if (isHistory) {
                historySortDescending = !historySortDescending;
            } else {
                combatSortDescending = !combatSortDescending;
            }
        } else {
            // 切换排序列，默认降序
            if (isHistory) {
                historySortKey = key;
                historySortDescending = true;
            } else {
                combatSortKey = key;
                combatSortDescending = true;
            }
        }
        // 刷新排序
        if (isHistory) refreshSortedHistoryPlayers();
        else refreshSortedPlayers();
        playButtonClickSound();
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        if (pageTransitionActive
                || currentPage == Page.PERSONAL) {
            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    delta
            );
        }

        if (currentPage == Page.HISTORY) {
            if (delta > 0) {
                historyScrollOffset--;
            } else if (delta < 0) {
                historyScrollOffset++;
            }

            historyScrollOffset = Mth.clamp(
                    historyScrollOffset,
                    0,
                    historyMaxScroll
            );
        } else {
            if (delta > 0) {
                scrollOffset--;
            } else if (delta < 0) {
                scrollOffset++;
            }

            scrollOffset = Mth.clamp(
                    scrollOffset,
                    0,
                    maxScroll
            );
        }

        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void requestHistoryData() {
        if (historyLoading) {
            return;
        }
        historyLoading = true;

        // 发送请求最近5条记录
        ModNetwork.CHANNEL.sendToServer(RequestHistoryPacket.requestRecent(5));
    }

    /**
     * 当服务端响应到达时调用，更新历史数据
     */
    public void updateHistoryData(List<HistoryData> historyDataList) {
        historyLoading = false;

        // 转换为 List<HistoryPageData>
        List<HistoryPageData> newPages = new ArrayList<>();
        for (HistoryData historyData : historyDataList) {
            List<PlayerInfoEntry> entries = new ArrayList<>();
            for (PlayerData pd : historyData.getPlayerDataList()) {
                entries.add(new PlayerInfoEntry(
                        pd.getName(),
                        pd.getJobId(),
                        pd.getTeamName(),
                        pd.getTeamColor(),
                        pd.getScores()
                ));
            }
            newPages.add(new HistoryPageData(entries));
        }

        historyPages = newPages;
        // 确保选中页有效
        if (selectedHistoryPageIndex >= historyPages.size()) {
            selectedHistoryPageIndex = Math.max(0, historyPages.size() - 1);
        }
        refreshSortedHistoryPlayers();
    }

    // 监听器回调
    private void onHistoryResponse(List<HistoryData> historyDataList) {
        // 仅当屏幕还开着时更新
        if (minecraft != null && minecraft.screen == this) {
            updateHistoryData(historyDataList);
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        // 移除监听器
        ClientHistoryManager.removeResponseListener(this::onHistoryResponse);
    }

    private boolean isLocalPlayer(String playerName) {
        return playerName.equals(localPlayerName);
    }

    private void cachePlayerSkins() {
        if (minecraft == null || minecraft.getConnection() == null) return;
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().getName();
            skinCache.put(name, info.getSkinLocation());
        }
    }

    private void drawPlayerNameCell(GuiGraphics graphics, String playerName, int x, int y, int cellWidth) {
        ResourceLocation skin = skinCache.get(playerName);
        int avatarX = x + 2;
        int avatarY = y + (ROW_HEIGHT - AVATAR_SIZE) / 2;

        if (skin != null) {
            PlayerFaceRenderer.draw(graphics, skin, avatarX, avatarY, AVATAR_SIZE);
            RenderSystem.enableBlend(); // 恢复混合状态
        } else {
            // 可选：绘制灰色占位方块
            graphics.fill(avatarX, avatarY, avatarX + AVATAR_SIZE, avatarY + AVATAR_SIZE, 0xFF808080);
        }

        int textX = avatarX + AVATAR_SIZE + AVATAR_TEXT_GAP;
        int textWidth = Math.max(0, cellWidth - (AVATAR_SIZE + AVATAR_TEXT_GAP + 8));
        String shortened = font.plainSubstrByWidth(playerName, textWidth);
        int textColor = isLocalPlayer(playerName) ? LOCAL_PLAYER_NAME_COLOR : TEXT_COLOR;
        graphics.drawString(font, shortened, textX, y + (ROW_HEIGHT - font.lineHeight) / 2, textColor, false);
    }

}
