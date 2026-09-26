package me.jojo.gardenease.gui;

import me.jojo.gardenease.config.ConfigManager;
import me.jojo.gardenease.data.GroupConfig;
import me.jojo.gardenease.data.GroupMode;
import me.jojo.gardenease.data.LaneConfig;
import me.jojo.gardenease.data.MovementPattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class GroupEditorScreen extends Screen {

    private static final int BG_COLOR = 0xEE1A1B26;
    private static final int PANEL_COLOR = 0xFF24283B;
    private static final int ACCENT_COLOR = 0xFF7AA2F7;
    private static final int TEXT_COLOR = 0xFFC0CAF5;
    private static final int TEXT_DIM = 0xFF565F89;
    private static final int SUCCESS_COLOR = 0xFF9ECE6A;
    private static final int DANGER_COLOR = 0xFFF7768E;
    private static final int PURPLE_COLOR = 0xFFBB9AF7;

    private final Screen parent;
    private final GroupConfig group;

    private EditBox nameField;
    private EditBox descriptionField;
    private EditBox loopCommandField;

    private List<LaneEditorEntry> laneEntries = new ArrayList<>();

    private int guiWidth = 480;
    private int guiHeight = 350;
    private int guiLeft;
    private int guiTop;

    private int laneScrollOffset = 0;
    private static final int MAX_VISIBLE_LANES = 4;
    private static final int LANE_ENTRY_HEIGHT = 45;

    private final List<FancyButton> buttons = new ArrayList<>();
    private long lastClickTime = 0;

    public GroupEditorScreen(Screen parent, GroupConfig group) {
        super(Component.literal("Edit Group: " + group.getName()));
        this.parent = parent;
        this.group = group;
    }

    @Override
    protected void init() {
        guiLeft = (this.width - guiWidth) / 2;
        guiTop = (this.height - guiHeight) / 2;

        int fieldWidth = 200;
        int labelWidth = 90;
        int x = guiLeft + labelWidth + 15;
        int y = guiTop + 35;

        nameField = new EditBox(font, x, y, fieldWidth, 18, Component.literal("Name"));
        nameField.setMaxLength(50);
        nameField.setValue(group.getName());

        descriptionField = new EditBox(font, x, y + 22, fieldWidth, 18, Component.literal("Description"));
        descriptionField.setMaxLength(100);
        descriptionField.setValue(group.getDescription());

        loopCommandField = new EditBox(font, x, y + 44, fieldWidth, 18, Component.literal("Loop Command"));
        loopCommandField.setMaxLength(100);
        loopCommandField.setValue(group.getLoopCommand());

        addRenderableWidget(nameField);
        addRenderableWidget(descriptionField);
        addRenderableWidget(loopCommandField);

        refreshLaneEntries();

        buttons.clear();
        int btnY = guiTop + guiHeight - 35;
        buttons.add(new FancyButton(guiLeft + 15, btnY, 80, 28, "§a Save", this::save));
        buttons.add(new FancyButton(guiLeft + 100, btnY, 80, 28, "§7 Cancel", () -> minecraft.setScreenAndShow(parent)));

        // MODE toggle button
        boolean isDuration = group.getMode() == GroupMode.DURATION;
        String modeLabel = "MODE: " + (isDuration ? "§aDURATION" : "§dCORDS");
        FancyButton modeBtn = new FancyButton(guiLeft + 185, btnY, 105, 28, modeLabel, () -> {
            group.cycleMode();
            refreshLaneEntries();
            // Re-init to refresh mode button text
            init();
        });
        modeBtn.bgColor = isDuration ? (SUCCESS_COLOR & 0x55FFFFFF) : (PURPLE_COLOR & 0x55FFFFFF);
        modeBtn.hoverColor = isDuration ? SUCCESS_COLOR : PURPLE_COLOR;
        buttons.add(modeBtn);

        buttons.add(new FancyButton(guiLeft + guiWidth - 95, btnY, 80, 28, "§a+ Add Lane", this::addLane));
    }

    private void refreshLaneEntries() {
        for (LaneEditorEntry entry : laneEntries) {
            removeWidget(entry.nameField);
            if (entry.durationField != null) removeWidget(entry.durationField);
            if (entry.endXField != null) removeWidget(entry.endXField);
            if (entry.endYField != null) removeWidget(entry.endYField);
            if (entry.endZField != null) removeWidget(entry.endZField);
        }
        laneEntries.clear();

        int lanesStartY = guiTop + 115;
        int laneAreaX = guiLeft + 15;

        List<LaneConfig> lanes = group.getLanes();
        for (int i = 0; i < lanes.size(); i++) {
            LaneConfig lane = lanes.get(i);
            int visualIndex = i - laneScrollOffset;

            if (visualIndex >= 0 && visualIndex < MAX_VISIBLE_LANES) {
                int entryY = lanesStartY + (visualIndex * LANE_ENTRY_HEIGHT);
                LaneEditorEntry entry = new LaneEditorEntry(lane, laneAreaX, entryY, guiWidth - 30, i);
                laneEntries.add(entry);

                addRenderableWidget(entry.nameField);
                if (entry.durationField != null) addRenderableWidget(entry.durationField);
                if (entry.endXField != null) addRenderableWidget(entry.endXField);
                if (entry.endYField != null) addRenderableWidget(entry.endYField);
                if (entry.endZField != null) addRenderableWidget(entry.endZField);
            }
        }
    }

    private void addLane() {
        LaneConfig newLane = group.addLane();
        newLane.setName("Lane " + group.getLanes().size());
        newLane.setDuration(2.0f);
        newLane.setMovementPattern(MovementPattern.W);
        refreshLaneEntries();
    }

    private void removeLane(int index) {
        if (index >= 0 && index < group.getLanes().size()) {
            group.removeLane(group.getLanes().get(index).getId());
            if (laneScrollOffset > 0 && laneScrollOffset >= group.getLanes().size() - MAX_VISIBLE_LANES + 1) {
                laneScrollOffset = Math.max(0, group.getLanes().size() - MAX_VISIBLE_LANES);
            }
            refreshLaneEntries();
        }
    }

    private void cyclePattern(int index) {
        if (index >= 0 && index < group.getLanes().size()) {
            LaneConfig lane = group.getLanes().get(index);
            MovementPattern[] patterns = MovementPattern.values();
            int currentOrdinal = lane.getMovementPattern().ordinal();
            int nextOrdinal = (currentOrdinal + 1) % patterns.length;
            lane.setMovementPattern(patterns[nextOrdinal]);
        }
    }

    private void save() {
        group.setName(nameField.getValue());
        group.setDescription(descriptionField.getValue());
        group.setLoopCommand(loopCommandField.getValue());

        for (LaneEditorEntry entry : laneEntries) {
            entry.applyToLane();
        }

        ConfigManager.saveGroup(group);
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        RenderUtils.drawRoundedRect(context, guiLeft, guiTop, guiWidth, guiHeight, 8, BG_COLOR);

        context.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + 35, PANEL_COLOR);
        context.text(font, Component.literal("Edit Group"), guiLeft + 15, guiTop + 12, ACCENT_COLOR, false);

        // Show current mode in header
        boolean isDuration = group.getMode() == GroupMode.DURATION;
        String modeDisplay = "Mode: " + (isDuration ? "§aDURATION" : "§dCORDS");
        context.text(font, modeDisplay, guiLeft + guiWidth - 120, guiTop + 12, TEXT_DIM, false);

        int y = guiTop + 40;
        context.text(font, "§7Name:", guiLeft + 15, y + 5, TEXT_DIM, false);
        context.text(font, "§7Description:", guiLeft + 15, y + 27, TEXT_DIM, false);
        context.text(font, "§7Loop Cmd:", guiLeft + 15, y + 49, TEXT_DIM, false);

        nameField.extractWidgetRenderState(context, mouseX, mouseY, delta);
        descriptionField.extractWidgetRenderState(context, mouseX, mouseY, delta);
        loopCommandField.extractWidgetRenderState(context, mouseX, mouseY, delta);

        int lanesHeaderY = guiTop + 100;
        String label = String.format("§l§eLanes §7\"%d\" total", group.getLanes().size());
        context.text(font, label, guiLeft + 290, lanesHeaderY, TEXT_COLOR, false);

        if (group.getLanes().size() > MAX_VISIBLE_LANES) {
            String scrollInfo = "§7[" + (laneScrollOffset + 1) + "-" + Math.min(laneScrollOffset + MAX_VISIBLE_LANES, group.getLanes().size()) + "]";
            context.text(font, scrollInfo, guiLeft + guiWidth - 60, lanesHeaderY, TEXT_DIM, false);
        }

        int lanesStartY = guiTop + 115;
        int laneAreaHeight = MAX_VISIBLE_LANES * LANE_ENTRY_HEIGHT;
        context.fill(guiLeft + 10, lanesStartY - 3, guiLeft + guiWidth - 10, lanesStartY + laneAreaHeight, 0x40000000);
        RenderUtils.drawScrollbar(context, guiLeft + guiWidth - 14, lanesStartY, laneAreaHeight,
            group.getLanes().size(), MAX_VISIBLE_LANES, laneScrollOffset,
            Math.max(0, group.getLanes().size() - MAX_VISIBLE_LANES), TEXT_DIM);

        if (group.getLanes().isEmpty()) {
            drawCenteredString(context, "§7No lanes - Click '+ Add Lane' to create one", guiLeft + guiWidth / 2, lanesStartY + 30, TEXT_DIM);
        }

        for (LaneEditorEntry entry : laneEntries) {
            entry.render(context, mouseX, mouseY, delta, font);
        }

        for (FancyButton btn : buttons) {
            btn.render(context, mouseX, mouseY, font);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            long now = System.currentTimeMillis();
            if (now - lastClickTime < 50) return true;
            lastClickTime = now;

            for (FancyButton btn : buttons) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    btn.onClick();
                    return true;
                }
            }

            for (LaneEditorEntry entry : laneEntries) {
                if (entry.handleClick((int) mouseX, (int) mouseY)) {
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
        int maxScroll = Math.max(0, group.getLanes().size() - MAX_VISIBLE_LANES);
        laneScrollOffset = Math.max(0, Math.min(maxScroll, laneScrollOffset - (int) Math.signum(verticalAmount)));
        refreshLaneEntries();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreenAndShow(parent);
            return true;
        }
        return super.keyPressed(input);
    }

    public boolean shouldPause() {
        return false;
    }

    private void drawCenteredString(GuiGraphicsExtractor context, String text, int x, int y, int color) {
        int width = font.width(text);
        context.text(font, text, x - width / 2, y, color, false);
    }

    private class LaneEditorEntry {
        LaneConfig lane;
        int x, y, width;
        int laneIndex;

        EditBox nameField;
        // Duration mode
        EditBox durationField;
        // Cords mode
        EditBox endXField;
        EditBox endYField;
        EditBox endZField;

        FancyButton patternBtn;
        FancyButton gaussianBtn;
        FancyButton deleteBtn;

        LaneEditorEntry(LaneConfig lane, int x, int y, int width, int laneIndex) {
            this.lane = lane;
            this.x = x;
            this.y = y;
            this.width = width;
            this.laneIndex = laneIndex;

            boolean cordsMode = group.getMode() == GroupMode.CORDS;

            nameField = new EditBox(font, x + 30, y + 2, 75, 16, Component.literal("Lane Name"));
            nameField.setMaxLength(30);
            nameField.setValue(lane.getName());

            if (!cordsMode) {
                durationField = new EditBox(font, x + 113, y + 2, 45, 16, Component.literal("Duration"));
                durationField.setMaxLength(10);
                durationField.setValue(String.format("%.3f", lane.getDuration()));
                endXField = null;
                endYField = null;
                endZField = null;
            } else {
                durationField = null;
                // X, Y, Z each 38px wide — fits 3 fields in cords mode
                endXField = new EditBox(font, x + 113, y + 2, 38, 16, Component.literal("End X"));
                endXField.setMaxLength(12);
                endXField.setValue(lane.isCordsSet() ? String.format("%.1f", lane.getEndX()) : "");

                endYField = new EditBox(font, x + 158, y + 2, 38, 16, Component.literal("End Y"));
                endYField.setMaxLength(12);
                endYField.setValue(lane.isCordsSet() ? String.format("%.1f", lane.getEndY()) : "");

                endZField = new EditBox(font, x + 203, y + 2, 38, 16, Component.literal("End Z"));
                endZField.setMaxLength(12);
                endZField.setValue(lane.isCordsSet() ? String.format("%.1f", lane.getEndZ()) : "");
            }

            int patternX = cordsMode ? x + 246 : x + 165;
            patternBtn = new FancyButton(patternX, y + 1, 50, 18, lane.getMovementPattern().name(), () -> {
                cyclePattern(laneIndex);
                patternBtn.text = group.getLanes().get(laneIndex).getMovementPattern().name();
            });
            patternBtn.bgColor = 0xFF444444;
            patternBtn.hoverColor = ACCENT_COLOR;

            int gaussX = cordsMode ? x + 300 : x + 224;
            gaussianBtn = new FancyButton(gaussX, y + 1, 35, 18, lane.isGaussianMovement() ? "§aON" : "§cOFF", () -> {
                lane.setGaussianMovement(!lane.isGaussianMovement());
                gaussianBtn.text = lane.isGaussianMovement() ? "§aON" : "§cOFF";
            });
            gaussianBtn.bgColor = 0xFF444444;
            gaussianBtn.hoverColor = ACCENT_COLOR;

            deleteBtn = new FancyButton(x + width - 25, y + 1, 20, 18, "§c✕", () -> removeLane(laneIndex));
            deleteBtn.bgColor = 0xFF442222;
            deleteBtn.hoverColor = DANGER_COLOR;
        }

        void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, net.minecraft.client.gui.Font tr) {
            context.fill(x, y, x + width, y + LANE_ENTRY_HEIGHT - 5, 0x60333333);

            context.text(tr, "§7#" + (laneIndex + 1), x + 5, y + 5, TEXT_DIM, false);

            nameField.extractWidgetRenderState(context, mouseX, mouseY, delta);

            boolean cordsMode = group.getMode() == GroupMode.CORDS;

            if (!cordsMode && durationField != null) {
                durationField.extractWidgetRenderState(context, mouseX, mouseY, delta);
                context.text(tr, "§7s", x + 160, y + 5, TEXT_DIM, false);
            } else if (cordsMode) {
                if (endXField != null) {
                    context.text(tr, "§7X", x + 107, y + 5, TEXT_DIM, false);
                    endXField.extractWidgetRenderState(context, mouseX, mouseY, delta);
                }
                if (endYField != null) {
                    context.text(tr, "§7Y", x + 152, y + 5, TEXT_DIM, false);
                    endYField.extractWidgetRenderState(context, mouseX, mouseY, delta);
                }
                if (endZField != null) {
                    context.text(tr, "§7Z", x + 197, y + 5, TEXT_DIM, false);
                    endZField.extractWidgetRenderState(context, mouseX, mouseY, delta);
                }
                if (!lane.isCordsSet()) {
                    context.text(tr, "§c?", x + 243, y + 5, DANGER_COLOR, false);
                }
            }

            patternBtn.text = lane.getMovementPattern().name();
            patternBtn.render(context, mouseX, mouseY, tr);

            gaussianBtn.render(context, mouseX, mouseY, tr);
            int gaussLabelX = cordsMode ? x + 338 : x + 262;
            context.text(tr, "§7G", gaussLabelX, y + 5, TEXT_DIM, false);

            deleteBtn.render(context, mouseX, mouseY, tr);
        }

        boolean handleClick(int mouseX, int mouseY) {
            if (patternBtn.isMouseOver(mouseX, mouseY)) {
                patternBtn.onClick();
                return true;
            }
            if (gaussianBtn.isMouseOver(mouseX, mouseY)) {
                gaussianBtn.onClick();
                return true;
            }
            if (deleteBtn.isMouseOver(mouseX, mouseY)) {
                deleteBtn.onClick();
                return true;
            }
            return false;
        }

        void applyToLane() {
            lane.setName(nameField.getValue());
            lane.setGaussianMovement(gaussianBtn.text.contains("ON"));

            boolean cordsMode = group.getMode() == GroupMode.CORDS;
            if (!cordsMode && durationField != null) {
                try {
                    float dur = Float.parseFloat(durationField.getValue());
                    lane.setDuration(dur);
                } catch (NumberFormatException ignored) {}
            } else if (cordsMode) {
                try {
                    double ex = Double.parseDouble(endXField != null ? endXField.getValue() : "");
                    double ey = Double.parseDouble(endYField != null ? endYField.getValue() : "");
                    double ez = Double.parseDouble(endZField != null ? endZField.getValue() : "");
                    lane.setEndCoords(ex, ey, ez);
                } catch (NumberFormatException ignored) {}
            }
        }
    }
}
