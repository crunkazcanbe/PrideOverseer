package com.dogpound.prideoverseer.client;

import com.dogpound.prideoverseer.net.OverseerNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The overseer camera: it leaves your body and floats above a point on the ground like a strategy game. Your body
 * stays where it is (you see it down there). The mouse is free for clicking units.
 */
public final class OverseerMode {
    private OverseerMode() {}

    /** client-only camera entity, never added to the world */
    static final class Cam extends Entity {
        Cam(World w) { super(w); setSize(0.01F, 0.01F); noClip = true; }
        @Override protected void entityInit() {}
        @Override protected void readEntityFromNBT(NBTTagCompound c) {}
        @Override protected void writeEntityToNBT(NBTTagCompound c) {}
        @Override public float getEyeHeight() { return 0; }
        @Override public boolean isInvisible() { return true; }
    }

    static boolean active;
    static Cam cam;
    /** pivot on the ground the camera looks at, its angles, and how far back it hangs */
    static double px, py, pz, dist = 40, tDist = 40;
    static float yaw, pitch = 55, tYaw, tPitch = 55;
    static double tpx, tpy, tpz;
    private static boolean hideGuiBefore;
    private static int thirdBefore;
    private static long lastView;

    public static boolean active() { return active; }

    public static void enter(BlockPos at) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || active) return;
        Settings S = Settings.get();
        Vec3d c = at != null ? new Vec3d(at).addVector(0.5, 0, 0.5) : mc.player.getPositionVector();
        px = tpx = c.x; py = tpy = c.y; pz = tpz = c.z;
        yaw = tYaw = mc.player.rotationYaw;
        pitch = tPitch = S.startPitch;
        dist = tDist = S.startDistance;
        cam = new Cam(mc.world);
        place();
        hideGuiBefore = mc.gameSettings.hideGUI;
        thirdBefore = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 0;
        mc.setRenderViewEntity(cam);
        active = true;
        OverseerNet.NET.sendToServer(new OverseerNet.Cmd("enter", null));
        mc.displayGuiScreen(new OverseerScreen());
        if (S.autoSelectOnEnter) for (ClientData.Unit u : ClientData.UNITS.values()) Selection.add(u.id);
        Sounds.play(Sounds.OPEN);
    }

    public static void exit() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active) return;
        active = false;
        mc.gameSettings.hideGUI = hideGuiBefore;
        mc.gameSettings.thirdPersonView = thirdBefore;
        if (mc.player != null) mc.setRenderViewEntity(mc.player);
        cam = null;
        OverseerNet.NET.sendToServer(new OverseerNet.Cmd("exit", null));
        if (mc.currentScreen instanceof OverseerScreen) mc.displayGuiScreen(null);
        Sounds.play(Sounds.CLOSE);
    }

    // ------------------------------------------------------------------ camera maths

    static Vec3d forward() {
        float y = yaw * 0.017453292F, p = pitch * 0.017453292F;
        return new Vec3d(-MathHelper.sin(y) * MathHelper.cos(p), -MathHelper.sin(p), MathHelper.cos(y) * MathHelper.cos(p));
    }
    static Vec3d right() { Vec3d f = forward(); Vec3d r = new Vec3d(-f.z, 0, f.x); double l = r.lengthVector(); return l < 1e-6 ? new Vec3d(1, 0, 0) : r.scale(1 / l); }
    static Vec3d up() { return right().crossProduct(forward()).normalize(); }
    static Vec3d eye() { Vec3d f = forward(); return new Vec3d(px - f.x * dist, py - f.y * dist, pz - f.z * dist); }

    static void place() {
        if (cam == null) return;
        Vec3d e = eye();
        cam.posX = cam.prevPosX = cam.lastTickPosX = e.x;
        cam.posY = cam.prevPosY = cam.lastTickPosY = e.y;
        cam.posZ = cam.prevPosZ = cam.lastTickPosZ = e.z;
        cam.rotationYaw = cam.prevRotationYaw = yaw;
        cam.rotationPitch = cam.prevRotationPitch = pitch;
    }

    /** per frame: glide toward the targets (smooth camera) */
    static void glide(float k) {
        Settings S = Settings.get();
        if (!S.smoothCamera) k = 1F;
        px += (tpx - px) * k; py += (tpy - py) * k; pz += (tpz - pz) * k;
        dist += (tDist - dist) * k;
        yaw += MathHelper.wrapDegrees(tYaw - yaw) * k;
        pitch += (tPitch - pitch) * k;
        place();
    }

    /** move the pivot along the ground (screen-relative), keeping it on the land below */
    static void pan(double fwd, double side) {
        Settings S = Settings.get();
        Vec3d f = forward();
        double fl = Math.sqrt(f.x * f.x + f.z * f.z), fx = fl < 1e-6 ? 0 : f.x / fl, fz = fl < 1e-6 ? 0 : f.z / fl;
        Vec3d r = right();
        double s = S.panSpeed * Math.max(0.35, tDist / 30.0);
        tpx += (fx * fwd + r.x * side) * s;
        tpz += (fz * fwd + r.z * side) * s;
        if (S.keepHeight) tpy = groundY(tpx, tpz, tpy);
    }

    static void rotate(float dYaw, float dPitch) {
        Settings S = Settings.get();
        tYaw += dYaw * S.rotateSpeed;
        tPitch = MathHelper.clamp(tPitch + dPitch * S.rotateSpeed, 15F, 89.5F);
    }

    static void zoom(int wheel) {
        Settings S = Settings.get();
        int w = S.invertZoom ? -wheel : wheel;
        double k = 1 + 0.13 * S.zoomSpeed;
        tDist = MathHelper.clamp(w > 0 ? tDist / k : tDist * k, S.minDistance, S.maxDistance);
    }

    static void flyTo(double x, double y, double z) { tpx = x; tpy = y; tpz = z; }

    static double groundY(double x, double z, double fallback) {
        World w = Minecraft.getMinecraft().world;
        if (w == null) return fallback;
        BlockPos top = w.getHeight(new BlockPos(x, 0, z));
        return top.getY() > 0 ? top.getY() : fallback;
    }

    // ------------------------------------------------------------------ mouse <-> world

    /** a ray from the camera through window pixel (mx, my), y down */
    static Vec3d[] ray(int mx, int my) {
        Minecraft mc = Minecraft.getMinecraft();
        double nx = mx / (double) mc.displayWidth * 2 - 1, ny = 1 - my / (double) mc.displayHeight * 2;
        double tanY = Math.tan(Math.toRadians(fov()) / 2), tanX = tanY * mc.displayWidth / (double) mc.displayHeight;
        Vec3d f = forward(), r = right(), u = up();
        Vec3d dir = f.add(r.scale(nx * tanX)).add(u.scale(ny * tanY)).normalize();
        return new Vec3d[]{ eye(), dir };
    }

    static float fov() { return Settings.get().fov; }

    static RayTraceResult pickBlock(int mx, int my) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return null;
        Vec3d[] r = ray(mx, my);
        return mc.world.rayTraceBlocks(r[0], r[0].add(r[1].scale(800)), false, true, false);
    }

    /** the unit under the mouse: real entities first (exact boxes), then far units the server told us about */
    static ClientData.Unit pickUnit(int mx, int my) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3d[] r = ray(mx, my);
        Vec3d from = r[0], to = from.add(r[1].scale(800));
        RayTraceResult blk = pickBlock(mx, my);
        double maxD = blk == null ? 1e9 : blk.hitVec.distanceTo(from) + 1.5;
        ClientData.Unit best = null; double bd = 1e18;
        for (ClientData.Unit u : ClientData.UNITS.values()) {
            if (!Settings.get().shows(u.kind)) continue;
            Entity e = mc.world.getEntityByID(u.id);
            AxisAlignedBB bb = e != null ? e.getEntityBoundingBox().grow(0.35) : new AxisAlignedBB(u.x - 0.6, u.y, u.z - 0.6, u.x + 0.6, u.y + 1.9, u.z + 0.6);
            RayTraceResult hit = bb.calculateIntercept(from, to);
            if (hit == null) continue;
            double d = hit.hitVec.distanceTo(from);
            if (d < bd && d < maxD) { bd = d; best = u; }
        }
        return best;
    }

    /** world -> window pixels (y down) or null when behind the camera */
    static double[] project(double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3d d = new Vec3d(x, y, z).subtract(eye());
        Vec3d f = forward(), r = right(), u = up();
        double zf = d.dotProduct(f);
        if (zf < 0.1) return null;
        double tanY = Math.tan(Math.toRadians(fov()) / 2), tanX = tanY * mc.displayWidth / (double) mc.displayHeight;
        double sx = d.dotProduct(r) / zf / tanX, sy = d.dotProduct(u) / zf / tanY;
        return new double[]{ (sx + 1) / 2 * mc.displayWidth, (1 - sy) / 2 * mc.displayHeight, zf };
    }

    /** window pixels -> GUI pixels */
    static double gui(double px) { return px / new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor(); }

    static double[] unitPos(ClientData.Unit u, float pt) {
        Entity e = Minecraft.getMinecraft().world.getEntityByID(u.id);
        if (e == null) return new double[]{ u.x, u.y, u.z, 1.9 };
        double h = e instanceof EntityLivingBase ? e.height : 1.8;
        return new double[]{ e.lastTickPosX + (e.posX - e.lastTickPosX) * pt, e.lastTickPosY + (e.posY - e.lastTickPosY) * pt, e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt, h };
    }

    // ------------------------------------------------------------------ events

    public static final class Events {
        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !active) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || mc.world == null || mc.player.isDead) { exit(); return; }
            if (cam != null && cam.world != mc.world) { exit(); return; }
            if (!(mc.currentScreen instanceof OverseerScreen) && mc.currentScreen == null) { exit(); return; }
            if (Settings.get().followSelected && !Selection.IDS.isEmpty()) {
                double x = 0, z = 0; int n = 0;
                for (ClientData.Unit u : Selection.units()) { double[] p = unitPos(u, 1F); x += p[0]; z += p[2]; n++; }
                if (n > 0) { tpx = x / n; tpz = z / n; tpy = groundY(tpx, tpz, tpy); }
            }
            long now = System.currentTimeMillis();
            if (now - lastView > 500) {
                lastView = now;
                NBTTagCompound t = new NBTTagCompound();
                t.setDouble("x", px); t.setDouble("y", py); t.setDouble("z", pz);
                OverseerNet.NET.sendToServer(new OverseerNet.Cmd("view", t));
            }
        }

        @SubscribeEvent
        public void render(TickEvent.RenderTickEvent e) {
            if (e.phase == TickEvent.Phase.START && active) glide(0.25F);
        }

        @SubscribeEvent
        public void fov(EntityViewRenderEvent.FOVModifier e) { if (active) e.setFOV(OverseerMode.fov()); }

        /** no first-person hand floating in front of the camera (her 10-05: "make it look just like a role-playing game") */
        @SubscribeEvent
        public void hand(net.minecraftforge.client.event.RenderHandEvent e) { if (active) e.setCanceled(true); }

        /** your body stays hidden too unless you want to see where you left it */
        @SubscribeEvent
        public void body(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
            if (active && !Settings.get().showBody && e.getEntityPlayer() == Minecraft.getMinecraft().player) e.setCanceled(true);
        }

        /** your own HUD hides while you oversee (the overseer screen draws its own) */
        @SubscribeEvent
        public void hud(RenderGameOverlayEvent.Pre e) { if (active && Settings.get().hideHudOnEnter) e.setCanceled(true); }
    }
}
