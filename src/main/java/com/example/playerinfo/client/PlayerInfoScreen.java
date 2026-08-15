package com.example.playerinfo.client;

// PLAYER_INFO_SCREEN_PHYSICAL_KEY_V5
// Forge 1.20.1：读取当前绑定键的物理按下状态，防止界面频闪。

import com.example.playerinfo.data.HistoryData;
import com.example.playerinfo.data.PlayerData;
import com.example.playerinfo.network.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    /*
     * 使用静态字段保存设置，使界面关闭后再次打开时
     * 仍然保持上一次选择的排序方式。
     */
    private static SortMode selectedSortMode = SortMode.KILLS;
    private static boolean descendingOrder = true;
    private static boolean prioritizeSameTeam = false;
    private static Page selectedPage = Page.COMBAT;

    private static SortMode selectedHistorySortMode =
            SortMode.KILLS;
    private static boolean historyDescendingOrder = true;
    private static boolean historyPrioritizeSameTeam = false;

    private final PersonalStats personalStats;
    private final List<String> objectiveNames;
    private final List<PlayerInfoEntry> players;
    private List<HistoryPageData> historyPages;
    private final List<PlayerInfoEntry> sortedPlayers =
            new ArrayList<>();
    private final List<PlayerInfoEntry> sortedHistoryPlayers =
            new ArrayList<>();

    private int sortModeButtonX;
    private int sortDirectionButtonX;
    private int teamPriorityButtonX;
    private int pageSwitchButtonX;
    private int buttonsY;
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

        int panelWidth = calculatePanelWidth();
        int panelHeight = calculatePanelHeight();
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        pageSwitchButtonX = panelX + PADDING;

        historyPageButtonsX =
                panelX + panelWidth
                        - PADDING - BUTTON_WIDTH;
        historyPageButtonsY =
                panelY + PADDING + TITLE_HEIGHT;

        int totalButtonWidth =
                BUTTON_WIDTH * 3 + BUTTON_GAP * 2;

        sortModeButtonX =
                panelX + panelWidth - PADDING - totalButtonWidth;

        sortDirectionButtonX =
                sortModeButtonX + BUTTON_WIDTH + BUTTON_GAP;

        teamPriorityButtonX =
                sortDirectionButtonX + BUTTON_WIDTH + BUTTON_GAP;

        // 位于面板标题区域内，避免与边框或表头重叠。
        buttonsY = panelY + PADDING + 3;
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

        int availableWidth = tableWidth;

        int nameWidth = Math.max(
                1,
                availableWidth * 22 / 100
        );

        int jobWidth = Math.max(
                1,
                availableWidth * 14 / 100
        );

        int teamWidth = Math.max(
                1,
                availableWidth * 14 / 100
        );

        int remainingWidth =
                availableWidth
                        - nameWidth
                        - jobWidth
                        - teamWidth;

        int scoreWidth =
                remainingWidth / scoreColumnCount;

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

        drawHeader(
                graphics,
                Component.translatable(
                        "screen.playerinfo.team"
                ),
                currentX,
                headerY,
                teamWidth
        );

        currentX += teamWidth;

        for (String objectiveName : objectiveNames) {
            drawHeader(
                    graphics,
                    getObjectiveDisplayName(objectiveName),
                    currentX,
                    headerY,
                    scoreWidth
            );

            currentX += scoreWidth;
        }

        // 表格内容裁剪区域
        graphics.enableScissor(
                tableX,
                rowsY + combatOffsetY,
                tableX + tableWidth,
                rowsBottom + combatOffsetY
        );

        int endIndex = Math.min(
                sortedPlayers.size(),
                scrollOffset + visibleRows
        );

        for (int index = scrollOffset;
             index < endIndex;
             index++) {

            PlayerInfoEntry entry =
                    sortedPlayers.get(index);

            int visibleIndex = index - scrollOffset;

            int rowY =
                    rowsY + visibleIndex * ROW_HEIGHT;

            int rowColor = visibleIndex % 2 == 0
                    ? ROW_COLOR_1
                    : ROW_COLOR_2;

            graphics.fill(
                    tableX,
                    rowY,
                    tableX + tableWidth,
                    rowY + ROW_HEIGHT,
                    rowColor
            );

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
                    jobWidth,
                    job.getTextColor()
            );

            currentX += jobWidth;

            boolean hasTeam =
                    !entry.teamName().isBlank();

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
                panelHeight
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

    private void startPageTransition() {
        if (pageTransitionActive) {
            return;
        }

        playButtonClickSound();

        transitionTarget = currentPage.next();
        selectedPage = transitionTarget;
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

        drawHeader(
                graphics,
                Component.translatable(
                        "screen.playerinfo.team"
                ),
                currentX,
                headerY,
                teamWidth
        );
        currentX += teamWidth;

        for (String objectiveName :
                HISTORY_OBJECTIVE_NAMES) {
            drawHeader(
                    graphics,
                    getObjectiveDisplayName(objectiveName),
                    currentX,
                    headerY,
                    scoreWidth
            );
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

            graphics.fill(
                    tableX,
                    rowY,
                    tableX + tableWidth,
                    rowY + ROW_HEIGHT,
                    visibleIndex % 2 == 0
                            ? ROW_COLOR_1
                            : ROW_COLOR_2
            );

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
                    jobWidth,
                    job.getTextColor()
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

        Comparator<PlayerInfoEntry> scoreComparator =
                Comparator.comparingInt(
                        this::getSelectedScore
                );

        if (descendingOrder) {
            scoreComparator =
                    scoreComparator.reversed();
        }

        Comparator<PlayerInfoEntry> comparator =
                scoreComparator.thenComparing(
                        PlayerInfoEntry::playerName,
                        String.CASE_INSENSITIVE_ORDER
                );

        String localTeamName =
                findLocalTeamName();

        if (prioritizeSameTeam &&
                !localTeamName.isBlank()) {

            Comparator<PlayerInfoEntry> teamComparator =
                    Comparator.comparingInt(entry ->
                            localTeamName.equals(
                                    entry.teamName()
                            ) ? 0 : 1
                    );

            comparator =
                    teamComparator.thenComparing(
                            comparator
                    );
        }

        sortedPlayers.sort(comparator);
        scrollOffset = 0;
    }

    private int getSelectedScore(
            PlayerInfoEntry entry
    ) {
        int scoreIndex = objectiveNames.indexOf(
                selectedSortMode.objectiveName
        );

        if (scoreIndex < 0 ||
                scoreIndex >= entry.scores().size()) {
            return 0;
        }

        return entry.scores().get(scoreIndex);
    }

    private void refreshSortedHistoryPlayers() {
        sortedHistoryPlayers.clear();

        if (selectedHistoryPageIndex >= 0
                && selectedHistoryPageIndex
                < historyPages.size()) {
            sortedHistoryPlayers.addAll(
                    historyPages.get(
                            selectedHistoryPageIndex
                    ).players()
            );
        }

        Comparator<PlayerInfoEntry> scoreComparator =
                Comparator.comparingInt(
                        this::getHistorySelectedScore
                );

        if (historyDescendingOrder) {
            scoreComparator = scoreComparator.reversed();
        }

        Comparator<PlayerInfoEntry> comparator =
                scoreComparator.thenComparing(
                        PlayerInfoEntry::playerName,
                        String.CASE_INSENSITIVE_ORDER
                );

        String localTeamName =
                findLocalHistoryTeamName();

        if (historyPrioritizeSameTeam
                && !localTeamName.isBlank()) {
            Comparator<PlayerInfoEntry> teamComparator =
                    Comparator.comparingInt(entry ->
                            localTeamName.equals(
                                    entry.teamName()
                            ) ? 0 : 1
                    );
            comparator = teamComparator.thenComparing(
                    comparator
            );
        }

        sortedHistoryPlayers.sort(comparator);
        historyScrollOffset = 0;
    }

    private String findLocalHistoryTeamName() {
        if (minecraft == null
                || minecraft.player == null
                || selectedHistoryPageIndex < 0
                || selectedHistoryPageIndex
                >= historyPages.size()) {
            return "";
        }

        String localPlayerName =
                minecraft.player.getScoreboardName();

        for (PlayerInfoEntry entry :
                historyPages.get(
                        selectedHistoryPageIndex
                ).players()) {
            if (entry.playerName().equals(
                    localPlayerName
            )) {
                return entry.teamName();
            }
        }

        return "";
    }

    private int getHistorySelectedScore(
            PlayerInfoEntry entry
    ) {
        int scoreIndex =
                HISTORY_OBJECTIVE_NAMES.indexOf(
                        selectedHistorySortMode
                                .objectiveName
                );

        if (scoreIndex < 0
                || scoreIndex >= entry.scores().size()) {
            return 0;
        }

        return entry.scores().get(scoreIndex);
    }

    private String findLocalTeamName() {
        if (minecraft == null ||
                minecraft.player == null) {
            return "";
        }

        String localPlayerName =
                minecraft.player.getScoreboardName();

        for (PlayerInfoEntry entry : players) {
            if (entry.playerName().equals(
                    localPlayerName
            )) {
                return entry.teamName();
            }
        }

        return "";
    }

    private void drawPageControls(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        boolean pageButtonHovered = isInsideButton(
                mouseX,
                mouseY,
                pageSwitchButtonX,
                buttonsY
        );

        drawControlButton(
                graphics,
                pageSwitchButtonX,
                buttonsY,
                currentPage.icon,
                pageButtonHovered
        );

        if (!pageTransitionActive
                && (currentPage == Page.COMBAT
                || currentPage == Page.HISTORY)) {
            drawControlButtons(
                    graphics,
                    mouseX,
                    mouseY
            );

            if (currentPage == Page.HISTORY) {
                drawHistoryPageButtons(
                        graphics,
                        mouseX,
                        mouseY
                );
            }
        }

        if (pageButtonHovered) {
            graphics.renderTooltip(
                    font,
                    Component.translatable(
                            "screen.playerinfo.page_switch_tooltip",
                            Component.translatable(
                                    currentPage.titleKey
                            ),
                            Component.translatable(
                                    currentPage.next().titleKey
                            )
                    ),
                    mouseX,
                    mouseY
            );
        }
    }

    private void drawControlButtons(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        boolean historyPage =
                currentPage == Page.HISTORY;
        SortMode activeSortMode = historyPage
                ? selectedHistorySortMode
                : selectedSortMode;
        boolean activeDescending = historyPage
                ? historyDescendingOrder
                : descendingOrder;
        boolean activeTeamPriority = historyPage
                ? historyPrioritizeSameTeam
                : prioritizeSameTeam;

        drawControlButton(
                graphics,
                sortModeButtonX,
                buttonsY,
                activeSortMode.icon,
                isInsideButton(
                        mouseX,
                        mouseY,
                        sortModeButtonX,
                        buttonsY
                )
        );

        drawControlButton(
                graphics,
                sortDirectionButtonX,
                buttonsY,
                activeDescending ? "↓" : "↑",
                isInsideButton(
                        mouseX,
                        mouseY,
                        sortDirectionButtonX,
                        buttonsY
                )
        );

        drawControlButton(
                graphics,
                teamPriorityButtonX,
                buttonsY,
                activeTeamPriority ? "⚑" : "⚐",
                isInsideButton(
                        mouseX,
                        mouseY,
                        teamPriorityButtonX,
                        buttonsY
                )
        );

        String tooltipText = null;

        if (isInsideButton(
                mouseX,
                mouseY,
                sortModeButtonX,
                buttonsY
        )) {
            tooltipText = activeSortMode.displayName;
        } else if (isInsideButton(
                mouseX,
                mouseY,
                sortDirectionButtonX,
                buttonsY
        )) {
            tooltipText = activeDescending
                    ? "从高到低"
                    : "从低到高";
        } else if (isInsideButton(
                mouseX,
                mouseY,
                teamPriorityButtonX,
                buttonsY
        )) {
            tooltipText = activeTeamPriority
                    ? "优先同队"
                    : "默认排序";
        }

        if (tooltipText != null) {
            graphics.renderTooltip(
                    font,
                    Component.literal(tooltipText),
                    mouseX,
                    mouseY
            );
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
            boolean hovered
    ) {
        drawControlButton(
                graphics,
                x,
                y,
                symbol,
                hovered,
                false
        );
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

    private void drawLeftCell(
            GuiGraphics graphics,
            String text,
            int x,
            int y,
            int cellWidth
    ) {
        String shortened = font.plainSubstrByWidth(
                text,
                cellWidth - 8
        );

        graphics.drawString(
                font,
                shortened,
                x + 4,
                y + (ROW_HEIGHT - font.lineHeight) / 2,
                TEXT_COLOR,
                false
        );
    }

    private void drawCenterCell(
            GuiGraphics graphics,
            String text,
            int x,
            int y,
            int cellWidth
    ) {
        drawCenterCell(
                graphics,
                text,
                x,
                y,
                cellWidth,
                TEXT_COLOR
        );
    }

    private void drawCenterCell(
            GuiGraphics graphics,
            String text,
            int x,
            int y,
            int cellWidth,
            int color
    ) {
        String shortened = font.plainSubstrByWidth(
                text,
                Math.max(0, cellWidth - 6)
        );

        graphics.drawCenteredString(
                font,
                shortened,
                x + cellWidth / 2,
                y + (ROW_HEIGHT - font.lineHeight) / 2,
                color
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
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int mouseButton
    ) {
        if (mouseButton == 0) {
            if (isInsideButton(
                    mouseX,
                    mouseY,
                    pageSwitchButtonX,
                    buttonsY
            )) {
                startPageTransition();
                return true;
            }

            if (pageTransitionActive) {
                return true;
            }

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

            if (isInsideButton(
                    mouseX,
                    mouseY,
                    sortModeButtonX,
                    buttonsY
            )) {
                playButtonClickSound();

                if (currentPage == Page.HISTORY) {
                    selectedHistorySortMode =
                            selectedHistorySortMode.next();
                    refreshSortedHistoryPlayers();
                } else {
                    selectedSortMode =
                            selectedSortMode.next();
                    refreshSortedPlayers();
                }
                return true;
            }

            if (isInsideButton(
                    mouseX,
                    mouseY,
                    sortDirectionButtonX,
                    buttonsY
            )) {
                playButtonClickSound();

                if (currentPage == Page.HISTORY) {
                    historyDescendingOrder =
                            !historyDescendingOrder;
                    refreshSortedHistoryPlayers();
                } else {
                    descendingOrder = !descendingOrder;
                    refreshSortedPlayers();
                }
                return true;
            }

            if (isInsideButton(
                    mouseX,
                    mouseY,
                    teamPriorityButtonX,
                    buttonsY
            )) {
                playButtonClickSound();

                if (currentPage == Page.HISTORY) {
                    historyPrioritizeSameTeam =
                            !historyPrioritizeSameTeam;
                    refreshSortedHistoryPlayers();
                } else {
                    prioritizeSameTeam =
                            !prioritizeSameTeam;
                    refreshSortedPlayers();
                }
                return true;
            }
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );
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

    private enum Page {
        COMBAT(
                0,
                "screen.playerinfo.title",
                "⚔"
        ),

        PERSONAL(
                1,
                "screen.playerinfo.personal_title",
                "☺"
        ),

        HISTORY(
                2,
                "screen.playerinfo.history_title",
                "🕑"
        );

        private final int index;
        private final String titleKey;
        private final String icon;

        Page(
                int index,
                String titleKey,
                String icon
        ) {
            this.index = index;
            this.titleKey = titleKey;
            this.icon = icon;
        }

        private Page next() {
            return switch (this) {
                case COMBAT -> PERSONAL;
                case PERSONAL -> HISTORY;
                case HISTORY -> COMBAT;
            };
        }
    }

    private enum SortMode {
        KILLS(
                "kill_count",
                "击杀排序",
                "⚔"
        ),

        DEATHS(
                "death_count",
                "死亡排序",
                "☠"
        ),

        DAMAGE(
                "damage_dealt",
                "伤害排序",
                "✦"
        ),

        ABSORBED(
                "damage_absorbed",
                "承伤排序",
                "◆"
        );

        private final String objectiveName;
        private final String displayName;
        private final String icon;

        SortMode(
                String objectiveName,
                String displayName,
                String icon
        ) {
            this.objectiveName = objectiveName;
            this.displayName = displayName;
            this.icon = icon;
        }

        private SortMode next() {
            SortMode[] modes = values();

            return modes[
                    (ordinal() + 1) % modes.length
                    ];
        }
    }

    private enum Job {
        UNKNOWN(
                0,
                "",
                ChatFormatting.WHITE
        ),

        WARRIOR(
                1,
                "screen.playerinfo.job.warrior",
                ChatFormatting.DARK_BLUE
        ),

        RANGER(
                2,
                "screen.playerinfo.job.ranger",
                ChatFormatting.DARK_GREEN
        ),

        CRUSADER(
                3,
                "screen.playerinfo.job.crusader",
                ChatFormatting.LIGHT_PURPLE
        ),

        IRON_TOWER_GUARD(
                5,
                "screen.playerinfo.job.iron_tower_guard",
                ChatFormatting.GRAY
        ),

        FLAME_DEMON(
                4,
                "screen.playerinfo.job.flame_demon",
                ChatFormatting.RED
        ),

        DRUID(
                456,
                "screen.playerinfo.job.druid",
                ChatFormatting.GREEN
        );

        private final int id;
        private final String translationKey;
        private final ChatFormatting textColor;

        Job(
                int id,
                String translationKey,
                ChatFormatting textColor
        ) {
            this.id = id;
            this.translationKey = translationKey;
            this.textColor = textColor;
        }

        private String getDisplayName() {
            if (translationKey.isEmpty()) {
                return "";
            }

            return Component.translatable(
                    translationKey
            ).getString();
        }

        private int getTextColor() {
            Integer rgbColor = textColor.getColor();

            if (rgbColor == null) {
                return TEXT_COLOR;
            }

            return 0xFF000000 | rgbColor;
        }

        private static Job fromId(int id) {
            for (Job job : values()) {
                if (job.id == id) {
                    return job;
                }
            }

            return UNKNOWN;
        }
    }
}
