package me.jojo.gardenease.gui;

import me.jojo.gardenease.GardenEase;
import me.jojo.gardenease.config.ConfigManager;
import me.jojo.gardenease.config.ModConfig;
import me.jojo.gardenease.data.GroupConfig;
import me.jojo.gardenease.data.GroupMode;
import me.jojo.gardenease.data.LaneConfig;
import me.jojo.gardenease.data.FarmProfile;
import me.jojo.gardenease.commands.RecordingManager;
import me.jojo.gardenease.macro.MacroController;
import me.jojo.gardenease.macro.MacroState;
import me.jojo.gardenease.util.MessageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import me.jojo.gardenease.util.Text;

import java.util.ArrayList;
import java.util.List;

public class GardenEaseScreen extends Screen {
    private static final int BG_COLOR = 0xEE1A1B26;
    private static final int PANEL_COLOR = 0xFF24283B;
    private static final int SIDEBAR_COLOR = 0xFF16161E;
    private static final int ACCENT_COLOR = 0xFF7AA2F7;
    private static final int TEXT_COLOR = 0xFFC0CAF5;
    private static final int TEXT_DIM = 0xFF565F89;
    private static final int SUCCESS_COLOR = 0xFF9ECE6A;
    private static final int DANGER_COLOR = 0xFFF7768E;
    private static final int WARNING_COLOR = 0xFFE0AF68;
    private static final int PURPLE_COLOR = 0xFFBB9AF7;
    private static final int MAX_VISIBLE_GROUPS = 4;

    private int guiWidth = 500;
    private int guiHeight = 350;
    private int sidebarWidth = 60;
    private int guiLeft;
    private int guiTop;

    private int currentTab = 0;
    private int selectedGroupIndex = -1;
    private int scrollOffset = 0;
    private int settingsScrollOffset = 0;

    private List<FancyButton> tabButtons = new ArrayList<>();
    private List<FancyButton> actionButtons = new ArrayList<>();
    private List<FancyButton> groupButtons = new ArrayList<>();

    private boolean loopEnabled = true;
    private boolean sprintEnabled = true;
    private boolean hideModMessages = false;
    private boolean autoUngrabMouse = false;
    private String newGroupName = "";
    private boolean showCreateInput = false;
    private long lastClickTime = 0;
    private EditBox farmEndXField;
    private EditBox farmEndYField;
    private EditBox farmEndZField;
    private boolean profileWidgetsAdded = false;

    public GardenEaseScreen() {
        super(Component.literal("Farming Macro"));
    }

    @Override
    protected void init() {
        super.init();
        guiLeft = (this.width - guiWidth) / 2;
        guiTop = (this.height - guiHeight) / 2;

        ConfigManager.loadGroups();
        loopEnabled = ModConfig.INSTANCE.isLoopEnabled();
        sprintEnabled = ModConfig.INSTANCE.isSprintEnabled();
        hideModMessages = ModConfig.INSTANCE.isHideModMessages();
        autoUngrabMouse = ModConfig.INSTANCE.isAutoUngrabMouse();

        initButtons();
    }

    private void initButtons() {
        tabButtons.clear();
        actionButtons.clear();
        groupButtons.clear();

        int buttonSize = 40;
        int startY = guiTop + 50;
        int spacing = 10;

        tabButtons.add(new FancyButton(guiLeft + (sidebarWidth - buttonSize) / 2, startY, buttonSize, buttonSize, "G", () -> selectTab(0)));
        tabButtons.add(new FancyButton(guiLeft + (sidebarWidth - buttonSize) / 2, startY + (buttonSize + spacing), buttonSize, buttonSize, "S", () -> selectTab(1)));
        tabButtons.add(new FancyButton(guiLeft + (sidebarWidth - buttonSize) / 2, startY + (buttonSize + spacing) * 2, buttonSize, buttonSize, "A", () -> selectTab(2)));
        tabButtons.add(new FancyButton(guiLeft + (sidebarWidth - buttonSize) / 2, startY + (buttonSize + spacing) * 3, buttonSize, buttonSize, "K", () -> selectTab(3)));
        tabButtons.add(new FancyButton(guiLeft + (sidebarWidth - buttonSize) / 2, startY + (buttonSize + spacing) * 4, buttonSize, buttonSize, "P", () -> selectTab(4)));

        initProfileFields();
        if (currentTab == 4) addProfileFields();

        refreshGroupButtons();
    }

    private List<Integer> getDisplayOrder() {
        List<GroupConfig> groups = ModConfig.INSTANCE.getGroups();
        int lastRunId = ModConfig.INSTANCE.getLastRunGroupId();

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) order.add(i);

