package com.example.playerinfo.client;

import com.example.playerinfo.network.ModNetwork;
import com.example.playerinfo.network.PersonalStats;
import com.example.playerinfo.network.SetEquippedTitleC2SPacket;
import com.example.playerinfo.enums.PlayerTitle;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TitleSelectionScreen extends Screen {

    private static final int COLUMNS = 4;
    private static final int ROWS = 3;
    private static final int PADDING = 10;
    private static final int TITLE_BAR_HEIGHT = 32;
    private static final int CARD_GAP = 6;
    private static final int BACK_WIDTH = 24;
    private static final int BACK_HEIGHT = 20;
    private static final int SCROLL_BAR_WIDTH = 2;
    private static final int SCROLL_BAR_SPACE = 7;

    private static final int PANEL_COLOR = 0xF0101010;
    private static final int CARD_COLOR = 0xEE202020;
    private static final int BORDER_COLOR = 0x90FFFFFF;
    private static final int GOLD_COLOR = 0xFFFFD700;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final PlayerInfoScreen parent;
    private final PersonalStats personalStats;
    private int rowOffset;

    public TitleSelectionScreen(
            PlayerInfoScreen parent,
            PersonalStats personalStats
    ) {
        super(Component.translatable(
                "screen.playerinfo.titles"
        ));
        this.parent = parent;
        this.personalStats = personalStats;
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBackground(graphics);
        RenderSystem.enableBlend();

        List<PlayerTitle> titles = visibleTitles();
        clampRowOffset(titles.size());
        Layout layout = layout(titles.size());
        drawRoundedRectangle(
                graphics,
                layout.panelX,
                layout.panelY,
                layout.panelWidth,
                layout.panelHeight,
                3,
                BORDER_COLOR
        );
        drawRoundedRectangle(
                graphics,
                layout.panelX + 1,
                layout.panelY + 1,
                Math.max(1, layout.panelWidth - 2),
                Math.max(1, layout.panelHeight - 2),
                2,
                PANEL_COLOR
        );

        drawBackButton(graphics, layout, mouseX, mouseY);
        graphics.drawCenteredString(
                font,
                title,
                layout.panelX + layout.panelWidth / 2,
                layout.panelY
                        + (TITLE_BAR_HEIGHT
                        - font.lineHeight) / 2,
                GOLD_COLOR
        );

        String equippedTitleId =
                ClientTitleState.getEquippedTitleId();
        int firstTitleIndex = rowOffset * COLUMNS;
        int visibleTitleCount = Math.min(
                COLUMNS * ROWS,
                titles.size() - firstTitleIndex
        );

        for (int visibleIndex = 0;
             visibleIndex < visibleTitleCount;
             visibleIndex++) {
            renderCard(
                    graphics,
                    layout,
                    visibleIndex,
                    titles.get(firstTitleIndex + visibleIndex),
                    equippedTitleId,
                    mouseX,
                    mouseY
            );
        }

        drawScrollBar(
                graphics,
                layout,
                titles.size()
        );

        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        PlayerTitle lockedTitle =
                findHoveredLockedTitle(
                        layout,
                        titles,
                        mouseX,
                        mouseY
                );

        if (lockedTitle != null) {
            graphics.renderTooltip(
                    font,
                    lockedTitle.unlockCondition(),
                    mouseX,
                    mouseY
            );
        } else if (contains(
                mouseX,
                mouseY,
                layout.backX,
                layout.backY,
                BACK_WIDTH,
                BACK_HEIGHT
        )) {
            graphics.renderTooltip(
                    font,
                    Component.translatable(
                            "screen.playerinfo.back_to_personal"
                    ),
                    mouseX,
                    mouseY
            );
        }
    }

    private void renderCard(
            GuiGraphics graphics,
            Layout layout,
            int index,
            PlayerTitle title,
            String equippedTitleId,
            int mouseX,
            int mouseY
    ) {
        Rectangle card = layout.card(index);
        Rectangle action = layout.action(index);
        boolean unlocked = title.isUnlocked(personalStats);
        boolean equipped = title.id().equals(equippedTitleId);
        boolean hovered = action.contains(mouseX, mouseY);
        int cardBorder = equipped
                ? GOLD_COLOR
                : BORDER_COLOR;

        drawRoundedRectangle(
                graphics,
                card.x,
                card.y,
                card.width,
                card.height,
                3,
                cardBorder
        );
        drawRoundedRectangle(
                graphics,
                card.x + 1,
                card.y + 1,
                Math.max(1, card.width - 2),
                Math.max(1, card.height - 2),
                2,
                CARD_COLOR
        );

        int titleTop = card.y + 2;
        int titleBottom = Math.max(
                titleTop + 1,
                action.y - 2
        );
        graphics.enableScissor(
                card.x + 2,
                titleTop,
                card.x + card.width - 2,
                titleBottom
        );
        graphics.drawCenteredString(
                font,
                title.displayName(),
                card.x + card.width / 2,
                titleTop + Math.max(
                        1,
                        (titleBottom - titleTop
                                - font.lineHeight) / 2
                ),
                TEXT_COLOR
        );
        graphics.disableScissor();

        drawActionButton(
                graphics,
                action,
                unlocked,
                equipped,
                hovered
        );
    }

    private void drawActionButton(
            GuiGraphics graphics,
            Rectangle action,
            boolean unlocked,
            boolean equipped,
            boolean hovered
    ) {
        int borderColor = hovered || equipped
                ? GOLD_COLOR
                : 0xFFA0A0A0;
        int backgroundColor;

        if (!unlocked) {
            backgroundColor = hovered
                    ? 0xE0555555
                    : 0xD02A2A2A;
        } else if (equipped) {
            backgroundColor = hovered
                    ? 0xE04F8050
                    : 0xE038683A;
        } else {
            backgroundColor = hovered
                    ? 0xE0606060
                    : 0xD0383838;
        }

        graphics.fill(
                action.x,
                action.y,
                action.x + action.width,
                action.y + action.height,
                borderColor
        );
        graphics.fill(
                action.x + 1,
                action.y + 1,
                action.x + action.width - 1,
                action.y + action.height - 1,
                backgroundColor
        );

        if (!unlocked) {
            drawLockIcon(
                    graphics,
                    action.x + action.width / 2 - 4,
                    action.y + action.height / 2 - 5,
                    hovered ? GOLD_COLOR : TEXT_COLOR
            );
            return;
        }

        Component label = Component.translatable(
                equipped
                        ? "screen.playerinfo.title_unequip"
                        : "screen.playerinfo.title_equip"
        );
        int labelWidth = font.width(label);
        int iconWidth = 8;
        int gap = 3;
        int groupWidth = iconWidth + gap + labelWidth;
        int groupX = action.x
                + Math.max(2,
                (action.width - groupWidth) / 2);
        int iconY = action.y
                + (action.height - 8) / 2;

        if (equipped) {
            drawCheckIcon(
                    graphics,
                    groupX,
                    iconY,
                    GOLD_COLOR
            );
        } else {
            drawWearIcon(
                    graphics,
                    groupX,
                    iconY,
                    TEXT_COLOR
            );
        }

        int textX = groupX + iconWidth + gap;
        graphics.enableScissor(
                textX,
                action.y + 1,
                action.x + action.width - 2,
                action.y + action.height - 1
        );
        graphics.drawString(
                font,
                label,
                textX,
                action.y + (action.height
                        - font.lineHeight) / 2,
                TEXT_COLOR,
                false
        );
        graphics.disableScissor();
    }

    private void drawBackButton(
            GuiGraphics graphics,
            Layout layout,
            int mouseX,
            int mouseY
    ) {
        boolean hovered = contains(
                mouseX,
                mouseY,
                layout.backX,
                layout.backY,
                BACK_WIDTH,
                BACK_HEIGHT
        );
        int border = hovered
                ? GOLD_COLOR
                : 0xFFA0A0A0;
        int background = hovered
                ? 0xE0606060
                : 0xD0303030;

        graphics.fill(
                layout.backX,
                layout.backY,
                layout.backX + BACK_WIDTH,
                layout.backY + BACK_HEIGHT,
                border
        );
        graphics.fill(
                layout.backX + 1,
                layout.backY + 1,
                layout.backX + BACK_WIDTH - 1,
                layout.backY + BACK_HEIGHT - 1,
                background
        );
        graphics.drawCenteredString(
                font,
                Component.literal("←"),
                layout.backX + BACK_WIDTH / 2,
                layout.backY
                        + (BACK_HEIGHT - font.lineHeight) / 2,
                TEXT_COLOR
        );
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int mouseButton
    ) {
        if (mouseButton != 0) {
            return super.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );
        }

        List<PlayerTitle> titles = visibleTitles();
        clampRowOffset(titles.size());
        Layout layout = layout(titles.size());

        if (contains(
                mouseX,
                mouseY,
                layout.backX,
                layout.backY,
                BACK_WIDTH,
                BACK_HEIGHT
        )) {
            playButtonClickSound();
            onClose();
            return true;
        }

        int firstTitleIndex = rowOffset * COLUMNS;
        int visibleTitleCount = Math.min(
                COLUMNS * ROWS,
                titles.size() - firstTitleIndex
        );

        for (int visibleIndex = 0;
             visibleIndex < visibleTitleCount;
             visibleIndex++) {
            Rectangle action = layout.action(visibleIndex);

            if (!action.contains(mouseX, mouseY)) {
                continue;
            }

            PlayerTitle selectedTitle = titles.get(
                    firstTitleIndex + visibleIndex
            );

            if (selectedTitle.isUnlocked(personalStats)) {
                String requestedId = selectedTitle.id().equals(
                        ClientTitleState.getEquippedTitleId()
                )
                        ? PlayerTitle.NONE_ID
                        : selectedTitle.id();

                playButtonClickSound();
                ModNetwork.CHANNEL.sendToServer(
                        new SetEquippedTitleC2SPacket(
                                requestedId
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

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        List<PlayerTitle> titles = visibleTitles();
        int maxRowOffset = maxRowOffset(titles.size());

        if (maxRowOffset <= 0) {
            rowOffset = 0;
            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    delta
            );
        }

        Layout layout = layout(titles.size());
        if (!contains(
                mouseX,
                mouseY,
                layout.gridX,
                layout.gridY,
                layout.gridWidth,
                layout.gridHeight
        )) {
            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    delta
            );
        }

        if (delta > 0.0D) {
            rowOffset--;
        } else if (delta < 0.0D) {
            rowOffset++;
        }

        rowOffset = Mth.clamp(
                rowOffset,
                0,
                maxRowOffset
        );
        return true;
    }

    @Override
    public void onClose() {
        parent.prepareForReturnFromTitles();

        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private PlayerTitle findHoveredLockedTitle(
            Layout layout,
            List<PlayerTitle> titles,
            double mouseX,
            double mouseY
    ) {
        int firstTitleIndex = rowOffset * COLUMNS;
        int visibleTitleCount = Math.min(
                COLUMNS * ROWS,
                titles.size() - firstTitleIndex
        );

        for (int visibleIndex = 0;
             visibleIndex < visibleTitleCount;
             visibleIndex++) {
            PlayerTitle title = titles.get(
                    firstTitleIndex + visibleIndex
            );

            if (!title.isUnlocked(personalStats)
                    && layout.action(visibleIndex).contains(
                    mouseX,
                    mouseY
            )) {
                return title;
            }
        }

        return null;
    }

    private List<PlayerTitle> visibleTitles() {
        List<PlayerTitle> titles = new ArrayList<>();

        for (PlayerTitle title : PlayerTitle.values()) {
            boolean unlocked = title.isUnlocked(personalStats);
            if (!title.hiddenUntilUnlocked() || unlocked) {
                titles.add(title);
            }
        }

        titles.sort(
                Comparator.comparingInt(title ->
                        title.isUnlocked(personalStats) ? 0 : 1
                )
        );
        return titles;
    }

    private int maxRowOffset(int titleCount) {
        int totalRows = (titleCount + COLUMNS - 1)
                / COLUMNS;
        return Math.max(0, totalRows - ROWS);
    }

    private void clampRowOffset(int titleCount) {
        rowOffset = Mth.clamp(
                rowOffset,
                0,
                maxRowOffset(titleCount)
        );
    }

    private void drawScrollBar(
            GuiGraphics graphics,
            Layout layout,
            int titleCount
    ) {
        int totalRows = (titleCount + COLUMNS - 1)
                / COLUMNS;
        if (totalRows <= ROWS) {
            return;
        }

        int trackX = layout.gridX + layout.gridWidth + 3;
        graphics.fill(
                trackX,
                layout.gridY,
                trackX + SCROLL_BAR_WIDTH,
                layout.gridY + layout.gridHeight,
                0x50505050
        );

        int thumbHeight = Math.max(
                12,
                layout.gridHeight * ROWS / totalRows
        );
        int thumbTravel = layout.gridHeight - thumbHeight;
        int maxRowOffset = maxRowOffset(titleCount);
        int thumbY = layout.gridY
                + thumbTravel * rowOffset / maxRowOffset;

        graphics.fill(
                trackX,
                thumbY,
                trackX + SCROLL_BAR_WIDTH,
                thumbY + thumbHeight,
                GOLD_COLOR
        );
    }

    private Layout layout(int titleCount) {
        int panelWidth = Math.max(
                1,
                Math.min(580, width - 16)
        );
        int panelHeight = Math.max(
                1,
                Math.min(286, height - 30)
        );
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        int gridX = panelX + PADDING;
        int gridY = panelY + TITLE_BAR_HEIGHT;
        boolean scrollable = maxRowOffset(titleCount) > 0;
        int gridWidth = Math.max(
                1,
                panelWidth - PADDING * 2
                        - (scrollable
                        ? SCROLL_BAR_SPACE
                        : 0)
        );
        int gridHeight = Math.max(
                1,
                panelHeight - TITLE_BAR_HEIGHT - PADDING
        );
        int cardWidth = Math.max(
                1,
                (gridWidth - CARD_GAP
                        * (COLUMNS - 1)) / COLUMNS
        );
        int cardHeight = Math.max(
                1,
                (gridHeight - CARD_GAP
                        * (ROWS - 1)) / ROWS
        );

        return new Layout(
                panelX,
                panelY,
                panelWidth,
                panelHeight,
                panelX + PADDING,
                panelY + 6,
                gridX,
                gridY,
                gridWidth,
                gridHeight,
                cardWidth,
                cardHeight
        );
    }

    private void playButtonClickSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(
                            SoundEvents.UI_BUTTON_CLICK,
                            1.0F
                    )
            );
        }
    }

    private static void drawRoundedRectangle(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int radius,
            int color
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int safeRadius = Math.max(
                0,
                Math.min(
                        radius,
                        Math.min(width, height) / 2
                )
        );
        graphics.fill(
                x + safeRadius,
                y,
                x + width - safeRadius,
                y + height,
                color
        );
        graphics.fill(
                x,
                y + safeRadius,
                x + width,
                y + height - safeRadius,
                color
        );

        if (safeRadius >= 2) {
            graphics.fill(
                    x + 1,
                    y + 1,
                    x + width - 1,
                    y + height - 1,
                    color
            );
        }
    }

    private static void drawLockIcon(
            GuiGraphics graphics,
            int x,
            int y,
            int color
    ) {
        graphics.renderOutline(x, y + 4, 8, 6, color);
        graphics.fill(x + 2, y + 1, x + 6, y + 2, color);
        graphics.fill(x + 1, y + 2, x + 2, y + 6, color);
        graphics.fill(x + 6, y + 2, x + 7, y + 6, color);
        graphics.fill(x + 3, y + 6, x + 5, y + 8, color);
    }

    private static void drawCheckIcon(
            GuiGraphics graphics,
            int x,
            int y,
            int color
    ) {
        graphics.renderOutline(x, y, 8, 8, color);
        graphics.fill(x + 2, y + 4, x + 4, y + 6, color);
        graphics.fill(x + 4, y + 3, x + 5, y + 5, color);
        graphics.fill(x + 5, y + 2, x + 7, y + 4, color);
    }

    private static void drawWearIcon(
            GuiGraphics graphics,
            int x,
            int y,
            int color
    ) {
        graphics.renderOutline(x, y, 8, 8, color);
        graphics.fill(x + 3, y + 2, x + 5, y + 6, color);
        graphics.fill(x + 1, y + 3, x + 7, y + 5, color);
    }

    private static boolean contains(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x
                && mouseX < x + width
                && mouseY >= y
                && mouseY < y + height;
    }

    private record Rectangle(
            int x,
            int y,
            int width,
            int height
    ) {
        private boolean contains(
                double mouseX,
                double mouseY
        ) {
            return TitleSelectionScreen.contains(
                    mouseX,
                    mouseY,
                    x,
                    y,
                    width,
                    height
            );
        }
    }

    private record Layout(
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int backX,
            int backY,
            int gridX,
            int gridY,
            int gridWidth,
            int gridHeight,
            int cardWidth,
            int cardHeight
    ) {
        private Rectangle card(int index) {
            int column = index % COLUMNS;
            int row = index / COLUMNS;

            return new Rectangle(
                    gridX + column
                            * (cardWidth + CARD_GAP),
                    gridY + row
                            * (cardHeight + CARD_GAP),
                    cardWidth,
                    cardHeight
            );
        }

        private Rectangle action(int index) {
            Rectangle card = card(index);
            int actionHeight = Math.max(
                    1,
                    Math.min(
                            22,
                            Math.max(
                                    1,
                                    card.height / 2 - 4
                            )
                    )
            );

            return new Rectangle(
                    card.x + 3,
                    card.y + card.height
                            - actionHeight - 3,
                    Math.max(1, card.width - 6),
                    actionHeight
            );
        }
    }
}
