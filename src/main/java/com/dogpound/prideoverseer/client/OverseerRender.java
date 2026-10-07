package com.dogpound.prideoverseer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** Things drawn into the world while overseeing: unit rings, order lines and flags, village borders, rally beacons. */
public final class OverseerRender {

    static int kindColor(String kind) {
        switch (kind) {
            case "VILLAGER": return 0x7CC8FF; case "GUARD": return 0xFFD23A; case "GOLEM": return 0xC9C9C9; case "PET": return 0xFF9FD1;
            case "MOUNT": return 0xC9A27E; case "SOLDIER": return 0xFF6A4A; case "WORKER": return 0x8CE06A; case "NPC": return 0xB59CFF;
            case "HOSTILE": return 0xE02040; default: return 0xA6E36B;
        }
    }

    private static void col(BufferBuilder b, double x, double y, double z, int rgb, float a) {
        b.pos(x, y, z).color(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, a).endVertex();
    }

    /** a flat ring lying on the ground */
    static void ring(double x, double y, double z, double r, double w, int rgb, float a) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int seg = 28;
        for (int i = 0; i <= seg; i++) {
            double an = i * Math.PI * 2 / seg, c = Math.cos(an), s = Math.sin(an);
            col(b, x + c * r, y, z + s * r, rgb, a);
            col(b, x + c * (r + w), y, z + s * (r + w), rgb, a * 0.15F);
        }
        t.draw();
    }

    static void line(double x1, double y1, double z1, double x2, double y2, double z2, int rgb, float a) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        col(b, x1, y1, z1, rgb, a); col(b, x2, y2, z2, rgb, a);
        t.draw();
    }

    /** a little pennant: pole + triangle cloth */
    static void flag(double x, double y, double z, int rgb, float h) {
        line(x, y, z, x, y + h, z, 0xFFFFFF, 0.9F);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        col(b, x, y + h, z, rgb, 0.95F); col(b, x + h * 0.45, y + h * 0.85, z, rgb, 0.95F); col(b, x, y + h * 0.7, z, rgb, 0.95F);
        col(b, x, y + h, z, rgb, 0.95F); col(b, x, y + h * 0.7, z, rgb, 0.95F); col(b, x + h * 0.45, y + h * 0.85, z, rgb, 0.95F);
        t.draw();
    }

    @SubscribeEvent
    public void world(RenderWorldLastEvent e) {
        if (!OverseerMode.active()) return;
        Minecraft mc = Minecraft.getMinecraft();
        Settings S = Settings.get();
        float pt = e.getPartialTicks();
        Vec3d c = OverseerMode.eye();
        long now = System.currentTimeMillis();
        GlStateManager.pushMatrix();
        GlStateManager.translate(-c.x, -c.y, -c.z);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2.5F);
        try {
            // villages: a dashed ring round each one + a banner pole in the middle
            if (S.villageBorders) for (ClientData.Village v : ClientData.VILLAGES) {
                double gy = OverseerMode.groundY(v.pos.getX() + 0.5, v.pos.getZ() + 0.5, v.pos.getY()) + 0.15;
                int rgb = "village".equals(v.kind) ? 0xF5A9B8 : 0xB59CFF;
                Tessellator t = Tessellator.getInstance();
                BufferBuilder b = t.getBuffer();
                b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
                int seg = Math.max(32, v.radius * 2);
                double spin = (now % 20000) / 20000.0 * Math.PI * 2 / seg * 4;
                for (int i = 0; i < seg; i += 2) {
                    double a1 = i * Math.PI * 2 / seg + spin, a2 = (i + 1) * Math.PI * 2 / seg + spin;
                    col(b, v.pos.getX() + 0.5 + Math.cos(a1) * v.radius, gy, v.pos.getZ() + 0.5 + Math.sin(a1) * v.radius, rgb, 0.8F);
                    col(b, v.pos.getX() + 0.5 + Math.cos(a2) * v.radius, gy, v.pos.getZ() + 0.5 + Math.sin(a2) * v.radius, rgb, 0.8F);
                }
                t.draw();
                flag(v.pos.getX() + 0.5, gy, v.pos.getZ() + 0.5, rgb, 4F);
            }
            // rally flags: a soft beam so you can find them from far away
            if (S.rallyFlags) for (BlockPos f : ClientData.FLAGS) {
                Tessellator t = Tessellator.getInstance();
                BufferBuilder b = t.getBuffer();
                b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                double x = f.getX() + 0.5, z = f.getZ() + 0.5, y0 = f.getY(), y1 = y0 + 40, w = 0.35;
                col(b, x - w, y0, z, 0xFFD23A, 0.35F); col(b, x + w, y0, z, 0xFFD23A, 0.35F); col(b, x + w, y1, z, 0xFFD23A, 0F); col(b, x - w, y1, z, 0xFFD23A, 0F);
                col(b, x, y0, z - w, 0xFFD23A, 0.35F); col(b, x, y0, z + w, 0xFFD23A, 0.35F); col(b, x, y1, z + w, 0xFFD23A, 0F); col(b, x, y1, z - w, 0xFFD23A, 0F);
                t.draw();
                ring(x, y0 + 0.05, z, 1.4 + 0.3 * Math.sin(now / 300.0), 0.25, 0xFFD23A, 0.9F);
            }
            // units: ring on the ground, brighter + pulsing when selected
            ClientData.Unit hover = OverseerScreen.hoverUnit;
            for (ClientData.Unit u : ClientData.UNITS.values()) {
                if (!S.shows(u.kind)) continue;
                boolean sel = Selection.has(u.id), hov = hover != null && hover.id == u.id;
                double[] p = OverseerMode.unitPos(u, pt);
                if (S.rings || sel || hov) {
                    int rgb = sel ? (S.pride ? PrideFrame.RAINBOW[(int) ((now / 120 + u.id) % 6)] & 0xFFFFFF : S.accent & 0xFFFFFF) : hov ? 0xFFFFFF : kindColor(u.kind);
                    double r = sel ? 0.75 + 0.06 * Math.sin(now / 160.0) : 0.6;
                    if (S.ringStyle == 2) {
                        double h = r;
                        line(p[0] - h, p[1] + 0.06, p[2] - h, p[0] + h, p[1] + 0.06, p[2] - h, rgb, 0.95F);
                        line(p[0] + h, p[1] + 0.06, p[2] - h, p[0] + h, p[1] + 0.06, p[2] + h, rgb, 0.95F);
                        line(p[0] + h, p[1] + 0.06, p[2] + h, p[0] - h, p[1] + 0.06, p[2] + h, rgb, 0.95F);
                        line(p[0] - h, p[1] + 0.06, p[2] + h, p[0] - h, p[1] + 0.06, p[2] - h, rgb, 0.95F);
                    } else ring(p[0], p[1] + 0.06, p[2], r, S.ringStyle == 1 ? 0.08 : sel ? 0.32 : 0.18, rgb, sel || hov ? 0.95F : 0.55F);
                }
                // what it's doing: a line to where it's going + a flag there
                if (sel && S.orderLines && u.opos != null) {
                    double ty = u.opos.getY() + 0.1;
                    line(p[0], p[1] + 0.1, p[2], u.opos.getX() + 0.5, ty, u.opos.getZ() + 0.5, 0x8CE06A, 0.8F);
                    if (S.orderFlags) flag(u.opos.getX() + 0.5, ty, u.opos.getZ() + 0.5, 0x8CE06A, 1.6F);
                    if (u.opos2 != null) {
                        line(u.opos.getX() + 0.5, ty, u.opos.getZ() + 0.5, u.opos2.getX() + 0.5, u.opos2.getY() + 0.1, u.opos2.getZ() + 0.5, 0xFFD23A, 0.7F);
                        if (S.orderFlags) flag(u.opos2.getX() + 0.5, u.opos2.getY() + 0.1, u.opos2.getZ() + 0.5, 0xFFD23A, 1.6F);
                    }
                }
            }
            // where a right-click would send them
            RayTraceResult hb = OverseerScreen.hoverBlock;
            if (hb != null && hb.typeOfHit == RayTraceResult.Type.BLOCK && !Selection.IDS.isEmpty() && hover == null) {
                BlockPos b = hb.getBlockPos();
                ring(b.getX() + 0.5, b.getY() + 1.04, b.getZ() + 0.5, 0.45 + 0.1 * Math.sin(now / 150.0), 0.2, 0x8CE06A, 0.9F);
            }
            // ripples where orders were given
            for (java.util.Iterator<double[]> it = ClientData.PINGS.iterator(); it.hasNext(); ) {
                double[] pg = it.next();
                double age = (now - pg[3]) / 900.0;
                if (age > 1) { it.remove(); continue; }
                for (int k = 0; k < 3; k++) {
                    double a2 = age - k * 0.18;
                    if (a2 < 0) continue;
                    ring(pg[0], pg[1] + 0.06, pg[2], 0.3 + a2 * 2.6, 0.12, 0x8CE06A, (float) (1 - a2) * 0.9F);
                }
            }
            // pending second click for patrol/guard
            if (OverseerScreen.pendingFrom != null) {
                BlockPos b = OverseerScreen.pendingFrom;
                flag(b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5, 0xFFD23A, 2F);
            }
        } finally {
            GlStateManager.glLineWidth(1F);
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    }
}