        order.sort((a, b) -> groups.get(a).getName().compareToIgnoreCase(groups.get(b).getName()));

        if (lastRunId >= 0) {
            for (int i = 0; i < order.size(); i++) {
                if (groups.get(order.get(i)).getId() == lastRunId) {
                    int idx = order.remove(i);
                    order.add(0, idx);
                    break;
                }
            }
        }
        return order;
    }

    private void refreshGroupButtons() {
        groupButtons.clear();
        List<GroupConfig> groups = ModConfig.INSTANCE.getGroups();
        List<Integer> order = getDisplayOrder();

        int startY = guiTop + 60;
        int buttonHeight = 40;
        int contentWidth = guiWidth - sidebarWidth - 30;

        for (int i = 0; i < MAX_VISIBLE_GROUPS; i++) {
            int displayIdx = i + scrollOffset;
            if (displayIdx >= order.size()) break;

            final int actualIndex = order.get(displayIdx);
            GroupConfig group = groups.get(actualIndex);
            int y = startY + (i * (buttonHeight + 8));

            boolean isLastRun = group.getId() == ModConfig.INSTANCE.getLastRunGroupId();
            String label = (isLastRun ? "§e★ " : "") + group.getName() + " §7(" + group.getLanes().size() + " lanes)";
            FancyButton btn = new FancyButton(guiLeft + sidebarWidth + 15, y, contentWidth, buttonHeight,
                    label,
                    () -> selectedGroupIndex = actualIndex);
            btn.bgColor = 0x442A2A3A;
            groupButtons.add(btn);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        RenderUtils.drawRoundedRect(context, guiLeft, guiTop, guiWidth, guiHeight, 8, BG_COLOR);
        RenderUtils.drawRoundedRect(context, guiLeft, guiTop, sidebarWidth, guiHeight, 8, SIDEBAR_COLOR);

        context.fill(guiLeft + sidebarWidth, guiTop, guiLeft + guiWidth, guiTop + 40, PANEL_COLOR);
        context.text(this.font, Component.literal("GardenEase"), guiLeft + sidebarWidth + 15, guiTop + 15, ACCENT_COLOR, false);

        String status = MacroController.getInstance().isRunning() ? "§a● RUNNING" : "§7○ IDLE";
        context.text(this.font, Component.literal(status), guiLeft + guiWidth - 80, guiTop + 15, TEXT_COLOR, false);

        for (int i = 0; i < tabButtons.size(); i++) {
            FancyButton btn = tabButtons.get(i);
            btn.selected = (i == currentTab);
            btn.render(context, mouseX, mouseY, this.font);
        }

        switch (currentTab) {
            case 0 -> renderGroupsTab(context, mouseX, mouseY);
            case 1 -> renderSettingsTab(context, mouseX, mouseY);
            case 2 -> renderAboutTab(context, mouseX, mouseY);
            case 3 -> renderKeybindsTab(context, mouseX, mouseY);
            case 4 -> renderProfilesTab(context, mouseX, mouseY);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void renderGroupsTab(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        List<GroupConfig> groups = ModConfig.INSTANCE.getGroups();
        actionButtons.clear();

        if (groups.isEmpty()) {
            drawCenteredString(context, "§7No groups created yet", guiLeft + guiWidth / 2, guiTop + 120, TEXT_DIM);
            drawCenteredString(context, "§7Click 'Create New' to add one", guiLeft + guiWidth / 2, guiTop + 135, TEXT_DIM);
        } else {
            for (FancyButton btn : groupButtons) {
                int idx = groupButtons.indexOf(btn) + scrollOffset;
                btn.selected = (idx == selectedGroupIndex);
                btn.render(context, mouseX, mouseY, this.font);
            }
        }

        RenderUtils.drawScrollbar(context, guiLeft + guiWidth - 12, guiTop + 60, 232,
            getDisplayOrder().size(), MAX_VISIBLE_GROUPS, scrollOffset,
            Math.max(0, getDisplayOrder().size() - MAX_VISIBLE_GROUPS), TEXT_DIM);

        int bottomY = guiTop + guiHeight - 45;

        FancyButton createBtn = new FancyButton(guiLeft + 15, bottomY, 62, 30, "§a+ New", this::createNewGroup);
        createBtn.bgColor = 0xFF2D5A2D;
        createBtn.hoverColor = 0xFF3D7A3D;
        createBtn.render(context, mouseX, mouseY, this.font);
        actionButtons.add(createBtn);

        if (selectedGroupIndex >= 0 && selectedGroupIndex < groups.size()) {
            GroupConfig selected = groups.get(selectedGroupIndex);

            // x positions: New(15,62) Start(80,48) Edit(131,48) Rec(182,48) Clone(233,52) MODE(288,78) Resume(369,58) Del(430,55)
            FancyButton startBtn = new FancyButton(guiLeft + 80, bottomY, 48, 30, "§a▶ Start", () -> startGroup(selected));
            startBtn.bgColor = SUCCESS_COLOR & 0x88FFFFFF;
            startBtn.hoverColor = SUCCESS_COLOR;
            startBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(startBtn);

            FancyButton editBtn = new FancyButton(guiLeft + 131, bottomY, 48, 30, "§b✎ Edit", () -> this.minecraft.setScreenAndShow(new GroupEditorScreen(this, selected)));
            editBtn.bgColor = ACCENT_COLOR & 0x88FFFFFF;
            editBtn.hoverColor = ACCENT_COLOR;
            editBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(editBtn);

            FancyButton recordBtn = new FancyButton(guiLeft + 182, bottomY, 48, 30, "§e● Rec", () -> recordGroup(selected));
            recordBtn.bgColor = WARNING_COLOR & 0x88FFFFFF;
            recordBtn.hoverColor = WARNING_COLOR;
            recordBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(recordBtn);

            FancyButton cloneBtn = new FancyButton(guiLeft + 233, bottomY, 52, 30, "§d⧉ Clone", () -> cloneGroup(selected));
            cloneBtn.bgColor = PURPLE_COLOR & 0x66FFFFFF;
            cloneBtn.hoverColor = PURPLE_COLOR;
            cloneBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(cloneBtn);

            boolean isDuration = selected.getMode() == GroupMode.DURATION;
            String modeLabel = "§fMODE: " + (isDuration ? "§aDUR" : "§dCORDS");
            FancyButton modeBtn = new FancyButton(guiLeft + 288, bottomY, 78, 30, modeLabel, () -> {
                selected.cycleMode();
                ConfigManager.saveGroup(selected);
            });
            modeBtn.bgColor = isDuration ? (SUCCESS_COLOR & 0x55FFFFFF) : (PURPLE_COLOR & 0x55FFFFFF);
            modeBtn.hoverColor = isDuration ? SUCCESS_COLOR : PURPLE_COLOR;
            modeBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(modeBtn);

            FancyButton resumeBtn = new FancyButton(guiLeft + 369, bottomY, 58, 30, "§6⇢ Resume", () -> resumeGroup(selected));
            resumeBtn.bgColor = SUCCESS_COLOR & 0x88FFFFFF;
            resumeBtn.hoverColor = SUCCESS_COLOR;
            resumeBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(resumeBtn);

            FancyButton deleteBtn = new FancyButton(guiLeft + 430, bottomY, 55, 30, "§c✕ Del", () -> deleteGroup(selected));
            deleteBtn.bgColor = DANGER_COLOR & 0x88FFFFFF;
            deleteBtn.hoverColor = DANGER_COLOR;
            deleteBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(deleteBtn);

            context.text(this.font, "§7Selected: §f" + selected.getName(), guiLeft + 15, guiTop + guiHeight - 60, TEXT_COLOR, false);
        }

        if (MacroController.getInstance().isRunning()) {
            int stopX = selectedGroupIndex >= 0 ? guiLeft + 425 : guiLeft + 90;
            FancyButton stopBtn = new FancyButton(stopX, bottomY, 55, 30, "§c■ Stop", this::stopMacro);
            stopBtn.bgColor = DANGER_COLOR;
            stopBtn.hoverColor = 0xFFFF5555;
            stopBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(stopBtn);
        }
    }

    private void renderSettingsTab(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int contentX = guiLeft + 70;
        int y = guiTop + 75 - settingsScrollOffset;
        int visibleTop = guiTop + 45;
        int visibleBottom = guiTop + guiHeight - 15;
        actionButtons.clear();

        if (y >= visibleTop - 20 && y <= visibleBottom) {
            context.text(this.font, "§l§eSettings", contentX, y, TEXT_COLOR, false);
        }
        y += 25;

        if (y >= visibleTop && y <= visibleBottom) {
            context.text(this.font, "Stop Farming Key", contentX, y, TEXT_COLOR, false);
        }
        y += 15;
        if (y >= visibleTop && y <= visibleBottom) {
            context.text(this.font, "§7Configure in: Options > Controls > Key Binds", contentX, y, TEXT_DIM, false);
        }
        y += 12;
        if (y >= visibleTop && y <= visibleBottom) {
            context.text(this.font, "§7Category: Farming Macro", contentX, y, TEXT_DIM, false);
        }
        y += 25;

        if (y >= visibleTop && y + 28 <= visibleBottom) {
            context.text(this.font, "Sprint While Macro Running", contentX, y, TEXT_COLOR, false);
        }
        y += 15;
        if (y >= visibleTop && y + 28 <= visibleBottom) {
            String sprintToggleText = sprintEnabled ? "§a✓ Enabled" : "§c✕ Disabled";
            FancyButton sprintToggleBtn = new FancyButton(contentX, y, 120, 28, sprintToggleText, this::toggleSprint);
            sprintToggleBtn.bgColor = sprintEnabled ? (SUCCESS_COLOR & 0x88FFFFFF) : (DANGER_COLOR & 0x88FFFFFF);
            sprintToggleBtn.hoverColor = sprintEnabled ? SUCCESS_COLOR : DANGER_COLOR;
            sprintToggleBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(sprintToggleBtn);
        }

        y += 40;
        if (y >= visibleTop && y + 28 <= visibleBottom) {
            context.text(this.font, "Loop While Macro Running", contentX, y, TEXT_COLOR, false);
        }
        y += 15;
        if (y >= visibleTop && y + 28 <= visibleBottom) {
            String loopToggleText = loopEnabled ? "§a✓ Enabled" : "§c✕ Disabled";
            FancyButton loopToggleBtn = new FancyButton(contentX, y, 120, 28, loopToggleText, this::toggleLoopWhileMacroing);
            loopToggleBtn.bgColor = loopEnabled ? (SUCCESS_COLOR & 0x88FFFFFF) : (DANGER_COLOR & 0x88FFFFFF);
            loopToggleBtn.hoverColor = loopEnabled ? SUCCESS_COLOR : DANGER_COLOR;
            loopToggleBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(loopToggleBtn);
        }

        y += 40;
        if (y >= visibleTop && y + 28 <= visibleBottom) {
            context.text(this.font, "Hide Mod Messages", contentX, y, TEXT_COLOR, false);
        }
        y += 15;
        if (y >= visibleTop && y + 28 <= visibleBottom) {
            String hideMessagesText = hideModMessages ? "§a✓ Hidden" : "§c✕ Visible";
            FancyButton hideMessagesBtn = new FancyButton(contentX, y, 120, 28, hideMessagesText, this::toggleHideMessages);
            hideMessagesBtn.bgColor = hideModMessages ? (SUCCESS_COLOR & 0x88FFFFFF) : (DANGER_COLOR & 0x88FFFFFF);
            hideMessagesBtn.hoverColor = hideModMessages ? SUCCESS_COLOR : DANGER_COLOR;
            hideMessagesBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(hideMessagesBtn);
        }

        y += 40;
        if (y >= visibleTop && y + 28 <= visibleBottom) {
            context.text(this.font, "Ungrabbable Mouse", contentX, y, TEXT_COLOR, false);
        }
        y += 15;
        if (y >= visibleTop && y + 28 <= visibleBottom) {
            String autoUngrabText = autoUngrabMouse ? "§a✓ Enabled" : "§c✕ Disabled";
            FancyButton autoUngrabBtn = new FancyButton(contentX, y, 120, 28, autoUngrabText, this::toggleAutoUngrabMouse);
            autoUngrabBtn.bgColor = autoUngrabMouse ? (SUCCESS_COLOR & 0x88FFFFFF) : (DANGER_COLOR & 0x88FFFFFF);
            autoUngrabBtn.hoverColor = autoUngrabMouse ? SUCCESS_COLOR : DANGER_COLOR;
            autoUngrabBtn.render(context, mouseX, mouseY, this.font);
            actionButtons.add(autoUngrabBtn);
        }

        y += 45;
        if (y >= visibleTop && y <= visibleBottom) {
            context.text(this.font, "§l§eInfo", contentX, y, TEXT_COLOR, false);
        }
        y += 18;
        if (y >= visibleTop && y <= visibleBottom) {
            context.text(this.font, "§7Version: §f" + GardenEase.MOD_VERSION, contentX, y, TEXT_DIM, false);
        }
        y += 12;
        if (y >= visibleTop && y <= visibleBottom) {
            context.text(this.font, "§7Groups: §f" + ModConfig.INSTANCE.getGroups().size(), contentX, y, TEXT_DIM, false);
        }

        RenderUtils.drawScrollbar(context, guiLeft + guiWidth - 12, guiTop + 45, guiHeight - 60,
            getSettingsContentHeight(), guiHeight - 90, settingsScrollOffset,
            Math.max(0, getSettingsContentHeight() - (guiHeight - 90)), TEXT_DIM);
    }

    private int getSettingsContentHeight() {
        int contentHeight = 0;
        contentHeight += 407;
        return contentHeight;
    }

    private void renderAboutTab(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int y = guiTop + 75;
        int x = guiLeft + 70;

        context.text(this.font, "§l§eAbout GardenEase", x, y, TEXT_COLOR, false);
        y += 20;

        String[] aboutLines = {
                "§aGardenEase is a QOL mod designed to help",
                "§ayou rest your fingers while farming.",
                "",
                "§fHealth & Safety First:",
                "§7- We care about your health.",
                "§7- Avoid holding keys for hours non-stop.",
                "§7- Take regular breaks.",
                "",
                "§fAnti-Ban Tips:",
                "§c- Don't AFK! Always keep an eye on the game.",
                "§c- Take breaks and don't farm for hours.",
                "§c- Don't do it recursively.",
                "",
                "§bJoin our Discord for support:",
                "§ehttps://discord.gg/eb8uCbVP2K"
        };

        for (String line : aboutLines) {
            context.text(this.font, line, x, y, TEXT_COLOR, false);
            y += 11;
        }
    }

    private void renderKeybindsTab(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int x = guiLeft + 70;
        int y = guiTop + 75;
        context.text(this.font, "§l§eKeybinds", x, y, TEXT_COLOR, false);
        y += 25;

        String[] keybinds = {
                "§fStop Farming Key §7- Immediately stops the running macro.",
                "§fQuick Resume Key §7- Resumes the paused lane at its saved position.",
                "§fStart Profile Farm Key §7- Starts the selected crop profile farm.",
        };
        for (String keybind : keybinds) {
            context.text(this.font, keybind, x, y, TEXT_COLOR, false);
            y += 20;
        }
        y += 5;
        context.text(this.font, "§7Rebind these in Minecraft Options > Controls > Key Binds", x, y, TEXT_DIM, false);
        y += 12;
        context.text(this.font, "§7Category: GardenEase", x, y, TEXT_DIM, false);
    }

    private void renderProfilesTab(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int x = guiLeft + 75;
        int y = guiTop + 75;
        actionButtons.clear();

        context.text(this.font, "§l§eProfiles", x, y, TEXT_COLOR, false);
        y += 28;
        FarmProfile profile = getSelectedProfile();
        context.text(this.font, "§7Profile", x, y + 7, TEXT_COLOR, false);
        FancyButton previousProfileButton = new FancyButton(x + 75, y, 28, 28, "<", this::selectPreviousProfile);
        previousProfileButton.bgColor = ACCENT_COLOR & 0x88FFFFFF;
        previousProfileButton.hoverColor = ACCENT_COLOR;
        previousProfileButton.render(context, mouseX, mouseY, this.font);
        actionButtons.add(previousProfileButton);

        FancyButton profileNameButton = new FancyButton(x + 108, y, 145, 28, profile.getDisplayName(), null);
        profileNameButton.bgColor = PANEL_COLOR;
        profileNameButton.render(context, mouseX, mouseY, this.font);
        actionButtons.add(profileNameButton);

        FancyButton nextProfileButton = new FancyButton(x + 260, y, 28, 28, ">", this::selectNextProfile);
        nextProfileButton.bgColor = ACCENT_COLOR & 0x88FFFFFF;
        nextProfileButton.hoverColor = ACCENT_COLOR;
        nextProfileButton.render(context, mouseX, mouseY, this.font);
        actionButtons.add(nextProfileButton);

        y += 35;
        context.text(this.font, "§7Movement: §f" + profile.getMovementDescription(), x, y, TEXT_COLOR, false);
        y += 29;
        context.text(this.font, "§7End X", x, y + 5, TEXT_COLOR, false);
        context.text(this.font, "§7End Y", x, y + 29, TEXT_COLOR, false);
        context.text(this.font, "§7End Z", x, y + 53, TEXT_COLOR, false);

        FancyButton currentPositionButton = new FancyButton(x + 185, y + 72, 145, 28, "Set Current", this::setProfileEndToCurrentPosition);
        currentPositionButton.bgColor = WARNING_COLOR & 0x88FFFFFF;
        currentPositionButton.hoverColor = WARNING_COLOR;
        currentPositionButton.render(context, mouseX, mouseY, this.font);
        actionButtons.add(currentPositionButton);

        FancyButton saveButton = new FancyButton(x, guiTop + guiHeight - 45, 80, 28, "§a Save", this::saveProfile);
        saveButton.bgColor = SUCCESS_COLOR & 0x88FFFFFF;
        saveButton.hoverColor = SUCCESS_COLOR;
        saveButton.render(context, mouseX, mouseY, this.font);
        actionButtons.add(saveButton);

        FancyButton startButton = new FancyButton(x + 90, guiTop + guiHeight - 45, 120, 28, "§a▶ Start", this::startProfile);
        startButton.bgColor = SUCCESS_COLOR & 0x88FFFFFF;
        startButton.hoverColor = SUCCESS_COLOR;
        startButton.render(context, mouseX, mouseY, this.font);
        actionButtons.add(startButton);

        if (MacroController.getInstance().getState().hasProfilePauseData()) {
            FancyButton resumeButton = new FancyButton(x + 220, guiTop + guiHeight - 45, 105, 28, "§6⇢ Resume", this::resumeProfile);
            resumeButton.bgColor = WARNING_COLOR & 0x88FFFFFF;
            resumeButton.hoverColor = WARNING_COLOR;
            resumeButton.render(context, mouseX, mouseY, this.font);
            actionButtons.add(resumeButton);
        }
    }

    private FarmProfile getSelectedProfile() {
        FarmProfile[] profiles = FarmProfile.values();
        int index = Math.max(0, Math.min(ModConfig.INSTANCE.getSelectedProfileIndex(), profiles.length - 1));
        return profiles[index];
    }

    private void initProfileFields() {
        profileWidgetsAdded = false;
        int x = guiLeft + 150;
        int y = guiTop + 181;
        farmEndXField = new EditBox(font, x, y, 105, 18, Component.literal("End X"));
        farmEndYField = new EditBox(font, x, y + 24, 105, 18, Component.literal("End Y"));
        farmEndZField = new EditBox(font, x, y + 48, 105, 18, Component.literal("End Z"));
        farmEndXField.setMaxLength(32);
        farmEndYField.setMaxLength(32);
        farmEndZField.setMaxLength(32);
        if (ModConfig.INSTANCE.isFarmEndSet()) {
            farmEndXField.setValue(Double.toString(ModConfig.INSTANCE.getFarmEndX()));
            farmEndYField.setValue(Double.toString(ModConfig.INSTANCE.getFarmEndY()));
            farmEndZField.setValue(Double.toString(ModConfig.INSTANCE.getFarmEndZ()));
        }
    }

    private void addProfileFields() {
        if (profileWidgetsAdded) return;
        addRenderableWidget(farmEndXField);
        addRenderableWidget(farmEndYField);
        addRenderableWidget(farmEndZField);
        profileWidgetsAdded = true;
    }

    private void removeProfileFields() {
        if (!profileWidgetsAdded) return;
        removeWidget(farmEndXField);
        removeWidget(farmEndYField);
        removeWidget(farmEndZField);
        profileWidgetsAdded = false;
    }

    private void selectTab(int tab) {
        if (currentTab == 4 && tab != 4) removeProfileFields();
        currentTab = tab;
        if (currentTab == 4) addProfileFields();
    }

    private void selectPreviousProfile() {
        int profileCount = FarmProfile.values().length;
        int previous = (ModConfig.INSTANCE.getSelectedProfileIndex() - 1 + profileCount) % profileCount;
        ModConfig.INSTANCE.setSelectedProfileIndex(previous);
        ConfigManager.save();
    }

    private void selectNextProfile() {
        int next = (ModConfig.INSTANCE.getSelectedProfileIndex() + 1) % FarmProfile.values().length;
        ModConfig.INSTANCE.setSelectedProfileIndex(next);
        ConfigManager.save();
    }

    private void saveProfile() {
        try {
            double x = Double.parseDouble(farmEndXField.getValue());
            double y = Double.parseDouble(farmEndYField.getValue());
            double z = Double.parseDouble(farmEndZField.getValue());
            ModConfig.INSTANCE.setFarmEndX(x);
            ModConfig.INSTANCE.setFarmEndY(y);
            ModConfig.INSTANCE.setFarmEndZ(z);
            ModConfig.INSTANCE.setFarmEndSet(true);
            ModConfig.INSTANCE.setSelectedProfileIndex(Math.max(0, Math.min(
                    ModConfig.INSTANCE.getSelectedProfileIndex(), FarmProfile.values().length - 1)));
            ConfigManager.save();
            MessageUtil.sendClientMessage(Component.literal("§aProfile settings saved."));
        } catch (NumberFormatException ignored) {
            MessageUtil.sendClientMessage(Component.literal("§cEnter valid farm end coordinates."));
        }
    }

    private void setProfileEndToCurrentPosition() {
        if (minecraft == null || minecraft.player == null) return;
        farmEndXField.setValue(String.format("%.2f", minecraft.player.getX()));
        farmEndYField.setValue(String.format("%.2f", minecraft.player.getY()));
        farmEndZField.setValue(String.format("%.2f", minecraft.player.getZ()));
    }

    private void startProfile() {
        MacroController macro = MacroController.getInstance();
        macro.startProfile();
        if (macro.isRunning()) this.onClose();
    }

    private void resumeProfile() {
        MacroController macro = MacroController.getInstance();
        macro.resumeProfile();
        if (macro.isRunning()) this.onClose();
    }

    private void drawCenteredString(GuiGraphicsExtractor context, String text, int x, int y, int color) {
        int width = this.font.width(text);
        context.text(this.font, text, x - width / 2, y, color, false);
    }

    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            long now = System.currentTimeMillis();
            if (now - lastClickTime < 50) return true;
            lastClickTime = now;

            for (FancyButton btn : tabButtons) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    btn.onClick();
                    return true;
                }
            }

            for (FancyButton btn : actionButtons) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    btn.onClick();
                    return true;
                }
            }

            for (FancyButton btn : groupButtons) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    btn.onClick();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (handleMouseClick(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTab == 0) {
            int maxScroll = Math.max(0, getDisplayOrder().size() - MAX_VISIBLE_GROUPS);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(verticalAmount)));
            refreshGroupButtons();
            return true;
        }
        if (currentTab == 1) {
            int visibleHeight = guiHeight - 90;
            int maxScroll = Math.max(0, getSettingsContentHeight() - visibleHeight);
            settingsScrollOffset = Math.max(0, Math.min(maxScroll, settingsScrollOffset - (int) Math.signum(verticalAmount) * 20));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int generateUniqueGroupId() {
        int maxId = 0;
        for (GroupConfig g : ModConfig.INSTANCE.getGroups()) {
            if (g.getId() > maxId) maxId = g.getId();
        }
        return maxId + 1;
    }

    private void createNewGroup() {
        int newId = generateUniqueGroupId();
        String name = "Group_" + newId;
        GroupConfig newGroup = new GroupConfig(
                newId,
                name,
                "",
                "/warp garden"
        );
        ModConfig.INSTANCE.getGroups().add(newGroup);
        ConfigManager.saveGroup(newGroup);
        refreshGroupButtons();

        if (this.minecraft != null && this.minecraft.player != null) {
            MessageUtil.sendClientMessage(Component.literal("§aCreated group: §f" + name + " §7- Use /gm record " + name + " to add lanes"));
        }
    }

    private void startGroup(GroupConfig group) {
        if (group.getLanes().isEmpty()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                MessageUtil.sendClientMessage(Component.literal("§cGroup has no lanes! Use /gm record " + group.getName() + " first"));
            }
            return;
        }

        int idx = ModConfig.INSTANCE.getGroups().indexOf(group);
        ModConfig.INSTANCE.setSelectedGroupIndex(idx);
        ModConfig.INSTANCE.setLastRunGroupId(group.getId());
        ConfigManager.save();
        MacroController.getInstance().start();

        this.onClose();
    }

    private void recordGroup(GroupConfig group) {
        RecordingManager.startRecording(group);
        if (this.minecraft != null && this.minecraft.player != null) {
            MessageUtil.sendClientMessage(Component.literal("§a=== RECORDING MODE ==="));
            MessageUtil.sendClientMessage(Component.literal("§7Recording for: §6" + group.getName()));
            if (group.getMode() == GroupMode.CORDS) {
                MessageUtil.sendClientMessage(Component.literal("§dCORDS mode: §7Walk to where each lane ends, hold §eRIGHT SHIFT §7to freeze movement, then press a movement key to save."));
                MessageUtil.sendClientMessage(Component.literal("§7For combos (WA, WD, SA, SD): hold both keys within 150ms before releasing RIGHT SHIFT."));
            } else {
                MessageUtil.sendClientMessage(Component.literal("§aDURATION mode: §7Hold a movement key for the lane duration, release to save."));
            }
            MessageUtil.sendClientMessage(Component.literal("§7Type §e/fm record §7to stop recording."));
        }
        this.onClose();
    }

    private void resumeGroup(GroupConfig group) {
        MacroController macro = MacroController.getInstance();
        MacroState state = macro.getState();
        String pausedGroup = state.getPausedGroupName();

        if (pausedGroup == null || !pausedGroup.equalsIgnoreCase(group.getName())) {
            if (this.minecraft != null && this.minecraft.player != null) {
                MessageUtil.sendClientMessage(Component.literal("§cNo pause data found for §e" + group.getName()));
            }
            return;
        }

        if (this.minecraft != null && this.minecraft.player != null) {
            double px = this.minecraft.player.getX();
            double py = this.minecraft.player.getY();
            double pz = this.minecraft.player.getZ();

            double dx = Math.abs(px - state.getPausedX());
            double dy = Math.abs(py - state.getPausedY());
            double dz = Math.abs(pz - state.getPausedZ());

            if (dx > 2.0 || dy > 2.0 || dz > 2.0) {
                String coords = String.format("%.1f, %.1f, %.1f", state.getPausedX(), state.getPausedY(), state.getPausedZ());
                MessageUtil.sendClientMessage(Component.literal("§cYou need to be at §e" + coords + " §cto resume"));
                return;
            }
        }

        int groupIndex = -1;
        for (int i = 0; i < ModConfig.INSTANCE.getGroups().size(); i++) {
            if (ModConfig.INSTANCE.getGroups().get(i).getName().equalsIgnoreCase(group.getName())) {
                groupIndex = i;
                break;
            }
        }

        if (groupIndex == -1) {
            if (this.minecraft != null && this.minecraft.player != null) {
                MessageUtil.sendClientMessage(Component.literal("§cGroup not found!"));
            }
            return;
        }

        ModConfig.INSTANCE.setSelectedGroupIndex(groupIndex);
        int laneIndex = state.getPausedLaneIndex();
        float offsetSeconds = state.getPausedRemainingDuration();
        state.clearPauseData();

        macro.start(laneIndex, offsetSeconds);
        this.onClose();
    }

    private void cloneGroup(GroupConfig group) {
        int newId = generateUniqueGroupId();
        String newName = group.getName() + "_Copy";
        GroupConfig clone = new GroupConfig(newId, newName, group.getDescription(), group.getLoopCommand(), group.getMode());
        for (LaneConfig lane : group.getLanes()) {
            clone.getLanes().add(lane.copy());
        }
        ModConfig.INSTANCE.getGroups().add(clone);
        ConfigManager.saveGroup(clone);
        selectedGroupIndex = ModConfig.INSTANCE.getGroups().size() - 1;
        refreshGroupButtons();

        if (this.minecraft != null && this.minecraft.player != null) {
            MessageUtil.sendClientMessage(Component.literal("§aCloned group: §f" + newName));
        }
    }

    private void deleteGroup(GroupConfig group) {
        ModConfig.INSTANCE.getGroups().remove(group);
        try {
            java.nio.file.Path groupsPath = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir().resolve("farmmacro/groups");
            String fileName = group.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
            java.nio.file.Files.deleteIfExists(groupsPath.resolve(fileName));
        } catch (Exception e) {}

        selectedGroupIndex = -1;
        refreshGroupButtons();

        if (this.minecraft != null && this.minecraft.player != null) {
            MessageUtil.sendClientMessage(Component.literal("§aDeleted group: §f" + group.getName()));
        }
    }

    private void stopMacro() {
        MacroController.getInstance().stop();
    }

    private void toggleSprint() {
        sprintEnabled = !sprintEnabled;
        ModConfig.INSTANCE.setSprintEnabled(sprintEnabled);
        ConfigManager.save();
    }

    private void toggleLoopWhileMacroing() {
        loopEnabled = !loopEnabled;
        ModConfig.INSTANCE.setLoopEnabled(loopEnabled);
        ConfigManager.save();
    }

    private void toggleHideMessages() {
        hideModMessages = !hideModMessages;
        ModConfig.INSTANCE.setHideModMessages(hideModMessages);
        ConfigManager.save();
    }

    private void toggleAutoUngrabMouse() {
        autoUngrabMouse = !autoUngrabMouse;
        ModConfig.INSTANCE.setAutoUngrabMouse(autoUngrabMouse);
        ConfigManager.save();
    }

    public boolean shouldPause() {
        return false;
    }

    public static void open() {
        Minecraft.getInstance().setScreenAndShow(new GardenEaseScreen());
    }
}
