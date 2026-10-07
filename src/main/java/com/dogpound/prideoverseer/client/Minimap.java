package com.dogpound.prideoverseer.client;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * A top-down map of the land round the camera (block colours like a vanilla map, with hill shading), unit dots,
 * villages and rally flags, and the camera's view. Click it to fly there. Redrawn a slice at a time so it's cheap.
 */
final class Minimap {
    private Minimap() {}

    private static final int N = 128;                       // texture size
    private static DynamicTexture tex;
    private static int[] px;
    private static int row;                                  // next row to redraw
    private static int cx0, cz0, scale0 = -1;                // centre + blocks per pixel the texture was made for

    private static int color(World w, int x, int z) {
        BlockPos top = w.getHeight(new BlockPos(x, 0, z));
        if (top.getY() <= 0) return 0xFF0E0A16;
        BlockPos b = top.down();
        IBlockState s = w.getBlockState(b);
        MapColor mc = s.getMapColor(w, b);
        int c = mc == null ? 0x7FB238 : mc.colorValue;
        if (c == 0) c = 0x707070;
        int north = w.getHeight(new BlockPos(x, 0, z - 1)).getY();
        float shade = top.getY() > north ? 1.12F : top.getY() < north ? 0.82F : 1F;
        int r = Math.min(255, (int) (((c >> 16) & 255) * shade)), g = Math.min(255, (int) (((c >> 8) & 255) * shade)), bl = Math.min(255, (int) ((c & 255) * shade));
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static void refresh() {
        Minecraft mc = Minecraft.getMinecraft();
        int sc = Math.max(1, Settings.get().minimapZoom);
        int cx = MathHelper.floor(OverseerMode.px), cz = MathHelper.floor(OverseerMode.pz);
        if (tex == null) { tex = new DynamicTexture(N, N); px = tex.getTextureData(); }
        if (sc != scale0 || Math.abs(cx - cx0) > sc * 8 || Math.abs(cz - cz0) > sc * 8) { cx0 = cx; cz0 = cz; scale0 = sc; row = 0; }
        for (int k = 0; k < 8 && row < N; k++, row++) {                       // 8 rows a frame: a full map every 16 frames
            int z = cz0 + (row - N / 2) * sc;
            for (int i = 0; i < N; i++) px[row * N + i] = color(mc.world, cx0 + (i - N / 2) * sc, z);
        }
        if (row >= N) row = 0;
        tex.updateDynamicTexture();
    }

    static void draw(GuiScreen s, int mx, int my, List<Object[]> hits) {
        Minecraft mc = Minecraft.getMinecraft();
        Settings S = Settings.get();
        int size = Math.max(80, Math.min(260, S.minimapSize));
        int x = 4, y = s.height - 62 - size - 4;
        if (S.villagePanel) x = 152;
        refresh();
        int sc = scale0;
        // frame
        Gui.drawRect(x - 2, y - 2, x + size + 2, y + size + 2, 0xE0140E22);
        Gui.drawRect(x - 2, y - 2, x + size + 2, y - 1, PrideFrame.PINK);
        // the map picture
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.bindTexture(tex.getGlTextureId());
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x, y + size, 0).tex(0, 1).endVertex();
        b.pos(x + size, y + size, 0).tex(1, 1).endVertex();
        b.pos(x + size, y, 0).tex(1, 0).endVertex();
        b.pos(x, y, 0).tex(0, 0).endVertex();
        t.draw();
        double k = size / (double) (N * sc);                  // GUI pixels per block
        double ox = x + size / 2.0, oz = y + size / 2.0;
        // villages, flags, units
        for (ClientData.Village v : ClientData.VILLAGES) {
            int vx = (int) (ox + (v.pos.getX() - cx0) * k), vz = (int) (oz + (v.pos.getZ() - cz0) * k);
            if (vx < x || vz < y || vx > x + size || vz > y + size) continue;
            Gui.drawRect(vx - 2, vz - 2, vx + 3, vz + 3, 0xFFF5A9B8);
        }
        for (BlockPos f : ClientData.FLAGS) {
            int fx = (int) (ox + (f.getX() - cx0) * k), fz = (int) (oz + (f.getZ() - cz0) * k);
            if (fx >= x && fz >= y && fx <= x + size && fz <= y + size) Gui.drawRect(fx - 1, fz - 3, fx + 2, fz + 1, 0xFFFFD23A);
        }
        for (ClientData.Unit u : ClientData.UNITS.values()) {
            if (!S.shows(u.kind)) continue;
            int ux = (int) (ox + (u.x - cx0) * k), uz = (int) (oz + (u.z - cz0) * k);
            if (ux < x || uz < y || ux >= x + size || uz >= y + size) continue;
            int c = Selection.has(u.id) ? 0xFFFFFFFF : 0xFF000000 | OverseerRender.kindColor(u.kind);
            Gui.drawRect(ux - 1, uz - 1, ux + 1, uz + 1, c);
        }
        // you
        int yx = (int) (ox + (mc.player.posX - cx0) * k), yz = (int) (oz + (mc.player.posZ - cz0) * k);
        if (yx >= x && yz >= y && yx <= x + size && yz <= y + size) { Gui.drawRect(yx - 2, yz - 2, yx + 2, yz + 2, 0xFF000000); Gui.drawRect(yx - 1, yz - 1, yx + 1, yz + 1, 0xFF5BCEFA); }
        // the camera: a little cross where it looks
        int px0 = (int) (ox + (OverseerMode.px - cx0) * k), pz0 = (int) (oz + (OverseerMode.pz - cz0) * k);
        Gui.drawRect(px0 - 4, pz0, px0 + 5, pz0 + 1, 0xFFFFFFFF);
        Gui.drawRect(px0, pz0 - 4, px0 + 1, pz0 + 5, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow("§7" + (N * sc) + " blocks", x + 2, y + size - 9, 0xFFFFFF);
        hits.add(new Object[]{ x, y, size, size, (Runnable) () -> {
            int mxx = org.lwjgl.input.Mouse.getX() * s.width / mc.displayWidth, myy = s.height - org.lwjgl.input.Mouse.getY() * s.height / mc.displayHeight - 1;
            double wxv = cx0 + (mxx - ox) / k, wzv = cz0 + (myy - oz) / k;
            OverseerMode.flyTo(wxv, OverseerMode.groundY(wxv, wzv, OverseerMode.py), wzv);
        } });
    }
}
