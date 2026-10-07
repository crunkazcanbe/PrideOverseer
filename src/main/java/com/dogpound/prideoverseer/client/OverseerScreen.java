package com.dogpound.prideoverseer.client;

import com.dogpound.prideoverseer.net.OverseerNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The overseer view's screen: everything you click while floating above the world. Top bar, village list (left), unit
 * card (right), command bar + formations (bottom), minimap, message log, floating names and health bars, box select,
 * control groups 1-9, and keys for everything (? shows them).
 */
public class OverseerScreen extends GuiScreen {
    static ClientData.Unit hoverUnit;
    static RayTraceResult hoverBlock;
    static BlockPos pendingFrom;                 // first click of a two-click order (patrol)
    static String mode;                          // null, or a command waiting for a ground click: move guard patrol wander tp
    private static boolean uiHidden, help;
    private static int leftTab;                  // 0 villages, 1 units
    private static int leftScroll;

    private final List<Object[]> hits = new ArrayList<Object[]>();
    private int downX = -1, downY = -1, downButton = -1, lastX, lastY;
    private boolean dragging, rotating;
    private long lastClick; private int lastClickId = -1;
    private GuiTextField renameField;
    private String tip = "";

    @Override public boolean doesGuiPauseGame() { return Settings.get().pauseInSingleplayer; }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        PrideFrame.sync();
    }

    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    // ------------------------------------------------------------------ helpers

    private int wx(int guiX) { return guiX * mc.displayWidth / width; }
    private int wy(int guiY) { return guiY * mc.displayHeight / height; }
    private double gx(double winX) { return winX * width / mc.displayWidth; }
    private double gy(double winY) { return winY * height / mc.displayHeight; }

    private int panelBg() { return ((int) (255 * Settings.get().panelOpacity / 100F) << 24) | 0x140E22; }

    private boolean button(int mx, int my, int x, int y, int w, int h, String label, int color, String tipText, Runnable r) {
        boolean over = PrideFrame.button(x, y, w, h, label, color, mx, my);
        hits.add(new Object[]{ x, y, w, h, r });
        if (over && tipText != null) tip = tipText;
        return over;
    }

    private void panel(int x, int y, int w, int h, int edge) {
        Gui.drawRect(x, y, x + w, y + h, panelBg());
        Gui.drawRect(x, y, x + w, y + 1, edge);
        Gui.drawRect(x, y + h - 1, x + w, y + h, 0x40000000);
    }

    static void send(String op, NBTTagCompound t) { OverseerNet.NET.sendToServer(new OverseerNet.Cmd(op, t)); }

    private void order(String type, BlockPos pos, BlockPos pos2) {
        if (Selection.IDS.isEmpty()) { ClientData.log("§7Select some units first (click, or drag a box)."); Sounds.play(Sounds.DENY); return; }
        Settings S = Settings.get();
        NBTTagCompound t = new NBTTagCompound();
        t.setString("type", type);
        t.setIntArray("ids", Selection.array());
        if (pos != null) { t.setLong("pos", pos.toLong()); ClientData.ping(pos); }
        if (pos2 != null) { t.setLong("pos2", pos2.toLong()); ClientData.ping(pos2); }
        t.setString("formation", S.formation);
        t.setFloat("facing", OverseerMode.yaw);
        t.setInteger("radius", type.equals("GUARD") ? S.guardRadius : S.wanderRadius);
        send("order", t);
        List<ClientData.Unit> l = Selection.units();
        Sounds.answer(l.isEmpty() ? "" : l.get(0).kind);
    }

    private void act(String action, NBTTagCompound extra) {
        if (Selection.IDS.isEmpty()) { ClientData.log("§7Select some units first."); Sounds.play(Sounds.DENY); return; }
        NBTTagCompound t = extra == null ? new NBTTagCompound() : extra;
        t.setString("action", action);
        t.setIntArray("ids", Selection.array());
        send("act", t);
        Sounds.play(Sounds.ORDER);
    }

    private void talk(ClientData.Unit u) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("id", u.id);
        send("talk", t);
        Sounds.play(Sounds.SELECT);
    }

    private BlockPos groundUnderMouse(int mx, int my) {
        RayTraceResult r = OverseerMode.pickBlock(wx(mx), wy(my));
        return r != null && r.typeOfHit == RayTraceResult.Type.BLOCK ? r.getBlockPos().up() : null;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear();
        tip = "";
        Settings S = Settings.get();
        PrideFrame.sync();
        hoverUnit = inUi(mx, my) ? null : OverseerMode.pickUnit(wx(mx), wy(my));
        hoverBlock = inUi(mx, my) ? null : OverseerMode.pickBlock(wx(mx), wy(my));
        edgePan(mx, my);
        keysHeld();

        drawLabels(pt);
        if (dragging && downButton == 0) {
            int x0 = Math.min(downX, mx), y0 = Math.min(downY, my), x1 = Math.max(downX, mx), y1 = Math.max(downY, my);
            Gui.drawRect(x0, y0, x1, y1, 0x30F5A9B8);
            Gui.drawRect(x0, y0, x1, y0 + 1, PrideFrame.PINK); Gui.drawRect(x0, y1 - 1, x1, y1, PrideFrame.PINK);
            Gui.drawRect(x0, y0, x0 + 1, y1, PrideFrame.PINK); Gui.drawRect(x1 - 1, y0, x1, y1, PrideFrame.PINK);
        }
        if (!uiHidden) {
            if (S.topBar) drawTop(mx, my);
            if (S.villagePanel) drawLeft(mx, my);
            if (ClientData.village != null) drawVillage(mx, my);
            if (S.unitCard) drawCard(mx, my);
            if (S.commandBar) drawCommands(mx, my);
            if (S.minimap) Minimap.draw(this, mx, my, hits);
            if (S.messageLog) drawLog();
        }
        if (mode != null) {
            String m = "§e✦ Click the ground to " + modeText() + "  §7(right-click or Esc cancels)";
            drawCenteredString(fontRenderer, m, width / 2, 30, 0xFFFFFF);
        }
        if (renameField != null) {
            int bw = 220, bx = width / 2 - bw / 2, by = height / 2 - 30;
            panel(bx - 6, by - 20, bw + 12, 52, PrideFrame.PINK);
            fontRenderer.drawStringWithShadow(renameVillage ? "§dName this village §7(Enter = save, empty = its own name)" : "§dRename " + Selection.IDS.size() + " unit" + (Selection.IDS.size() == 1 ? "" : "s") + " §7(Enter = save, empty = remove name)", bx, by - 14, 0xFFFFFF);
            renameField.drawTextBox();
        }
        if (help) drawHelp();
        if (!tip.isEmpty() && S.hints) {
            int tw = fontRenderer.getStringWidth(tip) + 8;
            int tx = Math.min(mx + 10, width - tw - 2), ty = my - 14;
            Gui.drawRect(tx, ty, tx + tw, ty + 12, 0xE0140E22);
            fontRenderer.drawStringWithShadow(tip, tx + 4, ty + 2, 0xFFFFFF);
        }
        super.drawScreen(mx, my, pt);
    }

    private String modeText() {
        switch (mode) {
            case "guard": return "set the guard spot";
            case "patrol": return pendingFrom == null ? "set the FIRST patrol point" : "set the SECOND patrol point";
            case "wander": return "pick where they roam";
            case "tp": return "teleport them there";
            case "move": return "send them there";
            default: return mode;
        }
    }

    /** names, health bars, order text over every unit (2D, always readable) + arrows to selected units off screen */
    private void drawLabels(float pt) {
        Settings S = Settings.get();
        double cx = width / 2.0, cy = height / 2.0;
        for (ClientData.Unit u : ClientData.UNITS.values()) {
            if (!S.shows(u.kind)) continue;
            boolean sel = Selection.has(u.id), hov = hoverUnit != null && hoverUnit.id == u.id;
            double[] wp = OverseerMode.unitPos(u, pt);
            double[] s = OverseerMode.project(wp[0], wp[1] + wp[3] + 0.4, wp[2]);
            if (s == null || s[0] < 0 || s[1] < 0 || s[0] > mc.displayWidth || s[1] > mc.displayHeight) {
                if (sel && S.offscreenArrows && s != null) {
                    double x = gx(s[0]) - cx, y = gy(s[1]) - cy, k = Math.min((cx - 14) / Math.max(1e-3, Math.abs(x)), (cy - 14) / Math.max(1e-3, Math.abs(y)));
                    int ax = (int) (cx + x * Math.min(1, k)), ay = (int) (cy + y * Math.min(1, k));
                    drawCenteredString(fontRenderer, "§d➤", ax, ay - 4, 0xFFFFFF);
                }
                continue;
            }
            double sx = gx(s[0]), sy = gy(s[1]);
            float scale = (float) MathHelper.clamp(28.0 / s[2], 0.5, 1.0) * S.labelScale;
            GlStateManager.pushMatrix();
            GlStateManager.translate(sx, sy, 0);
            GlStateManager.scale(scale, scale, 1);
            int y = 0;
            if (S.healthBars && (sel || hov || u.hp < u.max) && u.max > 0) {
                int w = 30, f = (int) (w * MathHelper.clamp(u.hp / u.max, 0, 1));
                Gui.drawRect(-w / 2 - 1, y - 1, w / 2 + 1, y + 4, 0xC0000000);
                int c = u.hp / u.max > 0.5 ? 0xFF8CE06A : u.hp / u.max > 0.25 ? 0xFFFFD23A : 0xFFE02040;
                Gui.drawRect(-w / 2, y, -w / 2 + f, y + 3, c);
                y -= 10;
            }
            if (S.names && (!S.nameOnlyHover || sel || hov)) {
                String n = (S.icons ? icon(u.kind) + " " : "") + (sel ? "§d" : hov ? "§f" : "§7") + u.name + (u.order.isEmpty() ? "" : " §8· §a" + orderName(u.order));
                int w = fontRenderer.getStringWidth(n);
                Gui.drawRect(-w / 2 - 2, y - 2, w / 2 + 2, y + 8, sel ? 0xA0402060 : 0x80000000);
                fontRenderer.drawStringWithShadow(n, -w / 2F, y - 1, 0xFFFFFF);
            }
            GlStateManager.popMatrix();
        }
        if (Settings.get().villageNames) for (ClientData.Village v : ClientData.VILLAGES) {
            double gyv = OverseerMode.groundY(v.pos.getX() + 0.5, v.pos.getZ() + 0.5, v.pos.getY());
            double[] s = OverseerMode.project(v.pos.getX() + 0.5, gyv + 5, v.pos.getZ() + 0.5);
            if (s == null) continue;
            String n = "§d⚑ §f§l" + v.name + " §r§7" + (v.villagers > 0 ? v.villagers + " people" : "");
            drawCenteredString(fontRenderer, n, (int) gx(s[0]), (int) gy(s[1]), 0xFFFFFF);
        }
    }

    static String icon(String kind) {
        switch (kind) {
            case "VILLAGER": return "§b☺"; case "GUARD": return "§e⛨"; case "GOLEM": return "§7⬛"; case "PET": return "§d♥"; case "MOUNT": return "§6♞";
            case "SOLDIER": return "§c⚔"; case "WORKER": return "§a⚒"; case "NPC": return "§5✦"; case "HOSTILE": return "§4☠"; default: return "§a❀";
        }
    }

    static String orderName(String o) {
        switch (o) {
            case "MOVE": return "moving"; case "HOLD": return "holding"; case "FOLLOW": return "following"; case "GUARD": return "guarding";
            case "PATROL": return "patrolling"; case "WANDER": return "roaming"; case "HOME": return "going home"; case "COME": return "coming";
            default: return o.toLowerCase(Locale.ROOT);
        }
    }

    private boolean inUi(int mx, int my) {
        if (uiHidden) return false;
        for (Object[] h : hits) if (mx >= (Integer) h[0] && my >= (Integer) h[1] && mx < (Integer) h[0] + (Integer) h[2] && my < (Integer) h[1] + (Integer) h[3]) return true;
        Settings S = Settings.get();
        if (S.topBar && my < 18) return true;
        if (S.commandBar && my > height - 58) return true;
        if (S.villagePanel && mx < 150 && my > 22 && my < height - 62) return true;
        if (ClientData.village != null) { int vx = S.villagePanel ? 152 : 4; if (mx >= vx && mx < vx + 236 && my >= 22 && my < 22 + 170 + ClientData.village.getCompoundTag("jobs").getSize() * 6) return true; }
        if (S.unitCard && mx > width - 172 && my > 22 && my < 240 && !Selection.IDS.isEmpty()) return true;
        return false;
    }

    private void drawTop(int mx, int my) {
        panel(0, 0, width, 18, PrideFrame.PINK);
        int n = PrideFrame.RAINBOW.length, sw = Math.max(1, width / n);
        for (int i = 0; i < n; i++) Gui.drawRect(i * sw, 0, i == n - 1 ? width : (i + 1) * sw, 2, PrideFrame.RAINBOW[i]);
        long time = mc.world.getWorldTime() % 24000L;
        int h = (int) ((time / 1000 + 6) % 24), m = (int) ((time % 1000) * 60 / 1000);
        String left = "§d§l✦ Pride Overseer  §r§7" + ClientData.UNITS.size() + " units · §f" + Selection.IDS.size() + " §7selected · " + ClientData.VILLAGES.size() + " settlements · "
                + String.format(Locale.ROOT, "%s %02d:%02d", h < 6 || h >= 19 ? "☾" : "☀", h, m);
        fontRenderer.drawStringWithShadow(left, 4, 6, 0xFFFFFF);
        int bx = width - 4;
        bx -= 44; button(mx, my, bx, 3, 42, 13, "✕ Exit", 0xFF5A2238, "Back to your body (Esc / O)", OverseerMode::exit);
        bx -= 50; button(mx, my, bx, 3, 48, 13, "⚙ Options", PrideFrame.BUTTON, "Every Overseer setting", () -> mc.displayGuiScreen(new SettingsScreen(this)));
        bx -= 34; button(mx, my, bx, 3, 32, 13, "? Keys", PrideFrame.BUTTON, "All the keys (?)", () -> help = !help);
        bx -= 50; button(mx, my, bx, 3, 48, 13, "⌂ My body", PrideFrame.BUTTON, "Fly back over yourself (Home)", () -> OverseerMode.flyTo(mc.player.posX, mc.player.posY, mc.player.posZ));
        bx -= 52; button(mx, my, bx, 3, 50, 13, "⟳ Refresh", PrideFrame.BUTTON, "Ask for fresh village info", () -> send("villages", null));
    }

    private void drawLeft(int mx, int my) {
        int x = 2, y = 22, w = 146, h = height - 22 - 62;
        panel(x, y, w, h, PrideFrame.BLUE);
        button(mx, my, x + 2, y + 3, 46, 13, leftTab == 0 ? "§l⚑ Places" : "⚑ Places", leftTab == 0 ? PrideFrame.TILE_ON : PrideFrame.BUTTON, "Villages and NPC settlements", () -> { leftTab = 0; leftScroll = 0; });
        button(mx, my, x + 50, y + 3, 46, 13, leftTab == 1 ? "§l☺ Units" : "☺ Units", leftTab == 1 ? PrideFrame.TILE_ON : PrideFrame.BUTTON, "Everyone you can command", () -> { leftTab = 1; leftScroll = 0; });
        boolean fresh = !ClientData.NEWS.isEmpty() && System.currentTimeMillis() - Long.parseLong(ClientData.NEWS.get(ClientData.NEWS.size() - 1)[1]) < 10000;
        button(mx, my, x + 98, y + 3, 46, 13, (leftTab == 2 ? "§l" : "") + (fresh && leftTab != 2 ? "§e" : "") + "✉ News", leftTab == 2 ? PrideFrame.TILE_ON : PrideFrame.BUTTON, "What's happening in your villages", () -> { leftTab = 2; leftScroll = 0; });
        int top = y + 20, bottom = y + h - 2, row = leftTab == 0 ? 30 : leftTab == 2 ? 22 : 13;
        PrideFrame.clip(x, top, w, bottom - top);
        int ry = top - leftScroll;
        if (leftTab == 0) {
            List<ClientData.Village> vs = new ArrayList<ClientData.Village>(ClientData.VILLAGES);
            vs.sort(Comparator.comparingDouble(v -> v.pos.distanceSq(mc.player.getPosition())));
            if (vs.isEmpty()) fontRenderer.drawSplitString("§7No villages near you yet. Villages show up when their doors and villagers are loaded.", x + 4, ry + 2, w - 8, 0xFFFFFF);
            for (ClientData.Village v : vs) {
                boolean over = mx >= x && mx < x + w && my >= ry && my < ry + row && my >= top && my < bottom;
                if (over) Gui.drawRect(x + 1, ry, x + w - 1, ry + row, 0x30FFFFFF);
                int dist = (int) Math.sqrt(v.pos.distanceSq(mc.player.getPosition()));
                fontRenderer.drawStringWithShadow("§d⚑ §f" + v.name, x + 4, ry + 3, 0xFFFFFF);
                fontRenderer.drawStringWithShadow("§8" + dist + "m", x + w - 6 - fontRenderer.getStringWidth(dist + "m"), ry + 3, 0xFFFFFF);
                String info = "village".equals(v.kind) ? "§7☺" + v.villagers + " §7⌂" + v.doors + " §7⬛" + v.golems + (v.rep != 0 ? (v.rep > 0 ? " §a+" : " §c") + v.rep : "")
                        : "§5" + v.kind + " §7· " + v.villagers + " NPCs";
                fontRenderer.drawStringWithShadow(info, x + 12, ry + 15, 0xFFFFFF);
                final ClientData.Village vv = v;
                hits.add(new Object[]{ x, Math.max(ry, top), w, Math.max(0, Math.min(ry + row, bottom) - Math.max(ry, top)), (Runnable) () -> {
                    OverseerMode.flyTo(vv.pos.getX() + 0.5, OverseerMode.groundY(vv.pos.getX() + 0.5, vv.pos.getZ() + 0.5, vv.pos.getY()), vv.pos.getZ() + 0.5);
                    openVillage(vv.pos);
                    if (GuiScreen.isShiftKeyDown()) for (ClientData.Unit u : ClientData.UNITS.values())
                        if (Math.hypot(u.x - vv.pos.getX(), u.z - vv.pos.getZ()) < vv.radius + 4) Selection.add(u.id);
                    Sounds.play(Sounds.SELECT);
                } });
                ry += row;
            }
        } else if (leftTab == 2) {
            if (ClientData.NEWS.isEmpty()) fontRenderer.drawSplitString("§7Nothing yet. Births, deaths, festivals and monsters at the gates show up here.", x + 4, ry + 2, w - 8, 0xFFFFFF);
            long nowMs = System.currentTimeMillis();
            for (int i = ClientData.NEWS.size() - 1; i >= 0; i--) {
                String[] n = ClientData.NEWS.get(i);
                boolean over = mx >= x && mx < x + w && my >= ry && my < ry + row && my >= top && my < bottom;
                if (over) Gui.drawRect(x + 1, ry, x + w - 1, ry + row, 0x30FFFFFF);
                long ago = (nowMs - Long.parseLong(n[1])) / 1000;
                String when = ago < 60 ? ago + "s" : ago / 60 + "m";
                List<String> lines = fontRenderer.listFormattedStringToWidth(n[0], w - 10);
                if (!lines.isEmpty()) fontRenderer.drawStringWithShadow(lines.get(0), x + 4, ry + 2, 0xFFFFFF);
                if (lines.size() > 1) fontRenderer.drawStringWithShadow("§7" + lines.get(1), x + 4, ry + 11, 0xFFFFFF);
                fontRenderer.drawString("§8" + when, x + w - 6 - fontRenderer.getStringWidth(when), ry + 11, 0xFFFFFF);
                final BlockPos at = BlockPos.fromLong(Long.parseLong(n[2]));
                hits.add(new Object[]{ x, Math.max(ry, top), w, Math.max(0, Math.min(ry + row, bottom) - Math.max(ry, top)), (Runnable) () -> {
                    OverseerMode.flyTo(at.getX() + 0.5, OverseerMode.groundY(at.getX() + 0.5, at.getZ() + 0.5, at.getY()), at.getZ() + 0.5);
                    Sounds.play(Sounds.SELECT);
                } });
                ry += row;
            }
        } else {
            List<ClientData.Unit> us = new ArrayList<ClientData.Unit>(ClientData.UNITS.values());
            us.removeIf(u -> !Settings.get().shows(u.kind));
            us.sort(Comparator.comparingDouble(u -> (u.x - OverseerMode.px) * (u.x - OverseerMode.px) + (u.z - OverseerMode.pz) * (u.z - OverseerMode.pz)));
            for (ClientData.Unit u : us) {
                boolean sel = Selection.has(u.id);
                boolean over = mx >= x && mx < x + w && my >= ry && my < ry + row && my >= top && my < bottom;
                if (sel || over) Gui.drawRect(x + 1, ry, x + w - 1, ry + row, sel ? 0x40F5A9B8 : 0x30FFFFFF);
                fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(icon(u.kind) + " §f" + u.name, w - 8), x + 4, ry + 2, 0xFFFFFF);
                final ClientData.Unit uu = u;
                hits.add(new Object[]{ x, Math.max(ry, top), w, Math.max(0, Math.min(ry + row, bottom) - Math.max(ry, top)), (Runnable) () -> {
                    if (GuiScreen.isShiftKeyDown()) Selection.toggle(uu.id); else Selection.set(uu.id);
                    OverseerMode.flyTo(uu.x, uu.y, uu.z);
                    Sounds.play(Sounds.SELECT);
                } });
                ry += row;
            }
        }
        PrideFrame.unclip();
        int total = leftTab == 0 ? ClientData.VILLAGES.size() * 30 : leftTab == 2 ? ClientData.NEWS.size() * 22 : ClientData.UNITS.size() * 13;
        leftScroll = MathHelper.clamp(leftScroll, 0, Math.max(0, total - (bottom - top)));
        PrideFrame.scrollbar(x + w - 3, top, bottom - top, leftScroll, bottom - top, total);
    }

    private void drawCard(int mx, int my) {
        List<ClientData.Unit> sel = Selection.units();
        ClientData.Unit u = sel.size() == 1 ? sel.get(0) : sel.isEmpty() ? hoverUnit : null;
        int w = 168, x = width - w - 2, y = 22;
        if (u != null) {
            int h = 96;
            panel(x, y, w, h, OverseerRender.kindColor(u.kind) | 0xFF000000);
            fontRenderer.drawStringWithShadow(icon(u.kind) + " §f§l" + fontRenderer.trimStringToWidth(u.name, w - 30), x + 5, y + 5, 0xFFFFFF);
            fontRenderer.drawStringWithShadow("§7" + u.kind.toLowerCase(Locale.ROOT) + (u.job.isEmpty() ? "" : " · " + u.job) + (u.child ? " · child" : "") + " §8(" + u.mod + ")", x + 5, y + 17, 0xFFFFFF);
            if (u.max > 0) {
                int bw = w - 10, f = (int) (bw * MathHelper.clamp(u.hp / u.max, 0, 1));
                Gui.drawRect(x + 5, y + 29, x + 5 + bw, y + 35, 0xFF3A0A12);
                PrideFrame.gradient(x + 5, y + 29, x + 5 + f, y + 35, 0xFFFF5070, 0xFFB01030);
                fontRenderer.drawStringWithShadow("§f❤ " + Math.round(u.hp) + "§7/" + Math.round(u.max), x + 7, y + 38, 0xFFFFFF);
            }
            fontRenderer.drawStringWithShadow("§7Doing: §a" + (u.order.isEmpty() ? "§7its own thing" : orderName(u.order)), x + 5, y + 50, 0xFFFFFF);
            int dist = (int) Math.sqrt((u.x - mc.player.posX) * (u.x - mc.player.posX) + (u.z - mc.player.posZ) * (u.z - mc.player.posZ));
            fontRenderer.drawStringWithShadow("§7" + dist + "m from you" + (u.trading ? " · §etrading" : ""), x + 5, y + 62, 0xFFFFFF);
            button(mx, my, x + 5, y + 76, 52, 14, "💬 Talk", PrideFrame.TILE_ON, "Same as walking up and right-clicking (T)", () -> talk(u));
            button(mx, my, x + 60, y + 76, 52, 14, "◎ Look", PrideFrame.BUTTON, "Fly the camera to it (Space)", () -> OverseerMode.flyTo(u.x, u.y, u.z));
            button(mx, my, x + 115, y + 76, 48, 14, "✎ Name", PrideFrame.BUTTON, "Rename (Insert)", () -> { if (!Selection.has(u.id)) Selection.set(u.id); openRename(); });
            boolean fol = Settings.get().followSelected;
            button(mx, my, x + 5, y + 93, 158, 13, fol ? "§a◉ Camera follows (on)" : "◎ Camera follows selection", fol ? PrideFrame.TILE_ON : PrideFrame.BUTTON, "The camera stays on whoever is selected", () -> { Settings S2 = Settings.get(); S2.followSelected = !S2.followSelected; S2.save(); });
            if ("VILLAGER".equals(u.kind)) {
                if (!ClientData.TRADES.containsKey(u.id) && requested != u.id) { requested = u.id; NBTTagCompound t = new NBTTagCompound(); t.setInteger("id", u.id); send("trades", t); }
                List<String[]> tr = ClientData.TRADES.get(u.id);
                int ty = y + 112;
                if (tr != null) {
                    int th = 14 + Math.max(1, tr.size()) * 10;
                    panel(x, ty, w, th, 0xFFFFD23A);
                    fontRenderer.drawStringWithShadow("§e§lTrades", x + 5, ty + 3, 0xFFFFFF);
                    if (tr.isEmpty()) fontRenderer.drawStringWithShadow("§7nothing to sell yet", x + 8, ty + 14, 0xFFFFFF);
                    int ly = ty + 14;
                    for (String[] r : tr) {
                        String line = ("true".equals(r[2]) ? "§8§m" : "§f") + r[0] + " §7→ " + ("true".equals(r[2]) ? "§8§m" : "§a") + r[1];
                        drawScaledLine(fontRenderer.trimStringToWidth(line, (int) ((w - 10) / 0.8F)), x + 6, ly, 0.8F);
                        ly += 10;
                    }
                }
            }
        } else if (!sel.isEmpty()) {
            Map<String, Integer> kinds = new LinkedHashMap<String, Integer>();
            for (ClientData.Unit s : sel) kinds.merge(s.kind, 1, Integer::sum);
            int h = 20 + kinds.size() * 11;
            panel(x, y, w, h, PrideFrame.PINK);
            fontRenderer.drawStringWithShadow("§d§l" + sel.size() + " selected", x + 5, y + 5, 0xFFFFFF);
            int ry = y + 17;
            for (Map.Entry<String, Integer> k : kinds.entrySet()) {
                fontRenderer.drawStringWithShadow(icon(k.getKey()) + " §f" + k.getValue() + " §7" + k.getKey().toLowerCase(Locale.ROOT), x + 8, ry, 0xFFFFFF);
                ry += 11;
            }
        }
    }

    private int requested = -1;

    private void drawScaledLine(String s, int x, int y, float k) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(k, k, 1);
        fontRenderer.drawStringWithShadow(s, 0, 0, 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    static void openVillage(BlockPos at) {
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("pos", at.toLong());
        send("vdetail", t);
    }

    private void villageCmd(String what, String arg) {
        if (ClientData.village == null) return;
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("pos", ClientData.village.getLong("pos"));
        t.setString("what", what);
        if (arg != null) t.setString("arg", arg);
        send("vcmd", t);
        Sounds.play(Sounds.ORDER);
    }

    /** the village card: people by job, mood, reputation, safety, and whole-village commands */
    private void drawVillage(int mx, int my) {
        net.minecraft.nbt.NBTTagCompound v = ClientData.village;
        int x = Settings.get().villagePanel ? 152 : 4, y = 22, w = 236;
        net.minecraft.nbt.NBTTagCompound jobs = v.getCompoundTag("jobs");
        int rows = (jobs.getSize() + 1) / 2;
        int h = 116 + rows * 11;
        panel(x, y, w, h, PrideFrame.PINK);
        fontRenderer.drawStringWithShadow("§d⚑ §f§l" + v.getString("name"), x + 6, y + 5, 0xFFFFFF);
        button(mx, my, x + w - 16, y + 3, 13, 12, "✕", 0xFF5A2238, "Close the village card", () -> ClientData.village = null);
        fontRenderer.drawStringWithShadow("§7☺ §f" + v.getInteger("people") + " people §7(" + v.getInteger("kids") + " children)  §7⌂ §f" + v.getInteger("doors") + "  §7⬛ §f" + v.getInteger("golems"), x + 6, y + 18, 0xFFFFFF);
        // mood, your reputation, danger as bars
        int by = y + 31;
        meter(x + 6, by, w - 12, "Mood", v.getInteger("mood") / 100F, v.getInteger("mood") + "%", 0xFFFF7FAE, 0xFF8CE06A); by += 13;
        int rep = v.getInteger("rep");
        meter(x + 6, by, w - 12, "Likes you", Math.max(0, Math.min(1F, (rep + 30) / 60F)), (rep > 0 ? "+" : "") + rep, rep < 0 ? 0xFFE02040 : 0xFFFFD23A, rep < 0 ? 0xFFFF7A50 : 0xFF8CE06A); by += 13;
        int mon = v.getInteger("monsters");
        meter(x + 6, by, w - 12, "Danger", Math.min(1F, mon / 8F), mon == 0 ? "safe" : mon + " monsters", 0xFFFFD23A, 0xFFE02040); by += 15;
        // people by job, two columns
        int i = 0;
        for (String k : jobs.getKeySet()) {
            int cx = x + 8 + (i % 2) * (w / 2 - 4), cy = by + (i / 2) * 11;
            fontRenderer.drawStringWithShadow(jobIcon(k) + " §f" + jobs.getInteger(k) + " §7" + k, cx, cy, 0xFFFFFF);
            i++;
        }
        by += rows * 11 + 4;
        // whole-village commands
        String[][] b = { { "🔔 Gather", "gather", "Everyone to the square" }, { "☾ Curfew", "curfew", "Everyone indoors" },
                { "⚠ Alarm", "alarm", "Hide indoors, golems guard the square" }, { "🎉 Festival!", "festival", "Gather, fireworks, music and hearts" },
                { "✿ Normal life", "free", "Forget all orders here" }, { "✎ Rename", "rename", "Give this village a name" } };
        int bw = (w - 16) / 3;
        for (int k = 0; k < b.length; k++) {
            final String id = b[k][1];
            int bx = x + 6 + (k % 3) * (bw + 2), byy = by + (k / 3) * 17;
            button(mx, my, bx, byy, bw, 15, b[k][0], id.equals("festival") ? PrideFrame.TILE_ON : PrideFrame.TILE, b[k][2], () -> {
                if (id.equals("rename")) { renameVillage = true; openRenameField(v.getString("name")); } else villageCmd(id, null);
            });
        }
        button(mx, my, x + 6, by + 36, w - 12, 13, "☐ Select everyone who lives here", PrideFrame.BUTTON, "Then give them orders from the command bar", () -> {
            BlockPos c = BlockPos.fromLong(v.getLong("pos"));
            int r = v.getInteger("radius") + 8;
            for (ClientData.Unit u : ClientData.UNITS.values()) if (Math.hypot(u.x - c.getX(), u.z - c.getZ()) < r && !"GOLEM".equals(u.kind)) Selection.add(u.id);
            Sounds.play(Sounds.SELECT);
        });
    }

    private void meter(int x, int y, int w, String label, float k, String value, int from, int to) {
        fontRenderer.drawStringWithShadow("§7" + label, x, y + 1, 0xFFFFFF);
        int bx = x + 62, bw = w - 62 - fontRenderer.getStringWidth(value) - 6;
        Gui.drawRect(bx - 1, y + 1, bx + bw + 1, y + 9, 0x90000000);
        Gui.drawRect(bx, y + 2, bx + bw, y + 8, 0xFF201828);
        PrideFrame.gradient(bx, y + 2, bx + (int) (bw * k), y + 8, from, to);
        fontRenderer.drawStringWithShadow("§f" + value, x + w - fontRenderer.getStringWidth(value), y + 1, 0xFFFFFF);
    }

    static String jobIcon(String job) {
        switch (job) {
            case "farmer": return "§a✿"; case "librarian": return "§f✎"; case "priest": case "cleric": return "§d✚"; case "smith": case "blacksmith": return "§7⚒";
            case "butcher": return "§c✂"; case "nitwit": return "§e☻"; case "child": return "§b♥"; case "guard": return "§e⛨"; case "npc": return "§5✦";
            default: return "§b☺";
        }
    }

    private boolean renameVillage;

    private void openRenameField(String text) {
        renameField = new GuiTextField(0, fontRenderer, width / 2 - 110, height / 2 - 20, 220, 18);
        renameField.setMaxStringLength(32);
        renameField.setFocused(true);
        renameField.setText(text == null ? "" : text);
    }

    private void drawCommands(int mx, int my) {
        int h = 56, y = height - h;
        panel(0, y, width, h, PrideFrame.PINK);
        String[][] cmds = {
                { "➜ Move", "move", "Right-click the ground (or this, then click)" }, { "✋ Hold", "HOLD", "Stand still right here (H)" },
                { "⇢ Follow me", "FOLLOW", "Follow your body (U)" }, { "⛨ Guard", "guard", "Guard a spot, fighters attack enemies (G)" },
                { "⇄ Patrol", "patrol", "Walk between two points (P)" }, { "❀ Roam", "wander", "Wander around a spot (N)" },
                { "⌂ Go home", "HOME", "Back to their village, then normal life (B)" }, { "☞ Come here", "COME", "Walk to your body (C)" },
                { "⚑ Rally", "rally", "Gather at the nearest Rally Flag (L)" }, { "✕ Free", "stop", "Forget orders, back to normal life (Delete)" },
                { "✦ Glow", "glow", "Make them glow so you can spot them (K)" }, { "✎ Rename", "rename", "Name them (Insert)" } };
        int bw = Math.max(54, Math.min(84, (width - 8) / 12 - 4)), bx = 4, by = y + 5;
        for (String[] c : cmds) {
            final String id = c[1];
            button(mx, my, bx, by, bw, 18, c[0], PrideFrame.TILE, c[2], () -> command(id));
            bx += bw + 4;
            if (bx + bw > width - 4) { bx = 4; by += 22; }
        }
        // formations + creative tools on the second row
        int fy = y + 30, fx = 4;
        fontRenderer.drawStringWithShadow("§7Formation:", fx, fy + 4, 0xFFFFFF);
        fx += fontRenderer.getStringWidth("Formation:") + 6;
        for (String f : new String[]{ "box", "line", "column", "circle", "wedge", "loose" }) {
            boolean on = f.equals(Settings.get().formation);
            int fw = fontRenderer.getStringWidth(f) + 10;
            button(mx, my, fx, fy, fw, 16, (on ? "§l" : "") + f, on ? PrideFrame.TILE_ON : PrideFrame.BUTTON, "Moves keep this shape", () -> { Settings.get().formation = f; Settings.get().save(); });
            fx += fw + 3;
        }
        fx += 10;
        if (ClientData.creative) {
            fontRenderer.drawStringWithShadow("§7Creative:", fx, fy + 4, 0xFFFFFF);
            fx += fontRenderer.getStringWidth("Creative:") + 6;
            button(mx, my, fx, fy, 50, 16, "✚ Heal", PrideFrame.BUTTON, "Full health", () -> act("heal", null)); fx += 54;
            button(mx, my, fx, fy, 66, 16, "⇲ Teleport", PrideFrame.BUTTON, "Click the ground to teleport them", () -> mode = "tp"); fx += 70;
            button(mx, my, fx, fy, 70, 16, "⇱ Bring here", PrideFrame.BUTTON, "Teleport them to your body", () -> act("tpme", null)); fx += 74;
            button(mx, my, fx, fy, 58, 16, "⌂ Set home", PrideFrame.BUTTON, "Where 'Go home' sends them (their current spot)", () -> act("sethome", null)); fx += 62;
        }
        button(mx, my, fx, fy, 68, 16, "☐ Select all", PrideFrame.BUTTON, "Everyone on screen (Ctrl+A)", this::selectAllVisible); fx += 72;
        button(mx, my, fx, fy, 50, 16, "○ None", PrideFrame.BUTTON, "Deselect (click empty ground)", Selection::clear);
    }

    private void command(String id) {
        switch (id) {
            case "move": case "guard": case "patrol": case "wander":
                if (Selection.IDS.isEmpty()) { ClientData.log("§7Select some units first."); Sounds.play(Sounds.DENY); return; }
                mode = id; pendingFrom = null; Sounds.play(Sounds.SELECT); break;
            case "stop": case "glow": case "rally": act(id, null); break;
            case "rename": openRename(); break;
            case "COME": order("COME", null, null); break;
            default: order(id, null, null);
        }
    }

    private void openRename() {
        if (Selection.IDS.isEmpty()) return;
        renameField = new GuiTextField(0, fontRenderer, width / 2 - 110, height / 2 - 20, 220, 18);
        renameField.setMaxStringLength(32);
        renameField.setFocused(true);
        List<ClientData.Unit> l = Selection.units();
        if (l.size() == 1) renameField.setText(l.get(0).name);
    }

    private void drawLog() {
        long now = System.currentTimeMillis();
        int y = height - 66;
        for (int i = ClientData.LOG.size() - 1; i >= 0 && y > height / 2; i--) {
            String[] e = ClientData.LOG.get(i);
            long age = now - Long.parseLong(e[1]);
            if (age > 8000) break;
            int a = (int) (255 * Math.min(1, (8000 - age) / 1500.0));
            if (a < 8) continue;
            fontRenderer.drawStringWithShadow(e[0], width / 2 - fontRenderer.getStringWidth(e[0]) / 2, y, (a << 24) | 0xFFFFFF);
            y -= 11;
        }
    }

    private void drawHelp() {
        String[] lines = {
                "§d§lPride Overseer keys",
                "§fW A S D / arrows §7move the camera   §fQ E §7turn   §fR F §7tilt   §fwheel / Z X §7zoom",
                "§fright-drag §7turn the camera   §fmiddle-drag §7move it   §fmouse at the screen edge §7moves it too",
                "§fclick §7select   §fShift+click §7add/remove   §fdrag §7box select   §fdouble-click §7everyone of that kind",
                "§fright-click ground §7move there   §fright-click a unit §7talk to it (trade, MCA, Custom NPCs...)",
                "§fH §7hold  §fU §7follow me  §fG §7guard  §fP §7patrol  §fN §7roam  §fB §7go home  §fC §7come here  §fL §7rally",
                "§fK §7glow  §fT §7talk  §fInsert §7rename  §fDelete §7free  §fSpace §7look at selection  §fHome §7my body",
                "§fCtrl+1-9 §7save a group   §f1-9 §7pick a group   §fShift+1-9 §7add a group   §fCtrl+A §7all on screen",
                "§fTab §7next village   §fF1 §7hide the panels   §fM §7minimap   §fV §7village list   §f, §7options   §fEsc / O §7back to your body" };
        int w = 0;
        for (String l : lines) w = Math.max(w, fontRenderer.getStringWidth(l));
        int x = width / 2 - w / 2 - 8, y = height / 2 - lines.length * 6 - 8;
        panel(x, y, w + 16, lines.length * 12 + 12, PrideFrame.PINK);
        for (int i = 0; i < lines.length; i++) fontRenderer.drawStringWithShadow(lines[i], x + 8, y + 7 + i * 12, 0xFFFFFF);
    }

    // ------------------------------------------------------------------ camera from keys / edges

    private long lastFrame;

    private void keysHeld() {
        long now = System.currentTimeMillis();
        double dt = lastFrame == 0 ? 0.016 : Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        if (renameField != null) return;
        double sp = dt * 40 * (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ? 2.5 : 1);
        double f = 0, s = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_W) || Keyboard.isKeyDown(Keyboard.KEY_UP)) f += sp;
        if (Keyboard.isKeyDown(Keyboard.KEY_S) || Keyboard.isKeyDown(Keyboard.KEY_DOWN)) f -= sp;
        if (Keyboard.isKeyDown(Keyboard.KEY_D) || Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) s += sp;
        if (Keyboard.isKeyDown(Keyboard.KEY_A) || Keyboard.isKeyDown(Keyboard.KEY_LEFT)) s -= sp;
        if (f != 0 || s != 0) OverseerMode.pan(f * 0.5, s * 0.5);
        float r = (float) (dt * 90);
        if (Keyboard.isKeyDown(Keyboard.KEY_Q)) OverseerMode.rotate(-r, 0);
        if (Keyboard.isKeyDown(Keyboard.KEY_E)) OverseerMode.rotate(r, 0);
        if (Keyboard.isKeyDown(Keyboard.KEY_R)) OverseerMode.rotate(0, -r * 0.6F);
        if (Keyboard.isKeyDown(Keyboard.KEY_F)) OverseerMode.rotate(0, r * 0.6F);
        if (Keyboard.isKeyDown(Keyboard.KEY_Z)) OverseerMode.tDist = Math.max(Settings.get().minDistance, OverseerMode.tDist - dt * 40);
        if (Keyboard.isKeyDown(Keyboard.KEY_X)) OverseerMode.tDist = Math.min(Settings.get().maxDistance, OverseerMode.tDist + dt * 40);
    }

    private void edgePan(int mx, int my) {
        Settings S = Settings.get();
        if (!S.edgePan || !Mouse.isInsideWindow() || dragging || renameField != null) return;
        int m = S.edgePanMargin;
        double f = 0, s = 0;
        if (mx <= m) s -= 0.6; if (mx >= width - 1 - m) s += 0.6;
        if (my <= m) f += 0.6; if (my >= height - 1 - m) f -= 0.6;
        if (f != 0 || s != 0) OverseerMode.pan(f, s);
    }

    // ------------------------------------------------------------------ input

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int w = Mouse.getEventDWheel();
        if (w == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (Settings.get().villagePanel && !uiHidden && mx < 150 && my > 22 && my < height - 62) { leftScroll -= Integer.signum(w) * 26; return; }
        OverseerMode.zoom(w);
    }

    @Override
    protected void mouseClicked(int mx, int my, int b) throws IOException {
        if (renameField != null) { renameField.mouseClicked(mx, my, b); return; }
        if (help) { help = false; return; }
        if (b == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Object[] h = hits.get(i);
                if (mx >= (Integer) h[0] && my >= (Integer) h[1] && mx < (Integer) h[0] + (Integer) h[2] && my < (Integer) h[1] + (Integer) h[3]) {
                    ((Runnable) h[4]).run();
                    return;
                }
            }
        }
        if (inUi(mx, my)) return;
        downX = mx; downY = my; downButton = b; lastX = mx; lastY = my; dragging = false; rotating = false;
    }

    @Override
    protected void mouseClickMove(int mx, int my, int b, long t) {
        if (downButton < 0) return;
        int dx = mx - lastX, dy = my - lastY;
        lastX = mx; lastY = my;
        if (Math.abs(mx - downX) + Math.abs(my - downY) > 4) dragging = true;
        if (!dragging) return;
        Settings S = Settings.get();
        int inv = S.invertDrag ? -1 : 1;
        if (b == 1) { rotating = true; OverseerMode.rotate(dx * 0.6F * inv, dy * 0.4F * inv); }
        else if (b == 2) OverseerMode.pan(dy * 0.25 * inv, -dx * 0.25 * inv);
    }

    @Override
    protected void mouseReleased(int mx, int my, int b) {
        if (downButton < 0 || b != downButton) return;
        boolean wasDrag = dragging;
        int sx = downX, sy = downY;
        downButton = -1; dragging = false;
        if (b == 0) {
            if (wasDrag) { boxSelect(sx, sy, mx, my); return; }
            leftClick(mx, my);
        } else if (b == 1 && !wasDrag && !rotating) rightClick(mx, my);
    }

    private void leftClick(int mx, int my) {
        Settings S = Settings.get();
        if (mode != null) {
            BlockPos g = groundUnderMouse(mx, my);
            if (g == null) return;
            switch (mode) {
                case "move": order("MOVE", g, null); mode = null; break;
                case "guard": order("GUARD", g, null); mode = null; break;
                case "wander": order("WANDER", g, null); mode = null; break;
                case "tp": { NBTTagCompound t = new NBTTagCompound(); t.setLong("pos", g.toLong()); act("tp", t); mode = null; break; }
                case "patrol":
                    if (pendingFrom == null) { pendingFrom = g; Sounds.play(Sounds.SELECT); }
                    else { order("PATROL", pendingFrom, g); pendingFrom = null; mode = null; }
                    break;
                default: mode = null;
            }
            return;
        }
        ClientData.Unit u = OverseerMode.pickUnit(wx(mx), wy(my));
        if (u == null) { if (!isShiftKeyDown()) Selection.clear(); return; }
        long now = System.currentTimeMillis();
        if (S.doubleClickSelectsType && lastClickId == u.id && now - lastClick < 350) {
            for (ClientData.Unit o : ClientData.UNITS.values()) if (o.kind.equals(u.kind) && onScreen(o)) Selection.add(o.id);
        } else if (isShiftKeyDown()) Selection.toggle(u.id);
        else Selection.set(u.id);
        lastClick = now; lastClickId = u.id;
        Sounds.play(Sounds.SELECT);
    }

    private void rightClick(int mx, int my) {
        if (mode != null) { mode = null; pendingFrom = null; return; }
        ClientData.Unit u = OverseerMode.pickUnit(wx(mx), wy(my));
        if (u != null) { talk(u); return; }
        if (!Settings.get().rightClickMoves) return;
        BlockPos g = groundUnderMouse(mx, my);
        if (g != null && !Selection.IDS.isEmpty()) order("MOVE", g, null);
    }

    private boolean onScreen(ClientData.Unit u) {
        double[] s = OverseerMode.project(u.x, u.y + 1, u.z);
        return s != null && s[0] >= 0 && s[1] >= 0 && s[0] <= mc.displayWidth && s[1] <= mc.displayHeight;
    }

    private void boxSelect(int x0, int y0, int x1, int y1) {
        int ax = Math.min(x0, x1), ay = Math.min(y0, y1), bx = Math.max(x0, x1), by = Math.max(y0, y1);
        if (!isShiftKeyDown()) Selection.clear();
        int n = 0;
        for (ClientData.Unit u : ClientData.UNITS.values()) {
            if (!Settings.get().shows(u.kind)) continue;
            double[] p = OverseerMode.unitPos(u, 1F);
            double[] s = OverseerMode.project(p[0], p[1] + p[3] * 0.5, p[2]);
            if (s == null) continue;
            double gxv = gx(s[0]), gyv = gy(s[1]);
            if (gxv >= ax && gxv <= bx && gyv >= ay && gyv <= by) { Selection.add(u.id); n++; }
        }
        if (n > 0) Sounds.play(Sounds.SELECT);
    }

    private void selectAllVisible() {
        for (ClientData.Unit u : ClientData.UNITS.values()) if (Settings.get().shows(u.kind) && onScreen(u)) Selection.add(u.id);
        Sounds.play(Sounds.SELECT);
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        Settings S = Settings.get();
        if (renameField != null) {
            if (key == Keyboard.KEY_ESCAPE) { renameField = null; renameVillage = false; return; }
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                if (renameVillage) { villageCmd("rename", renameField.getText()); renameVillage = false; }
                else { NBTTagCompound t = new NBTTagCompound(); t.setString("name", renameField.getText()); act("rename", t); }
                renameField = null; return;
            }
            renameField.textboxKeyTyped(c, key);
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (help) { help = false; return; }
            if (mode != null) { mode = null; pendingFrom = null; return; }
            OverseerMode.exit(); return;
        }
        if (key == ClientSetup.KEY_OPEN.getKeyCode()) { OverseerMode.exit(); return; }
        boolean ctrl = isCtrlKeyDown(), shift = isShiftKeyDown();
        if (key >= Keyboard.KEY_1 && key <= Keyboard.KEY_9 && S.ctrlGroups) {
            int n = key - Keyboard.KEY_1 + 1;
            if (ctrl) { Selection.saveGroup(n); ClientData.log("§d✦ §7Group §f" + n + " §7= " + Selection.IDS.size() + " units"); }
            else { Selection.loadGroup(n, shift); Sounds.play(Sounds.SELECT); }
            return;
        }
        if (ctrl && key == Keyboard.KEY_A) { selectAllVisible(); return; }
        switch (key) {
            case Keyboard.KEY_H: command("HOLD"); break;
            case Keyboard.KEY_U: command("FOLLOW"); break;
            case Keyboard.KEY_G: command("guard"); break;
            case Keyboard.KEY_P: command("patrol"); break;
            case Keyboard.KEY_N: command("wander"); break;
            case Keyboard.KEY_B: command("HOME"); break;
            case Keyboard.KEY_C: command("COME"); break;
            case Keyboard.KEY_L: command("rally"); break;
            case Keyboard.KEY_K: command("glow"); break;
            case Keyboard.KEY_DELETE: case Keyboard.KEY_BACK: command("stop"); break;
            case Keyboard.KEY_INSERT: openRename(); break;
            case Keyboard.KEY_T: { List<ClientData.Unit> l = Selection.units(); if (!l.isEmpty()) talk(l.get(0)); else if (hoverUnit != null) talk(hoverUnit); break; }
            case Keyboard.KEY_SPACE: {
                List<ClientData.Unit> l = Selection.units();
                if (!l.isEmpty()) { double x = 0, z = 0, y = 0; for (ClientData.Unit u : l) { x += u.x; y += u.y; z += u.z; } OverseerMode.flyTo(x / l.size(), y / l.size(), z / l.size()); }
                break;
            }
            case Keyboard.KEY_HOME: OverseerMode.flyTo(mc.player.posX, mc.player.posY, mc.player.posZ); break;
            case Keyboard.KEY_TAB: {
                if (ClientData.VILLAGES.isEmpty()) break;
                List<ClientData.Village> vs = new ArrayList<ClientData.Village>(ClientData.VILLAGES);
                vs.sort(Comparator.comparingDouble(v -> v.pos.distanceSq(mc.player.getPosition())));
                tabIndex = (tabIndex + 1) % vs.size();
                ClientData.Village v = vs.get(tabIndex);
                OverseerMode.flyTo(v.pos.getX() + 0.5, OverseerMode.groundY(v.pos.getX() + 0.5, v.pos.getZ() + 0.5, v.pos.getY()), v.pos.getZ() + 0.5);
                ClientData.log("§d⚑ §f" + v.name);
                openVillage(v.pos);
                break;
            }
            case Keyboard.KEY_F1: uiHidden = !uiHidden; break;
            case Keyboard.KEY_M: S.minimap = !S.minimap; S.save(); break;
            case Keyboard.KEY_V: S.villagePanel = !S.villagePanel; S.save(); break;
            case Keyboard.KEY_COMMA: mc.displayGuiScreen(new SettingsScreen(this)); break;
            case Keyboard.KEY_SLASH: help = !help; break;
            default: break;
        }
    }
    private static int tabIndex = -1;

    @Override public void updateScreen() { if (renameField != null) renameField.updateCursorCounter(); }

    static Entity entity(int id) { return Minecraft.getMinecraft().world.getEntityByID(id); }
    static ScaledResolution res() { return new ScaledResolution(Minecraft.getMinecraft()); }
}
