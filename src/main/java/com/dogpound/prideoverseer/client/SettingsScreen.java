package com.dogpound.prideoverseer.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Every Overseer option in the Pride style: tabs down the left, switches and sliders on the right. */
public class SettingsScreen extends GuiScreen {
    private final GuiScreen parent;
    private static int tab;
    private int scroll, contentH, oy, px, pw, top, bottom;
    private final List<Object[]> hits = new ArrayList<Object[]>();
    private String hint = "";
    private static final String[][] TABS = {
            { "Camera", "how the view moves" }, { "In the world", "rings, names, bars, lines" }, { "Who shows", "which kinds of units" },
            { "Panels", "bars, lists, minimap" }, { "Controls", "clicks, keys, orders" }, { "Look & sound", "colours, sounds" } };

    public SettingsScreen(GuiScreen parent) { this.parent = parent; }
    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear(); hint = "";
        PrideFrame.sync();
        PrideFrame f = PrideFrame.fit(width, height);
        f.draw(this, "Pride Overseer — Options", "§7saved as you go");
        int tw = Math.min(130, Math.max(96, f.cw / 6)), ty = f.cy;
        for (int i = 0; i < TABS.length; i++) {
            boolean on = i == tab, over = mx >= f.cx && mx < f.cx + tw && my >= ty && my < ty + 26;
            PrideFrame.tile(f.cx, ty, tw, 26, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length], over, on);
            fontRenderer.drawStringWithShadow(TABS[i][0], f.cx + 6, ty + 4, on ? 0xFFFFFF : 0xD8D0E8);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(TABS[i][1], tw - 10), f.cx + 6, ty + 15, 0x8A8499);
            final int t = i;
            hits.add(new Object[]{ f.cx, ty, tw, 26, (Runnable) () -> { tab = t; scroll = 0; } });
            ty += 29;
        }
        px = f.cx + tw + 10; pw = f.cw - tw - 16; top = f.cy; bottom = f.cy + f.ch - 30;
        PrideFrame.clip(px - 2, top, pw + 4, bottom - top);
        oy = top - scroll;
        Settings S = Settings.get();
        switch (tab) {
            case 0:
                slider(mx, my, "Starting tilt (degrees)", 15, 89, () -> S.startPitch, v -> S.startPitch = (float) v);
                slider(mx, my, "Starting distance", 6, 200, () -> S.startDistance, v -> S.startDistance = (float) v);
                slider(mx, my, "Closest zoom", 2, 40, () -> S.minDistance, v -> S.minDistance = (float) v);
                slider(mx, my, "Farthest zoom", 40, 400, () -> S.maxDistance, v -> S.maxDistance = (float) v);
                slider(mx, my, "Camera field of view", 30, 110, () -> S.fov, v -> S.fov = (float) v);
                slider(mx, my, "Move speed", 0.2, 4, () -> S.panSpeed, v -> S.panSpeed = (float) v);
                slider(mx, my, "Turn speed", 0.2, 4, () -> S.rotateSpeed, v -> S.rotateSpeed = (float) v);
                slider(mx, my, "Zoom speed", 0.2, 4, () -> S.zoomSpeed, v -> S.zoomSpeed = (float) v);
                tog(mx, my, "Smooth camera (glides instead of jumping)", () -> S.smoothCamera, () -> S.smoothCamera = !S.smoothCamera);
                tog(mx, my, "Move the camera with the mouse at the screen edge", () -> S.edgePan, () -> S.edgePan = !S.edgePan);
                slider(mx, my, "Screen-edge margin (pixels)", 1, 40, () -> S.edgePanMargin, v -> S.edgePanMargin = (int) v);
                tog(mx, my, "Camera follows the selected units", () -> S.followSelected, () -> S.followSelected = !S.followSelected);
                tog(mx, my, "Camera keeps to the height of the land", () -> S.keepHeight, () -> S.keepHeight = !S.keepHeight);
                tog(mx, my, "Invert mouse drag", () -> S.invertDrag, () -> S.invertDrag = !S.invertDrag);
                tog(mx, my, "Invert the zoom wheel", () -> S.invertZoom, () -> S.invertZoom = !S.invertZoom);
                break;
            case 1:
                tog(mx, my, "Rings under units", () -> S.rings, () -> S.rings = !S.rings);
                cycle(mx, my, "Ring style", new String[]{ "Glow", "Thin", "Square" }, () -> S.ringStyle, v -> S.ringStyle = v);
                tog(mx, my, "Health bars", () -> S.healthBars, () -> S.healthBars = !S.healthBars);
                tog(mx, my, "Names over units", () -> S.names, () -> S.names = !S.names);
                tog(mx, my, "Names only on selected / hovered units", () -> S.nameOnlyHover, () -> S.nameOnlyHover = !S.nameOnlyHover);
                tog(mx, my, "Little icons before names", () -> S.icons, () -> S.icons = !S.icons);
                slider(mx, my, "Name size", 0.5, 2, () -> S.labelScale, v -> S.labelScale = (float) v);
                tog(mx, my, "Lines to where selected units are going", () -> S.orderLines, () -> S.orderLines = !S.orderLines);
                tog(mx, my, "Flags on their destinations", () -> S.orderFlags, () -> S.orderFlags = !S.orderFlags);
                tog(mx, my, "Village borders", () -> S.villageBorders, () -> S.villageBorders = !S.villageBorders);
                tog(mx, my, "Village names", () -> S.villageNames, () -> S.villageNames = !S.villageNames);
                tog(mx, my, "Rally flag beams", () -> S.rallyFlags, () -> S.rallyFlags = !S.rallyFlags);
                tog(mx, my, "Arrows to selected units off screen", () -> S.offscreenArrows, () -> S.offscreenArrows = !S.offscreenArrows);
                break;
            case 2:
                tog(mx, my, "☺ Villagers", () -> S.showVillagers, () -> S.showVillagers = !S.showVillagers);
                tog(mx, my, "⛨ Guards", () -> S.showGuards, () -> S.showGuards = !S.showGuards);
                tog(mx, my, "⬛ Golems", () -> S.showGolems, () -> S.showGolems = !S.showGolems);
                tog(mx, my, "♥ Pets", () -> S.showPets, () -> S.showPets = !S.showPets);
                tog(mx, my, "♞ Mounts", () -> S.showMounts, () -> S.showMounts = !S.showMounts);
                tog(mx, my, "⚔ Soldiers (Ancient Warfare...)", () -> S.showSoldiers, () -> S.showSoldiers = !S.showSoldiers);
                tog(mx, my, "⚒ Workers (Ancient Warfare...)", () -> S.showWorkers, () -> S.showWorkers = !S.showWorkers);
                tog(mx, my, "✦ Mod NPCs (MCA, Custom NPCs, Millénaire, ToroQuest)", () -> S.showNpcs, () -> S.showNpcs = !S.showNpcs);
                tog(mx, my, "❀ Named / owned animals", () -> S.showAnimals, () -> S.showAnimals = !S.showAnimals);
                tog(mx, my, "☠ Hostile mobs (creative / if the server allows)", () -> S.showHostiles, () -> S.showHostiles = !S.showHostiles);
                break;
            case 3:
                tog(mx, my, "Top bar", () -> S.topBar, () -> S.topBar = !S.topBar);
                tog(mx, my, "Places / units list (left)", () -> S.villagePanel, () -> S.villagePanel = !S.villagePanel);
                tog(mx, my, "Unit card (right)", () -> S.unitCard, () -> S.unitCard = !S.unitCard);
                tog(mx, my, "Command bar (bottom)", () -> S.commandBar, () -> S.commandBar = !S.commandBar);
                tog(mx, my, "Minimap", () -> S.minimap, () -> S.minimap = !S.minimap);
                slider(mx, my, "Minimap size", 80, 260, () -> S.minimapSize, v -> S.minimapSize = (int) v);
                slider(mx, my, "Minimap blocks per pixel", 1, 8, () -> S.minimapZoom, v -> S.minimapZoom = (int) v);
                tog(mx, my, "Messages over the command bar", () -> S.messageLog, () -> S.messageLog = !S.messageLog);
                tog(mx, my, "Hints when you hover a button", () -> S.hints, () -> S.hints = !S.hints);
                slider(mx, my, "Panel see-through % (0 = clear)", 10, 100, () -> S.panelOpacity, v -> S.panelOpacity = (int) v);
                tog(mx, my, "Hide your normal HUD while overseeing", () -> S.hideHudOnEnter, () -> S.hideHudOnEnter = !S.hideHudOnEnter);
                tog(mx, my, "Show your own body down in the world", () -> S.showBody, () -> S.showBody = !S.showBody);
                break;
            case 4:
                tog(mx, my, "Right-click the ground moves the selection", () -> S.rightClickMoves, () -> S.rightClickMoves = !S.rightClickMoves);
                tog(mx, my, "Double-click selects everyone of that kind", () -> S.doubleClickSelectsType, () -> S.doubleClickSelectsType = !S.doubleClickSelectsType);
                tog(mx, my, "Control groups (Ctrl+1-9 / 1-9)", () -> S.ctrlGroups, () -> S.ctrlGroups = !S.ctrlGroups);
                cycle(mx, my, "Formation", new String[]{ "box", "line", "column", "circle", "wedge", "loose" },
                        () -> java.util.Arrays.asList("box", "line", "column", "circle", "wedge", "loose").indexOf(S.formation),
                        v -> S.formation = new String[]{ "box", "line", "column", "circle", "wedge", "loose" }[v]);
                slider(mx, my, "Guard area radius", 3, 40, () -> S.guardRadius, v -> S.guardRadius = (int) v);
                slider(mx, my, "Roam area radius", 3, 48, () -> S.wanderRadius, v -> S.wanderRadius = (int) v);
                tog(mx, my, "Select everyone when you open the view", () -> S.autoSelectOnEnter, () -> S.autoSelectOnEnter = !S.autoSelectOnEnter);
                tog(mx, my, "Pause the game while overseeing (single player)", () -> S.pauseInSingleplayer, () -> S.pauseInSingleplayer = !S.pauseInSingleplayer);
                break;
            default:
                tog(mx, my, "Pride colours (selected rings shimmer through the rainbow)", () -> S.pride, () -> S.pride = !S.pride);
                cycle(mx, my, "Accent colour", new String[]{ "Pink", "Blue", "Purple", "Gold", "Green" },
                        () -> java.util.Arrays.asList(0xFFF5A9B8, 0xFF5BCEFA, 0xFFB59CFF, 0xFFFFD23A, 0xFF8CE06A).indexOf(S.accent),
                        v -> S.accent = new int[]{ 0xFFF5A9B8, 0xFF5BCEFA, 0xFFB59CFF, 0xFFFFD23A, 0xFF8CE06A }[v]);
                tog(mx, my, "Sounds (clicks, units answering)", () -> S.clickSound, () -> S.clickSound = !S.clickSound);
                oy += 8;
                button(mx, my, "§c↺ Reset every Overseer option", () -> { Settings.reset(); });
                break;
        }
        contentH = oy + scroll - top;
        PrideFrame.unclip();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - (bottom - top))));
        PrideFrame.scrollbar(px + pw + 2, top, bottom - top, scroll, bottom - top, contentH);
        int by = f.cy + f.ch - 22;
        PrideFrame.button(f.cx + f.cw - 80, by, 80, 18, "✔ Done", PrideFrame.BUTTON, mx, my);
        hits.add(new Object[]{ f.cx + f.cw - 80, by, 80, 18, (Runnable) () -> mc.displayGuiScreen(parent) });
        if (!hint.isEmpty()) fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(hint, f.cw - 100), f.cx, by + 5, 0xC8C0DC);
        super.drawScreen(mx, my, pt);
    }

    private boolean visible(int y, int h) { return y + h > top && y < bottom; }

    private void tog(int mx, int my, String label, BooleanSupplier v, Runnable r) {
        if (visible(oy, 16)) {
            boolean on = v.getAsBoolean(), over = mx >= px && mx < px + pw && my >= oy && my < oy + 16 && my >= top && my < bottom;
            if (over) Gui.drawRect(px - 2, oy - 1, px + pw, oy + 15, 0x30FFFFFF);
            Gui.drawRect(px, oy + 4, px + 16, oy + 12, on ? 0xFF8CE06A : 0xFF3D2168);
            Gui.drawRect(on ? px + 9 : px + 1, oy + 5, on ? px + 15 : px + 7, oy + 11, 0xFFFFFFFF);
            fontRenderer.drawStringWithShadow(label, px + 22, oy + 4, on ? 0xFFFFFF : 0xA79FBF);
            hits.add(new Object[]{ px, Math.max(oy, top), pw, Math.min(16, bottom - oy), (Runnable) () -> { r.run(); Settings.get().save(); } });
        }
        oy += 18;
    }

    private void cycle(int mx, int my, String label, String[] opts, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
        if (visible(oy, 16)) {
            int cur = Math.max(0, get.getAsInt());
            fontRenderer.drawStringWithShadow("§7" + label, px, oy + 4, 0xFFFFFF);
            int x = px + 150;
            for (int i = 0; i < opts.length; i++) {
                int w = fontRenderer.getStringWidth(opts[i]) + 10;
                PrideFrame.button(x, oy, w, 15, (i == cur ? "§l" : "") + opts[i], i == cur ? PrideFrame.TILE_ON : PrideFrame.BUTTON, mx, my);
                final int k = i;
                hits.add(new Object[]{ x, oy, w, 15, (Runnable) () -> { set.accept(k); Settings.get().save(); } });
                x += w + 3;
            }
        }
        oy += 19;
    }

    private String dragKey; private DoubleConsumer dragSet; private double dragMin, dragMax; private int dragX, dragW;

    private void slider(int mx, int my, String label, double min, double max, DoubleSupplier get, DoubleConsumer set) {
        if (visible(oy, 16)) {
            double v = get.getAsDouble();
            int sx = px + 230, sw = Math.max(80, pw - 240);
            String val = max - min > 20 ? String.valueOf(Math.round(v)) : String.format("%.2f", v);
            fontRenderer.drawStringWithShadow("§f" + label + " §d" + val, px, oy + 4, 0xFFFFFF);
            Gui.drawRect(sx, oy + 7, sx + sw, oy + 9, 0xFF3D2168);
            int kx = sx + (int) ((v - min) / (max - min) * sw);
            Gui.drawRect(sx, oy + 7, kx, oy + 9, PrideFrame.PINK);
            Gui.drawRect(kx - 3, oy + 3, kx + 3, oy + 13, 0xFFFFFFFF);
            hits.add(new Object[]{ sx - 4, oy, sw + 8, 16, (Runnable) () -> { dragKey = label; dragSet = set; dragMin = min; dragMax = max; dragX = sx; dragW = sw; drag(Mouse.getX() * width / mc.displayWidth); } });
        }
        oy += 18;
    }

    private void drag(int mx) {
        if (dragSet == null) return;
        double k = Math.max(0, Math.min(1, (mx - dragX) / (double) dragW));
        dragSet.accept(dragMin + (dragMax - dragMin) * k);
    }

    private void button(int mx, int my, String label, Runnable r) {
        if (visible(oy, 18)) {
            PrideFrame.button(px, oy, 220, 18, label, PrideFrame.BUTTON, mx, my);
            hits.add(new Object[]{ px, oy, 220, 18, r });
        }
        oy += 22;
    }

    @Override
    protected void mouseClicked(int mx, int my, int b) throws IOException {
        if (b != 0) return;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            if (mx >= (Integer) h[0] && my >= (Integer) h[1] && mx < (Integer) h[0] + (Integer) h[2] && my < (Integer) h[1] + (Integer) h[3]) { ((Runnable) h[4]).run(); return; }
        }
    }

    @Override protected void mouseClickMove(int mx, int my, int b, long t) { if (dragKey != null) drag(mx); }
    @Override protected void mouseReleased(int mx, int my, int s) { if (dragKey != null) { dragKey = null; dragSet = null; Settings.get().save(); } }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int w = Mouse.getEventDWheel();
        if (w != 0) scroll += w > 0 ? -30 : 30;
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException { if (key == Keyboard.KEY_ESCAPE) { Settings.get().save(); mc.displayGuiScreen(parent); } }
}
